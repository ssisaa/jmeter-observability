package com.smartjmeter.splunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjmeter.model.JMeterMetric;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends individual {@link JMeterMetric} events to a Splunk HTTP Event
 * Collector (HEC) endpoint.
 *
 * <p>The HEC payload envelope uses the standard fields:
 * <pre>{ "event": {...}, "sourcetype": "jmeter", "index": "..." }</pre>
 * </p>
 */
public class SplunkHECClient {

    private static final Logger LOG = Logger.getLogger(SplunkHECClient.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String url;
    private final String token;
    private final String index;
    private final HttpClient httpClient;

    public SplunkHECClient(String url, String token) {
        this(url, token, "performance");
    }

    public SplunkHECClient(String url, String token, String index) {
        this.url = url;
        this.token = token;
        this.index = index;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Serialize the metric as a JSON HEC event and POST it. Errors are
     * logged but never thrown so a Splunk outage cannot break the JMeter
     * test run.
     */
    public void send(JMeterMetric metric) {
        if (url == null || url.isBlank() || token == null || token.isBlank()) {
            LOG.log(Level.FINE, "Splunk HEC disabled (missing url/token) - skipping send");
            return;
        }
        try {
            String json = MAPPER.writeValueAsString(metric);
            String payload = """
                    {
                      "event": %s,
                      "sourcetype": "jmeter",
                      "index": "%s"
                    }
                    """.formatted(json, index);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Splunk " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                LOG.log(Level.WARNING, "Splunk HEC returned {0}: {1}",
                        new Object[]{response.statusCode(), response.body()});
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to send metric to Splunk HEC", e);
        }
    }

    /**
     * Build the HEC envelope for a given metric. Exposed for unit testing.
     */
    public String buildPayload(JMeterMetric metric) throws Exception {
        String json = MAPPER.writeValueAsString(metric);
        return """
                {
                  "event": %s,
                  "sourcetype": "jmeter",
                  "index": "%s"
                }
                """.formatted(json, index);
    }
}
