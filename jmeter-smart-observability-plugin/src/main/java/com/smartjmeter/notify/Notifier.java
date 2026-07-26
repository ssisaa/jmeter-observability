package com.smartjmeter.notify;

import java.util.Map;

/**
 * Sink for the final verdict + a link to the report.
 * Implementations must be safe on missing/invalid config (no-op, log WARN).
 */
public interface Notifier {

    /** Send the notification. Never throws to the caller. */
    void notify(NotificationPayload payload);

    /** True when the notifier has enough config to actually send. */
    boolean isConfigured();

    /** Short identifier for logging: slack | teams | email | jira | servicenow. */
    String name();

    /** Immutable structured payload every notifier consumes. */
    record NotificationPayload(
            String testName,
            String environment,
            String application,
            String verdict,          // GO | GO_WITH_CONDITIONS | NO_GO | INSUFFICIENT_DATA
            double productionConfidence,
            double riskScore,
            String rationale,
            String reportUrl,        // may be file://
            String reportPathOnDisk,
            Map<String, Object> topFindings) { }
}
