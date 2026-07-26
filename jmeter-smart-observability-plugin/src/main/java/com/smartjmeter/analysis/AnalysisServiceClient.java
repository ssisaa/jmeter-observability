package com.smartjmeter.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjmeter.util.HttpClientFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * v2.0.3 client for the shared {@link AnalysisServer}.
 *
 * <p>Used when {@code Analysis_Service_Url} is set. Falls back to a
 * static-analysis payload on any error so the report pipeline never
 * crashes because a shared service is down.</p>
 */
public final class AnalysisServiceClient {

    private static final Logger LOG = Logger.getLogger(AnalysisServiceClient.class.getName());
    private static final ObjectMapper M = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http;

    public AnalysisServiceClient(String baseUrl, boolean insecureTls) {
        this.baseUrl = strip(baseUrl);
        this.http = HttpClientFactory.create(insecureTls);
    }

    /** POST {systemPrompt, userPrompt} and return the parsed insights map. */
    public Map<String, Object> analyze(String systemPrompt, String userPrompt) {
        if (baseUrl.isBlank()) return staticFallback("analysis-service-url-blank");
        try {
            String body = M.writeValueAsString(Map.of(
                    "systemPrompt", systemPrompt == null ? "" : systemPrompt,
                    "userPrompt", userPrompt == null ? "" : userPrompt));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/analyze"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 300) {
                LOG.log(Level.WARNING, "analysis-service HTTP {0}: {1}",
                        new Object[]{r.statusCode(), snippet(r.body())});
                return staticFallback("http_" + r.statusCode());
            }
            return M.readValue(r.body(), Map.class);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "analysis-service call failed", e);
            return staticFallback(e.getClass().getSimpleName());
        }
    }

    /** Ping the {@code /healthz} endpoint. */
    public boolean isHealthy() {
        if (baseUrl.isBlank()) return false;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/healthz"))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 && r.body().replaceAll("\\s+", "").contains("\"ok\":true");
        } catch (Exception e) {
            return false;
        }
    }

    private static Map<String, Object> staticFallback(String reason) {
        return Map.of("structured", false,
                "markdown", "Static analysis (analysis service unavailable: " + reason + ").",
                "fallback_reason", reason);
    }

    private static String strip(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String snippet(String s) {
        return s == null ? "" : s.substring(0, Math.min(200, s.length()));
    }
}
