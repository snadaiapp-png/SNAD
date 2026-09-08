-- Workflow Y2 Wave 3 / Task 16
-- Add optimistic concurrency control to first-class workflow incidents.
-- Forward-only: historical Workflow Y2 migrations remain immutable.

ALTER TABLE workflow_incidents
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
