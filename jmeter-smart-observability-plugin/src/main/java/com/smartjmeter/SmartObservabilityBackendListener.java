package com.smartjmeter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjmeter.ai.AIAnalyzer;
import com.smartjmeter.config.PluginConfig;
import com.smartjmeter.correlate.CorrelationEngine;
import com.smartjmeter.model.JMeterMetric;
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
 * <p>For every batch of {@link SampleResult}s flushed by JMeter, this
 * listener:</p>
 * <ol>
 *   <li>Maps each sample into a {@link JMeterMetric}.</li>
 *   <li>Optionally appends it to a local JSON store.</li>
 *   <li>Optionally forwards it to Splunk HEC.</li>
 *   <li>Tracks failures for later Splunk Search API log correlation.</li>
 * </ol>
 *
 * <p>At <code>teardownTest</code> it runs the {@link AIAnalyzer}
 * skeleton, optionally runs Splunk log correlation via
 * {@link SplunkSearchClient}, and writes an HTML report via
 * {@link ReportGenerator}.</p>
 */
public class SmartObservabilityBackendListener extends AbstractBackendListenerClient {

    private static final Logger LOG = Logger.getLogger(SmartObservabilityBackendListener.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PluginConfig config;
    private SplunkHECClient splunk;
    private LocalJsonStore localStore;
    private final List<JMeterMetric> failedMetrics = new CopyOnWriteArrayList<>();

    @Override
    public Arguments getDefaultParameters() {
        Arguments args = new Arguments();
        args.addArgument(PluginConfig.PARAM_SPLUNK_URL, "https://splunk.company.com:8088/services/collector");
        args.addArgument(PluginConfig.PARAM_SPLUNK_TOKEN, "");
        args.addArgument(PluginConfig.PARAM_SPLUNK_INDEX, "performance");
        args.addArgument(PluginConfig.PARAM_ENVIRONMENT, "Performance-Test");
        args.addArgument(PluginConfig.PARAM_APPLICATION, "Migration-System");
        args.addArgument(PluginConfig.PARAM_TEST_NAME, "JMeter-Test");
        args.addArgument(PluginConfig.PARAM_LOCAL_STORE_PATH, "jmeter-metrics.json");
        args.addArgument(PluginConfig.PARAM_ENABLE_SPLUNK, "true");
        args.addArgument(PluginConfig.PARAM_ENABLE_LOCAL_STORE, "true");
        args.addArgument(PluginConfig.PARAM_SPLUNK_SEARCH_URL, "https://splunk.company.com:8089");
        args.addArgument(PluginConfig.PARAM_SPLUNK_SEARCH_TOKEN, "");
        args.addArgument(PluginConfig.PARAM_SPLUNK_LOG_INDEX, "app");
        args.addArgument(PluginConfig.PARAM_ENABLE_CORRELATION, "false");
        args.addArgument(PluginConfig.PARAM_CORRELATION_WINDOW_SECONDS, "30");
        args.addArgument(PluginConfig.PARAM_CORRELATION_OUTPUT_PATH, "log-correlation.json");
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
                "Smart Observability Plugin initialised (env={0}, app={1}, splunk={2}, local={3}, correlation={4})",
                new Object[]{
                        config.getEnvironment(),
                        config.getApplication(),
                        config.isSplunkEnabled(),
                        config.isLocalStoreEnabled(),
                        config.isCorrelationEnabled()
                });
    }

    @Override
    public void handleSampleResults(List<SampleResult> samples, BackendListenerContext context) {
        for (SampleResult result : samples) {
            JMeterMetric metric = toMetric(result);
            if (localStore != null) {
                localStore.append(metric);
            }
            if (splunk != null) {
                splunk.send(metric);
            }
            if (config.isCorrelationEnabled() && !metric.isSuccess()) {
                failedMetrics.add(metric);
            }
        }
    }

    @Override
    public void teardownTest(BackendListenerContext context) throws Exception {
        try {
            Map<String, Object> correlationOut = new LinkedHashMap<>();
            if (config.isCorrelationEnabled() && !failedMetrics.isEmpty()) {
                correlationOut = runCorrelation();
            }
            String analysis = new AIAnalyzer().analyze(MAPPER.writeValueAsString(correlationOut));
            new ReportGenerator().generate(analysis);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Report / correlation generation failed", e);
        }
        LOG.info("Smart Observability Plugin Completed");
        super.teardownTest(context);
    }

    private Map<String, Object> runCorrelation() {
        Map<String, Object> out = new LinkedHashMap<>();
        SplunkSearchClient search = new SplunkSearchClient(
                config.getSplunkSearchUrl(),
                config.getSplunkSearchToken());
        CorrelationEngine engine = new CorrelationEngine(config.getCorrelationWindowSeconds());
        List<CorrelationEngine.TimeWindow> windows = engine.buildFailureWindows(failedMetrics);

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
        out.put("failure_count", failedMetrics.size());
        out.put("window_seconds", config.getCorrelationWindowSeconds());
        out.put("windows", windowResults);

        try {
            Path outFile = Path.of(config.getCorrelationOutputPath());
            if (outFile.getParent() != null) {
                Files.createDirectories(outFile.getParent());
            }
            Files.writeString(outFile, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to write correlation output", e);
        }
        return out;
    }

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
}
