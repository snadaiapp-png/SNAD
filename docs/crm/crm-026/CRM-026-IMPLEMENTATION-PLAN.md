# CRM-026 IMPLEMENTATION PLAN

**Date:** 2026-07-31
**Ticket:** CRM-026 — Add CRM E2E test
**Status:** READY TO IMPLEMENT

---

## 1. Implementation Tasks

### Task 1: Create CRM Lifecycle E2E Test

**File:** `apps/web/e2e/crm-lifecycle.spec.ts`

**Test Flow:**
1. Login via `crm-auth-session.ts` helper
2. Navigate to `/crm`
3. Create a lead via API
4. Convert lead to customer via API
5. Open customer-360 view via UI
6. Create an opportunity via API
7. Move opportunity to Won status via API
8. Assert dashboard counts update via UI

**Implementation Details:**
```typescript
import { test, expect } from "@playwright/test";
import { loginThroughUi } from "./crm-auth-session";

const TENANT_A_EMAIL = process.env.CRM_TENANT_A_EMAIL ?? "";
const TENANT_A_PASSWORD = process.env.CRM_TENANT_A_PASSWORD ?? "";

test.describe("CRM Lifecycle E2E", () => {
  test.describe.configure({ mode: "serial" });

  let accessToken: string;

  test.beforeEach(async ({ page }) => {
    const login = await loginThroughUi(page, TENANT_A_EMAIL, TENANT_A_PASSWORD);
    accessToken = login.accessToken;
  });

  test("complete CRM lifecycle: lead → customer → opportunity → won", async ({ page }) => {
    // 1. Create lead via API
    const leadResponse = await page.request.post("/api/v1/crm/leads", {
      data: {
        displayName: "E2E Test Lead",
        companyName: "E2E Corp",
        email: "e2e@test.com",
        source: "e2e-test",
      },
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(leadResponse.ok()).toBe(true);
    const lead = await leadResponse.json();

    // 2. Convert lead to customer via API
    const convertResponse = await page.request.post(
      `/api/v1/crm/leads/${lead.id}/convert`,
      {
        data: {
          createOpportunity: true,
          currencyCode: "USD",
          opportunityName: "E2E Test Opportunity",
          amount: 10000,
        },
        headers: { Authorization: `Bearer ${accessToken}` },
      },
    );
    expect(convertResponse.ok()).toBe(true);
    const conversion = await convertResponse.json();

    // 3. Open customer-360 view via UI
    await page.goto("/crm");
    await page.waitForSelector("#crm-operational-content", { timeout: 30000 });
    // Navigate to customer-360
    // ... (UI interaction)

    // 4. Assert dashboard counts update
    await page.goto("/crm/overview");
    await page.waitForSelector("#crm-operational-content", { timeout: 30000 });
    // Verify counts updated
    // ... (UI assertion)
  });
});
```

---

## 2. Task Order

| # | Task | Depends On | Estimated Time |
|---|------|-----------|----------------|
| 1 | Create `crm-lifecycle.spec.ts` | None | 30 min |
| 2 | Test locally with `npx playwright test crm-lifecycle.spec.ts` | Task 1 | 15 min |
| 3 | Push to feature branch | Task 2 | 5 min |
| 4 | Verify Playwright CI passes | Task 3 | 10 min |
| 5 | Merge to main | Task 4 | 5 min |

**Total Estimated Time:** 65 min

---

## 3. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| API endpoints may not support test data creation | Low | High | Use existing API patterns from `crm-authenticated-acceptance.spec.ts` |
| Dashboard counts may not update in real-time | Medium | Medium | Add appropriate waits and retries |
| Multi-tenancy may require specific test setup | Low | Medium | Use existing tenant isolation patterns |
| Playwright CI may fail due to pre-existing issues | Medium | Low | Document and proceed if failures are pre-existing |

---

## 4. Validation Strategy

### 4.1 Local Validation

```bash
cd apps/web
npx playwright test crm-lifecycle.spec.ts --config=playwright.standard.config.ts
```

### 4.2 CI Validation

1. Push to feature branch `feature/crm-026-e2e-lifecycle`
2. Verify Playwright CI workflow passes
3. Check test results in CI artifacts

### 4.3 Production Validation

1. Merge to main
2. Verify Playwright CI passes on main
3. Verify test runs in production CI pipeline

---

## 5. Success Criteria

| # | Criterion | Validation |
|---|-----------|------------|
| 1 | `apps/web/e2e/crm-lifecycle.spec.ts` exists | File creation |
| 2 | Spec logs in, navigates to `/crm` | Test execution |
| 3 | Creates a lead | API call succeeds |
| 4 | Converts lead | API call succeeds |
| 5 | Opens customer-360 view | UI navigation |
| 6 | Creates an opportunity | API call succeeds |
| 7 | Moves opportunity to Won | API call succeeds |
| 8 | Asserts dashboard counts update | UI assertion |
| 9 | Wired into `playwright-ci.yml` | CI configuration |

---

## 6. Authorization

✅ **CRM-026 IMPLEMENTATION PLAN APPROVED**

All tasks defined. Ready to proceed with implementation.
