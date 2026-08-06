# Execution Checklist — EXEC-PROMPT-CRM-014

**Date:** 2026-07-29
**Work Item:** EXEC-PROMPT-CRM-014 — Wire leads tab to the API client
**Repository:** snadaiapp-png/SNAD
**Agent:** Portfolio Execution Agent

---

## Pre-Implementation Checklist

- [ ] CRM-010 merged and governance ratified
- [ ] All 134 existing tests pass on main
- [ ] Build compiles cleanly on main
- [ ] Feature branch created from main
- [ ] API client methods verified (`crmApi.leads()`, `crmApi.createLead()`, etc.)
- [ ] Lead status enum verified (NEW, ASSIGNED, CONTACTED, QUALIFIED, DISQUALIFIED, ARCHIVED)
- [ ] i18n provider available (`useCrmI18n`)
- [ ] Command Center shell available

---

## Implementation Checklist

### Phase 1: Leads List Component

- [ ] Create `apps/web/app/crm/command-center/leads-tab.tsx`
- [ ] Implement data fetching with `useEffect` + `useState`
- [ ] Render table with columns: Name, Company, Email, Status, Created, Actions
- [ ] Handle loading state (spinner)
- [ ] Handle empty state (message)
- [ ] Handle error state (error message)
- [ ] Implement pagination (20 per page)
- [ ] Add Arabic/English labels via i18n

### Phase 2: Status Filter

- [ ] Create `apps/web/app/crm/command-center/leads-filters.tsx`
- [ ] Implement dropdown/chip filter with 6 statuses
- [ ] Wire filter state to parent LeadsTab
- [ ] Debounce filter changes (300ms)
- [ ] Add Arabic/English filter labels

### Phase 3: Create Lead Form

- [ ] Create `apps/web/app/crm/command-center/leads-create-form.tsx`
- [ ] Implement form fields: display_name, company_name, email, phone, source, notes
- [ ] Add client-side validation
- [ ] Wire form submission to `crmApi.createLead()`
- [ ] Handle success (refresh list, close form)
- [ ] Handle error (show inline message)
- [ ] Add Arabic/English form labels

### Phase 4: Convert Dialog

- [ ] Create `apps/web/app/crm/command-center/leads-convert-dialog.tsx`
- [ ] Show lead summary (name, company, email)
- [ ] Implement conversion options (account, contact, opportunity)
- [ ] Wire conversion to `crmApi.convertLead()`
- [ ] Show resulting entity links after conversion
- [ ] Handle already-converted leads
- [ ] Add Arabic/English dialog labels

### Phase 5: Wire into Command Center

- [ ] Import LeadsTab in `crm-command-center.tsx`
- [ ] Replace `CrmEmptyState` for leads tab with `LeadsTab`
- [ ] Verify tab renders correctly

### Phase 6: i18n Translations

- [ ] Add Arabic translations for leads tab
- [ ] Add English translations for leads tab
- [ ] Verify language toggle works

### Phase 7: Testing

- [ ] Write `leads-tab.test.tsx` (list, loading, error, empty)
- [ ] Write `leads-filters.test.tsx` (filter, reset)
- [ ] Write `leads-create-form.test.tsx` (validation, submit)
- [ ] Write `leads-convert-dialog.test.tsx` (conversion flow)
- [ ] Run `mvn test` — all 134+ tests pass
- [ ] Run `tsc --noEmit` — no TypeScript errors
- [ ] Run Playwright E2E — leads tab functional

### Phase 8: Commit and Push

- [ ] Commit 1: `feat(crm-014): add leads tab component and status filter`
- [ ] Commit 2: `feat(crm-014): add create lead form and convert dialog`
- [ ] Commit 3: `feat(crm-014): wire leads tab into Command Center`
- [ ] Commit 4: `test(crm-014): add unit tests for leads tab components`
- [ ] Push to feature branch
- [ ] Create PR targeting main

---

## Post-Implementation Checklist

- [ ] All CI checks pass (25/25)
- [ ] All tests pass (134+)
- [ ] No TypeScript errors
- [ ] No regressions in existing CRM functionality
- [ ] PR reviewed and approved
- [ ] PR merged to main

---

## Acceptance Verification

| # | Criterion | Verified | Evidence |
|---|-----------|----------|----------|
| A1 | Leads tab renders list of leads from API | [ ] | |
| A2 | Status filter correctly filters leads | [ ] | |
| A3 | Create lead form submits and refreshes list | [ ] | |
| A4 | Status change updates lead status | [ ] | |
| A5 | Convert action shows account/contact/opportunity links | [ ] | |
| A6 | Tab does not render CrmEmptyState | [ ] | |
| A7 | All existing tests continue to pass | [ ] | |
| A8 | Build compiles cleanly | [ ] | |
| A9 | No TypeScript errors | [ ] | |

---

**Checklist Authority:** Portfolio Execution Agent
**Date:** 2026-07-29
