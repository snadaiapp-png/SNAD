package com.sanad.platform.security.rls;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Structural hypothesis test — reproduces the tenant lifecycle under FORCE RLS
 * to prove the common root cause of the 10 remaining failing test classes
 * at SHA 203970b3 (Task Y2-G4-CALLER-FORENSIC-FOLLOWUP-003).
 *
 * <p>Runs against the same PostgreSQL Direct + actual app role + same RLS policies
 * (V20261011_3 public.users FORCE RLS, V20260823_1 crm_contacts FORCE RLS)
 * used by the failing test classes.
 *
 * <h3>Proven scenarios</h3>
 * <ol>
 *   <li>{@code tenantCreationAndInsertSucceedsWithGucOnSameConnection} — Pattern A2
 *       mechanics work: <em>set_config + INSERT on same tx-bound Connection</em>.
 *   <li>{@code dataIsVisibleWithinSameTenantContext} — RLS permits when GUC matches
 *       row.tenant_id.
 *   <li>{@code crossTenantAccessIsDeniedByRls} — RLS denies when GUC != row.tenant_id.
 *   <li>{@code tenantIdentityChangeInsideActiveTxIsRejected} — TenantRlsConnectionHandler
 *       throws "Tenant identity changed inside an active RLS transaction" when
 *       SecurityContextHolder is rebound mid-tx.
 *   <li>{@code auditLogWriteSucceedsInReadWriteTx} — platform_audit_logs INSERT
 *       succeeds when @Transactional is read-write (not readOnly=true).
 *   <li>{@code auditLogWriteFailsInReadOnlyTx} — proves the regression introduced
 *       by @Transactional(readOnly=true) on audit-writing service methods
 *       (SQL state 25006 "cannot execute INSERT in a read-only transaction").
 * </ol>
 *
 * <p>This test does NOT modify any production code, RLS policy, or migration.
 * It only adds new test scenarios that prove the structural hypothesis.
 */
@SpringBootTest
@ActiveProfiles("local")
class TenantLifecycleRlsContractTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    /**
     * Precondition: verify RLS + FORCE ROW LEVEL SECURITY are enabled on public.users
     * and that the active role is the restricted sanad app role (not a SUPERUSER
     * or BYPASSRLS role that would silently bypass RLS).
     *
     * <p>This proves the test runs under the same security contract as the
     * target environment (V20261011_3 migration).
     */
    @BeforeAll
    static void verifyRlsEnabled(@Autowired JdbcTemplate jdbc) {
        // public.users must have relrowsecurity=true AND relforcerowsecurity=true
        boolean[] rlsFlags = jdbc.queryForObject(
                "SELECT c.relrowsecurity, c.relforcerowsecurity "
                        + "FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
                        + "WHERE n.nspname='public' AND c.relname='users' AND c.relkind='r'",
                (rs, rowNum) -> new boolean[]{rs.getBoolean(1), rs.getBoolean(2)});
        assertThat(rlsFlags[0])
                .as("public.users must have ENABLE ROW LEVEL SECURITY (V20261011_3)")
                .isTrue();
        assertThat(rlsFlags[1])
                .as("public.users must have FORCE ROW LEVEL SECURITY (V20261011_3) — table owner is NOT exempt")
                .isTrue();

        // The current role must NOT be SUPERUSER or BYPASSRLS (else RLS is silently bypassed)
        boolean[] roleFlags = jdbc.queryForObject(
                "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user",
                (rs, rowNum) -> new boolean[]{rs.getBoolean(1), rs.getBoolean(2)});
        assertThat(roleFlags[0])
                .as("current role must NOT be SUPERUSER (would bypass RLS)")
                .isFalse();
        assertThat(roleFlags[1])
                .as("current role must NOT have BYPASSRLS (would bypass RLS)")
                .isFalse();

        // The users_tenant_isolation policy must exist with both USING and WITH CHECK
        Long policyCount = jdbc.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname='public' "
                        + "AND tablename='users' AND policyname='users_tenant_isolation'",
                Long.class);
        assertThat(policyCount)
                .as("users_tenant_isolation policy must exist (V20261011_3)")
                .isEqualTo(1L);
    }

    /**
     * Scenario 1 — Tenant data creation succeeds when GUC is set on the same
     * tx-bound Connection (Pattern A2 mechanics).
     *
     * <p>Proves: set_config('app.tenant_id', tenantA, true) + INSERT INTO users
     * wrapped in tx.executeWithoutResult → both share the tx-bound physical
     * Connection → RLS WITH CHECK accepts the row.
     *
     * <p>Note: this test method is @Transactional so the same Connection is
     * used for set_config + INSERT + the verification SELECT. The test class
     * itself is NOT @Transactional (to allow {@link #auditLogWriteFailsInReadOnlyTx}
     * to create a fresh read-only tx and verify the SQL state 25006 regression).
     */
    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("1: tenantCreationAndInsertSucceedsWithGucOnSameConnection")
    void tenantCreationAndInsertSucceedsWithGucOnSameConnection() {
        TransactionTemplate tx = tx();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            // Seed tenant (no RLS on tenants)
            jdbc.update(
                    "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tenantId, "Lifecycle Test Tenant",
                    "lifecycle-" + tenantId.toString().substring(0, 8),
                    Timestamp.from(now), Timestamp.from(now));

            // Pattern A2: wrap set_config + INSERT INTO users in same tx
            tx.executeWithoutResult(txStatus -> {
                jdbc.queryForObject(
                        "SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update(
                        "INSERT INTO users (id, tenant_id, email, display_name, status, "
                                + "password_hash, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'Lifecycle User', 'ACTIVE', 'dummy', ?, ?)",
                        userId, tenantId,
                        "lifecycle-" + userId.toString().substring(0, 8) + "@example.test",
                        Timestamp.from(now), Timestamp.from(now));
            });

            // Verify the row exists at the DB level
            Integer rawCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
            assertThat(rawCount).as("row must be physically persisted").isEqualTo(1);
        } finally {
            // Cleanup (delete with GUC + same tx pattern)
            tx.executeWithoutResult(txStatus -> {
                jdbc.queryForObject(
                        "SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update("DELETE FROM users WHERE id = ?", userId);
            });
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
    }

    /**
     * Scenario 2 + 3 — Data is visible within same tenant context; cross-tenant
     * access is denied by RLS.
     */
    @Test
    @DisplayName("2+3: dataVisibleWithinTenantAndCrossTenantDenied")
    void dataVisibleWithinTenantAndCrossTenantDenied() {
        TransactionTemplate tx = tx();
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            // Seed tenants A and B
            for (UUID tid : List.of(tenantA, tenantB)) {
                jdbc.update(
                        "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                        tid, "Tenant " + tid.toString().substring(0, 8),
                        "t-" + tid.toString().substring(0, 8),
                        Timestamp.from(now), Timestamp.from(now));
            }
            // Seed userA in tenantA
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantA.toString());
                jdbc.update(
                        "INSERT INTO users (id, tenant_id, email, display_name, status, "
                                + "password_hash, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'User A', 'ACTIVE', 'dummy', ?, ?)",
                        userA, tenantA,
                        "ta-user-" + userA.toString().substring(0, 8) + "@example.test",
                        Timestamp.from(now), Timestamp.from(now));
            });

            // Within-tenant: bind tenantA, query for userA → 1 row
            Integer sameTenantCount = tx.execute(s -> {
                bindTenantContext(tenantA);
                return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userA);
            });
            assertThat(sameTenantCount).as("RLS permits when GUC = row.tenant_id").isEqualTo(1);

            // Cross-tenant: bind tenantB, query for userA → 0 rows (RLS denies)
            Integer crossTenantCount = tx.execute(s -> {
                bindTenantContext(tenantB);
                return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userA);
            });
            assertThat(crossTenantCount).as("RLS denies when GUC != row.tenant_id").isEqualTo(0);
        } finally {
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantA.toString());
                jdbc.update("DELETE FROM users WHERE id = ?", userA);
            });
            jdbc.update("DELETE FROM tenants WHERE id IN (?, ?)", tenantA, tenantB);
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Scenario 4 — Tenant identity change inside an active tx is rejected.
     *
     * <p>Proves: TenantRlsConnectionHandler throws "Tenant identity changed
     * inside an active RLS transaction" when SecurityContextHolder is rebound
     * to a different tenant within the same Spring-managed tx.
     *
     * <p>This is the structural cause for the "Tenant identity changed" errors
     * in SalesQualificationBusinessProcessE2ETest, UserApiMutationIntegrationTest,
     * UserApiPersistenceIntegrationTest, UserPhase7AuditPersistenceIntegrationTest
     * at SHA 203970b3.
     */
    @Test
    @DisplayName("4: tenantIdentityChangeInsideActiveTxIsRejected")
    void tenantIdentityChangeInsideActiveTxIsRejected() {
        TransactionTemplate tx = tx();
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        try {
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                // First query: handler fires, sets tenantAppliedFor=tenantA
                bindTenantContext(tenantA);
                jdbc.queryForObject(
                        "SELECT current_setting('app.tenant_id', true)", String.class);
                // Re-bind to tenantB mid-tx — next prepareStatement must throw
                bindTenantContext(tenantB);
                jdbc.queryForObject(
                        "SELECT current_setting('app.tenant_id', true)", String.class);
            })).hasMessageContaining("Tenant identity changed inside an active RLS transaction");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Scenario 5 — Audit log write succeeds in a read-write tx.
     *
     * <p>Proves: a @Transactional method that writes to platform_audit_logs
     * (no RLS, but the INSERT goes through the proxied Connection) succeeds
     * when the tx is read-write.
     */
    @Test
    @DisplayName("5: auditLogWriteSucceedsInReadWriteTx")
    void auditLogWriteSucceedsInReadWriteTx() {
        TransactionTemplate tx = tx();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            // Seed tenant + user with right GUC
            jdbc.update(
                    "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tenantId, "Audit Test Tenant",
                    "audit-" + tenantId.toString().substring(0, 8),
                    Timestamp.from(now), Timestamp.from(now));
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update(
                        "INSERT INTO users (id, tenant_id, email, display_name, status, "
                                + "password_hash, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'Audit User', 'ACTIVE', 'dummy', ?, ?)",
                        userId, tenantId,
                        "audit-" + userId.toString().substring(0, 8) + "@example.test",
                        Timestamp.from(now), Timestamp.from(now));
            });

            // Insert audit log row (no RLS on platform_audit_logs, but goes through
            // the proxied Connection; handler fires but returns early because
            // SecurityContextHolder is NULL)
            tx.executeWithoutResult(s -> {
                jdbc.update(
                        "INSERT INTO platform_audit_logs (id, actor_tenant_id, actor_user_id, "
                                + "target_tenant_id, action, resource_type, resource_id, reason, "
                                + "before_state, after_state, result, failure_reason, correlation_id, "
                                + "created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        auditId, tenantId, userId, tenantId, "TEST_AUDIT",
                        "TEST_RESOURCE", userId.toString(), "test", null,
                        "{\"test\":true}", "SUCCESS", null, "test-correlation",
                        Timestamp.from(now));
            });

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM platform_audit_logs WHERE id = ?",
                    Integer.class, auditId);
            assertThat(count).as("audit row must be persisted").isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM platform_audit_logs WHERE id = ?", auditId);
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update("DELETE FROM users WHERE id = ?", userId);
            });
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
    }

    /**
     * Scenario 6 — Audit log write FAILS in a read-only tx (regression test).
     *
     * <p>Proves: a @Transactional(readOnly = true) method that attempts to
     * write to platform_audit_logs fails with PostgreSQL SQL state 25006
     * "cannot execute INSERT in a read-only transaction". This is the
     * regression that was introduced by commit ee8c88ce and fixed by
     * commit 77e06d4c (changing @Transactional(readOnly = true) to
     * @Transactional on CallerIdentificationService.lookup()).
     */
    @Test
    @DisplayName("6: auditLogWriteFailsInReadOnlyTx")
    void auditLogWriteFailsInReadOnlyTx() {
        TransactionTemplate readOnlyTx = new TransactionTemplate(transactionManager);
        readOnlyTx.setReadOnly(true);

        UUID auditId = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            assertThatThrownBy(() -> readOnlyTx.executeWithoutResult(s -> {
                jdbc.update(
                        "INSERT INTO platform_audit_logs (id, actor_tenant_id, actor_user_id, "
                                + "target_tenant_id, action, resource_type, resource_id, reason, "
                                + "before_state, after_state, result, failure_reason, correlation_id, "
                                + "created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        auditId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "TEST_AUDIT_RO", "TEST_RESOURCE", "test", "test",
                        null, "{\"test\":true}", "SUCCESS", null, "test-ro",
                        Timestamp.from(now));
            }))
                    .hasMessageContaining("read-only transaction")
                    .hasMessageContaining("INSERT");
        } finally {
            // The INSERT failed, so no cleanup needed for auditId
            jdbc.update("DELETE FROM platform_audit_logs WHERE id = ?", auditId);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private TransactionTemplate tx() {
        if (tx == null) {
            tx = new TransactionTemplate(transactionManager);
        }
        return tx;
    }

    /**
     * Bind tenant_id into SecurityContextHolder so TenantRlsConnectionHandler
     * applies SET LOCAL app.tenant_id inside a tx.executeWithoutResult block.
     */
    private void bindTenantContext(UUID tenantId) {
        var token = new UsernamePasswordAuthenticationToken(
                "test-" + tenantId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", UUID.randomUUID().toString()));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
