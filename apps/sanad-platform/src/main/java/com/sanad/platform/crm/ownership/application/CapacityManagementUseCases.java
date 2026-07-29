package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.SalesTeam;
import com.sanad.platform.crm.ownership.domain.SalesTeamRepository;
import com.sanad.platform.crm.ownership.domain.TeamNotFoundException;
import com.sanad.platform.crm.ownership.domain.capacity.CapacityPlan;
import com.sanad.platform.crm.ownership.domain.capacity.CapacityRepository;
import com.sanad.platform.crm.ownership.domain.capacity.CapacityStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application service for CRM-008 Capacity Management.
 *
 * <p>Manages capacity planning including creation, adjustment, forecasting,
 * and activation. Enforces business rules for capacity limits and period
 * overlap prevention.
 */
public class CapacityManagementUseCases {

    private final CapacityRepository capacity;
    private final SalesTeamRepository teams;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public CapacityManagementUseCases(CapacityRepository capacity,
                                      SalesTeamRepository teams,
                                      AuditPort audit,
                                      TimelineEventPort timeline,
                                      ObjectMapper mapper) {
        this.capacity = capacity;
        this.teams = teams;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    /**
     * Create a new capacity plan for a team and period.
     */
    @Transactional
    public CapacityPlan createCapacityPlan(UUID tenantId, UUID actorId, CreateCapacityPlanCommand cmd) {
        requireContext(tenantId, actorId);
        if (cmd == null) throw new IllegalArgumentException("command required");
        requireId(cmd.teamId(), "teamId");
        requireId(cmd.periodStart(), "periodStart");
        requireId(cmd.periodEnd(), "periodEnd");

        // Validate team exists
        teams.findById(tenantId, cmd.teamId())
                .orElseThrow(() -> new TeamNotFoundException(tenantId, cmd.teamId()));

        if (cmd.periodEnd().isBefore(cmd.periodStart())) {
            throw new OwnershipDomainException("periodEnd must be on or after periodStart");
        }
        if (cmd.maxCapacity() <= 0) {
            throw new OwnershipDomainException("maxCapacity must be positive");
        }

        // Check for overlapping active plans
        CapacityPlan existing = capacity.findActiveByTeamAndPeriod(
                tenantId, cmd.teamId(), cmd.periodStart()).orElse(null);
        if (existing != null) {
            throw new OwnershipDomainException(
                    "Active capacity plan already exists for this team and period");
        }

        CapacityPlan created = capacity.create(new CapacityRepository.CreateCapacityPlanCommand(
                tenantId, cmd.teamId(), cmd.periodStart(), cmd.periodEnd(),
                cmd.maxCapacity(), actorId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE", "CAPACITY_PLAN", created.id(),
                new AuditChange(null, serializePlan(created)), now);
        timeline.record(tenantId, "CAPACITY_PLAN", created.id(),
                "crm.capacity.created", "Capacity plan created",
                "CRM_CAPACITY_PLAN", created.id(), actorId, now);
        return created;
    }

    /**
     * Adjust capacity plan allocation.
     */
    @Transactional
    public CapacityPlan adjustCapacity(UUID tenantId, UUID actorId, UUID planId,
                                        AdjustCapacityCommand cmd) {
        requireContext(tenantId, actorId);
        requireId(planId, "planId");
        if (cmd == null) throw new IllegalArgumentException("command required");

        CapacityPlan current = capacity.findById(tenantId, planId)
                .orElseThrow(() -> new OwnershipDomainException("Capacity plan not found: " + planId));

        if (current.isCompleted()) {
            throw new OwnershipDomainException("Cannot adjust COMPLETED capacity plan");
        }

        Integer maxCapacity = cmd.maxCapacity() > 0 ? cmd.maxCapacity() : current.maxCapacity();
        Integer allocatedCapacity = cmd.allocatedCapacity() >= 0
                ? cmd.allocatedCapacity() : current.allocatedCapacity();
        CapacityStatus status = cmd.status() != null ? cmd.status() : current.status();

        if (allocatedCapacity > maxCapacity) {
            throw new OwnershipDomainException("allocatedCapacity cannot exceed maxCapacity");
        }

        return capacity.update(tenantId, planId, new CapacityRepository.UpdateCapacityPlanCommand(
                maxCapacity, allocatedCapacity, status, actorId, current.version()))
                .orElseThrow(() -> new OwnershipDomainException("Concurrent modification: " + planId));
    }

    /**
     * Forecast capacity for a future period based on current trends.
     */
    public CapacityForecast forecastCapacity(UUID tenantId, UUID teamId, LocalDate periodStart,
                                              LocalDate periodEnd) {
        requireId(tenantId, "tenantId");
        requireId(teamId, "teamId");
        requireId(periodStart, "periodStart");
        requireId(periodEnd, "periodEnd");

        teams.findById(tenantId, teamId)
                .orElseThrow(() -> new TeamNotFoundException(tenantId, teamId));

        List<CapacityPlan> history = capacity.findByTeamId(tenantId, teamId);

        int avgMaxCapacity = (int) history.stream()
                .mapToInt(CapacityPlan::maxCapacity)
                .average()
                .orElse(0);

        int avgAllocated = (int) history.stream()
                .mapToInt(CapacityPlan::allocatedCapacity)
                .average()
                .orElse(0);

        return new CapacityForecast(teamId, periodStart, periodEnd,
                avgMaxCapacity, avgAllocated,
                avgMaxCapacity > 0 ? (double) avgAllocated / avgMaxCapacity * 100 : 0);
    }

    /**
     * List capacity plans for a team.
     */
    public List<CapacityPlan> listCapacityPlans(UUID tenantId, UUID teamId) {
        requireId(tenantId, "tenantId");
        requireId(teamId, "teamId");
        return List.copyOf(capacity.findByTeamId(tenantId, teamId));
    }

    /**
     * Get a specific capacity plan.
     */
    public CapacityPlan getCapacityPlan(UUID tenantId, UUID planId) {
        requireId(tenantId, "tenantId");
        requireId(planId, "planId");
        return capacity.findById(tenantId, planId)
                .orElseThrow(() -> new OwnershipDomainException("Capacity plan not found: " + planId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void requireContext(UUID tenantId, UUID actorId) {
        requireId(tenantId, "tenantId");
        requireId(actorId, "actorId");
    }

    private static void requireId(Object value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " required");
    }

    private com.fasterxml.jackson.databind.JsonNode serializePlan(CapacityPlan p) {
        if (p == null) return null;
        var node = mapper.createObjectNode();
        node.put("id", p.id().toString());
        node.put("teamId", p.teamId().toString());
        node.put("maxCapacity", p.maxCapacity());
        node.put("allocatedCapacity", p.allocatedCapacity());
        node.put("status", p.status().name());
        return node;
    }

    // ── Command Records ──────────────────────────────────────────────────

    public record CreateCapacityPlanCommand(
            UUID teamId,
            LocalDate periodStart,
            LocalDate periodEnd,
            int maxCapacity) {}

    public record AdjustCapacityCommand(
            Integer maxCapacity,
            Integer allocatedCapacity,
            CapacityStatus status) {}

    public record CapacityForecast(
            UUID teamId,
            LocalDate periodStart,
            LocalDate periodEnd,
            int forecastedMaxCapacity,
            int forecastedAllocatedCapacity,
            double forecastedUtilization) {}
}
