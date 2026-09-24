-- ============================================================
-- V20260815_12: Add requested_by_user_id to workflow_approval_requests
--
-- This enables proper Segregation of Duties (SOD):
-- The user who creates an approval request cannot approve/reject it.
-- ============================================================

ALTER TABLE workflow_approval_requests
    ADD COLUMN IF NOT EXISTS requested_by_user_id UUID;

-- Add FK for requested_by_user_id (nullable for backward compatibility)
ALTER TABLE workflow_approval_requests
    DROP CONSTRAINT IF EXISTS fk_wf_approval_requested_by;
ALTER TABLE workflow_approval_requests
    ADD CONSTRAINT fk_wf_approval_requested_by
    FOREIGN KEY (tenant_id, requested_by_user_id) REFERENCES users(tenant_id, id)
    ON DELETE SET NULL;

-- Add index for querying approvals by requester
CREATE INDEX IF NOT EXISTS idx_wf_approval_requested_by
    ON workflow_approval_requests(tenant_id, requested_by_user_id)
    WHERE requested_by_user_id IS NOT NULL;
