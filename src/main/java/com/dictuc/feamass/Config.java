package com.dictuc.feamass;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class Config {
    private static final Properties P = new Properties();

    // Carga config.properties desde:
    // 1) la carpeta del JAR (si aplica)
    // 2) el directorio de trabajo actual
    // 3) el classpath (resources)
    static {
        Path jarDir = getJarDir();
        if (jarDir != null) loadIfExists(jarDir.resolve("config.properties"));
        loadIfExists(Paths.get("config.properties"));
        // desde resources en el classpath
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) P.load(in);
        } catch (IOException ignore) {}
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

    private static void loadIfExists(Path p) {
        if (p == null || !Files.exists(p)) return;
        try (InputStream in = Files.newInputStream(p)) {
            P.load(in);
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