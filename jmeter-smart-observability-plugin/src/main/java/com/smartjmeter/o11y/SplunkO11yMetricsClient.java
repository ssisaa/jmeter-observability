package com.smartjmeter.o11y;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjmeter.util.HttpClientFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client for Splunk Observability Cloud (formerly SignalFx) metrics.
 *
 * <p>Uses the {@code GET /v2/timeserieswindow} endpoint which returns raw
 * data points for a program text (a metric name is the simplest program
 * text, e.g. {@code data('cpu.utilization').publish()}). For simplicity
 * this client accepts either a bare metric name or a full SignalFlow
 * program string; bare names are wrapped in {@code data('...').publish()}.
 * </p>
 *
 * <p>Auth: {@code X-SF-TOKEN: <token>}.</p>
 *
 * <p>All errors are logged and return an empty result so an outage cannot
 * break the JMeter test run.</p>
 */
public class SplunkO11yMetricsClient {

    private static final Logger LOG = Logger.getLogger(SplunkO11yMetricsClient.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String token;
    private final HttpClient httpClient;
    private long resolutionMs = 10_000;

    public SplunkO11yMetricsClient(String baseUrl, String token) {
        this(baseUrl, token, false);
    }

    public SplunkO11yMetricsClient(String baseUrl, String token, boolean insecureTls) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.token = token;
        this.httpClient = HttpClientFactory.create(insecureTls);
    }

    public SplunkO11yMetricsClient withResolutionMs(long ms) {
        this.resolutionMs = ms;
        return this;
    }

    /**
     * Fetch each configured metric over {@code [startMs, stopMs]} and
     * return a map keyed by metric name, value = list of
     * {@code {"ts": long, "value": Number}} points.
     */
    public Map<String, List<Map<String, Object>>> fetchAll(List<String> metrics,
                                                           long startMs,
                                                           long stopMs) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        if (metrics == null || metrics.isEmpty()) return out;
        if (baseUrl.isEmpty() || token == null || token.isBlank()) {
            LOG.log(Level.FINE, "O11y metrics disabled (missing baseUrl/token)");
            for (String m : metrics) out.put(m, Collections.emptyList());
            return out;
        }
        for (String metric : metrics) {
            out.put(metric, fetch(metric, startMs, stopMs));
        }
        return out;
    }

    public List<Map<String, Object>> fetch(String metric, long startMs, long stopMs) {
        try {
            String program = toProgramText(metric);
            // v2.0.5: switched from the deprecated GET /v2/timeserieswindow to
            // POST with a JSON body; the GET form now returns 404 on new
            // realms. This also matches the current Splunk O11y REST API.
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("program", program);
            body.put("startMs", startMs);
            body.put("stopMs", stopMs);
            body.put("resolution", resolutionMs);
            String url = baseUrl + "/v2/timeserieswindow";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("X-SF-TOKEN", token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404 || resp.statusCode() == 405) {
                // Fall back to the SignalFlow execute endpoint used by newer realms.
                return fetchViaSignalflow(program, startMs, stopMs);
            }
            if (resp.statusCode() >= 300) {
                LOG.log(Level.WARNING, "O11y timeserieswindow {0}: {1}",
                        new Object[]{resp.statusCode(), snippet(resp.body())});
                return Collections.emptyList();
            }
            return parseTimeSeriesWindow(resp.body());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to fetch o11y metric: " + metric, e);
            return Collections.emptyList();
        }
    }

    /**
     * Fallback to {@code POST /v2/signalflow/execute} for realms that no
     * longer serve {@code /v2/timeserieswindow}. Returns the same flat
     * point list shape.
     */
    private List<Map<String, Object>> fetchViaSignalflow(String program, long startMs, long stopMs) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("program", program);
            body.put("start", startMs);
            body.put("stop", stopMs);
            body.put("resolution", resolutionMs);
            body.put("immediate", true);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v2/signalflow/execute"))
                    .timeout(Duration.ofSeconds(20))
                    .header("X-SF-TOKEN", token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                LOG.log(Level.WARNING, "O11y signalflow {0}: {1}",
                        new Object[]{resp.statusCode(), snippet(resp.body())});
                return Collections.emptyList();
            }
            return parseSignalflowResponse(resp.body());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "SignalFlow fallback failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * Parse SignalFlow /v2/signalflow/execute response. The body is
     * newline-delimited JSON where each line is either a metadata frame
     * ({@code {"type":"metadata","tsId":"...","properties":{...}}}) or a
     * data frame ({@code {"type":"data","tsId":"...","logicalTimestampMs":..,"value":..}}).
     */
    public static List<Map<String, Object>> parseSignalflowResponse(String body) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        if (body == null || body.isBlank()) return out;
        for (String line : body.split("\r?\n")) {
            if (line.isBlank()) continue;
            JsonNode node;
            try { node = MAPPER.readTree(line); }
            catch (Exception e) { continue; }
            if (!"data".equals(node.path("type").asText())) continue;
            Map<String, Object> p = new HashMap<>();
            p.put("tsid", node.path("tsId").asText(""));
            p.put("ts", node.path("logicalTimestampMs").asLong());
            JsonNode v = node.path("value");
            p.put("value", v.isNumber() ? v.numberValue() : v.asText());
            out.add(p);
        }
        return out;
    }

    private static String snippet(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    /**
     * Wrap a bare metric name in a minimal SignalFlow program. Full
     * programs (containing {@code data(} or {@code .publish()}) are used
     * as-is.
     */
    public static String toProgramText(String metric) {
        String m = metric.trim();
        if (m.contains("data(") || m.contains(".publish(")) return m;
        return "data('" + m + "').publish()";
    }

    /**
     * Parse a {@code /v2/timeserieswindow} response into flat point lists.
     * The response shape is:
     * <pre>
     *   { "data": { "&lt;tsid&gt;": [ [tsMs, value], ... ] } }
     * </pre>
     * We flatten all series into a single ordered list, preserving series
     * identity via a {@code tsid} field on each point.
     * <p>Extracted for unit testing.</p>
     */
    public static List<Map<String, Object>> parseTimeSeriesWindow(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode data = root.path("data");
        if (data.isMissingNode() || !data.isObject()) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String tsid = entry.getKey();
            JsonNode arr = entry.getValue();
            if (!arr.isArray()) continue;
            for (JsonNode point : arr) {
                if (!point.isArray() || point.size() < 2) continue;
                Map<String, Object> p = new HashMap<>();
                p.put("tsid", tsid);
                p.put("ts", point.get(0).asLong());
                JsonNode v = point.get(1);
                p.put("value", v.isNumber() ? v.numberValue() : v.asText());
                out.add(p);
            }
        }
        return out;
    }

    /** Split the comma-separated metric list from the JMeter param. */
    public static List<String> parseMetricList(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String s : Arrays.asList(csv.split(","))) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
