package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.intelligence.domain.NextBestAction;
import com.sanad.platform.crm.intelligence.domain.NextBestActionPort;
import com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEventPublisher;
import com.sanad.platform.crm.intelligence.domain.event.NextBestActionGeneratedEvent;
import com.sanad.platform.crm.intelligence.domain.CachePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for Next Best Action recommendations.
 */
@Service
public class NextBestActionService {

    private final NextBestActionPort nbaPort;
    private final CustomerIntelligenceQueryPortAdapter queryAdapter;
    private final CustomerIntelligenceEventPublisher eventPublisher;
    private final TimelineEventPort timeline;
    private final CachePort cache;
    private final CustomerIntelligenceValidator validator;

    public NextBestActionService(NextBestActionPort nbaPort,
                                 CustomerIntelligenceQueryPortAdapter queryAdapter,
                                 CustomerIntelligenceEventPublisher eventPublisher,
                                 TimelineEventPort timeline,
                                 CachePort cache,
                                 CustomerIntelligenceValidator validator) {
        this.nbaPort = nbaPort;
        this.queryAdapter = queryAdapter;
        this.eventPublisher = eventPublisher;
        this.timeline = timeline;
        this.cache = cache;
        this.validator = validator;
    }

    @Transactional
    public NextBestAction generateRecommendation(UUID tenantId, UUID accountId, UUID actorId,
                                                  String actionCode, String description,
                                                  double confidence, String reasoning,
                                                  boolean humanConfirmationRequired) {
        validator.validateCustomer(tenantId, accountId);

        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        NextBestAction nba = nbaPort.create(tenantId, accountId, actionCode,
                description, confidence, reasoning, expiresAt, humanConfirmationRequired);

        cache.invalidateAll(tenantId, accountId);

        eventPublisher.publish(new NextBestActionGeneratedEvent(
                tenantId, accountId, nba.actionId(), actionCode, confidence,
                Instant.now(), "nba-" + UUID.randomUUID()));

        timeline.record(tenantId, "ACCOUNT", accountId,
                "crm.intelligence.nba.generated",
                "Next best action: " + actionCode,
                "CRM_INTELLIGENCE", nba.actionId(), actorId, Instant.now());

        return nba;
    }

    @Transactional
    public Optional<NextBestAction> acceptRecommendation(UUID tenantId, UUID actionId,
                                                          UUID actorId, long expectedVersion) {
        Optional<NextBestAction> resolved = nbaPort.resolve(
                tenantId, actionId, NextBestAction.STATUS_ACCEPTED, actorId, expectedVersion);

        resolved.ifPresent(nba -> {
            cache.invalidateAll(tenantId, nba.accountId());
            timeline.record(tenantId, "ACCOUNT", nba.accountId(),
                    "crm.intelligence.nba.accepted",
                    "Recommendation accepted: " + nba.actionCode(),
                    "CRM_INTELLIGENCE", actionId, actorId, Instant.now());
        });

        return resolved;
    }

    @Transactional
    public Optional<NextBestAction> rejectRecommendation(UUID tenantId, UUID actionId,
                                                          UUID actorId, long expectedVersion) {
        Optional<NextBestAction> resolved = nbaPort.resolve(
                tenantId, actionId, NextBestAction.STATUS_REJECTED, actorId, expectedVersion);

        resolved.ifPresent(nba -> {
            cache.invalidateAll(tenantId, nba.accountId());
            timeline.record(tenantId, "ACCOUNT", nba.accountId(),
                    "crm.intelligence.nba.rejected",
                    "Recommendation rejected: " + nba.actionCode(),
                    "CRM_INTELLIGENCE", actionId, actorId, Instant.now());
        });

        return resolved;
    }

    @Transactional
    public int expireStaleRecommendations(UUID tenantId) {
        return nbaPort.expireStale(tenantId);
    }

    public List<NextBestAction> getPendingActions(UUID tenantId, UUID accountId) {
        return queryAdapter.findNextBestActions(tenantId, accountId);
    }
}
