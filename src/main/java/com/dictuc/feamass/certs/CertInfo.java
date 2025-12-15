package com.dictuc.feamass.certs;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

public final class CertInfo {
    public final String alias;
    public final X509Certificate cert; // un solo certificado
    public final KeyStore ks;

    public CertInfo(String alias, X509Certificate cert, KeyStore ks) {
        this.alias = alias;
        this.cert = cert;
        this.ks = ks;
    }

    @Override
    public String toString() {
        String dn = cert != null ? cert.getSubjectX500Principal().getName() : "(sin cert)";
        return alias + " — " + dn;
    }
}