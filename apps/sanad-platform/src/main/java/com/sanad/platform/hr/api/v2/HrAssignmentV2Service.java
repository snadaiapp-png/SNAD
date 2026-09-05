package com.sanad.platform.hr.api.v2;

import com.sanad.platform.hr.api.v2.dto.CreateAssignmentRequest;
import com.sanad.platform.hr.assignment.domain.AssignmentType;
import com.sanad.platform.hr.assignment.domain.HrAssignment;
import com.sanad.platform.hr.assignment.domain.OccupancyMode;
import com.sanad.platform.hr.assignment.infrastructure.JdbcHrAssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 4 — Assignment v2 application service.
 *
 * <p>Resolution and error-projection layer over the WS2 atomic assignment
 * persistence: canonical 404 semantics for unknown assignments, terminal
 * close for end, and period-superseding semantics for change-manager and
 * transfer. All validations (occupancy, allocation, overlap, reporting,
 * org/position effectiveness) remain in the certified WS2 repository chain —
 * no business rule is duplicated or weakened here.
 */
@Service
public class HrAssignmentV2Service {

    private final JdbcHrAssignmentRepository repository;

    @Autowired
    public HrAssignmentV2Service(JdbcHrAssignmentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<HrAssignment> list(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return repository.listAssignments(tenantId);
    }

    public HrAssignment get(UUID tenantId, UUID assignmentId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return repository.findAssignmentById(tenantId, assignmentId)
                .orElseThrow(() -> notFound(assignmentId));
    }

    public HrAssignment create(UUID tenantId, CreateAssignmentRequest request) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(request, "request");
        return repository.createAssignmentAtomically(
                tenantId, request.employmentId(), request.organizationId(),
                request.orgUnitId(), request.positionId(), request.reportsToAssignmentId(),
                null, null, request.assignmentType(), request.occupancyMode(),
                request.allocationPercent(), request.effectiveFrom(), request.effectiveTo());
    }

    public HrAssignment end(UUID tenantId, UUID assignmentId, LocalDate effectiveTo, Long expectedVersion) {
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        requireAssignment(tenantId, assignmentId);
        return repository.endAssignmentAtomically(tenantId, assignmentId, effectiveTo, expectedVersion);
    }

    public HrAssignment changeManager(UUID tenantId, UUID assignmentId, UUID reportsToAssignmentId,
                                      LocalDate effectiveDate, Long expectedVersion) {
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        requireAssignment(tenantId, assignmentId);
        return repository.changeManagerAtomically(tenantId, assignmentId, reportsToAssignmentId,
                effectiveDate, expectedVersion);
    }

    public HrAssignment transfer(UUID tenantId, UUID assignmentId, UUID orgUnitId, UUID positionId,
                                 UUID reportsToAssignmentId, LocalDate effectiveDate, Long expectedVersion) {
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        requireAssignment(tenantId, assignmentId);
        return repository.transferAssignmentAtomically(tenantId, assignmentId, orgUnitId, positionId,
                reportsToAssignmentId, effectiveDate, expectedVersion);
    }

    private void requireAssignment(UUID tenantId, UUID assignmentId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(assignmentId, "assignmentId");
        if (repository.findAssignmentById(tenantId, assignmentId).isEmpty()) {
            throw notFound(assignmentId);
        }
    }

    private IllegalStateException notFound(UUID assignmentId) {
        return new IllegalStateException("HRM_ASSIGNMENT_NOT_FOUND: " + assignmentId);
    }
}
