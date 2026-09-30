package com.sanad.platform.access.service;

import com.sanad.platform.access.AccessConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Serializes and simulates tenant-admin survival before destructive writes. */
@Component
public class LastAdminGuard {
    private final JdbcTemplate jdbc;
    public LastAdminGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void assertTenantSurvives(long remainingActiveAdmins) {
        if (remainingActiveAdmins < 1L) {
            throw new AccessConflictException("Mutation would remove the last active tenant administrator");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertMayRevokeGrant(UUID tenantId, UUID grantId) {
        if (jdbc == null) return;
        scope(tenantId); lockTenantAdminRole(tenantId);
        Boolean targetIsAdmin = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM user_role_assignments ura
                    JOIN roles r ON r.tenant_id=ura.tenant_id AND r.id=ura.role_id
                    WHERE ura.tenant_id=? AND ura.id=? AND ura.status='ACTIVE'
                      AND (r.code='TENANT_ADMIN' OR r.template_key='TENANT_ADMIN' OR """ + recoverAuthority("r") + "))",
                Boolean.class, tenantId, grantId);
        if (!Boolean.TRUE.equals(targetIsAdmin)) return;
        assertTenantSurvives(countAdmins(tenantId, grantId, null, null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertMayDeactivateRole(UUID tenantId, UUID roleId) {
        if (jdbc == null) return;
        scope(tenantId); lockTenantAdminRole(tenantId);
        Boolean adminRole = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM roles r WHERE r.tenant_id=? AND r.id=? AND "
                        + "(r.code='TENANT_ADMIN' OR r.template_key='TENANT_ADMIN' OR "
                        + recoverAuthority("r") + "))",
                Boolean.class, tenantId, roleId);
        if (!Boolean.TRUE.equals(adminRole)) return;
        assertTenantSurvives(countAdmins(tenantId, null, roleId, null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertMayDeactivateUser(UUID tenantId, UUID userId) {
        if (jdbc == null) return;
        scope(tenantId); lockTenantAdminRole(tenantId);
        Boolean adminUser = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM user_role_assignments ura
                    JOIN roles r ON r.tenant_id=ura.tenant_id AND r.id=ura.role_id
                    WHERE ura.tenant_id=? AND ura.user_id=? AND ura.status='ACTIVE' AND r.status='ACTIVE'
                      AND (r.code='TENANT_ADMIN' OR r.template_key='TENANT_ADMIN' OR """ + recoverAuthority("r") + "))",
                Boolean.class, tenantId, userId);
        if (!Boolean.TRUE.equals(adminUser)) return;
        assertTenantSurvives(countAdmins(tenantId, null, null, userId));
    }

    private long countAdmins(UUID tenantId, UUID excludedGrant, UUID excludedRole, UUID excludedUser) {
        String sql = "SELECT COUNT(DISTINCT ura.user_id) "
                + "FROM user_role_assignments ura "
                + "JOIN users u ON u.tenant_id=ura.tenant_id AND u.id=ura.user_id "
                + "JOIN roles r ON r.tenant_id=ura.tenant_id AND r.id=ura.role_id "
                + "WHERE ura.tenant_id=? AND ura.status='ACTIVE' AND r.status='ACTIVE' AND u.status='ACTIVE' "
                + "AND (?::uuid IS NULL OR ura.id<>?::uuid) "
                + "AND (?::uuid IS NULL OR r.id<>?::uuid) "
                + "AND (?::uuid IS NULL OR u.id<>?::uuid) "
                + "AND (r.code='TENANT_ADMIN' OR r.template_key='TENANT_ADMIN' OR "
                + recoverAuthority("r") + ")";
        Long count = jdbc.queryForObject(sql, Long.class, tenantId,
                excludedGrant, excludedGrant, excludedRole, excludedRole, excludedUser, excludedUser);
        return count == null ? 0L : count;
    }

    private static String recoverAuthority(String roleAlias) {
        return "(EXISTS (SELECT 1 FROM role_capabilities rc1 JOIN access_capabilities ac1 ON ac1.id=rc1.capability_id "
                + "WHERE rc1.tenant_id=" + roleAlias + ".tenant_id AND rc1.role_id=" + roleAlias + ".id "
                + "AND ac1.status='ACTIVE' AND ac1.code='TENANT.ACTIVATE') "
                + "AND EXISTS (SELECT 1 FROM role_capabilities rc2 JOIN access_capabilities ac2 ON ac2.id=rc2.capability_id "
                + "WHERE rc2.tenant_id=" + roleAlias + ".tenant_id AND rc2.role_id=" + roleAlias + ".id "
                + "AND ac2.status='ACTIVE' AND ac2.code='AUTHORIZATION.RECOVER'))";
    }

    private void lockTenantAdminRole(UUID tenantId) {
        jdbc.query("SELECT id FROM roles WHERE tenant_id=? AND (code='TENANT_ADMIN' OR template_key='TENANT_ADMIN') FOR UPDATE",
                rs -> { while (rs.next()) rs.getObject(1); return null; }, tenantId);
    }
    private void scope(UUID tenantId) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
    }
}
