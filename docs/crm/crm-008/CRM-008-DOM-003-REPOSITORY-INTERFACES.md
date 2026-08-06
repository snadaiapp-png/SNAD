# CRM-008-DOM-003: Repository Interfaces

> **Agent:** Agent 2 — Domain Models & Repository Implementation
> **Task:** 4 — Repository Interface Implementation
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the implementation of 7 repository interfaces for CRM-008 Team Management. Each interface follows the established pattern with inner command records for Create/Update operations.

---

## 2. Implemented Repository Interfaces

| Repository | Package | Methods | Command Records |
|------------|---------|---------|-----------------|
| ShiftTemplateRepository | scheduling | 5 | CreateShiftTemplateCommand, UpdateShiftTemplateCommand |
| ShiftAssignmentRepository | scheduling | 6 | CreateShiftAssignmentCommand, UpdateShiftAssignmentCommand |
| AvailabilityRepository | availability | 5 | CreateAvailabilityCommand, UpdateAvailabilityCommand |
| SkillRepository | skills | 7 | CreateSkillCommand, UpdateSkillCommand |
| CapacityRepository | capacity | 5 | CreateCapacityPlanCommand, UpdateCapacityPlanCommand |
| WorkloadRepository | workload | 8 | CreateWorkloadCommand, UpdateWorkloadCommand |
| ServiceAssignmentRepository | service | 7 | CreateServiceAssignmentCommand, UpdateServiceAssignmentCommand |

---

## 3. Method Summary

### ShiftTemplateRepository (5 methods)
- `findById(UUID tenantId, UUID id)` — Find by ID
- `findAll(UUID tenantId, int limit, int offset)` — List with pagination
- `create(CreateShiftTemplateCommand)` — Create new template
- `update(UUID tenantId, UUID id, UpdateShiftTemplateCommand)` — Update with optimistic locking
- `existsByName(UUID tenantId, String name, UUID excludeId)` — Uniqueness check

### ShiftAssignmentRepository (6 methods)
- `findById(UUID tenantId, UUID id)` — Find by ID
- `findByTeamId(UUID tenantId, UUID teamId, int limit, int offset)` — List by team
- `findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to)` — List by staff date range
- `create(CreateShiftAssignmentCommand)` — Create new assignment
- `update(UUID tenantId, UUID id, UpdateShiftAssignmentCommand)` — Update with optimistic locking
- `hasOverlap(UUID tenantId, UUID staffId, LocalDate startDate, LocalDate endDate, UUID excludeId)` — Overlap detection

### AvailabilityRepository (5 methods)
- `findById(UUID tenantId, UUID id)` — Find by ID
- `findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to)` — List by staff date range
- `create(CreateAvailabilityCommand)` — Create new availability
- `update(UUID tenantId, UUID id, UpdateAvailabilityCommand)` — Update with optimistic locking
- `delete(UUID tenantId, UUID id)` — Soft delete

### SkillRepository (7 methods)
- `findById(UUID tenantId, UUID id)` — Find by ID
- `findByStaffId(UUID tenantId, UUID staffId)` — List by staff
- `findBySkillName(UUID tenantId, String skillName)` — List by skill name
- `create(CreateSkillCommand)` — Create new skill
- `update(UUID tenantId, UUID id, UpdateSkillCommand)` — Update with optimistic locking
- `delete(UUID tenantId, UUID id)` — Soft delete
- `existsByStaffAndSkill(UUID tenantId, UUID staffId, String skillName, UUID excludeId)` — Uniqueness check

### CapacityRepository (5 methods)
- `findById(UUID tenantId, UUID id)` — Find by ID
- `findByTeamId(UUID tenantId, UUID teamId)` — List by team
- `findActiveByTeamAndPeriod(UUID tenantId, UUID teamId, LocalDate date)` — Find active plan for date
- `create(CreateCapacityPlanCommand)` — Create new plan
- `update(UUID tenantId, UUID id, UpdateCapacityPlanCommand)` — Update with optimistic locking

### WorkloadRepository (8 methods)
- `findById(UUID tenantId, UUID id)` — Find by ID
- `findByStaffId(UUID tenantId, UUID staffId, WorkloadStatus status)` — List by staff and status
- `findByServiceId(UUID tenantId, UUID serviceId)` — List by service
- `create(CreateWorkloadCommand)` — Create new workload
- `update(UUID tenantId, UUID id, UpdateWorkloadCommand)` — Update with optimistic locking
- `delete(UUID tenantId, UUID id)` — Soft delete
- `sumEstimatedHoursByStaff(UUID tenantId, UUID staffId, LocalDate from, LocalDate to)` — Aggregate hours
- `sumActualHoursByStaff(UUID tenantId, UUID staffId, LocalDate from, LocalDate to)` — Aggregate hours

### ServiceAssignmentRepository (7 methods)
- `findById(UUID tenantId, UUID id)` — Find by ID
- `findByTeamId(UUID tenantId, UUID teamId)` — List by team
- `findByServiceId(UUID tenantId, UUID serviceId)` — List by service
- `create(CreateServiceAssignmentCommand)` — Create new assignment
- `update(UUID tenantId, UUID id, UpdateServiceAssignmentCommand)` — Update with optimistic locking
- `delete(UUID tenantId, UUID id)` — Soft delete
- `existsByTeamAndService(UUID tenantId, UUID teamId, UUID serviceId, UUID excludeId)` — Uniqueness check

---

## 4. Design Decisions

1. **Inner Command Records**: Create/Update commands defined as inner records for type safety
2. **Optimistic Locking**: Update methods accept expectedVersion for concurrency control
3. **Optional Returns**: Update methods return Optional to indicate stale version conflicts
4. **Tenant Scoping**: All methods require tenantId as first parameter
5. **Pagination Support**: List methods support limit/offset for large datasets
6. **Aggregate Functions**: WorkloadRepository includes SUM queries for capacity planning

---

## 5. Total Method Count

**43 repository methods across 7 interfaces**

---

**Certification Date:** 2026-07-28
**Agent 2 Task 4 Status:** COMPLETE
