-- ============================================================================
-- V20260921_1 — Canonical project owner full-platform authority
--
-- Canonical owner:
--   tenant: 00000000-0000-0000-0000-000000000001 (SNAD Control Plane)
--   user:   00000000-0000-0000-0000-000000000010
--   email:  snad.ai.app@gmail.com
--
-- Goals:
--   1) keep exactly one canonical owner identity for the approved email;
--   2) preserve tenant isolation by archiving (not deleting) foreign duplicates;
--   3) guarantee the control-plane ADMIN role owns every ACTIVE capability;
--   4) guarantee TENANT scope for the ADMIN role wherever scoped authorization
--      is used (HRM/recruitment and future scoped modules);
--   5) preserve forward-only Flyway governance.
-- ============================================================================

DO $$
DECLARE
    cp_tenant CONSTANT UUID := '00000000-0000-0000-0000-000000000001'::uuid;
    owner_id CONSTANT UUID := '00000000-0000-0000-0000-000000000010'::uuid;
    owner_email CONSTANT TEXT := 'snad.ai.app@gmail.com';
    admin_role_id UUID;
    owner_count INTEGER;
BEGIN
    -- Canonical owner must already exist. Do not silently manufacture a second
    -- privileged identity if the deterministic seed has drifted.
    SELECT COUNT(*) INTO owner_count
    FROM users
    WHERE id = owner_id
      AND tenant_id = cp_tenant;

    IF owner_count <> 1 THEN
        RAISE EXCEPTION 'Deterministic SNAD project owner is missing or duplicated';
    END IF;

    UPDATE users
    SET email = owner_email,
        status = 'ACTIVE',
        platform_admin = TRUE,
        must_change_password = FALSE,
        updated_at = NOW()
    WHERE id = owner_id
      AND tenant_id = cp_tenant;

    -- Revoke security state for any foreign identity that incorrectly reused the
    -- project-owner email before changing its address. The row itself is kept
    -- for audit/history and is never deleted.
    UPDATE refresh_tokens rt
    SET status = 'REVOKED'
    WHERE rt.status = 'ACTIVE'
      AND EXISTS (
          SELECT 1
          FROM users u
          WHERE u.tenant_id = rt.tenant_id
            AND u.id = rt.user_id
            AND lower(u.email) = owner_email
            AND NOT (u.id = owner_id AND u.tenant_id = cp_tenant)
      );

    UPDATE password_reset_tokens prt
    SET status = 'REVOKED'
    WHERE prt.status = 'ACTIVE'
      AND EXISTS (
          SELECT 1
          FROM users u
          WHERE u.tenant_id = prt.tenant_id
            AND u.id = prt.user_id
            AND lower(u.email) = owner_email
            AND NOT (u.id = owner_id AND u.tenant_id = cp_tenant)
      );

    UPDATE user_role_assignments ura
    SET status = 'REVOKED',
        updated_at = NOW()
    WHERE ura.status = 'ACTIVE'
      AND EXISTS (
          SELECT 1
          FROM users u
          WHERE u.tenant_id = ura.tenant_id
            AND u.id = ura.user_id
            AND lower(u.email) = owner_email
            AND NOT (u.id = owner_id AND u.tenant_id = cp_tenant)
      );

    UPDATE users
    SET email = 'archived-owner-duplicate+'
                || replace(id::text, '-', '')
                || '@invalid.snad.local',
        status = 'ARCHIVED',
        platform_admin = FALSE,
        session_version = session_version + 1,
        updated_at = NOW()
    WHERE lower(email) = owner_email
      AND NOT (id = owner_id AND tenant_id = cp_tenant);

    -- Keep the canonical ADMIN role active. If the deterministic role id was
    -- replaced historically, select the current tenant ADMIN by code.
    UPDATE roles
    SET status = 'ACTIVE',
        updated_at = NOW()
    WHERE tenant_id = cp_tenant
      AND code = 'ADMIN';

    SELECT id INTO admin_role_id
    FROM roles
    WHERE tenant_id = cp_tenant
      AND code = 'ADMIN'
      AND status = 'ACTIVE'
    ORDER BY created_at, id
    LIMIT 1;

    IF admin_role_id IS NULL THEN
        RAISE EXCEPTION 'SNAD control-plane ADMIN role is missing';
    END IF;

    -- Normalize the canonical owner to one tenant-wide ACTIVE ADMIN grant.
    UPDATE user_role_assignments
    SET status = 'REVOKED',
        updated_at = NOW()
    WHERE tenant_id = cp_tenant
      AND user_id = owner_id
      AND role_id = admin_role_id
      AND status = 'ACTIVE';

    INSERT INTO user_role_assignments
        (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
    VALUES
        (gen_random_uuid(), cp_tenant, owner_id, admin_role_id, NULL, 'ACTIVE', NOW(), NOW());

    -- Project-owner invariant: the control-plane ADMIN role receives every
    -- ACTIVE capability, including capabilities introduced after earlier
    -- backfill migrations.
    INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
    SELECT gen_random_uuid(), cp_tenant, admin_role_id, c.id, NOW()
    FROM access_capabilities c
    WHERE c.status = 'ACTIVE'
      AND NOT EXISTS (
          SELECT 1
          FROM role_capabilities rc
          WHERE rc.tenant_id = cp_tenant
            AND rc.role_id = admin_role_id
            AND rc.capability_id = c.id
      );

    -- access_scope_grants is FORCE RLS. Establish the control-plane tenant GUC
    -- before creating role-based TENANT scopes. A scope row for every active
    -- capability is deliberate: modules that do not consume scoped auth ignore
    -- it; modules that do consume it fail closed unless this row exists.
    PERFORM set_config('app.tenant_id', cp_tenant::text, true);

    INSERT INTO access_scope_grants
        (id, tenant_id, role_id, user_id, capability_id, scope_type,
         organization_id, org_unit_id, legal_entity_id, is_direct_exception,
         reason, granted_by, effective_from, effective_to, status, created_at)
    SELECT
        gen_random_uuid(), cp_tenant, admin_role_id, NULL, c.id, 'TENANT',
        NULL, NULL, NULL, FALSE,
        'Canonical SNAD project owner full-platform authority',
        NULL, NOW(), NULL, 'ACTIVE', NOW()
    FROM access_capabilities c
    WHERE c.status = 'ACTIVE'
      AND NOT EXISTS (
          SELECT 1
          FROM access_scope_grants g
          WHERE g.tenant_id = cp_tenant
            AND g.role_id = admin_role_id
            AND g.capability_id = c.id
            AND g.scope_type = 'TENANT'
            AND g.status = 'ACTIVE'
      );

    -- Fail closed if any authority invariant is still incomplete.
    IF EXISTS (
        SELECT 1
        FROM access_capabilities c
        WHERE c.status = 'ACTIVE'
          AND NOT EXISTS (
              SELECT 1
              FROM role_capabilities rc
              WHERE rc.tenant_id = cp_tenant
                AND rc.role_id = admin_role_id
                AND rc.capability_id = c.id
          )
    ) THEN
        RAISE EXCEPTION 'Project-owner ADMIN is missing one or more active capabilities';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM access_capabilities c
        WHERE c.status = 'ACTIVE'
          AND NOT EXISTS (
              SELECT 1
              FROM access_scope_grants g
              WHERE g.tenant_id = cp_tenant
                AND g.role_id = admin_role_id
                AND g.capability_id = c.id
                AND g.scope_type = 'TENANT'
                AND g.status = 'ACTIVE'
          )
    ) THEN
        RAISE EXCEPTION 'Project-owner ADMIN is missing one or more tenant scope grants';
    END IF;

    SELECT COUNT(*) INTO owner_count
    FROM users
    WHERE lower(email) = owner_email
      AND status = 'ACTIVE';

    IF owner_count <> 1 THEN
        RAISE EXCEPTION 'Canonical project-owner email must resolve to exactly one active user';
    END IF;
END $$;
