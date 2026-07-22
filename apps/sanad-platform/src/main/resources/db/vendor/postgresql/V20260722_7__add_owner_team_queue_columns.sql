-- ============================================================
-- SNAD Platform — CRM-008B Foundation — V20260722.7
-- Add owner_team_id, owner_queue_id to 6 CRM record tables
-- ------------------------------------------------------------
-- ALTER migration: predecessor schema (crm_accounts, crm_contacts,
-- crm_leads, crm_opportunities, crm_activities, crm_tasks) MUST exist.
-- All target columns MUST be absent. All target indexes MUST be absent.
-- ============================================================

-- ============================================================
-- PRECONDITIONS
-- ============================================================
DO $precondition$
DECLARE
    expected_predecessor_tables INTEGER;
    target_columns_count        INTEGER;
    conflicting_index           INTEGER;
    failed_history              INTEGER;
BEGIN
    -- All 6 predecessor tables must exist
    SELECT COUNT(*) INTO expected_predecessor_tables
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name IN (
           'crm_accounts','crm_contacts','crm_leads',
           'crm_opportunities','crm_activities','crm_tasks'
       );
    IF expected_predecessor_tables <> 6 THEN
        RAISE EXCEPTION
            'V20260722.7 precondition failed: % of 6 predecessor tables exist (expected 6)',
            expected_predecessor_tables;
    END IF;

    -- All target columns MUST be absent (no partial state)
    SELECT COUNT(*) INTO target_columns_count
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name IN (
           'crm_accounts','crm_contacts','crm_leads',
           'crm_opportunities','crm_activities','crm_tasks'
       )
       AND column_name IN ('owner_team_id','owner_queue_id');
    IF target_columns_count <> 0 THEN
        RAISE EXCEPTION
            'V20260722.7 precondition failed: % of 12 target columns already exist (expected 0) — partial state',
            target_columns_count;
    END IF;

    -- No conflicting index names
    SELECT COUNT(*) INTO conflicting_index
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname IN (
           'idx_owr_accounts_owner_team','idx_owr_accounts_owner_queue',
           'idx_owr_contacts_owner_team','idx_owr_contacts_owner_queue',
           'idx_owr_leads_owner_team','idx_owr_leads_owner_queue',
           'idx_owr_opportunities_owner_team','idx_owr_opportunities_owner_queue',
           'idx_owr_activities_owner_team','idx_owr_activities_owner_queue',
           'idx_owr_tasks_owner_team','idx_owr_tasks_owner_queue'
       );
    IF conflicting_index <> 0 THEN
        RAISE EXCEPTION
            'V20260722.7 precondition failed: % conflicting indexes already exist',
            conflicting_index;
    END IF;

    SELECT COUNT(*) INTO failed_history
      FROM flyway_schema_history
     WHERE success = FALSE;
    IF failed_history > 0 THEN
        RAISE EXCEPTION
            'V20260722.7 refuses to apply over % failed Flyway history rows',
            failed_history;
    END IF;
END
$precondition$;

-- ============================================================
-- DDL — ADD COLUMN + INDEXES on 6 tables
-- ============================================================
ALTER TABLE crm_accounts ADD COLUMN owner_team_id UUID;
ALTER TABLE crm_accounts ADD COLUMN owner_queue_id UUID;

ALTER TABLE crm_contacts ADD COLUMN owner_team_id UUID;
ALTER TABLE crm_contacts ADD COLUMN owner_queue_id UUID;

ALTER TABLE crm_leads ADD COLUMN owner_team_id UUID;
ALTER TABLE crm_leads ADD COLUMN owner_queue_id UUID;

ALTER TABLE crm_opportunities ADD COLUMN owner_team_id UUID;
ALTER TABLE crm_opportunities ADD COLUMN owner_queue_id UUID;

ALTER TABLE crm_activities ADD COLUMN owner_team_id UUID;
ALTER TABLE crm_activities ADD COLUMN owner_queue_id UUID;

ALTER TABLE crm_tasks ADD COLUMN owner_team_id UUID;
ALTER TABLE crm_tasks ADD COLUMN owner_queue_id UUID;

CREATE INDEX idx_owr_accounts_owner_team
    ON crm_accounts (tenant_id, owner_team_id);

CREATE INDEX idx_owr_accounts_owner_queue
    ON crm_accounts (tenant_id, owner_queue_id);

CREATE INDEX idx_owr_contacts_owner_team
    ON crm_contacts (tenant_id, owner_team_id);

CREATE INDEX idx_owr_contacts_owner_queue
    ON crm_contacts (tenant_id, owner_queue_id);

CREATE INDEX idx_owr_leads_owner_team
    ON crm_leads (tenant_id, owner_team_id);

CREATE INDEX idx_owr_leads_owner_queue
    ON crm_leads (tenant_id, owner_queue_id);

CREATE INDEX idx_owr_opportunities_owner_team
    ON crm_opportunities (tenant_id, owner_team_id);

CREATE INDEX idx_owr_opportunities_owner_queue
    ON crm_opportunities (tenant_id, owner_queue_id);

CREATE INDEX idx_owr_activities_owner_team
    ON crm_activities (tenant_id, owner_team_id);

CREATE INDEX idx_owr_activities_owner_queue
    ON crm_activities (tenant_id, owner_queue_id);

CREATE INDEX idx_owr_tasks_owner_team
    ON crm_tasks (tenant_id, owner_team_id);

CREATE INDEX idx_owr_tasks_owner_queue
    ON crm_tasks (tenant_id, owner_queue_id);

-- ============================================================
-- POSTCONDITIONS
-- ============================================================
DO $postcondition$
DECLARE
    actual_columns INTEGER;
    expected_indexes INTEGER;
BEGIN
    -- All 12 new columns (2 per 6 tables) must exist
    SELECT COUNT(*) INTO actual_columns
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name IN (
           'crm_accounts','crm_contacts','crm_leads',
           'crm_opportunities','crm_activities','crm_tasks'
       )
       AND column_name IN ('owner_team_id','owner_queue_id');
    IF actual_columns <> 12 THEN
        RAISE EXCEPTION
            'V20260722.7 postcondition failed: % of 12 target columns exist',
            actual_columns;
    END IF;

    -- All columns must be UUID type, nullable
    PERFORM 1
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name IN (
           'crm_accounts','crm_contacts','crm_leads',
           'crm_opportunities','crm_activities','crm_tasks'
       )
       AND column_name IN ('owner_team_id','owner_queue_id')
       AND udt_name <> 'uuid';
    IF FOUND THEN
        RAISE EXCEPTION
            'V20260722.7 postcondition failed: at least one owner column is not UUID type';
    END IF;

    -- All 12 expected indexes must exist
    SELECT COUNT(*) INTO expected_indexes
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname IN (
           'idx_owr_accounts_owner_team','idx_owr_accounts_owner_queue',
           'idx_owr_contacts_owner_team','idx_owr_contacts_owner_queue',
           'idx_owr_leads_owner_team','idx_owr_leads_owner_queue',
           'idx_owr_opportunities_owner_team','idx_owr_opportunities_owner_queue',
           'idx_owr_activities_owner_team','idx_owr_activities_owner_queue',
           'idx_owr_tasks_owner_team','idx_owr_tasks_owner_queue'
       );
    IF expected_indexes <> 12 THEN
        RAISE EXCEPTION
            'V20260722.7 postcondition failed: expected 12 indexes, found %',
            expected_indexes;
    END IF;
END
$postcondition$;
