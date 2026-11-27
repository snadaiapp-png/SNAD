package com.sanad.platform.security.rls;

import java.util.UUID;

/**
 * Thread-scoped record of the transaction's HMAC-VERIFIED tenant binding —
 * the integration point between {@link TenantRlsTransactionContext} (the
 * trusted service-layer protocol: background workers, control-plane services,
 * scheduled jobs — paths with NO {@code SecurityContextHolder} authentication)
 * and {@link TenantRlsConnectionHandler} (the strict statement gate).
 *
 * <h3>Why this exists (G1 F-INT-01/03/04 — background operations)</h3>
 *
 * <p>The strict handler rejects in-transaction statements without a tenant
 * context. A background worker has no authenticated principal — yet it binds
 * a VERIFIED signed context through {@code TenantRlsTransactionContext} and
 * then issues tenant-scoped statements on the same transaction. Without this
 * record the handler would (a) reject the binding protocol's own statements
 * and (b) reject every legitimate tenant-scoped statement AFTER a successful
 * bind. With it:</p>
 *
 * <ol>
 *   <li>While the protocol is in flight ({@code beginProtocol()}), the handler
 *       passes statements through — the protocol statements themselves are the
 *       infrastructure establishing the context.</li>
 *   <li>After a successful bind ({@code complete(tenant)}), the handler treats
 *       the recorded tenant as the transaction's verified tenant scope — the
 *       database already validated the HMAC against the caller's own
 *       txid/pid, so this is a DB-verified fact, not an assertion.</li>
 * </ol>
 *
 * <h3>Security notes</h3>
 *
 * <ul>
 *   <li>Only the verified {@link TenantRlsTransactionContext} or
 *       {@link TenantSignedConnectionBinder} protocols may write this state, and only AFTER {@code app_bind_tenant_ctx}
 *       confirmed the mac. Nothing here bypasses Row-Level Security.</li>
 *   <li>Thread-local by design: pooled worker threads must not inherit a
 *       previous transaction's binding. {@code TransactionSynchronization}
 *       afterCompletion clears the state at every transaction boundary.</li>
 *   <li>The handler's identity-change guard still applies: a raw
 *       {@code SecurityContextHolder} swap inside a signed-bound transaction
 *       is rejected exactly as before.</li>
 * </ul>
 */
final class TenantRlsSignedBinding {

    private static final ThreadLocal<UUID> BOUND_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PROTOCOL_IN_FLIGHT =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private TenantRlsSignedBinding() {
    }

    /** The tenant whose HMAC-verified binding governs the current transaction, or {@code null}. */
    static UUID currentTenant() {
        return BOUND_TENANT.get();
    }

    /** Whether the binding protocol (slot → mint → bind → validate) is executing on this thread. */
    static boolean protocolInFlight() {
        return PROTOCOL_IN_FLIGHT.get();
    }

    /** Mark the start of the binding protocol on this thread (must be balanced). */
    static void beginProtocol() {
        PROTOCOL_IN_FLIGHT.set(Boolean.TRUE);
    }

    /**
     * Record the successful HMAC-verified binding for the current transaction
     * and end the protocol's pass-through window.
     */
    static void complete(UUID tenant) {
        PROTOCOL_IN_FLIGHT.set(Boolean.FALSE);
        BOUND_TENANT.set(tenant);
    }

    /** Abort the protocol without recording a binding (failure path — fail closed). */
    static void abort() {
        PROTOCOL_IN_FLIGHT.set(Boolean.FALSE);
    }

    /** Clear ALL state for the current thread (transaction completion / test isolation). */
    static void clear() {
        PROTOCOL_IN_FLIGHT.remove();
        BOUND_TENANT.remove();
    }
}
