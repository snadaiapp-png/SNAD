# CRM-008 Repository Contracts — Agent 1

> **Agent:** Agent 1 — Architecture & Database Foundation
> **Command:** CRM-008-EXECUTION-001
> **Task:** 6 — Repository Contracts
> **Date:** 2026-07-28
> **Status:** PASS

---

## 1. Contract Scope

Define repository interfaces for all CRM-008 Team Management entities.

---

## 2. Repository Contracts

### 2.1 ShiftTemplateRepository

```java
package com.sanad.platform.crm.ownership.domain.scheduling;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftTemplateRepository {

    record CreateShiftTemplateCommand(
        UUID tenantId,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        List<DayOfWeek> daysOfWeek,
        UUID createdBy
    ) {}

    record UpdateShiftTemplateCommand(
        String name,
        LocalTime startTime,
        LocalTime endTime,
        List<DayOfWeek> daysOfWeek,
        ShiftTemplateStatus status,
        UUID updatedBy,
        long expectedVersion
    ) {}

    Optional<ShiftTemplate> findById(UUID tenantId, UUID id);

    List<ShiftTemplate> findAll(UUID tenantId, int limit, int offset);

    ShiftTemplate create(CreateShiftTemplateCommand command);

    Optional<ShiftTemplate> update(UUID tenantId, UUID id, UpdateShiftTemplateCommand command);

    boolean existsByName(UUID tenantId, String name, UUID excludeId);
}
```

**Contract Tests:**
- `findById` returns empty for non-existent ID
- `findById` returns template for valid ID
- `findAll` returns paginated results
- `create` inserts new record with generated UUID
- `update` modifies existing record with version check
- `existsByName` detects duplicate names

---

### 2.2 ShiftAssignmentRepository

```java
package com.sanad.platform.crm.ownership.domain.scheduling;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftAssignmentRepository {

    record CreateShiftAssignmentCommand(
        UUID tenantId,
        UUID teamId,
        UUID staffId,
        UUID shiftTemplateId,
        LocalDate startDate,
        LocalDate endDate,
        UUID createdBy
    ) {}

    record UpdateShiftAssignmentCommand(
        UUID shiftTemplateId,
        LocalDate startDate,
        LocalDate endDate,
        ShiftAssignmentStatus status,
        UUID updatedBy,
        long expectedVersion
    ) {}

    Optional<ShiftAssignment> findById(UUID tenantId, UUID id);

    List<ShiftAssignment> findByTeamId(UUID tenantId, UUID teamId, int limit, int offset);

    List<ShiftAssignment> findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);

    ShiftAssignment create(CreateShiftAssignmentCommand command);

    Optional<ShiftAssignment> update(UUID tenantId, UUID id, UpdateShiftAssignmentCommand command);

    boolean hasOverlap(UUID tenantId, UUID staffId, LocalDate startDate, LocalDate endDate, UUID excludeId);
}
```

**Contract Tests:**
- `findById` returns empty for non-existent ID
- `findByTeamId` returns assignments for specific team
- `findByStaffId` returns assignments in date range
- `create` inserts new record
- `update` modifies existing record with version check
- `hasOverlap` detects scheduling conflicts

---

### 2.3 AvailabilityRepository

```java
package com.sanad.platform.crm.ownership.domain.availability;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityRepository {

    record CreateAvailabilityCommand(
        UUID tenantId,
        UUID staffId,
        AvailabilityType type,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        String reason,
        UUID createdBy
    ) {}

    record UpdateAvailabilityCommand(
        AvailabilityType type,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        String reason,
        UUID updatedBy,
        long expectedVersion
    ) {}

    Optional<StaffAvailability> findById(UUID tenantId, UUID id);

    List<StaffAvailability> findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);

    StaffAvailability create(CreateAvailabilityCommand command);

    Optional<StaffAvailability> update(UUID tenantId, UUID id, UpdateAvailabilityCommand command);

    boolean delete(UUID tenantId, UUID id);
}
```

**Contract Tests:**
- `findById` returns empty for non-existent ID
- `findByStaffId` returns availability in date range
- `create` inserts new record
- `update` modifies existing record with version check
- `delete` removes record

---

### 2.4 SkillRepository

```java
package com.sanad.platform.crm.ownership.domain.skills;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository {

    record CreateSkillCommand(
        UUID tenantId,
        UUID staffId,
        String skillName,
        SkillLevel level,
        int proficiency,
        UUID createdBy
    ) {}

    record UpdateSkillCommand(
        SkillLevel level,
        int proficiency,
        UUID updatedBy,
        long expectedVersion
    ) {}

    Optional<StaffSkill> findById(UUID tenantId, UUID id);

    List<StaffSkill> findByStaffId(UUID tenantId, UUID staffId);

    List<StaffSkill> findBySkillName(UUID tenantId, String skillName);

    StaffSkill create(CreateSkillCommand command);

    Optional<StaffSkill> update(UUID tenantId, UUID id, UpdateSkillCommand command);

    boolean delete(UUID tenantId, UUID id);

    boolean existsByStaffAndSkill(UUID tenantId, UUID staffId, String skillName, UUID excludeId);
}
```

**Contract Tests:**
- `findById` returns empty for non-existent ID
- `findByStaffId` returns all skills for staff
- `findBySkillName` returns all staff with skill
- `create` inserts new record
- `update` modifies existing record with version check
- `delete` removes record
- `existsByStaffAndSkill` detects duplicate skills

---

### 2.5 CapacityRepository

```java
package com.sanad.platform.crm.ownership.domain.capacity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapacityRepository {

    record CreateCapacityPlanCommand(
        UUID tenantId,
        UUID teamId,
        LocalDate periodStart,
        LocalDate periodEnd,
        int maxCapacity,
        UUID createdBy
    ) {}

    record UpdateCapacityPlanCommand(
        Integer maxCapacity,
        Integer allocatedCapacity,
        CapacityStatus status,
        UUID updatedBy,
        long expectedVersion
    ) {}

    Optional<CapacityPlan> findById(UUID tenantId, UUID id);

    List<CapacityPlan> findByTeamId(UUID tenantId, UUID teamId);

    Optional<CapacityPlan> findActiveByTeamAndPeriod(UUID tenantId, UUID teamId, LocalDate date);

    CapacityPlan create(CreateCapacityPlanCommand command);

    Optional<CapacityPlan> update(UUID tenantId, UUID id, UpdateCapacityPlanCommand command);
}
```

**Contract Tests:**
- `findById` returns empty for non-existent ID
- `findByTeamId` returns all plans for team
- `findActiveByTeamAndPeriod` returns active plan for date
- `create` inserts new record
- `update` modifies existing record with version check

---

### 2.6 WorkloadRepository

```java
package com.sanad.platform.crm.ownership.domain.workload;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkloadRepository {

    record CreateWorkloadCommand(
        UUID tenantId,
        UUID staffId,
        UUID serviceId,
        UUID jobId,
        int estimatedHours,
        LocalDate startDate,
        LocalDate endDate,
        UUID createdBy
    ) {}

    record UpdateWorkloadCommand(
        Integer actualHours,
        WorkloadStatus status,
        LocalDate endDate,
        UUID updatedBy,
        long expectedVersion
    ) {}

    Optional<WorkloadAssignment> findById(UUID tenantId, UUID id);

    List<WorkloadAssignment> findByStaffId(UUID tenantId, UUID staffId, WorkloadStatus status);

    List<WorkloadAssignment> findByServiceId(UUID tenantId, UUID serviceId);

    WorkloadAssignment create(CreateWorkloadCommand command);

    Optional<WorkloadAssignment> update(UUID tenantId, UUID id, UpdateWorkloadCommand command);

    boolean delete(UUID tenantId, UUID id);

    int sumEstimatedHoursByStaff(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);

    int sumActualHoursByStaff(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);
}
```

**Contract Tests:**
- `findById` returns empty for non-existent ID
- `findByStaffId` returns assignments by status
- `findByServiceId` returns assignments for service
- `create` inserts new record
- `update` modifies existing record with version check
- `delete` removes record
- `sumEstimatedHoursByStaff` aggregates hours
- `sumActualHoursByStaff` aggregates hours

---

### 2.7 ServiceAssignmentRepository

```java
package com.sanad.platform.crm.ownership.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceAssignmentRepository {

    record CreateServiceAssignmentCommand(
        UUID tenantId,
        UUID teamId,
        UUID serviceId,
        UUID createdBy
    ) {}

    record UpdateServiceAssignmentCommand(
        ServiceAssignmentStatus status,
        UUID updatedBy,
        long expectedVersion
    ) {}

    Optional<ServiceAssignment> findById(UUID tenantId, UUID id);

    List<ServiceAssignment> findByTeamId(UUID tenantId, UUID teamId);

    List<ServiceAssignment> findByServiceId(UUID tenantId, UUID serviceId);

    ServiceAssignment create(CreateServiceAssignmentCommand command);

    Optional<ServiceAssignment> update(UUID tenantId, UUID id, UpdateServiceAssignmentCommand command);

    boolean delete(UUID tenantId, UUID id);

    boolean existsByTeamAndService(UUID tenantId, UUID teamId, UUID serviceId, UUID excludeId);
}
```

**Contract Tests:**
- `findById` returns empty for non-existent ID
- `findByTeamId` returns assignments for team
- `findByServiceId` returns assignments for service
- `create` inserts new record
- `update` modifies existing record with version check
- `delete` removes record
- `existsByTeamAndService` detects duplicate assignments

---

## 3. Contract Summary

| Repository | Methods | Contract Tests |
|---|---|---|
| ShiftTemplateRepository | 5 | 6 |
| ShiftAssignmentRepository | 6 | 6 |
| AvailabilityRepository | 5 | 5 |
| SkillRepository | 7 | 7 |
| CapacityRepository | 5 | 5 |
| WorkloadRepository | 8 | 8 |
| ServiceAssignmentRepository | 7 | 7 |

**Total:** 7 repositories, 43 methods, 44 contract tests

---

## 4. Contract Decision

### Decision: **PASS**

All repository contracts are complete and follow CRM-007 patterns.

| Criterion | Status |
|---|---|
| Repository Interfaces | ✅ All 7 defined |
| Create Commands | ✅ All defined |
| Update Commands | ✅ All defined |
| CRUD Operations | ✅ All covered |
| Search Operations | ✅ All covered |
| Tenant Filtering | ✅ All methods accept tenantId |
| Pagination | ✅ Limit/offset supported |
| Contract Tests | ✅ All defined |

---

**Contract Date:** 2026-07-28
**Contractor:** Agent 1 — Architecture & Database Foundation
**Status:** PASS
