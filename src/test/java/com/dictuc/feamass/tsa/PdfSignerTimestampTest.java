package com.dictuc.feamass.tsa;

import com.dictuc.feamass.KeyAndCert;
import com.dictuc.feamass.PdfSigner;
import com.dictuc.feamass.certs.TestCertFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba el sellado de tiempo RFC 3161 de PdfSigner contra un TSA de prueba levantado en
 * localhost (mismo enfoque que XnetClientTest con el backend): asi se ejercita el camino real
 * completo -armado de la peticion, POST, validacion de la respuesta y adjuntado del token como
 * atributo no firmado- sin depender de que un TSA publico este disponible al correr la suite.
 *
 * Tambien cubre lo mas importante para la operacion diaria: que un TSA caido o que responde con
 * error NO impida firmar.
 */
class PdfSignerTimestampTest {

    private static final int PUERTO_TSA = 28901;
    private static final String URL_TSA = "http://localhost:" + PUERTO_TSA + "/tsa";
    private static final String URL_TSA_CON_ERROR = "http://localhost:" + PUERTO_TSA + "/tsa-roto";

    private HttpServer servidor;
    private KeyPair clavesTsa;
    private X509Certificate certTsa;
    private long serie = 1;

    @BeforeEach
    void levantarTsaDePrueba() throws Exception {
        clavesTsa = generarClaves();
        certTsa = certificadoDeTsa(clavesTsa);

        servidor = HttpServer.create(new InetSocketAddress("localhost", PUERTO_TSA), 0);
        servidor.createContext("/tsa", intercambio -> {
            try {
                TimeStampRequest peticion = new TimeStampRequest(leerBody(intercambio));
                TimeStampResponse respuesta = generadorDeRespuestas()
                        .generate(peticion, BigInteger.valueOf(serie++), new Date());
                byte[] cuerpo = respuesta.getEncoded();
                intercambio.getResponseHeaders().add("Content-Type", "application/timestamp-reply");
                intercambio.sendResponseHeaders(200, cuerpo.length);
                try (OutputStream os = intercambio.getResponseBody()) {
                    os.write(cuerpo);
                }
            } catch (Exception e) {
                intercambio.sendResponseHeaders(500, -1);
                intercambio.close();
            }
        });
        // TSA que existe pero contesta con error: cubre el caso "servicio degradado", distinto
        // de "no hay red" (que se prueba apuntando a un puerto cerrado).
        servidor.createContext("/tsa-roto", intercambio -> {
            intercambio.sendResponseHeaders(503, -1);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void detenerTsaDePrueba() {
        servidor.stop(0);
    }

    @Test
    void conTsaDisponibleElSignerInfoQuedaConElAtributoDeSelloDeTiempo() throws Exception {
        KeyAndCert kc = firmanteDePrueba();

        byte[] firmado = PdfSigner.signToBytes(pdfDeUnaPagina(), kc, null,
                new HttpTsaClient(URL_TSA, 2000, 5000));

        SignerInformation firmante = signerInfoDe(firmado);
        AttributeTable noFirmados = firmante.getUnsignedAttributes();
        assertNotNull(noFirmados, "el SignerInfo deberia traer atributos no firmados");

        Attribute sello = noFirmados.get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
        assertNotNull(sello, "falta el atributo id-aa-signatureTimeStampToken (1.2.840.113549.1.9.16.2.14)");
        assertEquals("1.2.840.113549.1.9.16.2.14",
                PKCSObjectIdentifiers.id_aa_signatureTimeStampToken.getId());
        assertEquals(1, sello.getAttrValues().size());

        // El token sella exactamente el valor de firma de ESTE SignerInfo, no cualquier cosa.
        TimeStampToken token = new TimeStampToken(
                ContentInfo.getInstance(sello.getAttrValues().getObjectAt(0)));
        assertArrayEquals(
                MessageDigest.getInstance("SHA-256").digest(firmante.getSignature()),
                token.getTimeStampInfo().getMessageImprintDigest());

        // Y el token viene firmado de verdad por el TSA (no es un blob cualquiera adjuntado).
        assertTrue(token.isSignatureValid(new JcaSimpleSignerInfoVerifierBuilder().build(certTsa)));

        // Adjuntar el atributo no debe invalidar la firma del PDF.
        assertTrue(firmaDelPdfEsValida(firmado, kc.cert()));
    }

    @Test
    void siElTsaEsInalcanzableSeFirmaIgualPeroSinSelloDeTiempo() throws Exception {
        KeyAndCert kc = firmanteDePrueba();

        byte[] firmado = PdfSigner.signToBytes(pdfDeUnaPagina(), kc, null,
                new HttpTsaClient("http://localhost:" + puertoCerrado() + "/tsa", 500, 500));

        SignerInformation firmante = signerInfoDe(firmado);
        assertNull(atributoDeSelloDeTiempo(firmante), "sin TSA no deberia quedar sello de tiempo");
        assertTrue(firmaDelPdfEsValida(firmado, kc.cert()),
                "la firma debe seguir siendo valida aunque no haya sello de tiempo");

        try (PDDocument doc = PDDocument.load(firmado)) {
            assertEquals(1, doc.getSignatureDictionaries().size());
        }
    }

    @Test
    void siElTsaRespondeConErrorHttpSeFirmaIgualPeroSinSelloDeTiempo() throws Exception {
        KeyAndCert kc = firmanteDePrueba();

        byte[] firmado = PdfSigner.signToBytes(pdfDeUnaPagina(), kc, null,
                new HttpTsaClient(URL_TSA_CON_ERROR, 2000, 5000));

        SignerInformation firmante = signerInfoDe(firmado);
        assertNull(atributoDeSelloDeTiempo(firmante), "una respuesta 503 no debe dejar sello a medias");
        assertTrue(firmaDelPdfEsValida(firmado, kc.cert()));
    }

    /* ====================== helpers ====================== */

    private static Attribute atributoDeSelloDeTiempo(SignerInformation firmante) {
        AttributeTable noFirmados = firmante.getUnsignedAttributes();
        return noFirmados == null ? null : noFirmados.get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
    }

    /** SignerInfo del PKCS#7 embebido en el PDF firmado. */
    private static SignerInformation signerInfoDe(byte[] pdfFirmado) throws Exception {
        try (PDDocument doc = PDDocument.load(pdfFirmado)) {
            List<PDSignature> firmas = doc.getSignatureDictionaries();
            assertEquals(1, firmas.size());
            CMSSignedData cms = new CMSSignedData(firmas.get(0).getContents());
            return cms.getSignerInfos().getSigners().iterator().next();
        }
    }

    /** Verificacion criptografica real: el CMS detached contra los bytes cubiertos por ByteRange. */
    private static boolean firmaDelPdfEsValida(byte[] pdfFirmado, X509Certificate cert) throws Exception {
        try (PDDocument doc = PDDocument.load(pdfFirmado)) {
            PDSignature sig = doc.getSignatureDictionaries().get(0);
            byte[] contenidoFirmado = sig.getSignedContent(pdfFirmado);
            CMSSignedData cms = new CMSSignedData(
                    new CMSProcessableByteArray(contenidoFirmado), sig.getContents());
            SignerInformation firmante = cms.getSignerInfos().getSigners().iterator().next();
            return firmante.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert));
        }
    }

    private TimeStampResponseGenerator generadorDeRespuestas() throws Exception {
        SignerInfoGenerator sig = new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().build())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(clavesTsa.getPrivate()), certTsa);

        TimeStampTokenGenerator tokenGen = new TimeStampTokenGenerator(
                sig,
                new JcaDigestCalculatorProviderBuilder().build()
                        .get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)),
                // Politica ficticia: el OID solo identifica al TSA de prueba.
                new org.bouncycastle.asn1.ASN1ObjectIdentifier("1.3.6.1.4.1.99999.1"));
        tokenGen.addCertificates(new JcaCertStore(List.of(certTsa)));
        return new TimeStampResponseGenerator(tokenGen, TSPAlgorithms.ALLOWED);
    }

    /** Certificado de TSA: RFC 3161 exige ExtendedKeyUsage=timeStamping y marcado como critico. */
    private static X509Certificate certificadoDeTsa(KeyPair claves) throws Exception {
        X500Name dn = new X500Name("CN=TSA de prueba, O=Dictuc");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                dn, BigInteger.valueOf(System.nanoTime()),
                new Date(System.currentTimeMillis() - 3600_000L),
                new Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000),
                dn, claves.getPublic());
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(claves.getPrivate())));
    }

    private static KeyAndCert firmanteDePrueba() throws Exception {
        return TestCertFactory.firmante(
                new Date(), new Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000),
                KeyUsage.digitalSignature);
    }

    private static KeyPair generarClaves() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    /** Puerto libre (se abre y se cierra al instante) para simular "no hay TSA escuchando". */
    private static int puertoCerrado() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static byte[] pdfDeUnaPagina() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] leerBody(HttpExchange intercambio) throws java.io.IOException {
        try (InputStream in = intercambio.getRequestBody(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }
}
