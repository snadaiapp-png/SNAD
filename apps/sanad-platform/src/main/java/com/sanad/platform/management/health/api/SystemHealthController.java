package com.sanad.platform.management.health.api;

import com.sanad.platform.management.health.SystemHealthAggregationService;
import com.sanad.platform.management.health.SystemHealthModel.SystemHealthSnapshot;
import com.sanad.platform.management.health.SystemHealthContributorRegistry;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;

/**
 * Central System Health API (v20260816.1).
 *
 * <p>Endpoints under {@code /api/v1/management/system-health}:
 * <ul>
 *   <li>{@code GET /} — returns the full SystemHealthSnapshot</li>
 *   <li>{@code GET /components} — returns the list of contributor IDs</li>
 *   <li>{@code GET /components/{componentId}} — returns a single component's health</li>
 * </ul>
 *
 * <p>All endpoints require {@code EXECUTIVE_COMMAND_CENTER.VIEW} capability.
 * Tenant-scoped via {@link SecurityContextUtils#tenantId}.
 */
@RestController
@RequestMapping("/api/v1/management/system-health")
public class SystemHealthController {

    private final SystemHealthAggregationService aggregationService;
    private final SystemHealthContributorRegistry registry;

    public SystemHealthController(
            SystemHealthAggregationService aggregationService,
            SystemHealthContributorRegistry registry) {
        this.aggregationService = aggregationService;
        this.registry = registry;
    }

    @GetMapping
    @RequireCapability("EXECUTIVE_COMMAND_CENTER.VIEW")
    public ResponseEntity<SystemHealthSnapshot> getSystemHealth(Authentication auth) {
        UUID tenantId = tenantId(auth);
        return ResponseEntity.ok(aggregationService.aggregate(tenantId));
    }

    @GetMapping("/components")
    @RequireCapability("EXECUTIVE_COMMAND_CENTER.VIEW")
    public ResponseEntity<List<String>> listContributors() {
        return ResponseEntity.ok(registry.allContributorIds());
    }

    @GetMapping("/components/{componentId}")
    @RequireCapability("EXECUTIVE_COMMAND_CENTER.VIEW")
    public ResponseEntity<SystemHealthSnapshot> getComponentHealth(
            Authentication auth,
            @PathVariable String componentId
    ) {
        UUID tenantId = tenantId(auth);
        // Aggregate all but return only the requested component
        var snapshot = aggregationService.aggregate(tenantId);
        var filtered = snapshot.components().stream()
                .filter(c -> c.componentId().equals(componentId))
                .toList();
        return ResponseEntity.ok(new SystemHealthSnapshot(
                filtered.isEmpty()
                        ? snapshot.overallStatus()
                        : filtered.get(0).status(),
                snapshot.healthScore(),
                snapshot.checkedAt(),
                filtered.size(),
                (int) filtered.stream().filter(c -> c.status().name().equals("HEALTHY")).count(),
                (int) filtered.stream().filter(c -> c.status().name().equals("DEGRADED")).count(),
                (int) filtered.stream().filter(c -> c.status().name().equals("UNHEALTHY")).count(),
                (int) filtered.stream().filter(c -> c.status().name().equals("UNKNOWN")).count(),
                filtered
        ));
    }
}
