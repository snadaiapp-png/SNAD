-- ============================================================================
-- SANAD CRM-008B — V20260722_3 — Create CRM Territories
-- ============================================================================
BEGIN;

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

CREATE TABLE crm_territories (
    id              UUID NOT NULL,
    tenant_id       UUID NOT NULL,
    code            VARCHAR(64) NOT NULL,
    display_name    VARCHAR(200) NOT NULL,
    parent_id       UUID,
    description     VARCHAR(1000),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    rule_type       VARCHAR(20) NOT NULL,
    rule_definition TEXT NOT NULL DEFAULT '{}',
    priority        INTEGER NOT NULL DEFAULT 100,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT pk_territories PRIMARY KEY (id),
    CONSTRAINT uk_territories_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_territories_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_territories_parent FOREIGN KEY (tenant_id, parent_id)
        REFERENCES crm_territories(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_territories_status CHECK (status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT ck_territories_rule_type CHECK (rule_type IN (
        'GEOGRAPHIC','SEGMENT','CHANNEL','ACCOUNT_LIST'
    )),
    CONSTRAINT ck_territories_no_self_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_territories_priority CHECK (priority >= 0 AND priority <= 10000)
);

CREATE UNIQUE INDEX uk_territories_tenant_code
    ON crm_territories (tenant_id, code);

CREATE INDEX idx_territories_tenant_status
    ON crm_territories (tenant_id, status, display_name);

CREATE INDEX idx_territories_tenant_parent
    ON crm_territories (tenant_id, parent_id, status);

CREATE TABLE crm_territory_closure (
    tenant_id       UUID NOT NULL,
    ancestor_id     UUID NOT NULL,
    descendant_id   UUID NOT NULL,
    depth           INTEGER NOT NULL,
    CONSTRAINT fk_territory_closure_ancestor FOREIGN KEY (tenant_id, ancestor_id)
        REFERENCES crm_territories(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_territory_closure_descendant FOREIGN KEY (tenant_id, descendant_id)
        REFERENCES crm_territories(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_territory_closure_depth CHECK (depth >= 0)
);

CREATE UNIQUE INDEX uk_territory_closure_triple
    ON crm_territory_closure (tenant_id, ancestor_id, descendant_id);

CREATE INDEX idx_territory_closure_descendant
    ON crm_territory_closure (tenant_id, descendant_id, depth);

CREATE INDEX idx_territory_closure_ancestor
    ON crm_territory_closure (tenant_id, ancestor_id, depth);

CREATE TABLE crm_territory_assignments (
    id              UUID NOT NULL,
    tenant_id       UUID NOT NULL,
    territory_id    UUID NOT NULL,
    assignee_type   VARCHAR(10) NOT NULL,
    assignee_id     UUID NOT NULL,
    role            VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    priority        INTEGER NOT NULL DEFAULT 100,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    effective_from  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    effective_to    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT pk_territory_assignments PRIMARY KEY (id),
    CONSTRAINT fk_territory_assignments_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_territory_assignments_territories FOREIGN KEY (tenant_id, territory_id)
        REFERENCES crm_territories(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_territory_assignments_type CHECK (assignee_type IN ('USER','TEAM')),
    CONSTRAINT ck_territory_assignments_role CHECK (role IN ('PRIMARY','BACKUP','OBSERVER')),
    CONSTRAINT ck_territory_assignments_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_territory_assignments_dates CHECK (effective_to IS NULL OR effective_from <= effective_to)
);

-- Partial unique index moved to vendor-specific migration

CREATE INDEX idx_territory_assignments_territory_status
    ON crm_territory_assignments (tenant_id, territory_id, status, priority);

CREATE INDEX idx_territory_assignments_assignee
    ON crm_territory_assignments (tenant_id, assignee_type, assignee_id, status);

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

COMMIT;
