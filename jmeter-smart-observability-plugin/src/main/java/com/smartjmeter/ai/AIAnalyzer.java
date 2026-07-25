package com.smartjmeter.ai;

/**
 * Placeholder analyzer that returns a static explanation. Phase&nbsp;4 will
 * replace the body with an LLM call using a performance knowledge base.
 */
public class AIAnalyzer {

    /**
     * Produce a plain-text analysis for the supplied aggregated
     * performance data (typically a JSON snapshot of metrics).
     */
    public String analyze(String performanceData) {
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
