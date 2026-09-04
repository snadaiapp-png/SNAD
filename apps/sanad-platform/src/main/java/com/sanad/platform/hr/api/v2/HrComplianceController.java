package com.sanad.platform.hr.api.v2;

import com.sanad.platform.hr.api.v2.dto.AuditEntryResponse;
import com.sanad.platform.hr.api.v2.dto.ComplianceContextResponse;
import com.sanad.platform.hr.api.v2.dto.CreateOverrideRequest;
import com.sanad.platform.hr.api.v2.dto.OverrideDecisionRequest;
import com.sanad.platform.hr.api.v2.dto.OverrideRequestResponse;
import com.sanad.platform.hr.audit.HrAuditReadService;
import com.sanad.platform.hr.compliance.application.ComplianceOverrideService;
import com.sanad.platform.hr.compliance.application.HrComplianceContextService;
import com.sanad.platform.hr.compliance.domain.ComplianceResource;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.compliance.infrastructure.JdbcComplianceOverrideRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 5 — canonical Compliance and Audit v2 endpoints
 * (7 operations: 6 compliance + 1 audit read).
 *
 * <p>Override HTTP semantics follow the WS5 plan: a valid
 * controlled-exception request is accepted (HTTP 201); hard statutory
 * blocks have NO override path and surface as compliance decisions on the
 * guarded operation instead. Approval requires the dedicated
 * HRM.COMPLIANCE_OVERRIDE.APPROVE capability plus a different approver and
 * current-rule revalidation — enforced by the WS3 service, never by this
 * controller. The audit read is capability-gated and returns identifiers
 * and metadata only.
 */
@RestController
@RequestMapping("/api/v2/hr")
public class HrComplianceController {

    private static final String OPERATION_PREFIX = "hr.v2.compliance";

    private final HrComplianceContextService policyContextService;
    private final ComplianceOverrideService overrideService;
    private final JdbcComplianceOverrideRepository overrideRepository;
    private final HrAuditReadService auditReadService;
    private final HrmIdempotentCommandExecutor idempotentCommands;

    public HrComplianceController(HrComplianceContextService policyContextService,
                                  ComplianceOverrideService overrideService,
                                  JdbcComplianceOverrideRepository overrideRepository,
                                  HrAuditReadService auditReadService,
                                  HrmIdempotentCommandExecutor idempotentCommands) {
        this.policyContextService = policyContextService;
        this.overrideService = overrideService;
        this.overrideRepository = overrideRepository;
        this.auditReadService = auditReadService;
        this.idempotentCommands = idempotentCommands;
    }

    // ==================== COMPLIANCE CONTEXT ====================

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrComplianceContext")
    @GetMapping("/compliance/context")
    @RequireCapability("HRM.EMPLOYEE.VIEW")
    public ComplianceContextResponse context(
            Authentication authentication,
            @RequestParam("employmentId") UUID employmentId,
            @RequestParam(value = "effectiveDate", required = false) LocalDate effectiveDate) {
        // Policy MODE metadata only — never employee PII; resolves Global Mode
        // metadata for uncertified jurisdictions.
        return ComplianceContextResponse.from(policyContextService.resolve(
                SecurityContextUtils.tenantId(authentication), employmentId,
                effectiveDate == null ? LocalDate.now() : effectiveDate));
    }

    // ==================== OVERRIDE WORKFLOW ====================

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrComplianceListOverrides")
    @GetMapping("/compliance/overrides")
    @RequireCapability("HRM.COMPLIANCE_OVERRIDE.REQUEST")
    public List<OverrideRequestResponse> listOverrides(Authentication authentication) {
        return overrideRepository.listByTenant(SecurityContextUtils.tenantId(authentication), 100).stream()
                .map(OverrideRequestResponse::from)
                .toList();
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrComplianceRequestOverride")
    @PostMapping("/compliance/overrides")
    @RequireCapability("HRM.COMPLIANCE_OVERRIDE.REQUEST")
    public ResponseEntity<OverrideRequestResponse> requestOverride(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOverrideRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".overrides.request",
                String.valueOf(request));
        OverrideRequestResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".overrides.request", idempotencyKey, fingerprint,
                OverrideRequestResponse.class,
                () -> {
                    UUID requestId = overrideService.requestOverride(
                            new HrCommandContext(tenantId, null, principalId, null),
                            request.complianceRuleId(),
                            new ComplianceResource(request.resourceType(), request.resourceId()),
                            request.justification(), request.evidenceReference(), null, null,
                            request.validFrom(), request.validUntil());
                    return OverrideRequestResponse.from(
                            overrideRepository.findById(tenantId, requestId).orElseThrow());
                });
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrComplianceApprove")
    @PostMapping("/compliance/overrides/{overrideId}/approve")
    @RequireCapability("HRM.COMPLIANCE_OVERRIDE.APPROVE")
    public OverrideRequestResponse approve(
            Authentication authentication, @PathVariable UUID overrideId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OverrideDecisionRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".overrides.approve",
                overrideId + "|" + request);
        return idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".overrides.approve", idempotencyKey, fingerprint,
                OverrideRequestResponse.class,
                () -> OverrideRequestResponse.from(
                        overrideService.approve(tenantId, overrideId, principalId, request.comment())));
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrComplianceReject")
    @PostMapping("/compliance/overrides/{overrideId}/reject")
    @RequireCapability("HRM.COMPLIANCE_OVERRIDE.APPROVE")
    public OverrideRequestResponse reject(
            Authentication authentication, @PathVariable UUID overrideId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OverrideDecisionRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".overrides.reject",
                overrideId + "|" + request);
        return idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".overrides.reject", idempotencyKey, fingerprint,
                OverrideRequestResponse.class,
                () -> OverrideRequestResponse.from(
                        overrideService.reject(tenantId, overrideId, principalId, request.comment())));
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrComplianceRevoke")
    @PostMapping("/compliance/overrides/{overrideId}/revoke")
    @RequireCapability("HRM.COMPLIANCE_OVERRIDE.APPROVE")
    public OverrideRequestResponse revoke(
            Authentication authentication, @PathVariable UUID overrideId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OverrideDecisionRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".overrides.revoke",
                overrideId + "|" + request);
        return idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".overrides.revoke", idempotencyKey, fingerprint,
                OverrideRequestResponse.class,
                () -> OverrideRequestResponse.from(
                        overrideService.revoke(tenantId, overrideId, principalId, request.comment())));
    }

    // ==================== AUDIT ====================

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrComplianceAudit")
    @GetMapping("/audit")
    @RequireCapability("HRM.AUDIT.VIEW")
    public List<AuditEntryResponse> audit(
            Authentication authentication,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return auditReadService.findRecent(SecurityContextUtils.tenantId(authentication), limit, resourceType)
                .stream()
                .map(AuditEntryResponse::from)
                .toList();
    }
}
