package com.sanad.platform.security.exception;

/**
 * Thrown when login credentials are invalid (user not found or password mismatch).
 * Maps to HTTP 401.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
