package com.sanad.platform.crm.web;

import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.dto.IntelligenceDtos;
import com.sanad.platform.crm.dto.IntelligenceDtos.*;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.intelligence.application.*;
import com.sanad.platform.crm.intelligence.domain.*;
import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.crm.pagination.CrmEnvelopes.ListResponse;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * V2 REST controller for Customer Intelligence endpoints.
 * <p>
 * Mounted under {@code /api/v2/crm/intelligence}. Provides REST access to
 * scoring, insights, segments, and next-best-action recommendations.
 * <p>
 * Branch: INT-001-rest-layer
 */
@RestController
@RequestMapping("/api/v2/crm/intelligence")
public class IntelligenceController {

    private final CustomerIntelligenceQueryPortAdapter queryAdapter;
    private final CustomerHealthService healthService;
    private final CustomerLifetimeValueService clvService;
    private final ChurnPredictionService churnService;
    private final CustomerSegmentationService segmentationService;
    private final NextBestActionService nbaService;
    private final CustomerInsightService insightService;
    private final CustomerScoringService scoringService;
    private final CrmOwnershipHttpSupport http;
    private final CrmIdempotencyHttpSupport idempotency;
    private final ETagService etags;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public IntelligenceController(
            CustomerIntelligenceQueryPortAdapter queryAdapter,
            CustomerHealthService healthService,
            CustomerLifetimeValueService clvService,
            ChurnPredictionService churnService,
            CustomerSegmentationService segmentationService,
            NextBestActionService nbaService,
            CustomerInsightService insightService,
            CustomerScoringService scoringService,
            CrmOwnershipHttpSupport http,
            CrmIdempotencyHttpSupport idempotency,
            ETagService etags,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.queryAdapter = queryAdapter;
        this.healthService = healthService;
        this.clvService = clvService;
        this.churnService = churnService;
        this.segmentationService = segmentationService;
        this.nbaService = nbaService;
        this.insightService = insightService;
        this.scoringService = scoringService;
        this.http = http;
        this.idempotency = idempotency;
        this.etags = etags;
        this.objectMapper = objectMapper;
    }

    // =====================================================================
    // Score Endpoints
    // =====================================================================

    /**
     * GET /api/v2/crm/intelligence/{accountId}/scores
     * List all latest scores for an account.
     */
    @RequireCapability("CRM.CUSTOMER_INTELLIGENCE.READ")
    @GetMapping("/{accountId}/scores")
    public ListResponse<ScoreResponse> listScores(
            Authentication authentication,
            @PathVariable UUID accountId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        List<StoredScore> scores = queryAdapter.findLatestScores(context.tenantId(), accountId);
        List<ScoreResponse> data = scores.stream()
                .map(this::toScoreResponse)
                .toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(data.size()), requestId(request));
    }

    /**
     * GET /api/v2/crm/intelligence/{accountId}/scores/{scoreType}
     * Get a specific score for an account.
     */
    @RequireCapability("CRM.CUSTOMER_INTELLIGENCE.READ")
    @GetMapping("/{accountId}/scores/{scoreType}")
    public ResponseEntity<SingleResponse<ScoreResponse>> getScore(
            Authentication authentication,
            @PathVariable UUID accountId,
            @PathVariable String scoreType,
            HttpServletRequest request) {
        var context = http.context(authentication);
        StoredScore score = queryAdapter.findLatestScore(context.tenantId(), accountId, scoreType)
                .orElseThrow(() -> new CrmContractException(CrmErrorCode.CRM_SCORE_NOT_FOUND));
        ScoreResponse body = toScoreResponse(score);
        return wrapSingle(body, "intelligence-score", 0L, request);
    }

    /**
     * GET /api/v2/crm/intelligence/{accountId}/scores/{scoreType}/history
     * Get score history for an account and score type.
     */
    @RequireCapability("CRM.CUSTOMER_INTELLIGENCE.READ")
    @GetMapping("/{accountId}/scores/{scoreType}/history")
    public ListResponse<ScoreHistoryResponse> getScoreHistory(
            Authentication authentication,
            @PathVariable UUID accountId,
            @PathVariable String scoreType,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {
        var context = http.context(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<ScoreHistoryEntry> history = scoringService.getScoreHistory(
                context.tenantId(), accountId, scoreType, safeLimit);
        List<ScoreHistoryResponse> data = history.stream()
                .map(this::toScoreHistoryResponse)
                .toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(data.size()), requestId(request));
    }

    /**
     * POST /api/v2/crm/intelligence/{accountId}/scores/health
     * Calculate health score for an account.
     */
    @RequireCapability("CRM.CUSTOMER_INTELLIGENCE.WRITE")
    @PostMapping("/{accountId}/scores/health")
    public ResponseEntity<SingleResponse<ScoreResponse>> calculateHealth(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody CustomerHealthIndicatorsRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(authentication,
                "POST:/api/v2/crm/intelligence/" + accountId + "/scores/health",
                key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, ScoreResponse.class);
        try {
            var context = http.context(authentication);
            StoredScore stored = healthService.calculateHealth(
                    context.tenantId(), accountId, context.userId(),
                    body.daysSinceLastActivity(), body.openOpportunities(),
                    body.totalPipeline(), body.meetingFreq30d(),
                    body.responseTimeAvgHours(), body.lifecycleStatus());
            ScoreResponse response = toScoreResponse(stored);
            return idempotency.complete(guard, response, "intelligence-score", 0L, HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    /**
     * POST /api/v2/crm/intelligence/{accountId}/scores/clv
     * Calculate Customer Lifetime Value for an account.
     */
    @RequireCapability("CRM.CUSTOMER_INTELLIGENCE.WRITE")
    @PostMapping("/{accountId}/scores/clv")
    public ResponseEntity<SingleResponse<ScoreResponse>> calculateCLV(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody ClvIndicatorsRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(authentication,
                "POST:/api/v2/crm/intelligence/" + accountId + "/scores/clv",
                key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, ScoreResponse.class);
        try {
            var context = http.context(authentication);
            StoredScore stored = clvService.calculateCLV(
                    context.tenantId(), accountId, context.userId(),
                    body.totalRevenue(), body.transactionCount(),
                    body.avgDealSize(), body.customerSinceMonths(),
                    body.growthRate());
            ScoreResponse response = toScoreResponse(stored);
            return idempotency.complete(guard, response, "intelligence-score", 0L, HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    /**
     * POST /api/v2/crm/intelligence/{accountId}/scores/churn
     * Predict churn risk for an account.
     */
    @RequireCapability("CRM.CUSTOMER_INTELLIGENCE.WRITE")
    @PostMapping("/{accountId}/scores/churn")
    public ResponseEntity<SingleResponse<ScoreResponse>> predictChurn(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody ChurnIndicatorsRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(authentication,
                "POST:/api/v2/crm/intelligence/" + accountId + "/scores/churn",
                key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, ScoreResponse.class);
        try {
            var context = http.context(authentication);
            StoredScore stored = churnService.predictChurnRisk(
                    context.tenantId(), accountId, context.userId(),
                    body.daysSinceLastActivity(), body.engagementDeclinePct(),
                    body.tenureDays(), body.supportIssuesLast90d());
            ScoreResponse response = toScoreResponse(stored);
            return idempotency.complete(guard, response, "intelligence-score", 0L, HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    // =====================================================================
    // Insights Endpoint
    // =====================================================================

    /**
     * GET /api/v2/crm/intelligence/{accountId}/insights
     * Get unified intelligence insights for an account.
     */
    @RequireCapability("CRM.CUSTOMER_INTELLIGENCE.READ")
    @GetMapping("/{accountId}/insights")
    public SingleResponse<InsightResponse> getInsights(
            Authentication authentication,
            @PathVariable UUID accountId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        Map<String, Object> rawInsights = insightService.getCustomerInsights(
                context.tenantId(), accountId);

        @SuppressWarnings("unchecked")
        Map<String, Object> scores = (Map<String, Object>) rawInsights.getOrDefault("scores", Map.of());

        @SuppressWarnings("unchecked")
        List<NextBestAction> nbaList = (List<NextBestAction>) rawInsights.getOrDefault("nextBestActions", List.of());
        List<NextBestActionResponse> nbaResponses = nbaList.stream()
                .map(this::toNbaResponse)
                .toList();

        @SuppressWarnings("unchecked")
        List<SegmentMembership> segList = (List<SegmentMembership>) rawInsights.getOrDefault("segments", List.of());
        List<SegmentMembershipResponse> segResponses = segList.stream()
                .map(this::toSegmentMembershipResponse)
                .toList();

        @SuppressWarnings("unchecked")
        Map<String, String> summary = (Map<String, String>) rawInsights.getOrDefault("summary", Map.of());

        InsightResponse body = new InsightResponse(
                accountId, scores, nbaResponses, segResponses, summary);
        return SingleResponse.of(body, requestId(request));
    }

    // =====================================================================
    // Segment Endpoints
    // =====================================================================

    /**
     * GET /api/v2/crm/intelligence/{accountId}/segments
     * Get active segments for an account.
     */
    @RequireCapability("CRM.CUSTOMER_INTELLIGENCE.READ")
    @GetMapping("/{accountId}/segments")
    public ListResponse<SegmentMembershipResponse> getAccountSegments(
            Authentication authentication,
            @PathVariable UUID accountId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        List<SegmentMembership> memberships = segmentationService.getActiveSegments(
                context.tenantId(), accountId);
        List<SegmentMembershipResponse> data = memberships.stream()
                .map(this::toSegmentMembershipResponse)
                .toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(data.size()), requestId(request));
    }

    /**
     * GET /api/v2/crm/intelligence/segments
     * List all segments for the tenant.
     */
    @RequireCapability("CRM.CUSTOMER_SEGMENT.MANAGE")
    @GetMapping("/segments")
    public ListResponse<SegmentResponse> listSegments(
            Authentication authentication,
            HttpServletRequest request) {
        var context = http.context(authentication);
        List<Segment> segments = segmentationService.getAllSegments(context.tenantId());
        List<SegmentResponse> data = segments.stream()
                .map(this::toSegmentResponse)
                .toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(data.size()), requestId(request));
    }

    /**
     * POST /api/v2/crm/intelligence/segments
     * Create a new segment.
     */
    @RequireCapability("CRM.CUSTOMER_SEGMENT.MANAGE")
    @PostMapping("/segments")
    public ResponseEntity<SingleResponse<SegmentResponse>> createSegment(
            Authentication authentication,
            @Valid @RequestBody SegmentCreateRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(authentication,
                "POST:/api/v2/crm/intelligence/segments",
                key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, SegmentResponse.class);
        try {
            var context = http.context(authentication);
            Segment segment = segmentationService.createSegment(
                    context.tenantId(), context.userId(),
                    body.segmentCode(), body.segmentName(),
                    body.segmentType(), body.description(), body.criteriaJson());
            SegmentResponse response = toSegmentResponse(segment);
            return idempotency.complete(guard, response, "segment", 0L, HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    /**
     * POST /api/v2/crm/intelligence/segments/{segmentId}/members
     * Add an account to a segment.
     */
    @RequireCapability("CRM.CUSTOMER_SEGMENT.MANAGE")
    @PostMapping("/segments/{segmentId}/members")
    public ResponseEntity<SingleResponse<SegmentMembershipResponse>> addSegmentMember(
            Authentication authentication,
            @PathVariable UUID segmentId,
            @Valid @RequestBody AddSegmentMemberRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(authentication,
                "POST:/api/v2/crm/intelligence/segments/" + segmentId + "/members",
                key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, SegmentMembershipResponse.class);
        try {
            var context = http.context(authentication);
            SegmentMembership membership = segmentationService.addCustomerToSegment(
                    context.tenantId(), body.accountId(), segmentId,
                    context.userId(), body.membershipType());
            SegmentMembershipResponse response = toSegmentMembershipResponse(membership);
            return idempotency.complete(guard, response, "segment-membership", 0L, HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    /**
     * DELETE /api/v2/crm/intelligence/segments/{segmentId}/members/{accountId}
     * Remove an account from a segment.
     */
    @RequireCapability("CRM.CUSTOMER_SEGMENT.MANAGE")
    @DeleteMapping("/segments/{segmentId}/members/{accountId}")
    public ResponseEntity<Void> removeSegmentMember(
            Authentication authentication,
            @PathVariable UUID segmentId,
            @PathVariable UUID accountId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        segmentationService.removeCustomerFromSegment(
                context.tenantId(), accountId, segmentId, context.userId());
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // Next Best Action Endpoints
    // =====================================================================

    /**
     * GET /api/v2/crm/intelligence/{accountId}/nba
     * Get next best actions for an account.
     */
    @RequireCapability("CRM.CUSTOMER_INTELLIGENCE.READ")
    @GetMapping("/{accountId}/nba")
    public ListResponse<NextBestActionResponse> getNextBestActions(
            Authentication authentication,
            @PathVariable UUID accountId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        List<NextBestAction> actions = queryAdapter.findNextBestActions(
                context.tenantId(), accountId);
        List<NextBestActionResponse> data = actions.stream()
                .map(this::toNbaResponse)
                .toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(data.size()), requestId(request));
    }

    /**
     * POST /api/v2/crm/intelligence/nba/{actionId}/accept
     * Accept a next best action recommendation.
     */
    @RequireCapability("CRM.CUSTOMER_INTELLIGENCE.WRITE")
    @PostMapping("/nba/{actionId}/accept")
    public ResponseEntity<SingleResponse<NextBestActionResponse>> acceptRecommendation(
            Authentication authentication,
            @PathVariable UUID actionId,
            @Valid @RequestBody NbaResolutionRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(authentication,
                "POST:/api/v2/crm/intelligence/nba/" + actionId + "/accept",
                key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, NextBestActionResponse.class);
        try {
            var context = http.context(authentication);
            NextBestAction nba = nbaService.acceptRecommendation(
                    context.tenantId(), actionId, context.userId(), body.expectedVersion())
                    .orElseThrow(() -> new CrmContractException(CrmErrorCode.CRM_NBA_NOT_FOUND));
            NextBestActionResponse response = toNbaResponse(nba);
            return idempotency.complete(guard, response, "next-best-action", nba.version(), HttpStatus.OK);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    /**
     * POST /api/v2/crm/intelligence/nba/{actionId}/reject
     * Reject a next best action recommendation.
     */
    @RequireCapability("CRM.CUSTOMER_INTELLIGENCE.WRITE")
    @PostMapping("/nba/{actionId}/reject")
    public ResponseEntity<SingleResponse<NextBestActionResponse>> rejectRecommendation(
            Authentication authentication,
            @PathVariable UUID actionId,
            @Valid @RequestBody NbaResolutionRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(authentication,
                "POST:/api/v2/crm/intelligence/nba/" + actionId + "/reject",
                key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, NextBestActionResponse.class);
        try {
            var context = http.context(authentication);
            NextBestAction nba = nbaService.rejectRecommendation(
                    context.tenantId(), actionId, context.userId(), body.expectedVersion())
                    .orElseThrow(() -> new CrmContractException(CrmErrorCode.CRM_NBA_NOT_FOUND));
            NextBestActionResponse response = toNbaResponse(nba);
            return idempotency.complete(guard, response, "next-best-action", nba.version(), HttpStatus.OK);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    // =====================================================================
    // Mapper Methods
    // =====================================================================

    private ScoreResponse toScoreResponse(StoredScore score) {
        Map<String, Object> components = new HashMap<>();
        if (score.componentsJson() != null && !score.componentsJson().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(score.componentsJson(), Map.class);
                components = parsed;
            } catch (Exception ignored) {
                // Return empty components on parse failure
            }
        }
        return new ScoreResponse(
                score.id(),
                score.accountId(),
                score.scoreType(),
                score.scoreValue(),
                score.scoreBand(),
                score.confidence() != null ? score.confidence() : 0.0,
                score.calculatedAt(),
                score.triggerReason(),
                components);
    }

    private ScoreHistoryResponse toScoreHistoryResponse(ScoreHistoryEntry entry) {
        return new ScoreHistoryResponse(
                entry.scoreType(),
                entry.previousValue(),
                entry.previousBand(),
                entry.newValue(),
                entry.newBand(),
                entry.delta(),
                entry.changedAt(),
                entry.triggerReason());
    }

    private NextBestActionResponse toNbaResponse(NextBestAction nba) {
        return new NextBestActionResponse(
                nba.actionId(),
                nba.accountId(),
                nba.actionCode(),
                nba.description(),
                nba.confidence(),
                nba.reasoning(),
                nba.status(),
                nba.generatedAt(),
                nba.expiresAt(),
                nba.humanConfirmationRequired(),
                nba.resolvedAt(),
                nba.resolvedBy(),
                nba.version());
    }

    private SegmentResponse toSegmentResponse(Segment segment) {
        return new SegmentResponse(
                segment.id(),
                segment.segmentCode(),
                segment.segmentName(),
                segment.segmentType(),
                segment.description(),
                segment.criteria(),
                segment.active(),
                segment.createdAt(),
                segment.updatedAt());
    }

    private SegmentMembershipResponse toSegmentMembershipResponse(SegmentMembership membership) {
        return new SegmentMembershipResponse(
                membership.id(),
                membership.accountId(),
                membership.segmentId(),
                membership.membershipType(),
                membership.assignedAt(),
                membership.active());
    }

    private <T> ResponseEntity<SingleResponse<T>> wrapSingle(
            T body, String entityType, long version, HttpServletRequest request) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        UUID id = extractId(body);
        if (id != null) {
            headers.setETag(etags.etag(entityType, id, version));
        }
        return ResponseEntity.ok().headers(headers).body(SingleResponse.of(body, requestId(request)));
    }

    private static UUID requestId(HttpServletRequest request) {
        if (request != null) {
            String value = request.getHeader("X-Request-ID");
            if (value != null && !value.isBlank()) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                    // Generate a valid request id below.
                }
            }
        }
        return UUID.randomUUID();
    }

    private static UUID extractId(Object dto) {
        if (dto == null || !dto.getClass().isRecord()) return null;
        try {
            for (var component : dto.getClass().getRecordComponents()) {
                if ("id".equals(component.getName()) && component.getType() == UUID.class) {
                    return (UUID) component.getAccessor().invoke(dto);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    // =====================================================================
    // Additional Request DTOs
    // =====================================================================

    public record ClvIndicatorsRequest(
            double totalRevenue,
            int transactionCount,
            double avgDealSize,
            int customerSinceMonths,
            double growthRate) {}

    public record AddSegmentMemberRequest(
            UUID accountId,
            String membershipType) {}
}
