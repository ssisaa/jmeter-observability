package com.smartjmeter.score;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the 8 component scores plus composites from the run
 * aggregate + rule-engine findings + optional cross-source telemetry
 * (Splunk log summary, O11y saturation, CloudWatch alarms).
 *
 * <p>Formulas mirror those in the enterprise architecture doc, §9.</p>
 */
public class HealthScorer {

    /**
     * @param slaP95Ms          target p95 (ms) for the overall test; drives the Performance Score
     * @param regressionPass    true if baseline diff has no p95_pct beyond the threshold
     * @param slaPassPct        fraction 0..1 of transactions inside SLA
     * @param infraSaturationPct max saturation across CPU/mem/disk/net (0..1)
     * @param gcPauseRatio      p95 gc pause / measurement window (0..1)
     * @param poolSaturation    max thread/conn pool saturation (0..1)
     * @param slowQueryRatio    slow queries / total DB ops (0..1)
     * @param deadlockRate      deadlock events per minute
     * @param dbConnSaturation  DB conn-pool saturation (0..1)
     * @param obsCoverage       fraction of expected signals available (0..1)
     * @param concurrencyR2     linear R² of p95 vs concurrency (0..1)
     * @param restartRate       pod restarts / target replicas (0..1)
     * @param availability      successful_requests / total (0..1)
     */
    public HealthScores score(Map<String, Object> aggregate,
                              List<Finding> findings,
                              long slaP95Ms,
                              boolean regressionPass,
                              double slaPassPct,
                              double infraSaturationPct,
                              double gcPauseRatio,
                              double poolSaturation,
                              double slowQueryRatio,
                              double deadlockRate,
                              double dbConnSaturation,
                              double obsCoverage,
                              double concurrencyR2,
                              double restartRate,
                              double availability) {
        HealthScores s = new HealthScores();

        // Performance score - p95 vs SLA
        long observedP95 = extractLong(aggregate, "rt_p95_ms", 0);
        double p95Ratio = slaP95Ms == 0 ? 1.0 : (double) observedP95 / slaP95Ms;
        s.performance = clamp(100 - (p95Ratio - 1) * 50);

        double errorRate = extractDouble(aggregate, "error_rate", 0);
        s.infrastructure = clamp(100 - infraSaturationPct * 100);
        s.application = clamp(100
                - 0.6 * errorRate * 100
                - 0.2 * gcPauseRatio * 100
                - 0.2 * poolSaturation * 100);
        s.database = clamp(100
                - 0.5 * slowQueryRatio * 100
                - 0.3 * deadlockRate * 10
                - 0.2 * dbConnSaturation * 100);
        s.observability = clamp(obsCoverage * 100);
        s.scalability = clamp(concurrencyR2 * 100);
        s.reliability = clamp(100 - restartRate * 100);
        s.availability = clamp(availability * 100);

        // Composite via weighted geometric mean of the 5 core scores.
        s.compositePerf = geometricMean(new double[]{
                Math.max(s.performance, 0.1),
                Math.max(s.infrastructure, 0.1),
                Math.max(s.application, 0.1),
                Math.max(s.database, 0.1),
                Math.max(s.scalability, 0.1),
        });

        // Release readiness pulls down on SLA / regression / observability failures.
        s.releaseReadiness = Math.min(Math.min(
                s.compositePerf,
                slaPassPct * 100),
                Math.min(regressionPass ? 100 : 60, s.observability));

        // Production confidence dampened by critical findings.
        long crit = findings.stream().filter(f -> f.severity() == Finding.Severity.CRITICAL).count();
        s.productionConfidence = clamp(s.releaseReadiness * Math.pow(0.8, crit));
        s.riskScore = clamp(100 - s.productionConfidence);
        return s;
    }

    /**
     * Compact one-line inputs summary useful for LLM prompts and the
     * appendix.
     */
    public static Map<String, Object> inputsSnapshot(long slaP95Ms, boolean regressionPass,
                                                     double slaPassPct, double infraSaturationPct,
                                                     double gcPauseRatio, double poolSaturation,
                                                     double slowQueryRatio, double deadlockRate,
                                                     double dbConnSaturation, double obsCoverage,
                                                     double concurrencyR2, double restartRate,
                                                     double availability) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sla_p95_ms", slaP95Ms);
        m.put("regression_pass", regressionPass);
        m.put("sla_pass_pct", slaPassPct);
        m.put("infra_saturation_pct", infraSaturationPct);
        m.put("gc_pause_ratio", gcPauseRatio);
        m.put("pool_saturation", poolSaturation);
        m.put("slow_query_ratio", slowQueryRatio);
        m.put("deadlock_rate", deadlockRate);
        m.put("db_conn_saturation", dbConnSaturation);
        m.put("obs_coverage", obsCoverage);
        m.put("concurrency_r2", concurrencyR2);
        m.put("restart_rate", restartRate);
        m.put("availability", availability);
        return m;
    }

    private static double geometricMean(double[] values) {
        double logSum = 0;
        for (double v : values) logSum += Math.log(v);
        return Math.exp(logSum / values.length);
    }

    private static double clamp(double v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private static long extractLong(Map<String, Object> agg, String key, long defaultVal) {
        Object overall = agg.get("overall");
        if (overall instanceof Map<?, ?> m) {
            Object v = m.get(key);
            if (v instanceof Number n) return n.longValue();
        }
        return defaultVal;
    }

    private static double extractDouble(Map<String, Object> agg, String key, double defaultVal) {
        Object overall = agg.get("overall");
        if (overall instanceof Map<?, ?> m) {
            Object v = m.get(key);
            if (v instanceof Number n) return n.doubleValue();
        }
        return defaultVal;
    }
}
