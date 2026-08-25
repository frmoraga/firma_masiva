package com.dictuc.feamass.certs;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comparacion best-effort entre la identidad de la sesion XNET activa (RUT y correo) y lo que
 * pueda estar codificado en el Subject DN de un certificado. No hay un OID/formato unico entre
 * las distintas CAs chilenas -- verificado empiricamente contra un certificado real de E-Sign
 * S.A. (la CA que usa Dictuc, 2026-08-25): el RUT NO aparece en ningun lado del DN, pero el
 * correo del titular si, codificado en el atributo PKCS#9 emailAddress (OID
 * 1.2.840.113549.1.9.1), que Java no decodifica a texto por defecto y muestra como
 * "1.2.840.113549.1.9.1=#<hexDER>" -- de ahi emailsEn(), que lo decodifica a mano. Por eso esta
 * clase intenta AMBAS senales (RUT y correo) y basta con que UNA coincida.
 *
 * Sigue siendo una heuristica de texto, no una validacion criptografica formal -- ver
 * Hallazgo S-1 de AUDITORIA_SEGURIDAD_FASE2.md ("FEA MASS no ata el certificado de firma a la
 * identidad XNET del firmante"). Por eso solo sirve para ADVERTIR al operador antes de firmar
 * con un certificado que no parece ser el suyo, nunca para bloquear la firma: bloquear sin
 * certeza del formato real usado por cada CA rompería firmas legitimas cuyo RUT/correo no se
 * pudo extraer con estos patrones.
 */
public final class RutMatcher {

    // RUT chileno: 1-2 digitos, bloques de 3 (con o sin puntos), guion opcional, digito
    // verificador (0-9 o K). Busca cualquier ocurrencia dentro del Subject DN completo.
    private static final Pattern RUT_PATTERN =
            Pattern.compile("(\\d{1,2}\\.?\\d{3}\\.?\\d{3})-?([0-9kK])\\b");

    // Atributo PKCS#9 emailAddress sin decodificar que deja Java en el DN (RFC2253) cuando no
    // reconoce el OID: "1.2.840.113549.1.9.1=#<hexDER>". El valor es un IA5String DER (tag
    // 0x16, longitud en forma corta -- un correo cabe holgado en un byte de longitud).
    private static final Pattern EMAIL_OID_PATTERN =
            Pattern.compile("1\\.2\\.840\\.113549\\.1\\.9\\.1=#([0-9a-fA-F]+)");

    private RutMatcher() {
    }

    /** Normaliza un RUT a solo digitos + digito verificador en mayuscula, sin puntos ni guion. */
    public static String normalizar(String rut) {
        if (rut == null) {
            return "";
        }
        return rut.replaceAll("[^0-9kK]", "").toUpperCase();
    }

    /** Todos los RUT-like candidatos normalizados encontrados en el texto (ej. un Subject DN). */
    public static List<String> candidatosEn(String texto) {
        List<String> out = new ArrayList<>();
        if (texto == null) {
            return out;
        }
        Matcher m = RUT_PATTERN.matcher(texto);
        while (m.find()) {
            out.add(normalizar(m.group(1) + m.group(2)));
        }
        return out;
    }

    /**
     * Todos los correos que aparezcan como atributo PKCS#9 emailAddress en el texto (ej. un
     * Subject DN completo), en minuscula. Decodifica a mano el valor DER IA5String que Java
     * deja sin interpretar -- ver comentario de EMAIL_OID_PATTERN arriba. Ante cualquier
     * formato inesperado (longitud DER larga/multi-byte, valor corrupto) se ignora esa
     * ocurrencia en vez de lanzar: esto es una heuristica best-effort, un candidato que no se
     * pudo decodificar simplemente no cuenta como evidencia ni a favor ni en contra.
     */
    public static List<String> emailsEn(String texto) {
        List<String> out = new ArrayList<>();
        if (texto == null) {
            return out;
        }
        Matcher m = EMAIL_OID_PATTERN.matcher(texto);
        while (m.find()) {
            String hex = m.group(1);
            try {
                if (hex.length() < 4) {
                    continue;
                }
                int longitudDer = Integer.parseInt(hex.substring(2, 4), 16);
                if (longitudDer >= 0x80) {
                    continue; // forma larga de longitud DER, no esperado para un correo -- se ignora
                }
                int finValor = Math.min(hex.length(), 4 + longitudDer * 2);
                StringBuilder sb = new StringBuilder();
                for (int i = 4; i + 1 < finValor; i += 2) {
                    sb.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
                }
                if (!sb.isEmpty()) {
                    out.add(sb.toString().toLowerCase());
                }
            } catch (NumberFormatException ignore) {
                // hex malformado -- se ignora esta ocurrencia, ver docblock del metodo
            }
        }
        return out;
    }

    /**
     * true si el certificado tiene AL MENOS UN candidato (RUT o correo) en su Subject DN y
     * NINGUNO coincide con la sesion activa. false si algún candidato coincide, o si no se
     * pudo extraer ningun candidato de ningun tipo (sin base para advertir -- evita falsos
     * positivos por CAs que no incluyen ninguna de las dos señales de forma reconocible).
     */
    public static boolean pareceNoCoincidir(X509Certificate cert, String rutSesion, String correoSesion) {
        if (cert == null) {
            return false;
        }
        return pareceNoCoincidirDn(cert.getSubjectX500Principal().getName(), rutSesion, correoSesion);
    }

    /**
     * Misma logica que {@link #pareceNoCoincidir(X509Certificate, String, String)} pero
     * operando directo sobre el texto del DN -- separado para poder probarlo con DNs reales
     * capturados a mano, sin tener que construir un X509Certificate real (que requiere una CA).
     */
    static boolean pareceNoCoincidirDn(String dn, String rutSesion, String correoSesion) {
        List<String> candidatosRut = candidatosEn(dn);
        List<String> candidatosEmail = emailsEn(dn);
        if (candidatosRut.isEmpty() && candidatosEmail.isEmpty()) {
            return false;
        }

        String rutEsperado = normalizar(rutSesion);
        boolean rutCoincide = !rutEsperado.isBlank() && candidatosRut.contains(rutEsperado);

        String correoEsperado = correoSesion == null ? "" : correoSesion.trim().toLowerCase();
        boolean correoCoincide = !correoEsperado.isBlank() && candidatosEmail.contains(correoEsperado);

        return !rutCoincide && !correoCoincide;
    }
}
