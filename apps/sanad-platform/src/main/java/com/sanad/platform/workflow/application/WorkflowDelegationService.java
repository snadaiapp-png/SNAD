package com.sanad.platform.workflow.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Delegation, fallback and B1 handling (design decisions G3/B1).
 *
 * <p>Delegations are planned, time-bounded and tenant-safe. A delegate never
 * inherits all delegator permissions — the delegate must independently
 * satisfy current action authorization at command time.</p>
 *
 * <p>B1 dominance: a hard-disabled linked user makes existing assigned work
 * {@code ASSIGNEE_UNAVAILABLE}. The system never auto-transfers the work to
 * the manager or a delegate merely because the user was disabled — an
 * authorized supervisor performs an explicit reassignment.</p>
 */
@Service
public class WorkflowDelegationService {

    private final JdbcTemplate jdbc;

    public WorkflowDelegationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record UnavailableAssignmentResult(String status, UUID reassignedEmployeeId) {}

    /**
     * B1: marks the assigned work unavailable when the assignee's linked user
     * is missing or disabled. Always returns a null reassignment — callers
     * must never read this as a transfer.
     */
    @Transactional
    public UnavailableAssignmentResult resolveExistingUnavailableAssignment(UUID tenantId, UUID workItemId) {
        var item = jdbc.queryForMap("""
                SELECT wi.assignee_employee_id, wi.status, wi.version AS item_version,
                       e.user_id
                FROM workflow_work_items wi
                LEFT JOIN hr_employees e ON e.tenant_id = wi.tenant_id AND e.id = wi.assignee_employee_id
                WHERE wi.tenant_id = ? AND wi.id = ?
                """, tenantId, workItemId);

        UUID userId = (UUID) item.get("user_id");
        long expectedVersion = ((Number) item.get("item_version")).longValue();

        boolean actionable = false;
        if (userId != null) {
            var status = jdbc.queryForList(
                    "SELECT status FROM users WHERE tenant_id = ? AND id = ?",
                    String.class, tenantId, userId);
            actionable = !status.isEmpty() && "ACTIVE".equals(status.get(0));
        }
        if (actionable) {
            return new UnavailableAssignmentResult((String) item.get("status"), null);
        }

        int updated = jdbc.update("""
                UPDATE workflow_work_items
                SET status = 'ASSIGNEE_UNAVAILABLE', version = version + 1, updated_at = NOW()
                WHERE tenant_id = ? AND id = ? AND version = ? AND status IN ('AVAILABLE', 'CLAIMED')
                """, tenantId, workItemId, expectedVersion);
        if (updated == 0) {
            // Lost the race or already unavailable — read the live status.
            String live = jdbc.queryForObject(
                    "SELECT status FROM workflow_work_items WHERE tenant_id = ? AND id = ?",
                    String.class, tenantId, workItemId);
            return new UnavailableAssignmentResult(live, null);
        }
        return new UnavailableAssignmentResult("ASSIGNEE_UNAVAILABLE", null);
    }

    /** ACTIVE delegation of {@code delegatorEmployeeId} covering {@code at}, scoped when filters given. */
    @Transactional(readOnly = true)
    public Optional<UUID> activeDelegateFor(UUID tenantId, UUID delegatorEmployeeId, Instant at,
                                            UUID workflowFamilyId, String module, String taskCategory) {
        List<UUID> delegates = jdbc.queryForList("""
                SELECT delegate_employee_id
                FROM workflow_delegations
                WHERE tenant_id = ?
                  AND delegator_employee_id = ?
                  AND status = 'ACTIVE'
                  AND valid_from <= ?
                  AND valid_until > ?
                  AND (workflow_family_id IS NULL OR workflow_family_id = ?)
                  AND (module IS NULL OR module = ?)
                  AND (task_category IS NULL OR task_category = ?)
                ORDER BY created_at ASC
                LIMIT 1
                """, UUID.class, tenantId, delegatorEmployeeId,
                Timestamp.from(at), Timestamp.from(at),
                workflowFamilyId, module, taskCategory);
        return delegates.stream().findFirst();
    }

    @Transactional
    public UUID createDelegation(UUID tenantId, UUID delegatorEmployeeId, UUID delegateEmployeeId,
                                 Instant validFrom, Instant validUntil, UUID createdBy) {
        if (!validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("Delegation window must end after it starts");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_delegations (
                    id, tenant_id, delegator_employee_id, delegate_employee_id,
                    valid_from, valid_until, status, created_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, NOW(), NOW())
                """, id, tenantId, delegatorEmployeeId, delegateEmployeeId,
                Timestamp.from(validFrom), Timestamp.from(validUntil), createdBy);
        return id;
    }
}
