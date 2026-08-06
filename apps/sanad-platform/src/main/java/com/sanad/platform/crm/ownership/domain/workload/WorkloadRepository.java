package com.sanad.platform.crm.ownership.domain.workload;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for WorkloadAssignment entities.
 */
public interface WorkloadRepository {

    record CreateWorkloadCommand(
            UUID tenantId,
            UUID staffId,
            UUID serviceId,
            UUID jobId,
            int estimatedHours,
            LocalDate startDate,
            LocalDate endDate,
            UUID createdBy
    ) {}

    record UpdateWorkloadCommand(
            Integer actualHours,
            WorkloadStatus status,
            LocalDate endDate,
            UUID updatedBy,
            long expectedVersion
    ) {}

    Optional<WorkloadAssignment> findById(UUID tenantId, UUID id);

    List<WorkloadAssignment> findByStaffId(UUID tenantId, UUID staffId, WorkloadStatus status);

    List<WorkloadAssignment> findByServiceId(UUID tenantId, UUID serviceId);

    WorkloadAssignment create(CreateWorkloadCommand command);

    Optional<WorkloadAssignment> update(UUID tenantId, UUID id, UpdateWorkloadCommand command);

    boolean delete(UUID tenantId, UUID id);

    int sumEstimatedHoursByStaff(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);

    int sumActualHoursByStaff(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);
}
