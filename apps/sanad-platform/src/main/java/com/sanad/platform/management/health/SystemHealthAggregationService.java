package com.sanad.platform.management.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Central System Health Aggregation Service (v20260816.1).
 *
 * <p>Executes all registered {@link SystemHealthContributor} checks, isolates
 * failures, and produces a unified {@link SystemHealthModel.SystemHealthSnapshot}.
 *
 * <p>CRITICAL — Failure Isolation:
 * <ul>
 *   <li>Each contributor's {@code checkHealth} is called in a try/catch.</li>
 *   <li>If a contributor throws, it is recorded as UNHEALTHY with the exception
 *       class name as the failure code.</li>
 *   <li>The aggregation NEVER throws — it always returns a snapshot.</li>
 *   <li>One SQL failure in CRM does NOT abort the entire aggregation.</li>
 *   <li>The aggregation does NOT run inside a shared transaction — each
 *       contributor manages its own transactional scope.</li>
 * </ul>
 *
 * <p>Health Score Calculation (deterministic, documented):
 * <ul>
 *   <li>HEALTHY = 100 weight</li>
 *   <li>DEGRADED = 50 weight</li>
 *   <li>UNHEALTHY = 0 weight</li>
 *   <li>UNKNOWN = excluded from score (if ALL are UNKNOWN, score = 0)</li>
 *   <li>Score = round(sum(weights) / count(non-unknown components))</li>
 * </ul>
 */
@Service
public class SystemHealthAggregationService {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthAggregationService.class);

    private final SystemHealthContributorRegistry registry;

    public SystemHealthAggregationService(SystemHealthContributorRegistry registry) {
        this.registry = registry;
    }

    /**
     * Aggregate health from all registered contributors.
     *
     * @param tenantId the tenant to check (may be null for platform-level)
     * @return a complete health snapshot
     */
    public SystemHealthModel.SystemHealthSnapshot aggregate(UUID tenantId) {
        List<SystemHealthContributor> contributors = registry.sortedContributors();
        List<SystemHealthModel.SystemHealthComponent> components = new ArrayList<>(contributors.size());

        for (SystemHealthContributor contributor : contributors) {
            SystemHealthModel.SystemHealthComponent component;
            try {
                component = contributor.checkHealth(tenantId);
                if (component == null) {
                    // Contributor returned null — treat as UNKNOWN
                    component = unknownComponent(contributor, "Contributor returned null");
                }
            } catch (Exception e) {
                log.warn("Health contributor {} threw exception: {}", contributor.componentId(), e.getMessage());
                component = unhealthyFromException(contributor, e);
            }
            components.add(component);
        }

        return buildSnapshot(components);
    }

    private SystemHealthModel.SystemHealthSnapshot buildSnapshot(List<SystemHealthModel.SystemHealthComponent> components) {
        int total = components.size();
        int healthy = 0, degraded = 0, unhealthy = 0, unknown = 0;
        int scoreSum = 0;
        int scoreCount = 0;
        SystemHealthModel.SystemHealthStatus overall = SystemHealthModel.SystemHealthStatus.HEALTHY;

        for (SystemHealthModel.SystemHealthComponent c : components) {
            var status = c.status();
            switch (status) {
                case HEALTHY -> { healthy++; scoreSum += 100; scoreCount++; }
                case DEGRADED -> { degraded++; scoreSum += 50; scoreCount++; }
                case UNHEALTHY -> { unhealthy++; scoreSum += 0; scoreCount++; }
                case UNKNOWN -> unknown++;
            }
            overall = SystemHealthModel.SystemHealthStatus.worst(overall, status);
        }

        int healthScore = scoreCount > 0 ? Math.round((float) scoreSum / scoreCount) : 0;

        return new SystemHealthModel.SystemHealthSnapshot(
                overall,
                healthScore,
                Instant.now(),
                total,
                healthy,
                degraded,
                unhealthy,
                unknown,
                components
        );
    }

    private SystemHealthModel.SystemHealthComponent unknownComponent(SystemHealthContributor c, String message) {
        return new SystemHealthModel.SystemHealthComponent(
                c.componentId(),
                c.componentType(),
                c.displayName(),
                SystemHealthModel.SystemHealthStatus.UNKNOWN,
                message,
                Instant.now(),
                0,
                java.util.Map.of(),
                null,
                "CONTRIBUTOR_NULL",
                SystemHealthModel.SystemHealthComponent.Severity.WARN
        );
    }

    private SystemHealthModel.SystemHealthComponent unhealthyFromException(SystemHealthContributor c, Exception e) {
        return new SystemHealthModel.SystemHealthComponent(
                c.componentId(),
                c.componentType(),
                c.displayName(),
                SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                "Health check failed: " + e.getClass().getSimpleName(),
                Instant.now(),
                0,
                java.util.Map.of(), // do NOT expose stack traces
                null,
                e.getClass().getSimpleName().toUpperCase(),
                SystemHealthModel.SystemHealthComponent.Severity.ERROR
        );
    }
}
