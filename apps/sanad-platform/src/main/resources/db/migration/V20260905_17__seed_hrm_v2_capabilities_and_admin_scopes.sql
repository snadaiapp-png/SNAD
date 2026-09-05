-- ============================================================
-- HRM-G0 / Master Task 6 / WS5 Task 1
-- V20260904_3__seed_hrm_v2_capabilities_and_admin_scopes.sql
-- ============================================================
-- Seeds EXACTLY the 19 canonical HRM.* capabilities (platform-level
-- catalog) and backfills tenant ADMIN grants:
--   * role_capabilities for existing tenants' ADMIN roles
--   * one TENANT-scope access_scope_grants row per ADMIN capability grant
-- HR_MANAGER is NEVER referenced — its canonical matrix remains exactly
-- HR.EMPLOYEE.READ / HR.EMPLOYEE.WRITE / HR.EMPLOYEE.ARCHIVE.
-- Runtime tenant provisioning (RegistrationProvisioner) already grants
-- every ACTIVE capability to new tenants' ADMIN roles, so future tenants
-- inherit these capabilities without further migration.
-- Next collision-free version: 20260904.3 (latest was 20260904.2).
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), c.code, c.name, c.description, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('HRM.EMPLOYEE.VIEW',                'HRM Employee View',                'View canonical employee records'),
    ('HRM.EMPLOYEE.CREATE',              'HRM Employee Create',              'Create canonical employees'),
    ('HRM.EMPLOYEE.UPDATE',              'HRM Employee Update',              'Update canonical employees'),
    ('HRM.EMPLOYEE.TERMINATE',           'HRM Employee Terminate',           'Terminate canonical employment'),
    ('HRM.ORG_STRUCTURE.VIEW',           'HRM Org Structure View',           'View org structure'),
    ('HRM.ORG_STRUCTURE.MANAGE',         'HRM Org Structure Manage',         'Manage org structure'),
    ('HRM.ASSIGNMENT.VIEW',              'HRM Assignment View',              'View assignments'),
    ('HRM.ASSIGNMENT.MANAGE',            'HRM Assignment Manage',            'Manage assignments'),
    ('HRM.CONTRACT.VIEW',                'HRM Contract View',                'View employment contracts'),
    ('HRM.CONTRACT.MANAGE',              'HRM Contract Manage',              'Manage employment contracts'),
    ('HRM.COMPENSATION.VIEW',            'HRM Compensation View',            'View compensation packages (sensitive-read audited)'),
    ('HRM.COMPENSATION.MANAGE',          'HRM Compensation Manage',          'Manage compensation packages'),
    ('HRM.PII.VIEW',                     'HRM PII View',                     'View restricted PII (sensitive-read audited)'),
    ('HRM.PII.MANAGE',                   'HRM PII Manage',                   'Manage restricted PII data'),
    ('HRM.USER_LINK.MANAGE',             'HRM User Link Manage',             'Manage person-to-user links'),
    ('HRM.AUDIT.VIEW',                   'HRM Audit View',                   'View HR audit evidence'),
    ('HRM.COMPLIANCE_OVERRIDE.REQUEST',  'HRM Compliance Override Request',  'Request a governed compliance override'),
    ('HRM.COMPLIANCE_OVERRIDE.APPROVE',  'HRM Compliance Override Approve',  'Approve/reject governed compliance overrides (four-eyes)'),
    ('HRM.ADMIN',                        'HRM Admin',                        'HRM module administration')
) AS c(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities e WHERE e.code = c.code);

-- Backfill ADMIN grants for tenants that already exist at migration time.
-- access_scope_grants is FORCE RLS (tenant policy on app.tenant_id), so the
-- per-tenant inserts must run WITH that tenant's GUC set — a plain migration
-- session has no tenant context and MUST NOT bypass RLS.
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
            FOR cap IN SELECT id, code FROM access_capabilities
                       WHERE code LIKE 'HRM.%' AND status = 'ACTIVE'
            LOOP
                -- role capability grant
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT gen_random_uuid(), r.tenant_id, r.id, cap.id, NOW()
                WHERE NOT EXISTS (SELECT 1 FROM role_capabilities rc
                                  WHERE rc.tenant_id = r.tenant_id AND rc.role_id = r.id
                                    AND rc.capability_id = cap.id);

                -- one effective TENANT-scope grant per ADMIN capability grant
                -- (no direct-user exceptions)
                INSERT INTO access_scope_grants (id, tenant_id, role_id, capability_id, scope_type,
                                                 is_direct_exception, reason, status, created_at)
                SELECT gen_random_uuid(), r.tenant_id, r.id, cap.id, 'TENANT',
                       FALSE, 'HRM-G0 WS5 Task 1 canonical ADMIN scope grant', 'ACTIVE', NOW()
                WHERE NOT EXISTS (SELECT 1 FROM access_scope_grants g
                                  WHERE g.tenant_id = r.tenant_id AND g.role_id = r.id
                                    AND g.capability_id = cap.id AND g.scope_type = 'TENANT'
                                    AND g.status = 'ACTIVE');
            END LOOP;
        END LOOP;
    END LOOP;

    PERFORM set_config('app.tenant_id', '', false);
END $$;
