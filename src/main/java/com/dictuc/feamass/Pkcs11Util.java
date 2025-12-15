package com.dictuc.feamass;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

public final class Pkcs11Util {

    private Pkcs11Util() {}

    /**
     * Wrapper pedido por TokenDetector:
     *  - cfgPath: ruta al .cfg SunPKCS11 (con 'name=...' y 'library=...')
     *  - slotIndex: ignorado aquí (usualmente el .cfg ya fija el slot)
     *  - pin: PIN del token (si el driver pide UI, puede ser null)
     */
    public static KeyAndCert findOnPkcs11(String cfgPath, Integer slotIndex, char[] pin) throws Exception {
        Path cfg = Path.of(cfgPath);
        return load(cfg, null, pin); // alias null -> primer keyEntry
    }

    /**
     * Carga un KeyAndCert desde un token PKCS#11, usando archivo de configuración SunPKCS11.
     * @param pkcs11Cfg Ruta al .cfg (con 'name = ...' y 'library = ...')
     * @param alias alias a usar dentro del token (si es null, toma el primer KeyEntry)
     * @param pin pin si el proveedor lo requiere (si el middleware muestra UI, puede ser null)
     */
    public static KeyAndCert load(Path pkcs11Cfg, String alias, char[] pin) throws Exception {
        String cfgText = Files.readString(pkcs11Cfg, StandardCharsets.UTF_8);

        Provider base = Security.getProvider("SunPKCS11");
        if (base == null) {
            throw new IllegalStateException("SunPKCS11 no disponible. Asegura jdk.crypto.cryptoki en el runtime.");
        }

        Provider configured = base.configure(cfgText);

        boolean exists = false;
        for (Provider p : Security.getProviders()) {
            if (p.getName().equals(configured.getName())) { exists = true; break; }
        }
        if (!exists) Security.addProvider(configured);

        KeyStore ks = KeyStore.getInstance("PKCS11", configured);
        ks.load(null, pin); // muchos drivers muestran UI; si no, usa el pin aquí

        String useAlias = (alias != null) ? alias : firstKeyEntry(ks);
        if (useAlias == null) throw new IllegalStateException("No hay entradas de clave en el token PKCS#11.");
        if (!ks.isKeyEntry(useAlias)) throw new IllegalArgumentException("Alias no es una entrada de clave: " + useAlias);

        PrivateKey key = (PrivateKey) ks.getKey(useAlias, pin);
        if (key == null) throw new IllegalStateException("No se obtuvo privateKey para alias: " + useAlias);

        Certificate[] chain = ks.getCertificateChain(useAlias);
        if (chain == null || chain.length == 0) {
            Certificate single = ks.getCertificate(useAlias);
            chain = (single == null) ? new Certificate[0] : new Certificate[]{ single };
        }
        if (chain.length == 0 || !(chain[0] instanceof X509Certificate)) {
            throw new IllegalStateException("Cadena vacía o inválida para alias: " + useAlias);
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
}