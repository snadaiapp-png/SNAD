package com.sanad.platform.management.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Escalation — a cross-domain escalation record linking risks, issues,
 * decisions, KPIs, or objectives to executive attention.
 *
 * <p>Escalations can be created:
 * <ul>
 *   <li>Manually by a user</li>
 *   <li>Automatically by the system when a KPI goes OFF_TRACK or a risk
 *       reaches CRITICAL severity</li>
 * </ul>
 *
 * <p>Lifecycle:
 * <pre>
 *   ACTIVE → ACKNOWLEDGED → RESOLVED
 *                    ↘ CANCELLED
 * </pre>
 */
public record Escalation(
        UUID id,
        UUID tenantId,
        String code,
        SourceEntityType sourceEntityType,
        UUID sourceEntityId,
        String reason,
        Severity severity,
        Status status,
        int escalationLevel,
        UUID assignedTo,
        Instant slaDeadline,
        Instant resolvedAt,
        String resolution,
        UUID createdBy,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        ACTIVE, ACKNOWLEDGED, RESOLVED, CANCELLED
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum SourceEntityType {
        RISK, ISSUE, DECISION, KPI, OBJECTIVE
    }

    public static Escalation create(
            UUID tenantId, String code, SourceEntityType sourceType, UUID sourceId,
            String reason, Severity severity, int escalationLevel,
            UUID assignedTo, Instant slaDeadline, UUID createdBy) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (escalationLevel < 1 || escalationLevel > 5) {
            throw new IllegalArgumentException("escalationLevel must be 1-5");
        }
        var now = Instant.now();
        return new Escalation(
                UUID.randomUUID(), tenantId, code, sourceType, sourceId,
                reason, severity, Status.ACTIVE, escalationLevel,
                assignedTo, slaDeadline, null, null, createdBy,
                0, now, now
        );
    }

    public Escalation acknowledge() {
        requireStatus(Status.ACTIVE, "acknowledge");
        return withStatus(Status.ACKNOWLEDGED);
    }

    public Escalation resolve(String resolution) {
        if (status != Status.ACTIVE && status != Status.ACKNOWLEDGED) {
            throw new IllegalStateException("Cannot resolve from " + status);
        }
        var now = Instant.now();
        return new Escalation(
                id, tenantId, code, sourceEntityType, sourceEntityId,
                reason, severity, Status.RESOLVED, escalationLevel,
                assignedTo, slaDeadline, now, resolution, createdBy,
                version + 1, createdAt, now
        );
    }

    public Escalation cancel() {
        requireStatus(Status.ACTIVE, "cancel");
        return withStatus(Status.CANCELLED);
    }

    private Escalation withStatus(Status newStatus) {
        return new Escalation(
                id, tenantId, code, sourceEntityType, sourceEntityId,
                reason, severity, newStatus, escalationLevel,
                assignedTo, slaDeadline, resolvedAt, resolution, createdBy,
                version + 1, createdAt, Instant.now()
        );
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " from " + status + " (requires " + expected + ")");
        }
    }
}
