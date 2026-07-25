package com.smartjmeter.report;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Very small HTML report writer used at the end of a JMeter run. Phase&nbsp;4
 * will extend this with Excel and PDF exporters.
 */
public class ReportGenerator {

    public static final String DEFAULT_REPORT_PATH = "Performance_Report.html";

    public Path generate(String analysis) throws Exception {
        return generate(analysis, DEFAULT_REPORT_PATH);
    }

    public Path generate(String analysis, String outputPath) throws Exception {
        String html = """
                <html>
                  <head>
                    <title>AI Performance Report</title>
                  </head>
                  <body>
                    <h1>AI Performance Report</h1>
                    <h2>Analysis</h2>
                    <pre>%s</pre>
                  </body>
                </html>
                """.formatted(escape(analysis));
        Path target = Path.of(outputPath);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, html);
        return target;
    }

    private static String escape(String in) {
        if (in == null) return "";
        return in.replace("&", "&amp;")
                 .replace("<", "&lt;")
                 .replace(">", "&gt;");
    }
}
