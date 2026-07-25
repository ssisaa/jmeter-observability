package com.smartjmeter.ai;

import com.smartjmeter.model.JMeterMetric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregates a list of {@link JMeterMetric}s into per-transaction
 * statistics fit for LLM prompts and executive dashboards.
 *
 * <p>Per transaction and overall: count, errors, error_rate, min, avg,
 * median, p50/p75/p90/p95/p99/p99.9, max, stddev, iqr, throughput_rps,
 * bytes totals, apdex_score, top error signatures.</p>
 */
public class MetricAggregator {

    /** Apdex satisfied threshold in ms. Same value used for both p50 and p95. */
    public static final long APDEX_T_MS_DEFAULT = 500;

    public static Map<String, Object> aggregate(List<JMeterMetric> metrics) {
        return aggregate(metrics, APDEX_T_MS_DEFAULT);
    }

    public static Map<String, Object> aggregate(List<JMeterMetric> metrics, long apdexTargetMs) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (metrics == null || metrics.isEmpty()) {
            out.put("sample_count", 0);
            return out;
        }
        Map<String, List<JMeterMetric>> byTxn = new TreeMap<>();
        long startMs = Long.MAX_VALUE;
        long stopMs = Long.MIN_VALUE;
        for (JMeterMetric m : metrics) {
            String txn = m.getTransaction() == null ? "<unlabeled>" : m.getTransaction();
            byTxn.computeIfAbsent(txn, k -> new ArrayList<>()).add(m);
            long ts = (long) m.getTimestamp();
            if (ts > 0) {
                if (ts < startMs) startMs = ts;
                if (ts > stopMs) stopMs = ts;
            }
        }
        Map<String, Object> perTxn = new LinkedHashMap<>();
        for (Map.Entry<String, List<JMeterMetric>> e : byTxn.entrySet()) {
            perTxn.put(e.getKey(), summarise(e.getValue(), startMs, stopMs, apdexTargetMs));
        }
        Map<String, Object> overall = summarise(metrics, startMs, stopMs, apdexTargetMs);
        if (startMs != Long.MAX_VALUE) overall.put("start_ms", startMs);
        if (stopMs != Long.MIN_VALUE) overall.put("stop_ms", stopMs);
        out.put("overall", overall);
        out.put("per_transaction", perTxn);
        return out;
    }

    private static Map<String, Object> summarise(List<JMeterMetric> ms, long start, long stop, long apdexT) {
        int n = ms.size();
        long[] rt = new long[n];
        long errors = 0;
        long bytesSentTotal = 0;
        long bytesRecvTotal = 0;
        double sum = 0;
        long satisfied = 0;
        long tolerating = 0;
        Map<String, Long> errorSigs = new HashMap<>();
        for (int i = 0; i < n; i++) {
            JMeterMetric m = ms.get(i);
            long v = m.getResponseTime();
            rt[i] = v;
            sum += v;
            if (!m.isSuccess()) {
                errors++;
                String sig = errorSignature(m);
                errorSigs.merge(sig, 1L, Long::sum);
            }
            bytesSentTotal += m.getBytesSent();
            bytesRecvTotal += m.getBytesReceived();
            if (v <= apdexT) satisfied++;
            else if (v <= 4 * apdexT) tolerating++;
        }
        java.util.Arrays.sort(rt);
        double avg = n == 0 ? 0 : sum / n;
        double variance = 0;
        for (long v : rt) variance += (v - avg) * (v - avg);
        double stddev = n == 0 ? 0 : Math.sqrt(variance / n);
        long q1 = percentile(rt, 25);
        long q3 = percentile(rt, 75);
        long iqr = q3 - q1;
        long median = percentile(rt, 50);

        long durationMs = (start > 0 && stop > start) ? (stop - start) : 0;
        double throughputRps = durationMs > 0 ? (n * 1000d) / durationMs : 0;
        double apdex = n == 0 ? 0 : (satisfied + tolerating / 2.0) / n;

        // Top 5 error signatures
        List<Map<String, Object>> topErrors = errorSigs.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(en -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("signature", en.getKey());
                    row.put("count", en.getValue());
                    return row;
                })
                .toList();

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("count", (long) n);
        s.put("errors", errors);
        s.put("error_rate", n == 0 ? 0.0 : (double) errors / n);
        s.put("rt_min_ms", n == 0 ? 0 : rt[0]);
        s.put("rt_avg_ms", Math.round(avg));
        s.put("rt_median_ms", median);
        s.put("rt_p50_ms", median);
        s.put("rt_p75_ms", q3);
        s.put("rt_p90_ms", percentile(rt, 90));
        s.put("rt_p95_ms", percentile(rt, 95));
        s.put("rt_p99_ms", percentile(rt, 99));
        s.put("rt_p999_ms", percentile(rt, 99.9));
        s.put("rt_max_ms", n == 0 ? 0 : rt[n - 1]);
        s.put("rt_stddev_ms", Math.round(stddev));
        s.put("rt_iqr_ms", iqr);
        s.put("throughput_rps", round2(throughputRps));
        s.put("bytes_sent_total", bytesSentTotal);
        s.put("bytes_received_total", bytesRecvTotal);
        s.put("apdex_score", round2(apdex));
        s.put("apdex_target_ms", apdexT);
        s.put("top_error_signatures", topErrors);
        return s;
    }

    private static long percentile(long[] sorted, double p) {
        if (sorted.length == 0) return 0;
        int idx = Math.min(sorted.length - 1, (int) Math.ceil((p / 100.0) * sorted.length) - 1);
        if (idx < 0) idx = 0;
        return sorted[idx];
    }

    private static double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

    private static String errorSignature(JMeterMetric m) {
        String code = m.getResponseCode() == null || m.getResponseCode().isBlank() ? "-" : m.getResponseCode();
        String txn = m.getTransaction() == null ? "<unlabeled>" : m.getTransaction();
        return txn + " :: " + code;
    }

    private MetricAggregator() { }
}
