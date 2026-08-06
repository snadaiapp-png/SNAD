package com.sanad.platform.crm.ownership.domain.workload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Workload assignment entity (CRM-008).
 *
 * <p>Tracks workload assigned to staff members. Links to services and jobs
 * with estimated and actual hours.
 */
public record WorkloadAssignment(
        UUID id,
        UUID tenantId,
        UUID staffId,
        UUID serviceId,
        UUID jobId,
        int estimatedHours,
        Integer actualHours,
        WorkloadStatus status,
        LocalDate startDate,
        LocalDate endDate,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public WorkloadAssignment {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (staffId == null) throw new IllegalArgumentException("staffId required");
        if (estimatedHours <= 0) throw new IllegalArgumentException("estimatedHours must be positive");
        if (startDate == null) throw new IllegalArgumentException("startDate required");
        if (status == null) status = WorkloadStatus.PLANNED;
    }

    public boolean isPlanned() { return status == WorkloadStatus.PLANNED; }
    public boolean isInProgress() { return status == WorkloadStatus.IN_PROGRESS; }
    public boolean isCompleted() { return status == WorkloadStatus.COMPLETED; }
    public boolean isCancelled() { return status == WorkloadStatus.CANCELLED; }
}
