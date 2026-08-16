-- ============================================================
-- V20260813_1: Seed Control Plane admin (H2 Test Mirror — No-Op)
-- ============================================================
-- H2 does not support DO $$ blocks or ::uuid casts in all contexts.
-- This migration exists solely to maintain Flyway version parity.
-- ============================================================
SELECT 1;
