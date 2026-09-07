package com.sanad.platform.workflow.infrastructure;

import com.sanad.platform.workflow.application.WorkflowAuthorizationReadPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JdbcTemplate read port over the canonical RBAC tables (roles,
 * user_role_assignments, role_capabilities, access_capabilities). Read-only,
 * tenant-scoped, no HR inference.
 */
@Repository
public class JdbcWorkflowAuthorizationReadPort implements WorkflowAuthorizationReadPort {

    private final JdbcTemplate jdbc;

    public JdbcWorkflowAuthorizationReadPort(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> findActiveUserIdsByRole(UUID tenantId, String roleCode) {
        return jdbc.queryForList("""
                SELECT DISTINCT ura.user_id
                FROM user_role_assignments ura
                JOIN roles r ON r.tenant_id = ura.tenant_id AND r.id = ura.role_id
                WHERE ura.tenant_id = ?
                  AND ura.status = 'ACTIVE'
                  AND r.code = ?
                  AND r.status = 'ACTIVE'
                """, UUID.class, tenantId, roleCode);
    }

    @Override
    public List<UUID> findActiveUserIdsByCapability(UUID tenantId, String capabilityCode) {
        return jdbc.queryForList("""
                SELECT DISTINCT ura.user_id
                FROM user_role_assignments ura
                JOIN roles r ON r.tenant_id = ura.tenant_id AND r.id = ura.role_id
                JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
                JOIN access_capabilities ac ON ac.id = rc.capability_id
                WHERE ura.tenant_id = ?
                  AND ura.status = 'ACTIVE'
                  AND r.status = 'ACTIVE'
                  AND ac.status = 'ACTIVE'
                  AND ac.code = ?
                """, UUID.class, tenantId, capabilityCode);
    }
}
