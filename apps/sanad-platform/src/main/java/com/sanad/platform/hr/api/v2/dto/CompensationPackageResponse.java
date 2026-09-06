package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.compensation.domain.CompensationComponent;
import com.sanad.platform.hr.compensation.domain.CompensationPackage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 5 — Compensation package view. Amounts are present only
 * when the response is produced by an audited restricted read; the list
 * endpoint returns the amount-free projection.
 */
public record CompensationPackageResponse(
        UUID packageId,
        UUID employmentId,
        String currencyCode,
        String payFrequency,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        Integer version,
        List<Component> components
) {

    public record Component(String componentCode, String componentType, java.math.BigDecimal amount,
                            String percentage) {
    }

    public static CompensationPackageResponse withoutAmounts(CompensationPackage pkg) {
        return new CompensationPackageResponse(pkg.id(), pkg.employmentId(), pkg.currencyCode(),
                pkg.payFrequency(), pkg.effectiveFrom(), pkg.effectiveTo(), pkg.status(),
                (int) pkg.version(), List.of());
    }

    public static CompensationPackageResponse withAmounts(CompensationPackage pkg) {
        List<Component> components = pkg.components() == null ? List.of() : pkg.components().stream()
                .map(c -> new Component(c.code(),
                        c.componentType() == null ? null : c.componentType().name(),
                        c.amount(),
                        c.percentage() == null ? null : c.percentage().toPlainString()))
                .toList();
        return new CompensationPackageResponse(pkg.id(), pkg.employmentId(), pkg.currencyCode(),
                pkg.payFrequency(), pkg.effectiveFrom(), pkg.effectiveTo(), pkg.status(),
                (int) pkg.version(), components);
    }
}
