package com.sanad.platform.hr.payroll.infrastructure;

import com.sanad.platform.hr.compensation.application.CompensationService;
import com.sanad.platform.hr.compensation.domain.CompensationComponent;
import com.sanad.platform.hr.compensation.domain.CompensationPackage;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.contract.domain.EmploymentContractRepository;
import com.sanad.platform.hr.contract.domain.EmploymentContractVersion;
import com.sanad.platform.hr.employment.Employment;
import com.sanad.platform.hr.employment.EmploymentRepository;
import com.sanad.platform.hr.employment.EmploymentStatus;
import com.sanad.platform.hr.employment.EmploymentStatusPeriod;
import com.sanad.platform.hr.payroll.application.PayrollAuthoritativeInputPort;
import com.sanad.platform.hr.time.application.HrLeaveService;
import com.sanad.platform.hr.time.application.HrTimesheetService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * G4-T3 adapter over authoritative G0/G2 application/domain contracts.
 *
 * <p>There is deliberately no payroll-owned SQL against employment,
 * compensation, timesheet or leave tables. Compensation reads retain the
 * existing fail-closed sensitive-read audit and authorization boundary.</p>
 */
@Component
public class CanonicalHrPayrollInputAdapter implements PayrollAuthoritativeInputPort {

    static final String COMPENSATION_READ_REASON = "HRM.G4.PAYROLL_INPUT_SNAPSHOT";

    private final EmploymentRepository employmentRepository;
    private final EmploymentContractRepository contractRepository;
    private final CompensationService compensationService;
    private final HrTimesheetService timesheetService;
    private final HrLeaveService leaveService;

    public CanonicalHrPayrollInputAdapter(
            EmploymentRepository employmentRepository,
            EmploymentContractRepository contractRepository,
            CompensationService compensationService,
            HrTimesheetService timesheetService,
            HrLeaveService leaveService) {
        this.employmentRepository = Objects.requireNonNull(employmentRepository);
        this.contractRepository = Objects.requireNonNull(contractRepository);
        this.compensationService = Objects.requireNonNull(compensationService);
        this.timesheetService = Objects.requireNonNull(timesheetService);
        this.leaveService = Objects.requireNonNull(leaveService);
    }

    @Override
    public PayrollInputSnapshot load(
            HrCommandContext context,
            UUID employmentId,
            LocalDate periodStart,
            LocalDate periodEnd) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(employmentId, "employmentId");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("HRM_PAYROLL_INPUT_PERIOD_INVALID");
        }

        UUID tenantId = Objects.requireNonNull(context.tenantId(), "context.tenantId");
        Employment employment = employmentRepository.findEmploymentById(tenantId, employmentId)
                .orElseThrow(() -> new IllegalStateException(
                        "HRM_PAYROLL_EMPLOYMENT_INPUT_MISSING: " + employmentId));

        EmploymentStatusPeriod statusPeriod = requireEffectivePayrollStatus(
                employmentRepository.statusPeriods(tenantId, employmentId), periodEnd);

        EmploymentContractVersion contract = contractRepository
                .findActivePrimaryVersion(tenantId, employmentId, periodEnd)
                .orElseThrow(() -> new IllegalStateException(
                        "HRM_PAYROLL_CONTRACT_INPUT_MISSING: " + employmentId + "@" + periodEnd));

        CompensationPackage compensation = compensationService.readActivePackageWithAudit(
                context, employmentId, periodEnd, COMPENSATION_READ_REASON);

        requireSourceCongruence(tenantId, employmentId, employment, contract, compensation);

        HrTimesheetService.PayrollTimesheetInput timesheet =
                timesheetService.requireApprovedPayrollInput(
                        tenantId, employmentId, periodStart, periodEnd);

        List<HrLeaveService.PayrollLeaveInput> leaveInputs =
                leaveService.listApprovedPayrollInputs(
                        tenantId, employmentId, periodStart, periodEnd);

        List<CompensationComponentInput> components = compensation.components().stream()
                .sorted(Comparator
                        .comparing(CompensationComponent::code)
                        .thenComparing(c -> c.id().toString()))
                .map(c -> new CompensationComponentInput(
                        c.id(),
                        c.componentType().name(),
                        c.code(),
                        c.amount(),
                        c.percentage()))
                .toList();

        List<LeaveInput> leaves = leaveInputs.stream()
                .sorted(Comparator
                        .comparing(HrLeaveService.PayrollLeaveInput::startDate)
                        .thenComparing(HrLeaveService.PayrollLeaveInput::endDate)
                        .thenComparing(l -> l.requestId().toString()))
                .map(l -> new LeaveInput(
                        l.requestId(),
                        l.leaveTypeId(),
                        l.startDate(),
                        l.endDate(),
                        l.daysCount(),
                        l.paid(),
                        l.approvedAt()))
                .toList();

        return new PayrollInputSnapshot(
                tenantId,
                employment.id(),
                employment.personId(),
                employment.legalEntityId(),
                employment.version(),
                statusPeriod.id(),
                statusPeriod.status().name(),
                contract.id(),
                contract.versionNumber(),
                compensation.id(),
                compensation.version(),
                compensation.currencyCode(),
                compensation.payFrequency(),
                components,
                new TimesheetInput(
                        timesheet.id(),
                        timesheet.periodStart(),
                        timesheet.periodEnd(),
                        timesheet.totalWorkedMinutes(),
                        timesheet.totalBreakMinutes(),
                        timesheet.version(),
                        timesheet.approvedAt()),
                leaves,
                periodEnd);
    }

    private EmploymentStatusPeriod requireEffectivePayrollStatus(
            List<EmploymentStatusPeriod> periods, LocalDate effectiveOn) {
        List<EmploymentStatusPeriod> matches = periods.stream()
                .filter(p -> !effectiveOn.isBefore(p.effectiveFrom()))
                .filter(p -> p.effectiveTo() == null || effectiveOn.isBefore(p.effectiveTo()))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "HRM_PAYROLL_EMPLOYMENT_STATUS_AMBIGUOUS: expected exactly one effective status, found "
                            + matches.size());
        }
        EmploymentStatus status = matches.get(0).status();
        if (status != EmploymentStatus.ACTIVE
                && status != EmploymentStatus.ON_LEAVE
                && status != EmploymentStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "HRM_PAYROLL_EMPLOYMENT_NOT_ELIGIBLE: " + status);
        }
        return matches.get(0);
    }

    private void requireSourceCongruence(
            UUID tenantId,
            UUID employmentId,
            Employment employment,
            EmploymentContractVersion contract,
            CompensationPackage compensation) {
        if (!tenantId.equals(employment.tenantId())
                || !tenantId.equals(contract.tenantId())
                || !tenantId.equals(compensation.tenantId())
                || !employmentId.equals(employment.id())
                || !employmentId.equals(contract.employmentId())
                || !employmentId.equals(compensation.employmentId())) {
            throw new IllegalStateException("HRM_PAYROLL_AUTHORITATIVE_INPUT_MISMATCH");
        }
    }
}
