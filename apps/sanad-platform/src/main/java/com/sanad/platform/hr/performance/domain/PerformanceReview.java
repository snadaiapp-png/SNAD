package com.sanad.platform.hr.performance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical G3 performance review aggregate (HRM-G3 Task 2).
 *
 * <p>A review is tenant-bound and anchored to canonical HR identities:
 * the subject and the reviewer are each resolved through
 * {@code User -> Person -> ACTIVE Employment}, never through raw user ids.
 * The database layer enforces the same invariants with composite
 * tenant-safe foreign keys; this record enforces them structurally so an
 * invalid aggregate cannot be built in the application layer either.</p>
 *
 * <p>Structural rules mirrored by PostgreSQL constraints:</p>
 * <ul>
 *   <li>{@code period_end >= period_start};</li>
 *   <li>{@code rating} is null while {@code DRAFT/CANCELLED} and 1..5 otherwise
 *       ({@code SUBMITTED}/{@code ACKNOWLEDGED} require a rating);</li>
 *   <li>{@code SELF} reviews carry the subject as reviewer;</li>
 *   <li>{@code MANAGER}/{@code PEER} reviews carry a reviewer distinct from
 *       the subject.</li>
 * </ul>
 */
public record PerformanceReview(
        UUID id,
        UUID tenantId,
        UUID subjectPersonId,
        UUID subjectEmploymentId,
        UUID reviewerPersonId,
        UUID reviewerEmploymentId,
        ReviewSource source,
        ReviewStatus status,
        String cycle,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer rating,
        String comments,
        int version,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        UUID updatedBy
) {

    public PerformanceReview {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(subjectPersonId, "subjectPersonId is required");
        Objects.requireNonNull(subjectEmploymentId, "subjectEmploymentId is required");
        Objects.requireNonNull(reviewerPersonId, "reviewerPersonId is required");
        Objects.requireNonNull(reviewerEmploymentId, "reviewerEmploymentId is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(periodStart, "periodStart is required");
        Objects.requireNonNull(periodEnd, "periodEnd is required");

        if (cycle == null || cycle.isBlank() || cycle.length() > 80) {
            throw new IllegalArgumentException("HRM_REVIEW_CYCLE_INVALID: cycle must be 1..80 characters");
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("HRM_REVIEW_PERIOD_INVALID: period_end must not precede period_start");
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("HRM_REVIEW_RATING_INVALID: rating must be between 1 and 5");
        }
        if ((status == ReviewStatus.SUBMITTED || status == ReviewStatus.ACKNOWLEDGED) && rating == null) {
            throw new IllegalArgumentException("HRM_REVIEW_RATING_REQUIRED: submitted or acknowledged reviews need a rating");
        }
        if (source == ReviewSource.SELF
                && !(reviewerEmploymentId.equals(subjectEmploymentId)
                     && reviewerPersonId.equals(subjectPersonId))) {
            throw new IllegalArgumentException("HRM_REVIEW_SELF_REVIEWER_MUST_BE_SUBJECT");
        }
        if (source != ReviewSource.SELF && reviewerEmploymentId.equals(subjectEmploymentId)) {
            throw new IllegalArgumentException("HRM_REVIEW_REVIEWER_MUST_DIFFER_FROM_SUBJECT");
        }
    }

    /** Returns a copy advanced to {@code newStatus} with optimistic-version increment. */
    public PerformanceReview withStatus(ReviewStatus newStatus, UUID actorUserId, Instant at) {
        Objects.requireNonNull(newStatus, "newStatus is required");
        return new PerformanceReview(
                id, tenantId,
                subjectPersonId, subjectEmploymentId,
                reviewerPersonId, reviewerEmploymentId,
                source, newStatus, cycle,
                periodStart, periodEnd,
                rating, comments,
                version + 1,
                createdAt, createdBy,
                at, actorUserId);
    }

    /** Review source — roadmap G3-T02: self, manager, and peer evaluations. */
    public enum ReviewSource {
        SELF, MANAGER, PEER
    }

    /**
     * Deterministic review lifecycle (execution order §9):
     * {@code DRAFT -> SUBMITTED -> ACKNOWLEDGED}, {@code DRAFT -> CANCELLED}.
     */
    public enum ReviewStatus {
        DRAFT, SUBMITTED, ACKNOWLEDGED, CANCELLED
    }
}
