-- ============================================================
-- SNAD Platform — CRM-008B Foundation — V20260722.1
-- Create CRM Sales Teams + Memberships
-- ------------------------------------------------------------
-- Forward-only. Fail-closed. Tenant-scoped. Composite FKs.
-- JSONB for structured metadata. Partial unique indexes for
-- single-active and single-primary invariants.
-- ============================================================

-- ============================================================
-- PRECONDITIONS — refuse partial / drift state
-- ============================================================
DO $precondition$
DECLARE
    g1_table_count     INTEGER;
    target_table_count INTEGER;
    conflicting_index  INTEGER;
    failed_history     INTEGER;
BEGIN
    -- CRM-G1 must be fully present (8 tables) OR fully absent (0 tables).
    SELECT COUNT(*)
      INTO g1_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name IN (
           'crm_tasks','crm_assignments','crm_transfers','crm_notes',
           'crm_audit_logs','crm_reports','crm_phone_numbers','crm_contact_lookup_index'
       );
    IF g1_table_count NOT IN (0, 8) THEN
        RAISE EXCEPTION
            'V20260722.1 refuses partial CRM-G1 state: found % of 8 G1 tables',
            g1_table_count;
    END IF;

    -- All target tables for this migration MUST be absent.
    SELECT COUNT(*)
      INTO target_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name IN ('crm_sales_teams','crm_team_memberships');
    IF target_table_count <> 0 THEN
        RAISE EXCEPTION
            'V20260722.1 precondition failed: % of 2 target tables already exist (expected 0)',
            target_table_count;
    END IF;

    -- No conflicting index names may exist.
    SELECT COUNT(*)
      INTO conflicting_index
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname IN (
           'uk_sales_teams_tenant_id','uk_sales_teams_tenant_code',
           'idx_sales_teams_tenant_status',
           'uk_team_memberships_tenant_id',
           'uk_team_memberships_active','uk_team_memberships_primary',
           'idx_team_memberships_team_status','idx_team_memberships_user_status'
       );
    IF conflicting_index <> 0 THEN
        RAISE EXCEPTION
            'V20260722.1 precondition failed: % conflicting indexes already exist',
            conflicting_index;
    END IF;

    -- Refuse to apply if any prior Flyway migration failed.
    SELECT COUNT(*)
      INTO failed_history
      FROM flyway_schema_history
     WHERE success = FALSE;
    IF failed_history > 0 THEN
        RAISE EXCEPTION
            'V20260722.1 refuses to apply over % failed Flyway history rows',
            failed_history;
    END IF;
END
$precondition$;

-- ============================================================
-- DDL — create tables, indexes, constraints
-- ============================================================
CREATE TABLE crm_sales_teams (
    id                   UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    code                 VARCHAR(64) NOT NULL,
    display_name         VARCHAR(200) NOT NULL,
    description          VARCHAR(1000),
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    manager_user_id      UUID,
    default_queue_id     UUID,
    default_territory_id UUID,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    team_id         UUID NOT NULL,
    user_id         UUID NOT NULL,
    role            VARCHAR(40) NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at         TIMESTAMP WITH TIME ZONE,
    left_reason     VARCHAR(100),
    capacity_max    INTEGER NOT NULL DEFAULT 50,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    CONSTRAINT ck_team_memberships_capacity CHECK (capacity_max >= 0 AND capacity_max <= 1000),
    CONSTRAINT ck_team_memberships_metadata CHECK (jsonb_typeof(metadata) = 'object')
);

-- Single active membership per (tenant, team, user)
CREATE UNIQUE INDEX uk_team_memberships_active
    ON crm_team_memberships (tenant_id, team_id, user_id)
    WHERE status = 'ACTIVE';

-- Single primary team per (tenant, user) among active memberships
CREATE UNIQUE INDEX uk_team_memberships_primary
    ON crm_team_memberships (tenant_id, user_id)
    WHERE status = 'ACTIVE' AND is_primary = TRUE;

CREATE INDEX idx_team_memberships_team_status
    ON crm_team_memberships (tenant_id, team_id, status, joined_at DESC);

CREATE INDEX idx_team_memberships_user_status
    ON crm_team_memberships (tenant_id, user_id, status, joined_at DESC);

-- ============================================================
-- POSTCONDITIONS — verify exact schema, types, predicates
-- ============================================================
DO $postcondition$
DECLARE
    team_table_exists        INTEGER;
    membership_table_exists  INTEGER;
    metadata_type            TEXT;
    metadata_nullable        TEXT;
    active_index_predicate   TEXT;
    primary_index_predicate  TEXT;
    expected_indexes         INTEGER;
BEGIN
    SELECT COUNT(*) INTO team_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_sales_teams';
    IF team_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.1 postcondition failed: crm_sales_teams not created';
    END IF;

    SELECT COUNT(*) INTO membership_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_team_memberships';
    IF membership_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.1 postcondition failed: crm_team_memberships not created';
    END IF;

    -- metadata column must be JSONB NOT NULL
    SELECT data_type, is_nullable INTO metadata_type, metadata_nullable
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_team_memberships'
       AND column_name = 'metadata';
    IF metadata_type IS NULL OR metadata_type <> 'USER-DEFINED' THEN
        RAISE EXCEPTION
            'V20260722.1 postcondition failed: crm_team_memberships.metadata data_type=% (expected USER-DEFINED/jsonb)',
            metadata_type;
    END IF;
    IF metadata_nullable <> 'NO' THEN
        RAISE EXCEPTION
            'V20260722.1 postcondition failed: crm_team_memberships.metadata is_nullable=% (expected NO)',
            metadata_nullable;
    END IF;

    -- Verify udt_name = jsonb
    PERFORM 1
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_team_memberships'
       AND column_name = 'metadata'
       AND udt_name = 'jsonb';
    IF NOT FOUND THEN
        RAISE EXCEPTION
            'V20260722.1 postcondition failed: crm_team_memberships.metadata udt_name is not jsonb';
    END IF;

    -- Verify partial unique index predicates
    SELECT indexdef INTO active_index_predicate
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_team_memberships'
       AND indexname = 'uk_team_memberships_active';
    IF active_index_predicate IS NULL
       OR active_index_predicate NOT LIKE '%WHERE (status = ''ACTIVE''::character varying)%' THEN
        RAISE EXCEPTION
            'V20260722.1 postcondition failed: uk_team_memberships_active predicate missing or wrong: %',
            active_index_predicate;
    END IF;

    SELECT indexdef INTO primary_index_predicate
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_team_memberships'
       AND indexname = 'uk_team_memberships_primary';
    IF primary_index_predicate IS NULL
       OR primary_index_predicate NOT LIKE '%status = ''ACTIVE''%is_primary = true%' THEN
        RAISE EXCEPTION
            'V20260722.1 postcondition failed: uk_team_memberships_primary predicate missing or wrong: %',
            primary_index_predicate;
    END IF;

    -- All 6 expected indexes present
    SELECT COUNT(*) INTO expected_indexes
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename IN ('crm_sales_teams','crm_team_memberships')
       AND indexname IN (
           'uk_sales_teams_tenant_id','uk_sales_teams_tenant_code',
           'idx_sales_teams_tenant_status',
           'uk_team_memberships_active','uk_team_memberships_primary',
           'idx_team_memberships_team_status','idx_team_memberships_user_status'
       );
    IF expected_indexes <> 7 THEN
        RAISE EXCEPTION
            'V20260722.1 postcondition failed: expected 7 indexes, found %',
            expected_indexes;
    END IF;
END
$postcondition$;
