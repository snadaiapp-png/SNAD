package com.sanad.platform.crm.intelligence.domain;

/**
 * A risk factor contributing to the risk score.
 */
public record RiskFactor(
        String name,
        String severity,
        double contribution,
        String description
) {
    public static final String SEVERITY_LOW = "LOW";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_HIGH = "HIGH";

    public RiskFactor {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("name is required");
        if (contribution < 0.0 || contribution > 100.0)
            throw new IllegalArgumentException("contribution must be 0.0–100.0");
    }
}
