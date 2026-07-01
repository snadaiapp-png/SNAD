-- ============================================================
-- SANAD Platform - Flyway migration V30 (H2 + PostgreSQL)
-- ------------------------------------------------------------
-- Stage 05A.2 §3 — Restore referential integrity for audit and
-- idempotency tables.
--
-- V29 dropped the FK constraints from audit_events, audit_chain_heads,
-- and idempotency_records to tenants to work around test cleanup
-- issues. This was a production security defect (CD-05-P1-018):
-- removing FK constraints weakens referential integrity.
--
-- V30 restores the FK constraints with ON DELETE RESTRICT:
--   - audit_events.tenant_id → tenants.id (ON DELETE RESTRICT)
--   - audit_chain_heads.tenant_id → tenants.id (ON DELETE RESTRICT)
--   - idempotency_records.tenant_id → tenants.id (ON DELETE RESTRICT)
--
-- ON DELETE RESTRICT means a tenant CANNOT be physically deleted
-- while audit history or idempotency records reference it. Tenants
-- must be archived (status = ARCHIVED) or anonymized instead.
--
-- This is the correct production behavior: audit history must
-- outlive the tenant's active lifecycle for compliance and legal
-- hold purposes.
--
-- Test cleanup must use ephemeral databases that are destroyed
-- after the CI job, not physical tenant deletion.
-- ============================================================

-- Restore FK on audit_events
ALTER TABLE audit_events
    ADD CONSTRAINT fk_audit_events_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants (id)
    ON DELETE RESTRICT;

-- Restore FK on audit_chain_heads
ALTER TABLE audit_chain_heads
    ADD CONSTRAINT fk_audit_chain_heads_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants (id)
    ON DELETE RESTRICT;

-- Restore FK on idempotency_records
ALTER TABLE idempotency_records
    ADD CONSTRAINT fk_idempotency_records_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants (id)
    ON DELETE RESTRICT;
