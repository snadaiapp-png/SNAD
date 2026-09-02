-- ============================================================
-- V20260902_4: Workflow Y2 runtime context and engine generation
--
-- Wave 2 — Task 10 (design decisions Y2/Z3/AA3/S3):
--   * Persist the resolved engine generation on every instance so each
--     instance executes on exactly one engine (no dual execution).
--   * Runtime metadata for graph execution, typed context, triggers,
--     idempotent starts, and sub-workflows.
-- ============================================================

ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS engine_generation VARCHAR(10) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS definition_family_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS definition_version_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS parent_instance_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(30);
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS trigger_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(200);
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS causation_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS context_json JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS context_schema_version INTEGER NOT NULL DEFAULT 1;

-- Externally retryable starts stay idempotent per tenant/trigger/definition.
CREATE UNIQUE INDEX IF NOT EXISTS uq_wf_instances_idempotency
    ON workflow_instances (tenant_id, trigger_type, workflow_definition_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Branch tokens for controlled parallelism (design decision R3, Task 14).
CREATE TABLE IF NOT EXISTS workflow_branch_tokens (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL REFERENCES tenants(id),
    workflow_instance_id    UUID            NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    fork_step_instance_id   UUID            NOT NULL REFERENCES workflow_step_instances(id) ON DELETE CASCADE,
    branch_key              VARCHAR(100)    NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'RUNNING',
    join_step_id            UUID,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL,
    updated_at              TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_workflow_branch_tokens PRIMARY KEY (id),
    CONSTRAINT uk_wf_branch_instance_key UNIQUE (workflow_instance_id, fork_step_instance_id, branch_key),
    CONSTRAINT ck_wf_branch_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_wf_branch_join
    ON workflow_branch_tokens(tenant_id, join_step_id, status);

ALTER TABLE workflow_instances DROP CONSTRAINT IF EXISTS ck_wf_inst_engine_generation;
ALTER TABLE workflow_instances ADD CONSTRAINT ck_wf_inst_engine_generation
    CHECK (engine_generation IN ('LEGACY', 'Y2'));

ALTER TABLE workflow_instances ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_branch_tokens;
CREATE POLICY tenant_isolation ON workflow_branch_tokens
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
