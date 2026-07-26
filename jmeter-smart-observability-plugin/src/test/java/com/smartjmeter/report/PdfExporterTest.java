package com.smartjmeter.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExporterTest {

    @Test
    void rendersHtmlToPdf(@TempDir Path dir) throws Exception {
        Path html = dir.resolve("in.html");
        Files.writeString(html, """
                <!DOCTYPE html><html><head><meta charset="utf-8"/><title>t</title></head>
                <body><h1>Perf verdict</h1><p>GO</p></body></html>""");
        Path pdf = dir.resolve("out.pdf");
        Path written = new PdfExporter().export(html, pdf);
        assertNotNull(written, "PDF export returned null");
        assertTrue(Files.exists(pdf));
        // PDF signature check: %PDF-
        byte[] head = Files.readAllBytes(pdf);
        assertTrue(head.length > 5
                && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F',
                "output is not a PDF");
    }
}
