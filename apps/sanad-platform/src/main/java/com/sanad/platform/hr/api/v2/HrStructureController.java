package com.sanad.platform.hr.api.v2;

import com.sanad.platform.hr.api.v2.dto.CreateJobRequest;
import com.sanad.platform.hr.api.v2.dto.CreateOrgUnitRequest;
import com.sanad.platform.hr.api.v2.dto.CreatePositionRequest;
import com.sanad.platform.hr.api.v2.dto.JobResponse;
import com.sanad.platform.hr.api.v2.dto.OrgUnitResponse;
import com.sanad.platform.hr.api.v2.dto.PositionResponse;
import com.sanad.platform.hr.api.v2.dto.ReviseJobRequest;
import com.sanad.platform.hr.api.v2.dto.ReviseOrgUnitRequest;
import com.sanad.platform.hr.api.v2.dto.RevisePositionRequest;
import com.sanad.platform.hr.api.v2.dto.StaffabilityResponse;
import com.sanad.platform.hr.structure.application.HrStructureV2Service;
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
 * HRM-G0 / WS5 Task 4 — canonical Structure v2 endpoints (14 operations:
 * 4 Org Unit + 4 Job + 6 Position).
 *
 * <p>Thin typed adapters over the WS2 structure persistence: coarse
 * capability gate → canonical resource resolution → atomic effective-dated
 * mutation → typed response. Reads gate on HRM.ORG_STRUCTURE.VIEW;
 * mutations on HRM.ORG_STRUCTURE.MANAGE. Revisions create new version rows
 * (history preserved); position freeze/close act on staffability only.
 */
@RestController
@RequestMapping("/api/v2/hr")
public class HrStructureController {

    private static final String OPERATION_PREFIX = "hr.v2.structure";

    private final HrStructureV2Service service;
    private final HrmIdempotentCommandExecutor idempotentCommands;

    public HrStructureController(HrStructureV2Service service,
                                 HrmIdempotentCommandExecutor idempotentCommands) {
        this.service = service;
        this.idempotentCommands = idempotentCommands;
    }

    // ==================== ORG UNITS (4) ====================

    @GetMapping("/org-units")
    @RequireCapability("HRM.ORG_STRUCTURE.VIEW")
    public List<OrgUnitResponse> listOrgUnits(Authentication authentication) {
        return service.listOrgUnits(SecurityContextUtils.tenantId(authentication));
    }

    @GetMapping("/org-units/{orgUnitId}")
    @RequireCapability("HRM.ORG_STRUCTURE.VIEW")
    public OrgUnitResponse getOrgUnit(Authentication authentication, @PathVariable UUID orgUnitId) {
        return service.getOrgUnit(SecurityContextUtils.tenantId(authentication), orgUnitId);
    }

    @PostMapping("/org-units")
    @RequireCapability("HRM.ORG_STRUCTURE.MANAGE")
    public ResponseEntity<OrgUnitResponse> createOrgUnit(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrgUnitRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".org-units.create",
                String.valueOf(request));
        OrgUnitResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".org-units.create", idempotencyKey, fingerprint,
                OrgUnitResponse.class, () -> service.createOrgUnit(tenantId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/org-units/{orgUnitId}/revise")
    @RequireCapability("HRM.ORG_STRUCTURE.MANAGE")
    public ResponseEntity<OrgUnitResponse> reviseOrgUnit(
            Authentication authentication, @PathVariable UUID orgUnitId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReviseOrgUnitRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".org-units.revise",
                orgUnitId + "|" + request);
        OrgUnitResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".org-units.revise", idempotencyKey, fingerprint,
                OrgUnitResponse.class,
                () -> service.reviseOrgUnit(tenantId, orgUnitId, request.parentOrgUnitId(),
                        request.name(), request.code(), request.unitType(), request.effectiveDate()));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    // ==================== JOBS (4) ====================

    @GetMapping("/jobs")
    @RequireCapability("HRM.ORG_STRUCTURE.VIEW")
    public List<JobResponse> listJobs(Authentication authentication) {
        return service.listJobs(SecurityContextUtils.tenantId(authentication));
    }

    @GetMapping("/jobs/{jobId}")
    @RequireCapability("HRM.ORG_STRUCTURE.VIEW")
    public JobResponse getJob(Authentication authentication, @PathVariable UUID jobId) {
        return service.getJob(SecurityContextUtils.tenantId(authentication), jobId);
    }

    @PostMapping("/jobs")
    @RequireCapability("HRM.ORG_STRUCTURE.MANAGE")
    public ResponseEntity<JobResponse> createJob(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateJobRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".jobs.create",
                String.valueOf(request));
        JobResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".jobs.create", idempotencyKey, fingerprint,
                JobResponse.class, () -> service.createJob(tenantId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/jobs/{jobId}/revise")
    @RequireCapability("HRM.ORG_STRUCTURE.MANAGE")
    public ResponseEntity<JobResponse> reviseJob(
            Authentication authentication, @PathVariable UUID jobId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReviseJobRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".jobs.revise",
                jobId + "|" + request);
        JobResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".jobs.revise", idempotencyKey, fingerprint,
                JobResponse.class,
                () -> service.reviseJob(tenantId, jobId, request.title(), request.grade(),
                        request.effectiveDate()));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    // ==================== POSITIONS (6) ====================

    @GetMapping("/positions")
    @RequireCapability("HRM.ORG_STRUCTURE.VIEW")
    public List<PositionResponse> listPositions(Authentication authentication) {
        return service.listPositions(SecurityContextUtils.tenantId(authentication));
    }

    @GetMapping("/positions/{positionId}")
    @RequireCapability("HRM.ORG_STRUCTURE.VIEW")
    public PositionResponse getPosition(Authentication authentication, @PathVariable UUID positionId) {
        return service.getPosition(SecurityContextUtils.tenantId(authentication), positionId);
    }

    @PostMapping("/positions")
    @RequireCapability("HRM.ORG_STRUCTURE.MANAGE")
    public ResponseEntity<PositionResponse> createPosition(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePositionRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".positions.create",
                String.valueOf(request));
        PositionResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".positions.create", idempotencyKey, fingerprint,
                PositionResponse.class, () -> service.createPosition(tenantId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/positions/{positionId}/revise")
    @RequireCapability("HRM.ORG_STRUCTURE.MANAGE")
    public ResponseEntity<PositionResponse> revisePosition(
            Authentication authentication, @PathVariable UUID positionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RevisePositionRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".positions.revise",
                positionId + "|" + request);
        PositionResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".positions.revise", idempotencyKey, fingerprint,
                PositionResponse.class,
                () -> service.revisePosition(tenantId, positionId, request.title(), request.jobId(),
                        request.orgUnitId(), request.effectiveDate()));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/positions/{positionId}/freeze")
    @RequireCapability("HRM.ORG_STRUCTURE.MANAGE")
    public ResponseEntity<StaffabilityResponse> freezePosition(
            Authentication authentication, @PathVariable UUID positionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".positions.freeze",
                String.valueOf(positionId));
        StaffabilityResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".positions.freeze", idempotencyKey, fingerprint,
                StaffabilityResponse.class, () -> service.freeze(tenantId, positionId));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/positions/{positionId}/close")
    @RequireCapability("HRM.ORG_STRUCTURE.MANAGE")
    public ResponseEntity<StaffabilityResponse> closePosition(
            Authentication authentication, @PathVariable UUID positionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".positions.close",
                String.valueOf(positionId));
        StaffabilityResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".positions.close", idempotencyKey, fingerprint,
                StaffabilityResponse.class, () -> service.close(tenantId, positionId));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
