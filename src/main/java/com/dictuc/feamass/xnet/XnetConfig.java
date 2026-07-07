package com.dictuc.feamass.xnet;

import com.dictuc.feamass.Config;

import java.net.URI;

public final class XnetConfig {

    private XnetConfig() {}

    public static String baseUrl() {
        String url = Config.get("xnet.base.url", "");
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static boolean isConfigured() {
        String url = baseUrl();
        return url != null && !url.isBlank();
    }

    /**
     * Si xnet.base.url usa http:// hacia un host que no es local, las credenciales de login
     * y el PDF viajarian sin cifrar. Devuelve un mensaje de advertencia en ese caso, o null
     * si la configuracion es segura (https://, o http:// solo contra localhost/127.0.0.1).
     */
    public static String advertenciaInseguridad() {
        String url = baseUrl();
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            boolean esLocal = host != null
                    && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1"));
            if ("http".equalsIgnoreCase(scheme) && !esLocal) {
                return "xnet.base.url esta configurado con http:// hacia un servidor remoto (" + host + ").\n"
                        + "Las credenciales y los informes viajarian sin cifrar por la red.\n\n"
                        + "Cambia xnet.base.url a https:// en config.properties antes de continuar.";
            }
        } catch (IllegalArgumentException ignore) {
            // URL invalida: se reporta igual al intentar conectar, no bloqueamos aqui.
        }
        return null;
    }

    public static int connectTimeoutMs() {
        return Config.getInt("xnet.connect.timeout.ms", 8000);
    }

    public static int readTimeoutMs() {
        return Config.getInt("xnet.read.timeout.ms", 30000);
    }

    public static int previewDpi() {
        return Config.getInt("xnet.preview.dpi", 110);
    }
}
