package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowIncident;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Incident lifecycle service (design decisions AF3/AH3). Lifecycle:
 * OPEN -> ACKNOWLEDGED -> RESOLVED (-> CLOSED optional). The platform never
 * silently converts an exhausted failure into success — incidents demand a
 * recorded operator resolution.
 */
@Service
public class WorkflowIncidentService {

    private final JdbcTemplate jdbc;

    public WorkflowIncidentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public WorkflowIncident open(UUID tenantId, UUID workflowInstanceId, UUID workflowStepInstanceId,
                                 String source, WorkflowIncident.Severity severity, String failureCategory) {
        var incident = WorkflowIncident.open(tenantId, workflowInstanceId, workflowStepInstanceId,
                source, severity, failureCategory);
        jdbc.update("""
                INSERT INTO workflow_incidents (
                    id, tenant_id, workflow_instance_id, step_instance_id, source,
                    severity, failure_category, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """, incident.id(), incident.tenantId(), incident.workflowInstanceId(),
                incident.workflowStepInstanceId(), incident.source(),
                incident.severity().name(), incident.failureCategory(), incident.status().name());
        return incident;
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowIncident> find(UUID tenantId, UUID incidentId) {
        return jdbc.query("""
                SELECT * FROM workflow_incidents WHERE tenant_id = ? AND id = ?
                """, (rs, n) -> map(rs), tenantId, incidentId).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<WorkflowIncident> findOpen(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM workflow_incidents
                WHERE tenant_id = ? AND status IN ('OPEN', 'ACKNOWLEDGED')
                ORDER BY created_at ASC LIMIT ?
                """, (rs, n) -> map(rs), tenantId, Math.min(limit, 200));
    }

    @Transactional
    public WorkflowIncident acknowledge(UUID tenantId, UUID incidentId, UUID actor) {
        var incident = load(tenantId, incidentId);
        var updated = incident.acknowledge(actor);
        persist(updated);
        return updated;
    }

    @Transactional
    public WorkflowIncident resolve(UUID tenantId, UUID incidentId, UUID actor, String resolution) {
        var incident = load(tenantId, incidentId);
        var updated = incident.resolve(actor, resolution);
        persist(updated);
        return updated;
    }

    private WorkflowIncident load(UUID tenantId, UUID incidentId) {
        return find(tenantId, incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));
    }

    private void persist(WorkflowIncident incident) {
        jdbc.update("""
                UPDATE workflow_incidents SET status = ?, owner = ?, resolution = ?, updated_at = NOW()
                WHERE id = ? AND tenant_id = ?
                """, incident.status().name(), incident.owner(), incident.resolution(),
                incident.id(), incident.tenantId());
    }

    private WorkflowIncident map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WorkflowIncident(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("workflow_instance_id", UUID.class),
                rs.getObject("step_instance_id", UUID.class),
                rs.getString("source"),
                WorkflowIncident.Severity.valueOf(rs.getString("severity")),
                rs.getString("failure_category"),
                WorkflowIncident.Status.valueOf(rs.getString("status")),
                rs.getObject("owner", UUID.class),
                rs.getString("resolution"),
                rs.getObject("retry_step_instance_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
