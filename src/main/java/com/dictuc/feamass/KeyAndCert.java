package com.dictuc.feamass;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

public final class KeyAndCert {
    private final PrivateKey privateKey;
    private final Certificate[] chain;
    private final X509Certificate signer;

    public KeyAndCert(PrivateKey privateKey, Certificate[] chain, X509Certificate signer) {
        this.privateKey = privateKey;
        this.chain = chain;
        this.signer = signer;
    }

    public PrivateKey privateKey() { return privateKey; }
    public Certificate[] chain() { return chain; }
    public X509Certificate cert() { return signer; }
}