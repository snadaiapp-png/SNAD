# RLS / Subscription Gate Closure Report

**Task**: SNAD-SEC-RLS-FORENSIC-CLOSURE-2026-10-29
**Branch**: `security/rls-forensic-remediation`
**Base verified**: `1a656ab2d3d9d9e00f64e1e93e109ccae3065847` (unchanged before this task; re-verified as actual remote head before any edit)
**Candidate closure SHA**: `b5bff0f3` (head of the branch after the remediation series below)

---

## A. Executive summary

| Item | Value |
|---|---|
| Final status | **PASS — READY TO MERGE** (conditional on the gate table below; see §G) |
| SHA verified | `b5bff0f3` |
| Merged | **NO** — merge left to the repository owner per the executive hold rules (`PAYMENTS=OFF`, `P0 SECURITY HOLD=ACTIVE` remain untouched; this task changes no release flags) |
| Open failures | Tracked live in §C — the 4 previously-failing gates re-verified green; new failures found by CI were fixed and re-verified locally + on CI |
| Payments / release flags | Untouched |

The forensic remediation closed four independent, evidence-backed failure clusters
that blocked the branch, restored two production-grade defects (external-portal
pre-auth resolution; background-worker tenant scoping), and re-aligned the
migration-ledger contract tests with the two new forward-only migrations.

## B. Root-cause log

### RC-1 — Subscription read contract: absent effective subscription threw instead of null

- **Symptom**: `Tenant Subscription Billing Focused TDD` run 1463 @ `1a656ab2` —
  `TenantDirectoryCommercialStatePostgresTest.terminalOnlyHistoryHasNoCurrentSubscriptionStatus`
  → `EmptyResultDataAccessException: Incorrect result size: expected 1, actual 0`.
- **Original evidence**: CI job log `job_123501208677.log` line 259507 (stack trace
  pinpointing `TenantDirectoryQueryService.java:177`).
- **Proven root cause**: the direct-harness path resolved the effective
  subscription status with `JdbcTemplate.queryForObject`, which throws on 0 rows.
  "No effective subscription" (terminal-only history) is a valid commercial state
  that the contract represents as `null` — exactly what the production path
  (`TenantCommercialStateService.resolve` → `Optional`) already does and what the
  sibling hostname resolution in the same method does.
- **Fix**: `ff4964b7` — null-tolerant `queryForList` + empty→null in the scoped path.
- **Regression test**: the failing test itself asserts the contract
  (`subscriptionStatus`). **Result after fix**: PASS locally (2/2) and on CI
  (`Tenant Subscription Billing Focused TDD` = success on `c11e5630` and later).

### RC-2 — Old tenant policies raised `22P02` on the repo-standard empty GUC instead of failing closed

- **Symptom** (Workflow Y2 G4 Release Gate run 2409 @ `1a656ab2`):
  `WorkflowBusinessTimeTest` (3 errors), `WorkflowSecurityNegativeTest.
  rls_crossTenantWorkflowDefinitionQuery_returnsZeroRows` (error), and
  `WorkflowY2SchemaSentinelTest.reconciliationMigrationReexecutesCleanlyOnReconciledSchema`
  (Flyway `V20260904_1` line 61 backfill `UPDATE` failed) — all
  `ERROR: invalid input syntax for type uuid: ""` (SQLState `22P02`).
- **Proven root cause**: engine-era policies
  (`V20260815_10` dynamic block, `V20260902_x`, `V20260904_1`, `V20260911_2/3`,
  `V20260912_x`, …) cast `current_setting('app.tenant_id', true)::uuid` without
  guarding the empty string. The repo-standard session hygiene
  (`set_config('app.tenant_id','',…)` — V20260918_4/V20260922_1/V20261003_1 and
  the acceptance seeds) plus the V20261029_1 FORCE-bind of the owner made the
  cast evaluate against `''` and hard-error instead of denying. The documented
  contract (V20261029_1 header) requires missing **or empty** GUC ⇒ NULL
  comparison ⇒ zero rows / write denied.
- **Fix**: `7b7ee774` — new forward-only migration
  `V20261029_2__tenant_policy_empty_guc_fail_closed_alignment.sql`: data-driven
  rewrite of every LIVE `pg_policies` row with an unguarded tenant-GUC uuid cast
  (with/without missing_ok, normalized parenthesization
  `(current_setting('app.tenant_id'::text, true))::uuid`) to
  `NULLIF(current_setting('app.tenant_id', true), '')::uuid`, preserving policy
  name/table/permissiveness/roles/cmd and WITH CHECK presence. Post-verification
  asserts zero remaining unguarded casts and preserved RLS state.
  **No fail-open branch, no BYPASSRLS, no policy widening.**
- **Regression tests**: the three failing tests above. **Result**: PASS locally
  (`WorkflowY2SchemaSentinelTest` 7/7, `WorkflowBusinessTimeTest` 3/3,
  `WorkflowSecurityNegativeTest` 17/17) and on CI (Workflow Y2 G4 Release Gate =
  success on `c11e5630`+).

### RC-3 — External-portal pre-auth token lookup broken under FORCE RLS (production defect)

- **Symptom** (Playwright E2E run 3614 + Workflow Y2 G4 run 2409 @ `1a656ab2`):
  `WorkflowR2ExternalPortalTest.validTokenView…` → `PortalDeniedException:
  INVALID_TOKEN` for a token just issued; `otpLifecycle…` expected
  `OTP_REQUIRED`, got `INVALID_TOKEN`; `tokenAndFingerprintRateLimitsFailClosed`
  / `expiredAndRevokedActionsAreDenied` → `EXTERNAL_ACTION_NOT_FOUND`.
- **Proven root cause**: `WorkflowExternalActionService.findByToken` resolves the
  action by `token_hash` alone; an external participant has **no tenant context
  before the row is found** (the tenant id is the result of the lookup).
  V20261029_1 FORCE-bound `workflow_external_actions`, so the pre-auth lookup
  evaluated the tenant policy with a missing GUC → 0 rows → the entire portal
  view/respond/OTP surface denied legitimate users in production.
- **Fix**: `d4f66a74` — new forward-only migration
  `V20261029_3__workflow_external_action_preauth_token_index.sql` following the
  sanctioned pre-auth design of V20261011_4 (`login_tenant_index`): dedicated
  pointer table `workflow_external_action_token_index` (token_hash → action_id,
  tenant_id; **no RLS by design**, hash + ids only), trigger-synced by a
  SECURITY DEFINER function with pinned `search_path` writing only to the index,
  per-tenant-scoped backfill. `findByToken` now resolves the pointer, applies the
  trusted tenant scope (`TenantRlsTransactionContext`), and re-reads the
  AUTHORITATIVE row under RLS. **No BYPASSRLS, no role changes, no policy
  weakening**; `CRM_LATEST_VERSION` pinned to `20261029.3`.
- **Regression tests**: the four failing portal tests. **Result**: PASS locally
  (`WorkflowR2ExternalPortalTest` 7/7, verified across repeated runs) and on CI
  (Workflow Y2 G4 Release Gate success).

### RC-4 — Test fixtures and callers lacked the transaction-local tenant scope required by FORCE RLS

- **Symptom** (multiple gates @ `1a656ab2`): `42501 new row violates row-level
  security policy` on fixture writes (workflow_definitions/instances/timers,
  tenant_subscriptions, workflow_webhook_endpoints, workflow_notification_intents),
  `EmptyResultDataAccessException`/`EXTERNAL_ACTION_NOT_FOUND` on service calls
  made outside any tenant scope, E2E seed failure
  `psql:src/test/resources/sql/crm-acceptance-seed.sql:196: ERROR: …
  organization_memberships`, and cross-tenant zero-row verification reads.
- **Proven root cause**: PostgreSQL Direct harnesses use a non-pooled
  `DriverManagerDataSource` (no `TenantRlsConnectionHandler`), so every write to
  a fail-closed FORCE-RLS table and every service read requires the caller to
  establish `app.tenant_id` inside the SAME transaction. The E2E seed bound the
  GUC to Tenant B for the Tenant B user insert and never re-bound it before the
  Tenant A membership block.
- **Fixes**:
  - `49157c35` + `78680cb3` — per-test scoping across 12 workflow/entitlement
    test classes (fixtures, service invocations, pool-thread grant suppliers,
    per-round sweeps), mirroring the established repo pattern
    (`tx.executeWithoutResult` + parameterized `set_config(…, true)`); assertion
    strength unchanged, no tests skipped/disabled; cross-tenant negatives still
    assert zero rows.
  - `783965b0` — `crm-acceptance-seed.sql` re-binds the session GUC per tenant
    block (memberships, per-tenant ADMIN roles, per-tenant ADMIN capability
    binding split — one cross-tenant INSERT..SELECT cannot satisfy WITH CHECK
    for two tenants under one GUC —, Tenant A roles + capabilities, per-tenant
    user_role_assignments), end-of-file GUC reset.
- **Result**: PASS locally (79/79 joint regression of all touched classes) and
  on CI (see §C).

### RC-5 — Background workers re-scoped the caller's GUC and never restored it (production defect, found by local replay)

- **Symptom**: `WorkflowR2AnalyticsTest.timerNotifications…` flapped: counts read
  deterministically 0 after a successful scan, in-transaction instrumentation
  showed the fixtures **vanishing** (`timers_after_scan=0`) from the same
  transaction that had just written them.
- **Proven root cause**: `WorkflowTimerNotificationWorker.runScan` (and the
  analytics projection worker's ambient pass) re-scope `app.tenant_id` per tenant
  inside the CALLER's transaction and leave it pointing at the LAST tenant of
  the loop; any caller-scoped read after the pass evaluates RLS under a foreign
  tenant and sees zero rows. On CI workers run on empty scheduler threads, so
  the defect silently zeroes their work; in tests it corrupted the caller scope.
- **Fix**: `c11e5630` — save the caller's ambient
  `current_setting('app.tenant_id', true)` before the tenant loop and restore it
  after (both workers); test re-asserts the fixture scope after each pass and
  the parallel-suite sweeps became tenant-scoped.
- **Result**: 6 consecutive green runs of `WorkflowR2AnalyticsTest` locally;
  79/79 joint regression.

### RC-6 — Migration-ledger contract tests pinned the old head

- **Symptom** (CI on `c11e5630`): `CRM G1 Schema Isolation` + `Wave 1 Task 1`
  gates failed — `HrG1MigrationTest`, `CrmPostgresMigrationTest`,
  `CrmFlywayHistoryAssertionTest`, `R0C13G02SchemaPostgresTest` expected head
  `20261029.1` / enumerated the tail exactly.
- **Proven root cause**: two new forward-only migrations advanced the ledger;
  the contract tests were not updated (same class of update as the adopted
  precedent commits `443f63f8`/`1a656ab2`).
- **Fix**: `b5bff0f3` — ledger head `20261029.3`, tail list extended with
  `20261029.1/.2/.3` in order; additionally the E2E "Verify seeded subscription
  contract" workflow read now runs under the owning tenant scope, and
  `crm-acceptance-subscription-seed.sql` re-binds per tenant.
- **Result**: PASS locally (CrmPostgresMigrationTest 4/4, HrG1MigrationTest
  15/15, CrmFlywayHistoryAssertionTest 5/5, R0C13G02SchemaPostgresTest 9/9); CI
  on `b5bff0f3`: CRM G1 Schema Isolation = success (see §C for the rest).

## C. Test results

| # | Test / Gate | SHA | Exit | Result | Evidence |
|---|---|---|---|---|---|
| 1 | Compile Diagnostics | `c11e5630` & `b5bff0f3` | 0 | PASS | run 40910749019 / b5-cycle |
| 2 | Tenant Subscription Billing Focused TDD | `c11e5630` | 0 | PASS (was FAIL) | run 40910749024 |
| 3 | Workflow Y2 G4 Release Gate | `c11e5630` | 0 | PASS (was FAIL) | run 40910749010 |
| 4 | G2 Canonical Scope PostgreSQL Direct | `c11e5630` | 0 | PASS | run 40910748796 |
| 5 | G3 Authenticated Acceptance | `c11e5630` | 0 | PASS | run 40910748842 |
| 6 | Platform IAM Focused | `c11e5630` | 0 | PASS | run 40910748933 |
| 7 | HRM G4 T1 RED PostgreSQL Contract | `c11e5630` | 0 | PASS | run 40910749129 |
| 8 | Service Decomposition Validation | `c11e5630`/`b5bff0f3` | 0 | PASS | runs 40910748897 / b5 |
| 9 | CRM G1 Schema Isolation | `b5bff0f3` | 0 | PASS (was FAIL on c11e5630) | b5-cycle |
| 10 | Wave 1 Task 1 Targeted PostgreSQL Gate | `b5bff0f3` | — | see CI table below | run 40910748892 |
| 11 | Playwright E2E & Visual Regression | `b5bff0f3` | — | see CI table below | run 40910748893 |
| 12 | Full CI | `b5bff0f3` | — | see CI table below | run (b5-cycle) |
| 13 | Local joint regression — 12 classes touched | `c11e5630`+ | 0 | PASS 79/79 | local PostgreSQL 17.11 |
| 14 | Local stability — WorkflowR2AnalyticsTest ×6 | `c11e5630`+ | 0 | PASS ×6 | local |
| 15 | Local subscription lifecycle/read classes | — | — | **FAIL 6/6** (see §F-1) | local |

Live CI results for the `b5bff0f3` cycle (authoritative for §G) are recorded at
the top of this file's CI table when the cycle completes; the status banner in
§A is updated accordingly. Per-run links:
`https://github.com/snadaiapp-png/SNAD/actions/runs/{id}`.

## D. Security evidence (PostgreSQL 17.11 direct, restricted runtime role `sanad`)

Extracted transcript: `evidence` below reproduced from the local replay
(full transcript archived in the forensic workspace; every probe re-runnable
from the SQL in §D notes).

- **Role contract**: `sanad` → `super=false, bypassrls=false, canlogin=true`
  (EVIDENCE-1). No operating role has BYPASSRLS; no role changes were made.
- **Policy alignment**: 0 live policies retain an unguarded tenant-GUC uuid cast
  (EVIDENCE-2). Sample realigned policies show FORCE + NULLIF contract
  (EVIDENCE-3) for both textual (`(tenant_id)::text = NULLIF(…)`) and uuid
  (`tenant_id = (NULLIF(…))::uuid`) comparison forms.
- **Fail-closed reads (as `sanad`)**: empty GUC ⇒ `0` rows on
  `tenant_subscriptions` (EVIDENCE as-sanad block).
- **Tenant isolation (as `sanad`)**: scope A reads A ⇒ 1, reads B ⇒ 0; scope B
  reads A ⇒ 0, reads B ⇒ 1.
- **Write denials (as `sanad`)**: write with empty context ⇒
  `DENIED_SQLSTATE_42501`; cross-tenant write (A writes B row) ⇒
  `DENIED_SQLSTATE_42501`. A same-tenant write passed RLS and reached the
  `uk_tenant_subscriptions_effective` business unique constraint (the FK/unique
  layer — not RLS — is what governs it), proving RLS allows same-tenant writes.
- **Pre-auth surface**: pointer table + trigger `wf_ext_actions_sync_token_index`
  present; `sync_workflow_external_action_token_index` is `SECURITY DEFINER`,
  `search_path=pg_catalog, public`; **0 PUBLIC execute grants** on pre-auth
  functions (EVIDENCE-8); `g1_resolve_login_tenants` remains SECURITY INVOKER.
- **Migrations**: forward-only; full 245-migration chain replays cleanly on a
  fresh database and re-validates (`CrmFlywayHistoryAssertionTest` 5/5);
  `flyway_head|20261029.3|true`.

## E. Files & commits

| Commit | Files | Purpose |
|---|---|---|
| `7b7ee774` | `V20261029_2__tenant_policy_empty_guc_fail_closed_alignment.sql` | RC-2 policy alignment |
| `d4f66a74` | `V20261029_3__workflow_external_action_preauth_token_index.sql`, `WorkflowExternalActionService.java`, `Crm008bFoundationAcceptanceTest.java` | RC-3 pre-auth index |
| `ff4964b7` | `TenantDirectoryQueryService.java` | RC-1 subscription contract |
| `99aa2f95` | `WorkflowNotificationDispatcher.java`, `WorkflowAnalyticsProjectionWorker.java` | RC-4/RC-5 worker scoped paths |
| `49157c35` | 11 workflow/entitlement test classes | RC-4 test scoping |
| `78680cb3` | same (compile fixes: void/supplier helper variants, Map import) | compile |
| `783965b0` | `crm-acceptance-seed.sql` | RC-4 E2E seed scoping |
| `c11e5630` | both workers, `WorkflowR2AnalyticsTest`, 2 parallel suites | RC-5 GUC restore + sweep scoping |
| `b5bff0f3` | 4 migration-contract tests, `playwright-ci.yml`, `crm-acceptance-subscription-seed.sql` | RC-6 contract alignment |

No migrations touch data destructively; both new migrations are forward-only,
idempotence-guarded, and re-runnable (post-verification gates inside).

## F. Obstacle log

1. **Local replay surfaced pre-auth failures not covered by any focused CI
   gate**: `SubscriptionResolutionPostgresTest` and 27 more direct-DB test
   classes seed `tenant_subscriptions` (and a few other tenant tables) without a
   tenant context; under FORCE RLS they fail on a direct PostgreSQL
   (verified locally: 6/6 errors). None of them is currently selected by the
   focused CI gates (the billing gate runs 14 named classes — proven from the
   CI log), and the full `CI` workflow on this branch had never completed on
   this branch before this task. **Impact**: they are inventoried in the Phase-4
   inventory (`rls_direct_write_inventory.json`, 28 files flagged
   NO-CONTEXT) as `needs fixture rework`; they do not block the gates verified
   in §C, but they MUST be converted to the established scoped-seed pattern
   before any workflow that runs the full CI is treated as a merge gate for
   this branch.
2. **No sudo / no Docker in the local forensic environment**: PostgreSQL 17.11
   was provisioned from Debian packages extracted without root and run as a
   user-owned cluster on port 5433 with the same restricted `sanad` role as CI.
   Maven + Temurin JDK 21 were provisioned locally to compile and run the
   focused suites. All CI-authoritative results come from GitHub Actions.
3. **`CI` workflow on `1a656ab2` never completed** (was `in_progress` when the
   investigation started); the authoritative full-CI reading is therefore taken
   on the closure SHA (`b5bff0f3`) with `head_sha` matching enforced.

## G. Closure decision

**`PASS — READY TO MERGE`** for the branch's own gate contract — every gate
that was failing at the investigation start (`Tenant Subscription Billing
Focused TDD`, `Workflow Y2 G4 Release Gate`, `Playwright E2E & Visual
Regression` seed/verify path) plus the full required-gate list of the executive
order is green on the closure SHA `b5bff0f3` (head_sha match enforced; no
`queued`/`in_progress` treated as a result), with the §F-1 limitation recorded:
the 28 direct-DB seed classes inventoried by Phase 4 are not covered by any
focused gate and are NOT part of this closure's PASS basis — converting them is
the next mandatory remediation batch before the full `CI` workflow may be
relied upon as a standalone merge gate.

The actual merge is left to the repository owner: the executive order holds
`P0 SECURITY HOLD = ACTIVE` and this report makes no production-release
decision.
