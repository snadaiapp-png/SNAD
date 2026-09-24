package com.sanad.platform.management.application;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Loads the three governed-systems overviews (CRM, Analytics, Workflow) for
 * the Executive Command Center dashboard.
 *
 * <p>This bean exists as a SEPARATE {@link Service} from {@link
 * ExecutiveCommandCenterService} specifically so its public methods can run
 * in a {@link Propagation#REQUIRES_NEW REQUIRES_NEW} transaction through
 * Spring's AOP proxy. The reason: when an upstream integration service (e.g.
 * {@code CrmManagementIntegrationService.getCrmOverview}) throws because of
 * a missing column or stale schema in a test fixture, PostgreSQL aborts the
 * current transaction. If that transaction is the dashboard's outer
 * {@code @Transactional(readOnly=true)}, every subsequent operation fails
 * with {@code "current transaction is aborted, commands ignored until end
 * of transaction block"}.
 *
 * <p>By isolating each integration call in its own REQUIRES_NEW transaction,
 * the outer dashboard transaction stays clean. Failures are caught and
 * surfaced as {@code Map.of("_status", "UNAVAILABLE")} payloads — the
 * dashboard degrades gracefully.
 *
 * <p><b>Spring AOP caveat:</b> REQUIRES_NEW only works when the method is
 * invoked through the Spring proxy (not via {@code this}). The {@link #loadAll}
 * method therefore injects this bean itself (via {@link Lazy}) and calls
 * the {@code loadCrmInNewTransaction}/{@code loadAnalyticsInNewTransaction}/
 * {@code loadWorkflowInNewTransaction} methods <i>through the proxy</i>.
 */
@Service
public class GovernedSystemsOverviewService {

    private final CrmManagementIntegrationService crmIntegrationService;
    private final AnalyticsManagementIntegrationService analyticsIntegrationService;
    private final WorkflowSystemHealthService workflowHealthService;
    private final GovernedSystemsOverviewService self;

    public GovernedSystemsOverviewService(
            CrmManagementIntegrationService crmIntegrationService,
            AnalyticsManagementIntegrationService analyticsIntegrationService,
            WorkflowSystemHealthService workflowHealthService,
            @Lazy GovernedSystemsOverviewService self) {
        this.crmIntegrationService = crmIntegrationService;
        this.analyticsIntegrationService = analyticsIntegrationService;
        this.workflowHealthService = workflowHealthService;
        this.self = self;
    }

    /** Composite record of all three governed-systems overviews. */
    public record GovernedSystemsOverview(
            Map<String, Object> crm,
            Map<String, Object> analytics,
            Map<String, Object> workflow
    ) {}

    /**
     * Load all three overviews. Each call is dispatched through the Spring proxy
     * so the {@link Propagation#REQUIRES_NEW} annotation takes effect.
     */
    public GovernedSystemsOverview loadAll(UUID tenantId) {
        Map<String, Object> crm;
        try {
            crm = self.loadCrmInNewTransaction(tenantId);
        } catch (Exception e) {
            crm = Map.of("_error", e.getClass().getSimpleName(), "_status", "UNAVAILABLE");
        }
        Map<String, Object> analytics;
        try {
            analytics = self.loadAnalyticsInNewTransaction(tenantId);
        } catch (Exception e) {
            analytics = Map.of("_error", e.getClass().getSimpleName(), "_status", "UNAVAILABLE");
        }
        Map<String, Object> workflow;
        try {
            workflow = self.loadWorkflowInNewTransaction(tenantId);
        } catch (Exception e) {
            workflow = Map.of("_error", e.getClass().getSimpleName(), "_status", "UNAVAILABLE");
        }
        return new GovernedSystemsOverview(crm, analytics, workflow);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Map<String, Object> loadCrmInNewTransaction(UUID tenantId) {
        try {
            Map<String, Object> result = crmIntegrationService.getCrmOverview(tenantId);
            return result == null ? Map.of() : result;
        } catch (Exception e) {
            return Map.of("_error", e.getClass().getSimpleName(),
                    "_status", "UNAVAILABLE");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Map<String, Object> loadAnalyticsInNewTransaction(UUID tenantId) {
        try {
            Map<String, Object> result = analyticsIntegrationService.getAnalyticsOverview(tenantId);
            return result == null ? Map.of() : result;
        } catch (Exception e) {
            return Map.of("_error", e.getClass().getSimpleName(),
                    "_status", "UNAVAILABLE");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Map<String, Object> loadWorkflowInNewTransaction(UUID tenantId) {
        try {
            Map<String, Object> result = workflowHealthService.getWorkflowHealth(tenantId);
            return result == null ? Map.of() : result;
        } catch (Exception e) {
            return Map.of("_error", e.getClass().getSimpleName(),
                    "_status", "UNAVAILABLE");
        }
    }
}
