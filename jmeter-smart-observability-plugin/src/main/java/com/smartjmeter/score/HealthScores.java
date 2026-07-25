package com.smartjmeter.score;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enterprise health-score bundle. Every score is on [0, 100].
 * Composite scores use a weighted geometric mean (a single zero
 * collapses the whole score, which is exactly what we want for a
 * production-safety verdict).
 */
public class HealthScores {

    public double performance;      // JMeter p95 vs SLA
    public double infrastructure;   // CPU / mem / disk / net saturation
    public double application;      // error rate + GC + pool saturation
    public double database;         // slow queries + deadlocks + conn pool
    public double observability;    // fraction of expected signals present
    public double scalability;      // R² of p95 vs concurrency
    public double reliability;      // 1 - restart_rate
    public double availability;     // successful / total, business-weighted
    public double compositePerf;    // geometric mean of five above
    public double releaseReadiness; // min(composite, sla_pass_pct*100, regression_pass*100, obs)
    public double productionConfidence;
    public double riskScore;        // 100 - productionConfidence

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("performance", round(performance));
        m.put("infrastructure", round(infrastructure));
        m.put("application", round(application));
        m.put("database", round(database));
        m.put("observability", round(observability));
        m.put("scalability", round(scalability));
        m.put("reliability", round(reliability));
        m.put("availability", round(availability));
        m.put("composite_performance", round(compositePerf));
        m.put("release_readiness", round(releaseReadiness));
        m.put("production_confidence", round(productionConfidence));
        m.put("risk_score", round(riskScore));
        return m;
    }

    private static double round(double d) { return Math.round(d * 10.0) / 10.0; }
}
