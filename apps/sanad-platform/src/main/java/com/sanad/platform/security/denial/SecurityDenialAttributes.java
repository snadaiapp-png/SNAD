package com.sanad.platform.security.denial;

/**
 * Stage 05A.2.9.1 §5 — Canonical names of the request attributes used by
 * the security denial pipeline.
 *
 * <p>Constants are {@code public static final} so they can be referenced
 * from filters, the entry point, the access-denied handler, the global
 * exception handler, and integration tests without magic strings.</p>
 */
public final class SecurityDenialAttributes {

    private SecurityDenialAttributes() {}

    /**
     * Request attribute holding the {@link SecurityDenialContext} for the
     * current denial. Set by the component that first detects the denial
     * (e.g. {@code JwtAuthenticationFilter}) and read by the
     * {@link SecurityDenialCoordinator}.
     */
    public static final String DENIAL_CONTEXT =
            "SANAD_SECURITY_DENIAL_CONTEXT";

    /**
     * Boolean request attribute set to {@code Boolean.TRUE} by the
     * coordinator after the audit row has been written. Prevents a
     * second denial path (e.g. Spring Security's AuthenticationEntryPoint
     * re-firing after the filter already recorded the denial) from
     * double-counting the same failure.
     */
    public static final String DENIAL_RECORDED =
            "SANAD_SECURITY_DENIAL_RECORDED";
}
