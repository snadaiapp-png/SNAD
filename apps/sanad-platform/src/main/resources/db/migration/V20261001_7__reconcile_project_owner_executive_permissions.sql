-- ============================================================================
-- V20261001_7 — Reconcile canonical project-owner executive permissions
--
-- Purpose:
--   * keep snad.ai.app@gmail.com as the canonical executive/platform owner;
--   * keep the canonical owner ACTIVE + platform_admin for bootstrap compatibility;
--   * ensure tenant-wide ACTIVE ADMIN and PLATFORM_OWNER assignments;
--   * reconcile every currently ACTIVE capability into both executive roles;
--   * reconcile tenant-wide ACTIVE scopes for every currently ACTIVE capability;
--   * preserve tenant isolation by operating only inside the canonical control tenant.
--
-- This migration is forward-only, idempotent, non-destructive to credentials,
-- and fail-closed on missing identity/roles/capabilities/scopes.
-- ============================================================================
DO $$
DECLARE
    control_tenant CONSTANT UUID := '00000000-0000-0000-0000-000000000001'::uuid;
    owner_id       CONSTANT UUID := '00000000-0000-0000-0000-000000000010'::uuid;
    owner_email    CONSTANT TEXT := 'snad.ai.app@gmail.com';
    role_rec RECORD;
    executive_role_count INTEGER;
    missing_capability_count INTEGER;
    missing_scope_count INTEGER;
    assignment_count INTEGER;
BEGIN
    -- access_scope_grants and platform IAM metadata are tenant/RLS scoped.
    PERFORM set_config('app.tenant_id', control_tenant::text, TRUE);

    -- Canonical identity must already exist exactly where governance defines it.
    IF NOT EXISTS (
        SELECT 1
          FROM users
         WHERE id = owner_id
           AND tenant_id = control_tenant
           AND lower(email) = lower(owner_email)
           AND status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION
            'EXECUTIVE OWNER RECONCILIATION FAILED: canonical ACTIVE owner is missing';
    END IF;

    -- Credentials are intentionally untouched.
    UPDATE users
       SET status = 'ACTIVE',
           platform_admin = TRUE,
           updated_at = NOW()
     WHERE id = owner_id
       AND tenant_id = control_tenant
       AND lower(email) = lower(owner_email);

    SELECT COUNT(*)
      INTO executive_role_count
      FROM roles
     WHERE tenant_id = control_tenant
       AND code IN ('ADMIN', 'PLATFORM_OWNER')
       AND status = 'ACTIVE';

    IF executive_role_count <> 2 THEN
        RAISE EXCEPTION
            'EXECUTIVE OWNER RECONCILIATION FAILED: expected ACTIVE ADMIN and PLATFORM_OWNER roles, found %',
            executive_role_count;
    END IF;

    FOR role_rec IN
        SELECT id, code
          FROM roles
         WHERE tenant_id = control_tenant
           AND code IN ('ADMIN', 'PLATFORM_OWNER')
           AND status = 'ACTIVE'
         ORDER BY code
    LOOP
        -- Keep an existing tenant-wide assignment active when present.
        UPDATE user_role_assignments
           SET status = 'ACTIVE',
               updated_at = NOW()
         WHERE tenant_id = control_tenant
           AND user_id = owner_id
           AND role_id = role_rec.id
           AND organization_id IS NULL;

        -- Create the tenant-wide assignment if it does not yet exist.
        INSERT INTO user_role_assignments (
            id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at
        )
        SELECT gen_random_uuid(), control_tenant, owner_id, role_rec.id,
               NULL, 'ACTIVE', NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1
              FROM user_role_assignments ura
             WHERE ura.tenant_id = control_tenant
               AND ura.user_id = owner_id
               AND ura.role_id = role_rec.id
               AND ura.organization_id IS NULL
               AND ura.status = 'ACTIVE'
        );

        -- Reconcile every capability that is ACTIVE at migration time.
        INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
        SELECT gen_random_uuid(), control_tenant, role_rec.id, c.id, NOW()
          FROM access_capabilities c
         WHERE c.status = 'ACTIVE'
           AND NOT EXISTS (
               SELECT 1
                 FROM role_capabilities rc
                WHERE rc.tenant_id = control_tenant
                  AND rc.role_id = role_rec.id
                  AND rc.capability_id = c.id
           );

        -- Preserve scoped authorization: the executive roles receive tenant-wide
        -- grants only in the canonical control tenant, not a cross-tenant shortcut.
        INSERT INTO access_scope_grants (
            id, tenant_id, role_id, user_id, capability_id, scope_type,
            organization_id, org_unit_id, legal_entity_id,
            is_direct_exception, reason, granted_by,
            effective_from, effective_to, status, created_at
        )
        SELECT gen_random_uuid(), control_tenant, role_rec.id, NULL, c.id, 'TENANT',
               NULL, NULL, NULL,
               FALSE, 'Canonical project owner executive tenant-wide scope', NULL,
               NOW(), NULL, 'ACTIVE', NOW()
          FROM access_capabilities c
         WHERE c.status = 'ACTIVE'
           AND NOT EXISTS (
               SELECT 1
                 FROM access_scope_grants g
                WHERE g.tenant_id = control_tenant
                  AND g.role_id = role_rec.id
                  AND g.user_id IS NULL
                  AND g.capability_id = c.id
                  AND g.scope_type = 'TENANT'
                  AND g.status = 'ACTIVE'
           );

        -- Fail closed: the canonical owner must have this tenant-wide role.
        SELECT COUNT(*)
          INTO assignment_count
          FROM user_role_assignments ura
         WHERE ura.tenant_id = control_tenant
           AND ura.user_id = owner_id
           AND ura.role_id = role_rec.id
           AND ura.organization_id IS NULL
           AND ura.status = 'ACTIVE';

        IF assignment_count = 0 THEN
            RAISE EXCEPTION
                'EXECUTIVE OWNER RECONCILIATION FAILED: missing ACTIVE tenant-wide assignment for role %',
                role_rec.code;
        END IF;

        -- Fail closed: no ACTIVE capability may be missing from either role.
        SELECT COUNT(*)
          INTO missing_capability_count
          FROM access_capabilities c
         WHERE c.status = 'ACTIVE'
           AND NOT EXISTS (
               SELECT 1
                 FROM role_capabilities rc
                WHERE rc.tenant_id = control_tenant
                  AND rc.role_id = role_rec.id
                  AND rc.capability_id = c.id
           );

        IF missing_capability_count <> 0 THEN
            RAISE EXCEPTION
                'EXECUTIVE OWNER RECONCILIATION FAILED: role % is missing % ACTIVE capabilities',
                role_rec.code, missing_capability_count;
        END IF;

        SELECT COUNT(*)
          INTO missing_scope_count
          FROM access_capabilities c
         WHERE c.status = 'ACTIVE'
           AND NOT EXISTS (
               SELECT 1
                 FROM access_scope_grants g
                WHERE g.tenant_id = control_tenant
                  AND g.role_id = role_rec.id
                  AND g.user_id IS NULL
                  AND g.capability_id = c.id
                  AND g.scope_type = 'TENANT'
                  AND g.status = 'ACTIVE'
           );

        IF missing_scope_count <> 0 THEN
            RAISE EXCEPTION
                'EXECUTIVE OWNER RECONCILIATION FAILED: role % is missing % ACTIVE tenant-wide scope grants',
                role_rec.code, missing_scope_count;
        END IF;
    END LOOP;

    -- Final identity invariant.
    IF NOT EXISTS (
        SELECT 1
          FROM users
         WHERE id = owner_id
           AND tenant_id = control_tenant
           AND lower(email) = lower(owner_email)
           AND status = 'ACTIVE'
           AND platform_admin = TRUE
    ) THEN
        RAISE EXCEPTION
            'EXECUTIVE OWNER RECONCILIATION FAILED: canonical owner identity invariant is missing';
    END IF;
END $$;
