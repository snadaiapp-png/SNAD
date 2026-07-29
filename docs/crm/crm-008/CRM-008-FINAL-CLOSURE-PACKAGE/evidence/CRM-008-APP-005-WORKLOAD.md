# CRM-008-APP-005: Workload Assignment Use Cases

> **Agent:** Agent 3 — Application Layer & Use Case Implementation
> **Task:** 5 — Workload Assignment Use Cases
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the implementation of Workload Assignment use cases for CRM-008.

---

## 2. Use Case Inventory

| Use Case | Method | Transactional | Description |
|----------|--------|---------------|-------------|
| AssignWork | `assignWork()` | ✅ | Assign work to staff |
| ReassignWork | `reassignWork()` | ✅ | Reassign to different staff |
| BalanceWorkload | `balanceWorkload()` | ✅ | Balance workload across staff |
| ReleaseAssignment | `releaseAssignment()` | ✅ | Cancel a workload assignment |
| ListByStaff | `listByStaff()` | ❌ | List by staff and status |
| ListByService | `listByService()` | ❌ | List by service |
| GetEstimatedHours | `getEstimatedHours()` | ❌ | Get estimated hours for period |
| GetActualHours | `getActualHours()` | ❌ | Get actual hours for period |

---

## 3. Business Rules

### AssignWork
- **Preconditions**: Staff must exist in tenant
- **Postconditions**: Workload created with PLANNED status
- **Validations**: estimatedHours > 0, startDate required

### ReassignWork
- **Preconditions**: Assignment must exist and not be COMPLETED/CANCELLED
- **Postconditions**: Old assignment cancelled, new assignment created
- **Validations**: New staff must be different from current

### BalanceWorkload
- **Preconditions**: All workload IDs must exist
- **Postconditions**: Workload redistributed evenly
- **Algorithm**: Total hours / number of active assignments

### ReleaseAssignment
- **Preconditions**: Assignment must exist and not be COMPLETED/CANCELLED
- **Postconditions**: Status changes to CANCELLED

---

## 4. State Transitions

```
PLANNED → IN_PROGRESS → COMPLETED
PLANNED → CANCELLED
IN_PROGRESS → CANCELLED
```

---

## 5. Integration

- Delegates to `WorkloadRepository`
- Records audit and timeline events

---

**Certification Date:** 2026-07-28
**Agent 3 Task 5 Status:** COMPLETE
