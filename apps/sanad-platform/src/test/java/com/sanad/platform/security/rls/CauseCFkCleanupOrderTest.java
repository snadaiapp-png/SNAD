package com.sanad.platform.security.rls;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cause C verification — the cleanup helper {@code purgeForensicResidue} in
 * {@code G1GDynamic403ForensicPostgresTest} deletes {@code users} BEFORE
 * {@code refresh_tokens}, violating the FK constraint
 * {@code fk_refresh_tokens_user} which blocks the DELETE because
 * {@code refresh_tokens} rows still reference the user.
 *
 * <h3>Hypothesis to prove</h3>
 * <ol>
 *   <li>The FK constraint {@code fk_refresh_tokens_user} exists on
 *       {@code refresh_tokens.user_id → users.id}.
 *   <li>DELETE FROM users (when refresh_tokens rows still reference the user)
 *       fails with SQL state 23503 (foreign_key_violation).
 *   <li>DELETE FROM refresh_tokens FIRST, then DELETE FROM users, succeeds.
 *   <li>This is NOT an RLS issue — it's a pure cleanup-ordering bug.
 * </ol>
 *
 * <p>The test uses real PostgreSQL with the actual app role. No mocks.
 */
@SpringBootTest
@ActiveProfiles("local")
class CauseCFkCleanupOrderTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    /**
     * Prove C1: the FK constraint {@code fk_refresh_tokens_user} exists on
     * refresh_tokens.user_id → users.id.
     */
    @Test
    @DisplayName("C1: FK constraint fk_refresh_tokens_user exists on refresh_tokens.user_id → users.id")
    void fkConstraintExists() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint "
                        + "WHERE conname = 'fk_refresh_tokens_user' "
                        + "AND conrelid = 'refresh_tokens'::regclass "
                        + "AND confrelid = 'users'::regclass",
                Long.class);
        assertThat(count)
                .as("FK fk_refresh_tokens_user must exist on refresh_tokens.user_id → users.id")
                .isEqualTo(1L);
    }

    /**
     * Prove C2: DELETE FROM users fails when refresh_tokens rows still
     * reference the user (FK violation, SQL state 23503).
     *
     * <p>This reproduces the failure in
     * {@code G1GDynamic403ForensicPostgresTest.scenarioB_tenantAdminExecutiveRequestIsDenied403}.
     */
    @Test
    @DisplayName("C2: DELETE FROM users fails with FK violation when refresh_tokens still references the user")
    void deleteFromUsersFailsWhenRefreshTokensReferenceExists() {
        TransactionTemplate tx = tx();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID refreshTokenId = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            // Seed tenant + user + refresh_token (FK reference)
            jdbc.update(
                    "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tenantId, "Cause C Tenant",
                    "cause-c-" + tenantId.toString().substring(0, 8),
                    Timestamp.from(now), Timestamp.from(now));
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update(
                        "INSERT INTO users (id, tenant_id, email, display_name, status, "
                                + "password_hash, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'Cause C User', 'ACTIVE', 'dummy', ?, ?)",
                        userId, tenantId,
                        "cause-c-" + userId.toString().substring(0, 8) + "@example.test",
                        Timestamp.from(now), Timestamp.from(now));
            });
            // Insert refresh_token referencing the user (RLS may or may not apply to
            // refresh_tokens — set GUC regardless to be safe)
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update(
                        "INSERT INTO refresh_tokens (id, tenant_id, user_id, token_hash, "
                                + "status, expires_at, created_at) "
                                + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                        refreshTokenId, tenantId, userId,
                        "dummy-hash-" + refreshTokenId.toString().substring(0, 8),
                        Timestamp.from(now.plusSeconds(3600)),
                        Timestamp.from(now));
            });

            // Attempt DELETE FROM users FIRST (wrong order) — must fail with FK violation
            assertThatThrownBy(() -> {
                tx.executeWithoutResult(s -> {
                    jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                            String.class, tenantId.toString());
                    jdbc.update("DELETE FROM users WHERE id = ?", userId);
                });
            })
                    .as("DELETE FROM users must fail with FK violation when refresh_tokens reference exists")
                    .hasMessageContaining("violates foreign key constraint")
                    .hasMessageContaining("fk_refresh_tokens_user");
        } finally {
            // Cleanup: correct order — refresh_tokens FIRST, then users
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update("DELETE FROM refresh_tokens WHERE id = ?", refreshTokenId);
                jdbc.update("DELETE FROM users WHERE id = ?", userId);
            });
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
    }

    /**
     * Prove C3: DELETE FROM refresh_tokens FIRST, then DELETE FROM users, succeeds.
     *
     * <p>This is the minimum fix for Cause C — reorder the cleanup to delete
     * child rows before parent rows.
     */
    @Test
    @DisplayName("C3: DELETE FROM refresh_tokens FIRST, then DELETE FROM users, succeeds (minimum fix)")
    void deleteFromRefreshTokensFirstThenUsersSucceeds() {
        TransactionTemplate tx = tx();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID refreshTokenId = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            // Seed
            jdbc.update(
                    "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tenantId, "Cause C3 Tenant",
                    "cause-c3-" + tenantId.toString().substring(0, 8),
                    Timestamp.from(now), Timestamp.from(now));
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update(
                        "INSERT INTO users (id, tenant_id, email, display_name, status, "
                                + "password_hash, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'Cause C3 User', 'ACTIVE', 'dummy', ?, ?)",
                        userId, tenantId,
                        "cause-c3-" + userId.toString().substring(0, 8) + "@example.test",
                        Timestamp.from(now), Timestamp.from(now));
                jdbc.update(
                        "INSERT INTO refresh_tokens (id, tenant_id, user_id, token_hash, "
                                + "status, expires_at, created_at) "
                                + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                        refreshTokenId, tenantId, userId,
                        "dummy-hash-" + refreshTokenId.toString().substring(0, 8),
                        Timestamp.from(now.plusSeconds(3600)),
                        Timestamp.from(now));
            });

            // Correct order: refresh_tokens FIRST, then users (both in same tx with GUC set)
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update("DELETE FROM refresh_tokens WHERE id = ?", refreshTokenId);
                jdbc.update("DELETE FROM users WHERE id = ?", userId);
            });

            // Verify both rows are gone
            Integer userCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
            Integer tokenCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM refresh_tokens WHERE id = ?", Integer.class, refreshTokenId);
            assertThat(userCount).as("user row must be deleted").isEqualTo(0);
            assertThat(tokenCount).as("refresh_token row must be deleted").isEqualTo(0);
        } finally {
            // Final cleanup (in case the test left rows behind)
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update("DELETE FROM refresh_tokens WHERE id = ?", refreshTokenId);
                jdbc.update("DELETE FROM users WHERE id = ?", userId);
            });
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private TransactionTemplate tx() {
        if (tx == null) {
            tx = new TransactionTemplate(transactionManager);
        }
        return tx;
    }
}
