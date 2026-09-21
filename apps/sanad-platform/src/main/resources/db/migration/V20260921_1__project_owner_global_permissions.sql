-- ============================================================================
-- V20260921_1 — Canonical project-owner permissions and control-plane identity
--
-- Guarantees:
--   * snad.ai.app@gmail.com is the single canonical project-owner login.
--   * the deterministic owner remains ACTIVE + platform_admin in the control-plane tenant.
--   * duplicate copies of the canonical email outside the control-plane tenant
--     are archived in-place (audit history preserved) and their sessions revoked.
--   * the control-plane ADMIN role is tenant-wide for the owner.
--   * every ACTIVE platform capability is granted to the control-plane ADMIN role.
--   * every ACTIVE capability receives an ACTIVE TENANT scope grant for that ADMIN
--     role so scoped HR authorization preserves the "owner has full access" invariant.
--
-- This migration is forward-only, idempotent and fail-closed.
-- ============================================================================
DO $$
DECLARE
    control_tenant CONSTANT UUID := '00000000-0000-0000-0000-000000000001'::uuid;
    owner_id       CONSTANT UUID := '00000000-0000-0000-0000-000000000010'::uuid;
    owner_email    CONSTANT TEXT := 'snad.ai.app@gmail.com';
    admin_role_id UUID;
    owner_count INTEGER;
    duplicate_count INTEGER;
    missing_capability_count INTEGER;
    missing_scope_count INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO owner_count
      FROM users
     WHERE id = owner_id
       AND tenant_id = control_tenant;

    IF owner_count <> 1 THEN
        RAISE EXCEPTION 'Canonical deterministic project owner is missing from the control-plane tenant';
    END IF;

    SELECT id
      INTO admin_role_id
      FROM roles
     WHERE tenant_id = control_tenant
       AND code = 'ADMIN'
       AND status = 'ACTIVE'
     ORDER BY id
     LIMIT 1;

    IF admin_role_id IS NULL THEN
        RAISE EXCEPTION 'ACTIVE ADMIN role is missing from the control-plane tenant';
    END IF;

    -- Canonical owner identity. Credentials are intentionally left unchanged.
    UPDATE users
       SET email = owner_email,
           status = 'ACTIVE',
           platform_admin = TRUE,
           must_change_password = FALSE,
           updated_at = NOW()
     WHERE id = owner_id
       AND tenant_id = control_tenant;

    -- Retain duplicate rows for audit/history, but they must no longer be usable
    -- as login identities for the canonical owner email.
    UPDATE refresh_tokens rt
       SET status = 'REVOKED'
     WHERE status = 'ACTIVE'
       AND EXISTS (
           SELECT 1
             FROM users u
            WHERE u.tenant_id = rt.tenant_id
              AND u.id = rt.user_id
              AND lower(u.email) = owner_email
              AND NOT (u.tenant_id = control_tenant AND u.id = owner_id)
       );

    UPDATE password_reset_tokens prt
       SET status = 'REVOKED'
     WHERE status = 'ACTIVE'
       AND EXISTS (
           SELECT 1
             FROM users u
            WHERE u.tenant_id = prt.tenant_id
              AND u.id = prt.user_id
              AND lower(u.email) = owner_email
              AND NOT (u.tenant_id = control_tenant AND u.id = owner_id)
       );

    UPDATE users
       SET email = 'archived-owner+' || replace(id::text, '-', '') || '@sanad.invalid',
           status = 'ARCHIVED',
           platform_admin = FALSE,
           password_hash = NULL,
           must_change_password = FALSE,
           session_version = session_version + 1,
           updated_at = NOW()
     WHERE lower(email) = owner_email
       AND NOT (tenant_id = control_tenant AND id = owner_id);

    -- Persist the identity invariant beyond this repair: the canonical owner
    -- email can only ever belong to the deterministic owner row.
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'ck_users_canonical_project_owner_identity'
           AND conrelid = 'users'::regclass
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT ck_users_canonical_project_owner_identity
            CHECK (
                lower(email) <> 'snad.ai.app@gmail.com'
                OR (
                    id = '00000000-0000-0000-0000-000000000010'::uuid
                    AND tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
                )
            );
    END IF;

    CREATE UNIQUE INDEX IF NOT EXISTS uq_users_canonical_project_owner_email
        ON users ((lower(email)))
        WHERE lower(email) = 'snad.ai.app@gmail.com';

    -- Make the deterministic owner a tenant-wide ADMIN. Existing organization-
    -- scoped ADMIN grants are retained for history but do not replace this grant.
    UPDATE user_role_assignments
       SET status = 'ACTIVE',
           updated_at = NOW()
     WHERE tenant_id = control_tenant
       AND user_id = owner_id
       AND role_id = admin_role_id
       AND organization_id IS NULL;

    INSERT INTO user_role_assignments (
        id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at
    )
    SELECT gen_random_uuid(), control_tenant, owner_id, admin_role_id,
           NULL, 'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1
          FROM user_role_assignments ura
         WHERE ura.tenant_id = control_tenant
           AND ura.user_id = owner_id
           AND ura.role_id = admin_role_id
           AND ura.organization_id IS NULL
           AND ura.status = 'ACTIVE'
    );

    -- Platform invariant: the project owner ADMIN gets every ACTIVE capability,
    -- including capabilities introduced after earlier ADMIN backfills.
    INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
    SELECT gen_random_uuid(), control_tenant, admin_role_id, c.id, NOW()
      FROM access_capabilities c
     WHERE c.status = 'ACTIVE'
       AND NOT EXISTS (
           SELECT 1
             FROM role_capabilities rc
            WHERE rc.tenant_id = control_tenant
              AND rc.role_id = admin_role_id
              AND rc.capability_id = c.id
       );

    -- access_scope_grants is FORCE RLS. Set only the control-plane tenant
    -- context while creating tenant-wide ADMIN scopes.
    PERFORM set_config('app.tenant_id', control_tenant::text, TRUE);

    INSERT INTO access_scope_grants (
        id, tenant_id, role_id, user_id, capability_id, scope_type,
        organization_id, org_unit_id, legal_entity_id,
        is_direct_exception, reason, granted_by,
        effective_from, effective_to, status, created_at
    )
    SELECT gen_random_uuid(), control_tenant, admin_role_id, NULL, c.id, 'TENANT',
           NULL, NULL, NULL,
           FALSE, 'Canonical project owner tenant-wide ADMIN scope', NULL,
           NOW(), NULL, 'ACTIVE', NOW()
      FROM access_capabilities c
     WHERE c.status = 'ACTIVE'
       AND NOT EXISTS (
           SELECT 1
             FROM access_scope_grants g
            WHERE g.tenant_id = control_tenant
              AND g.role_id = admin_role_id
              AND g.user_id IS NULL
              AND g.capability_id = c.id
              AND g.scope_type = 'TENANT'
              AND g.status = 'ACTIVE'
       );

    -- Final fail-closed verification.
    SELECT COUNT(*)
      INTO duplicate_count
      FROM users
     WHERE lower(email) = owner_email
       AND NOT (tenant_id = control_tenant AND id = owner_id);

    IF duplicate_count <> 0 THEN
        RAISE EXCEPTION 'Duplicate canonical project-owner login still exists';
    END IF;

    SELECT COUNT(*)
      INTO missing_capability_count
      FROM access_capabilities c
     WHERE c.status = 'ACTIVE'
       AND NOT EXISTS (
           SELECT 1
             FROM role_capabilities rc
            WHERE rc.tenant_id = control_tenant
              AND rc.role_id = admin_role_id
              AND rc.capability_id = c.id
       );

    IF missing_capability_count <> 0 THEN
        RAISE EXCEPTION 'Control-plane ADMIN is missing % ACTIVE capabilities', missing_capability_count;
    END IF;

    SELECT COUNT(*)
      INTO missing_scope_count
      FROM access_capabilities c
     WHERE c.status = 'ACTIVE'
       AND NOT EXISTS (
           SELECT 1
             FROM access_scope_grants g
            WHERE g.tenant_id = control_tenant
              AND g.role_id = admin_role_id
              AND g.capability_id = c.id
              AND g.scope_type = 'TENANT'
              AND g.status = 'ACTIVE'
       );

    IF missing_scope_count <> 0 THEN
        RAISE EXCEPTION 'Control-plane ADMIN is missing % tenant-wide scope grants', missing_scope_count;
    END IF;
END $$;
