-- ============================================================================
-- SANAD CRM-008B — V20260722_4 — Create CRM Assignment Rules
-- ============================================================================
BEGIN;

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

CREATE TABLE crm_assignment_rules (
    id              UUID NOT NULL,
    tenant_id       UUID NOT NULL,
    code            VARCHAR(64) NOT NULL,
    current_version INTEGER NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT pk_assignment_rules PRIMARY KEY (id),
    CONSTRAINT uk_assignment_rules_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_assignment_rules_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_assignment_rules_status CHECK (status IN ('ACTIVE','INACTIVE','DEPRECATED'))
);

CREATE UNIQUE INDEX uk_assignment_rules_tenant_code
    ON crm_assignment_rules (tenant_id, code);

CREATE TABLE crm_assignment_rule_versions (
    id                      UUID NOT NULL,
    tenant_id               UUID NOT NULL,
    rule_id                 UUID NOT NULL,
    version                 INTEGER NOT NULL,
    display_name            VARCHAR(200) NOT NULL,
    description             VARCHAR(1000),
    record_type             VARCHAR(20) NOT NULL,
    priority                INTEGER NOT NULL DEFAULT 100,
    match_conditions        TEXT NOT NULL DEFAULT '{}',
    distribution_method     VARCHAR(30) NOT NULL,
    target_owner_id         UUID,
    target_team_id          UUID,
    target_queue_id         UUID,
    fallback_owner_id       UUID,
    effective_from          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    effective_to            TIMESTAMP WITH TIME ZONE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by              UUID,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_rule_versions PRIMARY KEY (id),
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

CREATE UNIQUE INDEX uk_rule_versions_tenant_rule_version
    ON crm_assignment_rule_versions (tenant_id, rule_id, version);

CREATE INDEX idx_rule_versions_tenant_status_priority
    ON crm_assignment_rule_versions (tenant_id, status, record_type, priority, effective_from);

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

COMMIT;
