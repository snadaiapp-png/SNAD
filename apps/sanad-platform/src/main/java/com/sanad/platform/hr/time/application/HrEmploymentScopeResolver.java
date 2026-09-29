package com.sanad.platform.hr.time.application;

import org.springframework.dao.EmptyResultDataAccessException;
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
                      FROM hr_people person
                      JOIN hr_employees employee
                        ON employee.person_id = person.id
                       AND employee.tenant_id = person.tenant_id
                      JOIN users u
                        ON u.id = person.user_id
                       AND u.tenant_id = person.tenant_id
                     WHERE person.tenant_id = ?
                       AND person.user_id = ?
                       AND employee.status = 'ACTIVE'
                       AND u.status = 'ACTIVE'
                    """,
                    UUID.class,
                    tenantId,
                    userId);
        } catch (EmptyResultDataAccessException ex) {
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
                  FROM hr_people manager_person
                  JOIN hr_employees manager
                    ON manager.person_id = manager_person.id
                   AND manager.tenant_id = manager_person.tenant_id
                  JOIN hr_employee_assignments manager_assignment
                    ON manager_assignment.employment_id = manager.id
                   AND manager_assignment.tenant_id = manager.tenant_id
                  JOIN hr_employee_assignments target_assignment
                    ON target_assignment.tenant_id = manager_assignment.tenant_id
                   AND target_assignment.reports_to_assignment_id = manager_assignment.id
                  JOIN hr_employees target
                    ON target.id = target_assignment.employment_id
                   AND target.tenant_id = target_assignment.tenant_id
                  JOIN users manager_user
                    ON manager_user.id = manager_person.user_id
                   AND manager_user.tenant_id = manager_person.tenant_id
                 WHERE manager_person.tenant_id = ?
                   AND manager_person.user_id = ?
                   AND target.id = ?
                   AND target.status = 'ACTIVE'
                   AND manager.status = 'ACTIVE'
                   AND manager_user.status = 'ACTIVE'
                   AND manager_assignment.assignment_type = 'PRIMARY'
                   AND manager_assignment.status = 'ACTIVE'
                   AND manager_assignment.effective_from <= CURRENT_DATE
                   AND (manager_assignment.effective_to IS NULL OR manager_assignment.effective_to >= CURRENT_DATE)
                   AND target_assignment.assignment_type = 'PRIMARY'
                   AND target_assignment.status = 'ACTIVE'
                   AND target_assignment.effective_from <= CURRENT_DATE
                   AND (target_assignment.effective_to IS NULL OR target_assignment.effective_to >= CURRENT_DATE)
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
