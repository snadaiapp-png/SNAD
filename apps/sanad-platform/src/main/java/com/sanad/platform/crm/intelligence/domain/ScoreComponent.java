package com.sanad.platform.crm.intelligence.domain;

/**
 * A weighted component contributing to a customer score.
 *
 * @param name           component identifier (e.g. "response_time")
 * @param weight         contribution weight (0.0 – 1.0)
 * @param rawValue       the component's raw score
 * @param weightedValue  rawValue * weight
 */
public record ScoreComponent(
        String name,
        double weight,
        double rawValue,
        double weightedValue
) {
    public ScoreComponent {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("name is required");
        if (weight < 0.0 || weight > 1.0)
            throw new IllegalArgumentException("weight must be 0.0–1.0");
    }

    /**
     * Factory that computes the weighted value from raw and weight.
     */
    public static ScoreComponent of(String name, double weight, double rawValue) {
        return new ScoreComponent(name, weight, rawValue, rawValue * weight);
    }
}
