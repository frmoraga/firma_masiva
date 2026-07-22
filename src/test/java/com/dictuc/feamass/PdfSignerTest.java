package com.dictuc.feamass;

import com.dictuc.feamass.certs.TestCertFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfSignerTest {

    private static byte[] pdfDeUnaPagina() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] imagenPngDePrueba() throws Exception {
        BufferedImage img = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void firmaUnPdfYQuedaExactamenteUnaFirma() throws Exception {
        KeyAndCert kc = TestCertFactory.firmante(
                new Date(), new Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000),
                KeyUsage.digitalSignature);

        byte[] firmado = PdfSigner.signToBytes(pdfDeUnaPagina(), kc);

        try (PDDocument doc = PDDocument.load(firmado)) {
            List<PDSignature> firmas = doc.getSignatureDictionaries();
            assertEquals(1, firmas.size());
            PDSignature sig = firmas.get(0);
            assertEquals(PDSignature.FILTER_ADOBE_PPKLITE.getName(), sig.getFilter());
            assertEquals(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED.getName(), sig.getSubFilter());
            assertTrue(sig.getContents().length > 0);
        }
    }

    @Test
    void firmaConSelloVisualNoRompeElPdfYSigueQuedandoUnaFirma() throws Exception {
        KeyAndCert kc = TestCertFactory.firmante(
                new Date(), new Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000),
                KeyUsage.digitalSignature);

        PdfSigner.SelloVisual sello = new PdfSigner.SelloVisual(
                imagenPngDePrueba(), "Test Firmante - 22-07-2026", PdfSigner.PosicionSello.INFERIOR_DERECHA, -1);

        byte[] firmado = PdfSigner.signToBytes(pdfDeUnaPagina(), kc, sello);

        try (PDDocument doc = PDDocument.load(firmado)) {
            assertEquals(1, doc.getNumberOfPages());
            assertEquals(1, doc.getSignatureDictionaries().size());
        }
    }

    @Test
    void firmarConMultiplesCertificadosProduceContenidoDeFirmaDistinto() throws Exception {
        KeyAndCert kc1 = TestCertFactory.firmante(
                new Date(), new Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000),
                KeyUsage.digitalSignature);
        KeyAndCert kc2 = TestCertFactory.firmante(
                new Date(), new Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000),
                KeyUsage.digitalSignature);

        byte[] pdf = pdfDeUnaPagina();
        byte[] firmado1 = PdfSigner.signToBytes(pdf, kc1);
        byte[] firmado2 = PdfSigner.signToBytes(pdf, kc2);

        assertTrue(firmado1.length > 0 && firmado2.length > 0);
        // Firmas de dos certificados distintos no deberian coincidir byte a byte.
        assertTrue(!java.util.Arrays.equals(firmado1, firmado2));
    }
}
