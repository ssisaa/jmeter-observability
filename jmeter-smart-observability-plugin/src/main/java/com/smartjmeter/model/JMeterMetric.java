package com.smartjmeter.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data model representing a single JMeter sample metric flushed by the
 * Smart Observability Backend Listener.
 *
 * <p>All fields are plain JSON-serializable primitives / strings so this
 * class can be sent to Splunk HEC, written to the local JSON store, or
 * consumed by the AI analyzer without any adapters.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JMeterMetric {

    private String testName;
    private String transaction;
    private long responseTime;
    private long latency;
    private long bytesSent;
    private long bytesReceived;
    private boolean success;
    private double timestamp;
    private String environment;
    private String application;
    private String responseCode;
    private String threadName;

    public JMeterMetric() {
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getTransaction() {
        return transaction;
    }

    public void setTransaction(String transaction) {
        this.transaction = transaction;
    }

    public long getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(long responseTime) {
        this.responseTime = responseTime;
    }

    public long getLatency() {
        return latency;
    }

    public void setLatency(long latency) {
        this.latency = latency;
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public void setBytesSent(long bytesSent) {
        this.bytesSent = bytesSent;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public void setBytesReceived(long bytesReceived) {
        this.bytesReceived = bytesReceived;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }
}
