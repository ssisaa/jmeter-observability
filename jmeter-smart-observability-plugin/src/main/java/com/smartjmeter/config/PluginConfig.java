package com.smartjmeter.config;

import org.apache.jmeter.visualizers.backend.BackendListenerContext;

/**
 * Typed view over the {@link BackendListenerContext} parameters configured
 * through the JMeter GUI. Keeps parameter keys in a single place so they
 * cannot drift between the descriptor and the listener code.
 */
public class PluginConfig {

    public static final String PARAM_SPLUNK_URL = "Splunk_URL";
    public static final String PARAM_SPLUNK_TOKEN = "Splunk_Token";
    public static final String PARAM_SPLUNK_INDEX = "Splunk_Index";
    public static final String PARAM_ENVIRONMENT = "Environment";
    public static final String PARAM_APPLICATION = "Application";
    public static final String PARAM_TEST_NAME = "Test_Name";
    public static final String PARAM_LOCAL_STORE_PATH = "Local_Store_Path";
    public static final String PARAM_ENABLE_SPLUNK = "Enable_Splunk";
    public static final String PARAM_ENABLE_LOCAL_STORE = "Enable_Local_Store";

    // Phase 2: correlation
    public static final String PARAM_SPLUNK_SEARCH_URL = "Splunk_Search_URL";
    public static final String PARAM_SPLUNK_SEARCH_TOKEN = "Splunk_Search_Token";
    public static final String PARAM_SPLUNK_LOG_INDEX = "Splunk_Log_Index";
    public static final String PARAM_ENABLE_CORRELATION = "Enable_Correlation";
    public static final String PARAM_CORRELATION_WINDOW_SECONDS = "Correlation_Window_Seconds";
    public static final String PARAM_CORRELATION_OUTPUT_PATH = "Correlation_Output_Path";

    private final String splunkUrl;
    private final String splunkToken;
    private final String splunkIndex;
    private final String environment;
    private final String application;
    private final String testName;
    private final String localStorePath;
    private final boolean splunkEnabled;
    private final boolean localStoreEnabled;

    private final String splunkSearchUrl;
    private final String splunkSearchToken;
    private final String splunkLogIndex;
    private final boolean correlationEnabled;
    private final long correlationWindowSeconds;
    private final String correlationOutputPath;

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
        this.splunkSearchUrl = b.splunkSearchUrl;
        this.splunkSearchToken = b.splunkSearchToken;
        this.splunkLogIndex = b.splunkLogIndex;
        this.correlationEnabled = b.correlationEnabled;
        this.correlationWindowSeconds = b.correlationWindowSeconds;
        this.correlationOutputPath = b.correlationOutputPath;
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
                .splunkSearchUrl(ctx.getParameter(PARAM_SPLUNK_SEARCH_URL, ""))
                .splunkSearchToken(ctx.getParameter(PARAM_SPLUNK_SEARCH_TOKEN, ""))
                .splunkLogIndex(ctx.getParameter(PARAM_SPLUNK_LOG_INDEX, "app"))
                .correlationEnabled(Boolean.parseBoolean(ctx.getParameter(PARAM_ENABLE_CORRELATION, "false")))
                .correlationWindowSeconds(parseLong(ctx.getParameter(PARAM_CORRELATION_WINDOW_SECONDS, "30"), 30))
                .correlationOutputPath(ctx.getParameter(PARAM_CORRELATION_OUTPUT_PATH, "log-correlation.json"))
                .build();
    }

    private static long parseLong(String s, long defaultVal) {
        try { return Long.parseLong(s); } catch (Exception e) { return defaultVal; }
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
    public String getSplunkSearchUrl() { return splunkSearchUrl; }
    public String getSplunkSearchToken() { return splunkSearchToken; }
    public String getSplunkLogIndex() { return splunkLogIndex; }
    public boolean isCorrelationEnabled() { return correlationEnabled; }
    public long getCorrelationWindowSeconds() { return correlationWindowSeconds; }
    public String getCorrelationOutputPath() { return correlationOutputPath; }

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
        private String splunkSearchUrl = "";
        private String splunkSearchToken = "";
        private String splunkLogIndex = "app";
        private boolean correlationEnabled = false;
        private long correlationWindowSeconds = 30;
        private String correlationOutputPath = "log-correlation.json";

        public Builder splunkUrl(String v) { this.splunkUrl = v; return this; }
        public Builder splunkToken(String v) { this.splunkToken = v; return this; }
        public Builder splunkIndex(String v) { this.splunkIndex = v; return this; }
        public Builder environment(String v) { this.environment = v; return this; }
        public Builder application(String v) { this.application = v; return this; }
        public Builder testName(String v) { this.testName = v; return this; }
        public Builder localStorePath(String v) { this.localStorePath = v; return this; }
        public Builder splunkEnabled(boolean v) { this.splunkEnabled = v; return this; }
        public Builder localStoreEnabled(boolean v) { this.localStoreEnabled = v; return this; }
        public Builder splunkSearchUrl(String v) { this.splunkSearchUrl = v; return this; }
        public Builder splunkSearchToken(String v) { this.splunkSearchToken = v; return this; }
        public Builder splunkLogIndex(String v) { this.splunkLogIndex = v; return this; }
        public Builder correlationEnabled(boolean v) { this.correlationEnabled = v; return this; }
        public Builder correlationWindowSeconds(long v) { this.correlationWindowSeconds = v; return this; }
        public Builder correlationOutputPath(String v) { this.correlationOutputPath = v; return this; }

        public PluginConfig build() { return new PluginConfig(this); }
    }
}
