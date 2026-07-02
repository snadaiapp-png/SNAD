package com.sanad.platform.config.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Production-compatible Flyway Java migration V15.
 *
 * <p>The production schema history records version 15 as the JDBC migration
 * {@code seed rbac roles and capabilities}. Keeping the Java migration under
 * this exact class name preserves Flyway validation compatibility while
 * supporting both PostgreSQL and H2 PostgreSQL mode.</p>
 */

public class V15__seed_rbac_roles_and_capabilities extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V15__seed_rbac_roles_and_capabilities.class);

    private static final String[][] PREDEFINED_ROLES = {
            {"SUPER_ADMIN", "Super Administrator", "Full administrative access with all capabilities"},
            {"ORG_ADMIN", "Organization Admin", "Manage users, organizations, and memberships within the tenant"},
            {"MANAGER", "Manager", "Read access and membership management within assigned organizations"},
            {"MEMBER", "Member", "Basic read access to resources within the tenant"},
            {"VIEWER", "Viewer", "Read-only access to non-sensitive resources"}
    };

    private static final String[] ORG_ADMIN_CAPABILITIES = {
            "USER.READ", "USER.CREATE", "USER.WRITE",
            "ORGANIZATION.READ", "ORGANIZATION.CREATE", "ORGANIZATION.WRITE",
            "MEMBERSHIP.READ", "MEMBERSHIP.CREATE", "MEMBERSHIP.WRITE",
            "ROLE.READ", "CAPABILITY.READ", "ACCESS.EVALUATE",
            "USER.GRANT_ROLE", "USER.REVOKE_ROLE"
    };

    private static final String[] MANAGER_CAPABILITIES = {
            "USER.READ", "ORGANIZATION.READ",
            "MEMBERSHIP.READ", "MEMBERSHIP.CREATE", "MEMBERSHIP.WRITE",
            "ROLE.READ", "CAPABILITY.READ", "ACCESS.EVALUATE"
    };

    private static final String[] MEMBER_CAPABILITIES = {
            "USER.READ", "ORGANIZATION.READ", "MEMBERSHIP.READ",
            "ROLE.READ", "CAPABILITY.READ", "ACCESS.EVALUATE"
    };

    private static final String[] VIEWER_CAPABILITIES = MEMBER_CAPABILITIES;

    @Override
    public void migrate(Context context) throws Exception {
        List<UUID> tenantIds = new ArrayList<>();
        try (Statement statement = context.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id FROM tenants WHERE status = 'ACTIVE'")) {
            while (resultSet.next()) {
                tenantIds.add(UUID.fromString(resultSet.getString("id")));
            }
        }

        if (tenantIds.isEmpty()) {
            log.info("V15: No active tenants found; skipping role seeding.");
            return;
        }

        createRoles(context, tenantIds);
        assignAllCapabilitiesToRole(context, tenantIds, "SUPER_ADMIN");
        assignAllCapabilitiesToRole(context, tenantIds, "ADMIN");
        assignCapabilitiesToRole(context, tenantIds, "ORG_ADMIN", ORG_ADMIN_CAPABILITIES);
        assignCapabilitiesToRole(context, tenantIds, "MANAGER", MANAGER_CAPABILITIES);
        assignCapabilitiesToRole(context, tenantIds, "MEMBER", MEMBER_CAPABILITIES);
        assignCapabilitiesToRole(context, tenantIds, "VIEWER", VIEWER_CAPABILITIES);
        log.info("V15: RBAC role and capability seeding complete for {} tenant(s).", tenantIds.size());
    }

    private void createRoles(Context context, List<UUID> tenantIds) throws Exception {
        String sql = "INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at) "
                + "SELECT ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP "
                + "WHERE NOT EXISTS (SELECT 1 FROM roles WHERE tenant_id = ? AND code = ?)";

        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (UUID tenantId : tenantIds) {
                for (String[] role : PREDEFINED_ROLES) {
                    setUuid(statement, 1, UUID.randomUUID());
                    setUuid(statement, 2, tenantId);
                    statement.setString(3, role[0]);
                    statement.setString(4, role[1]);
                    statement.setString(5, role[2]);
                    setUuid(statement, 6, tenantId);
                    statement.setString(7, role[0]);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void assignAllCapabilitiesToRole(Context context, List<UUID> tenantIds, String roleCode) throws Exception {
        String sql = "INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) "
                + "SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP "
                + "FROM tenants t "
                + "JOIN roles r ON r.tenant_id = t.id AND r.code = ? "
                + "JOIN access_capabilities ac ON ac.status = 'ACTIVE' "
                + "WHERE t.id = ? AND t.status = 'ACTIVE' "
                + "AND NOT EXISTS (SELECT 1 FROM role_capabilities rc "
                + "WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id)";

        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (UUID tenantId : tenantIds) {
                statement.setString(1, roleCode);
                setUuid(statement, 2, tenantId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void assignCapabilitiesToRole(
            Context context,
            List<UUID> tenantIds,
            String roleCode,
            String[] capabilityCodes
    ) throws Exception {
        String placeholders = String.join(", ", java.util.Collections.nCopies(capabilityCodes.length, "?"));
        String sql = "INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) "
                + "SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP "
                + "FROM tenants t "
                + "JOIN roles r ON r.tenant_id = t.id AND r.code = ? "
                + "JOIN access_capabilities ac ON ac.code IN (" + placeholders + ") AND ac.status = 'ACTIVE' "
                + "WHERE t.id = ? AND t.status = 'ACTIVE' "
                + "AND NOT EXISTS (SELECT 1 FROM role_capabilities rc "
                + "WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id)";

        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (UUID tenantId : tenantIds) {
                int index = 1;
                statement.setString(index++, roleCode);
                for (String capabilityCode : capabilityCodes) {
                    statement.setString(index++, capabilityCode);
                }
                setUuid(statement, index, tenantId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws Exception {
        statement.setObject(index, value.toString(), Types.OTHER);
    }
}
