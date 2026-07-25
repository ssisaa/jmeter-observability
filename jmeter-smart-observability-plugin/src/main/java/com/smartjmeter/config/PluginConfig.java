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

    private final String splunkUrl;
    private final String splunkToken;
    private final String splunkIndex;
    private final String environment;
    private final String application;
    private final String testName;
    private final String localStorePath;
    private final boolean splunkEnabled;
    private final boolean localStoreEnabled;

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
                .build();
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

        public Builder splunkUrl(String v) { this.splunkUrl = v; return this; }
        public Builder splunkToken(String v) { this.splunkToken = v; return this; }
        public Builder splunkIndex(String v) { this.splunkIndex = v; return this; }
        public Builder environment(String v) { this.environment = v; return this; }
        public Builder application(String v) { this.application = v; return this; }
        public Builder testName(String v) { this.testName = v; return this; }
        public Builder localStorePath(String v) { this.localStorePath = v; return this; }
        public Builder splunkEnabled(boolean v) { this.splunkEnabled = v; return this; }
        public Builder localStoreEnabled(boolean v) { this.localStoreEnabled = v; return this; }

        public PluginConfig build() { return new PluginConfig(this); }
    }
}
