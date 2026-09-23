# G2 Final Evidence Manifest

## Status
```
G2_FINAL_GATE = NOT_CLOSED
IMPLEMENTATION_COMPLETE = NO
G2_FINAL_EXACT_HEAD_SHA = NOT_ASSIGNED (set after final push; current local HEAD pre-commit = e81aa13e, FAILED)
REMOTE_EXACT_HEAD_CI = PENDING (new SHA after this commit)
AUTHENTICATED_G2_E2E = NOT_PROVEN (tests + dedicated workflow + G2 acceptance seed SQL created; e81aa13e FAILED due to seed schema mismatch; new SHA PENDING)
BACKEND_LIFECYCLE_INTEGRATION = NOT_PROVEN
  - HrG2LeavePostgresIntegrationTest is STEP_BINDING_REGRESSION only (mocks workflow services; manually simulates progression)
  - FULL_BACKEND_LEAVE_LIFECYCLE = NOT_PROVEN (no true service-integration suite exists yet)
OPENAPI_PATH_METHOD_COVERAGE = IMPLEMENTED (HrG2OpenApiContractTest verifies paths/methods)
FULL_OPENAPI_CONTRACT = NOT_YET (no full request/response schema + 4xx + Idempotency-Key + expectedVersion/If-Match assertions)
INDEPENDENT_APPROVAL = NOT_REQUESTED
MERGE_AUTHORIZATION = NO
POST_MERGE_A_F = NOT_RUN
```

## FAILED e81aa13e CI status (historical — do NOT reuse as certification)

```
G2 Authenticated Acceptance = FAILURE
  reason = seed/schema mismatch in g2-acceptance-seed.sql
    (used entitled/used/pending/carried instead of canonical
     entitled_days/used_days/pending_days/carried_over_days)
  Playwright tests = NOT_RUN (seed step failed before Playwright started)

Generic Playwright = FAILURE
  reason = g2-authenticated.spec.ts remained inside playwright.standard.config.ts matrix
    (only playwright.config.ts had the testIgnore; generic CI uses playwright.standard.config.ts)
  tests failed because E2E_<ROLE>_EMAIL/PASSWORD env vars not provisioned in generic matrix

Security Baseline = FAILURE
  reason = Gitleaks generic-api-key false positive on
    docs/hrm/g2/evidence/HRM-G2-ENGINEERING-CLOSURE.md line 156
    (prose "G2 OpenAPI completeness, concurrency/idempotency proof"
     resembled an API-key assignment)

Maven = IN_PROGRESS at last verification
  (e81aa13e already a failed exact-head checkpoint regardless of Maven outcome)
```

## Local Tests (frontend)
```
LOCAL_FRONTEND_TESTS = PASS
  - typecheck = PASS
  - lint = 0 errors (65 pre-existing warnings — none added by this remediation)
  - SDS (brand:check) = PASS — "SNAD identity validation passed"
  - i18n parity = PASS — 1125 = 1125 keys
  - vitest (full) = PASS — 1034/1034 tests across 115 files
  - Next.js build = PASS — "✓ Compiled successfully"
  - perf budget = PASS — all routes within budget
  - Playwright project config routing = PASS
    - generic matrix (playwright.standard.config.ts) g2-authenticated.spec.ts count = 0
    - dedicated G2 (playwright-g2.config.ts) g2-authenticated.spec.ts count > 0
```

## Local Tests (backend)
```
LOCAL_BACKEND_COMPILE = NOT_RUN_ENVIRONMENT_LIMITATION (no local JDK)
LOCAL_BACKEND_TESTS = NOT_RUN_ENVIRONMENT_LIMITATION (no local JDK/PostgreSQL)
LOCAL_G2_AUTH_E2E = NOT_RUN_ENVIRONMENT_LIMITATION
  (requires CI environment: host-native PostgreSQL + Spring Boot + Next.js +
   Playwright browsers + G2 acceptance seed — only the dedicated
   .github/workflows/g2-authenticated-acceptance.yml can provision this
   end-to-end. Do NOT call local NOT_RUN = PASS.)
```

## Push artifacts (this CI-remediation commit)
```
NEW FILES (preserved from prior push):
  - apps/sanad-platform/src/test/resources/sql/g2-acceptance-seed.sql
      G2 acceptance tenant + plan + subscription + WORKFLOW module entitlement
      + 3 users + 3 hr_employees (Employee→Manager) + 3 RBAC roles +
      role_capabilities + user_role_assignments + leave balance.
      [CORRECTED] Column names now match canonical schema:
      entitled_days, used_days, pending_days, carried_over_days.
      [CORRECTED] Password hash uses psql variable binding:
      crypt(:'g2_e2e_password', gen_salt('bf', 10)) — cleartext never
      appears in SQL text; password is generated at runtime by CI workflow.
  - apps/web/e2e/g2-auth-session.ts
      Login helper (mirrors crm-auth-session.ts pattern). FAIL-CLOSED when
      E2E_<ROLE>_EMAIL/PASSWORD env vars missing.
      [CORRECTED] Removed .catch(() => false) on logout helper; now uses
      explicit locator assertion (deterministic, fail-closed on missing
      logout button).
  - apps/web/playwright-g2.config.ts
      Single-project Playwright config for G2 stateful journey (no locale/
      theme matrix — would cause mutable-state collisions).
  - .github/workflows/g2-authenticated-acceptance.yml
      Dedicated required G2 CI job. Host-native PostgreSQL (no Docker).
      [CORRECTED] Generates ephemeral per-run DB password + G2 E2E password
      via openssl rand; masks via ::add-mask::; exports to GITHUB_ENV.
      Passes G2_E2E_PASSWORD into seed SQL via psql -v g2_e2e_password=...
      No committed password literals.
      [NEW] Includes "Verify Playwright project configs" step that asserts
      g2-authenticated.spec.ts count = 0 in generic matrix AND > 0 in
      dedicated G2 matrix, before running Playwright.

MODIFIED FILES (this CI-remediation commit):
  - apps/web/e2e/g2-authenticated.spec.ts
      Real stateful journey: Employee submits leave → Manager approves →
      HR approves → APPROVED using SAME leave request.
      [CORRECTED] Removed unused findLeaveRequestIdByReason helper that had
      .isVisible().catch(() => false) (swallowed error pattern forbidden
      by directive §6).
      [CORRECTED] Mobile test now performs REAL clock-in/out mutation +
      API response verification + persisted state verification (no
      if(isVisible)=>PASS fallback).
  - apps/web/playwright.standard.config.ts
      [CORRECTED] Added **/g2-authenticated.spec.ts to testIgnore array
      (was missing — only playwright.config.ts had it; generic CI uses
      playwright.standard.config.ts).
  - apps/web/app/hr/leave/approvals/page.tsx (preserved from prior push)
      Fixed broken approve/reject endpoints. Calls /manager-approve,
      /hr-approve, /manager-reject, /hr-reject per the controller.
  - apps/web/app/hr/leave/page.tsx (preserved from prior push)
      Fixed broken capability checks; added data-testid to request form.
  - apps/web/playwright.config.ts (preserved from prior push)
      testIgnore array includes g2-authenticated.spec.ts.
  - docs/hrm/g2/evidence/HRM-G2-ENGINEERING-CLOSURE.md
      [CORRECTED] Reworded line 156 prose from "G2 OpenAPI completeness,
      concurrency/idempotency proof" to "contract-schema coverage and
      concurrency/idempotency evidence" (avoids gitleaks generic-api-key
      false positive).
      [CORRECTED] Records e81aa13e as historical FAILED checkpoint.
  - docs/hrm/g2/evidence/G2-FINAL-REQUIREMENT-MATRIX.md
      [CORRECTED] Truthful classification: HrG2LeavePostgresIntegrationTest
      is STEP_BINDING_REGRESSION (not FULL_LIFECYCLE) because it mocks
      workflow services. Added row 34 (Full backend leave lifecycle =
      NOT_YET) and row 27 (Full OpenAPI contract = NOT_YET) to honestly
      track the unmet scope.
  - docs/hrm/g2/evidence/G2-FINAL-EVIDENCE-MANIFEST.md
      This file. Honest PENDING/NOT_RUN/NOT_PROVEN status, no pre-written PASS.
      Records e81aa13e terminal FAILURE status for G2 Authenticated Acceptance,
      Generic Playwright, Security Baseline.
```

## Known remaining gaps (honest — must NOT be closed by CI remediation alone)
1. Leave policies CRUD UI not fully implemented (table exists, read-only view, policy-driven entitlement rule evaluation not wired)
2. Attendance break actions UI not implemented (backend event model exists)
3. Manual correction UI not implemented (backend event model exists)
4. Full OpenAPI contract NOT YET proven — current contract test verifies only paths/methods, not full request/response schemas + 4xx error codes + Idempotency-Key + expectedVersion/If-Match
5. Full backend leave lifecycle NOT YET proven — HrG2LeavePostgresIntegrationTest is STEP_BINDING_REGRESSION only (mocks workflow services; manually simulates progression); a true service-integration suite that exercises real WorkflowApprovalService + WorkflowGraphExecutionService does not yet exist
6. Authenticated E2E tests + dedicated workflow created but NOT yet certified by fresh CI run on new SHA
7. PostgreSQL Direct step-binding regression (HrG2LeavePostgresIntegrationTest) NOT yet certified by fresh CI run on new SHA
8. Independent approval NOT requested

## Historical SHAs (do NOT reuse as certification)
```
e81aa13e = comprehensive remediation commit / FAILED exact-head checkpoint
  - G2 Authenticated Acceptance = FAILURE (seed schema mismatch)
  - Generic Playwright = FAILURE (G2 spec remained in standard matrix)
  - Security Baseline = FAILURE (gitleaks false positive)
560df3be = step-instance binding implementation (the canonical fix)
50111a35 = roadmap IN_PROGRESS marker / FAILED CI checkpoint
495c7d65 = G0 closure regression contract fix / FAILED CI checkpoint
```

## Fresh exact-head CI target
```
NEW_G2_CHECKPOINT_SHA = <NEW_SHA after this commit>
All required CI must target the new SHA. Old green runs cannot certify.
e81aa13e is permanently historical FAILED exact-head evidence — do NOT rerun as final.
```
