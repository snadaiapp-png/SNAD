package com.sanad.platform.workflow.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Governed webhook endpoint registry service (GATE R2.10). Endpoints are
 * tenant-owned, HTTPS-only (DB CHECK), carry a bounded event-type list and
 * a secret stored ONLY as SHA-256 hash. Status transitions:
 * ACTIVE -> PAUSED -> REVOKED (terminal). No free-form HTTP execution is
 * exposed to workflow authors — destinations are explicitly registered.
 */
@Service
public class WorkflowWebhookEndpointService {

    private final JdbcTemplate jdbc;

    public WorkflowWebhookEndpointService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CreatedEndpoint(UUID id, String url, String plainSecret) {}

    @Transactional
    public CreatedEndpoint create(UUID tenantId, String name, String url,
                                  String description, List<String> eventTypes,
                                  UUID createdBy) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Endpoint name is required");
        }
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "Webhook URL must be HTTPS (governed channel)");
        }
        if (url.length() > 1000) {
            throw new IllegalArgumentException("Webhook URL too long");
        }
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one subscribed event type is required");
        }
        String eventTypesJson;
        try {
            eventTypesJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(eventTypes.stream()
                            .map(t -> t.trim().toUpperCase())
                            .toList());
        } catch (Exception e) {
            throw new IllegalArgumentException("Event types not serializable", e);
        }
        String secret = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String secretHash = sha256Hex(secret);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_webhook_endpoints (
                    id, tenant_id, name, url, description, secret_hash,
                    event_types, status, created_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'ACTIVE', ?, NOW(), NOW())
                """, id, tenantId, name, url, description, secretHash,
                eventTypesJson, createdBy);
        return new CreatedEndpoint(id, url, secret);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID tenantId) {
        return jdbc.queryForList("""
                SELECT id, name, url, description, event_types, status,
                       created_at, updated_at
                  FROM workflow_webhook_endpoints
                 WHERE tenant_id = ?
                 ORDER BY created_at DESC
                """, tenantId);
    }

    @Transactional
    public boolean setStatus(UUID tenantId, UUID endpointId, String status) {
        if (!List.of("ACTIVE", "PAUSED", "REVOKED").contains(status)) {
            throw new IllegalArgumentException("Invalid endpoint status: " + status);
        }
        if ("REVOKED".equals(status)) {
            // REVOKED is terminal: only allowed from ACTIVE/PAUSED, never back.
            int revoked = jdbc.update("""
                    UPDATE workflow_webhook_endpoints
                       SET status = 'REVOKED', updated_at = NOW()
                     WHERE tenant_id = ? AND id = ? AND status IN ('ACTIVE', 'PAUSED')
                    """, tenantId, endpointId);
            return revoked == 1;
        }
        return jdbc.update("""
                UPDATE workflow_webhook_endpoints
                   SET status = ?, updated_at = NOW()
                 WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE'
                """, status, tenantId, endpointId) == 1;
    }

    /**
     * Resolves the ACTIVE endpoints subscribed to an event type for a
     * tenant — the only targeting path for WEBHOOK intents.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> activeEndpointsFor(UUID tenantId,
                                                        String eventType) {
        String normalized = eventType.trim().toUpperCase();
        return jdbc.queryForList("""
                SELECT id, name, url, secret_hash
                  FROM workflow_webhook_endpoints
                 WHERE tenant_id = ? AND status = 'ACTIVE'
                   AND event_types::text LIKE ?
                """, tenantId, "%" + normalized + "%");
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
