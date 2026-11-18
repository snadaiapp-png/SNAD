-- ============================================================================
-- V20261115_1 — G1/C4: append-only runtime ACL on platform_audit_logs.
--
-- Closure context (docs/security/g1-final-closure/15, gate 4): among the 26
-- documented RLS-OFF public tables, `platform_audit_logs` granted runtime
-- `sanad` SELECT/INSERT/UPDATE/DELETE. An audit ledger must be append-only
-- for the runtime identity: tamper (UPDATE) and destruction (DELETE/
-- TRUNCATE) authority on audit evidence contradicts the ledger's purpose.
-- This migration closes that drift with the same forward-only,
-- topology-aware pattern as V20261103_3 (subscription_commands).
--
-- RLS NOTE: platform_audit_logs is one of the 26 documented RLS-OFF
-- exceptions (global audit plane). Whether it should carry RLS is a separate
-- C4 exception-review decision; this migration deliberately does NOT change
-- ENABLE/FORCE RLS, policies or ownership.
--
-- Behaviour by sanad.deployment_topology (declared per-database):
--   'separated'         TARGET MODEL. Preconditions: restricted runtime role,
--                       migrator role present, runtime does NOT own the
--                       table. REVOKE executes and the postcondition asserts
--                       EFFECTIVE privileges (has_table_privilege: no
--                       UPDATE/DELETE/TRUNCATE for sanad, SELECT/INSERT
--                       present). CI certification of the append-only
--                       denials: tests/security/migrator-separation/
--                       run-separation-cert.sh S9 (separated topology, real
--                       sanad connection).
--   'migration-fixture' DISPOSABLE topology (or unset). VALIDATE-ONLY: loud
--                       WARNING, preconditions asserted, NO ACL mutation.
--                       Under owner-as-runner the append-only ACL is
--                       structurally unenforceable (the owner can always
--                       re-grant) — same documented skip class as
--                       V20261102_2 / V20261103_2 / V20261103_3. This path
--                       is NOT a separation certification path, and the
--                       protected deployment gate
--                       (scripts/production/verify-append-only-gate.sh)
--                       fail-closes on it.
--   anything else       FAIL CLOSED (unknown topology value, 22023).
--
-- Flyway success alone is NOT proof of the security effect: the release
-- gate re-verifies EFFECTIVE catalog privileges (direct + inherited via
-- every member role) before a protected deployment may succeed.
--
-- No RLS policies, data, or bypass privileges are changed. Forward-only.
-- Requires V20261103_1/V20261103_2/V20261103_3 to have been applied.
-- ============================================================================

DO $pre$
DECLARE
    v_topology  text := current_setting('sanad.deployment_topology', true);
    v_separated boolean;
    v_owner     text;
BEGIN
    v_separated := (v_topology = 'separated');

    IF v_topology IS NULL OR v_topology = 'migration-fixture' THEN
        RAISE WARNING 'SANAD_TOPOLOGY_FIXTURE: sanad.deployment_topology is "%" - disposable migration-mechanics topology. Audit-ledger ACL correction is SKIPPED here (validate-only): the fixture runner owns the schema, a self-REVOKE would restrict the owner, and the append-only guarantee is only enforceable in the separated topology (certified by run-separation-cert.sh S9; enforced for deployments by scripts/production/verify-append-only-gate.sh). This path is NOT a separation certification path.', coalesce(v_topology, 'unset');
    ELSIF NOT v_separated THEN
        RAISE EXCEPTION 'G1_C4_TOPOLOGY: sanad.deployment_topology="%" (expected separated|migration-fixture|unset)', v_topology
            USING ERRCODE = '22023';
    END IF;

    -- common preconditions (both certified topologies)
    IF to_regclass('public.platform_audit_logs') IS NULL
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad' AND NOT rolsuper AND NOT rolbypassrls)
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad_migrator')
    THEN RAISE EXCEPTION 'G1_C4_PRECONDITION: audit table/runtime/migrator missing'
         USING ERRCODE = '42501'; END IF;

    SELECT pg_get_userbyid(relowner) INTO v_owner
      FROM pg_class WHERE oid = 'public.platform_audit_logs'::regclass;

    IF v_separated AND v_owner = 'sanad' THEN
        RAISE EXCEPTION 'G1_C4_PRECONDITION: runtime owns the audit ledger; separation required'
            USING ERRCODE = '42501';
    END IF;
END $pre$;

-- SEPARATED ONLY: enforce the append-only ACL (fixture topology: no mutation).
DO $acl$
BEGIN
    IF current_setting('sanad.deployment_topology', true) = 'separated' THEN
        REVOKE UPDATE, DELETE, TRUNCATE ON TABLE public.platform_audit_logs FROM sanad;
    END IF;
END $acl$;

-- SEPARATED ONLY: effective-privilege postconditions (runtime is NOT the
-- owner in this topology; has_table_privilege reflects the real ACL).
DO $post$
BEGIN
    IF current_setting('sanad.deployment_topology', true) = 'separated' THEN
        IF has_table_privilege('sanad','public.platform_audit_logs','UPDATE')
           OR has_table_privilege('sanad','public.platform_audit_logs','DELETE')
           OR has_table_privilege('sanad','public.platform_audit_logs','TRUNCATE')
           OR NOT has_table_privilege('sanad','public.platform_audit_logs','SELECT')
           OR NOT has_table_privilege('sanad','public.platform_audit_logs','INSERT')
        THEN RAISE EXCEPTION 'G1_C4_POSTCONDITION: runtime audit-ledger ACL not append-only'
             USING ERRCODE = '42501'; END IF;
    END IF;
END $post$;
