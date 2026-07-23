-- ============================================================
-- SNAD Platform — CRM-008B Foundation — V20260722.9
-- Create CRM Assignment Rule Counters
-- ------------------------------------------------------------
-- Forward-only. Fail-closed. Tenant-scoped. Composite FKs.
-- Round-robin counter table: per (tenant_id, rule_id).
-- ============================================================

-- ============================================================
-- PRECONDITIONS
-- ============================================================
DO $precondition$
DECLARE
    target_table_count INTEGER;
    conflicting_index  INTEGER;
    failed_history     INTEGER;
    rules_present      INTEGER;
BEGIN
    SELECT COUNT(*) INTO rules_present
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_assignment_rules';
    IF rules_present <> 1 THEN
        RAISE EXCEPTION 'V20260722.9 precondition failed: crm_assignment_rules must exist';
    END IF;

    SELECT COUNT(*)
      INTO target_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name = 'crm_assignment_rule_counters';
    IF target_table_count <> 0 THEN
        RAISE EXCEPTION
            'V20260722.9 precondition failed: crm_assignment_rule_counters already exists';
    END IF;

    SELECT COUNT(*)
      INTO conflicting_index
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname IN (
           'uk_assignment_rule_counters_tenant_id',
           'uk_assignment_rule_counters_tenant_rule',
           'idx_assignment_rule_counters_tenant_rule'
       );
    IF conflicting_index <> 0 THEN
        RAISE EXCEPTION
            'V20260722.9 precondition failed: % conflicting indexes already exist',
            conflicting_index;
    END IF;

    SELECT COUNT(*)
      INTO failed_history
      FROM flyway_schema_history
     WHERE success = FALSE;
    IF failed_history > 0 THEN
        RAISE EXCEPTION
            'V20260722.9 refuses to apply over % failed Flyway history rows',
            failed_history;
    END IF;
END
$precondition$;

-- ============================================================
-- DDL
-- ============================================================
CREATE TABLE crm_assignment_rule_counters (
    id          UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    rule_id     UUID NOT NULL,
    counter     BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_assignment_rule_counters PRIMARY KEY (id),
    CONSTRAINT uk_assignment_rule_counters_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_assignment_rule_counters_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_rule_counters_rules FOREIGN KEY (tenant_id, rule_id)
        REFERENCES crm_assignment_rules(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_assignment_rule_counters_non_negative CHECK (counter >= 0)
);

-- One counter row per (tenant, rule)
CREATE UNIQUE INDEX uk_assignment_rule_counters_tenant_rule
    ON crm_assignment_rule_counters (tenant_id, rule_id);

-- Tenant-leading index for counter lookups
CREATE INDEX idx_assignment_rule_counters_tenant_rule
    ON crm_assignment_rule_counters (tenant_id, rule_id, counter);

-- ============================================================
-- POSTCONDITIONS
-- ============================================================
DO $postcondition$
DECLARE
    table_exists      INTEGER;
    expected_indexes  INTEGER;
    counter_type      TEXT;
BEGIN
    SELECT COUNT(*) INTO table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_assignment_rule_counters';
    IF table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.9 postcondition failed: crm_assignment_rule_counters not created';
    END IF;

    -- counter must be BIGINT NOT NULL DEFAULT 0
    SELECT data_type INTO counter_type
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_assignment_rule_counters'
       AND column_name = 'counter';
    IF counter_type IS NULL OR counter_type <> 'bigint' THEN
        RAISE EXCEPTION
            'V20260722.9 postcondition failed: crm_assignment_rule_counters.counter data_type=% (expected bigint)',
            counter_type;
    END IF;

    SELECT COUNT(*) INTO expected_indexes
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_assignment_rule_counters'
       AND indexname IN (
           'uk_assignment_rule_counters_tenant_id',
           'uk_assignment_rule_counters_tenant_rule',
           'idx_assignment_rule_counters_tenant_rule'
       );
    IF expected_indexes <> 3 THEN
        RAISE EXCEPTION
            'V20260722.9 postcondition failed: expected 3 indexes, found %',
            expected_indexes;
    END IF;
END
$postcondition$;
