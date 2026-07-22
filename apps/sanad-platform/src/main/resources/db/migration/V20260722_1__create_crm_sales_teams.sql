-- ============================================================================
-- SANAD CRM-008B — V20260722_1 — Create CRM Sales Teams
-- ============================================================================
-- Forward-only. Fail-closed. Tenant-scoped. Composite FKs.
-- Tenant-leading indexes. UUID PKs. Sequential integer versioning.
-- ============================================================================

BEGIN;

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

CREATE TABLE crm_sales_teams (
    id                   UUID NOT NULL,
    tenant_id            UUID NOT NULL,
    code                 VARCHAR(64) NOT NULL,
    display_name         VARCHAR(200) NOT NULL,
    description          VARCHAR(1000),
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    manager_user_id      UUID,
    default_queue_id     UUID,
    default_territory_id UUID,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_by           UUID,
    CONSTRAINT pk_sales_teams PRIMARY KEY (id),
    CONSTRAINT uk_sales_teams_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_sales_teams_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_sales_teams_status CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED'))
);

CREATE UNIQUE INDEX uk_sales_teams_tenant_code
    ON crm_sales_teams (tenant_id, code);

CREATE INDEX idx_sales_teams_tenant_status
    ON crm_sales_teams (tenant_id, status, display_name);

CREATE TABLE crm_team_memberships (
    id              UUID NOT NULL,
    tenant_id       UUID NOT NULL,
    team_id         UUID NOT NULL,
    user_id         UUID NOT NULL,
    role            VARCHAR(40) NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    left_at         TIMESTAMP WITH TIME ZONE,
    left_reason     VARCHAR(100),
    capacity_max    INTEGER NOT NULL DEFAULT 50,
    metadata        TEXT NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT pk_team_memberships PRIMARY KEY (id),
    CONSTRAINT fk_team_memberships_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_memberships_sales_teams FOREIGN KEY (tenant_id, team_id)
        REFERENCES crm_sales_teams(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_team_memberships_role CHECK (role IN (
        'SALES_MANAGER','ACCOUNT_MANAGER','SALES_REPRESENTATIVE',
        'LEAD_QUALIFIER','OPPORTUNITY_SPECIALIST','READONLY_CONTRIBUTOR'
    )),
    CONSTRAINT ck_team_memberships_status CHECK (status IN ('ACTIVE','ENDED')),
    CONSTRAINT ck_team_memberships_capacity CHECK (capacity_max >= 0 AND capacity_max <= 1000)
);

-- Partial unique index moved to vendor-specific migration

-- Partial unique index moved to vendor-specific migration

CREATE INDEX idx_team_memberships_team_status
    ON crm_team_memberships (tenant_id, team_id, status, joined_at DESC);

CREATE INDEX idx_team_memberships_user_status
    ON crm_team_memberships (tenant_id, user_id, status, joined_at DESC);

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

COMMIT;
