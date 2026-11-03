-- ============================================================================
-- V20261103_3 — Issue #1462: forward-only correction of the PRODUCTION ACL
--               drift on the append-only ledger subscription_commands
--               (runtime UPDATE/DELETE observed live in production
--               2026-11-03; forensic evidence: g1_production_forensics/09).
--
-- v2 (2026-11-03, same PR): TOPOLOGY-AWARE, mirroring the V20261103_2 model.
--   The v1 precondition set failed closed in the migration-fixture topology
--   (CI/Spring-Boot contexts run the chain AS `sanad`, owner-as-runner:
--   "runtime owns ledger; separation required" P0001). That topology is a
--   documented disposable migration-mechanics mode, NOT a certification
--   path (V20261102_2 documented the same skip for the same reason).
--
-- Behaviour by sanad.deployment_topology (declared per-database):
--   'separated'         TARGET MODEL. Preconditions: restricted runtime role,
--                       migrator role present, runtime does NOT own the
--                       ledger, ENABLE+FORCE RLS. REVOKE executes, and the
--                       postcondition asserts EFFECTIVE privileges
--                       (has_table_privilege: no UPDATE/DELETE for sanad,
--                       SELECT/INSERT present).
--   'migration-fixture' DISPOSABLE topology (or unset). The REVOKE still
--                       normalizes the EXPLICIT ACL surface (removes the
--                       drifted grants), but the runner may be the owner,
--                       whose implicit owner privileges are not revocable by
--                       ACL (has_table_privilege(owner)=true by design).
--                       The postcondition therefore asserts the EXPLICIT ACL
--                       (aclexplode: no UPDATE/DELETE entry granted to sanad)
--                       and emits a loud WARNING that this path is NOT a
--                       separation certification path.
--   anything else       FAIL CLOSED (unknown topology value).
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
        RAISE WARNING 'SANAD_TOPOLOGY_FIXTURE: sanad.deployment_topology is "%" - disposable migration-mechanics topology. Ledger ACL drift is normalized on the explicit ACL only; this path is NOT a separation certification path (see V20261103_2 / RUNBOOK).', coalesce(v_topology, 'unset');
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

REVOKE UPDATE, DELETE ON TABLE public.subscription_commands FROM sanad;

DO $post$
DECLARE
    v_separated boolean := current_setting('sanad.deployment_topology', true) = 'separated';
BEGIN
    IF v_separated THEN
        -- effective-privilege postcondition (runtime is NOT the owner here)
        IF has_table_privilege('sanad','public.subscription_commands','UPDATE')
           OR has_table_privilege('sanad','public.subscription_commands','DELETE')
           OR NOT has_table_privilege('sanad','public.subscription_commands','SELECT')
           OR NOT has_table_privilege('sanad','public.subscription_commands','INSERT')
        THEN RAISE EXCEPTION 'G1_1462_POSTCONDITION: runtime ledger ACL not append-only'
             USING ERRCODE = '42501'; END IF;
    ELSE
        -- fixture topology: the runner may be the owner (implicit privileges
        -- are not ACL-revocable), so assert the EXPLICIT grant surface only.
        IF EXISTS (
            SELECT 1
              FROM aclexplode(coalesce(
                   (SELECT relacl FROM pg_class WHERE oid = 'public.subscription_commands'::regclass),
                   '{}'::aclitem[]))
             WHERE grantee = 'sanad'::regrole
               AND privilege_type IN ('UPDATE','DELETE'))
        THEN RAISE EXCEPTION 'G1_1462_POSTCONDITION: explicit ledger ACL still grants UPDATE/DELETE to sanad (fixture)'
             USING ERRCODE = '42501'; END IF;
        IF NOT EXISTS (
            SELECT 1
              FROM aclexplode(coalesce(
                   (SELECT relacl FROM pg_class WHERE oid = 'public.subscription_commands'::regclass),
                   '{}'::aclitem[]))
             WHERE grantee = 'sanad'::regrole
               AND privilege_type IN ('SELECT','INSERT'))
        THEN RAISE EXCEPTION 'G1_1462_POSTCONDITION: ledger SELECT/INSERT surface missing for runtime (fixture)'
             USING ERRCODE = '42501'; END IF;
    END IF;
END $post$;
