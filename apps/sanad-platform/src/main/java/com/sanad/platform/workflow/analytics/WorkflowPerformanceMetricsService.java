package com.sanad.platform.workflow.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Explainable performance metrics (GATE R2.17 / AD-11). Metrics are
 * computed ONLY from Journey-backed responsibility segments within a
 * bounded window, and every output embeds its explanation: metric
 * definition, measurement period, source event counts, included
 * responsibility segments, and excluded waits.
 *
 * <p>NO AUTOMATED EMPLOYMENT DECISION: this service is read-only. It
 * writes nothing to the HR domain, exposes no command surface, and no
 * automated policy may consume its output for terminate/demote/discipline/
 * promotion/salary/employment-status decisions. Metrics are evidence for
 * human-governed review only.</p>
 */
@Service
public class WorkflowPerformanceMetricsService {

    private final JdbcTemplate jdbc;

    public WorkflowPerformanceMetricsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record MetricResult(UUID employeeId, Instant windowFrom, Instant windowTo,
                               Map<String, Object> metrics,
                               Map<String, Object> explanation) {
    }

    @Transactional(readOnly = true)
    public MetricResult employeeMetrics(UUID tenantId, UUID employeeId,
                                        Instant from, Instant to) {
        Instant windowFrom = from != null ? from : Instant.now().minusSeconds(30 * 86400);
        Instant windowTo = to != null ? to : Instant.now();
        if (!windowTo.isAfter(windowFrom)) {
            throw new IllegalArgumentException("Metric window: to must be after from");
        }
        Map<String, Object> stats = jdbc.queryForMap("""
                SELECT COUNT(*) AS segments,
                       COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(seg.ended_at, NOW()) - seg.started_at))::BIGINT
                         FILTER (WHERE seg.segment_type IN ('ASSIGNED','CLAIMED'))), 0) AS responsibility_seconds,
                       COUNT(*) FILTER (WHERE seg.segment_type = 'CLAIMED') AS claimed_segments,
                       COUNT(*) FILTER (WHERE seg.segment_type = 'ASSIGNED') AS assigned_segments
                  FROM workflow_responsibility_segments seg
                 WHERE seg.tenant_id = ? AND seg.owner_employee_id = ?
                   AND seg.started_at >= ? AND seg.started_at < ?
                """, tenantId, employeeId,
                Timestamp.from(windowFrom), Timestamp.from(windowTo));
        Map<String, Object> completion = jdbc.queryForMap("""
                SELECT COUNT(*) AS completed_items,
                       COUNT(*) FILTER (WHERE wi.sla_due_at IS NOT NULL
                           AND wi.completed_at <= wi.sla_due_at) AS on_time_items
                  FROM workflow_work_items wi
                 WHERE wi.tenant_id = ? AND (wi.assignee_employee_id = ?
                        OR wi.claimed_by_employee_id = ?)
                   AND wi.status = 'COMPLETED'
                   AND wi.completed_at >= ? AND wi.completed_at < ?
                """, tenantId, employeeId, employeeId,
                Timestamp.from(windowFrom), Timestamp.from(windowTo));
        long segments = ((Number) stats.get("segments")).longValue();
        long responsibilitySeconds =
                ((Number) stats.get("responsibility_seconds")).longValue();
        long completedItems = ((Number) completion.get("completed_items")).longValue();
        long onTimeItems = ((Number) completion.get("on_time_items")).longValue();
        long claimedSegments = ((Number) stats.get("claimed_segments")).longValue();
        long assignedSegments = ((Number) stats.get("assigned_segments")).longValue();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalResponsibilitySeconds", responsibilitySeconds);
        metrics.put("completedWorkItems", completedItems);
        metrics.put("onTimeCompletionPercent", completedItems == 0 ? 0.0
                : Math.round(onTimeItems * 10000.0 / completedItems) / 100.0);
        metrics.put("avgResponsibilitySecondsPerSegment", segments == 0 ? 0.0
                : Math.round(responsibilitySeconds * 100.0 / segments) / 100.0);

        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("metricDefinition",
                "Employee responsibility time = sum of ASSIGNED and CLAIMED "
                        + "responsibility segments in the window; on-time = work item "
                        + "completed at or before its SLA due time.");
        explanation.put("measurementPeriod", Map.of(
                "from", windowFrom.toString(), "to", windowTo.toString()));
        explanation.put("sourceEvents", Map.of(
                "responsibilitySegments", segments,
                "completedWorkItems", completedItems));
        explanation.put("includedResponsibilitySegments", List.of("ASSIGNED", "CLAIMED"));
        explanation.put("excludedWaits",
                List.of("EXTERNAL_WAIT (customer wait)", "SYSTEM_WAIT (system wait)",
                        "QUEUE (queue wait)"));
        explanation.put("calculationInputs", Map.of(
                "claimedSegments", claimedSegments,
                "assignedSegments", assignedSegments,
                "onTimeItems", onTimeItems));
        explanation.put("automatedDecisionPolicy",
                "NO_AUTOMATED_EMPLOYMENT_DECISION: read-only evidence; no HR "
                        + "employment decision may be taken automatically from this output.");
        return new MetricResult(employeeId, windowFrom, windowTo, metrics, explanation);
    }
}
