package com.dictuc.feamass;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class PdfSigner {

    /** Firma un PDF de forma incremental y genera PKCS#7 detached, desde archivo a archivo. */
    public static void signSingle(File inputPdf, File outputPdf, KeyAndCert kc) throws Exception {
        try (PDDocument doc = PDDocument.load(inputPdf);
             OutputStream os = new BufferedOutputStream(new FileOutputStream(outputPdf))) {
            signIntoStream(doc, os, kc);
        }
    }

    /**
     * Firma todos los PDFs dentro de un ZIP dejando SOLO los firmados, con el MISMO nombre.
     * Lee el ZIP probando múltiples charsets para evitar el error "malformed input".
     */
    public static void signZip(File zipIn, File zipOut, KeyAndCert kc) throws Exception {
        // Escribimos en un temporal para no dejar el ZIP de salida corrupto en caso de reintentos
        File tmpOut = File.createTempFile("feamass_signed_", ".zip");
        try {
            List<Charset> tries = Arrays.asList(
                    StandardCharsets.UTF_8,
                    Charset.forName("CP437"),
                    StandardCharsets.ISO_8859_1,
                    Charset.forName("windows-1252")
            );

            Exception last = null;
            boolean done = false;

            for (Charset cs : tries) {
                try {
                    processZipWithCharset(zipIn, tmpOut, kc, cs);
                    done = true;
                    break;
                } catch (MalformedInputException | IllegalArgumentException e) {
                    // nombre no decodificable con este charset → probamos el siguiente
                    last = e;
                }
            }

            if (!done) {
                throw new IOException("No se pudo leer el ZIP con ninguna codificación conocida. Último error: "
                        + (last != null ? last.getMessage() : "desconocido"));
            }

            // Éxito: movemos el temporal al destino
            Files.deleteIfExists(zipOut.toPath());
            Files.move(tmpOut.toPath(), zipOut.toPath());
        } finally {
            // Limpieza si quedó el temporal
            try { Files.deleteIfExists(tmpOut.toPath()); } catch (Exception ignore) {}
        }
    }

    /* ====================== helpers internos ====================== */

    /** Procesa el ZIP usando el charset indicado. Lanza MalformedInputException si falla. */
    private static void processZipWithCharset(File zipIn, File zipOut, KeyAndCert kc, Charset cs) throws Exception {
        byte[] buf = new byte[32_768];

        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipIn)), cs);
             ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipOut)))) {

            ZipEntry inEntry;
            while ((inEntry = zin.getNextEntry()) != null) {
                String name = inEntry.getName();

                if (inEntry.isDirectory()) {
                    // preserva subcarpetas si vienen
                    zout.putNextEntry(new ZipEntry(name));
                    zout.closeEntry();
                    zin.closeEntry();
                    continue;
                }

                if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    // Saltamos no-PDF: el resultado contendrá solo PDFs firmados
                    zin.closeEntry();
                    continue;
                }

                // Leemos PDF a memoria
                ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
                int r;
                while ((r = zin.read(buf)) >= 0) pdfBytes.write(buf, 0, r);
                zin.closeEntry();

                // Firmamos y escribimos con el MISMO nombre
                byte[] signed = signToBytes(pdfBytes.toByteArray(), kc);

                ZipEntry outEntry = new ZipEntry(name);
                zout.putNextEntry(outEntry);
                zout.write(signed);
                zout.closeEntry();
            }

            zout.finish();
        }
    }

    /** Firma un PDF recibido como bytes y devuelve los bytes firmados (incremental, detached). */
    private static byte[] signToBytes(byte[] inputPdf, KeyAndCert kc) throws Exception {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(inputPdf));
             ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64_000, inputPdf.length + 8_000))) {
            signIntoStream(doc, out, kc);
            return out.toByteArray();
        }
    }

    /** Lógica común de firma incremental + CMS detached sobre un PDDocument y salida dada. */
    private static void signIntoStream(PDDocument doc, OutputStream os, KeyAndCert kc) throws Exception {
        PDSignature sig = new PDSignature();
        sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        sig.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        sig.setName(kc.cert().getSubjectX500Principal().getName());
        sig.setSignDate(Calendar.getInstance());

        doc.addSignature(sig);

        // Guardado incremental + bytes a firmar
        ExternalSigningSupport ext = doc.saveIncrementalForExternalSigning(os);
        byte[] toBeSigned = readAll(ext.getContent());

        // CMS detached
        byte[] cms = cmsSign(toBeSigned, kc.privateKey(), kc.chain());

        // Insertar firma
        ext.setSignature(cms);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(32_768, in.available()));
        byte[] buf = new byte[16_384];
        int r;
        while ((r = in.read(buf)) >= 0) baos.write(buf, 0, r);
        in.close();
        return baos.toByteArray();
    }

    private static byte[] cmsSign(byte[] toBeSigned,
                                  PrivateKey privateKey,
                                  java.security.cert.Certificate[] chain) throws Exception {
        List<X509Certificate> certs = new ArrayList<>();
        for (java.security.cert.Certificate c : chain) certs.add((X509Certificate) c);
        X509Certificate signerCert = certs.get(0);

        String algo = "SHA256with" + privateKey.getAlgorithm(); // RSA / EC / etc.
        ContentSigner signer = new JcaContentSignerBuilder(algo).build(privateKey);

        var digestProvider = new JcaDigestCalculatorProviderBuilder().build();
        var sigInfo = new JcaSignerInfoGeneratorBuilder(digestProvider).build(signer, signerCert);

        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        gen.addSignerInfoGenerator(sigInfo);
        gen.addCertificates(new JcaCertStore(certs));

        CMSTypedData content = new CMSProcessableByteArray(toBeSigned);
        CMSSignedData signed = gen.generate(content, false); // detached
        return signed.getEncoded();
    }
}