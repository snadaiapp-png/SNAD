package com.sanad.platform.hr.payroll;

import com.sanad.platform.hr.compensation.application.CompensationService;
import com.sanad.platform.hr.compensation.domain.CompensationComponent;
import com.sanad.platform.hr.compensation.domain.CompensationComponentType;
import com.sanad.platform.hr.compensation.domain.CompensationPackage;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.contract.domain.EmploymentContractRepository;
import com.sanad.platform.hr.contract.domain.EmploymentContractStatus;
import com.sanad.platform.hr.contract.domain.EmploymentContractVersion;
import com.sanad.platform.hr.employment.Employment;
import com.sanad.platform.hr.employment.EmploymentRepository;
import com.sanad.platform.hr.employment.EmploymentStatus;
import com.sanad.platform.hr.employment.EmploymentStatusPeriod;
import com.sanad.platform.hr.payroll.application.PayrollAuthoritativeInputPort;
import com.sanad.platform.hr.payroll.infrastructure.CanonicalHrPayrollInputAdapter;
import com.sanad.platform.hr.time.application.HrLeaveService;
import com.sanad.platform.hr.time.application.HrTimesheetService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CanonicalHrPayrollInputAdapterTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYMENT = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID PERSON = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID LEGAL_ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID CORRELATION = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);

    @Test
    void loadPinsAuthoritativeVersionsAndReturnsDeterministicOrdering() {
        EmploymentRepository employments = mock(EmploymentRepository.class);
        EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
        CompensationService compensation = mock(CompensationService.class);
        HrTimesheetService timesheets = mock(HrTimesheetService.class);
        HrLeaveService leave = mock(HrLeaveService.class);

        Employment employment = new Employment(
                EMPLOYMENT, TENANT, PERSON, LEGAL_ENTITY, "E-101", "FULL_TIME",
                EmploymentStatus.ACTIVE, LocalDate.of(2026, 1, 1), null, null, 7L);
        EmploymentStatusPeriod status = new EmploymentStatusPeriod(
                UUID.fromString("00000000-0000-0000-0000-000000000601"),
                TENANT, EMPLOYMENT, EmploymentStatus.ACTIVE,
                LocalDate.of(2026, 1, 1), null, "ACTIVATE", null, ACTOR, null);
        EmploymentContractVersion contract = new EmploymentContractVersion(
                UUID.fromString("00000000-0000-0000-0000-000000000701"),
                TENANT,
                UUID.fromString("00000000-0000-0000-0000-000000000702"),
                EMPLOYMENT,
                3,
                EmploymentContractStatus.ACTIVE,
                true,
                "FIXED_TERM",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 7, 1),
                null,
                null,
                null,
                ACTOR,
                Instant.parse("2026-07-01T00:00:00Z"));

        UUID packageId = UUID.fromString("00000000-0000-0000-0000-000000000801");
        CompensationComponent allowance = new CompensationComponent(
                UUID.fromString("00000000-0000-0000-0000-000000000803"),
                TENANT, packageId, CompensationComponentType.ALLOWANCE,
                "Z_ALLOWANCE", new BigDecimal("500.0000"), null);
        CompensationComponent base = new CompensationComponent(
                UUID.fromString("00000000-0000-0000-0000-000000000802"),
                TENANT, packageId, CompensationComponentType.BASE_SALARY,
                "BASE", new BigDecimal("10000.0000"), null);
        CompensationPackage pkg = new CompensationPackage(
                packageId, TENANT, EMPLOYMENT, "SAR", "MONTHLY",
                LocalDate.of(2026, 7, 1), null, CompensationPackage.STATUS_ACTIVE,
                null, List.of(allowance, base), 4L, Instant.parse("2026-07-01T00:00:00Z"));

        HrTimesheetService.PayrollTimesheetInput timesheet =
                new HrTimesheetService.PayrollTimesheetInput(
                        UUID.fromString("00000000-0000-0000-0000-000000000901"),
                        EMPLOYMENT, START, END, 9600, 600, "APPROVED",
                        Instant.parse("2026-11-01T09:00:00Z"), 5);

        HrLeaveService.PayrollLeaveInput laterLeave = new HrLeaveService.PayrollLeaveInput(
                UUID.fromString("00000000-0000-0000-0000-000000001002"),
                UUID.fromString("00000000-0000-0000-0000-000000001102"),
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21),
                new BigDecimal("2.00"), false, Instant.parse("2026-10-15T10:00:00Z"));
        HrLeaveService.PayrollLeaveInput earlierLeave = new HrLeaveService.PayrollLeaveInput(
                UUID.fromString("00000000-0000-0000-0000-000000001001"),
                UUID.fromString("00000000-0000-0000-0000-000000001101"),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5),
                new BigDecimal("1.00"), true, Instant.parse("2026-10-01T10:00:00Z"));

        HrCommandContext ctx = new HrCommandContext(TENANT, EMPLOYMENT, ACTOR, CORRELATION);
        when(employments.findEmploymentById(TENANT, EMPLOYMENT)).thenReturn(Optional.of(employment));
        when(employments.statusPeriods(TENANT, EMPLOYMENT)).thenReturn(List.of(status));
        when(contracts.findActivePrimaryVersion(TENANT, EMPLOYMENT, END)).thenReturn(Optional.of(contract));
        when(compensation.readActivePackageWithAudit(
                ctx, EMPLOYMENT, END, "HRM.G4.PAYROLL_INPUT_SNAPSHOT")).thenReturn(pkg);
        when(timesheets.requireApprovedPayrollInput(TENANT, EMPLOYMENT, START, END)).thenReturn(timesheet);
        when(leave.listApprovedPayrollInputs(TENANT, EMPLOYMENT, START, END))
                .thenReturn(List.of(laterLeave, earlierLeave));

        PayrollAuthoritativeInputPort port = new CanonicalHrPayrollInputAdapter(
                employments, contracts, compensation, timesheets, leave);

        PayrollAuthoritativeInputPort.PayrollInputSnapshot first =
                port.load(ctx, EMPLOYMENT, START, END);
        PayrollAuthoritativeInputPort.PayrollInputSnapshot second =
                port.load(ctx, EMPLOYMENT, START, END);

        assertThat(first).isEqualTo(second);
        assertThat(first.employmentVersion()).isEqualTo(7L);
        assertThat(first.contractVersionNumber()).isEqualTo(3);
        assertThat(first.compensationPackageVersion()).isEqualTo(4L);
        assertThat(first.timesheet().version()).isEqualTo(5);
        assertThat(first.timesheet().periodStart()).isEqualTo(START);
        assertThat(first.timesheet().periodEnd()).isEqualTo(END);
        assertThat(first.compensationComponents())
                .extracting(PayrollAuthoritativeInputPort.CompensationComponentInput::code)
                .containsExactly("BASE", "Z_ALLOWANCE");
        assertThat(first.approvedLeave())
                .extracting(PayrollAuthoritativeInputPort.LeaveInput::requestId)
                .containsExactly(earlierLeave.requestId(), laterLeave.requestId());
        assertThat(first.effectiveOn()).isEqualTo(END);
    }

    @Test
    void loadFailsClosedWhenEffectiveEmploymentStatusIsAmbiguous() {
        EmploymentRepository employments = mock(EmploymentRepository.class);
        EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
        CompensationService compensation = mock(CompensationService.class);
        HrTimesheetService timesheets = mock(HrTimesheetService.class);
        HrLeaveService leave = mock(HrLeaveService.class);

        Employment employment = new Employment(
                EMPLOYMENT, TENANT, PERSON, LEGAL_ENTITY, "E-101", "FULL_TIME",
                EmploymentStatus.ACTIVE, LocalDate.of(2026, 1, 1), null, null, 1L);
        EmploymentStatusPeriod p1 = new EmploymentStatusPeriod(
                UUID.randomUUID(), TENANT, EMPLOYMENT, EmploymentStatus.ACTIVE,
                LocalDate.of(2026, 1, 1), null, "A", null, ACTOR, null);
        EmploymentStatusPeriod p2 = new EmploymentStatusPeriod(
                UUID.randomUUID(), TENANT, EMPLOYMENT, EmploymentStatus.ON_LEAVE,
                LocalDate.of(2026, 10, 1), null, "B", null, ACTOR, null);

        when(employments.findEmploymentById(TENANT, EMPLOYMENT)).thenReturn(Optional.of(employment));
        when(employments.statusPeriods(TENANT, EMPLOYMENT)).thenReturn(List.of(p1, p2));

        PayrollAuthoritativeInputPort port = new CanonicalHrPayrollInputAdapter(
                employments, contracts, compensation, timesheets, leave);

        assertThatThrownBy(() -> port.load(
                new HrCommandContext(TENANT, EMPLOYMENT, ACTOR, CORRELATION),
                EMPLOYMENT, START, END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_EMPLOYMENT_STATUS_AMBIGUOUS");
    }

    @Test
    void loadFailsClosedWhenApprovedTimesheetIsMissing() {
        EmploymentRepository employments = mock(EmploymentRepository.class);
        EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
        CompensationService compensation = mock(CompensationService.class);
        HrTimesheetService timesheets = mock(HrTimesheetService.class);
        HrLeaveService leave = mock(HrLeaveService.class);

        Employment employment = new Employment(
                EMPLOYMENT, TENANT, PERSON, LEGAL_ENTITY, "E-101", "FULL_TIME",
                EmploymentStatus.ACTIVE, LocalDate.of(2026, 1, 1), null, null, 1L);
        EmploymentStatusPeriod status = new EmploymentStatusPeriod(
                UUID.randomUUID(), TENANT, EMPLOYMENT, EmploymentStatus.ACTIVE,
                LocalDate.of(2026, 1, 1), null, "A", null, ACTOR, null);
        EmploymentContractVersion contract = new EmploymentContractVersion(
                UUID.randomUUID(), TENANT, UUID.randomUUID(), EMPLOYMENT, 1,
                EmploymentContractStatus.ACTIVE, true, "FIXED_TERM",
                LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 1, 1),
                null, null, null, ACTOR, Instant.now());
        CompensationPackage pkg = new CompensationPackage(
                UUID.randomUUID(), TENANT, EMPLOYMENT, "SAR", "MONTHLY",
                LocalDate.of(2026, 1, 1), null, CompensationPackage.STATUS_ACTIVE,
                null, List.of(), 1L, Instant.now());
        HrCommandContext ctx = new HrCommandContext(TENANT, EMPLOYMENT, ACTOR, CORRELATION);

        when(employments.findEmploymentById(TENANT, EMPLOYMENT)).thenReturn(Optional.of(employment));
        when(employments.statusPeriods(TENANT, EMPLOYMENT)).thenReturn(List.of(status));
        when(contracts.findActivePrimaryVersion(TENANT, EMPLOYMENT, END)).thenReturn(Optional.of(contract));
        when(compensation.readActivePackageWithAudit(
                ctx, EMPLOYMENT, END, "HRM.G4.PAYROLL_INPUT_SNAPSHOT")).thenReturn(pkg);
        when(timesheets.requireApprovedPayrollInput(TENANT, EMPLOYMENT, START, END))
                .thenThrow(new IllegalStateException(
                        "HRM_PAYROLL_TIMESHEET_INPUT_INVALID: expected exactly one APPROVED timesheet, found 0"));

        PayrollAuthoritativeInputPort port = new CanonicalHrPayrollInputAdapter(
                employments, contracts, compensation, timesheets, leave);

        assertThatThrownBy(() -> port.load(ctx, EMPLOYMENT, START, END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_TIMESHEET_INPUT_INVALID");
    }
}
