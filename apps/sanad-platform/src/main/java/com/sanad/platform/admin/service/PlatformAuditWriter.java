package com.sanad.platform.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Low-level audit writer that writes directly to platform_audit_logs.
 * Used by both PlatformAuditService and CRM AuditPort adapter.
 * No Authentication required — accepts explicit IDs.
 */
@Component
public class PlatformAuditWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PlatformAuditWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void writeSuccess(
            UUID actorTenantId,
            UUID actorUserId,
            UUID targetTenantId,
            String action,
            String resourceType,
            String resourceId,
            String reason,
            Object beforeState,
            Object afterState,
            String correlationId,
            Instant timestamp
    ) {
        jdbcTemplate.update(
                "INSERT INTO platform_audit_logs "
                        + "(id, actor_tenant_id, actor_user_id, target_tenant_id, action, resource_type, "
                        + "resource_id, reason, before_state, after_state, result, correlation_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', ?, ?)",
                UUID.randomUUID(),
                actorTenantId,
                actorUserId,
                targetTenantId,
                AuditLengthGuard.guardAction(action),
                AuditLengthGuard.guardResourceType(resourceType),
                AuditLengthGuard.guardResourceId(resourceId),
                AuditLengthGuard.guardReason(reason),
                json(beforeState),
                json(afterState),
                AuditLengthGuard.guardCorrelationId(correlationId),
                Timestamp.from(timestamp == null ? Instant.now() : timestamp)
        );
    }

    private String json(Object state) {
        if (state == null) return null;
        if (state instanceof JsonNode node) {
            return node.toString();
        }
        if (state instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize audit state", e);
        }
    }
}
