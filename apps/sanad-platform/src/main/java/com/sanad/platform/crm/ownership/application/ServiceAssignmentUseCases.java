package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.SalesTeam;
import com.sanad.platform.crm.ownership.domain.SalesTeamRepository;
import com.sanad.platform.crm.ownership.domain.TeamNotFoundException;
import com.sanad.platform.crm.ownership.domain.service.ServiceAssignment;
import com.sanad.platform.crm.ownership.domain.service.ServiceAssignmentRepository;
import com.sanad.platform.crm.ownership.domain.service.ServiceAssignmentStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service for CRM-008 Service Assignment Management.
 *
 * <p>Manages service-to-team assignments including creation, reassignment,
 * completion, and cancellation. Enforces business rules for duplicate
 * prevention and state transitions.
 */
public class ServiceAssignmentUseCases {

    private final ServiceAssignmentRepository serviceAssignments;
    private final SalesTeamRepository teams;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public ServiceAssignmentUseCases(ServiceAssignmentRepository serviceAssignments,
                                     SalesTeamRepository teams,
                                     AuditPort audit,
                                     TimelineEventPort timeline,
                                     ObjectMapper mapper) {
        this.serviceAssignments = serviceAssignments;
        this.teams = teams;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    /**
     * Assign a service to a team.
     */
    @Transactional
    public ServiceAssignment assignService(UUID tenantId, UUID actorId, AssignServiceCommand cmd) {
        requireContext(tenantId, actorId);
        if (cmd == null) throw new IllegalArgumentException("command required");
        requireId(cmd.teamId(), "teamId");
        requireId(cmd.serviceId(), "serviceId");

        // Validate team exists
        teams.findById(tenantId, cmd.teamId())
                .orElseThrow(() -> new TeamNotFoundException(tenantId, cmd.teamId()));

        // Check for duplicate
        if (serviceAssignments.existsByTeamAndService(tenantId, cmd.teamId(), cmd.serviceId(), null)) {
            throw new OwnershipDomainException(
                    "Service is already assigned to this team");
        }

        ServiceAssignment created = serviceAssignments.create(
                new ServiceAssignmentRepository.CreateServiceAssignmentCommand(
                        tenantId, cmd.teamId(), cmd.serviceId(), actorId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE", "SERVICE_ASSIGNMENT", created.id(),
                new AuditChange(null, serializeAssignment(created)), now);
        timeline.record(tenantId, "SERVICE_ASSIGNMENT", created.id(),
                "crm.service.assigned", "Service assigned to team",
                "CRM_SERVICE_ASSIGNMENT", created.id(), actorId, now);
        return created;
    }

    /**
     * Reassign a service from one team to another.
     */
    @Transactional
    public ServiceAssignment reassignService(UUID tenantId, UUID actorId, UUID assignmentId,
                                              UUID newTeamId) {
        requireContext(tenantId, actorId);
        requireId(assignmentId, "assignmentId");
        requireId(newTeamId, "newTeamId");

        ServiceAssignment current = serviceAssignments.findById(tenantId, assignmentId)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Service assignment not found: " + assignmentId));

        if (current.isInactive()) {
            throw new OwnershipDomainException("Cannot reassign INACTIVE service assignment");
        }

        if (current.teamId().equals(newTeamId)) {
            throw new OwnershipDomainException("New team is the same as current");
        }

        // Validate new team exists
        teams.findById(tenantId, newTeamId)
                .orElseThrow(() -> new TeamNotFoundException(tenantId, newTeamId));

        // Check for duplicate on new team
        if (serviceAssignments.existsByTeamAndService(tenantId, newTeamId, current.serviceId(), null)) {
            throw new OwnershipDomainException(
                    "Service is already assigned to the new team");
        }

        // Deactivate current
        serviceAssignments.update(tenantId, assignmentId,
                new ServiceAssignmentRepository.UpdateServiceAssignmentCommand(
                        ServiceAssignmentStatus.INACTIVE, actorId, current.version()));

        // Create new assignment
        ServiceAssignment reassigned = serviceAssignments.create(
                new ServiceAssignmentRepository.CreateServiceAssignmentCommand(
                        tenantId, newTeamId, current.serviceId(), actorId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "REASSIGN", "SERVICE_ASSIGNMENT", assignmentId,
                new AuditChange(serializeAssignment(current), serializeAssignment(reassigned)), now);
        timeline.record(tenantId, "SERVICE_ASSIGNMENT", reassigned.id(),
                "crm.service.reassigned", "Service reassigned to different team",
                "CRM_SERVICE_ASSIGNMENT", reassigned.id(), actorId, now);
        return reassigned;
    }

    /**
     * Complete (deactivate) a service assignment.
     */
    @Transactional
    public ServiceAssignment completeService(UUID tenantId, UUID actorId, UUID assignmentId) {
        requireContext(tenantId, actorId);
        requireId(assignmentId, "assignmentId");

        ServiceAssignment current = serviceAssignments.findById(tenantId, assignmentId)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Service assignment not found: " + assignmentId));

        if (current.isInactive()) {
            throw new OwnershipDomainException("Service assignment is already INACTIVE");
        }

        ServiceAssignment completed = serviceAssignments.update(tenantId, assignmentId,
                new ServiceAssignmentRepository.UpdateServiceAssignmentCommand(
                        ServiceAssignmentStatus.INACTIVE, actorId, current.version()))
                .orElseThrow(() -> new OwnershipDomainException(
                        "Concurrent modification: " + assignmentId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "COMPLETE", "SERVICE_ASSIGNMENT", assignmentId,
                new AuditChange(serializeAssignment(current), serializeAssignment(completed)), now);
        timeline.record(tenantId, "SERVICE_ASSIGNMENT", assignmentId,
                "crm.service.completed", "Service assignment completed",
                "CRM_SERVICE_ASSIGNMENT", assignmentId, actorId, now);
        return completed;
    }

    /**
     * Cancel a service assignment.
     */
    @Transactional
    public ServiceAssignment cancelService(UUID tenantId, UUID actorId, UUID assignmentId) {
        requireContext(tenantId, actorId);
        requireId(assignmentId, "assignmentId");

        ServiceAssignment current = serviceAssignments.findById(tenantId, assignmentId)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Service assignment not found: " + assignmentId));

        if (current.isInactive()) {
            throw new OwnershipDomainException("Service assignment is already INACTIVE");
        }

        ServiceAssignment cancelled = serviceAssignments.update(tenantId, assignmentId,
                new ServiceAssignmentRepository.UpdateServiceAssignmentCommand(
                        ServiceAssignmentStatus.INACTIVE, actorId, current.version()))
                .orElseThrow(() -> new OwnershipDomainException(
                        "Concurrent modification: " + assignmentId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CANCEL", "SERVICE_ASSIGNMENT", assignmentId,
                new AuditChange(serializeAssignment(current), serializeAssignment(cancelled)), now);
        timeline.record(tenantId, "SERVICE_ASSIGNMENT", assignmentId,
                "crm.service.cancelled", "Service assignment cancelled",
                "CRM_SERVICE_ASSIGNMENT", assignmentId, actorId, now);
        return cancelled;
    }

    /**
     * List service assignments for a team.
     */
    public List<ServiceAssignment> listByTeam(UUID tenantId, UUID teamId) {
        requireId(tenantId, "tenantId");
        requireId(teamId, "teamId");
        return List.copyOf(serviceAssignments.findByTeamId(tenantId, teamId));
    }

    /**
     * List service assignments for a service.
     */
    public List<ServiceAssignment> listByService(UUID tenantId, UUID serviceId) {
        requireId(tenantId, "tenantId");
        requireId(serviceId, "serviceId");
        return List.copyOf(serviceAssignments.findByServiceId(tenantId, serviceId));
    }

    /**
     * Get a specific service assignment.
     */
    public ServiceAssignment getServiceAssignment(UUID tenantId, UUID assignmentId) {
        requireId(tenantId, "tenantId");
        requireId(assignmentId, "assignmentId");
        return serviceAssignments.findById(tenantId, assignmentId)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Service assignment not found: " + assignmentId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void requireContext(UUID tenantId, UUID actorId) {
        requireId(tenantId, "tenantId");
        requireId(actorId, "actorId");
    }

    private static void requireId(Object value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " required");
    }

    private com.fasterxml.jackson.databind.JsonNode serializeAssignment(ServiceAssignment a) {
        if (a == null) return null;
        var node = mapper.createObjectNode();
        node.put("id", a.id().toString());
        node.put("teamId", a.teamId().toString());
        node.put("serviceId", a.serviceId().toString());
        node.put("status", a.status().name());
        return node;
    }

    // ── Command Records ──────────────────────────────────────────────────

    public record AssignServiceCommand(
            UUID teamId,
            UUID serviceId) {}
}
