-- ============================================================
-- V20260815_7: Add SLA fields to executive_decisions + escalations
--
-- Adds:
--   - executive_decisions.submitted_at  (when decision was submitted for review)
--   - executive_decisions.approval_due_at (SLA deadline for approval)
--   - escalations.sla_breached_at (when SLA was first breached, NULL if not breached)
--
-- These fields support the SLA Monitoring Service which detects:
--   - Decisions pending beyond their approval SLA
--   - Escalations whose SLA deadline has passed
-- ============================================================

-- Add SLA fields to executive_decisions
ALTER TABLE executive_decisions
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS approval_due_at TIMESTAMP WITH TIME ZONE;

-- Add SLA breach tracking to escalations
ALTER TABLE escalations
    ADD COLUMN IF NOT EXISTS sla_breached_at TIMESTAMP WITH TIME ZONE;

-- Index for SLA queries: find decisions submitted but not yet approved, overdue
CREATE INDEX IF NOT EXISTS idx_decisions_sla_overdue
    ON executive_decisions(tenant_id, status, approval_due_at)
    WHERE approval_due_at IS NOT NULL AND status IN ('SUBMITTED', 'UNDER_REVIEW');

-- Index for escalation SLA queries: find active escalations with passed SLA deadline
CREATE INDEX IF NOT EXISTS idx_escalations_sla_breach
    ON escalations(tenant_id, status, sla_deadline)
    WHERE sla_deadline IS NOT NULL AND status = 'ACTIVE';
