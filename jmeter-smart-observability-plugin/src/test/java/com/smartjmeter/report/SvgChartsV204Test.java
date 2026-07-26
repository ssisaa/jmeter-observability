package com.smartjmeter.report;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v2.0.6 - only tests the chart types still rendered by ReportGenerator
 * (latency percentile bars, latency histogram and transaction top-12 bars
 * were removed on user request).
 */
class SvgChartsV204Test {

    private static Map<String, Object> demoAgg() {
        long now = System.currentTimeMillis();
        return Map.ofEntries(
                Map.entry("overall", Map.ofEntries(
                        Map.entry("count", 10_000L),
                        Map.entry("errors", 42L),
                        Map.entry("error_rate", 0.0042),
                        Map.entry("rt_avg_ms", 180),
                        Map.entry("rt_median_ms", 150),
                        Map.entry("rt_p95_ms", 480),
                        Map.entry("rt_p99_ms", 940),
                        Map.entry("rt_max_ms", 2100),
                        Map.entry("throughput_rps", 120.5),
                        Map.entry("apdex_score", 0.87),
                        Map.entry("start_ms", now - 60_000L),
                        Map.entry("stop_ms", now))),
                Map.entry("per_transaction", Map.of(
                        "GET /home", Map.of("count", 4000L, "errors", 0L,
                                "rt_median_ms", 40, "rt_avg_ms", 60, "rt_p95_ms", 120,
                                "rt_p99_ms", 220, "rt_max_ms", 300),
                        "POST /checkout", Map.of("count", 2500L, "errors", 42L,
                                "rt_median_ms", 210, "rt_avg_ms", 260, "rt_p95_ms", 780,
                                "rt_p99_ms", 1300, "rt_max_ms", 2100))),
                Map.entry("time_series", Map.of(
                        "bucket_seconds", 1,
                        "first_bucket_ms", now - 60_000L,
                        "buckets", List.of(
                                List.of(now - 60_000L, 100L, 0L, 55.0, 40),
                                List.of(now - 59_000L, 130L, 2L, 62.0, 60),
                                List.of(now - 58_000L, 150L, 1L, 65.0, 80),
                                List.of(now - 57_000L, 120L, 4L, 70.0, 90),
                                List.of(now - 56_000L, 145L, 3L, 68.0, 100))))
        );
    }

    @Test
    void kpiStripProducesAllFourCards() {
        String svg = SvgCharts.kpiStrip(demoAgg());
        assertTrue(svg.contains("Total samples"));
        assertTrue(svg.contains("Error rate"));
        assertTrue(svg.contains("p95"));
        assertTrue(svg.contains("Apdex"));
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.endsWith("</svg>"));
    }

    @Test
    void tpsHpsRtsLinesRender() {
        Map<String, Object> agg = demoAgg();
        assertTrue(SvgCharts.tpsLine(agg).contains("Transactions per second"));
        assertTrue(SvgCharts.hpsLine(agg).contains("Hits per second"));
        assertTrue(SvgCharts.rtsLine(agg).contains("Response time series"));
        assertTrue(SvgCharts.errorRateLine(agg).contains("Error rate over time"));
    }

    @Test
    void vusersLineRendersWhenThreadsPresent() {
        String svg = SvgCharts.vusersLine(demoAgg());
        assertTrue(svg.contains("Active virtual users"));
    }

    @Test
    void baselineComparisonUsesHistory() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> history = List.of(
                Map.of("timestamp_ms", now - 5L * 86400_000L, "p95_ms", 420),
                Map.of("timestamp_ms", now - 3L * 86400_000L, "p95_ms", 440),
                Map.of("timestamp_ms", now - 1L * 86400_000L, "p95_ms", 460));
        String svg = SvgCharts.baselineComparisonBars(demoAgg(), history);
        assertTrue(svg.contains("Current run"));
        assertTrue(svg.contains("Previous baseline"));
        assertTrue(svg.contains("Historic avg"));
    }

    @Test
    void o11ySeriesRendersPoints() {
        String svg = SvgCharts.o11ySeries("cpu.utilization",
                List.of(Map.of("value", 0.42), Map.of("value", 0.55), Map.of("value", 0.61)));
        assertTrue(svg.contains("cpu.utilization"));
        assertTrue(svg.startsWith("<svg"));
    }

    @Test
    void hasAnyDetectsData() {
        assertTrue(SvgCharts.hasAny(demoAgg()));
        assertFalse(SvgCharts.hasAny(Map.of()));
    }
}
