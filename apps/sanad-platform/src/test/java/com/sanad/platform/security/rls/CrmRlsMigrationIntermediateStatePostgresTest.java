package com.sanad.platform.security.rls;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL Direct migration-order regression test for the exact intermediate
 * state after V20261020_2 and before V20261021_1.
 *
 * <p>This test deliberately migrates only the disposable test_migration
 * database to 20261020.2, inspects the real PostgreSQL catalogs, then applies
 * the next migration and inspects the resulting catalogs again. It does not
 * connect Flyway to the source database URL and never touches production.</p>
 */
class CrmRlsMigrationIntermediateStatePostgresTest {
    private static final String BASE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final String ISOLATED_URL =
            MigrationTestSchemaSupport.getIsolatedJdbcUrl(BASE_URL);
    private static final String INTERMEDIATE_VERSION = "20261020.2";
    private static final String HARDENING_VERSION = "20261021.1";
    private static final List<String> PROTECTED_TABLES = List.of(
            "crm_capacity_plans", "crm_cases", "crm_email_logs",
            "crm_service_assignments", "crm_shift_assignments",
            "crm_shift_templates", "crm_staff_availability", "crm_staff_skills",
            "crm_workload_assignments");

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "CrmRlsMigrationIntermediateStatePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available, "PostgreSQL Direct required");
        MigrationTestSchemaSupport.ensureDatabase(BASE_URL, USER, PASSWORD);
    }

    @Test
    void intermediateMigrationIsNotFailClosedAndNextMigrationHardensAllTables() {
        Flyway intermediate = flyway(MigrationVersion.fromVersion(INTERMEDIATE_VERSION));
        // clean() is explicitly scoped to test_migration via ISOLATED_URL.
        intermediate.clean();
        intermediate.migrate();
        intermediate.validate();

        JdbcTemplate jdbc = jdbc();
        assertThat(migrationApplied(jdbc, INTERMEDIATE_VERSION)).isTrue();
        assertThat(migrationApplied(jdbc, HARDENING_VERSION)).isFalse();

        for (String table : PROTECTED_TABLES) {
            assertThat(rlsEnabled(jdbc, table))
                    .as("RLS must be enabled after %s on %s", INTERMEDIATE_VERSION, table)
                    .isTrue();
            assertThat(forceRlsEnabled(jdbc, table))
                    .as("intermediate state must be distinguished from FORCE RLS on %s", table)
                    .isFalse();

            String qual = policyExpression(jdbc, table, "qual");
            String withCheck = policyExpression(jdbc, table, "with_check");
            assertThat(qual.toLowerCase())
                    .as("intermediate USING policy exposes the missing-context allowance on %s", table)
                    .contains("is null");
            assertThat(withCheck.toLowerCase())
                    .as("intermediate WITH CHECK policy exposes the missing-context allowance on %s", table)
                    .contains("is null");
        }

        // Apply the next forward-only migration to the same isolated database.
        flyway(null).migrate();
        flyway(null).validate();

        assertThat(migrationApplied(jdbc, HARDENING_VERSION)).isTrue();
        for (String table : PROTECTED_TABLES) {
            assertThat(rlsEnabled(jdbc, table)).isTrue();
            assertThat(forceRlsEnabled(jdbc, table))
                    .as("FORCE ROW LEVEL SECURITY must be enabled after %s on %s",
                            HARDENING_VERSION, table)
                    .isTrue();

            String qual = policyExpression(jdbc, table, "qual").toLowerCase();
            String withCheck = policyExpression(jdbc, table, "with_check").toLowerCase();
            assertThat(qual).contains("nullif").contains("is not null");
            assertThat(withCheck).contains("nullif").contains("is not null");
            assertThat(qual).doesNotContain("current_setting('app.tenant_id', true) is null");
            assertThat(withCheck).doesNotContain("current_setting('app.tenant_id', true) is null");
        }
    }

    private static Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(ISOLATED_URL, USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static JdbcTemplate jdbc() {
        var dataSource = new DriverManagerDataSource(ISOLATED_URL, USER, PASSWORD);
        dataSource.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(dataSource);
    }

    private static boolean migrationApplied(JdbcTemplate jdbc, String version) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history "
                        + "WHERE version = ? AND success = TRUE",
                Integer.class, version);
        return count != null && count > 0;
    }

    private static boolean rlsEnabled(JdbcTemplate jdbc, String table) {
        Boolean enabled = jdbc.queryForObject(
                "SELECT c.relrowsecurity FROM pg_class c "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relname = ?",
                Boolean.class, table);
        return Boolean.TRUE.equals(enabled);
    }

    private static boolean forceRlsEnabled(JdbcTemplate jdbc, String table) {
        Boolean enabled = jdbc.queryForObject(
                "SELECT c.relforcerowsecurity FROM pg_class c "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relname = ?",
                Boolean.class, table);
        return Boolean.TRUE.equals(enabled);
    }

    private static String policyExpression(JdbcTemplate jdbc, String table, String column) {
        if (!"qual".equals(column) && !"with_check".equals(column)) {
            throw new IllegalArgumentException("Unsupported pg_policies column: " + column);
        }
        String sql = "SELECT " + column + " FROM pg_policies "
                + "WHERE schemaname = 'public' AND tablename = ? "
                + "AND policyname = 'tenant_isolation'";
        String expression = jdbc.queryForObject(sql, String.class, table);
        assertThat(expression).as("tenant_isolation %s policy must exist on %s", column, table)
                .isNotBlank();
        return expression;
    }
}
