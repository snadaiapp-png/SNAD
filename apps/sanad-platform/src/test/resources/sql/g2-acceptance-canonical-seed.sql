-- G2 canonical HR identity supplement.
-- The legacy acceptance fixture above remains intact; this file adds only the
-- canonical Person -> Employment -> Assignment graph required by current HR
-- authorization resolution. All writes remain tenant-scoped under FORCE RLS.

SELECT set_config('app.tenant_id', '33333333-3333-4333-8333-333333333331', false);

-- Canonical Person identities bound 1:1 to the existing acceptance users.
INSERT INTO hr_people (
    id, tenant_id, user_id, first_name, last_name, display_name, created_at, updated_at
) VALUES
    ('33333333-3333-4333-8333-333333333401', '33333333-3333-4333-8333-333333333331', '33333333-3333-4333-8333-333333333341', 'G2', 'Employee', 'G2 Employee', NOW(), NOW()),
    ('33333333-3333-4333-8333-333333333402', '33333333-3333-4333-8333-333333333331', '33333333-3333-4333-8333-333333333342', 'G2', 'Manager',  'G2 Manager',  NOW(), NOW()),
    ('33333333-3333-4333-8333-333333333403', '33333333-3333-4333-8333-333333333331', '33333333-3333-4333-8333-333333333343', 'G2', 'HR',       'G2 HR',       NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    display_name = EXCLUDED.display_name,
    updated_at = NOW();

-- Bridge the existing employment fixtures to canonical Person identities.
UPDATE hr_employees
   SET person_id = '33333333-3333-4333-8333-333333333401', updated_at = NOW()
 WHERE tenant_id = '33333333-3333-4333-8333-333333333331'
   AND id = '33333333-3333-4333-8333-333333333361';

UPDATE hr_employees
   SET person_id = '33333333-3333-4333-8333-333333333402', updated_at = NOW()
 WHERE tenant_id = '33333333-3333-4333-8333-333333333331'
   AND id = '33333333-3333-4333-8333-333333333362';

UPDATE hr_employees
   SET person_id = '33333333-3333-4333-8333-333333333403', updated_at = NOW()
 WHERE tenant_id = '33333333-3333-4333-8333-333333333331'
   AND id = '33333333-3333-4333-8333-333333333363';

-- Canonical PRIMARY assignments. The employee reports to the manager through
-- reports_to_assignment_id; no legacy manager_id dependency is required.
INSERT INTO hr_employee_assignments (
    id, tenant_id, employment_id, organization_id,
    reports_to_assignment_id, assignment_type, occupancy_mode,
    allocation_percent, effective_from, effective_to, status,
    created_at, updated_at
) VALUES (
    '33333333-3333-4333-8333-333333333412',
    '33333333-3333-4333-8333-333333333331',
    '33333333-3333-4333-8333-333333333362',
    '33333333-3333-4333-8333-333333333335',
    NULL, 'PRIMARY', 'NON_OCCUPYING', 100.00,
    CURRENT_DATE - 1, NULL, 'ACTIVE', NOW(), NOW()
)
ON CONFLICT (id) DO UPDATE SET
    employment_id = EXCLUDED.employment_id,
    organization_id = EXCLUDED.organization_id,
    reports_to_assignment_id = EXCLUDED.reports_to_assignment_id,
    assignment_type = EXCLUDED.assignment_type,
    occupancy_mode = EXCLUDED.occupancy_mode,
    allocation_percent = EXCLUDED.allocation_percent,
    effective_from = EXCLUDED.effective_from,
    effective_to = EXCLUDED.effective_to,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO hr_employee_assignments (
    id, tenant_id, employment_id, organization_id,
    reports_to_assignment_id, assignment_type, occupancy_mode,
    allocation_percent, effective_from, effective_to, status,
    created_at, updated_at
) VALUES (
    '33333333-3333-4333-8333-333333333411',
    '33333333-3333-4333-8333-333333333331',
    '33333333-3333-4333-8333-333333333361',
    '33333333-3333-4333-8333-333333333335',
    '33333333-3333-4333-8333-333333333412',
    'PRIMARY', 'NON_OCCUPYING', 100.00,
    CURRENT_DATE - 1, NULL, 'ACTIVE', NOW(), NOW()
)
ON CONFLICT (id) DO UPDATE SET
    employment_id = EXCLUDED.employment_id,
    organization_id = EXCLUDED.organization_id,
    reports_to_assignment_id = EXCLUDED.reports_to_assignment_id,
    assignment_type = EXCLUDED.assignment_type,
    occupancy_mode = EXCLUDED.occupancy_mode,
    allocation_percent = EXCLUDED.allocation_percent,
    effective_from = EXCLUDED.effective_from,
    effective_to = EXCLUDED.effective_to,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO hr_employee_assignments (
    id, tenant_id, employment_id, organization_id,
    reports_to_assignment_id, assignment_type, occupancy_mode,
    allocation_percent, effective_from, effective_to, status,
    created_at, updated_at
) VALUES (
    '33333333-3333-4333-8333-333333333413',
    '33333333-3333-4333-8333-333333333331',
    '33333333-3333-4333-8333-333333333363',
    '33333333-3333-4333-8333-333333333335',
    NULL, 'PRIMARY', 'NON_OCCUPYING', 100.00,
    CURRENT_DATE - 1, NULL, 'ACTIVE', NOW(), NOW()
)
ON CONFLICT (id) DO UPDATE SET
    employment_id = EXCLUDED.employment_id,
    organization_id = EXCLUDED.organization_id,
    reports_to_assignment_id = EXCLUDED.reports_to_assignment_id,
    assignment_type = EXCLUDED.assignment_type,
    occupancy_mode = EXCLUDED.occupancy_mode,
    allocation_percent = EXCLUDED.allocation_percent,
    effective_from = EXCLUDED.effective_from,
    effective_to = EXCLUDED.effective_to,
    status = EXCLUDED.status,
    updated_at = NOW();

-- Fail closed if the canonical graph is incomplete or ambiguous.
DO $$
DECLARE
    people_count INTEGER;
    employment_count INTEGER;
    assignment_count INTEGER;
    reporting_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO people_count
      FROM hr_people
     WHERE tenant_id = '33333333-3333-4333-8333-333333333331'
       AND user_id IN (
           '33333333-3333-4333-8333-333333333341',
           '33333333-3333-4333-8333-333333333342',
           '33333333-3333-4333-8333-333333333343'
       );

    SELECT COUNT(*) INTO employment_count
      FROM hr_people p
      JOIN hr_employees e
        ON e.tenant_id = p.tenant_id
       AND e.person_id = p.id
     WHERE p.tenant_id = '33333333-3333-4333-8333-333333333331'
       AND p.user_id IN (
           '33333333-3333-4333-8333-333333333341',
           '33333333-3333-4333-8333-333333333342',
           '33333333-3333-4333-8333-333333333343'
       )
       AND e.status = 'ACTIVE';

    SELECT COUNT(*) INTO assignment_count
      FROM hr_employee_assignments a
     WHERE a.tenant_id = '33333333-3333-4333-8333-333333333331'
       AND a.id IN (
           '33333333-3333-4333-8333-333333333411',
           '33333333-3333-4333-8333-333333333412',
           '33333333-3333-4333-8333-333333333413'
       )
       AND a.assignment_type = 'PRIMARY'
       AND a.status = 'ACTIVE'
       AND a.effective_from <= CURRENT_DATE
       AND (a.effective_to IS NULL OR a.effective_to >= CURRENT_DATE);

    SELECT COUNT(*) INTO reporting_count
      FROM hr_employee_assignments employee_assignment
      JOIN hr_employee_assignments manager_assignment
        ON manager_assignment.id = employee_assignment.reports_to_assignment_id
       AND manager_assignment.tenant_id = employee_assignment.tenant_id
     WHERE employee_assignment.tenant_id = '33333333-3333-4333-8333-333333333331'
       AND employee_assignment.id = '33333333-3333-4333-8333-333333333411'
       AND employee_assignment.employment_id = '33333333-3333-4333-8333-333333333361'
       AND manager_assignment.id = '33333333-3333-4333-8333-333333333412'
       AND manager_assignment.employment_id = '33333333-3333-4333-8333-333333333362';

    IF people_count <> 3 THEN
        RAISE EXCEPTION 'G2 canonical seed people_count=% expected=3', people_count;
    END IF;
    IF employment_count <> 3 THEN
        RAISE EXCEPTION 'G2 canonical seed employment_count=% expected=3', employment_count;
    END IF;
    IF assignment_count <> 3 THEN
        RAISE EXCEPTION 'G2 canonical seed assignment_count=% expected=3', assignment_count;
    END IF;
    IF reporting_count <> 1 THEN
        RAISE EXCEPTION 'G2 canonical seed reporting_count=% expected=1', reporting_count;
    END IF;
END
$$;

SELECT 'g2-canonical-seed: people=3 employments=3 assignments=3 reporting=PASS';

SELECT set_config('app.tenant_id', '', false);
