package com.smartjmeter.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the entire run context to a single JSON file. Downstream
 * systems (ServiceNow, Jira, ML training) subscribe to this.
 */
public class JsonExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public Path export(Path target, Map<String, Object> reportPayload) throws Exception {
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        Files.writeString(target, MAPPER.writeValueAsString(reportPayload));
        return target;
    }

    /**
     * Convenience: wrap the executive report inputs into the schema
     * consumers expect.
     */
    public static Map<String, Object> envelope(
            String schemaVersion, String runId, String testName,
            String environment, String application,
            Map<String, Object> aggregate,
            Map<String, Object> scores,
            Map<String, Object> verdict,
            Object findings,
            Map<String, Object> baselineDiff,
            Map<String, Object> correlation,
            Object o11y,
            Map<String, Object> cloudwatch,
            Map<String, Object> insights) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("schema_version", schemaVersion);
        env.put("run_id", runId);
        env.put("test_name", testName);
        env.put("environment", environment);
        env.put("application", application);
        env.put("generated_at", java.time.Instant.now().toString());
        env.put("aggregate", aggregate);
        env.put("scores", scores);
        env.put("verdict", verdict);
        env.put("findings", findings);
        env.put("baseline_diff", baselineDiff);
        env.put("correlation", correlation);
        env.put("o11y_metrics", o11y);
        env.put("cloudwatch", cloudwatch);
        env.put("ai_insights", insights);
        return env;
    }
}
