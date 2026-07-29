package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.List;

/**
 * Customer health score (0.0 – 100.0) with categorical band and weighted components.
 */
public record HealthScore(
        double value,
        String band,
        Instant calculatedAt,
        List<ScoreComponent> components
) {
    public static final String BAND_CRITICAL = "CRITICAL";
    public static final String BAND_AT_RISK = "AT_RISK";
    public static final String BAND_HEALTHY = "HEALTHY";
    public static final String BAND_THRIVING = "THRIVING";

    public HealthScore {
        if (value < 0.0 || value > 100.0)
            throw new IllegalArgumentException("Health score must be 0.0–100.0");
        if (band == null || band.isBlank())
            throw new IllegalArgumentException("band is required");
        if (calculatedAt == null)
            throw new IllegalArgumentException("calculatedAt is required");
    }

    /**
     * Derive band from numeric value using standard thresholds.
     */
    public static String bandFor(double value) {
        if (value < 25.0) return BAND_CRITICAL;
        if (value < 50.0) return BAND_AT_RISK;
        if (value < 75.0) return BAND_HEALTHY;
        return BAND_THRIVING;
    }
}
