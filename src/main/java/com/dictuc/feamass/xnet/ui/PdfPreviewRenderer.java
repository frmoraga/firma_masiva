package com.dictuc.feamass.xnet.ui;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/** Renderiza un PDF a imagenes en memoria, para vista previa sin exponer nunca el archivo real. */
public final class PdfPreviewRenderer {

    private PdfPreviewRenderer() {}

    public static List<Image> renderPages(byte[] pdfBytes, float dpi) throws Exception {
        List<Image> pages = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int count = doc.getNumberOfPages();
            for (int i = 0; i < count; i++) {
                BufferedImage bi = renderer.renderImageWithDPI(i, dpi);
                pages.add(SwingFXUtils.toFXImage(bi, null));
            }
        }
        return pages;
    }
}
