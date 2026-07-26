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
 * v2.0.6 regression: batch endpoint is POST /v2/timeserieswindow. The
 * SignalFlow fallback was removed - it required job orchestration and
 * returned 406 on realms that only serve the batch endpoint.
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
    void usesPostAndReturnsPoints() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        server.createContext("/v2/timeserieswindow", (HttpExchange x) -> {
            method.set(x.getRequestMethod());
            token.set(x.getRequestHeaders().getFirst("X-SF-TOKEN"));
            body.set(new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
        assertEquals("POST", method.get());
        assertEquals("tok-abc", token.get());
        assertTrue(body.get().contains("\"program\""));
        assertTrue(body.get().contains("\"startMs\""));
        assertEquals(2, pts.size());
        assertEquals(0.42, ((Number) pts.get(0).get("value")).doubleValue(), 0.001);
    }

    @Test
    void notFoundReturnsEmptyList() throws Exception {
        server.createContext("/v2/timeserieswindow", x -> {
            x.sendResponseHeaders(404, -1);
            x.close();
        });
        server.start();
        SplunkO11yMetricsClient c = new SplunkO11yMetricsClient(
                "http://127.0.0.1:" + port, "tok");
        List<Map<String, Object>> pts = c.fetch("cpu.utilization", 1L, 2L);
        assertTrue(pts.isEmpty(), "404 must return empty list (no SignalFlow fallback)");
    }

    @Test
    void nonJsonErrorLogsAndReturnsEmpty() throws Exception {
        server.createContext("/v2/timeserieswindow", x -> {
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
}
