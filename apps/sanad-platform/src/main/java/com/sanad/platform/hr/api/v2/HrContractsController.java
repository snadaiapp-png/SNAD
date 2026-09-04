package com.sanad.platform.hr.api.v2;

import com.sanad.platform.hr.api.v2.dto.AmendContractRequest;
import com.sanad.platform.hr.api.v2.dto.ActivateContractRequest;
import com.sanad.platform.hr.api.v2.dto.ContractResponse;
import com.sanad.platform.hr.api.v2.dto.CreateContractRequest;
import com.sanad.platform.hr.api.v2.dto.TerminateContractRequest;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.contract.application.EmploymentContractService;
import com.sanad.platform.hr.contract.domain.ContractCommandResult;
import com.sanad.platform.hr.contract.domain.EmploymentContract;
import com.sanad.platform.hr.contract.domain.EmploymentContractVersion;
import com.sanad.platform.hr.contract.infrastructure.JdbcEmploymentContractRepository;
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
 * HRM-G0 / WS5 Task 5 — canonical Contract v2 endpoints (6 operations).
 *
 * <p>Thin typed adapters over the WS6 contract service. Reads gate on
 * HRM.CONTRACT.VIEW, mutations on HRM.CONTRACT.MANAGE (the WS6
 * authorization port enforces the same capability inside the service
 * layer). Jurisdiction-specific terms are governed by the Country Terms
 * Validator — Global Mode refuses them with HRM_COUNTRY_PACK_NOT_CERTIFIED;
 * nothing here hard-codes country law.
 */
@RestController
@RequestMapping("/api/v2/hr")
public class HrContractsController {

    private static final String OPERATION_PREFIX = "hr.v2.contracts";

    private final EmploymentContractService service;
    private final JdbcEmploymentContractRepository repository;
    private final HrmIdempotentCommandExecutor idempotentCommands;

    public HrContractsController(EmploymentContractService service,
                                 JdbcEmploymentContractRepository repository,
                                 HrmIdempotentCommandExecutor idempotentCommands) {
        this.service = service;
        this.repository = repository;
        this.idempotentCommands = idempotentCommands;
    }

    @GetMapping("/contracts")
    @RequireCapability("HRM.CONTRACT.VIEW")
    public List<ContractResponse> list(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return repository.listContracts(tenantId, 100).stream()
                .map(ContractResponse::from)
                .toList();
    }

    @GetMapping("/contracts/{contractId}")
    @RequireCapability("HRM.CONTRACT.VIEW")
    public ContractResponse get(Authentication authentication, @PathVariable UUID contractId) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        EmploymentContract contract = repository.findContract(tenantId, contractId)
                .orElseThrow(() -> new IllegalStateException("HRM_CONTRACT_NOT_FOUND: " + contractId));
        EmploymentContractVersion latest = repository.findVersions(tenantId, contractId).stream()
                .reduce((first, second) -> second)
                .orElse(null);
        return latest == null ? ContractResponse.from(contract) : ContractResponse.from(contract, latest);
    }

    static String mapVersionStatus(EmploymentContractVersion version) {
        return version.status() == null ? null : version.status().name();
    }

    @PostMapping("/contracts")
    @RequireCapability("HRM.CONTRACT.MANAGE")
    public ResponseEntity<ContractResponse> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateContractRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".create",
                String.valueOf(request));
        ContractResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".create", idempotencyKey, fingerprint,
                ContractResponse.class,
                () -> {
                    ContractCommandResult result = service.createDraft(
                            new HrCommandContext(tenantId, request.employmentId(), principalId, null),
                            new EmploymentContractService.CreateContractCommand(
                                    request.employmentId(), request.contractNumber(), request.isPrimary(),
                                    request.contractTermType(), request.contractStartDate(),
                                    request.contractEndDate(), request.effectiveDate(),
                                    request.documentReference(), null));
                    return ContractResponse.fromVersion(result.version(),
                            result.complianceStatus(), result.packCode(), result.packVersion());
                });
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/contracts/{contractId}/amend")
    @RequireCapability("HRM.CONTRACT.MANAGE")
    public ContractResponse amend(
            Authentication authentication, @PathVariable UUID contractId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AmendContractRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".amend",
                contractId + "|" + request);
        return idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".amend", idempotencyKey, fingerprint,
                ContractResponse.class,
                () -> {
                    ContractCommandResult result = service.amend(
                            new HrCommandContext(tenantId, null, principalId, null), contractId,
                            new EmploymentContractService.AmendContractCommand(
                                    request.contractTermType(), request.contractStartDate(),
                                    request.contractEndDate(), request.effectiveDate(),
                                    request.documentReference(), null, request.reasonCode()));
                    return ContractResponse.fromVersion(result.version(),
                            result.complianceStatus(), result.packCode(), result.packVersion());
                });
    }

    @PostMapping("/contracts/{contractId}/activate")
    @RequireCapability("HRM.CONTRACT.MANAGE")
    public ContractResponse activate(
            Authentication authentication, @PathVariable UUID contractId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ActivateContractRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".activate",
                contractId + "|" + request);
        return idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".activate", idempotencyKey, fingerprint,
                ContractResponse.class,
                () -> {
                    ContractCommandResult result = service.activate(
                            new HrCommandContext(tenantId, null, principalId, null), contractId,
                            request.versionNumber(), request.effectiveDate());
                    return ContractResponse.fromVersion(result.version(),
                            result.complianceStatus(), result.packCode(), result.packVersion());
                });
    }

    @PostMapping("/contracts/{contractId}/terminate")
    @RequireCapability("HRM.CONTRACT.MANAGE")
    public ContractResponse terminate(
            Authentication authentication, @PathVariable UUID contractId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TerminateContractRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".terminate",
                contractId + "|" + request);
        return idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".terminate", idempotencyKey, fingerprint,
                ContractResponse.class,
                () -> {
                    ContractCommandResult result = service.terminate(
                            new HrCommandContext(tenantId, null, principalId, null), contractId,
                            request.effectiveDate(), request.reasonCode());
                    return ContractResponse.fromVersion(result.version(),
                            result.complianceStatus(), result.packCode(), result.packVersion());
                });
    }
}
