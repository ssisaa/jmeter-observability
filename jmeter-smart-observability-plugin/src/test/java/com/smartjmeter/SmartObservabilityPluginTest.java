package com.smartjmeter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjmeter.ai.AIAnalyzer;
import com.smartjmeter.model.JMeterMetric;
import com.smartjmeter.report.ReportGenerator;
import com.smartjmeter.splunk.SplunkHECClient;
import com.smartjmeter.store.LocalJsonStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SmartObservabilityPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void metricSerialisesToJson() throws Exception {
        JMeterMetric metric = sampleMetric();
        String json = mapper.writeValueAsString(metric);
        assertTrue(json.contains("\"transaction\":\"Login\""));
        assertTrue(json.contains("\"responseTime\":123"));
        assertTrue(json.contains("\"success\":true"));

        JMeterMetric round = mapper.readValue(json, JMeterMetric.class);
        assertEquals("Login", round.getTransaction());
        assertEquals(123L, round.getResponseTime());
        assertTrue(round.isSuccess());
    }

    @Test
    void splunkPayloadWrapsEventEnvelope() throws Exception {
        SplunkHECClient client = new SplunkHECClient("https://example/collector", "tok", "performance");
        String payload = client.buildPayload(sampleMetric());
        assertTrue(payload.contains("\"event\""));
        assertTrue(payload.contains("\"sourcetype\": \"jmeter\""));
        assertTrue(payload.contains("\"index\": \"performance\""));
        // Ensure the inner event is valid JSON with the metric fields.
        assertTrue(payload.contains("\"transaction\":\"Login\""));
    }

    @Test
    void splunkSendWithoutConfigIsNoop() {
        // Missing url/token should not throw or hang the caller.
        assertDoesNotThrow(() -> new SplunkHECClient("", "", "performance").send(sampleMetric()));
    }

    @Test
    void localJsonStoreAppendsNdjson(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("metrics.json");
        LocalJsonStore store = new LocalJsonStore(file.toString());
        store.append(sampleMetric());
        store.appendAll(List.of(sampleMetric(), sampleMetric()));

        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        for (String line : lines) {
            JMeterMetric m = mapper.readValue(line, JMeterMetric.class);
            assertEquals("Login", m.getTransaction());
        }
    }

    @Test
    void aiAnalyzerReturnsAnalysisText() {
        String text = new AIAnalyzer().analyze("{}");
        assertNotNull(text);
        assertTrue(text.contains("Performance Analysis"));
        assertTrue(text.contains("Recommendation"));
    }

    @Test
    void reportGeneratorWritesHtml(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("report.html");
        Path written = new ReportGenerator().generate("some analysis <b>bold</b>", out.toString());
        assertEquals(out, written);
        String html = Files.readString(out);
        assertTrue(html.contains("<h1>AI Performance Report</h1>"));
        // Angle brackets from the analysis body are escaped.
        assertTrue(html.contains("&lt;b&gt;bold&lt;/b&gt;"));
    }

    private JMeterMetric sampleMetric() {
        JMeterMetric metric = new JMeterMetric();
        metric.setTestName("Perf-Regression");
        metric.setTransaction("Login");
        metric.setResponseTime(123L);
        metric.setLatency(80L);
        metric.setBytesSent(512L);
        metric.setBytesReceived(4096L);
        metric.setSuccess(true);
        metric.setTimestamp(1_700_000_000_000d);
        metric.setEnvironment("perf");
        metric.setApplication("Migration-System");
        metric.setResponseCode("200");
        metric.setThreadName("thread-1");
        return metric;
    }
}
