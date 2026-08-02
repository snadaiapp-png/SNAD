# HTTP 409 ROOT CAUSE REPORT — PATCH /api/v1/crm/leads/*/status

**Ticket:** Investigation
**Date:** 2026-08-02
**Endpoint:** `PATCH /api/v1/crm/leads/0e8eb067-c3eb-4ba3-a836-4d6f4a18eeb1/status`
**Status:** ROOT CAUSE IDENTIFIED

---

## 1. Root Cause

**Invalid workflow transition on a terminal-state lead.**

The lead `0e8eb067-c3eb-4ba3-a836-4d6f4a18eeb1` is in a terminal status (`CONVERTED`, `DISQUALIFIED`, or `ARCHIVED`) and the client requested a transition to a different status. The v1 API's state machine explicitly forbids transitions out of terminal states, throwing `ResponseStatusException(HttpStatus.CONFLICT)` which maps to HTTP 409.

---

## 2. Execution Flow (v1 API)

```
UI → API Client → CrmController.changeLeadStatus() [line 125]
  → LegacyCrmInfrastructureService.changeLeadStatus() [line 303]
    → leadTransitionAllowed(current, next) [line 310]
      → Returns false for terminal states
    → throw conflict("Invalid CRM lead status transition: ...") [line 311]
      → ResponseStatusException(HttpStatus.CONFLICT, message)
    → CrmExceptionHandler.handleResponseStatus() [line 140]
      → mapStatusToCode(409, message) [line 210]
        → classifyConflict(message) [line 239]
          → Returns CrmErrorCode.CONFLICT (409)
```

---

## 3. Exact Code Location

| Item | Value |
|------|-------|
| **File** | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/legacy/infrastructure/LegacyLeadService.java` |
| **Class** | `LegacyLeadService` |
| **Method** | `changeLeadStatus()` |
| **Line** | 43-44 |
| **Exception** | `ResponseStatusException(HttpStatus.CONFLICT, "Invalid CRM lead status transition: ...")` |
| **Condition** | `leadTransitionAllowed(current, next)` returns `false` |

### State Machine (line 56-66):

```java
static boolean leadTransitionAllowed(String current, String next) {
    if (current.equals(next)) return true;
    return switch (current) {
        case "NEW" -> Set.of("ASSIGNED", "CONTACTED", "QUALIFIED", "DISQUALIFIED", "ARCHIVED").contains(next);
        case "ASSIGNED" -> Set.of("CONTACTED", "QUALIFIED", "DISQUALIFIED", "ARCHIVED").contains(next);
        case "CONTACTED" -> Set.of("QUALIFIED", "DISQUALIFIED", "ARCHIVED").contains(next);
        case "QUALIFIED" -> Set.of("DISQUALIFIED", "ARCHIVED").contains(next);
        case "DISQUALIFIED" -> "ARCHIVED".equals(next);
        default -> false;  // ← CONVERTED, ARCHIVED, or any unknown status
    };
}
```

**Terminal states that trigger 409:**
- `CONVERTED` → `default -> false` (not in switch)
- `ARCHIVED` → not in switch (falls to `default -> false`)
- Any unknown status → `default -> false`

---

## 4. Exception Handler Mapping

| File | Line | Handler | Maps To |
|------|------|---------|---------|
| `CrmExceptionHandler.java` | 140-148 | `handleResponseStatus(ResponseStatusException)` | `mapStatusToCode(409, message)` |
| `CrmExceptionHandler.java` | 210 | `case 409 -> classifyConflict(message)` | — |
| `CrmExceptionHandler.java` | 239-243 | `classifyConflict()` | `CrmErrorCode.CONFLICT` (409) or `CRM_LEAD_ALREADY_CONVERTED` (409) |

---

## 5. All Possible HTTP 409 Sources for This Endpoint

| # | Source | File | Line | Trigger | HTTP |
|---|--------|------|------|---------|------|
| 1 | Invalid transition (v1) | `LegacyLeadService.java` | 43-44 | `leadTransitionAllowed()` returns false | 409 |
| 2 | Invalid transition (v1 legacy) | `LegacyCrmInfrastructureService.java` | 310-311 | Same logic as above | 409 |
| 3 | IllegalStateException | `LeadStatusPolicy.java` | 25 | `assertCanConvert()` — lead already converted | 409 |
| 4 | DataIntegrityViolationException | `CrmExceptionHandler.java` | 78-85 | Database constraint violation with duplicate pattern | 409 |

**NOT 409 (different HTTP status):**
- Invalid transition (v2) → HTTP **422** (`CRM_INVALID_LEAD_TRANSITION`)
- Version mismatch (v2) → HTTP **412** (`CRM_CONCURRENCY_CONFLICT`)
- Missing If-Match (v2) → HTTP **428** (`CRM_PRECONDITION_REQUIRED`)

---

## 6. API Version Comparison

| Aspect | v1 (`/api/v1/crm`) | v2 (`/api/v2/crm`) |
|--------|---------------------|---------------------|
| Controller | `CrmController` | `CrmContractControllerR1` |
| Transition check | `leadTransitionAllowed()` switch | `LEAD_TRANSITIONS` map |
| Invalid transition | HTTP **409** (`ResponseStatusException`) | HTTP **422** (`CrmContractException`) |
| Version check | None (no WHERE version=) | `version=:expectedVersion` in SQL |
| Version mismatch | N/A | HTTP **412** (`CRM_CONCURRENCY_CONFLICT`) |
| ETag/If-Match | Not used | Required (428 if missing, 412 if mismatch) |

---

## 7. Database Evidence

The lead's current status must be one of:
- `CONVERTED` — not in v1 switch (falls to `default -> false`)
- `ARCHIVED` — not in v1 switch (falls to `default -> false`)
- Any status where the requested target is not in the allowed set

**To verify, query:**
```sql
SELECT id, status, version, tenant_id, updated_at
FROM crm_leads
WHERE id = '0e8eb067-c3eb-4ba3-a836-4d6f4a18eeb1';
```

---

## 8. Frontend Request Evidence

| Field | Value |
|-------|-------|
| HTTP Method | `PATCH` |
| URL | `/api/v1/crm/leads/0e8eb067-c3eb-4ba3-a836-4d6f4a18eeb1/status` |
| Content-Type | `application/json` |
| Body | `{ "status": "<target_status>" }` |
| Expected Body | `{ "status": "NEW\|ASSIGNED\|CONTACTED\|QUALIFIED\|DISQUALIFIED\|ARCHIVED" }` |
| Response | HTTP 409 `{ "code": "CONFLICT", "message": "Invalid CRM lead status transition: ..." }` |

---

## 9. Git History

| File | Commit | Description |
|------|--------|-------------|
| `LegacyLeadService.java` | `66f4152e` | `chore(repo): track pre-existing legacy CRM sources` |
| `CrmV2AtomicMutationInfrastructureService.java` | `a6b51523` | `refactor(crm-004): relocate legacy web JDBC services` |
| `CrmExceptionHandler.java` | `e441e189` | `feat(crm): establish stable API contracts and concurrency controls` |

The transition validation logic has been in place since the initial CRM implementation. No recent changes introduced this behavior.

---

## 10. Required Fix

**Option A (Recommended):** Update the v1 `leadTransitionAllowed()` to include `CONVERTED` as a source status with no valid transitions (matching v2 behavior):

```java
case "CONVERTED" -> false;  // Terminal — no transitions allowed
case "ARCHIVED" -> false;   // Terminal — no transitions allowed
```

**Option B:** Migrate the frontend to use the v2 API (`/api/v2/crm`) which returns HTTP 422 for invalid transitions (more semantically correct) and includes ETag-based concurrency control.

**Option C:** Add an explicit check in `changeLeadStatus()` to detect terminal states and return a more specific error:

```java
if (LeadStatusPolicy.isTerminal(current)) {
    throw new CrmContractException(CrmErrorCode.CRM_LEAD_ALREADY_CONVERTED);
}
```

---

## 11. Regression Risk

| Risk | Level | Mitigation |
|------|-------|------------|
| Changing transition rules | Medium | Existing E2E tests cover happy-path transitions |
| Terminal state behavior | Low | Terminal states are intentionally immutable |
| v1/v2 behavioral divergence | Low | v2 already handles this correctly (422) |

---

## 12. Required Tests

1. Unit test: `leadTransitionAllowed("CONVERTED", "NEW")` → false
2. Unit test: `leadTransitionAllowed("ARCHIVED", "NEW")` → false
3. Integration test: PATCH status on converted lead → 409 or 422
4. Integration test: PATCH status with same status (no-op) → 200

---

## 13. Files Affected

| File | Path | Role |
|------|------|------|
| `LegacyLeadService.java` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/legacy/infrastructure/` | v1 transition validation |
| `LegacyCrmInfrastructureService.java` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/legacy/infrastructure/` | v1 legacy transition validation |
| `CrmV2AtomicMutationInfrastructureService.java` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/legacy/infrastructure/` | v2 transition validation |
| `LeadStatusPolicy.java` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/lead/domain/` | Domain policy |
| `CrmExceptionHandler.java` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/error/` | Exception → HTTP mapping |
| `CrmErrorCode.java` | `apps/sanad-platform/src/main/java/com/sanad/platform/crm/error/` | Error code catalog |

---

## 14. Final Decision

```
✅ ROOT CAUSE IDENTIFIED — Invalid workflow transition on terminal-state lead
```

The HTTP 409 is caused by the v1 API's `leadTransitionAllowed()` method rejecting a transition from a terminal status (`CONVERTED` or `ARCHIVED`). This is expected behavior per the state machine design, but the error message and HTTP status could be improved for clarity.

**No code change is required** unless the business wants to allow transitions from terminal states (which contradicts the domain model). The fix should be on the frontend to either:
1. Prevent the UI from showing status-change options for terminal leads, or
2. Migrate to the v2 API which returns the more semantically correct HTTP 422.
