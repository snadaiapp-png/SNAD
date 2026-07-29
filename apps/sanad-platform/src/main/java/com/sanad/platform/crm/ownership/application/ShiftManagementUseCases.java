package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.SalesTeam;
import com.sanad.platform.crm.ownership.domain.SalesTeamRepository;
import com.sanad.platform.crm.ownership.domain.TeamNotFoundException;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignment;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignmentRepository;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignmentStatus;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftTemplate;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftTemplateRepository;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftTemplateStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application service for CRM-008 Shift Management.
 *
 * <p>Manages shift templates and shift assignments. Enforces scheduling
 * business rules including overlap detection and state transitions.
 */
public class ShiftManagementUseCases {

    private final ShiftTemplateRepository templates;
    private final ShiftAssignmentRepository assignments;
    private final SalesTeamRepository teams;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public ShiftManagementUseCases(ShiftTemplateRepository templates,
                                   ShiftAssignmentRepository assignments,
                                   SalesTeamRepository teams,
                                   AuditPort audit,
                                   TimelineEventPort timeline,
                                   ObjectMapper mapper) {
        this.templates = templates;
        this.assignments = assignments;
        this.teams = teams;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    // ── Shift Template Operations ────────────────────────────────────────

    @Transactional
    public ShiftTemplate createShiftTemplate(UUID tenantId, UUID actorId, CreateShiftTemplateCommand cmd) {
        requireContext(tenantId, actorId);
        if (cmd == null) throw new IllegalArgumentException("command required");
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (templates.existsByName(tenantId, cmd.name(), null)) {
            throw new OwnershipDomainException("Shift template name already exists: " + cmd.name());
        }

        ShiftTemplate created = templates.create(new ShiftTemplateRepository.CreateShiftTemplateCommand(
                tenantId, cmd.name(), cmd.startTime(), cmd.endTime(),
                cmd.daysOfWeek(), actorId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE", "SHIFT_TEMPLATE", created.id(),
                new AuditChange(null, serializeTemplate(created)), now);
        timeline.record(tenantId, "SHIFT_TEMPLATE", created.id(),
                "crm.shift_template.created", "Shift template created",
                "CRM_SHIFT_TEMPLATE", created.id(), actorId, now);
        return created;
    }

    @Transactional
    public ShiftTemplate updateShiftTemplate(UUID tenantId, UUID actorId, UUID templateId,
                                              UpdateShiftTemplateCommand cmd) {
        requireContext(tenantId, actorId);
        requireId(templateId, "templateId");
        if (cmd == null) throw new IllegalArgumentException("command required");

        ShiftTemplate current = templates.findById(tenantId, templateId)
                .orElseThrow(() -> new OwnershipDomainException("Shift template not found: " + templateId));
        assertActiveTemplate(current);

        if (cmd.name() != null && !cmd.name().isBlank()
                && templates.existsByName(tenantId, cmd.name(), templateId)) {
            throw new OwnershipDomainException("Shift template name already exists: " + cmd.name());
        }

        String name = cmd.name() != null ? cmd.name() : current.name();
        java.time.LocalTime startTime = cmd.startTime() != null ? cmd.startTime() : current.startTime();
        java.time.LocalTime endTime = cmd.endTime() != null ? cmd.endTime() : current.endTime();
        var daysOfWeek = cmd.daysOfWeek() != null ? cmd.daysOfWeek() : current.daysOfWeek();
        ShiftTemplateStatus status = cmd.status() != null ? cmd.status() : current.status();

        return templates.update(tenantId, templateId, new ShiftTemplateRepository.UpdateShiftTemplateCommand(
                name, startTime, endTime, daysOfWeek, status, actorId, current.version()))
                .orElseThrow(() -> new OwnershipDomainException("Concurrent modification: " + templateId));
    }

    @Transactional
    public ShiftTemplate publishShiftTemplate(UUID tenantId, UUID actorId, UUID templateId) {
        requireContext(tenantId, actorId);
        requireId(templateId, "templateId");

        ShiftTemplate current = templates.findById(tenantId, templateId)
                .orElseThrow(() -> new OwnershipDomainException("Shift template not found: " + templateId));

        if (current.isActive()) {
            throw new OwnershipDomainException("Template is already ACTIVE: " + templateId);
        }

        return templates.update(tenantId, templateId, new ShiftTemplateRepository.UpdateShiftTemplateCommand(
                current.name(), current.startTime(), current.endTime(), current.daysOfWeek(),
                ShiftTemplateStatus.ACTIVE, actorId, current.version()))
                .orElseThrow(() -> new OwnershipDomainException("Concurrent modification: " + templateId));
    }

    @Transactional
    public ShiftTemplate cancelShiftTemplate(UUID tenantId, UUID actorId, UUID templateId) {
        requireContext(tenantId, actorId);
        requireId(templateId, "templateId");

        ShiftTemplate current = templates.findById(tenantId, templateId)
                .orElseThrow(() -> new OwnershipDomainException("Shift template not found: " + templateId));

        if (current.isInactive()) {
            throw new OwnershipDomainException("Template is already INACTIVE: " + templateId);
        }

        return templates.update(tenantId, templateId, new ShiftTemplateRepository.UpdateShiftTemplateCommand(
                current.name(), current.startTime(), current.endTime(), current.daysOfWeek(),
                ShiftTemplateStatus.INACTIVE, actorId, current.version()))
                .orElseThrow(() -> new OwnershipDomainException("Concurrent modification: " + templateId));
    }

    public ShiftTemplate getShiftTemplate(UUID tenantId, UUID templateId) {
        requireId(tenantId, "tenantId");
        requireId(templateId, "templateId");
        return templates.findById(tenantId, templateId)
                .orElseThrow(() -> new OwnershipDomainException("Shift template not found: " + templateId));
    }

    public List<ShiftTemplate> listShiftTemplates(UUID tenantId, int limit, int offset) {
        requireId(tenantId, "tenantId");
        return List.copyOf(templates.findAll(tenantId, limit, offset));
    }

    // ── Shift Assignment Operations ──────────────────────────────────────

    @Transactional
    public ShiftAssignment assignShift(UUID tenantId, UUID actorId, CreateShiftAssignmentCommand cmd) {
        requireContext(tenantId, actorId);
        if (cmd == null) throw new IllegalArgumentException("command required");

        // Validate team exists
        teams.findById(tenantId, cmd.teamId())
                .orElseThrow(() -> new TeamNotFoundException(tenantId, cmd.teamId()));

        // Validate template exists and is active
        ShiftTemplate template = templates.findById(tenantId, cmd.shiftTemplateId())
                .orElseThrow(() -> new OwnershipDomainException(
                        "Shift template not found: " + cmd.shiftTemplateId()));
        assertActiveTemplate(template);

        // Check for overlapping assignments
        if (assignments.hasOverlap(tenantId, cmd.staffId(), cmd.startDate(), cmd.endDate(), null)) {
            throw new OwnershipDomainException(
                    "Staff member has overlapping shift assignment for the given date range");
        }

        ShiftAssignment created = assignments.create(
                new ShiftAssignmentRepository.CreateShiftAssignmentCommand(
                        tenantId, cmd.teamId(), cmd.staffId(), cmd.shiftTemplateId(),
                        cmd.startDate(), cmd.endDate(), actorId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE", "SHIFT_ASSIGNMENT", created.id(),
                new AuditChange(null, serializeAssignment(created)), now);
        timeline.record(tenantId, "SHIFT_ASSIGNMENT", created.id(),
                "crm.shift_assigned", "Shift assigned",
                "CRM_SHIFT_ASSIGNMENT", created.id(), actorId, now);
        return created;
    }

    @Transactional
    public ShiftAssignment updateShiftAssignment(UUID tenantId, UUID actorId, UUID assignmentId,
                                                  UpdateShiftAssignmentCommand cmd) {
        requireContext(tenantId, actorId);
        requireId(assignmentId, "assignmentId");
        if (cmd == null) throw new IllegalArgumentException("command required");

        ShiftAssignment current = assignments.findById(tenantId, assignmentId)
                .orElseThrow(() -> new OwnershipDomainException("Shift assignment not found: " + assignmentId));

        if (current.isCompleted() || current.isCancelled()) {
            throw new OwnershipDomainException("Cannot update " + current.status() + " assignment");
        }

        java.time.LocalDate startDate = cmd.startDate() != null ? cmd.startDate() : current.startDate();
        java.time.LocalDate endDate = cmd.endDate() != null ? cmd.endDate() : current.endDate();
        UUID templateId = cmd.shiftTemplateId() != null ? cmd.shiftTemplateId() : current.shiftTemplateId();
        ShiftAssignmentStatus status = cmd.status() != null ? cmd.status() : current.status();

        // Check overlap if dates changed
        if (!startDate.equals(current.startDate()) || !endDate.equals(current.endDate())) {
            if (assignments.hasOverlap(tenantId, current.staffId(), startDate, endDate, assignmentId)) {
                throw new OwnershipDomainException(
                        "Staff member has overlapping shift assignment for the given date range");
            }
        }

        return assignments.update(tenantId, assignmentId,
                new ShiftAssignmentRepository.UpdateShiftAssignmentCommand(
                        templateId, startDate, endDate, status, actorId, current.version()))
                .orElseThrow(() -> new OwnershipDomainException("Concurrent modification: " + assignmentId));
    }

    @Transactional
    public ShiftAssignment cancelShiftAssignment(UUID tenantId, UUID actorId, UUID assignmentId) {
        requireContext(tenantId, actorId);
        requireId(assignmentId, "assignmentId");

        ShiftAssignment current = assignments.findById(tenantId, assignmentId)
                .orElseThrow(() -> new OwnershipDomainException("Shift assignment not found: " + assignmentId));

        if (current.isCancelled()) {
            throw new OwnershipDomainException("Assignment is already CANCELLED");
        }
        if (current.isCompleted()) {
            throw new OwnershipDomainException("Cannot cancel COMPLETED assignment");
        }

        return assignments.update(tenantId, assignmentId,
                new ShiftAssignmentRepository.UpdateShiftAssignmentCommand(
                        current.shiftTemplateId(), current.startDate(), current.endDate(),
                        ShiftAssignmentStatus.CANCELLED, actorId, current.version()))
                .orElseThrow(() -> new OwnershipDomainException("Concurrent modification: " + assignmentId));
    }

    public List<ShiftAssignment> listShiftAssignmentsByTeam(UUID tenantId, UUID teamId, int limit, int offset) {
        requireId(tenantId, "tenantId");
        requireId(teamId, "teamId");
        return List.copyOf(assignments.findByTeamId(tenantId, teamId, limit, offset));
    }

    public List<ShiftAssignment> listShiftAssignmentsByStaff(UUID tenantId, UUID staffId,
                                                              LocalDate from, LocalDate to) {
        requireId(tenantId, "tenantId");
        requireId(staffId, "staffId");
        return List.copyOf(assignments.findByStaffId(tenantId, staffId, from, to));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void assertActiveTemplate(ShiftTemplate template) {
        if (template.isInactive()) {
            throw new OwnershipDomainException("Shift template is INACTIVE: " + template.id());
        }
    }

    private static void requireContext(UUID tenantId, UUID actorId) {
        requireId(tenantId, "tenantId");
        requireId(actorId, "actorId");
    }

    private static void requireId(UUID value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " required");
    }

    private com.fasterxml.jackson.databind.JsonNode serializeTemplate(ShiftTemplate t) {
        if (t == null) return null;
        var node = mapper.createObjectNode();
        node.put("id", t.id().toString());
        node.put("name", t.name());
        node.put("status", t.status().name());
        return node;
    }

    private com.fasterxml.jackson.databind.JsonNode serializeAssignment(ShiftAssignment a) {
        if (a == null) return null;
        var node = mapper.createObjectNode();
        node.put("id", a.id().toString());
        node.put("teamId", a.teamId().toString());
        node.put("staffId", a.staffId().toString());
        node.put("status", a.status().name());
        return node;
    }

    // ── Command Records ──────────────────────────────────────────────────

    public record CreateShiftTemplateCommand(
            String name,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            java.util.List<java.time.DayOfWeek> daysOfWeek) {}

    public record UpdateShiftTemplateCommand(
            String name,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            java.util.List<java.time.DayOfWeek> daysOfWeek,
            ShiftTemplateStatus status) {}

    public record CreateShiftAssignmentCommand(
            UUID teamId,
            UUID staffId,
            UUID shiftTemplateId,
            LocalDate startDate,
            LocalDate endDate) {}

    public record UpdateShiftAssignmentCommand(
            UUID shiftTemplateId,
            LocalDate startDate,
            LocalDate endDate,
            ShiftAssignmentStatus status) {}
}
