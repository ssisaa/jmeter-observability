package com.smartjmeter.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smartjmeter.score.Finding;
import com.smartjmeter.score.HealthScores;
import com.smartjmeter.score.Verdict;

import java.util.List;
import java.util.Map;

/**
 * Assembles the executive-grade LLM prompt from the full run context.
 * The output contract is JSON matching {@link InsightExtractor#OUTPUT_SCHEMA}
 * with a {@code markdown_report} field for humans.
 */
public class PromptBuilder {

    private static final ObjectMapper PRETTY = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static final String SYSTEM_PROMPT = """
            You are a Principal SRE preparing the executive performance report for a
            release readiness review. You will be given: aggregate JMeter metrics,
            deterministic health scores, deterministic rule-engine findings, a
            baseline diff versus the previous run, correlated Splunk log windows,
            Splunk Observability metrics, and AWS CloudWatch metrics + alarm states.

            Return STRICT JSON matching the provided schema. Do not fabricate metrics.
            Cite the field name that supports each claim in the "evidence" field.
            If a section has no supporting evidence, return an empty array / object
            with note "insufficient data".

            The markdown_report field must contain the same content structured with
            the following headers exactly, in this order:
              # Executive Summary
              # Overall Test Verdict
              # Release Readiness
              # Business Impact
              # Regressions vs. Baseline
              # Key Findings
              # Root Cause Analysis
              # Recommendations
            """;

    public static String buildUserPrompt(Map<String, Object> aggregateSummary,
                                         HealthScores scores,
                                         Verdict verdict,
                                         List<Finding> findings,
                                         Map<String, Object> baselineDiff,
                                         Map<String, Object> correlation,
                                         Map<String, List<Map<String, Object>>> o11yMetrics,
                                         Map<String, Object> cloudwatch,
                                         Map<String, Object> businessImpactCfg) {
        try {
            return """
                    Return a single JSON object matching this schema:

                    %s

                    Run context (JSON):

                    ## Aggregate Sample Summary
                    %s

                    ## Deterministic Health Scores
                    %s

                    ## Deterministic Verdict (pre-LLM)
                    %s

                    ## Deterministic Findings
                    %s

                    ## Baseline Diff (vs. previous run)
                    %s

                    ## Correlated Splunk Log Windows
                    %s

                    ## Splunk Observability Cloud Metrics
                    %s

                    ## AWS CloudWatch Metrics + Alarms
                    %s

                    ## Business Impact Configuration
                    %s

                    Produce the JSON report now.
                    """.formatted(
                    InsightExtractor.OUTPUT_SCHEMA,
                    PRETTY.writeValueAsString(aggregateSummary),
                    PRETTY.writeValueAsString(scores == null ? Map.of() : scores.toMap()),
                    PRETTY.writeValueAsString(verdict == null ? Map.of() : verdict.toMap()),
                    PRETTY.writeValueAsString(findings == null ? List.of() : findings.stream().map(f -> Map.of(
                            "id", f.ruleId(), "title", f.title(), "severity", f.severity().name(),
                            "confidence", f.confidence(), "evidence", f.evidence())).toList()),
                    PRETTY.writeValueAsString(baselineDiff == null ? Map.of() : baselineDiff),
                    PRETTY.writeValueAsString(correlation == null ? Map.of() : correlation),
                    PRETTY.writeValueAsString(o11yMetrics == null ? Map.of() : o11yMetrics),
                    PRETTY.writeValueAsString(cloudwatch == null ? Map.of() : cloudwatch),
                    PRETTY.writeValueAsString(businessImpactCfg == null ? Map.of() : businessImpactCfg)
            );
        } catch (Exception e) {
            return "Aggregate: " + aggregateSummary;
        }
    }
}
