-- G6 R4 — Canonical fail-closed tenant isolation for CRM idempotency records.
--
-- Historical migrations remain immutable. This migration reconciles both the
-- canonical source chain and environments that may still carry the legacy
-- generic tenant_isolation policy observed during production forensics.

ALTER TABLE crm_idempotency_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE crm_idempotency_records FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON crm_idempotency_records;
DROP POLICY IF EXISTS crm_idempotency_records_tenant_isolation ON crm_idempotency_records;

CREATE POLICY crm_idempotency_records_tenant_isolation
    ON crm_idempotency_records
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
    );
