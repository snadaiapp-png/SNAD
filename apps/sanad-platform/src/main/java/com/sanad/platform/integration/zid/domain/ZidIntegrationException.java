package com.sanad.platform.integration.zid.domain;

/**
 * Base exception for the Zid integration. Every message is written by
 * code paths that already exclude token material and customer PII;
 * {@code ZidSyncAuditService.redact()} is applied as a second line of
 * defense before anything is persisted or logged.
 */
public class ZidIntegrationException extends RuntimeException {

    public enum Reason {
        /** Integration master switch is off or required config missing. */
        NOT_CONFIGURED,
        /** Outbound Zid call failed transiently (5xx, timeout, 429). */
        TRANSIENT_FAILURE,
        /** Zid returned 401 — stored tokens are no longer acceptable. */
        TOKEN_REJECTED,
        /** OAuth authorization code was invalid, expired or replayed. */
        INVALID_AUTHORIZATION_CODE,
        /** Link state mismatch/expiry — possible CSRF or stale flow. */
        INVALID_LINK_STATE,
        /** Store identity could not be confirmed against the Zid API. */
        STORE_IDENTITY_UNRESOLVED,
        /** The store is already bound to (another) tenant. */
        STORE_ALREADY_BOUND,
        /** Webhook signature verification failed. */
        INVALID_WEBHOOK_SIGNATURE,
        /** Webhook payload could not be parsed or lacks required identifiers. */
        INVALID_WEBHOOK_PAYLOAD,
        /** Webhook arrived for a store with no trusted binding. */
        TRUSTED_BINDING_NOT_FOUND,
        /** Replayed webhook payload does not match the original event. */
        WEBHOOK_REPLAY_MISMATCH
    }

    private final Reason reason;

    public ZidIntegrationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ZidIntegrationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
