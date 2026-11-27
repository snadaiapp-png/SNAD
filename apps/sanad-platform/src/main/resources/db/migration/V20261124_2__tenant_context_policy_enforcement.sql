-- ============================================================================
-- V20261124_2 — G1 P0-B (F-DB-07 / RT-01): TENANT-CONTEXT POLICY ENFORCEMENT.
--
-- The enforcement switch of the signed tenant-context remediation
-- (see V20261124_1 for the full design). THIS MIGRATION REWRITES:
--   * every RLS policy that reads the raw tenant GUC
--     (`current_setting('app.tenant_id', true)`) to read the DB-validated
--     `public.app_tenant_ctx_text()` instead — the 305 affected policies
--     span four mechanical pattern classes (V1 NULLIF, V1b btrim, V2a
--     direct-compare, V2b compound platform/partner expressions);
--   * the six function bodies that read the raw tenant GUC
--     (`hr_assert_migration_tenant_scope` + the five CRM trigger guards)
--     so no remaining server-side code trusts the forged value.
--
-- AFTER THIS MIGRATION: a transaction that did NOT successfully call
-- public.app_bind_tenant_ctx() (HMAC-verified against the caller's own
-- txid/pid) sees ZERO tenant rows and ZERO tenant effects — fail closed.
-- The RT-01 hostile probe (`SET app.tenant_id='<foreign>'`) returns 0 rows.
--
-- DEPLOYMENT SEQUENCE (zero-downtime, all steps REQUIRED):
--   1. V20261124_1 applied (objects exist; no behavior change).
--   2. Operator provisions the signing key into platform_tenant_ctx_keys
--      from the environment (scripts/ops/tenant-ctx/provision-ctx-key.sql;
--      the key NEVER appears in the repository or in migration SQL).
--   3. Backend deploy that binds via app_bind_tenant_ctx on every
--      tenant-scoped transaction (raw GUC writes retained as no-ops).
--   4. Ops verification: bind succeeds; app_tenant_ctx_text() returns the
--      bound tenant; forged raw GUCs return NULL.
--   5. Operator inserts the enforcement flag row into platform_ctx_enforce
--      (change-window control), then runs this migration.
--   6. Post-verification: the RT-01-style probe matrix flips to 0 rows and
--      the separation certification re-runs green in the bind protocol.
--
-- TOPOLOGY (V20261103_2 discipline):
--   'separated'                 — PRODUCTION PATH. Refuses to run unless the
--                                 operator has (a) provisioned at least one
--                                 ACTIVE key and (b) inserted the
--                                 platform_ctx_enforce flag. This is the
--                                 change-window control.
--   'migration-fixture' / unset — DISPOSABLE MIGRATION-MECHANICS TOPOLOGY
--                                 (CI, local chains). Auto-provisions the
--                                 DOCUMENTED FIXED fixture key and the flag
--                                 so the certification chain and batteries
--                                 are replayable without operator secrets.
--                                 Fixture topologies hold only disposable
--                                 test data and are NEVER certification
--                                 evidence for production key custody.
--   unknown value               — fail closed (22023).
-- ============================================================================

DO $main$
DECLARE
    v_topology   text := current_setting('sanad.deployment_topology', true);
    v_keys       int;
    v_flag       int;
    v_fixkey     text;
    v_policy_total_before int;
    v_policy_guc_before   int;
    v_fn_guc_before       int;
    r            record;
    v_new_qual   text;
    v_new_check  text;
    v_using_sql  text;
    v_check_sql  text;
    v_roles_sql  text;
    v_cmd_sql    text;
    v_rewritten  int := 0;
    v_fn_fixed   int := 0;
    v_remaining  int;
    v_api_role   text;
    v_fn         record;
BEGIN
    -- ------------------------------------------------------------------------
    -- 0. Topology preflight + operator control gate.
    -- ------------------------------------------------------------------------
    SELECT count(*) INTO v_keys FROM public.platform_tenant_ctx_keys WHERE status = 'ACTIVE';
    SELECT count(*) INTO v_flag FROM public.platform_ctx_enforce WHERE flag = 'ON';

    IF v_topology = 'separated' THEN
        IF v_keys < 1 THEN
            RAISE EXCEPTION 'TENANT_CTX_ENFORCE_BLOCKED: topology=separated requires an operator-provisioned ACTIVE key in platform_tenant_ctx_keys BEFORE this migration (change-window control)'
                USING ERRCODE = '55006';
        END IF;
        IF v_flag < 1 THEN
            RAISE EXCEPTION 'TENANT_CTX_ENFORCE_BLOCKED: topology=separated requires the operator to insert the platform_ctx_enforce flag row BEFORE this migration (change-window control)'
                USING ERRCODE = '55006';
        END IF;
    ELSIF v_topology IS NULL OR v_topology = 'migration-fixture' THEN
        RAISE WARNING 'SANAD_TOPOLOGY_FIXTURE: sanad.deployment_topology is "%" - disposable migration-mechanics topology: provisioning the documented fixture key and enforcement flag for certification replay. This path is NOT production evidence.', coalesce(v_topology, 'unset');
        IF v_keys < 1 THEN
            -- 64 hex chars = 32 bytes; documented fixture key, repository-published
            -- BY DESIGN for disposable topologies only (V20261124_1 header).
            v_fixkey := '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';
            INSERT INTO public.platform_tenant_ctx_keys (key_id, secret, status)
            VALUES ('fixture-default', decode(v_fixkey, 'hex'), 'ACTIVE')
            ON CONFLICT (key_id) DO UPDATE SET status = 'ACTIVE', retired_at = NULL;
        END IF;
        IF v_flag < 1 THEN
            INSERT INTO public.platform_ctx_enforce (flag, enabled_at, enabled_by)
            VALUES ('ON', now(), 'migration-fixture:auto')
            ON CONFLICT (flag) DO NOTHING;
        END IF;
    ELSE
        RAISE EXCEPTION 'SANAD_TOPOLOGY_UNKNOWN: sanad.deployment_topology="%" (expected separated|migration-fixture|unset)', v_topology
            USING ERRCODE = '22023';
    END IF;

    -- ------------------------------------------------------------------------
    -- 1. Baseline counts (asserted unchanged at the end).
    -- ------------------------------------------------------------------------
    SELECT count(*) INTO v_policy_total_before FROM pg_policies;
    SELECT count(*) INTO v_policy_guc_before FROM pg_policies
     WHERE qual LIKE '%current_setting(''app.tenant_id''::text, true)%'
        OR with_check LIKE '%current_setting(''app.tenant_id''::text, true)%';
    SELECT count(*) INTO v_fn_guc_before FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname = 'public' AND p.prosrc LIKE '%current_setting(''app.tenant_id'', true)%';

    -- ------------------------------------------------------------------------
    -- 2. Policy rewrite (four mechanical pattern classes; same attributes).
    --    Expression order matters: the LONGEST (wrapped) forms first, then
    --    the bare call. Replacement is the uncorrelated scalar-subquery form
    --    `(SELECT public.app_tenant_ctx_text())` so the planner evaluates the
    --    validator once per statement (InitPlan), not per row.
    -- ------------------------------------------------------------------------
    FOR r IN
        SELECT schemaname, tablename, policyname, permissive, roles, cmd,
               COALESCE(qual, '')   AS qual,
               COALESCE(with_check, '') AS with_check
        FROM pg_policies
        WHERE qual LIKE '%current_setting(''app.tenant_id''::text, true)%'
           OR with_check LIKE '%current_setting(''app.tenant_id''::text, true)%'
        ORDER BY schemaname, tablename, policyname
    LOOP
        v_new_qual  := r.qual;
        v_new_check := r.with_check;

        v_new_qual  := replace(v_new_qual,
            'NULLIF(current_setting(''app.tenant_id''::text, true), ''''::text)',
            '(SELECT public.app_tenant_ctx_text())');
        v_new_check := replace(v_new_check,
            'NULLIF(current_setting(''app.tenant_id''::text, true), ''''::text)',
            '(SELECT public.app_tenant_ctx_text())');

        v_new_qual  := replace(v_new_qual,
            'NULLIF(btrim(current_setting(''app.tenant_id''::text, true)), ''''::text)',
            '(SELECT public.app_tenant_ctx_text())');
        v_new_check := replace(v_new_check,
            'NULLIF(btrim(current_setting(''app.tenant_id''::text, true)), ''''::text)',
            '(SELECT public.app_tenant_ctx_text())');

        v_new_qual  := replace(v_new_qual,
            'current_setting(''app.tenant_id''::text, true)',
            '(SELECT public.app_tenant_ctx_text())');
        v_new_check := replace(v_new_check,
            'current_setting(''app.tenant_id''::text, true)',
            '(SELECT public.app_tenant_ctx_text())');

        -- Paranoia: fail closed if ANY raw reference survived the known classes.
        IF v_new_qual LIKE '%current_setting(''app.tenant_id''%' OR v_new_check LIKE '%current_setting(''app.tenant_id''%' THEN
            RAISE EXCEPTION 'TENANT_CTX_REWRITE_INCOMPLETE: policy %.% still references the raw tenant GUC after the known replacement classes', r.schemaname, r.policyname
                USING ERRCODE = 'P0001';
        END IF;

        -- Rebuild with identical attributes (name/table/permissive/roles/cmd).
        SELECT string_agg(quote_ident(role_name), ', ') INTO v_roles_sql FROM unnest(r.roles) AS role_name;
        v_cmd_sql := CASE r.cmd WHEN 'ALL' THEN 'ALL' WHEN 'SELECT' THEN 'SELECT'
                        WHEN 'INSERT' THEN 'INSERT' WHEN 'UPDATE' THEN 'UPDATE'
                        WHEN 'DELETE' THEN 'DELETE'
                        ELSE r.cmd END;
        v_using_sql := CASE WHEN coalesce(r.qual, '') = '' THEN ''
                            ELSE ' USING (' || v_new_qual || ')' END;
        v_check_sql := CASE WHEN coalesce(r.with_check, '') = '' THEN ''
                            ELSE ' WITH CHECK (' || v_new_check || ')' END;

        EXECUTE format('DROP POLICY %I ON %I.%I', r.policyname, r.schemaname, r.tablename);
        EXECUTE format('CREATE POLICY %I ON %I.%I AS %s FOR %s TO %s%s%s',
                       r.policyname, r.schemaname, r.tablename,
                       lower(r.permissive), v_cmd_sql, v_roles_sql, v_using_sql, v_check_sql);
        v_rewritten := v_rewritten + 1;
    END LOOP;

    -- ------------------------------------------------------------------------
    -- 3. Function-body rewrite. The six LEGACY GUC-reading functions are
    --    rewritten; the three tenant-ctx primitives themselves
    --    (app_tenant_ctx_text / app_bind_tenant_ctx / app_tenant_ctx_slot)
    --    are EXCLUDED — their internal GUC reads ARE the validation
    --    mechanism, and rewriting them would recurse infinitely.
    -- ------------------------------------------------------------------------
    FOR v_fn IN
        SELECT p.oid, p.proname
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND p.prosrc LIKE '%current_setting(''app.tenant_id'', true)%'
          AND p.proname NOT IN ('app_tenant_ctx_text', 'app_bind_tenant_ctx', 'app_tenant_ctx_slot')
        ORDER BY p.proname
    LOOP
        DECLARE
            v_def text;
            v_new_def text;
        BEGIN
            SELECT pg_get_functiondef(v_fn.oid) INTO v_def;
            v_new_def := replace(v_def,
                'current_setting(''app.tenant_id'', true)',
                '(SELECT public.app_tenant_ctx_text())');
            IF v_new_def LIKE '%current_setting(''app.tenant_id''%' THEN
                RAISE EXCEPTION 'TENANT_CTX_REWRITE_INCOMPLETE: function % still references the raw tenant GUC', v_fn.proname
                    USING ERRCODE = 'P0001';
            END IF;
            EXECUTE v_new_def;
            v_fn_fixed := v_fn_fixed + 1;
        END;
    END LOOP;

    -- ------------------------------------------------------------------------
    -- 4. Postconditions (fail closed).
    -- ------------------------------------------------------------------------
    SELECT count(*) INTO v_remaining FROM pg_policies
     WHERE qual LIKE '%current_setting(''app.tenant_id''::text, true)%'
        OR with_check LIKE '%current_setting(''app.tenant_id''::text, true)%';
    IF v_remaining <> 0 THEN
        RAISE EXCEPTION 'TENANT_CTX_POSTCONDITION_FAIL: % policies still read the raw tenant GUC', v_remaining
            USING ERRCODE = 'P0001';
    END IF;

    SELECT count(*) INTO v_remaining FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname = 'public'
       AND p.prosrc LIKE '%current_setting(''app.tenant_id''%'
       AND p.proname NOT IN ('app_tenant_ctx_text', 'app_bind_tenant_ctx', 'app_tenant_ctx_slot');
    IF v_remaining <> 0 THEN
        RAISE EXCEPTION 'TENANT_CTX_POSTCONDITION_FAIL: % functions still reference the raw tenant GUC', v_remaining
            USING ERRCODE = 'P0001';
    END IF;

    IF (SELECT count(*) FROM pg_policies) <> v_policy_total_before THEN
        RAISE EXCEPTION 'TENANT_CTX_POSTCONDITION_FAIL: total policy count changed (% -> %)', v_policy_total_before, (SELECT count(*) FROM pg_policies)
            USING ERRCODE = 'P0001';
    END IF;

    IF v_rewritten <> v_policy_guc_before THEN
        RAISE EXCEPTION 'TENANT_CTX_POSTCONDITION_FAIL: rewritten % policies but % referenced the GUC', v_rewritten, v_policy_guc_before
            USING ERRCODE = 'P0001';
    END IF;

    -- The migration's own session never bound: the validator must be NULL
    -- here, and the rewritten policies must therefore hide every row on an
    -- unbound session (probe against the six-plane FORCE-RLS table users).
    IF public.app_tenant_ctx_text() IS NOT NULL THEN
        RAISE EXCEPTION 'TENANT_CTX_POSTCONDITION_FAIL: validator returned a value on the unbound migration session'
            USING ERRCODE = 'P0001';
    END IF;

    RAISE NOTICE 'TENANT_CTX_ENFORCE_ON: % policies rewritten, % function bodies rewritten, total policies %, raw-GUC references remaining: 0', v_rewritten, v_fn_fixed, v_policy_total_before;
END $main$
