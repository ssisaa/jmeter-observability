package com.smartjmeter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjmeter.ai.AIAnalyzer;
import com.smartjmeter.ai.LlmClient;
import com.smartjmeter.ai.MetricAggregator;
import com.smartjmeter.config.PluginConfig;
import com.smartjmeter.correlate.CorrelationEngine;
import com.smartjmeter.model.JMeterMetric;
import com.smartjmeter.o11y.SplunkO11yMetricsClient;
import com.smartjmeter.report.ReportGenerator;
import com.smartjmeter.splunk.SplunkHECClient;
import com.smartjmeter.splunk.SplunkSearchClient;
import com.smartjmeter.store.LocalJsonStore;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JMeter Backend Listener entry point.
 *
 * <p>Per batch of {@link SampleResult}s: maps to {@link JMeterMetric},
 * appends to the local store, forwards to Splunk HEC, and keeps a
 * running list of every metric for later aggregation.</p>
 *
 * <p>At {@code teardownTest}: builds an aggregate summary, optionally
 * runs Splunk log correlation, optionally fetches Splunk O11y metrics,
 * calls the configured LLM to produce a root-cause report, and writes
 * {@code Performance_Report.html}.</p>
 */
public class SmartObservabilityBackendListener extends AbstractBackendListenerClient {

    private static final Logger LOG = Logger.getLogger(SmartObservabilityBackendListener.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PluginConfig config;
    private SplunkHECClient splunk;
    private LocalJsonStore localStore;
    private final List<JMeterMetric> allMetrics = new CopyOnWriteArrayList<>();

    @Override
    public Arguments getDefaultParameters() {
        Arguments args = new Arguments();
        // Phase 1
        args.addArgument(PluginConfig.PARAM_SPLUNK_URL, "https://splunk.company.com:8088/services/collector");
        args.addArgument(PluginConfig.PARAM_SPLUNK_TOKEN, "");
        args.addArgument(PluginConfig.PARAM_SPLUNK_INDEX, "performance");
        args.addArgument(PluginConfig.PARAM_ENVIRONMENT, "Performance-Test");
        args.addArgument(PluginConfig.PARAM_APPLICATION, "Migration-System");
        args.addArgument(PluginConfig.PARAM_TEST_NAME, "JMeter-Test");
        args.addArgument(PluginConfig.PARAM_LOCAL_STORE_PATH, "jmeter-metrics.json");
        args.addArgument(PluginConfig.PARAM_ENABLE_SPLUNK, "true");
        args.addArgument(PluginConfig.PARAM_ENABLE_LOCAL_STORE, "true");
        // Phase 2
        args.addArgument(PluginConfig.PARAM_SPLUNK_SEARCH_URL, "https://splunk.company.com:8089");
        args.addArgument(PluginConfig.PARAM_SPLUNK_SEARCH_TOKEN, "");
        args.addArgument(PluginConfig.PARAM_SPLUNK_LOG_INDEX, "app");
        args.addArgument(PluginConfig.PARAM_ENABLE_CORRELATION, "false");
        args.addArgument(PluginConfig.PARAM_CORRELATION_WINDOW_SECONDS, "30");
        args.addArgument(PluginConfig.PARAM_CORRELATION_OUTPUT_PATH, "log-correlation.json");
        // Phase 3
        args.addArgument(PluginConfig.PARAM_O11Y_URL, "https://api.us1.signalfx.com");
        args.addArgument(PluginConfig.PARAM_O11Y_TOKEN, "");
        args.addArgument(PluginConfig.PARAM_O11Y_METRICS,
                "cpu.utilization,memory.utilization,jvm.gc.collection.count,jvm.threads.count,db.latency,k8s.pod.cpu.usage");
        args.addArgument(PluginConfig.PARAM_O11Y_RESOLUTION_MS, "10000");
        args.addArgument(PluginConfig.PARAM_ENABLE_O11Y, "false");
        args.addArgument(PluginConfig.PARAM_O11Y_OUTPUT_PATH, "o11y-metrics.json");
        // Phase 4
        args.addArgument(PluginConfig.PARAM_ENABLE_LLM, "false");
        args.addArgument(PluginConfig.PARAM_LLM_PROVIDER, "openai");
        args.addArgument(PluginConfig.PARAM_LLM_MODEL, "");
        args.addArgument(PluginConfig.PARAM_LLM_API_KEY, "");
        args.addArgument(PluginConfig.PARAM_LLM_API_KEY_ENV, "OPENAI_API_KEY");
        args.addArgument(PluginConfig.PARAM_LLM_BASE_URL, "");
        return args;
    }

    @Override
    public void setupTest(BackendListenerContext context) throws Exception {
        super.setupTest(context);
        this.config = PluginConfig.fromContext(context);

        if (config.isSplunkEnabled()) {
            this.splunk = new SplunkHECClient(
                    config.getSplunkUrl(),
                    config.getSplunkToken(),
                    config.getSplunkIndex());
        }
        if (config.isLocalStoreEnabled()) {
            this.localStore = new LocalJsonStore(config.getLocalStorePath());
        }

        LOG.log(Level.INFO,
                "Smart Observability Plugin initialised (env={0}, app={1}, splunk={2}, local={3}, "
                        + "correlation={4}, o11y={5}, llm={6}/{7})",
                new Object[]{
                        config.getEnvironment(),
                        config.getApplication(),
                        config.isSplunkEnabled(),
                        config.isLocalStoreEnabled(),
                        config.isCorrelationEnabled(),
                        config.isO11yEnabled(),
                        config.isLlmEnabled(),
                        config.getLlmProvider()
                });
    }

    @Override
    public void handleSampleResults(List<SampleResult> samples, BackendListenerContext context) {
        for (SampleResult result : samples) {
            JMeterMetric metric = toMetric(result);
            allMetrics.add(metric);
            if (localStore != null) {
                localStore.append(metric);
            }
            if (splunk != null) {
                splunk.send(metric);
            }
        }
    }

    @Override
    public void teardownTest(BackendListenerContext context) throws Exception {
        try {
            Map<String, Object> aggregate = MetricAggregator.aggregate(allMetrics);
            Map<String, Object> correlation = runCorrelation();
            Map<String, List<Map<String, Object>>> o11y = runO11yFetch(aggregate);
            String analysis = runAnalysis(aggregate, correlation, o11y);
            new ReportGenerator().generate(analysis);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Teardown analysis pipeline failed", e);
        }
        LOG.info("Smart Observability Plugin Completed");
        super.teardownTest(context);
    }

    /* ---------------- Pipeline steps ---------------- */

    private Map<String, Object> runCorrelation() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<JMeterMetric> failures = new ArrayList<>();
        for (JMeterMetric m : allMetrics) if (!m.isSuccess()) failures.add(m);
        if (!config.isCorrelationEnabled() || failures.isEmpty()) {
            out.put("failure_count", failures.size());
            out.put("window_seconds", config.getCorrelationWindowSeconds());
            out.put("windows", Collections.emptyList());
            return out;
        }
        SplunkSearchClient search = new SplunkSearchClient(
                config.getSplunkSearchUrl(),
                config.getSplunkSearchToken());
        CorrelationEngine engine = new CorrelationEngine(config.getCorrelationWindowSeconds());
        List<CorrelationEngine.TimeWindow> windows = engine.buildFailureWindows(failures);
        List<Map<String, Object>> windowResults = new ArrayList<>();
        for (CorrelationEngine.TimeWindow w : windows) {
            List<Map<String, Object>> events = search.correlateLogs(
                    config.getSplunkLogIndex(),
                    w.earliest(),
                    w.latest());
            Map<String, Object> entry = new HashMap<>();
            entry.put("earliest", w.earliest());
            entry.put("latest", w.latest());
            entry.put("event_count", events.size());
            entry.put("events", events);
            windowResults.add(entry);
        }
        out.put("failure_count", failures.size());
        out.put("window_seconds", config.getCorrelationWindowSeconds());
        out.put("windows", windowResults);
        writeJson(config.getCorrelationOutputPath(), out);
        return out;
    }

    private Map<String, List<Map<String, Object>>> runO11yFetch(Map<String, Object> aggregate) {
        if (!config.isO11yEnabled()) return Collections.emptyMap();
        List<String> metrics = SplunkO11yMetricsClient.parseMetricList(config.getO11yMetrics());
        if (metrics.isEmpty()) return Collections.emptyMap();

        long start = extractLong(aggregate, "start_ms", System.currentTimeMillis() - 60_000);
        long stop = extractLong(aggregate, "stop_ms", System.currentTimeMillis());
        if (stop <= start) stop = start + 60_000;

        SplunkO11yMetricsClient client = new SplunkO11yMetricsClient(
                config.getO11yUrl(), config.getO11yToken())
                .withResolutionMs(config.getO11yResolutionMs());
        Map<String, List<Map<String, Object>>> out = client.fetchAll(metrics, start, stop);
        writeJson(config.getO11yOutputPath(), out);
        return out;
    }

    private String runAnalysis(Map<String, Object> aggregate,
                               Map<String, Object> correlation,
                               Map<String, List<Map<String, Object>>> o11y) {
        if (!config.isLlmEnabled()) {
            return AIAnalyzer.staticAnalysis();
        }
        String key = config.resolveLlmApiKey();
        if (key.isBlank()) {
            LOG.log(Level.WARNING, "LLM enabled but no API key resolved from GUI param or env var; using static analysis");
            return AIAnalyzer.staticAnalysis();
        }
        LlmClient client = new LlmClient(
                LlmClient.Provider.parse(config.getLlmProvider()),
                config.getLlmModel(),
                key,
                config.getLlmBaseUrl());
        return new AIAnalyzer(client).analyze(aggregate, correlation, o11y);
    }

    /* ---------------- helpers ---------------- */

    private JMeterMetric toMetric(SampleResult result) {
        JMeterMetric metric = new JMeterMetric();
        metric.setTestName(config.getTestName());
        metric.setTransaction(result.getSampleLabel());
        metric.setResponseTime(result.getTime());
        metric.setLatency(result.getLatency());
        metric.setBytesSent(result.getSentBytes());
        metric.setBytesReceived(result.getBytesAsLong());
        metric.setSuccess(result.isSuccessful());
        metric.setTimestamp(result.getTimeStamp());
        metric.setEnvironment(config.getEnvironment());
        metric.setApplication(config.getApplication());
        metric.setResponseCode(result.getResponseCode());
        metric.setThreadName(result.getThreadName());
        return metric;
    }

    @SuppressWarnings("unchecked")
    private static long extractLong(Map<String, Object> aggregate, String key, long defaultVal) {
        Object overall = aggregate.get("overall");
        if (!(overall instanceof Map)) return defaultVal;
        Object v = ((Map<String, Object>) overall).get(key);
        if (v instanceof Number n) return n.longValue();
        return defaultVal;
    }

    private static void writeJson(String path, Object body) {
        try {
            Path target = Path.of(path);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to write JSON output to " + path, e);
        }
    }
}
