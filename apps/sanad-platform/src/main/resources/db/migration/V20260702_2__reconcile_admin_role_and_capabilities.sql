-- ============================================================
-- SANAD Platform - Flyway migration V20260702_2
-- Reconcile ADMIN role and capability assignments after the
-- production-compatible Java V15 migration and CRM V20260702_1.
-- ============================================================
--
-- The production schema history records V15 as the JDBC migration
-- "seed rbac roles and capabilities". The superseded SQL V15 omitted
-- role_capabilities.tenant_id and was not equivalent to production.
--
-- This forward-only migration safely closes the bootstrap gap:
--   1. Creates an ACTIVE ADMIN role for every tenant when missing.
--   2. Assigns every ACTIVE capability to each ADMIN role.
--
-- Safety:
--   - INSERT only; no UPDATE or DELETE.
--   - Idempotent through WHERE NOT EXISTS.
--   - Preserves customized roles and grants.
--   - Uses the complete role_capabilities schema including tenant_id.
--   - Runs after V20260702_1__create_unified_crm_core.sql.
-- ============================================================

INSERT INTO roles (
    id,
    tenant_id,
    code,
    name,
    description,
    status,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    tenant.id,
    'ADMIN',
    'Administrator',
    'Tenant-wide administrative access',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM tenants tenant
WHERE NOT EXISTS (
    SELECT 1
    FROM roles role
    WHERE role.tenant_id = tenant.id
      AND role.code = 'ADMIN'
);

INSERT INTO role_capabilities (
    id,
    tenant_id,
    role_id,
    capability_id,
    created_at
)
SELECT
    gen_random_uuid(),
    role.tenant_id,
    role.id,
    capability.id,
    CURRENT_TIMESTAMP
FROM roles role
CROSS JOIN access_capabilities capability
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND capability.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM role_capabilities assignment
      WHERE assignment.tenant_id = role.tenant_id
        AND assignment.role_id = role.id
        AND assignment.capability_id = capability.id
  );
