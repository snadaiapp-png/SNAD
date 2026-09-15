-- ============================================================
-- V20260914_1: PATH-B G1-B — Application catalog status lifecycle
-- (design spec 2026-09-14 §12.4, additive widening)
--
-- Widens ck_applications_status ADDITIVELY:
--   legacy values preserved verbatim (NO row rewrite): ACTIVE, INACTIVE, DEPRECATED
--   newly permitted lifecycle values:                  DRAFT, ARCHIVED
--
-- Archive actions target ARCHIVED; restore returns to ACTIVE. Existing
-- DEPRECATED rows remain readable and are not silently rewritten. There is
-- deliberately NO physical delete for catalog rows.
--
-- Forward-only, additive, idempotent; no index changes required
-- (idx_applications_status already covers status reads).
-- ============================================================

ALTER TABLE applications DROP CONSTRAINT IF EXISTS ck_applications_status;
ALTER TABLE applications ADD CONSTRAINT ck_applications_status
    CHECK (status IN (
        -- legacy values (kept for backward compatibility; never rewritten)
        'ACTIVE', 'INACTIVE', 'DEPRECATED',
        -- G1-B lifecycle additions (§12.4)
        'DRAFT', 'ARCHIVED'
    ));
