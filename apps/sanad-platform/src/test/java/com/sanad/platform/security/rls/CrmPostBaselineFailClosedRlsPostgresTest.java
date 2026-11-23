package com.sanad.platform.security.rls;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL Direct contract for the post-baseline CRM tenant RLS policies.
 *
 * <p>Proves the effective isolation behavior with REAL tenant rows seeded into
 * every one of the nine protected tables — not only policy metadata:
 * <ul>
 *   <li>policy metadata: forced RLS + fail-closed USING/WITH CHECK on all nine tables;</li>
 *   <li>missing and empty {@code app.tenant_id} see ZERO rows although real rows exist;</li>
 *   <li>valid context exposes exactly that tenant's rows in both directions (A vs B);</li>
 *   <li>cross-tenant UPDATE and DELETE are silently row-filtered to zero rows;</li>
 *   <li>cross-tenant INSERT is rejected by WITH CHECK (SQLSTATE 42501);</li>
 *   <li>switching {@code app.tenant_id} inside one transaction flips visibility
 *       transaction-locally and never leaks the previous tenant's rows.</li>
 * </ul>
 *
 * <p>Background jobs must set an explicit trusted tenant context; this test
 * deliberately defines no bypass.
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

    /** Row ids per tenant per table, populated by {@link #seedRealTenantRows()}. */
    private final Map<String, Map<String, UUID>> rowIds = new HashMap<>();

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
        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        // JDBC_URL is the disposable isolated migration-test database, never the base URL.
        flyway.clean();
        flyway.migrate();
        flyway.validate();
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(JDBC_URL, USER, PASSWORD, true));
        seedRealTenantRows();
    }

    /**
     * Seeds REAL rows for two distinct tenants into every one of the nine
     * protected tables through the same fail-closed policies the application
     * uses (session-scoped {@code app.tenant_id} set per tenant, inserts flow
     * through the WITH CHECK predicate).
     */
    private void seedRealTenantRows() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        jdbc.update("INSERT INTO public.tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', now(), now())",
                tenantA, "RLS-Tenant-A", "rls-tenant-a-" + tenantA);
        jdbc.update("INSERT INTO public.tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', now(), now())",
                tenantB, "RLS-Tenant-B", "rls-tenant-b-" + tenantB);

        var txManager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource());
        var txTemplate = new org.springframework.transaction.support.TransactionTemplate(txManager);
        for (String tenant : new String[] {tenantA.toString(), tenantB.toString()}) {
            UUID tenantUuid = UUID.fromString(tenant);
            txTemplate.executeWithoutResult(status -> {
            java.util.Map<String, Object> slot = jdbc.queryForMap(
                    "SELECT s.ctx_txid AS txid, s.ctx_pid AS pid FROM public.app_tenant_ctx_slot() s");
            String mac = com.sanad.platform.test.TenantCtxTestSupport.fixtureSigner().macFor(tenantUuid,
                    String.valueOf(slot.get("txid")), ((Number) slot.get("pid")).intValue());
            jdbc.queryForObject("SELECT public.app_bind_tenant_ctx(?::uuid, ?)",
                    Boolean.class, tenant, mac);
            UUID staff = UUID.randomUUID();
            UUID team = UUID.randomUUID();
            UUID service = UUID.randomUUID();
            UUID templateId = UUID.randomUUID();
            UUID rowId = UUID.randomUUID();
            UUID actor = UUID.randomUUID();

            jdbc.update("""
                    INSERT INTO public.crm_shift_templates
                      (id, tenant_id, name, start_time, end_time, days_of_week,
                       created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'MONDAY', ?, ?, now(), now())
                    """, templateId, UUID.fromString(tenant), "Template-" + tenant,
                    LocalTime.of(8, 0), LocalTime.of(16, 0), actor, actor);
            putRow("crm_shift_templates", tenant, templateId);

            jdbc.update("""
                    INSERT INTO public.crm_shift_assignments
                      (id, tenant_id, team_id, staff_id, shift_template_id,
                       start_date, end_date, created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                    """, rowId, UUID.fromString(tenant), team, staff, templateId,
                    LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actor, actor);
            putRow("crm_shift_assignments", tenant, rowId);

            rowId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO public.crm_service_assignments
                      (id, tenant_id, team_id, service_id, created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, now(), now())
                    """, rowId, UUID.fromString(tenant), team, service, actor, actor);
            putRow("crm_service_assignments", tenant, rowId);

            rowId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO public.crm_staff_skills
                      (id, tenant_id, staff_id, skill_name, level, proficiency,
                       created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, 'Java', 'BEGINNER', 30, ?, ?, now(), now())
                    """, rowId, UUID.fromString(tenant), staff, actor, actor);
            putRow("crm_staff_skills", tenant, rowId);

            rowId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO public.crm_staff_availability
                      (id, tenant_id, staff_id, type, start_date, end_date,
                       created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, 'AVAILABLE', ?, ?, ?, ?, now(), now())
                    """, rowId, UUID.fromString(tenant), staff,
                    LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 11), actor, actor);
            putRow("crm_staff_availability", tenant, rowId);

            rowId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO public.crm_workload_assignments
                      (id, tenant_id, staff_id, estimated_hours, start_date,
                       created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, 40, ?, ?, ?, now(), now())
                    """, rowId, UUID.fromString(tenant), staff,
                    LocalDate.of(2026, 8, 10), actor, actor);
            putRow("crm_workload_assignments", tenant, rowId);

            rowId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO public.crm_capacity_plans
                      (id, tenant_id, team_id, period_start, period_end, max_capacity,
                       created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 100, ?, ?, now(), now())
                    """, rowId, UUID.fromString(tenant), team,
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), actor, actor);
            putRow("crm_capacity_plans", tenant, rowId);

            rowId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO public.crm_cases (id, tenant_id, subject)
                    VALUES (?, ?, ?)
                    """, rowId, UUID.fromString(tenant), "Case-" + tenant);
            putRow("crm_cases", tenant, rowId);

            rowId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO public.crm_email_logs
                      (id, tenant_id, from_address, to_address, subject)
                    VALUES (?, ?, 'noreply@sanad.test', 'ops@sanad.test', ?)
                    """, rowId, UUID.fromString(tenant), "Email-" + tenant);
            putRow("crm_email_logs", tenant, rowId);
            });
        }
    }

    private void putRow(String table, String tenantId, UUID rowId) {
        rowIds.computeIfAbsent(tenantId, k -> new HashMap<>()).put(table, rowId);
    }

    /** First seeded tenant id (tenant A), insertion-ordered. */
    private String tenantA() {
        return rowIds.keySet().iterator().next();
    }

    /** Second seeded tenant id (tenant B), insertion-ordered. */
    private String tenantB() {
        return rowIds.keySet().stream().skip(1).findFirst().orElseThrow();
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
                      AND qual ILIKE '%app_tenant_ctx_text()%'
                      AND qual NOT ILIKE '%IS NULL OR%'
                      AND with_check ILIKE '%app_tenant_ctx_text()%'
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
            assertThat(countOwnedBy(table, tenantA()))
                    .as("seed precondition: real rows exist in %s for tenant A", table)
                    .isPositive();
            assertThat(countOwnedBy(table, tenantB()))
                    .as("seed precondition: real rows exist in %s for tenant B", table)
                    .isPositive();

            // UNBOUND transaction (no bind): the raw GUC is untrusted.
            Integer unbound = txTemplateForSeeding().execute(status -> count(table));
            assertThat(unbound)
                    .as("%s must deny reads when app.tenant_id is missing even though rows exist", table)
                    .isZero();

            // Forged empty context + forged mac: HMAC re-verification fails.
            Integer forged = txTemplateForSeeding().execute(status -> {
                jdbc.execute("SELECT set_config('app.tenant_id', '', true)");
                jdbc.execute("SELECT set_config('app.ctx_mac', 'deadbeef', true)");
                return count(table);
            });
            assertThat(forged)
                    .as("%s must deny reads when app.tenant_id is empty even though rows exist", table)
                    .isZero();
        }
    }

    @Test
    void validAndSwitchedTenantContextsNeverExposeAnotherTenant() {
        for (String table : TABLES) {
            UUID rowA = rowIds.get(tenantA()).get(table);
            UUID rowB = rowIds.get(tenantB()).get(table);

            // Under A's signed bind: visible total equals owned-by-A count, is
            // positive, and the seeded A row is visible while B's is invisible.
            boundAs(tenantA(), () -> {
                assertThat(count(table))
                        .as("%s exposes exactly tenant A's seeded rows under A's context", table)
                        .isEqualTo(countOwnedUnderCurrentContext(table, tenantA()))
                        .as("%s must actually contain tenant A rows (real data, not metadata-only)", table)
                        .isPositive();
                assertThat(countById(table, rowA)).as("%s row of A is visible to A", table).isEqualTo(1);
                assertThat(countById(table, rowB)).as("%s row of B is invisible to A", table).isZero();
            });

            // Switch to B (a fresh bound transaction): A's rows disappear.
            boundAs(tenantB(), () -> {
                assertThat(count(table))
                        .as("%s must not retain tenant A rows after switching to B", table)
                        .isEqualTo(countOwnedUnderCurrentContext(table, tenantB()))
                        .isPositive();
                assertThat(countById(table, rowB)).as("%s row of B is visible to B", table).isEqualTo(1);
                assertThat(countById(table, rowA)).as("%s row of A must be invisible to B", table).isZero();
            });
        }
    }

    @Test
    void crossTenantWriteOperationsAreDenied() {
        String table = "crm_cases";
        UUID rowA = rowIds.get(tenantA()).get(table);
        UUID rowB = rowIds.get(tenantB()).get(table);

        // Cross-tenant UPDATE/DELETE are row-filtered to zero rows under A's bind.
        boundAs(tenantA(), () -> {
            int updated = jdbc.update(
                    "UPDATE public.crm_cases SET subject = 'hijacked' WHERE id = ?", rowB);
            assertThat(updated).as("cross-tenant UPDATE affects zero rows").isZero();

            int deleted = jdbc.update("DELETE FROM public.crm_cases WHERE id = ?", rowB);
            assertThat(deleted).as("cross-tenant DELETE affects zero rows").isZero();

            // Cross-tenant INSERT violates WITH CHECK (fail-closed, 42501);
            // Spring translates the PSQLException, so assert on the root cause.
            UUID smuggled = UUID.randomUUID();
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO public.crm_cases (id, tenant_id, subject) VALUES (?, ?, 'smuggled')",
                    smuggled, UUID.fromString(tenantB())))
                    .as("cross-tenant INSERT must be rejected by WITH CHECK")
                    .isInstanceOf(RuntimeException.class)
                    .hasRootCauseInstanceOf(PSQLException.class)
                    .hasStackTraceContaining("row-level security policy");
        });

        // Row of B still intact and owned by B; B's bind still sees only B.
        boundAs(tenantB(), () -> {
            String subjectB = jdbc.queryForObject(
                    "SELECT subject FROM public.crm_cases WHERE id = ?", String.class, rowB);
            assertThat(subjectB).as("tenant B row unmodified after A's cross-tenant attempts")
                    .isEqualTo("Case-" + tenantB());
        });
    }

    @Test
    void contextSwitchInsideTransactionFlipsVisibilityWithoutLeak() throws Exception {
        try (Connection connection = new SingleConnectionDataSource(JDBC_URL, USER, PASSWORD, false)
                .getConnection()) {
            connection.setAutoCommit(false);
            String table = "crm_workload_assignments";
            UUID rowA = rowIds.get(tenantA()).get(table);
            UUID rowB = rowIds.get(tenantB()).get(table);

            setTenant(connection, tenantA());
            assertThat(visibleInTx(connection, table, rowA)).isEqualTo(1);
            assertThat(visibleInTx(connection, table, rowB)).isZero();

            // Transaction-local switch to B: A's row disappears immediately.
            setTenant(connection, tenantB());
            assertThat(visibleInTx(connection, table, rowA)).isZero();
            assertThat(visibleInTx(connection, table, rowB)).isEqualTo(1);

            // Back to A inside the SAME transaction: B's row disappears immediately.
            setTenant(connection, tenantA());
            assertThat(visibleInTx(connection, table, rowB)).isZero();
            assertThat(visibleInTx(connection, table, rowA)).isEqualTo(1);

            connection.rollback();
        }
    }

    /** G1 P0-B: the trusted context is the SIGNED BIND protocol (V20261124_1). */
    private void setTenant(Connection connection, String tenantId) throws Exception {
        String txid;
        int pid;
        try (PreparedStatement slot = connection.prepareStatement(
                "SELECT s.ctx_txid, s.ctx_pid FROM public.app_tenant_ctx_slot() s");
             ResultSet rs = slot.executeQuery()) {
            rs.next();
            txid = rs.getString(1);
            pid = rs.getInt(2);
        }
        String mac = com.sanad.platform.test.TenantCtxTestSupport.fixtureSigner().macFor(
                UUID.fromString(tenantId), txid, pid);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT public.app_bind_tenant_ctx(?::uuid, ?)")) {
            ps.setString(1, tenantId);
            ps.setString(2, mac);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
            }
        }
    }

    private int visibleInTx(Connection connection, String table, UUID rowId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM public." + table + " WHERE id = ?")) {
            ps.setObject(1, rowId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM public." + table, Integer.class);
    }

    /** Count rows owned by the tenant, under the CURRENTLY SET context (no side effects). */
    private int countOwnedUnderCurrentContext(String table, String tenantId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM public." + table + " WHERE tenant_id::text = ?",
                Integer.class, tenantId);
    }

    private int countById(String table, UUID rowId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM public." + table + " WHERE id = ?", Integer.class, rowId);
    }

    private int countOwnedBy(String table, String tenantId) {
        // The fail-closed policy filters even diagnostic COUNT queries, so the
        // count must run inside the owning tenant's own context (signed bind).
        return boundAs(tenantId, () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM public." + table + " WHERE tenant_id::text = ?",
                Integer.class, tenantId));
    }

    /** G1 P0-B signed-bind block: bind then run, one transaction. */
    private void boundAs(String tenant, Runnable body) {
        txTemplateForSeeding().executeWithoutResult(status -> {
            bindSigned(tenant);
            body.run();
        });
    }

    private <T> T boundAs(String tenant, java.util.function.Supplier<T> body) {
        return txTemplateForSeeding().execute(status -> {
            bindSigned(tenant);
            return body.get();
        });
    }

    private org.springframework.transaction.support.TransactionTemplate txTemplateForSeeding() {
        var txManager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource());
        return new org.springframework.transaction.support.TransactionTemplate(txManager);
    }

    private void bindSigned(String tenant) {
        UUID tenantUuid = UUID.fromString(tenant);
        java.util.Map<String, Object> slot = jdbc.queryForMap(
                "SELECT s.ctx_txid AS txid, s.ctx_pid AS pid FROM public.app_tenant_ctx_slot() s");
        String mac = com.sanad.platform.test.TenantCtxTestSupport.fixtureSigner().macFor(tenantUuid,
                String.valueOf(slot.get("txid")), ((Number) slot.get("pid")).intValue());
        jdbc.queryForObject("SELECT public.app_bind_tenant_ctx(?::uuid, ?)",
                Boolean.class, tenant, mac);
    }
}
