-- ============================================================
-- SNAD Platform — Add version column to crm_pipelines
-- ------------------------------------------------------------
-- Branch: crm/003-stable-api-contracts
-- Gate:   CRM-G2 — API Contract and Concurrency Gate
--
-- The other CRM entities (accounts, contacts, leads, opportunities,
-- activities, custom_field_definitions) already have a `version` column
-- from V20260702_1__create_unified_crm_core.sql. Pipelines was the
-- only editable CRM entity missing the column.
--
-- This migration uses portable SQL that works on both PostgreSQL and H2.
-- The information_schema query is supported by both databases.
-- ============================================================

-- Add version column to crm_pipelines if it does not already exist.
-- Using a portable approach that works on both PostgreSQL and H2.
ALTER TABLE crm_pipelines ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
