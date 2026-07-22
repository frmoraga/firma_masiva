package com.dictuc.feamass.certs;

import org.bouncycastle.asn1.x509.KeyUsage;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertValidationTest {

    @Test
    void certificadoVigenteConDigitalSignatureEsValido() throws Exception {
        X509Certificate cert = TestCertFactory.vigenteConUso(KeyUsage.digitalSignature);

        CertValidation.Result r = CertValidation.validar(cert);

        assertTrue(r.valido);
        assertNull(r.motivo);
    }

    @Test
    void certificadoVigenteConNonRepudiationEsValido() throws Exception {
        X509Certificate cert = TestCertFactory.vigenteConUso(KeyUsage.nonRepudiation);

        CertValidation.Result r = CertValidation.validar(cert);

        assertTrue(r.valido);
    }

    @Test
    void certificadoSinExtensionKeyUsageEsValido() throws Exception {
        Date ahora = new Date();
        Date en1Anio = new Date(ahora.getTime() + 365L * 24 * 3600 * 1000);
        X509Certificate cert = TestCertFactory.firmante(ahora, en1Anio, null).cert();

        CertValidation.Result r = CertValidation.validar(cert);

        assertTrue(r.valido);
    }

    @Test
    void certificadoConKeyUsageSinFirmaEsInvalido() throws Exception {
        // keyEncipherment (bit 2) sin digitalSignature (bit 0) ni nonRepudiation (bit 1)
        X509Certificate cert = TestCertFactory.vigenteConUso(KeyUsage.keyEncipherment);

        CertValidation.Result r = CertValidation.validar(cert);

        assertFalse(r.valido);
        assertTrue(r.motivo.contains("firma digital"));
    }

    @Test
    void certificadoVencidoEsInvalido() throws Exception {
        Date haceDosAnios = new Date(System.currentTimeMillis() - 730L * 24 * 3600 * 1000);
        Date haceUnAnio = new Date(System.currentTimeMillis() - 365L * 24 * 3600 * 1000);
        X509Certificate cert = TestCertFactory.firmante(haceDosAnios, haceUnAnio, KeyUsage.digitalSignature).cert();

        CertValidation.Result r = CertValidation.validar(cert);

        assertFalse(r.valido);
        assertTrue(r.motivo.contains("venció"));
    }

    @Test
    void certificadoAunNoVigenteEsInvalido() throws Exception {
        Date enUnAnio = new Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000);
        Date enDosAnios = new Date(System.currentTimeMillis() + 730L * 24 * 3600 * 1000);
        X509Certificate cert = TestCertFactory.firmante(enUnAnio, enDosAnios, KeyUsage.digitalSignature).cert();

        CertValidation.Result r = CertValidation.validar(cert);

        assertFalse(r.valido);
        assertTrue(r.motivo.contains("aún no es válido"));
    }

    @Test
    void certificadoNuloEsInvalido() {
        CertValidation.Result r = CertValidation.validar(null);

        assertFalse(r.valido);
    }
}
