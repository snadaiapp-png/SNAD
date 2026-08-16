-- ============================================================
-- V20260812_1: Create mobile sync metadata tables (H2 Test Mirror — No-Op)
-- ============================================================
-- H2 does not support gen_random_uuid() inline in CREATE TABLE.
-- This migration exists solely to maintain Flyway version parity.
-- H2 tests rely on application-level tenant filtering.
-- ============================================================
SELECT 1;
