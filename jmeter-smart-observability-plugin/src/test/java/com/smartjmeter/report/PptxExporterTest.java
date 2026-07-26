package com.smartjmeter.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptxExporterTest {

    @Test
    void generatesNonEmptyDeck(@TempDir Path dir) throws Exception {
        Map<String, Object> env = Map.of(
                "test_name", "checkout-load",
                "environment", "staging",
                "application", "web",
                "verdict", Map.of(
                        "level", "GO_WITH_CONDITIONS",
                        "production_confidence", 78.5,
                        "risk_score", 32.1,
                        "rationale", "P95 marginally above SLA on 1 transaction",
                        "rollout_plan", List.of("canary 5%", "watch p95 30 min"),
                        "rollback_triggers", List.of("p95 > 1500ms", "error_rate > 2%")),
                "health_scores", Map.of("latency", 82, "throughput", 90, "errors", 95),
                "findings", List.of(
                        Map.of("severity", "HIGH", "title", "p95 breach on /checkout", "category", "latency"),
                        Map.of("severity", "MED",  "title", "GC pause spikes",         "category", "jvm"))
        );

        Path out = dir.resolve("deck.pptx");
        Path written = new PptxExporter().export(env, out);
        assertNotNull(written, "PPTX export returned null");
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 1024, "PPTX file suspiciously small: " + Files.size(out));
    }

    @Test
    void toleratesEmptyEnvelope(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("deck.pptx");
        Path written = new PptxExporter().export(Map.of(), out);
        assertNotNull(written);
        assertTrue(Files.exists(out));
    }
}
