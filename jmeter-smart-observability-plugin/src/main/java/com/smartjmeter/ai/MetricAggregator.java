package com.smartjmeter.ai;

import com.smartjmeter.model.JMeterMetric;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregates a list of {@link JMeterMetric}s into per-transaction
 * statistics that fit into an LLM prompt without exploding the token
 * budget.
 *
 * <p>Statistics computed per transaction: count, error count, error
 * rate, min/avg/p95/max response time, total bytes received. A
 * top-level {@code overall} block carries the same numbers across all
 * transactions plus the global start / end timestamps.</p>
 */
public class MetricAggregator {

    public static Map<String, Object> aggregate(List<JMeterMetric> metrics) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (metrics == null || metrics.isEmpty()) {
            out.put("sample_count", 0);
            return out;
        }
        Map<String, List<Long>> rtByTxn = new TreeMap<>();
        Map<String, long[]> counters = new HashMap<>();
        long startMs = Long.MAX_VALUE;
        long stopMs = Long.MIN_VALUE;
        long totalBytes = 0;
        long totalErrors = 0;
        List<Long> allRt = new java.util.ArrayList<>();

        for (JMeterMetric m : metrics) {
            String txn = m.getTransaction() == null ? "<unlabeled>" : m.getTransaction();
            rtByTxn.computeIfAbsent(txn, k -> new java.util.ArrayList<>()).add(m.getResponseTime());
            long[] c = counters.computeIfAbsent(txn, k -> new long[2]); // [count, errors]
            c[0]++;
            if (!m.isSuccess()) { c[1]++; totalErrors++; }
            allRt.add(m.getResponseTime());
            totalBytes += m.getBytesReceived();
            long ts = (long) m.getTimestamp();
            if (ts > 0) {
                if (ts < startMs) startMs = ts;
                if (ts > stopMs) stopMs = ts;
            }
        }

        Map<String, Object> perTxn = new LinkedHashMap<>();
        for (Map.Entry<String, List<Long>> e : rtByTxn.entrySet()) {
            perTxn.put(e.getKey(), summarise(e.getValue(), counters.get(e.getKey())));
        }

        Map<String, Object> overall = summarise(allRt, new long[]{metrics.size(), totalErrors});
        overall.put("bytes_received_total", totalBytes);
        if (startMs != Long.MAX_VALUE) overall.put("start_ms", startMs);
        if (stopMs != Long.MIN_VALUE) overall.put("stop_ms", stopMs);

        out.put("overall", overall);
        out.put("per_transaction", perTxn);
        return out;
    }

    private static Map<String, Object> summarise(List<Long> rt, long[] counters) {
        List<Long> sorted = new java.util.ArrayList<>(rt);
        java.util.Collections.sort(sorted);
        long count = counters[0];
        long errors = counters[1];
        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        long p95 = sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(0.95 * sorted.size()) - 1));
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("count", count);
        s.put("errors", errors);
        s.put("error_rate", count == 0 ? 0.0 : (double) errors / count);
        s.put("rt_min_ms", min);
        s.put("rt_avg_ms", Math.round(avg));
        s.put("rt_p95_ms", p95);
        s.put("rt_max_ms", max);
        return s;
    }
}
