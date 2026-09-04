# HRM-G0 Evidence — 05 API/UI Cutover (WS5 Tasks 8–12)

Evidence record for WS5 Tasks 8–12 of the HRM-G0 foundation plan
(`docs/superpowers/plans/2026-08-27-hrm-g0-05-api-ui-cutover.md`, branch
`feat/hrm-g0-foundation`, PR #914). PostgreSQL Direct only (127.0.0.1:5432/sanad,
role `sanad`, `rolsuper=false`, `rolbypassrls=false`). No Docker, no
Testcontainers. No production targeting at any point.

## WS5 Task 8 — Typed web client + shared HR workspace

- Commit: `eb1cab6e` — `feat(hrm): add typed web client and workspace`
- `/hr` execution dashboard moved intact to `/hr/execution/page.tsx`.
- `lib/api/hr-v2-api.ts`: full 58-op canonical v2 surface, typed request/response
  DTOs mirroring backend records, `Idempotency-Key` on every POST,
  `expectedVersion` bodies where the API requires them, `HrmV2ApiError` +
  canonical error-envelope parsing.
- `hr-workspace.tsx`: authoritative nav (`/hr`, `/hr/employees`,
  `/hr/org-structure`, `/hr/jobs`, `/hr/positions`, `/hr/assignments`,
  `/hr/compliance`, `/hr/execution`), `aria-current`, nav landmark, UX-only
  capability gating.
- `hr-feedback.tsx`: Arabic safe errors for all canonical `HRM_*` codes and HTTP
  statuses; never exposes internals. `hr-compliance-badge.tsx`: GLOBAL_MODE is
  never rendered green.
- Evidence: `hr-v2-api.test.ts` 27/27, `hr-workspace.test.tsx` 5/5.
- Gates: `tsc --noEmit` PASS, lint 0 errors, production build PASS.

## WS5 Task 9 — Employee Directory + Employee 360

- Commit: `d742045d` — `feat(hrm): add employee directory and 360 profile`
- Directory: safe join of `listEmployments` + `listPeople`, search/status/
  classification filters, Arabic labels, leak-proof restricted-field test.
- Employee 360: 10 tabs, capability UX gating, audited private PII read on open,
  lazy compensation/audit loads with ref guards, lifecycle confirm dialog with
  `effectiveDate` + `expectedVersion` + `Idempotency-Key`, terminate labelled
  `إنهاء خدمة` (never `حذف`).
- Evidence: directory 7/7, 360 15/15; full suite 765/765 at commit time.

## WS5 Task 10 — Org structure / jobs / positions / assignments / compliance

- Commit: `d591fa69` — `feat(hrm): add operational structure and compliance workspace`
- Org structure: `asOf` effective-dated org chart; future-dated revisions hidden
  at today, visible at their effective date (pinned by test).
- Positions: occupancy DERIVED from ACTIVE assignments covering today; ended
  assignment does not occupy (pinned). Freeze/close with `Idempotency-Key`; no
  manual occupancy toggle (asserted absent).
- Assignments: transfer/change-manager with `expectedVersion` +
  `Idempotency-Key`; `409 HRM_CONCURRENCY_CONFLICT` renders the canonical
  conflict message, never success.
- Compliance: override request form (explicit rule reference; backend
  re-validates HARD rules), approve/reject/revoke gated by APPROVE capability,
  `422 HRM_COMPLIANCE_BLOCKED` rendered safely.
- `/hr` dashboard: authoritative counts, per-surface independent fetch (a 403 on
  one surface does not blank the dashboard), no mock data.
- Gates at `d591fa69`: full suite 786/786, `tsc` PASS, lint 0 errors, production
  build PASS emitting 9 HR routes.

## WS5 Task 11 — Deterministic tenant cutover + rollback

Artifacts: `scripts/hrm/g0-cutover-tenant.sql`,
`scripts/hrm/g0-rollback-tenant.sql`, `HrCutoverStateIntegrationTest.java`.

### Integration test (PostgreSQL Direct)

```text
TEST_CLASS = HrCutoverStateIntegrationTest
TOTAL = 2, PASSED = 2, FAILURES = 0, ERRORS = 0, SKIPPED = 0
migratingTenantCanReadV1ButCannotWriteV1      : GET 200; POST/PATCH 409 HRM_MIGRATION_REQUIRED
canonicalTransitionRequiresZeroUnresolvedRows : unresolved rows → BLOCKED (never CANONICAL);
                                                empty tenant → LEGACY (WS2 semantics)
```

Task 11 Step 5 backend gate (same run, PostgreSQL Direct):

```text
11 gate classes — TOTAL=125 PASSED=125 FAILURES=0 ERRORS=0 SKIPPED=0
(HrApiV2ContractTest 35, HrApiV2AuthorizationTest 3, People 26, Employment 11,
Assignment 15, Structure 12, Sensitive 11, HrOpenApiContractTest 1,
PlatformApiCountTest 4, HrV1CompatibilityIntegrationTest 5, CutoverState 2)
```

Frontend (unchanged since `d591fa69`, re-verified on this tree): full suite
786/786 PASS.

### Defects found and fixed by rehearsal (root-cause records)

1. `HrCutoverStateIntegrationTest` (TEST_DEFECT, session-restart partial write):
   missing `BeforeEach` import; `stateOf` used transaction-local `set_config`
   which evaporates under autocommit; `reconcileTenant` used `executeUpdate` on
   a result-returning call; fixture cleanup order violated
   `fk_hr_employment_status_periods_employment`. All fixed locally; no product
   code touched.
2. `g0-cutover-tenant.sql` / `g0-rollback-tenant.sql`
   (CUTOVER_SCRIPT_DEFECT): psql does not interpolate `:'var'` inside
   dollar-quoted DO bodies — freeze/rollback blocks received the literal string
   and aborted; and transaction-local `set_config(..., true)` evaporates between
   autocommit statements, breaking tenant-scope assertion/RLS for every later
   statement. Fixed: session-scoped binding + `_g0_tenant_ctx` temp table.
3. `g0-rollback-tenant.sql` (CUTOVER_SCRIPT_DEFECT): people deletion ran while
   the ledger still referenced them (`fk_hr_legacy_employee_mappings_person`).
   Fixed with FK-safe ordering: capture ledger-owned people → unlink
   employments → remove person private/identifier sub-records → clear ledger
   refs → delete captured people → LEGACY. No safety guard was weakened.

### Cutover rehearsal (rehearsal tenant, local PostgreSQL Direct)

```text
CUTOVER_REHEARSAL = PASS
TENANT            = c1100000-0000-4000-8000-000000000011 (synthetic, disposable)
INITIAL_STATE     = LEGACY
FINAL_STATE       = CANONICAL
BACKFILLED_ROWS   = 3 people / 3 assignments / 1 org unit + 1 ACTIVE org unit
                    version / 1 ACTIVE position version
RECONCILIATION    = all gates passed (hr_reconcile_tenant_report)
DUPLICATES        = 0 duplicate canonical persons
TENANT_LEAKAGE    = 0 (all canonical rows map 1:1 into the tenant ledger;
                    cross-tenant migration call denied 42501)
RERUN_RESULT      = refused fail-closed ("already CANONICAL"), state unchanged
RESULT            = PASS
```

### Rollback rehearsals (rehearsal tenants only)

```text
ROLLBACK_REHEARSAL         = PASS
A (ambiguous employer)     : 2 effective LEs → cutover BLOCKED, never CANONICAL;
                             rollback → LEGACY; legacy employees preserved (3/3).
B (partial failure)        : duplicate user_id fixture → cutover fail-closed
                             BLOCKED with exactly 1 canonical person migrated;
                             rollback → LEGACY.
INITIAL_STATE              = BLOCKED (B) / BLOCKED (A)
ROLLBACK_ALLOWED           = true (only before CANONICAL)
ROWS_REMOVED               = ledger-scoped only: assignments 1→0, people 1→0;
                             ledger refs cleared (0 dangles)
UNRELATED_DATA_PRESERVED   = legacy employees 3/3, status-period history 3/3,
                             legacy departments 1/1
TENANT_ISOLATION           = cross-tenant migration call denied (42501);
                             all cleanup tenant-scoped
FINAL_STATE                = LEGACY
CANONICAL_GUARD            = rollback refused on CANONICAL tenant; state unchanged
RERUN_SAFETY               = after operator data fix, full cutover rerun reached
                             CANONICAL with 3/3 people (idempotent structure reuse)
RESULT                     = PASS
```

## WS5 Task 12 — Preview / production rehearsal

Authoritative definition (recovered from the plan, not from memory):

```text
TASK12_TITLE        = Preview, production rehearsal and final WS5 gate
TASK12_OBJECTIVE    = Run the full backend suite, the full web suite/build,
                      deploy a preview exercising the HR surfaces under the
                      production-equivalent BFF/reverse-proxy security model
                      with a disposable/backfilled HR tenant, and publish the
                      human acceptance checklist. Grants NO production
                      authorization.
TASK12_REQUIRED_FILES = .github/workflows/hrm-human-preview.yml (path-filtered
                      HRM preview workflow — required because the existing
                      erp-human-preview.yml harness is pinned to PR #912 and
                      cannot run PR #914); docs/hrm/g0/evidence/05-api-ui-cutover.md
TASK12_ACCEPTANCE   = full backend suite PASS (failures=0, errors=0);
                      full web suite + lint + build PASS; preview deployed from
                      the exact PR head with same-origin/cross-site verification;
                      human checklist published
TASK12_HUMAN_GATES  = /hr dashboard, /hr/employees directory, Employee 360,
                      Arabic/RTL, org chart as-of, jobs/positions vacancy
                      derivation, assignment transfer/change-manager,
                      PII hidden without capability, compensation hidden
                      without capability, Global Mode warning, hard compliance
                      block with no override path
```

Implementation record:

- `.github/workflows/hrm-human-preview.yml` created: path-filtered
  (`apps/web/**`, `apps/sanad-platform/**`), pinned to PR #914, exact-PR-head
  checkout + contract verification, least-privilege `sanad` role
  (NOSUPERUSER/NOBYPASSRLS), isolated PostgreSQL 16 service, bootstrap tenant,
  HR_MANAGER role grant to the preview admin, disposable legacy HR tenant seed
  + authoritative Task 11 cutover script (asserts CANONICAL and canonical row
  counts before the preview is published), same-origin login 200,
  cross-site login 403, HRM v2 `/api/v2/hr/people` reachable through the public
  BFF with the session token (200, non-empty), human checklist in the step
  summary, 90-minute keep-alive.
- The preview's seed+cutover SQL was validated locally against PostgreSQL Direct
  on a fresh bootstrap-shaped tenant: `STATE=CANONICAL, PEOPLE=3` (simulation
  tenant removed afterwards).
- `MERGED = NO`, `PRODUCTION_DEPLOYED = NO` — Task 12 grants no deployment
  authorization.

## Main reconciliation (origin/main advanced during implementation)

origin/main moved 23 commits ahead (Y2 orchestration platform, SCP closure,
production schema reconciliation) while HRM-G0 was in flight. Integrated via
merge commit `b4cd5e65` (no rebase of the shared branch):

- **Flyway version collisions resolved**: 17 HRM migrations
  (`V20260829_1..V20260904_3`) renumbered to `V20260905_1..17`, order
  preserved, because 5 version numbers collided with main's SCP/Workflow-Y2
  migrations (`20260829.1`, `20260830.1`, `20260901.1`, `20260902.1`,
  `20260904.1`). Dev DB `flyway_schema_history` aligned in place (17 rows,
  checksums unchanged). Production has neither set applied, so renumbering
  only affects this branch's lineage.
- **API count pin recomputed**: 717 (baseline) + 46 (main: SCP 29 + Y2 17) +
  58 (HRM) = **821**; `PlatformApiCountTest` PASS against the real merged
  OpenAPI surface.
- **Retired physical Employment DELETE remains absent**; main's new HR finders
  (`findByUserId`, `findActiveByDepartment/Position/UserIds`) kept, main's
  delete endpoint removed by the merge (Task 7 semantics authoritative).
- **Flyway java-migration policy honored**: main's
  `FlywayJavaMigrationsChainConsistencyTest` forbids java migrations on the
  production classpath; the java-based RBAC seed usage was removed from 38
  test fixtures (merged SQL chain covers RBAC seeding).
- CRM postgres fixtures keep self-sufficient clean+migrate over the merged
  SQL chain; `CrmPostgresMigrationTest` + `CrmFlywayHistoryAssertionTest`
  updated for the merged version inventory and PASS.

## Release migration rehearsal (PostgreSQL Direct, fresh DB)

```text
MIGRATION_REHEARSAL = PASS
METHOD              = release jar (built from merged tree) booted against a
                      fresh, empty database owned by role `sanad`
                      (NOSUPERUSER/NOBYPASSRLS); full Flyway chain from scratch
FROM_VERSION        = (empty database)
TO_VERSION          = v20260905.17
MIGRATIONS          = 169 applied, 0 failed (validate-on-migrate enabled)
DATA_INTEGRITY      = 218 tables / 354 FKs / 858 indexes / 174 RLS tables,
                      identical counts to the incrementally-migrated dev DB
                      (pre-merge parity check); post-merge dev DB converges to
                      the same schema via the same chain
RLS                 = fail-closed probes PASS (own tenant 1, other tenant 0,
                      no GUC 0)
APPLICATION_BOOT    = PASS (health UP: db/readiness/liveness/ssl all UP;
                      booted in ~30s including full migration)
EXISTING_TENANT_COMPATIBILITY = PASS (dev DB is the incremental path; every
                      full-context test boots against it with validation;
                      CI PostgreSQL Acceptance job SUCCESS on #3219)
RESULT              = PASS
```

## Release rollback rehearsal (distinct from Task 11 tenant rollback)

```text
ROLLBACK_REHEARSAL = PASS
PREVIOUS_SHA       = ab2b46e7 (origin/main, pre-HRM application)
TARGET_SHA         = b4cd5e65 (merged release candidate)
DB_SCHEMA          = post-merge schema at v20260905.17 (superset of main's)
METHOD             = A: previous application jar booted against the new schema;
                     DB is NOT rolled back (forward-safe; canonical HRM data
                     stays authoritative per Task 11 semantics)
BOOT_RESULT        = "Schema 'public' is up to date. No migration necessary."
                     Health UP (db/readiness/liveness); verified with strict
                     validation AND with FLYWAY_IGNORE_MIGRATION_PATTERNS
                     documented as belt-and-braces for applied-but-missing
                     HRM versions
DATA_LOSS          = none (no destructive operations; HRM tables simply
                     invisible to the previous application)
RECOVERY           = forward-fix path unchanged: re-deploy the new application
RESULT             = PASS
```

## Final security / architecture review (git diff origin/main...HEAD)

```text
SECURITY_REVIEW = PASS
1. physical Employment DELETE     : absent (diff removes main's endpoint; no
                                    repository delete method, no delete SQL)
2. secrets                        : none hardcoded; preview workflow password
                                    is run-scoped, masked, disposable-tenant
                                    only (mirrors main's ERP preview precedent)
3. RLS                            : no weakening; new tables FORCE RLS with
                                    tenant_isolation USING+WITH CHECK; the four
                                    DROP POLICY statements are drop+recreate of
                                    the same strict policies (idempotency)
4. RBAC                           : no wildcard expansion; capability seeds are
                                    explicit least-privilege codes
5. tenant isolation               : cross-tenant migration calls denied 42501;
                                    RLS fail-closed probes PASS
6. cross-module DB access         : none from HR code into CRM/workflow/billing
7. country-law hardcoding         : none in HR code (SA rules remain in
                                    country packs; compliance engine gate intact)
8. frontend logging/telemetry     : no console logging or beacons in HR surfaces
9. compensation exposure          : LIST projection amount-free; amount-bearing
                                    GET behind capability + audited read
10. cutover SQL                   : no credentials, no hostnames; operator
                                    supplies connection; fail-closed ON_ERROR_STOP
```

## CI records

```text
#3219  run 33901478062  sha 101e65c8  ALL GREEN
       (Maven Test Suite SUCCESS, CRM Integration SUCCESS,
        PostgreSQL Acceptance SUCCESS)
#3222  run 33908914116  sha fc6a56f8  (Tasks 8-11 chain)
#3223  run —            sha 52932531  (Tasks 8-12 chain; superseded by merge)
FINAL  to be dispatched on the merged release-candidate SHA (b4cd5e65 lineage)
```
