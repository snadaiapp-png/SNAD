-- ============================================================================
-- SANAD P0 (Issue #1462) — one-time admin step for the migrator/runtime
-- separation upgrade path. Execute ONCE per database, as the bootstrap
-- admin (superuser or the current shared-topology owner), BEFORE running
-- any pending Flyway migrations as sanad_migrator.
--
-- What it does:
--   * transfers ownership of every non-extension relation (tables,
--     partitioned tables, sequences, views, materialized views) and every
--     non-extension routine (functions, procedures) in schema public to
--     sanad_migrator;
--   * transfers schema public and flyway_schema_history ownership;
--   * is idempotent (re-running is a no-op).
--
-- Why this is required: pending migrations such as V20261026_4 ALTER
-- functions that were created by the previous (shared) migration identity.
-- Running them as sanad_migrator without this transfer fails closed with
-- SQLSTATE 42501 "must be owner of function ...".
--
-- This script performs NO grants and NO revokes: the runtime ACL matrix is
-- owned by migration V20261103_2. It changes ownership only.
--
-- After this step, declare the database topology and run Flyway as the
-- migrator (see docs/security/migrator-runtime-separation/RUNBOOK.md):
--   ALTER DATABASE <db> SET sanad.deployment_topology = 'separated';
-- ============================================================================

DO $transfer$
DECLARE
    r record;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_migrator') THEN
        RAISE EXCEPTION 'TRANSFER_PRECONDITION: cluster role sanad_migrator is not provisioned (RUNBOOK section 2)';
    END IF;

    FOR r IN
        SELECT c.relkind, c.relname
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public'
           AND c.relkind IN ('r', 'p', 'S', 'v', 'm')
           AND pg_get_userbyid(c.relowner) <> 'sanad_migrator'
           AND NOT EXISTS (SELECT 1 FROM pg_depend d
                            WHERE d.objid = c.oid AND d.deptype = 'e')
           -- sequences OWNED BY a table column follow their table's owner
           AND NOT (c.relkind = 'S' AND EXISTS (
                SELECT 1 FROM pg_depend d
                 WHERE d.objid = c.oid AND d.deptype = 'a'
                   AND d.classid = 'pg_class'::regclass))
    LOOP
        EXECUTE format('ALTER %s public.%I OWNER TO sanad_migrator',
                       CASE r.relkind
                            WHEN 'S' THEN 'SEQUENCE'
                            WHEN 'v' THEN 'VIEW'
                            WHEN 'm' THEN 'MATERIALIZED VIEW'
                            ELSE 'TABLE'
                       END, r.relname);
    END LOOP;

    FOR r IN
        SELECT p.proname,
               pg_get_function_identity_arguments(p.oid) AS args,
               p.prokind
          FROM pg_proc p
          JOIN pg_namespace n ON n.oid = p.pronamespace
         WHERE n.nspname = 'public'
           AND pg_get_userbyid(p.proowner) <> 'sanad_migrator'
           AND NOT EXISTS (SELECT 1 FROM pg_depend d
                            WHERE d.objid = p.oid AND d.deptype = 'e')
    LOOP
        EXECUTE format('ALTER %s public.%I(%s) OWNER TO sanad_migrator',
                       CASE r.prokind WHEN 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END,
                       r.proname, r.args);
    END LOOP;
END
$transfer$;

ALTER SCHEMA public OWNER TO sanad_migrator;
ALTER TABLE public.flyway_schema_history OWNER TO sanad_migrator;

-- Verification (fail closed): nothing non-extension in public may remain
-- owned by another role.
DO $verify$
DECLARE
    offenders text;
BEGIN
    SELECT string_agg(o, ', ') INTO offenders
      FROM (SELECT pg_get_userbyid(c.relowner) AS o
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'public'
               AND c.relkind IN ('r', 'p', 'S', 'v', 'm')
               AND pg_get_userbyid(c.relowner) <> 'sanad_migrator'
               AND NOT EXISTS (SELECT 1 FROM pg_depend d
                                WHERE d.objid = c.oid AND d.deptype = 'e')
             LIMIT 3) s;
    IF offenders IS NOT NULL THEN
        RAISE EXCEPTION 'TRANSFER_VERIFY: relations still owned by %', offenders;
    END IF;
END
$verify$;
