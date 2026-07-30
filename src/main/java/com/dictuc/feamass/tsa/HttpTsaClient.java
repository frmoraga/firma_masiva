package com.dictuc.feamass.tsa;

import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * Cliente RFC 3161 sobre HTTP: pide a un TSA publico que selle el valor de la firma.
 *
 * El TSA solo recibe el HASH SHA-256 del valor de firma, nunca el PDF ni ningun dato del
 * informe: no hay filtracion de contenido hacia el servicio externo.
 */
public final class HttpTsaClient implements TsaClient {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final URI uri;
    private final HttpClient http;
    private final Duration readTimeout;

    public HttpTsaClient(String url, int connectTimeoutMs, int readTimeoutMs) {
        this.uri = URI.create(url);
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    private static HttpTsaClient compartido;

    /**
     * Instancia compartida configurada desde config.properties. Es unica a proposito: cada
     * HttpClient levanta su propio hilo selector, y una firma masiva crearia uno por documento.
     * Se construye a demanda (no en un static final) para que una tsa.url mal escrita se pueda
     * atrapar como excepcion normal en quien llama, en vez de reventar la clase entera.
     */
    public static synchronized HttpTsaClient desdeConfig() {
        if (compartido == null) {
            compartido = new HttpTsaClient(
                    TsaConfig.url(), TsaConfig.connectTimeoutMs(), TsaConfig.readTimeoutMs());
        }
        return compartido;
    }

    @Override
    public byte[] obtenerToken(byte[] bytesDeFirma) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytesDeFirma);

        TimeStampRequestGenerator gen = new TimeStampRequestGenerator();
        // Pedimos el certificado del TSA dentro del token: sin el, quien valide despues no puede
        // verificar la firma del sello sin salir a buscarlo a la red.
        gen.setCertReq(true);
        BigInteger nonce = new BigInteger(64, RANDOM);
        TimeStampRequest peticion = gen.generate(TSPAlgorithms.SHA256, digest, nonce);

        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(readTimeout)
                .header("Content-Type", "application/timestamp-query")
                .POST(HttpRequest.BodyPublishers.ofByteArray(peticion.getEncoded()))
                .build();

        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IOException("El TSA respondio HTTP " + resp.statusCode());
        }

        TimeStampResponse respuesta = new TimeStampResponse(resp.body());
        // validate() comprueba status, nonce y que el imprint sellado sea el que enviamos:
        // sin esto un intermediario podria devolver el sello de OTRO documento.
        respuesta.validate(peticion);

        TimeStampToken token = respuesta.getTimeStampToken();
        if (token == null) {
            throw new IOException("El TSA rechazo la peticion (status " + respuesta.getStatus()
                    + (respuesta.getFailInfo() != null ? ", failInfo " + respuesta.getFailInfo().intValue() : "") + ")");
        }
        return token.getEncoded();
    }
}
