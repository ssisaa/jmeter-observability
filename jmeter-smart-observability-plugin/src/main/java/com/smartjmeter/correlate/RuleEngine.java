package com.smartjmeter.correlate;

import com.smartjmeter.score.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic rule engine. Consumes the aggregated telemetry set
 * produced during teardown and yields ranked {@link Finding}s.
 *
 * <p>The rules operate on the pre-normalised view of the run:
 * aggregate stats, baseline diff, correlation summary, o11y metric
 * snapshot, and CloudWatch alarms. Fires deterministically; every
 * finding carries a base confidence that later steps can amplify.</p>
 */
public class RuleEngine {

    public List<Finding> evaluate(Map<String, Object> aggregate,
                                  Map<String, Object> baselineDiff,
                                  Map<String, Object> correlation,
                                  Map<String, List<Map<String, Object>>> o11y,
                                  Map<String, Object> cloudwatch) {
        return evaluate(aggregate, baselineDiff, correlation, o11y, cloudwatch, Map.of());
    }

    public List<Finding> evaluate(Map<String, Object> aggregate,
                                  Map<String, Object> baselineDiff,
                                  Map<String, Object> correlation,
                                  Map<String, List<Map<String, Object>>> o11y,
                                  Map<String, Object> cloudwatch,
                                  Map<String, Map<String, List<Map<String, Object>>>> externalMetrics) {
        List<Finding> out = new ArrayList<>();
        Map<String, Object> overall = safeMap(aggregate.get("overall"));

        double errorRate = num(overall.get("error_rate"));
        long p95 = numL(overall.get("rt_p95_ms"));
        long p99 = numL(overall.get("rt_p99_ms"));
        long failures = numL(correlation == null ? null : correlation.get("failure_count"));

        // R-CODE-REG - regression vs baseline
        if (baselineDiff != null && Boolean.TRUE.equals(baselineDiff.get("has_previous"))) {
            Map<String, Object> overallDiff = safeMap(baselineDiff.get("overall"));
            double p95Pct = num(overallDiff.get("rt_p95_pct"));
            double errPp = num(overallDiff.get("error_rate_pp"));
            if (p95Pct >= 25.0) {
                out.add(finding("R-CODE-REG",
                        "Overall p95 regressed " + p95Pct + "% vs previous run",
                        "regression",
                        p95Pct >= 50 ? Finding.Severity.CRITICAL : Finding.Severity.HIGH,
                        0.85,
                        "baseline diff overall.rt_p95_pct=" + p95Pct + "%"));
            }
            if (errPp >= 2.0) {
                out.add(finding("R-CODE-REG-ERR",
                        "Overall error rate regressed +" + errPp + "pp vs previous run",
                        "regression",
                        errPp >= 5 ? Finding.Severity.CRITICAL : Finding.Severity.HIGH,
                        0.9,
                        "baseline diff overall.error_rate_pp=" + errPp + "pp"));
            }
        }

        // R-ERR-RATE - high absolute error rate
        if (errorRate >= 0.10) {
            out.add(finding("R-ERR-RATE",
                    "High error rate " + Math.round(errorRate * 1000.0) / 10.0 + "%",
                    "reliability",
                    errorRate >= 0.20 ? Finding.Severity.CRITICAL : Finding.Severity.HIGH,
                    0.95,
                    "aggregate.error_rate=" + errorRate));
        }

        // R-LATENCY - p95 spike beyond common SLA (500ms)
        if (p95 >= 1500) {
            out.add(finding("R-LATENCY",
                    "Overall p95 latency " + p95 + " ms is high",
                    "latency",
                    p95 >= 3000 ? Finding.Severity.HIGH : Finding.Severity.MEDIUM,
                    0.6,
                    "aggregate.rt_p95_ms=" + p95));
        }

        // R-TAIL - very heavy tail (p99 >> p95)
        if (p95 > 0 && p99 >= 3 * p95) {
            out.add(finding("R-TAIL",
                    "Heavy tail: p99 " + p99 + " ms is " + (p99 / Math.max(1, p95)) + "x p95",
                    "latency",
                    Finding.Severity.MEDIUM,
                    0.7,
                    "aggregate.rt_p99_ms/rt_p95_ms"));
        }

        // R-OBS-GAP - no logs found for failed samples
        if (failures > 0 && correlation != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> windows = (List<Map<String, Object>>) correlation.getOrDefault("windows", List.of());
            long totalEvents = 0;
            for (Map<String, Object> w : windows) totalEvents += numL(w.get("event_count"));
            if (totalEvents == 0) {
                out.add(finding("R-OBS-GAP",
                        "No Splunk log events found for " + failures + " failed samples",
                        "observability",
                        Finding.Severity.MEDIUM,
                        0.55,
                        "correlation.windows.event_count=0"));
            }
        }

        // R-INFRA-CPU - infra CPU saturation from O11y
        double cpuMax = maxSeriesValue(o11y, "cpu.utilization");
        if (cpuMax >= 0.85) {
            out.add(finding("R-INFRA-CPU",
                    "CPU saturation observed (max " + Math.round(cpuMax * 100) + "%)",
                    "infrastructure",
                    cpuMax >= 0.95 ? Finding.Severity.HIGH : Finding.Severity.MEDIUM,
                    0.75,
                    "o11y.cpu.utilization.max=" + cpuMax));
        }

        // R-INFRA-MEM - memory saturation
        double memMax = maxSeriesValue(o11y, "memory.utilization");
        if (memMax >= 0.90) {
            out.add(finding("R-INFRA-MEM",
                    "Memory saturation observed (max " + Math.round(memMax * 100) + "%)",
                    "infrastructure",
                    memMax >= 0.97 ? Finding.Severity.HIGH : Finding.Severity.MEDIUM,
                    0.8,
                    "o11y.memory.utilization.max=" + memMax));
        }

        // R-GC-PAUSE - GC pause too high
        double gcMax = maxSeriesValue(o11y, "jvm.gc.pause_ms");
        if (gcMax >= 500) {
            out.add(finding("R-GC-PAUSE",
                    "Long GC pause detected (max " + Math.round(gcMax) + " ms)",
                    "gc",
                    gcMax >= 2000 ? Finding.Severity.HIGH : Finding.Severity.MEDIUM,
                    0.7,
                    "o11y.jvm.gc.pause_ms.max=" + gcMax));
        }

        // R-K8S-RESTART - pod restarts
        double restarts = sumSeriesValue(o11y, "k8s.pod.restart_count");
        if (restarts >= 3) {
            out.add(finding("R-K8S-RESTART",
                    "Pod restarts during test: " + Math.round(restarts),
                    "reliability",
                    restarts >= 10 ? Finding.Severity.HIGH : Finding.Severity.MEDIUM,
                    0.85,
                    "o11y.k8s.pod.restart_count.sum=" + restarts));
        }

        // R-CW-ALARM - CloudWatch alarms fired during window
        if (cloudwatch != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> alarms = (List<Map<String, Object>>) cloudwatch.getOrDefault("alarms", List.of());
            for (Map<String, Object> a : alarms) {
                if ("ALARM".equalsIgnoreCase(String.valueOf(a.get("state")))) {
                    out.add(finding("R-CW-ALARM-" + a.get("name"),
                            "CloudWatch alarm ALARM: " + a.get("name"),
                            "infrastructure",
                            Finding.Severity.HIGH,
                            0.9,
                            "cloudwatch.alarms[" + a.get("name") + "]"));
                }
            }
        }

        // v2.0.2 - External metric backend rules (Prometheus/Loki/Elastic/APM/Cloud)
        if (externalMetrics != null && !externalMetrics.isEmpty()) {
            for (Map.Entry<String, Map<String, List<Map<String, Object>>>> be : externalMetrics.entrySet()) {
                String backend = be.getKey();
                for (Map.Entry<String, List<Map<String, Object>>> q : be.getValue().entrySet()) {
                    String label = q.getKey();
                    List<Map<String, Object>> points = q.getValue();
                    if (points == null || points.isEmpty()) continue;
                    double max = 0, sum = 0;
                    for (Map<String, Object> p : points) {
                        double v = num(p.get("value"));
                        sum += v;
                        if (v > max) max = v;
                    }
                    double avg = sum / points.size();
                    String key = label.toLowerCase();

                    // Rule: any query labelled "*cpu*" whose max >= 0.85 or 85
                    if ((key.contains("cpu") || key.contains("saturation")) && (max >= 0.85 || max >= 85)) {
                        out.add(finding("R-EXT-CPU-" + backend + "-" + label,
                                backend + "/" + label + " CPU saturation observed (max " + fmt(max) + ")",
                                "infrastructure",
                                (max >= 0.95 || max >= 95) ? Finding.Severity.HIGH : Finding.Severity.MEDIUM,
                                0.7,
                                backend + "." + label + ".max=" + fmt(max)));
                    }
                    // Rule: any query labelled "*error*" whose avg >= 0.05 or 5(%)
                    if (key.contains("error") && (avg >= 0.05 || avg >= 5)) {
                        out.add(finding("R-EXT-ERR-" + backend + "-" + label,
                                backend + "/" + label + " error rate elevated (avg " + fmt(avg) + ")",
                                "reliability",
                                (avg >= 0.10 || avg >= 10) ? Finding.Severity.HIGH : Finding.Severity.MEDIUM,
                                0.75,
                                backend + "." + label + ".avg=" + fmt(avg)));
                    }
                    // Rule: any query labelled "*latency*" or "*duration*" whose max >= 2000
                    if ((key.contains("latency") || key.contains("duration")) && max >= 2000) {
                        out.add(finding("R-EXT-LAT-" + backend + "-" + label,
                                backend + "/" + label + " latency spike (max " + fmt(max) + " ms)",
                                "latency",
                                max >= 5000 ? Finding.Severity.HIGH : Finding.Severity.MEDIUM,
                                0.7,
                                backend + "." + label + ".max=" + fmt(max)));
                    }
                }
            }
        }

        out.sort((a, b) -> b.severity().ordinal() == a.severity().ordinal()
                ? Double.compare(b.confidence(), a.confidence())
                : Integer.compare(a.severity().ordinal(), b.severity().ordinal()));
        return out;
    }

    private static Finding finding(String id, String title, String category,
                                   Finding.Severity sev, double conf, String evidence) {
        return new Finding(id, title, category, sev, conf, evidence, List.of(), Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> safeMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : Map.of();
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static long numL(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    private static double maxSeriesValue(Map<String, List<Map<String, Object>>> o11y, String metric) {
        if (o11y == null) return 0;
        List<Map<String, Object>> points = o11y.getOrDefault(metric, List.of());
        double max = 0;
        for (Map<String, Object> p : points) {
            double v = num(p.get("value"));
            if (v > max) max = v;
        }
        return max;
    }

    private static double sumSeriesValue(Map<String, List<Map<String, Object>>> o11y, String metric) {
        if (o11y == null) return 0;
        List<Map<String, Object>> points = o11y.getOrDefault(metric, List.of());
        double sum = 0;
        for (Map<String, Object> p : points) sum += num(p.get("value"));
        return sum;
    }

    private static String fmt(double v) {
        return Math.round(v * 100.0) / 100.0 + "";
    }
}
