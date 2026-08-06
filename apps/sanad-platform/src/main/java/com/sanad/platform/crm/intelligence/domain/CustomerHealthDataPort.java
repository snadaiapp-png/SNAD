package com.sanad.platform.crm.intelligence.domain;

import java.util.UUID;

/**
 * Domain port for querying real customer health-indicator data from the
 * system of record.
 *
 * <p>Introduced to eliminate the hardcoded fake values that were previously
 * passed to {@code calculateHealthScore(...)} in {@code refreshAllScores()}.
 * Each implementation composes across the appropriate bounded-context
 * repositories (Activity, Opportunity, Account) to return live metrics.</p>
 */
public interface CustomerHealthDataPort {

    /**
     * Gather the five health indicators needed for scoring a given account.
     *
     * @param tenantId  the scoping tenant
     * @param accountId the customer account being scored
     * @return a populated {@link HealthIndicators} record (never {@code null})
     */
    HealthIndicators getHealthIndicators(UUID tenantId, UUID accountId);

    /**
     * Value object holding the raw indicators consumed by the scoring engine.
     *
     * @param daysSinceLastActivity  number of days since the most recent
     *                               customer-facing activity
     * @param openOpportunities      count of open opportunities for this account
     * @param totalPipeline          sum of expected amounts for open opportunities
     * @param meetingFreq30d         number of meetings held in the last 30 days
     * @param responseTimeAvgHours   average response time (hours) across
     *                               completed activities
     */
    record HealthIndicators(
            int daysSinceLastActivity,
            int openOpportunities,
            double totalPipeline,
            int meetingFreq30d,
            double responseTimeAvgHours
    ) {}
}
