package com.sanad.platform.security.exception;

/** Thrown when the password reset rate limit is exceeded. */
public class PasswordResetRateLimitException extends RuntimeException {
    public PasswordResetRateLimitException(String message) {
        super(message);
    }
}
