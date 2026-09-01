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
