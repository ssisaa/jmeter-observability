package com.smartjmeter.score;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executive verdict produced by {@link VerdictCompiler}. Answers the
 * front-page question: <em>Can we safely deploy this release into
 * production?</em>
 */
public record Verdict(
        Level level,
        double productionConfidence,
        double riskScore,
        String rationale,
        List<String> gatesPassed,
        List<String> gatesFailed,
        String deploymentRecommendation,
        List<String> rolloutPlan,
        List<String> rollbackTriggers) {

    public enum Level { GO, GO_WITH_CONDITIONS, NO_GO, INSUFFICIENT_DATA }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level.name());
        m.put("production_confidence", Math.round(productionConfidence * 10.0) / 10.0);
        m.put("risk_score", Math.round(riskScore * 10.0) / 10.0);
        m.put("rationale", rationale);
        m.put("gates_passed", gatesPassed);
        m.put("gates_failed", gatesFailed);
        m.put("deployment_recommendation", deploymentRecommendation);
        m.put("rollout_plan", rolloutPlan);
        m.put("rollback_triggers", rollbackTriggers);
        return m;
    }
}
