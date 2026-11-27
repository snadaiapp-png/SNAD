package com.sanad.platform.security.rls;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Dynamic-proxy invocation handler that binds the HMAC-SIGNED tenant context
 * to the underlying physical connection the first time a statement is created
 * within a transaction (V20261124_1 contract — G1 F-INT-01/03/04).
 *
 * <p>The binding is NOT the raw untrusted GUC write
 * {@code SET LOCAL app.tenant_id = ...} — after V20261124_2 the enforced
 * policies read {@code public.app_tenant_ctx_text()}, which re-verifies the
 * HMAC against the caller's OWN {@code txid_current()} and
 * {@code pg_backend_pid()}; a raw GUC write without a valid mac returns NULL
 * from the validator and every tenant row stays hidden. The handler therefore
 * executes the full application-side binding protocol on the physical
 * connection:</p>
 *
 * <ol>
 *   <li>{@code SELECT ctx_txid, ctx_pid FROM public.app_tenant_ctx_slot()} —
 *       one round trip for mac minting.</li>
 *   <li>{@link TenantContextSigner#macHex(UUID, String, int)} — HMAC-SHA256
 *       over {@code tenant:txid:pid} with the environment-provisioned key.</li>
 *   <li>{@code SELECT public.app_bind_tenant_ctx(?::uuid, ?)} — the SECURITY
 *       DEFINER verifier validates the mac against the caller's own txid/pid
 *       and sets {@code app.tenant_id} + {@code app.ctx_mac}
 *       transaction-local; any rejection raises 22000 and fails the
 *       transaction.</li>
 * </ol>
 *
 * <p>The tenant id always comes from the AUTHENTICATED principal (JWT claims
 * placed into {@code Authentication.getDetails()} by the JWT filter) — never
 * from user-supplied request data at this layer.</p>
 *
 * <h3>Activation conditions</h3>
 * <ul>
 *   <li>{@code autoCommit == false} — we are inside a Spring
 *       {@code @Transactional} boundary (the primary data-access path).</li>
 *   <li>A tenant id is present in the {@link SecurityContextHolder}.</li>
 * </ul>
 *
 * <p>If either condition is false the proxy is a transparent pass-through for
 * non-transactional reads; inside a transaction without tenant context the
 * strict contract below applies.</p>
 *
 * <h3>G1-i red-contract closure (2026-11-25)</h3>
 * <p>The historical silent pass-through for a statement issued inside an
 * active transaction with NO tenant context is rejected loudly: after
 * V20261124_2 the RLS policies fail closed on unbound sessions, so such a
 * statement could only ever return empty results — silently masking a
 * misrouted or misconfigured call. Tenant-data paths must carry an
 * authenticated tenant; genuinely pre-authentication statements (login,
 * token issuance, session bootstrap) must be explicitly classified via
 * {@link TenantRlsPreAuthScope}, which keeps this path transparent. The
 * strict behavior is governed by {@link TenantRlsSecurityConfig}
 * (F-INT-06): disabling it on the production path requires the full
 * governance triple; there is no unconditional system-property override.</p>
 *
 * <h3>Fail-closed behavior</h3>
 * <ul>
 *   <li>No signer configured → bind refused with an explicit SQLException
 *       (no raw-GUC fallback — the raw GUC is untrusted by design).</li>
 *   <li>Bind rejection from the database (no ACTIVE key validates the mac)
 *       → SQLException; the surrounding transaction fails and rolls back.</li>
 * </ul>
 */
final class TenantRlsConnectionHandler implements InvocationHandler {

    private static final String SLOT_SQL =
            "SELECT ctx_txid, ctx_pid FROM public.app_tenant_ctx_slot()";
    private static final String BIND_SQL =
            "SELECT public.app_bind_tenant_ctx(?::uuid, ?)";

    private final Connection delegate;
    private UUID tenantAppliedFor = null;

    TenantRlsConnectionHandler(Connection delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        // Intercept statement-creation entry points to guarantee the SIGNED
        // context is bound before any SQL reaches the engine.
        if (isStatementCreation(name)) {
            ensureTenantContext();
        }
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException exception) {
            // Reflective JDBC delegation must preserve checked SQLException,
            // rather than exposing UndeclaredThrowableException to callers.
            throw exception.getCause();
        } finally {
            // SET LOCAL (issued inside app_bind_tenant_ctx) ends at
            // commit/rollback. A pooled logical connection may participate in
            // another transaction after either boundary.
            if ("commit".equals(name) || "rollback".equals(name)
                    || "close".equals(name) || "setAutoCommit".equals(name)) {
                tenantAppliedFor = null;
                // A manually-bound tenant must NEVER escape its transaction on
                // a reused pool thread. The Spring transaction synchronization
                // provides a second safety net for managed transactions.
                TenantRlsSignedBinding.clear();
            }
        }
    }

    private boolean isStatementCreation(String methodName) {
        return "createStatement".equals(methodName)
                || "prepareStatement".equals(methodName)
                || "prepareCall".equals(methodName)
                || "nativeSQL".equals(methodName);
    }

    private void ensureTenantContext() throws SQLException {
        // Only apply within an explicit transaction — SET LOCAL (inside
        // app_bind_tenant_ctx) has no effect (and would error) in autocommit.
        if (delegate.getAutoCommit()) {
            return;
        }
        // F-INT-01/03/04 — background/control-plane paths: a HMAC-VERIFIED
        // signed binding established by TenantRlsTransactionContext governs
        // this transaction. The database validated the mac against the
        // caller's own txid/pid, so the recorded tenant is a DB-verified
        // fact. While the binding protocol is in flight, its own statements
        // pass through (they are the infrastructure establishing the scope).
        if (TenantRlsSignedBinding.protocolInFlight()) {
            return;
        }
        UUID signedTenant = TenantRlsSignedBinding.currentTenant();
        if (signedTenant != null) {
            // A thread-local marker is never sufficient evidence for another
            // connection/transaction. Re-check the DB-verified tenant on THIS
            // physical connection before trusting the marker. A leaked marker
            // after an unwrapped manual connection must fail closed.
            if (!signedTenant.equals(tenantAppliedFor)) {
                try (Statement validation = delegate.createStatement();
                     ResultSet result = validation.executeQuery(
                             "SELECT public.app_tenant_ctx_text()")) {
                    if (!result.next() || !signedTenant.toString().equals(result.getString(1))) {
                        TenantRlsSignedBinding.clear();
                        throw new SQLException("Signed tenant marker does not match this JDBC transaction");
                    }
                }
            }
            UUID authenticatedTenant = currentTenantId();
            if (authenticatedTenant != null && !authenticatedTenant.equals(signedTenant)) {
                throw new SQLException("Signed tenant binding conflicts with authenticated principal");
            }
            tenantAppliedFor = signedTenant;
            return;
        }
        UUID tenantId = currentTenantId();
        if (tenantAppliedFor != null) {
            // A DIFFERENT non-null tenant is a hard violation: the actor swapped
            // identity inside one transaction. A CLEARED context (null) is not an
            // identity change: the previously applied transaction-local binding
            // still scopes every statement of this transaction to the original
            // tenant, so RLS remains fail-closed. Deferred flushes and
            // post-authentication verification queries therefore continue under
            // the applied scope.
            if (tenantId != null && !tenantAppliedFor.equals(tenantId)) {
                throw new SQLException("Tenant identity changed inside an active RLS transaction");
            }
            return;
        }
        if (tenantId == null) {
            // G1-i red-contract closure: inside an active transaction a
            // statement without tenant context can only ever return empty
            // results (the V20261124_2 policies fail closed on unbound
            // sessions). Silent pass-through masks misrouting — reject loudly
            // unless the caller explicitly classified this path as pre-auth.
            TenantRlsRuntimePolicy.Policy policy = TenantRlsRuntimePolicy.current();
            if (TenantRlsPreAuthScope.isActive() || !policy.strictMissingContext()) {
                return;
            }
            throw new SQLException(
                    "Tenant RLS context missing for an in-transaction statement: "
                            + "tenant-data paths must carry an authenticated tenant, or the "
                            + "pre-authentication path must be explicitly declared with "
                            + "TenantRlsPreAuthScope");
        }
        bindSignedContext(tenantId);
        tenantAppliedFor = tenantId;
    }

    /**
     * Execute the signed binding protocol on the physical connection.
     * F-INT-01/03/04: the application itself mints the mac and calls the
     * database verifier — no raw GUC write, no fallback.
     */
    private void bindSignedContext(UUID tenantId) throws SQLException {
        TenantContextSigner signer = TenantRlsRuntimePolicy.current().signer();
        if (signer == null) {
            throw new SQLException(
                    "Tenant RLS signed-context binding refused: no HMAC signing key is configured "
                            + "(snad.rls.context-hmac-key / SANAD_TENANT_CTX_HMAC_KEY). The raw tenant "
                            + "GUC is untrusted after V20261124_2 and cannot be used as a fallback.");
        }
        try {
            String txid;
            int pid;
            try (Statement slot = delegate.createStatement();
                 ResultSet rs = slot.executeQuery(SLOT_SQL)) {
                if (!rs.next()) {
                    throw new SQLException("app_tenant_ctx_slot() returned no row — binding refused");
                }
                txid = rs.getString("ctx_txid");
                pid = rs.getInt("ctx_pid");
            }
            String mac = signer.macHex(tenantId, txid, pid);
            try (PreparedStatement bind = delegate.prepareStatement(BIND_SQL)) {
                bind.setString(1, tenantId.toString());
                bind.setString(2, mac);
                try (ResultSet rs = bind.executeQuery()) {
                    if (!rs.next() || !rs.getBoolean(1)) {
                        throw new SQLException(
                                "Tenant RLS signed-context bind REJECTED by the database: no ACTIVE key "
                                        + "in platform_tenant_ctx_keys validates the supplied mac for this "
                                        + "transaction. The transaction fails closed.");
                    }
                }
            }
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Tenant RLS signed-context binding failed (fail-closed)", e);
        }
    }

    /**
     * Read the tenant id from the Spring security context.
     *
     * <p>Mirrors the extraction logic in {@code SpringTenantContextAdapter}
     * and {@code CrmContractController#requiredTenant} — the tenant id is
     * placed into {@code Authentication.getDetails()} by
     * {@code JwtAuthenticationFilter} after JWT validation.</p>
     */
    private static UUID currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (!(auth.getDetails() instanceof java.util.Map<?, ?> details)) {
            return null;
        }
        Object raw = details.get("tenant_id");
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
