# UI / Database Consistency Report — Lead Status Display

**Date:** 2026-08-02
**Lead ID:** `e013b6d9-4eb0-4d74-88a3-beae51e05af4`
**Symptom:** UI shows "DISQUALIFIED" while backend forensic says ARCHIVED or CONVERTED

---

## Executive Summary

**No caching, optimistic UI, or stale state is involved.** The data flow from database to UI is a direct passthrough with no transformations. The inconsistency is caused by **DISQUALIFIED being treated as terminal in some UI surfaces but not others**, creating a situation where the command center allows status changes that the backend rejects with 409.

The real issue is a **three-way inconsistency in terminal status definitions** across the frontend:

| UI Surface | DISQUALIFIED is terminal? | Can user change status? |
|---|---|---|
| `leads-tab.tsx` (command center) | **NO** | **YES** (dropdown shown) |
| `leads/page.tsx` (operational list) | **YES** | NO (buttons hidden) |
| `leads/[leadId]/page.tsx` (lead detail) | **YES** | NO (buttons hidden) |

---

## Data Flow Trace

### Complete Path: Database → API → React → UI

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  crm_leads   │────►│ GET /leads   │────►│ React State  │────►│ UI Render    │
│  (PostgreSQL)│     │ (JSON)       │     │ (useState)   │     │ (<span>)     │
│  status: X   │     │ status: "X"  │     │ lead.status  │     │ lead.status  │
└─────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
      │                    │                    │                    │
      │              No transformation    No transformation   i18n lookup only
      │              No caching           No cache layer      (status → label)
      │              No mapping           No optimistic UI
      ▼
  Single source of truth
```

**Key finding: The `status` field passes through unchanged at every layer.** There is no transformation, mapping, caching, or optimistic update that could cause the displayed value to differ from the database value.

### Layer-by-Layer Evidence

#### Layer 1: Database → API Response

**`LeadRepository.java:15-18`** — `LeadRecord` is a Java record with `status` as a plain `String`:
```java
record LeadRecord(UUID id, long version, String displayName, String companyName, String email,
        String phone, String source, String status, UUID ownerUserId, ...)
```

**`CrmService.java:397-414`** — `toLeadRow()` copies `status` directly:
```java
private Map<String, Object> toLeadRow(LeadRecord record) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", record.id());
    row.put("status", record.status());  // ← direct copy, no transformation
    // ... other fields ...
    return row;
}
```

**`CrmController.java:114-116`** — List endpoint returns the raw row:
```java
@GetMapping("/leads")
public List<Map<String, Object>> listLeads(...) {
    return crm.listLeads(authentication, limit, status);
}
```

**Result:** API response `status` = database `status`. No transformation.

#### Layer 2: API Response → React State

**`lib/api/crm.ts:246`** — API client fetches with `cache: "no-store"`:
```typescript
leads: (status?: string) => apiClient.get<CrmLead[]>(`${root}/leads`, {
    query: { limit: 200, status },
    cache: "no-store"  // ← prevents HTTP cache
}),
```

**`lib/api/crm.ts:36-46`** — `CrmLead` interface has `status: string`:
```typescript
export interface CrmLead {
    id: string;
    status: string;  // ← plain string, no enum constraint
    // ... other fields ...
}
```

**No React Query, SWR, or application-level cache exists.** Grep for `useQuery`, `useSWR`, `queryClient` across `apps/web/app/crm/` returns zero results.

**Result:** React state `lead.status` = API response `status`. No caching.

#### Layer 3: React State → UI Rendering

**`leads-tab.tsx:162-167`** — Status badge renders `lead.status` directly:
```tsx
<span
  className={styles.statusBadge}
  style={{ backgroundColor: STATUS_COLORS[lead.status] ?? "var(--snad-muted)" }}
>
  {t(`leads.status.${lead.status.toLowerCase()}`)}
</span>
```

The only transformation is an **i18n label lookup** (`status.toLowerCase()` → translation key). The actual status value is unchanged.

**Result:** Displayed status = `lead.status` from React state. No transformation.

#### Layer 4: Optimistic UI Check

**`leads-tab.tsx:59-68`** — `handleStatusChange` does NOT update UI before PATCH:
```typescript
const handleStatusChange = useCallback(async (leadId, newStatus, currentStatus) => {
    if (TERMINAL_STATUSES.has(currentStatus)) return;
    try {
        await crmApi.changeLeadStatus(leadId, newStatus);  // PATCH
        await fetchLeads();                                 // re-fetch ALL leads
    } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to change status");
    }
}, [fetchLeads]);
```

- No `setState` call before the PATCH
- No optimistic UI update
- Full re-fetch after PATCH completes
- Error displayed if PATCH fails

**Result:** No optimistic UI that could cause stale display.

---

## Root Cause: DISQUALIFIED Terminal Status Inconsistency

### The Three Definitions

| File | Line | Definition | DISQUALIFIED included? |
|------|------|-----------|----------------------|
| `leads-tab.tsx` | 17 | `new Set(["CONVERTED", "ARCHIVED"])` | **NO** |
| `leads/page.tsx` | 85 | `["CONVERTED", "ARCHIVED", "DISQUALIFIED"]` | **YES** |
| `leads/[leadId]/page.tsx` | 28 | `new Set(["DISQUALIFIED", "CONVERTED", "ARCHIVED"])` | **YES** |

### Impact on User Experience

**Command Center (`leads-tab.tsx`):**
- DISQUALIFIED lead → `<select>` dropdown shown (line 182-193)
- User can select any status from `LEAD_STATUSES` including QUALIFIED, NEW, etc.
- `handleStatusChange` guard at line 61 checks `TERMINAL_STATUSES.has("DISQUALIFIED")` → **false**
- PATCH request is sent to backend
- Backend evaluates: `DISQUALIFIED → QUALIFIED` → `leadTransitionAllowed()` returns **false** (only ARCHIVED allowed from DISQUALIFIED)
- Backend throws 409 Conflict
- UI displays error message

**Operational List (`leads/page.tsx`):**
- DISQUALIFIED lead → read-only badge, no action buttons (line 176)
- User cannot attempt any status change
- No 409 occurs

**Lead Detail (`leads/[leadId]/page.tsx`):**
- DISQUALIFIED lead → read-only badge, no transition buttons (line 257-277)
- User cannot attempt any status change
- No 409 occurs

### Backend State Machine (for reference)

```java
// LegacyCrmInfrastructureService.java:1599-1609
private boolean leadTransitionAllowed(String current, String next) {
    if (current.equals(next)) return true;
    return switch (current) {
        case "NEW"        -> Set.of("ASSIGNED","CONTACTED","QUALIFIED","DISQUALIFIED","ARCHIVED").contains(next);
        case "ASSIGNED"   -> Set.of("CONTACTED","QUALIFIED","DISQUALIFIED","ARCHIVED").contains(next);
        case "CONTACTED"  -> Set.of("QUALIFIED","DISQUALIFIED","ARCHIVED").contains(next);
        case "QUALIFIED"  -> Set.of("DISQUALIFIED","ARCHIVED").contains(next);
        case "DISQUALIFIED" -> "ARCHIVED".equals(next);  // ← only ARCHIVED allowed
        default           -> false;  // ARCHIVED, CONVERTED → all blocked
    };
}
```

**DISQUALIFIED is semi-terminal:** only `ARCHIVED` is allowed as a next state. The command center UI does not reflect this.

---

## Consistency Check Results

| Check | Result | Evidence |
|-------|--------|----------|
| 1. Database row status | ✅ Consistent | `status` column in `crm_leads` table |
| 2. GET /api/v1/crm/leads/{id} response | ✅ Consistent | `toLeadRow()` copies `status` directly |
| 3. Network response (React) | ✅ Consistent | `cache: "no-store"`, `CrmLead.status: string` |
| 4. React Query / SWR cache | ✅ N/A | No cache layer exists |
| 5. Browser DevTools Network payload | ✅ Consistent | Raw JSON from API, no transformation |
| 6. Browser DevTools Response payload | ✅ Consistent | Same as network payload |
| 7. Optimistic UI updates | ✅ Not present | `handleStatusChange` does full re-fetch |
| 8. Table renders cached state | ✅ No | Fresh fetch on mount and after mutation |
| 9. PATCH targets same Lead ID | ✅ Yes | Same `lead.id` used for fetch and PATCH |
| 10. Refresh updates displayed status | ✅ Yes | `fetchLeads()` called after PATCH |

**All 10 checks pass.** The data flow is clean. The inconsistency is NOT caused by caching, optimistic UI, or stale state.

---

## Root Cause Summary

| Category | Finding |
|----------|---------|
| **Stale frontend cache** | ❌ Not the cause — no cache layer exists |
| **Optimistic UI** | ❌ Not the cause — no optimistic updates |
| **Browser cache** | ❌ Not the cause — `cache: "no-store"` |
| **API caching** | ❌ Not the cause — `cache: "no-store"` |
| **Different Lead IDs** | ❌ Not the cause — same ID used throughout |
| **Backend/database inconsistency** | ❌ Not the cause — status is read directly from DB |
| **DISQUALIFIED terminal inconsistency** | ✅ **ROOT CAUSE** — command center shows editable dropdown for DISQUALIFIED leads, but backend only allows ARCHIVED as next state |

---

## Recommended Fix

**Add `DISQUALIFIED` to `TERMINAL_STATUSES` in `leads-tab.tsx`:**

```typescript
// Current (line 17):
const TERMINAL_STATUSES = new Set<string>(["CONVERTED", "ARCHIVED"]);

// Fixed:
const TERMINAL_STATUSES = new Set<string>(["CONVERTED", "ARCHIVED", "DISQUALIFIED"]);
```

This aligns the command center with the operational pages and the backend state machine. A DISQUALIFIED lead will show a read-only badge instead of an editable dropdown, preventing the 409 Conflict.

**Also update:**
- `leads-tab.test.tsx:16` — Add `DISQUALIFIED` to test's `TERMINAL_STATUSES`
- `e2e/crm-035-terminal-leads.spec.ts:22` — Add `DISQUALIFIED` to E2E test's `TERMINAL_STATUSES`

---

## Files Referenced

| File | Lines | Role |
|------|-------|------|
| `leads-tab.tsx` | 17 | `TERMINAL_STATUSES` — **DISQUALIFIED missing** |
| `leads/page.tsx` | 85 | `terminalStates` — includes DISQUALIFIED |
| `leads/[leadId]/page.tsx` | 28 | `TERMINAL_STATUSES` — includes DISQUALIFIED |
| `leads-tab.tsx` | 173-194 | Conditional badge vs dropdown rendering |
| `leads-tab.tsx` | 59-68 | `handleStatusChange` — no optimistic UI |
| `lib/api/crm.ts` | 246 | `leads()` — `cache: "no-store"` |
| `lib/api/crm.ts` | 36-46 | `CrmLead` interface — `status: string` |
| `CrmService.java` | 397-414 | `toLeadRow()` — direct status copy |
| `LeadRepository.java` | 15-18 | `LeadRecord` — `status` as String |
| `LegacyCrmInfrastructureService.java` | 1599-1609 | `leadTransitionAllowed()` — state machine |
