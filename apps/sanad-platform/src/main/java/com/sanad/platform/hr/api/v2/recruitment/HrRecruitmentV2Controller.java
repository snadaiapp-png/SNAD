package com.sanad.platform.hr.api.v2.recruitment;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanad.platform.hr.api.v2.HrApiErrorCode;
import com.sanad.platform.hr.api.v2.HrDomainException;
import com.sanad.platform.hr.api.v2.HrmIdempotentCommandExecutor;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.recruitment.application.HireConversionCommand;
import com.sanad.platform.hr.recruitment.application.HireConversionResult;
import com.sanad.platform.hr.recruitment.application.HrApplicationService;
import com.sanad.platform.hr.recruitment.application.HrCandidateService;
import com.sanad.platform.hr.recruitment.application.HrHireConversionService;
import com.sanad.platform.hr.recruitment.application.HrInterviewService;
import com.sanad.platform.hr.recruitment.application.HrJobOpeningService;
import com.sanad.platform.hr.recruitment.application.HrOfferService;
import com.sanad.platform.hr.recruitment.domain.HrOffer;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrInterviewRepository;
import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * HRM-G1 T10 — canonical Recruitment v2 HTTP API (design §13).
 *
 * <p>All commands are thin adapters over the certified T3–T8 application
 * services. Tenant identity comes only from the authenticated security
 * context; FORCE RLS remains active underneath. Every mutation requires the
 * canonical Idempotency-Key header.</p>
 */
@RestController
@RequestMapping("/api/v2/hr/recruitment")
public class HrRecruitmentV2Controller {

    private static final String OP = "hr.v2.recruitment";

    private final HrJobOpeningService openings;
    private final HrCandidateService candidates;
    private final HrApplicationService applications;
    private final HrInterviewService interviews;
    private final HrOfferService offers;
    private final HrHireConversionService hireConversion;
    private final HrRecruitmentApiQueryService queries;
    private final HrmIdempotentCommandExecutor idempotency;

    public HrRecruitmentV2Controller(
            HrJobOpeningService openings,
            HrCandidateService candidates,
            HrApplicationService applications,
            HrInterviewService interviews,
            HrOfferService offers,
            HrHireConversionService hireConversion,
            HrRecruitmentApiQueryService queries,
            HrmIdempotentCommandExecutor idempotency) {
        this.openings = openings;
        this.candidates = candidates;
        this.applications = applications;
        this.interviews = interviews;
        this.offers = offers;
        this.hireConversion = hireConversion;
        this.queries = queries;
        this.idempotency = idempotency;
    }

    // ==================== openings ====================

    @Operation(operationId = "hrRecruitmentOpeningCreate")
    @PostMapping("/openings")
    @RequireCapability("HRM.RECRUITMENT.OPENING.MANAGE")
    public ResponseEntity<IdResponse> createOpening(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOpeningRequest request) {
        IdResponse result = idem(authentication, OP + ".opening.create", idempotencyKey, request,
                IdResponse.class, () -> new IdResponse(openings.create(
                        context(authentication), request.jobId(), request.jobVersionId(), request.orgUnitId(),
                        request.positionId(), request.requestedHeadcount(), request.opensAt(), request.closesAt())));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(operationId = "hrRecruitmentOpeningList")
    @GetMapping("/openings")
    @RequireCapability("HRM.RECRUITMENT.OPENING.VIEW")
    public HrRecruitmentApiQueryService.CursorPage<HrRecruitmentApiQueryService.OpeningView> listOpenings(
            Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return queries.openings(tenant(authentication), cursor, limit);
    }

    @Operation(operationId = "hrRecruitmentOpeningGet")
    @GetMapping("/openings/{id}")
    @RequireCapability("HRM.RECRUITMENT.OPENING.VIEW")
    public HrRecruitmentApiQueryService.OpeningView getOpening(
            Authentication authentication, @PathVariable UUID id) {
        return queries.opening(tenant(authentication), id);
    }

    @Operation(operationId = "hrRecruitmentOpeningSubmit")
    @PostMapping("/openings/{id}/submit")
    @RequireCapability("HRM.RECRUITMENT.OPENING.MANAGE")
    public WorkflowResponse submitOpening(Authentication authentication, @PathVariable UUID id,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return idem(authentication, OP + ".opening.submit", idempotencyKey, id,
                WorkflowResponse.class, () ->
                        new WorkflowResponse(id, openings.submit(context(authentication), id)));
    }

    @Operation(operationId = "hrRecruitmentOpeningApprove")
    @PostMapping("/openings/{id}/approve")
    @RequireCapability("HRM.RECRUITMENT.OPENING.PUBLISH")
    public CommandResponse approveOpening(Authentication authentication, @PathVariable UUID id,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return idem(authentication, OP + ".opening.approve", idempotencyKey, id,
                CommandResponse.class, () -> {
                    openings.approve(context(authentication), id);
                    return new CommandResponse(id, "OPEN");
                });
    }

    @Operation(operationId = "hrRecruitmentOpeningReject")
    @PostMapping("/openings/{id}/reject")
    @RequireCapability("HRM.RECRUITMENT.OPENING.PUBLISH")
    public CommandResponse rejectOpening(Authentication authentication, @PathVariable UUID id,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         @Valid @RequestBody ReasonRequest request) {
        return idem(authentication, OP + ".opening.reject", idempotencyKey, id + "|" + request,
                CommandResponse.class, () -> {
                    openings.reject(context(authentication), id, request.reasonCode());
                    return new CommandResponse(id, "DRAFT");
                });
    }

    @Operation(operationId = "hrRecruitmentOpeningPause")
    @PostMapping("/openings/{id}/pause")
    @RequireCapability("HRM.RECRUITMENT.OPENING.MANAGE")
    public CommandResponse pauseOpening(Authentication authentication, @PathVariable UUID id,
                                        @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return openingCommand(authentication, id, idempotencyKey, "pause", "PAUSED",
                () -> openings.pause(context(authentication), id));
    }

    @Operation(operationId = "hrRecruitmentOpeningResume")
    @PostMapping("/openings/{id}/resume")
    @RequireCapability("HRM.RECRUITMENT.OPENING.MANAGE")
    public CommandResponse resumeOpening(Authentication authentication, @PathVariable UUID id,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return openingCommand(authentication, id, idempotencyKey, "resume", "OPEN",
                () -> openings.resume(context(authentication), id));
    }

    @Operation(operationId = "hrRecruitmentOpeningClose")
    @PostMapping("/openings/{id}/close")
    @RequireCapability("HRM.RECRUITMENT.OPENING.MANAGE")
    public CommandResponse closeOpening(Authentication authentication, @PathVariable UUID id,
                                        @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return openingCommand(authentication, id, idempotencyKey, "close", "CLOSED",
                () -> openings.close(context(authentication), id));
    }

    @Operation(operationId = "hrRecruitmentOpeningCancel")
    @PostMapping("/openings/{id}/cancel")
    @RequireCapability("HRM.RECRUITMENT.OPENING.MANAGE")
    public CommandResponse cancelOpening(Authentication authentication, @PathVariable UUID id,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return openingCommand(authentication, id, idempotencyKey, "cancel", "CANCELLED",
                () -> openings.cancel(context(authentication), id));
    }

    // ==================== candidates ====================

    @Operation(operationId = "hrRecruitmentCandidateCreate")
    @PostMapping("/candidates")
    @RequireCapability("HRM.RECRUITMENT.CANDIDATE.MANAGE")
    public ResponseEntity<IdResponse> createCandidate(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateCandidateRequest request) {
        IdResponse result = idem(authentication, OP + ".candidate.create", idempotencyKey, request,
                IdResponse.class, () -> new IdResponse(candidates.create(context(authentication),
                        request.displayName(), request.email(), request.phone(),
                        request.compensationExpectations())));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(operationId = "hrRecruitmentCandidateList")
    @GetMapping("/candidates")
    @RequireCapability("HRM.RECRUITMENT.CANDIDATE.VIEW")
    public HrRecruitmentApiQueryService.CursorPage<HrRecruitmentApiQueryService.CandidateSummary> listCandidates(
            Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return queries.candidates(tenant(authentication), cursor, limit);
    }

    @Operation(operationId = "hrRecruitmentCandidateGet")
    @GetMapping("/candidates/{id}")
    @RequireCapability("HRM.RECRUITMENT.CANDIDATE.VIEW")
    public HrCandidateService.CandidateView getCandidate(
            Authentication authentication, @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean fullDetails) {
        return candidates.read(context(authentication), id, fullDetails);
    }

    @Operation(operationId = "hrRecruitmentCandidateArchive")
    @PostMapping("/candidates/{id}/archive")
    @RequireCapability("HRM.RECRUITMENT.CANDIDATE.MANAGE")
    public CommandResponse archiveCandidate(Authentication authentication, @PathVariable UUID id,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return idem(authentication, OP + ".candidate.archive", idempotencyKey, id,
                CommandResponse.class, () -> {
                    candidates.archive(context(authentication), id);
                    return new CommandResponse(id, "ARCHIVED");
                });
    }

    // ==================== applications ====================

    @Operation(operationId = "hrRecruitmentApplicationCreate")
    @PostMapping("/applications")
    @RequireCapability("HRM.RECRUITMENT.APPLICATION.MANAGE")
    public ResponseEntity<IdResponse> createApplication(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateApplicationRequest request) {
        IdResponse result = idem(authentication, OP + ".application.create", idempotencyKey, request,
                IdResponse.class, () -> {
                    try {
                        return new IdResponse(applications.apply(context(authentication),
                                request.candidateId(), request.jobOpeningId()));
                    } catch (IllegalStateException conflict) {
                        if (conflict.getMessage() != null
                                && conflict.getMessage().startsWith("HRM_APPLICATION_ALREADY_ACTIVE:")) {
                            throw new HrDomainException(HrApiErrorCode.HRM_APPLICATION_DUP_ACTIVE,
                                    "An active application already exists for this candidate and opening");
                        }
                        throw conflict;
                    }
                });
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(operationId = "hrRecruitmentApplicationList")
    @GetMapping("/applications")
    @RequireCapability("HRM.RECRUITMENT.APPLICATION.MANAGE")
    public HrRecruitmentApiQueryService.CursorPage<HrRecruitmentApiQueryService.ApplicationView> listApplications(
            Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return queries.applications(tenant(authentication), cursor, limit);
    }

    @Operation(operationId = "hrRecruitmentApplicationGet")
    @GetMapping("/applications/{id}")
    @RequireCapability("HRM.RECRUITMENT.APPLICATION.MANAGE")
    public HrRecruitmentApiQueryService.ApplicationView getApplication(
            Authentication authentication, @PathVariable UUID id) {
        return queries.application(tenant(authentication), id);
    }

    @Operation(operationId = "hrRecruitmentApplicationAdvance")
    @PostMapping("/applications/{id}/advance")
    @RequireCapability("HRM.RECRUITMENT.APPLICATION.ADVANCE")
    public CommandResponse advanceApplication(Authentication authentication, @PathVariable UUID id,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return applicationCommand(authentication, id, idempotencyKey, "advance",
                () -> applications.advance(context(authentication), id));
    }

    @Operation(operationId = "hrRecruitmentApplicationReject")
    @PostMapping("/applications/{id}/reject")
    @RequireCapability("HRM.RECRUITMENT.APPLICATION.REJECT")
    public CommandResponse rejectApplication(Authentication authentication, @PathVariable UUID id,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @Valid @RequestBody ReasonRequest request) {
        return idem(authentication, OP + ".application.reject", idempotencyKey, id + "|" + request,
                CommandResponse.class, () -> {
                    applications.reject(context(authentication), id, request.reasonCode());
                    return new CommandResponse(id, "REJECTED");
                });
    }

    @Operation(operationId = "hrRecruitmentApplicationWithdraw")
    @PostMapping("/applications/{id}/withdraw")
    @RequireCapability("HRM.RECRUITMENT.APPLICATION.MANAGE")
    public CommandResponse withdrawApplication(Authentication authentication, @PathVariable UUID id,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @Valid @RequestBody ReasonRequest request) {
        return idem(authentication, OP + ".application.withdraw", idempotencyKey, id + "|" + request,
                CommandResponse.class, () -> {
                    applications.withdraw(context(authentication), id, request.reasonCode());
                    return new CommandResponse(id, "WITHDRAWN");
                });
    }

    @Operation(operationId = "hrRecruitmentApplicationPipeline")
    @GetMapping("/applications/{id}/pipeline")
    @RequireCapability("HRM.RECRUITMENT.APPLICATION.MANAGE")
    public List<com.sanad.platform.hr.recruitment.domain.HrApplication.StagePeriod> applicationPipeline(
            Authentication authentication, @PathVariable UUID id) {
        return applications.stageHistory(context(authentication), id);
    }

    // ==================== interviews ====================

    @Operation(operationId = "hrRecruitmentInterviewSchedule")
    @PostMapping("/applications/{id}/interviews")
    @RequireCapability("HRM.RECRUITMENT.INTERVIEW.SCHEDULE")
    public ResponseEntity<IdResponse> scheduleInterview(
            Authentication authentication, @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ScheduleInterviewRequest request) {
        IdResponse result = idem(authentication, OP + ".interview.schedule", idempotencyKey, id + "|" + request,
                IdResponse.class, () -> new IdResponse(interviews.schedule(
                        context(authentication), id, request.plannedAt(), request.mode(),
                        request.durationMinutes(), request.panelUserIds())));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(operationId = "hrRecruitmentInterviewGet")
    @GetMapping("/interviews/{id}")
    @RequireCapability("HRM.RECRUITMENT.INTERVIEW.MANAGE")
    public JdbcHrInterviewRepository.InterviewRow getInterview(
            Authentication authentication, @PathVariable UUID id) {
        return interviews.get(context(authentication), id);
    }

    @Operation(operationId = "hrRecruitmentInterviewOutcome")
    @PostMapping("/interviews/{id}/outcome")
    @RequireCapability("HRM.RECRUITMENT.INTERVIEW.RECORD_OUTCOME")
    public CommandResponse recordInterviewOutcome(
            Authentication authentication, @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InterviewOutcomeRequest request) {
        return idem(authentication, OP + ".interview.outcome", idempotencyKey, id + "|" + request,
                CommandResponse.class, () -> {
                    interviews.recordOutcome(context(authentication), id, request.state(), request.outcome());
                    return new CommandResponse(id, request.state());
                });
    }

    @Operation(operationId = "hrRecruitmentInterviewFeedbackPut")
    @PutMapping("/interviews/{id}/feedback")
    @RequireCapability("HRM.RECRUITMENT.INTERVIEW.MANAGE")
    public CommandResponse putInterviewFeedback(
            Authentication authentication, @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody InterviewFeedbackRequest request) {
        JdbcHrInterviewRepository.InterviewRow current = interviews.get(context(authentication), id);
        checkIfMatch(ifMatch, current.version());
        return idem(authentication, OP + ".interview.feedback", idempotencyKey, id + "|" + request,
                CommandResponse.class, () -> {
                    interviews.submitFeedback(context(authentication), id, request.scorecard().toString());
                    return new CommandResponse(id, "FEEDBACK_RECORDED");
                });
    }

    @Operation(operationId = "hrRecruitmentInterviewFeedbackGet")
    @GetMapping("/interviews/{id}/feedback")
    @RequireCapability("HRM.RECRUITMENT.INTERVIEW.MANAGE")
    public List<HrRecruitmentApiQueryService.FeedbackView> getInterviewFeedback(
            Authentication authentication, @PathVariable UUID id) {
        interviews.get(context(authentication), id);
        return queries.feedback(tenant(authentication), id);
    }

    // ==================== offers ====================

    @Operation(operationId = "hrRecruitmentOfferCreate")
    @PostMapping("/applications/{id}/offers")
    @RequireCapability("HRM.RECRUITMENT.OFFER.MANAGE")
    public ResponseEntity<IdResponse> createOffer(
            Authentication authentication, @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOfferRequest request) {
        IdResponse result = idem(authentication, OP + ".offer.create", idempotencyKey, id + "|" + request,
                IdResponse.class, () -> new IdResponse(offers.createOffer(
                        context(authentication), id,
                        request.contractTerms().toString(),
                        request.compensation().toString(),
                        request.expiresAt())));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(operationId = "hrRecruitmentOfferExtend")
    @PostMapping("/offers/{id}/extend")
    @RequireCapability("HRM.RECRUITMENT.OFFER.EXTEND")
    public WorkflowResponse extendOffer(Authentication authentication, @PathVariable UUID id,
                                        @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return idem(authentication, OP + ".offer.extend", idempotencyKey, id,
                WorkflowResponse.class, () ->
                        new WorkflowResponse(id, offers.submitForApproval(context(authentication), id)));
    }

    @Operation(operationId = "hrRecruitmentOfferAccept")
    @PostMapping("/offers/{id}/accept")
    @RequireCapability("HRM.RECRUITMENT.OFFER.MANAGE")
    public HrOffer acceptOffer(Authentication authentication, @PathVariable UUID id,
                               @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return offerCommand(authentication, id, idempotencyKey, "accept",
                () -> offers.accept(context(authentication), id));
    }

    @Operation(operationId = "hrRecruitmentOfferDecline")
    @PostMapping("/offers/{id}/decline")
    @RequireCapability("HRM.RECRUITMENT.OFFER.MANAGE")
    public HrOffer declineOffer(Authentication authentication, @PathVariable UUID id,
                                @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return offerCommand(authentication, id, idempotencyKey, "decline",
                () -> offers.decline(context(authentication), id));
    }

    @Operation(operationId = "hrRecruitmentOfferWithdraw")
    @PostMapping("/offers/{id}/withdraw")
    @RequireCapability("HRM.RECRUITMENT.OFFER.MANAGE")
    public HrOffer withdrawOffer(Authentication authentication, @PathVariable UUID id,
                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                 @Valid @RequestBody ReasonRequest request) {
        return idem(authentication, OP + ".offer.withdraw", idempotencyKey, id + "|" + request,
                HrOffer.class, () -> {
                    offers.withdraw(context(authentication), id, request.reasonCode());
                    return requireOffer(authentication, id);
                });
    }

    @Operation(
            operationId = "hrRecruitmentOfferHireConversion",
            summary = "Idempotent hire conversion with replay semantics",
            description = "Converts an accepted offer atomically. Idempotent replay returns HTTP 200 with the original result and replayed=true.")
    @ApiResponse(responseCode = "200",
            description = "Successful conversion or idempotent replay. A replay returns the original references with replayed=true.")
    @PostMapping("/offers/{id}/hire-conversion")
    @RequireCapability("HRM.RECRUITMENT.HIRE.CONVERT")
    public ResponseEntity<HireConversionResult> hireConversion(
            Authentication authentication, @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody HireConversionRequest request) {
        HireConversionCommand command = new HireConversionCommand(
                idempotencyKey,
                request.identityClaims() == null ? List.of() : request.identityClaims().stream()
                        .map(c -> new HireConversionCommand.IdentityClaim(
                                c.identifierType(), c.issuingCountryCode(), c.value()))
                        .toList(),
                request.legalEntityId(),
                request.workerClassificationCode(),
                request.laborJurisdictionCode(),
                request.employmentStartDate(),
                request.allocationPercent(),
                request.positionId(),
                request.contractNumber());

        UUID tenantId = tenant(authentication);
        UUID principalId = principal(authentication);
        String operation = OP + ".offer.hire-conversion";
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(operation, id + "|" + request);

        HireConversionResult result = idempotency.execute(
                tenantId, principalId, operation, idempotencyKey, fingerprint,
                HireConversionResult.class,
                () -> hireConversion.convert(context(authentication), id, command),
                prior -> new HireConversionResult(
                        prior.offerId(), prior.applicationId(), prior.personId(), prior.personReused(),
                        prior.employeeNumber(), prior.employmentId(), prior.assignmentId(),
                        prior.contractId(), prior.compensationPackageId(), prior.onboardingPlanId(), true));
        return ResponseEntity.ok(result);
    }

    // ==================== helpers ====================

    private CommandResponse openingCommand(Authentication auth, UUID id, String key,
                                           String action, String resultingState, Runnable command) {
        return idem(auth, OP + ".opening." + action, key, id, CommandResponse.class, () -> {
            command.run();
            return new CommandResponse(id, resultingState);
        });
    }

    private CommandResponse applicationCommand(Authentication auth, UUID id, String key,
                                               String action, Runnable command) {
        return idem(auth, OP + ".application." + action, key, id, CommandResponse.class, () -> {
            command.run();
            HrRecruitmentApiQueryService.ApplicationView after = queries.application(tenant(auth), id);
            return new CommandResponse(id, after.state());
        });
    }

    private HrOffer offerCommand(Authentication auth, UUID id, String key, String action, Runnable command) {
        return idem(auth, OP + ".offer." + action, key, id, HrOffer.class, () -> {
            command.run();
            return requireOffer(auth, id);
        });
    }

    private HrOffer requireOffer(Authentication auth, UUID id) {
        return offers.find(context(auth), id)
                .orElseThrow(() -> new HrDomainException(HrApiErrorCode.HRM_TENANT_CONTEXT_MISMATCH,
                        "Offer is not visible in the current tenant context"));
    }

    private <T> T idem(Authentication auth, String operation, String key, Object body,
                       Class<T> responseType, Supplier<T> command) {
        return idempotency.execute(
                tenant(auth), principal(auth), operation, key,
                HrmIdempotentCommandExecutor.fingerprint(operation, String.valueOf(body)),
                responseType, command);
    }

    private HrCommandContext context(Authentication auth) {
        return new HrCommandContext(tenant(auth), null, principal(auth), UUID.randomUUID());
    }

    private UUID tenant(Authentication auth) {
        return SecurityContextUtils.tenantId(auth);
    }

    private UUID principal(Authentication auth) {
        return SecurityContextUtils.userId(auth);
    }

    private static void checkIfMatch(String value, long currentVersion) {
        if (value == null || value.isBlank()) return;
        String normalized = value.trim();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2).trim();
        normalized = normalized.replace("\"", "");
        if (!String.valueOf(currentVersion).equals(normalized)) {
            throw new HrDomainException(HrApiErrorCode.HRM_CONCURRENCY_CONFLICT,
                    "If-Match does not match the current resource version");
        }
    }

    public record IdResponse(UUID id) {}
    public record CommandResponse(UUID id, String state) {}
    public record WorkflowResponse(UUID id, UUID workflowInstanceId) {}

    public record CreateOpeningRequest(
            @NotNull UUID jobId,
            UUID jobVersionId,
            @NotNull UUID orgUnitId,
            UUID positionId,
            @Min(1) int requestedHeadcount,
            OffsetDateTime opensAt,
            OffsetDateTime closesAt) {}

    public record ReasonRequest(@NotBlank String reasonCode) {}

    public record CreateCandidateRequest(
            @NotBlank String displayName,
            String email,
            String phone,
            String compensationExpectations) {}

    public record CreateApplicationRequest(@NotNull UUID candidateId, @NotNull UUID jobOpeningId) {}

    public record ScheduleInterviewRequest(
            @NotNull OffsetDateTime plannedAt,
            @NotBlank String mode,
            Integer durationMinutes,
            List<UUID> panelUserIds) {}

    public record InterviewOutcomeRequest(@NotBlank String state, String outcome) {}

    public record InterviewFeedbackRequest(@NotNull JsonNode scorecard) {}

    public record CreateOfferRequest(
            @NotNull JsonNode contractTerms,
            @NotNull JsonNode compensation,
            OffsetDateTime expiresAt) {}

    public record IdentityClaimRequest(
            @NotBlank String identifierType,
            String issuingCountryCode,
            @NotBlank String value) {}

    public record HireConversionRequest(
            List<@Valid IdentityClaimRequest> identityClaims,
            @NotNull UUID legalEntityId,
            @NotBlank String workerClassificationCode,
            String laborJurisdictionCode,
            @NotNull LocalDate employmentStartDate,
            BigDecimal allocationPercent,
            UUID positionId,
            String contractNumber) {}
}
