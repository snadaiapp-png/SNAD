package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Workflow Definition — the blueprint for a workflow.
 *
 * <p>Legacy lifecycle (compatibility during Z3 cutover):
 * DRAFT → ACTIVE → INACTIVE → ARCHIVED</p>
 *
 * <p>Y2 publication lifecycle (design decision I3): a DRAFT version is
 * validated, then PUBLISHED; a published version is immutable. Running
 * instances remain pinned to the exact published version they started with.
 * {@link #definitionFamilyId} groups all concrete versions of one logical
 * workflow; every existing row remains a valid version identity.</p>
 */
public record WorkflowDefinition(
        UUID id,
        UUID tenantId,
        UUID definitionFamilyId,
        String code,
        String name,
        String description,
        String module,
        int version,
        Status status,
        TriggerType triggerType,
        UUID createdBy,
        long versionLock,
        EngineGeneration engineGeneration,
        PublicationState publicationState,
        UUID publishedBy,
        Instant publishedAt,
        Instant validatedAt,
        String definitionChecksum,
        int schemaVersion,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { DRAFT, ACTIVE, INACTIVE, ARCHIVED }
    public enum TriggerType { MANUAL, EVENT, SCHEDULED, API }
    public enum EngineGeneration { LEGACY, Y2 }
    public enum PublicationState { DRAFT, PUBLISHED, RETIRED }

    public static WorkflowDefinition create(
            UUID tenantId, String code, String name, String description,
            String module, TriggerType triggerType, UUID createdBy) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        var now = Instant.now();
        var id = UUID.randomUUID();
        return new WorkflowDefinition(
                id, tenantId, id, code, name, description,
                module != null ? module : "GENERAL", 1,
                Status.DRAFT, triggerType != null ? triggerType : TriggerType.MANUAL,
                createdBy, 0,
                EngineGeneration.LEGACY, PublicationState.DRAFT,
                null, null, null, null, 1,
                now, now
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

    /**
     * Renames the draft. Published versions are immutable (I3): any mutation
     * attempt on a PUBLISHED version is rejected.
     */
    public WorkflowDefinition rename(String newName) {
        if (publicationState == PublicationState.PUBLISHED) {
            throw new IllegalStateException("Published workflow versions are immutable");
        }
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("name must not be blank");
        return new WorkflowDefinition(id, tenantId, definitionFamilyId, code, newName, description,
                module, version, status, triggerType, createdBy, versionLock + 1,
                engineGeneration, publicationState, publishedBy, publishedAt, validatedAt,
                definitionChecksum, schemaVersion, createdAt, Instant.now());
    }

    /**
     * Publishes this DRAFT as an immutable Y2 version (I3). The publish gate
     * (validation/simulation, AN3) is enforced by the application layer.
     */
    public WorkflowDefinition publish(UUID actorUserId, String checksum) {
        if (publicationState != PublicationState.DRAFT) {
            throw new IllegalStateException("Only DRAFT definitions can be published");
        }
        var now = Instant.now();
        return new WorkflowDefinition(id, tenantId, definitionFamilyId, code, name, description,
                module, version, status, triggerType, createdBy, versionLock + 1,
                EngineGeneration.Y2, PublicationState.PUBLISHED, actorUserId, now,
                now, checksum, schemaVersion, createdAt, now);
    }

    /**
     * Creates the next DRAFT version in the same definition family. The
     * published source version remains unchanged and keeps serving the
     * instances pinned to it.
     */
    public WorkflowDefinition nextDraft(UUID actorUserId) {
        if (publicationState != PublicationState.PUBLISHED) {
            throw new IllegalStateException("Next drafts can only be created from PUBLISHED versions");
        }
        var now = Instant.now();
        return new WorkflowDefinition(
                UUID.randomUUID(), tenantId, definitionFamilyId, code, name, description,
                module, version + 1, Status.DRAFT, triggerType, actorUserId, 0,
                EngineGeneration.Y2, PublicationState.DRAFT,
                null, null, null, null, schemaVersion,
                now, now
        );
    }

    private WorkflowDefinition withStatus(Status newStatus) {
        return new WorkflowDefinition(id, tenantId, definitionFamilyId, code, name, description, module,
                version, newStatus, triggerType, createdBy, versionLock + 1,
                engineGeneration, publicationState, publishedBy, publishedAt, validatedAt,
                definitionChecksum, schemaVersion, createdAt, Instant.now());
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
