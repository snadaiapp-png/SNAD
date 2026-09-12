package com.sanad.platform.workflow.notification;

/**
 * Provider outcome for one delivery attempt (GATE R2.4). A provider
 * failure NEVER rolls back a committed workflow transition (AD-16) — the
 * dispatcher records the outcome on the durable intent only.
 *
 * @param providerMessageId provider-assigned id when available
 * @param delivered         true when the provider accepted the message
 * @param retryable         true when a retry may plausibly succeed
 * @param failureCategory   bounded classification when delivered=false
 */
public record WorkflowDeliveryResult(
        String providerMessageId,
        boolean delivered,
        boolean retryable,
        String failureCategory) {

    public static final String FC_NONE = "NONE";
    public static final String FC_TIMEOUT = "PROVIDER_TIMEOUT";
    public static final String FC_TRANSIENT = "PROVIDER_TRANSIENT";
    public static final String FC_REJECTED = "PROVIDER_REJECTED";
    public static final String FC_INVALID_RECIPIENT = "INVALID_RECIPIENT";
    public static final String FC_CONFIG_ERROR = "CONFIG_ERROR";
    public static final String FC_DISABLED = "CHANNEL_DISABLED";

    public static WorkflowDeliveryResult success(String providerMessageId) {
        return new WorkflowDeliveryResult(providerMessageId, true, false, FC_NONE);
    }

    public static WorkflowDeliveryResult retryableFailure(String category) {
        return new WorkflowDeliveryResult(null, false, true, category);
    }

    public static WorkflowDeliveryResult terminalFailure(String category) {
        return new WorkflowDeliveryResult(null, false, false, category);
    }
}
