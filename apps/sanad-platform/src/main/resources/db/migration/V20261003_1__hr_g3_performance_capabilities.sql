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


-- Backfill only the seven G3 performance capabilities to existing ACTIVE
-- tenant ADMIN roles, matching the canonical HRM capability migration pattern.
-- HR_MANAGER is intentionally never referenced or broadened.
DO $$
DECLARE
    t RECORD;
    r RECORD;
    cap RECORD;
BEGIN
    FOR t IN SELECT id FROM tenants WHERE status = 'ACTIVE' LOOP
        PERFORM set_config('app.tenant_id', t.id::text, false);

        FOR r IN SELECT id, tenant_id FROM roles
                 WHERE tenant_id = t.id AND code = 'ADMIN' AND status = 'ACTIVE'
        LOOP
            FOR cap IN
                SELECT id, code
                  FROM access_capabilities
                 WHERE code IN (
                    'HRM.PERFORMANCE.GOAL.SELF_VIEW',
                    'HRM.PERFORMANCE.GOAL.SELF_UPDATE',
                    'HRM.PERFORMANCE.GOAL.TEAM_MANAGE',
                    'HRM.PERFORMANCE.REVIEW.SELF_VIEW',
                    'HRM.PERFORMANCE.REVIEW.SELF_SUBMIT',
                    'HRM.PERFORMANCE.REVIEW.TEAM_MANAGE',
                    'HRM.PERFORMANCE.ADMIN'
                 )
                   AND status = 'ACTIVE'
            LOOP
                INSERT INTO role_capabilities
                    (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), r.tenant_id, r.id, cap.id, NOW()
                WHERE NOT EXISTS (
                    SELECT 1 FROM role_capabilities rc
                     WHERE rc.tenant_id = r.tenant_id
                       AND rc.role_id = r.id
                       AND rc.capability_id = cap.id
                );

                INSERT INTO access_scope_grants
                    (id, tenant_id, role_id, capability_id, scope_type,
                     is_direct_exception, reason, status, created_at)
                SELECT gen_random_uuid(), r.tenant_id, r.id, cap.id, 'TENANT',
                       FALSE, 'HRM-G3 Task 4 canonical ADMIN tenant grant',
                       'ACTIVE', NOW()
                WHERE NOT EXISTS (
                    SELECT 1 FROM access_scope_grants g
                     WHERE g.tenant_id = r.tenant_id
                       AND g.role_id = r.id
                       AND g.capability_id = cap.id
                       AND g.scope_type = 'TENANT'
                       AND g.status = 'ACTIVE'
                );
            END LOOP;
        END LOOP;
    END LOOP;

    PERFORM set_config('app.tenant_id', '', false);
END $$;
