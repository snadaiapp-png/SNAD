package com.sanad.platform.shared.api.exceptions;

import com.sanad.platform.shared.api.ErrorCode;

/**
 * Raised when a business rule invariant is violated.
 * Maps to HTTP 422 / {@link ErrorCode#SANAD_BIZ_001}.
 */
public class BusinessRuleException extends TypedBusinessException {

    public BusinessRuleException(String safeDetail) {
        this(safeDetail, java.util.Map.of());
    }

    public BusinessRuleException(String safeDetail, java.util.Map<String, String> diagnostics) {
        super(ErrorCode.SANAD_BIZ_001, safeDetail, diagnostics);
    }
}
