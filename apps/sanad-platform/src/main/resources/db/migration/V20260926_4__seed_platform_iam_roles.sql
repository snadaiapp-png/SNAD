-- ============================================================
-- V20260926_4 — Platform IAM: seed system roles + metadata
-- ============================================================
-- Seeds 6 protected system roles in the canonical control-plane tenant
-- and creates platform_role_metadata entries for each.
--
-- Roles from the Platform IAM design spec:
--   PLATFORM_OWNER    — protected, owner_role
--   PLATFORM_ADMIN     — protected
--   SECURITY_ADMIN     — protected
--   BILLING_ADMIN      — protected
--   SUPPORT_OPERATOR   — not protected
--   READ_ONLY_AUDITOR  — not protected
--
-- This migration:
--   - Is deterministic (uses canonical control tenant UUID).
--   - Is idempotent (WHERE NOT EXISTS per role).
--   - Creates role + metadata atomically per role.
--   - Does NOT assign capabilities (that's V20260926_5).
--   - Does NOT create users or memberships.
-- ============================================================

DO $$
DECLARE
    control_tenant CONSTANT UUID := '00000000-0000-0000-0000-000000000001'::uuid;
    role_count INT;
BEGIN
    -- Set tenant context for RLS-protected tables (roles, platform_role_metadata)
    PERFORM set_config('app.tenant_id', control_tenant::text, TRUE);

    -- ── PLATFORM_OWNER ──────────────────────────────────────────────
    INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
    SELECT gen_random_uuid(), control_tenant, 'PLATFORM_OWNER',
           'Platform Owner', 'Canonical platform owner role (protected, sole owner)',
           'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM roles WHERE tenant_id = control_tenant AND code = 'PLATFORM_OWNER'
    );

    INSERT INTO platform_role_metadata (control_tenant_id, role_id, role_type, protected, owner_role)
    SELECT control_tenant, r.id, 'SYSTEM', TRUE, TRUE
    FROM roles r
    WHERE r.tenant_id = control_tenant
      AND r.code = 'PLATFORM_OWNER'
      AND NOT EXISTS (
          SELECT 1 FROM platform_role_metadata prm
          WHERE prm.control_tenant_id = control_tenant
            AND prm.role_id = r.id
      );

    -- ── PLATFORM_ADMIN ─────────────────────────────────────────────
    INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
    SELECT gen_random_uuid(), control_tenant, 'PLATFORM_ADMIN',
           'Platform Admin', 'Platform administrator (protected)',
           'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM roles WHERE tenant_id = control_tenant AND code = 'PLATFORM_ADMIN'
    );

    INSERT INTO platform_role_metadata (control_tenant_id, role_id, role_type, protected, owner_role)
    SELECT control_tenant, r.id, 'SYSTEM', TRUE, FALSE
    FROM roles r
    WHERE r.tenant_id = control_tenant
      AND r.code = 'PLATFORM_ADMIN'
      AND NOT EXISTS (
          SELECT 1 FROM platform_role_metadata prm
          WHERE prm.control_tenant_id = control_tenant
            AND prm.role_id = r.id
      );

    -- ── SECURITY_ADMIN ─────────────────────────────────────────────
    INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
    SELECT gen_random_uuid(), control_tenant, 'SECURITY_ADMIN',
           'Security Admin', 'Security administrator (protected)',
           'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM roles WHERE tenant_id = control_tenant AND code = 'SECURITY_ADMIN'
    );

    INSERT INTO platform_role_metadata (control_tenant_id, role_id, role_type, protected, owner_role)
    SELECT control_tenant, r.id, 'SYSTEM', TRUE, FALSE
    FROM roles r
    WHERE r.tenant_id = control_tenant
      AND r.code = 'SECURITY_ADMIN'
      AND NOT EXISTS (
          SELECT 1 FROM platform_role_metadata prm
          WHERE prm.control_tenant_id = control_tenant
            AND prm.role_id = r.id
      );

    -- ── BILLING_ADMIN ─────────────────────────────────────────────
    INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
    SELECT gen_random_uuid(), control_tenant, 'BILLING_ADMIN',
           'Billing Admin', 'Billing administrator (protected)',
           'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM roles WHERE tenant_id = control_tenant AND code = 'BILLING_ADMIN'
    );

    INSERT INTO platform_role_metadata (control_tenant_id, role_id, role_type, protected, owner_role)
    SELECT control_tenant, r.id, 'SYSTEM', TRUE, FALSE
    FROM roles r
    WHERE r.tenant_id = control_tenant
      AND r.code = 'BILLING_ADMIN'
      AND NOT EXISTS (
          SELECT 1 FROM platform_role_metadata prm
          WHERE prm.control_tenant_id = control_tenant
            AND prm.role_id = r.id
      );

    -- ── SUPPORT_OPERATOR ──────────────────────────────────────────
    INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
    SELECT gen_random_uuid(), control_tenant, 'SUPPORT_OPERATOR',
           'Support Operator', 'Support operator (not protected)',
           'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM roles WHERE tenant_id = control_tenant AND code = 'SUPPORT_OPERATOR'
    );

    INSERT INTO platform_role_metadata (control_tenant_id, role_id, role_type, protected, owner_role)
    SELECT control_tenant, r.id, 'SYSTEM', FALSE, FALSE
    FROM roles r
    WHERE r.tenant_id = control_tenant
      AND r.code = 'SUPPORT_OPERATOR'
      AND NOT EXISTS (
          SELECT 1 FROM platform_role_metadata prm
          WHERE prm.control_tenant_id = control_tenant
            AND prm.role_id = r.id
      );

    -- ── READ_ONLY_AUDITOR ─────────────────────────────────────────
    INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
    SELECT gen_random_uuid(), control_tenant, 'READ_ONLY_AUDITOR',
           'Read-Only Auditor', 'Read-only auditor (not protected)',
           'ACTIVE', NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM roles WHERE tenant_id = control_tenant AND code = 'READ_ONLY_AUDITOR'
    );

    INSERT INTO platform_role_metadata (control_tenant_id, role_id, role_type, protected, owner_role)
    SELECT control_tenant, r.id, 'SYSTEM', FALSE, FALSE
    FROM roles r
    WHERE r.tenant_id = control_tenant
      AND r.code = 'READ_ONLY_AUDITOR'
      AND NOT EXISTS (
          SELECT 1 FROM platform_role_metadata prm
          WHERE prm.control_tenant_id = control_tenant
            AND prm.role_id = r.id
      );

    -- ── Fail-closed verification ───────────────────────────────────
    SELECT COUNT(*) INTO role_count FROM roles
    WHERE tenant_id = control_tenant
      AND code IN ('PLATFORM_OWNER','PLATFORM_ADMIN','SECURITY_ADMIN',
                   'BILLING_ADMIN','SUPPORT_OPERATOR','READ_ONLY_AUDITOR')
      AND status = 'ACTIVE';
    IF role_count IS NULL OR role_count < 6 THEN
        RAISE EXCEPTION 'PLATFORM IAM ROLES INCOMPLETE: expected 6 system roles in control tenant, found %', role_count;
    END IF;
END $$;
