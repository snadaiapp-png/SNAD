package com.sanad.platform.access.evaluation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Rebuilds and reads the tenant-jailed effective permission EXPLANATION
 * projection (Users Module Phase 7).
 *
 * <p>This is a read/explanation model only — {@link CapabilityEvaluationService}
 * remains the sole authorization authority. The projection must never grant
 * authority the canonical evaluator would deny.</p>
 *
 * <p>Phase 7 semantics:</p>
 * <ul>
 *   <li>Every row carries {@code effect} (ALLOW | DENY) and a canonical
 *       machine-readable {@code reason} mirroring the evaluator vocabulary
 *       (ROLE_CAPABILITY_MATCH, EXPLICIT_ALLOW_MATCH, EXPLICIT_DIRECT_DENY).</li>
 *   <li><b>Deny dominance:</b> an active capability-wide DENY override is
 *       projected as one effective DENY row and suppresses every role-derived
 *       or override-derived ALLOW row for that capability, consistently with
 *       the evaluator's Stage-B explicit-direct-deny precedence.</li>
 *   <li>Provenance sources stay ROLE, OVERRIDE, BREAK_GLASS. Relationship
 *       policy is NOT bulk-projected (RELATIONSHIP_PROVENANCE =
 *       NOT_APPLICABLE_OR_NOT_AVAILABLE): relationship resolution is
 *       evaluation-time and scope-dependent, so projecting it would risk
 *       fabricating authority. The canonical evaluator remains the only
 *       relationship authority (fail-closed).</li>
 * </ul>
 */
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

        // 1) Role-derived ALLOW rows with canonical reason; suppressed entirely
        //    for capabilities under an active DENY override (deny dominance).
        jdbc.update(
                "INSERT INTO effective_permission_projection ("
                + "id, tenant_id, user_id, capability_id, effect, scope_type, "
                + "scope_reference, source, matched_role_id, authorization_version, computed_at, reason) "
                + "SELECT gen_random_uuid(), ?, ?, q.capability_id, 'ALLOW', q.scope_type, "
                + "q.scope_reference, 'ROLE', q.role_id, ?, CURRENT_TIMESTAMP, 'ROLE_CAPABILITY_MATCH' "
                + "FROM ( "
                + "SELECT DISTINCT ON (rc.capability_id, "
                + "CASE WHEN ura.organization_id IS NULL THEN 'TENANT_ALL' ELSE 'ORGANIZATION' END, "
                + "ura.organization_id) "
                + "rc.capability_id, "
                + "CASE WHEN ura.organization_id IS NULL THEN 'TENANT_ALL' ELSE 'ORGANIZATION' END AS scope_type, "
                + "ura.organization_id AS scope_reference, "
                + "ura.role_id "
                + "FROM user_role_assignments ura "
                + "JOIN roles r ON r.tenant_id = ura.tenant_id AND r.id = ura.role_id "
                + "JOIN role_capabilities rc ON rc.tenant_id = ura.tenant_id AND rc.role_id = ura.role_id "
                + "JOIN access_capabilities ac ON ac.id = rc.capability_id "
                + "WHERE ura.tenant_id = ? AND ura.user_id = ? "
                + "AND ura.status = 'ACTIVE' AND r.status = 'ACTIVE' AND ac.status = 'ACTIVE' "
                + "ORDER BY rc.capability_id, "
                + "CASE WHEN ura.organization_id IS NULL THEN 'TENANT_ALL' ELSE 'ORGANIZATION' END, "
                + "ura.organization_id, ura.role_id "
                + ") q "
                + "WHERE" + activeDenyExists("q.capability_id")
                + "ON CONFLICT (tenant_id, user_id, capability_id, scope_type, scope_reference) "
                + "DO UPDATE SET effect = EXCLUDED.effect, "
                + "source = EXCLUDED.source, "
                + "matched_role_id = EXCLUDED.matched_role_id, "
                + "authorization_version = EXCLUDED.authorization_version, "
                + "computed_at = EXCLUDED.computed_at, "
                + "reason = EXCLUDED.reason",
                tenantId, userId, version, tenantId, userId, tenantId, userId);

        // 2) Explicit ALLOW override rows (ordinary or break-glass) with the
        //    canonical allow reason; same deny-dominance suppression.
        jdbc.update(
                "INSERT INTO effective_permission_projection ("
                + "id, tenant_id, user_id, capability_id, effect, scope_type, "
                + "scope_reference, source, matched_role_id, authorization_version, computed_at, reason) "
                + "SELECT gen_random_uuid(), o.tenant_id, o.user_id, o.capability_id, 'ALLOW', "
                + "COALESCE(o.scope_type, 'TENANT_ALL'), o.scope_reference, "
                + "CASE WHEN o.reason LIKE '[BREAK_GLASS] %' THEN 'BREAK_GLASS' ELSE 'OVERRIDE' END, "
                + "NULL, ?, CURRENT_TIMESTAMP, 'EXPLICIT_ALLOW_MATCH' "
                + "FROM user_permission_overrides o "
                + "WHERE o.tenant_id = ? AND o.user_id = ? AND o.effect = 'ALLOW' "
                + "AND o.valid_from <= CURRENT_TIMESTAMP "
                + "AND (o.valid_until IS NULL OR o.valid_until > CURRENT_TIMESTAMP) "
                + "AND" + activeDenyExists("o.capability_id")
                + "ON CONFLICT (tenant_id, user_id, capability_id, scope_type, scope_reference) "
                + "DO UPDATE SET effect = EXCLUDED.effect, "
                + "source = EXCLUDED.source, "
                + "matched_role_id = NULL, "
                + "authorization_version = EXCLUDED.authorization_version, "
                + "computed_at = EXCLUDED.computed_at, "
                + "reason = EXCLUDED.reason",
                version, tenantId, userId, tenantId, userId);

        // 3) Active capability-wide DENY overrides become effective DENY rows
        //    with the canonical direct-deny reason (explanation of dominance).
        jdbc.update(
                "INSERT INTO effective_permission_projection ("
                + "id, tenant_id, user_id, capability_id, effect, scope_type, "
                + "scope_reference, source, matched_role_id, authorization_version, computed_at, reason) "
                + "SELECT gen_random_uuid(), tenant_id, user_id, capability_id, 'DENY', "
                + "'TENANT_ALL', NULL, "
                + "CASE WHEN reason LIKE '[BREAK_GLASS] %' THEN 'BREAK_GLASS' ELSE 'OVERRIDE' END, "
                + "NULL, ?, CURRENT_TIMESTAMP, 'EXPLICIT_DIRECT_DENY' "
                + "FROM user_permission_overrides "
                + "WHERE tenant_id = ? AND user_id = ? AND effect = 'DENY' "
                + "AND valid_from <= CURRENT_TIMESTAMP "
                + "AND (valid_until IS NULL OR valid_until > CURRENT_TIMESTAMP) "
                + "ON CONFLICT (tenant_id, user_id, capability_id, scope_type, scope_reference) "
                + "DO UPDATE SET effect = EXCLUDED.effect, "
                + "source = EXCLUDED.source, "
                + "matched_role_id = NULL, "
                + "authorization_version = EXCLUDED.authorization_version, "
                + "computed_at = EXCLUDED.computed_at, "
                + "reason = EXCLUDED.reason",
                version, tenantId, userId);

        return list(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public List<EffectivePermissionRow> list(UUID tenantId, UUID userId) {
        requireSubject(tenantId, userId);
        scope(tenantId);
        return jdbc.query("SELECT capability_id, effect, scope_type, scope_reference, source, reason, "
                        + "matched_role_id, authorization_version, computed_at "
                        + "FROM effective_permission_projection "
                        + "WHERE tenant_id = ? AND user_id = ? "
                        + "ORDER BY capability_id, scope_type, scope_reference NULLS FIRST",
                (rs, rowNum) -> new EffectivePermissionRow(
                        rs.getObject("capability_id", UUID.class), rs.getString("effect"),
                        rs.getString("scope_type"), rs.getObject("scope_reference", UUID.class),
                        rs.getString("source"), rs.getString("reason"),
                        rs.getObject("matched_role_id", UUID.class), rs.getLong("authorization_version"),
                        rs.getTimestamp("computed_at").toInstant()), tenantId, userId);
    }

    /**
     * Active capability-wide DENY predicate (canonical active-window semantics,
     * identical to the evaluator's Stage-B filter). The capability column
     * reference is supplied by the caller's alias.
     */
    private static String activeDenyExists(String capabilityColumn) {
        return " NOT EXISTS (SELECT 1 FROM user_permission_overrides d "
                + "WHERE d.tenant_id = ? AND d.user_id = ? "
                + "AND d.capability_id = " + capabilityColumn + " "
                + "AND d.effect = 'DENY' "
                + "AND d.valid_from <= CURRENT_TIMESTAMP "
                + "AND (d.valid_until IS NULL OR d.valid_until > CURRENT_TIMESTAMP)) ";
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
            UUID capabilityId, String effect, String scopeType, UUID scopeReference, String source,
            String reason, UUID matchedRoleId, long authorizationVersion, Instant computedAt) {}
}
