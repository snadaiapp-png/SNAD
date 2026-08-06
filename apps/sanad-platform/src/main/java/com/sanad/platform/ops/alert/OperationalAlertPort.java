package com.sanad.platform.ops.alert;

import java.time.Instant;

/**
 * Port for dispatching operational alerts to external systems.
 * <p>
 * Implementations may deliver alerts via webhook (Slack, PagerDuty, Teams, Opsgenie)
 * or other notification channels. The port is intentionally provider-agnostic.
 * <p>
 * CRM-008 remediation: production-grade alerting for operational events.
 */
public interface OperationalAlertPort {

    /**
     * Dispatch an operational alert to the configured external system.
     *
     * @param alert the alert to dispatch
     * @throws IllegalStateException if delivery fails
     */
    void dispatch(OperationalAlert alert);

    /**
     * Returns true if this alerting provider is enabled and configured.
     */
    boolean isEnabled();

    /**
     * Immutable alert message carrying severity, summary, and structured details.
     */
    record OperationalAlert(
            String severity,
            String category,
            String summary,
            String details,
            String service,
            String environment,
            String tenantId,
            String correlationId,
            Instant occurredAt
    ) {
        public static OperationalAlertBuilder builder() {
            return new OperationalAlertBuilder();
        }
    }

    /**
     * Fluent builder for constructing operational alerts.
     */
    class OperationalAlertBuilder {
        private String severity = "INFO";
        private String category = "GENERAL";
        private String summary = "";
        private String details = "";
        private String service = "sanad-platform";
        private String environment = "production";
        private String tenantId;
        private String correlationId;
        private Instant occurredAt = Instant.now();

        public OperationalAlertBuilder severity(String severity) { this.severity = severity; return this; }
        public OperationalAlertBuilder category(String category) { this.category = category; return this; }
        public OperationalAlertBuilder summary(String summary) { this.summary = summary; return this; }
        public OperationalAlertBuilder details(String details) { this.details = details; return this; }
        public OperationalAlertBuilder service(String service) { this.service = service; return this; }
        public OperationalAlertBuilder environment(String environment) { this.environment = environment; return this; }
        public OperationalAlertBuilder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public OperationalAlertBuilder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public OperationalAlertBuilder occurredAt(Instant occurredAt) { this.occurredAt = occurredAt; return this; }

        public OperationalAlert build() {
            return new OperationalAlert(severity, category, summary, details, service, environment,
                    tenantId, correlationId, occurredAt);
        }
    }
}
