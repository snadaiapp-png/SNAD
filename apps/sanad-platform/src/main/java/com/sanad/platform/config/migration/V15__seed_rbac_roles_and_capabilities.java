package com.sanad.platform.config.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Flyway Java migration V15: Seeds predefined roles and assigns capabilities
 * for each existing tenant.
 *
 * <p>This is a Java-based migration because the SQL migration cannot use
 * a portable UUID generation function that works in both PostgreSQL and H2.
 * PostgreSQL uses {@code gen_random_uuid()}, H2 uses {@code RANDOM_UUID()}.
 * Java's {@code UUID.randomUUID()} works universally.</p>
 *
 * <p>Creates the following predefined roles for each ACTIVE tenant:</p>
 * <ul>
 *   <li>SUPER_ADMIN — all capabilities</li>
 *   <li>ORG_ADMIN — user, organization, membership management</li>
 *   <li>MANAGER — read access + membership management</li>
 *   <li>MEMBER — basic read access</li>
 *   <li>VIEWER — read-only access to non-sensitive resources</li>
 * </ul>
 *
 * <p>Also ensures the existing ADMIN role (created by bootstrap) gets all
 * capabilities, treating it equivalently to SUPER_ADMIN.</p>
 */
@Component
public class V15__seed_rbac_roles_and_capabilities extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V15__seed_rbac_roles_and_capabilities.class);

    /** Role definitions: code, name, description */
    private static final String[][] PREDEFINED_ROLES = {
            {"SUPER_ADMIN", "Super Administrator", "Full administrative access with all capabilities"},
            {"ORG_ADMIN",   "Organization Admin",   "Manage users, organizations, and memberships within the tenant"},
            {"MANAGER",     "Manager",              "Read access and membership management within assigned organizations"},
            {"MEMBER",      "Member",               "Basic read access to resources within the tenant"},
            {"VIEWER",      "Viewer",               "Read-only access to non-sensitive resources"}
    };

    /** Capability codes assigned to ORG_ADMIN */
    private static final String[] ORG_ADMIN_CAPABILITIES = {
            "USER.READ", "USER.CREATE", "USER.WRITE",
            "ORGANIZATION.READ", "ORGANIZATION.CREATE", "ORGANIZATION.WRITE",
            "MEMBERSHIP.READ", "MEMBERSHIP.CREATE", "MEMBERSHIP.WRITE",
            "ROLE.READ", "CAPABILITY.READ", "ACCESS.EVALUATE",
            "USER.GRANT_ROLE", "USER.REVOKE_ROLE"
    };

    /** Capability codes assigned to MANAGER */
    private static final String[] MANAGER_CAPABILITIES = {
            "USER.READ", "ORGANIZATION.READ",
            "MEMBERSHIP.READ", "MEMBERSHIP.CREATE", "MEMBERSHIP.WRITE",
            "ROLE.READ", "CAPABILITY.READ", "ACCESS.EVALUATE"
    };

    /** Capability codes assigned to MEMBER */
    private static final String[] MEMBER_CAPABILITIES = {
            "USER.READ", "ORGANIZATION.READ", "MEMBERSHIP.READ",
            "ROLE.READ", "CAPABILITY.READ", "ACCESS.EVALUATE"
    };

    /** Capability codes assigned to VIEWER (same as MEMBER) */
    private static final String[] VIEWER_CAPABILITIES = MEMBER_CAPABILITIES;

    @Override
    public void migrate(Context context) throws Exception {
        // Step 1: Get all ACTIVE tenants
        List<UUID> tenantIds = new ArrayList<>();
        try (Statement stmt = context.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id FROM tenants WHERE status = 'ACTIVE'")) {
            while (rs.next()) {
                tenantIds.add(UUID.fromString(rs.getString("id")));
            }
        }

        if (tenantIds.isEmpty()) {
            log.info("V15: No active tenants found; skipping role seeding.");
            return;
        }

        log.info("V15: Seeding predefined roles for {} tenant(s)", tenantIds.size());

        // Step 2: Create predefined roles for each tenant
        String insertRoleSql = "INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at) " +
                "SELECT ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP " +
                "WHERE NOT EXISTS (SELECT 1 FROM roles WHERE tenant_id = ? AND code = ?)";

        try (PreparedStatement ps = context.getConnection().prepareStatement(insertRoleSql)) {
            for (UUID tenantId : tenantIds) {
                for (String[] role : PREDEFINED_ROLES) {
                    // Use setObject with Types.OTHER for UUID columns —
                    // setString sends character varying which fails on
                    // PostgreSQL with "operator does not exist: uuid =
                    // character varying". Types.OTHER lets the JDBC driver
                    // send the value as a PostgreSQL uuid literal.
                    ps.setObject(1, UUID.randomUUID().toString(), java.sql.Types.OTHER);
                    ps.setObject(2, tenantId.toString(), java.sql.Types.OTHER);
                    ps.setString(3, role[0]); // code (VARCHAR column)
                    ps.setString(4, role[1]); // name (VARCHAR column)
                    ps.setString(5, role[2]); // description (VARCHAR column)
                    ps.setObject(6, tenantId.toString(), java.sql.Types.OTHER);
                    ps.setString(7, role[0]); // code for NOT EXISTS check
                    ps.addBatch();
                }
            }
            int[] results = ps.executeBatch();
            int totalInserted = 0;
            for (int r : results) totalInserted += r;
            log.info("V15: Inserted {} role(s)", totalInserted);
        }

        // Step 3: Assign capabilities to roles
        // SUPER_ADMIN gets ALL capabilities
        assignAllCapabilitiesToRole(context, tenantIds, "SUPER_ADMIN");
        // ADMIN (bootstrap role) also gets ALL capabilities
        assignAllCapabilitiesToRole(context, tenantIds, "ADMIN");
        // ORG_ADMIN
        assignCapabilitiesToRole(context, tenantIds, "ORG_ADMIN", ORG_ADMIN_CAPABILITIES);
        // MANAGER
        assignCapabilitiesToRole(context, tenantIds, "MANAGER", MANAGER_CAPABILITIES);
        // MEMBER
        assignCapabilitiesToRole(context, tenantIds, "MEMBER", MEMBER_CAPABILITIES);
        // VIEWER
        assignCapabilitiesToRole(context, tenantIds, "VIEWER", VIEWER_CAPABILITIES);

        log.info("V15: RBAC role and capability seeding complete.");
    }

    /**
     * Assigns ALL active capabilities to the specified role for each tenant.
     */
    private void assignAllCapabilitiesToRole(Context context, List<UUID> tenantIds, String roleCode) throws Exception {
        // Use gen_random_uuid() in the SELECT so each row gets a unique id.
        // Binding a single ? for id would give all rows the same UUID,
        // causing a primary key violation on multi-row inserts.
        String sql = "INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) " +
                "SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP " +
                "FROM tenants t " +
                "JOIN roles r ON r.tenant_id = t.id AND r.code = ? " +
                "JOIN access_capabilities ac ON ac.status = 'ACTIVE' " +
                "WHERE t.id = ? AND t.status = 'ACTIVE' " +
                "AND NOT EXISTS (" +
                "  SELECT 1 FROM role_capabilities rc " +
                "  WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id" +
                ")";

        try (PreparedStatement ps = context.getConnection().prepareStatement(sql)) {
            for (UUID tenantId : tenantIds) {
                ps.setString(1, roleCode);
                ps.setObject(2, tenantId.toString(), java.sql.Types.OTHER);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            int total = 0;
            for (int r : results) total += r;
            log.info("V15: Assigned {} capabilities to {} role across all tenants", total, roleCode);
        }
    }

    /**
     * Assigns specific capabilities to the specified role for each tenant.
     */
    private void assignCapabilitiesToRole(Context context, List<UUID> tenantIds, String roleCode, String[] capabilityCodes) throws Exception {
        // Build IN clause for capability codes
        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < capabilityCodes.length; i++) {
            if (i > 0) inClause.append(", ");
            inClause.append("?");
        }

        String sql = "INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) " +
                "SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP " +
                "FROM tenants t " +
                "JOIN roles r ON r.tenant_id = t.id AND r.code = ? " +
                "JOIN access_capabilities ac ON ac.code IN (" + inClause + ") AND ac.status = 'ACTIVE' " +
                "WHERE t.id = ? AND t.status = 'ACTIVE' " +
                "AND NOT EXISTS (" +
                "  SELECT 1 FROM role_capabilities rc " +
                "  WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id" +
                ")";

        try (PreparedStatement ps = context.getConnection().prepareStatement(sql)) {
            for (UUID tenantId : tenantIds) {
                int paramIdx = 1;
                ps.setString(paramIdx++, roleCode);
                for (String code : capabilityCodes) {
                    ps.setString(paramIdx++, code);
                }
                ps.setObject(paramIdx, tenantId.toString(), java.sql.Types.OTHER);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            int total = 0;
            for (int r : results) total += r;
            log.info("V15: Assigned {} capabilities to {} role across all tenants", total, roleCode);
        }
    }
}
