package com.dictuc.feamass.certs;

import com.dictuc.feamass.KeyAndCert;
import org.bouncycastle.asn1.x500.X500Name;
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
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

/** Genera certificados autofirmados en memoria para probar CertValidation y PdfSigner sin depender de hardware. */
public final class TestCertFactory {

    private TestCertFactory() {}

    public static KeyAndCert firmante(Date notBefore, Date notAfter, Integer keyUsageBits) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X500Name subject = new X500Name("CN=Test Firmante, O=Dictuc");
        BigInteger serial = BigInteger.valueOf(System.nanoTime());

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, kp.getPublic());

        if (keyUsageBits != null) {
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsageBits));
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

        return new KeyAndCert(kp.getPrivate(), new Certificate[]{cert}, cert);
    }

    public static X509Certificate vigenteConUso(int keyUsageBits) throws Exception {
        Date ahora = new Date();
        Date en1Anio = new Date(ahora.getTime() + 365L * 24 * 3600 * 1000);
        return firmante(ahora, en1Anio, keyUsageBits).cert();
    }
}
