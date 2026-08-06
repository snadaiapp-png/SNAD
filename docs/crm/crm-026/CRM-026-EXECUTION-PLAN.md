# CRM-026 EXECUTION PLAN

> **Document type:** Authorization gate and implementation plan
> **Created:** 2026-07-31
> **Status:** AUTHORIZED TO START

---

## 1. Executive Summary

CRM-026 (Add CRM E2E test) has been verified against all governance
prerequisites. All dependencies are satisfied, Playwright infrastructure exists,
and the frontend architecture is ready. Authorization is granted.

---

## 2. Acceptance Criteria

| # | Criterion | Current Status |
|---|-----------|----------------|
| 1 | `apps/web/e2e/crm-lifecycle.spec.ts` exists | ❌ NOT IMPLEMENTED |
| 2 | Spec logs in, navigates to `/crm`, creates a lead, converts it, opens customer-360, creates an opportunity, moves it to Won, asserts dashboard counts update | ❌ NOT IMPLEMENTED |
| 3 | Spec is wired into `playwright-ci.yml` and runs on every PR touching `apps/web/app/crm/**` | ✅ Already configured |

---

## 3. Prerequisites Verification

### 3.1 Dependency Graph

| Dependency | Status | Evidence |
|------------|--------|----------|
| EXEC-PROMPT-CRM-017 (Wire customer-360 view) | ✅ DONE | Roadmap: "Status: DONE" |
| EXEC-PROMPT-CRM-019 (Wire opportunities) | ✅ DONE | Roadmap: "Status: DONE" |
| EXEC-PROMPT-CRM-021 (Wire tasks tab) | ✅ DONE | Roadmap: "Status: DONE" |
| CRM-G3 (Core entities) | ✅ DONE | Roadmap: "Status: DONE" |
| CRM-G4 (Opportunities, pipeline) | ✅ DONE | Roadmap: "Status: DONE" |
| CRM-G5 (Tasks, transfers, employees) | ✅ DONE | Roadmap: "Status: DONE" |

### 3.2 Playwright Infrastructure

| Component | Status | Evidence |
|-----------|--------|----------|
| Playwright config | ✅ EXISTS | `apps/web/playwright.standard.config.ts` |
| CI workflow | ✅ EXISTS | `.github/workflows/playwright-ci.yml` |
| Test directory | ✅ EXISTS | `apps/web/e2e/` with 12 spec files |
| Auth helper | ✅ EXISTS | `apps/web/e2e/crm-auth-session.ts` |
| PR trigger | ✅ CONFIGURED | Paths: `apps/web/**` |

### 3.3 Existing E2E Tests

| Test File | Purpose |
|-----------|---------|
| `crm-authenticated-acceptance.spec.ts` | Full authenticated CRM acceptance |
| `crm-route-smoke.spec.ts` | Route smoke tests |
| `crm-rbac-acceptance.spec.ts` | RBAC acceptance tests |
| `crm-tenant-isolation.spec.ts` | Tenant isolation tests |
| `crm-operational.spec.ts` | Operational tests |
| `crm-accessibility.spec.ts` | Accessibility tests |
| `crm-integration-workspace.spec.ts` | Integration workspace tests |
| `visual-regression.spec.ts` | Visual regression tests |

---

## 4. Implementation Scope

### 4.1 Files to Create

| File | Purpose |
|------|---------|
| `apps/web/e2e/crm-lifecycle.spec.ts` | CRM lifecycle E2E test |

### 4.2 Test Flow

```
1. Login via crm-auth-session helper
2. Navigate to /crm
3. Create a lead (via leads tab or API)
4. Convert lead to customer
5. Open customer-360 view
6. Create an opportunity
7. Move opportunity to Won status
8. Assert dashboard counts update
```

### 4.3 Reusable Components

| Component | Source | Usage |
|-----------|--------|-------|
| `crm-auth-session.ts` | Existing | Login and session management |
| `test.describe()` | Playwright | Test grouping |
| `page.locator()` | Playwright | Element selection |
| `expect()` | Playwright | Assertions |

---

## 5. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| API endpoints may not support test data creation | Medium | High | Use existing API patterns from crm-authenticated-acceptance.spec.ts |
| Dashboard counts may not update in real-time | Low | Medium | Add appropriate waits and retries |
| Multi-tenancy may require specific test setup | Low | Medium | Use existing tenant isolation patterns |

---

## 6. Validation Strategy

1. **Local validation:** Run `npx playwright test crm-lifecycle.spec.ts` locally
2. **CI validation:** Push to feature branch, verify Playwright CI passes
3. **Production validation:** Verify test runs in production CI pipeline

---

## 7. Authorization

✅ **CRM-026 AUTHORIZED TO IMPLEMENT**

All prerequisites satisfied. Playwright infrastructure ready. Implementation may proceed.
