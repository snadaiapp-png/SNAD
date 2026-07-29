package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.CapacityManagementUseCases;
import com.sanad.platform.crm.ownership.application.CapacityManagementUseCases.AdjustCapacityCommand;
import com.sanad.platform.crm.ownership.application.CapacityManagementUseCases.CreateCapacityPlanCommand;
import com.sanad.platform.crm.ownership.domain.capacity.CapacityPlan;
import com.sanad.platform.crm.ownership.web.TeamModels.AdjustCapacityRequest;
import com.sanad.platform.crm.ownership.web.TeamModels.CreateCapacityPlanRequest;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V1 REST controller for CRM Capacity Plans.
 *
 * <p>Mounted under {@code /api/v1/crm/capacity}.
 */
@RestController
@RequestMapping("/api/v1/crm/capacity")
public class CapacityController {

    private final CapacityManagementUseCases capacity;

    public CapacityController(CapacityManagementUseCases capacity) {
        this.capacity = capacity;
    }

    @RequireCapability("CRM.CAPACITY.READ")
    @GetMapping
    public List<Map<String, Object>> listPlans(
            Authentication authentication,
            @RequestParam UUID teamId) {
        UUID tenantId = tenantId(authentication);
        return capacity.listCapacityPlans(tenantId, teamId)
                .stream().map(this::toRow).toList();
    }

    @RequireCapability("CRM.CAPACITY.READ")
    @GetMapping("/{planId}")
    public Map<String, Object> getPlan(Authentication authentication,
                                        @PathVariable UUID planId) {
        return toRow(capacity.getCapacityPlan(tenantId(authentication), planId));
    }

    @RequireCapability("CRM.CAPACITY.MANAGE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPlan(
            Authentication authentication,
            @Valid @RequestBody CreateCapacityPlanRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        CapacityPlan created = capacity.createCapacityPlan(tenantId, actorId,
                new CreateCapacityPlanCommand(
                        request.teamId(),
                        request.periodStart(),
                        request.periodEnd(),
                        request.maxCapacity()));

        return ResponseEntity.status(HttpStatus.CREATED).body(toRow(created));
    }

    @RequireCapability("CRM.CAPACITY.MANAGE")
    @PatchMapping("/{planId}")
    public Map<String, Object> adjustCapacity(
            Authentication authentication,
            @PathVariable UUID planId,
            @Valid @RequestBody AdjustCapacityRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        CapacityPlan updated = capacity.adjustCapacity(tenantId, actorId, planId,
                new AdjustCapacityCommand(
                        request.maxCapacity(),
                        request.allocatedCapacity(),
                        null));

        return toRow(updated);
    }

    @RequireCapability("CRM.CAPACITY.READ")
    @GetMapping("/forecast")
    public Map<String, Object> forecastCapacity(
            Authentication authentication,
            @RequestParam UUID teamId,
            @RequestParam String periodStart,
            @RequestParam String periodEnd) {
        UUID tenantId = tenantId(authentication);
        CapacityManagementUseCases.CapacityForecast forecast = capacity.forecastCapacity(
                tenantId, teamId, LocalDate.parse(periodStart), LocalDate.parse(periodEnd));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("team_id", forecast.teamId());
        row.put("period_start", forecast.periodStart().toString());
        row.put("period_end", forecast.periodEnd().toString());
        row.put("forecasted_max_capacity", forecast.forecastedMaxCapacity());
        row.put("forecasted_allocated_capacity", forecast.forecastedAllocatedCapacity());
        row.put("forecasted_utilization", forecast.forecastedUtilization());
        return row;
    }

    private Map<String, Object> toRow(CapacityPlan p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.id());
        row.put("tenant_id", p.tenantId());
        row.put("team_id", p.teamId());
        row.put("period_start", p.periodStart() != null ? p.periodStart().toString() : null);
        row.put("period_end", p.periodEnd() != null ? p.periodEnd().toString() : null);
        row.put("max_capacity", p.maxCapacity());
        row.put("allocated_capacity", p.allocatedCapacity());
        row.put("remaining_capacity", p.remainingCapacity());
        row.put("utilization_percentage", p.utilizationPercentage());
        row.put("status", p.status().name());
        row.put("created_by", p.createdBy());
        row.put("updated_by", p.updatedBy());
        row.put("created_at", toIso(p.createdAt()));
        row.put("updated_at", toIso(p.updatedAt()));
        row.put("version", p.version());
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
