package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.SalesTeam;
import com.sanad.platform.crm.ownership.domain.SalesTeamRepository;
import com.sanad.platform.crm.ownership.domain.TeamNotFoundException;
import com.sanad.platform.crm.ownership.domain.TeamStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service for CRM-008 Team Management.
 *
 * <p>Extends the existing SalesTeamUseCases with additional team lifecycle operations:
 * activate, search, and get details. Delegates core CRUD to SalesTeamUseCases.
 */
public class TeamManagementUseCases {

    private final SalesTeamRepository teams;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public TeamManagementUseCases(SalesTeamRepository teams,
                                  AuditPort audit,
                                  TimelineEventPort timeline,
                                  ObjectMapper mapper) {
        this.teams = teams;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    /**
     * Activate an archived team.
     */
    @Transactional
    public SalesTeam activateTeam(UUID tenantId, UUID actorId, UUID teamId) {
        requireContext(tenantId, actorId);
        requireId(teamId, "teamId");

        SalesTeam current = teams.findById(tenantId, teamId)
                .orElseThrow(() -> new TeamNotFoundException(tenantId, teamId));

        if (current.isActive()) {
            throw new OwnershipDomainException("Team is already ACTIVE: " + teamId);
        }

        teams.save(new SalesTeam(
                current.id(),
                tenantId,
                current.code(),
                current.displayName(),
                current.description(),
                TeamStatus.ACTIVE,
                current.managerUserId(),
                current.defaultQueueId(),
                current.defaultTerritoryId(),
                current.createdAt(),
                Instant.now(),
                current.createdBy(),
                actorId));

        SalesTeam activated = teams.findById(tenantId, teamId)
                .orElseThrow(() -> new TeamNotFoundException(tenantId, teamId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "ACTIVATE", "SALES_TEAM", teamId,
                new AuditChange(serializeTeam(current), serializeTeam(activated)), now);
        timeline.record(tenantId, "SALES_TEAM", teamId,
                "crm.team.activated", "Team activated",
                "CRM_SALES_TEAM", teamId, actorId, now);
        return activated;
    }

    /**
     * Get team details with membership count.
     */
    public SalesTeam getTeamDetails(UUID tenantId, UUID teamId) {
        requireId(tenantId, "tenantId");
        requireId(teamId, "teamId");
        return teams.findById(tenantId, teamId)
                .orElseThrow(() -> new TeamNotFoundException(tenantId, teamId));
    }

    /**
     * Search teams by status with optional name filter.
     */
    public List<SalesTeam> searchTeams(UUID tenantId, TeamStatus status, String nameFilter) {
        requireId(tenantId, "tenantId");
        if (status == null) {
            throw new IllegalArgumentException("status required");
        }
        List<SalesTeam> results = teams.findByTenant(tenantId, status);
        if (nameFilter != null && !nameFilter.isBlank()) {
            String lower = nameFilter.trim().toLowerCase();
            results = results.stream()
                    .filter(t -> t.displayName().toLowerCase().contains(lower)
                            || t.code().toLowerCase().contains(lower))
                    .toList();
        }
        return List.copyOf(results);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void requireContext(UUID tenantId, UUID actorId) {
        requireId(tenantId, "tenantId");
        requireId(actorId, "actorId");
    }

    private static void requireId(UUID value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " required");
        }
    }

    private JsonNode serializeTeam(SalesTeam team) {
        if (team == null) return null;
        var node = mapper.createObjectNode();
        node.put("id", team.id().toString());
        node.put("tenantId", team.tenantId().toString());
        node.put("code", team.code());
        node.put("displayName", team.displayName());
        node.put("description", team.description());
        node.put("status", team.status().name());
        putUuid(node, "managerUserId", team.managerUserId());
        putUuid(node, "defaultQueueId", team.defaultQueueId());
        putUuid(node, "defaultTerritoryId", team.defaultTerritoryId());
        return node;
    }

    private static void putUuid(com.fasterxml.jackson.databind.node.ObjectNode node,
                                String field, UUID value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value.toString());
        }
    }
}
