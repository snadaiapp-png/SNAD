package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;

/**
 * Predicted customer lifetime value.
 */
public record CustomerLifetimeValue(
        double predictedValue,
        double historicalValue,
        String tier,
        Instant calculatedAt,
        double confidence
) {
    public static final String TIER_HIGH_VALUE = "HIGH_VALUE";
    public static final String TIER_MID_VALUE = "MID_VALUE";
    public static final String TIER_LOW_VALUE = "LOW_VALUE";

    public CustomerLifetimeValue {
        if (predictedValue < 0.0)
            throw new IllegalArgumentException("predictedValue must be >= 0");
        if (historicalValue < 0.0)
            throw new IllegalArgumentException("historicalValue must be >= 0");
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be 0.0–1.0");
        if (calculatedAt == null)
            throw new IllegalArgumentException("calculatedAt is required");
    }

    public static String tierFor(double predictedValue) {
        if (predictedValue >= 100000) return TIER_HIGH_VALUE;
        if (predictedValue >= 25000) return TIER_MID_VALUE;
        return TIER_LOW_VALUE;
    }
}
