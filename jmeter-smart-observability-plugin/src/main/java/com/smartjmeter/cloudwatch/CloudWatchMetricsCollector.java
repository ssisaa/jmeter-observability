package com.smartjmeter.cloudwatch;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Config-driven collector for Amazon CloudWatch metrics + alarm state.
 *
 * <p>Metric config format (JSON list, provided via
 * {@code Cloudwatch_Metrics_Path}):</p>
 * <pre>
 * [
 *   { "namespace": "AWS/EC2", "metric": "CPUUtilization",
 *     "dimensions": {"InstanceId": "i-abc"}, "stat": "Average" },
 *   { "namespace": "AWS/RDS", "metric": "DatabaseConnections",
 *     "dimensions": {"DBInstanceIdentifier": "prod-db"}, "stat": "Maximum" }
 * ]
 * </pre>
 *
 * <p>Auth: AWS SDK default credential provider chain (env vars, profile,
 * IRSA, instance profile). Missing credentials or an empty metric list
 * degrade to an empty result - never throws.</p>
 */
public class CloudWatchMetricsCollector implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(CloudWatchMetricsCollector.class.getName());

    private final String region;
    private final CloudWatchClient client;

    public CloudWatchMetricsCollector(String region) {
        this.region = region == null || region.isBlank() ? "us-east-1" : region;
        CloudWatchClient c = null;
        try {
            c = CloudWatchClient.builder()
                    .region(Region.of(this.region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "CloudWatch client init failed; collector disabled", e);
        }
        this.client = c;
    }

    /**
     * Fetch each metric config over {@code [startMs, stopMs]} and
     * return a map keyed by "<namespace>/<metric>" with a list of
     * {@code {ts, value}} points.
     */
    public Map<String, Object> collect(List<Map<String, Object>> metricConfigs,
                                       List<String> alarmNames,
                                       long startMs,
                                       long stopMs) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> metrics = new LinkedHashMap<>();
        List<Map<String, Object>> alarmStates = new ArrayList<>();
        if (client == null) {
            out.put("metrics", metrics);
            out.put("alarms", alarmStates);
            return out;
        }
        Instant start = Instant.ofEpochMilli(startMs);
        Instant end = Instant.ofEpochMilli(stopMs);
        int period = (int) Math.max(60, Math.min(3600, (stopMs - startMs) / 60_000 * 60));

        if (metricConfigs != null) {
            for (Map<String, Object> cfg : metricConfigs) {
                try {
                    String key = cfg.get("namespace") + "/" + cfg.get("metric");
                    metrics.put(key, fetchOne(cfg, start, end, period));
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "CloudWatch metric fetch failed: " + cfg, e);
                }
            }
        }
        if (alarmNames != null && !alarmNames.isEmpty()) {
            try {
                DescribeAlarmsResponse resp = client.describeAlarms(
                        DescribeAlarmsRequest.builder().alarmNames(alarmNames).build());
                for (MetricAlarm a : resp.metricAlarms()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", a.alarmName());
                    row.put("state", a.stateValueAsString());
                    row.put("reason", a.stateReason());
                    row.put("updated", a.stateUpdatedTimestamp() == null ? null
                            : a.stateUpdatedTimestamp().toString());
                    alarmStates.add(row);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "CloudWatch describeAlarms failed", e);
            }
        }
        out.put("metrics", metrics);
        out.put("alarms", alarmStates);
        return out;
    }

    private List<Map<String, Object>> fetchOne(Map<String, Object> cfg, Instant start, Instant end, int period) {
        String ns = (String) cfg.get("namespace");
        String metric = (String) cfg.get("metric");
        String stat = (String) cfg.getOrDefault("stat", "Average");
        @SuppressWarnings("unchecked")
        Map<String, String> dims = (Map<String, String>) cfg.getOrDefault("dimensions", Map.of());
        List<Dimension> dimList = new ArrayList<>();
        for (Map.Entry<String, String> e : dims.entrySet()) {
            dimList.add(Dimension.builder().name(e.getKey()).value(e.getValue()).build());
        }
        GetMetricStatisticsRequest req = GetMetricStatisticsRequest.builder()
                .namespace(ns)
                .metricName(metric)
                .dimensions(dimList)
                .startTime(start)
                .endTime(end)
                .period(period)
                .statistics(Statistic.fromValue(stat))
                .build();
        GetMetricStatisticsResponse resp = client.getMetricStatistics(req);
        List<Map<String, Object>> points = new ArrayList<>();
        for (Datapoint dp : resp.datapoints()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("ts", dp.timestamp().toEpochMilli());
            switch (stat.toLowerCase(Locale.ROOT)) {
                case "sum" -> point.put("value", dp.sum());
                case "maximum" -> point.put("value", dp.maximum());
                case "minimum" -> point.put("value", dp.minimum());
                case "samplecount" -> point.put("value", dp.sampleCount());
                default -> point.put("value", dp.average());
            }
            points.add(point);
        }
        points.sort(Comparator.comparingLong(p -> (long) p.get("ts")));
        return points;
    }

    @Override
    public void close() {
        if (client != null) client.close();
    }
}
