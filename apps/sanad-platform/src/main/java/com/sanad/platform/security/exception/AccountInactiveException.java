package com.sanad.platform.security.exception;

/**
 * Thrown when a user's status is not ACTIVE at login time.
 * Maps to HTTP 401 (account cannot be used).
 */
public class AccountInactiveException extends RuntimeException {
    public AccountInactiveException(String message) {
        super(message);
    }
}
