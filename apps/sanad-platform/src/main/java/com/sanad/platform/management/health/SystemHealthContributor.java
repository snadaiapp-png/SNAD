package com.sanad.platform.management.health;

import java.util.UUID;

/**
 * System Health Contributor contract (v20260816.1).
 *
 * <p>Every component that wants to participate in the Central System Health
 * Platform must implement this interface and be annotated with
 * {@code @Component} (or {@code @Service}). The
 * {@link SystemHealthContributorRegistry} auto-discovers all Spring beans
 * implementing this contract.
 *
 * <p>This is the <b>adapter/contributor pattern</b>: the Central Health core
 * NEVER hard-codes module names. Adding a new module (ERP/HRM/POS) requires
 * only ONE new {@code @Component} class — no modification to
 * {@link SystemHealthAggregationService} or any other core file.
 *
 * <p>Requirements for implementations:
 * <ul>
 *   <li><b>tenant-aware</b>: take {@code tenantId} and filter by it</li>
 *   <li><b>read-only</b>: never mutate state</li>
 *   <li><b>deterministic</b>: same input → same output</li>
 *   <li><b>bounded execution time</b>: should complete in &lt; 5 seconds</li>
 *   <li><b>no secrets</b>: details map must NOT contain passwords, tokens, URLs</li>
 *   <li><b>failure isolation</b>: if the check fails, return UNHEALTHY — do NOT
 *       throw an exception (the aggregation service catches exceptions as a
 *       safety net, but contributors should handle their own errors)</li>
 * </ul>
 */
public interface SystemHealthContributor {

    /**
     * Unique identifier for this component (e.g. "postgresql", "crm", "workflow").
     * Must be stable across restarts.
     */
    String componentId();

    /**
     * Category type (e.g. "PLATFORM", "MODULE", "OPERATIONS", "GOVERNANCE").
     * Used for grouping in the UI.
     */
    String componentType();

    /**
     * Human-readable display name (English — the frontend localizes to Arabic).
     */
    String displayName();

    /**
     * Execute the health check for the given tenant.
     *
     * <p>If the check fails, return a {@link SystemHealthModel.SystemHealthComponent}
     * with status UNHEALTHY — do NOT throw. The aggregation service has a
     * safety-net catch for exceptions, but contributors should self-isolate.
     *
     * @param tenantId the tenant to check (may be null for platform-level checks)
     * @return the health component result
     */
    SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId);
}
