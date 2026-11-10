-- ============================================================================
-- SANAD G1 P0 (Issue #1462 / CAB 2026-11-10 §C) — one-time admin step that
-- provisions the READ-ONLY release audit identity used by
-- scripts/production/verify-flyway.sh (production Flyway compatibility gate).
--
-- WHY THIS EXISTS:
--   In the separated migrator/runtime topology (V20261103_2) the runtime role
--   `sanad` holds NO privilege on flyway_schema_history. The release workflow
--   previously audited Flyway with the backend runtime credentials, so the
--   canonical production-release path was intentionally fail-closed until the
--   audit moved to a separately governed read-only identity
--   (docs/security/G1-CAB-PRODUCTION-GO-NOGO-EVIDENCE-2026-11-10.md §C).
--
-- EXECUTION ORDER (production, Supabase tkbrvupemreqabwzdpyq):
--   Run this ONCE as the bootstrap admin (Supabase SQL editor / `postgres`)
--   BEFORE scripts/ops/migrator-separation/transfer-ownership-to-migrator.sql,
--   while the admin still owns the affected objects. Re-running later is
--   idempotent only from the owner context (see the SET ROLE note below).
--
-- CONTRACT:
--   Role sanad_release_auditor:
--     LOGIN enabled by the operator (password held ONLY in the GitHub
--     Production environment secrets PROD_RELEASE_AUDIT_USER /
--     PROD_RELEASE_AUDIT_PASSWORD / PROD_RELEASE_AUDIT_URL — never in the
--     repository), NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE.
--   Exact privilege set (least privilege, fail-closed verifiable):
--     USAGE on schema public
--     SELECT on public.flyway_schema_history   (migration ledger metadata)
--     SELECT on public.access_capabilities     (Workflow Y2 sentinel rows;
--                                               global reference data, no RLS)
--   NO privilege on any tenant business table; NO write privilege anywhere.
--   The Y2 definition-column sentinel reads pg_catalog.pg_attribute, which
--   is world-readable and requires no table privilege.
--
-- This script performs NO revokes and NO ownership changes.
-- ============================================================================

DO $provision$
DECLARE
    v_aud record;
BEGIN
    -- (a) dedicated read-only audit role (cluster-wide)
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_release_auditor') THEN
        CREATE ROLE sanad_release_auditor NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;

    -- (b) role contract (fail closed)
    SELECT rolsuper, rolbypassrls, rolcreatedb, rolcreaterole
      INTO v_aud
      FROM pg_roles
     WHERE rolname = 'sanad_release_auditor';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'AUDIT_ROLE_PROVISION_FAILED: sanad_release_auditor missing after creation';
    END IF;
    IF v_aud.rolsuper OR v_aud.rolbypassrls OR v_aud.rolcreatedb OR v_aud.rolcreaterole THEN
        RAISE EXCEPTION 'AUDIT_ROLE_CONTRACT: sanad_release_auditor must be NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE';
    END IF;

    -- (c) no membership path from the audit role into runtime/migrator/admin
    IF pg_has_role('sanad_release_auditor', 'sanad_migrator', 'MEMBER')
       OR pg_has_role('sanad_release_auditor', 'sanad', 'MEMBER') THEN
        RAISE EXCEPTION 'AUDIT_ROLE_CONTRACT: sanad_release_auditor must hold no membership in sanad/sanad_migrator';
    END IF;

    RAISE NOTICE 'AUDIT_ROLE_OK: sanad_release_auditor provisioned with the read-only contract';
END $provision$;

-- (d) least-privilege grants.
--     Run BEFORE the migrator ownership transfer (admin is the owner of the
--     granted objects at that point). ACL entries survive ALTER OWNER; if a
--     later re-run is required after the transfer, execute these GRANTs from
--     an owner context (e.g. SET ROLE sanad_migrator as the bootstrap admin).
GRANT USAGE ON SCHEMA public TO sanad_release_auditor;
GRANT SELECT ON TABLE public.flyway_schema_history TO sanad_release_auditor;
GRANT SELECT ON TABLE public.access_capabilities TO sanad_release_auditor;

-- (e) fail-closed verification of the effective privilege set (exact match:
--     the audit role must hold SELECT on exactly the two granted relations
--     and nothing more in schema public).
DO $verify$
DECLARE
    v_extra TEXT;
    v_missing BOOLEAN;
BEGIN
    SELECT string_agg(t.tablename, ', ') INTO v_extra
      FROM pg_tables t
     WHERE t.schemaname = 'public'
       AND t.tablename NOT IN ('flyway_schema_history', 'access_capabilities')
       AND has_table_privilege('sanad_release_auditor',
            'public.' || quote_ident(t.tablename), 'SELECT');
    IF v_extra IS NOT NULL THEN
        RAISE EXCEPTION 'AUDIT_GRANT_VERIFY: unexpected SELECT privilege(s) on: %', v_extra;
    END IF;

    SELECT NOT (has_table_privilege('sanad_release_auditor', 'public.flyway_schema_history', 'SELECT')
            AND has_table_privilege('sanad_release_auditor', 'public.access_capabilities', 'SELECT'))
      INTO v_missing;
    IF v_missing THEN
        RAISE EXCEPTION 'AUDIT_GRANT_VERIFY: required SELECT privilege missing on flyway_schema_history/access_capabilities';
    END IF;

    IF has_table_privilege('sanad_release_auditor', 'public.flyway_schema_history', 'INSERT')
       OR has_table_privilege('sanad_release_auditor', 'public.flyway_schema_history', 'UPDATE')
       OR has_schema_privilege('sanad_release_auditor', 'public', 'CREATE') THEN
        RAISE EXCEPTION 'AUDIT_GRANT_VERIFY: write/DDL privilege present — read-only contract violated';
    END IF;

    RAISE NOTICE 'AUDIT_GRANT_VERIFY_OK: read-only audit privilege set verified';
END $verify$;

-- (f) enable LOGIN with the operator-held secret (run manually, secret never
--     in SQL history/logs — use a psql protected variable or the operator's
--     governed channel):
--     SELECT format('ALTER ROLE sanad_release_auditor LOGIN PASSWORD %L', :'audit_secret') \gexec
--     Then store the identical value in the GitHub Production environment
--     secrets PROD_RELEASE_AUDIT_USER (= sanad_release_auditor) and
--     PROD_RELEASE_AUDIT_PASSWORD, plus PROD_RELEASE_AUDIT_URL pointing at
--     the production database.
