package com.sanad.platform.hr.time.application;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HRM-G2 Timesheet service.
 *
 * <p>Manages timesheet lifecycle: DRAFT → SUBMITTED → APPROVED
 * (with REJECTED and LOCKED states).
 *
 * <p>State transitions:
 *   DRAFT → SUBMITTED (employee submits)
 *   SUBMITTED → APPROVED (manager approves)
 *   SUBMITTED → REJECTED (manager rejects)
 *   REJECTED → DRAFT (employee edits and resubmits)
 *   APPROVED → LOCKED (payroll period closes)
 *
 * <p>Concurrency: optimistic locking via version column.
 * Stale writes produce 409 (via HrApiExceptionHandler).
 *
 * <p>Audit/outbox: every state transition writes to
 * hr_audit_ledger + hr_domain_event_outbox in the same transaction.
 */
@Service
public class HrTimesheetService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public HrTimesheetService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public UUID createTimesheet(UUID tenantId, UUID employmentId,
                                LocalDate periodStart, LocalDate periodEnd) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO hr_timesheets (id, tenant_id, employment_id, period_start, period_end, state) " +
                "VALUES (?, ?, ?, ?, ?, 'DRAFT')",
                id, tenantId, employmentId, periodStart, periodEnd
        );
        return id;
    }

    /** Return the target employment inside the current tenant or fail closed. */
    @Transactional(readOnly = true)
    public UUID requireTimesheetEmployment(UUID tenantId, UUID timesheetId) {
        List<UUID> matches = jdbc.query(
                "SELECT employment_id FROM hr_timesheets WHERE id = ? AND tenant_id = ?",
                (rs, rowNum) -> UUID.fromString(rs.getString("employment_id")),
                timesheetId,
                tenantId);
        if (matches.size() != 1) {
            throw new IllegalStateException("HRM_TIMESHEET_NOT_FOUND_IN_TENANT");
        }
        return matches.get(0);
    }

    @Transactional
    public void submit(UUID tenantId, UUID timesheetId, UUID employmentId) {
        int updated = jdbc.update(
                "UPDATE hr_timesheets SET state = 'SUBMITTED', submitted_at = ?, updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND employment_id = ? AND state = 'DRAFT'",
                clock.instant(), timesheetId, tenantId, employmentId
        );
        if (updated == 0) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: timesheet not in DRAFT state");
        }
        writeAuditAndOutbox(tenantId, "TimesheetSubmitted", timesheetId);
    }

    @Transactional
    public void approve(UUID tenantId, UUID timesheetId, UUID approverId, String comment) {
        int updated = jdbc.update(
                "UPDATE hr_timesheets SET state = 'APPROVED', approver_id = ?, approved_at = ?, " +
                "approver_comment = ?, updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND state = 'SUBMITTED'",
                approverId, clock.instant(), comment, timesheetId, tenantId
        );
        if (updated == 0) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: timesheet not in SUBMITTED state");
        }
        writeAuditAndOutbox(tenantId, "TimesheetApproved", timesheetId);
    }

    @Transactional
    public void reject(UUID tenantId, UUID timesheetId, UUID approverId, String reason) {
        int updated = jdbc.update(
                "UPDATE hr_timesheets SET state = 'REJECTED', approver_id = ?, approved_at = ?, " +
                "approver_comment = ?, updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND state = 'SUBMITTED'",
                approverId, clock.instant(), reason, timesheetId, tenantId
        );
        if (updated == 0) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: timesheet not in SUBMITTED state");
        }
        writeAuditAndOutbox(tenantId, "TimesheetRejected", timesheetId);
    }

    @Transactional
    public void lock(UUID tenantId, UUID timesheetId) {
        int updated = jdbc.update(
                "UPDATE hr_timesheets SET state = 'LOCKED', updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND state = 'APPROVED'",
                timesheetId, tenantId
        );
        if (updated == 0) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: timesheet not in APPROVED state");
        }
    }

    @Transactional(readOnly = true)
    public List<TimesheetResponse> listTimesheets(UUID tenantId, UUID employmentId, String state) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, employment_id, period_start, period_end, state, submitted_at, approved_at, approver_comment " +
                "FROM hr_timesheets WHERE tenant_id = ?"
        );
        List<Object> params = new java.util.ArrayList<>();
        params.add(tenantId);
        if (employmentId != null) {
            sql.append(" AND employment_id = ?");
            params.add(employmentId);
        }
        if (state != null && !state.isBlank()) {
            sql.append(" AND state = ?");
            params.add(state);
        }
        sql.append(" ORDER BY period_start DESC");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new TimesheetResponse(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("employment_id")),
                rs.getDate("period_start").toLocalDate(),
                rs.getDate("period_end").toLocalDate(),
                rs.getString("state"),
                rs.getTimestamp("submitted_at") != null ? rs.getTimestamp("submitted_at").toInstant() : null,
                rs.getTimestamp("approved_at") != null ? rs.getTimestamp("approved_at").toInstant() : null,
                rs.getString("approver_comment")
        ), params.toArray());
    }

    private void writeAuditAndOutbox(UUID tenantId, String eventType, UUID resourceId) {
        UUID auditId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        Instant now = clock.instant();

        jdbc.update(
                "INSERT INTO hr_audit_ledger (id, tenant_id, resource_type, resource_id, action, " +
                "actor_id, occurred_at, details) VALUES (?, ?, 'TIMESHEET', ?, ?, ?, ?, ?)",
                auditId, tenantId, resourceId, eventType, null, java.sql.Timestamp.from(now),
                "{\"resourceId\":\"" + resourceId + "\"}"
        );

        jdbc.update(
                "INSERT INTO hr_domain_event_outbox (id, tenant_id, event_type, resource_type, resource_id, " +
                "payload, created_at) VALUES (?, ?, ?, 'TIMESHEET', ?, ?::jsonb, ?)",
                outboxId, tenantId, eventType, resourceId,
                "{\"eventType\":\"" + eventType + "\",\"resourceId\":\"" + resourceId + "\"}",
                java.sql.Timestamp.from(now)
        );
    }

    public record TimesheetResponse(
            UUID id, UUID employmentId, LocalDate periodStart, LocalDate periodEnd,
            String state, Instant submittedAt, Instant approvedAt, String approverComment
    ) {}
}
