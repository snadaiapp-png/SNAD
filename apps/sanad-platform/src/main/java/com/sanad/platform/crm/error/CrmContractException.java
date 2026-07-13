package com.sanad.platform.crm.error;

import java.util.UUID;

/**
 * Thrown by the CRM layer to signal an expected business error that maps
 * cleanly to a stable {@link CrmErrorCode}. The
 * {@link CrmExceptionHandler} translates this into an HTTP response
 * using the standard {@link CrmErrorResponse} envelope.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
public class CrmContractException extends RuntimeException {

    private final CrmErrorCode code;
    private final String userMessage;
    private final UUID requestId;

    public CrmContractException(CrmErrorCode code) {
        this(code, code.defaultMessage(), null, null);
    }

    public CrmContractException(CrmErrorCode code, String userMessage) {
        this(code, userMessage, null, null);
    }

    public CrmContractException(CrmErrorCode code, String userMessage, UUID requestId) {
        this(code, userMessage, requestId, null);
    }

    public CrmContractException(CrmErrorCode code, String userMessage, UUID requestId, Throwable cause) {
        super(userMessage == null ? code.defaultMessage() : userMessage, cause);
        this.code = code;
        this.userMessage = userMessage == null ? code.defaultMessage() : userMessage;
        this.requestId = requestId;
    }

    public CrmErrorCode code() { return code; }
    public String userMessage() { return userMessage; }
    public UUID requestId() { return requestId; }
}
