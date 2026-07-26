package com.smartjmeter.demo;

import com.smartjmeter.forecast.CapacityForecast;
import com.smartjmeter.report.CsvExporter;
import com.smartjmeter.report.JsonExporter;
import com.smartjmeter.report.PdfExporter;
import com.smartjmeter.report.PptxExporter;
import com.smartjmeter.report.ReportGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v2.0.3 - Public demo report generator.
 *
 * <p>Writes a fully-populated HTML / PDF / PPTX / JSON / CSV set into
 * the given output directory (default {@code docs/demo}). Handy for
 * buyers who want to preview the plugin's deliverable before installing
 * anything, and for the fastapi backend to expose via
 * {@code /api/downloads/demo/*}.</p>
 *
 * <p>Usage:
 * {@code java -cp jmeter-smart-observability-plugin-2.0.3.jar com.smartjmeter.demo.DemoReport [outputDir]}</p>
 */
public final class DemoReport {

    private DemoReport() { }

    public static void main(String[] args) throws Exception {
        Path outDir = Paths.get(args.length > 0 ? args[0] : "docs/demo");
        Files.createDirectories(outDir);

        // Synthetic aggregate mimicking a checkout-flow soak
        Map<String, Object> overall = new LinkedHashMap<>();
        overall.put("count", 24_871L);
        overall.put("errors", 148L);
        overall.put("error_rate", 148d / 24_871d);
        overall.put("rt_avg_ms", 187);
        overall.put("rt_median_ms", 152);
        overall.put("rt_p95_ms", 612);
        overall.put("rt_p99_ms", 1_248);
        overall.put("rt_max_ms", 3_984);
        overall.put("start_ms", System.currentTimeMillis() - 30 * 60_000L);
        overall.put("stop_ms", System.currentTimeMillis());

        Map<String, Object> perTxn = new LinkedHashMap<>();
        perTxn.put("GET /home", txn(9_824, 0, 42, 55, 110, 240, 15));
        perTxn.put("GET /product/{id}", txn(6_512, 6, 88, 110, 260, 610, 40));
        perTxn.put("POST /cart", txn(3_218, 12, 120, 145, 380, 950, 55));
        perTxn.put("POST /checkout", txn(2_641, 82, 220, 280, 780, 2_140, 90));
        perTxn.put("POST /payment", txn(2_676, 48, 260, 320, 940, 3_984, 120));

        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("overall", overall);
        aggregate.put("per_transaction", perTxn);

        // Verdict
        Map<String, Object> verdict = new LinkedHashMap<>();
        verdict.put("level", "GO_WITH_CONDITIONS");
        verdict.put("production_confidence", 74.2);
        verdict.put("risk_score", 25.8);
        verdict.put("rationale", "SLA holds on 4/5 transactions; /payment p99 breach and 1.8% checkout errors require a canary rollout.");
        verdict.put("deployment_recommendation", "Canary 10% for 60 min");
        verdict.put("gates_passed", List.of("SLA", "Regression", "Observability", "Infrastructure"));
        verdict.put("gates_failed", List.of("No critical findings"));
        verdict.put("rollout_plan", List.of(
                "Canary 10% for 60 min with elevated dashboards",
                "Ramp to 50% if p95 stays under 800 ms",
                "Full rollout once error rate < 0.3%"));
        verdict.put("rollback_triggers", List.of(
                "checkout p95 > 1500 ms",
                "payment errors > 3%",
                "any CloudWatch alarm ALARM"));

        // Health scores
        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("performance", 82);
        scores.put("infrastructure", 88);
        scores.put("application", 78);
        scores.put("database", 84);
        scores.put("observability", 92);
        scores.put("scalability", 79);
        scores.put("reliability", 86);
        scores.put("availability", 95);
        scores.put("composite_performance", 84);
        scores.put("release_readiness", 74);
        scores.put("production_confidence", 74);

        // Findings
        List<Map<String, Object>> findings = List.of(
                finding("R-LATENCY", "payment p99 breach", "latency", "HIGH", 0.82,
                        "aggregate.per_transaction.POST /payment.rt_p99_ms=3984"),
                finding("R-ERR-RATE", "checkout error rate 3.1%", "reliability", "MEDIUM", 0.71,
                        "aggregate.per_transaction.POST /checkout.error_rate=0.031"),
                finding("R-TAIL", "heavy tail on /payment (p99 = 4.5x p95)", "latency", "MEDIUM", 0.68,
                        "rt_p99_ms/rt_p95_ms=4.24")
        );

        // External metrics
        Map<String, Map<String, List<Map<String, Object>>>> ext = new LinkedHashMap<>();
        ext.put("prometheus", Map.of(
                "cpu_saturation", List.of(pt(0.62), pt(0.68), pt(0.71), pt(0.66)),
                "request_error_rate", List.of(pt(0.006), pt(0.008), pt(0.011), pt(0.009))));
        ext.put("datadog", Map.of(
                "db_connection_saturation", List.of(pt(0.52), pt(0.55), pt(0.63)),
                "api_error_rate_pct", List.of(pt(0.6), pt(0.8), pt(1.1), pt(0.9))));
        ext.put("dynatrace", Map.of(
                "payment_latency_ms", List.of(pt(820), pt(910), pt(1240), pt(1180))));

        // Forecast — synthetic history that trends up but stays inside SLA
        Path history = outDir.resolve("history-tmp");
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 8; i++) {
            Files.createDirectories(history);
            long ts = now - i * 3L * 86_400_000L;
            Files.writeString(history.resolve("snapshot-" + ts + ".json"),
                    "{\"timestamp_ms\":" + ts + ",\"p95_ms\":" + (450 + (8 - i) * 20) + "}");
        }
        Map<String, Object> forecast = CapacityForecast.forecast(history, 1000, aggregate).toMap();

        // Baseline diff summary
        Map<String, Object> baselineDiff = new LinkedHashMap<>();
        baselineDiff.put("has_previous", true);
        baselineDiff.put("previous_at", "2026-02-04 14:22 UTC");
        baselineDiff.put("notable", List.of(
                "POST /payment rt_p95 up 18% vs previous baseline",
                "POST /checkout error_rate up +1.4pp"));
        baselineDiff.put("overall", Map.of("rt_p95_pct", 18.4, "error_rate_pp", 0.4));

        // Insights
        Map<String, Object> insights = new LinkedHashMap<>();
        insights.put("structured", true);
        insights.put("markdown",
                "## Root cause hypothesis\n" +
                "Payment gateway upstream is the dominant contributor to p99 " +
                "(4.5x p95 on POST /payment). Checkout errors correlate with " +
                "the same latency spikes.\n\n" +
                "## Recommended actions\n" +
                "1. Enable circuit breaker on payment client (2s open threshold).\n" +
                "2. Add idempotency retries on `/checkout` with jitter.\n" +
                "3. Scale API replicas to 6 during 15:00-19:00 UTC.\n");
        insights.put("business_impact", Map.of(
                "lost_conversions_est", 84,
                "usd_est", 3_780,
                "note", "Assuming $45 AOV and current checkout drop-off."));
        insights.put("capacity_estimate", Map.of(
                "peak_supported_tps", 260,
                "cliff_tps", 320,
                "months_headroom", 4));

        // Build context
        ReportGenerator.Context ctx = new ReportGenerator.Context.Builder()
                .testName("demo-checkout-load-2.0.3")
                .environment("staging")
                .application("smart-shop")
                .startMs((long) overall.get("start_ms"))
                .stopMs((long) overall.get("stop_ms"))
                .aggregate(aggregate)
                .verdict(verdict)
                .scores(scores)
                .findings(findings)
                .baselineDiff(baselineDiff)
                .externalMetrics(ext)
                .forecast(forecast)
                .aiInsights(insights)
                .llmAnalysis(String.valueOf(insights.get("markdown")))
                .llmProvider("openai")
                .llmModel("gpt-5.2")
                .build();

        // Write
        Path htmlPath = outDir.resolve("Performance_Report.html");
        new ReportGenerator().generate(ctx, htmlPath.toString());

        Path pdfPath = outDir.resolve("Performance_Report.pdf");
        new PdfExporter().export(htmlPath, pdfPath);

        Map<String, Object> envelope = JsonExporter.envelope(
                "report.v2.json",
                "demo-" + now,
                "demo-checkout-load-2.0.3", "staging", "smart-shop",
                aggregate, scores, verdict, findings,
                baselineDiff, Map.of("failure_count", 148L, "window_seconds", 30L,
                        "windows", List.of()),
                Map.of(), Map.of(), insights, ext, forecast);
        new JsonExporter().export(outDir.resolve("Performance_Report.json"), envelope);

        new PptxExporter().export(envelope, outDir.resolve("Performance_Report.pptx"));
        new CsvExporter().export(outDir.resolve("Performance_Report.csv"), aggregate);

        // Cleanup temp history dir
        try (var s = Files.walk(history)) {
            s.sorted((a, b) -> b.getNameCount() - a.getNameCount())
             .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignore) { } });
        }

        System.out.println("[demo] Wrote demo artefacts into " + outDir);
        try (var s = Files.list(outDir)) {
            s.forEach(p -> System.out.println("  - " + p.getFileName() + "  (" + safeSize(p) + " bytes)"));
        }
    }

    private static long safeSize(Path p) {
        try { return Files.size(p); } catch (Exception e) { return -1; }
    }

    private static Map<String, Object> txn(long count, long errors, int min, int avg, int p95, int max, int median) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", count);
        m.put("errors", errors);
        m.put("rt_min_ms", min);
        m.put("rt_avg_ms", avg);
        m.put("rt_median_ms", median);
        m.put("rt_p95_ms", p95);
        m.put("rt_max_ms", max);
        m.put("error_rate", count == 0 ? 0 : (double) errors / count);
        return m;
    }

    private static Map<String, Object> finding(String id, String title, String cat,
                                               String sev, double conf, String ev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("category", cat);
        m.put("severity", sev);
        m.put("confidence", conf);
        m.put("evidence", ev);
        return m;
    }

    private static Map<String, Object> pt(double v) {
        return Map.of("value", v);
    }
}
