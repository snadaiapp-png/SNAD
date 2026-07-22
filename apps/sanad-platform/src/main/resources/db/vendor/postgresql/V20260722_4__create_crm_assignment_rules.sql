-- ============================================================
-- SNAD Platform — CRM-008B Foundation — V20260722.4
-- Create CRM Assignment Rules + Rule Versions
-- ------------------------------------------------------------
-- Forward-only. Fail-closed. Tenant-scoped. Composite FKs.
-- JSONB for match_conditions. Partial unique index for
-- single-active-rule-version invariant.
-- ============================================================

-- ============================================================
-- PRECONDITIONS
-- ============================================================
DO $precondition$
DECLARE
    target_table_count INTEGER;
    conflicting_index  INTEGER;
    failed_history     INTEGER;
    territories_present INTEGER;
BEGIN
    SELECT COUNT(*) INTO territories_present
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_territories';
    IF territories_present <> 1 THEN
        RAISE EXCEPTION 'V20260722.4 precondition failed: crm_territories must exist';
    END IF;

    SELECT COUNT(*)
      INTO target_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name IN ('crm_assignment_rules','crm_assignment_rule_versions');
    IF target_table_count <> 0 THEN
        RAISE EXCEPTION
            'V20260722.4 precondition failed: % of 2 target tables already exist (expected 0)',
            target_table_count;
    END IF;

    SELECT COUNT(*)
      INTO conflicting_index
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname IN (
           'uk_assignment_rules_tenant_id','uk_assignment_rules_tenant_code',
           'uk_rule_versions_tenant_rule_version','uk_rule_versions_active',
           'idx_rule_versions_tenant_status_priority'
       );
    IF conflicting_index <> 0 THEN
        RAISE EXCEPTION
            'V20260722.4 precondition failed: % conflicting indexes already exist',
            conflicting_index;
    END IF;

    SELECT COUNT(*)
      INTO failed_history
      FROM flyway_schema_history
     WHERE success = FALSE;
    IF failed_history > 0 THEN
        RAISE EXCEPTION
            'V20260722.4 refuses to apply over % failed Flyway history rows',
            failed_history;
    END IF;
END
$precondition$;

-- ============================================================
-- DDL
-- ============================================================
CREATE TABLE crm_assignment_rules (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    code            VARCHAR(64) NOT NULL,
    current_version INTEGER NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT pk_assignment_rules PRIMARY KEY (id),
    CONSTRAINT uk_assignment_rules_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_assignment_rules_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_assignment_rules_status CHECK (status IN ('ACTIVE','INACTIVE','DEPRECATED')),
    CONSTRAINT ck_assignment_rules_current_version CHECK (current_version >= 1)
);

CREATE UNIQUE INDEX uk_assignment_rules_tenant_code
    ON crm_assignment_rules (tenant_id, code);

CREATE TABLE crm_assignment_rule_versions (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
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
    effective_from          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to            TIMESTAMP WITH TIME ZONE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by              UUID,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    )),
    CONSTRAINT ck_rule_versions_version CHECK (version >= 1),
    CONSTRAINT ck_rule_versions_priority CHECK (priority >= 0 AND priority <= 10000),
    CONSTRAINT ck_rule_versions_match_conditions CHECK (jsonb_typeof(match_conditions) = 'object'),
    CONSTRAINT ck_rule_versions_dates CHECK (effective_to IS NULL OR effective_from <= effective_to)
);

CREATE UNIQUE INDEX uk_rule_versions_tenant_rule_version
    ON crm_assignment_rule_versions (tenant_id, rule_id, version);

-- Single ACTIVE version per rule per tenant
CREATE UNIQUE INDEX uk_rule_versions_active
    ON crm_assignment_rule_versions (tenant_id, rule_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_rule_versions_tenant_status_priority
    ON crm_assignment_rule_versions (tenant_id, status, record_type, priority, effective_from);

-- ============================================================
-- POSTCONDITIONS
-- ============================================================
DO $postcondition$
DECLARE
    rules_table_exists       INTEGER;
    versions_table_exists    INTEGER;
    match_conditions_type    TEXT;
    active_index_predicate   TEXT;
    expected_indexes         INTEGER;
BEGIN
    SELECT COUNT(*) INTO rules_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_assignment_rules';
    IF rules_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.4 postcondition failed: crm_assignment_rules not created';
    END IF;

    SELECT COUNT(*) INTO versions_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_assignment_rule_versions';
    IF versions_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.4 postcondition failed: crm_assignment_rule_versions not created';
    END IF;

    -- match_conditions must be JSONB NOT NULL
    PERFORM 1
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_assignment_rule_versions'
       AND column_name = 'match_conditions'
       AND udt_name = 'jsonb'
       AND is_nullable = 'NO';
    IF NOT FOUND THEN
        RAISE EXCEPTION
            'V20260722.4 postcondition failed: crm_assignment_rule_versions.match_conditions is not JSONB NOT NULL';
    END IF;

    SELECT indexdef INTO active_index_predicate
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_assignment_rule_versions'
       AND indexname = 'uk_rule_versions_active';
    IF active_index_predicate IS NULL
       OR active_index_predicate NOT LIKE '%WHERE (status = ''ACTIVE''::character varying)%' THEN
        RAISE EXCEPTION
            'V20260722.4 postcondition failed: uk_rule_versions_active predicate missing or wrong: %',
            active_index_predicate;
    END IF;

    SELECT COUNT(*) INTO expected_indexes
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename IN ('crm_assignment_rules','crm_assignment_rule_versions')
       AND indexname IN (
           'uk_assignment_rules_tenant_id','uk_assignment_rules_tenant_code',
           'uk_rule_versions_tenant_rule_version','uk_rule_versions_active',
           'idx_rule_versions_tenant_status_priority'
       );
    IF expected_indexes <> 5 THEN
        RAISE EXCEPTION
            'V20260722.4 postcondition failed: expected 5 indexes, found %',
            expected_indexes;
    END IF;
END
$postcondition$;
