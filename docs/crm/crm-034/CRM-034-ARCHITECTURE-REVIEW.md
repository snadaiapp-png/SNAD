# CRM-034 Architecture Review

| Field | Value |
|-------|-------|
| Ticket | CRM-034 — Accessibility audit for CRM Command Center |
| Date | 2026-08-02 |
| Owner | Frontend squad |
| Status | NOT_STARTED |

---

## 1. Scope

CRM-034 introduces an automated accessibility audit using axe-core integrated
into the Playwright CI pipeline. The audit targets the `/crm` route (CRM
Command Center) and requires zero Critical or Serious violations.

---

## 2. Target Components

| Component | File | Impact |
|-----------|------|--------|
| CRM Command Center | `apps/web/app/crm/crm-command-center.tsx` | Primary audit target |
| Pipeline Tab | `apps/web/app/crm/components/pipeline-tab.tsx` | Accessibility audit scope |
| Customer-360 View | `apps/web/app/crm/crm-customer-360.tsx` | Accessibility audit scope |
| Opportunities Tab | `apps/web/app/crm/components/opportunities-tab.tsx` | Accessibility audit scope |
| Accounts Tab | `apps/web/app/crm/components/accounts-tab.tsx` | Accessibility audit scope |

---

## 3. Architecture Impact

- **No backend changes**: Accessibility audit is frontend-only
- **No database changes**: Audit runs against existing UI
- **No API changes**: Audit validates DOM structure and ARIA attributes
- **CI/CD integration**: Adds axe-core to `playwright-ci.yml`

---

## 4. Dependencies

| Dependency | Status | Evidence |
|------------|--------|----------|
| CRM-017 (customer-360 view) | DONE | Roadmap: 2026-07-29 |
| CRM-020 (pipeline Kanban) | DONE | Roadmap: 2026-07-29 |
| Playwright CI workflow | EXISTS | `.github/workflows/playwright-ci.yml` |

---

## 5. Implementation Approach

1. Add `@axe-core/playwright` dependency to `apps/web`
2. Create accessibility spec: `apps/web/e2e/crm-accessibility.spec.ts`
3. Integrate axe-core assertions into Playwright test
4. Commit audit evidence to `evidence/crm-axe-audit.json`
5. Update `playwright-ci.yml` to run accessibility tests

---

## 6. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| axe-core reports violations | Medium | High | Fix violations before merge |
| Playwright CI integration fails | Low | Medium | Test locally first |
| Performance impact on CI | Low | Low | axe-core is lightweight |

---

## 7. Conclusion

CRM-034 is a low-risk, frontend-only change that adds accessibility compliance
to the CRM Command Center. No architectural changes are required.
