package com.sanad.platform.workflow.notification;

import java.util.Map;
import java.util.UUID;

/**
 * Bounded delivery request handed to a {@link WorkflowChannelProvider}.
 * Carries everything a provider may need and nothing more: providers never
 * receive raw database access, request-scoped services, or execution
 * handles (AD-2 SPI boundary).
 */
public record WorkflowDeliveryRequest(
        UUID tenantId,
        UUID intentId,
        String eventType,
        UUID recipientUserId,
        UUID recipientParticipantId,
        String recipientAddress,
        String templateKey,
        String locale,
        String title,
        String body,
        String deepLink,
        String priority,
        UUID workflowInstanceId,
        UUID workItemId,
        UUID externalActionId,
        String deduplicationKey,
        UUID correlationId,
        String causationId,
        Map<String, Object> payload) {

    public WorkflowDeliveryRequest {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (intentId == null) {
            throw new IllegalArgumentException("intentId is required");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
