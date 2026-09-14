package com.sanad.platform.workflow.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative Notification Policy model (GATE R2.3 / AD-1). Policies are
 * tenant-scoped, bounded, validated, non-executable configuration: no
 * script, no SQL, no shell, no tenant-supplied HTTP code. Policy resolution
 * at dispatch: the newest enabled policy for (event_type, channel); a
 * missing policy for a non-IN_APP channel fails the intent closed.
 */
@Service
public class WorkflowNotificationPolicyService {

    private final JdbcTemplate jdbc;

    public WorkflowNotificationPolicyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record NotificationPolicy(
            UUID id,
            UUID tenantId,
            String name,
            WorkflowChannel channel,
            String eventType,
            String recipientSource,
            String templateKey,
            String locale,
            String timing,
            Integer offsetSeconds,
            String retryPolicy,
            String dedupStrategy,
            String escalation,
            String fallbackChannel,
            boolean enabled,
            String priority) {
    }

    @Transactional
    public NotificationPolicy create(UUID tenantId, String name, String channel,
                                     String eventType, String recipientSource,
                                     String templateKey, String locale, String timing,
                                     Integer offsetSeconds, String retryPolicy,
                                     String dedupStrategy, String escalation,
                                     String fallbackChannel, String priority) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Policy name is required");
        }
        WorkflowChannel parsedChannel = WorkflowChannel.parse(channel);
        String resolvedLocale = validateLocale(locale);
        String resolvedTiming = timing == null || timing.isBlank()
                ? "IMMEDIATE" : validateEnum("timing", timing,
                List.of("IMMEDIATE", "BEFORE_DUE", "ON_BREACH"));
        String resolvedRetry = retryPolicy == null || retryPolicy.isBlank()
                ? "STANDARD" : validateEnum("retryPolicy", retryPolicy,
                List.of("NONE", "STANDARD", "AGGRESSIVE"));
        String resolvedDedup = dedupStrategy == null || dedupStrategy.isBlank()
                ? "PER_EVENT" : validateEnum("dedupStrategy", dedupStrategy,
                List.of("PER_TIMER", "PER_ACTION", "PER_EVENT", "NONE"));
        String resolvedEscalation = escalation == null || escalation.isBlank()
                ? "NONE" : validateEnum("escalation", escalation,
                List.of("NONE", "SUPERVISOR_ALERT"));
        if (fallbackChannel != null && !fallbackChannel.isBlank()
                && !"IN_APP".equals(fallbackChannel)) {
            throw new IllegalArgumentException(
                    "Fallback channel is restricted to IN_APP in R2");
        }
        String resolvedPriority = priority == null || priority.isBlank()
                ? "NORMAL" : validateEnum("priority", priority,
                List.of("LOW", "NORMAL", "HIGH", "CRITICAL"));
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_notification_policies (
                    id, tenant_id, name, channel, event_type, recipient_source,
                    template_key, locale, timing, offset_seconds, retry_policy,
                    dedup_strategy, escalation, fallback_channel, enabled, priority,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, NOW(), NOW())
                """,
                id, tenantId, name, parsedChannel.name(), validateEventType(eventType),
                validateEnum("recipientSource",
                        recipientSource == null || recipientSource.isBlank()
                                ? "USER" : recipientSource,
                        List.of("USER", "EXTERNAL_PARTICIPANT")),
                templateKey, resolvedLocale, resolvedTiming, offsetSeconds,
                resolvedRetry, resolvedDedup, resolvedEscalation,
                (fallbackChannel == null || fallbackChannel.isBlank())
                        ? null : fallbackChannel,
                resolvedPriority);
        return load(tenantId, id).orElseThrow();
    }

    /** Newest enabled policy for (event_type, channel) — resolution authority. */
    @Transactional(readOnly = true)
    public Optional<NotificationPolicy> resolve(UUID tenantId, String eventType,
                                                WorkflowChannel channel) {
        List<NotificationPolicy> rows = jdbc.query("""
                SELECT id, tenant_id, name, channel, event_type, recipient_source,
                       template_key, locale, timing, offset_seconds, retry_policy,
                       dedup_strategy, escalation, fallback_channel, enabled, priority
                  FROM workflow_notification_policies
                 WHERE tenant_id = ? AND event_type = ? AND channel = ? AND enabled = TRUE
                 ORDER BY created_at DESC, id DESC
                 LIMIT 1
                """, (rs, i) -> new NotificationPolicy(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("name"),
                WorkflowChannel.parse(rs.getString("channel")),
                rs.getString("event_type"),
                rs.getString("recipient_source"),
                rs.getString("template_key"),
                rs.getString("locale"),
                rs.getString("timing"),
                rs.getObject("offset_seconds") == null
                        ? null : rs.getInt("offset_seconds"),
                rs.getString("retry_policy"),
                rs.getString("dedup_strategy"),
                rs.getString("escalation"),
                rs.getString("fallback_channel"),
                rs.getBoolean("enabled"),
                rs.getString("priority")),
                tenantId, validateEventType(eventType), channel.name());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID tenantId) {
        return jdbc.queryForList("""
                SELECT id, name, channel, event_type, recipient_source, template_key,
                       locale, timing, offset_seconds, retry_policy, dedup_strategy,
                       escalation, fallback_channel, enabled, priority, created_at
                  FROM workflow_notification_policies
                 WHERE tenant_id = ?
                 ORDER BY created_at DESC
                """, tenantId);
    }

    @Transactional
    public boolean setEnabled(UUID tenantId, UUID policyId, boolean enabled) {
        return jdbc.update("""
                UPDATE workflow_notification_policies
                   SET enabled = ?, updated_at = NOW()
                 WHERE tenant_id = ? AND id = ?
                """, enabled, tenantId, policyId) == 1;
    }

    @Transactional(readOnly = true)
    public Optional<NotificationPolicy> load(UUID tenantId, UUID policyId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM workflow_notification_policies
                 WHERE tenant_id = ? AND id = ?
                """, tenantId, policyId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        return Optional.of(new NotificationPolicy(
                (UUID) row.get("id"),
                (UUID) row.get("tenant_id"),
                (String) row.get("name"),
                WorkflowChannel.parse((String) row.get("channel")),
                (String) row.get("event_type"),
                (String) row.get("recipient_source"),
                (String) row.get("template_key"),
                (String) row.get("locale"),
                (String) row.get("timing"),
                (Integer) row.get("offset_seconds"),
                (String) row.get("retry_policy"),
                (String) row.get("dedup_strategy"),
                (String) row.get("escalation"),
                (String) row.get("fallback_channel"),
                (Boolean) row.get("enabled"),
                (String) row.get("priority")));
    }

    private static String validateEventType(String eventType) {
        if (eventType == null || eventType.isBlank() || eventType.length() > 60) {
            throw new IllegalArgumentException(
                    "Policy event_type is required (max 60 chars)");
        }
        return eventType.trim().toUpperCase();
    }

    private static String validateLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "ar";
        }
        if (!locale.matches("[a-z]{2}")) {
            throw new IllegalArgumentException("Locale must be a 2-letter code");
        }
        return locale;
    }

    private static String validateEnum(String field, String value, List<String> allowed) {
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid " + field + ": " + value);
        }
        return normalized;
    }
}
