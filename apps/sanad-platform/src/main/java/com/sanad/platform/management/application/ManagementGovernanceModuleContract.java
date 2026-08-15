package com.sanad.platform.management.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified governance contract that every current and future module must
 * implement to become governed by Senior Management (GAP 24).
 *
 * <p>Spring auto-discovers all beans implementing this interface via
 * {@link ManagementGovernanceModuleRegistry#modules()}, which means a new
 * module (ERP/HRM/POS/ECOMMERCE_CX/INDUSTRY_SOLUTIONS) needs only:
 * <ol>
 *   <li>A new {@code @Service} class implementing this contract.</li>
 *   <li>The contract bean is auto-discovered by the registry.</li>
 *   <li>{@code ExecutiveCommandCenterService.getDashboard} and
 *       {@code ExecutiveReportService.generateReport} automatically
 *       include the new module's data without modification.</li>
 * </ol>
 *
 * <p>This is the <b>module discovery</b> pattern: instead of hard-coding
 * the list of integration services by name (as the original
 * {@code GovernedSystemsOverviewService} did), we now use Spring's
 * {@code List<ManagementGovernanceModuleContract>} autowiring.
 *
 * <p>Each contract implementation MUST be:
 * <ul>
 *   <li><b>tenant-scoped</b>: every method takes {@code tenantId} and
 *       filters by it. Cross-tenant data leakage is a critical bug.</li>
 *   <li><b>resilient</b>: methods must not throw — they catch exceptions
 *       internally and return an {@code UNAVAILABLE} status so the
 *       command center stays healthy even if one module is broken.</li>
 *   <li><b>read-only</b>: methods never mutate state; they are pure
 *       aggregators.</li>
 *   <li><b>capability-respecting</b>: the module may use
 *       {@code @RequireCapability} at the controller level; the contract
 *       itself is invoked by services that already have
 *       {@code EXECUTIVE_COMMAND_CENTER.VIEW}.</li>
 * </ul>
 *
 * <p>Existing implementations:
 * <ul>
 *   <li>{@code CrmManagementIntegrationService}</li>
 *   <li>{@code FinanceManagementIntegrationService}</li>
 *   <li>{@code AnalyticsManagementIntegrationService}</li>
 *   <li>{@code WorkflowSystemHealthService}</li>
 *   <li>{@code ModuleGovernanceService} (itself governs the registry —
 *       a slight special case, but still implements the contract).</li>
 * </ul>
 *
 * <p>Future implementations (when those modules are built):
 * <ul>
 *   <li>{@code ErpManagementIntegrationService}</li>
 *   <li>{@code HrmManagementIntegrationService}</li>
 *   <li>{@code PosManagementIntegrationService}</li>
 *   <li>{@code EcommerceCxManagementIntegrationService}</li>
 *   <li>{@code IndustrySolutionsManagementIntegrationService}</li>
 * </ul>
 */
public interface ManagementGovernanceModuleContract {

    /** The module code as registered in the {@code modules} table (V20260814_1). */
    String moduleCode();

    /** Human-readable display name (Arabic or English — depends on tenant locale). */
    String displayName();

    /**
     * Whether this module is enabled for the given tenant (per the active
     * subscription's plan_module_entitlements). Implementations should use
     * {@code EntitlementResolver.isModuleEnabled(tenantId, moduleCode())}.
     */
    boolean isEnabled(UUID tenantId);

    /**
     * Health status: HEALTHY / DEGRADED / UNHEALTHY / UNAVAILABLE.
     * Used by the command center's overall status rollup.
     */
    ModuleHealthStatus healthStatus(UUID tenantId);

    /**
     * Capabilities exposed by this module for the tenant (e.g.
     * {@code ["CRM.VIEW","CRM.WRITE","CRM.ADMIN"]}).
     */
    List<String> capabilities(UUID tenantId);

    /**
     * Module-level KPI summary (a small map, e.g.
     * {@code {"activeAccounts":12,"wonRevenue":15000}}).
     * Returns an empty map if the module has no KPIs to surface.
     */
    Map<String, Object> kpiSummary(UUID tenantId);

    /**
     * Module-level operational summary (a small map, e.g.
     * {@code {"activeWorkflows":3,"slaBreaches":0}}).
     * Returns an empty map if the module has nothing to surface.
     */
    Map<String, Object> operationalSummary(UUID tenantId);

    /**
     * Module-level alerts count (open alerts owned by this module).
     */
    int openAlertsCount(UUID tenantId);

    /**
     * Module-level risks count (open risks owned by this module).
     */
    int openRisksCount(UUID tenantId);

    /**
     * Module-level issues count (open issues owned by this module).
     */
    int openIssuesCount(UUID tenantId);

    /**
     * SLA state for this module: OK / AT_RISK / BREACHED / NOT_APPLICABLE.
     */
    SlaState slaState(UUID tenantId);

    /**
     * Version/status metadata (e.g. {@code {"version":"1.0","maturity":"GA"}}).
     */
    Map<String, Object> metadata();

    /** Module health states (matches WorkflowSystemHealthService conventions). */
    enum ModuleHealthStatus {
        HEALTHY, DEGRADED, UNHEALTHY, UNAVAILABLE
    }

    /** SLA state enum. */
    enum SlaState {
        OK, AT_RISK, BREACHED, NOT_APPLICABLE
    }
}
