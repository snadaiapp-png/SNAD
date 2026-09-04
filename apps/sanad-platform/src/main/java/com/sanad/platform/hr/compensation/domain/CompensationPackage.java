package com.sanad.platform.hr.compensation.domain;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Effective-dated compensation package (WS6 Task 3). Historical packages are
 * immutable: a revision creates a NEW package (predecessor chain) and closes
 * the old window — never an in-place overwrite.
 */
public record CompensationPackage(
        UUID id,
        UUID tenantId,
        UUID employmentId,
        String currencyCode,
        String payFrequency,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        UUID predecessorPackageId,
        List<CompensationComponent> components,
        long version,
        Instant createdAt) {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String STATUS_ENDED = "ENDED";

    public CompensationPackage {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(employmentId, "employmentId");
        Objects.requireNonNull(currencyCode, "currencyCode");
        if (currencyCode.length() != 3) {
            throw new IllegalArgumentException("HRM_COMPENSATION_INVALID: currency_code must be ISO 4217 (3 letters)");
        }
        Objects.requireNonNull(payFrequency, "payFrequency");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(status, "status");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("HRM_COMPENSATION_INVALID: effective_to before effective_from");
        }
        components = components == null ? List.of() : List.copyOf(components);
        long baseSalaryCount = components.stream()
                .filter(c -> c.componentType() == CompensationComponentType.BASE_SALARY)
                .count();
        if (baseSalaryCount > 1) {
            throw new IllegalArgumentException("HRM_COMPENSATION_INVALID: at most one BASE_SALARY component per package");
        }
    }
}
