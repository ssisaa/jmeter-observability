package com.smartjmeter.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inline-SVG chart generator. No external dependencies, no data attributes;
 * every string returned is a standalone {@code <svg>} block ready to drop
 * into an HTML report.
 *
 * <ul>
 *   <li>{@link #waterfall(Map, int)} - horizontal p50/p95/max stacked bars
 *       per transaction, sorted worst-first.</li>
 *   <li>{@link #verdictSankey(Map)} - two-column Sankey mapping the
 *       verdict node to gates_passed / gates_failed.</li>
 *   <li>{@link #dependencyMap(Map)} - force-approx layout of transactions
 *       as circles sized by call count and coloured by error rate.</li>
 * </ul>
 */
public final class SvgCharts {

    private SvgCharts() { }

    /* ---------------- Waterfall ---------------- */

    @SuppressWarnings("unchecked")
    public static String waterfall(Map<String, Object> aggregate, int maxRows) {
        Map<String, Object> per = (Map<String, Object>) aggregate.getOrDefault("per_transaction", Map.of());
        if (per.isEmpty()) return "";

        List<double[]> rows = new ArrayList<>();
        List<String> names = new ArrayList<>();
        double maxV = 1;
        for (Map.Entry<String, Object> e : per.entrySet()) {
            if (!(e.getValue() instanceof Map)) continue;
            Map<String, Object> r = (Map<String, Object>) e.getValue();
            double p50 = num(r.get("rt_median_ms"), num(r.get("rt_avg_ms"), 0));
            double p95 = num(r.get("rt_p95_ms"), 0);
            double max = num(r.get("rt_max_ms"), p95);
            if (max > maxV) maxV = max;
            names.add(e.getKey());
            rows.add(new double[]{p50, p95, max});
        }
        // sort by p95 desc, take top-N
        Integer[] idx = new Integer[names.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(rows.get(b)[1], rows.get(a)[1]));

        int rowsToShow = Math.min(maxRows <= 0 ? 12 : maxRows, names.size());
        int rowH = 26;
        int width = 820, left = 220, right = 40, top = 30;
        int height = top + rowsToShow * rowH + 30;

        StringBuilder sb = new StringBuilder(2048);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
          .append(width).append(' ').append(height).append("\" role=\"img\" aria-label=\"Per-transaction waterfall\">")
          .append("<style>")
          .append(".wf-label{font:12px -apple-system,Segoe UI,Roboto,sans-serif;fill:#334155}")
          .append(".wf-value{font:11px monospace;fill:#0f172a}")
          .append(".wf-title{font:13px -apple-system,sans-serif;fill:#0f172a;font-weight:600}")
          .append("</style>")
          .append("<text class=\"wf-title\" x=\"12\" y=\"18\">Per-transaction latency (p50 / p95 / max, ms)</text>");
        double scale = (width - left - right) / maxV;

        for (int i = 0; i < rowsToShow; i++) {
            int r = idx[i];
            double[] v = rows.get(r);
            int y = top + i * rowH;
            String name = names.get(r);
            if (name.length() > 26) name = name.substring(0, 25) + "\u2026";
            sb.append("<text class=\"wf-label\" x=\"12\" y=\"").append(y + 15).append("\">")
              .append(escape(name)).append("</text>");
            int xp50 = (int) Math.round(v[0] * scale);
            int xp95 = (int) Math.round(v[1] * scale);
            int xmax = (int) Math.round(v[2] * scale);
            // stacked: max (grey) then p95 (amber) then p50 (blue)
            sb.append("<rect x=\"").append(left).append("\" y=\"").append(y + 4)
              .append("\" width=\"").append(Math.max(1, xmax)).append("\" height=\"16\" fill=\"#e2e8f0\"/>");
            sb.append("<rect x=\"").append(left).append("\" y=\"").append(y + 4)
              .append("\" width=\"").append(Math.max(1, xp95)).append("\" height=\"16\" fill=\"#f59e0b\"/>");
            sb.append("<rect x=\"").append(left).append("\" y=\"").append(y + 4)
              .append("\" width=\"").append(Math.max(1, xp50)).append("\" height=\"16\" fill=\"#4f46e5\"/>");
            sb.append("<text class=\"wf-value\" x=\"").append(left + Math.max(4, xmax) + 6)
              .append("\" y=\"").append(y + 16).append("\">")
              .append((long) v[0]).append(" / ").append((long) v[1]).append(" / ").append((long) v[2])
              .append("</text>");
        }
        // legend
        int ly = top + rowsToShow * rowH + 14;
        sb.append("<rect x=\"12\" y=\"").append(ly).append("\" width=\"10\" height=\"10\" fill=\"#4f46e5\"/>")
          .append("<text class=\"wf-label\" x=\"26\" y=\"").append(ly + 9).append("\">p50</text>")
          .append("<rect x=\"70\" y=\"").append(ly).append("\" width=\"10\" height=\"10\" fill=\"#f59e0b\"/>")
          .append("<text class=\"wf-label\" x=\"84\" y=\"").append(ly + 9).append("\">p95</text>")
          .append("<rect x=\"130\" y=\"").append(ly).append("\" width=\"10\" height=\"10\" fill=\"#e2e8f0\"/>")
          .append("<text class=\"wf-label\" x=\"144\" y=\"").append(ly + 9).append("\">max</text>");
        sb.append("</svg>");
        return sb.toString();
    }

    /* ---------------- Sankey (verdict -> gates) ---------------- */

    @SuppressWarnings("unchecked")
    public static String verdictSankey(Map<String, Object> verdict) {
        if (verdict == null || verdict.isEmpty()) return "";
        String level = String.valueOf(verdict.getOrDefault("level", "INSUFFICIENT_DATA"));
        List<String> passed = (List<String>) verdict.getOrDefault("gates_passed", List.of());
        List<String> failed = (List<String>) verdict.getOrDefault("gates_failed", List.of());
        int total = passed.size() + failed.size();
        if (total == 0) return "";

        int width = 820, height = 260, leftBoxX = 40, rightX = 500;
        String verdictColor = switch (level) {
            case "GO" -> "#10b981";
            case "GO_WITH_CONDITIONS" -> "#f59e0b";
            case "NO_GO" -> "#ef4444";
            default -> "#6b7280";
        };
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
          .append(width).append(' ').append(height).append("\" role=\"img\" aria-label=\"Verdict Sankey\">")
          .append("<style>")
          .append(".sk-node{font:12px -apple-system,sans-serif;fill:#fff;font-weight:600}")
          .append(".sk-lbl{font:12px -apple-system,sans-serif;fill:#334155}")
          .append(".sk-title{font:13px -apple-system,sans-serif;fill:#0f172a;font-weight:600}")
          .append("</style>")
          .append("<text class=\"sk-title\" x=\"12\" y=\"18\">Verdict &rarr; gate flow</text>");

        int nodeH = height - 60;
        // Verdict node
        sb.append("<rect x=\"").append(leftBoxX).append("\" y=\"30\" width=\"200\" height=\"")
          .append(nodeH).append("\" rx=\"6\" fill=\"").append(verdictColor).append("\"/>");
        sb.append("<text class=\"sk-node\" x=\"").append(leftBoxX + 100).append("\" y=\"")
          .append(30 + nodeH / 2 + 4).append("\" text-anchor=\"middle\">")
          .append(escape(level.replace('_', ' '))).append("</text>");

        // Right-side gate nodes stacked
        int stepH = Math.max(20, nodeH / total);
        int y = 30;
        int gateW = 260;
        // Draw ribbons + boxes
        int srcTopY = 30;
        for (String g : failed) {
            sb.append(ribbon(leftBoxX + 200, srcTopY + nodeH / 2, rightX, y + stepH / 2, "#ef4444"));
            sb.append("<rect x=\"").append(rightX).append("\" y=\"").append(y).append("\" width=\"")
              .append(gateW).append("\" height=\"").append(stepH - 2).append("\" rx=\"4\" fill=\"#ef4444\"/>")
              .append("<text class=\"sk-node\" x=\"").append(rightX + 12).append("\" y=\"")
              .append(y + stepH / 2 + 4).append("\">&#10007; ").append(escape(g)).append("</text>");
            y += stepH;
        }
        for (String g : passed) {
            sb.append(ribbon(leftBoxX + 200, srcTopY + nodeH / 2, rightX, y + stepH / 2, "#10b981"));
            sb.append("<rect x=\"").append(rightX).append("\" y=\"").append(y).append("\" width=\"")
              .append(gateW).append("\" height=\"").append(stepH - 2).append("\" rx=\"4\" fill=\"#10b981\"/>")
              .append("<text class=\"sk-node\" x=\"").append(rightX + 12).append("\" y=\"")
              .append(y + stepH / 2 + 4).append("\">&#10003; ").append(escape(g)).append("</text>");
            y += stepH;
        }
        sb.append("</svg>");
        return sb.toString();
    }

    private static String ribbon(int x1, int y1, int x2, int y2, String color) {
        int cx = (x1 + x2) / 2;
        return "<path d=\"M " + x1 + " " + y1 + " C " + cx + " " + y1 + ", " + cx + " " + y2 + ", " + x2 + " " + y2
                + "\" stroke=\"" + color + "\" stroke-width=\"3\" fill=\"none\" opacity=\"0.35\"/>";
    }

    /* ---------------- Dependency map ---------------- */

    @SuppressWarnings("unchecked")
    public static String dependencyMap(Map<String, Object> aggregate) {
        Map<String, Object> per = (Map<String, Object>) aggregate.getOrDefault("per_transaction", Map.of());
        if (per.isEmpty()) return "";
        int width = 820, height = 340;
        int cx = width / 2, cy = height / 2 + 10;

        StringBuilder sb = new StringBuilder(2048);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
          .append(width).append(' ').append(height).append("\" role=\"img\" aria-label=\"Transaction dependency map\">")
          .append("<style>")
          .append(".dm-lbl{font:11px -apple-system,sans-serif;fill:#334155}")
          .append(".dm-title{font:13px -apple-system,sans-serif;fill:#0f172a;font-weight:600}")
          .append("</style>")
          .append("<text class=\"dm-title\" x=\"12\" y=\"18\">Transaction dependency map (size = calls, colour = error rate)</text>");

        // Central "test" node
        sb.append("<circle cx=\"").append(cx).append("\" cy=\"").append(cy).append("\" r=\"22\" fill=\"#0f172a\"/>")
          .append("<text class=\"dm-lbl\" x=\"").append(cx).append("\" y=\"").append(cy + 4)
          .append("\" text-anchor=\"middle\" fill=\"#fff\">test</text>");

        List<Map.Entry<String, Object>> entries = new ArrayList<>(per.entrySet());
        long maxCount = 1;
        for (Map.Entry<String, Object> e : entries) {
            if (e.getValue() instanceof Map<?, ?> m) {
                long c = numL(m.get("count"));
                if (c > maxCount) maxCount = c;
            }
        }
        int n = entries.size();
        double radius = Math.min(width, height) * 0.35;
        for (int i = 0; i < n; i++) {
            Map.Entry<String, Object> e = entries.get(i);
            if (!(e.getValue() instanceof Map)) continue;
            Map<String, Object> row = (Map<String, Object>) e.getValue();
            double count = numL(row.get("count"));
            double err = numL(row.get("errors"));
            double errRate = count > 0 ? err / count : 0;
            double radiusThisNode = 8 + 22 * Math.sqrt(count / (double) maxCount);
            double angle = 2 * Math.PI * i / n - Math.PI / 2;
            double x = cx + radius * Math.cos(angle);
            double y = cy + radius * Math.sin(angle);
            String color = errRate == 0 ? "#10b981" : errRate < 0.02 ? "#f59e0b" : "#ef4444";
            sb.append("<line x1=\"").append(cx).append("\" y1=\"").append(cy)
              .append("\" x2=\"").append((int) x).append("\" y2=\"").append((int) y)
              .append("\" stroke=\"#cbd5e1\" stroke-width=\"1\"/>");
            sb.append("<circle cx=\"").append((int) x).append("\" cy=\"").append((int) y)
              .append("\" r=\"").append((int) radiusThisNode).append("\" fill=\"").append(color)
              .append("\" opacity=\"0.85\"/>");
            String label = e.getKey();
            if (label.length() > 16) label = label.substring(0, 15) + "\u2026";
            int labelY = (int) y + (int) radiusThisNode + 12;
            sb.append("<text class=\"dm-lbl\" x=\"").append((int) x).append("\" y=\"").append(labelY)
              .append("\" text-anchor=\"middle\">").append(escape(label)).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }

    /* ---------------- helpers ---------------- */

    private static double num(Object o, double defaultVal) {
        return o instanceof Number n ? n.doubleValue() : defaultVal;
    }

    private static long numL(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    static String escape(String in) {
        if (in == null) return "";
        return in.replace("&", "&amp;")
                 .replace("<", "&lt;")
                 .replace(">", "&gt;")
                 .replace("\"", "&quot;");
    }
}
