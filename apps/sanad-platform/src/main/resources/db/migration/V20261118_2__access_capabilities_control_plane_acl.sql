-- ============================================================================
-- V20261118_2 — G1/C4 PR-2 P1-3 (R-C4-04): `access_capabilities`
--               control-plane ACL.
--
-- Closure context (docs/security/g1-final-closure/20-PR2-RESIDUAL-RISK-
-- REMEDIATION-PLAN-2026-11-18.md, P1-3; live red-team evidence RT-12/12B,
-- RT-13/13B, RT-14/14B in doc 18): the runtime role `sanad` held full DML
-- on the capability registry `access_capabilities` — a compromised runtime
-- session could mint, deactivate or delete authorization-relevant capability
-- codes. The registry is migration-seeded control-plane data: no application
-- code path writes it (verified by source audit: readers only —
-- AccessCapabilityRepository consumers such as RegistrationProvisioner
-- template grants). This migration closes the drift with the exact
-- dedicated-connection pattern of V20261118_1 (tenants), as revised by
-- ORDER 11 (2026-11-21).
--
-- TARGET MODEL (ORDER 11):
--   sanad_control_plane : dedicated CONTROL-PLANE CONNECTION identity
--                         (LOGIN, environment-provisioned password, no
--                         membership with the runtime in any direction —
--                         see V20261118_1 for the full contract). Holds
--                         exactly SELECT, INSERT, UPDATE, DELETE on
--                         public.access_capabilities.
--   sanad               : keeps SELECT (role-template provisioning and RBAC
--                         evaluation read the ACTIVE catalog —
--                         RegistrationProvisioner/EffectivePermissionProjection),
--                         loses INSERT/UPDATE/DELETE/TRUNCATE in the separated
--                         topology.
--
-- Behaviour by sanad.deployment_topology: identical three-way contract to
-- V20261118_1 (separated = enforce + assert; migration-fixture/unset =
-- validate-only WARNING for the runtime REVOKE, dedicated-identity GRANT
-- still applied, membership-absence contract enforced; anything else =
-- FAIL CLOSED 22023).
--
-- Verification gates: run-separation-cert.sh S2 matrix (denials for the
-- plain runtime role + dedicated-connection lifecycle proof + SET ROLE
-- denial) and redteam-attack-surface-battery.sh RT-12/13/14 (denied) +
-- RT-37 (dedicated-connection lifecycle) + RT-39 (SET ROLE denied) on the
-- exact chain head.
--
-- No RLS policies, no data, no bypass privileges are changed. Forward-only.
-- Requires V20261103_1/V20261103_2 and V20261118_1 to have been applied.
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
        RAISE WARNING 'SANAD_TOPOLOGY_FIXTURE: sanad.deployment_topology is "%" - disposable migration-mechanics topology. The access_capabilities control-plane REVOKE against the runtime identity is SKIPPED here (validate-only): the fixture runner owns the schema, a self-REVOKE would restrict the owner, and the control-plane-only ACL is only enforceable in the separated topology (certified by run-separation-cert.sh S2CP; attacked by redteam-attack-surface-battery.sh RT-12/13/14). The GRANT to the dedicated sanad_control_plane connection identity still applies so the approved control-plane path exists in every topology. The membership-absence contract (no SET ROLE path from the runtime) IS enforced in every topology.', coalesce(v_topology, 'unset');
    ELSIF NOT v_separated THEN
        RAISE EXCEPTION 'G1_C4_TOPOLOGY: sanad.deployment_topology="%" (expected separated|migration-fixture|unset)', v_topology
            USING ERRCODE = '22023';
    END IF;

    -- common preconditions (both certified topologies)
    IF to_regclass('public.access_capabilities') IS NULL
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad' AND NOT rolsuper AND NOT rolbypassrls)
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad_migrator')
    THEN RAISE EXCEPTION 'G1_C4_PRECONDITION: access_capabilities table/runtime/migrator missing'
         USING ERRCODE = '42501'; END IF;

    -- control-plane connection-identity contract (ORDER 11; fail closed)
    SELECT rolname, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole, rolcanlogin
      INTO v_cp
      FROM pg_roles
     WHERE rolname = 'sanad_control_plane';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'G1_C4_CONTROL_PLANE_ROLE_REQUIRED: cluster role sanad_control_plane is not provisioned (see V20261118_1 provisioning guidance)'
            USING ERRCODE = '42501';
    END IF;

    IF v_cp.rolsuper OR v_cp.rolbypassrls OR v_cp.rolcreatedb OR v_cp.rolcreaterole THEN
        RAISE EXCEPTION 'G1_C4_CONTROL_PLANE_ROLE_CONTRACT: sanad_control_plane must be NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS'
            USING ERRCODE = '42501';
    END IF;

    IF NOT v_cp.rolcanlogin THEN
        RAISE EXCEPTION 'G1_C4_CONTROL_PLANE_ROLE_LOGIN: sanad_control_plane must be a LOGIN role (ORDER 11: independent connection identity for tenant/capability management)'
            USING ERRCODE = '42501';
    END IF;

    -- ORDER 11 membership-absence contract (see V20261118_1): fail closed.
    IF pg_has_role('sanad', 'sanad_control_plane', 'MEMBER') THEN
        RAISE EXCEPTION 'G1_C4_CONTROL_PLANE_MEMBERSHIP_FORBIDDEN: sanad must NOT be a member of sanad_control_plane (ORDER 11 contract)'
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
      FROM pg_class WHERE oid = 'public.access_capabilities'::regclass;

    IF v_separated AND v_owner = 'sanad' THEN
        RAISE EXCEPTION 'G1_C4_PRECONDITION: runtime owns the access_capabilities table; separation required'
            USING ERRCODE = '42501';
    END IF;
END $pre$;

-- Dedicated-connection GRANT (BOTH topologies; V20261118_1 precedent).
DO $acl$
BEGIN
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.access_capabilities TO sanad_control_plane;
END $acl$;

-- SEPARATED ONLY: revoke the registry DML from the plain runtime role.
DO $revoke$
BEGIN
    IF current_setting('sanad.deployment_topology', true) = 'separated' THEN
        REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON TABLE public.access_capabilities FROM sanad;
    END IF;
END $revoke$;

-- Postconditions (V20261118_1 precedent: grant asserted in both topologies,
-- runtime denial matrix asserted in the separated topology, membership
-- absence re-asserted after every mutation in every topology).
DO $post$
BEGIN
    IF NOT has_table_privilege('sanad_control_plane','public.access_capabilities','SELECT')
       OR NOT has_table_privilege('sanad_control_plane','public.access_capabilities','INSERT')
       OR NOT has_table_privilege('sanad_control_plane','public.access_capabilities','UPDATE')
       OR NOT has_table_privilege('sanad_control_plane','public.access_capabilities','DELETE')
    THEN RAISE EXCEPTION 'G1_C4_POSTCONDITION: control-plane identity does not hold the access_capabilities lifecycle DML (SELECT is required for WHERE-keyed statements)'
         USING ERRCODE = '42501'; END IF;

    IF current_setting('sanad.deployment_topology', true) = 'separated' THEN
        IF has_table_privilege('sanad','public.access_capabilities','INSERT')
           OR has_table_privilege('sanad','public.access_capabilities','UPDATE')
           OR has_table_privilege('sanad','public.access_capabilities','DELETE')
           OR has_table_privilege('sanad','public.access_capabilities','TRUNCATE')
           OR NOT has_table_privilege('sanad','public.access_capabilities','SELECT')
        THEN RAISE EXCEPTION 'G1_C4_POSTCONDITION: runtime access_capabilities ACL not control-plane-only (expected sanad: SELECT only)'
             USING ERRCODE = '42501'; END IF;
    END IF;

    IF pg_has_role('sanad', 'sanad_control_plane', 'MEMBER')
       OR pg_has_role('sanad_control_plane', 'sanad', 'MEMBER')
       OR pg_has_role('sanad_control_plane', 'sanad_migrator', 'MEMBER') THEN
        RAISE EXCEPTION 'G1_C4_POSTCONDITION: forbidden membership path exists between the runtime and the control-plane identity (ORDER 11 contract)'
             USING ERRCODE = '42501';
    END IF;
END $post$;
