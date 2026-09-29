package com.sanad.platform.hr.time.application;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves authenticated platform principals to HR employment identities and
 * enforces direct-report relationships for TEAM operations.
 *
 * <p>Every query includes tenant_id and active-state predicates. PostgreSQL RLS
 * remains the final tenant-isolation boundary; this resolver supplies the
 * application-level SELF/ReBAC constraint before business data is touched.</p>
 */
@Service
public class HrEmploymentScopeResolver {

    private final JdbcTemplate jdbc;

    public HrEmploymentScopeResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public UUID requireSelfEmployment(UUID tenantId, UUID userId) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT employee.id
                      FROM hr_employees employee
                      JOIN hr_people person
                        ON person.id = employee.person_id
                       AND person.tenant_id = employee.tenant_id
                      JOIN users u
                        ON u.id = person.user_id
                       AND u.tenant_id = person.tenant_id
                     WHERE employee.tenant_id = ?
                       AND person.user_id = ?
                       AND employee.status = 'ACTIVE'
                       AND u.status = 'ACTIVE'
                    """,
                    UUID.class,
                    tenantId,
                    userId);
        } catch (IncorrectResultSizeDataAccessException ex) {
            throw new AccessDeniedException(
                    "Authenticated principal has no active HR employment in this tenant", ex);
        }
    }

    @Transactional(readOnly = true)
    public void requireManagedEmployment(
            UUID tenantId,
            UUID managerUserId,
            UUID targetEmploymentId) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM hr_employees target
                  JOIN hr_employee_assignments target_assignment
                    ON target_assignment.employment_id = target.id
                   AND target_assignment.tenant_id = target.tenant_id
                  JOIN hr_employee_assignments manager_assignment
                    ON target_assignment.reports_to_assignment_id = manager_assignment.id
                   AND manager_assignment.tenant_id = target_assignment.tenant_id
                  JOIN hr_employees manager_employment
                    ON manager_employment.id = manager_assignment.employment_id
                   AND manager_employment.tenant_id = manager_assignment.tenant_id
                  JOIN hr_people manager_person
                    ON manager_person.id = manager_employment.person_id
                   AND manager_person.tenant_id = manager_employment.tenant_id
                  JOIN users manager_user
                    ON manager_user.id = manager_person.user_id
                   AND manager_user.tenant_id = manager_person.tenant_id
                 WHERE target.tenant_id = ?
                   AND manager_person.user_id = ?
                   AND target.id = ?
                   AND target.status = 'ACTIVE'
                   AND manager_employment.status = 'ACTIVE'
                   AND manager_user.status = 'ACTIVE'
                   AND target_assignment.assignment_type = 'PRIMARY'
                   AND target_assignment.status = 'ACTIVE'
                   AND target_assignment.effective_from <= CURRENT_DATE
                   AND (target_assignment.effective_to IS NULL OR target_assignment.effective_to >= CURRENT_DATE)
                   AND manager_assignment.assignment_type = 'PRIMARY'
                   AND manager_assignment.status = 'ACTIVE'
                   AND manager_assignment.effective_from <= CURRENT_DATE
                   AND (manager_assignment.effective_to IS NULL OR manager_assignment.effective_to >= CURRENT_DATE)
                """,
                Integer.class,
                tenantId,
                managerUserId,
                targetEmploymentId);
        if (count == null || count != 1) {
            throw new AccessDeniedException(
                    "Target employment is not an active direct report of the authenticated manager");
        }
    }
}
