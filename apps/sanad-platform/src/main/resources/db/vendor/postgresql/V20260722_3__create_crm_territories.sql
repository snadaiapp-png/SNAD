-- ============================================================
-- SNAD Platform — CRM-008B Foundation — V20260722.3
-- Create CRM Territories + Closure + Assignments
-- ------------------------------------------------------------
-- Forward-only. Fail-closed. Tenant-scoped. Composite FKs.
-- JSONB for rule_definition. Partial unique index for
-- single-active-assignment per territory.
-- Closure table for hierarchical territory queries.
-- ============================================================

-- ============================================================
-- PRECONDITIONS
-- ============================================================
DO $precondition$
DECLARE
    target_table_count INTEGER;
    conflicting_index  INTEGER;
    failed_history     INTEGER;
    teams_present      INTEGER;
    queues_present     INTEGER;
BEGIN
    SELECT COUNT(*) INTO teams_present
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_sales_teams';
    IF teams_present <> 1 THEN
        RAISE EXCEPTION 'V20260722.3 precondition failed: crm_sales_teams must exist';
    END IF;

    SELECT COUNT(*) INTO queues_present
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_queues';
    IF queues_present <> 1 THEN
        RAISE EXCEPTION 'V20260722.3 precondition failed: crm_queues must exist';
    END IF;

    SELECT COUNT(*)
      INTO target_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name IN ('crm_territories','crm_territory_closure','crm_territory_assignments');
    IF target_table_count <> 0 THEN
        RAISE EXCEPTION
            'V20260722.3 precondition failed: % of 3 target tables already exist (expected 0)',
            target_table_count;
    END IF;

    SELECT COUNT(*)
      INTO conflicting_index
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname IN (
           'uk_territories_tenant_id','uk_territories_tenant_code',
           'idx_territories_tenant_status','idx_territories_tenant_parent',
           'uk_territory_closure_triple','idx_territory_closure_descendant','idx_territory_closure_ancestor',
           'uk_territory_assignments_active',
           'idx_territory_assignments_territory_status','idx_territory_assignments_assignee'
       );
    IF conflicting_index <> 0 THEN
        RAISE EXCEPTION
            'V20260722.3 precondition failed: % conflicting indexes already exist',
            conflicting_index;
    END IF;

    SELECT COUNT(*)
      INTO failed_history
      FROM flyway_schema_history
     WHERE success = FALSE;
    IF failed_history > 0 THEN
        RAISE EXCEPTION
            'V20260722.3 refuses to apply over % failed Flyway history rows',
            failed_history;
    END IF;
END
$precondition$;

-- ============================================================
-- DDL
-- ============================================================
CREATE TABLE crm_territories (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    code            VARCHAR(64) NOT NULL,
    display_name    VARCHAR(200) NOT NULL,
    parent_id       UUID,
    description     VARCHAR(1000),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    rule_type       VARCHAR(20) NOT NULL,
    rule_definition JSONB NOT NULL DEFAULT '{}'::jsonb,
    priority        INTEGER NOT NULL DEFAULT 100,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    CONSTRAINT ck_territories_priority CHECK (priority >= 0 AND priority <= 10000),
    CONSTRAINT ck_territories_rule_definition CHECK (jsonb_typeof(rule_definition) = 'object')
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
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    territory_id    UUID NOT NULL,
    assignee_type   VARCHAR(10) NOT NULL,
    assignee_id     UUID NOT NULL,
    role            VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    priority        INTEGER NOT NULL DEFAULT 100,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    effective_from  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
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

-- Single active PRIMARY assignee per (tenant, territory, assignee_type)
CREATE UNIQUE INDEX uk_territory_assignments_active
    ON crm_territory_assignments (tenant_id, territory_id, assignee_type)
    WHERE status = 'ACTIVE' AND role = 'PRIMARY';

CREATE INDEX idx_territory_assignments_territory_status
    ON crm_territory_assignments (tenant_id, territory_id, status, priority);

CREATE INDEX idx_territory_assignments_assignee
    ON crm_territory_assignments (tenant_id, assignee_type, assignee_id, status);

-- ============================================================
-- POSTCONDITIONS
-- ============================================================
DO $postcondition$
DECLARE
    territory_table_exists  INTEGER;
    closure_table_exists    INTEGER;
    assign_table_exists     INTEGER;
    rule_def_type           TEXT;
    active_index_predicate  TEXT;
    expected_indexes        INTEGER;
BEGIN
    SELECT COUNT(*) INTO territory_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_territories';
    IF territory_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.3 postcondition failed: crm_territories not created';
    END IF;

    SELECT COUNT(*) INTO closure_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_territory_closure';
    IF closure_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.3 postcondition failed: crm_territory_closure not created';
    END IF;

    SELECT COUNT(*) INTO assign_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_territory_assignments';
    IF assign_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.3 postcondition failed: crm_territory_assignments not created';
    END IF;

    -- rule_definition must be JSONB NOT NULL
    PERFORM 1
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_territories'
       AND column_name = 'rule_definition'
       AND udt_name = 'jsonb'
       AND is_nullable = 'NO';
    IF NOT FOUND THEN
        RAISE EXCEPTION
            'V20260722.3 postcondition failed: crm_territories.rule_definition is not JSONB NOT NULL';
    END IF;

    SELECT indexdef INTO active_index_predicate
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_territory_assignments'
       AND indexname = 'uk_territory_assignments_active';
    IF active_index_predicate IS NULL
       OR active_index_predicate NOT LIKE '%status = ''ACTIVE''%'
       OR active_index_predicate NOT LIKE '%role = ''PRIMARY''%' THEN
        RAISE EXCEPTION
            'V20260722.3 postcondition failed: uk_territory_assignments_active predicate missing or wrong: %',
            active_index_predicate;
    END IF;

    SELECT COUNT(*) INTO expected_indexes
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename IN ('crm_territories','crm_territory_closure','crm_territory_assignments')
       AND indexname IN (
           'uk_territories_tenant_id','uk_territories_tenant_code',
           'idx_territories_tenant_status','idx_territories_tenant_parent',
           'uk_territory_closure_triple','idx_territory_closure_descendant','idx_territory_closure_ancestor',
           'uk_territory_assignments_active',
           'idx_territory_assignments_territory_status','idx_territory_assignments_assignee'
       );
    IF expected_indexes <> 10 THEN
        RAISE EXCEPTION
            'V20260722.3 postcondition failed: expected 10 indexes, found %',
            expected_indexes;
    END IF;
END
$postcondition$;
