-- ============================================================================
-- V20261003_3 — Wave 2 / Task 2: partner referential-integrity closure.
--
-- Closes the deferred Wave-1 integrity gap now that the canonical partners
-- principal table exists (V20261003_2).
--
-- Scope:
--   * user_permission_overrides.partner_id -> partners.id
--   * fail closed when any pre-existing non-null partner_id is orphaned
--   * preserve NULL partner_id for direct-tenant authorization records
--
-- Explicitly out of scope:
--   partner memberships, partner-tenant bindings, delegated administration,
--   partner APIs, commercial identity, billing, settlement.
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM user_permission_overrides upo
        LEFT JOIN partners p ON p.id = upo.partner_id
        WHERE upo.partner_id IS NOT NULL
          AND p.id IS NULL
    ) THEN
        RAISE EXCEPTION
            'W2-T2 precondition failed: orphan user_permission_overrides.partner_id rows exist'
            USING ERRCODE = '23503';
    END IF;
END
$$;

ALTER TABLE user_permission_overrides
    ADD CONSTRAINT fk_user_permission_overrides_partner
    FOREIGN KEY (partner_id)
    REFERENCES partners(id)
    ON DELETE RESTRICT;
