package com.sanad.platform.crm.ownership.domain.availability;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for StaffAvailability entities.
 */
public interface AvailabilityRepository {

    record CreateAvailabilityCommand(
            UUID tenantId,
            UUID staffId,
            AvailabilityType type,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime startTime,
            LocalTime endTime,
            String reason,
            UUID createdBy
    ) {}

    record UpdateAvailabilityCommand(
            AvailabilityType type,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime startTime,
            LocalTime endTime,
            String reason,
            UUID updatedBy,
            long expectedVersion
    ) {}

    Optional<StaffAvailability> findById(UUID tenantId, UUID id);

    List<StaffAvailability> findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);

    StaffAvailability create(CreateAvailabilityCommand command);

    Optional<StaffAvailability> update(UUID tenantId, UUID id, UpdateAvailabilityCommand command);

    boolean delete(UUID tenantId, UUID id);
}
