package com.sanad.platform.hr.api.v2;

import com.sanad.platform.hr.api.v2.dto.AssignmentResponse;
import com.sanad.platform.hr.api.v2.dto.ChangeManagerRequest;
import com.sanad.platform.hr.api.v2.dto.CreateAssignmentRequest;
import com.sanad.platform.hr.api.v2.dto.LifecycleCommandRequest;
import com.sanad.platform.hr.api.v2.dto.TransferRequest;
import com.sanad.platform.hr.assignment.domain.HrAssignment;
import com.sanad.platform.hr.assignment.infrastructure.JdbcHrAssignmentRepository;
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
 * HRM-G0 / WS5 Task 4 — canonical Assignment v2 endpoints (6 operations).
 *
 * <p>Thin typed adapters over the WS2 atomic assignment persistence:
 * coarse capability gate → canonical resource resolution → optimistic
 * concurrency check → idempotent command execution → typed response.
 * Reads gate on HRM.ASSIGNMENT.VIEW; every mutation gates on
 * HRM.ASSIGNMENT.MANAGE. Effective dates and expected versions are always
 * client-supplied — the server never defaults legally significant values.
 *
 * <p>end / change-manager / transfer are period-aware: supersede semantics
 * close the current open period and create the successor atomically;
 * historical placement is never overwritten.
 */
@RestController
@RequestMapping("/api/v2/hr")
public class HrAssignmentsController {

    private static final String OPERATION_PREFIX = "hr.v2.assignments";

    private final HrAssignmentV2Service service;
    private final HrmIdempotentCommandExecutor idempotentCommands;

    public HrAssignmentsController(HrAssignmentV2Service service,
                                   HrmIdempotentCommandExecutor idempotentCommands) {
        this.service = service;
        this.idempotentCommands = idempotentCommands;
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrAssignmentsList")
    @GetMapping("/assignments")
    @RequireCapability("HRM.ASSIGNMENT.VIEW")
    public List<AssignmentResponse> list(Authentication authentication) {
        return service.list(SecurityContextUtils.tenantId(authentication)).stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrAssignmentsGet")
    @GetMapping("/assignments/{assignmentId}")
    @RequireCapability("HRM.ASSIGNMENT.VIEW")
    public AssignmentResponse get(Authentication authentication, @PathVariable UUID assignmentId) {
        return AssignmentResponse.from(
                service.get(SecurityContextUtils.tenantId(authentication), assignmentId));
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrAssignmentsCreate")
    @PostMapping("/assignments")
    @RequireCapability("HRM.ASSIGNMENT.MANAGE")
    public ResponseEntity<AssignmentResponse> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateAssignmentRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".create",
                request + "|" + tenantId);
        AssignmentResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".create", idempotencyKey, fingerprint,
                AssignmentResponse.class, () -> AssignmentResponse.from(service.create(tenantId, request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrAssignmentsEnd")
    @PostMapping("/assignments/{assignmentId}/end")
    @RequireCapability("HRM.ASSIGNMENT.MANAGE")
    public AssignmentResponse end(
            Authentication authentication, @PathVariable UUID assignmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LifecycleCommandRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".end",
                assignmentId + "|" + request);
        return idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".end", idempotencyKey, fingerprint,
                AssignmentResponse.class,
                () -> AssignmentResponse.from(service.end(tenantId, assignmentId,
                        request.effectiveDate(), request.expectedVersion())));
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrAssignmentsChangeManager")
    @PostMapping("/assignments/{assignmentId}/change-manager")
    @RequireCapability("HRM.ASSIGNMENT.MANAGE")
    public AssignmentResponse changeManager(
            Authentication authentication, @PathVariable UUID assignmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ChangeManagerRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".change-manager",
                assignmentId + "|" + request);
        return idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".change-manager", idempotencyKey, fingerprint,
                AssignmentResponse.class,
                () -> AssignmentResponse.from(service.changeManager(tenantId, assignmentId,
                        request.reportsToAssignmentId(), request.effectiveDate(), request.expectedVersion())));
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrAssignmentsTransfer")
    @PostMapping("/assignments/{assignmentId}/transfer")
    @RequireCapability("HRM.ASSIGNMENT.MANAGE")
    public AssignmentResponse transfer(
            Authentication authentication, @PathVariable UUID assignmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".transfer",
                assignmentId + "|" + request);
        return idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".transfer", idempotencyKey, fingerprint,
                AssignmentResponse.class,
                () -> AssignmentResponse.from(service.transfer(tenantId, assignmentId,
                        request.orgUnitId(), request.positionId(), request.reportsToAssignmentId(),
                        request.effectiveDate(), request.expectedVersion())));
    }
}
