package com.sanad.platform.security.authorization;

import com.sanad.platform.access.AccessConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Mutation invariant for the EXACTLY-four protected role registry. */
@Component
public class ProtectedRoleGuard {
    private static final Set<String> PROTECTED = Set.of(
            "PLATFORM_OWNER", "PLATFORM_ADMIN", "AGENT_SUPER_ADMIN", "TENANT_ADMIN");
    private final JdbcTemplate jdbc;

    public ProtectedRoleGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void assertProtectedMutation(String roleCode, boolean actorHasPlatformManage) {
        if (roleCode == null || !PROTECTED.contains(roleCode)) return;
        if (!actorHasPlatformManage) {
            throw new AccessConflictException(
                    "Protected role mutation requires AUTHORIZATION.PLATFORM.MANAGE: " + roleCode);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertMayArchiveRole(UUID tenantId, UUID roleId) {
        String code = roleCode(tenantId, roleId);
        assertProtectedMutation(code, actorHasPlatformManage(tenantId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertMayDetachCapability(UUID tenantId, UUID roleId) {
        String code = roleCode(tenantId, roleId);
        assertProtectedMutation(code, actorHasPlatformManage(tenantId));
    }

    private String roleCode(UUID tenantId, UUID roleId) {
        if (jdbc == null) return null;
        scope(tenantId);
        return jdbc.query("SELECT code FROM roles WHERE tenant_id = ? AND id = ?",
                rs -> rs.next() ? rs.getString(1) : null, tenantId, roleId);
    }

    private boolean actorHasPlatformManage(UUID tenantId) {
        if (jdbc == null) return false;
        UUID actor = actorUserId();
        if (actor == null) return false;
        scope(tenantId);
        Boolean allowed = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1
                      FROM user_role_assignments ura
                      JOIN roles r ON r.tenant_id = ura.tenant_id AND r.id = ura.role_id
                      JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
                      JOIN access_capabilities ac ON ac.id = rc.capability_id
                     WHERE ura.tenant_id = ? AND ura.user_id = ?
                       AND ura.status = 'ACTIVE' AND r.status = 'ACTIVE'
                       AND ac.status = 'ACTIVE'
                       AND ac.code = 'AUTHORIZATION.PLATFORM.MANAGE'
                )
                """, Boolean.class, tenantId, actor);
        return Boolean.TRUE.equals(allowed);
    }

    private void scope(UUID tenantId) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
    }

    private static UUID actorUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof Map<?, ?> details)) return null;
        Object raw = details.get("user_id");
        if (raw == null) return null;
        try { return UUID.fromString(raw.toString()); } catch (IllegalArgumentException ignored) { return null; }
    }
}
