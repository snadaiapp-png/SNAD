package com.sanad.platform.hr.compensation.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Compensation component (WS6 Task 3). Structural rules only: exactly one of
 * amount / percentage, positive when present, currency belongs to the
 * PACKAGE (never the component). No statutory treatment here.
 */
public record CompensationComponent(
        UUID id,
        UUID tenantId,
        UUID packageId,
        CompensationComponentType componentType,
        String code,
        BigDecimal amount,
        BigDecimal percentage) {

    public CompensationComponent {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(componentType, "componentType");
        Objects.requireNonNull(code, "code");
        if (amount == null && percentage == null) {
            throw new IllegalArgumentException("HRM_COMPENSATION_INVALID: component requires amount or percentage");
        }
        if (amount != null && percentage != null) {
            throw new IllegalArgumentException("HRM_COMPENSATION_INVALID: component carries both amount and percentage");
        }
        if (amount != null && amount.signum() <= 0) {
            throw new IllegalArgumentException("HRM_COMPENSATION_INVALID: amount must be positive");
        }
        if (percentage != null && percentage.signum() <= 0) {
            throw new IllegalArgumentException("HRM_COMPENSATION_INVALID: percentage must be positive");
        }
    }
}
