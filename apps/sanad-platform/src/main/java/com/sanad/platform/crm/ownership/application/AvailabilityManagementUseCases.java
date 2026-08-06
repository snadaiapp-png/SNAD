package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.availability.AvailabilityRepository;
import com.sanad.platform.crm.ownership.domain.availability.AvailabilityType;
import com.sanad.platform.crm.ownership.domain.availability.StaffAvailability;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application service for CRM-008 Availability Management.
 *
 * <p>Manages staff availability records including submission, approval,
 * rejection, and calendar queries. Enforces business rules for date ranges
 * and availability types.
 */
public class AvailabilityManagementUseCases {

    private final AvailabilityRepository availability;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public AvailabilityManagementUseCases(AvailabilityRepository availability,
                                          AuditPort audit,
                                          TimelineEventPort timeline,
                                          ObjectMapper mapper) {
        this.availability = availability;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    /**
     * Submit a new availability record for a staff member.
     */
    @Transactional
    public StaffAvailability submitAvailability(UUID tenantId, UUID actorId,
                                                 SubmitAvailabilityCommand cmd) {
        requireContext(tenantId, actorId);
        if (cmd == null) throw new IllegalArgumentException("command required");
        requireId(cmd.staffId(), "staffId");
        if (cmd.type() == null) throw new IllegalArgumentException("type required");
        requireId(cmd.startDate(), "startDate");
        requireId(cmd.endDate(), "endDate");

        if (cmd.endDate().isBefore(cmd.startDate())) {
            throw new OwnershipDomainException("endDate must be on or after startDate");
        }

        StaffAvailability created = availability.create(
                new AvailabilityRepository.CreateAvailabilityCommand(
                        tenantId, cmd.staffId(), cmd.type(),
                        cmd.startDate(), cmd.endDate(),
                        cmd.startTime(), cmd.endTime(), cmd.reason(),
                        actorId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE", "STAFF_AVAILABILITY", created.id(),
                new AuditChange(null, serializeAvailability(created)), now);
        timeline.record(tenantId, "STAFF_AVAILABILITY", created.id(),
                "crm.availability.submitted", "Availability submitted",
                "CRM_STAFF_AVAILABILITY", created.id(), actorId, now);
        return created;
    }

    /**
     * Approve a pending availability record (sets type to AVAILABLE).
     */
    @Transactional
    public StaffAvailability approveAvailability(UUID tenantId, UUID actorId, UUID availabilityId) {
        requireContext(tenantId, actorId);
        requireId(availabilityId, "availabilityId");

        StaffAvailability current = availability.findById(tenantId, availabilityId)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Availability record not found: " + availabilityId));

        if (current.type() == AvailabilityType.AVAILABLE) {
            throw new OwnershipDomainException("Availability is already APPROVED");
        }

        return availability.update(tenantId, availabilityId,
                new AvailabilityRepository.UpdateAvailabilityCommand(
                        AvailabilityType.AVAILABLE,
                        current.startDate(), current.endDate(),
                        current.startTime(), current.endTime(), current.reason(),
                        actorId, current.version()))
                .orElseThrow(() -> new OwnershipDomainException(
                        "Concurrent modification: " + availabilityId));
    }

    /**
     * Reject a pending availability record (sets type to UNAVAILABLE).
     */
    @Transactional
    public StaffAvailability rejectAvailability(UUID tenantId, UUID actorId, UUID availabilityId,
                                                 String reason) {
        requireContext(tenantId, actorId);
        requireId(availabilityId, "availabilityId");

        StaffAvailability current = availability.findById(tenantId, availabilityId)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Availability record not found: " + availabilityId));

        if (current.type() == AvailabilityType.UNAVAILABLE) {
            throw new OwnershipDomainException("Availability is already REJECTED");
        }

        String rejectionReason = reason != null && !reason.isBlank() ? reason.trim() : "Rejected";

        return availability.update(tenantId, availabilityId,
                new AvailabilityRepository.UpdateAvailabilityCommand(
                        AvailabilityType.UNAVAILABLE,
                        current.startDate(), current.endDate(),
                        current.startTime(), current.endTime(), rejectionReason,
                        actorId, current.version()))
                .orElseThrow(() -> new OwnershipDomainException(
                        "Concurrent modification: " + availabilityId));
    }

    /**
     * Query availability for a staff member within a date range.
     */
    public List<StaffAvailability> calendarQuery(UUID tenantId, UUID staffId,
                                                  LocalDate from, LocalDate to) {
        requireId(tenantId, "tenantId");
        requireId(staffId, "staffId");
        requireId(from, "from");
        requireId(to, "to");

        if (to.isBefore(from)) {
            throw new OwnershipDomainException("'to' date must be on or after 'from' date");
        }

        return List.copyOf(availability.findByStaffId(tenantId, staffId, from, to));
    }

    /**
     * Delete an availability record.
     */
    @Transactional
    public boolean deleteAvailability(UUID tenantId, UUID actorId, UUID availabilityId) {
        requireContext(tenantId, actorId);
        requireId(availabilityId, "availabilityId");

        StaffAvailability current = availability.findById(tenantId, availabilityId)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Availability record not found: " + availabilityId));

        boolean deleted = availability.delete(tenantId, availabilityId);

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "DELETE", "STAFF_AVAILABILITY", availabilityId,
                new AuditChange(serializeAvailability(current), null), now);
        timeline.record(tenantId, "STAFF_AVAILABILITY", availabilityId,
                "crm.availability.deleted", "Availability deleted",
                "CRM_STAFF_AVAILABILITY", availabilityId, actorId, now);
        return deleted;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void requireContext(UUID tenantId, UUID actorId) {
        requireId(tenantId, "tenantId");
        requireId(actorId, "actorId");
    }

    private static void requireId(Object value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " required");
    }

    private com.fasterxml.jackson.databind.JsonNode serializeAvailability(StaffAvailability a) {
        if (a == null) return null;
        var node = mapper.createObjectNode();
        node.put("id", a.id().toString());
        node.put("staffId", a.staffId().toString());
        node.put("type", a.type().name());
        node.put("startDate", a.startDate().toString());
        node.put("endDate", a.endDate().toString());
        return node;
    }

    // ── Command Records ──────────────────────────────────────────────────

    public record SubmitAvailabilityCommand(
            UUID staffId,
            AvailabilityType type,
            LocalDate startDate,
            LocalDate endDate,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            String reason) {}
}
