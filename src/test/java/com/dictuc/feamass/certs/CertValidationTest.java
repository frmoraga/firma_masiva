package com.dictuc.feamass.certs;

import org.bouncycastle.asn1.x509.KeyUsage;
import org.junit.jupiter.api.Test;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertValidationTest {

    @Test
    void certificadoVigenteConDigitalSignatureYCadenaDeConfianzaEsValido() throws Exception {
        TestCertFactory.TestCa ca = TestCertFactory.raizDeConfianza();
        var kc = ca.emitirVigente(KeyUsage.digitalSignature);

        CertValidation.Result r = CertValidation.validar(kc.chain(), ca.trustStore());

        assertTrue(r.valido);
        assertNull(r.motivo);
    }

    @Test
    void certificadoVigenteConNonRepudiationYCadenaDeConfianzaEsValido() throws Exception {
        TestCertFactory.TestCa ca = TestCertFactory.raizDeConfianza();
        var kc = ca.emitirVigente(KeyUsage.nonRepudiation);

        CertValidation.Result r = CertValidation.validar(kc.chain(), ca.trustStore());

        assertTrue(r.valido);
    }

    @Test
    void certificadoSinExtensionKeyUsageConCadenaDeConfianzaEsValido() throws Exception {
        TestCertFactory.TestCa ca = TestCertFactory.raizDeConfianza();
        Date ahora = new Date();
        Date en1Anio = new Date(ahora.getTime() + 365L * 24 * 3600 * 1000);
        var kc = ca.emitir(ahora, en1Anio, null);

        CertValidation.Result r = CertValidation.validar(kc.chain(), ca.trustStore());

        assertTrue(r.valido);
    }

    @Test
    void certificadoAutofirmadoSinCadenaDeConfianzaEsInvalido() throws Exception {
        // Regresion del hallazgo critico: antes de este fix, cualquier certificado autofirmado
        // (vigente, con el KeyUsage correcto) se aceptaba igual como valido para firmar.
        // Se inyecta un almacen de confianza con una raiz de prueba no relacionada (en vez de
        // usar el almacen "Windows-ROOT" real del sistema operativo, via la sobrecarga publica
        // de 1 argumento): el tipo de almacen "Windows-ROOT" solo existe en Windows, asi que en
        // un runner Linux (CI) ese camino ni siquiera llega a evaluar la cadena y termina en un
        // mensaje distinto ("no esta disponible el almacen..."), rompiendo esta asercion sin
        // que el fix este realmente mal. Con una raiz de prueba inyectada, CertPathValidator
        // corre de verdad y el resultado es el mismo en cualquier sistema operativo.
        TestCertFactory.TestCa raizNoRelacionada = TestCertFactory.raizDeConfianza();
        X509Certificate cert = TestCertFactory.vigenteConUso(KeyUsage.digitalSignature);

        CertValidation.Result r = CertValidation.validar(new Certificate[]{ cert }, raizNoRelacionada.trustStore());

        assertFalse(r.valido);
        assertTrue(r.motivo.contains("entidad certificadora de confianza"));
    }

    @Test
    void certificadoConKeyUsageSinFirmaEsInvalido() throws Exception {
        // keyEncipherment (bit 2) sin digitalSignature (bit 0) ni nonRepudiation (bit 1).
        // Se rechaza por KeyUsage antes de llegar a validar la cadena, asi que no hace
        // falta una cadena de confianza real para este caso.
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
        CertValidation.Result r = CertValidation.validar((X509Certificate) null);

        assertFalse(r.valido);
    }
}
