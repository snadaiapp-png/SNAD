package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort;
import com.sanad.platform.crm.intelligence.domain.NextBestAction;
import com.sanad.platform.crm.intelligence.domain.ScoreHistoryEntry;
import com.sanad.platform.crm.intelligence.domain.Segment;
import com.sanad.platform.crm.intelligence.domain.SegmentMembership;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter that provides a clean application-facing API over the query port.
 * Allows application services to read intelligence data without depending
 * directly on the infrastructure layer.
 */
@Component
public class CustomerIntelligenceQueryPortAdapter {

    private final CustomerIntelligenceQueryPort queryPort;

    public CustomerIntelligenceQueryPortAdapter(CustomerIntelligenceQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    public Optional<CustomerIntelligenceQueryPort.StoredScore> findLatestScore(
            UUID tenantId, UUID accountId, String scoreType) {
        return queryPort.findLatestScore(tenantId, accountId, scoreType);
    }

    public List<CustomerIntelligenceQueryPort.StoredScore> findLatestScores(
            UUID tenantId, UUID accountId) {
        return queryPort.findLatestScores(tenantId, accountId);
    }

    public List<ScoreHistoryEntry> findScoreHistory(
            UUID tenantId, UUID accountId, String scoreType, int limit) {
        return queryPort.findScoreHistory(tenantId, accountId, scoreType, limit);
    }

    public List<NextBestAction> findNextBestActions(UUID tenantId, UUID accountId) {
        return queryPort.findNextBestActions(tenantId, accountId);
    }

    public List<SegmentMembership> findActiveSegments(UUID tenantId, UUID accountId) {
        return queryPort.findActiveSegments(tenantId, accountId);
    }

    public List<Segment> findAllSegments(UUID tenantId) {
        return queryPort.findAllSegments(tenantId);
    }
}
