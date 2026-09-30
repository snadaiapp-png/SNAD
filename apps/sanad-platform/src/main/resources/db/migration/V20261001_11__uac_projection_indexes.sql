-- ============================================================================
-- V20261001_11 — Wave 1 / Task 5: access decision hot-path indexes.
-- Forward-only execution overlay for the original V20260924_5 allocation.
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_upo_lookup
    ON user_permission_overrides (tenant_id, user_id, capability_id);

CREATE INDEX IF NOT EXISTS idx_rel_lookup
    ON subject_relationships (tenant_id, subject_user_id, relationship_type);

CREATE INDEX IF NOT EXISTS idx_epp_user
    ON effective_permission_projection (tenant_id, user_id);
