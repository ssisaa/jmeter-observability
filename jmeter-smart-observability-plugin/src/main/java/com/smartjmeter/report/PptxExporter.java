package com.smartjmeter.report;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PPTX exporter that turns the JSON report envelope into an executive
 * deck: title, verdict, health scores, top findings, next steps.
 *
 * <p>Deliberately minimal (Apache POI XSLF). Never throws to the caller.</p>
 */
public class PptxExporter {

    private static final Logger LOG = Logger.getLogger(PptxExporter.class.getName());

    public Path export(Map<String, Object> envelope, Path pptxPath) {
        try {
            if (pptxPath.getParent() != null) Files.createDirectories(pptxPath.getParent());
            try (XMLSlideShow ppt = new XMLSlideShow(); OutputStream os = Files.newOutputStream(pptxPath)) {
                addTitleSlide(ppt, envelope);
                addVerdictSlide(ppt, envelope);
                addScoresSlide(ppt, envelope);
                addFindingsSlide(ppt, envelope);
                addNextStepsSlide(ppt, envelope);
                ppt.write(os);
            }
            return pptxPath;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "PPTX export failed - continuing", e);
            return null;
        }
    }

    private static void addTitleSlide(XMLSlideShow ppt, Map<String, Object> env) {
        XSLFSlide s = ppt.createSlide();
        title(s, "Performance Test Verdict", 32, new Color(30, 41, 59));
        body(s, str(env.get("test_name")) + "  ·  " + str(env.get("environment"))
                + "  ·  " + str(env.get("application")), 100, 120);
    }

    @SuppressWarnings("unchecked")
    private static void addVerdictSlide(XMLSlideShow ppt, Map<String, Object> env) {
        XSLFSlide s = ppt.createSlide();
        Map<String, Object> v = (Map<String, Object>) env.getOrDefault("verdict", Map.of());
        title(s, "Verdict: " + str(v.get("level")), 28, colorFor(str(v.get("level"))));
        body(s, "Production confidence: " + v.getOrDefault("production_confidence", 0) + " / 100"
                + "\nRisk score: " + v.getOrDefault("risk_score", 0) + " / 100"
                + "\n\n" + str(v.get("rationale")), 60, 140);
    }

    @SuppressWarnings("unchecked")
    private static void addScoresSlide(XMLSlideShow ppt, Map<String, Object> env) {
        XSLFSlide s = ppt.createSlide();
        Map<String, Object> scores = (Map<String, Object>) env.getOrDefault("health_scores", Map.of());
        title(s, "Health Scores", 28, new Color(30, 41, 59));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : scores.entrySet()) {
            sb.append("• ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }
        body(s, sb.toString(), 60, 140);
    }

    @SuppressWarnings("unchecked")
    private static void addFindingsSlide(XMLSlideShow ppt, Map<String, Object> env) {
        XSLFSlide s = ppt.createSlide();
        title(s, "Top Findings", 28, new Color(30, 41, 59));
        List<Map<String, Object>> findings = (List<Map<String, Object>>) env.getOrDefault("findings", List.of());
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map<String, Object> f : findings) {
            if (shown++ >= 8) break;
            sb.append("• [").append(str(f.get("severity"))).append("] ")
              .append(str(f.get("title"))).append("  (").append(str(f.get("category"))).append(")\n");
        }
        if (sb.length() == 0) sb.append("No findings surfaced by the rule engine.");
        body(s, sb.toString(), 60, 140);
    }

    @SuppressWarnings("unchecked")
    private static void addNextStepsSlide(XMLSlideShow ppt, Map<String, Object> env) {
        XSLFSlide s = ppt.createSlide();
        Map<String, Object> v = (Map<String, Object>) env.getOrDefault("verdict", Map.of());
        title(s, "Rollout Plan", 28, new Color(30, 41, 59));
        StringBuilder sb = new StringBuilder();
        for (Object step : (List<Object>) v.getOrDefault("rollout_plan", List.of())) {
            sb.append("• ").append(str(step)).append("\n");
        }
        sb.append("\nRollback triggers:\n");
        for (Object t : (List<Object>) v.getOrDefault("rollback_triggers", List.of())) {
            sb.append("• ").append(str(t)).append("\n");
        }
        body(s, sb.toString(), 60, 140);
    }

    private static void title(XSLFSlide s, String text, int size, Color color) {
        XSLFTextBox tb = s.createTextBox();
        tb.setAnchor(new Rectangle(40, 30, 880, 80));
        XSLFTextParagraph p = tb.addNewTextParagraph();
        XSLFTextRun r = p.addNewTextRun();
        r.setText(text);
        r.setFontSize((double) size);
        r.setBold(true);
        r.setFontColor(color);
    }

    private static void body(XSLFSlide s, String text, int x, int y) {
        XSLFTextBox tb = s.createTextBox();
        tb.setAnchor(new Rectangle(x, y, 880, 380));
        for (String line : text.split("\n")) {
            XSLFTextParagraph p = tb.addNewTextParagraph();
            XSLFTextRun r = p.addNewTextRun();
            r.setText(line);
            r.setFontSize(16.0);
            r.setFontColor(new Color(30, 41, 59));
        }
    }

    private static Color colorFor(String v) {
        return switch (v == null ? "" : v) {
            case "GO" -> new Color(16, 185, 129);
            case "GO_WITH_CONDITIONS" -> new Color(245, 158, 11);
            case "NO_GO" -> new Color(239, 68, 68);
            default -> new Color(107, 114, 128);
        };
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    // Suppress unused warning for PictureData import (kept for future thumbnail embedding).
    @SuppressWarnings("unused") private static Class<?> keepImport() { return PictureData.class; }
}
