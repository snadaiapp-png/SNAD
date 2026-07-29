package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.List;

/**
 * Customer risk score (0.0 – 100.0, higher = more risk).
 */
public record RiskScore(
        double value,
        String band,
        Instant calculatedAt,
        List<RiskFactor> riskFactors
) {
    public static final String BAND_LOW_RISK = "LOW_RISK";
    public static final String BAND_MEDIUM_RISK = "MEDIUM_RISK";
    public static final String BAND_HIGH_RISK = "HIGH_RISK";

    public RiskScore {
        if (value < 0.0 || value > 100.0)
            throw new IllegalArgumentException("Risk score must be 0.0–100.0");
        if (calculatedAt == null)
            throw new IllegalArgumentException("calculatedAt is required");
    }

    public static String bandFor(double value) {
        if (value < 30.0) return BAND_LOW_RISK;
        if (value < 60.0) return BAND_MEDIUM_RISK;
        return BAND_HIGH_RISK;
    }
}
