package com.sanad.platform.crm.ownership.domain.availability;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Staff availability entity (CRM-008).
 *
 * <p>Tracks when staff members are available, unavailable, or on leave.
 * Supports partial-day availability with optional start/end times.
 */
public record StaffAvailability(
        UUID id,
        UUID tenantId,
        UUID staffId,
        AvailabilityType type,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        String reason,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public StaffAvailability {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (staffId == null) throw new IllegalArgumentException("staffId required");
        if (type == null) throw new IllegalArgumentException("type required");
        if (startDate == null) throw new IllegalArgumentException("startDate required");
        if (endDate == null) throw new IllegalArgumentException("endDate required");
        if (endDate.isBefore(startDate)) throw new IllegalArgumentException("endDate must be after startDate");
    }

    public boolean isAvailable() { return type == AvailabilityType.AVAILABLE; }
    public boolean isUnavailable() { return type == AvailabilityType.UNAVAILABLE; }
    public boolean isOnLeave() { return type == AvailabilityType.ON_LEAVE; }
}
