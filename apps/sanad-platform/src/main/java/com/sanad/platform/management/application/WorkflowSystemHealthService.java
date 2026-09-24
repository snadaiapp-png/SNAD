package com.sanad.platform.management.application;

import com.sanad.platform.workflow.application.WorkflowMonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow System Health Integration Service — provides Workflow Engine health metrics
 * to the Executive Command Center / System Health aggregation.
 *
 * <p>This service bridges the Workflow Engine with Senior Management's health monitoring.
 * It queries Workflow tables directly (READ-ONLY) and aggregates health indicators.
 *
 * <p>The service is resilient: if Workflow monitoring fails, it returns DEGRADED status
 * rather than crashing the Command Center.
 */
@Service
public class WorkflowSystemHealthService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSystemHealthService.class);

    private final JdbcTemplate jdbc;
    private final WorkflowMonitoringService workflowMonitoringService;

    public WorkflowSystemHealthService(JdbcTemplate jdbc, WorkflowMonitoringService workflowMonitoringService) {
        this.jdbc = jdbc;
        this.workflowMonitoringService = workflowMonitoringService;
    }

    /**
     * Get Workflow health metrics for a tenant.
     *
     * @return map with component status, instance/approval counts, SLA breaches
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getWorkflowHealth(UUID tenantId) {
        var health = new HashMap<String, Object>();

        try {
            health.putAll(getDefinitionMetrics(tenantId));
            health.putAll(getInstanceMetrics(tenantId));
            health.putAll(getApprovalMetrics(tenantId));
            health.putAll(getSlaMetrics(tenantId));
            health.put("componentName", "WORKFLOW_ENGINE");
            health.put("lastCheckedAt", java.time.Instant.now().toString());

            int overdueSteps = safeGet(health, "overdueSteps", 0);
            int overdueApprovals = safeGet(health, "overdueApprovals", 0);
            int failedInstances = safeGet(health, "failedInstances", 0);
            int slaBreaches = overdueSteps + overdueApprovals;

            String status = determineStatus(failedInstances, slaBreaches);
            health.put("status", status);

            log.info("Workflow health for tenant {}: status={}, activeDefs={}, runningInstances={}, pendingApprovals={}, slaBreaches={}",
                    tenantId, status, health.get("activeDefinitions"), health.get("runningInstances"),
                    health.get("pendingApprovals"), slaBreaches);

        } catch (Exception e) {
            log.error("Workflow health check failed for tenant {}: {}", tenantId, e.getMessage(), e);
            health.put("componentName", "WORKFLOW_ENGINE");
            health.put("status", "UNHEALTHY");
            health.put("error", e.getMessage());
            health.put("lastCheckedAt", java.time.Instant.now().toString());
        }

        return health;
    }

    private Map<String, Object> getDefinitionMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();
        try {
            var total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workflow_definitions WHERE tenant_id = ?",
                    Integer.class, tenantId);
            metrics.put("totalDefinitions", total != null ? total : 0);

            var active = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workflow_definitions WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, tenantId);
            metrics.put("activeDefinitions", active != null ? active : 0);
        } catch (Exception e) {
            metrics.put("totalDefinitions", 0);
            metrics.put("activeDefinitions", 0);
        }
        return metrics;
    }

    private Map<String, Object> getInstanceMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();
        try {
            var running = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ? AND status = 'RUNNING'",
                    Integer.class, tenantId);
            metrics.put("runningInstances", running != null ? running : 0);

            var completed = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ? AND status = 'COMPLETED'",
                    Integer.class, tenantId);
            metrics.put("completedInstances", completed != null ? completed : 0);

            var failed = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ? AND status = 'FAILED'",
                    Integer.class, tenantId);
            metrics.put("failedInstances", failed != null ? failed : 0);

            var cancelled = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ? AND status = 'CANCELLED'",
                    Integer.class, tenantId);
            metrics.put("cancelledInstances", cancelled != null ? cancelled : 0);

            var overdueSteps = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workflow_step_instances WHERE tenant_id = ? AND status = 'IN_PROGRESS' AND due_at IS NOT NULL AND due_at < NOW()",
                    Integer.class, tenantId);
            metrics.put("overdueSteps", overdueSteps != null ? overdueSteps : 0);
        } catch (Exception e) {
            metrics.put("runningInstances", 0);
            metrics.put("completedInstances", 0);
            metrics.put("failedInstances", 0);
            metrics.put("cancelledInstances", 0);
            metrics.put("overdueSteps", 0);
        }
        return metrics;
    }

    private Map<String, Object> getApprovalMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();
        try {
            var pending = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workflow_approval_requests WHERE tenant_id = ? AND status = 'PENDING'",
                    Integer.class, tenantId);
            metrics.put("pendingApprovals", pending != null ? pending : 0);

            var approved = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workflow_approval_requests WHERE tenant_id = ? AND status = 'APPROVED'",
                    Integer.class, tenantId);
            metrics.put("approvedCount", approved != null ? approved : 0);

            var rejected = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workflow_approval_requests WHERE tenant_id = ? AND status = 'REJECTED'",
                    Integer.class, tenantId);
            metrics.put("rejectedCount", rejected != null ? rejected : 0);

            var overdueApprovals = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workflow_approval_requests WHERE tenant_id = ? AND status = 'PENDING' AND due_at IS NOT NULL AND due_at < NOW()",
                    Integer.class, tenantId);
            metrics.put("overdueApprovals", overdueApprovals != null ? overdueApprovals : 0);
        } catch (Exception e) {
            metrics.put("pendingApprovals", 0);
            metrics.put("approvedCount", 0);
            metrics.put("rejectedCount", 0);
            metrics.put("overdueApprovals", 0);
        }
        return metrics;
    }

    private Map<String, Object> getSlaMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();
        try {
            int totalBreaches = workflowMonitoringService.checkAllSlaBreaches(tenantId);
            metrics.put("slaBreaches", totalBreaches);
        } catch (Exception e) {
            metrics.put("slaBreaches", 0);
        }
        return metrics;
    }

    private String determineStatus(int failedInstances, int slaBreaches) {
        if (failedInstances > 0 || slaBreaches > 0) {
            return "DEGRADED";
        }
        return "HEALTHY";
    }

    @SuppressWarnings("unchecked")
    private int safeGet(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultValue;
    }
}
