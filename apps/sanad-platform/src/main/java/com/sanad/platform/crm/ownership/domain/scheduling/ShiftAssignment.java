package com.sanad.platform.crm.ownership.domain.scheduling;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Shift assignment entity (CRM-008).
 *
 * <p>Assigns a shift template to a staff member for a specific date range.
 * Each assignment links a team member to a shift pattern.
 */
public record ShiftAssignment(
        UUID id,
        UUID tenantId,
        UUID teamId,
        UUID staffId,
        UUID shiftTemplateId,
        LocalDate startDate,
        LocalDate endDate,
        ShiftAssignmentStatus status,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public ShiftAssignment {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (teamId == null) throw new IllegalArgumentException("teamId required");
        if (staffId == null) throw new IllegalArgumentException("staffId required");
        if (shiftTemplateId == null) throw new IllegalArgumentException("shiftTemplateId required");
        if (startDate == null) throw new IllegalArgumentException("startDate required");
        if (endDate == null) throw new IllegalArgumentException("endDate required");
        if (endDate.isBefore(startDate)) throw new IllegalArgumentException("endDate must be after startDate");
        if (status == null) status = ShiftAssignmentStatus.SCHEDULED;
    }

    public boolean isScheduled() { return status == ShiftAssignmentStatus.SCHEDULED; }
    public boolean isActive() { return status == ShiftAssignmentStatus.ACTIVE; }
    public boolean isCompleted() { return status == ShiftAssignmentStatus.COMPLETED; }
    public boolean isCancelled() { return status == ShiftAssignmentStatus.CANCELLED; }
}
