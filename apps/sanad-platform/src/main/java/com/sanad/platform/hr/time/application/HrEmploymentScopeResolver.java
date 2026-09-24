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
                      FROM hr_employees employee
                      JOIN users u
                        ON u.id = employee.user_id
                       AND u.tenant_id = employee.tenant_id
                     WHERE employee.tenant_id = ?
                       AND employee.user_id = ?
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
                  FROM hr_employees target
                  JOIN hr_employees manager
                    ON manager.id = target.manager_id
                   AND manager.tenant_id = target.tenant_id
                  JOIN users manager_user
                    ON manager_user.id = manager.user_id
                   AND manager_user.tenant_id = manager.tenant_id
                 WHERE target.tenant_id = ?
                   AND manager.user_id = ?
                   AND target.id = ?
                   AND target.status = 'ACTIVE'
                   AND manager.status = 'ACTIVE'
                   AND manager_user.status = 'ACTIVE'
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
