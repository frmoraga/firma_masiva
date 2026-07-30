package com.dictuc.feamass.tsa;

import com.dictuc.feamass.Config;

/**
 * Configuracion del sellado de tiempo (RFC 3161), leida igual que el resto de la app desde
 * config.properties (ver Config: gana el archivo externo por sobre el empaquetado en el jar).
 */
public final class TsaConfig {

    /**
     * TSA por defecto: DigiCert. Es publico, gratuito, sin registro ni cuota declarada, responde
     * por HTTP plano (el protocolo RFC 3161 ya va firmado, no necesita TLS) y es el que usan por
     * defecto casi todas las herramientas de firma de escritorio, asi que su token lo reconoce
     * cualquier validador. Alternativa equivalente si algun dia deja de responder:
     * http://timestamp.sectigo.com (solo cambiar tsa.url en config.properties, sin recompilar).
     */
    public static final String URL_POR_DEFECTO = "http://timestamp.digicert.com";

    private TsaConfig() {}

    /** Permite apagar el sellado de tiempo por completo (p.ej. en una red sin salida a internet). */
    public static boolean habilitado() {
        return Boolean.parseBoolean(Config.get("tsa.enabled", "true").trim());
    }

    public static String url() {
        return Config.get("tsa.url", URL_POR_DEFECTO).trim();
    }

    /**
     * Timeouts cortos a proposito: el sello de tiempo es un "mejor esfuerzo" (si falla se firma
     * igual, sin el), asi que no puede dejar la UI colgada esperando a un TSA caido. En el peor
     * caso la firma se demora connect+read = 8 s extra.
     */
    public static int connectTimeoutMs() {
        return Config.getInt("tsa.connect.timeout.ms", 3000);
    }

    public static int readTimeoutMs() {
        return Config.getInt("tsa.read.timeout.ms", 5000);
    }
}
