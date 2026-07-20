-- ============================================================================
-- SANAD CRM-008 — V20260720_4 — Create CRM Assignment Rules
-- ============================================================================
-- Forward-only. Idempotent. Tenant-scoped. Versioned.
-- REVIEW ONLY — DO NOT EXECUTE in this design phase.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Assignment Rules (current version pointer)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_assignment_rules (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    code                VARCHAR(64) NOT NULL,
    current_version     INTEGER NOT NULL DEFAULT 1,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT fk_assignment_rules_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_assignment_rules_status CHECK (status IN ('ACTIVE','INACTIVE','DEPRECATED')),
    -- Inline composite UNIQUE so crm_assignment_rule_versions can FK to (tenant_id, rule_id)
    CONSTRAINT pk_assignment_rules PRIMARY KEY (id),
    CONSTRAINT uk_assignment_rules_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_assignment_rules_tenant_code
    ON crm_assignment_rules (tenant_id, code);

-- Only one ACTIVE rule per (tenant, code) — enforced on the rule itself
-- (status INACTIVE/DEPRECATED versions can coexist for history).
-- [CRM-008 fix] Partial WHERE clause removed for Flyway/PostgreSQL compatibility.
--      The invariant is enforced at the application layer instead.
CREATE UNIQUE INDEX IF NOT EXISTS uk_assignment_rules_active
    ON crm_assignment_rules (tenant_id, code);

-- ----------------------------------------------------------------------------
-- 2. Assignment Rule Versions (snapshot of every version)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_assignment_rule_versions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    rule_id                 UUID NOT NULL,
    version                 INTEGER NOT NULL,
    display_name            VARCHAR(200) NOT NULL,
    description             VARCHAR(1000),
    record_type             VARCHAR(20) NOT NULL,
    priority                INTEGER NOT NULL DEFAULT 100,
    match_conditions        JSONB NOT NULL DEFAULT '{}'::jsonb,
    distribution_method     VARCHAR(30) NOT NULL,
    target_owner_id         UUID,
    target_team_id          UUID,
    target_queue_id         UUID,
    fallback_owner_id       UUID,
    effective_from          TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_to            TIMESTAMPTZ,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_rule_versions_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_rule_versions_rules FOREIGN KEY (tenant_id, rule_id)
        REFERENCES crm_assignment_rules(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_rule_versions_status CHECK (status IN ('ACTIVE','INACTIVE','DEPRECATED')),
    CONSTRAINT ck_rule_versions_record_type CHECK (record_type IN (
        'LEAD','OPPORTUNITY','TASK','ACTIVITY','ACCOUNT'
    )),
    CONSTRAINT ck_rule_versions_method CHECK (distribution_method IN (
        'DIRECT_OWNER','TEAM_ASSIGNMENT','QUEUE_ASSIGNMENT','ROUND_ROBIN',
        'LEAST_LOADED','WEIGHTED','TERRITORY_BASED','SKILL_BASED','RULE_CHAIN'
    ))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rule_versions_tenant_rule_version
    ON crm_assignment_rule_versions (tenant_id, rule_id, version);

CREATE INDEX IF NOT EXISTS idx_rule_versions_tenant_status_priority
    ON crm_assignment_rule_versions (tenant_id, status, record_type, priority, effective_from);

-- [CRM-008 fix] Partial WHERE clause removed for Flyway/PostgreSQL compatibility.
--      The invariant is enforced at the application layer instead.
CREATE INDEX IF NOT EXISTS idx_rule_versions_active
    ON crm_assignment_rule_versions (tenant_id, record_type, priority);

COMMIT;

-- Validation:
-- SELECT count(*) FROM information_schema.tables WHERE table_name IN
--   ('crm_assignment_rules','crm_assignment_rule_versions');
-- Expected: 2
