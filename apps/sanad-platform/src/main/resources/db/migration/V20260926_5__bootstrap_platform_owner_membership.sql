-- ============================================================
-- V20260926_5 — Platform IAM: bootstrap canonical Platform Owner
-- ============================================================
-- Bootstraps the canonical project owner into the Platform IAM model:
--   1. Creates exactly one ACTIVE platform_membership
--   2. Assigns PLATFORM_OWNER role via user_role_assignments
--   3. Grants all PLATFORM.* capabilities to PLATFORM_OWNER via access_scope_grants
--
-- This migration is:
--   - Deterministic (uses canonical control tenant + owner UUIDs)
--   - Idempotent (WHERE NOT EXISTS for each insert)
--   - Fail-closed (RAISE EXCEPTION if prerequisites missing)
--   - Non-destructive (does NOT mutate password_hash or credentials)
--   - Keeps platform_admin=true for legacy compatibility
--
-- Canonical identifiers (from V20260921_1 migration):
--   control_tenant = 00000000-0000-0000-0000-000000000001
--   owner_id       = 00000000-0000-0000-0000-000000000010
--   owner_email    = snad.ai.app@gmail.com
-- ============================================================

DO $$
DECLARE
    control_tenant CONSTANT UUID := '00000000-0000-0000-0000-000000000001'::uuid;
    owner_id       CONSTANT UUID := '00000000-0000-0000-0000-000000000010'::uuid;
    owner_email    CONSTANT TEXT := 'snad.ai.app@gmail.com';

    verified_owner_id UUID;
    owner_status VARCHAR;
    platform_owner_role_id UUID;
    existing_membership_count INT;
    existing_assignment_count INT;
    existing_grant_count INT;
BEGIN
    -- Set tenant context FIRST — platform_role_metadata has FORCE RLS.
    -- All subsequent queries on RLS-protected tables require this context.
    PERFORM set_config('app.tenant_id', control_tenant::text, TRUE);

    -- ── 1. Resolve canonical owner by deterministic UUID + email ─────
    -- Must match both the UUID AND the canonical email — fail closed otherwise.
    SELECT id INTO verified_owner_id
    FROM users
    WHERE id = owner_id
      AND tenant_id = control_tenant
      AND lower(email) = lower(owner_email)
      AND status = 'ACTIVE';

    IF verified_owner_id IS NULL THEN
        RAISE EXCEPTION
            'PLATFORM OWNER BOOTSTRAP FAILED: canonical owner not found (tenant=%, user=%, email=%)',
            control_tenant, owner_id, owner_email;
    END IF;

    -- Verify platform_admin is true for compatibility
    SELECT platform_admin FROM users WHERE id = owner_id AND tenant_id = control_tenant
    INTO owner_status;
    IF owner_status IS NULL OR owner_status::boolean = false THEN
        RAISE EXCEPTION
            'PLATFORM OWNER BOOTSTRAP FAILED: canonical owner does not have platform_admin=true';
    END IF;

    -- ── 2. Verify PLATFORM_OWNER role exists ──────────────────────────
    SELECT id INTO platform_owner_role_id
    FROM roles
    WHERE tenant_id = control_tenant
      AND code = 'PLATFORM_OWNER'
      AND status = 'ACTIVE';

    IF platform_owner_role_id IS NULL THEN
        RAISE EXCEPTION
            'PLATFORM OWNER BOOTSTRAP FAILED: PLATFORM_OWNER role not found in control tenant';
    END IF;

    -- Verify PLATFORM_OWNER has correct metadata
    PERFORM 1
    FROM platform_role_metadata
    WHERE control_tenant_id = control_tenant
      AND role_id = platform_owner_role_id
      AND role_type = 'SYSTEM'
      AND protected = TRUE
      AND owner_role = TRUE;
    IF NOT FOUND THEN
        RAISE EXCEPTION
            'PLATFORM OWNER BOOTSTRAP FAILED: PLATFORM_OWNER metadata incorrect (must be SYSTEM/protected/owner_role)';
    END IF;

    -- ── 3. Create ACTIVE platform_membership (exactly one) ──────────
    SELECT COUNT(*) INTO existing_membership_count
    FROM platform_memberships
    WHERE control_tenant_id = control_tenant
      AND user_id = verified_owner_id
      AND status = 'ACTIVE';

    IF existing_membership_count = 0 THEN
        INSERT INTO platform_memberships (
            control_tenant_id, user_id, status,
            activated_at, created_by, status_reason
        )
        VALUES (
            control_tenant, verified_owner_id, 'ACTIVE',
            NOW(), verified_owner_id, 'Canonical Platform Owner bootstrap'
        );
        RAISE NOTICE 'Created ACTIVE platform_membership for canonical owner';
    ELSIF existing_membership_count = 1 THEN
        RAISE NOTICE 'ACTIVE platform_membership already exists for canonical owner (idempotent)';
    ELSE
        RAISE EXCEPTION
            'PLATFORM OWNER BOOTSTRAP FAILED: multiple ACTIVE memberships found (%)',
            existing_membership_count;
    END IF;

    -- ── 6. Assign PLATFORM_OWNER role to canonical owner ─────────────
    SELECT COUNT(*) INTO existing_assignment_count
    FROM user_role_assignments
    WHERE tenant_id = control_tenant
      AND user_id = verified_owner_id
      AND role_id = platform_owner_role_id
      AND status = 'ACTIVE';

    IF existing_assignment_count = 0 THEN
        INSERT INTO user_role_assignments (
            id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at
        )
        VALUES (
            gen_random_uuid(), control_tenant, verified_owner_id,
            platform_owner_role_id, NULL, 'ACTIVE', NOW(), NOW()
        );
        RAISE NOTICE 'Assigned PLATFORM_OWNER role to canonical owner';
    ELSE
        RAISE NOTICE 'PLATFORM_OWNER assignment already exists (idempotent)';
    END IF;

    -- ── 7. Grant all PLATFORM.* capabilities to PLATFORM_OWNER ──────
    -- via access_scope_grants (TENANT scope, role-level)
    SELECT COUNT(*) INTO existing_grant_count
    FROM access_scope_grants
    WHERE tenant_id = control_tenant
      AND role_id = platform_owner_role_id
      AND user_id IS NULL
      AND scope_type = 'TENANT'
      AND status = 'ACTIVE'
      AND capability_id IN (
          SELECT id FROM access_capabilities
          WHERE code LIKE 'PLATFORM.%' AND status = 'ACTIVE'
      );

    IF existing_grant_count < 17 THEN
        -- Insert missing grants (idempotent per capability)
        INSERT INTO access_scope_grants (
            id, tenant_id, role_id, user_id, capability_id, scope_type,
            organization_id, org_unit_id, legal_entity_id,
            is_direct_exception, reason, granted_by,
            effective_from, effective_to, status, created_at
        )
        SELECT gen_random_uuid(), control_tenant, platform_owner_role_id, NULL,
               ac.id, 'TENANT',
               NULL, NULL, NULL,
               FALSE, 'Canonical Platform Owner tenant-wide PLATFORM.* scope', NULL,
               NOW(), NULL, 'ACTIVE', NOW()
        FROM access_capabilities ac
        WHERE ac.code LIKE 'PLATFORM.%'
          AND ac.status = 'ACTIVE'
          AND NOT EXISTS (
              SELECT 1 FROM access_scope_grants asg
              WHERE asg.tenant_id = control_tenant
                AND asg.role_id = platform_owner_role_id
                AND asg.user_id IS NULL
                AND asg.capability_id = ac.id
                AND asg.scope_type = 'TENANT'
                AND asg.status = 'ACTIVE'
          );
        RAISE NOTICE 'Granted missing PLATFORM.* capabilities to PLATFORM_OWNER';
    ELSE
        RAISE NOTICE 'All PLATFORM.* capabilities already granted to PLATFORM_OWNER (idempotent)';
    END IF;

    -- ── 8. Final fail-closed verification ────────────────────────────

    -- Exactly one ACTIVE membership
    SELECT COUNT(*) INTO existing_membership_count
    FROM platform_memberships
    WHERE control_tenant_id = control_tenant
      AND user_id = verified_owner_id
      AND status = 'ACTIVE';
    IF existing_membership_count != 1 THEN
        RAISE EXCEPTION
            'PLATFORM OWNER BOOTSTRAP VERIFICATION FAILED: expected exactly 1 ACTIVE membership, found %',
            existing_membership_count;
    END IF;

    -- At least one ACTIVE PLATFORM_OWNER assignment
    SELECT COUNT(*) INTO existing_assignment_count
    FROM user_role_assignments
    WHERE tenant_id = control_tenant
      AND user_id = verified_owner_id
      AND role_id = platform_owner_role_id
      AND status = 'ACTIVE';
    IF existing_assignment_count = 0 THEN
        RAISE EXCEPTION
            'PLATFORM OWNER BOOTSTRAP VERIFICATION FAILED: no ACTIVE PLATFORM_OWNER assignment';
    END IF;

    -- All 17 PLATFORM.* capabilities granted
    SELECT COUNT(DISTINCT ac.code) INTO existing_grant_count
    FROM access_capabilities ac
    JOIN access_scope_grants asg ON asg.capability_id = ac.id
    WHERE ac.code LIKE 'PLATFORM.%'
      AND ac.status = 'ACTIVE'
      AND asg.tenant_id = control_tenant
      AND asg.role_id = platform_owner_role_id
      AND asg.user_id IS NULL
      AND asg.scope_type = 'TENANT'
      AND asg.status = 'ACTIVE';
    IF existing_grant_count < 17 THEN
        RAISE EXCEPTION
            'PLATFORM OWNER BOOTSTRAP VERIFICATION FAILED: expected 17 PLATFORM.* grants, found %',
            existing_grant_count;
    END IF;

    -- No PLATFORM_OWNER outside control tenant
    PERFORM 1
    FROM user_role_assignments ura
    JOIN roles r ON r.id = ura.role_id AND r.tenant_id = ura.tenant_id
    WHERE r.code = 'PLATFORM_OWNER'
      AND ura.tenant_id != control_tenant;
    IF FOUND THEN
        RAISE EXCEPTION
            'PLATFORM OWNER BOOTSTRAP VERIFICATION FAILED: PLATFORM_OWNER assignment found outside control tenant';
    END IF;

    RAISE NOTICE 'Platform Owner bootstrap complete: membership=ACTIVE, role=PLATFORM_OWNER, capabilities=17';
END $$;
