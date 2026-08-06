package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.intelligence.domain.CachePort;
import com.sanad.platform.crm.intelligence.domain.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.time.Instant;

/**
 * Consumes customer intelligence domain events for side-effects:
 * logging, cache invalidation, and timeline recording.
 *
 * <p>Fire-and-forget events (logging, cache invalidation) use {@code @EventListener}.
 * Events that require database consistency (timeline recording) use
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so the timeline
 * entry is only persisted when the publishing transaction commits successfully.</p>
 */
@Component
public class CustomerIntelligenceEventListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerIntelligenceEventListener.class);

    private final TimelineEventPort timeline;
    private final CachePort cache;

    public CustomerIntelligenceEventListener(TimelineEventPort timeline, CachePort cache) {
        this.timeline = timeline;
        this.cache = cache;
    }

    // ── Score Calculated (fire-and-forget: log + cache invalidation) ──

    @Async
    @EventListener
    public void onScoreCalculated(CustomerScoreCalculatedEvent event) {
        log.info("Score calculated: tenant={} account={} type={} value={} band={} delta={}",
                event.tenantId(), event.accountId(), event.scoreType(),
                event.scoreValue(), event.scoreBand(), event.delta());

        if (event.hasChanged()) {
            log.warn("Score band changed for account {}: {} -> {} (delta={})",
                    event.accountId(), event.previousValue(), event.scoreValue(), event.delta());
        }

        try {
            cache.invalidateAll(event.tenantId(), event.accountId());
        } catch (Exception e) {
            log.warn("Failed to invalidate cache after score event for account {}: {}",
                    event.accountId(), e.getMessage());
        }
    }

    // ── Health Changed (needs DB consistency: timeline) ──

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHealthChanged(CustomerHealthChangedEvent event) {
        log.info("Health changed: tenant={} account={} {} -> {}",
                event.tenantId(), event.accountId(),
                event.previousBand(), event.newBand());

        try {
            timeline.record(
                    event.tenantId(), "ACCOUNT", event.accountId(),
                    "crm.intelligence.health.changed",
                    "Health changed from " + event.previousBand() + " to " + event.newBand(),
                    "CRM_INTELLIGENCE", event.accountId(), null, event.occurredAt());
        } catch (Exception e) {
            log.warn("Failed to record timeline for health change on account {}: {}",
                    event.accountId(), e.getMessage());
        }
    }

    // ── Lifetime Value Updated (needs DB consistency: timeline) ──

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLifetimeValueUpdated(CustomerLifetimeValueUpdatedEvent event) {
        log.info("CLV updated: tenant={} account={} tier={} predictedValue={}",
                event.tenantId(), event.accountId(),
                event.tier(), event.predictedValue());

        try {
            timeline.record(
                    event.tenantId(), "ACCOUNT", event.accountId(),
                    "crm.intelligence.lifetime_value.updated",
                    "CLV tier updated to " + event.tier()
                            + " (predicted: " + event.predictedValue() + ")",
                    "CRM_INTELLIGENCE", event.accountId(), null, event.occurredAt());
        } catch (Exception e) {
            log.warn("Failed to record timeline for CLV update on account {}: {}",
                    event.accountId(), e.getMessage());
        }
    }

    // ── Segment Changed (needs DB consistency: timeline) ──

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSegmentChanged(CustomerSegmentChangedEvent event) {
        log.info("Segment changed: tenant={} account={} segment={} {}",
                event.tenantId(), event.accountId(),
                event.segmentCode(), event.changeType());

        try {
            timeline.record(
                    event.tenantId(), "ACCOUNT", event.accountId(),
                    "crm.intelligence.segment.changed",
                    "Segment " + event.changeType().toLowerCase() + ": " + event.segmentCode(),
                    "CRM_INTELLIGENCE", event.accountId(), null, event.occurredAt());
        } catch (Exception e) {
            log.warn("Failed to record timeline for segment change on account {}: {}",
                    event.accountId(), e.getMessage());
        }
    }

    // ── Next Best Action Generated (needs DB consistency: timeline) ──

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNextBestActionGenerated(NextBestActionGeneratedEvent event) {
        log.info("NBA generated: tenant={} account={} action={} confidence={}",
                event.tenantId(), event.accountId(),
                event.actionCode(), event.confidence());

        try {
            timeline.record(
                    event.tenantId(), "ACCOUNT", event.accountId(),
                    "crm.intelligence.next_best_action.generated",
                    "Next best action: " + event.actionCode()
                            + " (confidence: " + event.confidence() + ")",
                    "CRM_INTELLIGENCE", event.accountId(), null, event.occurredAt());
        } catch (Exception e) {
            log.warn("Failed to record timeline for NBA on account {}: {}",
                    event.accountId(), e.getMessage());
        }
    }

    // ── Opportunity Score Updated (fire-and-forget: log only) ──

    @Async
    @EventListener
    public void onOpportunityScoreUpdated(OpportunityScoreUpdatedEvent event) {
        log.info("Opportunity score updated: tenant={} account={} type={} score={} estimatedValue={}",
                event.tenantId(), event.accountId(),
                event.opportunityType(), event.opportunityScore(), event.estimatedValue());
    }
}
