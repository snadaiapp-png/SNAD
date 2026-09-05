package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkflowStep(
        UUID id,
        UUID tenantId,
        UUID workflowDefinitionId,
        String stepKey,
        String name,
        StepType stepType,
        int sequenceOrder,
        String configuration,  // JSON
        Integer slaHours,
        String requiredCapability,
        String requiredRole,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum StepType { ACTION, APPROVAL, CONDITION, NOTIFICATION, END }

    public static WorkflowStep create(
            UUID tenantId, UUID workflowDefinitionId, String stepKey, String name,
            StepType stepType, int sequenceOrder, String configuration,
            Integer slaHours, String requiredCapability, String requiredRole) {
        if (stepKey == null || stepKey.isBlank()) throw new IllegalArgumentException("stepKey must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        var now = Instant.now();
        return new WorkflowStep(UUID.randomUUID(), tenantId, workflowDefinitionId, stepKey, name,
                stepType, sequenceOrder, configuration, slaHours, requiredCapability, requiredRole,
                0, now, now);
    }
}
