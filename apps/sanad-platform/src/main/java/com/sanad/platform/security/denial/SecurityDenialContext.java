package com.sanad.platform.security.denial;

/**
 * Stage 05A.2.9.1 §5 — Immutable record carrying everything the
 * {@link SecurityDenialCoordinator} needs to (a) prevent duplicate
 * recording, (b) select the correct audit table, and (c) write the
 * standardized HTTP response.
 *
 * <p>One {@code SecurityDenialContext} is attached to each denied
 * request as a request attribute under
 * {@link SecurityDenialAttributes#DENIAL_CONTEXT}. The coordinator is
 * the <strong>only</strong> component that reads it back.</p>
 */
public record SecurityDenialContext(
        SecurityDenialCategory category,
        String errorCode,
        int httpStatus,
        boolean tenantVerified,
        String tokenFingerprint
) {

    /**
     * Canonical factory used by every denial path. Derives the
     * {@code tenantVerified} flag from the category itself so callers
     * cannot accidentally mismatch the two.
     */
    public static SecurityDenialContext of(
            SecurityDenialCategory category,
            String errorCode,
            int httpStatus,
            String tokenFingerprint
    ) {
        return new SecurityDenialContext(
                category,
                errorCode,
                httpStatus,
                category.isTenantVerified(),
                tokenFingerprint
        );
    }
}
