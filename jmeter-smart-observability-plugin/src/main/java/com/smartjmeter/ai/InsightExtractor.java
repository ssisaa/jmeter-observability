package com.smartjmeter.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Best-effort structured extraction of the LLM output.
 *
 * <p>The plugin asks the LLM to return either strict JSON matching the
 * insight schema, or Markdown. If the LLM output starts with
 * {@code ```json} or a bare {@code &#123;}, we parse it; otherwise we
 * treat the whole reply as free-form Markdown and return an
 * "unstructured" wrapper.</p>
 */
public class InsightExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Map<String, Object> extract(String rawLlmOutput) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (rawLlmOutput == null || rawLlmOutput.isBlank()) {
            out.put("structured", false);
            out.put("markdown", "");
            return out;
        }
        String stripped = stripFences(rawLlmOutput.trim());
        if (looksLikeJson(stripped)) {
            try {
                JsonNode root = MAPPER.readTree(stripped);
                if (root.isObject()) {
                    out.put("structured", true);
                    out.put("top_findings", listOf(root.path("top_findings")));
                    out.put("top_risks", listOf(root.path("top_risks")));
                    out.put("business_impact", MAPPER.convertValue(root.path("business_impact"), Map.class));
                    out.put("regressions", listOf(root.path("regressions")));
                    out.put("capacity_estimate", MAPPER.convertValue(root.path("capacity_estimate"), Map.class));
                    out.put("deployment_risk", MAPPER.convertValue(root.path("deployment_risk"), Map.class));
                    out.put("predicted_failures", listOf(root.path("predicted_failures")));
                    out.put("anomalies", listOf(root.path("anomalies")));
                    out.put("recommendations", MAPPER.convertValue(root.path("recommendations"), Map.class));
                    out.put("executive_summary", root.path("executive_summary").asText(""));
                    out.put("markdown", root.path("markdown_report").asText(""));
                    return out;
                }
            } catch (Exception ignore) {
                // fall through to markdown mode
            }
        }
        out.put("structured", false);
        out.put("markdown", rawLlmOutput);
        return out;
    }

    private static boolean looksLikeJson(String s) {
        return s.startsWith("{") || s.startsWith("[");
    }

    private static String stripFences(String s) {
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(nl + 1);
            int end = s.lastIndexOf("```");
            if (end >= 0) s = s.substring(0, end);
        }
        return s.trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(JsonNode node) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (node == null || !node.isArray()) return out;
        Iterator<JsonNode> it = node.elements();
        while (it.hasNext()) {
            JsonNode el = it.next();
            if (el.isObject()) out.add(MAPPER.convertValue(el, Map.class));
            else {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("text", el.asText());
                out.add(m);
            }
        }
        return out;
    }

    /** JSON schema string offered to the LLM as the expected output shape. */
    public static final String OUTPUT_SCHEMA = """
            {
              "executive_summary": "string (2-3 sentences)",
              "top_findings":       [{"title":"...", "impact":"...", "confidence":0.0, "evidence":"..."}],
              "top_risks":          [{"title":"...", "level":"CRITICAL|HIGH|MEDIUM|LOW", "confidence":0.0}],
              "business_impact":    {"lost_conversions_est": 0, "usd_est": 0, "note":""},
              "regressions":        [{"transaction":"...", "delta":"+40%", "metric":"rt_p95"}],
              "capacity_estimate":  {"peak_supported_tps": 0, "cliff_tps": 0, "months_headroom": 0.0},
              "deployment_risk":    {"level":"LOW|MEDIUM|HIGH|CRITICAL", "reasons":[]},
              "predicted_failures": [{"signal":"...", "eta_days":0, "confidence":0.0}],
              "anomalies":          [{"signal":"...", "description":"...", "ts":"..."}],
              "recommendations":    {"immediate":[], "short_term":[], "long_term":[]},
              "markdown_report":    "string - the same content rendered as markdown for humans"
            }
            """;
}
