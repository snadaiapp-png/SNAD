-- ============================================================
-- V20260816_1: Register CONTRACT_MANAGEMENT module (readiness only)
--
-- Adds CONTRACT_MANAGEMENT to the global module catalog. This is
-- READINESS ONLY — no business tables, no APIs, no services.
--
-- The module is registered as PLANNED (status=ACTIVE in the catalog
-- means "recognized module code", NOT "operationally implemented").
-- Tenant-level enablement is controlled by plan_module_entitlements
-- and EntitlementResolver.isModuleEnabled(tenantId, moduleCode).
--
-- ERP and POS already exist in the registry (V20260814_1 seed).
-- This migration adds ONLY Contract Management.
--
-- H2 compatibility: pure idempotent INSERT, runs on PG and H2.
-- ============================================================

INSERT INTO modules (id, code, name, description, status, display_order, version, enabled, created_at, updated_at)
SELECT gen_random_uuid(), v.code, v.name, v.description, v.status, v.display_order, v.version, v.enabled, NOW(), NOW()
FROM (VALUES
    ('CONTRACT_MANAGEMENT', 'Contract Management', 'Contract lifecycle management — drafts, reviews, approvals, renewals, obligations (PLANNED — not yet implemented)', 'ACTIVE', 110, '0.1', false)
) AS v(code, name, description, status, display_order, version, enabled)
WHERE NOT EXISTS (SELECT 1 FROM modules m WHERE m.code = v.code);
