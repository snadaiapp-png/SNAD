package com.sanad.platform.user.exception;

import java.util.UUID;

/** Raised when an email is already assigned to another user in the tenant. */
public class DuplicateUserEmailException extends RuntimeException {

    public DuplicateUserEmailException(UUID tenantId, String email) {
        super("User email already exists for this tenant");
    }
}
