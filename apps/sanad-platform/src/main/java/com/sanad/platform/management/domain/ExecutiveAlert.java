package com.sanad.platform.management.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Executive Alert — a cross-domain alert linking management entities to
 * executive attention. Alerts are deduplicated: one alert per (source_entity, type).
 *
 * <p>Lifecycle: OPEN → ACKNOWLEDGED → RESOLVED / DISMISSED
 */
public record ExecutiveAlert(
        UUID id,
        UUID tenantId,
        AlertType type,
        Severity severity,
        SourceEntityType sourceEntityType,
        UUID sourceEntityId,
        String title,
        String description,
        Status status,
        UUID acknowledgedBy,
        Instant acknowledgedAt,
        UUID resolvedBy,
        Instant resolvedAt,
        String resolution,
        UUID createdBy,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum AlertType {
        CRITICAL_RISK, CRITICAL_ISSUE, SLA_BREACH, KPI_OFF_TRACK,
        OBJECTIVE_OFF_TRACK, DECISION_PENDING, ESCALATION_OVERDUE
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum Status {
        OPEN, ACKNOWLEDGED, RESOLVED, DISMISSED
    }

    public enum SourceEntityType {
        RISK, ISSUE, DECISION, KPI, OBJECTIVE, ESCALATION
    }

    public static ExecutiveAlert create(
            UUID tenantId, AlertType type, Severity severity,
            SourceEntityType sourceType, UUID sourceId,
            String title, String description, UUID createdBy) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        var now = Instant.now();
        return new ExecutiveAlert(
                UUID.randomUUID(), tenantId, type, severity, sourceType, sourceId,
                title, description, Status.OPEN,
                null, null, null, null, null, createdBy,
                0, now, now
        );
    }

    public ExecutiveAlert acknowledge(UUID userId) {
        requireStatus(Status.OPEN, "acknowledge");
        var now = Instant.now();
        return new ExecutiveAlert(
                id, tenantId, type, severity, sourceEntityType, sourceEntityId,
                title, description, Status.ACKNOWLEDGED,
                userId, now, resolvedBy, resolvedAt, resolution, createdBy,
                version + 1, createdAt, now
        );
    }

    public ExecutiveAlert resolve(UUID userId, String resolution) {
        if (status != Status.OPEN && status != Status.ACKNOWLEDGED) {
            throw new IllegalStateException("Cannot resolve from " + status);
        }
        var now = Instant.now();
        return new ExecutiveAlert(
                id, tenantId, type, severity, sourceEntityType, sourceEntityId,
                title, description, Status.RESOLVED,
                acknowledgedBy, acknowledgedAt, userId, now, resolution, createdBy,
                version + 1, createdAt, now
        );
    }

    public ExecutiveAlert dismiss(UUID userId, String reason) {
        requireStatus(Status.OPEN, "dismiss");
        var now = Instant.now();
        return new ExecutiveAlert(
                id, tenantId, type, severity, sourceEntityType, sourceEntityId,
                title, description, Status.DISMISSED,
                null, null, userId, now, reason, createdBy,
                version + 1, createdAt, now
        );
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " from " + status + " (requires " + expected + ")");
        }
    }
}
