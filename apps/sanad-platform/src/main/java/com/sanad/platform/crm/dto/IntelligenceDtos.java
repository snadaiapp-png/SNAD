package com.sanad.platform.crm.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRM API Contract -- Intelligence Response / Request DTOs (camelCase).
 * <p>
 * These records form the public, typed API contract for the Customer Intelligence module.
 * They are consumed by:
 *   - The OpenAPI generator (springdoc-openapi) to produce the schema artifact.
 *   - The frontend type generator (openapi-typescript) to produce TS types.
 *   - The contract tests under apps/sanad-platform/src/test/java/.../crm/contract/.
 * <p>
 * Branch: INT-001-rest-layer
 */
public final class IntelligenceDtos {

    private IntelligenceDtos() {}

    // ────────────────────────────────────────────────────────────────────
    // Score Responses
    // ────────────────────────────────────────────────────────────────────

    public record ScoreResponse(
            UUID id,
            UUID accountId,
            String scoreType,
            double scoreValue,
            String scoreBand,
            double confidence,
            Instant calculatedAt,
            String triggerReason,
            Map<String, Object> components) {}

    public record ScoreHistoryResponse(
            String scoreType,
            Double previousValue,
            String previousBand,
            double newValue,
            String newBand,
            double delta,
            Instant changedAt,
            String triggerReason) {}

    // ────────────────────────────────────────────────────────────────────
    // Insight Response
    // ────────────────────────────────────────────────────────────────────

    public record InsightResponse(
            UUID accountId,
            Map<String, Object> scores,
            List<NextBestActionResponse> nextBestActions,
            List<SegmentMembershipResponse> segments,
            Map<String, String> summary) {}

    // ────────────────────────────────────────────────────────────────────
    // Next Best Action Responses
    // ────────────────────────────────────────────────────────────────────

    public record NextBestActionResponse(
            UUID id,
            UUID accountId,
            String actionCode,
            String description,
            double confidence,
            String reasoning,
            String status,
            Instant generatedAt,
            Instant expiresAt,
            boolean humanConfirmationRequired,
            Instant resolvedAt,
            UUID resolvedBy,
            long version) {}

    public record NbaResolutionRequest(long expectedVersion) {}

    // ────────────────────────────────────────────────────────────────────
    // Segment Responses
    // ────────────────────────────────────────────────────────────────────

    public record SegmentResponse(
            UUID id,
            String segmentCode,
            String segmentName,
            String segmentType,
            String description,
            Object criteria,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {}

    public record SegmentCreateRequest(
            String segmentCode,
            String segmentName,
            String segmentType,
            String description,
            String criteriaJson) {}

    public record SegmentMembershipResponse(
            UUID id,
            UUID accountId,
            UUID segmentId,
            String membershipType,
            Instant assignedAt,
            boolean active) {}

    // ────────────────────────────────────────────────────────────────────
    // Scoring Model Responses
    // ────────────────────────────────────────────────────────────────────

    public record ScoringModelResponse(
            UUID id,
            String scoreType,
            String version,
            Map<String, Object> weights,
            boolean active,
            Instant activatedAt) {}

    public record ScoringModelUpdateRequest(Map<String, Object> weights) {}

    // ────────────────────────────────────────────────────────────────────
    // Indicator Request DTOs
    // ────────────────────────────────────────────────────────────────────

    public record CustomerHealthIndicatorsRequest(
            int daysSinceLastActivity,
            int openOpportunities,
            double totalPipeline,
            int meetingFreq30d,
            double responseTimeAvgHours,
            String lifecycleStatus) {}

    public record ChurnIndicatorsRequest(
            int daysSinceLastActivity,
            double engagementDeclinePct,
            int tenureDays,
            int supportIssuesLast90d) {}
}
