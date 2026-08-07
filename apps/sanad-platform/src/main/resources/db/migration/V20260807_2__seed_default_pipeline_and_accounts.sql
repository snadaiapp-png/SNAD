-- V20260807_2: Seed default pipeline and sample accounts for new tenants
-- Fixes B-05 (no seed data for pipelines/accounts)
--
-- Idempotent: uses WHERE NOT EXISTS to prevent duplicate inserts.
-- Portability: portable SQL that works on both PostgreSQL 16 and H2 (PostgreSQL mode).
--   - No CROSS JOIN (VALUES ...) — uses individual INSERT statements
--   - Each INSERT provides all NOT NULL columns required by the schema
--   - gen_random_uuid() supported by both PostgreSQL and H2 (MODE=PostgreSQL)
--   - CURRENT_TIMESTAMP supported by both
--
-- Audit columns: crm_pipeline_stages is missing created_at/updated_at
-- (table created in V20260702_1 without them). ALTER TABLE adds them first.

-- ============================================================
-- Ensure audit columns exist on crm_pipeline_stages
-- ============================================================
ALTER TABLE crm_pipeline_stages ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_pipeline_stages ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

-- ============================================================
-- Default Sales Pipeline (one per ACTIVE tenant)
-- ============================================================
INSERT INTO crm_pipelines (id, tenant_id, name, currency_code, active, created_by, created_at, updated_at)
SELECT gen_random_uuid(), t.id, 'Sales Pipeline', 'SAR', true,
       t.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM crm_pipelines p
    WHERE p.tenant_id = t.id AND p.name = 'Sales Pipeline'
);

-- ============================================================
-- Pipeline Stages (one set per pipeline named 'Sales Pipeline')
-- ============================================================
INSERT INTO crm_pipeline_stages (id, pipeline_id, tenant_id, name, sequence, probability, terminal_state, active, created_at, updated_at)
SELECT gen_random_uuid(), p.id, p.tenant_id, 'Qualification', 1, 10.00, NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM crm_pipelines p WHERE p.name = 'Sales Pipeline'
AND NOT EXISTS (SELECT 1 FROM crm_pipeline_stages s WHERE s.pipeline_id = p.id AND s.name = 'Qualification');

INSERT INTO crm_pipeline_stages (id, pipeline_id, tenant_id, name, sequence, probability, terminal_state, active, created_at, updated_at)
SELECT gen_random_uuid(), p.id, p.tenant_id, 'Proposal', 2, 40.00, NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM crm_pipelines p WHERE p.name = 'Sales Pipeline'
AND NOT EXISTS (SELECT 1 FROM crm_pipeline_stages s WHERE s.pipeline_id = p.id AND s.name = 'Proposal');

INSERT INTO crm_pipeline_stages (id, pipeline_id, tenant_id, name, sequence, probability, terminal_state, active, created_at, updated_at)
SELECT gen_random_uuid(), p.id, p.tenant_id, 'Negotiation', 3, 70.00, NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM crm_pipelines p WHERE p.name = 'Sales Pipeline'
AND NOT EXISTS (SELECT 1 FROM crm_pipeline_stages s WHERE s.pipeline_id = p.id AND s.name = 'Negotiation');

INSERT INTO crm_pipeline_stages (id, pipeline_id, tenant_id, name, sequence, probability, terminal_state, active, created_at, updated_at)
SELECT gen_random_uuid(), p.id, p.tenant_id, 'Closed Won', 4, 100.00, 'WON', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM crm_pipelines p WHERE p.name = 'Sales Pipeline'
AND NOT EXISTS (SELECT 1 FROM crm_pipeline_stages s WHERE s.pipeline_id = p.id AND s.name = 'Closed Won');

INSERT INTO crm_pipeline_stages (id, pipeline_id, tenant_id, name, sequence, probability, terminal_state, active, created_at, updated_at)
SELECT gen_random_uuid(), p.id, p.tenant_id, 'Closed Lost', 5, 0.00, 'LOST', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM crm_pipelines p WHERE p.name = 'Sales Pipeline'
AND NOT EXISTS (SELECT 1 FROM crm_pipeline_stages s WHERE s.pipeline_id = p.id AND s.name = 'Closed Lost');

-- ============================================================
-- Sample Accounts (2 per ACTIVE tenant)
-- ============================================================
INSERT INTO crm_accounts (id, tenant_id, version, display_name, normalized_name, account_type, lifecycle_status, created_by, updated_by, created_at, updated_at)
SELECT gen_random_uuid(), t.id, 1, 'Sample Customer Co.', 'sample customer co.', 'BUSINESS', 'ACTIVE',
       t.id, t.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM crm_accounts a
    WHERE a.tenant_id = t.id AND a.display_name = 'Sample Customer Co.'
);

INSERT INTO crm_accounts (id, tenant_id, version, display_name, normalized_name, account_type, lifecycle_status, created_by, updated_by, created_at, updated_at)
SELECT gen_random_uuid(), t.id, 1, 'Demo Partner Ltd.', 'demo partner ltd.', 'PARTNER', 'ACTIVE',
       t.id, t.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM crm_accounts a
    WHERE a.tenant_id = t.id AND a.display_name = 'Demo Partner Ltd.'
);
