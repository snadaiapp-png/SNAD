package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequestRepository;
import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import com.sanad.platform.workflow.domain.WorkflowStepInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SLA Monitoring Scheduler for the Workflow Engine.
 *
 * <p>Periodically invokes {@link WorkflowMonitoringService#checkAllSlaBreaches(UUID)}
 * for every ACTIVE tenant. Designed to be:
 *
 * <ul>
 *   <li><strong>Idempotent</strong> — calling it N times produces the same result as 1 time.
 *       The monitoring service is read-only; it does not mutate workflow state.</li>
 *   <li><strong>Multi-tenant safe</strong> — iterates only ACTIVE tenants and runs each in
 *       its own short transaction to prevent one slow/failing tenant from blocking others.</li>
 *   <li><strong>FK-safe</strong> — no system-generated audit records (the monitoring service
 *       is read-only, so no actor_user_id is required).</li>
 *   <li><strong>Failure-isolated</strong> — a per-tenant failure is logged and swallowed;
 *       the scheduler continues with the next tenant. State is never corrupted.</li>
 *   <li><strong>Conditionally enabled</strong> — only runs when {@code scheduling.enabled=true}
 *       (inherited from {@link com.sanad.platform.config.SchedulingConfig}). In tests,
 *       the bean is created but {@code @Scheduled} methods are NOT invoked automatically.</li>
 * </ul>
 *
 * <p><strong>Testability:</strong> the {@link #runSlaCheck()} method is public so unit/integration
 * tests can invoke it directly to verify behavior without relying on Spring's scheduler.
 */
@Component
public class WorkflowSlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSlaScheduler.class);

    /** Maximum number of tenants processed per scheduler tick (safety bound). */
    private static final int MAX_TENANTS_PER_TICK = 200;

    private final WorkflowMonitoringService monitoringService;
    private final JdbcTemplate jdbc;
    private final boolean schedulerEnabled;

    @Autowired
    public WorkflowSlaScheduler(
            WorkflowMonitoringService monitoringService,
            JdbcTemplate jdbc,
            @Value("${scheduling.enabled:false}") boolean schedulerEnabled) {
        this.monitoringService = monitoringService;
        this.jdbc = jdbc;
        this.schedulerEnabled = schedulerEnabled;
    }

    /**
     * Periodically check SLA breaches for every active tenant.
     *
     * <p>Uses a fixed delay of 5 minutes (300_000 ms) with an initial delay of 60 seconds
     * to allow the application to fully start before the first check.
     *
     * <p>The delay is intentionally conservative to avoid overlapping executions on
     * slow Render instances. The delay can be overridden via {@code sanad.workflow.sla.interval-ms}.
     */
    @Scheduled(fixedDelayString = "${sanad.workflow.sla.interval-ms:300000}",
               initialDelayString = "${sanad.workflow.sla.initial-delay-ms:60000}")
    public void runSlaCheck() {
        if (!schedulerEnabled) {
            log.debug("WorkflowSlaScheduler: scheduling.enabled=false, skipping tick");
            return;
        }
        runSlaCheckInternal();
    }

    /**
     * Testable entry point — does NOT check {@code scheduling.enabled}.
     * Tests call this directly to verify behavior.
     */
    @Transactional(propagation = Propagation.NEVER)
    public SlaCheckResult runSlaCheckInternal() {
        long startedAt = System.currentTimeMillis();
        int tenantsProcessed = 0;
        int tenantsFailed = 0;
        int totalBreaches = 0;

        List<UUID> tenantIds;
        try {
            tenantIds = jdbc.queryForList(
                    "SELECT id FROM tenants WHERE status = 'ACTIVE' ORDER BY created_at ASC LIMIT ?",
                    UUID.class, MAX_TENANTS_PER_TICK);
        } catch (DataAccessException e) {
            log.error("WorkflowSlaScheduler: failed to load tenants list", e);
            return new SlaCheckResult(0, 0, 0, 0, System.currentTimeMillis() - startedAt);
        }

        for (UUID tenantId : tenantIds) {
            try {
                int breaches = checkTenant(tenantId);
                totalBreaches += breaches;
                tenantsProcessed++;
            } catch (Exception e) {
                tenantsFailed++;
                log.error("WorkflowSlaScheduler: failed to check tenant {}: {}",
                        tenantId, e.getMessage(), e);
                // Continue with the next tenant — failure isolation.
            }
        }

        long durationMs = System.currentTimeMillis() - startedAt;
        log.info("WorkflowSlaScheduler: processed={} failed={} totalBreaches={} durationMs={}",
                tenantsProcessed, tenantsFailed, totalBreaches, durationMs);
        return new SlaCheckResult(tenantsProcessed, tenantsFailed, totalBreaches,
                tenantIds.size(), durationMs);
    }

    /**
     * Check SLA breaches for a single tenant in its own transaction.
     *
     * <p>Uses {@link Propagation#REQUIRES_NEW} to ensure failures in one tenant's check
     * do not pollute the transaction state of other tenants.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int checkTenant(UUID tenantId) {
        return monitoringService.checkAllSlaBreaches(tenantId);
    }

    /**
     * Result of a single scheduler tick. Used for test assertions.
     */
    public record SlaCheckResult(
            int tenantsProcessed,
            int tenantsFailed,
            int totalBreaches,
            int tenantsSeen,
            long durationMs
    ) {}
}
