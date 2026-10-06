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

class PayrollCalculationEngineTest {

    private final PayrollCalculationEngine engine = new PayrollCalculationEngine();

    @Test
    void calculates_country_neutral_matrix_with_explicit_policy() {
        var snapshot = snapshot(List.of(
                component("BASE_SALARY", "BASE", "1000.0000", null),
                component("ALLOWANCE", "HOUSING", "200.0000", null),
                component("ALLOWANCE", "TRANSPORT", null, "10.0000"),
                component("BENEFIT", "BENEFIT_ONLY", "999.0000", null)
        ), List.of(unpaidLeave("2.0000")));

        var policy = new PayrollCalculationEngine.CalculationPolicy(
                List.of(
                        PayrollCalculationEngine.ConfiguredAllowance.fixed("HOUSING"),
                        PayrollCalculationEngine.ConfiguredAllowance.percentage(
                                "TRANSPORT", PayrollCalculationEngine.PercentageBasis.BASE)),
                List.of(
                        PayrollCalculationEngine.ConfiguredDeduction.fixed("LOAN", new BigDecimal("50.0000")),
                        PayrollCalculationEngine.ConfiguredDeduction.percentage(
                                "SAVINGS", new BigDecimal("5.0000"),
                                PayrollCalculationEngine.PercentageBasis.GROSS)
                ),
                new PayrollCalculationEngine.UnpaidLeavePolicy(true, new BigDecimal("40.0000"))
        );

        var result = engine.calculate(snapshot, policy);

        assertEquals(new BigDecimal("1000.0000"), result.baseAmount());
        assertEquals(new BigDecimal("1300.0000"), result.grossAmount());
        assertEquals(new BigDecimal("195.0000"), result.deductionTotal());
        assertEquals(new BigDecimal("1105.0000"), result.netAmount());
        assertEquals(List.of("HOUSING", "TRANSPORT"),
                result.earnings().stream().map(PayrollCalculationEngine.CalculationLine::code).toList());
        assertEquals(List.of("LOAN", "SAVINGS", "UNPAID_LEAVE"),
                result.deductions().stream().map(PayrollCalculationEngine.CalculationLine::code).toList());
    }

    @Test
    void unpaid_leave_is_not_deducted_unless_policy_explicitly_enables_it() {
        var snapshot = snapshot(
                List.of(component("BASE_SALARY", "BASE", "1000.0000", null)),
                List.of(unpaidLeave("3.0000")));

        var policy = new PayrollCalculationEngine.CalculationPolicy(
                List.of(),
                List.of(),
                PayrollCalculationEngine.UnpaidLeavePolicy.disabled()
        );

        var result = engine.calculate(snapshot, policy);

        assertEquals(new BigDecimal("0.0000"), result.deductionTotal());
        assertEquals(new BigDecimal("1000.0000"), result.netAmount());
    }

    @Test
    void same_snapshot_and_policy_are_deterministic_and_round_to_four_decimals() {
        var snapshot = snapshot(List.of(
                component("BASE_SALARY", "BASE", "999.99995", null),
                component("ALLOWANCE", "ROUNDING", null, "2.55555")
        ), List.of());

        var policy = new PayrollCalculationEngine.CalculationPolicy(
                List.of(PayrollCalculationEngine.ConfiguredAllowance.percentage(
                        "ROUNDING", PayrollCalculationEngine.PercentageBasis.BASE)),
                List.of(PayrollCalculationEngine.ConfiguredDeduction.percentage(
                        "PCT", new BigDecimal("1.11111"),
                        PayrollCalculationEngine.PercentageBasis.BASE)),
                PayrollCalculationEngine.UnpaidLeavePolicy.disabled()
        );

        var first = engine.calculate(snapshot, policy);
        var second = engine.calculate(snapshot, policy);

        assertEquals(first, second);
        assertEquals(4, first.baseAmount().scale());
        assertEquals(4, first.grossAmount().scale());
        assertEquals(4, first.deductionTotal().scale());
        assertEquals(4, first.netAmount().scale());
    }

    @Test
    void fails_closed_when_base_salary_is_missing_or_percentage_based() {
        var policy = PayrollCalculationEngine.CalculationPolicy.noAdjustments();

        assertThrows(IllegalStateException.class,
                () -> engine.calculate(snapshot(List.of(), List.of()), policy));

        assertThrows(IllegalStateException.class,
                () -> engine.calculate(snapshot(List.of(
                        component("BASE_SALARY", "BASE", null, "100.0000")), List.of()), policy));
    }

    @Test
    void fails_closed_when_configured_deductions_exceed_gross() {
        var snapshot = snapshot(
                List.of(component("BASE_SALARY", "BASE", "100.0000", null)), List.of());
        var policy = new PayrollCalculationEngine.CalculationPolicy(
                List.of(),
                List.of(PayrollCalculationEngine.ConfiguredDeduction.fixed(
                        "RECOVERY", new BigDecimal("101.0000"))),
                PayrollCalculationEngine.UnpaidLeavePolicy.disabled());

        assertThrows(IllegalStateException.class, () -> engine.calculate(snapshot, policy));
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
                        9600, 0, 5, Instant.parse("2026-09-30T12:00:00Z")),
                leave,
                LocalDate.of(2026, 9, 30));
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

    private PayrollAuthoritativeInputPort.LeaveInput unpaidLeave(String days) {
        return new PayrollAuthoritativeInputPort.LeaveInput(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 11),
                new BigDecimal(days),
                false,
                Instant.parse("2026-09-05T10:00:00Z"));
    }
}
