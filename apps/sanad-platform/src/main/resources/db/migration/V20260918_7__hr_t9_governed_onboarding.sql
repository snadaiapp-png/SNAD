-- ============================================================================
-- V20260918_1 — HRM-G1 T9 governed onboarding execution
-- Additive, forward-only: immutable checklist snapshots, optional Y2 link
-- marker, Y2 apply idempotency ledger, and the canonical entity_id outbox
-- envelope field required by the G1 contract.
-- ============================================================================

ALTER TABLE hr_onboarding_plans
    ADD COLUMN IF NOT EXISTS workflow_linked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE hr_onboarding_checklists
    ADD COLUMN IF NOT EXISTS definition_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE hr_domain_event_outbox
    ADD COLUMN IF NOT EXISTS entity_id UUID;

CREATE INDEX IF NOT EXISTS idx_hr_domain_event_outbox_entity
    ON hr_domain_event_outbox (tenant_id, entity_id, event_type);

CREATE TABLE IF NOT EXISTS hr_onboarding_workflow_transitions (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    task_id         UUID        NOT NULL REFERENCES hr_onboarding_tasks(id),
    transition_seq  BIGINT      NOT NULL,
    outcome         VARCHAR(16) NOT NULL,
    reason_code     VARCHAR(80),
    actor_user_id   UUID,
    correlation_id  UUID,
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_onboarding_workflow_transitions PRIMARY KEY (id),
    CONSTRAINT uq_hr_onboarding_workflow_transition
        UNIQUE (tenant_id, task_id, transition_seq),
    CONSTRAINT ck_hr_onboarding_workflow_outcome
        CHECK (outcome IN ('DONE','WAIVED','CANCELLED')),
    CONSTRAINT ck_hr_onboarding_workflow_seq CHECK (transition_seq >= 0)
);

CREATE INDEX IF NOT EXISTS idx_hr_onboarding_workflow_task
    ON hr_onboarding_workflow_transitions (tenant_id, task_id, applied_at DESC);

ALTER TABLE hr_onboarding_workflow_transitions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_onboarding_workflow_transitions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_onboarding_workflow_transitions;
CREATE POLICY tenant_isolation ON hr_onboarding_workflow_transitions FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));
