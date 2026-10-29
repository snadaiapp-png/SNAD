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

    private static final String AMBIENT_TX_REQUIRED_MSG =
            "runProjectionPass must run inside a Spring-managed transaction "
            + "(SET LOCAL app.tenant_id is only meaningful within a transaction boundary).";

    private final JdbcTemplate jdbc;
    private final WorkflowAnalyticsProjectionService projectionService;
    private final org.springframework.transaction.support.TransactionTemplate scoped;
    private final com.sanad.platform.security.rls.TenantRlsTransactionContext tenantRlsContext;
    private final boolean enabled;

    @org.springframework.beans.factory.annotation.Autowired
    public WorkflowAnalyticsProjectionWorker(
            JdbcTemplate jdbc,
            WorkflowAnalyticsProjectionService projectionService,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            com.sanad.platform.security.rls.TenantRlsTransactionContext tenantRlsContext,
            @Value("${sanad.workflow.analytics.projection.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.projectionService = projectionService;
        this.tenantRlsContext = tenantRlsContext;
        org.springframework.transaction.support.TransactionTemplate template =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        template.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.scoped = template;
        this.enabled = enabled;
    }

    /** Backward-compatible harness constructor: in-transaction ambient mode only. */
    public WorkflowAnalyticsProjectionWorker(
            JdbcTemplate jdbc,
            WorkflowAnalyticsProjectionService projectionService,
            boolean enabled) {
        this.jdbc = jdbc;
        this.projectionService = projectionService;
        this.tenantRlsContext = null;
        this.scoped = null;
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

    /**
     * One bounded projection pass (invokable directly in tests).
     *
     * <p>FORCE-RLS execution modes (V20261029_1): workflow_instances /
     * workflow_analytics_process_facts are fail-closed tenant tables and this
     * worker runs on a scheduled thread WITHOUT a SecurityContext tenant — a
     * single unscoped query would silently see zero rows. Mirroring
     * WorkflowDeadlineEnforcementWorker: called OUTSIDE any transaction the
     * pass enumerates the platform tenant directory (non-RLS) and projects
     * each tenant inside a REQUIRES_NEW transaction-local app.tenant_id
     * scope; called INSIDE a caller-managed transaction (tests, control
     * flows) it re-scopes app.tenant_id per tenant within that transaction
     * (is_local last-wins, strictly sequential). RLS stays fully enforced in
     * both modes.</p>
     */
    public int runProjectionPass() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive()) {
            return runProjectionPassInTransaction();
        }
        if (scoped == null || tenantRlsContext == null) {
            throw new IllegalStateException(AMBIENT_TX_REQUIRED_MSG);
        }
        int projected = 0;
        List<UUID> tenants = jdbc.queryForList(
                "SELECT id FROM tenants WHERE status = 'ACTIVE' ORDER BY id LIMIT ?",
                UUID.class, MAX_TENANTS_PER_TICK);
        for (UUID tenantId : tenants) {
            try {
                Integer n = scoped.execute(status -> {
                    tenantRlsContext.applyForCurrentTransaction(tenantId);
                    return projectStaleForTenant(tenantId);
                });
                projected += n == null ? 0 : n;
            } catch (Exception e) {
                log.error("Analytics projection failed for tenant {}: {}",
                        tenantId, e.getMessage());
            }
        }
        return projected;
    }

    /** Ambient-transaction variant: sequential per-tenant re-scoping, one tx. */
    private int runProjectionPassInTransaction() {
        if (tenantRlsContext == null) {
            throw new IllegalStateException(
                "TenantRlsTransactionContext is required to scope the per-tenant "
                + "projection pass under fail-closed FORCE RLS (V20261029_1).");
        }
        int projected = 0;
        List<UUID> tenants = jdbc.queryForList(
                "SELECT id FROM tenants WHERE status = 'ACTIVE' ORDER BY id LIMIT ?",
                UUID.class, MAX_TENANTS_PER_TICK);
        for (UUID tenantId : tenants) {
            try {
                tenantRlsContext.applyForCurrentTransaction(tenantId);
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
