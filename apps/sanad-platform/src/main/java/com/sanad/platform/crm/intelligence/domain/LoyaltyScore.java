package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.List;

/**
 * Customer loyalty score (0.0 – 100.0).
 */
public record LoyaltyScore(
        double value,
        String band,
        Instant calculatedAt,
        List<ScoreComponent> components
) {
    public static final String BAND_NEW = "NEW";
    public static final String BAND_GROWING = "GROWING";
    public static final String BAND_LOYAL = "LOYAL";
    public static final String BAND_CHAMPION = "CHAMPION";

    public LoyaltyScore {
        if (value < 0.0 || value > 100.0)
            throw new IllegalArgumentException("Loyalty score must be 0.0–100.0");
        if (calculatedAt == null)
            throw new IllegalArgumentException("calculatedAt is required");
    }

    public static String bandFor(double value) {
        if (value < 25.0) return BAND_NEW;
        if (value < 50.0) return BAND_GROWING;
        if (value < 80.0) return BAND_LOYAL;
        return BAND_CHAMPION;
    }
}
