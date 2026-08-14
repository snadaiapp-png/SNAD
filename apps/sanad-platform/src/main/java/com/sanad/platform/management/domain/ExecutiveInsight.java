package com.sanad.platform.management.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Executive Insight — an AI-generated (or deterministic) advisory analysis.
 *
 * <p>Insights are ALWAYS advisory. They never mutate business state.
 * Every insight must reference its evidence (source data IDs) and
 * carry a confidence score (0.0 to 1.0).
 *
 * <p>If no external AI provider is configured, insights are generated
 * by deterministic rule-based analysis (model_name = 'deterministic').
 */
public record ExecutiveInsight(
        UUID id,
        UUID tenantId,
        InsightType type,
        String title,
        String description,
        BigDecimal confidence,
        String evidence,  // JSON array of source data references
        String modelName,
        String modelVersion,
        boolean advisory,
        InsightStatus status,
        UUID generatedBy,
        Instant createdAt
) {
    public enum InsightType {
        SUMMARY, ANOMALY, TREND, RISK_ASSESSMENT, ISSUE_ANALYSIS, FORECAST, RECOMMENDATION
    }

    public enum InsightStatus {
        ACTIVE, DISMISSED, ARCHIVED
    }

    public static ExecutiveInsight create(
            UUID tenantId, InsightType type, String title, String description,
            BigDecimal confidence, String evidence,
            String modelName, String modelVersion, UUID generatedBy) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (confidence == null || confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        return new ExecutiveInsight(
                UUID.randomUUID(), tenantId, type, title, description,
                confidence, evidence,
                modelName != null ? modelName : "deterministic",
                modelVersion != null ? modelVersion : "1.0",
                true,  // ALWAYS advisory
                InsightStatus.ACTIVE, generatedBy, Instant.now()
        );
    }

    public ExecutiveInsight dismiss() {
        if (status == InsightStatus.ARCHIVED) {
            throw new IllegalStateException("Insight already ARCHIVED");
        }
        return new ExecutiveInsight(
                id, tenantId, type, title, description, confidence, evidence,
                modelName, modelVersion, advisory, InsightStatus.DISMISSED, generatedBy, createdAt
        );
    }

    public ExecutiveInsight archive() {
        return new ExecutiveInsight(
                id, tenantId, type, title, description, confidence, evidence,
                modelName, modelVersion, advisory, InsightStatus.ARCHIVED, generatedBy, createdAt
        );
    }
}
