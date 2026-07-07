package com.dictuc.feamass;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Bitacora de actividad en disco (fuera del jar): registra intentos de login y de firma
 * (exito/error, con el id del informe y el certificado usado) para poder auditar o dar
 * soporte despues de que la app se cerro. Nunca escribe contenido de PDFs ni contrasenas.
 */
public final class ActivityLog {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path ARCHIVO = resolverArchivo();

    private ActivityLog() {}

    private static Path resolverArchivo() {
        try {
            Path dir = Path.of(System.getProperty("user.home", "."), ".feamass");
            Files.createDirectories(dir);
            return dir.resolve("actividad.log");
        } catch (IOException e) {
            return Path.of("feamass-actividad.log");
        }
    }

    public static synchronized void registrar(String evento) {
        String linea = "[" + LocalDateTime.now().format(FMT) + "] " + evento;
        try (PrintWriter out = new PrintWriter(new FileWriter(ARCHIVO.toFile(), true))) {
            out.println(linea);
        } catch (IOException ignore) {
            // El registro en disco nunca debe interrumpir el flujo de firma.
        }
    }
}
