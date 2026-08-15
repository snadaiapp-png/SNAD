-- ============================================================
-- V20260815_22: Enable RLS on lifecycle base tables (H2 Test Mirror — No-Op)
-- ============================================================
-- H2 does not support PostgreSQL Row-Level Security.
-- This migration exists solely to maintain Flyway version parity
-- with the PostgreSQL migration V20260815_22 so that H2-based tests
-- do not fail with a missing-migration validation error.
--
-- RLS is a PostgreSQL-only defense-in-depth layer. H2 tests rely
-- on the existing application-level tenant filtering (WHERE tenant_id).
-- RLS is verified end-to-end by the production PostgreSQL deployment.
-- ============================================================

-- Intentionally empty — no-op for H2 compatibility.
SELECT 1;
