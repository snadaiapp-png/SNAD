package com.sanad.platform.management.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Key Result — the "KR" in OKR.
 *
 * <p>A measurable outcome that contributes to a {@link StrategicObjective}.
 * Key Results have a baseline, a target, and a current value. The direction
 * (UP or DOWN) determines whether higher or lower is better.
 *
 * <p>Status is derived from the current value vs target:
 * <ul>
 *   <li>{@code ON_TRACK} — within threshold of target</li>
 *   <li>{@code AT_RISK} — moving away from target</li>
 *   <li>{@code OFF_TRACK} — significantly behind</li>
 *   <li>{@code ACHIEVED} — target met or exceeded</li>
 *   <li>{@code NOT_STARTED} — current == baseline</li>
 *   <li>{@code MISSED} — period ended without achievement</li>
 * </ul>
 */
public record KeyResult(
        UUID id,
        UUID tenantId,
        UUID objectiveId,
        String title,
        String description,
        MetricUnit metricUnit,
        BigDecimal baselineValue,
        BigDecimal targetValue,
        BigDecimal currentValue,
        Direction direction,
        Status status,
        int weightPct,
        UUID ownerUserId,
        LocalDate dueDate,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum MetricUnit {
        COUNT, PERCENTAGE, CURRENCY, RATIO, DURATION
    }

    public enum Direction {
        UP,   // higher is better
        DOWN  // lower is better
    }

    public enum Status {
        NOT_STARTED, ON_TRACK, AT_RISK, OFF_TRACK, ACHIEVED, MISSED
    }

    public static KeyResult create(
            UUID tenantId, UUID objectiveId, String title, String description,
            MetricUnit unit, BigDecimal baseline, BigDecimal target, Direction direction,
            int weightPct, UUID ownerUserId, LocalDate dueDate) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (weightPct < 0 || weightPct > 100) {
            throw new IllegalArgumentException("weightPct must be 0-100");
        }
        var now = Instant.now();
        var baselineValue = baseline != null ? baseline : BigDecimal.ZERO;
        return new KeyResult(
                UUID.randomUUID(), tenantId, objectiveId, title, description, unit,
                baselineValue, target, baselineValue, direction,
                Status.NOT_STARTED, weightPct, ownerUserId, dueDate,
                0, now, now
        );
    }

    /** Record a new measurement and recompute status. */
    public KeyResult recordMeasurement(BigDecimal newValue) {
        if (newValue == null) {
            throw new IllegalArgumentException("newValue must not be null");
        }
        var newStatus = computeStatus(newValue);
        return new KeyResult(
                id, tenantId, objectiveId, title, description, metricUnit,
                baselineValue, targetValue, newValue, direction, newStatus,
                weightPct, ownerUserId, dueDate, version + 1, createdAt, Instant.now()
        );
    }

    /**
     * Compute the status based on the current value vs target.
     *
     * <p>Algorithm:
     * <ul>
     *   <li>If currentValue == baselineValue → NOT_STARTED</li>
     *   <li>If direction UP and currentValue >= target → ACHIEVED</li>
     *   <li>If direction DOWN and currentValue <= target → ACHIEVED</li>
     *   <li>Otherwise compute progress percentage and bucket:</li>
     *   <li>  ≥ 80% → ON_TRACK</li>
     *   <li>  ≥ 50% → AT_RISK</li>
     *   <li>  &lt; 50% → OFF_TRACK</li>
     * </ul>
     */
    public Status computeStatus(BigDecimal value) {
        if (value.compareTo(baselineValue) == 0) {
            return Status.NOT_STARTED;
        }
        boolean achieved = direction == Direction.UP
                ? value.compareTo(targetValue) >= 0
                : value.compareTo(targetValue) <= 0;
        if (achieved) {
            return Status.ACHIEVED;
        }
        // progress = (current - baseline) / (target - baseline)
        var totalRange = targetValue.subtract(baselineValue);
        if (totalRange.compareTo(BigDecimal.ZERO) == 0) {
            // baseline == target: any change is "achieved" (handled above)
            return Status.ACHIEVED;
        }
        var progress = value.subtract(baselineValue)
                .divide(totalRange, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        if (direction == Direction.DOWN) {
            // For DOWN, progress is inverted (lower value = more progress)
            progress = BigDecimal.valueOf(100).subtract(progress);
        }
        if (progress.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return Status.ON_TRACK;
        }
        if (progress.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return Status.AT_RISK;
        }
        return Status.OFF_TRACK;
    }

    /** Mark as MISSED — only valid when due date has passed and not achieved. */
    public KeyResult markMissed() {
        if (status == Status.ACHIEVED) {
            throw new IllegalStateException("Cannot mark ACHIEVED key result as MISSED");
        }
        return new KeyResult(
                id, tenantId, objectiveId, title, description, metricUnit,
                baselineValue, targetValue, currentValue, direction, Status.MISSED,
                weightPct, ownerUserId, dueDate, version + 1, createdAt, Instant.now()
        );
    }
}
