package com.sanad.platform.workflow.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * R1 GATE R1.6 — Workflow runtime impact counter for the subscription
 * control plane. Feeds {@code SubscriptionImpactService} preview with the
 * counts of live Workflow runtime objects that a removal/downgrade must
 * respect (DRAIN_EXISTING default: never silently kill active business
 * processes). Read-only by contract.
 */
@Service
public class WorkflowRuntimeImpactCounter {

    private final JdbcTemplate jdbc;

    public WorkflowRuntimeImpactCounter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countRuntime(UUID tenantId) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("ACTIVE_INSTANCES", count(tenantId,
                "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ? AND status IN ('RUNNING','CANCELLING')"));
        m.put("PAUSED_INSTANCES", count(tenantId,
                "SELECT COUNT(*) FROM workflow_instances WHERE tenant_id = ? AND status = 'PAUSED'"));
        m.put("PENDING_WORK_ITEMS", count(tenantId,
                "SELECT COUNT(*) FROM workflow_work_items WHERE tenant_id = ? AND status IN ('AVAILABLE','CLAIMED','IN_PROGRESS','ASSIGNEE_UNAVAILABLE')"));
        m.put("PENDING_APPROVALS", count(tenantId,
                "SELECT COUNT(*) FROM workflow_approval_requests WHERE tenant_id = ? AND status = 'PENDING'"));
        // Optional R1 tables (timers / external actions): a table that has not
        // been provisioned yet means zero such runtime objects exist — an honest
        // zero, not a failure. Core Workflow tables above are strict.
        m.put("ACTIVE_TIMERS", countOptional(tenantId, "workflow_timers",
                "SELECT COUNT(*) FROM workflow_timers WHERE tenant_id = ? AND status IN ('RUNNING','PAUSED')"));
        m.put("PENDING_EXTERNAL_ACTIONS", countOptional(tenantId, "workflow_external_actions",
                "SELECT COUNT(*) FROM workflow_external_actions WHERE tenant_id = ? AND status IN ('PENDING','VIEWED')"));
        m.put("OPEN_INCIDENTS", count(tenantId,
                "SELECT COUNT(*) FROM workflow_incidents WHERE tenant_id = ? AND status IN ('OPEN','ACKNOWLEDGED')"));
        return m;
    }

    private long count(UUID tenantId, String sql) {
        Long v = jdbc.queryForObject(sql, Long.class, tenantId);
        return v == null ? 0L : v;
    }

    private long countOptional(UUID tenantId, String table, String sql) {
        Integer present = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                Integer.class, table);
        if (present == null || present == 0) return 0L;
        return count(tenantId, sql);
    }
}
