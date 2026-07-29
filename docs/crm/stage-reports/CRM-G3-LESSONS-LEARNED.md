# CRM-G3 Lessons Learned

**Date:** 2026-07-29
**Milestone Group:** CRM-G3

---

## 1. What Went Well

### 1.1 Parallel Execution

CRM-015 and CRM-016 were executed in parallel, reducing total delivery time. Both tabs followed the same patterns and could be developed simultaneously without conflicts.

### 1.2 Consistent Patterns

All four G3 tabs followed the same architectural pattern:
- `useCrmI18n()` for translations
- `crmApi` for API calls
- `useState`/`useCallback`/`useEffect` for state management
- Loading, error, empty states
- Modal forms for create operations

This consistency made it easy to verify and review each implementation.

### 1.3 Reusable Components

The `Section<T>` generic component in customer-360-view.tsx eliminated duplication across 4 sections (contacts, opportunities, activities, timeline). This pattern could be reused in future views.

### 1.4 Single API Endpoint for Customer-360

The backend's `customer360` endpoint returns all data in a single response, avoiding N+1 queries and simplifying the frontend implementation.

---

## 2. What Could Be Improved

### 2.1 Unit Tests

No unit tests were added for the new components. This follows the existing pattern (overview, execution board have no unit tests), but adding component tests would improve confidence.

**Recommendation:** Add React Testing Library tests for G4+ components.

### 2.2 URL-Based Navigation

The customer-360 view uses local state for navigation (`selectedAccountId`), which means:
- No deep linking possible
- Browser back button doesn't work
- No URL sharing

**Recommendation:** Consider adding URL-based routing for customer-360 in G4.

### 2.3 Inline Editing

The customer-360 view is read-only. Editing account details, adding notes, or creating activities requires navigating to separate forms.

**Recommendation:** Consider adding inline editing in G4+.

---

## 3. Technical Insights

### 3.1 API Client Pattern

The `crmApi` object provides a clean, typed interface for all CRM operations. Each method returns a typed response, making it easy to verify correctness at compile time.

### 3.2 i18n Pattern

The `useCrmI18n()` hook provides a simple `t(key)` function for translations. The flat key structure (`customers.column.name`) is easy to maintain and search.

### 3.3 CSS Pattern

All components reuse the same CSS classes from `crm-command-center.module.css`. This ensures visual consistency and reduces CSS duplication.

---

## 4. Recommendations for G4

| # | Recommendation | Priority | Impact |
|---|----------------|----------|--------|
| 1 | Add component tests for new views | Medium | Improved confidence |
| 2 | Consider URL-based routing for detail views | Medium | Better UX |
| 3 | Add inline editing for customer-360 | Low | Improved productivity |
| 4 | Consider virtual scrolling for large lists | Low | Performance |

---

## 5. Metrics

| Metric | Value |
|--------|-------|
| Total work items | 4 |
| Total lines of code | ~1,140 |
| Total i18n keys | 120+ |
| Total documentation files | 19 |
| TypeScript errors | 0 |
| Regressions | 0 |

---

**Lessons Learned Authority:** CRM-017 Implementation & G3 Closure Agent
**Date:** 2026-07-29
