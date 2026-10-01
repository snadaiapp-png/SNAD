-- HRM G3 Task 4 — capability catalog only.
-- Forward-only and schema-neutral. No role-template mutation and no implicit
-- HR_MANAGER grant. Runtime/API authorization remains @RequireCapability based.

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), c.code, c.name, c.description, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('HRM.PERFORMANCE.GOAL.SELF_VIEW',
     'Performance Goal Self View',
     'View performance goals for the authenticated employee'),
    ('HRM.PERFORMANCE.GOAL.SELF_UPDATE',
     'Performance Goal Self Update',
     'Create and update performance goals for the authenticated employee'),
    ('HRM.PERFORMANCE.GOAL.TEAM_MANAGE',
     'Performance Goal Team Manage',
     'Manage performance goals for canonical direct reports'),
    ('HRM.PERFORMANCE.REVIEW.SELF_VIEW',
     'Performance Review Self View',
     'View performance reviews visible to the authenticated employee'),
    ('HRM.PERFORMANCE.REVIEW.SELF_SUBMIT',
     'Performance Review Self Submit',
     'Create and submit the authenticated employee performance review'),
    ('HRM.PERFORMANCE.REVIEW.TEAM_MANAGE',
     'Performance Review Team Manage',
     'Manage performance reviews for canonical direct reports'),
    ('HRM.PERFORMANCE.ADMIN',
     'Performance Administration',
     'Tenant-wide governed performance administration')
) AS c(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = c.code
);
