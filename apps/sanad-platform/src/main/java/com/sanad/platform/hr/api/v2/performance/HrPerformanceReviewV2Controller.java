package com.sanad.platform.hr.api.v2.performance;

import com.sanad.platform.hr.performance.PerformanceCapabilities;
import com.sanad.platform.hr.performance.application.HrPerformanceReviewService;
import com.sanad.platform.hr.performance.application.PerformanceReviewInput;
import com.sanad.platform.hr.performance.domain.PerformanceReview;
import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G3 performance-review HTTP boundary.
 *
 * <p>SELF routes and TEAM routes are capability-separated. Scope and lifecycle
 * decisions stay in {@link HrPerformanceReviewService}; the controller only
 * binds the trusted tenant/user context and maps the HTTP contract. There is no
 * legacy manager fallback.</p>
 */
@RestController
@RequestMapping("/api/v2/hr/performance/reviews")
@Tag(name = "HRM-G3 Performance Reviews")
public class HrPerformanceReviewV2Controller {

    private final HrPerformanceReviewService reviewService;
    private final TenantRlsTransactionContext rlsContext;

    public HrPerformanceReviewV2Controller(
            HrPerformanceReviewService reviewService,
            TenantRlsTransactionContext rlsContext) {
        this.reviewService = Objects.requireNonNull(reviewService, "reviewService is required");
        this.rlsContext = Objects.requireNonNull(rlsContext, "rlsContext is required");
    }

    @GetMapping
    @Operation(operationId = "hrPerformanceReviewsList", summary = "List performance reviews visible to self")
    @RequireCapability(PerformanceCapabilities.REVIEW_SELF_VIEW)
    @Transactional(readOnly = true)
    public List<PerformanceReview> listReviews(Authentication authentication) {
        Actor actor = bind(authentication);
        return reviewService.listReviewsForActor(actor.tenantId(), actor.userId());
    }

    @GetMapping("/{reviewId}")
    @Operation(operationId = "hrPerformanceReviewGet", summary = "Get a performance review visible to self")
    @RequireCapability(PerformanceCapabilities.REVIEW_SELF_VIEW)
    @Transactional(readOnly = true)
    public PerformanceReview getReview(
            Authentication authentication,
            @PathVariable UUID reviewId) {
        Actor actor = bind(authentication);
        return reviewService.getSelfReviewForApi(actor.tenantId(), actor.userId(), reviewId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "hrPerformanceReviewCreate", summary = "Create an own performance review draft")
    @RequireCapability(PerformanceCapabilities.REVIEW_SELF_SUBMIT)
    @Transactional
    public PerformanceReview createSelfReview(
            Authentication authentication,
            @Valid @RequestBody ReviewWriteRequest request) {
        Actor actor = bind(authentication);
        return reviewService.createSelfReview(actor.tenantId(), actor.userId(), request.toInput());
    }

    @PostMapping("/{reviewId}/submit")
    @Operation(operationId = "hrPerformanceReviewSubmit", summary = "Submit an own review")
    @RequireCapability(PerformanceCapabilities.REVIEW_SELF_SUBMIT)
    @Transactional
    public PerformanceReview submitReview(
            Authentication authentication,
            @PathVariable UUID reviewId) {
        Actor actor = bind(authentication);
        return reviewService.submitReview(actor.tenantId(), actor.userId(), reviewId);
    }

    @PostMapping("/{reviewId}/acknowledge")
    @Operation(operationId = "hrPerformanceReviewAcknowledge", summary = "Acknowledge a submitted own review")
    @RequireCapability(PerformanceCapabilities.REVIEW_SELF_SUBMIT)
    @Transactional
    public PerformanceReview acknowledgeReview(
            Authentication authentication,
            @PathVariable UUID reviewId) {
        Actor actor = bind(authentication);
        return reviewService.acknowledgeReview(actor.tenantId(), actor.userId(), reviewId);
    }

    @GetMapping("/team")
    @Operation(operationId = "hrPerformanceTeamReviewsList", summary = "List canonical direct-report reviews")
    @RequireCapability(PerformanceCapabilities.REVIEW_TEAM_MANAGE)
    @Transactional(readOnly = true)
    public List<PerformanceReview> listTeamReviews(Authentication authentication) {
        Actor actor = bind(authentication);
        return reviewService.listTeamReviews(actor.tenantId(), actor.userId());
    }

    @GetMapping("/team/{reviewId}")
    @Operation(operationId = "hrPerformanceTeamReviewGet", summary = "Get a canonical direct-report review")
    @RequireCapability(PerformanceCapabilities.REVIEW_TEAM_MANAGE)
    @Transactional(readOnly = true)
    public PerformanceReview getTeamReview(
            Authentication authentication,
            @PathVariable UUID reviewId) {
        Actor actor = bind(authentication);
        return reviewService.getTeamReviewForApi(actor.tenantId(), actor.userId(), reviewId);
    }

    @PostMapping("/team/{employmentId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "hrPerformanceTeamReviewCreate", summary = "Create a manager review for a direct report")
    @RequireCapability(PerformanceCapabilities.REVIEW_TEAM_MANAGE)
    @Transactional
    public PerformanceReview createTeamReview(
            Authentication authentication,
            @PathVariable UUID employmentId,
            @Valid @RequestBody ReviewWriteRequest request) {
        Actor actor = bind(authentication);
        return reviewService.createManagerReview(
                actor.tenantId(), actor.userId(), employmentId, request.toInput());
    }

    @PostMapping("/team/reviews/{reviewId}/cancel")
    @Operation(operationId = "hrPerformanceTeamReviewCancel", summary = "Cancel a manager-authored draft review")
    @RequireCapability(PerformanceCapabilities.REVIEW_TEAM_MANAGE)
    @Transactional
    public PerformanceReview cancelTeamReview(
            Authentication authentication,
            @PathVariable UUID reviewId) {
        Actor actor = bind(authentication);
        return reviewService.cancelTeamReviewForApi(actor.tenantId(), actor.userId(), reviewId);
    }

    private Actor bind(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        rlsContext.applyForCurrentTransaction(tenantId);
        return new Actor(tenantId, userId);
    }

    private record Actor(UUID tenantId, UUID userId) {}

    public record ReviewWriteRequest(
            @NotBlank @Size(max = 80) String cycle,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd,
            @Min(1) @Max(5) Integer rating,
            @Size(max = 4000) String comments) {

        PerformanceReviewInput toInput() {
            return new PerformanceReviewInput(cycle, periodStart, periodEnd, rating, comments);
        }
    }
}
