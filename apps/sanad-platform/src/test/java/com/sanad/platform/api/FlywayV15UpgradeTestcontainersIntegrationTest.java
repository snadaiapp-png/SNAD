package com.sanad.platform.api;

import com.sanad.platform.SanadPlatformApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2.9.1 §4 — Two-phase upgrade test simulating production.
 *
 * <p><b>CRITICAL:</b> Production has V15 as SQL ({@code seed_admin_role_and_capabilities}),
 * NOT Java. This test simulates the production state by applying V1-V19
 * from db/migration (which includes the SQL V15), then upgrading to the
 * new artifact that adds V20-V39 (pg-only) and V20260702_2 (reconciler).</p>
 *
 * <p>Phase A: Apply V1-V19 (matching production state from main).</p>
 * <p>Phase B: Run new artifact (adds pg-only migrations + reconciler).</p>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TESTCONTAINERS_TESTS", matches = "true")
class FlywayV15UpgradeTestcontainersIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sanad_upgrade_test")
            .withUsername("sanad")
            .withPassword("sanad-test-only");

    private static final UUID SENTINEL_TENANT_ID = UUID.randomUUID();
    private static final UUID SENTINEL_ROLE_ID = UUID.randomUUID();
    private static final UUID SENTINEL_CAPABILITY_ID = UUID.randomUUID();
    private static final UUID SENTINEL_ROLE_CAPABILITY_ID = UUID.randomUUID();

    @BeforeAll
    void phaseA_buildProductionState() throws Exception {
        // Phase A: Apply V1-V19 from db/migration ONLY (matching production).
        // Production uses locations=classpath:db/migration (no pg-only).
        // This applies V15 as SQL (seed_admin_role_and_capabilities).
        Flyway flywayV19 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("19")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .load();
        flywayV19.migrate();

        // Insert sentinel data that must survive the upgrade
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(
                    "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                    "VALUES ('%s', 'Sentinel Tenant', 'sentinel-test', 'ACTIVE', NOW(), NOW())",
                    SENTINEL_TENANT_ID));
            stmt.execute(String.format(
                    "INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at) " +
                    "VALUES ('%s', '%s', 'CUSTOM_ROLE', 'Custom Sentinel Role', " +
                    "'Pre-existing custom role', 'ACTIVE', NOW(), NOW())",
                    SENTINEL_ROLE_ID, SENTINEL_TENANT_ID));
            stmt.execute(String.format(
                    "INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at) " +
                    "VALUES ('%s', 'CUSTOM.CAPABILITY', 'Custom Capability', " +
                    "'Pre-existing custom capability', 'ACTIVE', NOW(), NOW())",
                    SENTINEL_CAPABILITY_ID));
            stmt.execute(String.format(
                    "INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) " +
                    "VALUES ('%s', '%s', '%s', '%s', NOW())",
                    SENTINEL_ROLE_CAPABILITY_ID, SENTINEL_TENANT_ID,
                    SENTINEL_ROLE_ID, SENTINEL_CAPABILITY_ID));
        }

        // Assert V15 is SQL (matching production)
        String v15Type = querySingleValue(
                "SELECT type FROM flyway_schema_history WHERE version = '15'");
        assertThat(v15Type)
                .as("V15 must be type=SQL (matching production)")
                .isEqualTo("SQL");
        String v15Desc = querySingleValue(
                "SELECT description FROM flyway_schema_history WHERE version = '15'");
        assertThat(v15Desc)
                .as("V15 description must be 'seed admin role and capabilities'")
                .isEqualTo("seed admin role and capabilities");
    }

    @Test
    @Order(1)
    @DisplayName("§4b_phaseB_newArtifactStartsSuccessfully")
    void phaseB_newArtifactStartsSuccessfully() {
        // Start the new artifact with BOTH migration directories.
        // This adds V20-V39 (pg-only) and V20260702_2 (reconciler).
        ConfigurableApplicationContext ctx = new SpringApplication(SanadPlatformApplication.class)
                .run("--spring.profiles.active=prod",
                     "--spring.datasource.url=" + postgres.getJdbcUrl(),
                     "--spring.datasource.username=" + postgres.getUsername(),
                     "--spring.datasource.password=" + postgres.getPassword(),
                     "--spring.datasource.driver-class-name=org.postgresql.Driver",
                     "--spring.flyway.enabled=true",
                     "--spring.flyway.locations=classpath:db/migration,classpath:db/migration-pg-only",
                     "--spring.flyway.validate-on-migrate=true",
                     "--spring.flyway.clean-disabled=true",
                     "--spring.flyway.baseline-on-migrate=false",
                     "--spring.jpa.hibernate.ddl-auto=validate",
                     "--sanad.security.jwt.secret=testcontainers-upgrade-test-only-non-production-key-1234567890",
                     "--server.port=0");
        ctx.close();
    }

    @Test
    @Order(2)
    @DisplayName("§4b_v15Preserved: V15 remains type=SQL after new artifact startup")
    void phaseB_v15Preserved() throws Exception {
        String v15Type = querySingleValue(
                "SELECT type FROM flyway_schema_history WHERE version = '15'");
        assertThat(v15Type)
                .as("V15 must remain type=SQL after new artifact startup")
                .isEqualTo("SQL");
    }

    @Test
    @Order(3)
    @DisplayName("§4b_v20260702_2AppliedOnce: reconciler applied exactly once as SQL")
    void phaseB_reconcilerAppliedOnce() throws Exception {
        String type = querySingleValue(
                "SELECT type FROM flyway_schema_history WHERE version = '20260702.2'");
        assertThat(type)
                .as("V20260702_2 must be type=SQL")
                .isEqualTo("SQL");
        String count = querySingleValue(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '20260702.2' AND success = true");
        assertThat(count)
                .as("V20260702_2 must be applied exactly once with success=true")
                .isEqualTo("1");
    }

    @Test
    @Order(4)
    @DisplayName("§4b_noFailedMigrations: zero migrations with success=false")
    void phaseB_noFailedMigrations() throws Exception {
        String count = querySingleValue(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false");
        assertThat(count)
                .as("No failed migrations allowed after upgrade")
                .isEqualTo("0");
    }

    @Test
    @Order(5)
    @DisplayName("§4b_sentinelTenantPreserved")
    void phaseB_sentinelTenantPreserved() throws Exception {
        String row = querySingleValue(
                String.format("SELECT name || '|' || status FROM tenants WHERE id = '%s'",
                        SENTINEL_TENANT_ID));
        assertThat(row)
                .as("Sentinel tenant must survive upgrade unchanged")
                .isEqualTo("Sentinel Tenant|ACTIVE");
    }

    @Test
    @Order(6)
    @DisplayName("§4b_sentinelRolePreserved")
    void phaseB_sentinelRolePreserved() throws Exception {
        String row = querySingleValue(
                String.format("SELECT code || '|' || name FROM roles WHERE id = '%s'",
                        SENTINEL_ROLE_ID));
        assertThat(row)
                .as("Sentinel custom role must survive upgrade unchanged")
                .isEqualTo("CUSTOM_ROLE|Custom Sentinel Role");
    }

    @Test
    @Order(7)
    @DisplayName("§4b_sentinelCapabilityPreserved")
    void phaseB_sentinelCapabilityPreserved() throws Exception {
        String row = querySingleValue(
                String.format("SELECT code || '|' || status FROM access_capabilities WHERE id = '%s'",
                        SENTINEL_CAPABILITY_ID));
        assertThat(row)
                .as("Sentinel custom capability must survive upgrade unchanged")
                .isEqualTo("CUSTOM.CAPABILITY|ACTIVE");
    }

    @Test
    @Order(8)
    @DisplayName("§4b_sentinelRoleCapabilityPreserved")
    void phaseB_sentinelRoleCapabilityPreserved() throws Exception {
        String count = querySingleValue(
                String.format("SELECT COUNT(*) FROM role_capabilities WHERE id = '%s'",
                        SENTINEL_ROLE_CAPABILITY_ID));
        assertThat(count)
                .as("Sentinel role_capability link must survive upgrade")
                .isEqualTo("1");
    }

    @Test
    @Order(9)
    @DisplayName("§4b_noDuplicateRoles")
    void phaseB_noDuplicateRoles() throws Exception {
        String dupCount = querySingleValue(
                "SELECT COUNT(*) FROM (" +
                "  SELECT tenant_id, code, COUNT(*) as c " +
                "  FROM roles GROUP BY tenant_id, code HAVING COUNT(*) > 1" +
                ") dups");
        assertThat(dupCount)
                .as("No duplicate roles after upgrade")
                .isEqualTo("0");
    }

    @Test
    @Order(10)
    @DisplayName("§4b_noDuplicateRoleCapabilities")
    void phaseB_noDuplicateRoleCapabilities() throws Exception {
        String dupCount = querySingleValue(
                "SELECT COUNT(*) FROM (" +
                "  SELECT tenant_id, role_id, capability_id, COUNT(*) as c " +
                "  FROM role_capabilities " +
                "  GROUP BY tenant_id, role_id, capability_id HAVING COUNT(*) > 1" +
                ") dups");
        assertThat(dupCount)
                .as("No duplicate role_capabilities after upgrade")
                .isEqualTo("0");
    }

    @Test
    @Order(11)
    @DisplayName("§4b_secondStartupNoChanges")
    void phaseB_secondStartupNoChanges() throws Exception {
        String migrationsBefore = querySingleValue(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true");
        String rolesBefore = querySingleValue("SELECT COUNT(*) FROM roles");
        String roleCapsBefore = querySingleValue("SELECT COUNT(*) FROM role_capabilities");

        ConfigurableApplicationContext ctx = new SpringApplication(SanadPlatformApplication.class)
                .run("--spring.profiles.active=prod",
                     "--spring.datasource.url=" + postgres.getJdbcUrl(),
                     "--spring.datasource.username=" + postgres.getUsername(),
                     "--spring.datasource.password=" + postgres.getPassword(),
                     "--spring.datasource.driver-class-name=org.postgresql.Driver",
                     "--spring.flyway.enabled=true",
                     "--spring.flyway.locations=classpath:db/migration,classpath:db/migration-pg-only",
                     "--spring.flyway.validate-on-migrate=true",
                     "--spring.flyway.clean-disabled=true",
                     "--spring.flyway.baseline-on-migrate=false",
                     "--spring.jpa.hibernate.ddl-auto=validate",
                     "--sanad.security.jwt.secret=testcontainers-upgrade-test-only-non-production-key-1234567890",
                     "--server.port=0");
        ctx.close();

        String migrationsAfter = querySingleValue(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true");
        String rolesAfter = querySingleValue("SELECT COUNT(*) FROM roles");
        String roleCapsAfter = querySingleValue("SELECT COUNT(*) FROM role_capabilities");

        assertThat(migrationsAfter)
                .as("Second startup must not add any migration rows")
                .isEqualTo(migrationsBefore);
        assertThat(rolesAfter)
                .as("Second startup must not add or remove any roles")
                .isEqualTo(rolesBefore);
        assertThat(roleCapsAfter)
                .as("Second startup must not add or remove any role_capabilities")
                .isEqualTo(roleCapsBefore);
    }

    private Connection getConnection() throws Exception {
        return java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private String querySingleValue(String sql) throws Exception {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return null;
    }
}
