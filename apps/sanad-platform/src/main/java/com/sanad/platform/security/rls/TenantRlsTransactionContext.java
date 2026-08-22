package com.sanad.platform.security.rls;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Applies the trusted tenant GUC ({@code app.tenant_id}) to the current
 * transaction for service-layer paths that intentionally operate without
 * a {@link org.springframework.security.core.Authentication} in the
 * {@link org.springframework.security.core.context.SecurityContextHolder}
 * (background workers, control-plane services, scheduled import jobs).
 *
 * <h3>THIS CLASS IS NOT AN AUTHORIZATION MECHANISM</h3>
 *
 * <p>This component merely attaches a tenant scope to the current
 * transaction. It does NOT verify that the caller is allowed to operate
 * on that tenant — that decision belongs to upstream callers (control
 * plane guard, capability evaluation, JWT filter, etc.). Calling
 * {@link #applyForCurrentTransaction(UUID)} with an arbitrary tenant id
 * will scope the transaction to that tenant; the caller is responsible
 * for ensuring the {@code trustedTenantId} argument is trustworthy.
 *
 * <h3>Nested invocation — last call wins</h3>
 *
 * <p>Each invocation re-issues {@code SELECT set_config('app.tenant_id', ?, true)}
 * with {@code is_local=true} so the GUC is scoped to the current
 * transaction. If the same transaction calls this method multiple times
 * (e.g., a service that calls another service that also applies the
 * scope), the <em>last</em> call wins — the previous GUC value is
 * overwritten. This is intentional: trusted callers may legitimately
 * re-scope a transaction when entering a sub-flow that targets a
 * different tenant.
 *
 * <h3>Connection binding</h3>
 *
 * <p>The GUC is set via the {@link JdbcTemplate} bound to this instance.
 * If the surrounding {@code @Transactional} boundary uses a different
 * {@link JdbcTemplate} or {@link javax.sql.DataSource} (e.g., a
 * dedicated read-replica), the GUC may not apply to that connection.
 * Production callers should always pass the same {@link JdbcTemplate}
 * that backs the data-access layer for the entities being mutated.
 *
 * <h3>Fail-closed RLS interaction</h3>
 *
 * <p>The CRM collaboration tables ({@code crm_entity_participants},
 * {@code crm_event_outbox}, {@code crm_timeline_events}) and all G8
 * mobile/call sync tables use {@code FORCE ROW LEVEL SECURITY} with a
 * fail-closed policy ({@code tenant_id = current_setting('app.tenant_id',
 * true)::UUID}). When the GUC is unset, the comparison is NULL = false,
 * so every read returns zero rows and every write is rejected. Trusted
 * service paths MUST therefore call this method (or rely on
 * {@link TenantRlsConnectionHandler}) before touching those tables.
 */
@Component
public class TenantRlsTransactionContext {

    private final JdbcTemplate jdbcTemplate;

    public TenantRlsTransactionContext(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Apply the trusted tenant id to the current transaction's GUC.
     *
     * @param trustedTenantId the tenant id to scope the transaction to.
     *        Must be non-null and a valid UUID.
     * @throws IllegalStateException if called outside a Spring-managed
     *         transaction. RLS relies on {@code SET LOCAL} scoping which is
     *         only meaningful inside a transaction; calling this method
     *         outside a transaction is a programming error.
     * @throws IllegalArgumentException if {@code trustedTenantId} is null.
     */
    public void applyForCurrentTransaction(UUID trustedTenantId) {
        if (trustedTenantId == null) {
            throw new IllegalArgumentException("trustedTenantId must not be null");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "TenantRlsTransactionContext.applyForCurrentTransaction must be called "
                            + "inside a Spring-managed transaction (SET LOCAL app.tenant_id "
                            + "is only meaningful within a transaction boundary).");
        }
        // set_config(name, value, is_local) — is_local=true scopes the GUC
        // to the current transaction, mirroring SET LOCAL semantics. The
        // value is a well-formed UUID string supplied by a trusted caller.
        jdbcTemplate.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)",
                String.class, trustedTenantId.toString());
        // Verify the GUC was actually applied to the current connection —
        // a defensive check that catches DataSource misconfiguration (e.g.,
        // a different physical Connection being used for the verification
        // query than for the previous set_config call).
        String applied = jdbcTemplate.queryForObject(
                "SELECT current_setting('app.tenant_id', true)", String.class);
        if (applied == null || !applied.equals(trustedTenantId.toString())) {
            throw new IllegalStateException(
                    "TenantRlsTransactionContext could not verify the GUC was applied — "
                            + "expected " + trustedTenantId + " but got "
                            + (applied == null ? "<null>" : applied)
                            + ". The DataSource is likely returning different physical "
                            + "Connections across queries; ensure a pooled DataSource "
                            + "(HikariCP) bound to a single transaction is used.");
        }
    }
}
