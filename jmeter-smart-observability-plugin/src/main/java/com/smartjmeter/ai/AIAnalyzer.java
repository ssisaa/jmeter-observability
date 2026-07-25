package com.smartjmeter.ai;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the LLM analysis pipeline (or a static fallback) at teardown.
 *
 * <p>The previous static-only skeleton is preserved as
 * {@link #staticAnalysis()} and used whenever the LLM is not configured
 * or the provider call throws.</p>
 */
public class AIAnalyzer {

    private static final Logger LOG = Logger.getLogger(AIAnalyzer.class.getName());

    private final LlmClient llm;

    /** Backwards-compatible no-arg ctor: LLM disabled, static output only. */
    public AIAnalyzer() {
        this.llm = null;
    }

    public AIAnalyzer(LlmClient llm) {
        this.llm = llm;
    }

    /**
     * Legacy entry point kept for callers that hand in a pre-serialised
     * blob of performance data. Delegates to the static analysis so old
     * behaviour is preserved when the LLM is unavailable.
     */
    public String analyze(String performanceData) {
        return staticAnalysis();
    }

    /** New rich-context entry point without a baseline diff. */
    public String analyze(Map<String, Object> aggregateSummary,
                          Map<String, Object> correlation,
                          Map<String, List<Map<String, Object>>> o11yMetrics) {
        return analyze(aggregateSummary, correlation, o11yMetrics, null);
    }

    /**
     * Rich entry point with an optional baseline diff. When
     * {@code baselineDiff.has_previous == true} the diff is embedded in
     * the LLM prompt as its own section.
     */
    public String analyze(Map<String, Object> aggregateSummary,
                          Map<String, Object> correlation,
                          Map<String, List<Map<String, Object>>> o11yMetrics,
                          Map<String, Object> baselineDiff) {
        if (llm == null || !llm.isConfigured()) {
            return staticAnalysis();
        }
        try {
            String userPrompt = PromptBuilder.buildUserPrompt(
                    aggregateSummary, correlation, o11yMetrics, baselineDiff);
            String reply = llm.chat(PromptBuilder.SYSTEM_PROMPT, userPrompt);
            if (reply == null || reply.isBlank()) {
                return staticAnalysis();
            }
            return reply;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM analysis failed - falling back to static output", e);
            return staticAnalysis();
        }
    }

    public static String staticAnalysis() {
        return """
                Performance Analysis

                Finding:
                Response time degradation detected.

                Possible Cause:
                Infrastructure resource saturation.

                Recommendation:
                Review JVM,
                Database,
                and external dependency metrics.
                """;
    }
}
