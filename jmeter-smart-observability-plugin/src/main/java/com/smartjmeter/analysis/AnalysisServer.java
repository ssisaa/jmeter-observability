package com.smartjmeter.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjmeter.ai.InsightExtractor;
import com.smartjmeter.ai.LlmClient;
import com.smartjmeter.ai.PromptBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * v2.0.3 Analysis Service.
 *
 * <p>Runs a lightweight JDK {@code HttpServer} exposing two endpoints
 * so multiple JMeter runners in a CI fleet can share one LLM budget:</p>
 * <ul>
 *   <li>{@code GET /healthz} - returns {@code {"ok":true,"version":"2.0.3"}}</li>
 *   <li>{@code POST /analyze} - accepts a JSON body with the same shape
 *       {@link PromptBuilder#buildUserPrompt} consumes and returns the
 *       {@link InsightExtractor} result.</li>
 * </ul>
 *
 * <p>Configure via environment variables:</p>
 * <pre>
 *   ANALYSIS_HOST         (default 0.0.0.0)
 *   ANALYSIS_PORT         (default 7788)
 *   ANALYSIS_PROVIDER     openai | anthropic | gemini | grok | groq (default openai)
 *   ANALYSIS_MODEL        model name (optional)
 *   ANALYSIS_LLM_API_KEY  provider API key (required, empty = static fallback)
 *   ANALYSIS_LLM_BASE_URL override endpoint (optional)
 * </pre>
 *
 * <p>Startup:
 * {@code java -cp jmeter-smart-observability-plugin-2.0.3.jar com.smartjmeter.analysis.AnalysisServer}
 * </p>
 */
public final class AnalysisServer {

    private static final Logger LOG = Logger.getLogger(AnalysisServer.class.getName());
    private static final ObjectMapper M = new ObjectMapper();
    private static final String VERSION = "2.0.3";

    private final HttpServer server;
    private final String provider;
    private final String model;
    private final String apiKey;
    private final String baseUrl;

    public AnalysisServer(String host, int port,
                          String provider, String model, String apiKey, String baseUrl) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.provider = provider == null || provider.isBlank() ? "openai" : provider;
        this.model = model;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.baseUrl = baseUrl;
        this.server.setExecutor(Executors.newFixedThreadPool(4));
        this.server.createContext("/healthz", this::handleHealth);
        this.server.createContext("/analyze", this::handleAnalyze);
    }

    public void start() {
        server.start();
        LOG.log(Level.INFO, "AnalysisServer listening on {0}:{1} provider={2} model={3}",
                new Object[]{server.getAddress().getHostString(), server.getAddress().getPort(),
                             provider, model == null ? "(default)" : model});
    }

    public void stop(int seconds) {
        server.stop(Math.max(0, seconds));
    }

    /** Bound port (0 when server not yet started). */
    public int port() { return server.getAddress().getPort(); }

    /* ---------------- handlers ---------------- */

    private void handleHealth(HttpExchange x) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("version", VERSION);
        body.put("provider", provider);
        body.put("model_configured", !(model == null || model.isBlank()));
        body.put("api_key_configured", !apiKey.isBlank());
        writeJson(x, 200, body);
    }

    private void handleAnalyze(HttpExchange x) throws IOException {
        if (!"POST".equalsIgnoreCase(x.getRequestMethod())) {
            writeJson(x, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        try {
            String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> req = M.readValue(body.isEmpty() ? "{}" : body, Map.class);
            if (apiKey.isBlank()) {
                writeJson(x, 200, Map.of(
                        "structured", false,
                        "markdown", "Static analysis (analysis service has no LLM key configured)."
                ));
                return;
            }
            LlmClient client = new LlmClient(
                    LlmClient.Provider.parse(provider), model, apiKey, baseUrl)
                    .withMaxTokens(2048);

            // Accept a pre-built userPrompt in the request body.
            String system = String.valueOf(req.getOrDefault("systemPrompt", PromptBuilder.SYSTEM_PROMPT));
            String userPrompt = String.valueOf(req.getOrDefault("userPrompt", ""));
            if (userPrompt.isBlank()) {
                writeJson(x, 400, Map.of("error", "missing_field",
                        "message", "'userPrompt' is required (build via PromptBuilder.buildUserPrompt)"));
                return;
            }
            String raw = client.chat(system, userPrompt);
            Map<String, Object> out = InsightExtractor.extract(raw);
            writeJson(x, 200, out);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "analyze failed", e);
            writeJson(x, 500, Map.of("error", e.getClass().getSimpleName(),
                    "message", e.getMessage() == null ? "" : e.getMessage()));
        }
    }

    private static void writeJson(HttpExchange x, int code, Object body) throws IOException {
        byte[] bytes = M.writerWithDefaultPrettyPrinter().writeValueAsBytes(body);
        x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        x.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = x.getResponseBody()) { os.write(bytes); }
    }

    /* ---------------- main ---------------- */

    public static void main(String[] args) throws Exception {
        String host = envOr("ANALYSIS_HOST", "0.0.0.0");
        int port = Integer.parseInt(envOr("ANALYSIS_PORT", "7788"));
        String provider = envOr("ANALYSIS_PROVIDER", "openai");
        String model = envOr("ANALYSIS_MODEL", "");
        String apiKey = envOr("ANALYSIS_LLM_API_KEY", "");
        String baseUrl = envOr("ANALYSIS_LLM_BASE_URL", "");
        AnalysisServer s = new AnalysisServer(host, port, provider, model, apiKey, baseUrl);
        s.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> s.stop(2)));
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
