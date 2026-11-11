package com.sanad.platform.crm.integration;

import com.sanad.platform.crm.integration.security.CallbackReplayStore;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0 RLS closure acceptance (executive order 2026-11-11) for
 * public.service_callback_replay — PostgreSQL Direct, real connections.
 *
 * <p>Mandatory matrix (V20261111_1):</p>
 * <ol>
 *   <li>Tenant A cannot read or modify Tenant B rows (and vice versa).</li>
 *   <li>Tenant B cannot access Tenant A rows.</li>
 *   <li>Missing tenant context denies all access (fail-closed).</li>
 *   <li>INSERT with a mismatched tenant id is REJECTED.</li>
 *   <li>DELETE outside the tenant has zero effect (row preserved).</li>
 *   <li>The cleanup worker (dedicated role) removes only expired rows and
 *       grants no unauthorized access; the runtime identity, without
 *       switching, cannot see cross-tenant rows.</li>
 *   <li>Callback consumption still works (regression) and remains
 *       exactly-once.</li>
 * </ol>
 *
 * <p>PostgreSQL Direct fixture identity is the runtime role (owner-as-runner
 * in the migration-fixture topology), and V20261111_1 FORCEs RLS so the
 * owner is bound by the policies exactly like the restricted runtime of the
 * separated topology. All verification queries therefore run with the
 * owning tenant's GUC bound (no superuser access exists on the CI fixture);
 * physical (beyond-RLS) integrity inventory is certified by the superuser
 * probes of tests/security/callback-replay-rls/run-rls-cert.sh on the
 * isolated separated-topology cluster.</p>
 */
class CallbackReplayRlsPostgresTest {

    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager txManager;
    private static TransactionTemplate txTemplate;
    private static CallbackReplayStore store;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeAll
    static void setup() {
        boolean postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                "CallbackReplayRlsPostgresTest");
        Assumptions.assumeTrue(
                postgresAvailable,
                "PostgreSQL Direct unavailable — skipping in non-CI environment");
        Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        jdbc = new JdbcTemplate(ds);
        txManager = new DataSourceTransactionManager(ds);
        txTemplate = new TransactionTemplate(txManager);
        store = new CallbackReplayStore(jdbc, txTemplate, new TenantRlsTransactionContext(jdbc));
    }

    @BeforeEach
    void seedTenants() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        for (UUID tenant : new UUID[] { tenantA, tenantB }) {
            jdbc.update(
                    "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                            "VALUES (?, 'RLS Tenant', ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    tenant,
                    "rls-" + tenant.toString().substring(0, 8));
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Run a block inside a transaction with app.tenant_id bound (trusted scoped path). */
    private <T> T asTenant(UUID tenant, TenantBlock<T> block) {
        AtomicReference<T> result = new AtomicReference<>();
        txTemplate.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenant.toString());
            result.set(block.run());
        });
        return result.get();
    }

    /** Run a block inside a transaction WITHOUT binding any tenant GUC. */
    private <T> T withoutTenantContext(TenantBlock<T> block) {
        AtomicReference<T> result = new AtomicReference<>();
        txTemplate.executeWithoutResult(status -> result.set(block.run()));
        return result.get();
    }

    /** Run a block inside a transaction with an explicitly EMPTY tenant GUC. */
    private <T> T withEmptyTenantContext(TenantBlock<T> block) {
        AtomicReference<T> result = new AtomicReference<>();
        txTemplate.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', '', true)", String.class);
            result.set(block.run());
        });
        return result.get();
    }

    private int countVisibleRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM service_callback_replay", Integer.class);
    }

    /**
     * Seed a row as its owning tenant (the trusted scoped path) with globally
     * unique jti/nonce — the (service_name, jti) and (service_name, nonce)
     * uniqueness is GLOBAL, so fixed strings would collide across runs.
     */
    private void seedRow(UUID tenant, String suffix, boolean expired) {
        String jti = "jti-" + suffix + "-" + tenant;
        String nonce = "nonce-" + suffix + "-" + tenant;
        String expiry = expired
                ? "CURRENT_TIMESTAMP - interval '1 hour'"
                : "CURRENT_TIMESTAMP + interval '1 hour'";
        asTenant(tenant, () -> jdbc.update(
                "INSERT INTO service_callback_replay " +
                        "(tenant_id, service_name, jti, nonce, correlation_id, received_at, expires_at) " +
                        "VALUES (?, 'rls-cert-service', ?, ?, 'rls-cert', " +
                        "CURRENT_TIMESTAMP - interval '2 hours', " + expiry + ")",
                tenant, jti, nonce));
    }

    /**
     * Raw INSERT probe with NO tenant binding and a caller-chosen tenant id —
     * used exclusively for the mandatory VIOLATION probes (missing context,
     * mismatched tenant, cleanup-role write). Legitimate seeding uses seedRow.
     */
    private int insertRow(UUID tenantId, String jti, String nonce, boolean expired) {
        String expiry = expired
                ? "CURRENT_TIMESTAMP - interval '1 hour'"
                : "CURRENT_TIMESTAMP + interval '1 hour'";
        return jdbc.update(
                "INSERT INTO service_callback_replay " +
                        "(tenant_id, service_name, jti, nonce, correlation_id, received_at, expires_at) " +
                        "VALUES (?, 'rls-cert-service', ?, ?, 'rls-cert', " +
                        "CURRENT_TIMESTAMP - interval '2 hours', " + expiry + ")",
                tenantId, jti, nonce);
    }

    /**
     * Run an expected-to-be-REJECTED probe inside its own REQUIRES_NEW
     * transaction: the anticipated SQLSTATE 42501 aborts exactly that
     * transaction (rolled back by the template) and can never poison the
     * surrounding assertions with 25P02 aborted-transaction errors.
     */
    private void expectRejectedInNewTransaction(Runnable probe) {
        TransactionTemplate isolatedProbe = new TransactionTemplate(txManager);
        isolatedProbe.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            isolatedProbe.executeWithoutResult(status -> probe.run());
        } catch (DataAccessException expected) {
            // The probe asserts its own SQLSTATE via assertSqlstate42501.
        }
    }

    /** Assert the statement fails with SQLSTATE 42501 carrying the expected fragment. */
    private static void assertSqlstate42501(String expectedFragment, Runnable statement) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataAccessException.class)
                .satisfies(error -> {
                    Throwable cause = error;
                    PSQLException sql = null;
                    while (cause != null) {
                        if (cause instanceof PSQLException candidate) {
                            sql = candidate;
                            break;
                        }
                        cause = cause.getCause();
                    }
                    assertThat((Throwable) sql).as("exception chain carries a PSQLException").isNotNull();
                    assertThat(sql.getSQLState()).isEqualTo("42501");
                    assertThat(sql.getMessage()).contains(expectedFragment);
                });
    }

    private interface TenantBlock<T> {
        T run();
    }

    // ------------------------------------------------------------------
    // catalog contract
    // ------------------------------------------------------------------

    @Test
    void catalogContractEnablesAndForcesRlsWithExactlyFiveBoundedPolicies() {
        Integer rlsEnabled = jdbc.queryForObject(
                "SELECT relrowsecurity::int FROM pg_class WHERE oid='public.service_callback_replay'::regclass",
                Integer.class);
        Integer rlsForced = jdbc.queryForObject(
                "SELECT relforcerowsecurity::int FROM pg_class WHERE oid='public.service_callback_replay'::regclass",
                Integer.class);
        assertThat(rlsEnabled).isEqualTo(1);
        assertThat(rlsForced).isEqualTo(1);

        Integer policyCount = jdbc.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname='public' AND tablename='service_callback_replay'",
                Integer.class);
        assertThat(policyCount).isEqualTo(5);

        Integer updatePolicies = jdbc.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname='public' AND tablename='service_callback_replay' AND cmd='UPDATE'",
                Integer.class);
        assertThat(updatePolicies).isZero();

        Integer expiredBounded = jdbc.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname='public' AND tablename='service_callback_replay' " +
                        "AND policyname IN ('expired_cleanup_select','expired_cleanup_delete') " +
                        "AND qual LIKE '%expires_at <= CURRENT_TIMESTAMP%'",
                Integer.class);
        assertThat(expiredBounded).isEqualTo(2);

        Integer tenantBounded = jdbc.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname='public' AND tablename='service_callback_replay' " +
                        "AND policyname IN ('tenant_isolation_select','tenant_isolation_insert','tenant_isolation_delete') " +
                        "AND (COALESCE(qual,'') || COALESCE(with_check,'')) LIKE '%NULLIF(current_setting%app.tenant_id%'",
                Integer.class);
        assertThat(tenantBounded).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // 1 & 2 — cross-tenant invisibility in BOTH directions
    // ------------------------------------------------------------------

    @Test
    void tenantACannotReadOrModifyTenantBRowsAndViceVersa() {
        seedRow(tenantB, "ab-1", false);

        Integer aSeesB = asTenant(tenantA, () -> countVisibleRows());
        assertThat(aSeesB).isZero();

        Integer bSeesOwn = asTenant(tenantB, () -> countVisibleRows());
        assertThat(bSeesOwn).isEqualTo(1);

        // Cross-tenant UPDATE and DELETE are inert: the rows are invisible.
        Integer crossUpdate = asTenant(tenantA, () ->
                jdbc.update("UPDATE service_callback_replay SET correlation_id='x' WHERE tenant_id=?", tenantB));
        assertThat(crossUpdate).isZero();
        Integer crossDelete = asTenant(tenantA, () ->
                jdbc.update("DELETE FROM service_callback_replay WHERE tenant_id=?", tenantB));
        assertThat(crossDelete).isZero();

        // Even the OWN row cannot be UPDATEd: no UPDATE policy exists by
        // design. PostgreSQL 17 applies the default-deny as a row filter for
        // UPDATE, so the statement is silently inert (zero rows affected);
        // the separated topology additionally revokes the privilege from the
        // runtime role. UPDATE is therefore impossible for every role.
        seedRow(tenantA, "ab-2", false);
        Integer ownUpdated = asTenant(tenantA, () ->
                jdbc.update("UPDATE service_callback_replay SET correlation_id='x' WHERE tenant_id=?", tenantA));
        assertThat(ownUpdated).isZero();
        Integer valueUnchanged = asTenant(tenantA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM service_callback_replay WHERE tenant_id=? AND correlation_id='rls-cert'",
                Integer.class, tenantA));
        assertThat(valueUnchanged).isEqualTo(1);

        // Tenant B data preserved (still visible to the owning tenant).
        Integer bPreserved = asTenant(tenantB, () -> countVisibleRows());
        assertThat(bPreserved).isEqualTo(1);

        // Symmetric direction: B cannot see A's rows either.
        Integer bSeesA = asTenant(tenantB, () -> countVisibleRows());
        assertThat(bSeesA).isEqualTo(1);
        Integer aSeesOwnAfter = asTenant(tenantA, () -> countVisibleRows());
        assertThat(aSeesOwnAfter).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 3 — missing / empty tenant context is fail-closed
    // ------------------------------------------------------------------

    @Test
    void missingTenantContextDeniesEveryAccess() {
        seedRow(tenantA, "ctx-1", false);

        Integer visibleUnset = withoutTenantContext(() -> countVisibleRows());
        assertThat(visibleUnset).isZero();
        Integer visibleEmpty = withEmptyTenantContext(() -> countVisibleRows());
        assertThat(visibleEmpty).isZero();

        // Writes without tenant context are REJECTED by the INSERT WITH CHECK.
        // The expected violation aborts only the probe's own transaction.
        expectRejectedInNewTransaction(() ->
                assertSqlstate42501("violates row-level security policy",
                        () -> insertRow(tenantA, "jti-ctx-2-" + tenantA, "nonce-ctx-2-" + tenantA, false)));
        Integer crossDeleteUnset = withoutTenantContext(() ->
                jdbc.update("DELETE FROM service_callback_replay WHERE tenant_id=?", tenantA));
        assertThat(crossDeleteUnset).isZero();

        Integer preserved = asTenant(tenantA, () -> countVisibleRows());
        assertThat(preserved).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 4 — INSERT with a mismatched tenant id is rejected
    // ------------------------------------------------------------------

    @Test
    void insertWithMismatchedTenantIdIsRejected() {
        asTenant(tenantA, () -> {
            assertSqlstate42501("violates row-level security policy",
                    () -> insertRow(tenantB, "jti-mis-1-" + tenantA, "nonce-mis-1-" + tenantA, false));
            return null;
        });
        Integer leaked = asTenant(tenantB, () -> countVisibleRows());
        assertThat(leaked).isZero();
    }

    // ------------------------------------------------------------------
    // 5 — tenant-scoped DELETE works (module-reset path), cross-tenant is inert
    // ------------------------------------------------------------------

    @Test
    void tenantScopedDeleteWorksAndCrossTenantDeleteIsInert() {
        seedRow(tenantA, "del-1", false);

        Integer crossDeleted = asTenant(tenantB, () ->
                jdbc.update("DELETE FROM service_callback_replay WHERE tenant_id=?", tenantA));
        assertThat(crossDeleted).isZero();

        Integer ownDeleted = asTenant(tenantA, () ->
                jdbc.update("DELETE FROM service_callback_replay WHERE tenant_id=?", tenantA));
        assertThat(ownDeleted).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 6 — cleanup worker: dedicated role, expired-only, no unauthorized access
    // ------------------------------------------------------------------

    @Test
    void cleanupRoleDeletesOnlyExpiredRowsAndGrantsNoBroaderAccess() {
        seedRow(tenantA, "cln-1", true);   // expired
        seedRow(tenantA, "cln-2", false);  // fresh
        seedRow(tenantB, "cln-3", true);   // expired
        seedRow(tenantB, "cln-4", false);  // fresh

        // The runtime identity is a NON-INHERITING member of the cleanup
        // role: without SET ROLE it must only ever see its OWN rows (fresh
        // or expired) and never the other tenant's rows.
        Integer aSeesOwn = asTenant(tenantA, () -> countVisibleRows());
        assertThat(aSeesOwn).isEqualTo(2);

        // The dedicated role sees ONLY expired rows, of any tenant.
        txTemplate.executeWithoutResult(status -> {
            jdbc.execute("SET ROLE sanad_callback_replay_cleanup");
            try {
                assertThat(countVisibleRows()).isEqualTo(2);

                Integer deleted = jdbc.update(
                        "DELETE FROM service_callback_replay WHERE expires_at <= CURRENT_TIMESTAMP");
                assertThat(deleted).isEqualTo(2);

                // Only non-expired rows remain, and the cleanup role cannot see them.
                assertThat(countVisibleRows()).isZero();
            } finally {
                jdbc.execute("RESET ROLE");
            }
        });

        // The cleanup role holds no INSERT privilege at all (SQLSTATE 42501,
        // permission denied) — probed in its own REQUIRES_NEW transaction so
        // the deliberate violation cannot abort the sweep verification above.
        expectRejectedInNewTransaction(() -> {
            jdbc.execute("SET ROLE sanad_callback_replay_cleanup");
            try {
                assertSqlstate42501("permission denied",
                        () -> insertRow(tenantA, "jti-cln-5-" + tenantA, "nonce-cln-5-" + tenantA, true));
            } finally {
                try {
                    jdbc.execute("RESET ROLE");
                } catch (DataAccessException abortedProbe) {
                    // The probe transaction is rolled back wholesale anyway.
                }
            }
        });

        // Fresh rows preserved, visible to their owning tenants; expired gone.
        Integer aOwnAfterSweep = asTenant(tenantA, () -> countVisibleRows());
        assertThat(aOwnAfterSweep).isEqualTo(1);
        Integer bOwnAfterSweep = asTenant(tenantB, () -> countVisibleRows());
        assertThat(bOwnAfterSweep).isEqualTo(1);
    }

    @Test
    void storeDeleteExpiredUsesTheDedicatedRoleAndOnlyRemovesExpiredRows() {
        seedRow(tenantA, "store-1", true);
        seedRow(tenantB, "store-2", true);
        seedRow(tenantA, "store-3", false);

        store.deleteExpired();

        // Expired rows gone (not visible to their owning tenants anymore);
        // the fresh row survives.
        Integer aOwn = asTenant(tenantA, () -> countVisibleRows());
        assertThat(aOwn).isEqualTo(1);
        Integer bOwn = asTenant(tenantB, () -> countVisibleRows());
        assertThat(bOwn).isZero();

        // The cleanup identity must never leak back to the runtime pool: a
        // normal tenant-scoped statement still works in the next transaction.
        Integer aOwnFresh = asTenant(tenantA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM service_callback_replay WHERE tenant_id=? AND expires_at > CURRENT_TIMESTAMP",
                Integer.class, tenantA));
        assertThat(aOwnFresh).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 7 — callback regression: consume() binds its own tenant context
    // ------------------------------------------------------------------

    @Test
    void consumeInsertsAndStaysExactlyOnceUnderRls() {
        Instant expiresAt = Instant.now().plusSeconds(60);
        String jti = "jti-reg-1-" + tenantA;
        String nonce = "nonce-reg-1-" + tenantA;
        boolean first = store.consume(tenantA, "rls-regression-service", jti, nonce, "corr-reg-1", expiresAt);
        assertThat(first).isTrue();

        Integer persisted = asTenant(tenantA, () -> countVisibleRows());
        assertThat(persisted).isEqualTo(1);

        boolean replay = store.consume(
                tenantA, "rls-regression-service", jti, nonce, "corr-reg-1", expiresAt);
        assertThat(replay).isFalse();

        Integer unchanged = asTenant(tenantA, () -> countVisibleRows());
        assertThat(unchanged).isEqualTo(1);

        // Cross-tenant replay with the same jti/nonce is still impossible:
        // the uniqueness is global (service_name, jti) / (service_name, nonce).
        boolean crossTenantConsume = store.consume(
                tenantB, "rls-regression-service", jti, nonce, "corr-reg-1", expiresAt);
        assertThat(crossTenantConsume).isFalse();

        // received_at / expires_at sanity for the consumed row (tenant view).
        Integer sane = asTenant(tenantA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM service_callback_replay WHERE tenant_id=? AND expires_at > received_at",
                Integer.class, tenantA));
        assertThat(sane).isEqualTo(1);
    }
}
