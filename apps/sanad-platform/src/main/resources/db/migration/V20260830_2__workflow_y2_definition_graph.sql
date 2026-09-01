-- ============================================================
-- V20260830_2: Workflow Y2 definition family / publication metadata
--
-- Wave 0 / Wave 1 — Task 4:
--   * Add immutable publication metadata to workflow_definitions.
--   * Preserve every existing workflow_definitions.id as a concrete version
--     identity; existing rows are backfilled with definition_family_id = id
--     and engine_generation = LEGACY (design decision I3 / Z3).
--
-- Task 5 appends workflow_step_transitions and the Y2 step types to this
-- same migration so the definition-graph schema stays one version.
-- ============================================================

ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS definition_family_id UUID;
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS engine_generation VARCHAR(10) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS publication_state VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS published_by UUID;
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS validated_at TIMESTAMPTZ;
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS definition_checksum VARCHAR(128);
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS schema_version INTEGER NOT NULL DEFAULT 1;

-- Backfill: each existing definition row becomes the first member of its
-- own version family. Existing ids remain valid version identities.
UPDATE workflow_definitions SET definition_family_id = id WHERE definition_family_id IS NULL;
ALTER TABLE workflow_definitions ALTER COLUMN definition_family_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_wf_def_family_version
    ON workflow_definitions(tenant_id, definition_family_id, version DESC);

ALTER TABLE workflow_definitions DROP CONSTRAINT IF EXISTS ck_wf_def_engine_generation;
ALTER TABLE workflow_definitions ADD CONSTRAINT ck_wf_def_engine_generation
    CHECK (engine_generation IN ('LEGACY', 'Y2'));

ALTER TABLE workflow_definitions DROP CONSTRAINT IF EXISTS ck_wf_def_publication_state;
ALTER TABLE workflow_definitions ADD CONSTRAINT ck_wf_def_publication_state
    CHECK (publication_state IN ('DRAFT', 'PUBLISHED', 'RETIRED'));

-- ============================================================
-- Task 5: explicit graph transitions + Y2 step types
-- ============================================================

-- Runtime routing uses explicit transition rows; sequence_order remains
-- presentation/backward-compatibility metadata only (design decision H3/R3).
CREATE TABLE IF NOT EXISTS workflow_step_transitions (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL REFERENCES tenants(id),
    workflow_definition_id  UUID            NOT NULL REFERENCES workflow_definitions(id) ON DELETE CASCADE,
    from_step_id            UUID            NOT NULL REFERENCES workflow_steps(id) ON DELETE CASCADE,
    to_step_id              UUID            NOT NULL REFERENCES workflow_steps(id),
    transition_key          VARCHAR(100)    NOT NULL,
    outcome                 VARCHAR(50),
    condition_ast           JSONB,
    priority                INTEGER         NOT NULL DEFAULT 0,
    metadata                JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workflow_step_transitions PRIMARY KEY (id),
    CONSTRAINT uk_wf_transitions_def_key UNIQUE (workflow_definition_id, transition_key)
);

CREATE INDEX IF NOT EXISTS idx_wf_transitions_def_from
    ON workflow_step_transitions(workflow_definition_id, from_step_id, priority DESC);

-- Y2 step types join the legacy five; ACTION remains for legacy
-- compatibility (design decision H3).
ALTER TABLE workflow_steps DROP CONSTRAINT IF EXISTS ck_wf_step_type;
ALTER TABLE workflow_steps ADD CONSTRAINT ck_wf_step_type
    CHECK (step_type IN (
        'ACTION', 'APPROVAL', 'CONDITION', 'NOTIFICATION', 'END',
        'START', 'HUMAN_TASK', 'SYSTEM_ACTION',
        'PARALLEL_FORK', 'PARALLEL_JOIN', 'CALL_WORKFLOW'
    ));

-- Same strict tenant isolation policy as the other workflow tables.
ALTER TABLE workflow_step_transitions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_step_transitions;
CREATE POLICY tenant_isolation ON workflow_step_transitions
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
