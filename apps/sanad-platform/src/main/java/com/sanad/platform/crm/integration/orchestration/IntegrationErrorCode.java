package com.sanad.platform.crm.integration.orchestration;

/**
 * Typed error codes for integration failures.
 * Never stores raw provider errors or stack traces.
 */
public enum IntegrationErrorCode {
    AI_GATEWAY_TIMEOUT,
    AI_GATEWAY_UNAVAILABLE,
    AI_POLICY_DENIED,
    AI_UNSAFE_OUTPUT,
    INVALID_CONTRACT,
    INVALID_SIGNATURE,
    UNAUTHORIZED_SERVICE,
    INVALID_TENANT,
    WORKFLOW_ENGINE_TIMEOUT,
    WORKFLOW_ENGINE_UNAVAILABLE,
    WORKFLOW_POLICY_DENIED,
    // Application-layer typed errors
    STALE_RECOMMENDATION,
    ENTITY_NOT_FOUND,
    ENTITY_STATE_CONFLICT,
    OBJECT_ACCESS_DENIED,
    IDEMPOTENCY_KEY_REUSED,
    INTEGRATION_VERSION_MISMATCH,
    STATE_TRANSITION_FAILED,
    OUTBOX_CLAIM_LOST,
    UNKNOWN_ERROR;

    public boolean isRetryable() {
        return switch (this) {
            case AI_GATEWAY_TIMEOUT, AI_GATEWAY_UNAVAILABLE,
                 WORKFLOW_ENGINE_TIMEOUT, WORKFLOW_ENGINE_UNAVAILABLE,
                 UNKNOWN_ERROR -> true;
            default -> false;
        };
    }
}
