package com.smartjmeter.report;

import com.smartjmeter.forecast.CapacityForecast;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end HTML render check for the v2.0.2 sections
 * (SVG charts, external metrics, capacity forecast).
 */
class ReportV202SectionsTest {

    @Test
    void renderIncludesAllNewSections(@TempDir Path dir) throws Exception {
        Map<String, Object> agg = Map.of(
                "overall", Map.of("count", 1000, "errors", 12, "error_rate", 0.012,
                        "rt_avg_ms", 210, "rt_p95_ms", 480, "rt_max_ms", 1800,
                        "start_ms", 0L, "stop_ms", 60_000L),
                "per_transaction", Map.of(
                        "GET /cart", Map.of("count", 500, "errors", 0,
                                "rt_median_ms", 40, "rt_avg_ms", 60, "rt_p95_ms", 120,
                                "rt_max_ms", 300, "rt_min_ms", 20),
                        "POST /checkout", Map.of("count", 200, "errors", 12,
                                "rt_median_ms", 80, "rt_avg_ms", 120, "rt_p95_ms", 900,
                                "rt_max_ms", 1800, "rt_min_ms", 40))
        );
        Map<String, Object> verdict = Map.of(
                "level", "GO_WITH_CONDITIONS",
                "production_confidence", 72.5,
                "risk_score", 27.5,
                "rationale", "Non-critical p95 breach on checkout",
                "gates_passed", List.of("SLA", "Regression"),
                "gates_failed", List.of("No critical findings"),
                "deployment_recommendation", "Canary at 10%",
                "rollout_plan", List.of("Canary 10%", "watch p95 for 30 min"),
                "rollback_triggers", List.of("p95 > 1500ms")
        );

        // Simulate external metrics from Prometheus and Datadog
        Map<String, Map<String, List<Map<String, Object>>>> ext = Map.of(
                "prometheus", Map.of("cpu_saturation",
                        List.of(Map.of("value", 0.72), Map.of("value", 0.65))),
                "datadog", Map.of("error_rate_pct",
                        List.of(Map.of("value", 1.2), Map.of("value", 1.5)))
        );

        // Forecast dir with a rising history
        Path history = dir.resolve("history");
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 5; i++) {
            long ts = now - i * 86_400_000L;
            Files.createDirectories(history);
            Files.writeString(history.resolve("snapshot-" + ts + ".json"),
                    "{\"timestamp_ms\":" + ts + ",\"p95_ms\":" + (400 + i * 40) + "}");
        }
        Map<String, Object> forecast = CapacityForecast.forecast(history, 1000, agg).toMap();

        ReportGenerator.Context ctx = new ReportGenerator.Context.Builder()
                .testName("checkout-load")
                .environment("staging")
                .application("web")
                .startMs(0L).stopMs(60_000L)
                .aggregate(agg)
                .verdict(verdict)
                .externalMetrics(ext)
                .forecast(forecast)
                .llmAnalysis("- Investigate checkout hot path.")
                .build();

        Path out = dir.resolve("report.html");
        new ReportGenerator().generate(ctx, out.toString());
        String html = Files.readString(out);

        // Charts
        assertTrue(html.contains("Visual Analytics"), "waterfall/sankey section missing");
        assertTrue(html.contains("Per-transaction latency"), "waterfall header missing");
        assertTrue(html.contains("Verdict &rarr; gate flow"), "sankey header missing");

        // External metrics
        assertTrue(html.contains("External Observability Sources"));
        assertTrue(html.contains("PROMETHEUS"));
        assertTrue(html.contains("DATADOG"));
        assertTrue(html.contains("cpu_saturation"));

        // Capacity forecast
        assertTrue(html.contains("Capacity Forecast"));
        assertTrue(html.contains("Days to breach"));
    }
}
