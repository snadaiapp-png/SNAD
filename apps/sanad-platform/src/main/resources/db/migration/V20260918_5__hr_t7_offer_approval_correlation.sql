-- ============================================================================
-- V20260918_5 — HRM G1 T7 offer approval correlation + immutable version identity.
--
-- TEST_ALIGNMENT_REASON = Legitimate forward-only feature migration (T7
-- EXECUTION DIRECTIVE §T7.12). Adds ONLY additive DDL:
--   * tenant-safe composite identity on hr_offer_versions (FK target),
--   * current-version reference + open-approval workflow correlation on
--     hr_offers (tenant, offer, offer_version, workflow definition/version,
--     workflow instance — persisted by the HRM application authority),
--   * the §11.2 idempotency invariant (ONE open approval per offer),
--   * offer/application/workflow lookup indexes,
--   * a DB-level append-only guard making historical offer versions
--     immutable (T7.4: no UPDATE/DELETE path, defense in depth).
-- No previously merged migration bytes are modified; no migration is
-- deleted; no Flyway history is rewritten. RLS (ENABLE + FORCE +
-- tenant_isolation) was installed on hr_offers / hr_offer_versions by
-- V20260918_3 and automatically covers the new columns.
-- ============================================================================

-- 1. Tenant-safe composite identity for version rows (FK target for the
--    offer aggregate's version references) + predecessor linkage for the
--    deterministic successor chain (§T7.4: old version unchanged, new
--    version number increments, predecessor recorded).
ALTER TABLE hr_offer_versions
    ADD CONSTRAINT uq_hr_offer_versions_id_tenant UNIQUE (id, tenant_id);

ALTER TABLE hr_offer_versions
    ADD COLUMN predecessor_version_id UUID;

-- Tenant-congruent predecessor linkage: (predecessor_version_id, tenant_id)
-- must resolve to a version row of the SAME tenant.
ALTER TABLE hr_offer_versions
    ADD CONSTRAINT fk_hr_offer_versions_predecessor
    FOREIGN KEY (predecessor_version_id, tenant_id)
    REFERENCES hr_offer_versions (id, tenant_id);

-- 2. Offer aggregate: current version reference + open approval correlation.
ALTER TABLE hr_offers
    ADD COLUMN current_version_id UUID,
    ADD COLUMN pending_offer_version_id UUID,
    ADD COLUMN pending_workflow_instance_id UUID,
    ADD COLUMN pending_workflow_definition_version_id UUID;

-- Tenant-congruent composite FKs: version references resolve ONLY within the
-- same tenant (gate §14 — tenant congruence enforced at DB level).
ALTER TABLE hr_offers
    ADD CONSTRAINT fk_hr_offers_current_version
    FOREIGN KEY (current_version_id, tenant_id)
    REFERENCES hr_offer_versions (id, tenant_id);
ALTER TABLE hr_offers
    ADD CONSTRAINT fk_hr_offers_pending_version
    FOREIGN KEY (pending_offer_version_id, tenant_id)
    REFERENCES hr_offer_versions (id, tenant_id);

-- 3. Design §11.2 idempotency: one OPEN approval per offer — the partial
--    unique index fails closed on any second concurrent open correlation.
CREATE UNIQUE INDEX uq_hr_offers_one_open_approval
    ON hr_offers (tenant_id, id)
    WHERE pending_workflow_instance_id IS NOT NULL;

-- 3b. §T7.5/§14 idempotency: one OPEN approval per (offer, offer_version).
CREATE UNIQUE INDEX uq_hr_offers_one_open_approval_per_version
    ON hr_offers (tenant_id, pending_offer_version_id)
    WHERE pending_workflow_instance_id IS NOT NULL;

-- 3c. Design §11.1: the Job Opening approval correlation (one OPEN approval
--     per opening / per submitted-state-period, idempotent re-submission).
ALTER TABLE hr_job_openings
    ADD COLUMN pending_workflow_instance_id UUID,
    ADD COLUMN pending_workflow_definition_version_id UUID;

CREATE UNIQUE INDEX uq_hr_job_openings_one_open_approval
    ON hr_job_openings (tenant_id, id)
    WHERE pending_workflow_instance_id IS NOT NULL;

CREATE INDEX idx_hr_job_openings_pending_workflow
    ON hr_job_openings (tenant_id, pending_workflow_instance_id);

-- 4. Lookup indexes (offer state scans, workflow correlation resolution).
CREATE INDEX idx_hr_offers_state
    ON hr_offers (tenant_id, state);
CREATE INDEX idx_hr_offers_pending_workflow
    ON hr_offers (tenant_id, pending_workflow_instance_id);

-- 5. T7.4: historical offer versions are immutable — the database itself
--    rejects UPDATE and DELETE on hr_offer_versions rows (defense in depth
--    behind the application authority, which exposes no such path).
CREATE FUNCTION hr_t7_offer_versions_append_only_guard() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'HRM_OFFER_VERSION_IMMUTABLE: offer versions are immutable history';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_hr_offer_versions_append_only
    BEFORE UPDATE OR DELETE ON hr_offer_versions
    FOR EACH ROW EXECUTE FUNCTION hr_t7_offer_versions_append_only_guard();
