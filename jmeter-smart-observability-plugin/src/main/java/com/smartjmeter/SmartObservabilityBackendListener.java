package com.smartjmeter;

import com.smartjmeter.ai.AIAnalyzer;
import com.smartjmeter.config.PluginConfig;
import com.smartjmeter.model.JMeterMetric;
import com.smartjmeter.report.ReportGenerator;
import com.smartjmeter.splunk.SplunkHECClient;
import com.smartjmeter.store.LocalJsonStore;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;

import java.util.List;
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
 * </ol>
 *
 * <p>At <code>teardownTest</code> it runs the {@link AIAnalyzer}
 * skeleton and writes an HTML report via {@link ReportGenerator}.</p>
 */
public class SmartObservabilityBackendListener extends AbstractBackendListenerClient {

    private static final Logger LOG = Logger.getLogger(SmartObservabilityBackendListener.class.getName());

    private PluginConfig config;
    private SplunkHECClient splunk;
    private LocalJsonStore localStore;

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
                "Smart Observability Plugin initialised (env={0}, app={1}, splunk={2}, local={3})",
                new Object[]{
                        config.getEnvironment(),
                        config.getApplication(),
                        config.isSplunkEnabled(),
                        config.isLocalStoreEnabled()
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
        }
    }

    @Override
    public void teardownTest(BackendListenerContext context) throws Exception {
        try {
            String analysis = new AIAnalyzer().analyze("aggregate-placeholder");
            new ReportGenerator().generate(analysis);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Report generation failed", e);
        }
        LOG.info("Smart Observability Plugin Completed");
        super.teardownTest(context);
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
