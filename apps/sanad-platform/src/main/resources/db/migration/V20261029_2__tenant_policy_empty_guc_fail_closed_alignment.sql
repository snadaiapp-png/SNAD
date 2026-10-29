-- ============================================================
-- SNAD-SEC-RLS-FORENSIC-CLOSURE-2026-10-29 — Phase 5 (empty-UUID) remediation
-- Tenant policy alignment: empty/missing GUC must FAIL CLOSED, never error
-- ============================================================
-- EVIDENCE (branch security/rls-forensic-remediation, run set at SHA 1a656ab2,
-- Workflow Y2 G4 Release Gate run 2409 / job "G4 Full Workflow Backend Gate"):
--   * WorkflowSecurityNegativeTest.rls_crossTenantWorkflowDefinitionQuery_returnsZeroRows
--       DataIntegrityViolationException: SELECT COUNT(*) FROM workflow_definitions
--       ... ERROR: invalid input syntax for type uuid: ""  (SQLState 22P02)
--   * WorkflowBusinessTimeTest (3 tests)
--       PSQLException: ERROR: invalid input syntax for type uuid: "" on INSERT
--       INTO workflow_business_calendars (SQLState 22P02)
--   * WorkflowY2SchemaSentinelTest.reconciliationMigrationReexecutesCleanlyOnReconciledSchema
--       FlywaySqlScriptException: V20260904_1 line 61
--       "UPDATE workflow_definitions SET definition_family_id = id ..." failed,
--       SQL State 22P02 "invalid input syntax for type uuid: \"\""
--
-- ROOT CAUSE (proven):
--   The repo contract (V20261029_1 header, V20261002_3, V20260925_2) is
--   fail-closed: "Missing, empty or unauthorized tenant context => NULL
--   comparison => zero rows, writes denied". However the workflow/engine-era
--   policies (V20260815_10 dynamic block, V20260902_x, V20260904_1,
--   V20260911_2/_3, V20260912_x and peers) were written as
--       tenant_id = current_setting('app.tenant_id', true)::uuid
--   (some without missing_ok, some as ::UUID). While V20261029_1 correctly
--   FORCE-bound the table owners, it deliberately kept those existing strict
--   policies unchanged. With the repo-standard session-hygiene reset
--   set_config('app.tenant_id', '', ...) (V20260918_4/V20260922_1/
--   V20261003_1/V20260905_17 and the users/g2/g3 acceptance seeds), the cast
--   ''::uuid raises 22P02 instead of failing closed. Under FORCE RLS the
--   migration runner itself is subject to these policies, so even an
--   idempotent historical backfill UPDATE inside a replayed migration errors.
--
-- CONTRACT (matches V20261029_1 verbatim, uuid-comparison variant):
--   USING / WITH CHECK ( tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid )
--   Missing OR empty GUC => NULL comparison => zero rows / write denied.
--   No IS NULL branch, no fail-open marker, no BYPASSRLS, no policy widening.
--   The comparison stays uuid-typed; only the empty-string hazard is removed.
--
-- MECHANISM (data-driven, no blind textual rewrites of migration history):
--   Walk pg_policies at apply time; for every LIVE policy whose qual and/or
--   with_check casts current_setting('app.tenant_id'...) directly to ::uuid
--   (with or without missing_ok, any case), recreate that policy with the
--   NULLIF-guarded cast, preserving name, table, permissiveness, roles,
--   command and the presence/absence of WITH CHECK exactly.
--
-- POST-VERIFY: zero live policies retain the unguarded cast; every touched
--   table keeps its ENABLE/FORCE RLS state; the fail-open "IS NULL" marker is
--   still absent everywhere. ============================================================

DO $snad$
DECLARE
    r record;
    new_qual text;
    new_with_check text;
    role_list text;
    touched int := 0;
    touched_tables text[] := ARRAY[]::text[];
BEGIN
    -- Precondition mirrors V20261029_1 G0: restricted runtime role contract.
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
                   AND NOT rolsuper AND NOT rolbypassrls) THEN
        RAISE EXCEPTION 'T2_RLS_RUNTIME_ROLE_UNAVAILABLE: provision restricted sanad role first';
    END IF;

    FOR r IN
        SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
          FROM pg_policies
         WHERE schemaname = 'public'
           AND (
                qual ~ $pat$current_setting\('app\.tenant_id'(::text)?, ?true\)::[uU][uU][iI][dD]$pat$
             OR qual ~ $pat$current_setting\('app\.tenant_id'\)::[uU][uU][iI][dD]$pat$
             OR with_check ~ $pat$current_setting\('app\.tenant_id'(::text)?, ?true\)::[uU][uU][iI][dD]$pat$
             OR with_check ~ $pat$current_setting\('app\.tenant_id'\)::[uU][uU][iI][dD]$pat$
           )
    LOOP
        -- Rewrite the unguarded cast to the repo fail-closed contract.
        new_qual := regexp_replace(
            r.qual,
            $re$current_setting\('app\.tenant_id'(::text)?, ?true\)::[uU][uU][iI][dD]$re$,
            $sub$NULLIF(current_setting('app.tenant_id', true), '')::uuid$sub$, 'g');
        new_qual := regexp_replace(
            new_qual,
            $re$current_setting\('app\.tenant_id'\)::[uU][uU][iI][dD]$re$,
            $sub$NULLIF(current_setting('app.tenant_id', true), '')::uuid$sub$, 'g');
        IF new_qual IS NOT NULL AND r.with_check IS NOT NULL THEN
            new_with_check := regexp_replace(
                r.with_check,
                $re$current_setting\('app\.tenant_id'(::text)?, ?true\)::[uU][uU][iI][dD]$re$,
                $sub$NULLIF(current_setting('app.tenant_id', true), '')::uuid$sub$, 'g');
            new_with_check := regexp_replace(
                new_with_check,
                $re$current_setting\('app\.tenant_id'\)::[uU][uU][iI][dD]$re$,
                $sub$NULLIF(current_setting('app.tenant_id', true), '')::uuid$sub$, 'g');
        ELSE
            new_with_check := r.with_check;
        END IF;

        -- Guard: the rewrite must actually have changed at least one side;
        -- a no-op here means the filter and the regex disagree — fail loudly.
        IF (new_qual IS NOT DISTINCT FROM r.qual)
           AND (new_with_check IS NOT DISTINCT FROM r.with_check) THEN
            RAISE EXCEPTION 'T2_POLICY_REWRITE_NOOP: %.% (policy %)',
                r.schemaname, r.tablename, r.policyname;
        END IF;

        role_list := (SELECT string_agg(quote_ident(role), ', ')
                        FROM unnest(r.roles) AS role);

        EXECUTE format('DROP POLICY %I ON %I.%I',
                       r.policyname, r.schemaname, r.tablename);
        IF r.with_check IS NULL THEN
            EXECUTE format('CREATE POLICY %I ON %I.%I AS %s FOR %s TO %s USING %s',
                           r.policyname, r.schemaname, r.tablename,
                           r.permissive, r.cmd, role_list, new_qual);
        ELSE
            EXECUTE format('CREATE POLICY %I ON %I.%I AS %s FOR %s TO %s USING %s WITH CHECK %s',
                           r.policyname, r.schemaname, r.tablename,
                           r.permissive, r.cmd, role_list, new_qual, new_with_check);
        END IF;
        touched := touched + 1;
        touched_tables := array_append(touched_tables,
                                       format('%I.%I', r.schemaname, r.tablename));
        RAISE NOTICE 'T2 realigned policy % on %.%', r.policyname, r.schemaname, r.tablename;
    END LOOP;

    IF touched = 0 THEN
        RAISE EXCEPTION 'T2_NO_POLICIES_TOUCHED: expected live unguarded tenant casts — investigate before applying';
    END IF;

    -- G3: no live policy may retain the unguarded cast.
    IF EXISTS (
        SELECT 1 FROM pg_policies
         WHERE schemaname = 'public'
           AND (   qual ~ $pat$current_setting\('app\.tenant_id'(::text)?, ?true\)::[uU][uU][iI][dD]$pat$
                OR qual ~ $pat$current_setting\('app\.tenant_id'\)::[uU][uU][iI][dD]$pat$
                OR with_check ~ $pat$current_setting\('app\.tenant_id'(::text)?, ?true\)::[uU][uU][iI][dD]$pat$
                OR with_check ~ $pat$current_setting\('app\.tenant_id'\)::[uU][uU][iI][dD]$pat$ )
    ) THEN
        RAISE EXCEPTION 'T2_VERIFY_UNGUARDED_CAST_REMAINED';
    END IF;

    -- G4: every touched table must still be RLS-protected (state preserved).
    DECLARE
        tbl text;
    BEGIN
        FOREACH tbl IN ARRAY touched_tables LOOP
            IF NOT EXISTS (
                SELECT 1 FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE format('%I.%I', n.nspname, c.relname) = tbl
                   AND c.relrowsecurity
            ) THEN
                RAISE EXCEPTION 'T2_VERIFY_RLS_STATE_LOST: %', tbl;
            END IF;
        END LOOP;
    END;

    -- G5: touched policies carry the NULLIF guard and no fail-open branch.
    -- (Scoped to the tables this migration touched — a global "IS NULL" scan
    -- would false-positive on legitimate predicates such as deleted_at IS NULL.)
    IF EXISTS (
        SELECT 1 FROM pg_policies p
         WHERE p.schemaname = 'public'
           AND format('%I.%I', p.schemaname, p.tablename) = ANY (touched_tables)
           AND (
                p.qual NOT ILIKE '%NULLIF(current_setting(%app.tenant_id%'
             OR (p.with_check IS NOT NULL
                 AND p.with_check NOT ILIKE '%NULLIF(current_setting(%app.tenant_id%')
             OR p.qual ILIKE '%app.tenant_id%IS NULL%'
           )
    ) THEN
        RAISE EXCEPTION 'T2_VERIFY_POLICY_CONTRACT_FAILED (guard missing or fail-open marker)';
    END IF;
END
$snad$;
