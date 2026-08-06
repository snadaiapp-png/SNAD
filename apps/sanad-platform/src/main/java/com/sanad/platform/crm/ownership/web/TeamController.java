package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.SalesTeamUseCases;
import com.sanad.platform.crm.ownership.application.TeamManagementUseCases;
import com.sanad.platform.crm.ownership.domain.SalesTeam;
import com.sanad.platform.crm.ownership.domain.TeamStatus;
import com.sanad.platform.crm.ownership.web.TeamModels.CreateTeamRequest;
import com.sanad.platform.crm.ownership.web.TeamModels.UpdateTeamRequest;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V1 REST controller for CRM Teams.
 *
 * <p>Mounted under {@code /api/v1/crm/teams}.
 */
@RestController
@RequestMapping("/api/v1/crm/teams")
public class TeamController {

    private final SalesTeamUseCases salesTeams;
    private final TeamManagementUseCases teamManagement;

    public TeamController(SalesTeamUseCases salesTeams, TeamManagementUseCases teamManagement) {
        this.salesTeams = salesTeams;
        this.teamManagement = teamManagement;
    }

    @RequireCapability("CRM.TEAM.READ")
    @GetMapping
    public List<Map<String, Object>> listTeams(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        UUID tenantId = tenantId(authentication);
        TeamStatus teamStatus = status != null ? TeamStatus.valueOf(status.toUpperCase()) : TeamStatus.ACTIVE;
        List<SalesTeam> teams = teamManagement.searchTeams(tenantId, teamStatus, search);
        return teams.stream().map(this::toRow).toList();
    }

    @RequireCapability("CRM.TEAM.READ")
    @GetMapping("/{teamId}")
    public Map<String, Object> getTeam(Authentication authentication, @PathVariable UUID teamId) {
        return toRow(teamManagement.getTeamDetails(tenantId(authentication), teamId));
    }

    @RequireCapability("CRM.TEAM.WRITE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTeam(
            Authentication authentication,
            @Valid @RequestBody CreateTeamRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        SalesTeam created = salesTeams.createTeam(tenantId, actorId,
                new SalesTeamUseCases.CreateTeamCommand(
                        request.code(),
                        request.displayName(),
                        request.description(),
                        request.managerUserId(),
                        request.defaultQueueId(),
                        request.defaultTerritoryId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(toRow(created));
    }

    @RequireCapability("CRM.TEAM.WRITE")
    @PatchMapping("/{teamId}")
    public Map<String, Object> updateTeam(
            Authentication authentication,
            @PathVariable UUID teamId,
            @Valid @RequestBody UpdateTeamRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        SalesTeam current = salesTeams.getTeam(tenantId, teamId);
        TeamStatus status = request.status() != null
                ? TeamStatus.valueOf(request.status().toUpperCase()) : current.status();

        SalesTeam updated = salesTeams.updateTeam(tenantId, actorId, teamId,
                new SalesTeamUseCases.UpdateTeamCommand(
                        request.displayName(),
                        request.description(),
                        status,
                        request.managerUserId(),
                        request.defaultQueueId(),
                        request.defaultTerritoryId()));

        return toRow(updated);
    }

    @RequireCapability("CRM.TEAM.WRITE")
    @PatchMapping("/{teamId}/archive")
    public Map<String, Object> archiveTeam(Authentication authentication, @PathVariable UUID teamId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        return toRow(salesTeams.archiveTeam(tenantId, actorId, teamId));
    }

    @RequireCapability("CRM.TEAM.WRITE")
    @PatchMapping("/{teamId}/activate")
    public Map<String, Object> activateTeam(Authentication authentication, @PathVariable UUID teamId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        return toRow(teamManagement.activateTeam(tenantId, actorId, teamId));
    }

    private Map<String, Object> toRow(SalesTeam t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.id());
        row.put("tenant_id", t.tenantId());
        row.put("code", t.code());
        row.put("display_name", t.displayName());
        row.put("description", t.description());
        row.put("status", t.status().name());
        row.put("manager_user_id", t.managerUserId());
        row.put("default_queue_id", t.defaultQueueId());
        row.put("default_territory_id", t.defaultTerritoryId());
        row.put("created_by", t.createdBy());
        row.put("updated_by", t.updatedBy());
        row.put("created_at", toIso(t.createdAt()));
        row.put("updated_at", toIso(t.updatedAt()));
        return row;
    }

    private static String toIso(Instant v) {
        return v == null ? null : v.toString();
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return context(authentication, "user_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }
}
