package com.smartjmeter.score;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compiles the production-readiness verdict from health scores, rule
 * findings, SLA compliance and baseline regression status.
 */
public class VerdictCompiler {

    /**
     * @param scores           bundle from {@link HealthScorer}
     * @param findings         deterministic + LLM-corroborated findings
     * @param slaPassPct       0..1 fraction of transactions inside SLA
     * @param regressionPass   true if no p95 regression beyond threshold
     * @param obsCoverage      0..1 fraction of expected observability signals
     * @param confidenceGoMin  minimum production confidence to be GO (default 85)
     * @param confidenceGwcMin minimum production confidence to be GO_WITH_CONDITIONS (default 70)
     * @param obsMin           minimum obs coverage below which verdict is INSUFFICIENT_DATA (default 0.6)
     */
    public Verdict compile(HealthScores scores,
                           List<Finding> findings,
                           double slaPassPct,
                           boolean regressionPass,
                           double obsCoverage,
                           double confidenceGoMin,
                           double confidenceGwcMin,
                           double obsMin) {

        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        gate(slaPassPct >= 0.95, "SLA gate: >=95% transactions within SLA", passed, failed);
        gate(regressionPass, "Regression gate: no p95 regression >25% vs baseline", passed, failed);
        gate(scores.performance >= 60, "Performance score >=60", passed, failed);
        gate(scores.infrastructure >= 60, "Infrastructure score >=60", passed, failed);
        gate(scores.application >= 60, "Application score >=60", passed, failed);
        gate(scores.observability >= obsMin * 100,
                "Observability coverage >=" + Math.round(obsMin * 100) + "%", passed, failed);
        long crit = findings.stream().filter(f -> f.severity() == Finding.Severity.CRITICAL).count();
        gate(crit == 0, "No critical findings", passed, failed);

        Verdict.Level level;
        String rationale;
        String recommendation;
        List<String> rollout;
        List<String> rollback;

        if (obsCoverage < obsMin) {
            level = Verdict.Level.INSUFFICIENT_DATA;
            rationale = String.format("Observability coverage %.0f%% below required %.0f%% "
                    + "- cannot certify safety.", obsCoverage * 100, obsMin * 100);
            recommendation = "Do not deploy. Extend observability first (missing signals: log/traces/infra).";
            rollout = List.of();
            rollback = List.of();
        } else if (crit > 0 || scores.productionConfidence < confidenceGwcMin) {
            level = Verdict.Level.NO_GO;
            rationale = String.format("Production confidence %.1f (< %.0f). Critical findings: %d.",
                    scores.productionConfidence, confidenceGwcMin, crit);
            recommendation = "Do not deploy. Address failed gates and re-run.";
            rollout = List.of();
            rollback = List.of();
        } else if (scores.productionConfidence < confidenceGoMin) {
            level = Verdict.Level.GO_WITH_CONDITIONS;
            rationale = String.format("Production confidence %.1f in [%.0f, %.0f). "
                            + "Deploy behind guardrails and monitor closely.",
                    scores.productionConfidence, confidenceGwcMin, confidenceGoMin);
            recommendation = "Deploy in a controlled canary (5% -> 25% -> 100%) behind feature flag.";
            rollout = List.of("Canary 5% for 30 minutes",
                    "Progress to 25% if p95 <= SLA and error rate <= baseline+0.5pp",
                    "Progress to 100% after 24h stable window");
            rollback = List.of("p95 crosses 1.5x SLA for 5 minutes",
                    "Error rate exceeds baseline by 2pp for 5 minutes",
                    "Any critical finding fires in production monitors");
        } else {
            level = Verdict.Level.GO;
            rationale = String.format("Production confidence %.1f (>= %.0f) with %d critical findings.",
                    scores.productionConfidence, confidenceGoMin, crit);
            recommendation = "Deploy to production during the standard change window.";
            rollout = List.of("Standard rolling deployment",
                    "Observe golden signals for 2h post-deploy");
            rollback = List.of("Standard rollback if error rate exceeds baseline by 2pp for 5 minutes");
        }

        return new Verdict(level, scores.productionConfidence, scores.riskScore,
                rationale, passed, failed, recommendation, rollout, rollback);
    }

    private static void gate(boolean pass, String label, List<String> passed, List<String> failed) {
        (pass ? passed : failed).add(label);
    }
}
