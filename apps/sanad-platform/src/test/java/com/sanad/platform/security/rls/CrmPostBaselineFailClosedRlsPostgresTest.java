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
import java.sql.SQLException;
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
 *   <li>policy metadata: forced RLS + fail-closed USING/WITH CHECK (reading the
 *       HMAC-validated {@code app_tenant_ctx_text()} context) on all nine tables;</li>
 *   <li>missing and empty tenant context see ZERO rows although real rows exist;</li>
 *   <li>a valid SIGNED context exposes exactly that tenant's rows in both
 *       directions (A vs B);</li>
 *   <li>cross-tenant UPDATE and DELETE are silently row-filtered to zero rows;</li>
 *   <li>cross-tenant INSERT is rejected by WITH CHECK (SQLSTATE 42501);</li>
 *   <li>re-binding the signed context inside one transaction flips visibility
 *       transaction-locally and never leaks the previous tenant's rows.</li>
 * </ul>
 *
 * <p>G1 F-INT signed-context contract: after V20261124_2 the enforced policies
 * read {@code public.app_tenant_ctx_text()} — a raw {@code app.tenant_id} write
 * without a valid HMAC mac does NOT establish a context (that is the RT-01
 * fail-closed guarantee). Context establishment in this test therefore runs the
 * SAME binding protocol the application uses (slot → HMAC mint →
 * {@code app_bind_tenant_ctx}) with the DOCUMENTED FIXTURE KEY, which matches
 * the ACTIVE key the chain provisions on the disposable migration-fixture
 * topology. All isolation assertions are unchanged by this harness upgrade.
 * Background jobs must set an explicit trusted tenant context; this test
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
    /**
     * Documented fixture key provisioned by V20261124_2 on the disposable
     * migration-fixture topology (repository-published BY DESIGN; NOT
     * production evidence).
     */
    private static final String FIXTURE_KEY_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
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
     * uses — each tenant's rows are inserted inside a SIGNED-BIND transaction
     * (the WITH CHECK predicate validates against the HMAC-verified context).
     */
    private void seedRealTenantRows() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        // Tenant registry rows are infrastructure — the tenants table is not
        // FORCE RLS; seeding it needs no tenant scope.
        jdbc.update("INSERT INTO public.tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', now(), now())",
                tenantA, "RLS-Tenant-A", "rls-tenant-a-" + tenantA);
        jdbc.update("INSERT INTO public.tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', now(), now())",
                tenantB, "RLS-Tenant-B", "rls-tenant-b-" + tenantB);

        seedTenantRows(tenantA);
        seedTenantRows(tenantB);
    }

    private void seedTenantRows(UUID tenant) {
        try (Connection conn = txConnection()) {
            conn.setAutoCommit(false);
            bindSignedTenant(conn, tenant);
            UUID staff = UUID.randomUUID();
            UUID team = UUID.randomUUID();
            UUID service = UUID.randomUUID();
            UUID templateId = UUID.randomUUID();
            UUID rowId = UUID.randomUUID();
            UUID actor = UUID.randomUUID();

            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.crm_shift_templates
                      (id, tenant_id, name, start_time, end_time, days_of_week,
                       created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'MONDAY', ?, ?, now(), now())
                    """)) {
                ps.setObject(1, templateId);
                ps.setObject(2, tenant);
                ps.setString(3, "Template-" + tenant);
                ps.setObject(4, LocalTime.of(8, 0));
                ps.setObject(5, LocalTime.of(16, 0));
                ps.setObject(6, actor);
                ps.setObject(7, actor);
                ps.executeUpdate();
            }
            putRow(tenant, "crm_shift_templates", templateId);

            rowId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.crm_shift_assignments
                      (id, tenant_id, team_id, staff_id, shift_template_id,
                       start_date, end_date, created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                    """)) {
                ps.setObject(1, rowId);
                ps.setObject(2, tenant);
                ps.setObject(3, team);
                ps.setObject(4, staff);
                ps.setObject(5, templateId);
                ps.setObject(6, LocalDate.of(2026, 8, 10));
                ps.setObject(7, LocalDate.of(2026, 8, 14));
                ps.setObject(8, actor);
                ps.setObject(9, actor);
                ps.executeUpdate();
            }
            putRow(tenant, "crm_shift_assignments", rowId);

            rowId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.crm_service_assignments
                      (id, tenant_id, team_id, service_id, created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, now(), now())
                    """)) {
                ps.setObject(1, rowId);
                ps.setObject(2, tenant);
                ps.setObject(3, team);
                ps.setObject(4, service);
                ps.setObject(5, actor);
                ps.setObject(6, actor);
                ps.executeUpdate();
            }
            putRow(tenant, "crm_service_assignments", rowId);

            rowId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.crm_staff_skills
                      (id, tenant_id, staff_id, skill_name, level, proficiency,
                       created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, 'Java', 'BEGINNER', 30, ?, ?, now(), now())
                    """)) {
                ps.setObject(1, rowId);
                ps.setObject(2, tenant);
                ps.setObject(3, staff);
                ps.setObject(4, actor);
                ps.setObject(5, actor);
                ps.executeUpdate();
            }
            putRow(tenant, "crm_staff_skills", rowId);

            rowId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.crm_staff_availability
                      (id, tenant_id, staff_id, type, start_date, end_date,
                       created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, 'AVAILABLE', ?, ?, ?, ?, now(), now())
                    """)) {
                ps.setObject(1, rowId);
                ps.setObject(2, tenant);
                ps.setObject(3, staff);
                ps.setObject(4, LocalDate.of(2026, 8, 10));
                ps.setObject(5, LocalDate.of(2026, 8, 11));
                ps.setObject(6, actor);
                ps.setObject(7, actor);
                ps.executeUpdate();
            }
            putRow(tenant, "crm_staff_availability", rowId);

            rowId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.crm_workload_assignments
                      (id, tenant_id, staff_id, estimated_hours, start_date,
                       created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, 40, ?, ?, ?, now(), now())
                    """)) {
                ps.setObject(1, rowId);
                ps.setObject(2, tenant);
                ps.setObject(3, staff);
                ps.setObject(4, LocalDate.of(2026, 8, 10));
                ps.setObject(5, actor);
                ps.setObject(6, actor);
                ps.executeUpdate();
            }
            putRow(tenant, "crm_workload_assignments", rowId);

            rowId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.crm_capacity_plans
                      (id, tenant_id, team_id, period_start, period_end, max_capacity,
                       created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 100, ?, ?, now(), now())
                    """)) {
                ps.setObject(1, rowId);
                ps.setObject(2, tenant);
                ps.setObject(3, team);
                ps.setObject(4, LocalDate.of(2026, 8, 1));
                ps.setObject(5, LocalDate.of(2026, 8, 31));
                ps.setObject(6, actor);
                ps.setObject(7, actor);
                ps.executeUpdate();
            }
            putRow(tenant, "crm_capacity_plans", rowId);

            rowId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.crm_cases (id, tenant_id, subject)
                    VALUES (?, ?, ?)
                    """)) {
                ps.setObject(1, rowId);
                ps.setObject(2, tenant);
                ps.setString(3, "Case-" + tenant);
                ps.executeUpdate();
            }
            putRow(tenant, "crm_cases", rowId);

            rowId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.crm_email_logs
                      (id, tenant_id, from_address, to_address, subject)
                    VALUES (?, ?, 'noreply@sanad.test', 'ops@sanad.test', ?)
                    """)) {
                ps.setObject(1, rowId);
                ps.setObject(2, tenant);
                ps.setString(3, "Email-" + tenant);
                ps.executeUpdate();
            }
            putRow(tenant, "crm_email_logs", rowId);

            conn.commit();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Seeding tenant rows under the signed context failed: " + e.getMessage(), e);
        }
    }

    private void putRow(UUID tenantId, String table, UUID rowId) {
        rowIds.computeIfAbsent(tenantId.toString(), k -> new HashMap<>()).put(table, rowId);
    }

    /** First seeded tenant id (tenant A), insertion-ordered. */
    private String tenantA() {
        return rowIds.keySet().iterator().next();
    }

    /** Second seeded tenant id (tenant B), insertion-ordered. */
    private String tenantB() {
        return rowIds.keySet().stream().skip(1).findFirst().orElseThrow();
    }

    // ── Signed-binding harness ───────────────────────────────────────────

    private Connection txConnection() throws SQLException {
        return new SingleConnectionDataSource(JDBC_URL, USER, PASSWORD, false).getConnection();
    }

    /**
     * Executes the application-side signed binding protocol on the given
     * connection: slot (txid/pid) → HMAC-SHA256 mint → app_bind_tenant_ctx.
     */
    private static void bindSignedTenant(Connection conn, UUID tenantId) throws SQLException {
        String txid;
        int pid;
        try (var st = conn.createStatement();
                var rs = st.executeQuery("SELECT ctx_txid, ctx_pid FROM public.app_tenant_ctx_slot()")) {
            rs.next();
            txid = rs.getString(1);
            pid = rs.getInt(2);
        }
        String mac = fixtureMac(tenantId, txid, pid);
        try (var ps = conn.prepareStatement("SELECT public.app_bind_tenant_ctx(?::uuid, ?)")) {
            ps.setString(1, tenantId.toString());
            ps.setString(2, mac);
            try (var rs = ps.executeQuery()) {
                rs.next();
                if (!rs.getBoolean(1)) {
                    throw new SQLException("signed bind rejected — no ACTIVE key validates the mac");
                }
            }
        }
    }

    private static String fixtureMac(UUID tenantId, String txid, int pid) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    java.util.HexFormat.of().parseHex(FIXTURE_KEY_HEX), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(
                    mac.doFinal((tenantId + ":" + txid + ":" + pid)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("fixture mac minting failed", e);
        }
    }

    private int countInTx(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM public." + table);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countByIdInTx(Connection conn, String table, UUID rowId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM public." + table + " WHERE id = ?")) {
            ps.setObject(1, rowId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countOwnedInTx(Connection conn, String table, String tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM public." + table + " WHERE tenant_id::text = ?")) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Seed-precondition verification read inside the owning tenant's signed context. */
    private int countOwnedBy(String table, String tenantId) {
        try (Connection conn = txConnection()) {
            conn.setAutoCommit(false);
            bindSignedTenant(conn, UUID.fromString(tenantId));
            int owned = countOwnedInTx(conn, table, tenantId);
            conn.rollback();
            return owned;
        } catch (SQLException e) {
            throw new IllegalStateException("verification read failed: " + e.getMessage(), e);
        }
    }

    // ── Contract tests ───────────────────────────────────────────────────

    @Test
    void everyTargetTableHasForcedFailClosedTenantPolicy() {
        for (String table : TABLES) {
            // V20261124_2 expression contract: the fail-closed policy reads the
            // HMAC-validated context (app_tenant_ctx_text) — a raw GUC without
            // a valid mac never satisfies it. The anti-permissive guard
            // (no IS NULL OR short-circuit) is unchanged.
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

            // (1) no context at all — fail closed
            try (Connection conn = txConnection()) {
                conn.setAutoCommit(false);
                assertThat(countInTx(conn, table))
                        .as("%s must deny reads when no tenant context is bound even though rows exist", table)
                        .isZero();
                conn.rollback();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }

            // (2) raw empty GUC without a mac — the RT-01 fail-closed guarantee
            try (Connection conn = txConnection()) {
                conn.setAutoCommit(false);
                try (var st = conn.createStatement()) {
                    st.execute("SELECT set_config('app.tenant_id', '', true)");
                }
                assertThat(countInTx(conn, table))
                        .as("%s must deny reads when app.tenant_id is empty even though rows exist", table)
                        .isZero();
                conn.rollback();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }

            // (3) raw FORGED tenant GUC without a mac — validator returns NULL
            try (Connection conn = txConnection()) {
                conn.setAutoCommit(false);
                try (var st = conn.createStatement()) {
                    st.execute("SELECT set_config('app.tenant_id', '" + tenantA() + "', true)");
                }
                assertThat(countInTx(conn, table))
                        .as("%s must deny reads under a forged raw tenant GUC without a signed mac", table)
                        .isZero();
                conn.rollback();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Test
    void validAndSwitchedTenantContextsNeverExposeAnotherTenant() {
        for (String table : TABLES) {
            UUID rowA = rowIds.get(tenantA()).get(table);
            UUID rowB = rowIds.get(tenantB()).get(table);
            try (Connection conn = txConnection()) {
                conn.setAutoCommit(false);

                // Under A's signed context: visible total equals owned-by-A count, is
                // positive, and the seeded A row is visible while the seeded B row is
                // invisible.
                bindSignedTenant(conn, UUID.fromString(tenantA()));
                assertThat(countInTx(conn, table))
                        .as("%s exposes exactly tenant A's seeded rows under A's context", table)
                        .isEqualTo(countOwnedInTx(conn, table, tenantA()))
                        .as("%s must actually contain tenant A rows (real data, not metadata-only)", table)
                        .isPositive();
                assertThat(countByIdInTx(conn, table, rowA)).as("%s row of A is visible to A", table).isEqualTo(1);
                assertThat(countByIdInTx(conn, table, rowB)).as("%s row of B is invisible to A", table).isZero();

                // Re-bind to B inside the same transaction: A's rows disappear
                // immediately (transaction-local re-scope, same protocol).
                bindSignedTenant(conn, UUID.fromString(tenantB()));
                assertThat(countInTx(conn, table))
                        .as("%s must not retain tenant A rows after switching to B", table)
                        .isEqualTo(countOwnedInTx(conn, table, tenantB()))
                        .isPositive();
                assertThat(countByIdInTx(conn, table, rowB)).as("%s row of B is visible to B", table).isEqualTo(1);
                assertThat(countByIdInTx(conn, table, rowA)).as("%s row of A must be invisible to B", table).isZero();

                conn.rollback();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Test
    void crossTenantWriteOperationsAreDenied() {
        String table = "crm_cases";
        UUID rowA = rowIds.get(tenantA()).get(table);
        UUID rowB = rowIds.get(tenantB()).get(table);

        try (Connection conn = txConnection()) {
            conn.setAutoCommit(false);
            bindSignedTenant(conn, UUID.fromString(tenantA()));

            // Cross-tenant UPDATE is row-filtered to zero rows.
            int updated;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE public.crm_cases SET subject = 'hijacked' WHERE id = ?")) {
                ps.setObject(1, rowB);
                updated = ps.executeUpdate();
            }
            assertThat(updated).as("cross-tenant UPDATE affects zero rows").isZero();

            // Cross-tenant DELETE is row-filtered to zero rows.
            int deleted;
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM public.crm_cases WHERE id = ?")) {
                ps.setObject(1, rowB);
                deleted = ps.executeUpdate();
            }
            assertThat(deleted).as("cross-tenant DELETE affects zero rows").isZero();

            // A's own row is untouched by A's cross-tenant attempts (asserted
            // BEFORE the intentional 42501 below, which aborts the transaction).
            assertThat(countByIdInTx(conn, table, rowA)).isEqualTo(1);

            // Cross-tenant INSERT violates WITH CHECK (fail-closed, SQLSTATE 42501)
            // — must be the LAST statement of the transaction: the rejection
            // aborts it (PostgreSQL semantics), which is itself the fail-closed
            // behavior under test.
            UUID smuggled = UUID.randomUUID();
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO public.crm_cases (id, tenant_id, subject) VALUES (?, ?, 'smuggled')")) {
                    ps.setObject(1, smuggled);
                    ps.setObject(2, UUID.fromString(tenantB()));
                    ps.executeUpdate();
                }
            }).as("cross-tenant INSERT must be rejected by WITH CHECK")
                    .isInstanceOf(SQLException.class)
                    .hasStackTraceContaining("row-level security policy");

            conn.rollback();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        // Row of B still intact and owned by B; B's signed context sees it unmodified.
        try (Connection conn = txConnection()) {
            conn.setAutoCommit(false);
            bindSignedTenant(conn, UUID.fromString(tenantB()));
            String subjectB;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT subject FROM public.crm_cases WHERE id = ?")) {
                ps.setObject(1, rowB);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    subjectB = rs.getString(1);
                }
            }
            assertThat(subjectB).as("tenant B row unmodified after A's cross-tenant attempts")
                    .isEqualTo("Case-" + tenantB());
            conn.rollback();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void contextSwitchInsideTransactionFlipsVisibilityWithoutLeak() throws Exception {
        try (Connection connection = txConnection()) {
            connection.setAutoCommit(false);
            String table = "crm_workload_assignments";
            UUID rowA = rowIds.get(tenantA()).get(table);
            UUID rowB = rowIds.get(tenantB()).get(table);

            bindSignedTenant(connection, UUID.fromString(tenantA()));
            assertThat(visibleInTx(connection, table, rowA)).isEqualTo(1);
            assertThat(visibleInTx(connection, table, rowB)).isZero();

            // Transaction-local switch to B: A's row disappears immediately.
            bindSignedTenant(connection, UUID.fromString(tenantB()));
            assertThat(visibleInTx(connection, table, rowA)).isZero();
            assertThat(visibleInTx(connection, table, rowB)).isEqualTo(1);

            // Back to A inside the SAME transaction: B's row disappears immediately.
            bindSignedTenant(connection, UUID.fromString(tenantA()));
            assertThat(visibleInTx(connection, table, rowB)).isZero();
            assertThat(visibleInTx(connection, table, rowA)).isEqualTo(1);

            connection.rollback();
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
}
