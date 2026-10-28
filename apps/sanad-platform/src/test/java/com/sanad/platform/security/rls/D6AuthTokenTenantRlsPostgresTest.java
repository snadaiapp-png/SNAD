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
 *   <li>forced fail-closed tenant_isolation policy on BOTH token tables;</li>
 *   <li>{@code auth_token_tenant_index} exists and is intentionally NOT
 *       RLS'd (documented pre-auth discovery contract, mirrors
 *       {@code login_tenant_index});</li>
 *   <li>missing / empty {@code app.tenant_id} see ZERO token rows although
 *       real rows exist — including for the table owner (FORCE RLS);</li>
 *   <li>valid context exposes exactly that tenant's tokens in both
 *       directions (A vs B) for reads;</li>
 *   <li>cross-tenant UPDATE/DELETE are row-filtered to zero rows;</li>
 *   <li>cross-tenant INSERT is rejected by WITH CHECK (SQLSTATE 42501);</li>
 *   <li>the sync trigger mirrors INSERT/DELETE into the discovery index and
 *       keeps kinds separate;</li>
 *   <li>the application's pre-auth contract holds end-to-end: index lookup
 *       (no context) → tenant id → scoped FOR UPDATE finds exactly the
 *       owning tenant's row and never another tenant's;</li>
 *   <li>{@code SET LOCAL} GUC resets at transaction end — a pooled
 *       connection reused for the next transaction sees ZERO rows
 *       (pool-reuse leak prevention);</li>
 *   <li>the runtime role {@code sanad} has NO {@code BYPASSRLS}.</li>
 * </ul>
 */
class D6AuthTokenTenantRlsPostgresTest {
    private static final String BASE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final String JDBC_URL = MigrationTestSchemaSupport.getIsolatedJdbcUrl(BASE_URL);
    private static final java.util.List<String> TOKEN_TABLES =
            java.util.List.of("refresh_tokens", "password_reset_tokens");

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
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "D6AuthTokenTenantRlsPostgresTest");
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
        jdbc.execute("RESET app.tenant_id");
    }

    private UUID seedUser(String tenant, String label) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, false)", String.class, tenant);
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.users (id, tenant_id, email, display_name, status,
                                          password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', 'd6-test-hash', now(), now())
                """, userId, UUID.fromString(tenant),
                "d6-" + label + "-" + userId + "@sanad.test", "D6 " + label);
        return userId;
    }

    private UUID seedRefreshToken(String tenant, UUID userId, String tokenHash) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, false)", String.class, tenant);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.refresh_tokens (id, tenant_id, user_id, token_hash,
                                                   status, expires_at, created_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', now() + interval '7 days', now())
                """, id, UUID.fromString(tenant), userId, tokenHash);
        return id;
    }

    private UUID seedPasswordResetToken(String tenant, UUID userId, String tokenHash) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, false)", String.class, tenant);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.password_reset_tokens (id, tenant_id, user_id, token_hash,
                                                          status, expires_at, created_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', now() + interval '30 minutes', now())
                """, id, UUID.fromString(tenant), userId, tokenHash);
        return id;
    }

    // ------------------------------------------------------------------
    // §5.4.8/§5.4.9 — policy metadata: FORCE RLS + fail-closed policy
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
                """, Integer.class, USER);
        assertThat(bypass).as("runtime role must not have BYPASSRLS").isZero();
    }

    // ------------------------------------------------------------------
    // §5.4.4 — missing/empty context denies reads (owner included: FORCE)
    // ------------------------------------------------------------------
    @Test
    void missingAndEmptyTenantContextsSeeNoTokenRowsEvenAsOwner() {
        for (String table : TOKEN_TABLES) {
            jdbc.execute("RESET app.tenant_id");
            assertThat(count(table))
                    .as("%s must deny reads with missing context although rows exist (FORCE RLS binds the owner)", table)
                    .isZero();
            jdbc.queryForObject("SELECT set_config('app.tenant_id', '', false)", String.class);
            assertThat(count(table))
                    .as("%s must deny reads with EMPTY context", table)
                    .isZero();
            jdbc.execute("RESET app.tenant_id");
        }
    }

    // ------------------------------------------------------------------
    // §5.4.1–3 — A/B isolation, both directions
    // ------------------------------------------------------------------
    @Test
    void tenantContextsExposeExactlyTheirOwnTokenRows() {
        jdbc.execute("RESET app.tenant_id");

        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, false)", String.class, tenantA);
        assertThat(countById("refresh_tokens", refreshA)).isEqualTo(1);
        assertThat(countById("refresh_tokens", refreshB)).isZero();
        assertThat(countById("password_reset_tokens", resetA)).isEqualTo(1);
        assertThat(countById("password_reset_tokens", resetB)).isZero();

        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, false)", String.class, tenantB);
        assertThat(countById("refresh_tokens", refreshB)).isEqualTo(1);
        assertThat(countById("refresh_tokens", refreshA)).isZero();
        assertThat(countById("password_reset_tokens", resetB)).isEqualTo(1);
        assertThat(countById("password_reset_tokens", resetA)).isZero();

        jdbc.execute("RESET app.tenant_id");
    }

    // ------------------------------------------------------------------
    // §5.4.6/7 — cross-tenant writes denied (row-filter + WITH CHECK)
    // ------------------------------------------------------------------
    @Test
    void crossTenantTokenWritesAreDenied() {
        jdbc.execute("RESET app.tenant_id");
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, false)", String.class, tenantA);

        // A cannot consume/revoke B's refresh token (row-filtered UPDATE).
        int hijack = jdbc.update(
                "UPDATE public.refresh_tokens SET status = 'USED' WHERE id = ?", refreshB);
        assertThat(hijack).as("cross-tenant UPDATE affects zero rows").isZero();

        // A cannot delete B's password reset token (row-filtered DELETE).
        int deleted = jdbc.update(
                "DELETE FROM public.password_reset_tokens WHERE id = ?", resetB);
        assertThat(deleted).as("cross-tenant DELETE affects zero rows").isZero();

        // A cannot mint a refresh token inside B (WITH CHECK, SQLSTATE 42501).
        UUID smuggled = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO public.refresh_tokens (id, tenant_id, user_id, token_hash,
                                                   status, expires_at, created_at)
                VALUES (?, ?, ?, 'd6-smuggled', 'ACTIVE', now() + interval '7 days', now())
                """, smuggled, UUID.fromString(tenantB), userB))
                .as("cross-tenant INSERT must be rejected by WITH CHECK")
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(PSQLException.class)
                .hasStackTraceContaining("row-level security policy");

        // A cannot mint a password-reset token inside B either.
        UUID smuggledReset = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO public.password_reset_tokens (id, tenant_id, user_id, token_hash,
                                                          status, expires_at, created_at)
                VALUES (?, ?, ?, 'd6-smuggled-reset', 'ACTIVE', now() + interval '30 minutes', now())
                """, smuggledReset, UUID.fromString(tenantB), userB))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(PSQLException.class)
                .hasStackTraceContaining("row-level security policy");

        // The smuggled hash never reached the discovery index.
        Integer smuggledInIndex = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.auth_token_tenant_index WHERE token_hash LIKE 'd6-smuggled%'",
                Integer.class);
        assertThat(smuggledInIndex).isZero();

        // B's rows are intact.
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, false)", String.class, tenantB);
        assertThat(statusOf("refresh_tokens", refreshB)).isEqualTo("ACTIVE");
        assertThat(statusOf("password_reset_tokens", resetB)).isEqualTo("ACTIVE");
        jdbc.execute("RESET app.tenant_id");
    }

    // ------------------------------------------------------------------
    // Trigger sync: INSERT/UPDATE/DELETE mirrored into the index, kinds separate
    // ------------------------------------------------------------------
    @Test
    void discoveryIndexStaysInSyncWithTokenRows() {
        jdbc.execute("RESET app.tenant_id");

        // New refresh token under A's context appears in the index.
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, false)", String.class, tenantA);
        String newHash = "d6-sync-hash-" + UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.refresh_tokens (id, tenant_id, user_id, token_hash,
                                                   status, expires_at, created_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', now() + interval '7 days', now())
                """, newId, UUID.fromString(tenantA), userA, newHash);
        assertThat(indexTenantFor("refresh", newHash))
                .as("index mirrors INSERT with the owning tenant")
                .contains(tenantA);

        // Deleting the token removes the index entry.
        jdbc.update("DELETE FROM public.refresh_tokens WHERE id = ?", newId);
        assertThat(indexTenantFor("refresh", newHash))
                .as("index mirrors DELETE")
                .isEmpty();

        // Kinds are separated: the refresh hash never resolves as a reset token.
        assertThat(indexTenantFor("password_reset", "d6-refresh-hash-A"))
                .as("refresh hash must not resolve under the password_reset kind")
                .isEmpty();
        jdbc.execute("RESET app.tenant_id");
    }

    // ------------------------------------------------------------------
    // §5.3 application contract end-to-end: index lookup (pre-auth) →
    // scoped FOR UPDATE under the discovered tenant.
    // ------------------------------------------------------------------
    @Test
    void preAuthIndexContractScopesThenFindsExactlyTheOwningRow() throws Exception {
        try (Connection connection = new SingleConnectionDataSource(JDBC_URL, USER, PASSWORD, false)
                .getConnection()) {
            connection.setAutoCommit(false);

            // Simulate AuthService#refresh with a B-tenant refresh token:
            // 1. NO tenant context yet — hash-only scoped lookup sees NOTHING.
            assertThat(visibleInTx(connection, "refresh_tokens", refreshB)).isZero();

            // 2. Pre-auth discovery via the index (no RLS) yields the owning tenant.
            String discovered = indexLookupInTx(connection, "refresh", "d6-refresh-hash-B");
            assertThat(discovered).as("index resolves the owning tenant without any GUC")
                    .isEqualTo(tenantB);

            // 3. applyTrustedUserScope(discovered) then the FOR UPDATE lookup.
            setTenantTx(connection, discovered);
            assertThat(visibleForUpdateInTx(connection, "refresh_tokens", refreshB)).isEqualTo(1);
            assertThat(visibleForUpdateInTx(connection, "refresh_tokens", refreshA)).isZero();

            // 4. The same contract for the password-reset flow with A's token.
            connection.rollback();
            String discoveredReset = indexLookupInTx(connection, "password_reset", "d6-reset-hash-A");
            assertThat(discoveredReset).isEqualTo(tenantA);
            setTenantTx(connection, discoveredReset);
            assertThat(visibleForUpdateInTx(connection, "password_reset_tokens", resetA)).isEqualTo(1);
            assertThat(visibleForUpdateInTx(connection, "password_reset_tokens", resetB)).isZero();

            connection.rollback();
        }
    }

    // ------------------------------------------------------------------
    // §5.4.10 — pool-reuse safety: SET LOCAL resets at transaction end
    // ------------------------------------------------------------------
    @Test
    void contextDoesNotLeakAcrossTransactionsOnOneConnection() throws Exception {
        try (Connection connection = new SingleConnectionDataSource(JDBC_URL, USER, PASSWORD, false)
                .getConnection()) {
            connection.setAutoCommit(false);

            // Transaction 1: scope to A and observe A's row.
            setTenantTx(connection, tenantA);
            assertThat(visibleInTx(connection, "refresh_tokens", refreshA)).isEqualTo(1);
            connection.commit();

            // Transaction 2 on the SAME physical connection: no context was
            // re-applied — SET LOCAL must have reset at COMMIT, so the pooled
            // connection must NOT leak A's tokens.
            assertThat(visibleInTx(connection, "refresh_tokens", refreshA))
                    .as("SET LOCAL app.tenant_id must reset at transaction end (pool reuse safety)")
                    .isZero();
            assertThat(visibleInTx(connection, "refresh_tokens", refreshB)).isZero();

            // Transaction 3: scope to B — only B visible; A gone.
            setTenantTx(connection, tenantB);
            assertThat(visibleInTx(connection, "refresh_tokens", refreshB)).isEqualTo(1);
            assertThat(visibleInTx(connection, "refresh_tokens", refreshA)).isZero();
            connection.rollback();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------
    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM public." + table, Integer.class);
    }

    private int countById(String table, UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM public." + table + " WHERE id = ?", Integer.class, id);
    }

    private String statusOf(String table, UUID id) {
        return jdbc.queryForObject(
                "SELECT status FROM public." + table + " WHERE id = ?", String.class, id);
    }

    private java.util.Optional<String> indexTenantFor(String kind, String tokenHash) {
        java.util.List<String> found = jdbc.query(
                "SELECT tenant_id::text FROM public.auth_token_tenant_index "
                        + "WHERE kind = ? AND token_hash = ?",
                (rs, n) -> rs.getString(1), kind, tokenHash);
        return found.stream().findFirst();
    }

    private static void setTenantTx(Connection connection, String tenant) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
            }
        }
    }

    private static int visibleInTx(Connection connection, String table, UUID id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM public." + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int visibleForUpdateInTx(Connection connection, String table, UUID id)
            throws Exception {
        // Mirrors RefreshTokenRepository#findByTokenHashForUpdate semantics
        // (SELECT ... FOR UPDATE) without aggregates — FOR UPDATE cannot wrap COUNT(*).
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM public." + table + " WHERE id = ? FOR UPDATE")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                int rows = 0;
                while (rs.next()) {
                    rows++;
                }
                return rows;
            }
        }
    }

    private static String indexLookupInTx(Connection connection, String kind, String tokenHash)
            throws Exception {
        // The exact SQL AuthTokenTenantScopeResolver issues — pre-context,
        // SECURITY INVOKER, discovery index only.
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
}
