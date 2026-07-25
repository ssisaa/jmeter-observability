package com.smartjmeter.score;

import java.util.List;
import java.util.Map;

/**
 * A single deterministic finding produced by the rule engine or a
 * scorer. Every finding carries a confidence score and evidence
 * pointers so the report can link back to raw data.
 */
public record Finding(
        String ruleId,
        String title,
        String category,      // db|gc|thread|memory|network|scaling|regression|config|dependency|deadlock|downstream|other
        Severity severity,
        double confidence,    // 0..1
        String evidence,      // short human string
        List<String> evidenceIds,
        Map<String, Object> details) {

    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }
}
