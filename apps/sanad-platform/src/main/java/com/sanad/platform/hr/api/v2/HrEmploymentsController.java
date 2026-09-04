package com.sanad.platform.hr.api.v2;

import com.sanad.platform.hr.api.v2.dto.CreateEmploymentRequest;
import com.sanad.platform.hr.api.v2.dto.EmploymentResponse;
import com.sanad.platform.hr.api.v2.dto.LifecycleCommandRequest;
import com.sanad.platform.hr.api.v2.dto.LifecycleCommandResponse;
import com.sanad.platform.hr.employment.HrEmploymentV2Service;
import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 3 — canonical Employment v2 endpoints (11 operations).
 *
 * <p>Thin typed adapters over the certified WS2 application services:
 * coarse capability gate → canonical resource resolution → optimistic
 * concurrency check → idempotent command execution → typed response.
 * Tenant identity comes exclusively from the security context; tenant
 * isolation is additionally enforced by fail-closed RLS underneath.
 */
@RestController
@RequestMapping("/api/v2/hr")
public class HrEmploymentsController {

    private static final String OPERATION_PREFIX = "hr.v2.employments";

    private final HrEmploymentV2Service service;
    private final HrmIdempotentCommandExecutor idempotentCommands;

    public HrEmploymentsController(HrEmploymentV2Service service,
                                   HrmIdempotentCommandExecutor idempotentCommands) {
        this.service = service;
        this.idempotentCommands = idempotentCommands;
    }

    @GetMapping("/employments")
    @RequireCapability("HRM.EMPLOYEE.VIEW")
    public List<EmploymentResponse> list(Authentication authentication) {
        return service.list(SecurityContextUtils.tenantId(authentication));
    }

    @GetMapping("/employments/{employmentId}")
    @RequireCapability("HRM.EMPLOYEE.VIEW")
    public EmploymentResponse get(Authentication authentication, @PathVariable UUID employmentId) {
        return service.get(SecurityContextUtils.tenantId(authentication), employmentId);
    }

    @PostMapping("/employments")
    @RequireCapability("HRM.EMPLOYEE.CREATE")
    public ResponseEntity<EmploymentResponse> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateEmploymentRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".create",
                String.valueOf(request));
        EmploymentResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".create", idempotencyKey, fingerprint,
                EmploymentResponse.class, () -> service.create(tenantId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/employments/{employmentId}/submit-onboarding")
    @RequireCapability("HRM.EMPLOYEE.UPDATE")
    public LifecycleCommandResponse submitOnboarding(
            Authentication authentication, @PathVariable UUID employmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LifecycleCommandRequest request) {
        return lifecycle(authentication, employmentId, "submit-onboarding", idempotencyKey, request,
                (t, id, v, eff, reason) -> service.submitOnboarding(t, id, v, eff, reason));
    }

    @PostMapping("/employments/{employmentId}/activate")
    @RequireCapability("HRM.EMPLOYEE.UPDATE")
    public LifecycleCommandResponse activate(
            Authentication authentication, @PathVariable UUID employmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LifecycleCommandRequest request) {
        return lifecycle(authentication, employmentId, "activate", idempotencyKey, request,
                (t, id, v, eff, reason) -> service.activate(t, id, v, eff, reason));
    }

    @PostMapping("/employments/{employmentId}/start-leave")
    @RequireCapability("HRM.EMPLOYEE.UPDATE")
    public LifecycleCommandResponse startLeave(
            Authentication authentication, @PathVariable UUID employmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LifecycleCommandRequest request) {
        return lifecycle(authentication, employmentId, "start-leave", idempotencyKey, request,
                (t, id, v, eff, reason) -> service.startLeave(t, id, v, eff, reason));
    }

    @PostMapping("/employments/{employmentId}/return-from-leave")
    @RequireCapability("HRM.EMPLOYEE.UPDATE")
    public LifecycleCommandResponse returnFromLeave(
            Authentication authentication, @PathVariable UUID employmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LifecycleCommandRequest request) {
        return lifecycle(authentication, employmentId, "return-from-leave", idempotencyKey, request,
                (t, id, v, eff, reason) -> service.returnFromLeave(t, id, v, eff, reason));
    }

    @PostMapping("/employments/{employmentId}/suspend")
    @RequireCapability("HRM.EMPLOYEE.UPDATE")
    public LifecycleCommandResponse suspend(
            Authentication authentication, @PathVariable UUID employmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LifecycleCommandRequest request) {
        return lifecycle(authentication, employmentId, "suspend", idempotencyKey, request,
                (t, id, v, eff, reason) -> service.suspend(t, id, v, eff, reason));
    }

    @PostMapping("/employments/{employmentId}/reinstate")
    @RequireCapability("HRM.EMPLOYEE.UPDATE")
    public LifecycleCommandResponse reinstate(
            Authentication authentication, @PathVariable UUID employmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LifecycleCommandRequest request) {
        return lifecycle(authentication, employmentId, "reinstate", idempotencyKey, request,
                (t, id, v, eff, reason) -> service.reinstate(t, id, v, eff, reason));
    }

    @PostMapping("/employments/{employmentId}/terminate")
    @RequireCapability("HRM.EMPLOYEE.TERMINATE")
    public LifecycleCommandResponse terminate(
            Authentication authentication, @PathVariable UUID employmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LifecycleCommandRequest request) {
        return lifecycle(authentication, employmentId, "terminate", idempotencyKey, request,
                (t, id, v, eff, reason) -> service.terminate(t, id, v, eff, reason));
    }

    @PostMapping("/employments/{employmentId}/void")
    @RequireCapability("HRM.EMPLOYEE.TERMINATE")
    public LifecycleCommandResponse voidEmployment(
            Authentication authentication, @PathVariable UUID employmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LifecycleCommandRequest request) {
        return lifecycle(authentication, employmentId, "void", idempotencyKey, request,
                (t, id, v, eff, reason) -> service.voidEmployment(t, id, v, eff, reason));
    }

    private LifecycleCommandResponse lifecycle(
            Authentication authentication, UUID employmentId, String operation, String idempotencyKey,
            LifecycleCommandRequest request,
            LifecycleInvocation invocation) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String operationCode = OPERATION_PREFIX + "." + operation;
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(operationCode,
                employmentId + "|" + request);
        HrEmploymentV2Service.LifecycleOutcome outcome = idempotentCommands.execute(
                tenantId, principalId, operationCode, idempotencyKey, fingerprint,
                HrEmploymentV2Service.LifecycleOutcome.class,
                () -> invocation.invoke(tenantId, employmentId, request.expectedVersion(),
                        request.effectiveDate(), request.reasonCode()));
        return new LifecycleCommandResponse(outcome.employmentId(), outcome.previousStatus(),
                outcome.newStatus(), outcome.closedPeriodId(), outcome.newPeriodId());
    }

    @FunctionalInterface
    private interface LifecycleInvocation {
        HrEmploymentV2Service.LifecycleOutcome invoke(UUID tenantId, UUID employmentId, Long expectedVersion,
                                                      java.time.LocalDate effectiveDate, String reasonCode);
    }
}
