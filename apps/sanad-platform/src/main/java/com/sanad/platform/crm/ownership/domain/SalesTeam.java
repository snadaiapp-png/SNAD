package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Sales team entity (CRM-008B).
 *
 * <p>Tenant-scoped grouping of users for sales assignment. Each team has a
 * unique code within its tenant, a manager, and optional default queue and
 * territory. Teams are archived (not deleted) to preserve history.
 */
public record SalesTeam(
        UUID id,
        UUID tenantId,
        String code,
        String displayName,
        String description,
        TeamStatus status,
        UUID managerUserId,
        UUID defaultQueueId,
        UUID defaultTerritoryId,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
    public SalesTeam {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code required");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName required");
        if (status == null) status = TeamStatus.ACTIVE;
    }

    public boolean isArchived() { return status == TeamStatus.ARCHIVED; }
    public boolean isActive() { return status == TeamStatus.ACTIVE; }
}
