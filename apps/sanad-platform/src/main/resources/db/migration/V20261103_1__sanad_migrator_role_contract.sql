-- ============================================================================
-- V20261103_1 — P0 MIGRATOR/RUNTIME ROLE SEPARATION (Issue #1462):
--               dedicated migration identity `sanad_migrator` — role contract.
--
-- Mandate: SANAD P0 FINAL REMEDIATION executive order, Article 3
--          (separate the migrator role from the runtime role).
--
-- EVIDENCE OF THE PROBLEM (live reproduction, isolated PostgreSQL 17.6,
-- 2026-11-02, this exact chain at head 20261102.2, baseline fixture run):
--   * All 324 public-schema objects are owned by the restricted runtime role
--     `sanad` under the current CI/self-hosted topology (owner-as-runtime).
--   * Connected AS `sanad` (NOSUPERUSER NOBYPASSRLS):
--       ALTER TABLE public.tenants ADD COLUMN ...  -> ALLOWED (rc=0)
--       TRUNCATE public.subscription_commands      -> ALLOWED (rc=0)
--         (TRUNCATE is NOT subject to RLS - the append-only ledger can be
--          wiped by the runtime identity)
--       UPDATE/DELETE public.flyway_schema_history -> ALLOWED (rc=0)
--       CREATE TABLE public.<any>                  -> ALLOWED (rc=0)
--   * In production (Supabase `tkbrvupemreqabwzdpyq`) the shared model instead
--     couples Flyway to the bootstrap/admin identity, which is exactly the
--     masked-gap class V20261102_1/V20261102_2 had to repair for the six
--     FORCE-RLS'd planes.
--
-- TARGET MODEL (this migration + V20261103_2):
--   sanad_migrator : dedicated Flyway identity. NOSUPERUSER NOBYPASSRLS
--                    NOCREATEDB NOCREATEROLE. Owns the public-schema objects
--                    after V20261103_2. LOGIN is enabled by the ENVIRONMENT
--                    operator (password held only in the secret store) -
--                    never by the chain, never by the application.
--   sanad          : runtime identity (REST API, workers, background jobs).
--                    NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE.
--                    NOT a member of sanad_migrator (directly or indirectly).
--                    Owns nothing after V20261103_2. Holds DML-only ACLs.
--
-- WHY THE ROLE IS ENVIRONMENT-PROVISIONED, NOT CREATED HERE:
--   The restricted CI fixture topology runs this chain as `sanad`
--   (NOCREATEROLE): CREATE ROLE is impossible there, and conditional creation
--   would fork chain behaviour per environment. A cluster-wide role
--   provisioned once by the bootstrap/admin actor (CI provisioning SQL,
--   docker/postgres-init, Supabase operator step) is visible to every
--   database on the cluster and keeps this migration deterministic and
--   fail-closed. Provisioning contract: 
--   docs/security/migrator-runtime-separation/RUNBOOK.md.
--
-- Forward-only. Idempotent. Never rewrites published history.
-- ============================================================================

DO $$
DECLARE
    v_mig  record;
    v_run  record;
BEGIN
    -- G1 - sanad_migrator must exist (fail closed, actionable guidance)
    SELECT rolname, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole, rolcanlogin
      INTO v_mig
      FROM pg_roles
     WHERE rolname = 'sanad_migrator';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'SANAD_MIGRATOR_ROLE_REQUIRED: cluster role sanad_migrator is not provisioned. Provision it with: CREATE ROLE sanad_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS; (CI provisioning and docker/postgres-init do this automatically; for production see docs/security/migrator-runtime-separation/RUNBOOK.md section 2)'
            USING ERRCODE = '42501';
    END IF;

    IF v_mig.rolsuper OR v_mig.rolbypassrls OR v_mig.rolcreatedb OR v_mig.rolcreaterole THEN
        RAISE EXCEPTION 'SANAD_MIGRATOR_CONTRACT: sanad_migrator must be NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE (found super=%, bypassrls=%, createdb=%, createrole=%)',
            v_mig.rolsuper, v_mig.rolbypassrls, v_mig.rolcreatedb, v_mig.rolcreaterole
            USING ERRCODE = '42501';
    END IF;

    -- G2 - runtime role contract (same gate class as V20261011_3/V20261102_1)
    SELECT rolsuper, rolbypassrls
      INTO v_run
      FROM pg_roles
     WHERE rolname = 'sanad';

    IF NOT FOUND OR v_run.rolsuper OR v_run.rolbypassrls THEN
        RAISE EXCEPTION 'SANAD_MIGRATOR_ROLE_PRECONDITION: restricted runtime role sanad required (NOSUPERUSER, NOBYPASSRLS)'
            USING ERRCODE = '42501';
    END IF;

    -- G3 - no direct membership path from runtime to migrator (fail closed)
    IF pg_has_role('sanad', 'sanad_migrator', 'MEMBER') THEN
        RAISE EXCEPTION 'SANAD_MIGRATOR_ROLE_PRECONDITION: sanad must not be a member of sanad_migrator'
            USING ERRCODE = '42501';
    END IF;

    -- G4 - no indirect membership path from runtime to migrator (fail closed)
    IF EXISTS (
        SELECT 1
          FROM pg_auth_members m
          JOIN pg_roles member_r ON member_r.oid = m.member
          JOIN pg_roles granted  ON granted.oid  = m.roleid
         WHERE granted.rolname = 'sanad_migrator'
           AND (member_r.rolname = 'sanad'
                OR pg_has_role('sanad', member_r.rolname, 'MEMBER'))
    ) THEN
        RAISE EXCEPTION 'SANAD_MIGRATOR_ROLE_PRECONDITION: indirect membership path sanad -> sanad_migrator detected'
            USING ERRCODE = '42501';
    END IF;
END $$;
