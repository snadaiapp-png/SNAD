-- ============================================================
-- V20260820_5: RBAC exact-matrix reconciliation + durable provenance
-- ============================================================
-- PROBLEM (from v10 brief):
--
-- V20260820_3 only ADDS corrected grants to system-managed roles.
-- It does NOT REMOVE obsolete grants that V20260820_2 incorrectly
-- added (because V20260820_2 used invented capability codes that
-- happened to exist in access_capabilities — e.g. HR.DEPARTMENT.*,
-- HR.POSITION.*).
--
-- Fresh production evidence shows:
--   HR_MANAGER currently retains:
--     HR.DEPARTMENT.READ, HR.DEPARTMENT.WRITE,
--     HR.POSITION.READ, HR.POSITION.WRITE
--   in addition to the correct:
--     HR.EMPLOYEE.READ, HR.EMPLOYEE.WRITE, HR.EMPLOYEE.ARCHIVE
--
-- The v10 brief also requires DURABLE PROVENANCE for system-managed
-- roles — the previous UPDATE roles SET is_system_managed=TRUE WHERE
-- code IN (...) marks ANY pre-existing tenant role with matching
-- code as system-managed, which silently takes over customer roles.
--
-- SOLUTION:
--   1. Add durable provenance columns to roles:
--        role_origin       TEXT  (NULL=customer-created,
--                                'SNAD_TEMPLATE'=provisioned from a
--                                canonical SNAD role template)
--        template_key      TEXT  (the canonical code — e.g. 'HR_MANAGER')
--        template_version  TEXT  (the migration version that provisioned
--                                this role — e.g. 'V20260820_2')
--   2. Mark as system-managed ONLY roles whose (template_key, tenant_id)
--      was provisioned by this migration family — by setting
--      role_origin='SNAD_TEMPLATE' AND template_key=<code> only when
--      is_system_managed is already TRUE AND code matches. This does
--      NOT take over customer roles — it only records provenance for
--      roles already marked system-managed by V20260820_3.
--   3. Remove obsolete grants from system-managed roles. The
--      obsolete grants are:
--        HR_MANAGER   → HR.DEPARTMENT.READ, HR.DEPARTMENT.WRITE,
--                       HR.POSITION.READ, HR.POSITION.WRITE
--      (only removed from roles with is_system_managed=TRUE AND
--      role_origin='SNAD_TEMPLATE' — never from customer roles).
--
-- This migration is non-destructive to customer-managed roles:
-- the DELETE on role_capabilities is scoped to system-managed
-- SNAD-template roles with the matching template_key only.
-- ============================================================

-- ============================================================
-- 1. Add durable provenance columns to roles
-- ============================================================
ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS role_origin TEXT;
ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS template_key TEXT;
ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS template_version TEXT;

COMMENT ON COLUMN roles.role_origin IS
    'Provenance marker. NULL=customer-created role. ''SNAD_TEMPLATE''=provisioned from a canonical SNAD role-template migration. Only SNAD_TEMPLATE roles are subject to strict exact-matrix reconciliation.';
COMMENT ON COLUMN roles.template_key IS
    'For SNAD_TEMPLATE roles: the canonical role code (e.g. ''HR_MANAGER''). NULL for customer roles.';
COMMENT ON COLUMN roles.template_version IS
    'For SNAD_TEMPLATE roles: the migration version that first provisioned this role (e.g. ''V20260820_2''). NULL for customer roles.';

-- ============================================================
-- 2. Set durable provenance for system-managed roles.
--    Only roles already marked is_system_managed=TRUE (by V20260820_3)
--    AND whose code matches one of the 9 canonical codes are stamped.
--    This ensures customer roles are NOT silently taken over.
-- ============================================================
UPDATE roles AS r
SET role_origin = 'SNAD_TEMPLATE',
    template_key = r.code,
    template_version = 'V20260820_2',
    updated_at = CURRENT_TIMESTAMP
WHERE r.is_system_managed = TRUE
  AND r.code IN (
    'CRM_SALES', 'HR_MANAGER',
    'ERP_PURCHASER', 'ERP_APPROVER',
    'FINANCE_USER', 'FINANCE_APPROVER',
    'STORE_MANAGER', 'WORKFLOW_APPROVER',
    'EXECUTIVE_VIEWER'
  )
  AND r.role_origin IS NULL;

-- ============================================================
-- 3. Remove obsolete grants from system-managed SNAD_TEMPLATE roles.
--    These grants were incorrectly added by V20260820_2 because it
--    invented capability codes (HR.DEPARTMENT.*, HR.POSITION.*) that
--    happened to exist in access_capabilities but are NOT part of the
--    canonical HR_MANAGER contract (which is HR.EMPLOYEE.* only).
--
--    The DELETE is scoped to roles with:
--      is_system_managed = TRUE
--      role_origin = 'SNAD_TEMPLATE'
--      template_key = 'HR_MANAGER'
--    so customer-managed roles with the same code (if any) are
--    NOT touched.
-- ============================================================
DELETE FROM role_capabilities rc
USING roles r
WHERE rc.tenant_id = r.tenant_id
  AND rc.role_id = r.id
  AND r.is_system_managed = TRUE
  AND r.role_origin = 'SNAD_TEMPLATE'
  AND r.template_key = 'HR_MANAGER'
  AND rc.capability_id IN (
      SELECT ac.id FROM access_capabilities ac
      WHERE ac.code IN (
          'HR.DEPARTMENT.READ', 'HR.DEPARTMENT.WRITE',
          'HR.POSITION.READ', 'HR.POSITION.WRITE'
      )
  );

-- ============================================================
-- 4. Validate the corrected HR_MANAGER matrix.
--    After this migration, every system-managed HR_MANAGER role MUST
--    have EXACTLY the capabilities:
--      HR.EMPLOYEE.READ, HR.EMPLOYEE.WRITE, HR.EMPLOYEE.ARCHIVE
--    and NO others. Raise an EXCEPTION if not.
-- ============================================================
DO $$
DECLARE
    bad_role_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO bad_role_count
    FROM roles r
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE r.is_system_managed = TRUE
      AND r.role_origin = 'SNAD_TEMPLATE'
      AND r.template_key = 'HR_MANAGER'
      AND (ac.code IS NULL
           OR ac.code NOT IN ('HR.EMPLOYEE.READ', 'HR.EMPLOYEE.WRITE', 'HR.EMPLOYEE.ARCHIVE'));
    IF bad_role_count > 0 THEN
        RAISE EXCEPTION
            'HR_MANAGER exact-matrix validation failed: % system-managed roles have unexpected capabilities. RBAC_EXACT_MATRIX=FAIL.',
            bad_role_count;
    END IF;
END $$;
