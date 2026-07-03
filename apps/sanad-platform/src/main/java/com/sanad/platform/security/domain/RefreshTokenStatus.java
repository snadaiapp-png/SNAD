package com.sanad.platform.security.domain;

/**
 * Status of a refresh token.
 *
 * <ul>
 *   <li>{@link #ACTIVE} — valid, can be used for refresh</li>
 *   <li>{@link #USED} — already used for refresh; presenting it again triggers replay protection</li>
 *   <li>{@link #REVOKED} — manually revoked (logout, password change, or family invalidation)</li>
 * </ul>
 */
public enum RefreshTokenStatus {
    ACTIVE,
    USED,
    REVOKED
}
