package com.sanad.platform.workflow.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dashboard query architecture (GATE R2.16 / AD-10). Server-side,
 * set-based aggregates over the R2 read models — one SQL per view (no
 * N+1), tenant-scoped by construction (RLS + explicit predicates), bounded
 * date windows, dimension filters. Empty state = empty payload with zero
 * counters; cross-tenant aggregation is impossible by predicate design.
 * Supported dashboards: SERVICE, EMPLOYEE, TEAM, EXECUTIVE, BOTTLENECK,
 * SLA, CUSTOMER.
 */
@Service
public class WorkflowAnalyticsQueryService {

    private final JdbcTemplate jdbc;

    public WorkflowAnalyticsQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Window(Instant from, Instant to) {
    }

    private static Window window(Instant from, Instant to) {
        Instant resolvedFrom = from != null ? from : Instant.now().minusSeconds(30 * 86400);
        Instant resolvedTo = to != null ? to : Instant.now();
        return new Window(resolvedFrom, resolvedTo);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> serviceDashboard(UUID tenantId, Instant from, Instant to) {
        Window w = window(from, to);
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS total_instances,
                       COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                       COUNT(*) FILTER (WHERE status IN ('CANCELLED','FAILED')) AS failed,
                       COUNT(*) FILTER (WHERE status = 'RUNNING') AS running,
                       COALESCE(AVG(process_duration_seconds)
                           FILTER (WHERE process_duration_seconds IS NOT NULL), 0) AS avg_process_seconds,
                       COALESCE(SUM(sla_breach_count), 0) AS sla_breaches,
                       COUNT(*) FILTER (WHERE late_completed) AS late_completions
                  FROM workflow_analytics_process_facts
                 WHERE tenant_id = ? AND started_at >= ? AND started_at < ?
                """, tenantId, Timestamp.from(w.from()), Timestamp.from(w.to()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> employeeDashboard(UUID tenantId, Instant from, Instant to,
                                                 Integer limit) {
        Window w = window(from, to);
        int bounded = Math.max(1, Math.min(limit == null ? 25 : limit, 100));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT owner_employee_id,
                       COUNT(*) AS steps,
                       COUNT(*) FILTER (WHERE completed_at IS NOT NULL) AS completed_steps,
                       COALESCE(AVG(employee_responsibility_seconds), 0) AS avg_responsibility_seconds,
                       COUNT(*) FILTER (WHERE sla_breached) AS breaches
                  FROM workflow_analytics_step_facts
                 WHERE tenant_id = ? AND entered_at >= ? AND entered_at < ?
                 GROUP BY owner_employee_id
                 ORDER BY steps DESC
                 LIMIT ?
                """, tenantId, Timestamp.from(w.from()), Timestamp.from(w.to()), bounded);
        return Map.of("windowFrom", w.from().toString(), "windowTo", w.to().toString(),
                "employees", rows);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> executiveDashboard(UUID tenantId, Instant from, Instant to) {
        Window w = window(from, to);
        Map<String, Object> service = serviceDashboard(tenantId, w.from(), w.to());
        Map<String, Object> sla = slaDashboard(tenantId, w.from(), w.to());
        service.put("sla", sla);
        return service;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> bottleneckDashboard(UUID tenantId, Instant from, Instant to,
                                                   Integer limit) {
        Window w = window(from, to);
        int bounded = Math.max(1, Math.min(limit == null ? 10 : limit, 50));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT step_key,
                       COUNT(*) AS instances,
                       COALESCE(AVG(COALESCE(queue_wait_seconds, 0)
                           + COALESCE(system_wait_seconds, 0)), 0) AS avg_wait_seconds,
                       COUNT(*) FILTER (WHERE completed_at IS NULL) AS pending
                  FROM workflow_analytics_step_facts
                 WHERE tenant_id = ? AND entered_at >= ? AND entered_at < ?
                 GROUP BY step_key
                 ORDER BY avg_wait_seconds DESC
                 LIMIT ?
                """, tenantId, Timestamp.from(w.from()), Timestamp.from(w.to()), bounded);
        return Map.of("bottlenecks", rows);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> slaDashboard(UUID tenantId, Instant from, Instant to) {
        Window w = window(from, to);
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS instances,
                       COALESCE(SUM(sla_breach_count), 0) AS breaches,
                       COUNT(*) FILTER (WHERE sla_compliant) AS compliant,
                       COUNT(*) FILTER (WHERE sla_compliant) * 100.0
                           / GREATEST(COUNT(*), 1) AS compliance_percent,
                       COUNT(*) FILTER (WHERE late_completed) AS late_completions,
                       COUNT(*) FILTER (WHERE timeout_count > 0) AS timed_out
                  FROM workflow_analytics_process_facts
                 WHERE tenant_id = ? AND started_at >= ? AND started_at < ?
                """, tenantId, Timestamp.from(w.from()), Timestamp.from(w.to()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> customerDashboard(UUID tenantId, Instant from, Instant to) {
        Window w = window(from, to);
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS instances,
                       COALESCE(AVG(COALESCE(customer_wait_seconds, 0)), 0) AS avg_customer_wait_seconds,
                       COALESCE(AVG(COALESCE(external_response_seconds, 0)), 0) AS avg_external_response_seconds,
                       COUNT(*) FILTER (WHERE external_response_seconds IS NOT NULL) AS responded_externally,
                       (SELECT COALESCE(AVG(rating), 0) FROM workflow_customer_feedback
                         WHERE tenant_id = ? AND created_at >= ? AND created_at < ?) AS avg_feedback_rating
                  FROM workflow_analytics_process_facts
                 WHERE tenant_id = ? AND started_at >= ? AND started_at < ?
                """, tenantId, Timestamp.from(w.from()), Timestamp.from(w.to()),
                tenantId, Timestamp.from(w.from()), Timestamp.from(w.to()));
    }
}
