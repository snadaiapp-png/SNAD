package com.sanad.platform.crm.ownership.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for territory assignments (tenant-scoped). */
public interface TerritoryAssignmentRepository {

    TerritoryAssignment save(TerritoryAssignment assignment);

    Optional<TerritoryAssignment> findById(UUID tenantId, UUID assignmentId);

    Optional<TerritoryAssignment> findActivePrimary(UUID tenantId, UUID territoryId, AssigneeType assigneeType);

    List<TerritoryAssignment> findActiveByTerritory(UUID tenantId, UUID territoryId);

    List<TerritoryAssignment> findActiveByAssignee(UUID tenantId, AssigneeType assigneeType, UUID assigneeId);

    void deactivate(UUID tenantId, UUID assignmentId, UUID updatedBy);
}
