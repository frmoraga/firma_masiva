package com.dictuc.feamass.xnet;

import com.dictuc.feamass.xnet.model.ErrorResponseDto;
import com.dictuc.feamass.xnet.model.FirmarResponseDto;
import com.dictuc.feamass.xnet.model.InformeSummary;
import com.dictuc.feamass.xnet.model.LoginRequestDto;
import com.dictuc.feamass.xnet.model.LoginResponseDto;
import com.dictuc.feamass.xnet.model.RechazarRequestDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Cliente REST hacia el backend real de XNET (PHP): sesion por cookie + header X-App:admin
 * + token CSRF sincronizado (ver AuthController/CsrfMiddleware/Session en el backend).
 * Cada instancia sostiene su propio HttpClient con manejo de cookies, por lo que debe
 * reutilizarse la MISMA instancia durante toda la sesion (login -> listar -> firmar).
 */
public final class XnetClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final Duration readTimeout;

    public XnetClient() {
        this.baseUrl = XnetConfig.baseUrl();
        this.readTimeout = Duration.ofMillis(XnetConfig.readTimeoutMs());
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(XnetConfig.connectTimeoutMs()))
                .cookieHandler(cookieManager)
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public LoginResponseDto login(String email, String password) throws XnetException {
        try {
            byte[] body = mapper.writeValueAsBytes(new LoginRequestDto(email, password));
            HttpRequest req = requestBuilder("/auth/login")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<byte[]> resp = send(req);
            checkStatus(resp);
            return mapper.readValue(resp.body(), LoginResponseDto.class);
        } catch (IOException | InterruptedException e) {
            throw networkError(e);
        }
    }

    public void logout(String csrfToken) throws XnetException {
        try {
            HttpRequest req = requestBuilder("/auth/logout")
                    .header("X-CSRF-Token", csrfToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<byte[]> resp = send(req);
            checkStatus(resp);
        } catch (IOException | InterruptedException e) {
            throw networkError(e);
        }
    }

    public List<InformeSummary> listarPendientesFirma() throws XnetException {
        try {
            HttpRequest req = requestBuilder("/informes/pendientes-firma").GET().build();
            HttpResponse<byte[]> resp = send(req);
            checkStatus(resp);
            return mapper.readValue(resp.body(), new TypeReference<List<InformeSummary>>() {});
        } catch (IOException | InterruptedException e) {
            throw networkError(e);
        }
    }

    /** @return bytes de la imagen de firma propia, o null si el usuario no tiene una configurada. */
    public byte[] descargarFirmaImagenPropia() throws XnetException {
        try {
            HttpRequest req = requestBuilder("/usuarios-dictuc/mi-firma-imagen").GET().build();
            HttpResponse<byte[]> resp = send(req);
            if (resp.statusCode() == 404) {
                return null;
            }
            checkStatus(resp);
            return resp.body();
        } catch (IOException | InterruptedException e) {
            throw networkError(e);
        }
    }

    public byte[] descargarPdf(long informeId) throws XnetException {
        try {
            HttpRequest req = requestBuilder("/informes/" + informeId + "/archivo?accion=ver").GET().build();
            HttpResponse<byte[]> resp = send(req);
            checkStatus(resp);
            return resp.body();
        } catch (IOException | InterruptedException e) {
            throw networkError(e);
        }
    }

    public FirmarResponseDto subirFirmado(long informeId, String csrfToken, byte[] pdfFirmado) throws XnetException {
        try {
            String boundary = "FeamassBoundary" + System.nanoTime();
            byte[] body = buildMultipartPdf(boundary, "archivo_firmado", "firmado.pdf", pdfFirmado);
            HttpRequest req = requestBuilder("/informes/" + informeId + "/firmar")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("X-CSRF-Token", csrfToken)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<byte[]> resp = send(req);
            checkStatus(resp);
            return mapper.readValue(resp.body(), FirmarResponseDto.class);
        } catch (IOException | InterruptedException e) {
            throw networkError(e);
        }
    }

    /**
     * El firmante rechaza el informe al revisarlo (encontro un error en el PDF) en vez de
     * firmarlo. Es terminal en el backend: el informe queda en estado "rechazado" y no vuelve
     * solo a la cola de firma (ver InformeEstadoService::rechazar en el backend).
     */
    public FirmarResponseDto rechazar(long informeId, String csrfToken, String justificacion) throws XnetException {
        try {
            byte[] body = mapper.writeValueAsBytes(new RechazarRequestDto(justificacion));
            HttpRequest req = requestBuilder("/informes/" + informeId + "/rechazar")
                    .header("Content-Type", "application/json")
                    .header("X-CSRF-Token", csrfToken)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<byte[]> resp = send(req);
            checkStatus(resp);
            return mapper.readValue(resp.body(), FirmarResponseDto.class);
        } catch (IOException | InterruptedException e) {
            throw networkError(e);
        }
    }

    /* ====================== helpers internos ====================== */

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("X-App", "admin")
                .timeout(readTimeout);
    }

    private HttpResponse<byte[]> send(HttpRequest req) throws IOException, InterruptedException {
        return http.send(req, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static byte[] buildMultipartPdf(String boundary, String fieldName, String fileName, byte[] fileBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(fileBytes.length + 256);
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void checkStatus(HttpResponse<byte[]> resp) throws XnetException {
        int status = resp.statusCode();
        if (status >= 200 && status < 300) return;

        String message = null;
        try {
            ErrorResponseDto err = mapper.readValue(resp.body(), ErrorResponseDto.class);
            message = err.error;
        } catch (Exception ignore) {
            // cuerpo de error no venia en el formato esperado
        }
        if (message == null) message = "Error HTTP " + status;

        XnetException.Kind kind = switch (status) {
            case 401 -> XnetException.Kind.UNAUTHENTICATED;
            case 403 -> XnetException.Kind.FORBIDDEN;
            case 404 -> XnetException.Kind.NOT_FOUND;
            case 409 -> XnetException.Kind.CONFLICT;
            case 400, 422 -> XnetException.Kind.VALIDATION;
            default -> XnetException.Kind.SERVER_ERROR;
        };
        throw new XnetException(kind, status, message);
    }

    private XnetException networkError(Exception e) {
        return new XnetException(XnetException.Kind.NETWORK, "No fue posible conectar con XNET: " + e.getMessage(), e);
    }
}
