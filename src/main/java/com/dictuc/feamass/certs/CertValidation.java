package com.dictuc.feamass.certs;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;

/** Valida que un certificado sea apto para firmar: vigente y con uso de firma digital. */
public final class CertValidation {

    private static final SimpleDateFormat FECHA_FMT = new SimpleDateFormat("dd-MM-yyyy");

    private CertValidation() {}

    public static final class Result {
        public final boolean valido;
        public final String motivo; // null si valido

        private Result(boolean valido, String motivo) {
            this.valido = valido;
            this.motivo = motivo;
        }
    }

    public static Result validar(X509Certificate cert) {
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

        return new Result(true, null);
    }
}
