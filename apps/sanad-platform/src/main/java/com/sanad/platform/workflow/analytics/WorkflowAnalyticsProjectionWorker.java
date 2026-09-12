package com.sanad.platform.workflow.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Staleness-bounded analytics projection worker (AD-9). Rebuilds facts for
 * instances whose evidence changed since their last projection
 * (derived_from_event_id < journey head), in deterministic bounded batches.
 * Read-model maintenance only — never mutates journey/timers/instances.
 */
@Component
public class WorkflowAnalyticsProjectionWorker {

    private static final Logger log =
            LoggerFactory.getLogger(WorkflowAnalyticsProjectionWorker.class);

    private static final int MAX_TENANTS_PER_TICK = 200;
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final WorkflowAnalyticsProjectionService projectionService;
    private final boolean enabled;

    public WorkflowAnalyticsProjectionWorker(
            JdbcTemplate jdbc,
            WorkflowAnalyticsProjectionService projectionService,
            @Value("${sanad.workflow.analytics.projection.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.projectionService = projectionService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${sanad.workflow.analytics.projection.interval-ms:120000}",
               initialDelayString = "${sanad.workflow.analytics.projection.initial-delay-ms:60000}")
    public void projectStaleInstances() {
        if (!enabled) {
            return;
        }
        try {
            runProjectionPass();
        } catch (Exception e) {
            log.error("Analytics projection tick failed: {}", e.getMessage(), e);
        }
    }

    /** One bounded projection pass (invokable directly in tests). */
    @Transactional
    public int runProjectionPass() {
        int projected = 0;
        List<UUID> tenants = jdbc.queryForList(
                "SELECT id FROM tenants WHERE status = 'ACTIVE' ORDER BY id LIMIT ?",
                UUID.class, MAX_TENANTS_PER_TICK);
        for (UUID tenantId : tenants) {
            try {
                projected += projectStaleForTenant(tenantId);
            } catch (Exception e) {
                log.error("Analytics projection failed for tenant {}: {}",
                        tenantId, e.getMessage());
            }
        }
        return projected;
    }

    private int projectStaleForTenant(UUID tenantId) {
        List<UUID> stale = jdbc.queryForList("""
                SELECT i.id
                  FROM workflow_instances i
                  LEFT JOIN workflow_analytics_process_facts f
                    ON f.tenant_id = i.tenant_id AND f.workflow_instance_id = i.id
                  LEFT JOIN LATERAL (
                       SELECT COALESCE(MAX(j.id), 0) AS head
                         FROM workflow_journey j
                        WHERE j.tenant_id = i.tenant_id
                          AND j.workflow_instance_id = i.id) head ON TRUE
                 WHERE i.tenant_id = ?
                   AND (f.id IS NULL
                        OR f.derived_from_event_id < head.head
                        OR i.updated_at > f.derived_at)
                 ORDER BY i.updated_at DESC
                 LIMIT ?
                """, UUID.class, tenantId, BATCH_SIZE);
        for (UUID instanceId : stale) {
            projectionService.projectInstance(tenantId, instanceId);
        }
        return stale.size();
    }
}
