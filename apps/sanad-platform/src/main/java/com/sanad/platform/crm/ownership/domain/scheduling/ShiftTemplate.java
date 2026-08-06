package com.sanad.platform.crm.ownership.domain.scheduling;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Shift template entity (CRM-008).
 *
 * <p>Tenant-scoped definition of a recurring shift pattern. Each template defines
 * a start time, end time, and which days of the week it applies to.
 */
public record ShiftTemplate(
        UUID id,
        UUID tenantId,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        List<DayOfWeek> daysOfWeek,
        ShiftTemplateStatus status,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public ShiftTemplate {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (startTime == null) throw new IllegalArgumentException("startTime required");
        if (endTime == null) throw new IllegalArgumentException("endTime required");
        if (daysOfWeek == null || daysOfWeek.isEmpty()) throw new IllegalArgumentException("daysOfWeek required");
        if (status == null) status = ShiftTemplateStatus.ACTIVE;
    }

    public boolean isActive() { return status == ShiftTemplateStatus.ACTIVE; }
    public boolean isInactive() { return status == ShiftTemplateStatus.INACTIVE; }
}
