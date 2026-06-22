package com.sanad.platform.security.exception;

/**
 * Thrown when a USED refresh token is presented again (replay attack).
 * All refresh tokens for the user are revoked.
 * Maps to HTTP 401.
 */
public class RefreshTokenReplayException extends RuntimeException {
    public RefreshTokenReplayException(String message) {
        super(message);
    }
}
