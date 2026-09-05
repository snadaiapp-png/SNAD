package com.sanad.platform.hr.api;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G0 / Master Task 6 / WS5 Task 1 RED contract — canonical HRM v2
 * capability catalog, seeded idempotently, granted to tenant ADMIN roles
 * with TENANT-scope grants, and NEVER broadening HR_MANAGER.
 */
class HrApiV2AuthorizationTest {

    private static final List<String> CANONICAL_HRM_CAPABILITIES = List.of(
            "HRM.EMPLOYEE.VIEW", "HRM.EMPLOYEE.CREATE", "HRM.EMPLOYEE.UPDATE", "HRM.EMPLOYEE.TERMINATE",
            "HRM.ORG_STRUCTURE.VIEW", "HRM.ORG_STRUCTURE.MANAGE",
            "HRM.ASSIGNMENT.VIEW", "HRM.ASSIGNMENT.MANAGE",
            "HRM.CONTRACT.VIEW", "HRM.CONTRACT.MANAGE",
            "HRM.COMPENSATION.VIEW", "HRM.COMPENSATION.MANAGE",
            "HRM.PII.VIEW", "HRM.PII.MANAGE",
            "HRM.USER_LINK.MANAGE",
            "HRM.AUDIT.VIEW",
            "HRM.COMPLIANCE_OVERRIDE.REQUEST", "HRM.COMPLIANCE_OVERRIDE.APPROVE",
            "HRM.ADMIN");

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static String isolatedUrl;

    private Connection connection;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection c = ds.getConnection()) {
                available = c.isValid(5);
            }
        } catch (Throwable ignored) {
        }
        Assumptions.assumeTrue(available, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void migrateFreshDatabase() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(isolatedUrl, DB_USER, DB_PASSWORD);
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();
        connection = ds.getConnection();
        connection.setAutoCommit(true);
    }

    @Test
    void canonicalHrmCapabilityCatalogIsSeededExactly() throws Exception {
        List<String> seeded = queryColumn(
                "SELECT code FROM access_capabilities WHERE code LIKE 'HRM.%' AND status = 'ACTIVE' ORDER BY code");
        assertThat(seeded)
                .as("exactly the 19 canonical HRM.* capabilities must be seeded ACTIVE")
                .containsExactlyInAnyOrderElementsOf(CANONICAL_HRM_CAPABILITIES);
    }

    @Test
    void hrManagerMatrixIsNeverBroadenedByHrmCapabilities() throws Exception {
        // Provision a tenant with the legacy HR_MANAGER template exactly as the
        // RoleTemplateProvisioner defines it, then prove no HRM.* capability
        // reaches the role.
        UUID tenantId = UUID.randomUUID();
        insertTenant(tenantId);
        setTenant(tenantId);
        UUID hrManagerRoleId = insertRole(tenantId, "HR_MANAGER");
        grant(tenantId, hrManagerRoleId, "HR.EMPLOYEE.READ");
        grant(tenantId, hrManagerRoleId, "HR.EMPLOYEE.WRITE");
        grant(tenantId, hrManagerRoleId, "HR.EMPLOYEE.ARCHIVE");

        List<String> caps = roleCapabilities(tenantId, hrManagerRoleId);
        assertThat(caps).containsExactlyInAnyOrder(
                "HR.EMPLOYEE.READ", "HR.EMPLOYEE.WRITE", "HR.EMPLOYEE.ARCHIVE");
        assertThat(caps.stream().filter(c -> c.startsWith("HRM.")).count())
                .as("no HRM.* capability may be bound to HR_MANAGER")
                .isZero();
    }

    @Test
    void adminGrantBackfillIsIdempotentAndCreatesTenantScopeGrants() throws Exception {
        UUID tenantId = UUID.randomUUID();
        insertTenant(tenantId);
        setTenant(tenantId);
        UUID adminRoleId = insertRole(tenantId, "ADMIN");
        UUID hrManagerRoleId = insertRole(tenantId, "HR_MANAGER");
        grant(tenantId, hrManagerRoleId, "HR.EMPLOYEE.READ");
        grant(tenantId, hrManagerRoleId, "HR.EMPLOYEE.WRITE");
        grant(tenantId, hrManagerRoleId, "HR.EMPLOYEE.ARCHIVE");

        // Runtime provisioning grants every ACTIVE capability to ADMIN
        // (RegistrationProvisioner semantics) — replicate, then apply the
        // migration's scope-grant backfill logic and verify idempotency.
        executeUpdate("INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) "
                        + "SELECT gen_random_uuid(), ?, ?, c.id, NOW() FROM access_capabilities c "
                        + "WHERE c.status = 'ACTIVE' "
                        + "AND NOT EXISTS (SELECT 1 FROM role_capabilities rc WHERE rc.tenant_id = ? "
                        + "AND rc.role_id = ? AND rc.capability_id = c.id)",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, adminRoleId);
                    ps.setObject(3, tenantId);
                    ps.setObject(4, adminRoleId);
                });
        applyScopeGrantBackfill();

        List<String> adminCaps = roleCapabilities(tenantId, adminRoleId);
        assertThat(adminCaps).as("ADMIN must hold the full canonical HRM capability set")
                .containsAll(CANONICAL_HRM_CAPABILITIES);
        assertThat(queryScalar("SELECT COUNT(*) FROM access_scope_grants g "
                + "JOIN access_capabilities c ON c.id = g.capability_id "
                + "WHERE g.tenant_id = '" + tenantId + "' AND g.role_id = '" + adminRoleId + "' "
                + "AND g.scope_type = 'TENANT' AND c.code LIKE 'HRM.%' AND g.status = 'ACTIVE'"))
                .as("one TENANT-scope grant per ADMIN HRM capability")
                .isEqualTo("19");

        // Idempotency: re-running the backfill must not duplicate anything.
        applyScopeGrantBackfill();
        assertThat(queryScalar("SELECT COUNT(*) FROM access_scope_grants g "
                + "JOIN access_capabilities c ON c.id = g.capability_id "
                + "WHERE g.tenant_id = '" + tenantId + "' AND g.role_id = '" + adminRoleId + "' "
                + "AND g.scope_type = 'TENANT' AND c.code LIKE 'HRM.%' AND g.status = 'ACTIVE'"))
                .isEqualTo("19");

        List<String> hrManagerCaps = roleCapabilities(tenantId, hrManagerRoleId);
        assertThat(hrManagerCaps).containsExactlyInAnyOrder(
                "HR.EMPLOYEE.READ", "HR.EMPLOYEE.WRITE", "HR.EMPLOYEE.ARCHIVE");
    }

    // ==================== fixtures / plumbing ====================

    /** Mirrors the V20260904_3 ADMIN scope-grant backfill statement (same SQL contract). */
    private void applyScopeGrantBackfill() throws Exception {
        executeUpdate("INSERT INTO access_scope_grants (id, tenant_id, role_id, capability_id, scope_type, "
                        + "is_direct_exception, reason, status, created_at) "
                        + "SELECT gen_random_uuid(), rc.tenant_id, rc.role_id, rc.capability_id, 'TENANT', "
                        + "FALSE, 'HRM-G0 WS5 Task 1 canonical ADMIN scope grant', 'ACTIVE', NOW() "
                        + "FROM role_capabilities rc "
                        + "JOIN roles r ON r.id = rc.role_id AND r.tenant_id = rc.tenant_id AND r.code = 'ADMIN' "
                        + "JOIN access_capabilities cap ON cap.id = rc.capability_id AND cap.code LIKE 'HRM.%' "
                        + "WHERE rc.tenant_id::text = current_setting('app.tenant_id', true) "
                        + "AND NOT EXISTS (SELECT 1 FROM access_scope_grants g "
                        + "WHERE g.tenant_id = rc.tenant_id AND g.role_id = rc.role_id "
                        + "AND g.capability_id = rc.capability_id AND g.scope_type = 'TENANT' AND g.status = 'ACTIVE')",
                null);
    }

    private void insertTenant(UUID id) throws Exception {
        executeUpdate("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                ps -> {
                    ps.setObject(1, id);
                    ps.setString(2, "WS5-CAP-" + id);
                    ps.setString(3, "ws5cap-" + id.toString().substring(0, 8));
                });
    }

    private void setTenant(UUID tenant) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
    }

    private UUID insertRole(UUID tenantId, String code) throws Exception {
        UUID roleId = UUID.randomUUID();
        executeUpdate("INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?, 'ACTIVE', NOW(), NOW())",
                ps -> {
                    ps.setObject(1, roleId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, code);
                    ps.setString(4, code);
                    ps.setString(5, "test role");
                });
        return roleId;
    }

    private void grant(UUID tenantId, UUID roleId, String capabilityCode) throws Exception {
        executeUpdate("INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) "
                        + "SELECT gen_random_uuid(), ?, ?, c.id, NOW() FROM access_capabilities c WHERE c.code = ?",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, roleId);
                    ps.setString(3, capabilityCode);
                });
    }

    private List<String> roleCapabilities(UUID tenantId, UUID roleId) throws Exception {
        return queryColumn("SELECT c.code FROM role_capabilities rc JOIN access_capabilities c ON c.id = rc.capability_id "
                + "WHERE rc.tenant_id = '" + tenantId + "' AND rc.role_id = '" + roleId + "' ORDER BY c.code");
    }

    private List<String> queryColumn(String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    private String queryScalar(String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void executeUpdate(String sql, SqlBinder binder) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (binder != null) binder.bind(ps);
            ps.executeUpdate();
        }
    }

    private interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
    }
}
