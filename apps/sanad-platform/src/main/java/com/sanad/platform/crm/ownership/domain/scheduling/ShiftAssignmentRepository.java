package com.sanad.platform.crm.ownership.domain.scheduling;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ShiftAssignment entities.
 */
public interface ShiftAssignmentRepository {

    record CreateShiftAssignmentCommand(
            UUID tenantId,
            UUID teamId,
            UUID staffId,
            UUID shiftTemplateId,
            LocalDate startDate,
            LocalDate endDate,
            UUID createdBy
    ) {}

    record UpdateShiftAssignmentCommand(
            UUID shiftTemplateId,
            LocalDate startDate,
            LocalDate endDate,
            ShiftAssignmentStatus status,
            UUID updatedBy,
            long expectedVersion
    ) {}

    Optional<ShiftAssignment> findById(UUID tenantId, UUID id);

    List<ShiftAssignment> findByTeamId(UUID tenantId, UUID teamId, int limit, int offset);

    List<ShiftAssignment> findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);

    ShiftAssignment create(CreateShiftAssignmentCommand command);

    Optional<ShiftAssignment> update(UUID tenantId, UUID id, UpdateShiftAssignmentCommand command);

    boolean hasOverlap(UUID tenantId, UUID staffId, LocalDate startDate, LocalDate endDate, UUID excludeId);
}
