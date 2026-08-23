-- ============================================================
-- SNAD Platform — CRM Contacts — FORCE RLS + fail-closed tenant policy
-- ------------------------------------------------------------
-- Task C2 of the CRM Contacts Collaboration Integration plan.
--
-- Contract (mirrors V20260822_2 pattern for crm_entity_participants /
-- crm_event_outbox / crm_timeline_events):
--
--   * ENABLE + FORCE ROW LEVEL SECURITY on crm_contacts — the table
--     owner (Flyway / migration role, normally `sanad`) is NOT
--     exempted. Without FORCE the owner bypasses RLS and sees every
--     row regardless of the GUC.
--
--   * Fail-closed USING / WITH CHECK clauses:
--       tenant_id = current_setting('app.tenant_id', true)::UUID
--     When the GUC is unset (NULL) the comparison evaluates to NULL,
--     which PostgreSQL treats as false. All rows are hidden and all
--     writes are rejected. This is the opposite of the legacy
--     V20260730_1 permissive-when-unset policy.
--
--   * The legacy permissive-when-unset policy named "tenant_isolation"
--     (created by V20260730_1's DO loop) is REPLACED by a conforming
--     policy named "crm_contacts_tenant_isolation". DROP POLICY is
--     mandatory: PostgreSQL OR-combines permissive policies on the
--     same table, so leaving the legacy policy in place would
--     re-enable the silent-skip window when the GUC is unset.
--
-- The GUC is set per transaction by TenantRlsTransactionContext (Java)
-- for trusted service paths and by raw SET LOCAL in integration tests.
-- ============================================================

ALTER TABLE crm_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE crm_contacts FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON crm_contacts;
DROP POLICY IF EXISTS crm_contacts_tenant_isolation ON crm_contacts;

CREATE POLICY crm_contacts_tenant_isolation ON crm_contacts
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);
