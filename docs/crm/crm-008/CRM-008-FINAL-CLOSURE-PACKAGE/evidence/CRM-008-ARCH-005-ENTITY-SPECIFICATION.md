# CRM-008 Entity Specification — Agent 1

> **Agent:** Agent 1 — Architecture & Database Foundation
> **Command:** CRM-008-EXECUTION-001
> **Task:** 5 — Entity Specification
> **Date:** 2026-07-28
> **Status:** PASS

---

## 1. Specification Scope

Generate complete specifications for all CRM-008 Team Management entities.

---

## 2. Entity Specifications

### 2.1 ShiftTemplate

**Package:** `com.sanad.platform.crm.ownership.domain.scheduling`

```java
public record ShiftTemplate(
    UUID id,
    UUID tenantId,
    String name,
    LocalTime startTime,
    LocalTime endTime,
    List<DayOfWeek> daysOfWeek,
    ShiftTemplateStatus status,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
)
```

**Validation Rules:**
- `name` — Required, 1-100 characters
- `startTime` — Required, must be before endTime
- `endTime` — Required
- `daysOfWeek` — Required, at least one day

**Status Enum:**
```java
public enum ShiftTemplateStatus {
    ACTIVE, INACTIVE
}
```

**Repository Interface:**
```java
public interface ShiftTemplateRepository {
    record CreateShiftTemplateCommand(
        UUID tenantId, String name, LocalTime startTime,
        LocalTime endTime, List<DayOfWeek> daysOfWeek, UUID createdBy
    ) {}

    record UpdateShiftTemplateCommand(
        String name, LocalTime startTime, LocalTime endTime,
        List<DayOfWeek> daysOfWeek, ShiftTemplateStatus status,
        UUID updatedBy, long expectedVersion
    ) {}

    Optional<ShiftTemplate> findById(UUID tenantId, UUID id);
    List<ShiftTemplate> findAll(UUID tenantId, int limit, int offset);
    ShiftTemplate create(CreateShiftTemplateCommand command);
    Optional<ShiftTemplate> update(UUID tenantId, UUID id, UpdateShiftTemplateCommand command);
    boolean existsByName(UUID tenantId, String name, UUID excludeId);
}
```

---

### 2.2 ShiftAssignment

**Package:** `com.sanad.platform.crm.ownership.domain.scheduling`

```java
public record ShiftAssignment(
    UUID id,
    UUID tenantId,
    UUID teamId,
    UUID staffId,
    UUID shiftTemplateId,
    LocalDate startDate,
    LocalDate endDate,
    ShiftAssignmentStatus status,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
)
```

**Validation Rules:**
- `teamId` — Required
- `staffId` — Required
- `shiftTemplateId` — Required
- `startDate` — Required
- `endDate` — Required, must be after startDate

**Status Enum:**
```java
public enum ShiftAssignmentStatus {
    SCHEDULED, ACTIVE, COMPLETED, CANCELLED
}
```

**Repository Interface:**
```java
public interface ShiftAssignmentRepository {
    record CreateShiftAssignmentCommand(
        UUID tenantId, UUID teamId, UUID staffId, UUID shiftTemplateId,
        LocalDate startDate, LocalDate endDate, UUID createdBy
    ) {}

    record UpdateShiftAssignmentCommand(
        UUID shiftTemplateId, LocalDate startDate, LocalDate endDate,
        ShiftAssignmentStatus status, UUID updatedBy, long expectedVersion
    ) {}

    Optional<ShiftAssignment> findById(UUID tenantId, UUID id);
    List<ShiftAssignment> findByTeamId(UUID tenantId, UUID teamId, int limit, int offset);
    List<ShiftAssignment> findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);
    ShiftAssignment create(CreateShiftAssignmentCommand command);
    Optional<ShiftAssignment> update(UUID tenantId, UUID id, UpdateShiftAssignmentCommand command);
    boolean hasOverlap(UUID tenantId, UUID staffId, LocalDate startDate, LocalDate endDate, UUID excludeId);
}
```

---

### 2.3 StaffAvailability

**Package:** `com.sanad.platform.crm.ownership.domain.availability`

```java
public record StaffAvailability(
    UUID id,
    UUID tenantId,
    UUID staffId,
    AvailabilityType type,
    LocalDate startDate,
    LocalDate endDate,
    LocalTime startTime,
    LocalTime endTime,
    String reason,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
)
```

**Validation Rules:**
- `staffId` — Required
- `type` — Required
- `startDate` — Required
- `endDate` — Required, must be after startDate

**Status Enum:**
```java
public enum AvailabilityType {
    AVAILABLE, UNAVAILABLE, ON_LEAVE
}
```

**Repository Interface:**
```java
public interface AvailabilityRepository {
    record CreateAvailabilityCommand(
        UUID tenantId, UUID staffId, AvailabilityType type,
        LocalDate startDate, LocalDate endDate,
        LocalTime startTime, LocalTime endTime,
        String reason, UUID createdBy
    ) {}

    record UpdateAvailabilityCommand(
        AvailabilityType type, LocalDate startDate, LocalDate endDate,
        LocalTime startTime, LocalTime endTime, String reason,
        UUID updatedBy, long expectedVersion
    ) {}

    Optional<StaffAvailability> findById(UUID tenantId, UUID id);
    List<StaffAvailability> findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to);
    StaffAvailability create(CreateAvailabilityCommand command);
    Optional<StaffAvailability> update(UUID tenantId, UUID id, UpdateAvailabilityCommand command);
    boolean delete(UUID tenantId, UUID id);
}
```

---

### 2.4 StaffSkill

**Package:** `com.sanad.platform.crm.ownership.domain.skills`

```java
public record StaffSkill(
    UUID id,
    UUID tenantId,
    UUID staffId,
    String skillName,
    SkillLevel level,
    int proficiency,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
)
```

**Validation Rules:**
- `staffId` — Required
- `skillName` — Required, 1-100 characters
- `level` — Required
- `proficiency` — Required, 1-100

**Status Enum:**
```java
public enum SkillLevel {
    BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
}
```

**Repository Interface:**
```java
public interface SkillRepository {
    record CreateSkillCommand(
        UUID tenantId, UUID staffId, String skillName,
        SkillLevel level, int proficiency, UUID createdBy
    ) {}

    record UpdateSkillCommand(
        SkillLevel level, int proficiency, UUID updatedBy, long expectedVersion
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

---

### 2.5 CapacityPlan

**Package:** `com.sanad.platform.crm.ownership.domain.capacity`

```java
public record CapacityPlan(
    UUID id,
    UUID tenantId,
    UUID teamId,
    LocalDate periodStart,
    LocalDate periodEnd,
    int maxCapacity,
    int allocatedCapacity,
    CapacityStatus status,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
)
```

**Validation Rules:**
- `teamId` — Required
- `periodStart` — Required
- `periodEnd` — Required, must be after periodStart
- `maxCapacity` — Required, must be positive
- `allocatedCapacity` — Must be >= 0

**Computed Properties:**
- `remainingCapacity()` — maxCapacity - allocatedCapacity
- `utilizationPercentage()` — (allocatedCapacity / maxCapacity) * 100

**Status Enum:**
```java
public enum CapacityStatus {
    DRAFT, ACTIVE, COMPLETED
}
```

**Repository Interface:**
```java
public interface CapacityRepository {
    record CreateCapacityPlanCommand(
        UUID tenantId, UUID teamId, LocalDate periodStart,
        LocalDate periodEnd, int maxCapacity, UUID createdBy
    ) {}

    record UpdateCapacityPlanCommand(
        Integer maxCapacity, Integer allocatedCapacity,
        CapacityStatus status, UUID updatedBy, long expectedVersion
    ) {}

    Optional<CapacityPlan> findById(UUID tenantId, UUID id);
    List<CapacityPlan> findByTeamId(UUID tenantId, UUID teamId);
    Optional<CapacityPlan> findActiveByTeamAndPeriod(UUID tenantId, UUID teamId, LocalDate date);
    CapacityPlan create(CreateCapacityPlanCommand command);
    Optional<CapacityPlan> update(UUID tenantId, UUID id, UpdateCapacityPlanCommand command);
}
```

---

### 2.6 WorkloadAssignment

**Package:** `com.sanad.platform.crm.ownership.domain.workload`

```java
public record WorkloadAssignment(
    UUID id,
    UUID tenantId,
    UUID staffId,
    UUID serviceId,
    UUID jobId,
    int estimatedHours,
    Integer actualHours,
    WorkloadStatus status,
    LocalDate startDate,
    LocalDate endDate,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
)
```

**Validation Rules:**
- `staffId` — Required
- `estimatedHours` — Required, must be positive
- `startDate` — Required

**Status Enum:**
```java
public enum WorkloadStatus {
    PLANNED, IN_PROGRESS, COMPLETED, CANCELLED
}
```

**Repository Interface:**
```java
public interface WorkloadRepository {
    record CreateWorkloadCommand(
        UUID tenantId, UUID staffId, UUID serviceId, UUID jobId,
        int estimatedHours, LocalDate startDate, LocalDate endDate, UUID createdBy
    ) {}

    record UpdateWorkloadCommand(
        Integer actualHours, WorkloadStatus status, LocalDate endDate,
        UUID updatedBy, long expectedVersion
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

---

### 2.7 ServiceAssignment

**Package:** `com.sanad.platform.crm.ownership.domain.service`

```java
public record ServiceAssignment(
    UUID id,
    UUID tenantId,
    UUID teamId,
    UUID serviceId,
    ServiceAssignmentStatus status,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
)
```

**Validation Rules:**
- `teamId` — Required
- `serviceId` — Required

**Status Enum:**
```java
public enum ServiceAssignmentStatus {
    ACTIVE, INACTIVE
}
```

**Repository Interface:**
```java
public interface ServiceAssignmentRepository {
    record CreateServiceAssignmentCommand(
        UUID tenantId, UUID teamId, UUID serviceId, UUID createdBy
    ) {}

    record UpdateServiceAssignmentCommand(
        ServiceAssignmentStatus status, UUID updatedBy, long expectedVersion
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

---

## 3. Entity Summary

| Entity | Package | Fields | Status |
|---|---|---|---|
| ShiftTemplate | scheduling | 12 | ✅ Specified |
| ShiftAssignment | scheduling | 13 | ✅ Specified |
| StaffAvailability | availability | 13 | ✅ Specified |
| StaffSkill | skills | 11 | ✅ Specified |
| CapacityPlan | capacity | 13 | ✅ Specified |
| WorkloadAssignment | workload | 14 | ✅ Specified |
| ServiceAssignment | service | 10 | ✅ Specified |

**Total:** 7 entities

---

## 4. Specification Decision

### Decision: **PASS**

All entities are fully specified with validation rules, enums, and repository interfaces.

| Criterion | Status |
|---|---|
| Entity Definitions | ✅ All 7 entities |
| Validation Rules | ✅ All defined |
| Enums | ✅ All defined |
| Repository Interfaces | ✅ All defined |
| Command Records | ✅ All defined |
| Naming Standards | ✅ Follows CRM-007 conventions |

---

**Specification Date:** 2026-07-28
**Specifier:** Agent 1 — Architecture & Database Foundation
**Status:** PASS
