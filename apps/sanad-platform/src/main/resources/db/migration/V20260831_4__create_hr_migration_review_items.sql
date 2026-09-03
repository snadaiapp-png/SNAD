-- ============================================================
-- HRM-G0 / WS2 / Task 6 — Create hr_migration_review_items table
-- ============================================================
-- Stores machine-readable review items for unresolved legacy HR
-- backfill conditions. Each row represents one ambiguity that
-- prevented deterministic migration.
--
-- Contract (frozen by HrCanonicalBackfillIntegrationTest):
--   Columns queried by the test:
--     tenant_id        (RLS-scoped)
--     issue_code       (stable, e.g. DUPLICATE_USER_ID)
--     review_reason    (non-null, human-readable explanation)
--     legacy_entity_id (the unresolved legacy row reference)
--
-- RLS: ENABLE + FORCE + fail-closed (no IS NULL OR branch).
-- ============================================================

CREATE TABLE IF NOT EXISTS hr_migration_review_items (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL,
    legacy_entity_type  VARCHAR(40)    NOT NULL,
    legacy_entity_id    UUID           NOT NULL,
    issue_code          VARCHAR(60)    NOT NULL,
    severity            VARCHAR(20)    NOT NULL DEFAULT 'REVIEW',
    review_reason       TEXT           NOT NULL,
    candidate_ref       UUID,
    resolution_state    VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_migration_review_items PRIMARY KEY (id),
    CONSTRAINT fk_hr_migration_review_items_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_hr_migration_review_items_severity
        CHECK (severity IN ('REVIEW', 'BLOCKED', 'WARNING')),
    CONSTRAINT ck_hr_migration_review_items_resolution
        CHECK (resolution_state IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);

CREATE INDEX IF NOT EXISTS idx_hr_migration_review_items_tenant
    ON hr_migration_review_items (tenant_id, issue_code);

CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_migration_review_items_tenant_entity_issue
    ON hr_migration_review_items (tenant_id, legacy_entity_id, issue_code)
    WHERE resolution_state = 'OPEN';

-- RLS: fail-closed (same pattern as V20260831_3)

ALTER TABLE hr_migration_review_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_migration_review_items FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON hr_migration_review_items;

CREATE POLICY tenant_isolation
ON hr_migration_review_items
FOR ALL
USING (
    tenant_id::text = current_setting('app.tenant_id', true)
)
WITH CHECK (
    tenant_id::text = current_setting('app.tenant_id', true)
);
