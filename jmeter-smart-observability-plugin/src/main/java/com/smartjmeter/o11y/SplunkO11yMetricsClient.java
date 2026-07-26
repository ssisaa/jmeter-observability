package com.smartjmeter.o11y;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client for Splunk Observability Cloud (formerly SignalFx) metrics.
 *
 * <p>Uses the {@code GET /v1/timeserieswindow} endpoint - the only
 * bounded, non-streaming batch endpoint for retrieving raw metric points
 * in a fixed time window. This is the correct choice for JMeter runs
 * where we want to fetch metrics between test start and test end and
 * then close out; SignalFlow streams indefinitely and is unsuitable
 * here.</p>
 *
 * <p>Query params:</p>
 * <ul>
 *   <li>{@code query} - Elasticsearch-style filter e.g.
 *       {@code sf_metric:"cpu.utilization"}</li>
 *   <li>{@code startMs}, {@code endMs} - Unix millis window</li>
 *   <li>{@code resolution} - one of 1000, 60000, 300000, 3600000</li>
 * </ul>
 *
 * <p>Auth: {@code X-SF-Token: <token>}.</p>
 *
 * <p>All errors are logged and return an empty result so an outage cannot
 * break the JMeter test run.</p>
 */
public class SplunkO11yMetricsClient {

    private static final Logger LOG = Logger.getLogger(SplunkO11yMetricsClient.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Valid resolutions accepted by /v1/timeserieswindow. */
    private static final long[] VALID_RESOLUTIONS_MS = {1_000L, 60_000L, 300_000L, 3_600_000L};

    /** data('metric.name')... -> metric.name */
    private static final Pattern DATA_METRIC = Pattern.compile("data\\(\\s*['\"]([^'\"]+)['\"]");

    private final String baseUrl;
    private final String token;
    private final HttpClient httpClient;
    private long resolutionMs = 60_000L;

    public SplunkO11yMetricsClient(String baseUrl, String token) {
        this(baseUrl, token, false);
    }

    public SplunkO11yMetricsClient(String baseUrl, String token, boolean insecureTls) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.token = token;
        this.httpClient = HttpClientFactory.create(insecureTls);
    }

    public SplunkO11yMetricsClient withResolutionMs(long ms) {
        this.resolutionMs = snapResolution(ms);
        return this;
    }

    /**
     * Fetch each configured metric over {@code [startMs, endMs]} and
     * return a map keyed by metric name, value = list of
     * {@code {"tsid": String, "ts": long, "value": Number}} points.
     */
    public Map<String, List<Map<String, Object>>> fetchAll(List<String> metrics,
                                                           long startMs,
                                                           long endMs) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        if (metrics == null || metrics.isEmpty()) return out;
        if (baseUrl.isEmpty() || token == null || token.isBlank()) {
            LOG.log(Level.FINE, "O11y metrics disabled (missing baseUrl/token)");
            for (String m : metrics) out.put(m, Collections.emptyList());
            return out;
        }
        for (String metric : metrics) {
            out.put(metric, fetch(metric, startMs, endMs));
        }
        return out;
    }

    public List<Map<String, Object>> fetch(String metric, long startMs, long endMs) {
        try {
            String queryValue = toQuery(metric);
            String url = baseUrl + "/v1/timeserieswindow"
                    + "?query=" + URLEncoder.encode(queryValue, StandardCharsets.UTF_8)
                    + "&startMs=" + startMs
                    + "&endMs=" + endMs
                    + "&resolution=" + resolutionMs;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("X-SF-Token", token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) {
                LOG.log(Level.WARNING,
                        "O11y /v1/timeserieswindow returned 404 - check O11y_URL. "
                                + "Expected: https://api.<realm>.signalfx.com "
                                + "(e.g. api.us1, api.us0, api.eu0, api.sg0, api.jp0). "
                                + "Base URL used: {0}. Metric skipped: {1}",
                        new Object[]{baseUrl, metric});
                return Collections.emptyList();
            }
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                LOG.log(Level.WARNING,
                        "O11y auth failed ({0}) - check O11y_TOKEN (X-SF-Token). "
                                + "Metric skipped: {1}",
                        new Object[]{resp.statusCode(), metric});
                return Collections.emptyList();
            }
            if (resp.statusCode() >= 300) {
                LOG.log(Level.WARNING, "O11y timeserieswindow {0} for {1}: {2}",
                        new Object[]{resp.statusCode(), metric, snippet(resp.body())});
                return Collections.emptyList();
            }
            return parseTimeSeriesWindow(resp.body());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to fetch o11y metric: " + metric, e);
            return Collections.emptyList();
        }
    }

    private static String snippet(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    /**
     * Convert a user-supplied metric spec into an Elasticsearch-style
     * query understood by {@code /v1/timeserieswindow}.
     *
     * <ul>
     *   <li>{@code cpu.utilization} -> {@code sf_metric:"cpu.utilization"}</li>
     *   <li>{@code sf_metric:"foo" AND host:web1} -> used as-is</li>
     *   <li>{@code data('foo').publish()} (SignalFlow) -> {@code sf_metric:"foo"}
     *       (the SignalFlow program is downgraded; /v1 does not run programs)</li>
     * </ul>
     */
    public static String toQuery(String metric) {
        if (metric == null) return "";
        String m = metric.trim();
        if (m.isEmpty()) return m;
        if (m.contains("data(") || m.contains(".publish(")) {
            Matcher mat = DATA_METRIC.matcher(m);
            if (mat.find()) {
                LOG.log(Level.INFO,
                        "SignalFlow program supplied for O11y metric; using extracted name "
                                + "\"{0}\" against /v1/timeserieswindow (server-side "
                                + "aggregation is not applied).", mat.group(1));
                return "sf_metric:\"" + mat.group(1) + "\"";
            }
            LOG.log(Level.WARNING,
                    "SignalFlow program \"{0}\" cannot be executed by /v1/timeserieswindow "
                            + "and no metric name could be extracted.", m);
            return m;
        }
        if (m.contains(":") || m.contains(" AND ") || m.contains(" OR ")) {
            return m;
        }
        return "sf_metric:\"" + m + "\"";
    }

    /**
     * Snap arbitrary resolution to the smallest valid
     * /v1/timeserieswindow bucket that is &gt;= the requested value, so we
     * never return finer-grained (and more expensive) data than asked
     * for. Values above 1h are clamped to 1h.
     */
    static long snapResolution(long ms) {
        for (long candidate : VALID_RESOLUTIONS_MS) {
            if (candidate >= ms) return candidate;
        }
        return VALID_RESOLUTIONS_MS[VALID_RESOLUTIONS_MS.length - 1];
    }

    /**
     * Parse a {@code /v1/timeserieswindow} response into flat point lists.
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
