package com.sanad.platform.subscription.billing.domain;

/**
 * Provider-neutral failure raised when a webhook cannot be cryptographically
 * verified or its signed envelope is malformed.
 */
public class ProviderEventVerificationException extends RuntimeException {

    public enum Reason {
        INVALID_SIGNATURE,
        MALFORMED_PAYLOAD
    }

    private final Reason reason;

    public ProviderEventVerificationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ProviderEventVerificationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
