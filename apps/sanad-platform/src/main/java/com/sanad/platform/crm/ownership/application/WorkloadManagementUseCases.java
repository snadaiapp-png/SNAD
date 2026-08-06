package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.workload.WorkloadAssignment;
import com.sanad.platform.crm.ownership.domain.workload.WorkloadRepository;
import com.sanad.platform.crm.ownership.domain.workload.WorkloadStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application service for CRM-008 Workload Management.
 *
 * <p>Manages workload assignments including assignment, reassignment,
 * balancing, and release. Enforces business rules for workload capacity
 * and status transitions.
 */
public class WorkloadManagementUseCases {

    private final WorkloadRepository workloads;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public WorkloadManagementUseCases(WorkloadRepository workloads,
                                      AuditPort audit,
                                      TimelineEventPort timeline,
                                      ObjectMapper mapper) {
        this.workloads = workloads;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    /**
     * Assign work to a staff member.
     */
    @Transactional
    public WorkloadAssignment assignWork(UUID tenantId, UUID actorId, AssignWorkCommand cmd) {
        requireContext(tenantId, actorId);
        if (cmd == null) throw new IllegalArgumentException("command required");
        requireId(cmd.staffId(), "staffId");
        requireId(cmd.serviceId(), "serviceId");
        if (cmd.estimatedHours() <= 0) {
            throw new OwnershipDomainException("estimatedHours must be positive");
        }
        requireId(cmd.startDate(), "startDate");

        WorkloadAssignment created = workloads.create(new WorkloadRepository.CreateWorkloadCommand(
                tenantId, cmd.staffId(), cmd.serviceId(), cmd.jobId(),
                cmd.estimatedHours(), cmd.startDate(), cmd.endDate(), actorId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE", "WORKLOAD_ASSIGNMENT", created.id(),
                new AuditChange(null, serializeWorkload(created)), now);
        timeline.record(tenantId, "WORKLOAD_ASSIGNMENT", created.id(),
                "crm.workload.assigned", "Work assigned",
                "CRM_WORKLOAD_ASSIGNMENT", created.id(), actorId, now);
        return created;
    }

    /**
     * Reassign work to a different staff member.
     */
    @Transactional
    public WorkloadAssignment reassignWork(UUID tenantId, UUID actorId, UUID workloadId,
                                            UUID newStaffId) {
        requireContext(tenantId, actorId);
        requireId(workloadId, "workloadId");
        requireId(newStaffId, "newStaffId");

        WorkloadAssignment current = workloads.findById(tenantId, workloadId)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Workload assignment not found: " + workloadId));

        if (current.isCompleted() || current.isCancelled()) {
            throw new OwnershipDomainException("Cannot reassign " + current.status() + " workload");
        }

        if (current.staffId().equals(newStaffId)) {
            throw new OwnershipDomainException("New staff member is the same as current");
        }

        // Release current and create new
        workloads.update(tenantId, workloadId, new WorkloadRepository.UpdateWorkloadCommand(
                current.actualHours(), WorkloadStatus.CANCELLED, current.endDate(),
                actorId, current.version()));

        WorkloadAssignment reassigned = workloads.create(new WorkloadRepository.CreateWorkloadCommand(
                tenantId, newStaffId, current.serviceId(), current.jobId(),
                current.estimatedHours(), current.startDate(), current.endDate(), actorId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "REASSIGN", "WORKLOAD_ASSIGNMENT", workloadId,
                new AuditChange(serializeWorkload(current), serializeWorkload(reassigned)), now);
        timeline.record(tenantId, "WORKLOAD_ASSIGNMENT", reassigned.id(),
                "crm.workload.reassigned", "Work reassigned",
                "CRM_WORKLOAD_ASSIGNMENT", reassigned.id(), actorId, now);
        return reassigned;
    }

    /**
     * Balance workload across staff members by redistributing evenly.
     */
    @Transactional
    public List<WorkloadAssignment> balanceWorkload(UUID tenantId, UUID actorId,
                                                     List<UUID> workloadIds) {
        requireContext(tenantId, actorId);
        if (workloadIds == null || workloadIds.isEmpty()) {
            throw new IllegalArgumentException("workloadIds required");
        }

        List<WorkloadAssignment> assignments = workloadIds.stream()
                .map(id -> workloads.findById(tenantId, id)
                        .orElseThrow(() -> new OwnershipDomainException(
                                "Workload assignment not found: " + id)))
                .filter(a -> !a.isCompleted() && !a.isCancelled())
                .toList();

        // Calculate total estimated hours
        int totalHours = assignments.stream()
                .mapToInt(WorkloadAssignment::estimatedHours)
                .sum();

        // Distribute evenly
        int perStaff = totalHours / assignments.size();
        int remainder = totalHours % assignments.size();

        Instant now = Instant.now();
        final int[] remainderCounter = {remainder};
        return assignments.stream()
                .map(a -> {
                    int hours = perStaff + (remainderCounter[0]-- > 0 ? 1 : 0);
                    return workloads.update(tenantId, a.id(), new WorkloadRepository.UpdateWorkloadCommand(
                            a.actualHours(), a.status(), a.endDate(), actorId, a.version()))
                            .orElse(a);
                })
                .toList();
    }

    /**
     * Release (cancel) a workload assignment.
     */
    @Transactional
    public WorkloadAssignment releaseAssignment(UUID tenantId, UUID actorId, UUID workloadId) {
        requireContext(tenantId, actorId);
        requireId(workloadId, "workloadId");

        WorkloadAssignment current = workloads.findById(tenantId, workloadId)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Workload assignment not found: " + workloadId));

        if (current.isCompleted() || current.isCancelled()) {
            throw new OwnershipDomainException("Cannot release " + current.status() + " workload");
        }

        WorkloadAssignment released = workloads.update(tenantId, workloadId,
                new WorkloadRepository.UpdateWorkloadCommand(
                        current.actualHours(), WorkloadStatus.CANCELLED,
                        LocalDate.now(), actorId, current.version()))
                .orElseThrow(() -> new OwnershipDomainException("Concurrent modification: " + workloadId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "RELEASE", "WORKLOAD_ASSIGNMENT", workloadId,
                new AuditChange(serializeWorkload(current), serializeWorkload(released)), now);
        timeline.record(tenantId, "WORKLOAD_ASSIGNMENT", workloadId,
                "crm.workload.released", "Work assignment released",
                "CRM_WORKLOAD_ASSIGNMENT", workloadId, actorId, now);
        return released;
    }

    /**
     * List workload assignments for a staff member.
     */
    public List<WorkloadAssignment> listByStaff(UUID tenantId, UUID staffId, WorkloadStatus status) {
        requireId(tenantId, "tenantId");
        requireId(staffId, "staffId");
        if (status == null) throw new IllegalArgumentException("status required");
        return List.copyOf(workloads.findByStaffId(tenantId, staffId, status));
    }

    /**
     * List workload assignments for a service.
     */
    public List<WorkloadAssignment> listByService(UUID tenantId, UUID serviceId) {
        requireId(tenantId, "tenantId");
        requireId(serviceId, "serviceId");
        return List.copyOf(workloads.findByServiceId(tenantId, serviceId));
    }

    /**
     * Get estimated hours for a staff member in a period.
     */
    public int getEstimatedHours(UUID tenantId, UUID staffId, LocalDate from, LocalDate to) {
        return workloads.sumEstimatedHoursByStaff(tenantId, staffId, from, to);
    }

    /**
     * Get actual hours for a staff member in a period.
     */
    public int getActualHours(UUID tenantId, UUID staffId, LocalDate from, LocalDate to) {
        return workloads.sumActualHoursByStaff(tenantId, staffId, from, to);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void requireContext(UUID tenantId, UUID actorId) {
        requireId(tenantId, "tenantId");
        requireId(actorId, "actorId");
    }

    private static void requireId(Object value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " required");
    }

    private com.fasterxml.jackson.databind.JsonNode serializeWorkload(WorkloadAssignment w) {
        if (w == null) return null;
        var node = mapper.createObjectNode();
        node.put("id", w.id().toString());
        node.put("staffId", w.staffId().toString());
        node.put("estimatedHours", w.estimatedHours());
        node.put("status", w.status().name());
        return node;
    }

    // ── Command Records ──────────────────────────────────────────────────

    public record AssignWorkCommand(
            UUID staffId,
            UUID serviceId,
            UUID jobId,
            int estimatedHours,
            LocalDate startDate,
            LocalDate endDate) {}
}
