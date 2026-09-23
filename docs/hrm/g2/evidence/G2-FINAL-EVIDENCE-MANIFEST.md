# G2 Final Evidence Manifest

## Status
```
G2_FINAL_GATE = NOT_CLOSED
IMPLEMENTATION_COMPLETE = NO
G2_FINAL_EXACT_HEAD_SHA = NOT_ASSIGNED (set after final push; current local HEAD pre-commit = d0835ae1)
REMOTE_EXACT_HEAD_CI = PENDING
AUTHENTICATED_G2_E2E = NOT_PROVEN (tests written + dedicated workflow + G2 acceptance seed SQL created; CI run PENDING)
BACKEND_LIFECYCLE_INTEGRATION = NOT_PROVEN (HrG2LeavePostgresIntegrationTest written; CI run PENDING)
INDEPENDENT_APPROVAL = NOT_REQUESTED
MERGE_AUTHORIZATION = NO
POST_MERGE_A_F = NOT_RUN
```

## Local Tests (frontend)
```
LOCAL_FRONTEND_TESTS = PASS
  - typecheck = PASS
  - lint = 0 errors (65 pre-existing warnings — none added by this remediation)
  - SDS (brand:check) = PASS — "SNAD identity validation passed"
  - i18n parity = PASS — 1125 = 1125 keys
  - vitest (full) = PASS — 1034/1034 tests across 115 files
  - Next.js build = PASS — "✓ Compiled successfully in 3.3s"
  - perf budget = PASS — all routes within budget
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

## Push artifacts (this remediation)
```
NEW FILES:
  - apps/sanad-platform/src/test/resources/sql/g2-acceptance-seed.sql
      G2 acceptance tenant + plan + subscription + WORKFLOW module entitlement
      + 3 users (Employee/Manager/HR) + 3 hr_employees (Employee→Manager)
      + 3 RBAC roles + role_capabilities + user_role_assignments + leave balance.
  - apps/web/e2e/g2-auth-session.ts
      Login helper (mirrors crm-auth-session.ts pattern). FAIL-CLOSED when
      E2E_<ROLE>_EMAIL/PASSWORD env vars missing.
  - apps/web/playwright-g2.config.ts
      Single-project Playwright config for G2 stateful journey (no locale/theme
      matrix — would cause mutable-state collisions).
  - .github/workflows/g2-authenticated-acceptance.yml
      Dedicated required G2 CI job. Host-native PostgreSQL (no Docker/
      Testcontainers). Provisions Employee/Manager/HR + WORKFLOW entitlement
      via g2-acceptance-seed.sql. Builds + starts Spring Boot + Next.js.
      Sets E2E_<ROLE>_EMAIL/PASSWORD env vars. Runs Playwright via
      playwright-g2.config.ts. Uploads artifacts.

MODIFIED FILES:
  - apps/web/e2e/g2-authenticated.spec.ts
      Real stateful journey: Employee submits leave → Manager approves →
      HR approves → APPROVED using SAME leave request. Uses correct
      login selectors (#login-email, #login-password, form button[type="submit"]).
      data-testid selectors for deterministic interaction. No test.skip,
      no Assumptions, no @Disabled, no continue-on-error.
  - apps/web/app/hr/leave/approvals/page.tsx
      Fix broken approve/reject endpoints. Now calls /manager-approve,
      /hr-approve, /manager-reject, /hr-reject per the controller. Manager
      sees PENDING_MANAGER + "Manager Approve" button. HR sees PENDING_HR +
      "HR Approve" button. Manager does NOT see HR-only approval button
      (directive §6: "Manager cannot perform HR-only final approval").
  - apps/web/app/hr/leave/page.tsx
      Fix broken capability checks (HRM.LEAVE.VIEW/REQUEST/APPROVE didn't
      exist in the catalog). Now uses HRM.LEAVE.SELF_VIEW/SELF_REQUEST/
      TEAM_APPROVE/HR_APPROVE. Added data-testid to request form fields
      for deterministic Playwright selectors.
  - apps/web/playwright.config.ts
      Added **/g2-authenticated.spec.ts to testIgnore array so the generic
      visual regression matrix no longer runs the G2 spec (which would
      fail without secrets AND cause mutable-state collisions across
      6 locale/theme projects).
  - docs/hrm/g2/evidence/G2-FINAL-REQUIREMENT-MATRIX.md
      Updated with current state — IMPLEMENTED for implemented items,
      PENDING for CI evidence. Added rows for G2 authenticated E2E,
      step-instance binding, PostgreSQL Direct lifecycle, tenant isolation.
  - docs/hrm/g2/evidence/G2-FINAL-EVIDENCE-MANIFEST.md
      This file. Honest PENDING/NOT_RUN status, no pre-written PASS.
  - docs/hrm/g2/evidence/HRM-G2-ENGINEERING-CLOSURE.md
      Updated closure certificate with proven PLAYWRIGHT_ROOT_CAUSE and
      the CORRECTION description.
```

## Known remaining gaps (honest)
1. Leave policies CRUD UI not fully implemented (table exists, read-only view, policy-driven entitlement rule evaluation not wired)
2. Attendance break actions UI not implemented (backend event model exists)
3. Manual correction UI not implemented (backend event model exists)
4. OpenAPI auto-generated from annotations — contract test verifies paths/methods but full request/response schema completeness not asserted
5. Authenticated E2E tests + dedicated workflow created but NOT yet certified by fresh CI run
6. PostgreSQL Direct backend lifecycle tests (HrG2LeavePostgresIntegrationTest) NOT yet certified by fresh CI run
7. Independent approval NOT requested

## Historical SHAs (do NOT reuse as certification)
```
560df3be = step-instance binding implementation (the canonical fix)
50111a35 = roadmap IN_PROGRESS marker / FAILED CI checkpoint
495c7d65 = G0 closure regression contract fix / FAILED CI checkpoint (Playwright failure)
```

## Fresh exact-head CI target
```
G2_FINAL_CANDIDATE_SHA = <NEW_SHA after this commit>
All required CI must target the new SHA. Old green runs cannot certify.
```
