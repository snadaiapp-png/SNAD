-- ============================================================
-- V20260902_7: Workflow Y2 break-glass override audit
--
-- Wave 3 — Task 20 (design decision AH3): break-glass emergency commands
-- append an OVERRIDE business-audit event. Extends the audit action check
-- forward-only; no historical migration is touched.
-- ============================================================

ALTER TABLE workflow_transition_audit DROP CONSTRAINT IF EXISTS ck_wf_audit_action;
ALTER TABLE workflow_transition_audit ADD CONSTRAINT ck_wf_audit_action
    CHECK (action IN (
        'CREATE', 'UPDATE', 'ACTIVATE', 'DEACTIVATE', 'START', 'PAUSE', 'RESUME',
        'CANCEL', 'ADVANCE', 'APPROVE', 'REJECT', 'EXPIRE', 'FAIL', 'COMPLETE',
        'ARCHIVE', 'ASSIGN', 'OVERRIDE'
    ));
