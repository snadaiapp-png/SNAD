-- ============================================================
-- R2-A — Notification & Communication Foundation (GATES R2.3/R2.4/R2.5)
-- ============================================================
-- ADDITIVE FORWARD-ONLY. Extends the R1/Y2 workflow_notification_intents
-- ledger to the R2 delivery lifecycle, adds the authoritative notification
-- policy model, the IN_APP user feed, and the governed webhook endpoint
-- registry. Every new table: tenant_id, PK, FKs, indexes, uniqueness,
-- timestamps, state constraints, RLS ENABLE + fail-closed tenant_isolation.

-- ------------------------------------------------------------
-- 1. workflow_notification_intents — additive widening (R2.5)
--    channel += PUSH, WHATSAPP; lifecycle per AD-3 (legacy values kept).
-- ------------------------------------------------------------
ALTER TABLE workflow_notification_intents
    DROP CONSTRAINT IF EXISTS ck_wf_notification_channel;
ALTER TABLE workflow_notification_intents
    ADD CONSTRAINT ck_wf_notification_channel CHECK (channel IN
        ('IN_APP', 'EMAIL', 'PUSH', 'WHATSAPP', 'WEBHOOK'));

ALTER TABLE workflow_notification_intents
    DROP CONSTRAINT IF EXISTS ck_wf_notification_delivery;
ALTER TABLE workflow_notification_intents
    ADD CONSTRAINT ck_wf_notification_delivery CHECK (delivery_status IN (
        'PENDING', 'PROCESSING', 'DELIVERED', 'RETRY_WAIT',
        'FAILED_RETRYABLE', 'FAILED_TERMINAL', 'CANCELLED',
        'SENT', 'FAILED'));

ALTER TABLE workflow_notification_intents
    ADD COLUMN IF NOT EXISTS external_action_id UUID,
    ADD COLUMN IF NOT EXISTS recipient_participant_id UUID,
    ADD COLUMN IF NOT EXISTS recipient_address VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS title VARCHAR(300),
    ADD COLUMN IF NOT EXISTS body VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS template_key VARCHAR(200),
    ADD COLUMN IF NOT EXISTS locale VARCHAR(10),
    ADD COLUMN IF NOT EXISTS payload JSONB,
    ADD COLUMN IF NOT EXISTS priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS deep_link VARCHAR(500),
    ADD COLUMN IF NOT EXISTS policy_id UUID,
    ADD COLUMN IF NOT EXISTS provider_type VARCHAR(60),
    ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS failure_category VARCHAR(40),
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS correlation_id UUID,
    ADD COLUMN IF NOT EXISTS causation_id VARCHAR(200);

ALTER TABLE workflow_notification_intents
    ADD CONSTRAINT ck_wf_notification_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL'));

CREATE INDEX IF NOT EXISTS idx_wf_notifications_dispatch
    ON workflow_notification_intents(tenant_id, delivery_status, next_attempt_at)
    WHERE delivery_status IN ('PENDING', 'RETRY_WAIT', 'FAILED_RETRYABLE');
CREATE INDEX IF NOT EXISTS idx_wf_notifications_feed
    ON workflow_notification_intents(tenant_id, recipient_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wf_notifications_action
    ON workflow_notification_intents(tenant_id, external_action_id)
    WHERE external_action_id IS NOT NULL;

-- ------------------------------------------------------------
-- 2. workflow_notification_policies — authoritative policy model (R2.3)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_notification_policies (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    name                VARCHAR(200)    NOT NULL,
    channel             VARCHAR(20)     NOT NULL,
    event_type          VARCHAR(60)     NOT NULL,
    recipient_source    VARCHAR(30)     NOT NULL DEFAULT 'USER',
    template_key        VARCHAR(200),
    locale              VARCHAR(10)     NOT NULL DEFAULT 'ar',
    timing              VARCHAR(20)     NOT NULL DEFAULT 'IMMEDIATE',
    offset_seconds      INTEGER,
    retry_policy        VARCHAR(20)     NOT NULL DEFAULT 'STANDARD',
    dedup_strategy      VARCHAR(20)     NOT NULL DEFAULT 'PER_EVENT',
    escalation          VARCHAR(30)     NOT NULL DEFAULT 'NONE',
    fallback_channel    VARCHAR(20),
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    priority            VARCHAR(10)     NOT NULL DEFAULT 'NORMAL',
    configuration       JSONB,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_workflow_notification_policies PRIMARY KEY (id),
    CONSTRAINT uk_wf_notification_policies UNIQUE (tenant_id, name),
    CONSTRAINT ck_wf_np_channel CHECK (channel IN
        ('IN_APP', 'EMAIL', 'PUSH', 'WHATSAPP', 'WEBHOOK')),
    CONSTRAINT ck_wf_np_recipient CHECK (recipient_source IN
        ('USER', 'EXTERNAL_PARTICIPANT')),
    CONSTRAINT ck_wf_np_timing CHECK (timing IN
        ('IMMEDIATE', 'BEFORE_DUE', 'ON_BREACH')),
    CONSTRAINT ck_wf_np_retry CHECK (retry_policy IN
        ('NONE', 'STANDARD', 'AGGRESSIVE')),
    CONSTRAINT ck_wf_np_dedup CHECK (dedup_strategy IN
        ('PER_TIMER', 'PER_ACTION', 'PER_EVENT', 'NONE')),
    CONSTRAINT ck_wf_np_escalation CHECK (escalation IN
        ('NONE', 'SUPERVISOR_ALERT')),
    CONSTRAINT ck_wf_np_fallback CHECK (
        fallback_channel IS NULL OR fallback_channel IN ('IN_APP')),
    CONSTRAINT ck_wf_np_priority CHECK (priority IN
        ('LOW', 'NORMAL', 'HIGH', 'CRITICAL'))
);
CREATE INDEX IF NOT EXISTS idx_wf_notification_policies_lookup
    ON workflow_notification_policies(tenant_id, event_type, channel, enabled);

ALTER TABLE workflow_notification_policies ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_notification_policies_tenant_isolation
    ON workflow_notification_policies;
CREATE POLICY workflow_notification_policies_tenant_isolation
    ON workflow_notification_policies
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- ------------------------------------------------------------
-- 3. workflow_user_notifications — IN_APP user feed read model (R2.6)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_user_notifications (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    recipient_user_id   UUID            NOT NULL,
    event_type          VARCHAR(60)     NOT NULL,
    workflow_instance_id UUID,
    work_item_id        UUID,
    external_action_id  UUID,
    title               VARCHAR(300)    NOT NULL,
    body                VARCHAR(2000),
    deep_link           VARCHAR(500),
    priority            VARCHAR(10)     NOT NULL DEFAULT 'NORMAL',
    source_ref          VARCHAR(200),
    dedup_key           VARCHAR(200),
    intent_id           UUID,
    read_at             TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_workflow_user_notifications PRIMARY KEY (id),
    CONSTRAINT uk_wf_user_notifications_tenant UNIQUE (tenant_id, id),
    CONSTRAINT uq_wf_user_notifications_dedup UNIQUE (tenant_id, dedup_key),
    CONSTRAINT ck_wf_un_priority CHECK (priority IN
        ('LOW', 'NORMAL', 'HIGH', 'CRITICAL'))
);
CREATE INDEX IF NOT EXISTS idx_wf_user_notifications_feed
    ON workflow_user_notifications(tenant_id, recipient_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wf_user_notifications_unread
    ON workflow_user_notifications(tenant_id, recipient_user_id, read_at)
    WHERE read_at IS NULL;

-- RLS is tenant-scoped (platform convention: the RLS connection handler
-- sets only app.tenant_id). Recipient-identity scoping is enforced at the
-- service layer (always derived from the authenticated principal, never a
-- client parameter) — same pattern as /work-items/mine.
ALTER TABLE workflow_user_notifications ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_user_notifications_tenant_isolation
    ON workflow_user_notifications;
CREATE POLICY workflow_user_notifications_tenant_isolation
    ON workflow_user_notifications
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- ------------------------------------------------------------
-- 4. workflow_webhook_endpoints — governed destination registry (R2.10)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_webhook_endpoints (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    name                VARCHAR(200)    NOT NULL,
    url                 VARCHAR(1000)   NOT NULL,
    description         VARCHAR(500),
    secret_hash         CHAR(64),
    event_types         JSONB           NOT NULL DEFAULT '[]'::jsonb,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_by          UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_workflow_webhook_endpoints PRIMARY KEY (id),
    CONSTRAINT uk_wf_webhook_endpoints UNIQUE (tenant_id, name),
    CONSTRAINT ck_wf_webhook_https CHECK (url LIKE 'https://%'),
    CONSTRAINT ck_wf_webhook_status CHECK (status IN
        ('ACTIVE', 'PAUSED', 'REVOKED'))
);
CREATE INDEX IF NOT EXISTS idx_wf_webhook_endpoints_status
    ON workflow_webhook_endpoints(tenant_id, status);

ALTER TABLE workflow_webhook_endpoints ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_webhook_endpoints_tenant_isolation
    ON workflow_webhook_endpoints;
CREATE POLICY workflow_webhook_endpoints_tenant_isolation
    ON workflow_webhook_endpoints
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);
