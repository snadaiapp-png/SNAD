package com.sanad.platform.ops.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook-based adapter for dispatching operational alerts.
 * <p>
 * Supports Slack, PagerDuty, Microsoft Teams, Opsgenie, and generic webhook endpoints.
 * The adapter formats the payload based on the configured provider.
 * <p>
 * Activated only when {@code snad.ops.alerting.enabled=true} and
 * {@code snad.ops.alerting.provider} is not "disabled".
 * <p>
 * CRM-008 remediation: production-grade alerting for operational events.
 */
@Component
@ConditionalOnProperty(prefix = "snad.ops.alerting", name = "enabled", havingValue = "true")
public class WebhookOperationalAlertAdapter implements OperationalAlertPort {

    private static final Logger log = LoggerFactory.getLogger(WebhookOperationalAlertAdapter.class);

    private final OperationalAlertProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, Instant> deduplicationCache = new ConcurrentHashMap<>();

    public WebhookOperationalAlertAdapter(OperationalAlertProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void dispatch(OperationalAlert alert) {
        if (!isEnabled()) {
            return;
        }
        if (!meetsMinimumSeverity(alert.severity())) {
            log.debug("Alert below minimum severity: {} < {}", alert.severity(), properties.getMinimumSeverity());
            return;
        }
        if (isDuplicate(alert)) {
            log.debug("Alert deduplicated: {} / {}", alert.category(), alert.summary());
            return;
        }

        String payload = formatPayload(alert);
        URI endpoint = resolveEndpoint();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload));

        String token = properties.getBearerToken();
        if (token != null && !token.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        try {
            HttpResponse<Void> response = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Alert dispatched: category={} severity={} status={}",
                        alert.category(), alert.severity(), response.statusCode());
            } else {
                log.warn("Alert delivery returned {}: category={} severity={}",
                        response.statusCode(), alert.category(), alert.severity());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Alert delivery interrupted: category={}", alert.category(), e);
        } catch (Exception e) {
            log.error("Alert delivery failed: category={} error={}", alert.category(), e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled() && !"disabled".equalsIgnoreCase(properties.getProvider());
    }

    private boolean meetsMinimumSeverity(String alertSeverity) {
        int alertLevel = severityOrdinal(alertSeverity);
        int minLevel = severityOrdinal(properties.getMinimumSeverity());
        return alertLevel >= minLevel;
    }

    private boolean isDuplicate(OperationalAlert alert) {
        String key = alert.category() + ":" + alert.summary();
        Instant lastSent = deduplicationCache.get(key);
        Instant now = Instant.now();
        if (lastSent != null && now.minusSeconds(properties.getDeduplicationWindowSeconds()).isBefore(lastSent)) {
            return true;
        }
        deduplicationCache.put(key, now);
        // Evict old entries periodically
        if (deduplicationCache.size() > 1000) {
            deduplicationCache.entrySet().removeIf(e ->
                    e.getValue().plusSeconds(properties.getDeduplicationWindowSeconds() * 2).isBefore(now));
        }
        return false;
    }

    private URI resolveEndpoint() {
        String url = properties.getWebhookUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Alerting webhook URL is not configured");
        }
        return URI.create(url);
    }

    private String formatPayload(OperationalAlert alert) {
        String provider = properties.getProvider().toLowerCase();
        return switch (provider) {
            case "slack" -> formatSlackPayload(alert);
            case "pagerduty" -> formatPagerDutyPayload(alert);
            case "teams" -> formatTeamsPayload(alert);
            case "opsgenie" -> formatOpsGeniePayload(alert);
            default -> formatGenericPayload(alert);
        };
    }

    private String formatSlackPayload(OperationalAlert alert) {
        String color = switch (alert.severity()) {
            case "CRITICAL" -> "#dc3545";
            case "ERROR" -> "#dc3545";
            case "WARN" -> "#ffc107";
            default -> "#17a2b8";
        };
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", "🚨 " + alert.severity() + " Alert");
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("color", color);
        attachment.put("title", "[" + alert.severity() + "] " + alert.category());
        attachment.put("text", alert.summary());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Service", alert.service());
        fields.put("Environment", alert.environment());
        fields.put("Category", alert.category());
        fields.put("Time", alert.occurredAt().toString());
        if (alert.tenantId() != null) fields.put("Tenant", alert.tenantId());
        if (alert.correlationId() != null) fields.put("Correlation", alert.correlationId());
        attachment.put("fields", fields);
        if (alert.details() != null && !alert.details().isBlank()) {
            attachment.put("footer", alert.details());
        }
        payload.put("attachments", new Object[]{attachment});
        return toJson(payload);
    }

    private String formatPagerDutyPayload(OperationalAlert alert) {
        String severity = switch (alert.severity()) {
            case "CRITICAL" -> "critical";
            case "ERROR" -> "error";
            case "WARN" -> "warning";
            default -> "info";
        };
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_action", "trigger");
        Map<String, Object> routingKey = new LinkedHashMap<>();
        routingKey.put("routing_key", properties.getBearerToken());
        routingKey.put("event_action", "trigger");
        Map<String, Object> payloadInner = new LinkedHashMap<>();
        payloadInner.put("severity", severity);
        payloadInner.put("source", alert.service());
        payloadInner.put("component", alert.category());
        payloadInner.put("group", alert.environment());
        payloadInner.put("summary", alert.summary());
        payloadInner.put("timestamp", alert.occurredAt().toString());
        Map<String, Object> customDetails = new LinkedHashMap<>();
        customDetails.put("category", alert.category());
        customDetails.put("environment", alert.environment());
        if (alert.details() != null) customDetails.put("details", alert.details());
        if (alert.tenantId() != null) customDetails.put("tenant_id", alert.tenantId());
        if (alert.correlationId() != null) customDetails.put("correlation_id", alert.correlationId());
        payloadInner.put("custom_details", customDetails);
        payload.put("payload", payloadInner);
        return toJson(payload);
    }

    private String formatTeamsPayload(OperationalAlert alert) {
        String color = switch (alert.severity()) {
            case "CRITICAL", "ERROR" -> "attention";
            case "WARN" -> "warning";
            default -> "good";
        };
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("@type", "MessageCard");
        payload.put("@context", "http://schema.org/extensions");
        payload.put("themeColor", color.equals("attention") ? "dc3545" : color.equals("warning") ? "ffc107" : "17a2b8");
        payload.put("summary", alert.summary());
        payload.put("text", "**[" + alert.severity() + "] " + alert.category() + "**\n\n" + alert.summary());
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("Service", alert.service());
        facts.put("Environment", alert.environment());
        facts.put("Time", alert.occurredAt().toString());
        if (alert.tenantId() != null) facts.put("Tenant", alert.tenantId());
        payload.put("facts", facts);
        return toJson(payload);
    }

    private String formatOpsGeniePayload(OperationalAlert alert) {
        String priority = switch (alert.severity()) {
            case "CRITICAL" -> "P1";
            case "ERROR" -> "P2";
            case "WARN" -> "P3";
            default -> "P4";
        };
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "[" + alert.severity() + "] " + alert.category() + ": " + alert.summary());
        payload.put("alias", alert.category() + "-" + alert.service());
        payload.put("description", alert.details() != null ? alert.details() : alert.summary());
        payload.put("priority", priority);
        payload.put("source", alert.service());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("environment", alert.environment());
        details.put("category", alert.category());
        if (alert.tenantId() != null) details.put("tenant_id", alert.tenantId());
        if (alert.correlationId() != null) details.put("correlation_id", alert.correlationId());
        payload.put("details", details);
        return toJson(payload);
    }

    private String formatGenericPayload(OperationalAlert alert) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("severity", alert.severity());
        payload.put("category", alert.category());
        payload.put("summary", alert.summary());
        payload.put("details", alert.details());
        payload.put("service", alert.service());
        payload.put("environment", alert.environment());
        payload.put("timestamp", alert.occurredAt().toString());
        if (alert.tenantId() != null) payload.put("tenant_id", alert.tenantId());
        if (alert.correlationId() != null) payload.put("correlation_id", alert.correlationId());
        return toJson(payload);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize alert payload", e);
            return "{\"error\":\"serialization_failed\"}";
        }
    }

    private static int severityOrdinal(String severity) {
        return switch (severity.toUpperCase()) {
            case "TRACE" -> 0;
            case "DEBUG" -> 1;
            case "INFO" -> 2;
            case "WARN" -> 3;
            case "ERROR" -> 4;
            case "CRITICAL" -> 5;
            default -> 2;
        };
    }
}
