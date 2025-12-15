// ZipUtil.java
package com.dictuc.feamass;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class ZipUtil {

    private ZipUtil() {}

    public static List<File> unzipOnlyPdfs(File zipFile, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs())
            throw new IOException("No se pudo crear: " + destDir);

        List<File> pdfs = new ArrayList<>();

        // 1) try UTF-8; 2) fallback CP437; 3) fallback ISO-8859-1
        IOException last = null;
        for (Charset cs : List.of(StandardCharsets.UTF_8,
                                  Charset.forName("CP437"),
                                  StandardCharsets.ISO_8859_1)) {
            try {
                unzipWithCharset(zipFile, destDir, cs, pdfs);
                return pdfs; // listo
            } catch (IOException e) {
                last = e;    // probamos el siguiente charset
            }
        }
        throw last != null ? last : new IOException("No se pudo abrir ZIP (charsets)");
    }

    private static void unzipWithCharset(File zipFile, File destDir, Charset cs,
                                         List<File> outPdfs) throws IOException {
        outPdfs.clear();
        // Desde Java 11 existen constructores con Charset
        try (ZipFile zf = new ZipFile(zipFile, cs)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (ze.isDirectory()) continue;

                String name = ze.getName();
                // normaliza separadores y evita path traversal
                name = name.replace('\\', '/');
                if (name.startsWith("../") || name.contains("/../"))
                    continue;

                // Solo PDFs
                if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) continue;

                File out = new File(destDir, name);
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs())
                    throw new IOException("No se pudo crear dir: " + parent);

                try (InputStream in = zf.getInputStream(ze);
                     OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = in.read(buf)) >= 0) os.write(buf, 0, r);
                }
                outPdfs.add(out);
            }
        }
    }

    public static void zipFolderPdfs(File folder, File zipOut) throws IOException {
        // empaqueta SOLO PDFs del folder (mantiene subcarpetas)
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipOut)))) {
            // Usa UTF-8 al escribir para evitar el problema a futuro
            zos.setLevel(9);
            final String basePath = folder.getCanonicalPath() + File.separator;
            Deque<File> stack = new ArrayDeque<>();
            stack.push(folder);

            while (!stack.isEmpty()) {
                File cur = stack.pop();
                File[] list = cur.listFiles();
                if (list == null) continue;
                for (File f : list) {
                    if (f.isDirectory()) {
                        stack.push(f);
                        continue;
                    }
                    if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) continue;

                    String rel = f.getCanonicalPath().substring(basePath.length())
                            .replace(File.separatorChar, '/');
                    ZipEntry ze = new ZipEntry(rel);
                    zos.putNextEntry(ze);
                    try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
                        byte[] buf = new byte[64 * 1024];
                        int r;
                        while ((r = in.read(buf)) >= 0) zos.write(buf, 0, r);
                    }
                    zos.closeEntry();
                }
            }
        }
    }
}