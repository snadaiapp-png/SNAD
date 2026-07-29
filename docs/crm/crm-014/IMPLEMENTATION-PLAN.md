# Implementation Plan — EXEC-PROMPT-CRM-014

**Date:** 2026-07-29
**Work Item:** EXEC-PROMPT-CRM-014 — Wire leads tab to the API client
**Group:** CRM-G3 (Core CRM entities end-to-end)
**Repository:** snadaiapp-png/SNAD
**Agent:** Portfolio Execution Agent

---

## 1. Objective

Replace the `CrmEmptyState` placeholder in the CRM Command Center's `leads` tab with a fully functional leads management interface wired to the backend API.

---

## 2. Architecture Context

### 2.1 Current State

The CRM Command Center (`apps/web/app/crm/crm-command-center.tsx`) renders 16 tabs. Most tabs currently render `CrmEmptyState`. The `leads` tab must be the first to receive real backend integration.

### 2.2 Backend API (Already Implemented)

The leads API is fully implemented in the backend:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/crm/leads` | GET | List leads with filters |
| `/api/crm/leads` | POST | Create new lead |
| `/api/crm/leads/{id}` | GET | Get lead details |
| `/api/crm/leads/{id}/status` | PATCH | Change lead status |
| `/api/crm/leads/{id}/convert` | POST | Convert lead to account/contact/opportunity |

### 2.3 API Client (Already Exists)

`apps/web/lib/api/crm.ts` already defines:
- `CrmLead` interface
- `crmApi.leads()` method
- Lead status types

### 2.4 Frontend Architecture

```
apps/web/app/crm/
├── crm-command-center.tsx          # Main shell (16 tabs)
├── crm-empty-state.tsx             # Placeholder component
├── crm-i18n.tsx                    # i18n provider
├── components/                     # Shared components
└── command-center/                 # Tab-specific components (TO CREATE)
    ├── leads-tab.tsx               # Leads list
    ├── leads-filters.tsx           # Status filter
    ├── leads-create-form.tsx       # Create lead form
    └── leads-convert-dialog.tsx    # Convert dialog
```

---

## 3. Implementation Steps

### Step 1: Verify API Client (30 min)

**Goal:** Confirm `crmApi` has all required lead methods.

| Check | Expected | Action if missing |
|-------|----------|-------------------|
| `crmApi.leads(filters)` | EXISTS | Add method |
| `crmApi.createLead(data)` | EXISTS | Add method |
| `crmApi.changeLeadStatus(id, status)` | EXISTS | Add method |
| `crmApi.convertLead(id)` | EXISTS | Add method |
| `CrmLead` interface | EXISTS | Add interface |
| Lead status enum | EXISTS | Add enum |

### Step 2: Create LeadsTab Component (2 hours)

**File:** `apps/web/app/crm/command-center/leads-tab.tsx`

```tsx
// Component structure:
// - Fetches leads on mount and when filters change
// - Renders table with columns: Name, Company, Email, Status, Created, Actions
// - Shows loading spinner during fetch
// - Shows empty state if no leads
// - Shows error state on API failure
```

**Key decisions:**
- Use `useEffect` + `useState` for data fetching (or SWR/React Query if available)
- Debounce search input (300ms)
- Paginate results (20 per page)
- Status displayed as colored badges

### Step 3: Create Status Filter (1 hour)

**File:** `apps/web/app/crm/command-center/leads-filters.tsx`

```tsx
// Filter options:
// - All (default)
// - NEW
// - ASSIGNED
// - CONTACTED
// - QUALIFIED
// - DISQUALIFIED
// - ARCHIVED
```

**Key decisions:**
- Dropdown or chip-based filter
- Arabic/English labels via i18n
- Filter state managed by parent LeadsTab

### Step 4: Create Create Lead Form (2 hours)

**File:** `apps/web/app/crm/command-center/leads-create-form.tsx`

```tsx
// Fields:
// - display_name (required)
// - company_name (optional)
// - email (optional, validated)
// - phone (optional)
// - source (optional: WEB, REFERRAL, IMPORT, MANUAL)
// - notes (optional)
```

**Key decisions:**
- Modal dialog or inline form
- Client-side validation before API call
- Success: refresh list, close form
- Error: show inline error message

### Step 5: Create Convert Dialog (2 hours)

**File:** `apps/web/app/crm/command-center/leads-convert-dialog.tsx`

```tsx
// Shows:
// - Lead summary (name, company, email)
// - Conversion options:
//   - Create account (from company_name)
//   - Create contact (from lead data)
//   - Create opportunity (optional)
// - Confirm/Cancel buttons
```

**Key decisions:**
- Pre-fill account name from `company_name`
- Pre-fill contact from lead fields
- Show resulting entity links after conversion
- Handle already-converted leads gracefully

### Step 6: Wire Tab into Command Center (30 min)

**File:** `apps/web/app/crm/crm-command-center.tsx`

```tsx
// Replace:
//   case 'leads': return <CrmEmptyState ... />;
// With:
//   case 'leads': return <LeadsTab />;
```

### Step 7: Add i18n Translations (30 min)

**File:** `apps/web/app/crm/crm-i18n.tsx`

Add Arabic and English translations for:
- Leads tab title
- Column headers
- Filter labels
- Form labels
- Error messages
- Success messages
- Empty state messages

### Step 8: Write Tests (2 hours)

| Test File | Coverage |
|-----------|----------|
| `leads-tab.test.tsx` | List rendering, loading, error, empty states |
| `leads-filters.test.tsx` | Filter application, reset |
| `leads-create-form.test.tsx` | Validation, submission, error handling |
| `leads-convert-dialog.test.tsx` | Conversion flow, entity linking |

### Step 9: Integration Testing (1 hour)

- Run `mvn test` — all 134 existing tests pass
- Run `tsc --noEmit` — no TypeScript errors
- Run Playwright E2E — leads tab functional

### Step 10: Commit and Push (30 min)

```
feat(crm-014): wire leads tab to API client

- Add LeadsTab component with real backend integration
- Add status filter with 6 lead statuses
- Add create lead form with validation
- Add lead convert dialog with account/contact/opportunity creation
- Wire leads tab into Command Center
- Add Arabic/English i18n translations
- Add unit tests for all new components
```

---

## 4. Task Breakdown

| # | Task | Estimate | Dependencies | Status |
|---|------|----------|--------------|--------|
| 1 | Verify API client methods | 30 min | None | PENDING |
| 2 | Create LeadsTab component | 2h | Task 1 | PENDING |
| 3 | Create status filter | 1h | Task 2 | PENDING |
| 4 | Create create lead form | 2h | Task 1 | PENDING |
| 5 | Create convert dialog | 2h | Task 1 | PENDING |
| 6 | Wire tab into Command Center | 30 min | Tasks 2-5 | PENDING |
| 7 | Add i18n translations | 30 min | Task 6 | PENDING |
| 8 | Write unit tests | 2h | Tasks 2-5 | PENDING |
| 9 | Integration testing | 1h | Task 8 | PENDING |
| 10 | Commit and push | 30 min | Task 9 | PENDING |
| **Total** | | **~12 hours** | | |

---

## 5. Commit Strategy

| Commit | Message | Contents |
|--------|---------|----------|
| 1 | `feat(crm-014): add leads tab component and status filter` | Tasks 2-3 |
| 2 | `feat(crm-014): add create lead form and convert dialog` | Tasks 4-5 |
| 3 | `feat(crm-014): wire leads tab into Command Center` | Tasks 6-7 |
| 4 | `test(crm-014): add unit tests for leads tab components` | Task 8 |
| 5 | `fix(crm-014): resolve integration test issues` | Task 9 (if needed) |

---

## 6. Rollback Plan

If implementation fails or introduces regressions:

1. Revert the feature branch to the starting point
2. All changes are isolated on the feature branch
3. No impact on main until PR is merged
4. Existing CRM tests serve as regression gate

---

## 7. Success Criteria

| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | Leads tab shows real data from API | Visual check |
| 2 | Status filter works correctly | Unit test |
| 3 | Create lead form works | Unit test |
| 4 | Lead conversion works | Unit test |
| 5 | All existing tests pass | `mvn test` |
| 6 | No TypeScript errors | `tsc --noEmit` |
| 7 | No regressions | CI checks pass |

---

**Plan Authority:** Portfolio Execution Agent
**Date:** 2026-07-29
**Estimated completion:** 1.5 sprints (3 weeks)
