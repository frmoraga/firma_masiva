package com.dictuc.feamass;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class Config {
    private static final Properties P = build();

    // Precedencia real (el directorio de trabajo actual gana, despues la carpeta del JAR,
    // despues los defaults empaquetados en el classpath): se arma con Properties encadenadas
    // via el constructor Properties(defaults), en vez de P.load() repetido sobre el mismo
    // objeto -- P.load() SOBRESCRIBE claves ya cargadas, asi que la ULTIMA llamada gana sin
    // importar el orden pensado; con el classpath cargando al final, terminaba siempre
    // ganando el sobre cualquier config externa real.
    private static Properties build() {
        Properties defaults = new Properties();
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) defaults.load(in);
        } catch (IOException ignore) {}

        Properties external = new Properties(defaults);
        Path jarDir = getJarDir();
        if (jarDir != null) loadIfExists(external, jarDir.resolve("config.properties"));
        loadIfExists(external, Paths.get("config.properties"));
        return external;
    }

    private static Path getJarDir() {
        try {
            var url = Config.class.getProtectionDomain().getCodeSource().getLocation();
            Path p = Paths.get(url.toURI());
            return Files.isDirectory(p) ? p : p.getParent();
        } catch (Exception e) {
            return null;
        }
    }

    private static void loadIfExists(Properties target, Path p) {
        if (p == null || !Files.exists(p)) return;
        try (InputStream in = Files.newInputStream(p)) {
            target.load(in);
        } catch (IOException ignore) {}
    }

    public static String get(String key, String defVal) {
        return P.getProperty(key, defVal);
    }

    public static Integer getInt(String key, Integer defVal) {
        String v = P.getProperty(key);
        if (v == null) return defVal;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defVal;
        }
    }
}