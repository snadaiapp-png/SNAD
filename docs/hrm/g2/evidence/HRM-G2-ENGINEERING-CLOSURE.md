# HRM-G2 Engineering Closure Certificate (Pre-Merge DRAFT)

> STATUS_AUTHORITY: DRAFT — PRE-MERGE
> Stage: G2 (Time & Attendance — Scheduling — Timesheets — Leave)
> G2_FINAL_GATE = NOT_CLOSED
> PR: #1133

## Latest reconciliation (2026-09-23 e81aa13e root-cause remediation)

| Field | Value |
|---|---|
| Branch | `hr/g2-time-attendance-leave` |
| Base SHA (origin/main) | `7a8399e25fe97758ee1e0ff6df512d18f8654da1` |
| FAILED_HEAD_SHA (historical — DO NOT reuse as certification) | `e81aa13e9f92e7b848a215e9a07eccbb67fcb491` |
| Step-binding fix commit (HISTORICAL — preserved as evidence) | `560df3bef1382ec57c83013a8e4687e4ac16d4b9` |
| Previous checkpoints (HISTORICAL — FAILED) | `50111a35` (roadmap IN_PROGRESS marker / FAILED CI); `495c7d65` (G0 closure regression contract fix / FAILED CI) |

## FAILED e81aa13e CI status (historical — preserved as evidence)

| Gate | Status | Root cause |
|---|---|---|
| G2 Authenticated Acceptance | FAILURE | `g2-acceptance-seed.sql` used wrong column names (`entitled/used/pending/carried`) instead of canonical `entitled_days/used_days/pending_days/carried_over_days`; seed step failed before Playwright started; Playwright tests = NOT_RUN |
| Generic Playwright | FAILURE | `g2-authenticated.spec.ts` remained inside `playwright.standard.config.ts` matrix (only `playwright.config.ts` had the testIgnore; generic CI uses `playwright.standard.config.ts`); tests failed because E2E_<ROLE>_EMAIL/PASSWORD env vars not provisioned in generic matrix |
| Security Baseline | FAILURE | Gitleaks `generic-api-key` false positive on this file at line 156 — prose "G2 OpenAPI completeness, concurrency/idempotency proof" resembled an API-key assignment |
| Maven | IN_PROGRESS at last verification | (e81aa13e already a failed exact-head checkpoint regardless of Maven outcome) |

## Status
- G2_FINAL_GATE = NOT_CLOSED
- IMPLEMENTATION_COMPLETE = NO
- ENGINEERING_CERTIFICATION = NOT_APPROVED
- MERGE_AUTHORIZATION = NO

## What this CI-remediation commit fixes (root-cause corrections for e81aa13e failures)

### 1. SEED_SCHEMA_FIX — `g2-acceptance-seed.sql` column names corrected

The seed used wrong column names: `entitled/used/pending/carried`. Canonical
schema (V20260923_1) defines: `entitled_days/used_days/pending_days/carried_over_days`.
Replaced the INSERT column list to match the real migrated schema. The
canonical schema is NOT modified to satisfy the seed — the seed conforms
to the schema.

### 2. GENERIC_PLAYWRIGHT_CONFIG_FIX — `playwright.standard.config.ts` testIgnore

The generic CI workflow runs `npx playwright test --config=playwright.standard.config.ts`.
Only `playwright.config.ts` had `**/g2-authenticated.spec.ts` in testIgnore;
`playwright.standard.config.ts` did NOT. Added the entry to
`playwright.standard.config.ts` testIgnore array so the generic visual
matrix no longer runs the G2 spec (which requires isolated data +
secrets that only the dedicated G2 workflow provisions).

### 3. SECURITY_FALSE_POSITIVE_FIX — reworded prose at line 156

Reworded "G2 OpenAPI completeness, concurrency/idempotency proof" to
"contract-schema coverage and concurrency/idempotency evidence" (no
"API" + "completeness" + comma sequence that triggered gitleaks
`generic-api-key` rule). Did NOT disable gitleaks, NOT add a broad
allowlist, NOT exclude `docs/hrm`, NOT suppress `generic-api-key`
globally, NOT mark Security as PASS manually.

### 4. EPHEMERAL_CREDENTIALS — removed committed password literals

The dedicated G2 workflow previously committed literals:
`E2E_*_PASSWORD = TestPass123!` and `DATABASE_PASSWORD = sanad_g2_pass`.

Replaced with per-run ephemeral credentials generated at runtime:
- DB_PASSWORD = `openssl rand -base64 32 | tr -d '/+=' | head -c 40`
- G2_E2E_PASSWORD = `openssl rand -base64 32 | tr -d '/+=' | head -c 32`

Both values are masked via `::add-mask::$VALUE`, exported to GITHUB_ENV,
and never printed. The seed SQL receives the G2 E2E password via psql
variable binding (`-v g2_e2e_password="$G2_E2E_PASSWORD"`) so the cleartext
never appears in SQL text either; bcrypt hash computed at runtime via
`crypt(:'g2_e2e_password', gen_salt('bf', 10))`.

### 5. SWALLOWED_ERRORS — removed `.catch(() => false)` patterns

Removed `.catch(() => false)` from the `logoutThroughUi` helper in
`g2-auth-session.ts`. Now uses explicit `logoutBtn.first().click({ timeout: 5_000 })`
(deterministic locator; fail-closed on missing logout button via Playwright
timeout, not silent fallback). Removed the unused `findLeaveRequestIdByReason`
helper from `g2-authenticated.spec.ts` (it had `.isVisible().catch(() => false)`).

### 6. MOBILE_REAL_MUTATION — mobile test now performs real clock-in/out

The mobile test previously only verified that a clock-in OR clock-out
button was visible (no mutation). Now performs a REAL state mutation:
- Determines canonical starting state (clocked in vs out)
- Clicks whichever button is visible (clock-in OR clock-out — both are
  real mutations)
- Waits for the matching API response (POST /api/v2/hr/time/attendance/clock-in
  OR clock-out)
- Verifies the API response is 200
- Verifies the opposite button is now visible (state transition persisted)

No `if button visible => act; else => PASS` fallback. Both branches
perform a mandatory mutation.

### 7. PLAYWRIGHT_PROJECT_CONFIG_VERIFICATION — new CI step

Added a "Verify Playwright project configs" step to the G2 workflow
that runs `npx playwright test --config=playwright.standard.config.ts --list`
and asserts `g2-authenticated.spec.ts` count = 0 (generic matrix must
NOT run G2), then runs `npx playwright test --config=playwright-g2.config.ts --list`
and asserts count > 0 (dedicated matrix MUST run G2). Fails the G2 job
if the routing is wrong.

## Honest scope classification (do NOT overclaim)

### POSTGRESQL_STEP_BINDING_REGRESSION vs FULL_BACKEND_LEAVE_LIFECYCLE

`HrG2LeavePostgresIntegrationTest` uses real JDBC repositories for
step-binding data, but mocks `WorkflowApprovalService`,
`WorkflowExecutionService`, `WorkflowGraphExecutionService`, and
`WorkflowEntitlementGuard`, and manually simulates Manager → HR progression.

Therefore classified accurately as:
- STEP_BINDING_REGRESSION = IMPLEMENTED (4 tests, useful regression evidence)
- FULL_BACKEND_LEAVE_LIFECYCLE = NOT_PROVEN (no true service-integration suite exists yet)

The test is NOT deleted; it remains as step-binding regression evidence.
But it is NOT used as proof of full service lifecycle.

### OPENAPI_PATH_METHOD_COVERAGE vs FULL_OPENAPI_CONTRACT

`HrG2OpenApiContractTest` verifies only paths/methods. It does NOT
verify:
- request schemas
- response schemas
- operationIds
- 400/401/403/404/409 error codes
- Idempotency-Key header contract
- expectedVersion / If-Match optimistic locking contract

Therefore classified accurately as:
- OPENAPI_PATH_METHOD_COVERAGE = IMPLEMENTED
- FULL_OPENAPI_CONTRACT = NOT_YET

## Verification Status

### Local frontend verification (all PASS)
- `npm run typecheck` → PASS
- `npm run lint` → 0 errors (65 pre-existing warnings, none new)
- `npm test` (vitest) → 1034/1034 PASS (115 files)
- `npm run brand:check` (SDS) → PASS
- `scripts/ci/check_i18n_keys.py` → 1125 = 1125 PASS
- `npm run build` → ✓ Compiled successfully
- `scripts/ci/check-performance-budget.py` → PASS
- Playwright project config routing: generic matrix g2-authenticated.spec.ts count = 0; dedicated G2 matrix count > 0

### Local backend verification (NOT_RUN_ENVIRONMENT_LIMITATION)
- LOCAL_BACKEND_COMPILE = NOT_RUN_ENVIRONMENT_LIMITATION (no local JDK)
- LOCAL_BACKEND_TESTS = NOT_RUN_ENVIRONMENT_LIMITATION (no local JDK/PostgreSQL)
- LOCAL_G2_AUTH_E2E = NOT_RUN_ENVIRONMENT_LIMITATION (requires CI environment)

### Backend verification relies on fresh exact-head CI on the NEW SHA

## Required Fresh Exact-Head CI (on the NEW SHA after this commit)

| Gate | Required | Status |
|---|---|---|
| Compile Diagnostics | SUCCESS | PENDING |
| Web CI | SUCCESS | PENDING |
| SNAD Identity Governance | SUCCESS | PENDING |
| Maven Test Suite | SUCCESS | PENDING |
| PostgreSQL Acceptance | SUCCESS | PENDING |
| CRM Integration | SUCCESS | PENDING |
| Security Baseline | SUCCESS | PENDING (gitleaks false positive fixed) |
| Schema Isolation | SUCCESS | PENDING |
| Performance Baseline | SUCCESS | PENDING |
| Backup Restore Validation | SUCCESS | PENDING |
| Pre-Merge Operational Smoke | SUCCESS | PENDING |
| Workflow Y2 G4 Release Gate | SUCCESS | PENDING |
| Stage 07 Artifact Provenance | SUCCESS | PENDING |
| API Contract Validation | SUCCESS | PENDING |
| Service Decomposition Validation | SUCCESS | PENDING |
| Generic Playwright E2E & Visual Regression | terminal result | PENDING (g2-authenticated.spec.ts now excluded from this matrix via testIgnore in playwright.standard.config.ts) |
| G2 Authenticated Acceptance (dedicated) | SUCCESS | PENDING (seed schema fixed; ephemeral credentials; Playwright routing verified) |

G2-specific evidence:

| Gate | Required | Status |
|---|---|---|
| G2_EMPLOYEE_E2E | PASS | PENDING |
| G2_MANAGER_E2E  | PASS | PENDING |
| G2_HR_E2E       | PASS | PENDING |
| G2_MOBILE_E2E   | PASS (real mutation) | PENDING |
| G2_FAILED       | 0    | PENDING |
| G2_SKIPPED_REQUIRED | 0 | PENDING |

## Authorization
- IMPLEMENTATION_COMPLETE = NO
- ENGINEERING_CERTIFICATION = NOT_APPROVED
- FINAL_EXACT_HEAD_CI = PENDING (must run on new head SHA)
- MERGE_AUTHORIZATION = NO

Do NOT merge until all required CI on the new SHA is green AND the
broader G2 closure (backend lifecycle evidence, authenticated
Employee/Manager/HR desktop/mobile browser journeys, contract-schema
coverage and concurrency/idempotency evidence, tenant isolation/RLS
evidence, closure manifest, independent approval) is genuinely
complete per directive §17.
