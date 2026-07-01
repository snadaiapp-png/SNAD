-- ============================================================
-- SANAD Platform - Flyway migration V24
-- ------------------------------------------------------------
-- Stage 05 §22 — Seed audit and idempotency capabilities into
-- the access_capabilities catalog.
--
-- These capabilities are required by the new audit query API
-- (AUDIT.READ, AUDIT.INTEGRITY_VERIFY, AUDIT.EXPORT) and by
-- idempotency administration (IDEMPOTENCY.ADMIN).
--
-- Uses fixed UUIDs in the a0000008 range (audit) and a0000009
-- range (idempotency) to avoid collisions with V14 seeds.
--
-- Compatible with PostgreSQL 14+ and H2 (MODE=PostgreSQL).
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT uuid_val, code, name, description, status, created_at, updated_at
FROM (
    VALUES
    -- Audit capabilities
    (CAST('a0000008-0000-0000-0000-000000000001' AS uuid), 'AUDIT.READ',             'Read Audit Events',         'View audit events for the current tenant',                'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000008-0000-0000-0000-000000000002' AS uuid), 'AUDIT.INTEGRITY_VERIFY', 'Verify Audit Integrity',    'Recompute and verify the audit hash chain',               'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000008-0000-0000-0000-000000000003' AS uuid), 'AUDIT.EXPORT',           'Export Audit Events',       'Export audit events for compliance or legal hold',        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Idempotency administration
    (CAST('a0000009-0000-0000-0000-000000000001' AS uuid), 'IDEMPOTENCY.ADMIN',      'Administer Idempotency',    'View and manage idempotency records for the tenant',      'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS t(uuid_val, code, name, description, status, created_at, updated_at)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities WHERE code = t.code
);
