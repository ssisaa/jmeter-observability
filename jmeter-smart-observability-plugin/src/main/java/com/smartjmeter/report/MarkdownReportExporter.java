package com.smartjmeter.report;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * v2.0.6 - Markdown exporter for Confluence / Wiki.
 *
 * <p>Writes {@code Performance_Report.md} that Confluence's
 * "Insert markup -&gt; Markdown" or Confluence Cloud's "Import Markdown"
 * flow can paste into a page. No emojis, no HTML - only headings,
 * tables, bullets and inline code so the output stays clean when copy /
 * pasted.</p>
 */
public final class MarkdownReportExporter {

    private static final Logger LOG = Logger.getLogger(MarkdownReportExporter.class.getName());

    /** Render the report envelope as Markdown and write it to {@code target}. */
    public Path export(Map<String, Object> envelope, Path target) {
        try {
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            Files.writeString(target, render(envelope));
            return target;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Markdown export failed - continuing", e);
            return null;
        }
    }

    /** Return the Markdown body (exposed for tests). */
    @SuppressWarnings("unchecked")
    public static String render(Map<String, Object> env) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("# Performance Test Report - ").append(str(env.get("test_name"))).append('\n');
        sb.append("_Generated: ").append(str(env.getOrDefault("generated_at", Instant.now().toString()))).append("_\n\n");

        // 1. Executive summary
        Map<String, Object> verdict = mapOrEmpty(env.get("verdict"));
        String level = str(verdict.getOrDefault("level", "INSUFFICIENT_DATA"));
        sb.append("## Executive Summary\n\n");
        sb.append("| Field | Value |\n|---|---|\n");
        row(sb, "Verdict", "**" + level.replace('_', ' ') + "**");
        row(sb, "Application",  str(env.get("application")));
        row(sb, "Environment",  str(env.get("environment")));
        row(sb, "Production confidence", verdict.getOrDefault("production_confidence", "-") + " / 100");
        row(sb, "Risk score", verdict.getOrDefault("risk_score", "-") + " / 100");
        row(sb, "Rationale", str(verdict.getOrDefault("rationale", "-")));
        sb.append('\n');

        // 2. Key metrics
        Map<String, Object> aggregate = mapOrEmpty(env.get("aggregate"));
        Map<String, Object> overall = mapOrEmpty(aggregate.get("overall"));
        if (!overall.isEmpty()) {
            sb.append("## Key Metrics\n\n");
            sb.append("| Metric | Value |\n|---|---|\n");
            row(sb, "Total samples", overall.get("count"));
            row(sb, "Errors",        overall.get("errors"));
            row(sb, "Error rate",    pct(overall.get("error_rate")));
            row(sb, "Throughput",    overall.getOrDefault("throughput_rps", "-") + " rps");
            row(sb, "p50",           overall.getOrDefault("rt_median_ms", "-") + " ms");
            row(sb, "p95",           overall.getOrDefault("rt_p95_ms", "-") + " ms");
            row(sb, "p99",           overall.getOrDefault("rt_p99_ms", "-") + " ms");
            row(sb, "Max",           overall.getOrDefault("rt_max_ms", "-") + " ms");
            row(sb, "Peak VUsers",   overall.getOrDefault("peak_threads", "-"));
            row(sb, "Apdex",         overall.getOrDefault("apdex_score", "-"));
            sb.append('\n');
        }

        // 3. Findings / Key issues
        Object f = env.get("findings");
        if (f instanceof List<?> fl && !fl.isEmpty()) {
            sb.append("## Key Issues (top " + Math.min(fl.size(), 10) + ")\n\n");
            sb.append("| # | Severity | Title | Category | Confidence |\n|---|---|---|---|---|\n");
            int i = 0;
            for (Object item : fl) {
                if (i++ >= 10) break;
                if (item instanceof Map<?, ?> row) {
                    Object confVal = row.get("confidence");
                    sb.append("| ").append(i).append(" | ")
                      .append(str(row.get("severity"))).append(" | ")
                      .append(str(row.get("title"))).append(" | ")
                      .append(str(row.get("category"))).append(" | ")
                      .append(confVal == null ? "-" : String.valueOf(confVal)).append(" |\n");
                }
            }
            sb.append('\n');
        }

        // 4. Per-transaction stats
        Map<String, Object> per = mapOrEmpty(aggregate.get("per_transaction"));
        if (!per.isEmpty()) {
            sb.append("## Per-Transaction Statistics\n\n");
            sb.append("| Transaction | Samples | Errors | Err % | p50 | p95 | p99 | Max |\n");
            sb.append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
            for (Map.Entry<String, Object> e : per.entrySet()) {
                Map<String, Object> r = mapOrEmpty(e.getValue());
                long cnt = num(r.get("count"));
                long err = num(r.get("errors"));
                double errRate = cnt == 0 ? 0 : (double) err / cnt * 100;
                sb.append("| ").append(e.getKey()).append(" | ")
                  .append(cnt).append(" | ")
                  .append(err).append(" | ")
                  .append(String.format("%.2f%%", errRate)).append(" | ")
                  .append(r.getOrDefault("rt_median_ms", "-")).append(" | ")
                  .append(r.getOrDefault("rt_p95_ms", "-")).append(" | ")
                  .append(r.getOrDefault("rt_p99_ms", "-")).append(" | ")
                  .append(r.getOrDefault("rt_max_ms", "-")).append(" |\n");
            }
            sb.append('\n');
        }

        // 5. Baseline delta
        Object h = env.get("baseline_diff");
        if (h instanceof Map<?, ?> bd && !bd.isEmpty()) {
            sb.append("## Baseline Comparison\n\n");
            Object notable = ((Map<?, ?>) h).get("notable");
            if (notable instanceof List<?> nl && !nl.isEmpty()) {
                for (Object o : nl) sb.append("- ").append(str(o)).append('\n');
                sb.append('\n');
            }
        }

        // 6. External metric sources
        Object ext = env.get("external_metrics");
        if (ext instanceof Map<?, ?> em && !em.isEmpty()) {
            sb.append("## External Observability Sources\n\n");
            for (Map.Entry<?, ?> e : em.entrySet()) {
                sb.append("### ").append(str(e.getKey()).toUpperCase()).append('\n');
                if (e.getValue() instanceof Map<?, ?> queries) {
                    sb.append("| Query | Points | Min | Avg | Max |\n|---|---:|---:|---:|---:|\n");
                    for (Map.Entry<?, ?> q : queries.entrySet()) {
                        if (q.getValue() instanceof List<?> pts) {
                            long n = pts.size();
                            double min = Double.MAX_VALUE, max = 0, sum = 0;
                            for (Object p : pts) {
                                if (p instanceof Map<?, ?> pm && pm.get("value") instanceof Number val) {
                                    double v = val.doubleValue();
                                    sum += v;
                                    if (v < min) min = v;
                                    if (v > max) max = v;
                                }
                            }
                            sb.append("| ").append(str(q.getKey())).append(" | ").append(n)
                              .append(" | ").append(n == 0 ? "-" : fmt(min))
                              .append(" | ").append(n == 0 ? "-" : fmt(sum / n))
                              .append(" | ").append(n == 0 ? "-" : fmt(max)).append(" |\n");
                        }
                    }
                    sb.append('\n');
                }
            }
        }

        // 7. Root cause + recommendations
        Map<String, Object> insights = mapOrEmpty(env.get("ai_insights"));
        if (!insights.isEmpty()) {
            Object md = insights.get("markdown");
            if (md != null && !str(md).isBlank()) {
                sb.append("## Root Cause Analysis\n\n").append(str(md)).append("\n\n");
            }
            Object rcs = insights.get("root_causes");
            if (rcs instanceof List<?> rcl && !rcl.isEmpty()) {
                sb.append("### Structured root-cause candidates\n\n");
                for (Object o : rcl) sb.append("- ").append(str(o)).append('\n');
                sb.append('\n');
            }
            Object recs = insights.get("recommendations");
            if (recs instanceof List<?> rl && !rl.isEmpty()) {
                sb.append("## Recommendations\n\n");
                for (Object o : rl) sb.append("- ").append(str(o)).append('\n');
                sb.append('\n');
            }
            Object bi = insights.get("business_impact");
            if (bi instanceof Map<?, ?> bim && !bim.isEmpty()) {
                sb.append("## Business Impact\n\n");
                for (Map.Entry<?, ?> e : bim.entrySet()) {
                    sb.append("- **").append(str(e.getKey())).append("**: ").append(str(e.getValue())).append('\n');
                }
                sb.append('\n');
            }
        }

        // 8. Rollout plan / rollback triggers
        Object roll = verdict.get("rollout_plan");
        if (roll instanceof List<?> rl && !rl.isEmpty()) {
            sb.append("## Rollout Plan\n\n");
            int i = 1;
            for (Object o : rl) sb.append(i++).append(". ").append(str(o)).append('\n');
            sb.append('\n');
        }
        Object rbt = verdict.get("rollback_triggers");
        if (rbt instanceof List<?> rl && !rl.isEmpty()) {
            sb.append("## Rollback Triggers\n\n");
            for (Object o : rl) sb.append("- ").append(str(o)).append('\n');
            sb.append('\n');
        }

        sb.append("---\n_Generated by JMeter Smart Observability AI Plugin v2.0.6_\n");
        return sb.toString();
    }

    /* ---------- helpers ---------- */

    private static void row(StringBuilder sb, String k, Object v) {
        sb.append("| ").append(k).append(" | ").append(str(v)).append(" |\n");
    }
    private static String str(Object o) { return o == null ? "-" : String.valueOf(o); }
    private static long num(Object o) { return o instanceof Number n ? n.longValue() : 0; }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOrEmpty(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }
    private static String pct(Object o) {
        if (!(o instanceof Number n)) return "-";
        return String.format("%.3f%%", n.doubleValue() * 100);
    }
    private static String fmt(double v) {
        if (Math.abs(v) < 10) return String.format("%.3f", v);
        if (Math.abs(v) < 1000) return String.format("%.1f", v);
        return String.format("%.0f", v);
    }
}
