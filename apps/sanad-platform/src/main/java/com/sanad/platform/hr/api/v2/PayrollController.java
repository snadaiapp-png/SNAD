package com.sanad.platform.hr.api.v2;

import com.sanad.platform.hr.audit.HrAuthenticatedContext;
import com.sanad.platform.hr.payroll.application.PayrollAccountingExportPort;
import com.sanad.platform.hr.payroll.application.PayrollApiService;
import com.sanad.platform.hr.payroll.application.PayrollLifecycle;
import com.sanad.platform.hr.payroll.application.PayrollLifecycleService;
import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * G4-T8 governed payroll API.
 *
 * <p>Tenant/user identity is derived exclusively from the authenticated
 * principal. No tenant identifier is accepted from request paths, bodies or
 * headers. Mutations require Idempotency-Key and lifecycle/version semantics
 * remain delegated to the G4-T5/T6 application boundary.</p>
 */
@RestController
@RequestMapping("/api/v2/hr/payroll")
public class PayrollController {

    private static final String OP = "hr.v2.payroll";

    private final PayrollApiService service;
    private final HrmIdempotentCommandExecutor idempotentCommands;

    public PayrollController(PayrollApiService service,
                             HrmIdempotentCommandExecutor idempotentCommands) {
        this.service = service;
        this.idempotentCommands = idempotentCommands;
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPayrollCreateRun")
    @PostMapping("/runs")
    @RequireCapability("HRM.PAYROLL.CALCULATE")
    public ResponseEntity<PayrollApiService.RunView> createRun(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRunRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OP + ".createRun", request.toString());
        PayrollApiService.RunView response = idempotentCommands.execute(
                tenantId, userId, OP + ".createRun", idempotencyKey, fingerprint,
                PayrollApiService.RunView.class,
                () -> service.createRun(tenantId, userId, request.toCommand()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPayrollListRuns")
    @GetMapping("/runs")
    @RequireCapability("HRM.PAYROLL.VIEW")
    public List<PayrollApiService.RunView> listRuns(Authentication authentication) {
        return service.listRuns(SecurityContextUtils.tenantId(authentication));
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPayrollGetRun")
    @GetMapping("/runs/{runId}")
    @RequireCapability("HRM.PAYROLL.VIEW")
    public PayrollApiService.RunView getRun(Authentication authentication, @PathVariable UUID runId) {
        return service.getRun(SecurityContextUtils.tenantId(authentication), runId);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPayrollCalculate")
    @PostMapping("/runs/{runId}/calculate")
    @RequireCapability("HRM.PAYROLL.CALCULATE")
    public PayrollLifecycleService.TransitionResult calculate(
            Authentication authentication,
            @PathVariable UUID runId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody VersionedMutationRequest request) {
        HrAuthenticatedContext actor = actor(authentication);
        String fingerprint = fingerprint("calculate", runId, request);
        return service.transition(actor, runId, PayrollLifecycle.CALCULATED,
                request.expectedVersion(), request.reason(), idempotencyKey, fingerprint);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPayrollRecalculate")
    @PostMapping("/runs/{runId}/recalculate")
    @RequireCapability("HRM.PAYROLL.CALCULATE")
    public PayrollLifecycleService.TransitionResult recalculate(
            Authentication authentication,
            @PathVariable UUID runId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody VersionedMutationRequest request) {
        HrAuthenticatedContext actor = actor(authentication);
        String fingerprint = fingerprint("recalculate", runId, request);
        return service.recalculate(actor, runId, request.expectedVersion(),
                request.reason(), idempotencyKey, fingerprint);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPayrollListItems")
    @GetMapping("/runs/{runId}/items")
    @RequireCapability("HRM.PAYROLL.VIEW")
    public List<PayrollApiService.ItemView> listItems(
            Authentication authentication, @PathVariable UUID runId) {
        return service.listItems(SecurityContextUtils.tenantId(authentication), runId);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPayrollGetItem")
    @GetMapping("/runs/{runId}/items/{itemId}")
    @RequireCapability("HRM.PAYROLL.VIEW")
    public PayrollApiService.ItemView getItem(
            Authentication authentication, @PathVariable UUID runId, @PathVariable UUID itemId) {
        return service.getItem(SecurityContextUtils.tenantId(authentication), runId, itemId);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPayrollReview")
    @PostMapping("/runs/{runId}/review")
    @RequireCapability("HRM.PAYROLL.REVIEW")
    public PayrollLifecycleService.TransitionResult review(
            Authentication authentication,
            @PathVariable UUID runId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody VersionedMutationRequest request) {
        HrAuthenticatedContext actor = actor(authentication);
        return service.transition(actor, runId, PayrollLifecycle.REVIEWED,
                request.expectedVersion(), request.reason(), idempotencyKey,
                fingerprint("review", runId, request));
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPayrollApprove")
    @PostMapping("/runs/{runId}/approve")
    @RequireCapability("HRM.PAYROLL.APPROVE")
    public PayrollLifecycleService.TransitionResult approve(
            Authentication authentication,
            @PathVariable UUID runId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody VersionedMutationRequest request) {
        HrAuthenticatedContext actor = actor(authentication);
        return service.transition(actor, runId, PayrollLifecycle.APPROVED,
                request.expectedVersion(), request.reason(), idempotencyKey,
                fingerprint("approve", runId, request));
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPayrollExport")
    @PostMapping("/runs/{runId}/export")
    @RequireCapability("HRM.PAYROLL.EXPORT")
    public PayrollAccountingExportPort.ExportReceipt export(
            Authentication authentication,
            @PathVariable UUID runId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExportRequest request) {
        return service.export(actor(authentication), runId, request.expectedVersion(), idempotencyKey);
    }

    private static HrAuthenticatedContext actor(Authentication authentication) {
        return new HrAuthenticatedContext(
                SecurityContextUtils.tenantId(authentication),
                SecurityContextUtils.userId(authentication),
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    private static String fingerprint(String operation, UUID runId, Object request) {
        return HrmIdempotentCommandExecutor.fingerprint(
                OP + "." + operation, runId + "|" + request);
    }

    public record CreateRunRequest(
            @NotNull UUID legalEntityId,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd,
            @NotBlank String currencyCode,
            @NotNull Instant sourceCutoffAt) {
        PayrollApiService.CreateRun toCommand() {
            return new PayrollApiService.CreateRun(
                    legalEntityId, periodStart, periodEnd, currencyCode, sourceCutoffAt);
        }
    }

    public record VersionedMutationRequest(
            @PositiveOrZero long expectedVersion,
            @NotBlank String reason) {}

    public record ExportRequest(@PositiveOrZero long expectedVersion) {}
}
