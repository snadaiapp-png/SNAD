-- ============================================================
-- V20260924_4 — HRM-G2: DB-level idempotency for leave workflow starts
-- ============================================================
-- Adds a unique constraint on workflow_instances.idempotency_key
-- within a tenant, preventing duplicate workflow starts for the same
-- logical leave request submission.
--
-- NO exception swallowing. If duplicate historical non-null
-- idempotency keys exist, Flyway will fail explicitly.
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_workflow_instances_tenant_idemkey'
    ) THEN
        ALTER TABLE workflow_instances
            ADD CONSTRAINT uq_workflow_instances_tenant_idemkey
            UNIQUE (tenant_id, idempotency_key);
    END IF;
END $$;
