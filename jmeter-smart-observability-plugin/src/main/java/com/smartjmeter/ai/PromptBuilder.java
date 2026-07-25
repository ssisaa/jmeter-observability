package com.smartjmeter.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;
import java.util.Map;

/**
 * Assembles the LLM prompt from three artefacts produced during a
 * JMeter run: an aggregate summary of samples, the correlated Splunk
 * log windows, and the Splunk O11y metric points.
 */
public class PromptBuilder {

    private static final ObjectMapper PRETTY = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static final String SYSTEM_PROMPT = """
            You are a senior Site Reliability Engineer analysing the results of a
            JMeter performance test. Given aggregate metrics, correlated log
            windows, and infrastructure telemetry, produce a concise, actionable
            report with these sections:

              1. Executive Summary (2-3 lines)
              2. Key Findings (bullet list)
              3. Probable Root Causes (ranked, with evidence citations)
              4. Recommended Actions (prioritised)

            Prefer plain text. Do not fabricate metrics. If a section has no
            supporting evidence, say "insufficient data".
            """;

    public static String buildUserPrompt(Map<String, Object> aggregateSummary,
                                         Map<String, Object> correlation,
                                         Map<String, List<Map<String, Object>>> o11yMetrics) {
        try {
            String summaryJson = PRETTY.writeValueAsString(aggregateSummary);
            String correlationJson = PRETTY.writeValueAsString(correlation);
            String metricsJson = PRETTY.writeValueAsString(o11yMetrics);
            return """
                    Performance run artefacts (JSON):

                    ## Aggregate Sample Summary
                    %s

                    ## Correlated Splunk Log Windows
                    %s

                    ## Splunk Observability Cloud Metrics
                    %s

                    Produce the report now.
                    """.formatted(summaryJson, correlationJson, metricsJson);
        } catch (Exception e) {
            return "Aggregate: " + aggregateSummary
                    + "\nCorrelation: " + correlation
                    + "\nO11y: " + o11yMetrics;
        }
    }
}
