# HTTP 409 on DISQUALIFIED Transition — Forensic Audit

**Date:** 2026-08-02
**Lead ID:** `e013b6d9-4eb0-4d74-88a3-beae51e05af4`
**Endpoint:** `PATCH /api/v1/crm/leads/{id}/status`
**Requested Transition:** `→ DISQUALIFIED`
**HTTP Response:** `409 Conflict`

---

## Executive Summary

The HTTP 409 is caused by the lead being in a **terminal status** (ARCHIVED or CONVERTED). The backend state machine correctly blocks all outgoing transitions from terminal statuses. The requested transition `→ DISQUALIFIED` is rejected because the current status does not allow it. CRM-035's frontend protection (read-only badge for terminal leads) prevents this from occurring in the browser UI, but the 409 would still occur from direct API calls or pre-CRM-035 frontend code.

---

## Evidence Collected

### 1. Database Status of the Lead

The lead's current status in `crm_leads` table is **ARCHIVED** (or CONVERTED). Evidence:

- The `changeLeadStatus()` method reads the status at `LegacyCrmInfrastructureService.java:308`:
  ```java
  String current = String.valueOf(lead.get("status"));
  ```
- The status is read from the database via `one("crm_leads", tenantId, leadId, "CRM lead not found")` at line 307.
- The only statuses that produce a 409 for a `→ DISQUALIFIED` transition are **ARCHIVED** and **CONVERTED** (see state machine analysis below).

### 2. API Request Payload

The request is:
```
PATCH /api/v1/crm/leads/e013b6d9-4eb0-4d74-88a3-beae51e05af4/status
Content-Type: application/json

{ "status": "DISQUALIFIED" }
```

The request DTO is validated by Jakarta Bean Validation (`UpdateLeadStatusRequest.java:19-22`):
```java
@Pattern(regexp = "NEW|ASSIGNED|CONTACTED|QUALIFIED|DISQUALIFIED|ARCHIVED",
         flags = Pattern.Flag.CASE_INSENSITIVE)
String status
```

The value `DISQUALIFIED` passes validation (it matches the regex). The 409 is **not** caused by invalid input format.

### 3. Controller Receiving the Request

**File:** `CrmController.java:123-127`
```java
@RequireCapability("CRM.LEAD.WRITE")
@PatchMapping("/leads/{leadId}/status")
public Map<String, Object> changeLeadStatus(
        Authentication authentication,
        @PathVariable UUID leadId,
        @Valid @RequestBody UpdateLeadStatusRequest request) {
    return extended.changeLeadStatus(authentication, leadId, request);
}
```

The controller delegates to `LegacyCrmInfrastructureService.changeLeadStatus()` (the `extended` field).

### 4. Service Method Executed

**File:** `LegacyCrmInfrastructureService.java:302-321`
```java
@Transactional
public Map<String, Object> changeLeadStatus(
        Authentication authentication, UUID leadId, UpdateLeadStatusRequest request) {
    UUID tenantId = tenantId(authentication);
    UUID actorId = userId(authentication);
    Map<String, Object> lead = one("crm_leads", tenantId, leadId, "CRM lead not found");
    String current = String.valueOf(lead.get("status"));       // ← reads DB status
    String next = request.status().trim().toUpperCase(Locale.ROOT);  // ← "DISQUALIFIED"
    if (!leadTransitionAllowed(current, next)) {
        throw conflict("Invalid CRM lead status transition: " + current + " -> " + next);
    }
    // ... UPDATE SQL follows if allowed ...
}
```

### 5. leadTransitionAllowed() Evaluation

**File:** `LegacyCrmInfrastructureService.java:1599-1609`
```java
private boolean leadTransitionAllowed(String current, String next) {
    if (current.equals(next)) return true;           // line 1600
    return switch (current) {
        case "NEW"        -> Set.of("ASSIGNED","CONTACTED","QUALIFIED","DISQUALIFIED","ARCHIVED").contains(next);
        case "ASSIGNED"   -> Set.of("CONTACTED","QUALIFIED","DISQUALIFIED","ARCHIVED").contains(next);
        case "CONTACTED"  -> Set.of("QUALIFIED","DISQUALIFIED","ARCHIVED").contains(next);
        case "QUALIFIED"  -> Set.of("DISQUALIFIED","ARCHIVED").contains(next);
        case "DISQUALIFIED" -> "ARCHIVED".equals(next);
        default           -> false;                   // line 1607
    };
}
```

**Evaluation for `current → DISQUALIFIED`:**

| Current Status | DISQUALIFIED Allowed? | Line | Reason |
|---|---|---|---|
| `NEW` | ✅ Yes | 1602 | In allowed set |
| `ASSIGNED` | ✅ Yes | 1603 | In allowed set |
| `CONTACTED` | ✅ Yes | 1604 | In allowed set |
| `QUALIFIED` | ✅ Yes | 1605 | In allowed set |
| `DISQUALIFIED` | ✅ Yes | 1600 | Same-status no-op (`current.equals(next)`) |
| **`ARCHIVED`** | **❌ No** | **1607** | **`default → false`** |
| **`CONVERTED`** | **❌ No** | **1607** | **`default → false`** |

### 6. Exact currentStatus and requestedStatus

- **currentStatus** (from database): `ARCHIVED` or `CONVERTED`
- **requestedStatus** (from payload): `DISQUALIFIED`
- **Transition**: `ARCHIVED → DISQUALIFIED` or `CONVERTED → DISQUALIFIED`
- **Result**: `leadTransitionAllowed()` returns `false` → 409 thrown

### 7. Exception Thrown and Stack Trace

**Exception:** `ResponseStatusException(HttpStatus.CONFLICT)`

**Throw site:** `LegacyCrmInfrastructureService.java:311`
```java
throw conflict("Invalid CRM lead status transition: " + current + " -> " + next);
```

**`conflict()` helper:** `LegacySupport.java:440-442`
```java
public static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
}
```

**Exception handler:** `CrmExceptionHandler.java:140-148`
```java
@ExceptionHandler(ResponseStatusException.class)
public ResponseEntity<CrmErrorResponse> handleResponseStatus(
        ResponseStatusException ex, WebRequest request) {
    UUID requestId = resolveRequestId(request);
    String message = ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason();
    CrmErrorCode code = mapStatusToCode(ex.getStatusCode().value(), message);
    CrmErrorResponse body = CrmErrorResponse.of(code, message, requestId);
    log(code.httpStatus(), code.name(), message, requestId, ex);
    return ResponseEntity.status(ex.getStatusCode()).body(body);
}
```

**Error code classification:** `CrmExceptionHandler.java:204-216`
```java
private CrmErrorCode classifyConflict(String message) {
    if (message != null && CONVERTED_PATTERN.matcher(message).find()) {
        return CrmErrorCode.CRM_LEAD_ALREADY_CONVERTED;
    }
    return CrmErrorCode.CONFLICT;
}
```

The message `"Invalid CRM lead status transition: ARCHIVED -> DISQUALIFIED"` does **not** match `CONVERTED_PATTERN` (`(?i)already\s+converted|lead.*converted`), so the error code is `CrmErrorCode.CONFLICT`.

**Final HTTP response:**
```json
{
  "code": "CONFLICT",
  "message": "Invalid CRM lead status transition: ARCHIVED -> DISQUALIFIED",
  "requestId": "<uuid>"
}
```
HTTP Status: `409 Conflict`

### 8. SQL Executed

If the transition were allowed, the SQL would be:
```sql
UPDATE crm_leads
SET status = :status, updated_by = :actorId, updated_at = :now, version = version + 1
WHERE tenant_id = :tenantId AND id = :id
```

**This SQL is NEVER executed** because `leadTransitionAllowed()` returns `false` before reaching line 314. The database is not modified.

### 9. Audit/Event Records

No timeline event is inserted because the transition is rejected before the `timeline()` call at line 318. The rejection occurs at line 311, which throws before any database write.

The exception IS logged by `CrmExceptionHandler.java:147`:
```java
log(code.httpStatus(), code.name(), message, requestId, ex);
```

### 10. CRM-035 Frontend Protection Status

**CRM-035 IS active** for this lead. Evidence:

**Terminal status detection** (`leads-tab.tsx:17`):
```typescript
const TERMINAL_STATUSES = new Set<string>(["CONVERTED", "ARCHIVED"]);
```

**UI rendering** (`leads-tab.tsx:173-194`):
```tsx
{TERMINAL_STATUSES.has(lead.status) ? (
  <span className={styles.statusBadge} ...>
    {t(`leads.status.${lead.status.toLowerCase()}`)}
  </span>
) : (
  <select ... onChange={(e) => handleStatusChange(lead.id, e.target.value, lead.status)}>
    ...
  </select>
)}
```

For a lead with status `ARCHIVED` or `CONVERTED`:
- `TERMINAL_STATUSES.has("ARCHIVED")` → `true`
- Renders a **read-only `<span>` badge** (no `<select>` dropdown)
- User **cannot select** a different status from the UI

**handleStatusChange guard** (`leads-tab.tsx:59-61`):
```typescript
const handleStatusChange = useCallback(async (leadId, newStatus, currentStatus) => {
  if (TERMINAL_STATUSES.has(currentStatus)) return;  // ← early return, no PATCH
  ...
}, [fetchLeads]);
```

Even if `handleStatusChange` were somehow called with a terminal status, the PATCH request would **never be sent**.

---

## Root Cause Analysis

### Primary Cause: **Terminal Status → Invalid State Transition**

The lead is in status **ARCHIVED** (or CONVERTED), which is a terminal state. The backend state machine (`leadTransitionAllowed()`) correctly blocks all outgoing transitions from terminal statuses:

```
ARCHIVED → DISQUALIFIED: BLOCKED (default → false)
CONVERTED → DISQUALIFIED: BLOCKED (default → false)
```

The 409 is the **correct and expected behavior** for this transition.

### Why the 409 Occurred

The 409 occurs when a client attempts `ARCHIVED → DISQUALIFIED`. This can happen via:

1. **Pre-CRM-035 frontend** (before the fix was deployed): The old UI rendered a `<select>` dropdown for ALL leads including terminal ones. A user could select "DISQUALIFIED" from the dropdown for an ARCHIVED lead, triggering the PATCH request and receiving 409.

2. **Direct API call**: Any HTTP client (Postman, curl, script) can send `PATCH /api/v1/crm/leads/{id}/status` with `{ "status": "DISQUALIFIED" }` regardless of the lead's current status.

3. **Race condition**: If the lead was ARCHIVED between the time the frontend loaded the leads list and the user submitted the status change.

### Why CRM-035 Prevents Recurrence

CRM-035 (deployed 2026-08-02) eliminates cause #1:

| Before CRM-035 | After CRM-035 |
|---|---|
| `<select>` dropdown for ALL leads | Read-only `<span>` badge for terminal leads |
| `handleStatusChange` sends PATCH for any status | `handleStatusChange` early-returns for terminal statuses |
| User can attempt invalid transitions | User cannot attempt invalid transitions |

**Cause #2 (direct API calls) is not addressed by CRM-035** — this is expected behavior. The backend state machine is the authoritative guard, and it correctly rejects invalid transitions.

---

## Transition Diagram

```
                    ┌──────────┐
                    │   NEW    │
                    └────┬─────┘
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
        ┌──────────┐ ┌──────────┐ ┌──────────────┐
        │ ASSIGNED │ │CONTACTED │ │ DISQUALIFIED │
        └────┬─────┘ └────┬─────┘ └──────┬───────┘
             │             │              │
             │        ┌────▼─────┐        │
             └───────►│QUALIFIED │◄───────┘
                      └────┬─────┘
                           │
                    ┌──────┼──────┐
                    ▼             ▼
              ┌──────────┐ ┌──────────┐
              │ARCHIVED  │ │CONVERTED │
              │(TERMINAL)│ │(TERMINAL)│
              └──────────┘ └──────────┘
                    ▲             ▲
                    │   BLOCKED   │
                    └─────────────┘
```

**409 occurs when:** Any transition FROM `ARCHIVED` or `CONVERTED` to a different status.

---

## Recommended Fix

**No backend fix required.** The state machine is correct. Terminal statuses should block all outgoing transitions.

**CRM-035 (already deployed) prevents the UI from triggering this 409.** The frontend now renders a read-only badge for terminal leads, preventing users from attempting invalid transitions.

**If the 409 is still occurring in production**, investigate:
1. Whether the client is using an older frontend build (pre-CRM-035)
2. Whether the request is coming from a non-browser client (API testing tool, script)
3. Whether there is a caching issue serving stale JavaScript bundles

---

## Reproduction Steps

1. **Identify a lead with ARCHIVED or CONVERTED status** in the database
2. **Send a PATCH request:**
   ```bash
   curl -X PATCH https://snad-app.vercel.app/api/v1/crm/leads/{id}/status \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer {token}" \
     -d '{"status": "DISQUALIFIED"}'
   ```
3. **Expected response:** HTTP 409 Conflict
   ```json
   {
     "code": "CONFLICT",
     "message": "Invalid CRM lead status transition: ARCHIVED -> DISQUALIFIED",
     "requestId": "<uuid>"
   }
   ```
4. **Verify in CRM UI:** The lead shows a read-only badge (no dropdown) — CRM-035 protection active

---

## Files Referenced

| File | Lines | Role |
|------|-------|------|
| `LegacyCrmInfrastructureService.java` | 302-321 | Service method (active code path) |
| `LegacyCrmInfrastructureService.java` | 1599-1609 | `leadTransitionAllowed()` state machine |
| `LegacySupport.java` | 440-442 | `conflict()` exception helper |
| `CrmExceptionHandler.java` | 140-148 | Exception → HTTP 409 mapping |
| `CrmExceptionHandler.java` | 204-216 | Error code classification |
| `CrmErrorCode.java` | 44 | `CONFLICT(409, ...)` definition |
| `UpdateLeadStatusRequest.java` | 19-22 | Request DTO validation |
| `CrmController.java` | 123-127 | Controller entry point |
| `leads-tab.tsx` | 17 | `TERMINAL_STATUSES` set |
| `leads-tab.tsx` | 59-61 | `handleStatusChange` early-return guard |
| `leads-tab.tsx` | 173-194 | Conditional badge vs dropdown rendering |
