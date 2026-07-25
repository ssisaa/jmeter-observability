package com.smartjmeter.o11y;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SplunkO11yMetricsClientTest {

    @Test
    void wrapsBareMetricNameInSignalFlow() {
        assertEquals(
                "data('cpu.utilization').publish()",
                SplunkO11yMetricsClient.toProgramText("cpu.utilization"));
    }

    @Test
    void leavesFullProgramUntouched() {
        String p = "data('cpu').mean().publish()";
        assertEquals(p, SplunkO11yMetricsClient.toProgramText(p));
    }

    @Test
    void parsesTimeSeriesWindow() throws Exception {
        String json = """
                {
                  "data": {
                    "AAA": [[1700000000000, 12.5], [1700000010000, 13.1]],
                    "BBB": [[1700000000000, 40]]
                  }
                }
                """;
        List<Map<String, Object>> points = SplunkO11yMetricsClient.parseTimeSeriesWindow(json);
        assertEquals(3, points.size());
        assertEquals(1_700_000_000_000L, points.get(0).get("ts"));
        assertEquals("AAA", points.get(0).get("tsid"));
    }

    @Test
    void parsesEmptyOrMissingData() throws Exception {
        assertTrue(SplunkO11yMetricsClient.parseTimeSeriesWindow("{}").isEmpty());
        assertTrue(SplunkO11yMetricsClient.parseTimeSeriesWindow("{\"data\":{}}").isEmpty());
    }

    @Test
    void parsesMetricList() {
        assertEquals(List.of("cpu.utilization", "memory.utilization"),
                SplunkO11yMetricsClient.parseMetricList("cpu.utilization, memory.utilization ,"));
        assertTrue(SplunkO11yMetricsClient.parseMetricList("").isEmpty());
        assertTrue(SplunkO11yMetricsClient.parseMetricList(null).isEmpty());
    }

    @Test
    void fetchWithoutConfigReturnsEmptyMap() {
        SplunkO11yMetricsClient client = new SplunkO11yMetricsClient("", "");
        Map<String, List<Map<String, Object>>> out = client.fetchAll(
                List.of("cpu.utilization"), 0, 1000);
        assertEquals(1, out.size());
        assertTrue(out.get("cpu.utilization").isEmpty());
    }
}
