-- G7: Add change tracking columns to CRM entity tables
-- Requirements: DATA-002 (Change Tracking)
-- Date: 2026-08-12
-- Adds last_synced_at and sync_version to all 7 syncable entity types

-- ============================================================
-- crm_accounts
-- ============================================================
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_accounts ADD COLUMN IF NOT EXISTS sync_version BIGINT NOT NULL DEFAULT 0;

-- ============================================================
-- crm_contacts
-- ============================================================
ALTER TABLE crm_contacts ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_contacts ADD COLUMN IF NOT EXISTS sync_version BIGINT NOT NULL DEFAULT 0;

-- ============================================================
-- crm_leads
-- ============================================================
ALTER TABLE crm_leads ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_leads ADD COLUMN IF NOT EXISTS sync_version BIGINT NOT NULL DEFAULT 0;

-- ============================================================
-- crm_opportunities
-- ============================================================
ALTER TABLE crm_opportunities ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_opportunities ADD COLUMN IF NOT EXISTS sync_version BIGINT NOT NULL DEFAULT 0;

-- ============================================================
-- crm_tasks
-- ============================================================
ALTER TABLE crm_tasks ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_tasks ADD COLUMN IF NOT EXISTS sync_version BIGINT NOT NULL DEFAULT 0;

-- ============================================================
-- crm_notes
-- ============================================================
ALTER TABLE crm_notes ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_notes ADD COLUMN IF NOT EXISTS sync_version BIGINT NOT NULL DEFAULT 0;

-- ============================================================
-- crm_activities (if table exists — check structure)
-- ============================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'crm_activities') THEN
        ALTER TABLE crm_activities ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP WITH TIME ZONE;
        ALTER TABLE crm_activities ADD COLUMN IF NOT EXISTS sync_version BIGINT NOT NULL DEFAULT 0;
    END IF;
END $$;

-- ============================================================
-- Indexes for sync queries (delta pull: WHERE sync_version > ?)
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_accounts_sync_version ON crm_accounts(sync_version);
CREATE INDEX IF NOT EXISTS idx_contacts_sync_version ON crm_contacts(sync_version);
CREATE INDEX IF NOT EXISTS idx_leads_sync_version ON crm_leads(sync_version);
CREATE INDEX IF NOT EXISTS idx_opportunities_sync_version ON crm_opportunities(sync_version);
CREATE INDEX IF NOT EXISTS idx_tasks_sync_version ON crm_tasks(sync_version);
CREATE INDEX IF NOT EXISTS idx_notes_sync_version ON crm_notes(sync_version);

-- ============================================================
-- Trigger: Auto-increment sync_version on UPDATE
-- ============================================================
CREATE OR REPLACE FUNCTION fn_update_sync_version()
RETURNS TRIGGER AS $$
BEGIN
    NEW.sync_version = OLD.sync_version + 1;
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to all entity tables
CREATE TRIGGER trg_accounts_sync_version
    BEFORE UPDATE ON crm_accounts
    FOR EACH ROW EXECUTE FUNCTION fn_update_sync_version();

CREATE TRIGGER trg_contacts_sync_version
    BEFORE UPDATE ON crm_contacts
    FOR EACH ROW EXECUTE FUNCTION fn_update_sync_version();

CREATE TRIGGER trg_leads_sync_version
    BEFORE UPDATE ON crm_leads
    FOR EACH ROW EXECUTE FUNCTION fn_update_sync_version();

CREATE TRIGGER trg_opportunities_sync_version
    BEFORE UPDATE ON crm_opportunities
    FOR EACH ROW EXECUTE FUNCTION fn_update_sync_version();

CREATE TRIGGER trg_tasks_sync_version
    BEFORE UPDATE ON crm_tasks
    FOR EACH ROW EXECUTE FUNCTION fn_update_sync_version();

CREATE TRIGGER trg_notes_sync_version
    BEFORE UPDATE ON crm_notes
    FOR EACH ROW EXECUTE FUNCTION fn_update_sync_version();
