# CRM-026 ARCHITECTURE REVIEW

**Date:** 2026-07-31
**Ticket:** CRM-026 — Add CRM E2E test
**Status:** ✅ ARCHITECTURE READY

---

## 1. Current Implementation Review

### 1.1 Playwright Infrastructure

| Component | Status | Location |
|-----------|--------|----------|
| Playwright config | ✅ Ready | `apps/web/playwright.standard.config.ts` |
| CI workflow | ✅ Ready | `.github/workflows/playwright-ci.yml` |
| Test directory | ✅ Ready | `apps/web/e2e/` |
| Auth helper | ✅ Ready | `apps/web/e2e/crm-auth-session.ts` |
| PR trigger | ✅ Configured | Paths: `apps/web/**` |

### 1.2 Existing E2E Tests

| Test | Pattern | Reusable |
|------|---------|----------|
| `crm-authenticated-acceptance.spec.ts` | Full lifecycle via UI | ✅ Yes |
| `crm-route-smoke.spec.ts` | Route validation | ✅ Yes |
| `crm-rbac-acceptance.spec.ts` | RBAC testing | ✅ Yes |
| `crm-tenant-isolation.spec.ts` | Tenant isolation | ✅ Yes |

### 1.3 API Endpoints Available

| Entity | Create | Read | Update | Delete |
|--------|--------|------|--------|--------|
| Accounts | ✅ | ✅ | ✅ | ✅ |
| Contacts | ✅ | ✅ | ✅ | ✅ |
| Leads | ✅ | ✅ | ✅ | ✅ |
| Opportunities | ✅ | ✅ | ✅ | ✅ |
| Pipelines | ✅ | ✅ | — | — |
| Activities | ✅ | ✅ | ✅ | — |
| Dashboard | — | ✅ | — | — |

---

## 2. Reusable Components

### 2.1 Auth Helper

```typescript
import { loginThroughUi } from "./crm-auth-session";
const login = await loginThroughUi(page, email, password);
// Returns: { accessToken, user: { tenantId, ... } }
```

### 2.2 API Patterns

```typescript
// Create via API (faster than UI)
const lead = await page.request.post("/api/v1/crm/leads", {
  data: { displayName: "Test Lead", source: "e2e" },
  headers: { Authorization: `Bearer ${accessToken}` },
});

// Read via API
const accounts = await page.request.get("/api/v1/crm/accounts", {
  headers: { Authorization: `Bearer ${accessToken}` },
});
```

### 2.3 UI Patterns

```typescript
// Navigate to CRM
await page.goto("/crm");

// Wait for CRM ready
await page.waitForSelector("#crm-operational-content", { timeout: 30000 });

// Click tab
await page.locator('button:has-text("Leads")').click();

// Fill form
await page.locator('input[name="displayName"]').fill("Test Lead");

// Submit form
await page.locator('button[type="submit"]').click();
```

---

## 3. Multi-Tenant Compliance

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Tenant-scoped queries | ✅ | All API endpoints use tenant ID from auth token |
| Cross-tenant access blocked | ✅ | Backend validates tenant ID on all endpoints |
| Test data isolation | ✅ | Each test run uses unique tenant credentials |

---

## 4. RBAC Compliance

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Capability-based auth | ✅ | All endpoints require `CRM.*` capabilities |
| Test user permissions | ✅ | CRM_TENANT_A_EMAIL has full CRM capabilities |
| Unauthorized access blocked | ✅ | 403 returned for missing capabilities |

---

## 5. Implementation Gaps

| Gap | Impact | Mitigation |
|-----|--------|------------|
| No existing `crm-lifecycle.spec.ts` | None | Create new file |
| Dashboard counts may need wait | Low | Add `waitForLoadState("networkidle")` |
| Lead conversion may require account | Low | Create account first via API |

---

## 6. Architecture Decision

**Approach:** Hybrid API + UI testing

- **API calls** for data setup (faster, more reliable)
- **UI interactions** for user workflows (validates frontend)
- **API assertions** for data verification (faster than UI assertions)

**Rationale:**
- Existing tests use this pattern (`crm-authenticated-acceptance.spec.ts`)
- Faster execution than pure UI testing
- More reliable than pure UI testing
- Still validates critical UI flows

---

## 7. Authorization

✅ **CRM-026 ARCHITECTURE REVIEW PASSED**

All components ready. Implementation may proceed.
