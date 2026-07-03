package com.sanad.platform.security.exception;

/** Raised when a requested public workspace identifier is already reserved or used. */
public class RegistrationConflictException extends RuntimeException {
    public RegistrationConflictException(String message) {
        super(message);
    }
}
