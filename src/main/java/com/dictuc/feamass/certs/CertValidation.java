package com.dictuc.feamass.certs;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.PKIXParameters;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida que un certificado sea apto para firmar. Se comprueban, en este orden:
 * <ol>
 *   <li>vigencia (notBefore / notAfter);</li>
 *   <li>uso de clave habilitado para firma (KeyUsage);</li>
 *   <li>cadena de confianza: que el certificado encadene contra una raíz instalada
 *       en el almacén de entidades de confianza de Windows ({@code Windows-ROOT}).</li>
 * </ol>
 * Sin la comprobación (3) cualquier certificado autofirmado generado por el propio usuario
 * sería aceptado como válido para firmar.
 */
public final class CertValidation {

    private static final SimpleDateFormat FECHA_FMT = new SimpleDateFormat("dd-MM-yyyy");

    /** Almacén de raíces de confianza del sistema operativo (SunMSCAPI). */
    private static final String TRUST_STORE_TYPE = "Windows-ROOT";

    /** Se cachea porque {@code listAll} valida un certificado por alias y abrir el almacén cuesta. */
    private static volatile KeyStore trustStoreCache;

    private CertValidation() {}

    public static final class Result {
        public final boolean valido;
        public final String motivo; // null si valido

        private Result(boolean valido, String motivo) {
            this.valido = valido;
            this.motivo = motivo;
        }
    }

    /**
     * Valida un certificado suelto. La cadena de confianza se resuelve igualmente contra
     * {@code Windows-ROOT}: el emisor debe estar instalado como raíz de confianza.
     */
    public static Result validar(X509Certificate cert) {
        return validar(cert == null ? null : new Certificate[]{ cert });
    }

    /** Valida la cadena completa entregada por el almacén (el firmante debe ir primero). */
    public static Result validar(Certificate[] chain) {
        return validar(chain, null);
    }

    /**
     * Igual que {@link #validar(Certificate[])} pero permitiendo inyectar el almacén de raíces
     * de confianza. Solo para pruebas: en producción se usa siempre {@code Windows-ROOT}.
     *
     * @param trustStoreOverride almacén con las raíces de confianza; {@code null} usa el del sistema.
     */
    static Result validar(Certificate[] chain, KeyStore trustStoreOverride) {
        X509Certificate cert = firmante(chain);
        if (cert == null) {
            return new Result(false, "No se encontró el certificado.");
        }
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException e) {
            return new Result(false, "El certificado venció el " + FECHA_FMT.format(cert.getNotAfter()) + ".");
        } catch (CertificateNotYetValidException e) {
            return new Result(false, "El certificado aún no es válido (vigente desde el "
                    + FECHA_FMT.format(cert.getNotBefore()) + ").");
        }

        // KeyUsage: índice 0 = digitalSignature, índice 1 = nonRepudiation/contentCommitment.
        // Si la extensión viene presente y ninguno de los dos bits está activo, el certificado
        // no está habilitado para firmar (aunque tenga clave privada disponible).
        boolean[] usage = cert.getKeyUsage();
        if (usage != null && usage.length > 1 && !usage[0] && !usage[1]) {
            return new Result(false, "El certificado no tiene habilitado el uso de firma digital.");
        }

        return validarCadena(chain, trustStoreOverride);
    }

    /** Certificado del firmante: primer X509 de la cadena. */
    static X509Certificate firmante(Certificate[] chain) {
        if (chain == null || chain.length == 0) return null;
        return (chain[0] instanceof X509Certificate) ? (X509Certificate) chain[0] : null;
    }

    /**
     * Comprueba con {@code CertPathValidator} (algoritmo PKIX) que la cadena termine en una
     * raíz de confianza. Si no se puede abrir el almacén de raíces se falla cerrado: sin
     * poder verificar la confianza, el certificado no se considera utilizable.
     */
    private static Result validarCadena(Certificate[] chain, KeyStore trustStoreOverride) {
        KeyStore trustStore;
        try {
            trustStore = (trustStoreOverride != null) ? trustStoreOverride : trustStoreDelSistema();
        } catch (Exception e) {
            return new Result(false, "No se pudo verificar la cadena de confianza del certificado: "
                    + "no está disponible el almacén de entidades de confianza de Windows.");
        }

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            CertPath certPath = cf.generateCertPath(rutaDeCertificacion(chain));

            PKIXParameters params = new PKIXParameters(trustStore);
            // La revocación (OCSP/CRL) queda fuera de este cambio: exige acceso de red y una
            // política de fallo definida. Aquí solo se comprueba el encadenamiento a una raíz
            // de confianza, que es lo que faltaba por completo.
            params.setRevocationEnabled(false);

            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            validator.validate(certPath, params);
            return new Result(true, null);
        } catch (CertPathValidatorException e) {
            return new Result(false, "El certificado no fue emitido por una entidad certificadora de confianza.");
        } catch (InvalidAlgorithmParameterException e) {
            // Ocurre, por ejemplo, si el almacén de raíces no tiene ninguna entrada de confianza.
            return new Result(false, "No se pudo verificar la cadena de confianza del certificado: "
                    + "el almacén de entidades de confianza está vacío o no es válido.");
        } catch (Exception e) {
            return new Result(false, "No se pudo verificar la cadena de confianza del certificado.");
        }
    }

    /**
     * Convierte la cadena del almacén en la ruta de certificación que espera PKIX: solo
     * certificados X509 y sin la raíz autofirmada final (el ancla de confianza no forma
     * parte de la ruta, se toma del almacén de raíces).
     */
    private static List<X509Certificate> rutaDeCertificacion(Certificate[] chain) {
        List<X509Certificate> ruta = new ArrayList<>();
        for (Certificate c : chain) {
            if (c instanceof X509Certificate) ruta.add((X509Certificate) c);
        }
        if (ruta.size() > 1) {
            X509Certificate ultimo = ruta.get(ruta.size() - 1);
            boolean autofirmado = ultimo.getSubjectX500Principal().equals(ultimo.getIssuerX500Principal());
            if (autofirmado) ruta.remove(ruta.size() - 1);
        }
        return ruta;
    }

    private static KeyStore trustStoreDelSistema() throws Exception {
        KeyStore cached = trustStoreCache;
        if (cached != null) return cached;
        synchronized (CertValidation.class) {
            if (trustStoreCache == null) {
                KeyStore ks = KeyStore.getInstance(TRUST_STORE_TYPE);
                ks.load(null, null);
                trustStoreCache = ks;
            }
            return trustStoreCache;
        }
    }
}
