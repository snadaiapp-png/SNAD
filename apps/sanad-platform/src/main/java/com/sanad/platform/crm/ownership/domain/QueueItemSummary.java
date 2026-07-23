package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/** Tenant-scoped read projection for one currently queued CRM record. */
public record QueueItemSummary(
        UUID assignmentId,
        UUID tenantId,
        UUID queueId,
        AssignmentRecordType recordType,
        UUID recordId,
        Instant queuedAt,
        String reason,
        UUID correlationId
) {}
