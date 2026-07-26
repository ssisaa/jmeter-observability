package com.smartjmeter.o11y;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v2.0.7 regression: bounded batch fetch uses
 * {@code GET /v1/timeserieswindow?query=sf_metric:"..."&startMs=..&endMs=..&resolution=..}.
 * /v2/timeserieswindow does not exist (404) and SignalFlow streams
 * indefinitely, so neither is used.
 */
class SplunkO11yMetricsClientPostTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void usesGetV1AndReturnsPoints() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        server.createContext("/v1/timeserieswindow", (HttpExchange x) -> {
            method.set(x.getRequestMethod());
            token.set(x.getRequestHeaders().getFirst("X-SF-Token"));
            query.set(x.getRequestURI().getRawQuery());
            String resp = "{\"data\":{\"cpu-tsid\":[[1700000000000,0.42],[1700000060000,0.55]]}}";
            byte[] out = resp.getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().set("Content-Type", "application/json");
            x.sendResponseHeaders(200, out.length);
            try (OutputStream os = x.getResponseBody()) { os.write(out); }
        });
        server.start();

        SplunkO11yMetricsClient c = new SplunkO11yMetricsClient(
                "http://127.0.0.1:" + port, "tok-abc");
        List<Map<String, Object>> pts = c.fetch("cpu.utilization", 1_700_000_000_000L, 1_700_000_060_000L);
        assertEquals("GET", method.get());
        assertEquals("tok-abc", token.get());
        assertNotNull(query.get(), "query string must be present");
        assertTrue(query.get().contains("query="), "query param missing: " + query.get());
        assertTrue(query.get().contains("startMs=1700000000000"), "startMs missing: " + query.get());
        assertTrue(query.get().contains("endMs=1700000060000"), "endMs missing: " + query.get());
        assertTrue(query.get().contains("resolution="), "resolution missing: " + query.get());
        assertTrue(query.get().contains("sf_metric"), "sf_metric wrapping missing: " + query.get());
        assertEquals(2, pts.size());
        assertEquals(0.42, ((Number) pts.get(0).get("value")).doubleValue(), 0.001);
        assertEquals("cpu-tsid", pts.get(0).get("tsid"));
    }

    @Test
    void notFoundReturnsEmptyList() throws Exception {
        server.createContext("/v1/timeserieswindow", x -> {
            x.sendResponseHeaders(404, -1);
            x.close();
        });
        server.start();
        SplunkO11yMetricsClient c = new SplunkO11yMetricsClient(
                "http://127.0.0.1:" + port, "tok");
        List<Map<String, Object>> pts = c.fetch("cpu.utilization", 1L, 2L);
        assertTrue(pts.isEmpty(), "404 must return empty list (no fallback endpoint)");
    }

    @Test
    void authFailureReturnsEmpty() throws Exception {
        server.createContext("/v1/timeserieswindow", x -> {
            x.sendResponseHeaders(401, -1);
            x.close();
        });
        server.start();
        SplunkO11yMetricsClient c = new SplunkO11yMetricsClient(
                "http://127.0.0.1:" + port, "bad-token");
        assertTrue(c.fetch("cpu.utilization", 1L, 2L).isEmpty());
    }

    @Test
    void nonJsonErrorLogsAndReturnsEmpty() throws Exception {
        server.createContext("/v1/timeserieswindow", x -> {
            byte[] out = "<html>500</html>".getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().set("Content-Type", "text/html");
            x.sendResponseHeaders(500, out.length);
            try (OutputStream os = x.getResponseBody()) { os.write(out); }
        });
        server.start();
        SplunkO11yMetricsClient c = new SplunkO11yMetricsClient(
                "http://127.0.0.1:" + port, "tok");
        assertTrue(c.fetch("m", 0L, 1L).isEmpty());
    }

    @Test
    void bareMetricIsWrappedAsSfMetricQuery() {
        assertEquals("sf_metric:\"cpu.utilization\"",
                SplunkO11yMetricsClient.toQuery("cpu.utilization"));
    }

    @Test
    void signalFlowProgramIsDowngradedToSfMetric() {
        assertEquals("sf_metric:\"memory.utilization\"",
                SplunkO11yMetricsClient.toQuery("data('memory.utilization').publish()"));
        assertEquals("sf_metric:\"foo\"",
                SplunkO11yMetricsClient.toQuery("data(\"foo\").publish()"));
    }

    @Test
    void rawFilterExpressionsArePreserved() {
        assertEquals("sf_metric:\"cpu.utilization\" AND host:web1",
                SplunkO11yMetricsClient.toQuery("sf_metric:\"cpu.utilization\" AND host:web1"));
    }

    @Test
    void resolutionIsSnappedToValidBucket() {
        assertEquals(60_000L, SplunkO11yMetricsClient.snapResolution(10_000L));
        assertEquals(1_000L, SplunkO11yMetricsClient.snapResolution(500L));
        assertEquals(1_000L, SplunkO11yMetricsClient.snapResolution(1_000L));
        assertEquals(300_000L, SplunkO11yMetricsClient.snapResolution(240_000L));
        assertEquals(3_600_000L, SplunkO11yMetricsClient.snapResolution(4_000_000L));
    }
}
