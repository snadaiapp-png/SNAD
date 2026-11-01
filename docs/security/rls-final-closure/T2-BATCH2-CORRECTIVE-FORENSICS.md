# T2 Batch 2 — P0 Corrective Forensics (PR #1463)

- Order: P0 Corrective Order for PR #1463 (2026-10-31)
- Candidate SHA under investigation: `9b94a39d206acaeb073f3a827e289dd0e6837e41`
- Base: main `5e854ef4426d7ff500675be69f15ff3358db917a` (PR #1460 merged)
- Flags: `P0_SECURITY_HOLD=ACTIVE`, `G1_CERTIFICATION=HOLD`, `PAYMENTS=OFF`,
  `PRODUCTION_RELEASE=NO-GO` — unchanged by this corrective work.

## 1. Gate outcomes under investigation (SHA 9b94a39d)

| Gate | Run / Job | Outcome |
| --- | --- | --- |
| Backup Restore Validation | run 41227217243 (job 124613778770) | failure |
| CRM G1 Schema Isolation | run 41227217125 (job 124613778216) | failure |
| Wave 1 Task 1 Targeted PostgreSQL | run 41227217227 (job 124613778320) | failure |
| CI → PostgreSQL Acceptance Tests | run 41227217218, job `pg-acceptance` | failure |
| CI → Maven Test Suite | run 41227217218 | in_progress at last check |

CI job logs and the `crm-g1-schema-isolation-9b94a39d…` artifact (id
13052840214) could not be downloaded from this environment: the GitHub REST
API returned intermittent 403 (unauthenticated per-IP rate limit) and the
artifact endpoint requires authentication (401). The failures were therefore
reproduced and pinned locally (PostgreSQL Direct), which is stronger evidence
than log analysis alone; provenance of every file in this directory is local
reproduction, not a CI artifact mirror.

## 2. Root cause — one defect family, four surfaces

`V20261031_1__d6_t2_batch2_rls_closure.sql` guards, fail-closed (RAISE
EXCEPTION, line-88 `$guard$` terminator), that nine tables exist before it
binds FORCE RLS. Five of them are created ONLY by `db/vendor/postgresql`
migrations:

| Guarded table | Created by |
| --- | --- |
| provisioning_jobs / provisioning_job_steps / subscription_commands | db/migration/V20260830_1 |
| login_tenant_index | db/migration/V20261011_4 |
| crm_sales_teams | db/vendor/postgresql/V20260722_1 |
| crm_queue_memberships | db/vendor/postgresql/V20260722_2 |
| crm_assignment_rules | db/vendor/postgresql/V20260722_4 |
| crm_transfer_steps | db/vendor/postgresql/V20260722_6 |
| crm_next_best_actions | db/vendor/postgresql/V20260729_1 |

Every consumer that ran a **migration-location-only chain**
(`classpath:db/migration` without `classpath:db/vendor/postgresql`) therefore
hit the guard the moment `V20261031_1` was reached:

1. **Backup Restore Validation** — the workflow's psql loop lists only
   `db/migration/V*.sql`.
2. **16 test classes** — their Flyway instances declared
   `.locations("classpath:db/migration")` explicitly.
3. **HrG1MigrationTest / HrG1RlsFailClosedIntegrationTest** — Flyway instances
   with NO `.locations(...)` at all (Flyway defaults to `db/migration` only).
4. Production application profiles were already correct
   (`classpath:db/migration,classpath:db/vendor/{vendor}` in all five
   application*.yml profiles).

Secondary, previously masked defects surfaced once the chain applied:

5. **Stale version pins**: `HrG1MigrationTest.REPOSITORY_LEDGER_HEAD_VERSION`,
   `Crm008bFoundationAcceptanceTest.CRM_LATEST_VERSION`,
   `R0C13G02SchemaPostgresTest` (hard-coded), and the two pending-version
   lists in `CrmPostgresMigrationTest` expected `20261029.3` as head /
   omitted `EXTERNAL_ACTION_PREAUTH_TOKEN_INDEX_VERSION`.
6. **Misordered history assertion**: `CrmFlywayHistoryAssertionTest` listed
   `20261031.1` BEFORE the `20261029.1/2/3` tail (position 222 mismatch).
7. **`GET /api/v1/executive/provisioning/jobs`** (LifecycleController#listJobs)
   queried `provisioning_jobs` in autocommit — no `app.tenant_id` GUC — so the
   new fail-closed FORCE RLS returned an empty list
   (`ModuleRegistryUatPostgresAcceptanceTest.uatF_provisioningJobs_typedCamelCaseContract`:
   `No value at JSON path "$[0].id"`). The fixture cleanup also issued bare
   DELETEs against the now-FORCE-RLS'd table.

## 3. Fixes (no PRECONDITION weakened, no policy/guard/force/WITH CHECK change)

- `.github/workflows/backup-restore-validation.yml`: apply the FULL
  version-sorted chain from BOTH locations (exactly what production Flyway
  does with `locations=classpath:db/migration,classpath:db/vendor/postgresql`);
  fix the loop to `psql -f "$file"` (was hard-wiring the migration dir).
- All 19 test-side Flyway instances now use the two-location chain
  (16 mechanical edits + the two default-location instances in the HR module
  + the javadoc example in `MigrationTestSchemaSupport`).
- Version pins aligned to `20261031.1` in 5 files; history list reordered so
  the tail reads `…20261028.1, 20261029.1, 20261029.2, 20261029.3, 20261031.1`.
- `LifecycleController#listJobs`: `@Transactional(readOnly = true)` +
  `TenantRlsTransactionContext.applyForCurrentTransaction(scope)` with
  `scope = tenantId param (filter-authorized) else JWT tenant` — the same
  pattern as `HrPerformanceReviewV2Controller#bind`. Trust rationale: the JWT
  filter 403s any non-owner request whose `tenantId` param differs from the
  JWT tenant; governed control-plane owners may cross tenants only on this
  allow-listed route. Two unit tests updated for the constructor change.
- `ModuleRegistryUatPostgresAcceptanceTest` fixture cleanup binds the GUC
  (inTenantContext) for both `provisioning_jobs` and `tenant_subscriptions`.

## 4. PostgreSQL Direct RED → GREEN evidence

Environment: host-native PostgreSQL (no containers); migrations applied as a
restricted role (NOSUPERUSER, NOBYPASSRLS); PG 16.2 and 17.11 used; isolated
databases `sim_backup_red` / `sim_backup_green` / `pg_acceptance`; role
contract mirrored from the CI workflows.

RED (chain = db/migration only, i.e. the pre-fix gate behaviour):

- 216 migration-dir files applied, then the chain aborted EXACTLY at
  `V20261031_1`: SQLSTATE `P0001`, `D6_T2_BATCH2_PRECONDITION: required
  table(s) missing` — missing tables identified by name:
  `crm_next_best_actions`, `crm_assignment_rules`, `crm_queue_memberships`,
  `crm_transfer_steps`, `crm_sales_teams`.
- Test-level RED (Surefire): `CrmG1TenantIsolationPostgresTest` →
  `FlywayMigrate Script V20261031_1__d6_t2_batch2_rls_closure.sql failed`
  (`P0001`, same guard message).

GREEN (chain = migration + vendor merged, version-sorted; the fixed gate):

- 245 files applied, zero failures; `V20261031_1` APPLIED_OK.
- Catalog: all 8 RLS tables `relrowsecurity=true AND relforcerowsecurity=true`;
  `login_tenant_index` intentionally remains non-RLS (pre-auth discovery
  contract, V20261011_4) with its ACL closed by this migration.
- Test suites on the fixed branch (host-native PG, restricted role):
  - Wave 1 septet: **47/47** PASS
  - CI pg-acceptance quintet: **37/37** PASS
  - LifecycleController unit tests + fixed-class spot batch 1: **24/24** PASS
  - fixed-class spot batch 2: **25/25** PASS

## 5. PR #1458 coordination (Pre-Auth vs T2 Batch 2)

- #1458 adds `V20261028_2__d6_auth_token_tenant_index_and_rls.sql`
  (auth_token_tenant_index; FORCE RLS on refresh_tokens /
  password_reset_tokens) — no version collision and no shared table with
  `V20261031_1`.
- Textual conflicts WILL occur in two files both PRs touch:
  `CrmFlywayHistoryAssertionTest.java` (both append after the `20261028.1`
  entry) and `CrmPostgresMigrationTest.java` (both redefine
  `LATEST_MIGRATION_VERSION`). Resolution: merge **#1458 first**, then rebase
  #1463 keeping BOTH entries in order (`20261028.2` then `20261031.1`) and
  `LATEST_MIGRATION_VERSION = 20261031.1`. #1458's own new test
  (`D6AuthTokenTenantRlsPostgresTest`) already uses the two-location chain.

## 6. Status after this corrective commit

- All four failed gates: root-caused and fixed; locally verified GREEN
  (above). CI re-runs on the NEW commit SHA are REQUIRED before any gate may
  be declared PASS — no PASS is claimed here.
- Maven Test Suite (ci.yml) was still in_progress on 9b94a39d at last check;
  its outcome on the new SHA must be observed like the others.
- Still holding: merge, deploy, payments, and any lifting of
  `P0_SECURITY_HOLD` — pending green CI evidence at the new SHA, the
  independent security review, and a valid G1 certificate.
