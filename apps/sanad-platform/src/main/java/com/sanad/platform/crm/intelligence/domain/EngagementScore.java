package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.List;

/**
 * Customer engagement score (0.0 – 100.0).
 */
public record EngagementScore(
        double value,
        String band,
        Instant calculatedAt,
        List<ScoreComponent> components
) {
    public static final String BAND_DORMANT = "DORMANT";
    public static final String BAND_LOW = "LOW";
    public static final String BAND_MODERATE = "MODERATE";
    public static final String BAND_HIGH = "HIGH";

    public EngagementScore {
        if (value < 0.0 || value > 100.0)
            throw new IllegalArgumentException("Engagement score must be 0.0–100.0");
        if (calculatedAt == null)
            throw new IllegalArgumentException("calculatedAt is required");
    }

    public static String bandFor(double value) {
        if (value < 20.0) return BAND_DORMANT;
        if (value < 40.0) return BAND_LOW;
        if (value < 70.0) return BAND_MODERATE;
        return BAND_HIGH;
    }
}
