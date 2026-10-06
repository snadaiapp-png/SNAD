package com.sanad.platform.hr.payroll.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * G4-T4 country-neutral, deterministic payroll calculation engine.
 *
 * <p>This engine deliberately performs no statutory inference, pay-frequency
 * conversion, attendance recalculation, tax, GOSI, WPS, EOS or overtime
 * premium logic. Any non-statutory deduction and unpaid-leave rate must be
 * supplied explicitly by an approved tenant policy.</p>
 */
public final class PayrollCalculationEngine {

    public static final int MONEY_SCALE = 4;
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public CalculationResult calculate(
            PayrollAuthoritativeInputPort.PayrollInputSnapshot snapshot,
            CalculationPolicy policy) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(policy, "policy");

        List<PayrollAuthoritativeInputPort.CompensationComponentInput> baseComponents =
                snapshot.compensationComponents().stream()
                        .filter(c -> "BASE_SALARY".equals(c.type()))
                        .toList();
        if (baseComponents.size() != 1) {
            throw new IllegalStateException(
                    "HRM_PAYROLL_BASE_SALARY_INVALID: expected exactly one BASE_SALARY, found "
                            + baseComponents.size());
        }

        var baseComponent = baseComponents.get(0);
        if (baseComponent.amount() == null || baseComponent.percentage() != null) {
            throw new IllegalStateException(
                    "HRM_PAYROLL_BASE_SALARY_INVALID: BASE_SALARY must be a fixed amount");
        }
        BigDecimal base = money(baseComponent.amount());

        List<CalculationLine> earnings = calculateAllowances(
                snapshot.compensationComponents(), policy.enabledAllowanceCodes(), base);

        BigDecimal allowanceTotal = earnings.stream()
                .map(CalculationLine::amount)
                .reduce(zero(), BigDecimal::add);
        BigDecimal gross = money(base.add(allowanceTotal));

        List<CalculationLine> deductions = calculateConfiguredDeductions(
                policy.configuredDeductions(), base, gross);

        if (policy.unpaidLeavePolicy().enabled()) {
            BigDecimal unpaidDays = snapshot.approvedLeave().stream()
                    .filter(l -> !l.paid())
                    .map(PayrollAuthoritativeInputPort.LeaveInput::daysCount)
                    .peek(days -> {
                        if (days == null || days.signum() < 0) {
                            throw new IllegalStateException(
                                    "HRM_PAYROLL_UNPAID_LEAVE_INPUT_INVALID");
                        }
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (unpaidDays.signum() > 0) {
                BigDecimal unpaidAmount = money(
                        unpaidDays.multiply(policy.unpaidLeavePolicy().dailyRate()));
                deductions.add(new CalculationLine(
                        "UNPAID_LEAVE", "UNPAID_LEAVE", unpaidAmount));
            }
        }

        deductions.sort(Comparator.comparing(CalculationLine::code)
                .thenComparing(CalculationLine::category));

        BigDecimal deductionTotal = money(deductions.stream()
                .map(CalculationLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        if (deductionTotal.compareTo(gross) > 0) {
            throw new IllegalStateException(
                    "HRM_PAYROLL_NEGATIVE_NET_REJECTED: deductions exceed gross");
        }

        BigDecimal net = money(gross.subtract(deductionTotal));
        return new CalculationResult(
                base,
                gross,
                deductionTotal,
                net,
                earnings,
                deductions);
    }

    private List<CalculationLine> calculateAllowances(
            List<PayrollAuthoritativeInputPort.CompensationComponentInput> components,
            Set<String> enabledCodes,
            BigDecimal base) {
        List<PayrollAuthoritativeInputPort.CompensationComponentInput> selected = components.stream()
                .filter(c -> "ALLOWANCE".equals(c.type()))
                .filter(c -> enabledCodes.contains(c.code()))
                .sorted(Comparator
                        .comparing(PayrollAuthoritativeInputPort.CompensationComponentInput::code)
                        .thenComparing(c -> c.componentId().toString()))
                .toList();

        Set<String> seen = new HashSet<>();
        List<CalculationLine> lines = new ArrayList<>();
        for (var component : selected) {
            if (!seen.add(component.code())) {
                throw new IllegalStateException(
                        "HRM_PAYROLL_ALLOWANCE_AMBIGUOUS: duplicate enabled allowance code "
                                + component.code());
            }
            BigDecimal amount;
            if (component.amount() != null && component.percentage() == null) {
                amount = money(component.amount());
            } else if (component.amount() == null && component.percentage() != null) {
                amount = percentageOf(base, component.percentage());
            } else {
                throw new IllegalStateException(
                        "HRM_PAYROLL_ALLOWANCE_INVALID: " + component.code());
            }
            lines.add(new CalculationLine(component.code(), "ALLOWANCE", amount));
        }
        return lines;
    }

    private List<CalculationLine> calculateConfiguredDeductions(
            List<ConfiguredDeduction> configured,
            BigDecimal base,
            BigDecimal gross) {
        List<ConfiguredDeduction> ordered = configured.stream()
                .sorted(Comparator.comparing(ConfiguredDeduction::code))
                .toList();

        Set<String> seen = new HashSet<>();
        List<CalculationLine> lines = new ArrayList<>();
        for (ConfiguredDeduction deduction : ordered) {
            if (!seen.add(deduction.code())) {
                throw new IllegalStateException(
                        "HRM_PAYROLL_DEDUCTION_AMBIGUOUS: duplicate deduction code "
                                + deduction.code());
            }
            if ("UNPAID_LEAVE".equals(deduction.code())) {
                throw new IllegalStateException(
                        "HRM_PAYROLL_DEDUCTION_CODE_RESERVED: UNPAID_LEAVE");
            }

            BigDecimal amount;
            if (deduction.amount() != null) {
                amount = money(deduction.amount());
            } else {
                BigDecimal basis = deduction.percentageBasis() == PercentageBasis.BASE
                        ? base : gross;
                amount = percentageOf(basis, deduction.percentage());
            }
            lines.add(new CalculationLine(deduction.code(), "NON_STATUTORY_DEDUCTION", amount));
        }
        return lines;
    }

    private BigDecimal percentageOf(BigDecimal basis, BigDecimal percentage) {
        if (percentage == null || percentage.signum() <= 0) {
            throw new IllegalStateException("HRM_PAYROLL_PERCENTAGE_INVALID");
        }
        return money(basis.multiply(percentage).divide(ONE_HUNDRED, 12, MONEY_ROUNDING));
    }

    private BigDecimal money(BigDecimal value) {
        Objects.requireNonNull(value, "money value");
        if (value.signum() < 0) {
            throw new IllegalStateException("HRM_PAYROLL_NEGATIVE_AMOUNT_REJECTED");
        }
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    public enum PercentageBasis {
        BASE,
        GROSS
    }

    public record CalculationPolicy(
            Set<String> enabledAllowanceCodes,
            List<ConfiguredDeduction> configuredDeductions,
            UnpaidLeavePolicy unpaidLeavePolicy) {
        public CalculationPolicy {
            enabledAllowanceCodes = enabledAllowanceCodes == null
                    ? Set.of() : Set.copyOf(enabledAllowanceCodes);
            configuredDeductions = configuredDeductions == null
                    ? List.of() : List.copyOf(configuredDeductions);
            unpaidLeavePolicy = Objects.requireNonNull(unpaidLeavePolicy, "unpaidLeavePolicy");
        }

        public static CalculationPolicy noAdjustments() {
            return new CalculationPolicy(
                    Set.of(),
                    List.of(),
                    UnpaidLeavePolicy.disabled());
        }
    }

    public record ConfiguredDeduction(
            String code,
            BigDecimal amount,
            BigDecimal percentage,
            PercentageBasis percentageBasis) {
        public ConfiguredDeduction {
            Objects.requireNonNull(code, "code");
            if (code.isBlank()) {
                throw new IllegalArgumentException("HRM_PAYROLL_DEDUCTION_CODE_REQUIRED");
            }
            boolean hasAmount = amount != null;
            boolean hasPercentage = percentage != null;
            if (hasAmount == hasPercentage) {
                throw new IllegalArgumentException(
                        "HRM_PAYROLL_DEDUCTION_INVALID: exactly one of amount/percentage is required");
            }
            if (hasAmount) {
                if (amount.signum() <= 0 || percentageBasis != null) {
                    throw new IllegalArgumentException(
                            "HRM_PAYROLL_DEDUCTION_INVALID: fixed deduction must be positive and have no percentage basis");
                }
            } else {
                if (percentage.signum() <= 0 || percentageBasis == null) {
                    throw new IllegalArgumentException(
                            "HRM_PAYROLL_DEDUCTION_INVALID: percentage deduction requires positive percentage and basis");
                }
            }
        }

        public static ConfiguredDeduction fixed(String code, BigDecimal amount) {
            return new ConfiguredDeduction(code, amount, null, null);
        }

        public static ConfiguredDeduction percentage(
                String code, BigDecimal percentage, PercentageBasis basis) {
            return new ConfiguredDeduction(code, null, percentage, basis);
        }
    }

    public record UnpaidLeavePolicy(boolean enabled, BigDecimal dailyRate) {
        public UnpaidLeavePolicy {
            if (enabled) {
                if (dailyRate == null || dailyRate.signum() <= 0) {
                    throw new IllegalArgumentException(
                            "HRM_PAYROLL_UNPAID_LEAVE_POLICY_INVALID: enabled policy requires positive dailyRate");
                }
            } else if (dailyRate != null) {
                throw new IllegalArgumentException(
                        "HRM_PAYROLL_UNPAID_LEAVE_POLICY_INVALID: disabled policy must not carry dailyRate");
            }
        }

        public static UnpaidLeavePolicy disabled() {
            return new UnpaidLeavePolicy(false, null);
        }
    }

    public record CalculationLine(String code, String category, BigDecimal amount) {
        public CalculationLine {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(amount, "amount");
        }
    }

    public record CalculationResult(
            BigDecimal baseAmount,
            BigDecimal grossAmount,
            BigDecimal deductionTotal,
            BigDecimal netAmount,
            List<CalculationLine> earnings,
            List<CalculationLine> deductions) {
        public CalculationResult {
            earnings = List.copyOf(earnings);
            deductions = List.copyOf(deductions);
        }
    }
}
