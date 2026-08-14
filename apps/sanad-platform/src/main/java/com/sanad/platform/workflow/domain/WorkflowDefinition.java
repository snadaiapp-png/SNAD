package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Workflow Definition — the blueprint for a workflow.
 *
 * State machine: DRAFT → ACTIVE → INACTIVE → ARCHIVED
 */
public record WorkflowDefinition(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        String module,
        int version,
        Status status,
        TriggerType triggerType,
        UUID createdBy,
        long versionLock,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { DRAFT, ACTIVE, INACTIVE, ARCHIVED }
    public enum TriggerType { MANUAL, EVENT, SCHEDULED, API }

    public static WorkflowDefinition create(
            UUID tenantId, String code, String name, String description,
            String module, TriggerType triggerType, UUID createdBy) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        var now = Instant.now();
        return new WorkflowDefinition(
                UUID.randomUUID(), tenantId, code, name, description,
                module != null ? module : "GENERAL", 1,
                Status.DRAFT, triggerType != null ? triggerType : TriggerType.MANUAL,
                createdBy, 0, now, now
        );
    }

    public WorkflowDefinition activate() {
        if (status != Status.DRAFT && status != Status.INACTIVE)
            throw new IllegalStateException("Cannot activate from " + status);
        return withStatus(Status.ACTIVE);
    }

    public WorkflowDefinition deactivate() {
        requireStatus(Status.ACTIVE, "deactivate");
        return withStatus(Status.INACTIVE);
    }

    public WorkflowDefinition archive() {
        requireStatus(Status.INACTIVE, "archive");
        return withStatus(Status.ARCHIVED);
    }

    private WorkflowDefinition withStatus(Status newStatus) {
        return new WorkflowDefinition(id, tenantId, code, name, description, module,
                version, newStatus, triggerType, createdBy, versionLock + 1, createdAt, Instant.now());
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
