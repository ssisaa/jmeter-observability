package com.smartjmeter.report;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Renders an existing HTML report to PDF using openhtmltopdf.
 * Applies a light normalisation pass so the report's loose-HTML output
 * (lowercase doctype, named entities like {@code &mdash;}) is accepted
 * by openhtmltopdf's strict XHTML parser.
 */
public class PdfExporter {

    private static final Logger LOG = Logger.getLogger(PdfExporter.class.getName());

    /** Named entities the ReportGenerator emits that aren't legal in raw XHTML. */
    private static final Map<String, String> ENTITY_REPLACEMENTS = Map.of(
            "&mdash;",  "&#8212;",
            "&ndash;",  "&#8211;",
            "&rarr;",   "&#8594;",
            "&larr;",   "&#8592;",
            "&hellip;", "&#8230;",
            "&Delta;",  "&#916;",
            "&middot;", "&#183;",
            "&nbsp;",   "&#160;",
            "&copy;",   "&#169;");

    /** Convert the given HTML file to PDF at {@code pdfPath}. */
    public Path export(Path htmlPath, Path pdfPath) {
        try {
            if (pdfPath.getParent() != null) Files.createDirectories(pdfPath.getParent());
            String html = normaliseForXhtml(Files.readString(htmlPath));
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

    /** Minimal HTML -> XHTML fixups so openhtmltopdf's XML parser accepts the input. */
    static String normaliseForXhtml(String html) {
        String out = html
                .replaceFirst("(?i)<!doctype\\s+html\\s*>", "<!DOCTYPE html>")
                .replace("<meta charset=\"utf-8\"/>", "<meta charset=\"utf-8\" />")
                .replace("<meta charset=\"utf-8\">", "<meta charset=\"utf-8\" />")
                .replace("<br>", "<br/>")
                .replace("<hr>", "<hr/>");
        for (Map.Entry<String, String> e : ENTITY_REPLACEMENTS.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }
}
