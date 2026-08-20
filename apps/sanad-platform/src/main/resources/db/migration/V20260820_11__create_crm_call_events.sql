-- G8 EXECUTION 03 — TRACK C: crm_call_events (call aggregate SSoT, G8-ADR-003).
--
-- Forward-only migration. The call aggregate owns the full telephony
-- lifecycle; crm_activities (activity_type='CALL') and crm_timeline_events
-- are BUSINESS projections written by the application layer.
--
-- Security posture (project pattern V20260812_1/3):
--   * tenant_id NOT NULL + composite tenant-safe FKs toward
--     crm_contacts / crm_accounts / users
--   * normalised-only phone columns (no raw numbers); masking in output/logs
--   * ENABLE ROW LEVEL SECURITY + tenant isolation policy here,
--     FORCE ROW LEVEL SECURITY in V20260820_12

-- The (tenant_id, id) unique on users already exists (V5, uk_users_tenant_id),
-- so the composite tenant-safe agent FK below can reference it directly.
-- (v13.0.1: the previous additive ALTER was redundant and failed with 42P07;
-- this migration never applied anywhere, so it is corrected in place.)

CREATE TABLE crm_call_events (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    provider VARCHAR(40) NOT NULL,
    provider_call_id VARCHAR(160) NOT NULL,

    direction VARCHAR(16) NOT NULL,
    source VARCHAR(32) NOT NULL,

    from_number_normalized VARCHAR(24),
    to_number_normalized VARCHAR(24),

    match_status VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
    matched_entity_type VARCHAR(16),
    matched_entity_id UUID,
    matched_contact_id UUID,
    matched_account_id UUID,
    match_source VARCHAR(32),

    agent_user_id UUID,
    device_id UUID,

    status VARCHAR(16) NOT NULL DEFAULT 'RINGING',

    ringing_at TIMESTAMP WITH TIME ZONE,
    answered_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    duration_seconds INTEGER,
    disposition VARCHAR(32),

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_call_events PRIMARY KEY (id),
    CONSTRAINT uk_crm_call_events_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_call_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_call_events_contact_same_tenant FOREIGN KEY (tenant_id, matched_contact_id)
        REFERENCES crm_contacts (tenant_id, id),
    CONSTRAINT fk_crm_call_events_account_same_tenant FOREIGN KEY (tenant_id, matched_account_id)
        REFERENCES crm_accounts (tenant_id, id),
    CONSTRAINT fk_crm_call_events_agent_same_tenant FOREIGN KEY (tenant_id, agent_user_id)
        REFERENCES users (tenant_id, id),

    CONSTRAINT ck_crm_call_events_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_crm_call_events_source CHECK (source IN (
        'MANUAL', 'ANDROID_CALL', 'IOS_CALLER_EXTENSION', 'PBX', 'VOIP')),
    CONSTRAINT ck_crm_call_events_status CHECK (status IN (
        'RINGING', 'ANSWERED', 'COMPLETED', 'MISSED', 'REJECTED', 'BUSY', 'FAILED')),
    CONSTRAINT ck_crm_call_events_disposition CHECK (disposition IS NULL OR disposition IN (
        'CONNECTED', 'NO_ANSWER', 'BUSY', 'REJECTED', 'FAILED',
        'CALLBACK_REQUESTED', 'FOLLOW_UP_REQUIRED', 'OTHER')),
    CONSTRAINT ck_crm_call_events_match_status CHECK (match_status IN (
        'EXACT', 'AMBIGUOUS', 'UNKNOWN', 'PRIVATE_NUMBER', 'INVALID_NUMBER', 'RESTRICTED')),
    CONSTRAINT ck_crm_call_events_phone_e164 CHECK (
        from_number_normalized IS NULL OR from_number_normalized LIKE '+%'),
    CONSTRAINT ck_crm_call_events_duration CHECK (
        duration_seconds IS NULL OR duration_seconds >= 0),
    CONSTRAINT ck_crm_call_events_match_type CHECK (
        matched_entity_type IS NULL OR matched_entity_type IN ('CONTACT', 'ACCOUNT', 'LEAD'))
);

-- Idempotency gate (P0): one aggregate per provider call id (G8-03 §12).
CREATE UNIQUE INDEX uq_crm_call_events_provider_call
    ON crm_call_events (tenant_id, provider, provider_call_id);
CREATE INDEX idx_crm_call_events_matched_entity
    ON crm_call_events (tenant_id, matched_entity_type, matched_entity_id, created_at DESC);
CREATE INDEX idx_crm_call_events_agent
    ON crm_call_events (tenant_id, agent_user_id, created_at DESC);
CREATE INDEX idx_crm_call_events_status
    ON crm_call_events (tenant_id, status, created_at DESC);
CREATE INDEX idx_crm_call_events_from_number
    ON crm_call_events (tenant_id, from_number_normalized, created_at DESC);

-- Tenant isolation (pattern V20260812_1): the GUC is set per transaction by
-- TenantRlsConnectionHandler; superuser/owner bypass is closed by FORCE in
-- V20260820_12.
ALTER TABLE crm_call_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY call_events_tenant_isolation ON crm_call_events
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID);
