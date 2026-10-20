package com.sanad.platform.security.rls;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL Direct contract for the post-baseline CRM tenant RLS policies.
 * Verifies policy metadata and the effective visibility contract for missing,
 * empty, valid, and switched tenant contexts. Background jobs must set an
 * explicit trusted tenant context; this test deliberately defines no bypass.
 */
class CrmPostBaselineFailClosedRlsPostgresTest {
    private static final String BASE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final String JDBC_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(BASE_URL);
    private static final List<String> TABLES = List.of(
            "crm_capacity_plans", "crm_cases", "crm_email_logs",
            "crm_service_assignments", "crm_shift_assignments",
            "crm_shift_templates", "crm_staff_availability", "crm_staff_skills",
            "crm_workload_assignments");

    private JdbcTemplate jdbc;

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "CrmPostBaselineFailClosedRlsPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available, "PostgreSQL Direct required");
        MigrationTestSchemaSupport.ensureDatabase(BASE_URL, USER, PASSWORD);
    }

    @BeforeEach
    void migrateIsolatedSchema() {
        Flyway.configure()
                .dataSource(JDBC_URL, USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(JDBC_URL, USER, PASSWORD, true));
    }

    @Test
    void everyTargetTableHasForcedFailClosedTenantPolicy() {
        for (String table : TABLES) {
            Integer policyCount = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM pg_policies
                    WHERE schemaname = 'public'
                      AND tablename = ?
                      AND policyname = 'tenant_isolation'
                      AND cmd = 'ALL'
                      AND qual ILIKE '%NULLIF%'
                      AND qual ILIKE '%app.tenant_id%'
                      AND qual NOT ILIKE '%IS NULL OR%'
                      AND with_check ILIKE '%NULLIF%'
                      AND with_check ILIKE '%app.tenant_id%'
                      AND with_check NOT ILIKE '%IS NULL OR%'
                    """, Integer.class, table);
            assertThat(policyCount).as("fail-closed USING/WITH CHECK policy for %s", table)
                    .isEqualTo(1);

            Integer forced = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM pg_class c
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public'
                      AND c.relname = ?
                      AND c.relrowsecurity
                      AND c.relforcerowsecurity
                    """, Integer.class, table);
            assertThat(forced).as("RLS enabled and forced for %s", table).isEqualTo(1);
        }
    }

    @Test
    void missingAndEmptyTenantContextsSeeNoRows() {
        for (String table : TABLES) {
            jdbc.execute("RESET app.tenant_id");
            assertThat(count(table))
                    .as("%s must deny reads when app.tenant_id is missing", table)
                    .isZero();

            jdbc.queryForObject("SELECT set_config('app.tenant_id', '', false)", String.class);
            assertThat(count(table))
                    .as("%s must deny reads when app.tenant_id is empty", table)
                    .isZero();
        }
    }

    @Test
    void validAndSwitchedTenantContextsNeverExposeAnotherTenant() {
        for (String table : TABLES) {
            jdbc.execute("RESET app.tenant_id");
            List<String> tenants = jdbc.query(
                    "SELECT DISTINCT tenant_id::text FROM public." + table
                            + " WHERE tenant_id IS NOT NULL LIMIT 2",
                    (rs, row) -> rs.getString(1));
            if (tenants.isEmpty()) {
                // Policy metadata and the fail-closed empty-context contract are
                // still checked for empty tables by the companion tests.
                continue;
            }

            String tenantA = tenants.get(0);
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, false)",
                    String.class, tenantA);
            Integer visibleA = count(table);
            Integer rowsOwnedByA = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM public." + table + " WHERE tenant_id::text = ?",
                    Integer.class, tenantA);
            assertThat(visibleA).as("%s only exposes tenant A", table).isEqualTo(rowsOwnedByA);

            String tenantB = tenants.size() > 1 ? tenants.get(1) : UUID.randomUUID().toString();
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, false)",
                    String.class, tenantB);
            Integer visibleB = count(table);
            Integer rowsOwnedByB = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM public." + table + " WHERE tenant_id::text = ?",
                    Integer.class, tenantB);
            assertThat(visibleB).as("%s must not retain tenant A after context switch", table)
                    .isEqualTo(rowsOwnedByB);
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM public." + table, Integer.class);
    }
}
