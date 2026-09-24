package com.sanad.platform.management.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Executive Issue — a tracked management issue with lifecycle.
 *
 * <p>Lifecycle:
 * <pre>
 *   OPEN → TRIAGED → IN_PROGRESS → RESOLVED → CLOSED
 *                         ↘ BLOCKED → IN_PROGRESS
 *   CLOSED → REOPENED → IN_PROGRESS
 * </pre>
 */
public record Issue(
        UUID id,
        UUID tenantId,
        String code,
        String title,
        String description,
        Severity severity,
        Priority priority,
        Status status,
        String source,
        String impact,
        String rootCause,
        String resolution,
        UUID ownerUserId,
        UUID reportedBy,
        Instant reportedAt,
        LocalDate dueDate,
        Instant resolvedAt,
        Instant closedAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        OPEN, TRIAGED, IN_PROGRESS, BLOCKED, RESOLVED, CLOSED, REOPENED
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum Priority {
        LOW, NORMAL, HIGH, CRITICAL
    }

    public static Issue create(
            UUID tenantId, String code, String title, String description,
            Severity severity, Priority priority, String source, String impact,
            UUID ownerUserId, UUID reportedBy, LocalDate dueDate) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        var now = Instant.now();
        return new Issue(
                UUID.randomUUID(), tenantId, code, title, description,
                severity, priority, Status.OPEN, source, impact,
                null, null, ownerUserId, reportedBy, now, dueDate,
                null, null, 0, now, now
        );
    }

    public Issue triage() {
        requireStatus(Status.OPEN, "triage");
        return withStatus(Status.TRIAGED);
    }

    public Issue startProgress() {
        if (status != Status.TRIAGED && status != Status.REOPENED) {
            throw new IllegalStateException("Cannot start progress from " + status);
        }
        return withStatus(Status.IN_PROGRESS);
    }

    public Issue block(String reason) {
        requireStatus(Status.IN_PROGRESS, "block");
        // Store reason in rootCause field (reuse for simplicity)
        return new Issue(
                id, tenantId, code, title, description, severity, priority,
                Status.BLOCKED, source, impact, reason, resolution,
                ownerUserId, reportedBy, reportedAt, dueDate, resolvedAt, closedAt,
                version + 1, createdAt, Instant.now()
        );
    }

    public Issue unblock() {
        requireStatus(Status.BLOCKED, "unblock");
        return withStatus(Status.IN_PROGRESS);
    }

    public Issue resolve(String resolution) {
        requireStatus(Status.IN_PROGRESS, "resolve");
        var now = Instant.now();
        return new Issue(
                id, tenantId, code, title, description, severity, priority,
                Status.RESOLVED, source, impact, rootCause, resolution,
                ownerUserId, reportedBy, reportedAt, dueDate, now, closedAt,
                version + 1, createdAt, now
        );
    }

    public Issue close() {
        requireStatus(Status.RESOLVED, "close");
        var now = Instant.now();
        return new Issue(
                id, tenantId, code, title, description, severity, priority,
                Status.CLOSED, source, impact, rootCause, resolution,
                ownerUserId, reportedBy, reportedAt, dueDate, resolvedAt, now,
                version + 1, createdAt, now
        );
    }

    public Issue reopen(String reason) {
        requireStatus(Status.CLOSED, "reopen");
        return new Issue(
                id, tenantId, code, title, description, severity, priority,
                Status.REOPENED, source, impact, reason, null,
                ownerUserId, reportedBy, reportedAt, dueDate, null, null,
                version + 1, createdAt, Instant.now()
        );
    }

    private Issue withStatus(Status newStatus) {
        return new Issue(
                id, tenantId, code, title, description, severity, priority,
                newStatus, source, impact, rootCause, resolution,
                ownerUserId, reportedBy, reportedAt, dueDate, resolvedAt, closedAt,
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
