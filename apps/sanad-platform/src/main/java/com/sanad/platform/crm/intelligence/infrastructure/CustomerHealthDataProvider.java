package com.sanad.platform.crm.intelligence.infrastructure;

import com.sanad.platform.crm.activity.domain.ActivityRepository;
import com.sanad.platform.crm.activity.domain.ActivityRepository.ActivityRecord;
import com.sanad.platform.crm.intelligence.domain.CustomerHealthDataPort;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository.OpportunityRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Composes real customer health data from the Activity and Opportunity
 * bounded contexts.
 *
 * <p>Previously {@code refreshAllScores()} used hardcoded placeholder
 * values for every indicator. This provider replaces those with live
 * queries so that scores reflect actual customer behaviour.</p>
 */
@Component
public class CustomerHealthDataProvider implements CustomerHealthDataPort {

    private static final Logger log = LoggerFactory.getLogger(CustomerHealthDataProvider.class);

    /** Activity types that count as "meetings" for meeting-frequency scoring. */
    private static final Set<String> MEETING_TYPES = Set.of("MEETING", "CALL", "VIDEO_CALL");

    /** Closed/lost statuses excluded from open-opportunity counts. */
    private static final Set<String> CLOSED_STATUSES = Set.of("CLOSED_WON", "CLOSED_LOST", "LOST");

    /** Default response time (hours) when no completed activities exist. */
    private static final double DEFAULT_RESPONSE_TIME_HOURS = 24.0;

    private final ActivityRepository activityRepository;
    private final OpportunityRepository opportunityRepository;

    public CustomerHealthDataProvider(ActivityRepository activityRepository,
                                       OpportunityRepository opportunityRepository) {
        this.activityRepository = activityRepository;
        this.opportunityRepository = opportunityRepository;
    }

    @Override
    public HealthIndicators getHealthIndicators(UUID tenantId, UUID accountId) {
        List<ActivityRecord> recentActivities = fetchRecentActivities(tenantId, accountId);
        List<OpportunityRecord> opportunities = fetchOpenOpportunities(tenantId, accountId);

        int daysSinceLastActivity = computeDaysSinceLastActivity(recentActivities);
        int openOpportunities = countOpen(opportunities);
        double totalPipeline = sumPipeline(opportunities);
        int meetingFreq30d = countMeetingsLast30d(recentActivities);
        double responseTimeAvgHours = computeAvgResponseTimeHours(recentActivities);

        log.debug("Health indicators for account {}: daysSinceLastActivity={}, openOpps={}, "
                        + "pipeline={}, meetings30d={}, responseTime={}h",
                accountId, daysSinceLastActivity, openOpportunities,
                totalPipeline, meetingFreq30d, responseTimeAvgHours);

        return new HealthIndicators(
                daysSinceLastActivity,
                openOpportunities,
                totalPipeline,
                meetingFreq30d,
                responseTimeAvgHours);
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private List<ActivityRecord> fetchRecentActivities(UUID tenantId, UUID accountId) {
        try {
            // Fetch the latest 100 activities related to this account.
            return activityRepository.findAll(tenantId, 100, "ACCOUNT", accountId, null);
        } catch (Exception e) {
            log.warn("Failed to fetch activities for account {}: {}", accountId, e.getMessage());
            return List.of();
        }
    }

    private List<OpportunityRecord> fetchOpenOpportunities(UUID tenantId, UUID accountId) {
        try {
            // Fetch all opportunities for this account (limit 500 as a safety cap).
            return opportunityRepository.findAll(tenantId, 500, accountId);
        } catch (Exception e) {
            log.warn("Failed to fetch opportunities for account {}: {}", accountId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Days since the most recent activity {@code createdAt} timestamp.
     * Returns 365 if no activities exist (conservative "cold" default).
     */
    private static int computeDaysSinceLastActivity(List<ActivityRecord> activities) {
        return activities.stream()
                .map(ActivityRecord::createdAt)
                .filter(t -> t != null)
                .max(Instant::compareTo)
                .map(latest -> (int) Duration.between(latest, Instant.now()).toDays())
                .map(days -> Math.max(0, days))
                .orElse(365);
    }

    /** Count records whose status is not in the closed/lost set. */
    private static int countOpen(List<OpportunityRecord> opportunities) {
        return (int) opportunities.stream()
                .filter(o -> o.status() == null || !CLOSED_STATUSES.contains(o.status().toUpperCase()))
                .count();
    }

    /** Sum {@code amount} for opportunities not in a closed/lost status. */
    private static double sumPipeline(List<OpportunityRecord> opportunities) {
        return opportunities.stream()
                .filter(o -> o.status() == null || !CLOSED_STATUSES.contains(o.status().toUpperCase()))
                .map(OpportunityRecord::amount)
                .filter(a -> a != null)
                .map(BigDecimal::doubleValue)
                .reduce(0.0, Double::sum);
    }

    /** Count activities with a meeting-like type in the last 30 days. */
    private static int countMeetingsLast30d(List<ActivityRecord> activities) {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        return (int) activities.stream()
                .filter(a -> a.activityType() != null
                        && MEETING_TYPES.contains(a.activityType().toUpperCase()))
                .filter(a -> a.createdAt() != null && a.createdAt().isAfter(cutoff))
                .count();
    }

    /**
     * Average response time in hours computed from completed activities.
     * Falls back to {@link #DEFAULT_RESPONSE_TIME_HOURS} when no completed
     * activities exist.
     */
    private static double computeAvgResponseTimeHours(List<ActivityRecord> activities) {
        var completed = activities.stream()
                .filter(a -> a.createdAt() != null && a.completedAt() != null)
                .map(a -> Duration.between(a.createdAt(), a.completedAt()).toHours())
                .filter(h -> h >= 0)
                .toList();

        if (completed.isEmpty()) {
            return DEFAULT_RESPONSE_TIME_HOURS;
        }
        return completed.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(DEFAULT_RESPONSE_TIME_HOURS);
    }
}
