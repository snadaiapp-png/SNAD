-- ============================================================
-- SNAD Platform — CRM-008B Foundation — V20260722.2
-- Create CRM Queues + Queue Memberships
-- ------------------------------------------------------------
-- Forward-only. Fail-closed. Tenant-scoped. Composite FKs.
-- Partial unique index for single-active-membership invariant.
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
BEGIN
    -- V20260722.1 must have been applied (crm_sales_teams exists).
    SELECT COUNT(*) INTO teams_present
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_sales_teams';
    IF teams_present <> 1 THEN
        RAISE EXCEPTION
            'V20260722.2 precondition failed: crm_sales_teams must exist (V20260722.1 not applied)';
    END IF;

    -- Target tables for this migration MUST be absent.
    SELECT COUNT(*)
      INTO target_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name IN ('crm_queues','crm_queue_memberships');
    IF target_table_count <> 0 THEN
        RAISE EXCEPTION
            'V20260722.2 precondition failed: % of 2 target tables already exist (expected 0)',
            target_table_count;
    END IF;

    SELECT COUNT(*)
      INTO conflicting_index
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname IN (
           'uk_queues_tenant_id','uk_queues_tenant_code',
           'idx_queues_tenant_status_type',
           'uk_queue_memberships_active',
           'idx_queue_memberships_user_active'
       );
    IF conflicting_index <> 0 THEN
        RAISE EXCEPTION
            'V20260722.2 precondition failed: % conflicting indexes already exist',
            conflicting_index;
    END IF;

    SELECT COUNT(*)
      INTO failed_history
      FROM flyway_schema_history
     WHERE success = FALSE;
    IF failed_history > 0 THEN
        RAISE EXCEPTION
            'V20260722.2 refuses to apply over % failed Flyway history rows',
            failed_history;
    END IF;
END
$precondition$;

-- ============================================================
-- DDL
-- ============================================================
CREATE TABLE crm_queues (
    id                          UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    code                        VARCHAR(64) NOT NULL,
    display_name                VARCHAR(200) NOT NULL,
    record_type                 VARCHAR(20) NOT NULL,
    description                 VARCHAR(1000),
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    max_items_per_user          INTEGER NOT NULL DEFAULT 10,
    sla_minutes                 INTEGER,
    escalation_target_queue_id  UUID,
    default_owner_id            UUID,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                  UUID,
    updated_by                  UUID,
    CONSTRAINT pk_queues PRIMARY KEY (id),
    CONSTRAINT uk_queues_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_queues_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_queues_status CHECK (status IN ('ACTIVE','DRAINING','ARCHIVED')),
    CONSTRAINT ck_queues_record_type CHECK (record_type IN (
        'LEAD','OPPORTUNITY','TASK','ACTIVITY','ACCOUNT'
    )),
    CONSTRAINT ck_queues_capacity CHECK (max_items_per_user >= 1 AND max_items_per_user <= 1000),
    CONSTRAINT ck_queues_no_self_escalation CHECK (escalation_target_queue_id IS NULL
        OR escalation_target_queue_id <> id)
);

CREATE UNIQUE INDEX uk_queues_tenant_code
    ON crm_queues (tenant_id, code);

CREATE INDEX idx_queues_tenant_status_type
    ON crm_queues (tenant_id, status, record_type);

CREATE TABLE crm_queue_memberships (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    queue_id        UUID NOT NULL,
    user_id         UUID NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    added_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at      TIMESTAMP WITH TIME ZONE,
    removed_reason  VARCHAR(100),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT pk_queue_memberships PRIMARY KEY (id),
    CONSTRAINT fk_queue_memberships_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_queue_memberships_queues FOREIGN KEY (tenant_id, queue_id)
        REFERENCES crm_queues(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_queue_memberships_status CHECK (status IN ('ACTIVE','REMOVED'))
);

-- Single active membership per (tenant, queue, user)
CREATE UNIQUE INDEX uk_queue_memberships_active
    ON crm_queue_memberships (tenant_id, queue_id, user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_queue_memberships_user_active
    ON crm_queue_memberships (tenant_id, user_id, status);

-- ============================================================
-- POSTCONDITIONS
-- ============================================================
DO $postcondition$
DECLARE
    queue_table_exists      INTEGER;
    membership_table_exists INTEGER;
    active_index_predicate  TEXT;
    expected_indexes        INTEGER;
BEGIN
    SELECT COUNT(*) INTO queue_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_queues';
    IF queue_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.2 postcondition failed: crm_queues not created';
    END IF;

    SELECT COUNT(*) INTO membership_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_queue_memberships';
    IF membership_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.2 postcondition failed: crm_queue_memberships not created';
    END IF;

    -- Verify uk_queue_memberships_active partial index predicate.
    -- Use pg_get_expr(pg_index.indpred, pg_index.indrelid) for stable semantic check.
    SELECT pg_get_expr(i.indpred, i.indrelid)
      INTO active_index_predicate
      FROM pg_index i
      JOIN pg_class c ON c.oid = i.indrelid
      JOIN pg_class ci ON ci.oid = i.indexrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'public'
       AND c.relname = 'crm_queue_memberships'
       AND ci.relname = 'uk_queue_memberships_active';
    IF active_index_predicate IS NULL THEN
        RAISE EXCEPTION
            'V20260722.2 postcondition failed: uk_queue_memberships_active predicate missing or wrong: %',
            active_index_predicate;
    END IF;
    IF position('status' in active_index_predicate) = 0
       OR position('ACTIVE' in active_index_predicate) = 0 THEN
        RAISE EXCEPTION
            'V20260722.2 postcondition failed: uk_queue_memberships_active predicate does not reference status/ACTIVE: %',
            active_index_predicate;
    END IF;

    SELECT COUNT(*) INTO expected_indexes
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename IN ('crm_queues','crm_queue_memberships')
       AND indexname IN (
           'uk_queues_tenant_id','uk_queues_tenant_code',
           'idx_queues_tenant_status_type',
           'uk_queue_memberships_active','idx_queue_memberships_user_active'
       );
    IF expected_indexes <> 5 THEN
        RAISE EXCEPTION
            'V20260722.2 postcondition failed: expected 5 indexes, found %',
            expected_indexes;
    END IF;
END
$postcondition$;
