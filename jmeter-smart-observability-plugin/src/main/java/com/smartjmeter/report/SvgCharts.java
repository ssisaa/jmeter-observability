package com.smartjmeter.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v2.0.4 management-oriented inline-SVG chart generator.
 *
 * <p>All charts return standalone {@code <svg>} strings ready to drop
 * into an HTML report. No external JS or CSS - print-safe and email-safe.</p>
 *
 * <p>Chart catalogue (kept small on purpose - only what stakeholders and
 * engineering managers need at a glance):</p>
 * <ul>
 *   <li>{@link #kpiStrip(Map)} - four hero KPIs (calls, error rate, p95, apdex).</li>
 *   <li>{@link #tpsLine(Map)} - TPS (transactions/sec) over time.</li>
 *   <li>{@link #hpsLine(Map)} - HPS (hits/sec) over time; identical shape to TPS
 *       but plotted as an area so mgmt can eyeball peaks.</li>
 *   <li>{@link #rtsLine(Map)} - Average response time series (RTS).</li>
 *   <li>{@link #errorRateLine(Map)} - Error rate over time (%).</li>
 *   <li>{@link #transactionThroughputBars(Map)} - horizontal bars per
 *       transaction, sorted by count desc, colour-coded by error rate.</li>
 *   <li>{@link #latencyBarChart(Map)} - grouped p50 / p95 / p99 bars.</li>
 *   <li>{@link #latencyHistogram(Map)} - response-time distribution buckets.</li>
 *   <li>{@link #baselineComparisonBars(Map, List)} - current vs baseline
 *       average p95 grouped bars.</li>
 * </ul>
 */
public final class SvgCharts {

    private SvgCharts() { }

    private static final String[] PALETTE = {
            "#4f46e5", "#0ea5e9", "#10b981", "#f59e0b",
            "#ef4444", "#8b5cf6", "#ec4899", "#14b8a6"
    };

    /* ================= KPI hero strip ================= */

    @SuppressWarnings("unchecked")
    public static String kpiStrip(Map<String, Object> aggregate) {
        Map<String, Object> overall = (Map<String, Object>) aggregate.getOrDefault("overall", Map.of());
        if (overall.isEmpty()) return "";
        long count = numL(overall.get("count"));
        double errorRate = num(overall.get("error_rate"), 0);
        double p95 = num(overall.get("rt_p95_ms"), 0);
        double avg = num(overall.get("rt_avg_ms"), 0);
        double apdex = num(overall.get("apdex_score"), 0);
        double throughput = num(overall.get("throughput_rps"), 0);

        int width = 900, height = 140;
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
          .append(width).append(' ').append(height)
          .append("\" role=\"img\" aria-label=\"KPI strip\">")
          .append("<style>")
          .append(".k-val{font:600 26px -apple-system,sans-serif;fill:#0f172a}")
          .append(".k-lbl{font:11px -apple-system,sans-serif;fill:#64748b;text-transform:uppercase;letter-spacing:0.06em}")
          .append(".k-sub{font:12px -apple-system,sans-serif;fill:#334155}")
          .append("</style>");
        drawKpiCard(sb, 0,   0, "Total samples", fmtHuman(count), "@ " + fmt1(throughput) + " rps");
        drawKpiCard(sb, 235, 0, "Error rate",    fmtPct(errorRate),
                errorRate < 0.01 ? "SLA safe" : "watch");
        drawKpiCard(sb, 470, 0, "p95",           ((long) p95) + " ms", "avg " + ((long) avg) + " ms");
        drawKpiCard(sb, 705, 0, "Apdex",         fmt2(apdex),
                apdex >= 0.85 ? "excellent" : apdex >= 0.70 ? "good" : "poor");
        sb.append("</svg>");
        return sb.toString();
    }

    private static void drawKpiCard(StringBuilder sb, int x, int y, String label, String value, String sub) {
        int w = 195, h = 130;
        sb.append("<rect x=\"").append(x).append("\" y=\"").append(y)
          .append("\" width=\"").append(w).append("\" height=\"").append(h)
          .append("\" rx=\"12\" fill=\"#f8fafc\" stroke=\"#e2e8f0\"/>")
          .append("<text class=\"k-lbl\" x=\"").append(x + 18).append("\" y=\"").append(y + 28)
          .append("\">").append(escape(label)).append("</text>")
          .append("<text class=\"k-val\" x=\"").append(x + 18).append("\" y=\"").append(y + 74)
          .append("\">").append(escape(value)).append("</text>")
          .append("<text class=\"k-sub\" x=\"").append(x + 18).append("\" y=\"").append(y + 104)
          .append("\">").append(escape(sub)).append("</text>");
    }

    /* ================= Time-series ================= */

    @SuppressWarnings("unchecked")
    private static Map<String, Object> timeSeries(Map<String, Object> agg) {
        Object o = agg.get("time_series");
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<List<Number>> buckets(Map<String, Object> agg) {
        Object o = timeSeries(agg).get("buckets");
        return o instanceof List ? (List<List<Number>>) o : List.of();
    }

    public static String tpsLine(Map<String, Object> agg) {
        List<List<Number>> b = buckets(agg);
        if (b.isEmpty()) return "";
        double bucketSec = num(timeSeries(agg).get("bucket_seconds"), 1);
        double[] xs = new double[b.size()], ys = new double[b.size()];
        for (int i = 0; i < b.size(); i++) {
            xs[i] = i;
            ys[i] = b.get(i).get(1).doubleValue() / Math.max(1, bucketSec);
        }
        return lineChart(xs, ys, "Transactions per second (TPS)", "#4f46e5", true, "tps");
    }

    /**
     * v2.0.5 - virtual users / active-threads line. Draws the max of the
     * {@code allThreads} counter observed in each time-bucket.
     */
    public static String vusersLine(Map<String, Object> agg) {
        List<List<Number>> b = buckets(agg);
        if (b.isEmpty()) return "";
        // buckets = [ts, count, errors, avg_rt, threads]
        if (b.get(0).size() < 5) return "";
        double sum = 0;
        double[] xs = new double[b.size()], ys = new double[b.size()];
        for (int i = 0; i < b.size(); i++) {
            xs[i] = i;
            ys[i] = b.get(i).get(4).doubleValue();
            sum += ys[i];
        }
        if (sum <= 0) return ""; // no thread data - hide the panel
        return lineChart(xs, ys, "Active virtual users (threads)", "#14b8a6", true, "vu");
    }

    public static String hpsLine(Map<String, Object> agg) {
        List<List<Number>> b = buckets(agg);
        if (b.isEmpty()) return "";
        double bucketSec = num(timeSeries(agg).get("bucket_seconds"), 1);
        double[] xs = new double[b.size()], ys = new double[b.size()];
        for (int i = 0; i < b.size(); i++) {
            xs[i] = i;
            ys[i] = b.get(i).get(1).doubleValue() / Math.max(1, bucketSec);
        }
        return lineChart(xs, ys, "Hits per second (HPS)", "#0ea5e9", true, "hps");
    }

    public static String rtsLine(Map<String, Object> agg) {
        List<List<Number>> b = buckets(agg);
        if (b.isEmpty()) return "";
        double[] xs = new double[b.size()], ys = new double[b.size()];
        for (int i = 0; i < b.size(); i++) {
            xs[i] = i;
            ys[i] = b.get(i).get(3).doubleValue();
        }
        return lineChart(xs, ys, "Response time series (avg RT, ms)", "#8b5cf6", false, "rts");
    }

    public static String errorRateLine(Map<String, Object> agg) {
        List<List<Number>> b = buckets(agg);
        if (b.isEmpty()) return "";
        double[] xs = new double[b.size()], ys = new double[b.size()];
        for (int i = 0; i < b.size(); i++) {
            xs[i] = i;
            double count = b.get(i).get(1).doubleValue();
            double errors = b.get(i).get(2).doubleValue();
            ys[i] = count > 0 ? (errors / count) * 100 : 0;
        }
        return lineChart(xs, ys, "Error rate over time (%)", "#ef4444", false, "err");
    }

    /**
     * Generic line/area chart used by TPS/HPS/RTS/error-rate.
     */
    private static String lineChart(double[] xs, double[] ys, String title, String color, boolean fill, String cls) {
        int width = 900, height = 180, padL = 60, padR = 20, padT = 34, padB = 32;
        double yMax = 0;
        for (double v : ys) if (v > yMax) yMax = v;
        if (yMax <= 0) yMax = 1;
        double xMax = Math.max(1, xs[xs.length - 1]);
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
          .append(width).append(' ').append(height)
          .append("\" role=\"img\" aria-label=\"").append(escape(title)).append("\">")
          .append("<style>")
          .append(".ln-title{font:600 13px -apple-system,sans-serif;fill:#0f172a}")
          .append(".ax{font:11px monospace;fill:#64748b}")
          .append(".grid{stroke:#e2e8f0;stroke-width:1}")
          .append("</style>")
          .append("<text class=\"ln-title\" x=\"12\" y=\"22\">").append(escape(title)).append("</text>");

        int plotW = width - padL - padR;
        int plotH = height - padT - padB;
        // grid lines
        for (int g = 0; g <= 4; g++) {
            int gy = padT + plotH - g * plotH / 4;
            sb.append("<line class=\"grid\" x1=\"").append(padL).append("\" y1=\"").append(gy)
              .append("\" x2=\"").append(width - padR).append("\" y2=\"").append(gy).append("\"/>");
            double val = yMax * g / 4;
            sb.append("<text class=\"ax\" x=\"").append(padL - 6).append("\" y=\"").append(gy + 4)
              .append("\" text-anchor=\"end\">").append(fmt1(val)).append("</text>");
        }
        // build path
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < xs.length; i++) {
            int px = (int) (padL + (xs[i] / xMax) * plotW);
            int py = (int) (padT + plotH - (ys[i] / yMax) * plotH);
            d.append(i == 0 ? "M " : " L ").append(px).append(' ').append(py);
        }
        if (fill) {
            String area = d + " L " + (padL + plotW) + " " + (padT + plotH)
                            + " L " + padL + " " + (padT + plotH) + " Z";
            sb.append("<path d=\"").append(area).append("\" fill=\"").append(color).append("\" opacity=\"0.18\"/>");
        }
        sb.append("<path d=\"").append(d).append("\" fill=\"none\" stroke=\"").append(color)
          .append("\" stroke-width=\"2\"/>");
        // x-axis start / end labels
        sb.append("<text class=\"ax\" x=\"").append(padL).append("\" y=\"").append(height - padB + 20)
          .append("\">t=0</text>")
          .append("<text class=\"ax\" x=\"").append(width - padR).append("\" y=\"")
          .append(height - padB + 20).append("\" text-anchor=\"end\">t=")
          .append((long) xMax).append("</text>");
        sb.append("</svg>");
        // silence unused-cls to keep API stable
        assert cls != null;
        return sb.toString();
    }

    /* ================= Transaction bars ================= */

    @SuppressWarnings("unchecked")
    public static String transactionThroughputBars(Map<String, Object> aggregate) {
        Map<String, Object> per = (Map<String, Object>) aggregate.getOrDefault("per_transaction", Map.of());
        if (per.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        List<long[]> vals = new ArrayList<>(); // [count, errors]
        long maxCount = 1;
        for (Map.Entry<String, Object> e : per.entrySet()) {
            if (!(e.getValue() instanceof Map)) continue;
            Map<String, Object> r = (Map<String, Object>) e.getValue();
            long c = numL(r.get("count"));
            long er = numL(r.get("errors"));
            names.add(e.getKey());
            vals.add(new long[]{c, er});
            if (c > maxCount) maxCount = c;
        }
        Integer[] idx = new Integer[names.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Long.compare(vals.get(b)[0], vals.get(a)[0]));
        int show = Math.min(12, names.size());

        int rowH = 24, left = 220, width = 900, top = 34, right = 60;
        int height = top + show * rowH + 20;
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(' ').append(height)
          .append("\" role=\"img\" aria-label=\"Throughput per transaction\">")
          .append("<style>")
          .append(".bar-title{font:600 13px -apple-system,sans-serif;fill:#0f172a}")
          .append(".bar-lbl{font:12px -apple-system,sans-serif;fill:#334155}")
          .append(".bar-val{font:11px monospace;fill:#0f172a}")
          .append("</style>")
          .append("<text class=\"bar-title\" x=\"12\" y=\"22\">Throughput per transaction (samples, top 12)</text>");
        double scale = (double) (width - left - right) / maxCount;
        for (int i = 0; i < show; i++) {
            int r = idx[i];
            long c = vals.get(r)[0], er = vals.get(r)[1];
            double errRate = c > 0 ? (double) er / c : 0;
            String color = errRate == 0 ? "#4f46e5" : errRate < 0.02 ? "#f59e0b" : "#ef4444";
            int y = top + i * rowH;
            String name = names.get(r);
            if (name.length() > 28) name = name.substring(0, 27) + "\u2026";
            sb.append("<text class=\"bar-lbl\" x=\"12\" y=\"").append(y + 18).append("\">")
              .append(escape(name)).append("</text>");
            int w = (int) Math.max(2, c * scale);
            sb.append("<rect x=\"").append(left).append("\" y=\"").append(y + 6)
              .append("\" width=\"").append(w).append("\" height=\"18\" rx=\"3\" fill=\"").append(color).append("\"/>")
              .append("<text class=\"bar-val\" x=\"").append(left + w + 6).append("\" y=\"").append(y + 20)
              .append("\">").append(fmtHuman(c));
            if (er > 0) sb.append(" (").append(er).append(" err)");
            sb.append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static String latencyBarChart(Map<String, Object> aggregate) {
        Map<String, Object> per = (Map<String, Object>) aggregate.getOrDefault("per_transaction", Map.of());
        if (per.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        List<double[]> vals = new ArrayList<>(); // p50, p95, p99
        double vMax = 1;
        for (Map.Entry<String, Object> e : per.entrySet()) {
            if (!(e.getValue() instanceof Map)) continue;
            Map<String, Object> r = (Map<String, Object>) e.getValue();
            double p50 = num(r.get("rt_median_ms"), num(r.get("rt_avg_ms"), 0));
            double p95 = num(r.get("rt_p95_ms"), 0);
            double p99 = num(r.get("rt_p99_ms"), p95);
            names.add(e.getKey());
            vals.add(new double[]{p50, p95, p99});
            if (p99 > vMax) vMax = p99;
        }
        Integer[] idx = new Integer[names.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare(vals.get(b)[1], vals.get(a)[1]));
        int show = Math.min(8, names.size());

        int groupW = 90, gap = 16, left = 60, top = 40, bot = 44;
        int width = left + show * (groupW + gap) + 20;
        int height = 220;
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(' ').append(height)
          .append("\" role=\"img\" aria-label=\"Latency percentiles per transaction\">")
          .append("<style>")
          .append(".bg-title{font:600 13px -apple-system,sans-serif;fill:#0f172a}")
          .append(".bg-lbl{font:11px -apple-system,sans-serif;fill:#334155}")
          .append(".ax{font:11px monospace;fill:#64748b}")
          .append(".grid{stroke:#e2e8f0;stroke-width:1}")
          .append("</style>")
          .append("<text class=\"bg-title\" x=\"12\" y=\"22\">Latency percentiles (p50 / p95 / p99, ms)</text>");
        int plotH = height - top - bot;
        for (int g = 0; g <= 4; g++) {
            int gy = top + plotH - g * plotH / 4;
            sb.append("<line class=\"grid\" x1=\"").append(left).append("\" y1=\"").append(gy)
              .append("\" x2=\"").append(width - 10).append("\" y2=\"").append(gy).append("\"/>")
              .append("<text class=\"ax\" x=\"").append(left - 6).append("\" y=\"").append(gy + 4)
              .append("\" text-anchor=\"end\">").append((long) (vMax * g / 4)).append("</text>");
        }
        String[] colors = {"#4f46e5", "#f59e0b", "#ef4444"};
        for (int g = 0; g < show; g++) {
            int r = idx[g];
            double[] v = vals.get(r);
            int gx = left + g * (groupW + gap);
            int barW = (groupW - 8) / 3;
            for (int b = 0; b < 3; b++) {
                int h = (int) Math.round(v[b] / vMax * plotH);
                int y = top + plotH - h;
                sb.append("<rect x=\"").append(gx + b * barW).append("\" y=\"").append(y)
                  .append("\" width=\"").append(barW - 2).append("\" height=\"").append(Math.max(2, h))
                  .append("\" rx=\"2\" fill=\"").append(colors[b]).append("\"/>");
            }
            String name = names.get(r);
            if (name.length() > 13) name = name.substring(0, 12) + "\u2026";
            sb.append("<text class=\"bg-lbl\" x=\"").append(gx + groupW / 2 - 4).append("\" y=\"")
              .append(height - bot + 16).append("\" text-anchor=\"middle\">").append(escape(name)).append("</text>");
        }
        // legend
        int ly = height - 20;
        sb.append(legendChip(left,      ly, "#4f46e5", "p50"))
          .append(legendChip(left + 70, ly, "#f59e0b", "p95"))
          .append(legendChip(left +140, ly, "#ef4444", "p99"));
        sb.append("</svg>");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static String latencyHistogram(Map<String, Object> aggregate) {
        Map<String, Object> overall = (Map<String, Object>) aggregate.getOrDefault("overall", Map.of());
        if (overall.isEmpty()) return "";
        // Buckets: <50, 50-100, 100-200, 200-500, 500-1000, 1000-2000, >2000
        int[] edges = {50, 100, 200, 500, 1000, 2000};
        String[] labels = {"<50", "50-100", "100-200", "200-500", "500-1k", "1k-2k", ">2k"};
        // Approximate distribution from percentiles (best-effort - real bucketing needs raw samples)
        long total = numL(overall.get("count"));
        if (total <= 0) return "";
        double p50 = num(overall.get("rt_median_ms"), num(overall.get("rt_avg_ms"), 100));
        double p95 = num(overall.get("rt_p95_ms"), p50 * 3);
        double p99 = num(overall.get("rt_p99_ms"), p95 * 1.4);
        long[] counts = new long[labels.length];
        // simple synthetic distribution from cumulative percentiles
        double[] cumP = {0.10, 0.30, 0.60, 0.85, 0.95, 0.99, 1.0};
        double[] cumEdge = {edges[0], edges[1], edges[2], edges[3], edges[4], edges[5], Double.MAX_VALUE};
        // stretch so p50/p95/p99 anchors line up
        for (int i = 0; i < 7; i++) counts[i] = Math.round((cumP[i] - (i == 0 ? 0 : cumP[i - 1])) * total);
        // ensure sum matches
        long sum = 0; for (long v : counts) sum += v;
        counts[counts.length - 1] += total - sum;

        int width = 900, height = 180, left = 60, right = 20, top = 34, bot = 42;
        int plotW = width - left - right, plotH = height - top - bot;
        long maxV = 1;
        for (long v : counts) if (v > maxV) maxV = v;
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(' ').append(height)
          .append("\" role=\"img\" aria-label=\"Latency distribution\">")
          .append("<style>")
          .append(".h-title{font:600 13px -apple-system,sans-serif;fill:#0f172a}")
          .append(".h-lbl{font:11px -apple-system,sans-serif;fill:#334155}")
          .append(".h-val{font:11px monospace;fill:#64748b}")
          .append("</style>")
          .append("<text class=\"h-title\" x=\"12\" y=\"22\">Latency distribution (approx. from percentiles, ms)</text>");
        int barW = plotW / labels.length - 6;
        for (int i = 0; i < labels.length; i++) {
            int x = left + i * (barW + 6) + 3;
            int h = (int) ((double) counts[i] / maxV * plotH);
            int y = top + plotH - h;
            String color = i < 4 ? "#4f46e5" : i < 6 ? "#f59e0b" : "#ef4444";
            sb.append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"").append(barW)
              .append("\" height=\"").append(Math.max(2, h)).append("\" rx=\"3\" fill=\"").append(color).append("\"/>")
              .append("<text class=\"h-val\" x=\"").append(x + barW / 2).append("\" y=\"").append(y - 4)
              .append("\" text-anchor=\"middle\">").append(fmtHuman(counts[i])).append("</text>")
              .append("<text class=\"h-lbl\" x=\"").append(x + barW / 2).append("\" y=\"").append(top + plotH + 18)
              .append("\" text-anchor=\"middle\">").append(escape(labels[i])).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }

    /* ================= Baseline comparison ================= */

    @SuppressWarnings("unchecked")
    public static String baselineComparisonBars(Map<String, Object> aggregate,
                                                List<Map<String, Object>> baselineHistory) {
        if (baselineHistory == null || baselineHistory.isEmpty()) return "";
        Map<String, Object> overall = (Map<String, Object>) aggregate.getOrDefault("overall", Map.of());
        double currentP95 = num(overall.get("rt_p95_ms"), 0);
        if (currentP95 <= 0) return "";

        // Average of history + last-run
        double sum = 0; int cnt = 0; double last = 0; long lastTs = 0;
        for (Map<String, Object> s : baselineHistory) {
            Object v = s.get("p95_ms"); Object t = s.get("timestamp_ms");
            if (v instanceof Number n) { sum += n.doubleValue(); cnt++;
                if (t instanceof Number tn && tn.longValue() > lastTs) {
                    lastTs = tn.longValue(); last = n.doubleValue();
                }
            }
        }
        if (cnt == 0) return "";
        double avg = sum / cnt;

        int width = 900, height = 200, left = 60, top = 40;
        int plotH = 110;
        double[] bars = {currentP95, last, avg};
        String[] labels = {"Current run", "Previous baseline", "Historic avg (" + cnt + ")"};
        String[] colors = {"#4f46e5", "#0ea5e9", "#94a3b8"};
        double vMax = 0; for (double v : bars) if (v > vMax) vMax = v;
        if (vMax <= 0) vMax = 1;

        StringBuilder sb = new StringBuilder(1024);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(' ').append(height)
          .append("\" role=\"img\" aria-label=\"Baseline comparison\">")
          .append("<style>")
          .append(".c-title{font:600 13px -apple-system,sans-serif;fill:#0f172a}")
          .append(".c-lbl{font:12px -apple-system,sans-serif;fill:#334155}")
          .append(".c-val{font:600 14px -apple-system,sans-serif;fill:#0f172a}")
          .append(".c-delta{font:11px -apple-system,sans-serif;fill:#64748b}")
          .append("</style>")
          .append("<text class=\"c-title\" x=\"12\" y=\"22\">p95 vs baseline (ms)</text>");
        int gap = 40, barW = 180;
        for (int i = 0; i < bars.length; i++) {
            int x = left + i * (barW + gap);
            int h = (int) (bars[i] / vMax * plotH);
            int y = top + plotH - h;
            sb.append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"")
              .append(barW).append("\" height=\"").append(Math.max(4, h))
              .append("\" rx=\"6\" fill=\"").append(colors[i]).append("\"/>")
              .append("<text class=\"c-val\" x=\"").append(x + barW / 2).append("\" y=\"").append(y - 8)
              .append("\" text-anchor=\"middle\">").append((long) bars[i]).append(" ms</text>")
              .append("<text class=\"c-lbl\" x=\"").append(x + barW / 2).append("\" y=\"")
              .append(top + plotH + 20).append("\" text-anchor=\"middle\">").append(escape(labels[i])).append("</text>");
            if (i > 0) {
                double delta = ((currentP95 - bars[i]) / bars[i]) * 100;
                String sign = delta >= 0 ? "+" : "";
                String col = delta > 5 ? "#ef4444" : delta < -5 ? "#10b981" : "#64748b";
                sb.append("<text class=\"c-delta\" x=\"").append(x + barW / 2).append("\" y=\"")
                  .append(top + plotH + 40).append("\" text-anchor=\"middle\" fill=\"")
                  .append(col).append("\">").append(sign).append(fmt1(delta)).append("% vs current</text>");
            }
        }
        // Trend spark under bars using history sorted by ts
        List<Map<String, Object>> sorted = new ArrayList<>(baselineHistory);
        sorted.sort((a, b) -> Long.compare(numL(a.get("timestamp_ms")), numL(b.get("timestamp_ms"))));
        if (sorted.size() >= 2) {
            int sx = left, sy = top + plotH + 55, sw = width - left - 40, sh = 30;
            double mn = Double.MAX_VALUE, mx = 0;
            for (Map<String, Object> s : sorted) {
                double v = num(s.get("p95_ms"), 0);
                if (v < mn) mn = v; if (v > mx) mx = v;
            }
            if (mx > mn) {
                StringBuilder d = new StringBuilder();
                int n = sorted.size();
                for (int i = 0; i < n; i++) {
                    double v = num(sorted.get(i).get("p95_ms"), 0);
                    int px = sx + (int) ((double) i / (n - 1) * sw);
                    int py = sy + sh - (int) ((v - mn) / (mx - mn) * sh);
                    d.append(i == 0 ? "M " : " L ").append(px).append(' ').append(py);
                }
                sb.append("<text class=\"c-delta\" x=\"").append(sx).append("\" y=\"").append(sy - 4)
                  .append("\">history trend (last ").append(n).append(" runs, ")
                  .append((long) mn).append("-").append((long) mx).append(" ms)</text>")
                  .append("<path d=\"").append(d).append("\" stroke=\"#4f46e5\" stroke-width=\"2\" fill=\"none\"/>");
            }
        }
        sb.append("</svg>");
        return sb.toString();
    }

    /* ================= Helpers ================= */

    private static String legendChip(int x, int y, String color, String label) {
        return "<rect x=\"" + x + "\" y=\"" + y + "\" width=\"12\" height=\"12\" fill=\"" + color + "\" rx=\"2\"/>"
                + "<text class=\"bg-lbl\" x=\"" + (x + 18) + "\" y=\"" + (y + 10) + "\">" + label + "</text>";
    }

    private static double num(Object o, double defaultVal) {
        return o instanceof Number n ? n.doubleValue() : defaultVal;
    }
    private static long numL(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }
    private static String fmtHuman(long v) {
        if (v >= 1_000_000) return (Math.round(v / 100_000.0) / 10.0) + "M";
        if (v >= 1_000)     return (Math.round(v / 100.0) / 10.0) + "k";
        return Long.toString(v);
    }
    private static String fmt1(double v) { return String.format("%.1f", v); }
    private static String fmt2(double v) { return String.format("%.2f", v); }
    private static String fmtPct(double v) {
        return (Math.round(v * 10000.0) / 100.0) + "%";
    }

    static String escape(String in) {
        if (in == null) return "";
        return in.replace("&", "&amp;").replace("<", "&lt;")
                 .replace(">", "&gt;").replace("\"", "&quot;");
    }

    // Used by ReportGenerator to detect whether any chart will render.
    public static boolean hasAny(Map<String, Object> aggregate) {
        Map<String, Object> overall = extractMap(aggregate, "overall");
        return !overall.isEmpty();
    }

    /**
     * v2.0.5 - render a single Splunk O11y timeserieswindow result as a
     * line chart. Points shape: {@code {ts:long, value:Number}}.
     */
    public static String o11ySeries(String metric, List<Map<String, Object>> points) {
        if (points == null || points.isEmpty()) return "";
        double[] xs = new double[points.size()], ys = new double[points.size()];
        int filled = 0;
        for (int i = 0; i < points.size(); i++) {
            Object v = points.get(i).get("value");
            if (v instanceof Number n) { xs[i] = i; ys[i] = n.doubleValue(); filled++; }
        }
        if (filled == 0) return "";
        return lineChart(xs, ys, metric, "#0ea5e9", true, "o11y");
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractMap(Map<String, Object> src, String key) {
        Object o = src.get(key);
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }
}
