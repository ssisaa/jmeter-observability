package com.smartjmeter.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjmeter.model.JMeterMetric;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmAndAnalyzerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void providerParseAndDefaults() {
        assertEquals(LlmClient.Provider.OPENAI, LlmClient.Provider.parse(null));
        assertEquals(LlmClient.Provider.OPENAI, LlmClient.Provider.parse("openai"));
        assertEquals(LlmClient.Provider.ANTHROPIC, LlmClient.Provider.parse("Claude"));
        assertEquals(LlmClient.Provider.GEMINI, LlmClient.Provider.parse("google"));
        assertEquals(LlmClient.Provider.GROK, LlmClient.Provider.parse("xai"));
        assertEquals(LlmClient.Provider.GROQ, LlmClient.Provider.parse("groq"));

        assertEquals("gpt-4o-mini", LlmClient.Provider.OPENAI.defaultModel());
        assertEquals("claude-sonnet-4-5-20250929", LlmClient.Provider.ANTHROPIC.defaultModel());
        assertEquals("gemini-2.5-flash", LlmClient.Provider.GEMINI.defaultModel());
        assertEquals("grok-4.5", LlmClient.Provider.GROK.defaultModel());
        assertEquals("llama-3.3-70b-versatile", LlmClient.Provider.GROQ.defaultModel());
        assertEquals("https://api.groq.com/openai", LlmClient.Provider.GROQ.defaultBaseUrl());
    }

    @Test
    void openAiBodyShape() throws Exception {
        LlmClient c = new LlmClient(LlmClient.Provider.OPENAI, null, "sk-test", null);
        JsonNode body = mapper.readTree(c.buildOpenAiBody("sys", "hi"));
        assertEquals("gpt-4o-mini", body.get("model").asText());
        assertEquals("system", body.get("messages").get(0).get("role").asText());
        assertEquals("sys", body.get("messages").get(0).get("content").asText());
        assertEquals("user", body.get("messages").get(1).get("role").asText());
        assertEquals("hi", body.get("messages").get(1).get("content").asText());
    }

    @Test
    void anthropicBodyShape() throws Exception {
        LlmClient c = new LlmClient(LlmClient.Provider.ANTHROPIC, "claude-x", "k", null);
        JsonNode body = mapper.readTree(c.buildAnthropicBody("sys", "hi"));
        assertEquals("claude-x", body.get("model").asText());
        assertEquals("sys", body.get("system").asText());
        assertEquals("user", body.get("messages").get(0).get("role").asText());
        assertEquals("hi", body.get("messages").get(0).get("content").asText());
    }

    @Test
    void geminiBodyShape() throws Exception {
        LlmClient c = new LlmClient(LlmClient.Provider.GEMINI, "gemini-2.5-flash", "k", null);
        JsonNode body = mapper.readTree(c.buildGeminiBody("sys", "hi"));
        assertEquals("sys", body.get("systemInstruction").get("parts").get(0).get("text").asText());
        assertEquals("hi", body.get("contents").get(0).get("parts").get(0).get("text").asText());
    }

    @Test
    void parsesEachProviderResponse() throws Exception {
        String openai = """
                {"choices":[{"message":{"role":"assistant","content":"hello"}}]}""";
        assertEquals("hello", LlmClient.parseOpenAiResponse(openai));

        String anthropic = """
                {"content":[{"type":"text","text":"hi "},{"type":"text","text":"there"}]}""";
        assertEquals("hi there", LlmClient.parseAnthropicResponse(anthropic));

        String gemini = """
                {"candidates":[{"content":{"parts":[{"text":"g1"},{"text":"g2"}]}}]}""";
        assertEquals("g1g2", LlmClient.parseGeminiResponse(gemini));
    }

    @Test
    void aiAnalyzerFallsBackWhenNotConfigured() {
        String out = new AIAnalyzer().analyze(Map.of(), Map.of(), Map.of());
        assertTrue(out.contains("Performance Analysis"));
    }

    @Test
    void aiAnalyzerFallsBackWhenLlmThrows() {
        LlmClient broken = new LlmClient(LlmClient.Provider.OPENAI, null,
                "sk-invalid", "http://127.0.0.1:1"); // unreachable
        String out = new AIAnalyzer(broken).analyze(
                Map.of("overall", Map.of()), Map.of(), Map.of());
        assertTrue(out.contains("Performance Analysis"));
    }

    @Test
    void metricAggregatorProducesPerTransactionAndOverall() {
        JMeterMetric a = metric("Login", 100L, true, 1_700_000_000_000L);
        JMeterMetric b = metric("Login", 200L, false, 1_700_000_001_000L);
        JMeterMetric c = metric("Checkout", 300L, true, 1_700_000_002_000L);
        Map<String, Object> out = MetricAggregator.aggregate(List.of(a, b, c));

        Map<?, ?> overall = (Map<?, ?>) out.get("overall");
        assertEquals(3L, overall.get("count"));
        assertEquals(1L, overall.get("errors"));
        assertEquals(1_700_000_000_000L, overall.get("start_ms"));
        assertEquals(1_700_000_002_000L, overall.get("stop_ms"));

        Map<?, ?> perTxn = (Map<?, ?>) out.get("per_transaction");
        Map<?, ?> login = (Map<?, ?>) perTxn.get("Login");
        assertEquals(2L, login.get("count"));
        assertEquals(1L, login.get("errors"));
    }

    @Test
    void promptBuilderIncludesAllThreeSections() {
        String p = PromptBuilder.buildUserPrompt(
                Map.of("count", 10),
                null, null, List.of(),
                Map.of("failure_count", 2),
                Map.of("windows", List.of()),
                Map.of("cpu.utilization", List.of(Map.of("ts", 1L, "value", 80))),
                Map.of(), Map.of());
        assertTrue(p.contains("Aggregate Sample Summary"));
        assertTrue(p.contains("Correlated Splunk Log Windows"));
        assertTrue(p.contains("Splunk Observability Cloud Metrics"));
        assertTrue(p.contains("cpu.utilization"));
    }

    private JMeterMetric metric(String txn, long rt, boolean ok, long ts) {
        JMeterMetric m = new JMeterMetric();
        m.setTransaction(txn);
        m.setResponseTime(rt);
        m.setSuccess(ok);
        m.setTimestamp(ts);
        return m;
    }
}
