package com.sanad.platform.shared.api.exceptions;

import com.sanad.platform.shared.api.ErrorCode;

/**
 * Raised when a write operation violates a uniqueness or state constraint.
 * Maps to HTTP 409 / {@link ErrorCode#SANAD_CON_001}.
 */
public class ConflictException extends TypedBusinessException {

    public ConflictException(String resourceType) {
        this(resourceType, java.util.Map.of());
    }

    public ConflictException(String resourceType, java.util.Map<String, String> diagnostics) {
        super(ErrorCode.SANAD_CON_001,
              "A conflict was detected for the requested " + resourceType + ".",
              diagnostics);
    }
}
