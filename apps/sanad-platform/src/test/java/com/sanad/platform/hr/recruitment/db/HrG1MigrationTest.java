package com.sanad.platform.hr.recruitment.db;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1-T2 — schema + RLS migration acceptance (design §5.2, §8.1).
 *
 * <p>Runs against the disposable {@code test_migration} database on
 * HOST-NATIVE PostgreSQL (no containers). Flyway history is append-only;
 * this test cleans ONLY the disposable test_migration database, never the
 * shared {@code sanad} database.</p>
 *
 * <p>RED contract (TDD): before the G1 migrations exist, every assertion
 * here fails because the tables/policies/seed are absent.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HrG1MigrationTest {

    static final String G1_SCHEMA_VERSION = "20260908.1";
    static final String G1_RLS_VERSION = "20260908.2";
    static final String G1_SEED_VERSION = "20260908.3";

    static final List<String> HR_G1_TABLES = List.of(
            "hr_job_openings",
            "hr_job_opening_periods",
            "hr_candidates",
            "hr_applications",
            "hr_application_stage_periods",
            "hr_interviews",
            "hr_interview_participants",
            "hr_interview_feedback",
            "hr_offers",
            "hr_offer_versions",
            "hr_hire_conversions",
            "hr_onboarding_plans",
            "hr_onboarding_checklist_templates",
            "hr_onboarding_checklists",
            "hr_onboarding_tasks");

    private JdbcTemplate jdbc;
    private String url;
    private String username;
    private String password;

    @BeforeAll
    void requirePostgreSql() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("HrG1MigrationTest");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is not available — skipping HrG1MigrationTest.");

        url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                "jdbc:postgresql://localhost:5432/sanad");
        username = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

        // Disposable sibling database so flyway.clean() never touches sanad.
        String testMigrationUrl = url.replaceFirst("/sanad", "/test_migration");
        MigrationTestSchemaSupport.ensureDatabase(testMigrationUrl, username, password);

        DriverManagerDataSource ds = new DriverManagerDataSource(testMigrationUrl, username, password);
        jdbc = new JdbcTemplate(ds);
    }

    private Flyway flyway(String targetVersion) {
        org.flywaydb.core.api.configuration.FluentConfiguration builder = Flyway.configure()
                .dataSource(url.replaceFirst("/sanad", "/test_migration"), username, password)
                .validateOnMigrate(false)
                // Flyway 10+ disables clean by default; the disposable
                // test_migration database is the one place it is legitimate.
                .cleanDisabled(false);
        if (targetVersion != null) {
            builder.target(org.flywaydb.core.api.MigrationVersion.fromVersion(targetVersion));
        }
        return builder.load();
    }

    private List<String> existingTables() {
        return jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
                String.class);
    }

    /**
     * JUnit default method order is unspecified and the CRM migration tests
     * share this disposable database (cleaning it to intermediate baselines).
     * Every test therefore first brings the schema to the latest state —
     * migrate() is idempotent, so this is a no-op when already current.
     */
    @org.junit.jupiter.api.BeforeEach
    void ensureFullyMigrated() {
        flyway(null).migrate();
    }

    @Test
    void migrationsApplyCleanAndCreateAllG1Tables() {
        // test_migration is the disposable scratch database — start from a
        // clean slate so this test is self-contained.
        flyway(null).clean();

        Flyway full = flyway(null);
        full.migrate();

        String latest = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version::text), '') FROM flyway_schema_history WHERE success = true",
                String.class);

        assertThat(existingTables())
                .as("all §5.2 G1 tables must exist after migration")
                .containsAll(HR_G1_TABLES);
        assertThat(latest)
                .as("Flyway history is append-only; G1 migrations are the new terminal state")
                .isGreaterThanOrEqualTo(G1_SEED_VERSION);
    }

    @Test
    void ledgerUniqueAndPartialActiveApplicationIndexExist() {
        assertThat(existingTables()).containsAll(List.of("hr_hire_conversions", "hr_applications"));

        Integer ledgerUnique = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes "
                        + "WHERE tablename = 'hr_hire_conversions' "
                        + "AND indexdef ILIKE '%UNIQUE%' AND indexdef ILIKE '%tenant_id%' AND indexdef ILIKE '%offer_id%'",
                Integer.class);
        assertThat(ledgerUnique)
                .as("§5.2: hr_hire_conversions UK(tenant_id, offer_id) — terminal-state ledger key")
                .isGreaterThanOrEqualTo(1);

        Integer partialActive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_index i "
                        + "JOIN pg_class c ON c.oid = i.indexrelid "
                        + "JOIN pg_class t ON t.oid = i.indrelid "
                        + "WHERE t.relname = 'hr_applications' AND i.indpred IS NOT NULL AND i.indisunique",
                Integer.class);
        assertThat(partialActive)
                .as("§5.2: partial unique index enforcing ONE active application per (candidate, opening)")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void rlsEnabledForcedWithTenantIsolationPolicyOnEveryG1Table() {
        assertThat(existingTables()).containsAll(HR_G1_TABLES);

        for (String table : HR_G1_TABLES) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT c.relrowsecurity AS enabled, c.relforcerowsecurity AS forced "
                            + "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                            + "WHERE n.nspname = 'public' AND c.relname = ?", table);
            assertThat(row.get("enabled"))
                    .as("RLS enabled on %s (§8.1)", table).isEqualTo(true);
            assertThat(row.get("forced"))
                    .as("FORCE ROW LEVEL SECURITY on %s — owner must not bypass (§8.1)", table).isEqualTo(true);

            Integer policies = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_policies WHERE tablename = ? AND policyname = 'tenant_isolation'",
                    Integer.class, table);
            assertThat(policies)
                    .as("tenant_isolation policy present on %s (G0 policy shape)", table)
                    .isEqualTo(1);
        }
    }

    @Test
    void seedCreatesGenericOnboardingChecklistTemplateForActiveTenants() throws Exception {
        // The seed is tenant-scoped (G0 V20260807_2 pattern): migrate to the
        // schema+RLS state, create an ACTIVE tenant, then migrate forward so
        // the seed migration sees it. Start clean so the upgrade flow is real.
        flyway(null).clean();

        Flyway preSeed = flyway(G1_RLS_VERSION);
        preSeed.migrate();

        assertThat(existingTables()).contains("hr_onboarding_checklist_templates");

        UUID tenantId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, 'HR G1 Seed Tenant', ?, 'ACTIVE', NOW(), NOW()) "
                        + "ON CONFLICT (id) DO NOTHING",
                tenantId, "hr-g1-seed-" + tenantId.toString().substring(0, 8));

        Flyway forward = flyway(null);
        forward.migrate();

        // FORCE RLS means any query without tenant context sees nothing by
        // design (fail closed). DriverManagerDataSource opens a NEW
        // connection per call, so SET + SELECT must share ONE connection.
        String testMigrationUrl = url.replaceFirst("/sanad", "/test_migration");
        try (java.sql.Connection ctx = DriverManager.getConnection(testMigrationUrl, username, password)) {
            try (java.sql.Statement st = ctx.createStatement()) {
                st.execute("SET app.tenant_id = '" + tenantId + "'");
            }
            Integer templates;
            try (java.sql.PreparedStatement ps = ctx.prepareStatement(
                    "SELECT COUNT(*) FROM hr_onboarding_checklist_templates WHERE tenant_id = ?::uuid")) {
                ps.setString(1, tenantId.toString());
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    templates = rs.getInt(1);
                }
            }
            assertThat(templates)
                    .as("§17: one generic onboarding checklist template seeded per tenant")
                    .isEqualTo(1);

            Map<String, Object> template;
            try (java.sql.PreparedStatement ps = ctx.prepareStatement(
                    "SELECT code, name, version, is_active FROM hr_onboarding_checklist_templates "
                            + "WHERE tenant_id = ?::uuid")) {
                ps.setString(1, tenantId.toString());
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    final Map<String, Object> row = new java.util.HashMap<>();
                    row.put("code", rs.getString("code"));
                    row.put("name", rs.getString("name"));
                    row.put("version", rs.getInt("version"));
                    row.put("is_active", rs.getBoolean("is_active"));
                    template = row;
                }
            }
            assertThat(template.get("version")).isEqualTo(1);
            assertThat(template.get("is_active")).isEqualTo(true);
        }
    }
}
