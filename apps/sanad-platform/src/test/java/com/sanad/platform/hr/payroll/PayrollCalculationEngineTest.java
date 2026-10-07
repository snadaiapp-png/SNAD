package com.sanad.platform.hr.payroll;

import com.sanad.platform.hr.payroll.application.PayrollAuthoritativeInputPort;
import com.sanad.platform.hr.payroll.application.PayrollCalculationEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollCalculationEngineTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 9, 30);

    private final PayrollCalculationEngine engine = new PayrollCalculationEngine();

    @Test
    void calculatesCountryNeutralMatrixWithExplicitPolicy() {
        var snapshot = snapshot(List.of(
                component("BASE_SALARY", "BASE", "1000.0000", null),
                component("ALLOWANCE", "HOUSING", "200.0000", null),
                component("ALLOWANCE", "UNCONFIGURED", "700.0000", null),
                component("BENEFIT", "BENEFIT_ONLY", "999.0000", null)
        ), List.of(unpaidLeave("2.0000", PERIOD_START.plusDays(9), PERIOD_START.plusDays(10))));

        var policy = new PayrollCalculationEngine.CalculationPolicy(
                "MONTHLY",
                List.of(PayrollCalculationEngine.ConfiguredAllowance.fixed("HOUSING")),
                List.of(
                        PayrollCalculationEngine.ConfiguredDeduction.fixed(
                                "LOAN_RECOVERY", new BigDecimal("50.0000")),
                        PayrollCalculationEngine.ConfiguredDeduction.percentage(
                                "VOLUNTARY_SAVINGS", new BigDecimal("5.0000"),
                                PayrollCalculationEngine.PercentageBasis.GROSS)
                ),
                new PayrollCalculationEngine.UnpaidLeavePolicy(true, new BigDecimal("40.0000"))
        );

        var first = engine.calculate(snapshot, policy);
        var second = engine.calculate(snapshot, policy);

        assertEquals(first, second);
        assertEquals(new BigDecimal("1000.0000"), first.baseAmount());
        assertEquals(new BigDecimal("200.0000"), first.allowanceTotal());
        assertEquals(new BigDecimal("1200.0000"), first.grossAmount());
        assertEquals(new BigDecimal("110.0000"), first.nonStatutoryDeductionTotal());
        assertEquals(new BigDecimal("80.0000"), first.unpaidLeaveDeduction());
        assertEquals(new BigDecimal("190.0000"), first.deductionTotal());
        assertEquals(new BigDecimal("1010.0000"), first.netAmount());
        assertEquals(List.of("HOUSING"),
                first.earnings().stream().map(PayrollCalculationEngine.CalculationLine::code).toList());
        assertEquals(List.of("LOAN_RECOVERY", "UNPAID_LEAVE", "VOLUNTARY_SAVINGS"),
                first.deductions().stream().map(PayrollCalculationEngine.CalculationLine::code).toList());
    }

    @Test
    void roundsAllMonetaryOutputsToFourDecimalsDeterministically() {
        var snapshot = snapshot(
                List.of(component("BASE_SALARY", "BASE", "999.99995", null)),
                List.of());

        var policy = new PayrollCalculationEngine.CalculationPolicy(
                "MONTHLY",
                List.of(),
                List.of(PayrollCalculationEngine.ConfiguredDeduction.percentage(
                        "VOLUNTARY_PLAN", new BigDecimal("1.11111"),
                        PayrollCalculationEngine.PercentageBasis.BASE)),
                PayrollCalculationEngine.UnpaidLeavePolicy.disabled()
        );

        var result = engine.calculate(snapshot, policy);

        assertEquals(new BigDecimal("1000.0000"), result.baseAmount());
        assertEquals(new BigDecimal("11.1111"), result.deductionTotal());
        assertEquals(new BigDecimal("988.8889"), result.netAmount());
        assertEquals(4, result.baseAmount().scale());
        assertEquals(4, result.grossAmount().scale());
        assertEquals(4, result.deductionTotal().scale());
        assertEquals(4, result.netAmount().scale());
    }

    @Test
    void failsClosedWhenBaseSalaryIsMissingOrPercentageBased() {
        var policy = PayrollCalculationEngine.CalculationPolicy.noAdjustments("MONTHLY");

        var missing = assertThrows(IllegalStateException.class,
                () -> engine.calculate(snapshot(List.of(), List.of()), policy));
        assertTrue(missing.getMessage().contains("HRM_PAYROLL_BASE_SALARY_INVALID"));

        var percentage = assertThrows(IllegalStateException.class,
                () -> engine.calculate(snapshot(List.of(
                        component("BASE_SALARY", "BASE", null, "100.0000")), List.of()), policy));
        assertTrue(percentage.getMessage().contains("HRM_PAYROLL_BASE_SALARY_INVALID"));
    }

    @Test
    void failsClosedOnPercentageAllowanceBecauseBasisIsNotAuthoritative() {
        var snapshot = snapshot(List.of(
                component("BASE_SALARY", "BASE", "1000.0000", null),
                component("ALLOWANCE", "HOUSING", null, "25.0000")
        ), List.of());

        var policy = new PayrollCalculationEngine.CalculationPolicy(
                "MONTHLY",
                List.of(PayrollCalculationEngine.ConfiguredAllowance.fixed("HOUSING")),
                List.of(),
                PayrollCalculationEngine.UnpaidLeavePolicy.disabled());

        var failure = assertThrows(IllegalStateException.class, () -> engine.calculate(snapshot, policy));
        assertTrue(failure.getMessage().contains("HRM_PAYROLL_ALLOWANCE_BASIS_AMBIGUOUS"));
    }

    @Test
    void failsClosedWhenPayFrequencyWouldRequireImplicitConversion() {
        var snapshot = snapshot(
                List.of(component("BASE_SALARY", "BASE", "1000.0000", null)),
                List.of());

        var policy = PayrollCalculationEngine.CalculationPolicy.noAdjustments("BIWEEKLY");

        var failure = assertThrows(IllegalStateException.class, () -> engine.calculate(snapshot, policy));
        assertTrue(failure.getMessage().contains("HRM_PAYROLL_PAY_FREQUENCY_MISMATCH"));
    }

    @Test
    void failsClosedForCrossPeriodUnpaidLeaveInsteadOfRecalculatingLeaveDays() {
        var snapshot = snapshot(
                List.of(component("BASE_SALARY", "BASE", "1000.0000", null)),
                List.of(unpaidLeave("4.0000", PERIOD_START.minusDays(2), PERIOD_START.plusDays(1))));

        var policy = new PayrollCalculationEngine.CalculationPolicy(
                "MONTHLY",
                List.of(),
                List.of(),
                new PayrollCalculationEngine.UnpaidLeavePolicy(true, new BigDecimal("40.0000")));

        var failure = assertThrows(IllegalStateException.class, () -> engine.calculate(snapshot, policy));
        assertTrue(failure.getMessage().contains("HRM_PAYROLL_UNPAID_LEAVE_PERIOD_AMBIGUOUS"));
    }

    @Test
    void unpaidLeaveIsNotDeductedUnlessPolicyExplicitlyEnablesIt() {
        var snapshot = snapshot(
                List.of(component("BASE_SALARY", "BASE", "1000.0000", null)),
                List.of(unpaidLeave("3.0000", PERIOD_START.plusDays(3), PERIOD_START.plusDays(5))));

        var result = engine.calculate(
                snapshot,
                PayrollCalculationEngine.CalculationPolicy.noAdjustments("MONTHLY"));

        assertEquals(new BigDecimal("0.0000"), result.deductionTotal());
        assertEquals(new BigDecimal("1000.0000"), result.netAmount());
    }

    @Test
    void rejectsReservedStatutoryDeductionCodes() {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> PayrollCalculationEngine.ConfiguredDeduction.percentage(
                        "GOSI", new BigDecimal("9.7500"),
                        PayrollCalculationEngine.PercentageBasis.BASE));
        assertTrue(failure.getMessage().contains("HRM_PAYROLL_STATUTORY_RULE_NOT_AUTHORIZED"));
    }

    @Test
    void failsClosedWhenConfiguredDeductionsExceedGross() {
        var snapshot = snapshot(
                List.of(component("BASE_SALARY", "BASE", "100.0000", null)), List.of());
        var policy = new PayrollCalculationEngine.CalculationPolicy(
                "MONTHLY",
                List.of(),
                List.of(PayrollCalculationEngine.ConfiguredDeduction.fixed(
                        "RECOVERY", new BigDecimal("101.0000"))),
                PayrollCalculationEngine.UnpaidLeavePolicy.disabled());

        var failure = assertThrows(IllegalStateException.class, () -> engine.calculate(snapshot, policy));
        assertTrue(failure.getMessage().contains("HRM_PAYROLL_NEGATIVE_NET_REJECTED"));
    }

    private PayrollAuthoritativeInputPort.PayrollInputSnapshot snapshot(
            List<PayrollAuthoritativeInputPort.CompensationComponentInput> components,
            List<PayrollAuthoritativeInputPort.LeaveInput> leave) {
        UUID tenant = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID employment = UUID.fromString("22222222-2222-4222-8222-222222222222");
        return new PayrollAuthoritativeInputPort.PayrollInputSnapshot(
                tenant,
                employment,
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                UUID.fromString("44444444-4444-4444-8444-444444444444"),
                7L,
                UUID.fromString("55555555-5555-4555-8555-555555555555"),
                "ACTIVE",
                UUID.fromString("66666666-6666-4666-8666-666666666666"),
                3,
                UUID.fromString("77777777-7777-4777-8777-777777777777"),
                4L,
                "SAR",
                "MONTHLY",
                components,
                new PayrollAuthoritativeInputPort.TimesheetInput(
                        UUID.fromString("88888888-8888-4888-8888-888888888888"),
                        PERIOD_START,
                        PERIOD_END,
                        9600,
                        0,
                        5,
                        Instant.parse("2026-10-01T12:00:00Z")),
                leave,
                PERIOD_END);
    }

    private PayrollAuthoritativeInputPort.CompensationComponentInput component(
            String type, String code, String amount, String percentage) {
        return new PayrollAuthoritativeInputPort.CompensationComponentInput(
                UUID.nameUUIDFromBytes((type + ":" + code).getBytes()),
                type,
                code,
                amount == null ? null : new BigDecimal(amount),
                percentage == null ? null : new BigDecimal(percentage));
    }

    private PayrollAuthoritativeInputPort.LeaveInput unpaidLeave(
            String days,
            LocalDate start,
            LocalDate end) {
        return new PayrollAuthoritativeInputPort.LeaveInput(
                UUID.randomUUID(),
                UUID.randomUUID(),
                start,
                end,
                new BigDecimal(days),
                false,
                Instant.parse("2026-09-05T10:00:00Z"));
    }
}
