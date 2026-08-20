-- G8 EXECUTION 03 — TRACK C: FORCE RLS on crm_call_events.
--
-- PostgreSQL only enforces RLS on the table owner when FORCE ROW LEVEL
-- SECURITY is set (pattern V20260812_3 for the mobile sync tables).
ALTER TABLE crm_call_events FORCE ROW LEVEL SECURITY;
