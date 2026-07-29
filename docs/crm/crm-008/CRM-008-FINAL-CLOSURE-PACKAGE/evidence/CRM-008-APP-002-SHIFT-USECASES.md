# CRM-008-APP-002: Shift Management Use Cases

> **Agent:** Agent 3 — Application Layer & Use Case Implementation
> **Task:** 2 — Shift Management Use Cases
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the implementation of Shift Management use cases for CRM-008.

---

## 2. Use Case Inventory

| Use Case | Method | Transactional | Description |
|----------|--------|---------------|-------------|
| CreateShiftTemplate | `createShiftTemplate()` | ✅ | Create a new shift template |
| UpdateShiftTemplate | `updateShiftTemplate()` | ✅ | Update an existing template |
| PublishShiftTemplate | `publishShiftTemplate()` | ✅ | Activate a template |
| CancelShiftTemplate | `cancelShiftTemplate()` | ✅ | Deactivate a template |
| AssignShift | `assignShift()` | ✅ | Assign a shift to staff |
| UpdateShiftAssignment | `updateShiftAssignment()` | ✅ | Update a shift assignment |
| CancelShiftAssignment | `cancelShiftAssignment()` | ✅ | Cancel a shift assignment |
| ListShiftTemplates | `listShiftTemplates()` | ❌ | List templates with pagination |
| ListShiftAssignmentsByTeam | `listShiftAssignmentsByTeam()` | ❌ | List assignments by team |
| ListShiftAssignmentsByStaff | `listShiftAssignmentsByStaff()` | ❌ | List assignments by staff |

---

## 3. Business Rules

### CreateShiftTemplate
- **Preconditions**: Name must be unique within tenant
- **Postconditions**: Template created with ACTIVE status
- **Validations**: Name required, startTime/endTime required, daysOfWeek required

### AssignShift
- **Preconditions**: Team and template must exist, template must be ACTIVE
- **Postconditions**: Assignment created with SCHEDULED status
- **Validations**: No overlapping assignments for staff member
- **Overlap Check**: `start_date <= existing.end_date AND end_date >= existing.start_date`

### UpdateShiftAssignment
- **Preconditions**: Assignment must exist and not be COMPLETED/CANCELLED
- **Postconditions**: Assignment updated
- **Validations**: Overlap check if dates changed

### CancelShiftAssignment
- **Preconditions**: Assignment must exist and not be COMPLETED/CANCELLED
- **Postconditions**: Status changes to CANCELLED

---

## 4. State Transitions

### ShiftTemplate
```
ACTIVE → INACTIVE (via cancelShiftTemplate)
INACTIVE → ACTIVE (via publishShiftTemplate)
```

### ShiftAssignment
```
SCHEDULED → ACTIVE → COMPLETED
SCHEDULED → CANCELLED
ACTIVE → CANCELLED
```

---

## 5. Integration

- Delegates to `ShiftTemplateRepository` and `ShiftAssignmentRepository`
- Validates team existence via `SalesTeamRepository`
- Records audit and timeline events

---

**Certification Date:** 2026-07-28
**Agent 3 Task 2 Status:** COMPLETE
