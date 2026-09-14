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

    // Repository truth: G1 chain renumbered from 20260908.1-.3 to
    // 20260908.2-.4 so main's workflow incident optimistic lock keeps
    // 20260908.1 (forward-only, no out-of-order, no history rewrite).
    static final String G1_SCHEMA_VERSION = "20260908.2";
    static final String G1_RLS_VERSION = "20260908.3";
    static final String G1_SEED_VERSION = "20260908.4";

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

    // ==================== T7.12 — offer approval correlation schema ====================

    static final String T7_CORRELATION_VERSION = "20260914.1";

    // TEST_ALIGNMENT_REASON = forward-only defect-fix migration (T7 closure §10/T7-A26):
    // V20260914_2 rebuilds the engine idempotency index with NULLS NOT DISTINCT.
    static final String T7_IDEMPOTENCY_NULLS_NOT_DISTINCT_VERSION = "20260914.2";

    @Test
    void t7WorkflowIdempotencyIndexEnforcesNullTriggerType() {
        flyway(null).migrate();
        List<String> indexDefs = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'workflow_instances' "
                        + "AND indexname = 'uq_wf_instances_idempotency'", String.class);
        assertThat(indexDefs).hasSize(1);
        assertThat(indexDefs.get(0))
                .as("T7-A26: the engine idempotency index is NULLS NOT DISTINCT so the same "
                        + "idempotency key can never start a second instance for the NULL "
                        + "trigger_type rows the Y2 adapters produce")
                .containsIgnoringCase("UNIQUE")
                .containsIgnoringCase("NULLS NOT DISTINCT")
                .contains("idempotency_key")
                .contains("WHERE");
    }

    @Test
    void t7OfferCorrelationColumnsExist() {
        flyway(null).migrate();
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'hr_offers'", String.class);
        assertThat(columns).as("T7.12: workflow correlation/reference + version reference columns")
                .contains("current_version_id", "pending_offer_version_id",
                        "pending_workflow_instance_id", "pending_workflow_definition_version_id");
        List<String> openingColumns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'hr_job_openings'",
                String.class);
        assertThat(openingColumns).as("gate §14: opening approval correlation columns")
                .contains("pending_workflow_instance_id", "pending_workflow_definition_version_id");
        List<String> versionColumns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'hr_offer_versions'",
                String.class);
        assertThat(versionColumns).as("gate §4: successor-chain predecessor linkage")
                .contains("predecessor_version_id");
    }

    @Test
    void t7OfferVersionTenantSafeIdentityConstraintExists() {
        flyway(null).migrate();
        Integer idTenantUnique = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'hr_offer_versions'::regclass "
                        + "AND contype = 'u' AND pg_get_constraintdef(oid) ILIKE '%id%tenant_id%'",
                Integer.class);
        assertThat(idTenantUnique)
                .as("T7.12: UNIQUE (id, tenant_id) enables tenant-safe composite FK references")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void t7OneOpenApprovalPerOfferVersionPartialIndexExists() {
        flyway(null).migrate();
        List<String> indexDefs = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'hr_offers' "
                        + "AND indexname = 'uq_hr_offers_one_open_approval_per_version'", String.class);
        assertThat(indexDefs).hasSize(1);
        assertThat(indexDefs.get(0))
                .as("gate §5: one OPEN workflow approval per (offer, offer_version)")
                .containsIgnoringCase("UNIQUE").contains("pending_offer_version_id").contains("WHERE");
        List<String> openingIdx = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'hr_job_openings' "
                        + "AND indexname = 'uq_hr_job_openings_one_open_approval'", String.class);
        assertThat(openingIdx).hasSize(1);
        assertThat(openingIdx.get(0)).containsIgnoringCase("UNIQUE").contains("pending_workflow_instance_id");
    }

    @Test
    void t7OneOpenApprovalPerOfferPartialIndexExists() {
        flyway(null).migrate();
        List<String> indexDefs = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'hr_offers' "
                        + "AND indexname = 'uq_hr_offers_one_open_approval'", String.class);
        assertThat(indexDefs).hasSize(1);
        assertThat(indexDefs.get(0))
                .as("§11.2 idempotency: ONE open approval per offer (partial unique index)")
                .containsIgnoringCase("UNIQUE")
                .contains("pending_workflow_instance_id")
                .contains("WHERE");
    }

    @Test
    void t7OfferVersionsAppendOnlyGuardIsInstalled() {
        flyway(null).migrate();
        Integer triggers = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_trigger "
                        + "WHERE tgrelid = 'hr_offer_versions'::regclass AND NOT tgisinternal "
                        + "AND tgname = 'trg_hr_offer_versions_append_only'",
                Integer.class);
        assertThat(triggers).as("T7.4/T7.12: DB-level append-only guard on offer versions").isEqualTo(1);

        // Behavioral probe on the disposable database: seeded rows survive
        // UPDATE/DELETE attempts (rows are intentionally left behind — the
        // disposable test_migration database is cleaned by other tests).
        UUID tenantId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                tenantId, "T7 Guard Tenant", "t7g-" + tenantId.toString().substring(0, 8));

        org.springframework.jdbc.datasource.DriverManagerDataSource ds =
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        url.replaceFirst("/sanad", "/test_migration"), username, password);
        javax.sql.DataSource tenantDs = (javax.sql.DataSource) java.lang.reflect.Proxy.newProxyInstance(
                javax.sql.DataSource.class.getClassLoader(), new Class<?>[]{javax.sql.DataSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        java.sql.Connection c = (java.sql.Connection) method.invoke(ds, args);
                        try (var st = c.createStatement()) {
                            st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', false)");
                        }
                        return c;
                    }
                    return method.invoke(ds, args);
                });
        org.springframework.jdbc.core.JdbcTemplate t = new org.springframework.jdbc.core.JdbcTemplate(tenantDs);
        UUID organizationId = UUID.randomUUID();
        t.update("INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())", organizationId, tenantId,
                "Org-" + organizationId.toString().substring(0, 8));
        t.update("INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'JOB-T7G', NOW())", UUID.randomUUID(), tenantId, organizationId);
        t.update("INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) "
                + "VALUES (?, ?, ?, 'OU-T7G', NOW())", UUID.randomUUID(), tenantId, organizationId);
        t.update("INSERT INTO hr_job_openings (id, tenant_id, opening_number, job_id, org_unit_id, state, requested_headcount) "
                + "SELECT gen_random_uuid(), ?, 'T7GUARD-1', j.id, u.id, 'OPEN', 1 FROM hr_jobs j "
                + "JOIN hr_org_units u ON u.tenant_id = j.tenant_id WHERE j.tenant_id = ?", tenantId, tenantId);
        t.update("INSERT INTO hr_candidates (id, tenant_id, candidate_number, display_name, pool_state, version) "
                + "VALUES (gen_random_uuid(), ?, 'CAND-T7G', 'T7 Guard Candidate', 'ACTIVE', 0)", tenantId);
        t.update("INSERT INTO hr_applications (id, tenant_id, candidate_id, job_opening_id, state, version) "
                + "SELECT gen_random_uuid(), ?, c.id, o.id, 'OFFER', 0 FROM hr_candidates c, hr_job_openings o "
                + "WHERE c.tenant_id = ? AND o.tenant_id = ? LIMIT 1", tenantId, tenantId, tenantId);
        t.update("INSERT INTO hr_offers (id, tenant_id, application_id, offer_number, state, version) "
                + "SELECT gen_random_uuid(), tenant_id, id, 'T7GUARD-1', 'DRAFT', 0 FROM hr_applications "
                + "WHERE tenant_id = ? LIMIT 1", tenantId);
        t.update("INSERT INTO hr_offer_versions (tenant_id, offer_id, version_number, contract_terms, compensation) "
                + "SELECT tenant_id, id, 1, '{}'::jsonb, '{}'::jsonb FROM hr_offers WHERE tenant_id = ? LIMIT 1", tenantId);
        String termsBefore = t.queryForObject(
                "SELECT contract_terms::text FROM hr_offer_versions WHERE tenant_id = ? LIMIT 1",
                String.class, tenantId);
        assertThatThrownByByJdbc(t, "UPDATE hr_offer_versions SET contract_terms = '{\"x\":1}'::jsonb "
                + "WHERE tenant_id = '" + tenantId + "'")
                .as("T7.4: UPDATE of an offer version must be rejected by the DB guard")
                .isInstanceOf(Exception.class);
        assertThatThrownByByJdbc(t, "DELETE FROM hr_offer_versions WHERE tenant_id = '" + tenantId + "'")
                .as("T7.4: DELETE of an offer version must be rejected by the DB guard")
                .isInstanceOf(Exception.class);
        assertThat(t.queryForObject(
                "SELECT contract_terms::text FROM hr_offer_versions WHERE tenant_id = ? LIMIT 1",
                String.class, tenantId)).isEqualTo(termsBefore);
    }

    private static org.assertj.core.api.AbstractThrowableAssert<?, ? extends Throwable> assertThatThrownByByJdbc(
            org.springframework.jdbc.core.JdbcTemplate t, String sql) {
        return org.assertj.core.api.Assertions.assertThatThrownBy(() -> t.execute(sql));
    }
}
