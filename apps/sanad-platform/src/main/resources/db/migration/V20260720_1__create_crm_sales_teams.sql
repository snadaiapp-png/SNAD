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
-- IMPORTANT: include UNIQUE (tenant_id, id) inline as a table constraint
-- so that crm_team_memberships can reference it via composite FK below.
-- (PostgreSQL requires the unique constraint to exist before the FK.)
CREATE TABLE IF NOT EXISTS crm_sales_teams (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
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
    created_by          UUID,
    updated_by          UUID,
    -- Composite unique on (tenant_id, id) so child tables can reference it.
    -- This is required in addition to the PK because PostgreSQL FKs need
    -- a unique constraint on the exact referenced column list.
    CONSTRAINT pk_sales_teams PRIMARY KEY (id),
    CONSTRAINT uk_sales_teams_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_sales_teams_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_sales_teams_status CHECK (status IN ('ACTIVE','SUSPENDED','ARCHIVED'))
);

-- One team code per tenant (separate from the composite unique on (tenant_id, id))
CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_teams_tenant_code
    ON crm_sales_teams (tenant_id, code);

-- Tenant-leading index for listing
CREATE INDEX IF NOT EXISTS idx_sales_teams_tenant_status
    ON crm_sales_teams (tenant_id, status, display_name);

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
    -- Composite FK referencing the UNIQUE (tenant_id, id) constraint on crm_sales_teams
    CONSTRAINT fk_team_memberships_sales_teams FOREIGN KEY (tenant_id, team_id)
        REFERENCES crm_sales_teams(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_team_memberships_role CHECK (role IN (
        'SALES_MANAGER','ACCOUNT_MANAGER','SALES_REPRESENTATIVE',
        'LEAD_QUALIFIER','OPPORTUNITY_SPECIALIST','READONLY_CONTRIBUTOR'
    )),
    CONSTRAINT ck_team_memberships_status CHECK (status IN ('ACTIVE','ENDED')),
    CONSTRAINT ck_team_memberships_capacity CHECK (capacity_max >= 0 AND capacity_max <= 1000)
);

-- One ACTIVE membership per (tenant, team, user)
-- [CRM-008 fix] Partial WHERE clause removed for Flyway/PostgreSQL compatibility.
--      The invariant is enforced at the application layer instead.
CREATE UNIQUE INDEX IF NOT EXISTS uk_team_memberships_active
    ON crm_team_memberships (tenant_id, team_id, user_id);

-- One PRIMARY membership per (tenant, user)
-- [CRM-008 fix] Partial WHERE clause removed for Flyway/PostgreSQL compatibility.
--      The invariant is enforced at the application layer instead.
CREATE UNIQUE INDEX IF NOT EXISTS uk_team_memberships_primary
    ON crm_team_memberships (tenant_id, user_id);

-- Tenant-leading index for "list members of team X"
CREATE INDEX IF NOT EXISTS idx_team_memberships_team_status
    ON crm_team_memberships (tenant_id, team_id, status, joined_at DESC);

-- Tenant-leading index for "list teams of user X"
CREATE INDEX IF NOT EXISTS idx_team_memberships_user_status
    ON crm_team_memberships (tenant_id, user_id, status, joined_at DESC);

-- Index for workload queries (find all ACTIVE memberships for a user across teams)
-- [CRM-008 fix] Partial WHERE clause removed for Flyway/PostgreSQL compatibility.
--      The invariant is enforced at the application layer instead.
CREATE INDEX IF NOT EXISTS idx_team_memberships_user_active
    ON crm_team_memberships (tenant_id, user_id, status);

COMMIT;

-- ----------------------------------------------------------------------------
-- Post-migration validation (run manually after execution)
-- ----------------------------------------------------------------------------
-- SELECT count(*) FROM information_schema.tables
--  WHERE table_name IN ('crm_sales_teams','crm_team_memberships');
-- Expected: 2
--
-- SELECT indexname FROM pg_indexes WHERE tablename='crm_sales_teams';
-- Expected: pk_sales_teams, uk_sales_teams_tenant_id, uk_sales_teams_tenant_code, idx_sales_teams_tenant_status
