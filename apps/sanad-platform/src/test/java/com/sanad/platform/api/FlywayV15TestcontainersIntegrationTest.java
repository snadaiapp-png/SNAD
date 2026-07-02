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
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2.9.1 §5/§6 — Testcontainers PostgreSQL integration tests
 * for the Flyway V15 reconciliation.
 *
 * <p>These tests spin up a REAL PostgreSQL 16 container and apply all
 * Flyway migrations from scratch using the production profile.</p>
 *
 * <p><b>CRITICAL:</b> V15 is an SQL migration ({@code seed_admin_role_and_capabilities}),
 * NOT a Java migration. This matches the production database which was
 * deployed from main. The Java V15 was removed because it never matched production
 * was removed because it was never applied to production.</p>
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
        registry.add("sanad.security.jwt.secret",
                () -> "testcontainers-test-only-non-production-key-1234567890");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("§5a_emptyDb_v15_recordedAsSql: type=SQL, description=seed admin role and capabilities")
    void emptyDb_v15_recordedAsSql() throws Exception {
        String type = queryFlywayHistory("SELECT type FROM flyway_schema_history WHERE version = '15'");
        assertThat(type)
                .as("V15 must be type=SQL (matching production)")
                .isEqualTo("SQL");
        String desc = queryFlywayHistory("SELECT description FROM flyway_schema_history WHERE version = '15'");
        assertThat(desc)
                .as("V15 description must be 'seed admin role and capabilities'")
                .isEqualTo("seed admin role and capabilities");
    }

    @Test
    @DisplayName("§5a_emptyDb_v15_successTrue: V15 row has success=true")
    void emptyDb_v15_successTrue() throws Exception {
        String count = queryFlywayHistory(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '15' AND success = true");
        assertThat(count)
                .as("V15 must have success=true")
                .isEqualTo("1");
    }

    @Test
    @DisplayName("§5a_emptyDb_v20260702_2_recordedAsSql: reconciler applied as SQL")
    void emptyDb_v20260702_2_recordedAsSql() throws Exception {
        String type = queryFlywayHistory(
                "SELECT type FROM flyway_schema_history WHERE version = '20260702.2'");
        assertThat(type)
                .as("V20260702_2 reconciler must be recorded as SQL")
                .isEqualTo("SQL");
    }

    @Test
    @DisplayName("§5a_emptyDb_v20260702_2_appliedExactlyOnce: count=1, success=true")
    void emptyDb_v20260702_2_appliedExactlyOnce() throws Exception {
        String count = queryFlywayHistory(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '20260702.2' AND success = true");
        assertThat(count)
                .as("V20260702_2 must be applied exactly once with success=true")
                .isEqualTo("1");
    }

    @Test
    @DisplayName("§5a_emptyDb_noFailedMigrations: all migrations success=true")
    void emptyDb_noFailedMigrations() throws Exception {
        String count = queryFlywayHistory(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false");
        assertThat(count)
                .as("No failed migrations allowed")
                .isEqualTo("0");
    }

    @Test
    @DisplayName("§5a_emptyDb_flywayValidateSucceeds: validate-on-migrate=true passes")
    void emptyDb_flywayValidateSucceeds() throws Exception {
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
