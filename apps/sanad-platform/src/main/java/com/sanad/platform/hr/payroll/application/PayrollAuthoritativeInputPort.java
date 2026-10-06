package com.sanad.platform.hr.payroll.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * G4-T3 application port for deterministic, version-pinned payroll inputs.
 *
 * <p>The port exposes HR-owned canonical inputs only. Implementations must
 * fail closed when canonical employment, contract, compensation or approved
 * time input is missing or ambiguous. No attendance/statutory recalculation
 * belongs here.</p>
 */
public interface PayrollAuthoritativeInputPort {

    PayrollInputSnapshot load(
            HrCommandContext context,
            UUID employmentId,
            LocalDate periodStart,
            LocalDate periodEnd);

    record PayrollInputSnapshot(
            UUID tenantId,
            UUID employmentId,
            UUID personId,
            UUID legalEntityId,
            long employmentVersion,
            UUID employmentStatusPeriodId,
            String employmentStatus,
            UUID contractVersionId,
            int contractVersionNumber,
            UUID compensationPackageId,
            long compensationPackageVersion,
            String currencyCode,
            String payFrequency,
            List<CompensationComponentInput> compensationComponents,
            TimesheetInput timesheet,
            List<LeaveInput> approvedLeave,
            LocalDate effectiveOn
    ) {
        public PayrollInputSnapshot {
            compensationComponents = List.copyOf(compensationComponents);
            approvedLeave = List.copyOf(approvedLeave);
        }
    }

    record CompensationComponentInput(
            UUID componentId,
            String type,
            String code,
            BigDecimal amount,
            BigDecimal percentage
    ) {}

    record TimesheetInput(
            UUID timesheetId,
            int totalWorkedMinutes,
            int totalBreakMinutes,
            int version,
            Instant approvedAt
    ) {}

    record LeaveInput(
            UUID requestId,
            UUID leaveTypeId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal daysCount,
            boolean paid,
            Instant approvedAt
    ) {}
}
