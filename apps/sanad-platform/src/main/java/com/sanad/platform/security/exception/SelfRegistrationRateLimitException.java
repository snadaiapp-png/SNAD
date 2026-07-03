package com.sanad.platform.security.exception;

/** Raised when public workspace registration exceeds the permitted hourly rate. */
public class SelfRegistrationRateLimitException extends RuntimeException {
    public SelfRegistrationRateLimitException(String message) {
        super(message);
    }
}
