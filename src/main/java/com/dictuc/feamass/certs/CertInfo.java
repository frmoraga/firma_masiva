package com.dictuc.feamass.certs;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

public final class CertInfo {
    public final String alias;
    public final X509Certificate cert;   // certificado del firmante (primero de la cadena)
    public final Certificate[] chain;    // cadena completa tal como la entrega el almacén
    public final KeyStore ks;
    public final CertValidation.Result validacion;

    public CertInfo(String alias, Certificate[] chain, KeyStore ks) {
        this.alias = alias;
        this.chain = (chain == null) ? new Certificate[0] : chain;
        this.cert = CertValidation.firmante(this.chain);
        this.ks = ks;
        this.validacion = CertValidation.validar(this.chain);
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
