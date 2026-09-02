package com.sanad.platform.hr.security;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HRM-G0 / WS4 / Task 1 RED contract for scoped-authorization persistence.
 * Authorization evaluation behavior is added in WS4 Task 2.
 */
class HrScopedAuthorizationIntegrationTest {

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
                .javaMigrations(new com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities())
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
    void accessScopeGrantTableUsesForcedFailClosedRls() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = 'access_scope_grants'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("access_scope_grants must exist").isTrue();
                assertThat(rs.getBoolean("relrowsecurity")).isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity")).isTrue();
            }
        }
    }

    @Test
    void principalMustBeExactlyOneRoleOrUser() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        seedTenantAndCapability(tenantId, capabilityId);
        setTenant(tenantId);

        assertThatThrownBy(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO access_scope_grants " +
                            "(tenant_id, capability_id, scope_type, status) VALUES (?, ?, 'TENANT', 'ACTIVE')")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, capabilityId);
                ps.executeUpdate();
            }
        }).isInstanceOf(SQLException.class)
          .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    @Test
    void directExceptionRequiresGovernanceMetadata() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        seedTenantAndCapability(tenantId, capabilityId);
        seedUser(tenantId, userId);
        setTenant(tenantId);

        assertThatThrownBy(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO access_scope_grants " +
                            "(tenant_id, user_id, capability_id, scope_type, is_direct_exception, status) " +
                            "VALUES (?, ?, ?, 'SELF', TRUE, 'ACTIVE')")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                ps.setObject(3, capabilityId);
                ps.executeUpdate();
            }
        }).isInstanceOf(SQLException.class)
          .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    @Test
    void legacyCapabilitiesAreNotBackfilledIntoCanonicalScopeGrants() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM access_scope_grants g " +
                        "JOIN access_capabilities c ON c.id = g.capability_id " +
                        "WHERE c.code LIKE 'HR.%' OR c.code LIKE 'HRM.%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    private void seedTenantAndCapability(UUID tenantId, UUID capabilityId) throws Exception {
        resetTenant();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "WS4-" + tenantId);
            ps.setString(3, "ws4-" + tenantId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO access_capabilities (id, code, name, status, created_at, updated_at) " +
                        "VALUES (?, ?, 'WS4 Test Capability', 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, capabilityId);
            ps.setString(2, "TEST.WS4." + capabilityId);
            ps.executeUpdate();
        }
    }

    private void seedUser(UUID tenantId, UUID userId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (id, tenant_id, email, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
            ps.setObject(1, userId);
            ps.setObject(2, tenantId);
            ps.setString(3, userId + "@example.invalid");
            ps.executeUpdate();
        }
    }

    private void setTenant(UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private void resetTenant() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', '', false)")) {
            ps.execute();
        }
    }
}
