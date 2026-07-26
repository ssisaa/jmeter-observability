package com.smartjmeter.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjmeter.util.HttpClientFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic HTTP metrics collector for OSS, APM and multi-cloud sources.
 *
 * <p>Every backend exposes a query endpoint that returns JSON. This class
 * models them uniformly as {@code (baseUrl, headers, path, queryTemplate,
 * jsonPointerToPoints)} so we don't need N handwritten SDK integrations.
 * Every backend can be added by editing a config entry.</p>
 *
 * <p>Supported preset backends:</p>
 * <ul>
 *   <li><b>Prometheus</b>       {@code /api/v1/query_range}</li>
 *   <li><b>Loki</b>              {@code /loki/api/v1/query_range}</li>
 *   <li><b>Elastic</b>           {@code /_search}</li>
 *   <li><b>Datadog</b>           {@code /api/v1/query} (DD-API-KEY / DD-APPLICATION-KEY)</li>
 *   <li><b>New Relic NRDB</b>    {@code /v1/accounts/&#123;acct&#125;/query} (NRQL, Api-Key)</li>
 *   <li><b>Dynatrace Metrics</b> {@code /api/v2/metrics/query} (Api-Token)</li>
 *   <li><b>Azure Monitor</b>     {@code /subscriptions/.../metrics?api-version=2018-01-01} (Bearer)</li>
 *   <li><b>Google Cloud Ops</b>  {@code /v3/projects/&#123;p&#125;/timeSeries} (Bearer)</li>
 * </ul>
 *
 * <p>All calls are safe on missing config (empty result, never throws).</p>
 */
public class GenericHttpMetricsCollector {

    private static final Logger LOG = Logger.getLogger(GenericHttpMetricsCollector.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Query(String backend, String queryTemplate) { }

    private final String backend;      // logical name, used to select shape
    private final String baseUrl;
    private final Map<String, String> headers;
    private final HttpClient http;

    public GenericHttpMetricsCollector(String backend, String baseUrl,
                                       Map<String, String> headers, boolean insecureTls) {
        this.backend = backend == null ? "" : backend.toLowerCase(Locale.ROOT);
        this.baseUrl = strip(baseUrl);
        this.headers = headers == null ? Map.of() : headers;
        this.http = HttpClientFactory.create(insecureTls);
    }

    /**
     * Run each query template within [startMs, stopMs]. Returns
     * {@code { "<queryLabel>": [ {ts, value, ...}, ... ] }}.
     */
    public Map<String, List<Map<String, Object>>> query(Map<String, String> labeledQueries,
                                                        long startMs, long stopMs) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        if (baseUrl.isEmpty() || labeledQueries == null || labeledQueries.isEmpty()) return out;
        for (Map.Entry<String, String> e : labeledQueries.entrySet()) {
            try {
                out.put(e.getKey(), fetch(e.getValue(), startMs, stopMs));
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Metrics fetch failed backend=" + backend + " q=" + e.getKey(), ex);
                out.put(e.getKey(), List.of());
            }
        }
        return out;
    }

    private List<Map<String, Object>> fetch(String q, long startMs, long stopMs) throws Exception {
        String startIso = java.time.Instant.ofEpochMilli(startMs).toString();
        String stopIso = java.time.Instant.ofEpochMilli(stopMs).toString();
        double startS = startMs / 1000.0, stopS = stopMs / 1000.0;
        HttpRequest.Builder rb = HttpRequest.newBuilder().timeout(Duration.ofSeconds(20));
        headers.forEach(rb::header);
        rb.header("Accept", "application/json");
        String url;

        switch (backend) {
            case "prometheus" -> {
                url = baseUrl + "/api/v1/query_range?query=" + enc(q)
                        + "&start=" + startS + "&end=" + stopS + "&step=30s";
                rb.uri(URI.create(url)).GET();
            }
            case "loki" -> {
                url = baseUrl + "/loki/api/v1/query_range?query=" + enc(q)
                        + "&start=" + (long)(startS * 1_000_000_000L)
                        + "&end=" + (long)(stopS * 1_000_000_000L)
                        + "&step=30s&limit=1000";
                rb.uri(URI.create(url)).GET();
            }
            case "elastic" -> {
                url = baseUrl + "/_search";
                rb.uri(URI.create(url))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(q));
            }
            case "datadog" -> {
                url = baseUrl + "/api/v1/query?from=" + (long)startS + "&to=" + (long)stopS + "&query=" + enc(q);
                rb.uri(URI.create(url)).GET();
            }
            case "newrelic" -> {
                url = baseUrl + "?nrql=" + enc(q);
                rb.uri(URI.create(url)).GET();
            }
            case "dynatrace" -> {
                url = baseUrl + "/api/v2/metrics/query?metricSelector=" + enc(q)
                        + "&from=" + startIso + "&to=" + stopIso;
                rb.uri(URI.create(url)).GET();
            }
            case "azure" -> {
                // baseUrl already contains subscriptions/resource path; q holds metric names comma-sep
                url = baseUrl + "&api-version=2018-01-01&metricnames=" + enc(q)
                        + "&timespan=" + startIso + "/" + stopIso;
                rb.uri(URI.create(url)).GET();
            }
            case "gcp" -> {
                // baseUrl: https://monitoring.googleapis.com/v3/projects/<p>/timeSeries
                url = baseUrl + "?filter=" + enc(q)
                        + "&interval.startTime=" + startIso
                        + "&interval.endTime=" + stopIso;
                rb.uri(URI.create(url)).GET();
            }
            default -> {
                LOG.log(Level.WARNING, "Unknown backend: " + backend);
                return List.of();
            }
        }

        HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            LOG.log(Level.WARNING, "{0} {1}: {2}", new Object[]{backend, resp.statusCode(), snippet(resp.body())});
            return List.of();
        }
        return normalise(resp.body());
    }

    /** Parse the response into a common {ts, value, series?} point list. */
    private List<Map<String, Object>> normalise(String body) throws Exception {
        JsonNode root = MAPPER.readTree(body);
        List<Map<String, Object>> out = new ArrayList<>();
        switch (backend) {
            case "prometheus" -> collectPrometheus(root.path("data").path("result"), out);
            case "loki" -> collectPrometheus(root.path("data").path("result"), out); // same envelope
            case "elastic" -> collectElastic(root, out);
            case "datadog" -> collectDatadog(root, out);
            case "newrelic" -> collectNewRelic(root, out);
            case "dynatrace" -> collectDynatrace(root, out);
            case "azure" -> collectAzure(root, out);
            case "gcp" -> collectGcp(root, out);
            default -> { }
        }
        return out;
    }

    private static void collectPrometheus(JsonNode arr, List<Map<String, Object>> out) {
        if (!arr.isArray()) return;
        for (JsonNode series : arr) {
            JsonNode values = series.path("values");
            if (!values.isArray()) continue;
            String label = series.path("metric").toString();
            for (JsonNode point : values) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("series", label);
                p.put("ts", (long) (point.get(0).asDouble() * 1000));
                p.put("value", parseDouble(point.get(1).asText()));
                out.add(p);
            }
        }
    }

    private static void collectElastic(JsonNode root, List<Map<String, Object>> out) {
        JsonNode hits = root.path("hits").path("hits");
        if (!hits.isArray()) return;
        for (JsonNode hit : hits) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", hit.path("_id").asText());
            p.put("value", 1);
            p.put("source", MAPPER.convertValue(hit.path("_source"), Map.class));
            out.add(p);
        }
    }

    private static void collectDatadog(JsonNode root, List<Map<String, Object>> out) {
        JsonNode arr = root.path("series");
        if (!arr.isArray()) return;
        for (JsonNode s : arr) {
            String label = s.path("scope").asText("");
            for (JsonNode pt : s.path("pointlist")) {
                if (pt.isArray() && pt.size() >= 2) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("series", label);
                    p.put("ts", pt.get(0).asLong());
                    p.put("value", pt.get(1).asDouble());
                    out.add(p);
                }
            }
        }
    }

    private static void collectNewRelic(JsonNode root, List<Map<String, Object>> out) {
        JsonNode arr = root.path("results");
        if (!arr.isArray()) return;
        for (JsonNode r : arr) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("value", r);
            out.add(p);
        }
    }

    private static void collectDynatrace(JsonNode root, List<Map<String, Object>> out) {
        JsonNode arr = root.path("result");
        if (!arr.isArray()) return;
        for (JsonNode m : arr) {
            String metricId = m.path("metricId").asText();
            for (JsonNode d : m.path("data")) {
                JsonNode ts = d.path("timestamps");
                JsonNode vs = d.path("values");
                int n = Math.min(ts.size(), vs.size());
                for (int i = 0; i < n; i++) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("series", metricId);
                    p.put("ts", ts.get(i).asLong());
                    p.put("value", vs.get(i).asDouble());
                    out.add(p);
                }
            }
        }
    }

    private static void collectAzure(JsonNode root, List<Map<String, Object>> out) {
        JsonNode arr = root.path("value");
        if (!arr.isArray()) return;
        for (JsonNode m : arr) {
            String name = m.path("name").path("value").asText();
            for (JsonNode ts : m.path("timeseries")) {
                for (JsonNode d : ts.path("data")) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("series", name);
                    p.put("ts", d.path("timeStamp").asText());
                    p.put("value", d.has("average") ? d.get("average").asDouble()
                            : d.has("total") ? d.get("total").asDouble()
                            : d.has("maximum") ? d.get("maximum").asDouble() : 0);
                    out.add(p);
                }
            }
        }
    }

    private static void collectGcp(JsonNode root, List<Map<String, Object>> out) {
        JsonNode arr = root.path("timeSeries");
        if (!arr.isArray()) return;
        for (JsonNode ts : arr) {
            String label = ts.path("metric").path("type").asText();
            for (JsonNode pt : ts.path("points")) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("series", label);
                p.put("ts", pt.path("interval").path("endTime").asText());
                p.put("value", pt.path("value").path("doubleValue").asDouble(
                        pt.path("value").path("int64Value").asDouble(0)));
                out.add(p);
            }
        }
    }

    private static double parseDouble(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String strip(String s) { if (s == null) return ""; return s.endsWith("/") ? s.substring(0, s.length()-1) : s; }
    private static String snippet(String s) { return s == null ? "" : s.substring(0, Math.min(200, s.length())); }
}
