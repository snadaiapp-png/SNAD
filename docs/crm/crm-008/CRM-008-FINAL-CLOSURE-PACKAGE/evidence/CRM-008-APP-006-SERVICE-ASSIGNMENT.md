# CRM-008-APP-006: Service Assignment Use Cases

> **Agent:** Agent 3 — Application Layer & Use Case Implementation
> **Task:** 6 — Service Assignment Use Cases
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the implementation of Service Assignment use cases for CRM-008.

---

## 2. Use Case Inventory

| Use Case | Method | Transactional | Description |
|----------|--------|---------------|-------------|
| AssignService | `assignService()` | ✅ | Assign service to team |
| ReassignService | `reassignService()` | ✅ | Reassign to different team |
| CompleteService | `completeService()` | ✅ | Complete a service assignment |
| CancelService | `cancelService()` | ✅ | Cancel a service assignment |
| ListByTeam | `listByTeam()` | ❌ | List by team |
| ListByService | `listByService()` | ❌ | List by service |
| GetServiceAssignment | `getServiceAssignment()` | ❌ | Get specific assignment |

---

## 3. Business Rules

### AssignService
- **Preconditions**: Team must exist
- **Postconditions**: Assignment created with ACTIVE status
- **Validations**: teamId/serviceId required
- **Uniqueness**: One assignment per team-service pair

### ReassignService
- **Preconditions**: Assignment must exist and be ACTIVE
- **Postconditions**: Old assignment deactivated, new assignment created
- **Validations**: New team must be different, no duplicate on new team

### CompleteService
- **Preconditions**: Assignment must exist and be ACTIVE
- **Postconditions**: Status changes to INACTIVE
- **Invariants**: Cannot complete if already INACTIVE

### CancelService
- **Preconditions**: Assignment must exist and be ACTIVE
- **Postconditions**: Status changes to INACTIVE
- **Invariants**: Cannot cancel if already INACTIVE

---

## 4. State Transitions

```
ACTIVE → INACTIVE (via completeService or cancelService)
```

---

## 5. Integration

- Delegates to `ServiceAssignmentRepository`
- Validates team existence via `SalesTeamRepository`
- Records audit and timeline events

---

**Certification Date:** 2026-07-28
**Agent 3 Task 6 Status:** COMPLETE
