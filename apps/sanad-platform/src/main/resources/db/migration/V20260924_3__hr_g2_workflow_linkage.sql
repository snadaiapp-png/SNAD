-- ============================================================
-- V20260924_3 — HRM-G2: add workflow linkage columns to leave_requests
-- ============================================================
-- Links leave requests to the canonical SANAD Workflow Engine.
-- The workflow_instance_id references workflow_instances.id.
-- RLS is already enabled on hr_leave_requests (V20260923_2).
-- ============================================================

ALTER TABLE hr_leave_requests
    ADD COLUMN IF NOT EXISTS workflow_instance_id UUID,
    ADD COLUMN IF NOT EXISTS current_workflow_step VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_hr_leave_requests_workflow
    ON hr_leave_requests (tenant_id, workflow_instance_id)
    WHERE workflow_instance_id IS NOT NULL;
