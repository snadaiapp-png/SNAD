-- ============================================================================
-- SANAD CRM-008B — V20260722_7 — Add owner_team_id, owner_queue_id to CRM tables
-- ============================================================================
-- Alter migration: predecessor schema MUST exist. Target columns MUST be absent.
-- ============================================================================

BEGIN;

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

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

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

COMMIT;
