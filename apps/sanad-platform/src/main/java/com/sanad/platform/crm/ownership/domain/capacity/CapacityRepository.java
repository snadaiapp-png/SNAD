package com.sanad.platform.crm.ownership.domain.capacity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CapacityPlan entities.
 */
public interface CapacityRepository {

    record CreateCapacityPlanCommand(
            UUID tenantId,
            UUID teamId,
            LocalDate periodStart,
            LocalDate periodEnd,
            int maxCapacity,
            UUID createdBy
    ) {}

    record UpdateCapacityPlanCommand(
            Integer maxCapacity,
            Integer allocatedCapacity,
            CapacityStatus status,
            UUID updatedBy,
            long expectedVersion
    ) {}

    Optional<CapacityPlan> findById(UUID tenantId, UUID id);

    List<CapacityPlan> findByTeamId(UUID tenantId, UUID teamId);

    Optional<CapacityPlan> findActiveByTeamAndPeriod(UUID tenantId, UUID teamId, LocalDate date);

    CapacityPlan create(CreateCapacityPlanCommand command);

    Optional<CapacityPlan> update(UUID tenantId, UUID id, UpdateCapacityPlanCommand command);
}
