package com.sanad.platform.crm.integration.security;

import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable nonce/JTI replay protection for authenticated integration callbacks.
 *
 * <p>RLS contract (V20261111_1): {@code service_callback_replay} is
 * ENABLE+FORCE row-level-security with fail-closed tenant policies on the
 * trusted GUC {@code app.tenant_id}. This store therefore:</p>
 * <ul>
 *   <li>{@link #consume} — binds the trusted tenant scope (validated upstream
 *       by {@code WorkflowCallbackSecurity.verify} through the signed service
 *       JWT and its tenant-binding check) inside its own transaction before
 *       the INSERT. The INSERT policy {@code tenant_isolation_insert}
 *       enforces that the row's tenant matches the bound context; a missing or
 *       mismatched tenant context is rejected at the database level.</li>
 *   <li>{@link #deleteExpired} — the hourly sweep must remove expired replay
 *       evidence across ALL tenants, so it switches, inside its own
 *       transaction, to the dedicated least-privilege role
 *       {@code sanad_callback_replay_cleanup} (environment-provisioned,
 *       NOLOGIN, NOBYPASSRLS). The database bounds that role to
 *       {@code expires_at <= CURRENT_TIMESTAMP} rows of this table only via
 *       the {@code expired_cleanup_select}/{@code expired_cleanup_delete}
 *       policies; the runtime role itself keeps no standing cross-tenant
 *       privilege (its membership is non-inheriting). RESET ROLE is enforced
 *       in a finally block so a pooled connection can never leak the cleanup
 *       identity back to the application pool.</li>
 * </ul>
 */
@Component
public class CallbackReplayStore {

    /**
     * Environment-provisioned non-login role bounded by V20261111_1 to
     * expired rows of service_callback_replay (SELECT+DELETE only).
     */
    static final String CLEANUP_ROLE = "sanad_callback_replay_cleanup";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final TenantRlsTransactionContext tenantRlsContext;

    public CallbackReplayStore(
            JdbcTemplate jdbc,
            TransactionTemplate transactionTemplate,
            TenantRlsTransactionContext tenantRlsContext) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.tenantRlsContext = tenantRlsContext;
    }

    /**
     * Consumes a callback token identity exactly once. Returns false when either
     * the service/JTI tuple or service/nonce tuple was previously observed.
     */
    public boolean consume(
            UUID tenantId,
            String serviceName,
            String jti,
            String nonce,
            String correlationId,
            Instant expiresAt) {
        try {
            Integer inserted = transactionTemplate.execute(status -> {
                // Trusted scoped path (the same mechanism ModuleResetService uses
                // for this table): bind app.tenant_id inside this transaction so
                // the fail-closed INSERT policy of V20261111_1 can verify the row
                // belongs to the validated tenant.
                tenantRlsContext.applyForCurrentTransaction(tenantId);
                return jdbc.update(
                        "INSERT INTO service_callback_replay " +
                                "(tenant_id, service_name, jti, nonce, correlation_id, received_at, expires_at) " +
                                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)",
                        tenantId,
                        required(serviceName, "serviceName"),
                        required(jti, "jti"),
                        required(nonce, "nonce"),
                        required(correlationId, "correlationId"),
                        Timestamp.from(expiresAt));
            });
            return inserted != null && inserted == 1;
        } catch (DuplicateKeyException replay) {
            return false;
        }
    }

    @Scheduled(cron = "${sanad.service-auth.replay-cleanup-cron:0 17 * * * *}")
    public void deleteExpired() {
        transactionTemplate.executeWithoutResult(status ->
                jdbc.execute((ConnectionCallback<Void>) connection -> {
                    try (var statement = connection.createStatement()) {
                        // Bind the dedicated least-privilege cleanup identity for
                        // this transaction only. The expired_cleanup_delete
                        // policy (V20261111_1) makes any broader delete
                        // impossible at the database level.
                        statement.execute("SET ROLE " + CLEANUP_ROLE);
                        statement.executeUpdate(
                                "DELETE FROM service_callback_replay WHERE expires_at <= CURRENT_TIMESTAMP");
                    } finally {
                        // The pooled connection must NEVER return to the pool
                        // with the cleanup identity still active: that role is
                        // intentionally useless for every other table.
                        try (var reset = connection.createStatement()) {
                            reset.execute("RESET ROLE");
                        }
                    }
                    return null;
                }));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }
}
