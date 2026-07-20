-- ============================================================================
-- SANAD CRM-008 — V20260720_7 — Add owner_team_id, owner_queue_id to CRM tables
-- ============================================================================
-- Forward-only. Idempotent (ADD COLUMN IF NOT EXISTS).
-- Adds two nullable columns to existing CRM tables so the assignment engine
-- can record team or queue ownership without breaking the existing
-- owner_user_id fast-path.
--
-- The columns are nullable because legacy records (created before CRM-008)
-- will only have owner_user_id populated.
--
-- REVIEW ONLY — DO NOT EXECUTE in this design phase.
-- ============================================================================

BEGIN;

-- crm_accounts
ALTER TABLE crm_accounts
    ADD COLUMN IF NOT EXISTS owner_team_id UUID,
    ADD COLUMN IF NOT EXISTS owner_queue_id UUID;

CREATE INDEX IF NOT EXISTS idx_crm_accounts_tenant_owner_team
    ON crm_accounts (tenant_id, owner_team_id)
    WHERE owner_team_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_crm_accounts_tenant_owner_queue
    ON crm_accounts (tenant_id, owner_queue_id)
    WHERE owner_queue_id IS NOT NULL;

-- crm_contacts
ALTER TABLE crm_contacts
    ADD COLUMN IF NOT EXISTS owner_team_id UUID,
    ADD COLUMN IF NOT EXISTS owner_queue_id UUID;

CREATE INDEX IF NOT EXISTS idx_crm_contacts_tenant_owner_team
    ON crm_contacts (tenant_id, owner_team_id)
    WHERE owner_team_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_crm_contacts_tenant_owner_queue
    ON crm_contacts (tenant_id, owner_queue_id)
    WHERE owner_queue_id IS NOT NULL;

-- crm_leads
ALTER TABLE crm_leads
    ADD COLUMN IF NOT EXISTS owner_team_id UUID,
    ADD COLUMN IF NOT EXISTS owner_queue_id UUID;

CREATE INDEX IF NOT EXISTS idx_crm_leads_tenant_owner_team
    ON crm_leads (tenant_id, owner_team_id)
    WHERE owner_team_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_crm_leads_tenant_owner_queue
    ON crm_leads (tenant_id, owner_queue_id)
    WHERE owner_queue_id IS NOT NULL;

-- crm_opportunities
ALTER TABLE crm_opportunities
    ADD COLUMN IF NOT EXISTS owner_team_id UUID,
    ADD COLUMN IF NOT EXISTS owner_queue_id UUID;

CREATE INDEX IF NOT EXISTS idx_crm_opportunities_tenant_owner_team
    ON crm_opportunities (tenant_id, owner_team_id)
    WHERE owner_team_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_crm_opportunities_tenant_owner_queue
    ON crm_opportunities (tenant_id, owner_queue_id)
    WHERE owner_queue_id IS NOT NULL;

-- crm_activities
ALTER TABLE crm_activities
    ADD COLUMN IF NOT EXISTS owner_team_id UUID,
    ADD COLUMN IF NOT EXISTS owner_queue_id UUID;

CREATE INDEX IF NOT EXISTS idx_crm_activities_tenant_owner_team
    ON crm_activities (tenant_id, owner_team_id)
    WHERE owner_team_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_crm_activities_tenant_owner_queue
    ON crm_activities (tenant_id, owner_queue_id)
    WHERE owner_queue_id IS NOT NULL;

-- crm_tasks
ALTER TABLE crm_tasks
    ADD COLUMN IF NOT EXISTS owner_team_id UUID,
    ADD COLUMN IF NOT EXISTS owner_queue_id UUID;

CREATE INDEX IF NOT EXISTS idx_crm_tasks_tenant_owner_team
    ON crm_tasks (tenant_id, owner_team_id)
    WHERE owner_team_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_crm_tasks_tenant_owner_queue
    ON crm_tasks (tenant_id, owner_queue_id)
    WHERE owner_queue_id IS NOT NULL;

COMMIT;

-- Validation:
-- SELECT column_name FROM information_schema.columns
--  WHERE table_name IN ('crm_accounts','crm_contacts','crm_leads',
--                       'crm_opportunities','crm_activities','crm_tasks')
--    AND column_name IN ('owner_team_id','owner_queue_id');
-- Expected: 12 rows (2 columns × 6 tables)
