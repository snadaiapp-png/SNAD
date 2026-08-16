-- G7 (mobile offline foundation) — DEF-008 runtime remediation
--
-- Runtime verification against local PostgreSQL 17 showed that the table OWNER
-- (the role the application connects as by default: `sanad`) BYPASSES row-level
-- security, because PostgreSQL only enforces RLS on the owner when
-- FORCE ROW LEVEL SECURITY is set. The tenant-isolation policies created in
-- V20260812_1 are fail-closed by construction (`tenant_id = current_setting('app.tenant_id', true)::uuid`),
-- but were ineffective for the owner role at runtime (all rows visible regardless
-- of the app.tenant_id GUC). This migration forces RLS on the four mobile sync
-- tables so the policies apply to every role, including the owner, restoring
-- fail-closed tenant isolation at runtime. (Runtime-verified: see G7 report.)
ALTER TABLE mobile_device_registry FORCE ROW LEVEL SECURITY;
ALTER TABLE mobile_sync_cursor      FORCE ROW LEVEL SECURITY;
ALTER TABLE mobile_sync_log         FORCE ROW LEVEL SECURITY;
ALTER TABLE mobile_conflict_log     FORCE ROW LEVEL SECURITY;
