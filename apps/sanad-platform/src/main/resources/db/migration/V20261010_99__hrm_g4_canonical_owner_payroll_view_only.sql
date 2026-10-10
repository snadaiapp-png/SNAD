-- HRM G4: narrowly authorize canonical platform owner for payroll VIEW.
-- No payroll mutation/export privileges, no other tenants, no role-wide grants.
-- Explicit DENY remains dominant at evaluation time.
DO $$
DECLARE
    v_tenant CONSTANT uuid := '00000000-0000-0000-0000-000000000001';
    v_user CONSTANT uuid := '00000000-0000-0000-0000-000000000010';
    v_cap uuid;
BEGIN
    PERFORM set_config('app.tenant_id', v_tenant::text, TRUE);

    IF (SELECT count(*) FROM users
        WHERE tenant_id = v_tenant AND id = v_user
          AND lower(email) = 'snad.ai.app@gmail.com' AND status = 'ACTIVE'
          AND platform_admin = TRUE) <> 1 THEN
        RAISE EXCEPTION 'HRM G4 payroll VIEW grant blocked: canonical owner identity mismatch';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM user_role_assignments ura
        JOIN roles r ON r.id = ura.role_id AND r.tenant_id = ura.tenant_id
        WHERE ura.tenant_id = v_tenant AND ura.user_id = v_user
          AND ura.status = 'ACTIVE' AND r.status = 'ACTIVE'
          AND r.code = 'PLATFORM_OWNER'
    ) THEN
        RAISE EXCEPTION 'HRM G4 payroll VIEW grant blocked: PLATFORM_OWNER role missing';
    END IF;

    INSERT INTO access_capabilities
        (id, code, name, description, status, created_at, updated_at)
    SELECT gen_random_uuid(), 'HRM.PAYROLL.VIEW', 'Payroll View',
           'View authorized payroll runs (read only)', 'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM access_capabilities WHERE code = 'HRM.PAYROLL.VIEW'
    );

    SELECT id INTO STRICT v_cap
    FROM access_capabilities
    WHERE code = 'HRM.PAYROLL.VIEW' AND status = 'ACTIVE';

    -- Never overwrite an explicit direct deny, including future temporary denies.
    IF EXISTS (
        SELECT 1 FROM user_permission_overrides
        WHERE tenant_id = v_tenant AND user_id = v_user AND capability_id = v_cap
          AND effect = 'DENY' AND (valid_until IS NULL OR valid_until > NOW())
          AND valid_from <= NOW()
    ) THEN
        RAISE EXCEPTION 'HRM G4 payroll VIEW grant blocked: active explicit DENY';
    END IF;

    INSERT INTO user_permission_overrides
        (id, tenant_id, user_id, capability_id, effect, scope_type, scope_reference,
         reason, valid_from, valid_until, created_by, created_at, updated_at, version)
    SELECT gen_random_uuid(), v_tenant, v_user, v_cap, 'ALLOW', 'TENANT_ALL', NULL,
           'Owner-authorized HRM G4 read-only payroll display for canonical platform owner',
           NOW(), NULL, v_user, NOW(), NOW(), 0
    WHERE NOT EXISTS (
        SELECT 1 FROM user_permission_overrides
        WHERE tenant_id = v_tenant AND user_id = v_user AND capability_id = v_cap
          AND effect = 'ALLOW' AND scope_type = 'TENANT_ALL'
          AND valid_until IS NULL
    );

    IF NOT EXISTS (
        SELECT 1 FROM user_permission_overrides
        WHERE tenant_id = v_tenant AND user_id = v_user AND capability_id = v_cap
          AND effect = 'ALLOW' AND scope_type = 'TENANT_ALL'
          AND valid_until IS NULL
    ) THEN
        RAISE EXCEPTION 'HRM G4 payroll VIEW grant postcondition failed';
    END IF;
END $$;
