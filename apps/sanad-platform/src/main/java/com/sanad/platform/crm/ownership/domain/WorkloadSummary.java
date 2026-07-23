package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/** Workload summary for a user or team. */
public record WorkloadSummary(
        UUID tenantId,
        UUID ownerId,
        OwnerType ownerType,
        long activeAssignments,
        long activeQueueItems,
        long activeTeamMemberships,
        long overdueTasks
) {
    public int totalLoad() {
        return (int) (activeAssignments + activeQueueItems);
    }
}
