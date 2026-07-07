package com.dictuc.feamass.certs;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

public final class CertInfo {
    public final String alias;
    public final X509Certificate cert; // un solo certificado
    public final KeyStore ks;
    public final CertValidation.Result validacion;

    public CertInfo(String alias, X509Certificate cert, KeyStore ks) {
        this.alias = alias;
        this.cert = cert;
        this.ks = ks;
        this.validacion = CertValidation.validar(cert);
    }

    public boolean esValido() {
        return validacion.valido;
    }

    @Override
    public String toString() {
        String dn = cert != null ? cert.getSubjectX500Principal().getName() : "(sin cert)";
        String base = alias + " — " + dn;
        return validacion.valido ? base : base + "  ⚠ NO UTILIZABLE: " + validacion.motivo;
    }
}