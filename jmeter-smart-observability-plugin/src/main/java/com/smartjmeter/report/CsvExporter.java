package com.smartjmeter.report;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * Writes a per-transaction CSV suitable for Excel and audit tooling.
 * Columns: transaction, count, errors, error_rate, min, avg, median,
 * p90, p95, p99, p999, max, stddev, throughput_rps, apdex.
 */
public class CsvExporter {

    private static final String HEADER =
            "transaction,count,errors,error_rate,rt_min_ms,rt_avg_ms,rt_median_ms,"
                    + "rt_p90_ms,rt_p95_ms,rt_p99_ms,rt_p999_ms,rt_max_ms,rt_stddev_ms,"
                    + "throughput_rps,apdex_score";

    @SuppressWarnings("unchecked")
    public Path export(Path target, Map<String, Object> aggregate) throws Exception {
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            w.write(HEADER);
            w.newLine();
            writeRow(w, "OVERALL", (Map<String, Object>) aggregate.getOrDefault("overall", Map.of()));
            Map<String, Object> perTxn = (Map<String, Object>) aggregate.getOrDefault("per_transaction", Map.of());
            for (Map.Entry<String, Object> e : perTxn.entrySet()) {
                writeRow(w, e.getKey(), (Map<String, Object>) e.getValue());
            }
        }
        return target;
    }

    private static void writeRow(BufferedWriter w, String name, Map<String, Object> row) throws Exception {
        w.write(esc(name));
        appendNum(w, row, "count");
        appendNum(w, row, "errors");
        appendNum(w, row, "error_rate");
        appendNum(w, row, "rt_min_ms");
        appendNum(w, row, "rt_avg_ms");
        appendNum(w, row, "rt_median_ms");
        appendNum(w, row, "rt_p90_ms");
        appendNum(w, row, "rt_p95_ms");
        appendNum(w, row, "rt_p99_ms");
        appendNum(w, row, "rt_p999_ms");
        appendNum(w, row, "rt_max_ms");
        appendNum(w, row, "rt_stddev_ms");
        appendNum(w, row, "throughput_rps");
        appendNum(w, row, "apdex_score");
        w.newLine();
    }

    private static void appendNum(BufferedWriter w, Map<String, Object> row, String key) throws Exception {
        Object v = row.get(key);
        w.write(",");
        w.write(v == null ? "" : String.valueOf(v));
    }

    private static String esc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
