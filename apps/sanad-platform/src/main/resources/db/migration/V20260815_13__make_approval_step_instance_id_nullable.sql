-- ============================================================
-- V20260815_13: Make workflow_approval_requests.workflow_step_instance_id nullable
--
-- This enables approval requests to be created independent of a specific
-- workflow_step_instance row. This is required for the Senior Management
-- integration path where an approval is created at the decision submission
-- time, BEFORE any step_instance has been started for that workflow.
--
-- The FK constraint is preserved (when not NULL, must reference an existing row).
-- ============================================================

ALTER TABLE workflow_approval_requests
    ALTER COLUMN workflow_step_instance_id DROP NOT NULL;
