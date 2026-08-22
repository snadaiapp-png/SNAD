-- ============================================================
-- SNAD Platform — CRM Collaboration & Event Foundation — RLS (Task 1)
-- ------------------------------------------------------------
-- Force fail-closed row-level security on the new collaboration
-- tables and reconcile the legacy crm_timeline_events policy with
-- the same fail-closed contract.
--
-- Contract (project pattern V20260812_1 + V20260820_12):
--   * ENABLE + FORCE ROW LEVEL SECURITY — the table owner (Flyway /
--     migration role) is NOT exempted.
--   * Fail-closed USING / WITH CHECK clauses:
--       tenant_id = current_setting('app.tenant_id', true)::UUID
--     When the GUC is unset (NULL), the comparison is NULL = false,
--     so all rows are hidden and writes are rejected. This is the
--     opposite of the legacy V20260730_1 permissive-when-unset policy
--     and intentionally closes the silent-skip window observed on
--     ModuleResetService.crossTenantReset.
--   * The GUC is set per transaction by TenantRlsTransactionContext
--     (Java) for trusted service paths and by raw SET LOCAL in
--     integration tests.
--
-- For crm_timeline_events the existing permissive-when-unset policy
-- named "tenant_isolation" (created by V20260730_1) is REPLACED by a
-- conforming policy named "crm_timeline_events_tenant_isolation".
-- FORCE RLS is applied so the table owner is also subject to the
-- policy (ModuleResetService preview/reset previously saw all rows).
-- ============================================================

-- ------------------------------------------------------------
-- crm_entity_participants — fail-closed + FORCE RLS
-- ------------------------------------------------------------
ALTER TABLE crm_entity_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE crm_entity_participants FORCE ROW LEVEL SECURITY;

CREATE POLICY crm_entity_participants_tenant_isolation ON crm_entity_participants
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- ------------------------------------------------------------
-- crm_event_outbox — fail-closed + FORCE RLS
-- ------------------------------------------------------------
ALTER TABLE crm_event_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE crm_event_outbox FORCE ROW LEVEL SECURITY;

CREATE POLICY crm_event_outbox_tenant_isolation ON crm_event_outbox
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- ------------------------------------------------------------
-- crm_timeline_events — FORCE RLS + replace legacy permissive policy
-- ------------------------------------------------------------
ALTER TABLE crm_timeline_events FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON crm_timeline_events;

CREATE POLICY crm_timeline_events_tenant_isolation ON crm_timeline_events
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);
