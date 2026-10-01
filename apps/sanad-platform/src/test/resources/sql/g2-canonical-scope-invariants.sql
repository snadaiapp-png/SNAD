-- ============================================================
-- G2 canonical HR scope invariants
-- ------------------------------------------------------------
-- Executed after g2-acceptance-seed.sql on the same PostgreSQL Direct
-- candidate database. This is intentionally an executable fail-closed
-- contract, not a diagnostic count: if the seeded graph cannot satisfy the
-- same identity/relationship semantics used by HrEmploymentScopeResolver,
-- psql -v ON_ERROR_STOP=1 must stop the acceptance pipeline before the
-- backend or Playwright can hide the defect behind a later HTTP 403.
-- ============================================================

SELECT set_config('app.tenant_id', '33333333-3333-4333-8333-333333333331', false);

DO $$
DECLARE
    v_self_scope_principals INTEGER;
    v_team_scope_links INTEGER;
BEGIN
    -- SELF contract mirrors HrEmploymentScopeResolver.requireSelfEmployment:
    -- active governed User -> canonical Person -> exactly one ACTIVE Employment
    -- in the same tenant. Legacy hr_employees.user_id is deliberately not used
    -- as an ownership source.
    SELECT COUNT(*)
      INTO v_self_scope_principals
      FROM (
            SELECT person.user_id
              FROM hr_employees employee
              JOIN hr_people person
                ON employee.person_id = person.id
               AND employee.tenant_id = person.tenant_id
              JOIN users governed_user
                ON governed_user.id = person.user_id
               AND governed_user.tenant_id = person.tenant_id
             WHERE employee.tenant_id = '33333333-3333-4333-8333-333333333331'
               AND person.user_id IN (
                    '33333333-3333-4333-8333-333333333341',
                    '33333333-3333-4333-8333-333333333342',
                    '33333333-3333-4333-8333-333333333343'
               )
               AND employee.status = 'ACTIVE'
               AND governed_user.status = 'ACTIVE'
             GROUP BY person.user_id
            HAVING COUNT(employee.id) = 1
      ) uniquely_resolvable_self;

    IF v_self_scope_principals <> 3 THEN
        RAISE EXCEPTION
            'G2_CANONICAL_SELF_SCOPE_INVARIANT_FAILED: expected 3 uniquely resolvable active principals, found %',
            v_self_scope_principals;
    END IF;

    -- TEAM contract mirrors HrEmploymentScopeResolver.requireManagedEmployment:
    -- the Employee target must be linked to the Manager principal through the
    -- effective ACTIVE PRIMARY assignment graph, not legacy manager_id.
    SELECT COUNT(*)
      INTO v_team_scope_links
      FROM hr_employees target_employee
      JOIN hr_employee_assignments target_assignment
        ON target_assignment.employment_id = target_employee.id
       AND target_assignment.tenant_id = target_employee.tenant_id
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
     WHERE target_employee.tenant_id = '33333333-3333-4333-8333-333333333331'
       AND target_employee.id = '33333333-3333-4333-8333-333333333361'
       AND manager_person.user_id = '33333333-3333-4333-8333-333333333342'
       AND target_employee.status = 'ACTIVE'
       AND manager_employment.status = 'ACTIVE'
       AND manager_user.status = 'ACTIVE'
       AND target_assignment.assignment_type = 'PRIMARY'
       AND target_assignment.status = 'ACTIVE'
       AND target_assignment.effective_from <= CURRENT_DATE
       AND (target_assignment.effective_to IS NULL OR target_assignment.effective_to >= CURRENT_DATE)
       AND manager_assignment.assignment_type = 'PRIMARY'
       AND manager_assignment.status = 'ACTIVE'
       AND manager_assignment.effective_from <= CURRENT_DATE
       AND (manager_assignment.effective_to IS NULL OR manager_assignment.effective_to >= CURRENT_DATE);

    IF v_team_scope_links <> 1 THEN
        RAISE EXCEPTION
            'G2_CANONICAL_TEAM_SCOPE_INVARIANT_FAILED: expected exactly 1 Employee->Manager active PRIMARY reporting link, found %',
            v_team_scope_links;
    END IF;
END $$;

SELECT 'g2-canonical-scope-invariants: PASS self=3 team=1';
SELECT set_config('app.tenant_id', '', false);
