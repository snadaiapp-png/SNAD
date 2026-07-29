package com.sanad.platform.crm.intelligence.domain;

/**
 * Container for all five customer intelligence scores.
 */
public record CustomerScores(
        HealthScore healthScore,
        CustomerLifetimeValue lifetimeValue,
        EngagementScore engagementScore,
        RiskScore riskScore,
        LoyaltyScore loyaltyScore
) {}
