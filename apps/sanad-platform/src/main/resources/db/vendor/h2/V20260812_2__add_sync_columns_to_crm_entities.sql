-- G7: Add change tracking columns to CRM entity tables (H2 variant)
-- Requirements: DATA-002 (Change Tracking)
-- Date: 2026-08-12
-- Adds last_synced_at and sync_version to all 7 syncable entity types
--
-- This is the H2/local-profile variant of V20260812_2. The PostgreSQL
-- original lives under db/vendor/postgresql/ and uses PL/pgSQL triggers
-- and DO $$ BEGIN ... END $$ blocks which H2 does not support. The H2
-- variant omits the trigger (H2 does not support CREATE TRIGGER with
-- PL/pgSQL function bodies) — the sync_version increment is handled
-- at the application layer for local tests. The column additions and
-- indexes are preserved because H2 supports those constructs.

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
-- crm_activities (H2 does not support DO $$ BEGIN ... END $$ blocks)
-- ============================================================
-- The PostgreSQL variant uses a conditional DO block to add columns
-- only if crm_activities exists. H2 does not support PL/pgSQL DO blocks.
-- crm_activities is created by V20260723_1 (CRM core) which always runs
-- before this migration, so the table is guaranteed to exist. Add the
-- columns directly.
ALTER TABLE crm_activities ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_activities ADD COLUMN IF NOT EXISTS sync_version BIGINT NOT NULL DEFAULT 0;

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
-- Trigger omitted on H2
-- ============================================================
-- The PostgreSQL variant creates a PL/pgSQL trigger function
-- fn_update_sync_version() and attaches it as a BEFORE UPDATE trigger
-- to each entity table. H2 does not support CREATE TRIGGER with
-- PL/pgSQL function bodies. The sync_version increment is handled at
-- the application layer for local/H2 tests. PostgreSQL remains the
-- authoritative acceptance database for trigger behaviour.
