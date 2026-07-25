package com.smartjmeter.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;
import java.util.Map;

/**
 * Assembles the LLM prompt from four artefacts produced during a
 * JMeter run: aggregate summary, correlated Splunk log windows,
 * Splunk O11y metric points, and (optionally) a baseline diff
 * highlighting per-transaction regressions.
 */
public class PromptBuilder {

    private static final ObjectMapper PRETTY = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static final String SYSTEM_PROMPT = """
            You are a senior Site Reliability Engineer analysing the results of a
            JMeter performance test. Given aggregate metrics, correlated log
            windows, infrastructure telemetry, and (when present) a diff against
            the previous run baseline, produce a concise, actionable report with
            these sections:

              1. Executive Summary (2-3 lines)
              2. Key Findings (bullet list)
              3. Regressions vs. Baseline (only if the baseline diff has entries;
                 quote the specific transactions and %/pp changes)
              4. Probable Root Causes (ranked, with evidence citations)
              5. Recommended Actions (prioritised)

            Prefer plain text. Do not fabricate metrics. If a section has no
            supporting evidence, say "insufficient data".
            """;

    public static String buildUserPrompt(Map<String, Object> aggregateSummary,
                                         Map<String, Object> correlation,
                                         Map<String, List<Map<String, Object>>> o11yMetrics) {
        return buildUserPrompt(aggregateSummary, correlation, o11yMetrics, null);
    }

    public static String buildUserPrompt(Map<String, Object> aggregateSummary,
                                         Map<String, Object> correlation,
                                         Map<String, List<Map<String, Object>>> o11yMetrics,
                                         Map<String, Object> baselineDiff) {
        try {
            String summaryJson = PRETTY.writeValueAsString(aggregateSummary);
            String correlationJson = PRETTY.writeValueAsString(correlation);
            String metricsJson = PRETTY.writeValueAsString(o11yMetrics);
            String diffSection = renderDiffSection(baselineDiff);
            return """
                    Performance run artefacts (JSON):

                    ## Aggregate Sample Summary
                    %s

                    ## Correlated Splunk Log Windows
                    %s

                    ## Splunk Observability Cloud Metrics
                    %s
                    %s
                    Produce the report now.
                    """.formatted(summaryJson, correlationJson, metricsJson, diffSection);
        } catch (Exception e) {
            return "Aggregate: " + aggregateSummary
                    + "\nCorrelation: " + correlation
                    + "\nO11y: " + o11yMetrics
                    + "\nBaseline diff: " + baselineDiff;
        }
    }

    private static String renderDiffSection(Map<String, Object> baselineDiff) throws Exception {
        if (baselineDiff == null || baselineDiff.isEmpty()
                || !Boolean.TRUE.equals(baselineDiff.get("has_previous"))) {
            return "";
        }
        return "\n## Baseline Diff (vs. previous run at "
                + baselineDiff.getOrDefault("previous_at", "?") + ")\n"
                + PRETTY.writeValueAsString(baselineDiff)
                + "\n";
    }
}
