package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.NextBestAction;
import com.sanad.platform.crm.intelligence.domain.ScoreHistoryEntry;
import com.sanad.platform.crm.intelligence.domain.SegmentMembership;
import com.sanad.platform.crm.intelligence.domain.CachePort;
import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.query.domain.Customer360QueryPort;
import com.sanad.platform.crm.query.domain.Customer360QueryPort.Customer360View;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate application service for Customer 360 views.
 *
 * <p>Combines the existing basic Customer360View (from CRM-007) with
 * intelligence data (scores, segments, NBA) into a unified response.
 */
@Service
public class Customer360ApplicationService {

    private final Customer360QueryPort customer360QueryPort;
    private final CustomerIntelligenceQueryPortAdapter intelligenceQuery;
    private final AccountRepository accountRepository;
    private final CachePort cache;

    public Customer360ApplicationService(Customer360QueryPort customer360QueryPort,
                                         CustomerIntelligenceQueryPortAdapter intelligenceQuery,
                                         AccountRepository accountRepository,
                                         CachePort cache) {
        this.customer360QueryPort = customer360QueryPort;
        this.intelligenceQuery = intelligenceQuery;
        this.accountRepository = accountRepository;
        this.cache = cache;
    }

    /**
     * Load the enriched Customer 360 view (profile + intelligence).
     */
    public Map<String, Object> loadCustomer360(UUID tenantId, UUID accountId) {
        // Check cache first
        Map<String, Object> cached = cache.getView(tenantId, accountId, Map.class);
        if (cached != null) return cached;

        // Load base view
        Customer360View baseView = customer360QueryPort.getCustomer360(tenantId, accountId);

        // Load account record for additional profile data
        AccountRecord account = accountRepository.findById(tenantId, accountId);

        // Load intelligence
        List<StoredScore> scores = intelligenceQuery.findLatestScores(tenantId, accountId);
        List<NextBestAction> nbas = intelligenceQuery.findNextBestActions(tenantId, accountId);
        List<SegmentMembership> segments = intelligenceQuery.findActiveSegments(tenantId, accountId);

        // Build unified response
        Map<String, Object> result = new HashMap<>();
        result.put("accountId", accountId);
        result.put("tenantId", tenantId);
        result.put("displayName", baseView.displayName());
        result.put("accountType", baseView.accountType());
        result.put("lifecycleStatus", baseView.lifecycleStatus());
        if (account != null) {
            result.put("primaryCurrencyCode", account.primaryCurrencyCode());
            result.put("ownerUserId", account.ownerUserId());
        }
        result.put("contacts", baseView.contacts());
        result.put("opportunities", baseView.opportunities());
        result.put("activities", baseView.activities());

        // Intelligence block
        Map<String, Object> intelligence = new HashMap<>();
        if (!scores.isEmpty()) {
            Map<String, Object> scoresMap = new HashMap<>();
            for (StoredScore score : scores) {
                Map<String, Object> scoreData = new HashMap<>();
                scoreData.put("value", score.scoreValue());
                scoreData.put("band", score.scoreBand());
                scoreData.put("calculatedAt", score.calculatedAt());
                if (score.confidence() != null) scoreData.put("confidence", score.confidence());
                scoresMap.put(score.scoreType().toLowerCase(), scoreData);
            }
            intelligence.put("scores", scoresMap);
        }
        if (!nbas.isEmpty()) {
            intelligence.put("nextBestActions", nbas);
        }
        if (!segments.isEmpty()) {
            intelligence.put("segments", segments);
        }
        result.put("intelligence", intelligence);

        // Cache the result
        cache.putView(tenantId, accountId, result);
        return result;
    }

    /**
     * Get customer scores (cached).
     */
    public List<StoredScore> getScores(UUID tenantId, UUID accountId) {
        List<StoredScore> cached = cache.getScores(tenantId, accountId);
        if (cached != null) return cached;
        List<StoredScore> scores = intelligenceQuery.findLatestScores(tenantId, accountId);
        cache.putScores(tenantId, accountId, scores);
        return scores;
    }

    /**
     * Get score history.
     */
    public List<ScoreHistoryEntry> getScoreHistory(UUID tenantId, UUID accountId,
                                                    String scoreType, int limit) {
        return intelligenceQuery.findScoreHistory(tenantId, accountId, scoreType, limit);
    }
}
