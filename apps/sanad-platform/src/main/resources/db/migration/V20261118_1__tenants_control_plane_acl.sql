-- ============================================================================
-- V20261118_1 — G1/C4 PR-2 P0-1 (R-C4-01): `tenants` control-plane ACL.
--
-- Closure context (docs/security/g1-final-closure/20-PR2-RESIDUAL-RISK-
-- REMEDIATION-PLAN-2026-11-18.md, P0-1; live red-team evidence RT-07/07B/07C,
-- RT-08/08B, RT-09/09B in doc 18): the runtime role `sanad` held full CRUD
-- on the RLS-OFF control-plane table `tenants` with no DB-level guardrail —
-- a compromised runtime session could enumerate, suspend or delete ANY
-- tenant row. This migration closes that drift with the same forward-only,
-- topology-aware pattern as V20261115_1 (platform_audit_logs).
--
-- ORDER 11 REVISION (2026-11-21, Control Plane Security & PostgreSQL
-- Root-Cause Remediation): the originally drafted SET ROLE compensating
-- control (NOLOGIN role + `GRANT sanad_control_plane TO sanad WITH INHERIT
-- FALSE`) is FORBIDDEN. A separation that depends on runtime MEMBERSHIP in
-- the administrative role is no separation at all: any compromised runtime
-- session could simply `SET ROLE sanad_control_plane` and mint, suspend or
-- delete tenants (live proof: the SET ROLE path executed with no
-- authorization gate, and failed only on an unrelated ACL detail — 42501,
-- missing SELECT, surfaced as 25P02 on the RESET ROLE compensation).
--
-- TARGET MODEL (ORDER 11):
--   sanad_control_plane : dedicated CONTROL-PLANE CONNECTION identity.
--                         LOGIN, NOSUPERUSER NOCREATEDB NOCREATEROLE
--                         NOBYPASSRLS, password provisioned ONLY by the
--                         deployment environment (CI service setup, docker
--                         postgres-init, runbook secret store — the chain
--                         never creates roles or credentials: V20261103_1
--                         precedent). Holds exactly SELECT, INSERT, UPDATE,
--                         DELETE on public.tenants. SELECT is REQUIRED: every
--                         lifecycle statement is WHERE-keyed, and PostgreSQL
--                         demands SELECT for the predicate — the exact gap
--                         that produced the 42501/25P02 failure chain.
--   sanad               : runtime identity. Keeps SELECT on tenants (documented
--                         tenant-name resolution requirement: subdomain
--                         lookups during login/provisioning). LOSES
--                         INSERT/UPDATE/DELETE/TRUNCATE in the separated
--                         topology.
--   MEMBERSHIP          : NONE, IN EITHER DIRECTION, IN ANY TOPOLOGY. The
--                         runtime must not be able to SET ROLE to the
--                         control-plane identity (directly or transitively),
--                         and the control-plane identity must not reach the
--                         migrator or the runtime. Both directions are
--                         asserted fail-closed below and re-asserted in the
--                         postconditions after every ACL mutation.
--
-- Behaviour by sanad.deployment_topology (declared per-database):
--   'separated'         TARGET MODEL. Preconditions: restricted runtime role,
--                       migrator role present, control-plane LOGIN role
--                       present, runtime does NOT own the table. GRANT to the
--                       control-plane identity executes unconditionally;
--                       REVOKE of INSERT/UPDATE/DELETE/TRUNCATE from sanad
--                       executes; the postconditions assert EFFECTIVE
--                       privileges (has_table_privilege matrix: sanad
--                       SELECT=true, INSERT/UPDATE/DELETE/TRUNCATE=false;
--                       sanad_control_plane SELECT/INSERT/UPDATE/DELETE=true)
--                       and the ABSENCE of any membership path.
--                       CI certification of the denials, of the dedicated
--                       connection path and of SET ROLE denial:
--                       tests/security/migrator-separation/run-separation-cert.sh
--                       S2 (separated topology, real connections) and
--                       tests/security/g1-c4/redteam-attack-surface-battery.sh
--                       RT-07/08/09 (denied) + RT-36 (dedicated-connection
--                       lifecycle) + RT-39 (SET ROLE denied).
--   'migration-fixture' DISPOSABLE topology (or unset). VALIDATE-ONLY for the
--                       runtime REVOKE: loud WARNING, preconditions asserted,
--                       no ACL mutation against the runtime identity. Under
--                       owner-as-runner the control-plane-only ACL is
--                       structurally unenforceable (the owner can always
--                       re-grant) — same documented skip class as
--                       V20261102_2 / V20261103_2 / V20261103_3 / V20261115_1.
--                       The GRANT to sanad_control_plane still executes so the
--                       application's dedicated control-plane connection
--                       works in every topology. The membership ABSENCE
--                       contract IS enforced here (it is topology-independent
--                       and owned by the cluster provisioning, not by the
--                       table owner). This path is NOT a separation
--                       certification path.
--   anything else       FAIL CLOSED (unknown topology value, 22023).
--
-- Flyway success alone is NOT proof of the security effect: the separation
-- certification and the red-team battery re-verify EFFECTIVE catalog
-- privileges and connection identities on the exact chain head before any
-- protected deployment.
--
-- No RLS policies, no data, no bypass privileges are changed. Forward-only.
-- Requires V20261103_1/V20261103_2 (migrator contract + ownership) to have
-- been applied.
-- ============================================================================

DO $pre$
DECLARE
    v_topology  text := current_setting('sanad.deployment_topology', true);
    v_separated boolean;
    v_owner     text;
    v_cp        record;
BEGIN
    v_separated := (v_topology = 'separated');

    IF v_topology IS NULL OR v_topology = 'migration-fixture' THEN
        RAISE WARNING 'SANAD_TOPOLOGY_FIXTURE: sanad.deployment_topology is "%" - disposable migration-mechanics topology. The tenants control-plane REVOKE against the runtime identity is SKIPPED here (validate-only): the fixture runner owns the schema, a self-REVOKE would restrict the owner, and the control-plane-only ACL is only enforceable in the separated topology (certified by run-separation-cert.sh S2; attacked by redteam-attack-surface-battery.sh RT-07/08/09). The GRANT to the dedicated sanad_control_plane connection identity still applies so the approved control-plane path exists in every topology. The membership-absence contract (no SET ROLE path from the runtime) IS enforced in every topology.', coalesce(v_topology, 'unset');
    ELSIF NOT v_separated THEN
        RAISE EXCEPTION 'G1_C4_TOPOLOGY: sanad.deployment_topology="%" (expected separated|migration-fixture|unset)', v_topology
            USING ERRCODE = '22023';
    END IF;

    -- common preconditions (both certified topologies)
    IF to_regclass('public.tenants') IS NULL
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad' AND NOT rolsuper AND NOT rolbypassrls)
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad_migrator')
    THEN RAISE EXCEPTION 'G1_C4_PRECONDITION: tenants table/runtime/migrator missing'
         USING ERRCODE = '42501'; END IF;

    -- control-plane connection-identity contract (ORDER 11; fail closed)
    SELECT rolname, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole, rolcanlogin
      INTO v_cp
      FROM pg_roles
     WHERE rolname = 'sanad_control_plane';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'G1_C4_CONTROL_PLANE_ROLE_REQUIRED: cluster role sanad_control_plane is not provisioned. Provision it with: CREATE ROLE sanad_control_plane LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS; <environment sets its password>; GRANT CONNECT ON DATABASE <db> TO sanad_control_plane; GRANT USAGE ON SCHEMA public TO sanad_control_plane; (CI provisioning / docker postgres-init / RUNBOOK section 2). The password lives ONLY in the deployment environment.'
            USING ERRCODE = '42501';
    END IF;

    IF v_cp.rolsuper OR v_cp.rolbypassrls OR v_cp.rolcreatedb OR v_cp.rolcreaterole THEN
        RAISE EXCEPTION 'G1_C4_CONTROL_PLANE_ROLE_CONTRACT: sanad_control_plane must be NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS (found super=%, bypassrls=%, createdb=%, createrole=%)',
            v_cp.rolsuper, v_cp.rolbypassrls, v_cp.rolcreatedb, v_cp.rolcreaterole
            USING ERRCODE = '42501';
    END IF;

    IF NOT v_cp.rolcanlogin THEN
        RAISE EXCEPTION 'G1_C4_CONTROL_PLANE_ROLE_LOGIN: sanad_control_plane must be a LOGIN role (ORDER 11: independent connection identity for tenant/capability management). The deprecated NOLOGIN + SET ROLE membership design is forbidden.'
            USING ERRCODE = '42501';
    END IF;

    -- ORDER 11 membership-absence contract: NO SET ROLE path, in ANY
    -- topology, in EITHER direction, direct or transitive. Fail closed.
    IF pg_has_role('sanad', 'sanad_control_plane', 'MEMBER') THEN
        RAISE EXCEPTION 'G1_C4_CONTROL_PLANE_MEMBERSHIP_FORBIDDEN: sanad must NOT be a member of sanad_control_plane (ORDER 11: a separation based on runtime membership in the administrative role with SET ROLE is forbidden). REVOKE sanad_control_plane FROM sanad; on every cluster provisioned with the deprecated contract.'
            USING ERRCODE = '42501';
    END IF;
    IF pg_has_role('sanad_control_plane', 'sanad', 'MEMBER') THEN
        RAISE EXCEPTION 'G1_C4_CONTROL_PLANE_REVERSE_MEMBERSHIP_FORBIDDEN: sanad_control_plane must not be a member of sanad'
            USING ERRCODE = '42501';
    END IF;
    IF pg_has_role('sanad_control_plane', 'sanad_migrator', 'MEMBER') THEN
        RAISE EXCEPTION 'G1_C4_CONTROL_PLANE_ROLE_ESCALATION: sanad_control_plane must not be a member of sanad_migrator'
            USING ERRCODE = '42501';
    END IF;

    SELECT pg_get_userbyid(relowner) INTO v_owner
      FROM pg_class WHERE oid = 'public.tenants'::regclass;

    IF v_separated AND v_owner = 'sanad' THEN
        RAISE EXCEPTION 'G1_C4_PRECONDITION: runtime owns the tenants table; separation required'
            USING ERRCODE = '42501';
    END IF;
END $pre$;

-- Dedicated-connection GRANT (BOTH topologies): the control-plane identity
-- holds exactly the tenant lifecycle DML plus the SELECT required by every
-- WHERE-keyed lifecycle statement (the 42501 root cause of the SET ROLE
-- draft). Connection-level access (CONNECT on this database + schema USAGE)
-- is granted here idempotently because the migration runner owns the
-- database in both certified topologies — the CREDENTIAL stays
-- environment-provisioned and never appears in the chain.
DO $acl$
BEGIN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.tenants TO sanad_control_plane;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO sanad_control_plane', current_database());
    GRANT USAGE ON SCHEMA public TO sanad_control_plane;
END $acl$;

-- SEPARATED ONLY: revoke the control-plane DML from the plain runtime role
-- (fixture topology: no mutation against the runtime identity).
DO $revoke$
BEGIN
    IF current_setting('sanad.deployment_topology', true) = 'separated' THEN
        REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON TABLE public.tenants FROM sanad;
    END IF;
END $revoke$;

-- Postconditions: the dedicated-connection grant is asserted in BOTH
-- topologies (including SELECT — the WHERE-keyed lifecycle requirement);
-- the runtime denial matrix is asserted in the separated topology
-- (has_table_privilege reflects the real ACL there — the runtime is NOT
-- the owner); the ORDER 11 membership-absence contract is re-asserted AFTER
-- every mutation, in every topology.
DO $post$
BEGIN
    IF NOT has_table_privilege('sanad_control_plane','public.tenants','SELECT')
       OR NOT has_table_privilege('sanad_control_plane','public.tenants','INSERT')
       OR NOT has_table_privilege('sanad_control_plane','public.tenants','UPDATE')
       OR NOT has_table_privilege('sanad_control_plane','public.tenants','DELETE')
    THEN RAISE EXCEPTION 'G1_C4_POSTCONDITION: control-plane identity does not hold the tenants lifecycle DML (SELECT is required for WHERE-keyed statements)'
         USING ERRCODE = '42501'; END IF;

    IF current_setting('sanad.deployment_topology', true) = 'separated' THEN
        IF has_table_privilege('sanad','public.tenants','INSERT')
           OR has_table_privilege('sanad','public.tenants','UPDATE')
           OR has_table_privilege('sanad','public.tenants','DELETE')
           OR has_table_privilege('sanad','public.tenants','TRUNCATE')
           OR NOT has_table_privilege('sanad','public.tenants','SELECT')
        THEN RAISE EXCEPTION 'G1_C4_POSTCONDITION: runtime tenants ACL not control-plane-only (expected sanad: SELECT only)'
             USING ERRCODE = '42501'; END IF;
    END IF;

    IF pg_has_role('sanad', 'sanad_control_plane', 'MEMBER')
       OR pg_has_role('sanad_control_plane', 'sanad', 'MEMBER')
       OR pg_has_role('sanad_control_plane', 'sanad_migrator', 'MEMBER') THEN
        RAISE EXCEPTION 'G1_C4_POSTCONDITION: forbidden membership path exists between the runtime and the control-plane identity (ORDER 11 contract)'
             USING ERRCODE = '42501';
    END IF;
END $post$;
