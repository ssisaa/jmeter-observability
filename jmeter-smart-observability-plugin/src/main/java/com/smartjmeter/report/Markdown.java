package com.smartjmeter.report;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, safe Markdown &rarr; HTML renderer used by
 * {@link ReportGenerator} to lay out LLM-authored analysis text.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>ATX headings {@code # / ## / ### / ####}</li>
 *   <li>Unordered lists via {@code - } / {@code * }</li>
 *   <li>Ordered lists via {@code 1. }</li>
 *   <li>Bold via {@code **text**}</li>
 *   <li>Inline code via {@code `code`}</li>
 *   <li>Paragraph grouping across blank-line-separated blocks</li>
 * </ul>
 *
 * <p>Everything else is HTML-escaped so LLM output cannot inject
 * script tags into the report.</p>
 */
final class Markdown {

    private Markdown() { }

    static String render(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String[] lines = raw.replace("\r\n", "\n").split("\n", -1);
        StringBuilder out = new StringBuilder(raw.length() * 2);

        String listMode = null;        // null | "ul" | "ol"
        List<String> paragraph = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.strip();

            if (trimmed.isEmpty()) {
                flushParagraph(paragraph, out);
                listMode = closeList(listMode, out);
                continue;
            }

            // Headings
            if (trimmed.startsWith("#")) {
                flushParagraph(paragraph, out);
                listMode = closeList(listMode, out);
                int level = 0;
                while (level < trimmed.length() && level < 6 && trimmed.charAt(level) == '#') level++;
                String text = trimmed.substring(level).strip();
                out.append("<h").append(level).append(">")
                   .append(inline(text))
                   .append("</h").append(level).append(">");
                continue;
            }

            // Unordered list
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                flushParagraph(paragraph, out);
                if (!"ul".equals(listMode)) {
                    listMode = closeList(listMode, out);
                    out.append("<ul>");
                    listMode = "ul";
                }
                out.append("<li>").append(inline(trimmed.substring(2).strip())).append("</li>");
                continue;
            }

            // Ordered list  (matches "1. ", "12. ")
            int dot = trimmed.indexOf('.');
            if (dot > 0 && dot < 4 && isAllDigits(trimmed.substring(0, dot))
                    && trimmed.length() > dot + 1 && trimmed.charAt(dot + 1) == ' ') {
                flushParagraph(paragraph, out);
                if (!"ol".equals(listMode)) {
                    listMode = closeList(listMode, out);
                    out.append("<ol>");
                    listMode = "ol";
                }
                out.append("<li>").append(inline(trimmed.substring(dot + 2).strip())).append("</li>");
                continue;
            }

            // Regular paragraph line
            listMode = closeList(listMode, out);
            paragraph.add(trimmed);
        }
        flushParagraph(paragraph, out);
        closeList(listMode, out);
        return out.toString();
    }

    private static void flushParagraph(List<String> paragraph, StringBuilder out) {
        if (paragraph.isEmpty()) return;
        out.append("<p>").append(inline(String.join(" ", paragraph))).append("</p>");
        paragraph.clear();
    }

    private static String closeList(String mode, StringBuilder out) {
        if ("ul".equals(mode)) out.append("</ul>");
        else if ("ol".equals(mode)) out.append("</ol>");
        return null;
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return !s.isEmpty();
    }

    /**
     * Escape HTML, then re-enable safe inline markup for **bold** and
     * `inline code`.
     */
    static String inline(String s) {
        String escaped = ReportGenerator.escape(s);
        // Inline code first so ** inside `code` is preserved literally.
        StringBuilder sb = new StringBuilder(escaped.length());
        boolean inCode = false;
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == '`') {
                sb.append(inCode ? "</code>" : "<code>");
                inCode = !inCode;
            } else {
                sb.append(c);
            }
        }
        if (inCode) sb.append("</code>");
        String withCode = sb.toString();
        // Bold: **...**
        StringBuilder out = new StringBuilder(withCode.length());
        int i = 0;
        boolean bold = false;
        while (i < withCode.length()) {
            if (i + 1 < withCode.length() && withCode.charAt(i) == '*' && withCode.charAt(i + 1) == '*') {
                out.append(bold ? "</strong>" : "<strong>");
                bold = !bold;
                i += 2;
            } else {
                out.append(withCode.charAt(i));
                i++;
            }
        }
        if (bold) out.append("</strong>");
        return out.toString();
    }
}
