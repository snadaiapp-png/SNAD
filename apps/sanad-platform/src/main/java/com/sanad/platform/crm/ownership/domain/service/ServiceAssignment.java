package com.sanad.platform.crm.ownership.domain.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Service assignment entity (CRM-008).
 *
 * <p>Links teams to services they can provide.
 * Each assignment associates a team with a specific service.
 */
public record ServiceAssignment(
        UUID id,
        UUID tenantId,
        UUID teamId,
        UUID serviceId,
        ServiceAssignmentStatus status,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public ServiceAssignment {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (teamId == null) throw new IllegalArgumentException("teamId required");
        if (serviceId == null) throw new IllegalArgumentException("serviceId required");
        if (status == null) status = ServiceAssignmentStatus.ACTIVE;
    }

    public boolean isActive() { return status == ServiceAssignmentStatus.ACTIVE; }
    public boolean isInactive() { return status == ServiceAssignmentStatus.INACTIVE; }
}
