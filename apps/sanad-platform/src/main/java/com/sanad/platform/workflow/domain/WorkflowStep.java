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
        /**
         * SLA policy mode (V3): WALL_CLOCK (legacy-compatible elapsed hours),
         * CALENDAR_TIME (elapsed hours, explicit), or BUSINESS_TIME (due
         * instant resolved through the pinned tenant business calendar).
         */
        String slaMode,
        /** Pinned tenant business calendar for BUSINESS_TIME resolution. */
        UUID slaCalendarId,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum StepType {
        ACTION, APPROVAL, CONDITION, NOTIFICATION, END,
        START, HUMAN_TASK, SYSTEM_ACTION, PARALLEL_FORK, PARALLEL_JOIN, CALL_WORKFLOW
    }

    public static WorkflowStep create(
            UUID tenantId, UUID workflowDefinitionId, String stepKey, String name,
            StepType stepType, int sequenceOrder, String configuration,
            Integer slaHours, String requiredCapability, String requiredRole) {
        return create(tenantId, workflowDefinitionId, stepKey, name, stepType, sequenceOrder,
                configuration, slaHours, requiredCapability, requiredRole, null, null);
    }

    public static WorkflowStep create(
            UUID tenantId, UUID workflowDefinitionId, String stepKey, String name,
            StepType stepType, int sequenceOrder, String configuration,
            Integer slaHours, String requiredCapability, String requiredRole,
            String slaMode, UUID slaCalendarId) {
        if (stepKey == null || stepKey.isBlank()) throw new IllegalArgumentException("stepKey must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        var now = Instant.now();
        return new WorkflowStep(UUID.randomUUID(), tenantId, workflowDefinitionId, stepKey, name,
                stepType, sequenceOrder, configuration, slaHours, requiredCapability, requiredRole,
                slaMode, slaCalendarId, 0, now, now);
    }
}
