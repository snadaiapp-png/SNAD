-- ============================================================================
-- V20261103_2 — P0 MIGRATOR/RUNTIME ROLE SEPARATION (Issue #1462):
--               ownership transfer to `sanad_migrator` + evidence-derived
--               least-privilege runtime ACL for `sanad` (also closes F-01).
--
-- Mandate: SANAD P0 FINAL REMEDIATION executive order, Article 3.
-- Depends on: V20261103_1 (role contract).
--
-- DEPLOYMENT TOPOLOGY MODEL (declared per-database by the provisioning actor,
--   read via the GUC `sanad.deployment_topology`):
--
--   'separated'        TARGET MODEL. The database declares that the migrator
--                      identity owns the schema. Requirements enforced here:
--                        * the Flyway runner MUST be able to act as
--                          sanad_migrator (superuser or member or the role
--                          itself) - otherwise this migration FAILS CLOSED;
--                        * all non-extension public-schema objects (tables,
--                          partitioned tables, sequences, views, materialized
--                          views, functions, procedures) are transferred to
--                          sanad_migrator;
--                        * the runtime role sanad receives the exact DML-only
--                          ACL matrix below and loses CREATE on schema public;
--                        * flyway_schema_history becomes unreadable/unnecessary
--                          for the runtime role (ALL revoked);
--                        * default privileges for sanad_migrator grant the
--                          runtime matrix to future migrator-created objects.
--
--   'migration-fixture' (or unset)  DISPOSABLE MIGRATION-MECHANICS TOPOLOGY
--                      (CI test_migration sandboxes, local dev). The runner
--                      owns what it creates (owner-as-runner) so that
--                      flyway.clean()/migrate() cycles keep working. This
--                      migration then only:
--                        * (re-)asserts the runtime grant matrix (no-op under
--                          ownership) and the RLS/role invariants;
--                        * emits a loud WARNING that ownership separation is
--                          NOT applied and that this path is NOT a separation
--                          certification path.
--                      Negative (denial) guarantees are asserted ONLY in the
--                      separated topology - under ownership they are
--                      structurally impossible (V20261102_2 documented the
--                      same skip for the same reason).
--
--   anything else      FAIL CLOSED (unknown topology value).
--
-- RUNTIME ACL MATRIX (evidence-derived, from the in-repo call-site audits of
-- V20261102_1/V20261102_2 plus docs/security/g1-final-closure/04 finding
-- F-01, which this migration closes):
--   PROTECTED SET (exact privileges - preserved bit-for-bit):
--     refresh_tokens            SELECT, INSERT, UPDATE, DELETE   (V20261102_1)
--     password_reset_tokens     SELECT, INSERT, UPDATE, DELETE   (V20261102_1)
--     auth_token_tenant_index   SELECT, INSERT, UPDATE, DELETE   (V20261101_1)
--     login_tenant_index        SELECT, INSERT, DELETE           (V20261031_1)
--     provisioning_jobs         SELECT, INSERT, UPDATE           (V20261102_2)
--     provisioning_job_steps    SELECT, INSERT, UPDATE           (V20261102_2)
--     subscription_commands     SELECT, INSERT  (append-only)    (V20261102_2)
--     tenant_subscriptions      SELECT, INSERT, UPDATE           (F-01 closure;
--                               DELETE has zero call sites - not granted)
--   EXCLUDED ENTIRELY:
--     flyway_schema_history     NO privilege for sanad (migration ledger is
--                               migrator-only; tampering proven possible
--                               under the owner-as-runtime model)
--   GENERAL SET (every other public table):
--     SELECT, INSERT, UPDATE, DELETE - the exact surface the application
--     previously exercised through ownership, minus TRUNCATE / REFERENCES /
--     TRIGGER / DDL / ownership. TRUNCATE is denied everywhere because it is
--     NOT subject to RLS.
--   SEQUENCES: USAGE, SELECT (serial/identity inserts).
--   ROUTINES: EXECUTE (triggers, pre-auth resolvers, policy helpers; sanad
--     is the server-side runtime identity and is not exposed to PostgREST
--     RPC surfaces, which are bound to anon/authenticated/service_role).
--
-- NO RLS weakening: ENABLE/FORCE states and policies are NOT touched.
-- No SECURITY DEFINER added, no BYPASSRLS, no new LOGIN, no REVOKE of the
-- Supabase service_role grant (V20261026_4 G3 preserved).
-- Forward-only. Idempotent. Never rewrites published history.
-- ============================================================================

DO $$
DECLARE
    v_topology   text := current_setting('sanad.deployment_topology', true);
    v_separated  boolean;
    v_obj        record;
    v_tbl        text;
    v_owner      text;
    v_bad        text;
    v_missing    int := 0;
    v_extra      int := 0;
    v_rls_bad    text;
    v_kind_sql   text;
BEGIN
    ------------------------------------------------------------------
    -- 0. Mode resolution (fail closed on unknown values)
    ------------------------------------------------------------------
    v_separated := (v_topology = 'separated');

    IF v_topology IS NULL OR v_topology = 'migration-fixture' THEN
        RAISE WARNING 'SANAD_TOPOLOGY_FIXTURE: sanad.deployment_topology is "%" - disposable migration-mechanics topology (owner-as-runner). Ownership separation is NOT applied on this database and this path is NOT a separation certification path.', coalesce(v_topology, 'unset');
    ELSIF NOT v_separated THEN
        RAISE EXCEPTION 'SANAD_TOPOLOGY_UNKNOWN: sanad.deployment_topology="%" (expected separated|migration-fixture|unset)', v_topology
            USING ERRCODE = '22023';
    END IF;

    IF v_separated AND NOT pg_has_role(current_user, 'sanad_migrator', 'USAGE') THEN
        RAISE EXCEPTION 'SANAD_TOPOLOGY_SEPARATED_REQUIRES_MIGRATOR: this database declares sanad.deployment_topology=separated but "%" cannot act as sanad_migrator. Run Flyway as sanad_migrator (docs/security/migrator-runtime-separation/RUNBOOK.md section 3).', current_user
            USING ERRCODE = '42501';
    END IF;

    ------------------------------------------------------------------
    -- 1. SEPARATED ONLY - ownership transfer to sanad_migrator
    ------------------------------------------------------------------
    IF v_separated THEN
        -- 1.1 schema public must be controllable by the migrator identity.
        --     Valid owners: sanad_migrator (direct) or pg_database_owner
        --     (the database owner must then be sanad_migrator). Anything
        --     else fails closed with actionable guidance.
        SELECT pg_get_userbyid(nspowner) INTO v_owner
          FROM pg_namespace WHERE nspname = 'public';

        IF v_owner NOT IN ('sanad_migrator', 'pg_database_owner') THEN
            RAISE EXCEPTION 'SANAD_SCHEMA_OWNER_PRECONDITION: schema public is owned by "%" - an admin must run: ALTER SCHEMA public OWNER TO sanad_migrator; (or make sanad_migrator the database owner) before this migration', v_owner
                USING ERRCODE = '42501';
        END IF;
        IF NOT has_schema_privilege('sanad_migrator', 'public', 'CREATE') THEN
            RAISE EXCEPTION 'SANAD_SCHEMA_CREATE_PRECONDITION: sanad_migrator lacks CREATE on schema public (owner="%" but privileges are missing)', v_owner
                USING ERRCODE = '42501';
        END IF;

        -- 1.2 tables / partitioned tables / sequences / views / matviews
        --     flyway_schema_history is EXCLUDED: taking an ACCESS EXCLUSIVE
        --     lock on it inside a Flyway migration deadlocks against Flyway's
        --     own schema-history lock connection (reproduced live: the
        --     migration blocks forever waiting on the relation lock). Its
        --     ownership is enforced by the dedicated guard in section 4.5
        --     (owner MUST already be sanad_migrator - fresh separated runs
        --     satisfy this because Flyway itself connects as sanad_migrator;
        --     upgrade-from-shared-topology runs require the runbook admin
        --     step: ALTER TABLE flyway_schema_history OWNER TO sanad_migrator).
        --     Sequences OWNED BY a table column (serial/identity, pg_depend
        --     deptype='a') are EXCLUDED: PostgreSQL forbids changing their
        --     owner directly ("cannot change owner of sequence linked to
        --     table") - they follow their owning table automatically.
        FOR v_obj IN
            SELECT c.relkind, c.relname
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'public'
               AND c.relkind IN ('r', 'p', 'S', 'v', 'm')
               AND c.relname <> 'flyway_schema_history'
               AND NOT EXISTS (SELECT 1 FROM pg_depend d
                                WHERE d.objid = c.oid AND d.deptype = 'e')
               AND NOT (c.relkind = 'S' AND EXISTS (
                    SELECT 1 FROM pg_depend d
                     WHERE d.objid = c.oid AND d.deptype = 'a'
                       AND d.classid = 'pg_class'::regclass))
        LOOP
            v_kind_sql := CASE v_obj.relkind
                              WHEN 'S' THEN 'SEQUENCE'
                              WHEN 'v' THEN 'VIEW'
                              WHEN 'm' THEN 'MATERIALIZED VIEW'
                              ELSE 'TABLE'
                          END;
            EXECUTE format('ALTER %s public.%I OWNER TO sanad_migrator', v_kind_sql, v_obj.relname);
        END LOOP;

        -- 1.3 functions / procedures (extension-owned excluded)
        FOR v_obj IN
            SELECT p.proname,
                   pg_get_function_identity_arguments(p.oid) AS args,
                   p.prokind
              FROM pg_proc p
              JOIN pg_namespace n ON n.oid = p.pronamespace
             WHERE n.nspname = 'public'
               AND NOT EXISTS (SELECT 1 FROM pg_depend d
                                WHERE d.objid = p.oid AND d.deptype = 'e')
        LOOP
            EXECUTE format('ALTER %s public.%I(%s) OWNER TO sanad_migrator',
                           CASE v_obj.prokind WHEN 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END,
                           v_obj.proname, v_obj.args);
        END LOOP;

        -- 1.4 hard reset of every runtime privilege (as the NEW owner this
        --     cancels grants made by ANY prior grantor - verified semantics
        --     on PostgreSQL 17.6: owner REVOKE cancels third-party grants).
        --     flyway_schema_history is EXCLUDED from the table sweep: an
        --     ACCESS EXCLUSIVE lock on it deadlocks against Flyway's own
        --     history lock connection (see 1.2). No REVOKE on it is needed:
        --     under every valid topology sanad never held grants on the
        --     ledger (V20261102_1 probe: 0 rows for grantee='sanad'); the
        --     no-privilege invariant is asserted by the section 4.5 guard.
        FOR v_tbl IN
            SELECT tablename FROM pg_tables
             WHERE schemaname = 'public'
               AND tablename <> 'flyway_schema_history'
        LOOP
            EXECUTE format('REVOKE ALL ON TABLE public.%I FROM sanad', v_tbl);
        END LOOP;
        REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM sanad;
        REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM sanad;
        REVOKE CREATE ON SCHEMA public FROM sanad;

        -- 1.5 defensive membership strip (no-op warning when absent)
        BEGIN
            EXECUTE 'REVOKE sanad_migrator FROM sanad';
        EXCEPTION WHEN OTHERS THEN
            NULL; -- membership absent or already revoked - desired state
        END;
    END IF;

    ------------------------------------------------------------------
    -- 2. Runtime ACL matrix (both topologies; under fixture ownership the
    --    explicit rows simply normalize, the effective set is unchanged)
    ------------------------------------------------------------------
    GRANT USAGE ON SCHEMA public TO sanad;

    -- 2.1 protected set (exact privileges)
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.refresh_tokens          TO sanad;
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.password_reset_tokens   TO sanad;
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.auth_token_tenant_index TO sanad;
    GRANT SELECT, INSERT, DELETE           ON TABLE public.login_tenant_index    TO sanad;
    GRANT SELECT, INSERT, UPDATE           ON TABLE public.provisioning_jobs     TO sanad;
    GRANT SELECT, INSERT, UPDATE           ON TABLE public.provisioning_job_steps TO sanad;
    GRANT SELECT, INSERT                   ON TABLE public.subscription_commands TO sanad;
    GRANT SELECT, INSERT, UPDATE           ON TABLE public.tenant_subscriptions  TO sanad; -- F-01

    -- 2.2 general set: every other public table except the migration ledger
    FOR v_tbl IN
        SELECT tablename FROM pg_tables
         WHERE schemaname = 'public'
           AND tablename NOT IN (
               'refresh_tokens', 'password_reset_tokens',
               'auth_token_tenant_index', 'login_tenant_index',
               'provisioning_jobs', 'provisioning_job_steps',
               'subscription_commands', 'tenant_subscriptions',
               'flyway_schema_history')
    LOOP
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.%I TO sanad', v_tbl);
    END LOOP;

    -- 2.3 sequences (serial/identity inserts)
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sanad;

    -- 2.4 routines (triggers, pre-auth resolvers, policy helpers)
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO sanad;

    ------------------------------------------------------------------
    -- 3. SEPARATED ONLY - default privileges for future migrator objects
    ------------------------------------------------------------------
    IF v_separated THEN
        ALTER DEFAULT PRIVILEGES FOR ROLE sanad_migrator IN SCHEMA public
            GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sanad;
        ALTER DEFAULT PRIVILEGES FOR ROLE sanad_migrator IN SCHEMA public
            GRANT USAGE, SELECT ON SEQUENCES TO sanad;
        ALTER DEFAULT PRIVILEGES FOR ROLE sanad_migrator IN SCHEMA public
            GRANT EXECUTE ON FUNCTIONS TO sanad;
    END IF;

    ------------------------------------------------------------------
    -- 4. Exit guards (fail the migration when any invariant is broken)
    ------------------------------------------------------------------
    -- 4.1 runtime role contract (both topologies)
    IF EXISTS (SELECT 1 FROM pg_roles
                WHERE rolname = 'sanad'
                  AND (rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole)) THEN
        RAISE EXCEPTION 'SANAD_RUNTIME_ACL_VERIFY: sanad contract violation (super/bypassrls/createdb/createrole)';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles
                WHERE rolname = 'sanad_migrator'
                  AND (rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole)) THEN
        RAISE EXCEPTION 'SANAD_RUNTIME_ACL_VERIFY: sanad_migrator contract violation';
    END IF;
    IF pg_has_role('sanad', 'sanad_migrator', 'MEMBER') THEN
        RAISE EXCEPTION 'SANAD_RUNTIME_ACL_VERIFY: sanad must not be a member of sanad_migrator';
    END IF;

    -- 4.2 protected set - exact positive surface (both topologies)
    IF NOT (has_table_privilege('sanad', 'public.refresh_tokens', 'SELECT')
        AND has_table_privilege('sanad', 'public.refresh_tokens', 'INSERT')
        AND has_table_privilege('sanad', 'public.refresh_tokens', 'UPDATE')
        AND has_table_privilege('sanad', 'public.refresh_tokens', 'DELETE')
        AND has_table_privilege('sanad', 'public.password_reset_tokens', 'SELECT')
        AND has_table_privilege('sanad', 'public.password_reset_tokens', 'INSERT')
        AND has_table_privilege('sanad', 'public.password_reset_tokens', 'UPDATE')
        AND has_table_privilege('sanad', 'public.password_reset_tokens', 'DELETE')
        AND has_table_privilege('sanad', 'public.auth_token_tenant_index', 'SELECT')
        AND has_table_privilege('sanad', 'public.auth_token_tenant_index', 'INSERT')
        AND has_table_privilege('sanad', 'public.auth_token_tenant_index', 'UPDATE')
        AND has_table_privilege('sanad', 'public.auth_token_tenant_index', 'DELETE')
        AND has_table_privilege('sanad', 'public.login_tenant_index', 'SELECT')
        AND has_table_privilege('sanad', 'public.login_tenant_index', 'INSERT')
        AND has_table_privilege('sanad', 'public.login_tenant_index', 'DELETE')
        AND has_table_privilege('sanad', 'public.provisioning_jobs', 'SELECT')
        AND has_table_privilege('sanad', 'public.provisioning_jobs', 'INSERT')
        AND has_table_privilege('sanad', 'public.provisioning_jobs', 'UPDATE')
        AND has_table_privilege('sanad', 'public.provisioning_job_steps', 'SELECT')
        AND has_table_privilege('sanad', 'public.provisioning_job_steps', 'INSERT')
        AND has_table_privilege('sanad', 'public.provisioning_job_steps', 'UPDATE')
        AND has_table_privilege('sanad', 'public.subscription_commands', 'SELECT')
        AND has_table_privilege('sanad', 'public.subscription_commands', 'INSERT')
        AND has_table_privilege('sanad', 'public.tenant_subscriptions', 'SELECT')
        AND has_table_privilege('sanad', 'public.tenant_subscriptions', 'INSERT')
        AND has_table_privilege('sanad', 'public.tenant_subscriptions', 'UPDATE')) THEN
        RAISE EXCEPTION 'SANAD_RUNTIME_ACL_VERIFY: protected-set DML surface incomplete';
    END IF;

    -- 4.3 schema USAGE + sequence/routine surface (both topologies)
    IF NOT has_schema_privilege('sanad', 'public', 'USAGE') THEN
        RAISE EXCEPTION 'SANAD_RUNTIME_ACL_VERIFY: sanad lacks USAGE on schema public';
    END IF;

    -- 4.4 RLS must remain ENABLE + FORCE on the guarded planes (both
    --     topologies; mirrors V20261102_1/V20261102_2 guards)
    FOREACH v_tbl IN ARRAY ARRAY['users', 'refresh_tokens', 'password_reset_tokens',
                                 'provisioning_jobs', 'provisioning_job_steps',
                                 'subscription_commands'] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_class
             WHERE oid = to_regclass('public.' || v_tbl)
               AND relrowsecurity AND relforcerowsecurity) THEN
            RAISE EXCEPTION 'SANAD_RUNTIME_ACL_VERIFY: FORCE RLS must remain on %', v_tbl;
        END IF;
    END LOOP;

    -- 4.5 SEPARATED ONLY - the separation invariants (negatives)
    IF v_separated THEN
        -- no runtime privilege on the migration ledger
        IF EXISTS (SELECT 1 FROM unnest(ARRAY['SELECT','INSERT','UPDATE','DELETE','TRUNCATE','REFERENCES','TRIGGER']) AS p(priv)
                    WHERE has_table_privilege('sanad', 'public.flyway_schema_history', p.priv)) THEN
            RAISE EXCEPTION 'SANAD_RUNTIME_ACL_VERIFY: sanad must hold NO privilege on flyway_schema_history';
        END IF;

        -- no CREATE on schema public for the runtime identity
        IF has_schema_privilege('sanad', 'public', 'CREATE') THEN
            RAISE EXCEPTION 'SANAD_RUNTIME_ACL_VERIFY: sanad must not hold CREATE on schema public';
        END IF;

        -- every non-extension public relation owned by sanad_migrator
        -- (flyway_schema_history excluded from the sweep - see section 1.2;
        --  it has its own dedicated guard immediately below)
        SELECT string_agg(s.relname, ', ') INTO v_bad
          FROM (SELECT c.relname
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public'
                   AND c.relkind IN ('r', 'p', 'S', 'v', 'm')
                   AND c.relname <> 'flyway_schema_history'
                   AND pg_get_userbyid(c.relowner) <> 'sanad_migrator'
                   AND NOT EXISTS (SELECT 1 FROM pg_depend d
                                    WHERE d.objid = c.oid AND d.deptype = 'e')
                 LIMIT 3) s;
        IF v_bad IS NOT NULL THEN
            RAISE EXCEPTION 'SANAD_OWNERSHIP_VERIFY: non-extension relations not owned by sanad_migrator: %', v_bad;
        END IF;

        -- the migration ledger itself must be migrator-owned BEFORE this
        -- migration completes (fresh separated runs: Flyway connects as
        -- sanad_migrator and creates it accordingly; upgrade runs: the
        -- runbook admin step transferred it out-of-band). sanad must hold
        -- NO privilege on it (tampering was proven possible under the
        -- owner-as-runtime model).
        IF EXISTS (SELECT 1 FROM pg_class c
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                   WHERE n.nspname = 'public'
                     AND c.relname = 'flyway_schema_history'
                     AND pg_get_userbyid(c.relowner) <> 'sanad_migrator') THEN
            RAISE EXCEPTION 'SANAD_OWNERSHIP_VERIFY: flyway_schema_history must be owned by sanad_migrator (admin step: ALTER TABLE public.flyway_schema_history OWNER TO sanad_migrator;)'
                USING ERRCODE = '42501';
        END IF;

        -- every non-extension public routine owned by sanad_migrator
        SELECT string_agg(s.proname, ', ') INTO v_bad
          FROM (SELECT p.proname
                  FROM pg_proc p
                  JOIN pg_namespace n ON n.oid = p.pronamespace
                 WHERE n.nspname = 'public'
                   AND pg_get_userbyid(p.proowner) <> 'sanad_migrator'
                   AND NOT EXISTS (SELECT 1 FROM pg_depend d
                                    WHERE d.objid = p.oid AND d.deptype = 'e')
                 LIMIT 3) s;
        IF v_bad IS NOT NULL THEN
            RAISE EXCEPTION 'SANAD_OWNERSHIP_VERIFY: non-extension routines not owned by sanad_migrator: %', v_bad;
        END IF;

        -- schema public ownership
        SELECT pg_get_userbyid(nspowner) INTO v_owner
          FROM pg_namespace WHERE nspname = 'public';
        IF v_owner NOT IN ('sanad_migrator', 'pg_database_owner') THEN
            RAISE EXCEPTION 'SANAD_OWNERSHIP_VERIFY: schema public owner is %', v_owner;
        END IF;

        -- append-only ledger: UPDATE/DELETE must be denied on the ledger
        IF has_table_privilege('sanad', 'public.subscription_commands', 'UPDATE')
           OR has_table_privilege('sanad', 'public.subscription_commands', 'DELETE') THEN
            RAISE EXCEPTION 'SANAD_RUNTIME_ACL_VERIFY: subscription_commands must remain append-only for sanad';
        END IF;

        -- TRUNCATE denied everywhere (it bypasses RLS). The privilege
        -- probe is schema-qualified FROM THE ROW'S OWN COLUMNS: the planner
        -- may evaluate has_table_privilege() before the schemaname filter
        -- (predicate reordering), and a hardcoded 'public.' prefix would
        -- then probe non-existent relations like public.pg_statistic
        -- (reproduced live as SQLSTATE 42P01).
        SELECT string_agg(s.tablename, ', ') INTO v_bad
          FROM (SELECT schemaname, tablename FROM pg_tables
                 WHERE schemaname = 'public'
                   AND has_table_privilege('sanad',
                        quote_ident(schemaname) || '.' || quote_ident(tablename),
                        'TRUNCATE')
                 LIMIT 3) s;
        IF v_bad IS NOT NULL THEN
            RAISE EXCEPTION 'SANAD_RUNTIME_ACL_VERIFY: TRUNCATE must be denied for sanad on every table (found on: %)', v_bad;
        END IF;

        -- general set completeness: SELECT/INSERT/UPDATE/DELETE present on
        -- every non-protected, non-history public table (same row-qualified
        -- probe pattern as the TRUNCATE guard above)
        SELECT count(*) INTO v_missing
          FROM pg_tables
         WHERE schemaname = 'public'
           AND tablename NOT IN (
               'refresh_tokens', 'password_reset_tokens',
               'auth_token_tenant_index', 'login_tenant_index',
               'provisioning_jobs', 'provisioning_job_steps',
               'subscription_commands', 'tenant_subscriptions',
               'flyway_schema_history')
           AND NOT (has_table_privilege('sanad',
                        quote_ident(schemaname) || '.' || quote_ident(tablename), 'SELECT')
                AND has_table_privilege('sanad',
                        quote_ident(schemaname) || '.' || quote_ident(tablename), 'INSERT')
                AND has_table_privilege('sanad',
                        quote_ident(schemaname) || '.' || quote_ident(tablename), 'UPDATE')
                AND has_table_privilege('sanad',
                        quote_ident(schemaname) || '.' || quote_ident(tablename), 'DELETE'));
        IF v_missing > 0 THEN
            RAISE EXCEPTION 'SANAD_RUNTIME_ACL_VERIFY: % general-set table(s) missing the DML surface', v_missing;
        END IF;

        -- sequences (same row-qualified probe pattern)
        SELECT count(*) INTO v_missing
          FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public' AND c.relkind = 'S'
           AND NOT (has_sequence_privilege('sanad',
                        quote_ident(n.nspname) || '.' || quote_ident(c.relname), 'USAGE')
                AND has_sequence_privilege('sanad',
                        quote_ident(n.nspname) || '.' || quote_ident(c.relname), 'SELECT'));
        IF v_missing > 0 THEN
            RAISE EXCEPTION 'SANAD_RUNTIME_ACL_VERIFY: % sequence(s) missing USAGE/SELECT for sanad', v_missing;
        END IF;
    END IF;
END $$;
