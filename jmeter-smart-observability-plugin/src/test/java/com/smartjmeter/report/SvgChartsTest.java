package com.smartjmeter.report;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SvgChartsTest {

    @Test
    void waterfallProducesSvg() {
        Map<String, Object> agg = Map.of("per_transaction", Map.of(
                "GET /cart", Map.of("rt_median_ms", 40, "rt_p95_ms", 120, "rt_max_ms", 300, "count", 500, "errors", 0),
                "POST /checkout", Map.of("rt_median_ms", 80, "rt_p95_ms", 900, "rt_max_ms", 1800, "count", 200, "errors", 5)
        ));
        String svg = SvgCharts.waterfall(agg, 5);
        assertTrue(svg.startsWith("<svg"), "expected svg root");
        assertTrue(svg.contains("checkout"));
        assertTrue(svg.contains("cart"));
        assertTrue(svg.endsWith("</svg>"));
    }

    @Test
    void waterfallEmptyOnEmpty() {
        assertEquals("", SvgCharts.waterfall(Map.of(), 5));
    }

    @Test
    void sankeyDrawsPassAndFail() {
        Map<String, Object> verdict = Map.of(
                "level", "GO_WITH_CONDITIONS",
                "gates_passed", List.of("SLA", "Regression"),
                "gates_failed", List.of("No critical findings"));
        String svg = SvgCharts.verdictSankey(verdict);
        assertTrue(svg.contains("GO WITH CONDITIONS"));
        assertTrue(svg.contains("SLA"));
        assertTrue(svg.contains("No critical findings"));
    }

    @Test
    void sankeyEmptyWhenNoGates() {
        Map<String, Object> verdict = Map.of("level", "GO",
                "gates_passed", List.of(), "gates_failed", List.of());
        assertEquals("", SvgCharts.verdictSankey(verdict));
    }

    @Test
    void dependencyMapEscapesLabels() {
        Map<String, Object> agg = Map.of("per_transaction", Map.of(
                "a<b>c", Map.of("count", 10, "errors", 0),
                "safe", Map.of("count", 5, "errors", 2)
        ));
        String svg = SvgCharts.dependencyMap(agg);
        assertTrue(svg.contains("a&lt;b&gt;c"), "label must be escaped");
    }
}
