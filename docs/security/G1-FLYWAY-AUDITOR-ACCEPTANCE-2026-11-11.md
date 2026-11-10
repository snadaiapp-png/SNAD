# SANAD G1 — release-only Flyway auditor (DRAFT; NOT PRODUCTION AUTHORIZATION)

Target baseline: `main@43e6c41909ce54a5185b808d403ac93bc95b6b97`. This document defines an isolated rehearsal and a reviewed production change. This PR does **not** create roles, grant privileges, run Flyway, export a backup, deploy Render/Vercel, or lift G1.

## Proven conflict and intended contract

The pre-existing `scripts/production/verify-flyway.sh` read `flyway_schema_history` as the Render runtime `DATABASE_USERNAME=sanad`. G1 correctly forbids the runtime role from SELECT on Flyway history. Granting the runtime SELECT to make a release green would defeat separation.

The revised script requires a separately scoped audit session (`sanad_release_auditor`). It NEVER obtains database secrets from Render, never falls back to `sanad` or `sanad_migrator`, checks the *actual* `current_user`, verifies that the audit identity is not privileged or a member of either operational role, and validates strictly read-only effective Flyway ACLs. It checks Flyway schema head, missing SQL checksums, duplicate applied versions, Y2 table/column and active capability sentinels. These checks are NOT a substitute for actual Flyway `validate` by the authorized migrator. The Y2 column-presence sentinel reads `pg_catalog.pg_attribute` / `pg_class` / `pg_namespace` rather than `information_schema.columns`: the latter hides columns from users with no table-level SELECT, causing false negatives and pressuring unsafe GRANT expansion. The isolated PG17 integration gate explicitly checks that the auditor remains limited to Flyway history and capability SELECT.

## Proposed minimum database privileges (approved DBA operator only)

**RUN FIRST ON A PRODUCTION-DERIVED ISOLATED RESTORE**; do not paste onto live production until signed CAB change, approved backup, isolated role-split/rollback/restore proofs and G1 independent security review.

- Provision `sanad_release_auditor LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION NOINHERIT` using a random password from the approved secret manager (never repository/source, workflow logs or command arguments).
- No `sanad` or `sanad_migrator` membership, and no `CREATEDB`, `CREATE`, `TRUNCATE`, DML, ownership, `SECURITY DEFINER`, `pg_read_all_data`, `pg_write_all_data` or `BYPASSRLS`.
- Authorized DBA should grant ONLY `CONNECT` to database `postgres`, `USAGE` on `public`, and `SELECT` on `public.flyway_schema_history` and `public.access_capabilities`. Review **pre-existing PUBLIC privileges, default privileges and inherited memberships** before accepting least-privilege posture.
- In the protected GitHub `production` environment only: `PROD_JDBC_URL` (host/database only, no URL-embedded credentials), `PROD_FLYWAY_AUDIT_USER=sanad_release_auditor`, `PROD_FLYWAY_AUDIT_PASSWORD` set through the secret store, with actual reviewer-controlled workflow access. These are NOT the runtime `DATABASE_PASSWORD` and NOT the migrator password.
- `scripts/production/verify-flyway.sh` requires `FLYWAY_AUDIT_JDBC_URL`, `FLYWAY_AUDIT_USERNAME`, `FLYWAY_AUDIT_PASSWORD`; it fails closed if absent, identity drifts or ACL is broader. Public GitHub CI tests use only synthetic stubbed responses.

Example **review-only** DCL shape; never run on live without independent explicit CAB authority, pre-change recovery point and full rollback plan:

```sql
-- In authorized DBA session, supply role password out-of-band.
-- CREATE ROLE sanad_release_auditor LOGIN NOSUPERUSER NOCREATEDB
--   NOCREATEROLE NOBYPASSRLS NOREPLICATION NOINHERIT PASSWORD <SECURE_PARAMETER>;
GRANT CONNECT ON DATABASE postgres TO sanad_release_auditor;
GRANT USAGE ON SCHEMA public TO sanad_release_auditor;
GRANT SELECT ON TABLE public.flyway_schema_history TO sanad_release_auditor;
GRANT SELECT ON TABLE public.access_capabilities TO sanad_release_auditor;
-- Verify role flags, grants, inherited membership and denials independently.
```

## Isolated PostgreSQL 17 acceptance

1. Create a disposable PG17 cluster with no production networking/secrets. First exercise 35+ independent tenant/privilege and Flyway chain probes using `.github/workflows/migrator-runtime-separation.yml` (the existing isolated run #42880314820 was 62/62 + 11/11 on source baseline).
2. Run `G1 Flyway Auditor Acceptance`: its new disposable PG17 job executes `tests/security/flyway-auditor/pg17-acceptance.sh` with real PostgreSQL 17 role/ACL SQL and synthetic objects. Verify `current_user`, read-only Flyway SELECT, `access_capabilities` SELECT, denied auditor UPDATE, rejected broad auditor UPDATE, rejected runtime Flyway SELECT and rejected pending-schema head. It uses no Supabase/Render/Vercel credentials or production data. This is an isolated role-contract test, NOT production permission certification.
3. Execute the actual release-audit script from a protected synthetic credential fixture against that disposable cluster and verify successful expected current head, then negative cases for schema drift, failed migrations, missing SQL checksums, missing capabilities, illegal ACL, missing secrets, wrong identity, and injected credentials.
4. Independently rerun CI and Post-Merge on the **new merge SHA**; do not reuse `43e6c419` for the future release.

## Recovery and approvals still PENDING

Historical durable encrypted backup #42623864874 and structural public ACL restoration #42638822139 have successful limited evidence, but **fresh approved production-derived restoration with exact scope, owner/default ACL, tenant/worker data, key custody, real RPO/RTO, and current-cutover rollback** remains unverified. Production RLS/ACL five-table repairs, `login_tenant_index` least privilege, `sanad_migrator` bootstrap and the OCI release have NOT been deployed.

**CAB must sign** exact new SHA + immutable Backend digest, DB head, approved backup identifier, data owner, DBA, independent security reviewer, change window, rollback criteria and production 3-user testing before any promotion. PR approval is not CAB approval.

**Decision remains: `P0_SECURITY_HOLD=ACTIVE`, `G1=NO-GO`, `PAYMENTS=OFF`.**
