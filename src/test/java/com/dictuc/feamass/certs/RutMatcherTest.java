package com.dictuc.feamass.certs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * candidatosEn()/emailsEn() se verifican contra un DN real (no inventado): capturado de un
 * certificado E-Sign S.A. real leido del almacen Windows-MY el 2026-08-25 -- confirma que esa
 * CA (la que usa Dictuc) NO incluye el RUT en el DN, pero SI el correo del titular, codificado
 * en el atributo PKCS#9 emailAddress que Java no decodifica por defecto.
 */
class RutMatcherTest {

    private static final String DN_ESIGN_REAL =
            "1.2.840.113549.1.9.1=#1611666d6f72616761406469637475632e636c,"
                    + "CN=Francisco Andrés Moraga Villanueva,"
                    + "OU=Terminos de uso en www.esign-la.com/acuerdoterceros,"
                    + "O=E-Sign S.A.,C=CL";

    @Test
    void emailsEn_decodifica_el_correo_embebido_en_un_dn_real_de_esign() {
        assertEquals(List.of("fmoraga@dictuc.cl"), RutMatcher.emailsEn(DN_ESIGN_REAL));
    }

    @Test
    void candidatosEn_no_encuentra_ningun_rut_en_un_dn_real_de_esign() {
        // Confirma empiricamente el hallazgo: esta CA no incluye el RUT en el Subject DN.
        assertTrue(RutMatcher.candidatosEn(DN_ESIGN_REAL).isEmpty());
    }

    @Test
    void candidatosEn_extrae_un_rut_con_puntos_y_guion() {
        assertEquals(List.of("123456785"), RutMatcher.candidatosEn("CN=Persona Test, SERIALNUMBER=12.345.678-5, C=CL"));
    }

    @Test
    void normalizar_ignora_puntos_guion_y_mayusculas() {
        assertEquals("123456785", RutMatcher.normalizar("12.345.678-5"));
        assertEquals("12345678K", RutMatcher.normalizar("12345678-k"));
    }

    @Test
    void emailsEn_sin_el_oid_no_encuentra_nada() {
        assertTrue(RutMatcher.emailsEn("CN=Alguien, O=Otra CA, C=CL").isEmpty());
    }

    @Test
    void pareceNoCoincidirDn_usa_el_correo_cuando_el_dn_no_tiene_rut() {
        assertFalse(RutMatcher.pareceNoCoincidirDn(DN_ESIGN_REAL, "11.111.111-1", "fmoraga@dictuc.cl"));
    }

    @Test
    void pareceNoCoincidirDn_advierte_si_el_correo_del_dn_es_de_otra_persona() {
        assertTrue(RutMatcher.pareceNoCoincidirDn(DN_ESIGN_REAL, "11.111.111-1", "otra.persona@dictuc.cl"));
    }

    @Test
    void pareceNoCoincidirDn_no_advierte_si_no_hay_ningun_candidato_en_el_dn() {
        assertFalse(RutMatcher.pareceNoCoincidirDn("CN=Alguien, O=Otra CA, C=CL", "11.111.111-1", "quien.sea@dictuc.cl"));
    }

    @Test
    void pareceNoCoincidirDn_advierte_si_hay_rut_pero_no_coincide() {
        assertTrue(RutMatcher.pareceNoCoincidirDn("CN=Persona Test, SERIALNUMBER=12.345.678-5, C=CL", "11.111.111-1", null));
    }

    @Test
    void pareceNoCoincidirDn_no_advierte_si_el_rut_coincide() {
        assertFalse(RutMatcher.pareceNoCoincidirDn("CN=Persona Test, SERIALNUMBER=12.345.678-5, C=CL", "12.345.678-5", null));
    }
}
