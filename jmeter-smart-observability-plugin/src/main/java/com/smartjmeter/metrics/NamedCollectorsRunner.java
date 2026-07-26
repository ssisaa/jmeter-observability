package com.smartjmeter.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs every backend declared in the {@code Metric_Sources_Json} param
 * ({@link com.smartjmeter.metrics.GenericHttpMetricsCollector} covers
 * Prometheus, Loki, Elastic, Datadog, New Relic, Dynatrace, Azure Monitor
 * and Google Cloud Ops).
 *
 * <p>Also writes a per-backend {@code <backend>-metrics.json} artefact so
 * the raw evidence sits next to the HTML report.</p>
 *
 * <p>Return shape: {@code backend -> queryLabel -> points[]} so both the
 * report generator and the rule engine can walk it without knowing the
 * source vendor.</p>
 */
public final class NamedCollectorsRunner {

    private static final Logger LOG = Logger.getLogger(NamedCollectorsRunner.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NamedCollectorsRunner() { }

    /** Parse config JSON, run every source, write JSON artefacts, return merged map. */
    public static Map<String, Map<String, List<Map<String, Object>>>> run(
            String metricSourcesJson, long startMs, long stopMs,
            boolean insecureTls, Path outputDir) {

        Map<String, Map<String, List<Map<String, Object>>>> merged = new LinkedHashMap<>();
        if (metricSourcesJson == null || metricSourcesJson.isBlank()
                || "[]".equals(metricSourcesJson.trim())) return merged;

        List<Map<String, Object>> sources;
        try {
            sources = MAPPER.readValue(metricSourcesJson,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Metric_Sources_Json parse failed", e);
            return merged;
        }

        for (Map<String, Object> src : sources) {
            String backend = String.valueOf(src.getOrDefault("backend", "")).trim().toLowerCase();
            String baseUrl = String.valueOf(src.getOrDefault("baseUrl", "")).trim();
            if (backend.isEmpty() || baseUrl.isEmpty()) continue;

            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) src.getOrDefault("headers", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, String> queries = (Map<String, String>) src.getOrDefault("queries", Map.of());
            String outFile = String.valueOf(src.getOrDefault("outPath", backend + "-metrics.json"));

            try {
                GenericHttpMetricsCollector coll = new GenericHttpMetricsCollector(
                        backend, baseUrl, headers, insecureTls);
                Map<String, List<Map<String, Object>>> data = coll.query(queries, startMs, stopMs);
                merged.merge(backend, data, (oldMap, newMap) -> {
                    Map<String, List<Map<String, Object>>> combined = new LinkedHashMap<>(oldMap);
                    combined.putAll(newMap);
                    return combined;
                });
                writeJson(outputDir.resolve(outFile), data);
                LOG.log(Level.INFO, "Collected {0} metrics ({1} queries) -> {2}",
                        new Object[]{backend, data.size(), outFile});
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Metric source failed: " + backend, ex);
            }
        }
        return merged;
    }

    private static void writeJson(Path target, Object body) {
        try {
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            Files.writeString(target, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to write JSON to " + target, e);
        }
    }
}
