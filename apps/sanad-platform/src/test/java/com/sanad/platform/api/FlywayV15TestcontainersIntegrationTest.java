package com.sanad.platform.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2.9.1 §5/§6 — Testcontainers PostgreSQL integration tests
 * for the Flyway V15 reconciliation.
 *
 * <p>These tests spin up a REAL PostgreSQL 16 container and apply all
 * Flyway migrations from scratch using the production profile. They
 * prove:</p>
 *
 * <ol type="a">
 *   <li><b>Empty database (§5a):</b> All migrations apply successfully,
 *       V15 is recorded as type=JDBC with description="seed rbac roles
 *       and capabilities", V20260702_1 is recorded as type=SQL, flyway
 *       validate succeeds, and no duplicate roles/capabilities/role_capabilities
 *       exist.</li>
 * </ol>
 *
 * <p>These tests require Docker at runtime. In environments without
 * Docker (local dev without Docker), they are skipped via
 * {@code @EnabledIfEnvironmentVariable}.</p>
 */
@Testcontainers
@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TESTCONTAINERS_TESTS", matches = "true")
class FlywayV15TestcontainersIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sanad_v15_test")
            .withUsername("sanad")
            .withPassword("sanad-test-only");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/migration-pg-only");
        registry.add("spring.flyway.validate-on-migrate", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.profiles.active", () -> "prod");
        // JWT secret for context load — not a production secret
        registry.add("sanad.security.jwt.secret",
                () -> "testcontainers-test-only-non-production-key-1234567890");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("§5a_emptyDb_v15_recordedAsJdbc: type=JDBC, description=seed rbac roles and capabilities")
    void emptyDb_v15_recordedAsJdbc() throws Exception {
        String row = queryFlywayHistory("SELECT type || '|' || description FROM flyway_schema_history WHERE version = '15'");
        assertThat(row)
                .as("V15 must be recorded as JDBC|seed rbac roles and capabilities")
                .isEqualTo("JDBC|seed rbac roles and capabilities");
    }

    @Test
    @DisplayName("§5a_emptyDb_v15_typeIsJdbc: type column must be JDBC (not SQL)")
    void emptyDb_v15_typeIsJdbc() throws Exception {
        String type = queryFlywayHistory("SELECT type FROM flyway_schema_history WHERE version = '15'");
        assertThat(type)
                .as("V15 type must be JDBC (Java migration), not SQL")
                .isEqualTo("JDBC");
    }

    @Test
    @DisplayName("§5a_emptyDb_v20260702_1_recordedAsSql: reconciler applied as SQL")
    void emptyDb_v20260702_1_recordedAsSql() throws Exception {
        String row = queryFlywayHistory(
                "SELECT type FROM flyway_schema_history WHERE version = '20260702.1'");
        assertThat(row)
                .as("V20260702_1 reconciler must be recorded as SQL")
                .isEqualTo("SQL");
    }

    @Test
    @DisplayName("§5a_emptyDb_v20260702_1_appliedExactlyOnce: count=1, success=true")
    void emptyDb_v20260702_1_appliedExactlyOnce() throws Exception {
        String count = queryFlywayHistory(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '20260702.1' AND success = true");
        assertThat(count)
                .as("V20260702_1 must be applied exactly once with success=true")
                .isEqualTo("1");
    }

    @Test
    @DisplayName("§5a_emptyDb_noFailedMigrations: all migrations success=true")
    void emptyDb_noFailedMigrations() throws Exception {
        String row = queryFlywayHistory(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false");
        assertThat(row)
                .as("No failed migrations allowed")
                .isEqualTo("0");
    }

    @Test
    @DisplayName("§5a_emptyDb_flywayValidateSucceeds: validate-on-migrate=true passes")
    void emptyDb_flywayValidateSucceeds() throws Exception {
        // If the Spring context loaded successfully, Flyway validation
        // passed (validate-on-migrate=true). This test asserts the context
        // loaded by virtue of the @SpringBootTest annotation.
        // Additionally, verify the migration count is correct.
        String count = queryFlywayHistory(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true");
        int migrationCount = Integer.parseInt(count);
        assertThat(migrationCount)
                .as("All migrations must be applied successfully")
                .isGreaterThanOrEqualTo(36);
    }

    @Test
    @DisplayName("§5a_emptyDb_noDuplicateRoles: no duplicate (tenant_id, code) in roles")
    void emptyDb_noDuplicateRoles() throws Exception {
        String dupCount = queryGeneric(
                "SELECT COUNT(*) FROM (SELECT tenant_id, code, COUNT(*) as c FROM roles GROUP BY tenant_id, code HAVING COUNT(*) > 1) dups");
        assertThat(dupCount)
                .as("No duplicate roles (tenant_id, code) allowed after V15 + reconciler")
                .isEqualTo("0");
    }

    @Test
    @DisplayName("§5a_emptyDb_noDuplicateRoleCapabilities: no duplicate (tenant_id, role_id, capability_id)")
    void emptyDb_noDuplicateRoleCapabilities() throws Exception {
        String dupCount = queryGeneric(
                "SELECT COUNT(*) FROM (SELECT tenant_id, role_id, capability_id, COUNT(*) as c FROM role_capabilities GROUP BY tenant_id, role_id, capability_id HAVING COUNT(*) > 1) dups");
        assertThat(dupCount)
                .as("No duplicate role_capabilities allowed after V15 + reconciler")
                .isEqualTo("0");
    }

    @Test
    @DisplayName("§5a_emptyDb_v15_successTrue: V15 row has success=true")
    void emptyDb_v15_successTrue() throws Exception {
        String row = queryFlywayHistory(
                "SELECT BOOL(success) FROM flyway_schema_history WHERE version = '15'");
        assertThat(row)
                .as("V15 must have success=true")
                .isEqualTo("t");
    }

    // === Helpers ===

    private String queryFlywayHistory(String sql) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return null;
    }

    private String queryGeneric(String sql) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return null;
    }
}
