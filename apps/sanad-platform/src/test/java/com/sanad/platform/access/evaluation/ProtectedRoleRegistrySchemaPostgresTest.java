package com.sanad.platform.access.evaluation;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtectedRoleRegistrySchemaPostgresTest {

    private static final List<String> EXPECTED_CODES = List.of(
            "AGENT_SUPER_ADMIN",
            "PLATFORM_ADMIN",
            "PLATFORM_OWNER",
            "TENANT_ADMIN");

    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ProtectedRoleRegistrySchemaPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(
                available,
                "PostgreSQL Direct is required for ProtectedRoleRegistrySchemaPostgresTest");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);
    }

    @Test
    void protectsExactlyFourCanonicalCodesAndSeedsTenantAdmin() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(isolatedUrl(), USER, PASSWORD)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();

        flyway.clean();
        flyway.migrate();
        flyway.validate();

        try (Connection connection = connect()) {
            assertThat(regclass(connection, "protected_system_roles"))
                    .isEqualTo("protected_system_roles");
            assertThat(readProtectedCodes(connection)).containsExactlyElementsOf(EXPECTED_CODES);
            assertThat(readProtectedCodes(connection)).doesNotContain("AGENT_CUSTOM_ADMIN");

            SQLException customAdmin = assertThrows(SQLException.class, () -> {
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO protected_system_roles
                            (code, description, immutability_level, min_authority)
                        VALUES ('AGENT_CUSTOM_ADMIN', 'forbidden test row', 'MANAGED', 1)
                        """)) {
                    ps.executeUpdate();
                }
            });
            assertThat(customAdmin.getSQLState()).isEqualTo("23514");

            for (UUID tenantId : tenantIds(connection)) {
                setTenant(connection, tenantId);
                try (PreparedStatement ps = connection.prepareStatement("""
                        SELECT role_origin, template_key, status
                          FROM roles
                         WHERE tenant_id = ?
                           AND code = 'TENANT_ADMIN'
                        """)) {
                    ps.setObject(1, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next())
                                .as("TENANT_ADMIN must exist for tenant %s", tenantId)
                                .isTrue();
                        assertThat(rs.getString("role_origin")).isEqualTo("SNAD_TEMPLATE");
                        assertThat(rs.getString("template_key")).isEqualTo("TENANT_ADMIN");
                        assertThat(rs.getString("status")).isEqualTo("ACTIVE");
                        assertThat(rs.next()).isFalse();
                    }
                }
            }
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(isolatedUrl(), USER, PASSWORD);
    }

    private static String isolatedUrl() {
        return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);
    }

    private static void setTenant(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.executeQuery();
        }
    }

    private static List<String> readProtectedCodes(Connection connection) throws SQLException {
        List<String> codes = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT code FROM protected_system_roles ORDER BY code")) {
            while (rs.next()) {
                codes.add(rs.getString(1));
            }
        }
        return codes;
    }

    private static List<UUID> tenantIds(Connection connection) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id FROM tenants ORDER BY id")) {
            while (rs.next()) {
                ids.add(rs.getObject(1, UUID.class));
            }
        }
        assertThat(ids).isNotEmpty();
        return ids;
    }

    private static String regclass(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT to_regclass('public.' || ?)::text")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
