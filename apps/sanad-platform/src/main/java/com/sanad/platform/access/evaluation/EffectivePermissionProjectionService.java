package com.sanad.platform.access.evaluation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Rebuilds and reads the tenant-jailed ALLOW-only effective permission projection. */
@Service
public class EffectivePermissionProjectionService {

    private final JdbcTemplate jdbc;
    private final AuthorizationVersionService authorizationVersionService;

    public EffectivePermissionProjectionService(
            JdbcTemplate jdbc, AuthorizationVersionService authorizationVersionService) {
        this.jdbc = jdbc;
        this.authorizationVersionService = authorizationVersionService;
    }

    @Transactional
    public List<EffectivePermissionRow> rebuild(UUID tenantId, UUID userId) {
        requireSubject(tenantId, userId);
        scope(tenantId);
        long version = authorizationVersionService.current(tenantId, userId);
        jdbc.update("DELETE FROM effective_permission_projection WHERE tenant_id = ? AND user_id = ?",
                tenantId, userId);

        jdbc.update("""
                INSERT INTO effective_permission_projection (
                    id, tenant_id, user_id, capability_id, effect, scope_type,
                    scope_reference, source, matched_role_id, authorization_version, computed_at)
                SELECT gen_random_uuid(), ?, ?, q.capability_id, 'ALLOW', q.scope_type,
                       q.scope_reference, 'ROLE', q.role_id, ?, CURRENT_TIMESTAMP
                  FROM (
                    SELECT DISTINCT ON (rc.capability_id,
                            CASE WHEN ura.organization_id IS NULL THEN 'TENANT_ALL' ELSE 'ORGANIZATION' END,
                            ura.organization_id)
                           rc.capability_id,
                           CASE WHEN ura.organization_id IS NULL THEN 'TENANT_ALL' ELSE 'ORGANIZATION' END AS scope_type,
                           ura.organization_id AS scope_reference,
                           ura.role_id
                      FROM user_role_assignments ura
                      JOIN roles r ON r.tenant_id = ura.tenant_id AND r.id = ura.role_id
                      JOIN role_capabilities rc ON rc.tenant_id = ura.tenant_id AND rc.role_id = ura.role_id
                      JOIN access_capabilities ac ON ac.id = rc.capability_id
                     WHERE ura.tenant_id = ? AND ura.user_id = ?
                       AND ura.status = 'ACTIVE' AND r.status = 'ACTIVE' AND ac.status = 'ACTIVE'
                     ORDER BY rc.capability_id,
                              CASE WHEN ura.organization_id IS NULL THEN 'TENANT_ALL' ELSE 'ORGANIZATION' END,
                              ura.organization_id, ura.role_id
                  ) q
                ON CONFLICT (tenant_id, user_id, capability_id, scope_type, scope_reference)
                DO UPDATE SET source = EXCLUDED.source,
                              matched_role_id = EXCLUDED.matched_role_id,
                              authorization_version = EXCLUDED.authorization_version,
                              computed_at = EXCLUDED.computed_at
                """, tenantId, userId, version, tenantId, userId);

        jdbc.update("""
                INSERT INTO effective_permission_projection (
                    id, tenant_id, user_id, capability_id, effect, scope_type,
                    scope_reference, source, matched_role_id, authorization_version, computed_at)
                SELECT gen_random_uuid(), tenant_id, user_id, capability_id, 'ALLOW',
                       COALESCE(scope_type, 'TENANT_ALL'), scope_reference,
                       CASE WHEN reason LIKE '[BREAK_GLASS] %' THEN 'BREAK_GLASS' ELSE 'OVERRIDE' END,
                       NULL, ?, CURRENT_TIMESTAMP
                  FROM user_permission_overrides
                 WHERE tenant_id = ? AND user_id = ? AND effect = 'ALLOW'
                   AND valid_from <= CURRENT_TIMESTAMP
                   AND (valid_until IS NULL OR valid_until > CURRENT_TIMESTAMP)
                ON CONFLICT (tenant_id, user_id, capability_id, scope_type, scope_reference)
                DO UPDATE SET source = EXCLUDED.source,
                              matched_role_id = NULL,
                              authorization_version = EXCLUDED.authorization_version,
                              computed_at = EXCLUDED.computed_at
                """, version, tenantId, userId);
        return list(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public List<EffectivePermissionRow> list(UUID tenantId, UUID userId) {
        requireSubject(tenantId, userId);
        scope(tenantId);
        return jdbc.query("SELECT capability_id, scope_type, scope_reference, source, matched_role_id, "
                        + "authorization_version, computed_at FROM effective_permission_projection "
                        + "WHERE tenant_id = ? AND user_id = ? "
                        + "ORDER BY capability_id, scope_type, scope_reference NULLS FIRST",
                (rs, rowNum) -> new EffectivePermissionRow(
                        rs.getObject("capability_id", UUID.class), rs.getString("scope_type"),
                        rs.getObject("scope_reference", UUID.class), rs.getString("source"),
                        rs.getObject("matched_role_id", UUID.class), rs.getLong("authorization_version"),
                        rs.getTimestamp("computed_at").toInstant()), tenantId, userId);
    }

    private void scope(UUID tenantId) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                String.class, tenantId.toString());
    }

    private static void requireSubject(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            throw new IllegalArgumentException("tenantId and userId are required");
        }
    }

    public record EffectivePermissionRow(
            UUID capabilityId, String scopeType, UUID scopeReference, String source,
            UUID matchedRoleId, long authorizationVersion, Instant computedAt) {}
}
