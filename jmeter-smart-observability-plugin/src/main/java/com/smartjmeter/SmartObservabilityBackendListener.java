package com.smartjmeter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjmeter.ai.AIAnalyzer;
import com.smartjmeter.ai.InsightExtractor;
import com.smartjmeter.ai.LlmClient;
import com.smartjmeter.ai.MetricAggregator;
import com.smartjmeter.ai.PromptBuilder;
import com.smartjmeter.baseline.BaselineDiff;
import com.smartjmeter.baseline.BaselineStore;
import com.smartjmeter.cloudwatch.CloudWatchMetricsCollector;
import com.smartjmeter.config.PluginConfig;
import com.smartjmeter.correlate.CorrelationEngine;
import com.smartjmeter.correlate.RuleEngine;
import com.smartjmeter.model.JMeterMetric;
import com.smartjmeter.o11y.SplunkO11yMetricsClient;
import com.smartjmeter.report.CsvExporter;
import com.smartjmeter.report.JsonExporter;
import com.smartjmeter.report.ReportGenerator;
import com.smartjmeter.score.Finding;
import com.smartjmeter.score.HealthScorer;
import com.smartjmeter.score.HealthScores;
import com.smartjmeter.score.Verdict;
import com.smartjmeter.score.VerdictCompiler;
import com.smartjmeter.splunk.AsyncBatchingHECClient;
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
    private AsyncBatchingHECClient splunkBatch;
    private LocalJsonStore localStore;
    private BaselineStore baselineStore;
    private Map<String, Object> previousBaseline = Map.of();
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
        args.addArgument(PluginConfig.PARAM_HEC_BATCH_ENABLED, "false");
        args.addArgument(PluginConfig.PARAM_HEC_BATCH_SIZE, "100");
        args.addArgument(PluginConfig.PARAM_HEC_FLUSH_INTERVAL_MS, "1000");
        args.addArgument(PluginConfig.PARAM_HEC_QUEUE_CAPACITY, "10000");
        args.addArgument(PluginConfig.PARAM_TLS_INSECURE, "false");
        args.addArgument(PluginConfig.PARAM_OUTPUT_DIRECTORY, "");
        args.addArgument(PluginConfig.PARAM_REPORT_OUTPUT_PATH, "Performance_Report.html");
        args.addArgument(PluginConfig.PARAM_ENABLE_BASELINE_DIFF, "false");
        args.addArgument(PluginConfig.PARAM_BASELINE_PATH, "");
        args.addArgument(PluginConfig.PARAM_BASELINE_UPDATE_MODE, "always");
        // Phase 8 - Enterprise report
        args.addArgument(PluginConfig.PARAM_SLA_P95_MS, "1000");
        args.addArgument(PluginConfig.PARAM_APDEX_TARGET_MS, "500");
        args.addArgument(PluginConfig.PARAM_REGRESSION_THRESHOLD_PCT, "25");
        args.addArgument(PluginConfig.PARAM_ENABLE_CLOUDWATCH, "false");
        args.addArgument(PluginConfig.PARAM_CLOUDWATCH_REGION, "us-east-1");
        args.addArgument(PluginConfig.PARAM_CLOUDWATCH_METRICS_JSON, "[]");
        args.addArgument(PluginConfig.PARAM_CLOUDWATCH_ALARMS, "");
        args.addArgument(PluginConfig.PARAM_CLOUDWATCH_OUTPUT_PATH, "cloudwatch-metrics.json");
        args.addArgument(PluginConfig.PARAM_JSON_REPORT_PATH, "Performance_Report.json");
        args.addArgument(PluginConfig.PARAM_CSV_REPORT_PATH, "Performance_Report.csv");
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
            if (config.isHecBatchEnabled()) {
                this.splunkBatch = new AsyncBatchingHECClient(
                        config.getSplunkUrl(),
                        config.getSplunkToken(),
                        config.getSplunkIndex(),
                        config.getHecBatchSize(),
                        config.getHecFlushIntervalMs(),
                        config.getHecQueueCapacity(),
                        config.isTlsInsecure());
            } else {
                this.splunk = new SplunkHECClient(
                        config.getSplunkUrl(),
                        config.getSplunkToken(),
                        config.getSplunkIndex(),
                        config.isTlsInsecure());
            }
        }
        if (config.isLocalStoreEnabled()) {
            this.localStore = new LocalJsonStore(config.resolvePath(config.getLocalStorePath()).toString());
        }

        if (config.isBaselineDiffEnabled()) {
            this.baselineStore = new BaselineStore(config.resolveBaselinePath());
            this.previousBaseline = baselineStore.load();
            LOG.log(Level.INFO, "Baseline path: {0} (previous={1})",
                    new Object[]{baselineStore.getFilePath(), !previousBaseline.isEmpty()});
        }

        LOG.log(Level.INFO,
                "Smart Observability Plugin initialised (env={0}, app={1}, splunk={2}, local={3}, "
                        + "correlation={4}, o11y={5}, llm={6}/{7}, baseline={8}, out={9})",
                new Object[]{
                        config.getEnvironment(),
                        config.getApplication(),
                        config.isSplunkEnabled(),
                        config.isLocalStoreEnabled(),
                        config.isCorrelationEnabled(),
                        config.isO11yEnabled(),
                        config.isLlmEnabled(),
                        config.getLlmProvider(),
                        config.isBaselineDiffEnabled(),
                        config.resolvePath("").toString()
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
            if (splunkBatch != null) {
                splunkBatch.send(metric);
            } else if (splunk != null) {
                splunk.send(metric);
            }
        }
    }

    @Override
    public void teardownTest(BackendListenerContext context) throws Exception {
        try {
            if (splunkBatch != null) {
                splunkBatch.close();
            }
            Map<String, Object> aggregate = MetricAggregator.aggregate(allMetrics, config.getApdexTargetMs());
            Map<String, Object> correlation = runCorrelation();
            Map<String, List<Map<String, Object>>> o11y = runO11yFetch(aggregate);
            Map<String, Object> cloudwatch = runCloudWatchFetch(aggregate);
            Map<String, Object> baselineDiff = runBaselineDiff(aggregate);

            // Deterministic rules + scoring + verdict
            List<Finding> findings = new RuleEngine().evaluate(aggregate, baselineDiff, correlation, o11y, cloudwatch);
            HealthScorer scorer = new HealthScorer();
            HealthScores scores = scorer.score(
                    aggregate, findings,
                    config.getSlaP95Ms(),
                    baselineRegressionPass(baselineDiff),
                    slaPassPct(aggregate),
                    o11ySaturation(o11y, "cpu.utilization", "memory.utilization"),
                    o11yGcPauseRatio(o11y),
                    0.5,  // pool_saturation - proxy value pending native measurement
                    0.0,  // slow_query_ratio - filled by Splunk Search results in future rev
                    0.0,  // deadlock_rate
                    0.5,  // db_conn_saturation
                    observabilityCoverage(correlation, o11y, cloudwatch),
                    1.0,  // concurrency_r2 - proxy; requires ramp-up sampling to compute
                    o11yRestartRate(o11y),
                    1.0 - error_rate(aggregate));
            Verdict verdict = new VerdictCompiler().compile(
                    scores, findings,
                    slaPassPct(aggregate),
                    baselineRegressionPass(baselineDiff),
                    observabilityCoverage(correlation, o11y, cloudwatch),
                    85, 70, 0.6);

            // LLM-generated executive insights (JSON output)
            Map<String, Object> insights = runInsightGeneration(
                    aggregate, scores, verdict, findings, baselineDiff, correlation, o11y, cloudwatch);
            String analysisMarkdown = String.valueOf(insights.getOrDefault("markdown", AIAnalyzer.staticAnalysis()));

            // Findings -> serialisable list for report/exporters
            List<Map<String, Object>> findingsMap = findings.stream().map(f -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", f.ruleId());
                m.put("title", f.title());
                m.put("category", f.category());
                m.put("severity", f.severity().name());
                m.put("confidence", f.confidence());
                m.put("evidence", f.evidence());
                return m;
            }).toList();

            ReportGenerator.Context reportCtx = new ReportGenerator.Context.Builder()
                    .testName(config.getTestName())
                    .environment(config.getEnvironment())
                    .application(config.getApplication())
                    .startMs(extractLong(aggregate, "start_ms", 0))
                    .stopMs(extractLong(aggregate, "stop_ms", 0))
                    .aggregate(aggregate)
                    .correlation(correlation)
                    .o11yMetrics(o11y)
                    .cloudwatch(cloudwatch)
                    .scores(scores.toMap())
                    .verdict(verdict.toMap())
                    .findings(findingsMap)
                    .baselineDiff(baselineDiff)
                    .aiInsights(insights)
                    .llmAnalysis(analysisMarkdown)
                    .llmProvider(config.isLlmEnabled() ? config.getLlmProvider() : null)
                    .llmModel(config.isLlmEnabled() ? config.getLlmModel() : null)
                    .build();
            Path resolvedReport = config.resolvePath(config.getReportOutputPath());
            new ReportGenerator().generate(reportCtx, resolvedReport.toString());
            LOG.log(Level.INFO, "Performance report written to {0}", resolvedReport);

            // JSON + CSV exports
            Path jsonPath = config.resolvePath(config.getJsonReportPath());
            Map<String, Object> envelope = JsonExporter.envelope(
                    "report.v2.json",
                    config.getTestName() + "-" + extractLong(aggregate, "start_ms", 0),
                    config.getTestName(), config.getEnvironment(), config.getApplication(),
                    aggregate, scores.toMap(), verdict.toMap(), findingsMap,
                    baselineDiff, correlation, o11y, cloudwatch, insights);
            new JsonExporter().export(jsonPath, envelope);
            LOG.log(Level.INFO, "JSON report written to {0}", jsonPath);

            Path csvPath = config.resolvePath(config.getCsvReportPath());
            new CsvExporter().export(csvPath, aggregate);
            LOG.log(Level.INFO, "CSV report written to {0}", csvPath);

            if (baselineStore != null && shouldUpdateBaseline(aggregate)) {
                baselineStore.save(config.getTestName(), aggregate);
                LOG.log(Level.INFO, "Baseline updated at {0}", baselineStore.getFilePath());
            }
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
                config.getSplunkSearchToken(),
                config.isTlsInsecure());
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
        writeJson(config.resolvePath(config.getCorrelationOutputPath()), out);
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
                config.getO11yUrl(), config.getO11yToken(), config.isTlsInsecure())
                .withResolutionMs(config.getO11yResolutionMs());
        Map<String, List<Map<String, Object>>> out = client.fetchAll(metrics, start, stop);
        writeJson(config.resolvePath(config.getO11yOutputPath()), out);
        return out;
    }

    private Map<String, Object> runBaselineDiff(Map<String, Object> aggregate) {
        if (!config.isBaselineDiffEnabled()) return Map.of();
        return BaselineDiff.compute(previousBaseline, aggregate);
    }

    private boolean shouldUpdateBaseline(Map<String, Object> aggregate) {
        String mode = config.getBaselineUpdateMode();
        if (mode == null) return true;
        return switch (mode.trim().toLowerCase()) {
            case "never" -> false;
            case "on-success" -> {
                Object overall = aggregate.get("overall");
                if (overall instanceof Map<?, ?> m) {
                    Object errors = m.get("errors");
                    yield !(errors instanceof Number n) || n.longValue() == 0;
                }
                yield true;
            }
            default -> true; // "always"
        };
    }

    private String runAnalysis(Map<String, Object> aggregate,
                               Map<String, Object> correlation,
                               Map<String, List<Map<String, Object>>> o11y,
                               Map<String, Object> baselineDiff) {
        // Retained for backwards compatibility; the new enterprise pipeline
        // uses runInsightGeneration below.
        if (!config.isLlmEnabled()) {
            return AIAnalyzer.staticAnalysis();
        }
        String key = config.resolveLlmApiKey();
        if (key.isBlank()) return AIAnalyzer.staticAnalysis();
        LlmClient client = new LlmClient(
                LlmClient.Provider.parse(config.getLlmProvider()),
                config.getLlmModel(), key, config.getLlmBaseUrl());
        return new AIAnalyzer(client).analyze(aggregate, correlation, o11y, baselineDiff);
    }

    private Map<String, Object> runCloudWatchFetch(Map<String, Object> aggregate) {
        if (!config.isCloudwatchEnabled()) return Map.of();
        long start = extractLong(aggregate, "start_ms", System.currentTimeMillis() - 60_000);
        long stop = extractLong(aggregate, "stop_ms", System.currentTimeMillis());
        if (stop <= start) stop = start + 60_000;
        List<Map<String, Object>> metricConfigs;
        try {
            metricConfigs = MAPPER.readValue(
                    config.getCloudwatchMetricsJson() == null || config.getCloudwatchMetricsJson().isBlank()
                            ? "[]" : config.getCloudwatchMetricsJson(),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse CloudWatch_Metrics_Json - continuing without", e);
            metricConfigs = List.of();
        }
        List<String> alarmNames = config.getCloudwatchAlarms() == null || config.getCloudwatchAlarms().isBlank()
                ? List.of()
                : java.util.Arrays.stream(config.getCloudwatchAlarms().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
        try (CloudWatchMetricsCollector cw = new CloudWatchMetricsCollector(config.getCloudwatchRegion())) {
            Map<String, Object> out = cw.collect(metricConfigs, alarmNames, start, stop);
            writeJson(config.resolvePath(config.getCloudwatchOutputPath()), out);
            return out;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "CloudWatch collection failed", e);
            return Map.of();
        }
    }

    private Map<String, Object> runInsightGeneration(Map<String, Object> aggregate,
                                                     HealthScores scores, Verdict verdict,
                                                     List<Finding> findings,
                                                     Map<String, Object> baselineDiff,
                                                     Map<String, Object> correlation,
                                                     Map<String, List<Map<String, Object>>> o11y,
                                                     Map<String, Object> cloudwatch) {
        if (!config.isLlmEnabled()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("structured", false);
            m.put("markdown", AIAnalyzer.staticAnalysis());
            return m;
        }
        String key = config.resolveLlmApiKey();
        if (key.isBlank()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("structured", false);
            m.put("markdown", AIAnalyzer.staticAnalysis());
            return m;
        }
        try {
            LlmClient client = new LlmClient(
                    LlmClient.Provider.parse(config.getLlmProvider()),
                    config.getLlmModel(), key, config.getLlmBaseUrl())
                    .withMaxTokens(2048);
            String userPrompt = PromptBuilder.buildUserPrompt(
                    aggregate, scores, verdict, findings, baselineDiff, correlation, o11y, cloudwatch, Map.of());
            String raw = client.chat(PromptBuilder.SYSTEM_PROMPT, userPrompt);
            return InsightExtractor.extract(raw);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM insight generation failed - static fallback", e);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("structured", false);
            m.put("markdown", AIAnalyzer.staticAnalysis());
            return m;
        }
    }

    // --- helper metrics for the scorer ---

    @SuppressWarnings("unchecked")
    private double slaPassPct(Map<String, Object> aggregate) {
        Map<String, Object> perTxn = (Map<String, Object>) aggregate.getOrDefault("per_transaction", Map.of());
        if (perTxn.isEmpty()) return 1.0;
        long pass = 0, total = 0;
        long slaP95 = config.getSlaP95Ms();
        for (Object o : perTxn.values()) {
            if (o instanceof Map<?, ?> row) {
                Object p95 = row.get("rt_p95_ms");
                total++;
                if (p95 instanceof Number n && n.longValue() <= slaP95) pass++;
            }
        }
        return total == 0 ? 1.0 : (double) pass / total;
    }

    private boolean baselineRegressionPass(Map<String, Object> baselineDiff) {
        if (baselineDiff == null || !Boolean.TRUE.equals(baselineDiff.get("has_previous"))) return true;
        Object overall = baselineDiff.get("overall");
        if (overall instanceof Map<?, ?> m) {
            Object p95Pct = m.get("rt_p95_pct");
            if (p95Pct instanceof Number n && Math.abs(n.doubleValue()) > config.getRegressionThresholdPct()) {
                return false;
            }
        }
        return true;
    }

    private static double o11ySaturation(Map<String, List<Map<String, Object>>> o11y, String... metrics) {
        if (o11y == null || o11y.isEmpty()) return 0;
        double max = 0;
        for (String m : metrics) {
            for (Map<String, Object> p : o11y.getOrDefault(m, List.of())) {
                Object v = p.get("value");
                if (v instanceof Number n && n.doubleValue() > max) max = n.doubleValue();
            }
        }
        return Math.min(1.0, max);
    }

    private static double o11yGcPauseRatio(Map<String, List<Map<String, Object>>> o11y) {
        double max = 0;
        for (Map<String, Object> p : o11y == null ? List.<Map<String, Object>>of() : o11y.getOrDefault("jvm.gc.pause_ms", List.of())) {
            Object v = p.get("value");
            if (v instanceof Number n && n.doubleValue() > max) max = n.doubleValue();
        }
        // Normalise: 2000 ms of GC pause -> ratio 1.0
        return Math.min(1.0, max / 2000d);
    }

    private static double o11yRestartRate(Map<String, List<Map<String, Object>>> o11y) {
        double sum = 0;
        for (Map<String, Object> p : o11y == null ? List.<Map<String, Object>>of() : o11y.getOrDefault("k8s.pod.restart_count", List.of())) {
            Object v = p.get("value");
            if (v instanceof Number n) sum += n.doubleValue();
        }
        // Normalise: 5 restarts -> ratio 1.0
        return Math.min(1.0, sum / 5d);
    }

    private static double observabilityCoverage(Map<String, Object> correlation,
                                                Map<String, List<Map<String, Object>>> o11y,
                                                Map<String, Object> cloudwatch) {
        int have = 0, total = 3;
        if (correlation != null && !correlation.isEmpty()) have++;
        if (o11y != null && !o11y.isEmpty()) have++;
        if (cloudwatch != null && !cloudwatch.isEmpty()) have++;
        return total == 0 ? 0 : (double) have / total;
    }

    private static double error_rate(Map<String, Object> aggregate) {
        Object overall = aggregate.get("overall");
        if (overall instanceof Map<?, ?> m) {
            Object v = m.get("error_rate");
            if (v instanceof Number n) return n.doubleValue();
        }
        return 0;
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

    private static void writeJson(Path target, Object body) {
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to write JSON output to " + target, e);
        }
    }
}
