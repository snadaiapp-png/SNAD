-- ============================================================
-- SNAD Platform — CRM Collaboration & Event Foundation (Task 1)
-- ------------------------------------------------------------
-- Forward-only migration that introduces the schema backbone for
-- collaboration (entity participants) and durable CRM events:
--
--   1. crm_entity_participants — many-to-many relation between
--      CRM entities (CONTACT / TASK / CASE) and platform users,
--      role-scoped to COLLABORATOR / WATCHER. History is preserved
--      by appending new rows on removal; the unique partial index
--      keeps only one ACTIVE relation per (tenant, entity, user, role).
--
--   2. crm_timeline_events — additive structured-event columns
--      (summary_key, metadata_json, correlation_id, causation_id,
--      schema_version) so the legacy record method stays byte-for-byte
--      backward compatible while the structured path enables durable
--      outbox correlation.
--
--   3. crm_event_outbox — durable transactional outbox for CRM
--      domain events. Status machine PENDING -> PROCESSING -> PUBLISHED
--      (or FAILED with retry). Tenant isolation is enforced by RLS
--      in V20260822_2.
--
-- All new tables reuse the platform RLS pattern (app.tenant_id GUC)
-- and follow the tenant-scoped composite-unique convention
-- (tenant_id, id) introduced by V20260702_1.
-- ============================================================

-- ------------------------------------------------------------
-- 1. crm_entity_participants
-- ------------------------------------------------------------
CREATE TABLE crm_entity_participants (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    entity_type VARCHAR(16) NOT NULL,
    entity_id UUID NOT NULL,

    user_id UUID NOT NULL,
    role VARCHAR(24) NOT NULL,

    added_at TIMESTAMP WITH TIME ZONE NOT NULL,
    added_by UUID NOT NULL,

    removed_at TIMESTAMP WITH TIME ZONE,
    removed_by UUID,

    CONSTRAINT pk_crm_entity_participants PRIMARY KEY (id),
    CONSTRAINT uk_crm_entity_participants_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_entity_participants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),

    CONSTRAINT ck_crm_entity_participants_entity_type
        CHECK (entity_type IN ('CONTACT', 'TASK', 'CASE')),
    CONSTRAINT ck_crm_entity_participants_role
        CHECK (role IN ('COLLABORATOR', 'WATCHER')),
    CONSTRAINT ck_crm_entity_participants_removal_pair
        CHECK ((removed_at IS NULL AND removed_by IS NULL)
            OR (removed_at IS NOT NULL AND removed_by IS NOT NULL)),
    CONSTRAINT ck_crm_entity_participants_version
        CHECK (version >= 0)
);

-- Only one ACTIVE relation per (tenant, entity, user, role). History
-- rows with removed_at IS NULL are excluded by the partial predicate.
CREATE UNIQUE INDEX uk_crm_entity_participants_active
    ON crm_entity_participants (tenant_id, entity_type, entity_id, user_id, role)
    WHERE removed_at IS NULL;

CREATE INDEX idx_crm_entity_participants_entity
    ON crm_entity_participants (tenant_id, entity_type, entity_id, removed_at, added_at DESC);

CREATE INDEX idx_crm_entity_participants_user
    ON crm_entity_participants (tenant_id, user_id, removed_at, added_at DESC);

CREATE INDEX idx_crm_entity_participants_role
    ON crm_entity_participants (tenant_id, entity_type, entity_id, role, removed_at);

-- ------------------------------------------------------------
-- 2. crm_timeline_events — additive structured-event columns
-- ------------------------------------------------------------
ALTER TABLE crm_timeline_events
    ADD COLUMN summary_key VARCHAR(160),
    ADD COLUMN metadata_json TEXT,
    ADD COLUMN correlation_id VARCHAR(160),
    ADD COLUMN causation_id VARCHAR(160),
    ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1;

-- Partial index that only covers structured rows (those that carry a
-- correlation_id). Legacy rows remain indexed by idx_crm_timeline_subject.
CREATE INDEX idx_crm_timeline_correlation
    ON crm_timeline_events (tenant_id, correlation_id, occurred_at DESC)
    WHERE correlation_id IS NOT NULL;

-- ------------------------------------------------------------
-- 3. crm_event_outbox — durable transactional outbox
-- ------------------------------------------------------------
CREATE TABLE crm_event_outbox (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    event_type VARCHAR(160) NOT NULL,
    summary_key VARCHAR(160),
    payload_json TEXT NOT NULL,
    metadata_json TEXT,

    correlation_id VARCHAR(160),
    causation_id VARCHAR(160),
    schema_version INTEGER NOT NULL DEFAULT 1,

    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,

    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_event_outbox PRIMARY KEY (id),
    CONSTRAINT uk_crm_event_outbox_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_event_outbox_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),

    CONSTRAINT ck_crm_event_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_crm_event_outbox_schema_version
        CHECK (schema_version > 0),
    CONSTRAINT ck_crm_event_outbox_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_crm_event_outbox_due
    ON crm_event_outbox (status, available_at, tenant_id)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX idx_crm_event_outbox_correlation
    ON crm_event_outbox (tenant_id, correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE INDEX idx_crm_event_outbox_event_type
    ON crm_event_outbox (tenant_id, event_type, status, created_at DESC);
