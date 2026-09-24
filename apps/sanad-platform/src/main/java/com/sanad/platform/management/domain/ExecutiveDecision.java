package com.sanad.platform.management.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Executive Decision — a formal management decision with full lifecycle.
 *
 * <p>SLA fields: submittedAt and approvalDueAt track the approval SLA.
 * Default approval SLA is 7 days from submission (configurable via constructor).
 */
public record ExecutiveDecision(
        UUID id,
        UUID tenantId,
        String decisionNumber,
        String title,
        String description,
        String rationale,
        String category,
        Priority priority,
        Status status,
        String impact,
        String expectedOutcome,
        String actualOutcome,
        UUID ownerUserId,
        UUID createdBy,
        UUID decidedBy,
        LocalDate decisionDate,
        LocalDate dueDate,
        Instant executedAt,
        Instant completedAt,
        Instant submittedAt,
        Instant approvalDueAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, EXECUTING, COMPLETED, CANCELLED
    }

    public enum Priority {
        LOW, NORMAL, HIGH, CRITICAL
    }

    /** Default approval SLA in hours (7 days = 168 hours). */
    public static final long DEFAULT_APPROVAL_SLA_HOURS = 168;

    public static ExecutiveDecision create(
            UUID tenantId, String decisionNumber, String title, String description,
            String rationale, String category, Priority priority, String impact,
            String expectedOutcome, UUID ownerUserId, UUID createdBy,
            LocalDate dueDate) {
        if (decisionNumber == null || decisionNumber.isBlank()) {
            throw new IllegalArgumentException("decisionNumber must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        var now = Instant.now();
        return new ExecutiveDecision(
                UUID.randomUUID(), tenantId, decisionNumber, title, description,
                rationale, category, priority, Status.DRAFT, impact, expectedOutcome, null,
                ownerUserId, createdBy, null, null, dueDate, null, null,
                null, null, 0, now, now
        );
    }

    public ExecutiveDecision submit() {
        requireStatus(Status.DRAFT, "submit");
        var now = Instant.now();
        var slaDeadline = now.plus(DEFAULT_APPROVAL_SLA_HOURS, ChronoUnit.HOURS);
        return withStatusAndSla(Status.SUBMITTED, now, slaDeadline);
    }

    public ExecutiveDecision startReview() {
        requireStatus(Status.SUBMITTED, "startReview");
        return withStatus(Status.UNDER_REVIEW);
    }

    public ExecutiveDecision approve(UUID approverId) {
        requireStatus(Status.UNDER_REVIEW, "approve");
        if (approverId.equals(createdBy)) {
            throw new IllegalStateException(
                    "Segregation of duties: approver cannot be the same person who created the decision");
        }
        var now = Instant.now();
        return new ExecutiveDecision(
                id, tenantId, decisionNumber, title, description, rationale, category,
                priority, Status.APPROVED, impact, expectedOutcome, actualOutcome,
                ownerUserId, createdBy, approverId, LocalDate.now(), dueDate, null, null,
                submittedAt, approvalDueAt, version + 1, createdAt, now
        );
    }

    public ExecutiveDecision reject(UUID rejecterId) {
        requireStatus(Status.UNDER_REVIEW, "reject");
        var now = Instant.now();
        return new ExecutiveDecision(
                id, tenantId, decisionNumber, title, description, rationale, category,
                priority, Status.REJECTED, impact, expectedOutcome, actualOutcome,
                ownerUserId, createdBy, rejecterId, LocalDate.now(), dueDate, null, null,
                submittedAt, approvalDueAt, version + 1, createdAt, now
        );
    }

    public ExecutiveDecision startExecuting() {
        requireStatus(Status.APPROVED, "startExecuting");
        var now = Instant.now();
        return new ExecutiveDecision(
                id, tenantId, decisionNumber, title, description, rationale, category,
                priority, Status.EXECUTING, impact, expectedOutcome, actualOutcome,
                ownerUserId, createdBy, decidedBy, decisionDate, dueDate, now, null,
                submittedAt, approvalDueAt, version + 1, createdAt, now
        );
    }

    public ExecutiveDecision complete(String actualOutcome) {
        requireStatus(Status.EXECUTING, "complete");
        var now = Instant.now();
        return new ExecutiveDecision(
                id, tenantId, decisionNumber, title, description, rationale, category,
                priority, Status.COMPLETED, impact, expectedOutcome, actualOutcome,
                ownerUserId, createdBy, decidedBy, decisionDate, dueDate, executedAt, now,
                submittedAt, approvalDueAt, version + 1, createdAt, now
        );
    }

    public ExecutiveDecision cancel() {
        if (status == Status.COMPLETED) {
            throw new IllegalStateException("Cannot cancel COMPLETED decision");
        }
        return withStatus(Status.CANCELLED);
    }

    /** Check if the decision's approval SLA has been breached. */
    public boolean isApprovalSlaBreached() {
        return submittedAt != null && approvalDueAt != null
                && Instant.now().isAfter(approvalDueAt)
                && (status == Status.SUBMITTED || status == Status.UNDER_REVIEW);
    }

    private ExecutiveDecision withStatus(Status newStatus) {
        return withStatusAndSla(newStatus, submittedAt, approvalDueAt);
    }

    private ExecutiveDecision withStatusAndSla(Status newStatus, Instant submittedAt, Instant approvalDueAt) {
        return new ExecutiveDecision(
                id, tenantId, decisionNumber, title, description, rationale, category,
                priority, newStatus, impact, expectedOutcome, actualOutcome,
                ownerUserId, createdBy, decidedBy, decisionDate, dueDate, executedAt, completedAt,
                submittedAt, approvalDueAt, version + 1, createdAt, Instant.now()
        );
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " from " + status + " (requires " + expected + ")");
        }
    }
}
