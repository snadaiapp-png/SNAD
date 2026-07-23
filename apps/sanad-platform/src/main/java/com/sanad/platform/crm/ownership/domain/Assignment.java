package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Current ownership assignment for a CRM record.
 * Extends CRM-G1 crm_assignments with owner_type/owner_user_id/owner_team_id/owner_queue_id.
 */
public record Assignment(
        UUID id,
        UUID tenantId,
        long version,
        // G1 columns (preserved for backward compatibility)
        String subjectType,
        UUID subjectId,
        UUID assignedUserId,
        String assignmentRole,
        AssignmentStatus status,
        Instant startsAt,
        Instant endsAt,
        String reason,
        // CRM-008B columns
        OwnerType ownerType,
        UUID ownerUserId,
        UUID ownerTeamId,
        UUID ownerQueueId,
        AssignmentRecordType recordType,
        UUID recordId,
        UUID assignedByRuleId,
        UUID assignedByUserId,
        UUID correlationId,
        String workflowResult,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
    public Assignment {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (status == null) status = AssignmentStatus.ACTIVE;
        if (assignmentRole == null || assignmentRole.isBlank()) assignmentRole = "OWNER";
    }

    public boolean isActive() { return status == AssignmentStatus.ACTIVE; }
}
