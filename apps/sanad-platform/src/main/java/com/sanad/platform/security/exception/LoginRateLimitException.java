package com.sanad.platform.security.exception;

/**
 * Thrown when too many failed login attempts have been recorded.
 * Maps to HTTP 429 Too Many Requests.
 */
public class LoginRateLimitException extends RuntimeException {
    public LoginRateLimitException(String message) {
        super(message);
    }
}
