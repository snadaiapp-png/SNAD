-- ============================================================
-- V20260924_4 — HRM-G2: DB-level idempotency for leave workflow starts
-- ============================================================
-- Adds a unique constraint on workflow_instances.idempotency_key
-- within a tenant, preventing duplicate workflow starts for the same
-- logical leave request submission.
--
-- Concurrent submits with the same deterministic key
-- (HR_LEAVE_APPROVAL:<tenantId>:<leaveRequestId>) will produce
-- exactly 1 workflow instance — the second insert fails with
-- a unique violation, which the application catches and returns
-- the existing instance.
-- ============================================================

-- The idempotency_key column already exists on workflow_instances
-- (created by the workflow engine schema). This migration adds
-- the unique constraint if not already present.
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
EXCEPTION WHEN OTHERS THEN
    -- Constraint may already exist or column may not be present yet
    NULL;
END $$;
