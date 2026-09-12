package com.sanad.platform.workflow.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * R1 GATE R1.27 — responsibility segments: ownership attribution through
 * assignment / claim / reassignment / delegation / release / completion /
 * timeout. External wait, system wait and unassigned queue time are recorded
 * as SEPARATE segment types — foundation for R2 analytics. NO employee
 * scoring in R1 (scope control).
 */
@Service
public class WorkflowResponsibilityService {

    private final JdbcTemplate jdbc;
    private final WorkflowJourneyService journey;

    public WorkflowResponsibilityService(JdbcTemplate jdbc, WorkflowJourneyService journey) {
        this.jdbc = jdbc;
        this.journey = journey;
    }

    /** Closes the currently open segment for the item (if any) and opens a new one. */
    @Transactional
    public UUID beginSegment(UUID tenantId, UUID workItemId, UUID workflowInstanceId,
                             String segmentType, UUID ownerEmployeeId, String reason,
                             UUID correlationId, UUID causationId) {
        jdbc.update("""
                UPDATE workflow_responsibility_segments SET ended_at = NOW()
                 WHERE tenant_id=? AND work_item_id=? AND ended_at IS NULL
                """, tenantId, workItemId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_responsibility_segments (id, tenant_id, work_item_id,
                    workflow_instance_id, segment_type, owner_employee_id, started_at, reason)
                VALUES (?,?,?,?,?,?,NOW(),?)
                """, id, tenantId, workItemId, workflowInstanceId, segmentType, ownerEmployeeId, reason);
        journey.append(tenantId, new WorkflowJourneyService.JourneyEvent(
                "RESPONSIBILITY_SEGMENT_STARTED", workflowInstanceId, null, workItemId, null, null,
                null, null, null, null, null, null, ownerEmployeeId == null ? "SYSTEM" : "EMPLOYEE",
                null, ownerEmployeeId, null, null, segmentType, reason, correlationId, causationId,
                "resp-open:" + id, null));
        return id;
    }

    @Transactional
    public void endAllSegments(UUID tenantId, UUID workItemId, String reason,
                               UUID correlationId, UUID causationId, UUID workflowInstanceId) {
        jdbc.update("""
                UPDATE workflow_responsibility_segments SET ended_at = NOW()
                 WHERE tenant_id=? AND work_item_id=? AND ended_at IS NULL
                """, tenantId, workItemId);
        journey.append(tenantId, new WorkflowJourneyService.JourneyEvent(
                "RESPONSIBILITY_SEGMENT_ENDED", workflowInstanceId, null, workItemId, null, null,
                null, null, null, null, null, null, "SYSTEM", null, null, null, null, null,
                reason, correlationId, causationId, "resp-close:" + workItemId + ":" + reason, null));
    }

    /** Ownership durations per employee (R1.27 example: A 4h, B 2h). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> ownershipDurations(UUID tenantId, UUID workItemId) {
        return jdbc.queryForList("""
                SELECT owner_employee_id, segment_type,
                       SUM(COALESCE(EXTRACT(EPOCH FROM (COALESCE(ended_at, NOW()) - started_at))::BIGINT, 0))
                           AS owned_seconds
                  FROM workflow_responsibility_segments
                 WHERE tenant_id=? AND work_item_id=? AND segment_type IN ('ASSIGNED','CLAIMED')
                 GROUP BY owner_employee_id, segment_type
                 ORDER BY owner_employee_id
                """, tenantId, workItemId);
    }
}
