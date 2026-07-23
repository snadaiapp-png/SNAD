package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable append-only ownership change record.
 * No UPDATE or DELETE path is ever allowed.
 */
public record OwnershipHistory(
        UUID id,
        UUID tenantId,
        AssignmentRecordType recordType,
        UUID recordId,
        OwnerType fromOwnerType,
        UUID fromOwnerUserId,
        UUID fromOwnerTeamId,
        UUID fromOwnerQueueId,
        OwnerType toOwnerType,
        UUID toOwnerUserId,
        UUID toOwnerTeamId,
        UUID toOwnerQueueId,
        ChangeType changeType,
        TriggerSource triggerSource,
        UUID triggerReferenceId,
        UUID actorUserId,
        String reason,
        UUID correlationId,
        Instant effectiveAt,
        Instant recordedAt
) {
    public OwnershipHistory {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (recordType == null) throw new IllegalArgumentException("recordType required");
        if (recordId == null) throw new IllegalArgumentException("recordId required");
        if (toOwnerType == null) throw new IllegalArgumentException("toOwnerType required");
        if (changeType == null) throw new IllegalArgumentException("changeType required");
        if (triggerSource == null) throw new IllegalArgumentException("triggerSource required");
        if (actorUserId == null) throw new IllegalArgumentException("actorUserId required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason required");
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (effectiveAt == null) effectiveAt = Instant.now();
        if (recordedAt == null) recordedAt = Instant.now();
    }
}
