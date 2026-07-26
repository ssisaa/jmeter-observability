package com.smartjmeter.report;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Renders an existing HTML report to PDF using openhtmltopdf.
 * The HTML is expected to be well-formed XHTML/HTML5 - the shipped
 * ReportGenerator output already is.
 */
public class PdfExporter {

    private static final Logger LOG = Logger.getLogger(PdfExporter.class.getName());

    /** Convert the given HTML file to PDF at {@code pdfPath}. */
    public Path export(Path htmlPath, Path pdfPath) {
        try {
            if (pdfPath.getParent() != null) Files.createDirectories(pdfPath.getParent());
            String html = Files.readString(htmlPath);
            try (OutputStream os = Files.newOutputStream(pdfPath)) {
                PdfRendererBuilder b = new PdfRendererBuilder();
                b.useFastMode();
                b.withHtmlContent(html, htmlPath.toUri().toString());
                b.toStream(os);
                b.run();
            }
            return pdfPath;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "PDF export failed - HTML report remains available", e);
            return null;
        }
    }
}
