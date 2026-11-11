-- ============================================================================
-- V20261111_1 — P0 RLS CLOSURE: public.service_callback_replay
-- Mandate: SANAD G1 / P0 Security Remediation executive order (2026-11-11),
-- merge reference cac06b8825a676971e731ace62a93f8d60e1a509.
--
-- GAP (forensic, verified at cac06b88):
--   * V20260724_2 created service_callback_replay with a UUID tenant_id but
--     NO ENABLE/FORCE RLS and NO policies.
--   * V20261029_1 (fail-closed tenant RLS wave) enumerates tables by module
--     prefix sets; service_callback_replay (service_ prefix) is absent from
--     BOTH the protected_tables and force_only_tables arrays.
--   * V20261104_2 closed the 19 historical crm_* tables — including the
--     sibling crm_integration_command_artifacts created by the same
--     V20260724_2 — the service_-prefixed replay guard stayed open.
--   * D6-TENANT-RLS-INVENTORY.md classified it as EXCEPTED (callback replay
--     guard) with the follow-up "evaluate a per-tenant policy variant once
--     callback authentication is established upstream". That condition now
--     holds: WorkflowCallbackSecurity.verify() validates the signed service
--     JWT (including tenant binding) BEFORE CallbackReplayStore.consume()
--     runs, so the tenant identity is trusted upstream and the per-tenant
--     policy variant is implementable (this migration + the store change).
--
-- CONTRACT (mirrors V20261029_1 / V20261021_1 precedent):
--   ENABLE + FORCE ROW LEVEL SECURITY on public.service_callback_replay.
--   Fail-closed tenant policies TO PUBLIC (missing/empty/foreign tenant GUC
--   ⇒ NULLIF ⇒ NULL comparison ⇒ zero rows / write denied):
--     SELECT   USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
--     INSERT   WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
--     DELETE   USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
--   TO PUBLIC (not TO sanad): every role is governed by the same predicate
--   (house precedent — V20261029_1 CONTRACT note; keeps non-owner isolation
--   verification roles usable).
--   NO UPDATE policy exists on purpose: UPDATE is default-denied for every
--   role (verified on PostgreSQL 17.11: an UPDATE with no applicable policy
--   matches zero rows — silently inert even for the table owner under FORCE
--   RLS — and V20261105_1 additionally revoked the UPDATE privilege from
--   sanad in the separated topology). The runtime path (consume INSERT,
--   deleteExpired DELETE) never updates rows.
--
-- CLEANUP PATH (least privilege, executive order phase 2.6):
--   The hourly expired-row sweep (CallbackReplayStore.deleteExpired, default
--   cron 0 17 * * * *) runs cross-tenant BY DESIGN: replay evidence must not
--   outlive its expiry. Granting the runtime role a standing cross-tenant
--   DELETE is exactly what this closure must avoid. Instead a dedicated
--   non-login role, environment-provisioned (V20261103_1 role contract
--   precedent — the chain never creates roles):
--       sanad_callback_replay_cleanup
--       NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS
--   holds exactly SELECT+DELETE on this one table and is bounded by TWO
--   policies: it can only ever SEE and DELETE rows with
--   expires_at <= CURRENT_TIMESTAMP, regardless of tenant. The runtime role
--   sanad holds a NON-INHERITING membership (WITH INHERIT FALSE, SET TRUE)
--   so the scheduler can bind the role inside its own transaction
--   (SET ROLE ... / RESET ROLE) without acquiring standing cross-tenant
--   privileges: policy TO-role matching follows role inheritance rules, so
--   the cleanup policies never apply to sanad itself.
--   No BYPASSRLS. No SECURITY DEFINER. No owner bypass: FORCE binds the
--   table owner to the same predicate.
--
-- ROLE PROVISIONING (cluster-wide bootstrap admin actor):
--   * apps/sanad-platform/docker/postgres-init/02-create-migrator-role.sh
--   * .github/workflows/ci.yml (PostgreSQL Direct provisioning block)
--   * tests/security/callback-replay-rls/run-rls-cert.sh (isolated PG17 cert)
--   * docs/security/migrator-runtime-separation/RUNBOOK.md §2 (production)
--   Provisioning SQL:
--     CREATE ROLE sanad_callback_replay_cleanup
--       NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
--     GRANT sanad_callback_replay_cleanup TO sanad WITH INHERIT FALSE;
--
-- COORDINATION WITH EXISTING MIGRATIONS:
--   * Runs AFTER V20261103_2 (sanad general-set DML grant) and V20261105_1
--     (UPDATE revocation, separated topology) — sanad already holds
--     SELECT/INSERT/DELETE on this table; this migration does not alter
--     sanad's grant surface.
--   * CallbackReplayStore.consume() binds app.tenant_id inside its own
--     transaction before the INSERT (trusted scoped path, the exact
--     mechanism ModuleResetService already uses for its tenant-scoped
--     deletes of this very table).
--
-- Forward-only. Fail-closed. Never rewrites published history.
-- ============================================================================

DO $p0$
DECLARE
    v_tbl      text := 'service_callback_replay';
    v_cleanup  text := 'sanad_callback_replay_cleanup';
    v_separated boolean;
    v_count    int;
    v_rel      record;
BEGIN
    v_separated := (current_setting('sanad.deployment_topology', true) = 'separated');

    ------------------------------------------------------------------
    -- G0 — restricted runtime role contract (V20261029_1 precedent)
    ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM pg_roles
                    WHERE rolname = 'sanad' AND NOT rolsuper AND NOT rolbypassrls) THEN
        RAISE EXCEPTION 'P0_CALLBACK_RLS_RUNTIME_ROLE: restricted runtime role sanad required (NOSUPERUSER, NOBYPASSRLS)'
            USING ERRCODE = '42501';
    END IF;

    ------------------------------------------------------------------
    -- G1 — cleanup role must be environment-provisioned (fail closed)
    ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_cleanup) THEN
        RAISE EXCEPTION 'P0_CALLBACK_CLEANUP_ROLE_REQUIRED: cluster role sanad_callback_replay_cleanup is not provisioned. Provision with: CREATE ROLE sanad_callback_replay_cleanup NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS; GRANT sanad_callback_replay_cleanup TO sanad WITH INHERIT FALSE; (docker postgres-init / CI provisioning / RUNBOOK section 2)'
            USING ERRCODE = '42501';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles
                WHERE rolname = v_cleanup
                  AND (rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolcanlogin)) THEN
        RAISE EXCEPTION 'P0_CALLBACK_CLEANUP_ROLE_CONTRACT: sanad_callback_replay_cleanup must be NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS'
            USING ERRCODE = '42501';
    END IF;

    ------------------------------------------------------------------
    -- G2 — membership contract: SET-only, NON-INHERITING
    ------------------------------------------------------------------
    IF NOT pg_has_role('sanad', v_cleanup, 'MEMBER') THEN
        RAISE EXCEPTION 'P0_CALLBACK_CLEANUP_MEMBERSHIP_REQUIRED: GRANT sanad_callback_replay_cleanup TO sanad WITH INHERIT FALSE; is required (scheduler SET ROLE path)'
            USING ERRCODE = '42501';
    END IF;
    IF pg_has_role('sanad', v_cleanup, 'USAGE') THEN
        RAISE EXCEPTION 'P0_CALLBACK_CLEANUP_MEMBERSHIP_INHERITING: sanad must hold sanad_callback_replay_cleanup NON-INHERITING (WITH INHERIT FALSE) so the expired-row policies never apply to the runtime identity'
            USING ERRCODE = '42501';
    END IF;
    IF NOT pg_has_role('sanad', v_cleanup, 'SET') THEN
        RAISE EXCEPTION 'P0_CALLBACK_CLEANUP_MEMBERSHIP_SET: sanad must retain SET ROLE on sanad_callback_replay_cleanup (WITH SET TRUE)'
            USING ERRCODE = '42501';
    END IF;
    -- No escalation path from the cleanup role to privileged identities.
    -- (pg_has_role RAISES on a missing role rather than returning false, so
    --  the optional sanad_migrator reference is guarded by an EXISTS check;
    --  in-chain V20261103_1 guarantees it exists, but the guard keeps the
    --  failure mode a clear contract violation instead of a catalog error.)
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_migrator')
       AND pg_has_role(v_cleanup, 'sanad_migrator', 'MEMBER') THEN
        RAISE EXCEPTION 'P0_CALLBACK_CLEANUP_ROLE_ESCALATION: sanad_callback_replay_cleanup must not be a member of sanad_migrator'
            USING ERRCODE = '42501';
    END IF;
    IF pg_has_role(v_cleanup, 'sanad', 'MEMBER') THEN
        RAISE EXCEPTION 'P0_CALLBACK_CLEANUP_ROLE_ESCALATION: sanad_callback_replay_cleanup must not be a member of sanad'
            USING ERRCODE = '42501';
    END IF;

    ------------------------------------------------------------------
    -- G3 — table / column preconditions (fail closed)
    ------------------------------------------------------------------
    IF to_regclass('public.service_callback_replay') IS NULL THEN
        RAISE EXCEPTION 'P0_CALLBACK_TABLE_MISSING: public.service_callback_replay (V20260724_2) not found'
            USING ERRCODE = '42P01';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = v_tbl
                      AND column_name = 'tenant_id' AND data_type = 'uuid') THEN
        RAISE EXCEPTION 'P0_CALLBACK_TENANT_COLUMN: service_callback_replay.tenant_id must be UUID'
            USING ERRCODE = '42703';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = v_tbl
                      AND column_name = 'expires_at' AND data_type = 'timestamp with time zone') THEN
        RAISE EXCEPTION 'P0_CALLBACK_EXPIRY_COLUMN: service_callback_replay.expires_at must be TIMESTAMPTZ'
            USING ERRCODE = '42703';
    END IF;

    ------------------------------------------------------------------
    -- G4 — RLS state: ENABLE + FORCE, explicit PUBLIC revoke
    ------------------------------------------------------------------
    ALTER TABLE public.service_callback_replay ENABLE ROW LEVEL SECURITY;
    ALTER TABLE public.service_callback_replay FORCE ROW LEVEL SECURITY;
    REVOKE ALL ON TABLE public.service_callback_replay FROM PUBLIC;

    ------------------------------------------------------------------
    -- G5 — fail-closed tenant policies TO PUBLIC
    --      (IDEMPOTENCE: DROP + CREATE per V20261029_1 §IDEMPOTENCE)
    ------------------------------------------------------------------
    DROP POLICY IF EXISTS tenant_isolation_select ON public.service_callback_replay;
    CREATE POLICY tenant_isolation_select ON public.service_callback_replay
        FOR SELECT TO PUBLIC
        USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));

    DROP POLICY IF EXISTS tenant_isolation_insert ON public.service_callback_replay;
    CREATE POLICY tenant_isolation_insert ON public.service_callback_replay
        FOR INSERT TO PUBLIC
        WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));

    DROP POLICY IF EXISTS tenant_isolation_delete ON public.service_callback_replay;
    CREATE POLICY tenant_isolation_delete ON public.service_callback_replay
        FOR DELETE TO PUBLIC
        USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));
    -- Deliberately NO UPDATE policy: UPDATE is default-denied for every role.

    ------------------------------------------------------------------
    -- G6 — cleanup policies bounded to expired rows (role-scoped)
    ------------------------------------------------------------------
    DROP POLICY IF EXISTS expired_cleanup_select ON public.service_callback_replay;
    CREATE POLICY expired_cleanup_select ON public.service_callback_replay
        FOR SELECT TO sanad_callback_replay_cleanup
        USING (expires_at <= CURRENT_TIMESTAMP);

    DROP POLICY IF EXISTS expired_cleanup_delete ON public.service_callback_replay;
    CREATE POLICY expired_cleanup_delete ON public.service_callback_replay
        FOR DELETE TO sanad_callback_replay_cleanup
        USING (expires_at <= CURRENT_TIMESTAMP);

    ------------------------------------------------------------------
    -- G7 — cleanup role ACL: exactly SELECT + DELETE, nothing else
    ------------------------------------------------------------------
    REVOKE ALL ON TABLE public.service_callback_replay FROM sanad_callback_replay_cleanup;
    GRANT SELECT, DELETE ON TABLE public.service_callback_replay TO sanad_callback_replay_cleanup;

    ------------------------------------------------------------------
    -- G8 — postcondition verification (fail closed on any drift)
    ------------------------------------------------------------------
    SELECT relrowsecurity, relforcerowsecurity INTO v_rel
      FROM pg_class WHERE oid = 'public.service_callback_replay'::regclass;
    IF NOT (v_rel.relrowsecurity AND v_rel.relforcerowsecurity) THEN
        RAISE EXCEPTION 'P0_CALLBACK_VERIFY: ENABLE/FORCE RLS state missing';
    END IF;

    -- Exactly five policies; no UPDATE policy; no unexpected extras.
    SELECT count(*) INTO v_count FROM pg_policy
     WHERE polrelid = 'public.service_callback_replay'::regclass;
    IF v_count <> 5 THEN
        RAISE EXCEPTION 'P0_CALLBACK_VERIFY: expected 5 policies, found %', v_count;
    END IF;
    SELECT count(*) INTO v_count FROM pg_policy
     WHERE polrelid = 'public.service_callback_replay'::regclass AND polcmd = 'U';
    IF v_count <> 0 THEN
        RAISE EXCEPTION 'P0_CALLBACK_VERIFY: UPDATE policy must not exist (default-denied)';
    END IF;

    -- Tenant policy expressions: normalization-tolerant, no fail-open IS NULL
    -- branch (mirrors V20261029_1 G3).
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
         WHERE schemaname = 'public' AND tablename = v_tbl
           AND policyname = 'tenant_isolation_select' AND cmd = 'SELECT'
           AND qual LIKE '%tenant_id%text = NULLIF(current_setting%app.tenant_id%'
           AND qual NOT ILIKE '%IS NULL%') THEN
        RAISE EXCEPTION 'P0_CALLBACK_VERIFY: tenant_isolation_select contract failed';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
         WHERE schemaname = 'public' AND tablename = v_tbl
           AND policyname = 'tenant_isolation_insert' AND cmd = 'INSERT'
           AND with_check LIKE '%tenant_id%text = NULLIF(current_setting%app.tenant_id%'
           AND with_check NOT ILIKE '%IS NULL%') THEN
        RAISE EXCEPTION 'P0_CALLBACK_VERIFY: tenant_isolation_insert contract failed';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
         WHERE schemaname = 'public' AND tablename = v_tbl
           AND policyname = 'tenant_isolation_delete' AND cmd = 'DELETE'
           AND qual LIKE '%tenant_id%text = NULLIF(current_setting%app.tenant_id%'
           AND qual NOT ILIKE '%IS NULL%') THEN
        RAISE EXCEPTION 'P0_CALLBACK_VERIFY: tenant_isolation_delete contract failed';
    END IF;

    -- Cleanup policy expressions: strictly expiry-bounded, role-scoped.
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
         WHERE schemaname = 'public' AND tablename = v_tbl
           AND policyname = 'expired_cleanup_select' AND cmd = 'SELECT'
           AND roles = '{sanad_callback_replay_cleanup}'
           AND qual LIKE '%expires_at <= CURRENT_TIMESTAMP%') THEN
        RAISE EXCEPTION 'P0_CALLBACK_VERIFY: expired_cleanup_select contract failed';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
         WHERE schemaname = 'public' AND tablename = v_tbl
           AND policyname = 'expired_cleanup_delete' AND cmd = 'DELETE'
           AND roles = '{sanad_callback_replay_cleanup}'
           AND qual LIKE '%expires_at <= CURRENT_TIMESTAMP%') THEN
        RAISE EXCEPTION 'P0_CALLBACK_VERIFY: expired_cleanup_delete contract failed';
    END IF;

    -- Cleanup role privilege surface: exactly SELECT + DELETE.
    IF NOT (has_table_privilege(v_cleanup, 'public.service_callback_replay', 'SELECT')
        AND has_table_privilege(v_cleanup, 'public.service_callback_replay', 'DELETE')) THEN
        RAISE EXCEPTION 'P0_CALLBACK_VERIFY: cleanup role must hold SELECT+DELETE';
    END IF;
    IF EXISTS (SELECT 1 FROM unnest(ARRAY['INSERT','UPDATE','TRUNCATE','REFERENCES','TRIGGER']) AS p(priv)
                WHERE has_table_privilege(v_cleanup, 'public.service_callback_replay', p.priv)) THEN
        RAISE EXCEPTION 'P0_CALLBACK_VERIFY: cleanup role must hold NO privilege beyond SELECT+DELETE';
    END IF;

    -- Runtime surface intact for the callback consume path + module reset.
    IF NOT (has_table_privilege('sanad', 'public.service_callback_replay', 'SELECT')
        AND has_table_privilege('sanad', 'public.service_callback_replay', 'INSERT')
        AND has_table_privilege('sanad', 'public.service_callback_replay', 'DELETE')) THEN
        RAISE EXCEPTION 'P0_CALLBACK_VERIFY: sanad runtime surface (SELECT/INSERT/DELETE) incomplete';
    END IF;
    -- Separated topology negative: UPDATE stays revoked (V20261105_1 contract).
    IF v_separated AND has_table_privilege('sanad', 'public.service_callback_replay', 'UPDATE') THEN
        RAISE EXCEPTION 'P0_CALLBACK_VERIFY: sanad must not hold UPDATE in the separated topology';
    END IF;
END
$p0$;
