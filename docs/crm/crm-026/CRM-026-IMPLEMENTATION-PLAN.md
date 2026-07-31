# CRM-026 IMPLEMENTATION PLAN

**Date:** 2026-07-31
**Ticket:** CRM-026 — Add CRM E2E test
**Status:** IN PROGRESS

---

## Architecture Review Summary

### Playwright Infrastructure

| Component | Status | Location |
|-----------|--------|----------|
| Playwright config | ✅ Ready | `apps/web/playwright.standard.config.ts` |
| CI workflow | ✅ Ready | `.github/workflows/playwright-ci.yml` |
| Test directory | ✅ Ready | `apps/web/e2e/` |
| Auth helper | ✅ Ready | `apps/web/e2e/crm-auth-session.ts` |
| PR trigger | ✅ Configured | Paths: `apps/web/**` |

### Existing Test Patterns

| Pattern | Source | Usage |
|---------|--------|-------|
| `loginThroughUi()` | `crm-auth-session.ts` | Login and session management |
| `waitForCrmReady()` | `crm-authenticated-acceptance.spec.ts` | Wait for CRM shell to load |
| API calls via `page.request` | `crm-authenticated-acceptance.spec.ts` | Fast data setup |
| UI assertions via `expect()` | `crm-authenticated-acceptance.spec.ts` | Validate UI state |

### API Endpoints Available

| Entity | Create | Read | Update | Delete |
|--------|--------|------|--------|--------|
| Leads | ✅ | ✅ | ✅ | ✅ |
| Opportunities | ✅ | ✅ | ✅ | ✅ |
| Activities | ✅ | ✅ | ✅ | — |
| Pipelines | ✅ | ✅ | — | — |
| Dashboard | — | ✅ | — | — |

---

## Implementation Tasks

### Task 1: Create `crm-lifecycle.spec.ts`

**File:** `apps/web/e2e/crm-lifecycle.spec.ts`

**Test Flow:**
1. Login via `crm-auth-session.ts` helper
2. Navigate to `/crm`
3. Create a lead via API
4. Qualify lead via UI
5. Convert lead via UI
6. Create opportunity via UI
7. Move opportunity stage via UI
8. Create activity via UI
9. Complete activity via UI
10. Verify dashboard counts update

### Task 2: Validate Locally

```bash
cd apps/web
npx playwright test crm-lifecycle.spec.ts --config=playwright.standard.config.ts
```

### Task 3: Commit and Push

- Commit with message: `feat(crm-026): add CRM lifecycle E2E test`
- Push to feature branch

### Task 4: Verify CI

- Wait for Playwright CI to pass
- Verify test runs in CI pipeline

### Task 5: Merge to Main

- Merge feature branch into main
- Verify merge commit

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| API endpoints may not support test data creation | Low | High | Use existing API patterns from `crm-authenticated-acceptance.spec.ts` |
| Dashboard counts may not update in real-time | Medium | Medium | Add appropriate waits and retries |
| Multi-tenancy may require specific test setup | Low | Medium | Use existing tenant isolation patterns |

---

## Authorization

✅ **CRM-026 IMPLEMENTATION PLAN APPROVED**

All tasks defined. Ready to proceed with implementation.
