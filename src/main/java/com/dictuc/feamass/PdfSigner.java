package com.dictuc.feamass;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;

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

    /** Posicion del sello visual (imagen de firma) sobre la pagina. */
    public enum PosicionSello {
        SUPERIOR_IZQUIERDA("Arriba - izquierda"),
        SUPERIOR_CENTRO("Arriba - centro"),
        SUPERIOR_DERECHA("Arriba - derecha"),
        INFERIOR_IZQUIERDA("Abajo - izquierda"),
        INFERIOR_CENTRO("Abajo - centro"),
        INFERIOR_DERECHA("Abajo - derecha");

        private final String etiqueta;

        PosicionSello(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    /**
     * Sello visual (imagen de la firma + texto) a estampar sobre la pagina antes de firmar.
     * No reemplaza la firma criptografica PKCS#7: es la apariencia visible del propio campo
     * de firma (unica forma soportada de forma fiable por un guardado incremental).
     */
    public static final class SelloVisual {
        public final byte[] imagen; // PNG o JPG
        public final String etiqueta; // ej. "Juan Perez - 06-07-2026 15:32"
        public final PosicionSello posicion;
        public final int pagina; // 0-based; -1 = ultima pagina

        public SelloVisual(byte[] imagen, String etiqueta, PosicionSello posicion, int pagina) {
            this.imagen = imagen;
            this.etiqueta = etiqueta;
            this.posicion = posicion;
            this.pagina = pagina;
        }
    }

    /** Firma un PDF de forma incremental y genera PKCS#7 detached, desde archivo a archivo. */
    public static void signSingle(File inputPdf, File outputPdf, KeyAndCert kc) throws Exception {
        try (PDDocument doc = PDDocument.load(inputPdf);
             OutputStream os = new BufferedOutputStream(new FileOutputStream(outputPdf))) {
            signIntoStream(doc, os, kc, null);
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
    public static byte[] signToBytes(byte[] inputPdf, KeyAndCert kc) throws Exception {
        return signToBytes(inputPdf, kc, null);
    }

    /** Igual que signToBytes(byte[], KeyAndCert), pero estampando antes un sello visual (opcional). */
    public static byte[] signToBytes(byte[] inputPdf, KeyAndCert kc, SelloVisual sello) throws Exception {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(inputPdf));
             ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64_000, inputPdf.length + 8_000))) {
            signIntoStream(doc, out, kc, sello);
            return out.toByteArray();
        }
    }

    /**
     * Logica comun de firma incremental + CMS detached, con sello visual opcional.
     *
     * IMPORTANTE: el sello NO se dibuja directo sobre el content stream de la pagina (eso se
     * intento primero y el guardado incremental para firmar lo descartaba silenciosamente — es
     * una limitacion conocida de PDFBox con objetos nuevos agregados fuera del mecanismo de
     * SignatureOptions). La unica forma fiable es la que usa el propio ejemplo oficial de PDFBox
     * (CreateVisibleSignature2): la imagen se asocia como apariencia visible (/AP /N) del campo
     * de firma en si, via SignatureOptions.setVisualSignature(...).
     */
    private static void signIntoStream(PDDocument doc, OutputStream os, KeyAndCert kc, SelloVisual sello) throws Exception {
        PDSignature sig = new PDSignature();
        sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        sig.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        sig.setName(kc.cert().getSubjectX500Principal().getName());
        sig.setSignDate(Calendar.getInstance());

        SignatureOptions signatureOptions = null;
        try {
            if (sello != null && doc.getNumberOfPages() > 0) {
                signatureOptions = new SignatureOptions();
                int pagina = sello.pagina < 0
                        ? doc.getNumberOfPages() - 1
                        : Math.min(sello.pagina, doc.getNumberOfPages() - 1);
                PDRectangle rect = calcularRectanguloSello(doc.getPage(pagina), sello);
                signatureOptions.setVisualSignature(crearPlantillaVisual(doc, pagina, rect, sello));
                signatureOptions.setPage(pagina);
                doc.addSignature(sig, signatureOptions);
            } else {
                doc.addSignature(sig);
            }

            ExternalSigningSupport ext = doc.saveIncrementalForExternalSigning(os);
            byte[] toBeSigned = readAll(ext.getContent());
            byte[] cms = cmsSign(toBeSigned, kc.privateKey(), kc.chain());
            ext.setSignature(cms);
        } finally {
            // No cerrar signatureOptions antes de terminar de guardar: algunos COSStream que
            // contiene quedan referenciados por el documento ya firmado (ver comentario del
            // ejemplo oficial de PDFBox, PDFBOX-3743).
            if (signatureOptions != null) {
                try {
                    signatureOptions.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    private static final float TAMANO_FUENTE_ETIQUETA = 6.5f;
    private static final float ALTURA_TEXTO_ETIQUETA = 12f;

    /** Medidas del sello: tamaño de la imagen y ancho/alto final del widget (el mayor entre imagen y texto). */
    private record DimensionesSello(float anchoImagen, float altoImagen, float ancho, float alto) {
    }

    private static DimensionesSello calcularDimensiones(SelloVisual sello) throws IOException {
        float[] dims = dimensionesImagen(sello.imagen);

        float anchoMax = 160f;
        float altoMaxImagen = 60f;
        float escala = Math.min(anchoMax / dims[0], altoMaxImagen / dims[1]);
        float anchoImagen = dims[0] * escala;
        float altoImagen = dims[1] * escala;

        float anchoTexto = 0f;
        if (sello.etiqueta != null && !sello.etiqueta.isBlank()) {
            anchoTexto = PDType1Font.HELVETICA.getStringWidth(sello.etiqueta) / 1000f * TAMANO_FUENTE_ETIQUETA
                    + 2f; // margen de seguridad para no tocar el borde
        }

        float ancho = Math.max(anchoImagen, anchoTexto);
        float alto = altoImagen + ALTURA_TEXTO_ETIQUETA;
        return new DimensionesSello(anchoImagen, altoImagen, ancho, alto);
    }

    /** Rectangulo (en coordenadas de pagina, origen abajo-izquierda) donde va el sello. */
    private static PDRectangle calcularRectanguloSello(PDPage page, SelloVisual sello) throws IOException {
        PDRectangle box = page.getMediaBox();
        DimensionesSello d = calcularDimensiones(sello);
        float ancho = d.ancho();
        float alto = d.alto();
        float margen = 24f;

        float x;
        float y;
        switch (sello.posicion) {
            case SUPERIOR_IZQUIERDA -> {
                x = margen;
                y = box.getHeight() - margen - alto;
            }
            case SUPERIOR_CENTRO -> {
                x = (box.getWidth() - ancho) / 2;
                y = box.getHeight() - margen - alto;
            }
            case SUPERIOR_DERECHA -> {
                x = box.getWidth() - margen - ancho;
                y = box.getHeight() - margen - alto;
            }
            case INFERIOR_IZQUIERDA -> {
                x = margen;
                y = margen;
            }
            case INFERIOR_CENTRO -> {
                x = (box.getWidth() - ancho) / 2;
                y = margen;
            }
            default -> {
                x = box.getWidth() - margen - ancho;
                y = margen;
            }
        }

        PDRectangle rect = new PDRectangle();
        rect.setLowerLeftX(x);
        rect.setLowerLeftY(y);
        rect.setUpperRightX(x + ancho);
        rect.setUpperRightY(y + alto);
        return rect;
    }

    /** Ancho/alto reales de la imagen (sin depender de un PDDocument), para calcular el rectangulo. */
    private static float[] dimensionesImagen(byte[] imagenBytes) throws IOException {
        var img = javax.imageio.ImageIO.read(new ByteArrayInputStream(imagenBytes));
        if (img == null) {
            throw new IOException("No se pudo leer la imagen de firma (formato no reconocido).");
        }
        return new float[]{img.getWidth(), img.getHeight()};
    }

    /**
     * Construye el documento-plantilla con la apariencia visible (imagen + etiqueta) del campo
     * de firma, siguiendo el patron del ejemplo oficial CreateVisibleSignature2 de PDFBox.
     */
    private static InputStream crearPlantillaVisual(PDDocument srcDoc, int pageIndex, PDRectangle rect, SelloVisual sello)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(srcDoc.getPage(pageIndex).getMediaBox());
            doc.addPage(page);

            PDAcroForm acroForm = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(acroForm);
            PDSignatureField signatureField = new PDSignatureField(acroForm);
            PDAnnotationWidget widget = signatureField.getWidgets().get(0);
            acroForm.setSignaturesExist(true);
            acroForm.setAppendOnly(true);
            acroForm.getCOSObject().setDirect(true);
            acroForm.getFields().add(signatureField);
            widget.setRectangle(rect);

            PDStream stream = new PDStream(doc);
            PDFormXObject form = new PDFormXObject(stream);
            form.setResources(new PDResources());
            form.setFormType(1);
            PDRectangle bbox = new PDRectangle(rect.getWidth(), rect.getHeight());
            form.setBBox(bbox);

            PDAppearanceDictionary appearance = new PDAppearanceDictionary();
            appearance.getCOSObject().setDirect(true);
            PDAppearanceStream appearanceStream = new PDAppearanceStream(form.getCOSObject());
            appearance.setNormalAppearance(appearanceStream);
            widget.setAppearance(appearance);

            DimensionesSello d = calcularDimensiones(sello);
            float xImagen = (bbox.getWidth() - d.anchoImagen()) / 2;

            try (PDPageContentStream cs = new PDPageContentStream(doc, appearanceStream)) {
                PDImageXObject imagen = PDImageXObject.createFromByteArray(doc, sello.imagen, "firma");
                cs.drawImage(imagen, xImagen, ALTURA_TEXTO_ETIQUETA, d.anchoImagen(), d.altoImagen());

                if (sello.etiqueta != null && !sello.etiqueta.isBlank()) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, TAMANO_FUENTE_ETIQUETA);
                    cs.newLineAtOffset(0, 1);
                    cs.showText(sello.etiqueta);
                    cs.endText();
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return new ByteArrayInputStream(baos.toByteArray());
        }
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
