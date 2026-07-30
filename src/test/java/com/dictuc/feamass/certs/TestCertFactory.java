package com.dictuc.feamass.certs;

import com.dictuc.feamass.KeyAndCert;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Genera certificados en memoria para probar CertValidation y PdfSigner sin depender de hardware.
 * Permite dos escenarios:
 * <ul>
 *   <li>{@link #firmante(Date, Date, Integer)}: certificado autofirmado suelto (sin cadena de confianza);</li>
 *   <li>{@link #raizDeConfianza()} + {@link TestCa#emitir(Date, Date, Integer)}: raíz de prueba y
 *       certificado hoja emitido por ella, para ejercitar la validación PKIX real.</li>
 * </ul>
 */
public final class TestCertFactory {

    private TestCertFactory() {}

    /** Certificado autofirmado: NO encadena contra ninguna raíz de confianza. */
    public static KeyAndCert firmante(Date notBefore, Date notAfter, Integer keyUsageBits) throws Exception {
        KeyPair kp = generarClaves();
        X500Name subject = new X500Name("CN=Test Firmante, O=Dictuc");
        X509Certificate cert = construir(subject, kp.getPublic(), subject, kp.getPrivate(),
                notBefore, notAfter, keyUsageBits, false);
        return new KeyAndCert(kp.getPrivate(), new Certificate[]{ cert }, cert);
    }

    /** Certificado autofirmado vigente con el KeyUsage indicado. */
    public static X509Certificate vigenteConUso(int keyUsageBits) throws Exception {
        return firmante(ahora(), enUnAnio(), keyUsageBits).cert();
    }

    /** Crea una autoridad certificadora de prueba (raíz autofirmada con CA=true y keyCertSign). */
    public static TestCa raizDeConfianza() throws Exception {
        KeyPair kp = generarClaves();
        X500Name subject = new X500Name("CN=Test Root CA, O=Dictuc");
        X509Certificate cert = construir(subject, kp.getPublic(), subject, kp.getPrivate(),
                new Date(System.currentTimeMillis() - 24L * 3600 * 1000),
                new Date(System.currentTimeMillis() + 3650L * 24 * 3600 * 1000),
                KeyUsage.keyCertSign | KeyUsage.cRLSign, true);
        return new TestCa(cert, subject, kp.getPrivate());
    }

    /** Autoridad de prueba: emite certificados hoja y expone su almacén de raíces de confianza. */
    public static final class TestCa {
        private final X509Certificate cert;
        private final X500Name dn;
        private final PrivateKey key;

        private TestCa(X509Certificate cert, X500Name dn, PrivateKey key) {
            this.cert = cert;
            this.dn = dn;
            this.key = key;
        }

        public X509Certificate cert() { return cert; }

        /** Almacén con esta raíz como única entidad de confianza, para inyectar en CertValidation. */
        public KeyStore trustStore() throws Exception {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(null, null);
            ks.setCertificateEntry("test-root-ca", cert);
            return ks;
        }

        /** Certificado hoja firmado por esta CA. La cadena devuelta es [hoja, raíz]. */
        public KeyAndCert emitir(Date notBefore, Date notAfter, Integer keyUsageBits) throws Exception {
            KeyPair kp = generarClaves();
            X500Name subject = new X500Name("CN=Test Firmante, O=Dictuc");
            // Reutiliza el X500Name original (no lo reconstruye desde el X509Certificate ya
            // convertido): BouncyCastle y Java ordenan los RDN de un DN en sentido opuesto por
            // defecto, asi que ida y vuelta por String rompe el encadenamiento (issuer del hoja
            // termina sin igualar el subject de la raiz).
            X509Certificate hoja = construir(subject, kp.getPublic(), dn, key,
                    notBefore, notAfter, keyUsageBits, false);
            return new KeyAndCert(kp.getPrivate(), new Certificate[]{ hoja, cert }, hoja);
        }

        /** Certificado hoja vigente con el KeyUsage indicado, firmado por esta CA. */
        public KeyAndCert emitirVigente(Integer keyUsageBits) throws Exception {
            return emitir(ahora(), enUnAnio(), keyUsageBits);
        }
    }

    private static X509Certificate construir(X500Name subject, PublicKey subjectKey,
                                             X500Name issuer, PrivateKey issuerKey,
                                             Date notBefore, Date notAfter,
                                             Integer keyUsageBits, boolean ca) throws Exception {
        BigInteger serial = BigInteger.valueOf(System.nanoTime());
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, subjectKey);

        if (ca) {
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        }
        if (keyUsageBits != null) {
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsageBits));
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey);
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static KeyPair generarClaves() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    private static Date ahora() {
        return new Date();
    }

    private static Date enUnAnio() {
        return new Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000);
    }
}
