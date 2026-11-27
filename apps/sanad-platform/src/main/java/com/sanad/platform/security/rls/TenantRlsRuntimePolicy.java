package com.sanad.platform.security.rls;

/**
 * Process-wide runtime policy for the tenant-RLS connection layer.
 *
 * <p>{@link TenantRlsSecurityConfig} resolves the deployment topology, the
 * signing key and the strictness governance at startup and installs the
 * resulting immutable policy here. {@link TenantRlsConnectionHandler} and
 * {@link TenantRlsTransactionContext} read it at BIND time (not construction
 * time), so every binding reflects the effective policy even for connections
 * wrapped before the configuration bean initializes.</p>
 *
 * <h3>Fail-closed defaults</h3>
 *
 * <p>The default policy carries NO signer (any bind attempt is refused with
 * an explicit error) and strict-missing-context = true. A Spring context
 * without {@link TenantRlsSecurityConfig} therefore fails closed on tenant
 * binds instead of silently operating on the raw untrusted GUC.</p>
 */
public final class TenantRlsRuntimePolicy {

    /**
     * Immutable effective policy.
     *
     * @param signer               the HMAC signer, or {@code null} when no
     *                             key is configured — binds then fail closed.
     * @param strictMissingContext whether an in-transaction statement without
     *                             any tenant context is rejected loudly
     *                             (true) or passes through transparently
     *                             (false — governed escape hatch only).
     */
    public record Policy(TenantContextSigner signer, boolean strictMissingContext) {
    }

    private static final Policy FAIL_CLOSED_DEFAULT = new Policy(null, true);

    private static volatile Policy current = FAIL_CLOSED_DEFAULT;

    private TenantRlsRuntimePolicy() {
    }

    /** The currently installed policy (never {@code null}). */
    public static Policy current() {
        return current;
    }

    /** Install a resolved policy (called once by {@link TenantRlsSecurityConfig}). */
    public static void install(Policy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Tenant RLS runtime policy must not be null");
        }
        current = policy;
    }

    /** Reset to the fail-closed default (test isolation support). */
    public static void reset() {
        current = FAIL_CLOSED_DEFAULT;
    }
}
