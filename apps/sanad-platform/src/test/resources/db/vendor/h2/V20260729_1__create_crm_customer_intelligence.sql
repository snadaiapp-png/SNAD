-- ============================================================
-- SNAD Platform — CRM-010 — V20260729.1 (H2 Test Mirror)
-- Customer 360 & Unified Customer Intelligence
--
-- Mirrors the PostgreSQL schema with H2-compatible syntax.
-- Partial indexes approximated as full indexes.
-- ============================================================

-- crm_customer_scores
CREATE TABLE IF NOT EXISTS crm_customer_scores (
    id              UUID NOT NULL DEFAULT RANDOM_UUID(),
    tenant_id       UUID NOT NULL,
    account_id      UUID NOT NULL,
    score_type      VARCHAR(40) NOT NULL,
    score_value     DOUBLE PRECISION NOT NULL,
    score_band      VARCHAR(40) NOT NULL,
    components      JSON NOT NULL DEFAULT '{}',
    confidence      DOUBLE PRECISION,
    calculated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    trigger_reason  VARCHAR(40) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_crm_customer_scores PRIMARY KEY (id),
    CONSTRAINT crm_customer_scores_tenant_id_uq UNIQUE (tenant_id, id),
    CONSTRAINT crm_customer_scores_uq_1 UNIQUE (tenant_id, account_id, score_type, calculated_at),
    CONSTRAINT crm_customer_scores_type_ck CHECK (score_type IN ('HEALTH','CLV','ENGAGEMENT','RISK','LOYALTY')),
    CONSTRAINT crm_customer_scores_trigger_ck CHECK (trigger_reason IN ('SCHEDULED','MANUAL','EVENT_DRIVEN'))
);

CREATE INDEX IF NOT EXISTS crm_customer_scores_tenant_account_type_idx
    ON crm_customer_scores (tenant_id, account_id, score_type, calculated_at);

-- crm_customer_score_history
CREATE TABLE IF NOT EXISTS crm_customer_score_history (
    id              UUID NOT NULL DEFAULT RANDOM_UUID(),
    tenant_id       UUID NOT NULL,
    account_id      UUID NOT NULL,
    score_type      VARCHAR(40) NOT NULL,
    previous_value  DOUBLE PRECISION,
    previous_band   VARCHAR(40),
    new_value       DOUBLE PRECISION NOT NULL,
    new_band        VARCHAR(40) NOT NULL,
    delta           DOUBLE PRECISION NOT NULL,
    changed_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by      UUID,
    trigger_reason  VARCHAR(40) NOT NULL,

    CONSTRAINT pk_crm_customer_score_history PRIMARY KEY (id),
    CONSTRAINT crm_customer_score_history_type_ck CHECK (score_type IN ('HEALTH','CLV','ENGAGEMENT','RISK','LOYALTY')),
    CONSTRAINT crm_customer_score_history_trigger_ck CHECK (trigger_reason IN ('SCHEDULED','MANUAL','EVENT_DRIVEN'))
);

CREATE INDEX IF NOT EXISTS crm_customer_score_history_tenant_account_idx
    ON crm_customer_score_history (tenant_id, account_id, changed_at);

-- crm_customer_segments
CREATE TABLE IF NOT EXISTS crm_customer_segments (
    id              UUID NOT NULL DEFAULT RANDOM_UUID(),
    tenant_id       UUID NOT NULL,
    segment_code    VARCHAR(80) NOT NULL,
    segment_name    VARCHAR(200) NOT NULL,
    segment_type    VARCHAR(40) NOT NULL,
    description     TEXT,
    criteria        JSON,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_crm_customer_segments PRIMARY KEY (id),
    CONSTRAINT crm_customer_segments_tenant_code_uq UNIQUE (tenant_id, segment_code),
    CONSTRAINT crm_customer_segments_type_ck CHECK (segment_type IN ('MANUAL','RULE_BASED','AI_GENERATED'))
);

-- crm_segment_memberships
CREATE TABLE IF NOT EXISTS crm_segment_memberships (
    id              UUID NOT NULL DEFAULT RANDOM_UUID(),
    tenant_id       UUID NOT NULL,
    account_id      UUID NOT NULL,
    segment_id      UUID NOT NULL,
    membership_type VARCHAR(40) NOT NULL,
    assigned_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by     UUID,
    active          BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_crm_segment_memberships PRIMARY KEY (id),
    CONSTRAINT crm_segment_memberships_tenant_account_segment_uq UNIQUE (tenant_id, account_id, segment_id),
    CONSTRAINT crm_segment_memberships_type_ck CHECK (membership_type IN ('MANUAL','AUTO'))
);

CREATE INDEX IF NOT EXISTS crm_segment_memberships_tenant_account_idx
    ON crm_segment_memberships (tenant_id, account_id, active);

CREATE INDEX IF NOT EXISTS crm_segment_memberships_tenant_segment_idx
    ON crm_segment_memberships (tenant_id, segment_id, active);

-- crm_next_best_actions
CREATE TABLE IF NOT EXISTS crm_next_best_actions (
    id                          UUID NOT NULL DEFAULT RANDOM_UUID(),
    tenant_id                   UUID NOT NULL,
    account_id                  UUID NOT NULL,
    action_code                 VARCHAR(80) NOT NULL,
    description                 TEXT,
    confidence                  DOUBLE PRECISION NOT NULL,
    reasoning                   TEXT,
    status                      VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    generated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    human_confirmation_required BOOLEAN NOT NULL DEFAULT TRUE,
    resolved_at                 TIMESTAMP WITH TIME ZONE,
    resolved_by                 UUID,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                     BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_crm_next_best_actions PRIMARY KEY (id),
    CONSTRAINT crm_next_best_actions_tenant_id_uq UNIQUE (tenant_id, id),
    CONSTRAINT crm_next_best_actions_status_ck CHECK (status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED')),
    CONSTRAINT crm_next_best_actions_expiry_ck CHECK (expires_at > generated_at)
);

CREATE INDEX IF NOT EXISTS crm_next_best_actions_tenant_account_status_idx
    ON crm_next_best_actions (tenant_id, account_id, status, generated_at);

-- crm_scoring_models
CREATE TABLE IF NOT EXISTS crm_scoring_models (
    id              UUID NOT NULL DEFAULT RANDOM_UUID(),
    tenant_id       UUID NOT NULL,
    score_type      VARCHAR(40) NOT NULL,
    version         VARCHAR(40) NOT NULL,
    weights         JSON NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    activated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_crm_scoring_models PRIMARY KEY (id),
    CONSTRAINT crm_scoring_models_tenant_type_version_uq UNIQUE (tenant_id, score_type, version),
    CONSTRAINT crm_scoring_models_type_ck CHECK (score_type IN ('HEALTH','CLV','ENGAGEMENT','RISK','LOYALTY'))
);

CREATE INDEX IF NOT EXISTS crm_scoring_models_tenant_type_active_idx
    ON crm_scoring_models (tenant_id, score_type, active);
