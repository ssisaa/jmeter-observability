package com.smartjmeter.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownReportExporterTest {

    private Map<String, Object> envelope() {
        return Map.ofEntries(
                Map.entry("test_name", "checkout-load"),
                Map.entry("application", "smart-shop"),
                Map.entry("environment", "staging"),
                Map.entry("generated_at", "2026-02-08T10:00:00Z"),
                Map.entry("verdict", Map.of(
                        "level", "GO_WITH_CONDITIONS",
                        "production_confidence", 74.2,
                        "risk_score", 25.8,
                        "rationale", "checkout p95 marginal",
                        "rollout_plan", List.of("Canary 10%", "Ramp to 50%"),
                        "rollback_triggers", List.of("p95 > 1500 ms"))),
                Map.entry("aggregate", Map.of(
                        "overall", Map.of("count", 24871L, "errors", 148L, "error_rate", 0.006,
                                "rt_median_ms", 152, "rt_p95_ms", 612, "rt_p99_ms", 1248,
                                "rt_max_ms", 3984, "peak_threads", 200, "apdex_score", 0.87,
                                "throughput_rps", 120.5),
                        "per_transaction", Map.of(
                                "GET /home", Map.of("count", 9824L, "errors", 0L,
                                        "rt_median_ms", 42, "rt_p95_ms", 110, "rt_p99_ms", 220, "rt_max_ms", 300),
                                "POST /checkout", Map.of("count", 2641L, "errors", 82L,
                                        "rt_median_ms", 220, "rt_p95_ms", 780, "rt_p99_ms", 2140, "rt_max_ms", 3984)))),
                Map.entry("findings", List.of(
                        Map.of("severity", "HIGH", "title", "payment p99 breach", "category", "latency", "confidence", 0.82),
                        Map.of("severity", "MEDIUM", "title", "checkout error rate up", "category", "reliability", "confidence", 0.71))),
                Map.entry("ai_insights", Map.of(
                        "markdown", "## Payment gateway saturated\nCircuit-break required.",
                        "root_causes", List.of("Payment gateway saturating at 260 rps"),
                        "recommendations", List.of("Enable circuit breaker", "Scale replicas 4->6"),
                        "business_impact", Map.of("lost_conversions_est", 84, "usd_est", 3780))),
                Map.entry("external_metrics", Map.of(
                        "prometheus", Map.of("cpu_saturation",
                                List.of(Map.of("value", 0.62), Map.of("value", 0.71)))))
        );
    }

    @Test
    void writesFile(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("Performance_Report.md");
        Path written = new MarkdownReportExporter().export(envelope(), out);
        assertNotNull(written);
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 500, "Markdown file suspiciously small");
    }

    @Test
    void renderContainsAllTopSections() {
        String md = MarkdownReportExporter.render(envelope());
        assertTrue(md.startsWith("# Performance Test Report"), "expected H1 title");
        assertTrue(md.contains("## Executive Summary"));
        assertTrue(md.contains("## Key Metrics"));
        assertTrue(md.contains("## Key Issues"));
        assertTrue(md.contains("## Per-Transaction Statistics"));
        assertTrue(md.contains("## Root Cause Analysis"));
        assertTrue(md.contains("## Recommendations"));
        assertTrue(md.contains("## Business Impact"));
        assertTrue(md.contains("## Rollout Plan"));
        assertTrue(md.contains("## Rollback Triggers"));
        assertTrue(md.contains("v2.0.6"));
    }

    @Test
    void renderIncludesTables() {
        String md = MarkdownReportExporter.render(envelope());
        // Markdown table separator line
        assertTrue(md.contains("|---|---|"));
        // Header of per-transaction table
        assertTrue(md.contains("| Transaction | Samples | Errors | Err % | p50 | p95 | p99 | Max |"));
        // Data
        assertTrue(md.contains("GET /home"));
        assertTrue(md.contains("POST /checkout"));
    }

    @Test
    void renderHandlesEmptyEnvelope() {
        String md = MarkdownReportExporter.render(Map.of());
        assertTrue(md.contains("# Performance Test Report"));
        // No crashes; sections absent when data missing.
    }
}
