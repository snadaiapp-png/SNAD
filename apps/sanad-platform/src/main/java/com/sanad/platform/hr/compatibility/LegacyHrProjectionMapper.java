package com.sanad.platform.hr.compatibility;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 7 — projects a canonical tenant's Person + Employment +
 * effective PRIMARY Assignment into the legacy v1 {@code HrEmployee}
 * response shape. Read-only compatibility: canonical data stays
 * authoritative and nothing is copied.
 */
@Service
public class LegacyHrProjectionMapper {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public LegacyHrProjectionMapper(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    }

    /**
     * Canonical projection for one employment. Returns {@code null} when the
     * tenant is not CANONICAL (caller falls back to the legacy read path).
     */
    public LegacyProjection projectCanonical(UUID tenantId, UUID employmentId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return transactionTemplate.execute(status -> {
            bindTenant(tenantId);
            List<LegacyProjection> rows = jdbc.query(
                    "SELECT e.id, e.tenant_id, p.user_id, e.employee_number, p.first_name, p.last_name, " +
                            "p.display_name, e.employment_type, e.status AS employment_status, " +
                            "a.position_id, a.org_unit_id " +
                            "FROM hr_employees e " +
                            "JOIN hr_people p ON p.id = e.person_id AND p.tenant_id = e.tenant_id " +
                            "LEFT JOIN hr_employee_assignments a ON a.employment_id = e.id " +
                            "AND a.assignment_type = 'PRIMARY' AND a.status = 'ACTIVE' " +
                            "AND a.effective_from <= NOW()::date AND (a.effective_to IS NULL OR a.effective_to >= NOW()::date) " +
                            "WHERE e.tenant_id = ? AND e.id = ? LIMIT 1",
                    (rs, rowNum) -> new LegacyProjection(
                            rs.getObject("id", UUID.class),
                            rs.getObject("tenant_id", UUID.class),
                            rs.getObject("user_id", UUID.class),
                            rs.getString("employee_number"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("display_name"),
                            rs.getString("employment_type"),
                            "ACTIVE".equals(rs.getString("employment_status")) ? "ACTIVE" : rs.getString("employment_status"),
                            rs.getObject("position_id", UUID.class),
                            rs.getObject("org_unit_id", UUID.class)),
                    tenantId, employmentId);
            return rows.isEmpty() ? null : rows.get(0);
        });
    }

    private void bindTenant(UUID tenantId) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
    }

    /** Legacy v1 response fields projected from canonical data. */
    public record LegacyProjection(
            UUID id,
            UUID tenantId,
            UUID userId,
            String employeeNumber,
            String firstName,
            String lastName,
            String displayName,
            String employmentType,
            String status,
            UUID positionId,
            UUID orgUnitId) {
    }
}
