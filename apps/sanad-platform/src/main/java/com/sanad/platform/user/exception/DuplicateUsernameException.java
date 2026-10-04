package com.sanad.platform.user.exception;

import java.util.UUID;

/** Raised when a username is already assigned to another user in the tenant. */
public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(UUID tenantId, String username) {
        super("Username already exists for this tenant");
    }
}
