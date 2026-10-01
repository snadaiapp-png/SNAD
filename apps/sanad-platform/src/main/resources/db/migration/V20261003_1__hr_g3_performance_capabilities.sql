-- HRM G3 Task 4 — capability catalog and canonical role reconciliation.
-- Forward-only and schema-neutral. The seven Task 4 capabilities are granted only
-- to tenant ADMIN roles and the canonical PLATFORM_OWNER role in the control tenant.
-- HR_MANAGER is intentionally never referenced or broadened. Runtime/API
-- authorization remains @RequireCapability based.

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


-- Backfill only the seven G3 performance capabilities to:
--   * existing ACTIVE tenant ADMIN roles; and
--   * the canonical ACTIVE PLATFORM_OWNER role in the control tenant.
-- This preserves the platform-owner invariant that the owner role carries every
-- active capability, without introducing any HR_MANAGER grant or legacy fallback.
DO $$
DECLARE
    t RECORD;
    r RECORD;
    cap RECORD;
BEGIN
    FOR t IN SELECT id FROM tenants WHERE status = 'ACTIVE' LOOP
        PERFORM set_config('app.tenant_id', t.id::text, false);

        FOR r IN
            SELECT id, tenant_id, code
              FROM roles
             WHERE tenant_id = t.id
               AND status = 'ACTIVE'
               AND (
                    code = 'ADMIN'
                    OR (
                        code = 'PLATFORM_OWNER'
                        AND t.id = '00000000-0000-0000-0000-000000000001'::uuid
                    )
               )
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
                       FALSE,
                       CASE
                           WHEN r.code = 'PLATFORM_OWNER'
                               THEN 'HRM-G3 Task 4 canonical PLATFORM_OWNER tenant grant'
                           ELSE 'HRM-G3 Task 4 canonical ADMIN tenant grant'
                       END,
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
