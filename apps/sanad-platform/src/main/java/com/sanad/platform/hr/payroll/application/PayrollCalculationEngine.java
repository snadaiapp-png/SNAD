package com.sanad.platform.hr.payroll.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * G4-T4 country-neutral, deterministic payroll calculation engine.
 *
 * <p>No statutory inference, pay-frequency conversion, attendance
 * recalculation, tax, GOSI, WPS, EOS or overtime premium logic exists here.
 * Non-statutory deductions and unpaid-leave rates must be supplied explicitly
 * by an approved tenant policy.</p>
 */
public final class PayrollCalculationEngine {

    public static final int MONEY_SCALE = 4;
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final Set<String> RESERVED_STATUTORY_CODES = Set.of(
            "GOSI", "SOCIAL_INSURANCE", "WPS", "SARIE", "ZAKAT", "TAX",
            "EOS", "END_OF_SERVICE", "OVERTIME");

    public CalculationResult calculate(
            PayrollAuthoritativeInputPort.PayrollInputSnapshot snapshot,
            CalculationPolicy policy) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(policy, "policy");

        if (!snapshot.payFrequency().equals(policy.expectedPayFrequency())) {
            throw new IllegalStateException(
                    "HRM_PAYROLL_PAY_FREQUENCY_MISMATCH: snapshot="
                            + snapshot.payFrequency() + ", policy=" + policy.expectedPayFrequency());
        }

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
                snapshot.compensationComponents(), policy.configuredAllowances());

        BigDecimal allowanceTotal = earnings.stream()
                .map(CalculationLine::amount)
                .reduce(zero(), BigDecimal::add);
        BigDecimal gross = money(base.add(allowanceTotal));

        List<CalculationLine> deductions = calculateConfiguredDeductions(
                policy.configuredDeductions(), base, gross);

        if (policy.unpaidLeavePolicy().enabled()) {
            var period = snapshot.timesheet();
            BigDecimal unpaidDays = zero();
            for (var leave : snapshot.approvedLeave()) {
                if (leave.paid()) {
                    continue;
                }
                if (leave.daysCount() == null || leave.daysCount().signum() < 0) {
                    throw new IllegalStateException("HRM_PAYROLL_UNPAID_LEAVE_INPUT_INVALID");
                }
                if (leave.startDate().isBefore(period.periodStart())
                        || leave.endDate().isAfter(period.periodEnd())) {
                    throw new IllegalStateException(
                            "HRM_PAYROLL_UNPAID_LEAVE_PERIOD_AMBIGUOUS: approved leave crosses payroll period");
                }
                unpaidDays = unpaidDays.add(leave.daysCount());
            }
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
                money(allowanceTotal),
                gross,
                money(deductions.stream()
                        .filter(d -> "NON_STATUTORY_DEDUCTION".equals(d.category()))
                        .map(CalculationLine::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)),
                money(deductions.stream()
                        .filter(d -> "UNPAID_LEAVE".equals(d.category()))
                        .map(CalculationLine::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)),
                deductionTotal,
                net,
                earnings,
                deductions);
    }

    private List<CalculationLine> calculateAllowances(
            List<PayrollAuthoritativeInputPort.CompensationComponentInput> components,
            List<ConfiguredAllowance> configured) {
        List<ConfiguredAllowance> orderedRules = configured.stream()
                .sorted(Comparator.comparing(ConfiguredAllowance::code))
                .toList();
        Set<String> seenRules = new HashSet<>();
        List<CalculationLine> lines = new ArrayList<>();
        for (ConfiguredAllowance rule : orderedRules) {
            if (!seenRules.add(rule.code())) {
                throw new IllegalStateException(
                        "HRM_PAYROLL_ALLOWANCE_POLICY_AMBIGUOUS: duplicate allowance rule " + rule.code());
            }
            List<PayrollAuthoritativeInputPort.CompensationComponentInput> matches = components.stream()
                    .filter(c -> "ALLOWANCE".equals(c.type()))
                    .filter(c -> rule.code().equals(c.code()))
                    .toList();
            if (matches.size() != 1) {
                throw new IllegalStateException(
                        "HRM_PAYROLL_ALLOWANCE_AMBIGUOUS: expected exactly one allowance for "
                                + rule.code() + ", found " + matches.size());
            }
            var component = matches.get(0);
            if (component.amount() == null || component.percentage() != null) {
                throw new IllegalStateException(
                        "HRM_PAYROLL_ALLOWANCE_BASIS_AMBIGUOUS: percentage allowance basis is not authoritative "
                                + rule.code());
            }
            lines.add(new CalculationLine(component.code(), "ALLOWANCE", money(component.amount())));
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

    private static boolean isReservedStatutoryCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return RESERVED_STATUTORY_CODES.stream()
                .anyMatch(token -> normalized.equals(token)
                        || normalized.startsWith(token + "_")
                        || normalized.endsWith("_" + token));
    }

    public enum PercentageBasis {
        BASE,
        GROSS
    }

    public record CalculationPolicy(
            String expectedPayFrequency,
            List<ConfiguredAllowance> configuredAllowances,
            List<ConfiguredDeduction> configuredDeductions,
            UnpaidLeavePolicy unpaidLeavePolicy) {
        public CalculationPolicy {
            Objects.requireNonNull(expectedPayFrequency, "expectedPayFrequency");
            if (expectedPayFrequency.isBlank()) {
                throw new IllegalArgumentException("HRM_PAYROLL_PAY_FREQUENCY_REQUIRED");
            }
            configuredAllowances = configuredAllowances == null
                    ? List.of() : List.copyOf(configuredAllowances);
            configuredDeductions = configuredDeductions == null
                    ? List.of() : List.copyOf(configuredDeductions);
            unpaidLeavePolicy = Objects.requireNonNull(unpaidLeavePolicy, "unpaidLeavePolicy");
        }

        public static CalculationPolicy noAdjustments(String expectedPayFrequency) {
            return new CalculationPolicy(
                    expectedPayFrequency,
                    List.of(),
                    List.of(),
                    UnpaidLeavePolicy.disabled());
        }
    }

    public record ConfiguredAllowance(String code) {
        public ConfiguredAllowance {
            Objects.requireNonNull(code, "code");
            if (code.isBlank()) {
                throw new IllegalArgumentException("HRM_PAYROLL_ALLOWANCE_CODE_REQUIRED");
            }
        }

        public static ConfiguredAllowance fixed(String code) {
            return new ConfiguredAllowance(code);
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
            if (isReservedStatutoryCode(code)) {
                throw new IllegalArgumentException(
                        "HRM_PAYROLL_STATUTORY_RULE_NOT_AUTHORIZED: " + code);
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
            } else if (percentage.signum() <= 0 || percentageBasis == null) {
                throw new IllegalArgumentException(
                        "HRM_PAYROLL_DEDUCTION_INVALID: percentage deduction requires positive percentage and basis");
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
            BigDecimal allowanceTotal,
            BigDecimal grossAmount,
            BigDecimal nonStatutoryDeductionTotal,
            BigDecimal unpaidLeaveDeduction,
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
