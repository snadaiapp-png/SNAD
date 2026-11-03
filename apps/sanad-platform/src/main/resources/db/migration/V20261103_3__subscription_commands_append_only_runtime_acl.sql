-- ============================================================================
-- V20261103_3 — Issue #1462: forward-only correction of the PRODUCTION ACL
--               drift on the append-only ledger subscription_commands
--               (runtime UPDATE/DELETE observed live in production
--               2026-11-03; forensic evidence: g1_production_forensics/09).
--
-- v3 (2026-11-03, same PR): fixture topology is now VALIDATE-ONLY.
--   v2 normalized the explicit ACL in the migration-fixture topology too,
--   which broke that topology's documented contract: the fixture runner IS
--   the owner (owner-as-runner), and a PostgreSQL self-REVOKE DOES restrict
--   the owner (verified live, PG 17.11 + CI PG 16.15: DELETE/UPDATE denied
--   with 42501 after the self-revoke). Downstream consequences reproduced in
--   CI: FK ON DELETE CASCADE from tenant_subscriptions into the ledger and
--   the tenants-side RI check (SELECT ... FOR KEY SHARE requires UPDATE)
--   both failed for the fixture runtime, breaking disposable-DB test
--   cleanability (R0C13G09*, PlatformUserPostgresAcceptanceTest cleanup).
--
-- Behaviour by sanad.deployment_topology (declared per-database):
--   'separated'         TARGET MODEL. Preconditions: restricted runtime role,
--                       migrator role present, runtime does NOT own the
--                       ledger, ENABLE+FORCE RLS. REVOKE executes, and the
--                       postcondition asserts EFFECTIVE privileges
--                       (has_table_privilege: no UPDATE/DELETE for sanad,
--                       SELECT/INSERT present). CI certification of the
--                       append-only denials: tests/security/migrator-
--                       separation/run-separation-cert.sh S6 (separated
--                       topology, real sanad connection).
--   'migration-fixture' DISPOSABLE topology (or unset). VALIDATE-ONLY: loud
--                       WARNING, preconditions asserted, NO ACL mutation.
--                       Under owner-as-runner the append-only ACL is
--                       structurally unenforceable (the owner can always
--                       re-grant) and enforcing it breaks FK cascade/RI
--                       semantics the fixture depends on — same documented
--                       skip class as V20261102_2 ("negative guarantees are
--                       asserted ONLY in the separated topology"). This
--                       path is NOT a separation certification path.
--   anything else       FAIL CLOSED (unknown topology value, 22023).
--
-- No RLS policies, data, or bypass privileges are changed. Forward-only.
-- Requires V20261103_1 and V20261103_2 to have been applied by Flyway.
-- ============================================================================

DO $pre$
DECLARE
    v_topology  text := current_setting('sanad.deployment_topology', true);
    v_separated boolean;
    v_owner     text;
BEGIN
    v_separated := (v_topology = 'separated');

    IF v_topology IS NULL OR v_topology = 'migration-fixture' THEN
        RAISE WARNING 'SANAD_TOPOLOGY_FIXTURE: sanad.deployment_topology is "%" - disposable migration-mechanics topology. Ledger ACL correction is SKIPPED here (validate-only): the fixture runner owns the schema, a self-REVOKE would restrict the owner and break FK cascade/RI cleanability, and the append-only guarantee is only enforceable in the separated topology (certified by run-separation-cert.sh S6). This path is NOT a separation certification path.', coalesce(v_topology, 'unset');
    ELSIF NOT v_separated THEN
        RAISE EXCEPTION 'G1_1462_TOPOLOGY: sanad.deployment_topology="%" (expected separated|migration-fixture|unset)', v_topology
            USING ERRCODE = '22023';
    END IF;

    -- common preconditions (both certified topologies)
    IF to_regclass('public.subscription_commands') IS NULL
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad' AND NOT rolsuper AND NOT rolbypassrls)
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad_migrator')
    THEN RAISE EXCEPTION 'G1_1462_PRECONDITION: table/runtime/migrator missing'
         USING ERRCODE = '42501'; END IF;

    SELECT pg_get_userbyid(relowner) INTO v_owner
      FROM pg_class WHERE oid = 'public.subscription_commands'::regclass;

    IF v_separated AND v_owner = 'sanad' THEN
        RAISE EXCEPTION 'G1_1462_PRECONDITION: runtime owns ledger; separation required'
            USING ERRCODE = '42501';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_class
                    WHERE oid = 'public.subscription_commands'::regclass
                      AND relrowsecurity AND relforcerowsecurity)
    THEN RAISE EXCEPTION 'G1_1462_PRECONDITION: ENABLE+FORCE RLS required'
         USING ERRCODE = '42501'; END IF;
END $pre$;

-- SEPARATED ONLY: correct the ACL drift (fixture topology: no ACL mutation).
DO $acl$
BEGIN
    IF current_setting('sanad.deployment_topology', true) = 'separated' THEN
        REVOKE UPDATE, DELETE ON TABLE public.subscription_commands FROM sanad;
    END IF;
END $acl$;

-- SEPARATED ONLY: effective-privilege postconditions (runtime is NOT the
-- owner in this topology; has_table_privilege reflects the real ACL).
DO $post$
BEGIN
    IF current_setting('sanad.deployment_topology', true) = 'separated' THEN
        IF has_table_privilege('sanad','public.subscription_commands','UPDATE')
           OR has_table_privilege('sanad','public.subscription_commands','DELETE')
           OR NOT has_table_privilege('sanad','public.subscription_commands','SELECT')
           OR NOT has_table_privilege('sanad','public.subscription_commands','INSERT')
        THEN RAISE EXCEPTION 'G1_1462_POSTCONDITION: runtime ledger ACL not append-only'
             USING ERRCODE = '42501'; END IF;
    END IF;
END $post$;
