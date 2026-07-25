package com.smartjmeter.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal single-turn LLM chat client that speaks to four provider APIs
 * directly over HTTPS from Java. Provider is selected by the
 * {@link Provider} enum, keeping the JMeter plugin dependency-free
 * beyond Jackson + the JDK.
 *
 * <p>The client is stateless — one instance issues one prompt and returns
 * the assistant text or throws.</p>
 */
public class LlmClient {

    private static final Logger LOG = Logger.getLogger(LlmClient.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum Provider {
        OPENAI, ANTHROPIC, GEMINI, GROK, GROQ;

        public static Provider parse(String s) {
            if (s == null) return OPENAI;
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "anthropic", "claude" -> ANTHROPIC;
                case "gemini", "google" -> GEMINI;
                case "grok", "xai" -> GROK;
                case "groq" -> GROQ;
                default -> OPENAI;
            };
        }

        public String defaultModel() {
            return switch (this) {
                case OPENAI -> "gpt-4o-mini";
                case ANTHROPIC -> "claude-sonnet-4-5-20250929";
                case GEMINI -> "gemini-2.5-flash";
                case GROK -> "grok-4.5";
                case GROQ -> "llama-3.3-70b-versatile";
            };
        }

        public String defaultBaseUrl() {
            return switch (this) {
                case OPENAI -> "https://api.openai.com";
                case ANTHROPIC -> "https://api.anthropic.com";
                case GEMINI -> "https://generativelanguage.googleapis.com";
                case GROK -> "https://api.x.ai";
                case GROQ -> "https://api.groq.com/openai";
            };
        }
    }

    private final Provider provider;
    private final String model;
    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private int maxTokens = 1024;
    private double temperature = 0.2;
    private Duration timeout = Duration.ofSeconds(60);

    public LlmClient(Provider provider, String model, String apiKey, String baseUrl) {
        this.provider = provider;
        this.model = (model == null || model.isBlank()) ? provider.defaultModel() : model;
        this.apiKey = apiKey;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank())
                ? provider.defaultBaseUrl()
                : stripTrailingSlash(baseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public LlmClient withMaxTokens(int v) { this.maxTokens = v; return this; }
    public LlmClient withTemperature(double v) { this.temperature = v; return this; }
    public LlmClient withTimeout(Duration d) { this.timeout = d; return this; }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Send a single-turn prompt (system + user) and return the assistant
     * text. On any error the exception is logged and rethrown to allow
     * the caller to fall back to a static analysis.
     */
    public String chat(String systemPrompt, String userPrompt) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("LLM API key not configured");
        }
        return switch (provider) {
            case OPENAI, GROK, GROQ -> callOpenAiCompatible(systemPrompt, userPrompt);
            case ANTHROPIC -> callAnthropic(systemPrompt, userPrompt);
            case GEMINI -> callGemini(systemPrompt, userPrompt);
        };
    }

    /* -------- Provider payload builders (exposed for unit testing) -------- */

    public String buildOpenAiBody(String systemPrompt, String userPrompt) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
        }
        ObjectNode usr = messages.addObject();
        usr.put("role", "user");
        usr.put("content", userPrompt);
        return MAPPER.writeValueAsString(body);
    }

    public String buildAnthropicBody(String systemPrompt, String userPrompt) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("system", systemPrompt);
        }
        ArrayNode messages = body.putArray("messages");
        ObjectNode usr = messages.addObject();
        usr.put("role", "user");
        usr.put("content", userPrompt);
        return MAPPER.writeValueAsString(body);
    }

    public String buildGeminiBody(String systemPrompt, String userPrompt) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = body.putObject("systemInstruction");
            ArrayNode parts = sys.putArray("parts");
            parts.addObject().put("text", systemPrompt);
        }
        ArrayNode contents = body.putArray("contents");
        ObjectNode msg = contents.addObject();
        msg.put("role", "user");
        ArrayNode parts = msg.putArray("parts");
        parts.addObject().put("text", userPrompt);
        ObjectNode gen = body.putObject("generationConfig");
        gen.put("temperature", temperature);
        gen.put("maxOutputTokens", maxTokens);
        return MAPPER.writeValueAsString(body);
    }

    /* -------- Response parsers (exposed for unit testing) -------- */

    public static String parseOpenAiResponse(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    public static String parseAnthropicResponse(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode content = root.path("content");
        if (!content.isArray() || content.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText());
            }
        }
        return sb.toString();
    }

    public static String parseGeminiResponse(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : parts) sb.append(p.path("text").asText());
        return sb.toString();
    }

    /* -------- HTTP callers -------- */

    private String callOpenAiCompatible(String system, String user) throws Exception {
        String body = buildOpenAiBody(system, user);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            LOG.log(Level.WARNING, "LLM {0} error {1}: {2}",
                    new Object[]{provider, resp.statusCode(), resp.body()});
            throw new RuntimeException("LLM " + provider + " HTTP " + resp.statusCode());
        }
        return parseOpenAiResponse(resp.body());
    }

    private String callAnthropic(String system, String user) throws Exception {
        String body = buildAnthropicBody(system, user);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/messages"))
                .timeout(timeout)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            LOG.log(Level.WARNING, "Anthropic error {0}: {1}",
                    new Object[]{resp.statusCode(), resp.body()});
            throw new RuntimeException("Anthropic HTTP " + resp.statusCode());
        }
        return parseAnthropicResponse(resp.body());
    }

    private String callGemini(String system, String user) throws Exception {
        String body = buildGeminiBody(system, user);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent?key=" + apiKey))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            LOG.log(Level.WARNING, "Gemini error {0}: {1}",
                    new Object[]{resp.statusCode(), resp.body()});
            throw new RuntimeException("Gemini HTTP " + resp.statusCode());
        }
        return parseGeminiResponse(resp.body());
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
