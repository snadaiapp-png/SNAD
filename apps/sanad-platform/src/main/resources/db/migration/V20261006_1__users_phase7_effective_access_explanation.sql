-- ============================================================================
-- V20261006_1 — Users Module Phase 7: effective access explanation model.
--
-- Official scope source:
--   docs/superpowers/plans/2026-10-03-user-administration-module-access-expansion-implementation.md
--   (Phase 7 — Effective access and audit)
--
-- MIGRATION_NEEDED_REASON (directive §13):
--   * effective_permission_projection.effect carried CHECK (effect IN ('ALLOW'))
--     — the Phase 7 required model (effect = ALLOW | DENY, deny dominance §6)
--     could not be represented.
--   * No `reason` column existed for the required canonical machine-readable
--     reason provenance (§5: source/reason sufficient for administrator
--     explanation).
--
-- Forward-only. No existing migration is rewritten. The unique index, RLS
-- policies (ENABLE + FORCE tenant_isolation), and the source CHECK (ROLE,
-- OVERRIDE, BREAK_GLASS) are intentionally unchanged — Phase 7 does NOT
-- project RELATIONSHIP rows (RELATIONSHIP_PROVENANCE =
-- NOT_APPLICABLE_OR_NOT_AVAILABLE; relationship authority stays exclusively
-- in the canonical evaluator).
--
-- Canonical reason vocabulary mirrors the authoritative evaluator
-- (CapabilityEvaluationService / AuthorizationDecision.reason):
--   ROLE_CAPABILITY_MATCH   — role-derived ALLOW
--   EXPLICIT_ALLOW_MATCH    — explicit user ALLOW override (ordinary or
--                             break-glass; BREAK_GLASS distinction lives in
--                             the existing source column)
--   EXPLICIT_DIRECT_DENY    — capability-wide effective DENY override
-- ============================================================================

-- 1) effect: ALLOW -> ALLOW | DENY (drop the Wave-1 single-effect constraint,
--    re-assert the two-effect contract under the original constraint name).
ALTER TABLE effective_permission_projection
    DROP CONSTRAINT effective_permission_projection_effect_check;

ALTER TABLE effective_permission_projection
    ADD CONSTRAINT effective_permission_projection_effect_check
    CHECK (effect IN ('ALLOW', 'DENY'));

-- 2) reason: canonical machine-readable explanation, mandatory on every row.
ALTER TABLE effective_permission_projection
    ADD COLUMN reason TEXT NULL;

-- Backfill existing rows with the canonical reason for their provenance so
-- the NOT NULL transition is total (forward-only, no rewrite of history
-- outside this projection read model, which is rebuilt per subject).
UPDATE effective_permission_projection
   SET reason = 'ROLE_CAPABILITY_MATCH'
 WHERE source = 'ROLE'
   AND reason IS NULL;

UPDATE effective_permission_projection
   SET reason = 'EXPLICIT_ALLOW_MATCH'
 WHERE source IN ('OVERRIDE', 'BREAK_GLASS')
   AND reason IS NULL;

ALTER TABLE effective_permission_projection
    ALTER COLUMN reason SET NOT NULL;
