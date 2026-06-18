package com.sanad.platform.user.exception;

import java.util.UUID;

/**
 * Raised when a user cannot be found inside the supplied tenant scope.
 * The message deliberately avoids disclosing whether the ID exists elsewhere.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID tenantId, UUID userId) {
        super("User not found");
    }
}
