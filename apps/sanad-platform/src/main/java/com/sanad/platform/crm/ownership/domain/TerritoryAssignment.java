package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/** Assignment of a user or team to a territory with a specific role. */
public record TerritoryAssignment(
        UUID id,
        UUID tenantId,
        UUID territoryId,
        AssigneeType assigneeType,
        UUID assigneeId,
        TerritoryAssignmentRole role,
        int priority,
        TerritoryAssignmentStatus status,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
    public TerritoryAssignment {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (territoryId == null) throw new IllegalArgumentException("territoryId required");
        if (assigneeType == null) throw new IllegalArgumentException("assigneeType required");
        if (assigneeId == null) throw new IllegalArgumentException("assigneeId required");
        if (role == null) role = TerritoryAssignmentRole.PRIMARY;
        if (status == null) status = TerritoryAssignmentStatus.ACTIVE;
        if (effectiveFrom == null) effectiveFrom = Instant.now();
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo cannot be before effectiveFrom");
        }
    }

    public boolean isActive() { return status == TerritoryAssignmentStatus.ACTIVE; }
    public boolean isPrimary() { return role == TerritoryAssignmentRole.PRIMARY; }
}
