package com.sanad.platform.crm.ownership.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ServiceAssignment entities.
 */
public interface ServiceAssignmentRepository {

    record CreateServiceAssignmentCommand(
            UUID tenantId,
            UUID teamId,
            UUID serviceId,
            UUID createdBy
    ) {}

    record UpdateServiceAssignmentCommand(
            ServiceAssignmentStatus status,
            UUID updatedBy,
            long expectedVersion
    ) {}

    Optional<ServiceAssignment> findById(UUID tenantId, UUID id);

    List<ServiceAssignment> findByTeamId(UUID tenantId, UUID teamId);

    List<ServiceAssignment> findByServiceId(UUID tenantId, UUID serviceId);

    ServiceAssignment create(CreateServiceAssignmentCommand command);

    Optional<ServiceAssignment> update(UUID tenantId, UUID id, UpdateServiceAssignmentCommand command);

    boolean delete(UUID tenantId, UUID id);

    boolean existsByTeamAndService(UUID tenantId, UUID teamId, UUID serviceId, UUID excludeId);
}
