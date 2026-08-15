package com.sanad.platform.management.health;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical System Health domain model (v20260816.1).
 *
 * <p>Defines the unified health status, component, and snapshot types used
 * by the Central System Health Platform. Every contributor returns
 * {@link SystemHealthComponent} instances; the aggregation service combines
 * them into a {@link SystemHealthSnapshot}.
 */
public final class SystemHealthModel {

    private SystemHealthModel() {}

    /** Canonical health states. Ordered by severity (HEALTHY < DEGRADED < UNHEALTHY < UNKNOWN). */
    public enum SystemHealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN;

        /** Weight used in health-score calculation. HEALTHY=100, DEGRADED=50, UNHEALTHY=0, UNKNOWN excluded. */
        public int weight() {
            return switch (this) {
                case HEALTHY -> 100;
                case DEGRADED -> 50;
                case UNHEALTHY -> 0;
                case UNKNOWN -> -1; // excluded from score
            };
        }

        /** Return the worst (highest ordinal) of two statuses, treating UNKNOWN as less severe than HEALTHY. */
        public static SystemHealthStatus worst(SystemHealthStatus a, SystemHealthStatus b) {
            if (a == null) return b;
            if (b == null) return a;
            if (a == UNHEALTHY || b == UNHEALTHY) return UNHEALTHY;
            if (a == DEGRADED || b == DEGRADED) return DEGRADED;
            if (a == UNKNOWN || b == UNKNOWN) return UNKNOWN;
            return HEALTHY;
        }
    }

    /**
     * A single health component result (e.g. PostgreSQL, CRM, Workflow).
     */
    public record SystemHealthComponent(
            String componentId,
            String componentType,
            String displayName,
            SystemHealthStatus status,
            String message,
            Instant checkedAt,
            long latencyMs,
            Map<String, Object> details,
            Instant lastHealthyAt,
            String failureCode,
            Severity severity
    ) {
        public enum Severity { INFO, WARN, ERROR }
    }

    /**
     * Aggregated health snapshot returned by the Central API.
     */
    public record SystemHealthSnapshot(
            SystemHealthStatus overallStatus,
            int healthScore,
            Instant checkedAt,
            int totalComponents,
            int healthyComponents,
            int degradedComponents,
            int unhealthyComponents,
            int unknownComponents,
            java.util.List<SystemHealthComponent> components
    ) {}
}
