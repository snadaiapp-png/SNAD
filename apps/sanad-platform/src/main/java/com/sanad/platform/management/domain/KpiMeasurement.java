package com.sanad.platform.management.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * KPI Measurement — an immutable historical actual value for a KPI Definition.
 *
 * <p>Measurements are append-only: once created, they are never updated or deleted.
 * This supports trend analysis, audit trails, and reproducible dashboards.
 *
 * <p>Status is computed at creation time from the measured value vs the active
 * KPI Target (if any).
 */
public record KpiMeasurement(
        UUID id,
        UUID tenantId,
        UUID kpiDefinitionId,
        UUID kpiTargetId,
        LocalDate period,
        BigDecimal measuredValue,
        BigDecimal previousValue,
        BigDecimal variancePct,
        Status status,
        String evidence,
        UUID measuredBy,
        Instant measuredAt
) {
    public enum Status {
        ON_TRACK, AT_RISK, OFF_TRACK, ACHIEVED, NOT_STARTED, NO_DATA
    }

    /**
     * Compute the status of a measurement given the measured value, the active target,
     * and the KPI direction.
     *
     * @param measured the measured value
     * @param target the target value (may be null if no target exists)
     * @param minimum the minimum acceptable value (may be null)
     * @param stretch the stretch goal value (may be null)
     * @param direction UP (higher is better) or DOWN (lower is better)
     * @return the computed status, or NO_DATA if target is null
     */
    public static Status computeStatus(
            BigDecimal measured, BigDecimal target, BigDecimal minimum,
            BigDecimal stretch, KeyResult.Direction direction) {
        if (target == null) {
            return Status.NO_DATA;
        }
        if (measured == null) {
            return Status.NOT_STARTED;
        }
        boolean achieved = direction == KeyResult.Direction.UP
                ? measured.compareTo(target) >= 0
                : measured.compareTo(target) <= 0;
        if (achieved) {
            return Status.ACHIEVED;
        }
        if (stretch != null) {
            boolean stretchAchieved = direction == KeyResult.Direction.UP
                    ? measured.compareTo(stretch) >= 0
                    : measured.compareTo(stretch) <= 0;
            if (stretchAchieved) {
                return Status.ACHIEVED;
            }
        }
        if (minimum != null) {
            boolean belowMinimum = direction == KeyResult.Direction.UP
                    ? measured.compareTo(minimum) < 0
                    : measured.compareTo(minimum) > 0;
            if (belowMinimum) {
                return Status.OFF_TRACK;
            }
        }
        // Between minimum and target → check how close we are
        var progress = measured.divide(target, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        if (direction == KeyResult.Direction.DOWN) {
            // For DOWN, if measured > target, progress > 100 means BAD.
            // Invert: progress = (target / measured) * 100
            if (measured.compareTo(BigDecimal.ZERO) > 0) {
                progress = target.divide(measured, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
        }
        if (progress.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return Status.ON_TRACK;
        }
        if (progress.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return Status.AT_RISK;
        }
        return Status.OFF_TRACK;
    }

    /**
     * Compute variance percentage: (measured - target) / target * 100.
     * Positive variance = better than target (for UP direction).
     */
    public static BigDecimal computeVariancePct(BigDecimal measured, BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) == 0 || measured == null) {
            return null;
        }
        return measured.subtract(target)
                .divide(target, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
