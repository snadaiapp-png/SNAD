package com.sanad.platform.hr.structure.application;

import com.sanad.platform.hr.api.v2.dto.CreateJobRequest;
import com.sanad.platform.hr.api.v2.dto.CreateOrgUnitRequest;
import com.sanad.platform.hr.api.v2.dto.CreatePositionRequest;
import com.sanad.platform.hr.api.v2.dto.JobResponse;
import com.sanad.platform.hr.api.v2.dto.OrgUnitResponse;
import com.sanad.platform.hr.api.v2.dto.PositionResponse;
import com.sanad.platform.hr.structure.domain.HrJobVersion;
import com.sanad.platform.hr.structure.domain.HrOrgUnitVersion;
import com.sanad.platform.hr.structure.domain.HrPositionVersion;
import com.sanad.platform.hr.structure.infrastructure.JdbcHrStructureRepository;
import com.sanad.platform.hr.structure.infrastructure.JdbcHrStructureRepository.PositionWithStaffability;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 4 — Structure v2 application service.
 *
 * <p>Resolution and error-projection layer over the WS2 structure
 * persistence: canonical 404 semantics, effective-dated revisions that
 * preserve version history, and staffability-only freeze/close transitions.
 * The WS2 cycle detection and period-aware validation chain is reused —
 * no structural rule is duplicated or weakened here.
 */
@Service
public class HrStructureV2Service {

    private final JdbcHrStructureRepository repository;

    @Autowired
    public HrStructureV2Service(JdbcHrStructureRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    // ==================== ORG UNITS ====================

    public List<OrgUnitResponse> listOrgUnits(UUID tenantId) {
        return repository.openOrgUnitVersions(requireTenant(tenantId)).stream()
                .map(OrgUnitResponse::from)
                .toList();
    }

    public OrgUnitResponse getOrgUnit(UUID tenantId, UUID orgUnitId) {
        return repository.orgUnitVersions(requireTenant(tenantId), orgUnitId).stream()
                .filter(v -> v.effectiveTo() == null)
                .findFirst()
                .map(OrgUnitResponse::from)
                .orElseThrow(() -> notFound("HRM_ORG_UNIT_NOT_FOUND", orgUnitId));
    }

    public OrgUnitResponse createOrgUnit(UUID tenantId, CreateOrgUnitRequest request) {
        Objects.requireNonNull(request, "request");
        String stableCode = request.code() + "-" + UUID.randomUUID().toString().substring(0, 6);
        HrOrgUnitVersion version = repository.createOrgUnitAtomically(requireTenant(tenantId),
                request.organizationId(),
                stableCode,
                request.name(), request.code(), request.unitType(),
                request.parentOrgUnitId(), request.effectiveFrom());
        return OrgUnitResponse.from(version);
    }

    public OrgUnitResponse reviseOrgUnit(UUID tenantId, UUID orgUnitId, UUID parentOrgUnitId,
                                         String name, String code, String unitType,
                                         LocalDate effectiveDate) {
        HrOrgUnitVersion current = repository.orgUnitVersions(requireTenant(tenantId), orgUnitId).stream()
                .filter(v -> v.effectiveTo() == null)
                .findFirst()
                .orElseThrow(() -> notFound("HRM_ORG_UNIT_NOT_FOUND", orgUnitId));
        HrOrgUnitVersion revised = repository.reviseOrgUnitAtomically(requireTenant(tenantId), orgUnitId,
                effectiveDate,
                parentOrgUnitId != null ? parentOrgUnitId : current.parentOrgUnitId(),
                name != null ? name : current.name(),
                code != null ? code : current.code(),
                unitType != null ? unitType : current.unitType());
        return OrgUnitResponse.from(revised);
    }

    // ==================== JOBS ====================

    public List<JobResponse> listJobs(UUID tenantId) {
        return repository.openJobVersions(requireTenant(tenantId)).stream()
                .map(JobResponse::from)
                .toList();
    }

    public JobResponse getJob(UUID tenantId, UUID jobId) {
        return repository.jobVersions(requireTenant(tenantId), jobId).stream()
                .filter(v -> v.effectiveTo() == null)
                .findFirst()
                .map(JobResponse::from)
                .orElseThrow(() -> notFound("HRM_JOB_NOT_FOUND", jobId));
    }

    public JobResponse createJob(UUID tenantId, CreateJobRequest request) {
        Objects.requireNonNull(request, "request");
        HrJobVersion version = repository.createJobAtomically(requireTenant(tenantId),
                request.organizationId(),
                "JB-" + UUID.randomUUID().toString().substring(0, 8),
                request.title(), null, request.grade(), request.effectiveFrom());
        return JobResponse.from(version);
    }

    public JobResponse reviseJob(UUID tenantId, UUID jobId, String title, String grade,
                                 LocalDate effectiveDate) {
        requireJob(tenantId, jobId);
        HrJobVersion current = repository.jobVersions(requireTenant(tenantId), jobId).stream()
                .filter(v -> v.effectiveTo() == null)
                .findFirst()
                .orElseThrow(() -> notFound("HRM_JOB_NOT_FOUND", jobId));
        HrJobVersion revised = repository.reviseJobAtomically(requireTenant(tenantId), jobId,
                title != null ? title : current.title(),
                current.description(),
                grade != null ? grade : current.grade(),
                effectiveDate);
        return JobResponse.from(revised);
    }

    // ==================== POSITIONS ====================

    public List<PositionResponse> listPositions(UUID tenantId) {
        return repository.openPositionVersions(requireTenant(tenantId)).stream()
                .map(PositionResponse::from)
                .toList();
    }

    public PositionResponse getPosition(UUID tenantId, UUID positionId) {
        return repository.openPositionVersions(requireTenant(tenantId)).stream()
                .filter(p -> p.positionId().equals(positionId))
                .findFirst()
                .map(PositionResponse::from)
                .orElseThrow(() -> notFound("HRM_POSITION_NOT_FOUND", positionId));
    }

    public PositionResponse createPosition(UUID tenantId, CreatePositionRequest request) {
        Objects.requireNonNull(request, "request");
        HrPositionVersion version = repository.createPositionAtomically(requireTenant(tenantId),
                request.title(), request.code(), request.jobId(), request.orgUnitId(),
                request.effectiveFrom());
        return PositionResponse.from(new PositionWithStaffability(version.positionId(), "ACTIVE", version));
    }

    public PositionResponse revisePosition(UUID tenantId, UUID positionId, String title,
                                           UUID jobId, UUID orgUnitId, LocalDate effectiveDate) {
        HrPositionVersion version = repository.revisePositionAtomically(requireTenant(tenantId),
                positionId, title, jobId, orgUnitId, effectiveDate);
        String staffability = repository.openPositionVersions(requireTenant(tenantId)).stream()
                .filter(p -> p.positionId().equals(positionId))
                .findFirst()
                .map(PositionWithStaffability::staffability)
                .orElse("ACTIVE");
        return PositionResponse.from(new PositionWithStaffability(positionId, staffability, version));
    }

    public com.sanad.platform.hr.api.v2.dto.StaffabilityResponse freeze(UUID tenantId, UUID positionId) {
        String staffability = repository.setPositionStaffability(requireTenant(tenantId), positionId, "INACTIVE");
        return new com.sanad.platform.hr.api.v2.dto.StaffabilityResponse(positionId, staffability);
    }

    public com.sanad.platform.hr.api.v2.dto.StaffabilityResponse close(UUID tenantId, UUID positionId) {
        String staffability = repository.setPositionStaffability(requireTenant(tenantId), positionId, "ARCHIVED");
        return new com.sanad.platform.hr.api.v2.dto.StaffabilityResponse(positionId, staffability);
    }

    // ==================== helpers ====================

    private UUID requireTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return tenantId;
    }

    private void requireJob(UUID tenantId, UUID jobId) {
        if (repository.jobVersions(requireTenant(tenantId), jobId).isEmpty()) {
            throw notFound("HRM_JOB_NOT_FOUND", jobId);
        }
    }

    private IllegalStateException notFound(String code, UUID id) {
        return new IllegalStateException(code + ": " + id);
    }
}
