package com.sanad.platform.hrmfoundation.platform;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import static org.assertj.core.api.Assertions.assertThat;

class PlatformPrerequisiteRlsIntegrationTest {
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private static String ISOLATED_URL;

    private static final String DB_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable = false;
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
            try (Connection conn = ds.getConnection()) { postgresAvailable = conn.isValid(5); }
        } catch (Throwable ignored) { postgresAvailable = false; }
        Assumptions.assumeTrue(postgresAvailable, "PostgreSQL Direct is not available");
        MigrationTestSchemaSupport.ensureDatabase(DB_URL, DB_USER, DB_PASSWORD);
        ISOLATED_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(DB_URL);
    }

    @BeforeEach
    void migrateAndSeed() {
        dataSource = new DriverManagerDataSource(ISOLATED_URL, DB_USER, DB_PASSWORD);
        jdbc = new JdbcTemplate(dataSource);
        // Clean the test_migration database, then migrate with baselineOnMigrate=false
        // so Flyway runs ALL migrations from scratch (not just baseline).
        Flyway flyway = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration,classpath:db/vendor/{vendor}")
                .baselineOnMigrate(false)
                .cleanDisabled(false)
                .validateOnMigrate(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void rlsIsFailClosedWhenTenantContextMissing() {
        // Without app.tenant_id, fail-closed RLS should return 0 rows
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM legal_entities", Integer.class);
        assertThat(count).as("missing tenant context should return 0 rows (fail-closed)").isEqualTo(0);
    }

    @Test
    void runtimeRoleIsNotSuperuser() {
        // Use pg_user catalog (compatible with all PostgreSQL versions)
        Boolean isSuperuser = jdbc.queryForObject(
                "SELECT usesuper FROM pg_user WHERE usename = current_user", Boolean.class);
        assertThat(isSuperuser).as("RLS tests must run as non-superuser role").isFalse();
    }

    @Test
    void runtimeRoleDoesNotHaveBypassrls() {
        Boolean hasBypass = jdbc.queryForObject(
                "SELECT COALESCE((SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user), false)",
                Boolean.class);
        assertThat(hasBypass).as("RLS tests must run as role without BYPASSRLS").isFalse();
    }
}
