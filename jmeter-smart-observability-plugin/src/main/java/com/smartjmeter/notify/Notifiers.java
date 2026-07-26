package com.smartjmeter.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartjmeter.util.HttpClientFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.mail.*;
import jakarta.mail.internet.*;

/**
 * Five compact notifier implementations (Slack, Teams, Email, Jira,
 * ServiceNow). Each speaks the target's canonical webhook / REST endpoint
 * and never throws to the caller.
 */
public final class Notifiers {

    private static final Logger LOG = Logger.getLogger(Notifiers.class.getName());
    private static final ObjectMapper M = new ObjectMapper();

    private Notifiers() { }

    /* -------------------- Slack -------------------- */

    public static final class SlackNotifier implements Notifier {
        private final String webhookUrl;
        private final HttpClient http;
        public SlackNotifier(String webhookUrl, boolean insecureTls) {
            this.webhookUrl = webhookUrl == null ? "" : webhookUrl;
            this.http = HttpClientFactory.create(insecureTls);
        }
        @Override public boolean isConfigured() { return !webhookUrl.isBlank(); }
        @Override public String name() { return "slack"; }
        @Override public void notify(NotificationPayload p) {
            if (!isConfigured()) return;
            try {
                ObjectNode body = M.createObjectNode();
                body.put("text", ":rocket: *" + p.testName() + "* verdict *" + p.verdict()
                        + "* (confidence " + p.productionConfidence() + "/100). " + p.rationale());
                body.put("username", "smart-observability");
                ArrayNode attachments = body.putArray("attachments");
                ObjectNode att = attachments.addObject();
                att.put("color", colorFor(p.verdict()));
                att.put("text", "Report: " + safe(p.reportUrl()));
                att.put("footer", p.environment() + " / " + p.application());
                post(webhookUrl, M.writeValueAsString(body));
            } catch (Exception e) { LOG.log(Level.WARNING, "Slack notify failed", e); }
        }
        private void post(String url, String json) throws Exception {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 300) LOG.log(Level.WARNING, "Slack HTTP {0}: {1}", new Object[]{r.statusCode(), r.body()});
        }
    }

    /* -------------------- Microsoft Teams -------------------- */

    public static final class TeamsNotifier implements Notifier {
        private final String webhookUrl;
        private final HttpClient http;
        public TeamsNotifier(String webhookUrl, boolean insecureTls) {
            this.webhookUrl = webhookUrl == null ? "" : webhookUrl;
            this.http = HttpClientFactory.create(insecureTls);
        }
        @Override public boolean isConfigured() { return !webhookUrl.isBlank(); }
        @Override public String name() { return "teams"; }
        @Override public void notify(NotificationPayload p) {
            if (!isConfigured()) return;
            try {
                ObjectNode body = M.createObjectNode();
                body.put("@type", "MessageCard");
                body.put("@context", "https://schema.org/extensions");
                body.put("themeColor", colorFor(p.verdict()).replace("#", ""));
                body.put("summary", "Perf verdict " + p.verdict());
                body.put("title", "Performance verdict: " + p.verdict() + " (" + p.productionConfidence() + "/100)");
                body.put("text", p.rationale() + "  \n[Open report](" + safe(p.reportUrl()) + ")  \nEnv: "
                        + p.environment() + " · App: " + p.application());
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body))).build();
                HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() >= 300) LOG.log(Level.WARNING, "Teams HTTP {0}: {1}", new Object[]{r.statusCode(), r.body()});
            } catch (Exception e) { LOG.log(Level.WARNING, "Teams notify failed", e); }
        }
    }

    /* -------------------- Email (SMTP) -------------------- */

    public static final class EmailNotifier implements Notifier {
        private final String host, user, pass, from, to;
        private final int port;
        private final boolean tls;
        public EmailNotifier(String host, int port, String user, String pass,
                             String from, String to, boolean tls) {
            this.host = host == null ? "" : host; this.port = port;
            this.user = user; this.pass = pass; this.from = from; this.to = to; this.tls = tls;
        }
        @Override public boolean isConfigured() {
            return host != null && !host.isBlank() && from != null && !from.isBlank() && to != null && !to.isBlank();
        }
        @Override public String name() { return "email"; }
        @Override public void notify(NotificationPayload p) {
            if (!isConfigured()) return;
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", host);
                props.put("mail.smtp.port", String.valueOf(port));
                props.put("mail.smtp.auth", user != null && !user.isBlank() ? "true" : "false");
                if (tls) props.put("mail.smtp.starttls.enable", "true");
                Session session = Session.getInstance(props, new Authenticator() {
                    @Override protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, pass == null ? "" : pass);
                    }
                });
                MimeMessage msg = new MimeMessage(session);
                msg.setFrom(new InternetAddress(from));
                for (String rcpt : to.split(",")) msg.addRecipient(Message.RecipientType.TO, new InternetAddress(rcpt.trim()));
                msg.setSubject("[" + p.verdict() + "] Perf verdict for " + p.testName() + " (" + p.environment() + ")");
                msg.setText(p.rationale() + "\n\nProduction confidence: " + p.productionConfidence()
                        + "/100\nRisk: " + p.riskScore() + "/100\nReport: " + safe(p.reportUrl())
                        + "\nOn disk: " + safe(p.reportPathOnDisk()));
                Transport.send(msg);
            } catch (Exception e) { LOG.log(Level.WARNING, "Email notify failed", e); }
        }
    }

    /* -------------------- Jira (REST v3, create issue) -------------------- */

    public static final class JiraNotifier implements Notifier {
        private final String baseUrl, projectKey, issueType, userEmail, apiToken;
        private final HttpClient http;
        public JiraNotifier(String baseUrl, String projectKey, String issueType,
                            String userEmail, String apiToken, boolean insecureTls) {
            this.baseUrl = strip(baseUrl); this.projectKey = projectKey;
            this.issueType = issueType == null || issueType.isBlank() ? "Task" : issueType;
            this.userEmail = userEmail; this.apiToken = apiToken;
            this.http = HttpClientFactory.create(insecureTls);
        }
        @Override public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isBlank() && projectKey != null && !projectKey.isBlank()
                    && userEmail != null && !userEmail.isBlank() && apiToken != null && !apiToken.isBlank();
        }
        @Override public String name() { return "jira"; }
        @Override public void notify(NotificationPayload p) {
            if (!isConfigured()) return;
            if (!"NO_GO".equals(p.verdict()) && !"GO_WITH_CONDITIONS".equals(p.verdict())) return;
            try {
                ObjectNode fields = M.createObjectNode();
                fields.putObject("project").put("key", projectKey);
                fields.put("summary", "[" + p.verdict() + "] " + p.testName() + " (" + p.environment() + ")");
                fields.putObject("issuetype").put("name", issueType);
                fields.put("description", p.rationale() + "\n\nConfidence " + p.productionConfidence()
                        + "/100\nRisk " + p.riskScore() + "/100\nReport: " + safe(p.reportUrl()));
                ObjectNode body = M.createObjectNode(); body.set("fields", fields);
                String creds = Base64.getEncoder().encodeToString((userEmail + ":" + apiToken).getBytes());
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/rest/api/3/issue"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Basic " + creds)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body))).build();
                HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() >= 300) LOG.log(Level.WARNING, "Jira HTTP {0}: {1}", new Object[]{r.statusCode(), r.body()});
            } catch (Exception e) { LOG.log(Level.WARNING, "Jira notify failed", e); }
        }
    }

    /* -------------------- ServiceNow (Table API - incident) -------------------- */

    public static final class ServiceNowNotifier implements Notifier {
        private final String instanceUrl, user, pass, table;
        private final HttpClient http;
        public ServiceNowNotifier(String instanceUrl, String user, String pass,
                                  String table, boolean insecureTls) {
            this.instanceUrl = strip(instanceUrl); this.user = user; this.pass = pass;
            this.table = table == null || table.isBlank() ? "incident" : table;
            this.http = HttpClientFactory.create(insecureTls);
        }
        @Override public boolean isConfigured() {
            return instanceUrl != null && !instanceUrl.isBlank() && user != null && !user.isBlank()
                    && pass != null && !pass.isBlank();
        }
        @Override public String name() { return "servicenow"; }
        @Override public void notify(NotificationPayload p) {
            if (!isConfigured()) return;
            if (!"NO_GO".equals(p.verdict())) return;
            try {
                ObjectNode body = M.createObjectNode();
                body.put("short_description", "[" + p.verdict() + "] " + p.testName());
                body.put("description", p.rationale() + " · Confidence " + p.productionConfidence()
                        + " · Report: " + safe(p.reportUrl()));
                body.put("impact", "2");
                body.put("urgency", "2");
                body.put("category", "performance");
                String creds = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(instanceUrl + "/api/now/table/" + table))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Basic " + creds)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body))).build();
                HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() >= 300) LOG.log(Level.WARNING, "ServiceNow HTTP {0}: {1}", new Object[]{r.statusCode(), r.body()});
            } catch (Exception e) { LOG.log(Level.WARNING, "ServiceNow notify failed", e); }
        }
    }

    /* -------------------- helpers -------------------- */

    private static String colorFor(String verdict) {
        return switch (verdict == null ? "" : verdict) {
            case "GO" -> "#10b981";
            case "GO_WITH_CONDITIONS" -> "#f59e0b";
            case "NO_GO" -> "#ef4444";
            default -> "#6b7280";
        };
    }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String strip(String s) { if (s == null) return ""; return s.endsWith("/") ? s.substring(0, s.length()-1) : s; }
}
