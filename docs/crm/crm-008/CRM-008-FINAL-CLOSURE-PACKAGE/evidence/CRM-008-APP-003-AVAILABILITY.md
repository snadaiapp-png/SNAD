# CRM-008-APP-003: Availability Management Use Cases

> **Agent:** Agent 3 — Application Layer & Use Case Implementation
> **Task:** 3 — Availability Management Use Cases
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the implementation of Availability Management use cases for CRM-008.

---

## 2. Use Case Inventory

| Use Case | Method | Transactional | Description |
|----------|--------|---------------|-------------|
| SubmitAvailability | `submitAvailability()` | ✅ | Submit new availability |
| ApproveAvailability | `approveAvailability()` | ✅ | Approve availability |
| RejectAvailability | `rejectAvailability()` | ✅ | Reject availability |
| CalendarQuery | `calendarQuery()` | ❌ | Query availability by date range |
| DeleteAvailability | `deleteAvailability()` | ✅ | Delete availability record |

---

## 3. Business Rules

### SubmitAvailability
- **Preconditions**: Staff must exist in tenant
- **Postconditions**: Availability record created
- **Validations**: type required, startDate/endDate required, endDate >= startDate

### ApproveAvailability
- **Preconditions**: Availability must exist and not be AVAILABLE
- **Postconditions**: Type changes to AVAILABLE
- **Invariants**: Cannot approve if already AVAILABLE

### RejectAvailability
- **Preconditions**: Availability must exist and not be UNAVAILABLE
- **Postconditions**: Type changes to UNAVAILABLE with rejection reason
- **Invariants**: Cannot reject if already UNAVAILABLE

### CalendarQuery
- **Preconditions**: Staff must exist in tenant
- **Postconditions**: Returns list of availability records in date range
- **Validations**: from/to dates required, to >= from

---

## 4. State Transitions

```
UNAVAILABLE → AVAILABLE (via approveAvailability)
AVAILABLE → UNAVAILABLE (via rejectAvailability)
ON_LEAVE → AVAILABLE (via approveAvailability)
ON_LEAVE → UNAVAILABLE (via rejectAvailability)
```

---

## 5. Integration

- Delegates to `AvailabilityRepository`
- Records audit and timeline events

---

**Certification Date:** 2026-07-28
**Agent 3 Task 3 Status:** COMPLETE
