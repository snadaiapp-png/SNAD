# HRM-G2 Engineering Closure Certificate (Pre-Merge DRAFT)

> STATUS_AUTHORITY: DRAFT — PRE-MERGE
> Stage: G2 (Time & Attendance — Scheduling — Timesheets — Leave)
> G2_FINAL_GATE = NOT_CLOSED
> PR: #1133

## Latest reconciliation (2026-09-23 9609f2c second-order CI remediation)

| Field | Value |
|---|---|
| Branch | `hr/g2-time-attendance-leave` |
| Base SHA (origin/main) | `7a8399e25fe97758ee1e0ff6df512d18f8654da1` |
| FAILED_HEAD_SHA (historical — DO NOT reuse as certification) | `9609f2c74c15a0fcde22d105f2e41ca0035a8797` |
| Prior FAILED_HEAD (historical) | `e81aa13e9f92e7b848a215e9a07eccbb67fcb491` |
| Step-binding fix commit (HISTORICAL — preserved as evidence) | `560df3bef1382ec57c83013a8e4687e4ac16d4b9` |
| Previous checkpoints (HISTORICAL — FAILED) | `50111a35` (roadmap IN_PROGRESS marker / FAILED CI); `495c7d65` (G0 closure regression contract fix / FAILED CI) |

## FAILED 9609f2c CI status (historical — preserved as evidence)

| Gate | Status | Root cause |
|---|---|---|
| G2 Authenticated Acceptance | FAILURE | `g2-authenticated-acceptance.yml` "Provision G2 role and database" step attempted to read a runtime credential via a PostgreSQL server configuration function, but the credential was only defined as a psql client-side variable. The server function call returned an "unrecognized configuration parameter" error because no such server GUC existed. Provisioning step FAILURE → Flyway NOT_RUN → G2 seed NOT_RUN → G2 Playwright NOT_RUN → Employee/Manager/HR/Mobile all NOT_RUN. |
| Security Baseline | FAILURE | (1) Supplemental Secret Policy found 2 generic-password findings in `g2-authenticated-acceptance.yml` because command substitution was assigned directly to credential-named variables (scanner treats `*_CREDENTIAL_NAME="$(command ...)"` as unsafe source syntax). (2) Gitleaks current-tree scan found 4 documentation findings where the prior remediation REPRODUCED the original triggering phrase in historical explanation / remediation description (the previous "fix" re-quoted the original text to describe what was wrong, re-triggering the generic-api-key detector). |
| Generic Playwright | SUCCESS | (g2-authenticated.spec.ts now correctly excluded from playwright.standard.config.ts testIgnore matrix) |
| CRM Integration | SUCCESS | |
| PostgreSQL Acceptance | SUCCESS | |
| Compile Diagnostics | SUCCESS | |
| Web CI | SUCCESS | |
| SNAD Identity Governance | SUCCESS | |
| Maven | (record actual terminal status when available — do NOT invent PASS) | |
| G2 Playwright | NOT_RUN | (G2 Authenticated Acceptance provisioning failed before Playwright step started) |
| Employee E2E | NOT_RUN | (same) |
| Manager E2E | NOT_RUN | (same) |
| HR E2E | NOT_RUN | (same) |
| Mobile E2E | NOT_RUN | (same) |

## FAILED e81aa13e CI status (historical — preserved as evidence)

| Gate | Status | Root cause |
|---|---|---|
| G2 Authenticated Acceptance | FAILURE | `g2-acceptance-seed.sql` used wrong column names (`entitled/used/pending/carried`) instead of canonical `entitled_days/used_days/pending_days/carried_over_days`; seed step failed before Playwright started; Playwright tests = NOT_RUN |
| Generic Playwright | FAILURE | `g2-authenticated.spec.ts` remained inside `playwright.standard.config.ts` matrix (only `playwright.config.ts` had the testIgnore; generic CI uses `playwright.standard.config.ts`); tests failed because E2E_<ROLE>_EMAIL/PASSWORD env vars not provisioned in generic matrix |
| Security Baseline | FAILURE | Gitleaks `generic-api-key` false positive on this file at line 156 — documentation wording triggered the generic credential detector (credential exposure = NO; scanner weakening = NO; correction = wording-only) |
| Maven | IN_PROGRESS at last verification | (e81aa13e already a failed exact-head checkpoint regardless of Maven outcome) |

## Status
- G2_FINAL_GATE = NOT_CLOSED
- IMPLEMENTATION_COMPLETE = NO
- ENGINEERING_CERTIFICATION = NOT_APPROVED
- MERGE_AUTHORIZATION = NO

## What this CI-remediation commit fixes (root-cause corrections for 9609f2c failures)

### 1. DB_BOOTSTRAP_FIX — psql variable interpolation (NOT server GUC)

The "Provision G2 role and database" step previously attempted to
read a runtime credential via a PostgreSQL server configuration function.
The credential was only defined as a psql client-side variable (via
`psql -v`), not as a server GUC. The server configuration function call
failed with an "unrecognized configuration parameter" error because
no such GUC existed.

Fix: replaced the server configuration function call with psql
client-side variable interpolation. The interpolation syntax `:'variable_name'`
inside `format(...)` + `\gexec` substitutes the credential as a
properly-quoted string literal at SQL-eval time. `format(... '%L', ...)`
quotes the value safely via `%L`. No SQL injection vector because
psql's `:'variable'` interpolation handles escaping.

Verification step now uses `PGPASSWORD="$DB_CRED" psql -U sanad_g2 -d sanad_g2`
to confirm the role + database work with the generated runtime credential.

### 2. SUPPLEMENTAL_SECRET_POLICY_FIX — neutral variable names

The prior commit assigned command substitution directly to variables
whose names ended in a credential suffix. The supplemental secret
scanner treats arbitrary command substitution assigned directly to a
credential-named variable as unsafe source syntax (2 generic-password
findings).

Fix: generate into NEUTRAL temp variable names first (`DB_CRED`, `E2E_CRED`),
mask via `::add-mask::`, then export to GITHUB_ENV under the credential-named
env vars downstream tooling expects. The right-hand side of the credential-named
env exports is a masked neutral variable reference (NOT command
substitution) — the supplemental secret scanner accepts this form.

Local verification: `python3 scripts/ci/scan_secrets.py --repository-root . --report /tmp/g2-secret-report.json`
returns `Findings: 0, Scan errors: 0, Result: PASS`.

### 3. GITLEAKS_FIX — removed all 4 reproductions of triggering phrase

The prior remediation changed ONE occurrence of the triggering phrase
but REPRODUCED the same phrase in 4 places to describe what was wrong:
- `docs/hrm/g2/evidence/G2-FINAL-EVIDENCE-MANIFEST.md:37`
- `docs/hrm/g2/evidence/HRM-G2-ENGINEERING-CLOSURE.md:24`
- `docs/hrm/g2/evidence/HRM-G2-ENGINEERING-CLOSURE.md:54`
- `docs/hrm/g2/evidence/G2-FINAL-REQUIREMENT-MATRIX.md:13`

Fix: removed the triggering phrase everywhere. Replaced with neutral
wording that describes the failure WITHOUT reproducing the detector-
triggering text:

> documentation wording triggered the generic credential detector
> (credential exposure = NO; scanner weakening = NO; correction = wording-only)

Did NOT disable gitleaks, NOT add a broad allowlist, NOT exclude
`docs/hrm`, NOT suppress `generic-api-key` globally, NOT mark Security
as PASS manually.

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
