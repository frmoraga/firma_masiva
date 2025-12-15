package com.dictuc.feamass.certs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

public final class CertLoader {

    private CertLoader() {}

    /** Lista todos los certificados con clave privada disponibles. */
    public static List<CertInfo> listAll(boolean includeWindowsMY, boolean includePkcs11, Path pkcs11Cfg) throws Exception {
        List<CertInfo> out = new ArrayList<>();
        if (includeWindowsMY) out.addAll(fromWindowsMY());
        if (includePkcs11 && pkcs11Cfg != null) out.addAll(fromPkcs11(pkcs11Cfg));
        return out;
    }

    /** Certificados del almacén Windows-MY (requiere jdk.crypto.mscapi en el runtime). */
    public static List<CertInfo> fromWindowsMY() throws Exception {
        Provider p = Security.getProvider("SunMSCAPI");
        if (p == null) {
            throw new IllegalStateException("Provider SunMSCAPI no disponible. Incluye jdk.crypto.mscapi en tu runtime.");
        }
        KeyStore ks = KeyStore.getInstance("Windows-MY", p);
        ks.load(null, null);

        List<CertInfo> list = new ArrayList<>();
        for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
            String alias = e.nextElement();
            if (!ks.isKeyEntry(alias)) continue;

            // Un solo certificado (no la cadena)
            Certificate c = ks.getCertificate(alias);
            if (c instanceof X509Certificate) {
                list.add(new CertInfo(alias, (X509Certificate) c, ks));
            }
        }
        return list;
    }

    /** Certificados desde un token PKCS#11 (requiere jdk.crypto.cryptoki en el runtime). */
    public static List<CertInfo> fromPkcs11(Path pkcs11Cfg) throws Exception {
        String cfgText = readUtf8(pkcs11Cfg);

        Provider base = Security.getProvider("SunPKCS11");
        if (base == null) {
            throw new IllegalStateException("Provider SunPKCS11 no disponible. Incluye jdk.crypto.cryptoki en tu runtime.");
        }

        // Configurar proveedor desde el texto del .cfg (API pública; sin sun.*)
        Provider configured = base.configure(cfgText);

        // Añadir si no existe ya uno con ese nombre
        boolean exists = Arrays.stream(Security.getProviders())
                .anyMatch(p -> p.getName().equals(configured.getName()));
        if (!exists) Security.addProvider(configured);

        KeyStore ks = KeyStore.getInstance("PKCS11", configured);
        // Si el middleware muestra UI de PIN, deja null:
        ks.load(null, null);

        List<CertInfo> list = new ArrayList<>();
        for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
            String alias = e.nextElement();
            if (!ks.isKeyEntry(alias)) continue;

            // Un solo certificado (no la cadena)
            Certificate c = ks.getCertificate(alias);
            if (c instanceof X509Certificate) {
                list.add(new CertInfo(alias, (X509Certificate) c, ks));
            }
        }
        return list;
    }

    /** Convierte CertInfo en KeyAndCert usable para firmar. */
    public static com.dictuc.feamass.KeyAndCert toKeyAndCert(CertInfo ci, char[] pinIfNeeded) throws Exception {
        Key key = ci.ks.getKey(ci.alias, pinIfNeeded);
        if (!(key instanceof PrivateKey)) {
            throw new IllegalStateException("No se pudo obtener privateKey para alias: " + ci.alias);
        }

        Certificate[] chain = ci.ks.getCertificateChain(ci.alias);
        if (chain == null || chain.length == 0) {
            Certificate single = ci.ks.getCertificate(ci.alias);
            chain = (single == null) ? new Certificate[0] : new Certificate[]{ single };
        }
        if (chain.length == 0 || !(chain[0] instanceof X509Certificate)) {
            throw new IllegalStateException("Cadena de certificados vacía o inválida para alias: " + ci.alias);
        }

        // Firmante = primer elemento de la cadena
        X509Certificate signer = (X509Certificate) chain[0];

        return new com.dictuc.feamass.KeyAndCert((PrivateKey) key, chain, signer);
    }

    private static String readUtf8(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}