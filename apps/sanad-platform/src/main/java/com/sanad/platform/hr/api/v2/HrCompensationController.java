package com.sanad.platform.hr.api.v2;

import com.sanad.platform.hr.api.v2.dto.CompensationPackageResponse;
import com.sanad.platform.hr.api.v2.dto.CreateCompensationRequest;
import com.sanad.platform.hr.api.v2.dto.EndCompensationRequest;
import com.sanad.platform.hr.api.v2.dto.ReviseCompensationRequest;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.compensation.application.CompensationService;
import com.sanad.platform.hr.compensation.application.HrCompensationComponentMapper;
import com.sanad.platform.hr.compensation.domain.CompensationPackage;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 5 — canonical Compensation v2 endpoints (5 operations).
 *
 * <p>Restricted-surface discipline: the LIST projection is amount-free
 * (directory-safe); amounts are returned ONLY by the single-package GET,
 * which passes the HRM.COMPENSATION.VIEW capability gate, the WS6
 * authorization port AND the fail-closed sensitive-read audit. Change
 * events never carry amounts (WS6 contract).
 */
@RestController
@RequestMapping("/api/v2/hr")
public class HrCompensationController {

    private static final String OPERATION_PREFIX = "hr.v2.compensation-packages";

    private final CompensationService service;
    private final HrmIdempotentCommandExecutor idempotentCommands;

    public HrCompensationController(CompensationService service,
                                    HrmIdempotentCommandExecutor idempotentCommands) {
        this.service = service;
        this.idempotentCommands = idempotentCommands;
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrCompensationList")
    @GetMapping("/compensation-packages")
    @RequireCapability("HRM.COMPENSATION.VIEW")
    public List<CompensationPackageResponse> list(
            Authentication authentication, @RequestParam("employmentId") UUID employmentId) {
        // Amount-free history projection — safe for directory contexts.
        return service.readHistoryWithoutAmounts(
                        new HrCommandContext(SecurityContextUtils.tenantId(authentication), employmentId,
                                SecurityContextUtils.userId(authentication), null), employmentId)
                .stream()
                .map(CompensationPackageResponse::withoutAmounts)
                .toList();
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrCompensationGet")
    @GetMapping("/compensation-packages/{packageId}")
    @RequireCapability("HRM.COMPENSATION.VIEW")
    public CompensationPackageResponse get(Authentication authentication, @PathVariable UUID packageId) {
        CompensationPackage pkg = service.readPackageWithAudit(
                new HrCommandContext(SecurityContextUtils.tenantId(authentication), null,
                        SecurityContextUtils.userId(authentication), null), packageId);
        return CompensationPackageResponse.withAmounts(pkg);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrCompensationCreate")
    @PostMapping("/compensation-packages")
    @RequireCapability("HRM.COMPENSATION.MANAGE")
    public ResponseEntity<CompensationPackageResponse> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateCompensationRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".create",
                String.valueOf(request));
        CompensationPackageResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".create", idempotencyKey, fingerprint,
                CompensationPackageResponse.class,
                () -> {
                    CompensationPackage pkg = service.createPackage(
                            new HrCommandContext(tenantId, request.employmentId(), principalId, null),
                            new CompensationService.CreateCompensationCommand(request.employmentId(), request.currencyCode(),
                                    request.payFrequency(), request.effectiveFrom(),
                                    HrCompensationComponentMapper.toDomain(request.components())));
                    // Created amounts are echoed only to a MANAGE-capable caller
                    // through the same audited read discipline.
                    return CompensationPackageResponse.withoutAmounts(pkg);
                });
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrCompensationRevise")
    @PostMapping("/compensation-packages/{packageId}/revise")
    @RequireCapability("HRM.COMPENSATION.MANAGE")
    public CompensationPackageResponse revise(
            Authentication authentication, @PathVariable UUID packageId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReviseCompensationRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".revise",
                packageId + "|" + request);
        return idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".revise", idempotencyKey, fingerprint,
                CompensationPackageResponse.class,
                () -> {
                    CompensationPackage pkg = service.revisePackage(
                            new HrCommandContext(tenantId, null, principalId, null), packageId,
                            new CompensationService.ReviseCompensationCommand(request.currencyCode(), request.payFrequency(),
                                    request.effectiveFrom(),
                                    HrCompensationComponentMapper.toDomain(request.components()),
                                    request.reasonCode()));
                    return CompensationPackageResponse.withoutAmounts(pkg);
                });
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrCompensationEnd")
    @PostMapping("/compensation-packages/{packageId}/end")
    @RequireCapability("HRM.COMPENSATION.MANAGE")
    public CompensationPackageResponse end(
            Authentication authentication, @PathVariable UUID packageId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EndCompensationRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".end",
                packageId + "|" + request);
        return idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".end", idempotencyKey, fingerprint,
                CompensationPackageResponse.class,
                () -> {
                    CompensationPackage pkg = service.endPackage(
                            new HrCommandContext(tenantId, null, principalId, null), packageId,
                            request.effectiveTo(), "WS5.COMPENSATION.END");
                    return CompensationPackageResponse.withoutAmounts(pkg);
                });
    }
}
