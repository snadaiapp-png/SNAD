package com.sanad.platform.hr.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Component
public class HrResourceContextResolver {

    private final JdbcTemplate jdbc;

    public HrResourceContextResolver(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public boolean isSelf(UUID tenantId, UUID userId, UUID personId) {
        if (personId == null) return false;
        Boolean match = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM hr_people WHERE tenant_id = ? AND user_id = ? AND id = ?)",
                Boolean.class, tenantId, userId, personId);
        return Boolean.TRUE.equals(match);
    }

    public boolean isDirectReport(UUID tenantId, UUID actorUserId, UUID targetEmploymentId, LocalDate authorizationDate) {
        if (targetEmploymentId == null) return false;
        Boolean match = jdbc.queryForObject(
                "SELECT EXISTS(" +
                        "SELECT 1 FROM hr_people p " +
                        "JOIN hr_employees actor_e ON actor_e.tenant_id = p.tenant_id AND actor_e.person_id = p.id " +
                        "JOIN hr_employee_assignments actor_a ON actor_a.tenant_id = actor_e.tenant_id AND actor_a.employment_id = actor_e.id " +
                        "JOIN hr_employee_assignments target_a ON target_a.tenant_id = actor_a.tenant_id AND target_a.reports_to_assignment_id = actor_a.id " +
                        "WHERE p.tenant_id = ? AND p.user_id = ? AND target_a.employment_id = ? " +
                        "AND actor_a.assignment_type = 'PRIMARY' AND actor_a.status = 'ACTIVE' " +
                        "AND actor_a.effective_from <= ? AND (actor_a.effective_to IS NULL OR actor_a.effective_to >= ?) " +
                        "AND target_a.assignment_type = 'PRIMARY' AND target_a.status = 'ACTIVE' " +
                        "AND target_a.effective_from <= ? AND (target_a.effective_to IS NULL OR target_a.effective_to >= ?)" +
                        ")",
                Boolean.class,
                tenantId, actorUserId, targetEmploymentId,
                authorizationDate, authorizationDate, authorizationDate, authorizationDate);
        return Boolean.TRUE.equals(match);
    }

    public boolean isInReportingTree(UUID tenantId, UUID actorUserId, UUID targetEmploymentId, LocalDate authorizationDate) {
        if (targetEmploymentId == null) return false;
        Boolean match = jdbc.queryForObject(
                "WITH RECURSIVE reporting(id, employment_id, path) AS (" +
                        "SELECT a.id, a.employment_id, ARRAY[a.id]::uuid[] " +
                        "FROM hr_people p " +
                        "JOIN hr_employees e ON e.tenant_id = p.tenant_id AND e.person_id = p.id " +
                        "JOIN hr_employee_assignments a ON a.tenant_id = e.tenant_id AND a.employment_id = e.id " +
                        "WHERE p.tenant_id = ? AND p.user_id = ? " +
                        "AND a.assignment_type = 'PRIMARY' AND a.status = 'ACTIVE' " +
                        "AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to >= ?) " +
                        "UNION ALL " +
                        "SELECT child.id, child.employment_id, reporting.path || child.id " +
                        "FROM reporting " +
                        "JOIN hr_employee_assignments child ON child.tenant_id = ? AND child.reports_to_assignment_id = reporting.id " +
                        "WHERE child.assignment_type = 'PRIMARY' AND child.status = 'ACTIVE' " +
                        "AND child.effective_from <= ? AND (child.effective_to IS NULL OR child.effective_to >= ?) " +
                        "AND NOT child.id = ANY(reporting.path)" +
                        ") SELECT EXISTS(SELECT 1 FROM reporting WHERE employment_id = ? AND cardinality(path) > 1)",
                Boolean.class,
                tenantId, actorUserId, authorizationDate, authorizationDate,
                tenantId, authorizationDate, authorizationDate, targetEmploymentId);
        return Boolean.TRUE.equals(match);
    }

    public boolean orgUnitContains(
            UUID tenantId,
            UUID organizationId,
            UUID grantedOrgUnitId,
            UUID targetOrgUnitId,
            LocalDate authorizationDate) {
        if (grantedOrgUnitId == null || targetOrgUnitId == null) return false;
        Boolean match = jdbc.queryForObject(
                "WITH RECURSIVE units(id, path) AS (" +
                        "SELECT u.id, ARRAY[u.id]::uuid[] " +
                        "FROM hr_org_units u " +
                        "JOIN hr_org_unit_versions v ON v.tenant_id = u.tenant_id AND v.org_unit_id = u.id " +
                        "WHERE u.tenant_id = ? AND u.id = ? AND (?::uuid IS NULL OR u.organization_id = ?::uuid) " +
                        "AND v.status = 'ACTIVE' AND v.effective_from <= ? AND (v.effective_to IS NULL OR v.effective_to >= ?) " +
                        "UNION ALL " +
                        "SELECT child_v.org_unit_id, units.path || child_v.org_unit_id " +
                        "FROM units " +
                        "JOIN hr_org_unit_versions child_v ON child_v.tenant_id = ? AND child_v.parent_org_unit_id = units.id " +
                        "WHERE child_v.status = 'ACTIVE' AND child_v.effective_from <= ? " +
                        "AND (child_v.effective_to IS NULL OR child_v.effective_to >= ?) " +
                        "AND NOT child_v.org_unit_id = ANY(units.path)" +
                        ") SELECT EXISTS(SELECT 1 FROM units WHERE id = ?)",
                Boolean.class,
                tenantId, grantedOrgUnitId, organizationId, organizationId,
                authorizationDate, authorizationDate,
                tenantId, authorizationDate, authorizationDate, targetOrgUnitId);
        return Boolean.TRUE.equals(match);
    }
}
