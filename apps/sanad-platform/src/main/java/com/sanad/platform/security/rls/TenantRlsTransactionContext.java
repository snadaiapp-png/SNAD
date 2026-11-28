package com.sanad.platform.security.rls;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Binds the HMAC-SIGNED tenant context to the current transaction for
 * service-layer paths that intentionally operate without a
 * {@link org.springframework.security.core.Authentication} in the
 * {@link org.springframework.security.core.context.SecurityContextHolder}
 * (background workers, control-plane services, scheduled import jobs,
 * administrative services) — G1 F-INT-01/03/04.
 *
 * <p>This is the application-side completion of the V20261124_1 contract:
 * instead of writing the raw untrusted GUC
 * ({@code set_config('app.tenant_id', ...)} — forgeable by any runtime SQL
 * session and rejected by the enforced V20261124_2 policies), the trusted
 * caller executes the full binding protocol inside the CURRENT
 * transaction:</p>
 *
 * <ol>
 *   <li>{@code SELECT ctx_txid, ctx_pid FROM public.app_tenant_ctx_slot()} —
 *       one round trip for mac minting on the transaction's own connection.</li>
 *   <li>{@link TenantContextSigner#macHex(UUID, String, int)} — HMAC-SHA256
 *       over {@code tenant:txid:pid} with the environment-provisioned key.</li>
 *   <li>{@code SELECT public.app_bind_tenant_ctx(?::uuid, ?)} — the SECURITY
 *       DEFINER verifier validates the mac (all ACTIVE keys, latest first)
 *       and sets {@code app.tenant_id} + {@code app.ctx_mac}
 *       transaction-local; failure raises 22000 and fails the transaction.</li>
 *   <li>End-to-end verification: {@code SELECT public.app_tenant_ctx_text()}
 *       must return the bound tenant — this reads the HMAC-VALIDATED value
 *       the enforced policies consume, not the raw GUC, so it proves the
 *       whole chain (slot → mint → verify) on the real connection.</li>
 * </ol>
 *
 * <h3>THIS METHOD IS NOT AN AUTHORIZATION MECHANISM</h3>
 *
 * <p>This component merely attaches a tenant scope to the current
 * transaction. It does NOT verify that the caller is allowed to operate
 * on that tenant — that decision belongs to upstream callers (control
 * plane guard, capability evaluation, JWT filter, etc.). Calling
 * {@link #applyForCurrentTransaction(UUID)} with an arbitrary tenant id
 * will scope the transaction to that tenant; the caller is responsible
 * for ensuring the {@code trustedTenantId} argument is trustworthy.</p>
 *
 * <h3>Nested invocation — last call wins</h3>
 *
 * <p>Each invocation re-executes the binding protocol with
 * {@code is_local=true} semantics (the GUCs are transaction-local). If the
 * same transaction calls this method multiple times (e.g., a service that
 * calls another service that also applies the scope), the <em>last</em>
 * call wins — the previous binding is overwritten. This is intentional:
 * trusted callers may legitimately re-scope a transaction when entering a
 * sub-flow that targets a different tenant.</p>
 *
 * <h3>Connection binding</h3>
 *
 * <p>The protocol runs on the {@link JdbcTemplate} bound to this instance.
 * If the surrounding {@code @Transactional} boundary uses a different
 * {@link JdbcTemplate} or {@link javax.sql.DataSource} (e.g., a
 * dedicated read-replica), the binding may not apply to that connection.
 * Production callers should always pass the same {@link JdbcTemplate}
 * that backs the data-access layer for the entities being mutated.</p>
 *
 * <h3>Fail-closed RLS interaction</h3>
 *
 * <p>The CRM collaboration tables, all G8 mobile/call sync tables and every
 * tenant-scoped table rewritten by V20261124_2 use
 * {@code FORCE ROW LEVEL SECURITY} with policies reading
 * {@code public.app_tenant_ctx_text()}. When no signed context is bound, the
 * validator returns NULL, every read returns zero rows and every write is
 * rejected. Trusted service paths MUST therefore call this method (or rely
 * on {@link TenantRlsConnectionHandler}) before touching those tables.</p>
 */
@Component
public class TenantRlsTransactionContext {

    private static final String SLOT_SQL =
            "SELECT ctx_txid, ctx_pid FROM public.app_tenant_ctx_slot()";
    private static final String BIND_SQL =
            "SELECT public.app_bind_tenant_ctx(?::uuid, ?)";
    private static final String VALIDATE_SQL =
            "SELECT public.app_tenant_ctx_text()";

    private final JdbcTemplate jdbcTemplate;

    public TenantRlsTransactionContext(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Bind the signed tenant context to the current transaction.
     *
     * @param trustedTenantId the tenant id to scope the transaction to.
     *        Must be non-null and a valid UUID.
     * @throws IllegalStateException if called outside a Spring-managed
     *         transaction (transaction-local binding is only meaningful
     *         inside a transaction boundary), or if no HMAC signing key is
     *         configured, or if the end-to-end HMAC-validated verification
     *         does not confirm the binding.
     * @throws IllegalArgumentException if {@code trustedTenantId} is null.
     * @throws org.springframework.dao.DataAccessException if the database
     *         rejects the bind (no ACTIVE key validates the mac) — the
     *         transaction fails closed.
     */
    public void applyForCurrentTransaction(UUID trustedTenantId) {
        if (trustedTenantId == null) {
            throw new IllegalArgumentException("trustedTenantId must not be null");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "TenantRlsTransactionContext.applyForCurrentTransaction must be called "
                            + "inside a Spring-managed transaction (transaction-local context "
                            + "binding is only meaningful within a transaction boundary).");
        }
        TenantContextSigner signer = TenantRlsRuntimePolicy.current().signer();
        if (signer == null) {
            throw new IllegalStateException(
                    "Tenant RLS signed-context binding refused: no HMAC signing key is configured "
                            + "(snad.rls.context-hmac-key / SANAD_TENANT_CTX_HMAC_KEY). The raw tenant "
                            + "GUC is untrusted after V20261124_2 and cannot be used as a fallback.");
        }
        // The protocol's own statements (slot/bind/validate) run through the
        // same wrapped pool; mark them as binding infrastructure so the strict
        // handler passes them through (F-INT-01/03/04).
        TenantRlsSignedBinding.beginProtocol();
        try {
            // 1. Slot — one round trip for mac minting (txid of THIS transaction,
            //    pid of THIS connection).
            Slot slot = jdbcTemplate.queryForObject(SLOT_SQL, (rs, rowNum) -> readSlot(rs));
            // 2. Mint the mac in the application (the key NEVER reaches SQL).
            String mac = signer.macHex(trustedTenantId, slot.txid(), slot.pid());
            // 3. Bind — the SECURITY DEFINER verifier re-computes the expected mac
            //    from the caller's OWN txid/pid; only a match sets the context.
            Boolean bound = jdbcTemplate.queryForObject(
                    BIND_SQL, Boolean.class, trustedTenantId.toString(), mac);
            if (!Boolean.TRUE.equals(bound)) {
                throw new IllegalStateException(
                        "Tenant RLS signed-context bind was not confirmed by the database "
                                + "(no ACTIVE key validated the mac) — transaction fails closed.");
            }
            // 4. End-to-end verification against the HMAC-VALIDATED value —
            //    catches DataSource misconfiguration (e.g., a different physical
            //    Connection being used across the protocol's round trips) and
            //    proves the binding the enforced policies will actually consume.
            String validated = jdbcTemplate.queryForObject(VALIDATE_SQL, String.class);
            if (validated == null || !validated.equals(trustedTenantId.toString())) {
                throw new IllegalStateException(
                        "TenantRlsTransactionContext could not verify the SIGNED context was applied — "
                                + "expected " + trustedTenantId + " but app_tenant_ctx_text() returned "
                                + (validated == null ? "<null>" : "a different value")
                                + ". The DataSource is likely returning different physical Connections "
                                + "across the binding protocol; ensure a pooled DataSource (HikariCP) "
                                + "bound to a single transaction is used.");
            }
        } catch (RuntimeException e) {
            // Fail closed: no binding is recorded on any failure path.
            TenantRlsSignedBinding.abort();
            throw e;
        }
        // Record the DB-verified binding for the strict handler and clear it at
        // the transaction boundary (pooled worker threads must not inherit it).
        TenantRlsSignedBinding.complete(trustedTenantId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            TenantRlsSignedBinding.clear();
                        }
                    });
        } else {
            TenantRlsSignedBinding.clear();
        }
    }

    private static Slot readSlot(ResultSet rs) throws SQLException {
        return new Slot(rs.getString("ctx_txid"), rs.getInt("ctx_pid"));
    }

    private record Slot(String txid, int pid) {
    }

    /**
     * Bind the HMAC-SIGNED tenant context to the current transaction of a
     * CALLER-MANAGED {@link java.sql.Connection} (G1 F-INT-03 — manual-JDBC
     * background workers: outbox dispatchers, IAM event consumers, audit
     * delivery workers that drive their own
     * {@code connection.setAutoCommit(false)} claim/finalize transactions
     * instead of a Spring transaction boundary).
     *
     * <p>Executes the exact production protocol on the given physical
     * connection: slot → HMAC-SHA256 mint in the application (the key NEVER
     * reaches SQL) → SECURITY DEFINER bind → end-to-end verification via
     * {@code app_tenant_ctx_text()}. Fail-closed at every step: any failure
     * aborts the protocol WITHOUT recording a binding, and the caller's
     * transaction must roll back.</p>
     *
     * <h3>Caller contract</h3>
     * <ul>
     *   <li>The connection must have {@code autoCommit == false} — the
     *       binding is transaction-local ({@code SET LOCAL}); with autocommit
     *       it would die with the bind statement itself. An
     *       {@link IllegalStateException} is thrown otherwise.</li>
     *   <li>Call this BEFORE the first tenant-data statement of the
     *       transaction (the strict connection handler rejects unclassified
     *       context-less statements).</li>
     *   <li>Call {@link #clearTransactionBinding()} when the transaction ends
     *       (commit/rollback, before releasing the pooled connection) so a
     *       pooled worker thread never carries a stale binding marker into
     *       the next transaction.</li>
     * </ul>
     *
     * @throws SQLException if the database rejects the bind (no ACTIVE key
     *         validates the mac) — the transaction fails closed.
     * @throws IllegalStateException if no signing key is configured, the
     *         connection is in autocommit, or the end-to-end verification
     *         does not confirm the binding.
     */
    public void bindConnection(java.sql.Connection connection, UUID trustedTenantId) throws SQLException {
        TenantSignedConnectionBinder.bind(connection, trustedTenantId);
    }

    /**
     * Clear the thread-scoped signed-binding record at a transaction
     * boundary of a CALLER-MANAGED connection (manual-JDBC workers must call
     * this after commit/rollback so pooled threads never inherit a stale
     * binding marker).
     */
    public void clearTransactionBinding() {
        TenantRlsSignedBinding.clear();
    }
}
