package com.sanad.platform.security.denial;

/**
 * Stage 05A.2.9.1 §5 — Canonical classification of every security denial
 * path that the SANAD platform can produce.
 *
 * <p>Each constant is the <strong>exact</strong> string persisted to the
 * {@code failure_category} column of {@code platform_security_audit_events}
 * (for pre-auth denials) or to the {@code error_code} column of
 * {@code audit_events} (for post-auth denials). Test assertions compare
 * against these constants — never against ad-hoc literals.</p>
 *
 * <p>Routing rule (enforced by {@link SecurityDenialCoordinator}):</p>
 * <ul>
 *   <li>Categories below marked {@code PRE_AUTH} are persisted to
 *       {@code platform_security_audit_events} (tenantVerified=false).</li>
 *   <li>Categories marked {@code POST_AUTH} are persisted to
 *       {@code audit_events} via the verified TenantContext
 *       (tenantVerified=true).</li>
 * </ul>
 */
public enum SecurityDenialCategory {

    /** No {@code Authorization} header on a protected resource. Pre-auth. */
    MISSING_JWT(false),

    /** B64/JWT structure unreadable, wrong issuer, malformed claims. Pre-auth. */
    MALFORMED_JWT(false),

    /** JWT parsed but signature verification failed. Pre-auth. */
    INVALID_SIGNATURE(false),

    /** JWT signature valid but {@code exp} is in the past. Pre-auth. */
    EXPIRED_JWT(false),

    /** JWT subject claim is missing or not a UUID. Pre-auth. */
    INVALID_SUBJECT(false),

    /** {@code tenant_id} claim missing, not a UUID, or tenant DB-lookup failed. Pre-auth. */
    UNVERIFIED_TENANT(false),

    /** User not found or no active membership in the JWT tenant. Pre-auth. */
    UNKNOWN_SESSION(false),

    /** Session version mismatch — token revoked by logout/password change. Pre-auth. */
    REVOKED_SESSION(false),

    /** JWT verified but request {@code tenantId} selector differs from claim. Post-auth. */
    TENANT_SELECTOR_MISMATCH(true),

    /** Valid session but credential rotation required before API use. Post-auth. */
    ROTATION_REQUIRED(true),

    /** Authenticated and tenant-verified, but missing a required capability. Post-auth. */
    CAPABILITY_DENIED(true);

    private final boolean tenantVerified;

    SecurityDenialCategory(boolean tenantVerified) {
        this.tenantVerified = tenantVerified;
    }

    /** {@code true} if the JWT was cryptographically verified AND (for ROTATION_REQUIRED /
     *  CAPABILITY_DENIED) the session was DB-validated. Such denials are written to
     *  {@code audit_events}; all others go to {@code platform_security_audit_events}. */
    public boolean isTenantVerified() {
        return tenantVerified;
    }
}
