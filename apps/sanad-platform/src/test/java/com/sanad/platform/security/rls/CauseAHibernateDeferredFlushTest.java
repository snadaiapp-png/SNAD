package com.sanad.platform.security.rls;

import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cause A verification — Hibernate's deferred-flush model is structurally
 * incompatible with multi-tenant JPA fixtures under {@code @Transactional} +
 * {@link TenantRlsConnectionHandler}'s per-tx GUC setting model.
 *
 * <h3>Hypothesis to prove</h3>
 * <ol>
 *   <li>{@code userRepository.save(userA)} with SecurityContextHolder=tenantA
 *       does NOT immediately execute INSERT INTO users — Hibernate defers.
 *   <li>When SecurityContextHolder is rebound to tenantB mid-tx, the deferred
 *       INSERT for userA still flushes later (at tx commit or at the next
 *       query). At flush time, the handler fires and EITHER throws
 *       "Tenant identity changed" (if tenantAppliedFor was set during a prior
 *       query) OR sets GUC=tenantB and the row.tenant_id=tenantA INSERT fails
 *       RLS rejection.
 *   <li>In contrast, raw {@code JdbcTemplate.update("INSERT INTO users...")}
 *       with preceding {@code set_config('app.tenant_id', tenantA, true)} on
 *       the SAME tx-bound Connection executes the INSERT immediately with
 *       the correct GUC, with no deferral.
 * </ol>
 *
 * <p>The test uses real PostgreSQL with the actual {@code sanad} app role
 * (verified by {@link TenantLifecycleRlsContractTest#verifyRlsEnabled}).
 * No mocks, no SUPERUSER bypass.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CauseAHibernateDeferredFlushTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    /**
     * Prove A1: when the SecurityContextHolder is bound to tenantA and the test
     * re-binds to tenantB mid-tx (before any flush), the deferred INSERT for
     * userA fails at flush time because the GUC at flush time is tenantB
     * (the LAST set context), but row.tenant_id=tenantA.
     *
     * <p>This is the structural cause for the failures in
     * {@code AccessPersistenceIntegrationTest},
     * {@code OrganizationMembershipUserLinkRepositoryTest},
     * {@code AuthApiIntegrationTest} setUp, and
     * {@code UserRepositoryTest}.
     */
    @Test
    @DisplayName("A1: Hibernate deferred INSERT flushes with the LAST set tenant context, not the context at save() time")
    void hibernateDeferredInsertFlushesWithLastSetContext() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            // Seed tenants (no RLS on tenants)
            for (UUID tid : List.of(tenantA, tenantB)) {
                jdbc.update(
                        "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                        tid, "Tenant " + tid.toString().substring(0, 8),
                        "t-" + tid.toString().substring(0, 8),
                        Timestamp.from(now), Timestamp.from(now));
            }

            // 1. bindContext(tenantA)
            bindTenantContext(tenantA);
            // 2. save(userA) — Hibernate DEFERS the INSERT (no prepareStatement call yet)
            User userAEntity = new User(tenantA,
                    "cause-a-" + userA.toString().substring(0, 8) + "@example.test",
                    "Cause A UserA", UserStatus.ACTIVE);
            userRepository.save(userAEntity);

            // 3. re-bind to tenantB mid-tx — SecurityContextHolder now reflects tenantB
            bindTenantContext(tenantB);
            // 4. save(userB) — Hibernate DEFERS the INSERT (still no prepareStatement call)
            User userBEntity = new User(tenantB,
                    "cause-b-" + userB.toString().substring(0, 8) + "@example.test",
                    "Cause B UserB", UserStatus.ACTIVE);
            userRepository.save(userBEntity);

            // 5. Trigger flush via saveAndFlush — Hibernate auto-flushes pending INSERTs.
            //    This is the FIRST prepareStatement call in this tx.
            //    Handler fires:
            //      - autoCommit=false ✓
            //      - tenantAppliedFor=NULL (first prepareStatement in this tx)
            //      - currentTenantId()=tenantB (the LAST bind)
            //      - SET LOCAL app.tenant_id='tenantB', tenantAppliedFor=tenantB
            //    Then Hibernate flushes:
            //      - INSERT INTO users (userA, tenant_id=tenantA) → handler sees
            //        tenantAppliedFor=tenantB, currentTenantId()=tenantB → match, no throw
            //      - INSERT runs with GUC=tenantB
            //      - Row tenant_id=tenantA → RLS WITH CHECK rejects (tenantA != tenantB)
            assertThatThrownBy(() -> {
                // Use a JdbcTemplate query that triggers Hibernate's auto-flush by
                // first calling a Spring Data JPA repository method that flushes.
                // SaveAndFlush forces Hibernate to flush pending INSERTs.
                userRepository.saveAndFlush(userBEntity);
            })
                    .as("Deferred INSERT for userA (tenant_id=tenantA) flushes with GUC=tenantB → RLS rejects")
                    .hasMessageContaining("new row violates row-level security policy for table \"users\"");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Prove A2: in contrast, raw {@code JdbcTemplate.update("INSERT INTO users...")}
     * with preceding {@code set_config('app.tenant_id', tenantA, true)} on the SAME
     * tx-bound Connection executes the INSERT immediately with the correct GUC.
     *
     * <p>This is the production-validated Pattern A2 fix (proven across 23 fixed
     * test classes at commit 203970b3). The INSERT succeeds because:
     * <ol>
     *   <li>{@code set_config(is_local=true)} scopes the GUC to the current tx
     *   <li>{@code jdbc.update("INSERT...")} on the same tx-bound Connection
     *       uses the GUC just set
     *   <li>No deferral — the INSERT executes immediately
     * </ol>
     */
    @Test
    @DisplayName("A2: raw JdbcTemplate INSERT with set_config on same Connection succeeds (Pattern A2 mechanics)")
    void rawJdbcTemplateInsertWithSetConfigSucceeds() {
        TransactionTemplate tx = tx();
        UUID tenantA = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update(
                    "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tenantA, "Tenant A",
                    "t-" + tenantA.toString().substring(0, 8),
                    Timestamp.from(now), Timestamp.from(now));

            // Pattern A2: set_config + INSERT in same tx → INSERT executes immediately
            // with the correct GUC. No Hibernate deferral.
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject(
                        "SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantA.toString());
                jdbc.update(
                        "INSERT INTO users (id, tenant_id, email, display_name, status, "
                                + "password_hash, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'Cause A User', 'ACTIVE', 'dummy', ?, ?)",
                        userA, tenantA,
                        "cause-a-raw-" + userA.toString().substring(0, 8) + "@example.test",
                        Timestamp.from(now), Timestamp.from(now));
            });

            // Verify the row was persisted (no SecurityContextHolder needed — raw JDBC)
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userA);
            assertThat(count).as("INSERT must succeed with Pattern A2 mechanics").isEqualTo(1);
        } finally {
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantA.toString());
                jdbc.update("DELETE FROM users WHERE id = ?", userA);
            });
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantA);
        }
    }

    /**
     * Prove A3: the GUC at INSERT execution time can be inspected via
     * {@code current_setting('app.tenant_id', true)}. This proves the
     * timing claim — when the INSERT executes, the GUC has a specific value
     * that we can capture.
     */
    @Test
    @DisplayName("A3: GUC at INSERT execution can be inspected via current_setting()")
    void gucAtInsertExecutionCanBeInspected() {
        TransactionTemplate tx = tx();
        UUID tenantA = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update(
                    "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tenantA, "Tenant A",
                    "t-" + tenantA.toString().substring(0, 8),
                    Timestamp.from(now), Timestamp.from(now));

            // Capture the GUC at INSERT execution time by using a single SQL statement
            // that does INSERT ... RETURNING current_setting('app.tenant_id', true)
            String gucAtInsert = tx.execute(s -> {
                jdbc.queryForObject(
                        "SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantA.toString());
                return jdbc.queryForObject(
                        "INSERT INTO users (id, tenant_id, email, display_name, status, "
                                + "password_hash, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'Cause A User', 'ACTIVE', 'dummy', ?, ?) "
                                + "RETURNING current_setting('app.tenant_id', true) AS guc_at_insert",
                        String.class,
                        userA, tenantA,
                        "cause-a-guc-" + userA.toString().substring(0, 8) + "@example.test",
                        Timestamp.from(now), Timestamp.from(now));
            });

            assertThat(gucAtInsert)
                    .as("GUC at INSERT execution must match the tenant_id set by set_config")
                    .isEqualTo(tenantA.toString());
        } finally {
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantA.toString());
                jdbc.update("DELETE FROM users WHERE id = ?", userA);
            });
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantA);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private TransactionTemplate tx() {
        if (tx == null) {
            tx = new TransactionTemplate(transactionManager);
        }
        return tx;
    }

    private void bindTenantContext(UUID tenantId) {
        var token = new UsernamePasswordAuthenticationToken(
                "test-" + tenantId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", UUID.randomUUID().toString()));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
