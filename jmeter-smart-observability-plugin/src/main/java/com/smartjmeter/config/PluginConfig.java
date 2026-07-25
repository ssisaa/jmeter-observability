package com.smartjmeter.config;

import org.apache.jmeter.visualizers.backend.BackendListenerContext;

/**
 * Typed view over the {@link BackendListenerContext} parameters configured
 * through the JMeter GUI. Keeps parameter keys in a single place so they
 * cannot drift between the descriptor and the listener code.
 */
public class PluginConfig {

    // Phase 1
    public static final String PARAM_SPLUNK_URL = "Splunk_URL";
    public static final String PARAM_SPLUNK_TOKEN = "Splunk_Token";
    public static final String PARAM_SPLUNK_INDEX = "Splunk_Index";
    public static final String PARAM_ENVIRONMENT = "Environment";
    public static final String PARAM_APPLICATION = "Application";
    public static final String PARAM_TEST_NAME = "Test_Name";
    public static final String PARAM_LOCAL_STORE_PATH = "Local_Store_Path";
    public static final String PARAM_ENABLE_SPLUNK = "Enable_Splunk";
    public static final String PARAM_ENABLE_LOCAL_STORE = "Enable_Local_Store";
    public static final String PARAM_HEC_BATCH_ENABLED = "HEC_Batch_Enabled";
    public static final String PARAM_HEC_BATCH_SIZE = "HEC_Batch_Size";
    public static final String PARAM_HEC_FLUSH_INTERVAL_MS = "HEC_Flush_Interval_Ms";
    public static final String PARAM_HEC_QUEUE_CAPACITY = "HEC_Queue_Capacity";
    public static final String PARAM_TLS_INSECURE = "TLS_Insecure";

    // Output paths
    public static final String PARAM_OUTPUT_DIRECTORY = "Output_Directory";
    public static final String PARAM_REPORT_OUTPUT_PATH = "Report_Output_Path";

    // Baseline diff (Phase 7)
    public static final String PARAM_ENABLE_BASELINE_DIFF = "Enable_Baseline_Diff";
    public static final String PARAM_BASELINE_PATH = "Baseline_Path";
    public static final String PARAM_BASELINE_UPDATE_MODE = "Baseline_Update_Mode";

    // Phase 8 - Enterprise report
    public static final String PARAM_SLA_P95_MS = "SLA_P95_Ms";
    public static final String PARAM_APDEX_TARGET_MS = "Apdex_Target_Ms";
    public static final String PARAM_REGRESSION_THRESHOLD_PCT = "Regression_Threshold_Pct";
    public static final String PARAM_ENABLE_CLOUDWATCH = "Enable_CloudWatch";
    public static final String PARAM_CLOUDWATCH_REGION = "CloudWatch_Region";
    public static final String PARAM_CLOUDWATCH_METRICS_JSON = "CloudWatch_Metrics_Json";
    public static final String PARAM_CLOUDWATCH_ALARMS = "CloudWatch_Alarms";
    public static final String PARAM_CLOUDWATCH_OUTPUT_PATH = "CloudWatch_Output_Path";
    public static final String PARAM_JSON_REPORT_PATH = "Json_Report_Path";
    public static final String PARAM_CSV_REPORT_PATH = "Csv_Report_Path";

    // Phase 2
    public static final String PARAM_SPLUNK_SEARCH_URL = "Splunk_Search_URL";
    public static final String PARAM_SPLUNK_SEARCH_TOKEN = "Splunk_Search_Token";
    public static final String PARAM_SPLUNK_LOG_INDEX = "Splunk_Log_Index";
    public static final String PARAM_ENABLE_CORRELATION = "Enable_Correlation";
    public static final String PARAM_CORRELATION_WINDOW_SECONDS = "Correlation_Window_Seconds";
    public static final String PARAM_CORRELATION_OUTPUT_PATH = "Correlation_Output_Path";

    // Phase 3 - Splunk Observability Cloud metrics
    public static final String PARAM_O11Y_URL = "O11y_API_URL";
    public static final String PARAM_O11Y_TOKEN = "O11y_Token";
    public static final String PARAM_O11Y_METRICS = "O11y_Metrics";
    public static final String PARAM_O11Y_RESOLUTION_MS = "O11y_Resolution_Ms";
    public static final String PARAM_ENABLE_O11Y = "Enable_O11y";
    public static final String PARAM_O11Y_OUTPUT_PATH = "O11y_Output_Path";

    // Phase 4 - LLM analysis
    public static final String PARAM_ENABLE_LLM = "Enable_LLM";
    public static final String PARAM_LLM_PROVIDER = "LLM_Provider";
    public static final String PARAM_LLM_MODEL = "LLM_Model";
    public static final String PARAM_LLM_API_KEY = "LLM_API_Key";
    public static final String PARAM_LLM_API_KEY_ENV = "LLM_API_Key_Env";
    public static final String PARAM_LLM_BASE_URL = "LLM_Base_URL";

    private final String splunkUrl;
    private final String splunkToken;
    private final String splunkIndex;
    private final String environment;
    private final String application;
    private final String testName;
    private final String localStorePath;
    private final boolean splunkEnabled;
    private final boolean localStoreEnabled;
    private final boolean hecBatchEnabled;
    private final int hecBatchSize;
    private final long hecFlushIntervalMs;
    private final int hecQueueCapacity;
    private final boolean tlsInsecure;
    private final String outputDirectory;
    private final String reportOutputPath;
    private final boolean baselineDiffEnabled;
    private final String baselinePath;
    private final String baselineUpdateMode;
    private final long slaP95Ms;
    private final long apdexTargetMs;
    private final double regressionThresholdPct;
    private final boolean cloudwatchEnabled;
    private final String cloudwatchRegion;
    private final String cloudwatchMetricsJson;
    private final String cloudwatchAlarms;
    private final String cloudwatchOutputPath;
    private final String jsonReportPath;
    private final String csvReportPath;

    private final String splunkSearchUrl;
    private final String splunkSearchToken;
    private final String splunkLogIndex;
    private final boolean correlationEnabled;
    private final long correlationWindowSeconds;
    private final String correlationOutputPath;

    private final String o11yUrl;
    private final String o11yToken;
    private final String o11yMetrics;
    private final long o11yResolutionMs;
    private final boolean o11yEnabled;
    private final String o11yOutputPath;

    private final boolean llmEnabled;
    private final String llmProvider;
    private final String llmModel;
    private final String llmApiKey;
    private final String llmApiKeyEnv;
    private final String llmBaseUrl;

    private PluginConfig(Builder b) {
        this.splunkUrl = b.splunkUrl;
        this.splunkToken = b.splunkToken;
        this.splunkIndex = b.splunkIndex;
        this.environment = b.environment;
        this.application = b.application;
        this.testName = b.testName;
        this.localStorePath = b.localStorePath;
        this.splunkEnabled = b.splunkEnabled;
        this.localStoreEnabled = b.localStoreEnabled;
        this.hecBatchEnabled = b.hecBatchEnabled;
        this.hecBatchSize = b.hecBatchSize;
        this.hecFlushIntervalMs = b.hecFlushIntervalMs;
        this.hecQueueCapacity = b.hecQueueCapacity;
        this.tlsInsecure = b.tlsInsecure;
        this.outputDirectory = b.outputDirectory;
        this.reportOutputPath = b.reportOutputPath;
        this.baselineDiffEnabled = b.baselineDiffEnabled;
        this.baselinePath = b.baselinePath;
        this.baselineUpdateMode = b.baselineUpdateMode;
        this.slaP95Ms = b.slaP95Ms;
        this.apdexTargetMs = b.apdexTargetMs;
        this.regressionThresholdPct = b.regressionThresholdPct;
        this.cloudwatchEnabled = b.cloudwatchEnabled;
        this.cloudwatchRegion = b.cloudwatchRegion;
        this.cloudwatchMetricsJson = b.cloudwatchMetricsJson;
        this.cloudwatchAlarms = b.cloudwatchAlarms;
        this.cloudwatchOutputPath = b.cloudwatchOutputPath;
        this.jsonReportPath = b.jsonReportPath;
        this.csvReportPath = b.csvReportPath;
        this.splunkSearchUrl = b.splunkSearchUrl;
        this.splunkSearchToken = b.splunkSearchToken;
        this.splunkLogIndex = b.splunkLogIndex;
        this.correlationEnabled = b.correlationEnabled;
        this.correlationWindowSeconds = b.correlationWindowSeconds;
        this.correlationOutputPath = b.correlationOutputPath;
        this.o11yUrl = b.o11yUrl;
        this.o11yToken = b.o11yToken;
        this.o11yMetrics = b.o11yMetrics;
        this.o11yResolutionMs = b.o11yResolutionMs;
        this.o11yEnabled = b.o11yEnabled;
        this.o11yOutputPath = b.o11yOutputPath;
        this.llmEnabled = b.llmEnabled;
        this.llmProvider = b.llmProvider;
        this.llmModel = b.llmModel;
        this.llmApiKey = b.llmApiKey;
        this.llmApiKeyEnv = b.llmApiKeyEnv;
        this.llmBaseUrl = b.llmBaseUrl;
    }

    public static PluginConfig fromContext(BackendListenerContext ctx) {
        return new Builder()
                .splunkUrl(ctx.getParameter(PARAM_SPLUNK_URL, ""))
                .splunkToken(ctx.getParameter(PARAM_SPLUNK_TOKEN, ""))
                .splunkIndex(ctx.getParameter(PARAM_SPLUNK_INDEX, "performance"))
                .environment(ctx.getParameter(PARAM_ENVIRONMENT, "Performance-Test"))
                .application(ctx.getParameter(PARAM_APPLICATION, "Unknown-App"))
                .testName(ctx.getParameter(PARAM_TEST_NAME, "JMeter-Test"))
                .localStorePath(ctx.getParameter(PARAM_LOCAL_STORE_PATH, "jmeter-metrics.json"))
                .splunkEnabled(Boolean.parseBoolean(ctx.getParameter(PARAM_ENABLE_SPLUNK, "true")))
                .localStoreEnabled(Boolean.parseBoolean(ctx.getParameter(PARAM_ENABLE_LOCAL_STORE, "true")))
                .hecBatchEnabled(Boolean.parseBoolean(ctx.getParameter(PARAM_HEC_BATCH_ENABLED, "false")))
                .hecBatchSize((int) parseLong(ctx.getParameter(PARAM_HEC_BATCH_SIZE, "100"), 100))
                .hecFlushIntervalMs(parseLong(ctx.getParameter(PARAM_HEC_FLUSH_INTERVAL_MS, "1000"), 1000))
                .hecQueueCapacity((int) parseLong(ctx.getParameter(PARAM_HEC_QUEUE_CAPACITY, "10000"), 10000))
                .tlsInsecure(Boolean.parseBoolean(ctx.getParameter(PARAM_TLS_INSECURE, "false")))
                .outputDirectory(ctx.getParameter(PARAM_OUTPUT_DIRECTORY, ""))
                .reportOutputPath(ctx.getParameter(PARAM_REPORT_OUTPUT_PATH, "Performance_Report.html"))
                .baselineDiffEnabled(Boolean.parseBoolean(ctx.getParameter(PARAM_ENABLE_BASELINE_DIFF, "false")))
                .baselinePath(ctx.getParameter(PARAM_BASELINE_PATH, ""))
                .baselineUpdateMode(ctx.getParameter(PARAM_BASELINE_UPDATE_MODE, "always"))
                .slaP95Ms(parseLong(ctx.getParameter(PARAM_SLA_P95_MS, "1000"), 1000))
                .apdexTargetMs(parseLong(ctx.getParameter(PARAM_APDEX_TARGET_MS, "500"), 500))
                .regressionThresholdPct(parseDouble(ctx.getParameter(PARAM_REGRESSION_THRESHOLD_PCT, "25"), 25))
                .cloudwatchEnabled(Boolean.parseBoolean(ctx.getParameter(PARAM_ENABLE_CLOUDWATCH, "false")))
                .cloudwatchRegion(ctx.getParameter(PARAM_CLOUDWATCH_REGION, "us-east-1"))
                .cloudwatchMetricsJson(ctx.getParameter(PARAM_CLOUDWATCH_METRICS_JSON, "[]"))
                .cloudwatchAlarms(ctx.getParameter(PARAM_CLOUDWATCH_ALARMS, ""))
                .cloudwatchOutputPath(ctx.getParameter(PARAM_CLOUDWATCH_OUTPUT_PATH, "cloudwatch-metrics.json"))
                .jsonReportPath(ctx.getParameter(PARAM_JSON_REPORT_PATH, "Performance_Report.json"))
                .csvReportPath(ctx.getParameter(PARAM_CSV_REPORT_PATH, "Performance_Report.csv"))
                .splunkSearchUrl(ctx.getParameter(PARAM_SPLUNK_SEARCH_URL, ""))
                .splunkSearchToken(ctx.getParameter(PARAM_SPLUNK_SEARCH_TOKEN, ""))
                .splunkLogIndex(ctx.getParameter(PARAM_SPLUNK_LOG_INDEX, "app"))
                .correlationEnabled(Boolean.parseBoolean(ctx.getParameter(PARAM_ENABLE_CORRELATION, "false")))
                .correlationWindowSeconds(parseLong(ctx.getParameter(PARAM_CORRELATION_WINDOW_SECONDS, "30"), 30))
                .correlationOutputPath(ctx.getParameter(PARAM_CORRELATION_OUTPUT_PATH, "log-correlation.json"))
                .o11yUrl(ctx.getParameter(PARAM_O11Y_URL, ""))
                .o11yToken(ctx.getParameter(PARAM_O11Y_TOKEN, ""))
                .o11yMetrics(ctx.getParameter(PARAM_O11Y_METRICS,
                        "cpu.utilization,memory.utilization,jvm.gc.collection.count,jvm.threads.count,db.latency,k8s.pod.cpu.usage"))
                .o11yResolutionMs(parseLong(ctx.getParameter(PARAM_O11Y_RESOLUTION_MS, "10000"), 10000))
                .o11yEnabled(Boolean.parseBoolean(ctx.getParameter(PARAM_ENABLE_O11Y, "false")))
                .o11yOutputPath(ctx.getParameter(PARAM_O11Y_OUTPUT_PATH, "o11y-metrics.json"))
                .llmEnabled(Boolean.parseBoolean(ctx.getParameter(PARAM_ENABLE_LLM, "false")))
                .llmProvider(ctx.getParameter(PARAM_LLM_PROVIDER, "openai"))
                .llmModel(ctx.getParameter(PARAM_LLM_MODEL, ""))
                .llmApiKey(ctx.getParameter(PARAM_LLM_API_KEY, ""))
                .llmApiKeyEnv(ctx.getParameter(PARAM_LLM_API_KEY_ENV, ""))
                .llmBaseUrl(ctx.getParameter(PARAM_LLM_BASE_URL, ""))
                .build();
    }

    private static long parseLong(String s, long defaultVal) {
        try { return Long.parseLong(s); } catch (Exception e) { return defaultVal; }
    }

    private static double parseDouble(String s, double defaultVal) {
        try { return Double.parseDouble(s); } catch (Exception e) { return defaultVal; }
    }

    /**
     * Resolve the LLM API key using the "GUI param wins over env var"
     * contract. Returns an empty string if neither is set.
     */
    public String resolveLlmApiKey() {
        if (llmApiKey != null && !llmApiKey.isBlank()) return llmApiKey;
        if (llmApiKeyEnv != null && !llmApiKeyEnv.isBlank()) {
            String v = System.getenv(llmApiKeyEnv);
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    public String getSplunkUrl() { return splunkUrl; }
    public String getSplunkToken() { return splunkToken; }
    public String getSplunkIndex() { return splunkIndex; }
    public String getEnvironment() { return environment; }
    public String getApplication() { return application; }
    public String getTestName() { return testName; }
    public String getLocalStorePath() { return localStorePath; }
    public boolean isSplunkEnabled() { return splunkEnabled; }
    public boolean isLocalStoreEnabled() { return localStoreEnabled; }
    public boolean isHecBatchEnabled() { return hecBatchEnabled; }
    public int getHecBatchSize() { return hecBatchSize; }
    public long getHecFlushIntervalMs() { return hecFlushIntervalMs; }
    public int getHecQueueCapacity() { return hecQueueCapacity; }
    public boolean isTlsInsecure() { return tlsInsecure; }
    public String getOutputDirectory() { return outputDirectory; }
    public String getReportOutputPath() { return reportOutputPath; }
    public boolean isBaselineDiffEnabled() { return baselineDiffEnabled; }
    public String getBaselinePath() { return baselinePath; }
    public String getBaselineUpdateMode() { return baselineUpdateMode; }
    public long getSlaP95Ms() { return slaP95Ms; }
    public long getApdexTargetMs() { return apdexTargetMs; }
    public double getRegressionThresholdPct() { return regressionThresholdPct; }
    public boolean isCloudwatchEnabled() { return cloudwatchEnabled; }
    public String getCloudwatchRegion() { return cloudwatchRegion; }
    public String getCloudwatchMetricsJson() { return cloudwatchMetricsJson; }
    public String getCloudwatchAlarms() { return cloudwatchAlarms; }
    public String getCloudwatchOutputPath() { return cloudwatchOutputPath; }
    public String getJsonReportPath() { return jsonReportPath; }
    public String getCsvReportPath() { return csvReportPath; }

    /**
     * Resolve a user-configured file path against {@link #outputDirectory}.
     * Absolute paths are returned as-is. Blank input yields the output
     * directory itself. Blank output directory falls back to the JMeter
     * process cwd, which is what the plugin did in earlier releases.
     */
    public java.nio.file.Path resolvePath(String path) {
        java.nio.file.Path candidate = (path == null || path.isBlank())
                ? java.nio.file.Path.of(".")
                : java.nio.file.Path.of(path);
        if (candidate.isAbsolute()) return candidate.normalize();
        if (outputDirectory == null || outputDirectory.isBlank()) {
            return candidate.toAbsolutePath().normalize();
        }
        return java.nio.file.Path.of(outputDirectory).resolve(candidate).toAbsolutePath().normalize();
    }

    /**
     * Resolve the baseline JSON path. If the user left it blank, derive
     * {@code baseline-<test_name>.json} inside the output directory so
     * multiple test plans don't collide.
     */
    public java.nio.file.Path resolveBaselinePath() {
        if (baselinePath != null && !baselinePath.isBlank()) return resolvePath(baselinePath);
        String safe = (testName == null || testName.isBlank())
                ? "default"
                : testName.replaceAll("[^A-Za-z0-9._-]", "_");
        return resolvePath("baseline-" + safe + ".json");
    }
    public String getSplunkSearchUrl() { return splunkSearchUrl; }
    public String getSplunkSearchToken() { return splunkSearchToken; }
    public String getSplunkLogIndex() { return splunkLogIndex; }
    public boolean isCorrelationEnabled() { return correlationEnabled; }
    public long getCorrelationWindowSeconds() { return correlationWindowSeconds; }
    public String getCorrelationOutputPath() { return correlationOutputPath; }
    public String getO11yUrl() { return o11yUrl; }
    public String getO11yToken() { return o11yToken; }
    public String getO11yMetrics() { return o11yMetrics; }
    public long getO11yResolutionMs() { return o11yResolutionMs; }
    public boolean isO11yEnabled() { return o11yEnabled; }
    public String getO11yOutputPath() { return o11yOutputPath; }
    public boolean isLlmEnabled() { return llmEnabled; }
    public String getLlmProvider() { return llmProvider; }
    public String getLlmModel() { return llmModel; }
    public String getLlmApiKey() { return llmApiKey; }
    public String getLlmApiKeyEnv() { return llmApiKeyEnv; }
    public String getLlmBaseUrl() { return llmBaseUrl; }

    public static class Builder {
        private String splunkUrl;
        private String splunkToken;
        private String splunkIndex = "performance";
        private String environment = "Performance-Test";
        private String application = "Unknown-App";
        private String testName = "JMeter-Test";
        private String localStorePath = "jmeter-metrics.json";
        private boolean splunkEnabled = true;
        private boolean localStoreEnabled = true;
        private boolean hecBatchEnabled = false;
        private int hecBatchSize = 100;
        private long hecFlushIntervalMs = 1000;
        private int hecQueueCapacity = 10_000;
        private boolean tlsInsecure = false;
        private String outputDirectory = "";
        private String reportOutputPath = "Performance_Report.html";
        private boolean baselineDiffEnabled = false;
        private String baselinePath = "";
        private String baselineUpdateMode = "always";
        private long slaP95Ms = 1000;
        private long apdexTargetMs = 500;
        private double regressionThresholdPct = 25;
        private boolean cloudwatchEnabled = false;
        private String cloudwatchRegion = "us-east-1";
        private String cloudwatchMetricsJson = "[]";
        private String cloudwatchAlarms = "";
        private String cloudwatchOutputPath = "cloudwatch-metrics.json";
        private String jsonReportPath = "Performance_Report.json";
        private String csvReportPath = "Performance_Report.csv";
        private String splunkSearchUrl = "";
        private String splunkSearchToken = "";
        private String splunkLogIndex = "app";
        private boolean correlationEnabled = false;
        private long correlationWindowSeconds = 30;
        private String correlationOutputPath = "log-correlation.json";
        private String o11yUrl = "";
        private String o11yToken = "";
        private String o11yMetrics = "";
        private long o11yResolutionMs = 10_000;
        private boolean o11yEnabled = false;
        private String o11yOutputPath = "o11y-metrics.json";
        private boolean llmEnabled = false;
        private String llmProvider = "openai";
        private String llmModel = "";
        private String llmApiKey = "";
        private String llmApiKeyEnv = "";
        private String llmBaseUrl = "";

        public Builder splunkUrl(String v) { this.splunkUrl = v; return this; }
        public Builder splunkToken(String v) { this.splunkToken = v; return this; }
        public Builder splunkIndex(String v) { this.splunkIndex = v; return this; }
        public Builder environment(String v) { this.environment = v; return this; }
        public Builder application(String v) { this.application = v; return this; }
        public Builder testName(String v) { this.testName = v; return this; }
        public Builder localStorePath(String v) { this.localStorePath = v; return this; }
        public Builder splunkEnabled(boolean v) { this.splunkEnabled = v; return this; }
        public Builder localStoreEnabled(boolean v) { this.localStoreEnabled = v; return this; }
        public Builder hecBatchEnabled(boolean v) { this.hecBatchEnabled = v; return this; }
        public Builder hecBatchSize(int v) { this.hecBatchSize = v; return this; }
        public Builder hecFlushIntervalMs(long v) { this.hecFlushIntervalMs = v; return this; }
        public Builder hecQueueCapacity(int v) { this.hecQueueCapacity = v; return this; }
        public Builder tlsInsecure(boolean v) { this.tlsInsecure = v; return this; }
        public Builder outputDirectory(String v) { this.outputDirectory = v; return this; }
        public Builder reportOutputPath(String v) { this.reportOutputPath = v; return this; }
        public Builder baselineDiffEnabled(boolean v) { this.baselineDiffEnabled = v; return this; }
        public Builder baselinePath(String v) { this.baselinePath = v; return this; }
        public Builder baselineUpdateMode(String v) { this.baselineUpdateMode = v; return this; }
        public Builder slaP95Ms(long v) { this.slaP95Ms = v; return this; }
        public Builder apdexTargetMs(long v) { this.apdexTargetMs = v; return this; }
        public Builder regressionThresholdPct(double v) { this.regressionThresholdPct = v; return this; }
        public Builder cloudwatchEnabled(boolean v) { this.cloudwatchEnabled = v; return this; }
        public Builder cloudwatchRegion(String v) { this.cloudwatchRegion = v; return this; }
        public Builder cloudwatchMetricsJson(String v) { this.cloudwatchMetricsJson = v; return this; }
        public Builder cloudwatchAlarms(String v) { this.cloudwatchAlarms = v; return this; }
        public Builder cloudwatchOutputPath(String v) { this.cloudwatchOutputPath = v; return this; }
        public Builder jsonReportPath(String v) { this.jsonReportPath = v; return this; }
        public Builder csvReportPath(String v) { this.csvReportPath = v; return this; }
        public Builder splunkSearchUrl(String v) { this.splunkSearchUrl = v; return this; }
        public Builder splunkSearchToken(String v) { this.splunkSearchToken = v; return this; }
        public Builder splunkLogIndex(String v) { this.splunkLogIndex = v; return this; }
        public Builder correlationEnabled(boolean v) { this.correlationEnabled = v; return this; }
        public Builder correlationWindowSeconds(long v) { this.correlationWindowSeconds = v; return this; }
        public Builder correlationOutputPath(String v) { this.correlationOutputPath = v; return this; }
        public Builder o11yUrl(String v) { this.o11yUrl = v; return this; }
        public Builder o11yToken(String v) { this.o11yToken = v; return this; }
        public Builder o11yMetrics(String v) { this.o11yMetrics = v; return this; }
        public Builder o11yResolutionMs(long v) { this.o11yResolutionMs = v; return this; }
        public Builder o11yEnabled(boolean v) { this.o11yEnabled = v; return this; }
        public Builder o11yOutputPath(String v) { this.o11yOutputPath = v; return this; }
        public Builder llmEnabled(boolean v) { this.llmEnabled = v; return this; }
        public Builder llmProvider(String v) { this.llmProvider = v; return this; }
        public Builder llmModel(String v) { this.llmModel = v; return this; }
        public Builder llmApiKey(String v) { this.llmApiKey = v; return this; }
        public Builder llmApiKeyEnv(String v) { this.llmApiKeyEnv = v; return this; }
        public Builder llmBaseUrl(String v) { this.llmBaseUrl = v; return this; }

        public PluginConfig build() { return new PluginConfig(this); }
    }
}
