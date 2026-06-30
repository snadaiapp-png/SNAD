package com.sanad.platform.shared.api.exceptions;

import com.sanad.platform.shared.api.ErrorCode;

/**
 * Raised when a request reaches a controller without a resolvable tenant context.
 * Maps to HTTP 403 / {@link ErrorCode#SANAD_TEN_001}.
 */
public class TenantContextException extends TypedBusinessException {

    public TenantContextException() {
        this("Tenant context is missing or invalid for this request.");
    }

    public TenantContextException(String safeDetail) {
        super(ErrorCode.SANAD_TEN_001, safeDetail, java.util.Map.of());
    }
}
