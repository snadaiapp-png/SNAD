package com.sanad.platform.api;

import com.sanad.platform.SanadPlatformApplication;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2.9.1 §4 — Two-phase upgrade test simulating an OLD production
 * database.
 *
 * <p>This test does NOT use {@code @SpringBootTest}. It manually controls
 * the database lifecycle and application startup in two phases:</p>
 *
 * <h3>Phase A — Build old production state</h3>
 * <ol>
 *   <li>Start PostgreSQL 16 container.</li>
 *   <li>Create a Flyway instance with the production migration locations.</li>
 *   <li>Register the Java V15 migration explicitly.</li>
 *   <li>Migrate to target=14 (apply V1–V14 only).</li>
 *   <li>Insert a real tenant with status=ACTIVE.</li>
 *   <li>Insert sentinel data (custom role, capability, role_capability).</li>
 *   <li>Migrate to target=15 (apply Java V15 on top of V14).</li>
 *   <li>Assert flyway_schema_history has V15 as type=JDBC, description=
 *       "seed rbac roles and capabilities", success=true.</li>
 * </ol>
 *
 * <h3>Phase B — Run new artifact</h3>
 * <ol>
 *   <li>Start SANAD application programmatically via SpringApplication
 *       against the same container, with prod profile and
 *       validate-on-migrate=true.</li>
 *   <li>Shut down the ApplicationContext after successful startup.</li>
 *   <li>Assert V20260702_1 reconciler applied exactly once as SQL.</li>
 *   <li>Assert no failed migrations.</li>
 *   <li>Assert sentinel data preserved (tenant, role, capability,
 *       role_capability).</li>
 *   <li>Assert ADMIN role created/completed for the existing tenant.</li>
 *   <li>Assert no duplicate roles or role_capabilities.</li>
 *   <li>Assert a second startup adds no new migration rows and no
 *       duplicate data.</li>
 * </ol>
 *
 * <p>Requires Docker at runtime. Skipped via
 * {@code @EnabledIfEnvironmentVariable} when Docker is not available.</p>
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

    // Sentinel data UUIDs — created in Phase A, verified in Phase B
    private static final UUID SENTINEL_TENANT_ID = UUID.randomUUID();
    private static final UUID SENTINEL_ROLE_ID = UUID.randomUUID();
    private static final UUID SENTINEL_CAPABILITY_ID = UUID.randomUUID();
    private static final UUID SENTINEL_ROLE_CAPABILITY_ID = UUID.randomUUID();

    private DataSource dataSource;

    // === Phase A: Build old production state ===

    @BeforeAll
    void phaseA_buildOldProductionState() throws Exception {
        // Step 1: Flyway to target=14 (V1–V14 only)
        Flyway flywayV14 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", "classpath:db/migration-pg-only")
                .target("14")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .load();
        flywayV14.migrate();

        // Step 2: Insert a real tenant with status=ACTIVE
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(
                    "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                    "VALUES ('%s', 'Sentinel Tenant', 'sentinel-test', 'ACTIVE', NOW(), NOW())",
                    SENTINEL_TENANT_ID));
        }

        // Step 3: Insert sentinel data that must survive the upgrade
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            // Custom role (not created by V15)
            stmt.execute(String.format(
                    "INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at) " +
                    "VALUES ('%s', '%s', 'CUSTOM_ROLE', 'Custom Sentinel Role', " +
                    "'Pre-existing custom role that must survive upgrade', 'ACTIVE', NOW(), NOW())",
                    SENTINEL_ROLE_ID, SENTINEL_TENANT_ID));

            // Custom capability (not part of the standard V14 seed)
            stmt.execute(String.format(
                    "INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at) " +
                    "VALUES ('%s', 'CUSTOM.CAPABILITY', 'Custom Capability', " +
                    "'Pre-existing custom capability that must survive upgrade', 'ACTIVE', NOW(), NOW())",
                    SENTINEL_CAPABILITY_ID));

            // Link the custom role to the custom capability
            stmt.execute(String.format(
                    "INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) " +
                    "VALUES ('%s', '%s', '%s', '%s', NOW())",
                    SENTINEL_ROLE_CAPABILITY_ID, SENTINEL_TENANT_ID,
                    SENTINEL_ROLE_ID, SENTINEL_CAPABILITY_ID));
        }

        // Step 4: Flyway to target=15 with Java V15 registered explicitly
        Flyway flywayV15 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", "classpath:db/migration-pg-only")
                .target("15")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .load();
        flywayV15.migrate();

        // Step 5: Assert V15 is recorded as JDBC with success=true
        // Use COUNT filter instead of BOOL() to avoid driver-dependent
        // 't'/'true' format differences.
        String v15TypeDesc = querySingleValue(
                "SELECT type || '|' || description " +
                "FROM flyway_schema_history WHERE version = '15'");
        assertThat(v15TypeDesc)
                .as("V15 must be type=JDBC, description='seed rbac roles and capabilities'")
                .isEqualTo("JDBC|seed rbac roles and capabilities");
        String v15Success = querySingleValue(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '15' AND success = true");
        assertThat(v15Success)
                .as("V15 must have success=true")
                .isEqualTo("1");
    }

    // === Phase B: Run new artifact ===

    @Test
    @Order(1)
    @DisplayName("§4b_phaseB_newArtifactStartsSuccessfully: Spring Boot starts with validate-on-migrate=true")
    void phaseB_newArtifactStartsSuccessfully() {
        // Start the SANAD application programmatically against the
        // container database. The prod profile applies all pending
        // migrations (V17–V36 + V20260702_1 reconciler) with
        // validate-on-migrate=true. If validation fails (e.g. V15
        // type mismatch), the startup throws and the test fails.
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

        // Capture the DataSource for assertions
        dataSource = ctx.getBean(DataSource.class);

        // Shut down immediately — we only need the migration to have run
        ctx.close();
    }

    @Test
    @Order(2)
    @DisplayName("§4b_v15Preserved: V15 remains type=JDBC after new artifact startup")
    void phaseB_v15Preserved() throws Exception {
        String v15TypeDesc = querySingleValue(
                "SELECT type || '|' || description " +
                "FROM flyway_schema_history WHERE version = '15'");
        assertThat(v15TypeDesc)
                .as("V15 must remain type=JDBC after new artifact startup")
                .isEqualTo("JDBC|seed rbac roles and capabilities");
        String v15Success = querySingleValue(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '15' AND success = true");
        assertThat(v15Success)
                .as("V15 must have success=true after new artifact startup")
                .isEqualTo("1");
    }

    @Test
    @Order(3)
    @DisplayName("§4b_v20260702_1AppliedOnce: reconciler applied exactly once as SQL")
    void phaseB_reconcilerAppliedOnce() throws Exception {
        String type = querySingleValue(
                "SELECT type FROM flyway_schema_history WHERE version = '20260702.1'");
        assertThat(type)
                .as("V20260702_1 must be type=SQL")
                .isEqualTo("SQL");
        String count = querySingleValue(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '20260702.1' AND success = true");
        assertThat(count)
                .as("V20260702_1 must be applied exactly once with success=true")
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
    @DisplayName("§4b_sentinelTenantPreserved: pre-existing tenant not deleted or modified")
    void phaseB_sentinelTenantPreserved() throws Exception {
        String row = querySingleValue(
                String.format(
                        "SELECT name || '|' || status FROM tenants WHERE id = '%s'",
                        SENTINEL_TENANT_ID));
        assertThat(row)
                .as("Sentinel tenant must survive upgrade unchanged")
                .isEqualTo("Sentinel Tenant|ACTIVE");
    }

    @Test
    @Order(6)
    @DisplayName("§4b_sentinelRolePreserved: pre-existing custom role not deleted or modified")
    void phaseB_sentinelRolePreserved() throws Exception {
        String row = querySingleValue(
                String.format(
                        "SELECT code || '|' || name FROM roles WHERE id = '%s'",
                        SENTINEL_ROLE_ID));
        assertThat(row)
                .as("Sentinel custom role must survive upgrade unchanged")
                .isEqualTo("CUSTOM_ROLE|Custom Sentinel Role");
    }

    @Test
    @Order(7)
    @DisplayName("§4b_sentinelCapabilityPreserved: pre-existing custom capability not deleted")
    void phaseB_sentinelCapabilityPreserved() throws Exception {
        String row = querySingleValue(
                String.format(
                        "SELECT code || '|' || status FROM access_capabilities WHERE id = '%s'",
                        SENTINEL_CAPABILITY_ID));
        assertThat(row)
                .as("Sentinel custom capability must survive upgrade unchanged")
                .isEqualTo("CUSTOM.CAPABILITY|ACTIVE");
    }

    @Test
    @Order(8)
    @DisplayName("§4b_sentinelRoleCapabilityPreserved: pre-existing role_capability link not deleted")
    void phaseB_sentinelRoleCapabilityPreserved() throws Exception {
        String count = querySingleValue(
                String.format(
                        "SELECT COUNT(*) FROM role_capabilities WHERE id = '%s'",
                        SENTINEL_ROLE_CAPABILITY_ID));
        assertThat(count)
                .as("Sentinel role_capability link must survive upgrade")
                .isEqualTo("1");
    }

    @Test
    @Order(9)
    @DisplayName("§4b_adminRoleCreatedForSentinelTenant: ADMIN role exists for the pre-existing tenant")
    void phaseB_adminRoleCreated() throws Exception {
        String count = querySingleValue(
                String.format(
                        "SELECT COUNT(*) FROM roles WHERE tenant_id = '%s' AND code = 'ADMIN'",
                        SENTINEL_TENANT_ID));
        assertThat(count)
                .as("ADMIN role must exist for the sentinel tenant after reconciler")
                .isEqualTo("1");
    }

    @Test
    @Order(10)
    @DisplayName("§4b_noDuplicateRoles: no duplicate (tenant_id, code) in roles")
    void phaseB_noDuplicateRoles() throws Exception {
        String dupCount = querySingleValue(
                "SELECT COUNT(*) FROM (" +
                "  SELECT tenant_id, code, COUNT(*) as c " +
                "  FROM roles GROUP BY tenant_id, code HAVING COUNT(*) > 1" +
                ") dups");
        assertThat(dupCount)
                .as("No duplicate roles (tenant_id, code) after upgrade")
                .isEqualTo("0");
    }

    @Test
    @Order(11)
    @DisplayName("§4b_noDuplicateRoleCapabilities: no duplicate (tenant_id, role_id, capability_id)")
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
    @Order(12)
    @DisplayName("§4b_secondStartupNoChanges: second startup adds no migration rows, no duplicate data")
    void phaseB_secondStartupNoChanges() throws Exception {
        // Count migrations before second startup
        String migrationsBefore = querySingleValue(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true");
        String rolesBefore = querySingleValue("SELECT COUNT(*) FROM roles");
        String roleCapsBefore = querySingleValue("SELECT COUNT(*) FROM role_capabilities");

        // Second startup — should be a no-op (all migrations already applied)
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

        // Count after second startup — must be identical
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

    // === Helpers ===

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
