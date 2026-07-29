-- ============================================================
-- SNAD Platform — CRM-010 — V20260729.1
-- Customer 360 & Unified Customer Intelligence
--
-- Creates 6 tables:
--   crm_customer_scores          Latest AI-computed scores
--   crm_customer_score_history   Immutable score change audit
--   crm_customer_segments        Segment definitions
--   crm_segment_memberships      Account ↔ Segment mapping
--   crm_next_best_actions        AI-generated recommendations
--   crm_scoring_models           Configurable scoring weights
--
-- Forward-only. Fail-closed. Tenant-scoped. PostgreSQL 16 native.
-- ============================================================

-- ============================================================
-- PRECONDITIONS
-- ============================================================
DO $precondition$
DECLARE
    conflict_count INTEGER;
    failed_count INTEGER;
BEGIN
    -- Tables must not already exist
    SELECT COUNT(*) INTO conflict_count FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'crm_customer_scores';
    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'V20260729.1 precondition failed: crm_customer_scores already exists';
    END IF;

    SELECT COUNT(*) INTO conflict_count FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'crm_customer_score_history';
    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'V20260729.1 precondition failed: crm_customer_score_history already exists';
    END IF;

    SELECT COUNT(*) INTO conflict_count FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'crm_customer_segments';
    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'V20260729.1 precondition failed: crm_customer_segments already exists';
    END IF;

    SELECT COUNT(*) INTO conflict_count FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'crm_segment_memberships';
    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'V20260729.1 precondition failed: crm_segment_memberships already exists';
    END IF;

    SELECT COUNT(*) INTO conflict_count FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'crm_next_best_actions';
    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'V20260729.1 precondition failed: crm_next_best_actions already exists';
    END IF;

    SELECT COUNT(*) INTO conflict_count FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'crm_scoring_models';
    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'V20260729.1 precondition failed: crm_scoring_models already exists';
    END IF;

    -- Index name conflicts
    SELECT COUNT(*) INTO conflict_count FROM pg_indexes
        WHERE indexname IN (
            'crm_customer_scores_tenant_account_type_idx',
            'crm_customer_score_history_tenant_account_idx',
            'crm_segment_memberships_tenant_account_idx',
            'crm_segment_memberships_tenant_segment_idx',
            'crm_next_best_actions_tenant_account_status_idx',
            'crm_scoring_models_tenant_type_active_idx'
        );
    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'V20260729.1 precondition failed: conflicting index names detected';
    END IF;

    -- Flyway history must be clean
    SELECT COUNT(*) INTO failed_count FROM flyway_schema_history WHERE success = FALSE;
    IF failed_count > 0 THEN
        RAISE EXCEPTION 'V20260729.1 precondition failed: flyway history contains % failed rows', failed_count;
    END IF;
END $precondition$;

-- ============================================================
-- TABLE: crm_customer_scores
-- Latest AI-computed scores per account/type
-- ============================================================
CREATE TABLE crm_customer_scores (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    account_id      UUID NOT NULL,
    score_type      VARCHAR(40) NOT NULL,
    score_value     DOUBLE PRECISION NOT NULL,
    score_band      VARCHAR(40) NOT NULL,
    components      JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence      DOUBLE PRECISION,
    calculated_at   TIMESTAMPTZ NOT NULL,
    trigger_reason  VARCHAR(40) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_crm_customer_scores PRIMARY KEY (id),
    CONSTRAINT crm_customer_scores_tenant_id_uq UNIQUE (tenant_id, id),
    CONSTRAINT crm_customer_scores_uq UNIQUE (tenant_id, account_id, score_type, calculated_at),
    CONSTRAINT crm_customer_scores_type_ck CHECK (score_type IN ('HEALTH','CLV','ENGAGEMENT','RISK','LOYALTY')),
    CONSTRAINT crm_customer_scores_trigger_ck CHECK (trigger_reason IN ('SCHEDULED','MANUAL','EVENT_DRIVEN')),
    CONSTRAINT crm_customer_scores_components_ck CHECK (jsonb_typeof(components) = 'object')
);

CREATE INDEX crm_customer_scores_tenant_account_type_idx
    ON crm_customer_scores (tenant_id, account_id, score_type, calculated_at DESC);

-- ============================================================
-- TABLE: crm_customer_score_history
-- Immutable audit trail of score changes
-- ============================================================
CREATE TABLE crm_customer_score_history (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    account_id      UUID NOT NULL,
    score_type      VARCHAR(40) NOT NULL,
    previous_value  DOUBLE PRECISION,
    previous_band   VARCHAR(40),
    new_value       DOUBLE PRECISION NOT NULL,
    new_band        VARCHAR(40) NOT NULL,
    delta           DOUBLE PRECISION NOT NULL,
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by      UUID,
    trigger_reason  VARCHAR(40) NOT NULL,

    CONSTRAINT pk_crm_customer_score_history PRIMARY KEY (id),
    CONSTRAINT crm_customer_score_history_type_ck CHECK (score_type IN ('HEALTH','CLV','ENGAGEMENT','RISK','LOYALTY')),
    CONSTRAINT crm_customer_score_history_trigger_ck CHECK (trigger_reason IN ('SCHEDULED','MANUAL','EVENT_DRIVEN'))
);

CREATE INDEX crm_customer_score_history_tenant_account_idx
    ON crm_customer_score_history (tenant_id, account_id, changed_at DESC);

-- ============================================================
-- TABLE: crm_customer_segments
-- Segment definitions (MANUAL, RULE_BASED, AI_GENERATED)
-- ============================================================
CREATE TABLE crm_customer_segments (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    segment_code    VARCHAR(80) NOT NULL,
    segment_name    VARCHAR(200) NOT NULL,
    segment_type    VARCHAR(40) NOT NULL,
    description     TEXT,
    criteria        JSONB,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_crm_customer_segments PRIMARY KEY (id),
    CONSTRAINT crm_customer_segments_tenant_code_uq UNIQUE (tenant_id, segment_code),
    CONSTRAINT crm_customer_segments_type_ck CHECK (segment_type IN ('MANUAL','RULE_BASED','AI_GENERATED'))
);

-- ============================================================
-- TABLE: crm_segment_memberships
-- Account ↔ Segment mapping
-- ============================================================
CREATE TABLE crm_segment_memberships (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    account_id      UUID NOT NULL,
    segment_id      UUID NOT NULL,
    membership_type VARCHAR(40) NOT NULL,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by     UUID,
    active          BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_crm_segment_memberships PRIMARY KEY (id),
    CONSTRAINT crm_segment_memberships_tenant_account_segment_uq UNIQUE (tenant_id, account_id, segment_id),
    CONSTRAINT crm_segment_memberships_type_ck CHECK (membership_type IN ('MANUAL','AUTO'))
);

CREATE INDEX crm_segment_memberships_tenant_account_idx
    ON crm_segment_memberships (tenant_id, account_id, active);

CREATE INDEX crm_segment_memberships_tenant_segment_idx
    ON crm_segment_memberships (tenant_id, segment_id, active);

-- ============================================================
-- TABLE: crm_next_best_actions
-- AI-generated recommendations
-- ============================================================
CREATE TABLE crm_next_best_actions (
    id                          UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    account_id                  UUID NOT NULL,
    action_code                 VARCHAR(80) NOT NULL,
    description                 TEXT,
    confidence                  DOUBLE PRECISION NOT NULL,
    reasoning                   TEXT,
    status                      VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    generated_at                TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    human_confirmation_required BOOLEAN NOT NULL DEFAULT TRUE,
    resolved_at                 TIMESTAMPTZ,
    resolved_by                 UUID,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                     BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_crm_next_best_actions PRIMARY KEY (id),
    CONSTRAINT crm_next_best_actions_tenant_id_uq UNIQUE (tenant_id, id),
    CONSTRAINT crm_next_best_actions_status_ck CHECK (status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED')),
    CONSTRAINT crm_next_best_actions_expiry_ck CHECK (expires_at > generated_at)
);

CREATE INDEX crm_next_best_actions_tenant_account_status_idx
    ON crm_next_best_actions (tenant_id, account_id, status, generated_at DESC);

-- ============================================================
-- TABLE: crm_scoring_models
-- Configurable scoring weights per tenant
-- ============================================================
CREATE TABLE crm_scoring_models (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    score_type      VARCHAR(40) NOT NULL,
    version         VARCHAR(40) NOT NULL,
    weights         JSONB NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    activated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_crm_scoring_models PRIMARY KEY (id),
    CONSTRAINT crm_scoring_models_tenant_type_version_uq UNIQUE (tenant_id, score_type, version),
    CONSTRAINT crm_scoring_models_type_ck CHECK (score_type IN ('HEALTH','CLV','ENGAGEMENT','RISK','LOYALTY')),
    CONSTRAINT crm_scoring_models_weights_ck CHECK (jsonb_typeof(weights) = 'object')
);

CREATE INDEX crm_scoring_models_tenant_type_active_idx
    ON crm_scoring_models (tenant_id, score_type, active);

-- ============================================================
-- SEED: RBAC Capabilities
-- ============================================================
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT 'a0000010-0000-0000-0000-000000001001', 'CRM.CUSTOMER_360.READ', 'Read Customer 360',
       'View unified customer 360 profile', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.CUSTOMER_360.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT 'a0000010-0000-0000-0000-000000001002', 'CRM.CUSTOMER_INTELLIGENCE.READ', 'Read Customer Intelligence',
       'View customer scores, insights, and AI predictions', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.CUSTOMER_INTELLIGENCE.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT 'a0000010-0000-0000-0000-000000001003', 'CRM.CUSTOMER_INTELLIGENCE.WRITE', 'Trigger Customer Rescoring',
       'Manually trigger customer intelligence recalculation', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.CUSTOMER_INTELLIGENCE.WRITE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT 'a0000010-0000-0000-0000-000000001004', 'CRM.CUSTOMER_INTELLIGENCE.ADMIN', 'Manage Scoring Models',
       'Configure and manage customer intelligence scoring models', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.CUSTOMER_INTELLIGENCE.ADMIN');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT 'a0000010-0000-0000-0000-000000001005', 'CRM.CUSTOMER_SEGMENT.MANAGE', 'Manage Customer Segments',
       'Create, update, and manage customer segments', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.CUSTOMER_SEGMENT.MANAGE');

-- ============================================================
-- POSTCONDITIONS
-- ============================================================
DO $postcondition$
DECLARE
    table_count INTEGER;
    cap_count INTEGER;
    idx_count INTEGER;
BEGIN
    -- All 6 tables must exist
    SELECT COUNT(*) INTO table_count FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name IN (
            'crm_customer_scores', 'crm_customer_score_history',
            'crm_customer_segments', 'crm_segment_memberships',
            'crm_next_best_actions', 'crm_scoring_models'
        );
    IF table_count != 6 THEN
        RAISE EXCEPTION 'V20260729.1 postcondition failed: expected 6 tables, found %', table_count;
    END IF;

    -- 6 indexes must exist
    SELECT COUNT(*) INTO idx_count FROM pg_indexes WHERE indexname IN (
        'crm_customer_scores_tenant_account_type_idx',
        'crm_customer_score_history_tenant_account_idx',
        'crm_segment_memberships_tenant_account_idx',
        'crm_segment_memberships_tenant_segment_idx',
        'crm_next_best_actions_tenant_account_status_idx',
        'crm_scoring_models_tenant_type_active_idx'
    );
    IF idx_count != 6 THEN
        RAISE EXCEPTION 'V20260729.1 postcondition failed: expected 6 indexes, found %', idx_count;
    END IF;

    -- 5 capabilities must be seeded
    SELECT COUNT(*) INTO cap_count FROM access_capabilities
        WHERE code IN (
            'CRM.CUSTOMER_360.READ',
            'CRM.CUSTOMER_INTELLIGENCE.READ',
            'CRM.CUSTOMER_INTELLIGENCE.WRITE',
            'CRM.CUSTOMER_INTELLIGENCE.ADMIN',
            'CRM.CUSTOMER_SEGMENT.MANAGE'
        ) AND status = 'ACTIVE';
    IF cap_count != 5 THEN
        RAISE EXCEPTION 'V20260729.1 postcondition failed: expected 5 capabilities, found %', cap_count;
    END IF;
END $postcondition$;
