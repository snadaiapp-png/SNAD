package com.sanad.platform.security.domain;

/** Lifecycle status of a password reset token. */
public enum PasswordResetTokenStatus {
    ACTIVE,
    USED,
    REVOKED,
    EXPIRED
}
