package com.smartjmeter.analysis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisServerTest {

    private AnalysisServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        // Port 0 -> OS picks a free port
        server = new AnalysisServer("127.0.0.1", 0, "openai", "", "", "");
        server.start();
        port = server.port();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void healthReturnsOk() throws Exception {
        HttpResponse<String> r = get("/healthz");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().replaceAll("\\s+", "").contains("\"ok\":true"));
        assertTrue(r.body().contains("\"version\""));
        assertTrue(r.body().contains("2.0.3"));
    }

    @Test
    void analyzeGetIsRejected() throws Exception {
        HttpResponse<String> r = get("/analyze");
        assertEquals(405, r.statusCode());
    }

    @Test
    void analyzeWithoutApiKeyReturnsStaticFallback() throws Exception {
        HttpResponse<String> r = post("/analyze", "{\"userPrompt\":\"hi\"}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("Static analysis"));
    }

    @Test
    void analyzeMissingUserPromptWithKeyReturns400() throws Exception {
        // Restart with a fake key so we hit the userPrompt validation.
        server.stop(0);
        server = new AnalysisServer("127.0.0.1", 0, "openai", "gpt-5.2", "dummy-key", "");
        server.start();
        port = server.port();
        HttpResponse<String> r = post("/analyze", "{}");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("missing_field"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
