package com.sanad.platform.security.rls;

import java.util.function.Supplier;

/**
 * Explicit thread-scoped classification for pre-authentication database
 * statements that legitimately execute inside a transaction WITHOUT a tenant
 * context (login flows, token issuance, session bootstrap, audit-before-auth).
 *
 * <p>G1-i red-contract closure (2026-11-25): the silent pass-through for
 * context-less statements inside an active transaction was the last red item
 * on {@link TenantRlsConnectionHandler}. The approved remediation design
 * classifies such paths EXPLICITLY: a tenant-data statement without context
 * inside a transaction is rejected loudly, while statements that are genuinely
 * pre-authentication opt in through this scope. The opt-in is deliberate,
 * code-visible and reviewable — never ambient.</p>
 *
 * <p>Thread-local by design: pooled worker threads must not inherit the
 * classification from a previous request. {@link #call(Supplier)} and
 * {@link #run(Runnable)} restore the previous state on exit, including
 * nested usage.</p>
 *
 * <p>Security notes: the scope only suppresses the CONTEXT-MISSING rejection
 * of the connection handler. It does not bypass Row-Level Security (the
 * rewritten policies fail closed on unbound sessions), does not elevate any
 * database privilege, and does not alter the HMAC-signed tenant-context
 * binding contract (V20261124_1/V20261124_2).</p>
 */
public final class TenantRlsPreAuthScope {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private TenantRlsPreAuthScope() {
    }

    /** Whether the current thread is inside an explicitly declared pre-auth scope. */
    public static boolean isActive() {
        return ACTIVE.get();
    }

    /** Enter the pre-auth scope for the current thread (must be balanced with {@link #clear()}). */
    public static void activate() {
        ACTIVE.set(Boolean.TRUE);
    }

    /** Leave the pre-auth scope for the current thread. */
    public static void clear() {
        ACTIVE.remove();
    }

    /** Execute {@code body} inside the pre-auth scope, restoring prior state afterwards. */
    public static <T> T call(Supplier<T> body) {
        boolean previous = ACTIVE.get();
        ACTIVE.set(Boolean.TRUE);
        try {
            return body.get();
        } finally {
            ACTIVE.set(previous);
        }
    }

    /** Run {@code body} inside the pre-auth scope, restoring prior state afterwards. */
    public static void run(Runnable body) {
        call(() -> {
            body.run();
            return null;
        });
    }
}
