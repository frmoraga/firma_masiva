package com.dictuc.feamass;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

public final class WindowsStoreUtil {

    private WindowsStoreUtil() {}

    /** Wrapper pedido por TokenDetector: devuelve el PRIMER KeyEntry disponible en Windows-MY. */
    public static KeyAndCert findSigningKeyOnWindowsMY() throws Exception {
        Provider p = Security.getProvider("SunMSCAPI");
        if (p == null) {
            throw new IllegalStateException("SunMSCAPI no disponible. Incluye jdk.crypto.mscapi en el runtime.");
        }
        KeyStore ks = KeyStore.getInstance("Windows-MY", p);
        ks.load(null, null);

        String alias = firstKeyEntry(ks);
        if (alias == null) return null;

        PrivateKey key = (PrivateKey) ks.getKey(alias, null);
        if (key == null) return null;

        Certificate[] chain = ks.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
            Certificate single = ks.getCertificate(alias);
            chain = (single == null) ? new Certificate[0] : new Certificate[]{ single };
        }
        if (chain.length == 0 || !(chain[0] instanceof X509Certificate)) return null;

        X509Certificate signer = (X509Certificate) chain[0];
        return new KeyAndCert(key, chain, signer);
    }

    /** Carga un KeyAndCert desde el almacén Windows-MY por alias exacto. */
    public static KeyAndCert loadByAlias(String alias) throws Exception {
        Provider p = Security.getProvider("SunMSCAPI");
        if (p == null) {
            throw new IllegalStateException("SunMSCAPI no disponible. Incluye jdk.crypto.mscapi en el runtime.");
        }

        KeyStore ks = KeyStore.getInstance("Windows-MY", p);
        ks.load(null, null);

        if (!ks.isKeyEntry(alias)) {
            String found = findAliasIgnoreCase(ks, alias);
            if (found == null) throw new IllegalArgumentException("Alias no encontrado en Windows-MY: " + alias);
            alias = found;
        }

        PrivateKey key = (PrivateKey) ks.getKey(alias, null);
        if (key == null) throw new IllegalStateException("No se obtuvo privateKey para alias: " + alias);

        Certificate[] chain = ks.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
            Certificate single = ks.getCertificate(alias);
            chain = (single == null) ? new Certificate[0] : new Certificate[]{ single };
        }
        if (chain.length == 0 || !(chain[0] instanceof X509Certificate)) {
            throw new IllegalStateException("Cadena vacía o inválida en Windows-MY para alias: " + alias);
        }

        X509Certificate signer = (X509Certificate) chain[0];
        return new KeyAndCert(key, chain, signer);
    }

    private static String firstKeyEntry(KeyStore ks) throws Exception {
        for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
            String a = e.nextElement();
            if (ks.isKeyEntry(a)) return a;
        }
        return null;
    }

    private static String findAliasIgnoreCase(KeyStore ks, String wanted) throws Exception {
        for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
            String a = e.nextElement();
            if (ks.isKeyEntry(a) && a.equalsIgnoreCase(wanted)) return a;
        }
        return null;
    }
}