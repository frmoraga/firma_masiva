package com.dictuc.feamass.xnet;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba el contrato HTTP real de XnetClient (login, subir firmado) contra un servidor de
 * prueba embebido, sin depender del backend real de XNET. El puerto y host se fijan en
 * src/test/resources/config.properties (xnet.base.url), que Config carga del classpath.
 */
class XnetClientTest {

    private HttpServer server;
    private String ultimoCsrfRecibido;
    private String ultimoBodyFirmar;
    private String ultimoCsrfRechazar;
    private String ultimoBodyRechazar;

    @BeforeEach
    void iniciarServidor() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 28899), 0);

        server.createContext("/auth/login", exchange -> {
            String body = leerBody(exchange);
            if (body.contains("credenciales-malas@test.cl")) {
                responder(exchange, 401, "{\"error\":\"Credenciales invalidas.\"}");
                return;
            }
            String json = "{"
                    + "\"usuario\":{\"id\":7,\"rut\":\"11111111-1\",\"nombre\":\"Test Firmante\","
                    + "\"unidad_id\":3,\"habilitado_para_firmar\":true,\"email\":\"test@dictuc.cl\",\"activo\":true},"
                    + "\"permisos\":[\"informe.firmar\"],"
                    + "\"csrf_token\":\"csrf-abc-123\""
                    + "}";
            responder(exchange, 200, json);
        });

        server.createContext("/informes/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/firmar")) {
                ultimoCsrfRecibido = exchange.getRequestHeaders().getFirst("X-CSRF-Token");
                ultimoBodyFirmar = leerBody(exchange);
                long id = Long.parseLong(path.substring("/informes/".length(), path.length() - "/firmar".length()));
                responder(exchange, 200, "{\"id\":" + id + ",\"estado\":\"no_disponible\"}");
                return;
            }
            if (path.endsWith("/rechazar")) {
                ultimoCsrfRechazar = exchange.getRequestHeaders().getFirst("X-CSRF-Token");
                ultimoBodyRechazar = leerBody(exchange);
                long id = Long.parseLong(path.substring("/informes/".length(), path.length() - "/rechazar".length()));
                responder(exchange, 200, "{\"id\":" + id + ",\"estado\":\"rechazado\"}");
                return;
            }
            responder(exchange, 404, "{\"error\":\"No encontrado.\"}");
        });

        server.start();
    }

    @AfterEach
    void detenerServidor() {
        server.stop(0);
    }

    private static String leerBody(HttpExchange exchange) throws java.io.IOException {
        try (InputStream in = exchange.getRequestBody(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static void responder(HttpExchange exchange, int status, String jsonBody) throws java.io.IOException {
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    void loginConCredencialesValidasDevuelveUsuarioYCsrf() throws Exception {
        XnetClient client = new XnetClient();

        var resp = client.login("test@dictuc.cl", "cualquier-clave");

        assertEquals("Test Firmante", resp.usuario.nombre);
        assertEquals(3, resp.usuario.unidadId);
        assertTrue(resp.usuario.habilitadoParaFirmar);
        assertEquals("csrf-abc-123", resp.csrfToken);
        assertEquals(1, resp.permisos.size());
    }

    @Test
    void loginConCredencialesInvalidasLanzaXnetExceptionUnauthenticated() {
        XnetClient client = new XnetClient();

        XnetException ex = assertThrows(XnetException.class,
                () -> client.login("credenciales-malas@test.cl", "x"));

        assertEquals(XnetException.Kind.UNAUTHENTICATED, ex.kind());
        assertEquals(401, ex.httpStatus());
    }

    @Test
    void subirFirmadoEnviaMultipartYCsrfYParseaLaRespuesta() throws Exception {
        XnetClient client = new XnetClient();
        byte[] pdfFalso = "%PDF-1.4 contenido de prueba".getBytes(StandardCharsets.UTF_8);

        var resp = client.subirFirmado(555, "csrf-de-prueba", pdfFalso);

        assertEquals(555, resp.id);
        assertEquals("no_disponible", resp.estado);
        assertEquals("csrf-de-prueba", ultimoCsrfRecibido);
        assertTrue(ultimoBodyFirmar.contains("name=\"archivo_firmado\""));
        assertTrue(ultimoBodyFirmar.contains("filename=\"firmado.pdf\""));
        assertTrue(ultimoBodyFirmar.contains("Content-Type: application/pdf"));
        assertTrue(ultimoBodyFirmar.contains("contenido de prueba"));
    }

    @Test
    void rechazarEnviaJsonYCsrfYParseaLaRespuesta() throws Exception {
        XnetClient client = new XnetClient();

        var resp = client.rechazar(777, "csrf-de-prueba", "El PDF tiene un error de calculo.");

        assertEquals(777, resp.id);
        assertEquals("rechazado", resp.estado);
        assertEquals("csrf-de-prueba", ultimoCsrfRechazar);
        assertTrue(ultimoBodyRechazar.contains("\"justificacion\":\"El PDF tiene un error de calculo.\""));
    }
}
