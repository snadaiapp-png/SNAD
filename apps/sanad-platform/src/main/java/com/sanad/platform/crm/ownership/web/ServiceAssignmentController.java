package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.ServiceAssignmentUseCases;
import com.sanad.platform.crm.ownership.application.ServiceAssignmentUseCases.AssignServiceCommand;
import com.sanad.platform.crm.ownership.domain.service.ServiceAssignment;
import com.sanad.platform.crm.ownership.web.TeamModels.AssignServiceRequest;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V1 REST controller for CRM Service Assignments.
 *
 * <p>Mounted under {@code /api/v1/crm/service-assignments}.
 */
@RestController
@RequestMapping("/api/v1/crm/service-assignments")
public class ServiceAssignmentController {

    private final ServiceAssignmentUseCases serviceAssignments;

    public ServiceAssignmentController(ServiceAssignmentUseCases serviceAssignments) {
        this.serviceAssignments = serviceAssignments;
    }

    @RequireCapability("CRM.ASSIGNMENT.READ")
    @GetMapping
    public List<Map<String, Object>> listAssignments(
            Authentication authentication,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID serviceId) {
        UUID tenantId = tenantId(authentication);
        if (teamId != null) {
            return serviceAssignments.listByTeam(tenantId, teamId)
                    .stream().map(this::toRow).toList();
        }
        if (serviceId != null) {
            return serviceAssignments.listByService(tenantId, serviceId)
                    .stream().map(this::toRow).toList();
        }
        return List.of();
    }

    @RequireCapability("CRM.ASSIGNMENT.READ")
    @GetMapping("/{assignmentId}")
    public Map<String, Object> getAssignment(Authentication authentication,
                                              @PathVariable UUID assignmentId) {
        return toRow(serviceAssignments.getServiceAssignment(tenantId(authentication), assignmentId));
    }

    @RequireCapability("CRM.ASSIGNMENT.MANAGE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> assignService(
            Authentication authentication,
            @Valid @RequestBody AssignServiceRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        ServiceAssignment created = serviceAssignments.assignService(tenantId, actorId,
                new AssignServiceCommand(
                        request.teamId(),
                        request.serviceId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(toRow(created));
    }

    @RequireCapability("CRM.ASSIGNMENT.MANAGE")
    @PatchMapping("/{assignmentId}/reassign")
    public Map<String, Object> reassignService(
            Authentication authentication,
            @PathVariable UUID assignmentId,
            @RequestBody Map<String, UUID> body) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        UUID newTeamId = body.get("new_team_id");
        return toRow(serviceAssignments.reassignService(tenantId, actorId, assignmentId, newTeamId));
    }

    @RequireCapability("CRM.ASSIGNMENT.MANAGE")
    @PatchMapping("/{assignmentId}/complete")
    public Map<String, Object> completeService(Authentication authentication,
                                                @PathVariable UUID assignmentId) {
        return toRow(serviceAssignments.completeService(tenantId(authentication), userId(authentication), assignmentId));
    }

    @RequireCapability("CRM.ASSIGNMENT.MANAGE")
    @PatchMapping("/{assignmentId}/cancel")
    public Map<String, Object> cancelService(Authentication authentication,
                                              @PathVariable UUID assignmentId) {
        return toRow(serviceAssignments.cancelService(tenantId(authentication), userId(authentication), assignmentId));
    }

    private Map<String, Object> toRow(ServiceAssignment a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.id());
        row.put("tenant_id", a.tenantId());
        row.put("team_id", a.teamId());
        row.put("service_id", a.serviceId());
        row.put("status", a.status().name());
        row.put("created_by", a.createdBy());
        row.put("updated_by", a.updatedBy());
        row.put("created_at", toIso(a.createdAt()));
        row.put("updated_at", toIso(a.updatedAt()));
        row.put("version", a.version());
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
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }
}
