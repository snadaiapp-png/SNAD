package com.sanad.platform.hr.performance.application;

import com.sanad.platform.hr.performance.domain.PerformanceReview;
import com.sanad.platform.hr.performance.domain.PerformanceReview.ReviewSource;
import com.sanad.platform.hr.performance.domain.PerformanceReview.ReviewStatus;
import com.sanad.platform.hr.performance.infrastructure.PerformanceReviewRepository;
import com.sanad.platform.hr.time.application.HrEmploymentScopeResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * G3 performance review use cases (HRM-G3 Task 2).
 *
 * <p>Scope model (canonical, no legacy fallbacks):</p>
 * <ul>
 *   <li>SELF — the authenticated user resolves to exactly one canonical
 *       {@code Person -> ACTIVE Employment} via {@link HrEmploymentScopeResolver};
 *       users without an active employment fail closed.</li>
 *   <li>TEAM — manager visibility derives exclusively from effective-dated
 *       PRIMARY assignment reporting relationships
 *       ({@code reports_to_assignment_id}); expired or foreign-tenant
 *       relationships never grant access.</li>
 *   <li>Reviewers are canonical employments; cross-tenant reviewer references
 *       are impossible (composite tenant-safe FK + service checks).</li>
 * </ul>
 *
 * <p>Lifecycle (deterministic, execution order §9):
 * {@code DRAFT -> SUBMITTED -> ACKNOWLEDGED} by the subject and
 * {@code DRAFT -> CANCELLED} by the reviewer; every other transition is
 * rejected. Transitions are optimistic on {@code version}.</p>
 */
@Service
public class HrPerformanceReviewService {

    private final PerformanceReviewRepository repository;
    private final HrEmploymentScopeResolver scopeResolver;

    public HrPerformanceReviewService(
            PerformanceReviewRepository repository,
            HrEmploymentScopeResolver scopeResolver) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.scopeResolver = Objects.requireNonNull(scopeResolver, "scopeResolver is required");
    }

    /** Employee creates a SELF review (reviewer == subject, canonical identity). */
    public PerformanceReview createSelfReview(UUID tenantId, UUID actorUserId, PerformanceReviewInput input) {
        UUID selfEmployment = scopeResolver.requireSelfEmployment(tenantId, actorUserId);
        UUID selfPerson = repository.requireCanonicalEmploymentPersonId(tenantId, selfEmployment);
        return insertDraft(tenantId, actorUserId, input,
                selfPerson, selfEmployment, selfPerson, selfEmployment, ReviewSource.SELF);
    }

    /** Manager creates a MANAGER review for a direct report (canonical TEAM scope). */
    public PerformanceReview createManagerReview(
            UUID tenantId,
            UUID managerUserId,
            UUID subjectEmploymentId,
            PerformanceReviewInput input) {
        scopeResolver.requireManagedEmployment(tenantId, managerUserId, subjectEmploymentId);
        UUID managerEmployment = scopeResolver.requireSelfEmployment(tenantId, managerUserId);
        UUID managerPerson = repository.requireCanonicalEmploymentPersonId(tenantId, managerEmployment);
        UUID subjectPerson = repository.requireCanonicalEmploymentPersonId(tenantId, subjectEmploymentId);
        return insertDraft(tenantId, managerUserId, input,
                subjectPerson, subjectEmploymentId, managerPerson, managerEmployment, ReviewSource.MANAGER);
    }

    /** Employee creates a PEER review for a colleague (distinct canonical employment). */
    public PerformanceReview createPeerReview(
            UUID tenantId,
            UUID actorUserId,
            UUID subjectEmploymentId,
            PerformanceReviewInput input) {
        UUID reviewerEmployment = scopeResolver.requireSelfEmployment(tenantId, actorUserId);
        if (reviewerEmployment.equals(subjectEmploymentId)) {
            throw new IllegalArgumentException(
                    "HRM_REVIEW_PEER_REVIEWER_MUST_DIFFER: a peer review cannot target the reviewer's own employment");
        }
        UUID reviewerPerson = repository.requireCanonicalEmploymentPersonId(tenantId, reviewerEmployment);
        UUID subjectPerson = repository.requireCanonicalEmploymentPersonId(tenantId, subjectEmploymentId);
        return insertDraft(tenantId, actorUserId, input,
                subjectPerson, subjectEmploymentId, reviewerPerson, reviewerEmployment, ReviewSource.PEER);
    }

    /**
     * Loads a review the actor is allowed to see: as subject, as reviewer, or
     * as the canonical manager of the subject (TEAM). Unknown ids under the
     * actor's tenant fail closed with an authorization error, so no
     * cross-tenant existence is leaked.
     */
    public PerformanceReview getReviewForActor(UUID tenantId, UUID actorUserId, UUID reviewId) {
        PerformanceReview review = repository.findById(tenantId, reviewId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Performance review is not visible to the authenticated principal"));
        UUID selfEmployment = scopeResolver.requireSelfEmployment(tenantId, actorUserId);
        if (review.subjectEmploymentId().equals(selfEmployment)
                || review.reviewerEmploymentId().equals(selfEmployment)) {
            return review;
        }
        try {
            scopeResolver.requireManagedEmployment(tenantId, actorUserId, review.subjectEmploymentId());
            return review;
        } catch (AccessDeniedException ignored) {
            throw new AccessDeniedException(
                    "Performance review is not visible to the authenticated principal");
        }
    }

    /** Reviews where the actor's employment is the subject or the reviewer. */
    public List<PerformanceReview> listReviewsForActor(UUID tenantId, UUID actorUserId) {
        UUID selfEmployment = scopeResolver.requireSelfEmployment(tenantId, actorUserId);
        return repository.listForEmployment(tenantId, selfEmployment);
    }

    /** Reviews of the actor's active direct reports (canonical TEAM scope). */
    public List<PerformanceReview> listTeamReviews(UUID tenantId, UUID managerUserId) {
        scopeResolver.requireSelfEmployment(tenantId, managerUserId);
        return repository.listTeamReviewsForManager(tenantId, managerUserId);
    }

    /** Reviewer submits a DRAFT review; submission requires a rating. */
    public PerformanceReview submitReview(UUID tenantId, UUID actorUserId, UUID reviewId) {
        PerformanceReview current = requireReviewOfActor(tenantId, actorUserId, reviewId);
        requireReviewer(current, scopeResolver.requireSelfEmployment(tenantId, actorUserId));
        if (current.rating() == null) {
            throw new IllegalArgumentException(
                    "HRM_REVIEW_RATING_REQUIRED_FOR_SUBMISSION: provide a rating before submitting");
        }
        return transition(tenantId, actorUserId, reviewId, current, ReviewStatus.SUBMITTED);
    }

    /** Subject acknowledges a SUBMITTED review. */
    public PerformanceReview acknowledgeReview(UUID tenantId, UUID actorUserId, UUID reviewId) {
        PerformanceReview current = requireReviewOfActor(tenantId, actorUserId, reviewId);
        requireSubject(current, scopeResolver.requireSelfEmployment(tenantId, actorUserId));
        return transition(tenantId, actorUserId, reviewId, current, ReviewStatus.ACKNOWLEDGED);
    }

    /** Reviewer cancels a DRAFT review. */
    public PerformanceReview cancelReview(UUID tenantId, UUID actorUserId, UUID reviewId) {
        PerformanceReview current = requireReviewOfActor(tenantId, actorUserId, reviewId);
        requireReviewer(current, scopeResolver.requireSelfEmployment(tenantId, actorUserId));
        return transition(tenantId, actorUserId, reviewId, current, ReviewStatus.CANCELLED);
    }

    // ==================== internals ====================

    private PerformanceReview insertDraft(
            UUID tenantId,
            UUID actorUserId,
            PerformanceReviewInput input,
            UUID subjectPersonId,
            UUID subjectEmploymentId,
            UUID reviewerPersonId,
            UUID reviewerEmploymentId,
            ReviewSource source) {
        Objects.requireNonNull(input, "input is required");
        if (input.periodStart() == null || input.periodEnd() == null) {
            throw new IllegalArgumentException("HRM_REVIEW_PERIOD_REQUIRED: period_start and period_end are required");
        }
        PerformanceReview review = new PerformanceReview(
                UUID.randomUUID(),
                tenantId,
                subjectPersonId,
                subjectEmploymentId,
                reviewerPersonId,
                reviewerEmploymentId,
                source,
                ReviewStatus.DRAFT,
                input.cycle(),
                input.periodStart(),
                input.periodEnd(),
                input.rating(),
                input.comments(),
                0,
                null, null, null, null);
        return repository.insert(review, actorUserId);
    }

    private PerformanceReview requireReviewOfActor(UUID tenantId, UUID actorUserId, UUID reviewId) {
        Objects.requireNonNull(reviewId, "reviewId is required");
        return repository.findById(tenantId, reviewId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Performance review is not visible to the authenticated principal"));
    }

    private void requireReviewer(PerformanceReview review, UUID actorEmployment) {
        if (!review.reviewerEmploymentId().equals(actorEmployment)) {
            throw new AccessDeniedException("Only the canonical reviewer may mutate the review");
        }
    }

    private void requireSubject(PerformanceReview review, UUID actorEmployment) {
        if (!review.subjectEmploymentId().equals(actorEmployment)) {
            throw new AccessDeniedException("Only the canonical subject may acknowledge the review");
        }
    }

    private PerformanceReview transition(
            UUID tenantId,
            UUID actorUserId,
            UUID reviewId,
            PerformanceReview current,
            ReviewStatus target) {
        validateTransition(current.status(), target);
        int updated = repository.transition(tenantId, reviewId, current.version(), target, actorUserId);
        if (updated != 1) {
            throw new IllegalStateException(
                    "HRM_REVIEW_CONCURRENT_MODIFICATION: review changed concurrently, retry with a fresh read");
        }
        return repository.findById(tenantId, reviewId)
                .orElseThrow(() -> new IllegalStateException(
                        "HRM_REVIEW_TRANSITION_FAILED: review disappeared after transition"));
    }

    private void validateTransition(ReviewStatus current, ReviewStatus target) {
        boolean allowed =
                (current == ReviewStatus.DRAFT && target == ReviewStatus.SUBMITTED)
                || (current == ReviewStatus.SUBMITTED && target == ReviewStatus.ACKNOWLEDGED)
                || (current == ReviewStatus.DRAFT && target == ReviewStatus.CANCELLED);
        if (!allowed) {
            throw new IllegalStateException(
                    "HRM_REVIEW_TRANSITION_INVALID: " + current + " -> " + target + " is not allowed");
        }
    }
}
