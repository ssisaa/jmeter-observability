package com.smartjmeter.splunk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin client over the Splunk Search REST API.
 *
 * <p>Runs an SPL query via {@code POST /services/search/jobs}, polls the
 * job until {@code isDone == 1}, and fetches JSON results from
 * {@code /services/search/jobs/{sid}/results}.</p>
 *
 * <p>Auth uses the same HEC-style header {@code "Authorization: Splunk <token>"}
 * for HEC-only environments, but if a session bearer is supplied via
 * {@link #withBearer(String)}, {@code "Authorization: Bearer <token>"} is
 * used instead (standard Splunk REST auth).</p>
 */
public class SplunkSearchClient {

    private static final Logger LOG = Logger.getLogger(SplunkSearchClient.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String token;
    private final HttpClient httpClient;
    private String authScheme = "Bearer";
    private long pollIntervalMs = 500;
    private long maxWaitMs = 30_000;

    public SplunkSearchClient(String baseUrl, String token) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public SplunkSearchClient withBearer(String scheme) {
        this.authScheme = scheme;
        return this;
    }

    public SplunkSearchClient withPollInterval(long ms) {
        this.pollIntervalMs = ms;
        return this;
    }

    public SplunkSearchClient withMaxWait(long ms) {
        this.maxWaitMs = ms;
        return this;
    }

    /**
     * Convenience: run a search that correlates JMeter samples with app
     * logs. The generated SPL is:
     * <pre>
     *   search index=&lt;index&gt; (error OR timeout OR exception)
     *   earliest=&lt;epochStart&gt; latest=&lt;epochEnd&gt;
     * </pre>
     */
    public List<Map<String, Object>> correlateLogs(String index,
                                                   long earliestEpochSeconds,
                                                   long latestEpochSeconds) {
        String spl = buildCorrelationSpl(index, earliestEpochSeconds, latestEpochSeconds);
        return runSearch(spl);
    }

    /**
     * Build the correlation SPL. Extracted for unit testing.
     */
    public static String buildCorrelationSpl(String index,
                                             long earliestEpochSeconds,
                                             long latestEpochSeconds) {
        return "search index=" + index
                + " (error OR timeout OR exception)"
                + " earliest=" + earliestEpochSeconds
                + " latest=" + latestEpochSeconds;
    }

    /**
     * Execute an SPL query end-to-end and return the {@code results} array.
     * Never throws — on failure returns an empty list and logs a warning.
     */
    public List<Map<String, Object>> runSearch(String spl) {
        if (baseUrl == null || baseUrl.isBlank() || token == null || token.isBlank()) {
            LOG.log(Level.FINE, "Splunk Search disabled (missing baseUrl/token)");
            return Collections.emptyList();
        }
        try {
            String sid = createJob(spl);
            if (sid == null) return Collections.emptyList();
            if (!waitForCompletion(sid)) {
                LOG.log(Level.WARNING, "Splunk search job {0} did not finish within {1}ms",
                        new Object[]{sid, maxWaitMs});
                return Collections.emptyList();
            }
            return fetchResults(sid);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Splunk search failed for query: " + spl, e);
            return Collections.emptyList();
        }
    }

    private String createJob(String spl) throws Exception {
        String body = "search=" + URLEncoder.encode(spl, StandardCharsets.UTF_8)
                + "&output_mode=json"
                + "&exec_mode=normal";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/services/search/jobs"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", authScheme + " " + token)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            LOG.log(Level.WARNING, "Splunk create-job {0}: {1}",
                    new Object[]{resp.statusCode(), resp.body()});
            return null;
        }
        JsonNode node = MAPPER.readTree(resp.body());
        return node.path("sid").asText(null);
    }

    private boolean waitForCompletion(String sid) throws Exception {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/services/search/jobs/" + sid + "?output_mode=json"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", authScheme + " " + token)
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 300) {
                JsonNode node = MAPPER.readTree(resp.body());
                JsonNode content = node.path("entry").path(0).path("content");
                if (content.path("isDone").asBoolean(false)
                        || content.path("dispatchState").asText("").equalsIgnoreCase("DONE")) {
                    return true;
                }
            }
            Thread.sleep(pollIntervalMs);
        }
        return false;
    }

    private List<Map<String, Object>> fetchResults(String sid) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/services/search/jobs/" + sid + "/results?output_mode=json&count=0"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", authScheme + " " + token)
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            LOG.log(Level.WARNING, "Splunk fetch-results {0}: {1}",
                    new Object[]{resp.statusCode(), resp.body()});
            return Collections.emptyList();
        }
        return parseResults(resp.body());
    }

    /**
     * Parse a Splunk search-results JSON body into a plain list of row maps.
     * Extracted for unit testing.
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseResults(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode results = root.path("results");
        if (!results.isArray()) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        Iterator<JsonNode> it = results.elements();
        while (it.hasNext()) {
            JsonNode row = it.next();
            out.add(MAPPER.convertValue(row, Map.class));
        }
        return out;
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
