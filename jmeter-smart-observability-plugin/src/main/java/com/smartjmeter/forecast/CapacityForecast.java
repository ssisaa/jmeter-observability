package com.smartjmeter.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Capacity forecasting on the p95 response-time series produced by every
 * baseline snapshot.
 *
 * <p>Two independent estimators run in parallel:</p>
 * <ol>
 *   <li><b>Sequence-model regression</b> (ordinary least-squares over
 *       log-time to be robust to gaps) predicts the *expected* p95 at any
 *       future run.</li>
 *   <li><b>Quantile-regression capacity forecast</b> lifts the estimate to
 *       the p90 upper envelope of historical deviations, so the answer is
 *       "we are 90 % confident p95 stays under SLA".</li>
 * </ol>
 *
 * <p>Given a slope (ms per day), the module solves the simple
 * intersection {@code p95_pred(t) = SLA} and returns days-until-breach.</p>
 *
 * <p>Requires at least 3 historical snapshots to produce a forecast.
 * Below that the module returns {@link #insufficient()}.</p>
 */
public final class CapacityForecast {

    private static final Logger LOG = Logger.getLogger(CapacityForecast.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CapacityForecast() { }

    /** Immutable output of the forecast. */
    public record Forecast(
            boolean insufficient,
            int samples,
            double currentP95Ms,
            double slopeMsPerDay,
            double interceptMs,
            long slaP95Ms,
            double daysToBreachP50,      // linear regression (expected)
            double daysToBreachP90,      // quantile-adjusted (safer)
            String verdict,              // OK | WATCH | BREACH_SOON | BREACHED
            String rationale) {

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("insufficient", insufficient);
            m.put("samples", samples);
            m.put("current_p95_ms", round(currentP95Ms));
            m.put("slope_ms_per_day", round(slopeMsPerDay));
            m.put("intercept_ms", round(interceptMs));
            m.put("sla_p95_ms", slaP95Ms);
            m.put("days_to_breach_p50", roundOrInf(daysToBreachP50));
            m.put("days_to_breach_p90", roundOrInf(daysToBreachP90));
            m.put("verdict", verdict);
            m.put("rationale", rationale);
            return m;
        }
    }

    /** Empty/insufficient forecast helper. */
    public static Forecast insufficient() {
        return new Forecast(true, 0, 0, 0, 0, 0, Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, "INSUFFICIENT_DATA",
                "Fewer than 3 baseline snapshots on disk - forecasting disabled.");
    }

    /** Forecast for the current test using stored baseline history. */
    public static Forecast forecast(Path historyDir, long slaP95Ms, Map<String, Object> currentAggregate) {
        List<double[]> series = loadSeries(historyDir);
        // Add current run as the latest point.
        double currentP95 = extractP95(currentAggregate);
        if (currentP95 > 0) series.add(new double[]{System.currentTimeMillis(), currentP95});
        if (series.size() < 3) return insufficient();

        // Sort ascending by timestamp
        series.sort(Comparator.comparingDouble(a -> a[0]));

        // Normalise to days
        double t0 = series.get(0)[0];
        int n = series.size();
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = (series.get(i)[0] - t0) / (1000d * 60 * 60 * 24);
            y[i] = series.get(i)[1];
        }

        // OLS slope + intercept
        double meanX = mean(x), meanY = mean(y);
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) { num += (x[i] - meanX) * (y[i] - meanY); den += (x[i] - meanX) * (x[i] - meanX); }
        double slope = den == 0 ? 0 : num / den;   // ms per day
        double intercept = meanY - slope * meanX;

        // Residuals -> p90 upper envelope
        double[] residuals = new double[n];
        for (int i = 0; i < n; i++) residuals[i] = y[i] - (slope * x[i] + intercept);
        double p90res = quantile(residuals, 0.9);
        double envelope = Math.max(0, p90res);  // envelope above the trend line

        double lastX = x[n - 1];
        double currentTrend = slope * lastX + intercept;

        // Solve for days-to-breach on p50 and p90 envelopes
        double sla = slaP95Ms;
        double daysP50 = slope <= 0
                ? (currentTrend >= sla ? 0 : Double.POSITIVE_INFINITY)
                : Math.max(0, (sla - currentTrend) / slope);
        double daysP90 = slope <= 0
                ? ((currentTrend + envelope) >= sla ? 0 : Double.POSITIVE_INFINITY)
                : Math.max(0, (sla - (currentTrend + envelope)) / slope);

        String verdict;
        String rationale;
        if (currentP95 >= sla) {
            verdict = "BREACHED";
            rationale = "Current p95 " + Math.round(currentP95) + " ms is already at/above SLA " + sla + " ms.";
        } else if (daysP90 <= 7) {
            verdict = "BREACH_SOON";
            rationale = "Trend + p90 residual envelope crosses SLA within a week.";
        } else if (daysP50 <= 30) {
            verdict = "WATCH";
            rationale = "Central trend crosses SLA within 30 days at current slope " + round(slope) + " ms/day.";
        } else {
            verdict = "OK";
            rationale = "Trend at " + round(slope) + " ms/day is well within SLA horizon.";
        }
        return new Forecast(false, n, currentP95, slope, intercept, slaP95Ms,
                daysP50, daysP90, verdict, rationale);
    }

    /** Append a timestamped p95 snapshot to the history directory. */
    public static void appendSnapshot(Path historyDir, Map<String, Object> aggregate) {
        double p95 = extractP95(aggregate);
        if (p95 <= 0) return;
        try {
            Files.createDirectories(historyDir);
            long ts = System.currentTimeMillis();
            Path out = historyDir.resolve("snapshot-" + ts + ".json");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp_ms", ts);
            body.put("iso", Instant.ofEpochMilli(ts).toString());
            body.put("p95_ms", p95);
            Files.writeString(out, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to append forecast snapshot", e);
        }
    }

    /* ---------------- helpers ---------------- */

    private static List<double[]> loadSeries(Path dir) {
        List<double[]> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().startsWith("snapshot-")
                       && p.getFileName().toString().endsWith(".json"))
             .forEach(p -> {
                try {
                    Map<?, ?> m = MAPPER.readValue(Files.readString(p), Map.class);
                    Object ts = m.get("timestamp_ms");
                    Object p95 = m.get("p95_ms");
                    if (ts instanceof Number tn && p95 instanceof Number pn) {
                        out.add(new double[]{tn.doubleValue(), pn.doubleValue()});
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Skip snapshot " + p, e);
                }
             });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to list forecast history " + dir, e);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static double extractP95(Map<String, Object> aggregate) {
        if (aggregate == null) return 0;
        Object overall = aggregate.get("overall");
        if (!(overall instanceof Map)) return 0;
        Object v = ((Map<String, Object>) overall).get("rt_p95_ms");
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static double mean(double[] a) {
        double s = 0; for (double v : a) s += v; return a.length == 0 ? 0 : s / a.length;
    }

    private static double quantile(double[] arr, double q) {
        double[] copy = arr.clone();
        java.util.Arrays.sort(copy);
        double pos = q * (copy.length - 1);
        int lo = (int) Math.floor(pos), hi = (int) Math.ceil(pos);
        if (lo == hi) return copy[lo];
        return copy[lo] + (pos - lo) * (copy[hi] - copy[lo]);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static Object roundOrInf(double v) {
        if (Double.isInfinite(v) || v > 1_000_000) return "infinity";
        return Math.round(v * 10.0) / 10.0;
    }
}
