package com.smartjmeter.baseline;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compute a per-transaction and overall delta between a previous
 * baseline aggregate and the current-run aggregate produced by
 * {@code MetricAggregator}.
 *
 * <p>The output shape is:</p>
 * <pre>
 * {
 *   "has_previous": true,
 *   "previous_at": "2026-01-25T14:00:00Z",
 *   "overall": {
 *     "count_delta": +2,
 *     "error_rate_pp": +3.0,      // percentage points
 *     "rt_avg_pct": +12.5,
 *     "rt_p95_pct": +40.0,
 *     "rt_max_pct": +5.0
 *   },
 *   "per_transaction": {
 *     "Login":    { same 5 keys },
 *     "Checkout": { ... },
 *     "NewOnly":  { "status": "new" }
 *   },
 *   "notable": [ "Checkout p95 +40.0% vs baseline" ... ]
 * }
 * </pre>
 */
public class BaselineDiff {

    /** Threshold (%) above which a delta is called out in {@code notable}. */
    public static final double NOTABLE_PCT = 20.0;
    /** Threshold (percentage points) for error-rate call-outs. */
    public static final double NOTABLE_ERR_PP = 2.0;

    @SuppressWarnings("unchecked")
    public static Map<String, Object> compute(Map<String, Object> previousEnvelope,
                                              Map<String, Object> currentAggregate) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (previousEnvelope == null || previousEnvelope.isEmpty()) {
            out.put("has_previous", false);
            return out;
        }
        out.put("has_previous", true);
        out.put("previous_at", previousEnvelope.getOrDefault("saved_at", ""));

        Map<String, Object> prevAgg = (Map<String, Object>) previousEnvelope.getOrDefault("aggregate", Map.of());
        Map<String, Object> prevOverall = (Map<String, Object>) prevAgg.getOrDefault("overall", Map.of());
        Map<String, Object> curOverall = (Map<String, Object>) currentAggregate.getOrDefault("overall", Map.of());
        out.put("overall", diffBlock(prevOverall, curOverall));

        Map<String, Object> prevTxn = (Map<String, Object>) prevAgg.getOrDefault("per_transaction", Map.of());
        Map<String, Object> curTxn = (Map<String, Object>) currentAggregate.getOrDefault("per_transaction", Map.of());
        Set<String> allTxn = new LinkedHashSet<>();
        allTxn.addAll(prevTxn.keySet());
        allTxn.addAll(curTxn.keySet());
        Map<String, Object> perTxn = new LinkedHashMap<>();
        java.util.List<String> notable = new java.util.ArrayList<>();
        for (String name : allTxn) {
            Map<String, Object> p = (Map<String, Object>) prevTxn.get(name);
            Map<String, Object> c = (Map<String, Object>) curTxn.get(name);
            if (p == null) {
                perTxn.put(name, Map.of("status", "new"));
                notable.add(name + " is new (not in baseline)");
                continue;
            }
            if (c == null) {
                perTxn.put(name, Map.of("status", "gone"));
                notable.add(name + " missing from this run (was in baseline)");
                continue;
            }
            Map<String, Object> block = diffBlock(p, c);
            perTxn.put(name, block);
            addNotable(notable, name, block);
        }
        out.put("per_transaction", perTxn);

        addNotable(notable, "overall", (Map<String, Object>) out.get("overall"));
        out.put("notable", notable);
        return out;
    }

    private static Map<String, Object> diffBlock(Map<String, Object> prev, Map<String, Object> cur) {
        long prevCount = asLong(prev.get("count"));
        long curCount = asLong(cur.get("count"));
        double prevErrRate = asDouble(prev.get("error_rate"));
        double curErrRate = asDouble(cur.get("error_rate"));
        long prevAvg = asLong(prev.get("rt_avg_ms"));
        long curAvg = asLong(cur.get("rt_avg_ms"));
        long prevP95 = asLong(prev.get("rt_p95_ms"));
        long curP95 = asLong(cur.get("rt_p95_ms"));
        long prevMax = asLong(prev.get("rt_max_ms"));
        long curMax = asLong(cur.get("rt_max_ms"));

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("count_delta", curCount - prevCount);
        b.put("error_rate_pp", round1((curErrRate - prevErrRate) * 100.0));
        b.put("rt_avg_pct", pct(prevAvg, curAvg));
        b.put("rt_p95_pct", pct(prevP95, curP95));
        b.put("rt_max_pct", pct(prevMax, curMax));
        return b;
    }

    private static void addNotable(java.util.List<String> notable, String name, Map<String, Object> b) {
        if (b == null) return;
        Double p95 = (Double) b.get("rt_p95_pct");
        Double avg = (Double) b.get("rt_avg_pct");
        Double errPp = (Double) b.get("error_rate_pp");
        if (p95 != null && Math.abs(p95) >= NOTABLE_PCT) {
            notable.add(name + " p95 " + fmtPct(p95) + " vs baseline");
        }
        if (avg != null && Math.abs(avg) >= NOTABLE_PCT) {
            notable.add(name + " avg RT " + fmtPct(avg) + " vs baseline");
        }
        if (errPp != null && Math.abs(errPp) >= NOTABLE_ERR_PP) {
            notable.add(name + " error rate " + fmtPp(errPp) + " vs baseline");
        }
    }

    private static double pct(long prev, long cur) {
        if (prev == 0) return cur == 0 ? 0.0 : 100.0;
        return round1(((double) (cur - prev) / prev) * 100.0);
    }

    private static long asLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) { try { return Long.parseLong(s); } catch (Exception e) { return 0; } }
        return 0;
    }

    private static double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
        return 0d;
    }

    private static double round1(double d) {
        return Math.round(d * 10.0) / 10.0;
    }

    private static String fmtPct(double d) {
        return (d >= 0 ? "+" : "") + d + "%";
    }

    private static String fmtPp(double d) {
        return (d >= 0 ? "+" : "") + d + "pp";
    }
}
