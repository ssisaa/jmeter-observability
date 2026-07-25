package com.smartjmeter.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportGeneratorTest {

    @Test
    void legacyBackwardsCompatibleEntryPointStillWorks(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("legacy.html");
        Path written = new ReportGenerator().generate("hello <script>x</script>", out.toString());
        assertEquals(out, written);
        String html = Files.readString(out);
        assertTrue(html.contains("<title>AI Performance Report</title>"));
        // Analysis text escaped
        assertTrue(html.contains("&lt;script&gt;x&lt;/script&gt;"));
    }

    @Test
    void richContextRendersKpisTableCorrelationAndAnalysis(@TempDir Path tmp) throws Exception {
        Map<String, Object> aggregate = Map.of(
                "overall", Map.of(
                        "count", 20L, "errors", 5L,
                        "rt_min_ms", 10L, "rt_avg_ms", 100L, "rt_p95_ms", 1500L, "rt_max_ms", 2000L,
                        "start_ms", 1_700_000_000_000L, "stop_ms", 1_700_000_010_000L),
                "per_transaction", Map.of(
                        "Login", Map.of("count", 10L, "errors", 0L,
                                "rt_min_ms", 10L, "rt_avg_ms", 50L,
                                "rt_p95_ms", 80L, "rt_max_ms", 100L),
                        "Checkout", Map.of("count", 10L, "errors", 5L,
                                "rt_min_ms", 20L, "rt_avg_ms", 150L,
                                "rt_p95_ms", 1500L, "rt_max_ms", 2000L))
        );
        Map<String, Object> correlation = Map.of(
                "failure_count", 5L,
                "window_seconds", 30L,
                "windows", List.of(Map.of("earliest", 1L, "latest", 2L, "event_count", 3)));
        Map<String, List<Map<String, Object>>> o11y = Map.of(
                "cpu.utilization", List.of(Map.of("ts", 1L, "value", 80)));

        ReportGenerator.Context ctx = new ReportGenerator.Context.Builder()
                .testName("smoke-run")
                .environment("perf")
                .application("Migration-System")
                .startMs(1_700_000_000_000L)
                .stopMs(1_700_000_010_000L)
                .aggregate(aggregate)
                .correlation(correlation)
                .o11yMetrics(o11y)
                .llmAnalysis("""
                        # Root Cause

                        ## Findings
                        - Checkout **fails** 50% of the time
                        - Login is fine

                        ## Recommendations
                        1. Roll back `deploy-42`
                        2. Investigate DB latency
                        """)
                .llmProvider("groq")
                .llmModel("llama-3.3-70b-versatile")
                .build();

        Path out = tmp.resolve("rich.html");
        new ReportGenerator().generate(ctx, out.toString());
        String html = Files.readString(out);

        // KPI values
        assertTrue(html.contains(">20<"));                 // sample count
        assertTrue(html.contains("25.00%"));               // error rate
        assertTrue(html.contains("1500 ms"));              // p95

        // Per-transaction table
        assertTrue(html.contains(">Checkout<"));
        assertTrue(html.contains("row-danger"));

        // Correlation pills
        assertTrue(html.contains("Failed samples"));
        assertTrue(html.contains("Correlated events"));

        // O11y metric row
        assertTrue(html.contains("cpu.utilization"));

        // Analysis markdown -> HTML
        assertTrue(html.contains("<h1>Root Cause</h1>"));
        assertTrue(html.contains("<h2>Findings</h2>"));
        assertTrue(html.contains("<ul><li>"));
        assertTrue(html.contains("<ol><li>"));
        assertTrue(html.contains("<strong>fails</strong>"));
        assertTrue(html.contains("<code>deploy-42</code>"));

        // Provider footer
        assertTrue(html.contains("Provider:"));
        assertTrue(html.contains("groq"));
        assertTrue(html.contains("llama-3.3-70b-versatile"));
    }

    @Test
    void markdownEscapesInjectionAttempts() {
        String rendered = Markdown.render("Hello <img src=x onerror=alert(1)> **bold**");
        assertFalse(rendered.contains("<img"));
        assertTrue(rendered.contains("&lt;img"));
        assertTrue(rendered.contains("<strong>bold</strong>"));
    }
}
