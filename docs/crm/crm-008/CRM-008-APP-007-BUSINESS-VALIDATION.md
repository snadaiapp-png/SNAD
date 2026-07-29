# CRM-008-APP-007: Business Validation

> **Agent:** Agent 3 — Application Layer & Use Case Implementation
> **Task:** 7 — Business Validation
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the business validation rules implemented across all CRM-008 UseCases.

---

## 2. Tenant Ownership Validation

| Use Case | Validation | Method |
|----------|------------|--------|
| All UseCases | `requireContext(tenantId, actorId)` | Null check on tenantId and actorId |
| All UseCases | Repository queries include `tenant_id` | SQL WHERE clause |
| TeamManagementUseCases | `searchTeams()` filters by tenant | Repository query |
| ShiftManagementUseCases | Team existence check | `teams.findById(tenantId, teamId)` |
| CapacityManagementUseCases | Team existence check | `teams.findById(tenantId, teamId)` |
| ServiceAssignmentUseCases | Team existence check | `teams.findById(tenantId, teamId)` |

---

## 3. Permission Checks

| Use Case | Check | Description |
|----------|-------|-------------|
| All UseCases | `actorId` required | Must have authenticated user |
| SalesTeamUseCases | `OwnershipUserValidationPort` | Validates user is ACTIVE in tenant |

---

## 4. Business Invariants

### ShiftTemplate
- Name must be unique within tenant
- startTime and endTime required
- daysOfWeek must not be empty

### ShiftAssignment
- No overlapping assignments for same staff member
- Template must be ACTIVE
- Team must exist

### StaffAvailability
- endDate must be on or after startDate
- type required (AVAILABLE, UNAVAILABLE, ON_LEAVE)

### StaffSkill
- skillName must be unique per staff member
- proficiency must be between 1 and 100

### CapacityPlan
- periodEnd must be on or after periodStart
- maxCapacity must be positive
- allocatedCapacity cannot exceed maxCapacity
- No overlapping active plans for same team and period

### WorkloadAssignment
- estimatedHours must be positive
- startDate required
- Cannot reassign completed/cancelled work

### ServiceAssignment
- One assignment per team-service pair (uniqueness)
- Cannot reassign INACTIVE assignments
- New team must be different from current

---

## 5. State Transition Validation

### ShiftTemplate
| From | To | Allowed |
|------|----|---------|
| ACTIVE | INACTIVE | ✅ via cancelShiftTemplate |
| INACTIVE | ACTIVE | ✅ via publishShiftTemplate |
| ACTIVE | ACTIVE | ❌ already ACTIVE |
| INACTIVE | INACTIVE | ❌ already INACTIVE |

### ShiftAssignment
| From | To | Allowed |
|------|----|---------|
| SCHEDULED | ACTIVE | ✅ |
| SCHEDULED | CANCELLED | ✅ via cancelShiftAssignment |
| ACTIVE | COMPLETED | ✅ |
| ACTIVE | CANCELLED | ✅ via cancelShiftAssignment |
| COMPLETED | * | ❌ terminal state |
| CANCELLED | * | ❌ terminal state |

### CapacityPlan
| From | To | Allowed |
|------|----|---------|
| DRAFT | ACTIVE | ✅ |
| ACTIVE | COMPLETED | ✅ |
| COMPLETED | * | ❌ terminal state |

### WorkloadAssignment
| From | To | Allowed |
|------|----|---------|
| PLANNED | IN_PROGRESS | ✅ |
| PLANNED | CANCELLED | ✅ via releaseAssignment |
| IN_PROGRESS | COMPLETED | ✅ |
| IN_PROGRESS | CANCELLED | ✅ via releaseAssignment |
| COMPLETED | * | ❌ terminal state |
| CANCELLED | * | ❌ terminal state |

### ServiceAssignment
| From | To | Allowed |
|------|----|---------|
| ACTIVE | INACTIVE | ✅ via completeService/cancelService |
| INACTIVE | * | ❌ terminal state |

---

## 6. Duplicate Prevention

| Entity | Unique Constraint | Validation |
|--------|-------------------|------------|
| ShiftTemplate | name per tenant | `existsByName()` |
| StaffSkill | skillName per staff | `existsByStaffAndSkill()` |
| ServiceAssignment | teamId + serviceId | `existsByTeamAndService()` |
| CapacityPlan | teamId + period (active) | `findActiveByTeamAndPeriod()` |

---

## 7. Optimistic Locking

All update operations use version-based optimistic locking:

| Repository | Lock Mechanism |
|------------|----------------|
| ShiftTemplateRepository | `version=:expectedVersion` |
| ShiftAssignmentRepository | `version=:expectedVersion` |
| AvailabilityRepository | `version=:expectedVersion` |
| SkillRepository | `version=:expectedVersion` |
| CapacityRepository | `version=:expectedVersion` |
| WorkloadRepository | `version=:expectedVersion` |
| ServiceAssignmentRepository | `version=:expectedVersion` |

**Conflict Handling**: Returns `Optional.empty()` on version mismatch, UseCase throws `OwnershipDomainException`.

---

## 8. Audit Trail

All write operations record:
1. **Audit Event**: `AuditPort.record()` with before/after JSON snapshots
2. **Timeline Event**: `TimelineEventPort.record()` with entity type, event key, and summary

| Operation | Audit Action | Timeline Event |
|-----------|--------------|----------------|
| Create | CREATE | `*.created` |
| Update | UPDATE | `*.updated` |
| Activate | ACTIVATE | `*.activated` |
| Archive | ARCHIVE | `*.archived` |
| Cancel | CANCEL | `*.cancelled` |
| Complete | COMPLETE | `*.completed` |
| Delete | DELETE | `*.deleted` |

---

**Certification Date:** 2026-07-28
**Agent 3 Task 7 Status:** COMPLETE
