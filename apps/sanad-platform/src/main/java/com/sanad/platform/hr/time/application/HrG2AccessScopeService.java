package com.sanad.platform.hr.time.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Canonical G2 authorization scope resolver.
 *
 * <p>Capability checks answer whether an action family is allowed. This service
 * answers which HR employee rows the authenticated user may act on. SELF scope
 * is always derived from the authenticated user_id; callers cannot select an
 * arbitrary employment_id. TEAM scope is restricted to direct reports of the
 * authenticated manager employee. Tenant isolation remains enforced by RLS.
 */
@Service
public class HrG2AccessScopeService {

    private final JdbcTemplate jdbc;

    public HrG2AccessScopeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public UUID requireSelfEmployment(UUID tenantId, UUID userId) {
        List<UUID> ids = jdbc.query(
                "SELECT id FROM hr_employees " +
                "WHERE tenant_id = ? AND user_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                tenantId, userId);
        if (ids.size() != 1) {
            throw new AccessDeniedException("Authenticated user is not linked to exactly one active HR employee");
        }
        return ids.get(0);
    }

    @Transactional(readOnly = true)
    public UUID requireSelfEmployment(UUID tenantId, UUID userId, UUID requestedEmploymentId) {
        UUID self = requireSelfEmployment(tenantId, userId);
        if (requestedEmploymentId != null && !self.equals(requestedEmploymentId)) {
            throw new AccessDeniedException("SELF scope cannot target another employee");
        }
        return self;
    }

    @Transactional(readOnly = true)
    public List<UUID> directReportEmploymentIds(UUID tenantId, UUID managerUserId) {
        return jdbc.query(
                "SELECT report.id " +
                "FROM hr_employees manager " +
                "JOIN hr_employees report ON report.tenant_id = manager.tenant_id " +
                "  AND report.manager_id = manager.id " +
                "WHERE manager.tenant_id = ? AND manager.user_id = ? " +
                "  AND manager.status = 'ACTIVE' AND report.status = 'ACTIVE' " +
                "ORDER BY report.id",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                tenantId, managerUserId);
    }

    @Transactional(readOnly = true)
    public void requireOwnedAttendanceRecord(UUID tenantId, UUID userId, UUID recordId) {
        UUID self = requireSelfEmployment(tenantId, userId);
        requireExists(
                "SELECT COUNT(*) FROM hr_attendance_records WHERE tenant_id = ? AND id = ? AND employment_id = ?",
                "Attendance record is outside SELF scope", tenantId, recordId, self);
    }

    @Transactional(readOnly = true)
    public void requireOwnedTimesheet(UUID tenantId, UUID userId, UUID timesheetId) {
        UUID self = requireSelfEmployment(tenantId, userId);
        requireExists(
                "SELECT COUNT(*) FROM hr_timesheets WHERE tenant_id = ? AND id = ? AND employment_id = ?",
                "Timesheet is outside SELF scope", tenantId, timesheetId, self);
    }

    @Transactional(readOnly = true)
    public void requireOwnedLeaveRequest(UUID tenantId, UUID userId, UUID requestId) {
        UUID self = requireSelfEmployment(tenantId, userId);
        requireExists(
                "SELECT COUNT(*) FROM hr_leave_requests WHERE tenant_id = ? AND id = ? AND employment_id = ?",
                "Leave request is outside SELF scope", tenantId, requestId, self);
    }

    @Transactional(readOnly = true)
    public void requireDirectReportTimesheet(UUID tenantId, UUID managerUserId, UUID timesheetId) {
        requireDirectReportResource(
                tenantId, managerUserId, timesheetId,
                "hr_timesheets", "Timesheet is outside TEAM scope");
    }

    @Transactional(readOnly = true)
    public void requireDirectReportLeaveRequest(UUID tenantId, UUID managerUserId, UUID requestId) {
        requireDirectReportResource(
                tenantId, managerUserId, requestId,
                "hr_leave_requests", "Leave request is outside TEAM scope");
    }

    private void requireDirectReportResource(
            UUID tenantId, UUID managerUserId, UUID resourceId, String table, String message) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " resource " +
                "JOIN hr_employees report ON report.tenant_id = resource.tenant_id " +
                "  AND report.id = resource.employment_id " +
                "JOIN hr_employees manager ON manager.tenant_id = report.tenant_id " +
                "  AND manager.id = report.manager_id " +
                "WHERE resource.tenant_id = ? AND resource.id = ? " +
                "  AND manager.user_id = ? AND manager.status = 'ACTIVE' AND report.status = 'ACTIVE'",
                Integer.class, tenantId, resourceId, managerUserId);
        if (count == null || count != 1) {
            throw new AccessDeniedException(message);
        }
    }

    private void requireExists(String sql, String message, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        if (count == null || count != 1) {
            throw new AccessDeniedException(message);
        }
    }
}
