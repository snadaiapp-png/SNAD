-- ============================================================================
-- SANAD CRM-008 — V20260720_1 — Create CRM Sales Teams
-- ============================================================================
-- Forward-only. Idempotent (IF NOT EXISTS). Tenant-scoped.
-- Composite FKs. Tenant-leading indexes. UUID PKs.
--
-- REVIEW ONLY — DO NOT EXECUTE in this design phase.
-- Execution authorized only in CRM-008B after CRM-007 closure gate.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Sales Teams
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_sales_teams (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    code                VARCHAR(64) NOT NULL,
    display_name        VARCHAR(200) NOT NULL,
    description         VARCHAR(1000),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    manager_user_id     UUID,
    default_queue_id    UUID,
    default_territory_id UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_by           UUID,
    CONSTRAINT fk_sales_teams_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_sales_teams_status CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED'))
);

-- One team code per tenant
CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_teams_tenant_code
    ON crm_sales_teams (tenant_id, code);

-- Tenant-leading index for listing
CREATE INDEX IF NOT EXISTS idx_sales_teams_tenant_status
    ON crm_sales_teams (tenant_id, status, display_name);

-- One ACTIVE manager per team (the manager is set on the team row itself)
-- Note: a user can manage multiple teams; the "one primary team per user"
-- is enforced on the team_memberships table below.

-- ----------------------------------------------------------------------------
-- 2. Team Memberships
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_team_memberships (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    team_id             UUID NOT NULL,
    user_id             UUID NOT NULL,
    role                VARCHAR(40) NOT NULL,
    is_primary          BOOLEAN NOT NULL DEFAULT false,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at             TIMESTAMPTZ,
    left_reason         VARCHAR(100),
    capacity_max        INTEGER NOT NULL DEFAULT 50,
    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
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

-- Composite FK to sales_teams (tenant_id, team_id) — already created via FK above.
-- Add a composite unique constraint on sales_teams for the FK target:
CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_teams_tenant_id_pk
    ON crm_sales_teams (tenant_id, id);

-- One ACTIVE membership per (tenant, team, user)
CREATE UNIQUE INDEX IF NOT EXISTS uk_team_memberships_active
    ON crm_team_memberships (tenant_id, team_id, user_id)
    WHERE status = 'ACTIVE';

-- One PRIMARY membership per (tenant, user)
CREATE UNIQUE INDEX IF NOT EXISTS uk_team_memberships_primary
    ON crm_team_memberships (tenant_id, user_id)
    WHERE is_primary = true AND status = 'ACTIVE';

-- Tenant-leading index for "list members of team X"
CREATE INDEX IF NOT EXISTS idx_team_memberships_team_status
    ON crm_team_memberships (tenant_id, team_id, status, joined_at DESC);

-- Tenant-leading index for "list teams of user X"
CREATE INDEX IF NOT EXISTS idx_team_memberships_user_status
    ON crm_team_memberships (tenant_id, user_id, status, joined_at DESC);

-- Index for workload queries (find all ACTIVE memberships for a user across teams)
CREATE INDEX IF NOT EXISTS idx_team_memberships_user_active
    ON crm_team_memberships (tenant_id, user_id, status)
    WHERE status = 'ACTIVE';

COMMIT;

-- ----------------------------------------------------------------------------
-- Post-migration validation (run manually after execution)
-- ----------------------------------------------------------------------------
-- SELECT count(*) FROM information_schema.tables
--  WHERE table_name IN ('crm_sales_teams','crm_team_memberships');
-- Expected: 2
--
-- SELECT count(*) FROM pg_indexes
--  WHERE tablename IN ('crm_sales_teams','crm_team_memberships');
-- Expected: 6 (1 PK + 2 unique + 3 indexes for teams/memberships)
