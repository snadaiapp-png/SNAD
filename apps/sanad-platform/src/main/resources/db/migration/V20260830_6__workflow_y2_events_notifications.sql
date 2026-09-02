-- ============================================================
-- V20260830_6: Workflow Y2 reliable events and notification intents
--
-- Wave 2 — Task 15 (design decisions J3/X3/K3):
--   * Idempotent inbox: duplicate DOMAIN_EVENT delivery can never create a
--     duplicate instance (unique tenant/event/trigger/definition).
--   * Transactional outbox aligned with the platform CRM event-outbox
--     conventions (PENDING -> PROCESSING -> PUBLISHED / FAILED, claims,
--     bounded retries). At-least-once only — never exactly-once.
--   * Notification intents are recorded inside the committed transition and
--     delivered separately; provider failure never reverses workflow state.
-- ============================================================

CREATE TABLE IF NOT EXISTS workflow_event_inbox (
    id                      UUID        NOT NULL,
    tenant_id               UUID        NOT NULL REFERENCES tenants(id),
    event_id                UUID        NOT NULL,
    trigger_key             VARCHAR(200) NOT NULL,
    workflow_definition_id  UUID        NOT NULL REFERENCES workflow_definitions(id),
    workflow_instance_id    UUID        REFERENCES workflow_instances(id),
    received_at             TIMESTAMPTZ NOT NULL,
    processed_at            TIMESTAMPTZ,
    status                  VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    error_code              VARCHAR(100),
    CONSTRAINT pk_workflow_event_inbox PRIMARY KEY (id),
    CONSTRAINT uk_wf_inbox_event UNIQUE (tenant_id, event_id, trigger_key, workflow_definition_id),
    CONSTRAINT ck_wf_inbox_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS workflow_event_outbox (
    id              UUID        NOT NULL,
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    event_type      VARCHAR(160) NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID        NOT NULL,
    payload_json    TEXT        NOT NULL,
    correlation_id  VARCHAR(160),
    causation_id    VARCHAR(160),
    schema_version  INTEGER     NOT NULL DEFAULT 1,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count   INTEGER     NOT NULL DEFAULT 0,
    last_error      TEXT,
    available_at    TIMESTAMPTZ NOT NULL,
    claimed_at      TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_workflow_event_outbox PRIMARY KEY (id),
    CONSTRAINT ck_wf_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_wf_outbox_dispatch
    ON workflow_event_outbox(status, available_at);

CREATE TABLE IF NOT EXISTS workflow_notification_intents (
    id              UUID        NOT NULL,
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    event_type      VARCHAR(60) NOT NULL,
    workflow_instance_id UUID   REFERENCES workflow_instances(id) ON DELETE CASCADE,
    work_item_id    UUID        REFERENCES workflow_work_items(id) ON DELETE CASCADE,
    recipient_user_id UUID,
    channel         VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    deduplication_key VARCHAR(200),
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count   INTEGER     NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_workflow_notification_intents PRIMARY KEY (id),
    CONSTRAINT ck_wf_notification_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'WEBHOOK')),
    CONSTRAINT ck_wf_notification_delivery CHECK (delivery_status IN (
        'PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_wf_notifications_recipient
    ON workflow_notification_intents(tenant_id, recipient_user_id, delivery_status);

-- Tenant isolation consistent with the other workflow tables.
ALTER TABLE workflow_event_inbox ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_event_inbox;
CREATE POLICY tenant_isolation ON workflow_event_inbox
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE workflow_event_outbox ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_event_outbox;
CREATE POLICY tenant_isolation ON workflow_event_outbox
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE workflow_notification_intents ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_notification_intents;
CREATE POLICY tenant_isolation ON workflow_notification_intents
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
