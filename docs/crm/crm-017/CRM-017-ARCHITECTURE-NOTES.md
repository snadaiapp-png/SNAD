# CRM-017 Architecture Notes — Customer-360 View

**Date:** 2026-07-29
**Work Item:** EXEC-PROMPT-CRM-017

---

## 1. Design Decisions

### 1.1 Navigation Pattern

The customer-360 view is implemented as a **conditional render** within the `CustomersTab` component, controlled by a `selectedAccountId` state variable:

```typescript
// When selectedAccountId is null → show accounts list
// When selectedAccountId is set  → show Customer360View
if (selectedAccountId) {
  return <Customer360View accountId={selectedAccountId} onBack={...} />;
}
// ... render accounts list
```

**Rationale:**
- Keeps the navigation state local to the customers tab
- No URL-based routing needed (single-page CRM Command Center)
- Clean separation between list and detail views
- Back button simply resets `selectedAccountId` to `null`

### 1.2 Component Composition

```
CustomersTab (state: selectedAccountId)
├── [null] → Accounts list (search, filter, create, archive)
└── [string] → Customer360View (accountId, onBack)
    ├── Account Summary (KPI grid)
    ├── Section<Contact> → ContactRow
    ├── Section<Opportunity> → OpportunityRow
    ├── Section<Activity> → ActivityRow
    └── Section<TimelineEvent> → TimelineRow
```

### 1.3 Reusable Section Component

The `Section<T>` generic component handles:
- Empty state rendering (when items array is empty)
- Item count display in the section title
- Table rendering with `renderItem` callback

This pattern is used 4 times (contacts, opportunities, activities, timeline) with zero duplication.

### 1.4 Timeline Sorting

Timeline events are sorted in reverse-chronological order:

```typescript
const sortedTimeline = [...timeline].sort(
  (a, b) => new Date(b.occurred_at).getTime() - new Date(a.occurred_at).getTime(),
);
```

**Rationale:**
- Most recent events appear first
- Immutable sort (creates copy before sorting)
- Simple date comparison via `getTime()`

---

## 2. API Design

### 2.1 Single Endpoint

The customer-360 view uses a single API endpoint:

```
GET /api/v1/crm/accounts/:id/customer-360
```

This endpoint returns a denormalized response containing:
- The account itself
- All contacts associated with the account
- All opportunities for the account (with pipeline/stage names)
- All activities related to the account
- All timeline events for the account

**Rationale:**
- Single round-trip for all customer data
- Backend handles the joins and aggregation
- Frontend receives a complete, ready-to-render payload
- Reduces complexity in the frontend

### 2.2 Pipeline/Stage Name Resolution

Opportunities include resolved `pipeline_name` and `stage_name` fields:

```typescript
opportunities: Array<CrmOpportunity & { pipeline_name?: string; stage_name?: string }>
```

**Rationale:**
- Avoids N+1 queries for pipeline/stage names
- Backend resolves names at query time
- Frontend can display human-readable names directly

---

## 3. State Management

### 3.1 Local State

All state is local to the component:

| State | Type | Purpose |
|-------|------|---------|
| `data` | `Customer360 \| null` | The fetched customer-360 data |
| `loading` | `boolean` | Whether the fetch is in progress |
| `error` | `string \| null` | Error message if fetch fails |

### 3.2 No Global State

The customer-360 view does not use any global state management (Redux, Zustand, etc.). All data is fetched on mount and stored locally.

**Rationale:**
- Customer-360 is a detail view, not a shared resource
- No other components need to read this data
- Local state keeps the component self-contained
- Simpler testing and reasoning

---

## 4. Error Handling Strategy

| Layer | Strategy |
|-------|----------|
| API call | Try/catch with error message extraction |
| Loading state | Spinner + loading message |
| Error state | Error banner with dismiss |
| Empty state | Per-section empty messages |
| Null fields | Fallback to "—" display |

---

## 5. Accessibility

- All interactive elements use `<button>` with `type="button"`
- Status badges use semantic `<span>` elements
- Tables use proper `<thead>` and `<tbody>` structure
- KPI cards use semantic `<span>` for labels and values

---

## 6. Performance Considerations

- Single API call for all customer data
- No polling or real-time updates (manual refresh button)
- Lazy rendering of sections (React handles this)
- No pagination needed (customer data is bounded)

---

## 7. Future Enhancements

| Enhancement | Priority | Notes |
|-------------|----------|-------|
| URL-based routing (`/crm/customers/:id`) | Medium | Enable deep linking |
| Edit account inline | Low | Could add edit button |
| Add note/activity from customer-360 | Medium | Would require additional forms |
| Real-time timeline updates | Low | WebSocket integration |
| Custom field rendering | Medium | CRM-018+ |

---

**Architecture Authority:** CRM-017 Implementation & G3 Closure Agent
**Date:** 2026-07-29
