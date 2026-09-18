package com.sanad.platform.hr.api.v2.onboarding;

import com.sanad.platform.hr.api.v2.HrApiErrorCode;
import com.sanad.platform.hr.api.v2.HrDomainException;
import com.sanad.platform.hr.api.v2.HrmIdempotentCommandExecutor;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.onboarding.HrOnboardingService;
import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

/** HRM-G1 T10 — canonical onboarding v2 HTTP API (design §13). */
@RestController
@RequestMapping("/api/v2/hr/onboarding")
public class HrOnboardingV2Controller {

    private static final String OP = "hr.v2.onboarding";

    private final HrOnboardingService service;
    private final HrOnboardingApiQueryService queries;
    private final HrmIdempotentCommandExecutor idempotency;

    public HrOnboardingV2Controller(HrOnboardingService service,
                                    HrOnboardingApiQueryService queries,
                                    HrmIdempotentCommandExecutor idempotency) {
        this.service = service;
        this.queries = queries;
        this.idempotency = idempotency;
    }

    @Operation(operationId = "hrOnboardingPlanList")
    @GetMapping("/plans")
    @RequireCapability("HRM.ONBOARDING.PLAN.MANAGE")
    public HrOnboardingApiQueryService.CursorPage<HrOnboardingApiQueryService.PlanSummary> listPlans(
            Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return queries.plans(tenant(authentication), cursor, limit);
    }

    @Operation(operationId = "hrOnboardingPlanGet")
    @GetMapping("/plans/{id}")
    @RequireCapability("HRM.ONBOARDING.PLAN.MANAGE")
    public HrOnboardingApiQueryService.PlanDetail getPlan(
            Authentication authentication, @PathVariable UUID id) {
        return queries.plan(tenant(authentication), id);
    }

    @Operation(operationId = "hrOnboardingPlanCreate")
    @PostMapping("/plans")
    @RequireCapability("HRM.ONBOARDING.PLAN.MANAGE")
    public ResponseEntity<IdResponse> createPlan(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePlanRequest request) {
        IdResponse result = idem(authentication, OP + ".plan.create", idempotencyKey, request,
                IdResponse.class, () -> new IdResponse(service.createPlan(
                        context(authentication), request.employmentId(), request.templateCode(),
                        request.workflowLinked())));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(operationId = "hrOnboardingTaskComplete")
    @PostMapping("/plans/{id}/tasks/{taskId}/complete")
    @RequireCapability("HRM.ONBOARDING.TASK.COMPLETE")
    public CommandResponse completeTask(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireTaskInPlan(authentication, id, taskId);
        return idem(authentication, OP + ".task.complete", idempotencyKey, id + "|" + taskId,
                CommandResponse.class, () -> {
                    service.completeTask(context(authentication), taskId, requestId(idempotencyKey));
                    return new CommandResponse(taskId, "DONE");
                });
    }

    @Operation(operationId = "hrOnboardingTaskWaive")
    @PostMapping("/plans/{id}/tasks/{taskId}/waive")
    @RequireCapability("HRM.ONBOARDING.TASK.WAIVE")
    public CommandResponse waiveTask(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WaiveTaskRequest request) {
        requireTaskInPlan(authentication, id, taskId);
        if (request.reasonCode() == null || request.reasonCode().isBlank()) {
            throw new HrDomainException(HrApiErrorCode.HRM_WAIVER_REASON_REQUIRED,
                    "A registered waiver reason is required");
        }
        return idem(authentication, OP + ".task.waive", idempotencyKey,
                id + "|" + taskId + "|" + request.reasonCode(), CommandResponse.class, () -> {
                    service.waiveTask(context(authentication), taskId,
                            request.reasonCode(), requestId(idempotencyKey));
                    return new CommandResponse(taskId, "WAIVED");
                });
    }

    @Operation(operationId = "hrOnboardingPlanCancel")
    @PostMapping("/plans/{id}/cancel")
    @RequireCapability("HRM.ONBOARDING.PLAN.MANAGE")
    public CommandResponse cancelPlan(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CancelPlanRequest request) {
        return idem(authentication, OP + ".plan.cancel", idempotencyKey, id + "|" + request,
                CommandResponse.class, () -> {
                    service.cancelPlan(context(authentication), id,
                            request.reasonCode(), requestId(idempotencyKey));
                    return new CommandResponse(id, "CANCELLED");
                });
    }

    private void requireTaskInPlan(Authentication auth, UUID planId, UUID taskId) {
        boolean found = queries.plan(tenant(auth), planId).tasks().stream()
                .anyMatch(task -> task.id().equals(taskId));
        if (!found) {
            throw new HrDomainException(HrApiErrorCode.HRM_TENANT_CONTEXT_MISMATCH,
                    "Task is not visible under the requested onboarding plan");
        }
    }

    private <T> T idem(Authentication auth, String operation, String key, Object body,
                       Class<T> type, Supplier<T> command) {
        return idempotency.execute(
                tenant(auth), principal(auth), operation, key,
                HrmIdempotentCommandExecutor.fingerprint(operation, String.valueOf(body)),
                type, command);
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

    private static UUID requestId(String idempotencyKey) {
        try {
            return UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException notUuid) {
            return UUID.nameUUIDFromBytes(("hrm-t10:" + idempotencyKey)
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    public record IdResponse(UUID id) {}
    public record CommandResponse(UUID id, String state) {}

    public record CreatePlanRequest(
            @NotNull UUID employmentId,
            @NotBlank String templateCode,
            boolean workflowLinked) {}

    public record WaiveTaskRequest(@NotBlank String reasonCode) {}
    public record CancelPlanRequest(@NotBlank String reasonCode) {}
}
