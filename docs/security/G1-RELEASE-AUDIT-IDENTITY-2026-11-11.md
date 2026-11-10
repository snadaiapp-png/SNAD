# G1 Release Audit Identity — CAB §C resolution record

**Date:** 2026-11-11
**Repository:** `snadaiapp-png/SNAD`
**Scope:** engineering change resolving the fail-closed incompatibility recorded in
[G1-CAB-PRODUCTION-GO-NOGO-EVIDENCE-2026-11-10.md](G1-CAB-PRODUCTION-GO-NOGO-EVIDENCE-2026-11-10.md) §C, item 3.
**Status of this document:** engineering record only — NOT a G1/CAB approval, NOT a certification.

## 1. The resolved incompatibility (restated)

The CAB evidence recorded: `scripts/production/verify-flyway.sh` read
`flyway_schema_history` using the backend runtime credentials
(`DATABASE_USERNAME` fetched from the Render environment together with
`DATABASE_URL`/`DATABASE_PASSWORD`). Under the separated migrator/runtime
topology enforced by `V20261103_2` (runtime `sanad` holds NO privilege on the
migration ledger), that audit path is structurally impossible — by design.
The production release workflow was therefore intentionally fail-closed until
the audit moved to a separately governed read-only identity.

## 2. The implemented change

| File | Change |
|---|---|
| `scripts/production/verify-flyway.sh` | Credentials now come exclusively from `AUDIT_DATABASE_URL` / `AUDIT_DATABASE_USERNAME` / `AUDIT_DATABASE_PASSWORD` environment variables (GitHub Production environment secrets). The script **fails closed** when they are absent and **rejects** `sanad`, `postgres`, `sanad_migrator`, `supabase_admin` and `service_role` as the audit identity. The Y2 definition-column sentinel now reads `pg_catalog.pg_attribute` (world-readable) instead of `information_schema.columns`, so the audit identity needs no privilege on the `workflow_definitions` business table. All ledger semantics (sentinels V15/20260702.x, failed/duplicate/checksum checks, installed_rank-derived head, pending/drift guards) are unchanged. |
| `.github/workflows/production-release.yml` | Job env passes `PROD_RELEASE_AUDIT_URL` / `PROD_RELEASE_AUDIT_USER` / `PROD_RELEASE_AUDIT_PASSWORD` Production-environment secrets; the input validation step requires all three to be present before any deployment action. |
| `.github/workflows/users-production-release.yml` | Same audit identity wiring for the Users production release path (it invokes the same gate). |
| `scripts/production/run-production-release.sh` | Operator entry point now requires the `AUDIT_*` environment explicitly (fail closed). |
| `scripts/production/tests/verify-flyway.test.sh` | Harness updated to the env-based credential contract; adds two new scenarios: (4) missing `AUDIT_*` environment fails closed, (5) the runtime role is rejected as the audit identity. The harness now runs against a pinned fixture repository tree so the regression scenarios are independent of the live chain head (the previous harness had silently gone RED on main as the chain advanced past `20260908.1`). |
| `scripts/ops/migrator-separation/provision-release-auditor.sql` | One-time admin bootstrap: creates `sanad_release_auditor` (NOLOGIN, NOSUPERUSER, NOBYPASSRLS, NOCREATEDB, NOCREATEROLE), grants the exact least-privilege set (`USAGE` on schema `public`; `SELECT` on `flyway_schema_history` and `access_capabilities`), and verifies the effective privilege set fail-closed (no other SELECT, no write, no DDL). |
| `docs/security/migrator-runtime-separation/RUNBOOK.md` | §2b documents the audit identity provisioning, secret store names, ordering relative to the migrator ownership transfer, and the `SET ROLE sanad_migrator` re-assertion path. |

## 3. Governance of the audit identity

* Credentials live only in the GitHub **Production** environment secret store
  (`PROD_RELEASE_AUDIT_URL` / `PROD_RELEASE_AUDIT_USER` /
  `PROD_RELEASE_AUDIT_PASSWORD`) — the same protection domain as
  `PROD_FLYWAY_MIGRATOR_*`.
* The identity holds **no** membership in `sanad`/`sanad_migrator` and no
  write or DDL privilege anywhere; it is verified fail-closed at provisioning
  time and empirically at every production release (a broken grant fails the
  release, never bypasses it).
* The runtime role does **not** regain `flyway_schema_history` SELECT — the
  CAB constraint "do not regrant Flyway history SELECT to runtime" holds.

## 4. Isolated DR verification (2026-11-11, project `wmlimglmvfirxhiwfekb`)

The provisioning script and the migrator ownership-transfer interplay were
exercised on the empty designated isolated-verification Supabase project
(PG 17.11, admin identity `postgres` — the same NOSUPERUSER platform shape as
production). Findings:

1. `pg_has_role` RAISES for a not-yet-provisioned role instead of returning
   false, so the auditor script's membership-isolation check aborted the whole
   bootstrap when `sanad_migrator` did not exist yet. **Fixed** with existence
   guards + NOTICES (the invariants remain enforced fail-closed by
   V20261103_1/V20261103_2 and the migrator workflow assertions).
2. On Supabase the admin identity is NOSUPERUSER: `ALTER ... OWNER TO
   sanad_migrator` requires (a) membership of the admin in the migrator role
   and (b) CREATE for the migrator on schema `public` **before** the transfer
   statements run (`GRANT sanad_migrator TO postgres; GRANT USAGE, CREATE ON
   SCHEMA public TO sanad_migrator;`). The GitHub-runner rehearsal never hit
   this because it runs as superuser. **Documented** in RUNBOOK §2(b-pre).
3. Production schema `public` is owned by `pg_database_owner` (the database
   owner `postgres` can transfer it); `V20261103_2` fails closed at its
   SANAD_SCHEMA_OWNER_PRECONDITION unless the schema is migrator-owned, which
   the transfer script performs.
4. Auditor grants survive the migrator ownership transfer — **empirically
   proven on the isolate**: after `ALTER SCHEMA/TABLE ... OWNER TO
   sanad_migrator` (ownership confirmed via `pg_class`), the auditor still
   held exactly `SELECT` on `flyway_schema_history` + `access_capabilities`
   (aclexplode enumeration), nothing on the business table, no write/DDL and
   no membership. The RUNBOOK `SET ROLE sanad_migrator` re-assertion path
   remains available as defense in depth, and the release gate itself proves
   the grants live at every production release (fail-closed).
5. The `pg_tables` view is not reliable under every Supabase catalog state
   for ad-hoc `has_table_privilege(...)` enumeration (spurious
   `public.pg_statistic` resolution errors at top level). The provisioning
   script's verification therefore uses `pg_catalog.pg_class` directly — the
   same pattern as the migrator workflow post-flight assertions.
6. Full green end-to-end re-run recorded: fixture → provisioning (5/5) →
   bootstrap order → transfer → post-transfer verification → cleanup; the DR
   project was restored to its pristine empty state afterwards
   (0 fixture tables, 0 fixture roles, schema ownership restored).
7. **Pooler username routing (empirically probed against the live Supabase
   session pooler):** the bare form `sanad_migrator` is refused with
   `ENOIDENTIFIER (no tenant identifier provided)`; the suffixed form
   `sanad_migrator.tkbrvupemreqabwzdpyq` routes correctly (probe returned
   `EAUTHQUERY user not found in the database` only because the role is not
   yet provisioned in production — tenant resolution succeeded). The migrator
   workflow's pre-flight therefore now accepts both the bare and the
   pooler-suffixed migrator username (the same convention as
   `g1-acl-rls-certification.yml`), while the database-side `current_user`
   assertion remains the authoritative identity gate. RUNBOOK §2 documents
   the pooler-form `PROD_FLYWAY_MIGRATOR_USER` and the `PROD_JDBC_URL`
   pooler URL for IPv4-only GitHub runners.

## 5. What this change does NOT do

* It does not deploy anything, does not touch the production database, and
  does not modify the pending migration chain (head remains `20261107.1`).
* It does not constitute G1, CAB, or payment authorization. Production
  deployment still requires the full CAB chain: fresh recovery point,
  isolated rehearsal, migrator bootstrap + protected migration, daemonless
  OCI publication, Vercel exact-SHA reconcile, and the Production
  environment approvals.
