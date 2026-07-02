-- ============================================================
-- SANAD Platform - Flyway migration V20260702_1
-- ------------------------------------------------------------
-- Stage 05A.2.9.1 (Flyway V15 Reconciliation) — Reconcile ADMIN
-- role and capability assignments.
--
-- This migration replaces the removed SQL V15
-- (V15__seed_admin_role_and_capabilities.sql) which was a
-- non-equivalent re-write of the original Java V15
-- (V15__seed_admin_role_and_capabilities.sql).
--
-- The original Java V15 creates 5 predefined roles (SUPER_ADMIN,
-- ORG_ADMIN, MANAGER, MEMBER, VIEWER) and assigns all capabilities
-- to SUPER_ADMIN and ADMIN. However, on a fresh database the ADMIN
-- role does not exist at migration time — it is created later by
-- the CredentialBootstrapService at application startup. As a
-- result, Java V15's "assign all capabilities to ADMIN" step is
-- a no-op on fresh databases.
--
-- The removed SQL V15 attempted to fix this (DEFECT-014) by
-- creating the ADMIN role at migration time, but it had a bug:
-- the INSERT into role_capabilities omitted the NOT NULL
-- tenant_id column, which would fail on a fresh database.
--
-- This reconciler migration:
--   1. Creates the ADMIN role for every tenant (idempotent).
--   2. Assigns all ACTIVE capabilities to every ADMIN role
--      (idempotent, with correct tenant_id).
--
-- Safety properties:
--   - Forward-only (INSERT only, no UPDATE/DELETE).
--   - Idempotent (WHERE NOT EXISTS skips existing rows).
--   - Does NOT delete or modify existing roles or capabilities.
--   - Does NOT duplicate existing role_capability assignments.
--   - Uses correct schema: role_capabilities(id, tenant_id,
--     role_id, capability_id, created_at).
--   - Works on both PostgreSQL and H2 (MODE=PostgreSQL) because
--     gen_random_uuid() is available in both.
--
-- Runs AFTER Java V15 (version 15 < 20260702_1) so on fresh
-- databases the 5 predefined roles already exist; this migration
-- only adds the ADMIN role and its capability grants.
--
-- On production databases where Java V15 was already applied,
-- this migration is a safe superset no-op: ADMIN already exists
-- (from bootstrap) and may already have capabilities — all
-- INSERTs use WHERE NOT EXISTS.
-- ============================================================

-- Step 1: Ensure ADMIN role exists for every tenant
INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
SELECT
    gen_random_uuid(),
    t.id,
    'ADMIN',
    'Administrator',
    'Tenant-wide administrative access',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM roles r
    WHERE r.tenant_id = t.id AND r.code = 'ADMIN'
);

-- Step 2: Assign all ACTIVE capabilities to every ADMIN role
-- Uses correct schema: role_capabilities(id, tenant_id, role_id,
-- capability_id, created_at) — tenant_id is NOT NULL (V8).
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT
    gen_random_uuid(),
    r.tenant_id,
    r.id,
    ac.id,
    CURRENT_TIMESTAMP
FROM roles r
CROSS JOIN access_capabilities ac
WHERE r.code = 'ADMIN'
  AND ac.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = r.tenant_id
      AND rc.role_id = r.id
      AND rc.capability_id = ac.id
  );
