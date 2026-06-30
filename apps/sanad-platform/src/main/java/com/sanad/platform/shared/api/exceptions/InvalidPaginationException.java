package com.sanad.platform.shared.api.exceptions;

import com.sanad.platform.shared.api.ErrorCode;

/**
 * Raised when pagination or sort parameters fail validation.
 * Maps to HTTP 400 / {@link ErrorCode#SANAD_PAG_001} (general) or
 * {@link ErrorCode#SANAD_PAG_002} (invalid sort field).
 */
public class InvalidPaginationException extends TypedBusinessException {

    public InvalidPaginationException(String safeDetail, ErrorCode code) {
        super(code, safeDetail, java.util.Map.of());
    }

    public static InvalidPaginationException invalidSort(String field) {
        return new InvalidPaginationException(
            "Sort field '" + field + "' is not allowed for this resource.",
            ErrorCode.SANAD_PAG_002);
    }

    public static InvalidPaginationException invalidSize() {
        return new InvalidPaginationException(
            "Page size must be between 1 and 100.",
            ErrorCode.SANAD_PAG_001);
    }

    public static InvalidPaginationException invalidPage() {
        return new InvalidPaginationException(
            "Page number must be zero or a positive integer.",
            ErrorCode.SANAD_PAG_001);
    }
}
