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
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL Direct contract for D6 Batch 1 (V20261028_2 —
 * SANAD-SEC-G1-D6-CLOSURE §5.4): fail-closed FORCE RLS on the identity
 * plane token tables {@code refresh_tokens} and {@code password_reset_tokens},
 * plus the pre-auth tenant discovery index contract.
 *
 * <p>Proves with REAL seeded tenant rows (not metadata only):
 * <ul>
 *   <li>forced fail-closed tenant_isolation policy on BOTH token tables —
 *       since V20261124_2 the policy reads the HMAC-validated
 *       {@code app_tenant_ctx_text()} (signed tenant-context binding,
 *       F-DB-07 / RT-01) and fails closed when no signed context is
 *       bound;</li>
 *   <li>{@code auth_token_tenant_index} exists and is intentionally NOT
 *       RLS'd (documented pre-auth discovery contract, mirrors
 *       {@code login_tenant_index});</li>
 *   <li>missing / empty / FORGED {@code app.tenant_id} GUCs see ZERO token
 *       rows although real rows exist — including for the table owner
 *       (FORCE RLS) — a raw GUC forgery cannot satisfy the HMAC
 *       re-verification;</li>
 *   <li>a properly SIGNED bound context exposes exactly that tenant's
 *       tokens in both directions (A vs B) for reads;</li>
 *   <li>cross-tenant UPDATE/DELETE are row-filtered to zero rows;</li>
 *   <li>cross-tenant INSERT is rejected by WITH CHECK (SQLSTATE 42501);</li>
 *   <li>the sync trigger mirrors INSERT/DELETE into the discovery index and
 *       keeps kinds separate;</li>
 *   <li>the application's pre-auth contract holds end-to-end: index lookup
 *       (no context) → tenant id → signed bind → scoped FOR UPDATE finds
 *       exactly the owning tenant's row and never another tenant's;</li>
 *   <li>the transaction-local signed bind resets at transaction end — a
 *       pooled connection reused for the next transaction sees ZERO rows
 *       (pool-reuse leak prevention);</li>
 *   <li>the runtime role {@code sanad} has NO {@code BYPASSRLS}.</li>
 * </ul>
 *
 * <h2>G1-i role topology (2026-11-25 contract review)</h2>
 * <p>The migration/fixture role (SPRING_DATASOURCE_USERNAME) applies the
 * chain and seeds rows; the PROBE role (SPRING_PROBE_USERNAME, default
 * {@code sanad}) is the restricted runtime identity that must NOT hold
 * BYPASSRLS and is subject to the enforced policies. Probes bind a signed
 * context per transaction with the documented fixture key provisioned by
 * V20261124_2 on the disposable topology (repository-published BY DESIGN;
 * this run is NOT production evidence).</p>
 */
class D6AuthTokenTenantRlsPostgresTest {
    private static final String BASE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    /** Restricted probe identity — the role whose RLS subject behavior is verified. */
    private static final String PROBE_USER = System.getenv().getOrDefault(
            "SPRING_PROBE_USERNAME", "sanad");
    private static final String PROBE_PASSWORD = System.getenv().getOrDefault(
            "SPRING_PROBE_PASSWORD", "");
    private static final String JDBC_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(BASE_URL);
    private static final java.util.List<String> TOKEN_TABLES =
            java.util.List.of("refresh_tokens", "password_reset_tokens");
    /**
     * Documented fixture key provisioned by V20261124_2 on the disposable
     * migration-fixture topology (repository-published BY DESIGN — see the
     * V20261124_1 header). NOT production evidence.
     */
    private static final String FIXTURE_KEY =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JdbcTemplate jdbc;

    private String tenantA;
    private String tenantB;
    private UUID userA;
    private UUID userB;
    private UUID refreshA;
    private UUID refreshB;
    private UUID resetA;
    private UUID resetB;

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                "D6AuthTokenTenantRlsPostgresTest");
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

    private void seedRealTenantRows() {
        tenantA = UUID.randomUUID().toString();
        tenantB = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO public.tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', now(), now())",
                UUID.fromString(tenantA), "D6-Tenant-A", "d6-tenant-a-" + tenantA);
        jdbc.update("INSERT INTO public.tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', now(), now())",
                UUID.fromString(tenantB), "D6-Tenant-B", "d6-tenant-b-" + tenantB);

        userA = seedUser(tenantA, "user-a");
        userB = seedUser(tenantB, "user-b");

        refreshA = seedRefreshToken(tenantA, userA, "d6-refresh-hash-A");
        refreshB = seedRefreshToken(tenantB, userB, "d6-refresh-hash-B");
        resetA = seedPasswordResetToken(tenantA, userA, "d6-reset-hash-A");
        resetB = seedPasswordResetToken(tenantB, userB, "d6-reset-hash-B");
    }

    // G1-REV2 re-verification fix: since V20261124_2 the identity plane
    // (users, refresh_tokens, password_reset_tokens) is FORCE RLS with the
    // signed fail-closed policy, so fixture seeding MUST go through the
    // same signed binding protocol the production application uses
    // (app_tenant_ctx_slot -> HMAC-SHA256 mint -> app_bind_tenant_ctx).
    // Acceptance assertions are UNCHANGED — only the seeding mechanism was
    // upgraded, exactly like the CrmIsolation/CrmPostBaseline harnesses
    // (RETEST-TRAIL items #9/#10). NOT a relaxation of any test criterion.
    private UUID seedUser(String tenant, String label) {
        UUID userId = UUID.randomUUID();
        try (Connection conn = new SingleConnectionDataSource(JDBC_URL, USER, PASSWORD, false).getConnection()) {
            conn.setAutoCommit(false);
            bindTx(conn, tenant);
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.users (id, tenant_id, email, display_name, status,
                                              password_hash, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', 'd6-test-hash', now(), now())
                    """)) {
                ps.setObject(1, userId);
                ps.setObject(2, UUID.fromString(tenant));
                ps.setString(3, "d6-" + label + "-" + userId + "@sanad.test");
                ps.setString(4, "D6 " + label);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (Exception e) {
            throw new IllegalStateException("signed-bound seeding failed for users", e);
        }
        return userId;
    }

    private UUID seedRefreshToken(String tenant, UUID userId, String tokenHash) {
        UUID id = UUID.randomUUID();
        try (Connection conn = new SingleConnectionDataSource(JDBC_URL, USER, PASSWORD, false).getConnection()) {
            conn.setAutoCommit(false);
            bindTx(conn, tenant);
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.refresh_tokens (id, tenant_id, user_id, token_hash,
                                                       status, expires_at, created_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', now() + interval '7 days', now())
                    """)) {
                ps.setObject(1, id);
                ps.setObject(2, UUID.fromString(tenant));
                ps.setObject(3, userId);
                ps.setString(4, tokenHash);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (Exception e) {
            throw new IllegalStateException("signed-bound seeding failed for refresh_tokens", e);
        }
        return id;
    }

    private UUID seedPasswordResetToken(String tenant, UUID userId, String tokenHash) {
        UUID id = UUID.randomUUID();
        try (Connection conn = new SingleConnectionDataSource(JDBC_URL, USER, PASSWORD, false).getConnection()) {
            conn.setAutoCommit(false);
            bindTx(conn, tenant);
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.password_reset_tokens (id, tenant_id, user_id, token_hash,
                                                       status, expires_at, created_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', now() + interval '30 minutes', now())
                    """)) {
                ps.setObject(1, id);
                ps.setObject(2, UUID.fromString(tenant));
                ps.setObject(3, userId);
                ps.setString(4, tokenHash);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (Exception e) {
            throw new IllegalStateException("signed-bound seeding failed for password_reset_tokens", e);
        }
        return id;
    }

    // ------------------------------------------------------------------
    // G1-i signed-context helpers (probe role + transaction-local bind)
    // ------------------------------------------------------------------

    /** Opens a connection for the RESTRICTED probe role (RLS applies to it). */
    private Connection probeConnection() throws Exception {
        Connection connection = new SingleConnectionDataSource(JDBC_URL, PROBE_USER, PROBE_PASSWORD, false)
                .getConnection();
        return connection;
    }

    /**
     * Binds a SIGNED tenant context for {@code tenant} inside the caller's
     * CURRENT transaction: the HMAC is computed over (tenant, txid, pid) of
     * the binding transaction itself, so the validator's re-verification
     * (same txid/pid) succeeds. Transaction-local by contract.
     */
    private void bindTx(Connection connection, String tenant) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT public.app_bind_tenant_ctx(?::uuid, "
                        + "encode(hmac((?::text||':'||txid_current()::text||':'||pg_backend_pid()::text)::bytea, "
                        + "decode(?,'hex'),'sha256'),'hex'))")) {
            ps.setObject(1, UUID.fromString(tenant));
            ps.setString(2, tenant);
            ps.setString(3, FIXTURE_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("bind returned").isTrue();
                assertThat(rs.getBoolean(1)).as("bind accepted for %s", tenant).isTrue();
            }
        }
    }

    private int countOn(Connection connection, String table) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM public." + table);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countByIdOn(Connection connection, String table, UUID id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM public." + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int visibleForUpdateOn(Connection connection, String table, UUID id) throws Exception {
        // FOR UPDATE cannot wrap an aggregate — count over the locked row-set.
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM (SELECT id FROM public." + table + " WHERE id = ? FOR UPDATE) locked")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String statusOn(Connection connection, String table, UUID id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status FROM public." + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private String indexLookupOn(Connection connection, String kind, String tokenHash) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT tenant_id::text FROM public.auth_token_tenant_index "
                        + "WHERE kind = ? AND token_hash = ?")) {
            ps.setString(1, kind);
            ps.setString(2, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // ------------------------------------------------------------------
    // §5.4.8/§5.4.9 — policy metadata: FORCE RLS + signed fail-closed policy
    // ------------------------------------------------------------------
    @Test
    void tokenTablesHaveForcedFailClosedTenantPolicyAndIndexIsUnprotected() {
        for (String table : TOKEN_TABLES) {
            Integer policyCount = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM pg_policies
                    WHERE schemaname = 'public'
                      AND tablename = ?
                      AND policyname = 'tenant_isolation'
                      AND cmd = 'ALL'
                      AND qual ILIKE '%app_tenant_ctx_text%'
                      AND qual ILIKE '%IS NOT NULL%'
                      AND qual NOT ILIKE '%IS NULL OR%'
                      AND with_check ILIKE '%app_tenant_ctx_text%'
                      AND with_check ILIKE '%IS NOT NULL%'
                      AND with_check NOT ILIKE '%IS NULL OR%'
                    """, Integer.class, table);
            assertThat(policyCount).as("fail-closed signed-context USING/WITH CHECK policy for %s", table)
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
            assertThat(forced).as("RLS enabled AND forced for %s", table).isEqualTo(1);
        }

        // The pre-auth discovery index is intentionally NOT RLS'd (documented
        // contract, same as login_tenant_index) and must exist with both kinds.
        Integer indexRls = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relname = 'auth_token_tenant_index'
                  AND c.relrowsecurity
                """, Integer.class);
        assertThat(indexRls).as("auth_token_tenant_index must NOT have RLS (pre-auth discovery contract)")
                .isZero();
        Integer indexRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.auth_token_tenant_index", Integer.class);
        assertThat(indexRows).as("index holds 2 refresh + 2 reset entries after seeding")
                .isEqualTo(4);
        Integer kinds = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT kind) FROM public.auth_token_tenant_index", Integer.class);
        assertThat(kinds).isEqualTo(2);

        // §5.4.9 — the runtime role must never hold BYPASSRLS.
        Integer bypass = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_roles
                WHERE rolname = ? AND rolbypassrls
                """, Integer.class, PROBE_USER);
        assertThat(bypass).as("runtime role must not have BYPASSRLS").isZero();
    }

    // ------------------------------------------------------------------
    // §5.4.4 — missing/empty/forged context denies reads (owner included: FORCE)
    // ------------------------------------------------------------------
    @Test
    void missingAndEmptyTenantContextsSeeNoTokenRowsEvenAsOwner() throws Exception {
        try (Connection connection = probeConnection()) {
            connection.setAutoCommit(true);
            for (String table : TOKEN_TABLES) {
                // 1. no context at all
                assertThat(countOn(connection, table))
                        .as("%s must deny reads with missing context although rows exist (FORCE RLS binds the owner)", table)
                        .isZero();
                // 2. EMPTY context
                connection.createStatement().execute("SELECT set_config('app.tenant_id', '', false)");
                assertThat(countOn(connection, table))
                        .as("%s must deny reads with EMPTY context", table)
                        .isZero();
                // 3. G1-i RT-01: a FORGED tenant id WITHOUT a signed mac is fail-closed.
                connection.createStatement().execute("SELECT set_config('app.tenant_id', '" + tenantB + "', false)");
                assertThat(countOn(connection, table))
                        .as("%s must deny reads under a forged tenant GUC without a signed mac", table)
                        .isZero();
                connection.createStatement().execute("RESET app.tenant_id");
            }
        }
    }

    // ------------------------------------------------------------------
    // §5.4.1–3 — A/B isolation, both directions, under signed binds
    // ------------------------------------------------------------------
    @Test
    void tenantContextsExposeExactlyTheirOwnTokenRows() throws Exception {
        try (Connection connection = probeConnection()) {
            connection.setAutoCommit(false);

            bindTx(connection, tenantA);
            assertThat(countByIdOn(connection, "refresh_tokens", refreshA)).isEqualTo(1);
            assertThat(countByIdOn(connection, "refresh_tokens", refreshB)).isZero();
            assertThat(countByIdOn(connection, "password_reset_tokens", resetA)).isEqualTo(1);
            assertThat(countByIdOn(connection, "password_reset_tokens", resetB)).isZero();
            connection.rollback();

            bindTx(connection, tenantB);
            assertThat(countByIdOn(connection, "refresh_tokens", refreshB)).isEqualTo(1);
            assertThat(countByIdOn(connection, "refresh_tokens", refreshA)).isZero();
            assertThat(countByIdOn(connection, "password_reset_tokens", resetB)).isEqualTo(1);
            assertThat(countByIdOn(connection, "password_reset_tokens", resetA)).isZero();
            connection.rollback();
        }
    }

    // ------------------------------------------------------------------
    // §5.4.6/7 — cross-tenant writes denied (row-filter + WITH CHECK)
    // ------------------------------------------------------------------
    @Test
    void crossTenantTokenWritesAreDenied() throws Exception {
        try (Connection connection = probeConnection()) {
            connection.setAutoCommit(false);
            bindTx(connection, tenantA);

            // A cannot consume/revoke B's refresh token (row-filtered UPDATE).
            int hijack = connection.createStatement().executeUpdate(
                    "UPDATE public.refresh_tokens SET status = 'USED' WHERE id = '" + refreshB + "'");
            assertThat(hijack).as("cross-tenant UPDATE affects zero rows").isZero();

            // A cannot delete B's password reset token (row-filtered DELETE).
            int deleted = connection.createStatement().executeUpdate(
                    "DELETE FROM public.password_reset_tokens WHERE id = '" + resetB + "'");
            assertThat(deleted).as("cross-tenant DELETE affects zero rows").isZero();

            // A cannot mint a refresh token inside B (WITH CHECK, SQLSTATE 42501).
            UUID smuggled = UUID.randomUUID();
            assertThatThrownBy(() -> connection.createStatement().executeUpdate(
                    "INSERT INTO public.refresh_tokens (id, tenant_id, user_id, token_hash, status, expires_at, created_at) "
                            + "VALUES ('" + smuggled + "', '" + tenantB + "', '" + userB + "', 'd6-smuggled', 'ACTIVE', now() + interval '7 days', now())"))
                    .as("cross-tenant INSERT must be rejected by WITH CHECK")
                    .isInstanceOf(java.sql.SQLException.class)
                    .hasStackTraceContaining("row-level security policy");

            // A cannot mint a password-reset token inside B either.
            UUID smuggledReset = UUID.randomUUID();
            assertThatThrownBy(() -> connection.createStatement().executeUpdate(
                    "INSERT INTO public.password_reset_tokens (id, tenant_id, user_id, token_hash, status, expires_at, created_at) "
                            + "VALUES ('" + smuggledReset + "', '" + tenantB + "', '" + userB + "', 'd6-smuggled-reset', 'ACTIVE', now() + interval '30 minutes', now())"))
                    .isInstanceOf(java.sql.SQLException.class)
                    .hasStackTraceContaining("row-level security policy");

            // The smuggled hash never reached the discovery index.
            Integer smuggledInIndex = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM public.auth_token_tenant_index WHERE token_hash LIKE 'd6-smuggled%'",
                    Integer.class);
            assertThat(smuggledInIndex).isZero();

            // B's rows are intact (signed bind for B in a fresh transaction).
            connection.rollback();
            bindTx(connection, tenantB);
            assertThat(statusOn(connection, "refresh_tokens", refreshB)).isEqualTo("ACTIVE");
            assertThat(statusOn(connection, "password_reset_tokens", resetB)).isEqualTo("ACTIVE");
            connection.rollback();
        }
    }

    // ------------------------------------------------------------------
    // Trigger sync: INSERT/UPDATE/DELETE mirrored into the index, kinds separate
    // ------------------------------------------------------------------
    @Test
    void discoveryIndexStaysInSyncWithTokenRows() {
        // New refresh token under A appears in the index (seeded via the
        // SIGNED binding protocol — the enforced catalog rejects unbound
        // owner writes; the trigger itself is context-independent).
        String newHash = "d6-sync-hash-" + UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        try (Connection conn = new SingleConnectionDataSource(JDBC_URL, USER, PASSWORD, false).getConnection()) {
            conn.setAutoCommit(false);
            bindTx(conn, tenantA);
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO public.refresh_tokens (id, tenant_id, user_id, token_hash,
                                                       status, expires_at, created_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', now() + interval '7 days', now())
                    """)) {
                ps.setObject(1, newId);
                ps.setObject(2, UUID.fromString(tenantA));
                ps.setObject(3, userA);
                ps.setString(4, newHash);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (Exception e) {
            throw new IllegalStateException("signed-bound seeding failed for index-sync insert", e);
        }
        assertThat(indexTenantFor("refresh", newHash))
                .as("index mirrors INSERT with the owning tenant")
                .contains(tenantA);

        // Deleting the token removes the index entry (signed-bound, same tenant).
        try (Connection conn = new SingleConnectionDataSource(JDBC_URL, USER, PASSWORD, false).getConnection()) {
            conn.setAutoCommit(false);
            bindTx(conn, tenantA);
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM public.refresh_tokens WHERE id = ?")) {
                ps.setObject(1, newId);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (Exception e) {
            throw new IllegalStateException("signed-bound seeding failed for index-sync delete", e);
        }
        assertThat(indexTenantFor("refresh", newHash))
                .as("index mirrors DELETE")
                .isEmpty();

        // Kinds are separated: the refresh hash never resolves as a reset token.
        assertThat(indexTenantFor("password_reset", "d6-refresh-hash-A"))
                .as("refresh hash must not resolve under the password_reset kind")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // §5.3 application contract end-to-end: index lookup (pre-auth) →
    // signed bind → scoped FOR UPDATE under the discovered tenant.
    // ------------------------------------------------------------------
    @Test
    void preAuthIndexContractScopesThenFindsExactlyTheOwningRow() throws Exception {
        try (Connection connection = probeConnection()) {
            connection.setAutoCommit(false);

            // Simulate AuthService#refresh with a B-tenant refresh token:
            // 1. NO signed context yet — hash-only scoped lookup sees NOTHING.
            assertThat(countOn(connection, "refresh_tokens")).isZero();
            assertThat(visibleForUpdateOn(connection, "refresh_tokens", refreshB)).isZero();

            // 2. Pre-auth discovery via the index (no RLS) yields the owning tenant.
            String discovered = indexLookupOn(connection, "refresh", "d6-refresh-hash-B");
            assertThat(discovered).as("index resolves the owning tenant without any GUC")
                    .isEqualTo(tenantB);

            // 3. applyTrustedUserScope(discovered) — signed bind — then the FOR UPDATE lookup.
            bindTx(connection, discovered);
            assertThat(visibleForUpdateOn(connection, "refresh_tokens", refreshB)).isEqualTo(1);
            assertThat(visibleForUpdateOn(connection, "refresh_tokens", refreshA)).isZero();

            // 4. The same contract for the password-reset flow with A's token.
            connection.rollback();
            String discoveredReset = indexLookupOn(connection, "password_reset", "d6-reset-hash-A");
            assertThat(discoveredReset).isEqualTo(tenantA);
            bindTx(connection, discoveredReset);
            assertThat(visibleForUpdateOn(connection, "password_reset_tokens", resetA)).isEqualTo(1);
            assertThat(visibleForUpdateOn(connection, "password_reset_tokens", resetB)).isZero();

            connection.rollback();
        }
    }

    // ------------------------------------------------------------------
    // §5.4.10 — pool-reuse safety: the signed bind is transaction-local
    // ------------------------------------------------------------------
    @Test
    void contextDoesNotLeakAcrossTransactionsOnOneConnection() throws Exception {
        try (Connection connection = probeConnection()) {
            connection.setAutoCommit(false);

            // Transaction 1: bind A and observe A's row.
            bindTx(connection, tenantA);
            assertThat(countOn(connection, "refresh_tokens")).isEqualTo(1);
            connection.commit();

            // Transaction 2 on the SAME physical connection: no bind was
            // re-applied — the transaction-local signed bind must have reset at
            // COMMIT, so the pooled connection must NOT leak A's tokens.
            assertThat(countOn(connection, "refresh_tokens"))
                    .as("the signed bind must reset at transaction end (pool reuse safety)")
                    .isZero();
            assertThat(countOn(connection, "refresh_tokens")).isZero();

            // Transaction 3: bind B — only B visible; A gone.
            bindTx(connection, tenantB);
            assertThat(countOn(connection, "refresh_tokens")).isEqualTo(1);
            connection.rollback();
        }
    }

    // ------------------------------------------------------------------
    // helpers (migration/fixture role — catalog and index-level probes)
    // ------------------------------------------------------------------
    private java.util.Optional<String> indexTenantFor(String kind, String tokenHash) {
        java.util.List<String> found = jdbc.query(
                "SELECT tenant_id::text FROM public.auth_token_tenant_index "
                        + "WHERE kind = ? AND token_hash = ?",
                (rs, n) -> rs.getString(1), kind, tokenHash);
        return found.stream().findFirst();
    }
}
