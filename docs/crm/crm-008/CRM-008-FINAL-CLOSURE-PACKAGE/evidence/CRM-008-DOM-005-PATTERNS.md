# CRM-008-DOM-005: Implementation Patterns

> **Agent:** Agent 2 — Domain Models & Repository Implementation
> **Task:** Cross-cutting Pattern Documentation
> **Date:** 2026-07-28
> **Status:** COMPLETE

---

## 1. Overview

This document records the implementation patterns used across CRM-008 domain layer, ensuring consistency with the existing SANAD codebase.

---

## 2. Domain Model Patterns

### Pattern: Java Record Entities
- **Usage**: All 7 domain entities
- **Example**: `ShiftTemplate.java`
- **Rationale**: Immutable, thread-safe, concise, matches existing SalesTeam pattern

### Pattern: Compact Constructor Validation
- **Usage**: All domain records
- **Example**: `if (tenantId == null) throw new IllegalArgumentException("tenantId required");`
- **Rationale**: Fail-fast validation at construction time

### Pattern: Enum Status Fields
- **Usage**: All entities with lifecycle states
- **Example**: `ShiftTemplateStatus`, `WorkloadStatus`
- **Rationale**: Type safety, IDE support, database constraint alignment

---

## 3. Repository Patterns

### Pattern: Inner Command Records
- **Usage**: All 7 repository interfaces
- **Example**: `CreateShiftTemplateCommand`, `UpdateShiftTemplateCommand`
- **Rationale**: Type-safe parameter objects, self-documenting

### Pattern: Optional Returns for Updates
- **Usage**: All update methods
- **Example**: `Optional<ShiftTemplate> update(UUID tenantId, UUID id, UpdateShiftTemplateCommand cmd)`
- **Rationale**: Indicates stale version conflicts without exceptions

### Pattern: Tenant-Scoped Queries
- **Usage**: All repository methods
- **Example**: `WHERE tenant_id=:tenantId`
- **Rationale**: Multi-tenant data isolation

### Pattern: Optimistic Locking
- **Usage**: All update operations
- **Example**: `AND version=:expectedVersion` in WHERE clause
- **Rationale**: Concurrent modification detection

---

## 4. JDBC Patterns

### Pattern: NamedParameterJdbcTemplate
- **Usage**: All 7 JDBC repositories
- **Rationale**: SQL injection prevention, readable parameter binding

### Pattern: @Repository + @Transactional
- **Usage**: All JDBC implementations
- **Rationale**: Spring exception translation, transaction management

### Pattern: RowMapper Static Methods
- **Usage**: OwnershipJdbcSupport class
- **Rationale**: Centralized mapping logic, reusable across repositories

### Pattern: EmptyResultDataAccessException Handling
- **Usage**: All findById methods
- **Rationale**: Clean Optional.empty() return for missing entities

---

## 5. Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Domain Record | PascalCase, singular | ShiftTemplate |
| Enum | PascalCase, singular | ShiftTemplateStatus |
| Repository Interface | PascalCase, singular | ShiftTemplateRepository |
| JDBC Implementation | Jdbc prefix + Interface name | JdbcShiftTemplateRepository |
| Create Command | Create + Entity + Command | CreateShiftTemplateCommand |
| Update Command | Update + Entity + Command | UpdateShiftTemplateCommand |
| Table Name | snake_case, plural, prefix | crm_shift_templates |

---

## 6. Package Structure

```
com.sanad.platform.crm.ownership
├── domain/
│   ├── scheduling/    (ShiftTemplate, ShiftAssignment)
│   ├── availability/  (StaffAvailability)
│   ├── skills/        (StaffSkill)
│   ├── capacity/      (CapacityPlan)
│   ├── workload/      (WorkloadAssignment)
│   └── service/       (ServiceAssignment)
└── infrastructure/
    ├── OwnershipJdbcSupport.java (RowMappers)
    └── Jdbc*.java               (Repository implementations)
```

---

**Certification Date:** 2026-07-28
**Agent 2 Pattern Documentation Status:** COMPLETE
