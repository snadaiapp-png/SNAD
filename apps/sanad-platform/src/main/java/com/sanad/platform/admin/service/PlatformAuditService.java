package com.sanad.platform.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.admin.api.AdminDtos.AuditEntryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Append-only audit writer for control-plane operations. */
@Service
public class PlatformAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PlatformAuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void success(
            Authentication authentication,
            UUID targetTenantId,
            String action,
            String resourceType,
            String resourceId,
            String reason,
            Object beforeState,
            Object afterState
    ) {
        PrincipalIds principal = principal(authentication);
        jdbcTemplate.update(
                "INSERT INTO platform_audit_logs "
                        + "(id, actor_tenant_id, actor_user_id, target_tenant_id, action, resource_type, "
                        + "resource_id, reason, before_state, after_state, result, correlation_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', ?, ?)",
                UUID.randomUUID(),
                principal.tenantId(),
                principal.userId(),
                targetTenantId,
                AuditLengthGuard.guardAction(action),
                AuditLengthGuard.guardResourceType(resourceType),
                AuditLengthGuard.guardResourceId(resourceId),
                AuditLengthGuard.guardReason(blankToNull(reason)),
                json(beforeState),
                json(afterState),
                AuditLengthGuard.guardCorrelationId(correlationId()),
                Timestamp.from(Instant.now())
        );
    }

    public List<AuditEntryResponse> recent(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        return jdbcTemplate.query(
                "SELECT id, actor_tenant_id, actor_user_id, target_tenant_id, action, resource_type, "
                        + "resource_id, reason, result, correlation_id, created_at "
                        + "FROM platform_audit_logs ORDER BY created_at DESC LIMIT ?",
                this::mapAudit,
                limit
        );
    }

    private AuditEntryResponse mapAudit(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AuditEntryResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("actor_tenant_id", UUID.class),
                resultSet.getObject("actor_user_id", UUID.class),
                resultSet.getObject("target_tenant_id", UUID.class),
                resultSet.getString("action"),
                resultSet.getString("resource_type"),
                resultSet.getString("resource_id"),
                resultSet.getString("reason"),
                resultSet.getString("result"),
                resultSet.getString("correlation_id"),
                instantFromResultSet(resultSet, "created_at")
        );
    }

    private static Instant instantFromResultSet(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.LocalDateTime ldt) return ldt.atOffset(java.time.ZoneOffset.UTC).toInstant();
        if (value instanceof java.util.Date date) return date.toInstant();
        return null;
    }

    @SuppressWarnings("unchecked")
    private PrincipalIds principal(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof Map<?, ?> rawDetails)) {
            return new PrincipalIds(null, null);
        }
        Map<String, Object> details = (Map<String, Object>) rawDetails;
        return new PrincipalIds(uuid(details.get("tenant_id")), uuid(details.get("user_id")));
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit state", exception);
        }
    }

    private String correlationId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String provided = request.getHeader("X-Correlation-ID");
            if (provided != null && !provided.isBlank() && provided.length() <= 100) {
                return provided.trim();
            }
        }
        return UUID.randomUUID().toString();
    }

    private static UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PrincipalIds(UUID tenantId, UUID userId) {
    }
}

/**
 * Defensive length guard for platform_audit_logs varchar columns.
 * Prevents "value too long for type character varying(N)" errors by
 * truncating strings to safe limits BEFORE the JdbcTemplate INSERT.
 * This is a safety net — not a replacement for @Size validation.
 */
final class AuditLengthGuard {

    static final String TRUNCATION_SUFFIX = "...[TRUNCATED]";
    static final int TRUNCATION_MARGIN = 15;

    private AuditLengthGuard() {}

    static String guard(String value, int limit) {
        if (value == null) return null;
        if (value.length() <= limit) return value;
        int maxContent = limit - TRUNCATION_MARGIN;
        if (maxContent <= 0) return value.substring(0, Math.min(value.length(), limit));
        return value.substring(0, maxContent) + TRUNCATION_SUFFIX;
    }

    static String guardAction(String action) { return guard(action, 150); }
    static String guardResourceType(String resourceType) { return guard(resourceType, 100); }
    static String guardResourceId(String resourceId) { return guard(resourceId, 100); }
    static String guardReason(String reason) { return guard(reason, 500); }
    static String guardResult(String result) { return guard(result, 20); }
    static String guardCorrelationId(String correlationId) { return guard(correlationId, 100); }
    static String guardFailureReason(String failureReason) { return guard(failureReason, 500); }
}
