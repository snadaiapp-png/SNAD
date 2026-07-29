package com.sanad.platform.crm.ownership.domain.scheduling;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ShiftTemplate entities.
 */
public interface ShiftTemplateRepository {

    record CreateShiftTemplateCommand(
            UUID tenantId,
            String name,
            LocalTime startTime,
            LocalTime endTime,
            List<DayOfWeek> daysOfWeek,
            UUID createdBy
    ) {}

    record UpdateShiftTemplateCommand(
            String name,
            LocalTime startTime,
            LocalTime endTime,
            List<DayOfWeek> daysOfWeek,
            ShiftTemplateStatus status,
            UUID updatedBy,
            long expectedVersion
    ) {}

    Optional<ShiftTemplate> findById(UUID tenantId, UUID id);

    List<ShiftTemplate> findAll(UUID tenantId, int limit, int offset);

    ShiftTemplate create(CreateShiftTemplateCommand command);

    Optional<ShiftTemplate> update(UUID tenantId, UUID id, UpdateShiftTemplateCommand command);

    boolean existsByName(UUID tenantId, String name, UUID excludeId);
}
