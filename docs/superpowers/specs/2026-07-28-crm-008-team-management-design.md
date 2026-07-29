# CRM-008 Team Management Design Spec

> **Date:** 2026-07-28
> **Status:** APPROVED
> **Baseline SHA:** 4cedf631a3e61f39039615d93cd03c3111213eb9
> **Module:** CRM-008 Team Management

---

## 1. Overview

CRM-008 extends the existing `crm/ownership` module to implement comprehensive team management capabilities including scheduling, availability tracking, skills management, capacity planning, workload allocation, and team analytics.

### 1.1 Objectives

- Implement shift scheduling and assignment
- Track staff availability and exceptions
- Manage skills matrix and proficiency levels
- Plan and monitor team capacity
- Allocate and track workload across services
- Provide team dashboard and KPI metrics
- Maintain multi-tenant isolation and RBAC

### 1.2 Scope

**In Scope:**
- Teams (extends existing SalesTeam)
- Team Members (extends existing TeamMembership)
- Team Assignment (extends existing)
- Shift Scheduling (new)
- Availability Calendar (new)
- Skills Matrix (new)
- Capacity Planning (new)
- Workload Allocation (new)
- Service Assignment (new)
- Team Dashboard (new)
- Team Activity Timeline (extends existing)
- Team KPIs (new)

**Out of Scope:**
- Payroll
- HR Administration
- Accounting
- ERP Financial Posting
- Tax Engine
- Recruitment
- Employee Contracts
- Attendance Hardware Integration

---

## 2. Architecture

### 2.1 Module Structure

Extend `crm/ownership` with sub-packages:

```
crm/ownership/
├── domain/
│   ├── SalesTeam.java (existing)
│   ├── TeamMembership.java (existing)
│   ├── TeamStatus.java (existing)
│   ├── MembershipRole.java (existing)
│   ├── MembershipStatus.java (existing)
│   ├── scheduling/
│   │   ├── ShiftTemplate.java
│   │   ├── ShiftTemplateStatus.java
│   │   ├── ShiftAssignment.java
│   │   ├── ShiftAssignmentStatus.java
│   │   ├── ShiftTemplateRepository.java
│   │   └── ShiftAssignmentRepository.java
│   ├── availability/
│   │   ├── StaffAvailability.java
│   │   ├── AvailabilityType.java
│   │   └── AvailabilityRepository.java
│   ├── skills/
│   │   ├── StaffSkill.java
│   │   ├── SkillLevel.java
│   │   └── SkillRepository.java
│   ├── capacity/
│   │   ├── CapacityPlan.java
│   │   ├── CapacityStatus.java
│   │   └── CapacityRepository.java
│   └── workload/
│       ├── WorkloadAssignment.java
│       ├── WorkloadStatus.java
│       └── WorkloadRepository.java
├── application/
│   ├── SalesTeamUseCases.java (existing)
│   ├── SchedulingUseCases.java (new)
│   ├── AvailabilityUseCases.java (new)
│   ├── SkillUseCases.java (new)
│   ├── CapacityUseCases.java (new)
│   ├── WorkloadUseCases.java (new)
│   └── OwnershipModuleConfiguration.java (extend)
├── infrastructure/
│   ├── JdbcSalesTeamRepository.java (existing)
│   ├── OwnershipJdbcSupport.java (existing)
│   ├── JdbcShiftTemplateRepository.java (new)
│   ├── JdbcShiftAssignmentRepository.java (new)
│   ├── JdbcAvailabilityRepository.java (new)
│   ├── JdbcSkillRepository.java (new)
│   ├── JdbcCapacityRepository.java (new)
│   └── JdbcWorkloadRepository.java (new)
└── web/
    ├── SalesTeamController.java (existing)
    ├── ShiftController.java (new)
    ├── AvailabilityController.java (new)
    ├── SkillController.java (new)
    ├── CapacityController.java (new)
    ├── WorkloadController.java (new)
    └── TeamDashboardController.java (new)
```

### 2.2 Design Patterns

Follow existing CRM patterns:
- **Domain records** (not JPA entities) with validation
- **Repository interfaces** as ports with inner records
- **JDBC infrastructure** using `NamedParameterJdbcTemplate`
- **UseCase classes** as thin `@Transactional` facades
- **ModuleConfiguration** for Spring bean wiring
- **Controllers** with `@RequireCapability` for RBAC

---

## 3. Data Model

### 3.1 ShiftTemplate

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

**Enum:**
```java
public enum ShiftTemplateStatus {
    ACTIVE, INACTIVE
}
```

**Database Table:** `crm_shift_templates`
- `id UUID NOT NULL`
- `tenant_id UUID NOT NULL`
- `name VARCHAR(100) NOT NULL`
- `start_time TIME NOT NULL`
- `end_time TIME NOT NULL`
- `days_of_week VARCHAR(50) NOT NULL` -- comma-separated day numbers
- `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`
- `created_by UUID NOT NULL`
- `updated_by UUID NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `version BIGINT NOT NULL DEFAULT 0`

### 3.2 ShiftAssignment

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

**Enum:**
```java
public enum ShiftAssignmentStatus {
    SCHEDULED, ACTIVE, COMPLETED, CANCELLED
}
```

**Database Table:** `crm_shift_assignments`
- `id UUID NOT NULL`
- `tenant_id UUID NOT NULL`
- `team_id UUID NOT NULL`
- `staff_id UUID NOT NULL`
- `shift_template_id UUID NOT NULL`
- `start_date DATE NOT NULL`
- `end_date DATE NOT NULL`
- `status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'`
- `created_by UUID NOT NULL`
- `updated_by UUID NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `version BIGINT NOT NULL DEFAULT 0`

### 3.3 StaffAvailability

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

**Enum:**
```java
public enum AvailabilityType {
    AVAILABLE, UNAVAILABLE, ON_LEAVE
}
```

**Database Table:** `crm_staff_availability`
- `id UUID NOT NULL`
- `tenant_id UUID NOT NULL`
- `staff_id UUID NOT NULL`
- `type VARCHAR(20) NOT NULL`
- `start_date DATE NOT NULL`
- `end_date DATE NOT NULL`
- `start_time TIME`
- `end_time TIME`
- `reason VARCHAR(500)`
- `created_by UUID NOT NULL`
- `updated_by UUID NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `version BIGINT NOT NULL DEFAULT 0`

### 3.4 StaffSkill

```java
public record StaffSkill(
    UUID id,
    UUID tenantId,
    UUID staffId,
    String skillName,
    SkillLevel level,
    Integer proficiency,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
)
```

**Enum:**
```java
public enum SkillLevel {
    BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
}
```

**Database Table:** `crm_staff_skills`
- `id UUID NOT NULL`
- `tenant_id UUID NOT NULL`
- `staff_id UUID NOT NULL`
- `skill_name VARCHAR(100) NOT NULL`
- `level VARCHAR(20) NOT NULL`
- `proficiency INTEGER NOT NULL CHECK (proficiency BETWEEN 1 AND 100)`
- `created_by UUID NOT NULL`
- `updated_by UUID NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `version BIGINT NOT NULL DEFAULT 0`

### 3.5 CapacityPlan

```java
public record CapacityPlan(
    UUID id,
    UUID tenantId,
    UUID teamId,
    LocalDate periodStart,
    LocalDate periodEnd,
    Integer maxCapacity,
    Integer allocatedCapacity,
    CapacityStatus status,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    long version
)
```

**Enum:**
```java
public enum CapacityStatus {
    DRAFT, ACTIVE, COMPLETED
}
```

**Database Table:** `crm_capacity_plans`
- `id UUID NOT NULL`
- `tenant_id UUID NOT NULL`
- `team_id UUID NOT NULL`
- `period_start DATE NOT NULL`
- `period_end DATE NOT NULL`
- `max_capacity INTEGER NOT NULL`
- `allocated_capacity INTEGER NOT NULL DEFAULT 0`
- `status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'`
- `created_by UUID NOT NULL`
- `updated_by UUID NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `version BIGINT NOT NULL DEFAULT 0`

### 3.6 WorkloadAssignment

```java
public record WorkloadAssignment(
    UUID id,
    UUID tenantId,
    UUID staffId,
    UUID serviceId,
    UUID jobId,
    Integer estimatedHours,
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

**Enum:**
```java
public enum WorkloadStatus {
    PLANNED, IN_PROGRESS, COMPLETED, CANCELLED
}
```

**Database Table:** `crm_workload_assignments`
- `id UUID NOT NULL`
- `tenant_id UUID NOT NULL`
- `staff_id UUID NOT NULL`
- `service_id UUID`
- `job_id UUID`
- `estimated_hours INTEGER NOT NULL`
- `actual_hours INTEGER`
- `status VARCHAR(20) NOT NULL DEFAULT 'PLANNED'`
- `start_date DATE NOT NULL`
- `end_date DATE`
- `created_by UUID NOT NULL`
- `updated_by UUID NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `version BIGINT NOT NULL DEFAULT 0`

### 3.7 ServiceAssignment

Links teams to services they can provide:

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

**Enum:**
```java
public enum ServiceAssignmentStatus {
    ACTIVE, INACTIVE
}
```

**Database Table:** `crm_service_assignments`
- `id UUID NOT NULL`
- `tenant_id UUID NOT NULL`
- `team_id UUID NOT NULL`
- `service_id UUID NOT NULL`
- `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`
- `created_by UUID NOT NULL`
- `updated_by UUID NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `version BIGINT NOT NULL DEFAULT 0`
- `CONSTRAINT uk_crm_service_assignment UNIQUE (tenant_id, team_id, service_id)`

---

## 4. API Design

### 4.1 Shift Templates

```
POST   /api/v1/crm/teams/{teamId}/shift-templates
GET    /api/v1/crm/teams/{teamId}/shift-templates
GET    /api/v1/crm/teams/{teamId}/shift-templates/{id}
PATCH  /api/v1/crm/teams/{teamId}/shift-templates/{id}
DELETE /api/v1/crm/teams/{teamId}/shift-templates/{id}
```

### 4.2 Shift Assignments

```
POST   /api/v1/crm/teams/{teamId}/shift-assignments
GET    /api/v1/crm/teams/{teamId}/shift-assignments
GET    /api/v1/crm/teams/{teamId}/shift-assignments/{id}
PATCH  /api/v1/crm/teams/{teamId}/shift-assignments/{id}
DELETE /api/v1/crm/teams/{teamId}/shift-assignments/{id}
```

### 4.3 Staff Availability

```
POST   /api/v1/crm/teams/{teamId}/availability
GET    /api/v1/crm/teams/{teamId}/availability
GET    /api/v1/crm/teams/{teamId}/availability/{id}
PATCH  /api/v1/crm/teams/{teamId}/availability/{id}
DELETE /api/v1/crm/teams/{teamId}/availability/{id}
GET    /api/v1/crm/teams/{teamId}/availability/calendar
```

### 4.4 Staff Skills

```
POST   /api/v1/crm/teams/{teamId}/skills
GET    /api/v1/crm/teams/{teamId}/skills
GET    /api/v1/crm/teams/{teamId}/skills/{id}
PATCH  /api/v1/crm/teams/{teamId}/skills/{id}
DELETE /api/v1/crm/teams/{teamId}/skills/{id}
GET    /api/v1/crm/teams/{teamId}/skills/matrix
```

### 4.5 Capacity Plans

```
POST   /api/v1/crm/teams/{teamId}/capacity
GET    /api/v1/crm/teams/{teamId}/capacity
GET    /api/v1/crm/teams/{teamId}/capacity/{id}
PATCH  /api/v1/crm/teams/{teamId}/capacity/{id}
```

### 4.6 Workload Assignments

```
POST   /api/v1/crm/teams/{teamId}/workload
GET    /api/v1/crm/teams/{teamId}/workload
GET    /api/v1/crm/teams/{teamId}/workload/{id}
PATCH  /api/v1/crm/teams/{teamId}/workload/{id}
DELETE /api/v1/crm/teams/{teamId}/workload/{id}
GET    /api/v1/crm/teams/{teamId}/workload/distribution
```

### 4.7 Service Assignments

```
POST   /api/v1/crm/teams/{teamId}/services
GET    /api/v1/crm/teams/{teamId}/services
GET    /api/v1/crm/teams/{teamId}/services/{id}
PATCH  /api/v1/crm/teams/{teamId}/services/{id}
DELETE /api/v1/crm/teams/{teamId}/services/{id}
```

### 4.8 Dashboard and KPIs

```
GET    /api/v1/crm/teams/{teamId}/dashboard
GET    /api/v1/crm/teams/{teamId}/kpis
GET    /api/v1/crm/teams/{teamId}/activity-timeline
```

### 4.8 RBAC Permissions

```
CRM.TEAM.READ           - Read team information
CRM.TEAM.WRITE          - Create/update teams
CRM.TEAM.ADMIN          - Manage teams (delete, archive)
CRM.TEAM.SCHEDULE.READ  - Read scheduling
CRM.TEAM.SCHEDULE.WRITE - Manage scheduling
CRM.TEAM.SKILL.READ     - Read skills
CRM.TEAM.SKILL.WRITE    - Manage skills
CRM.TEAM.CAPACITY.READ  - Read capacity
CRM.TEAM.CAPACITY.WRITE - Manage capacity
```

---

## 5. Implementation Phases

### Sprint 1: Infrastructure + Scheduling + Availability (2 weeks)

**Goal:** Establish core infrastructure, implement scheduling and availability management

**Tasks:**
1. Create database migration scripts (ShiftTemplate, ShiftAssignment, StaffAvailability tables)
2. Implement domain models (ShiftTemplate, ShiftAssignment, StaffAvailability records)
3. Implement Repository interfaces and JDBC implementations
4. Implement UseCases (SchedulingUseCases, AvailabilityUseCases)
5. Implement Controllers (ShiftController, AvailabilityController)
6. Add RBAC permissions (CRM.TEAM.SCHEDULE.READ/WRITE)
7. Write unit tests and integration tests
8. Write API documentation

**Deliverables:**
- 6 new database tables
- 10+ REST API endpoints
- Complete test coverage

### Sprint 2: Skills + Capacity + Workload (2 weeks)

**Goal:** Implement skills matrix, capacity planning, and workload allocation

**Tasks:**
1. Create database migration scripts (StaffSkill, CapacityPlan, WorkloadAssignment tables)
2. Implement domain models
3. Implement Repository and JDBC implementations
4. Implement UseCases (SkillUseCases, CapacityUseCases, WorkloadUseCases)
5. Implement Controllers (SkillController, CapacityController, WorkloadController)
6. Add RBAC permissions
7. Write tests
8. Write documentation

**Deliverables:**
- 3 new database tables
- 15+ REST API endpoints
- Skills matrix and capacity report APIs

### Sprint 3: Dashboard + KPIs + Service Assignment (2 weeks)

**Goal:** Implement team dashboard, KPI metrics, and service assignment

**Tasks:**
1. Create database migration script for ServiceAssignment table
2. Implement ServiceAssignment domain model and repository
3. Implement ServiceAssignmentUseCases
4. Implement ServiceAssignmentController
5. Implement TeamDashboard aggregate queries
6. Implement TeamKPI calculation logic
7. Extend TeamActivityTimeline
8. Implement TeamDashboardController
9. Write performance tests
10. Write user documentation
11. Final validation and certification

**Deliverables:**
- ServiceAssignment table and API
- Dashboard API
- KPI metrics API
- Complete documentation

---

## 6. Integration Points

### 6.1 Existing CRM Core

- **SalesTeam** - Extend with scheduling, skills, capacity attributes
- **TeamMembership** - Link to availability, skills, workload
- **AuditPort** - Record all mutations
- **TimelineEventPort** - Generate timeline events
- **TenantContextPort** - Multi-tenant isolation

### 6.2 Identity Service

- User authentication and authorization
- JWT token with tenant_id and user_id claims

### 6.3 Organization Service

- Organizational structure (departments, positions)
- User profiles

### 6.4 Notification Service

- Shift assignment notifications
- Availability change notifications
- Capacity alerts

---

## 7. Testing Strategy

### 7.1 Unit Tests

- Domain record validation
- UseCase logic
- Repository query building

### 7.2 Integration Tests

- JDBC repository operations
- Tenant isolation verification
- RBAC permission enforcement

### 7.3 Contract Tests

- API request/response validation
- Error envelope validation
- Pagination validation

### 7.4 E2E Tests

- Complete workflow scenarios
- Cross-module integration

---

## 8. Success Criteria

PASS when:
- Team lifecycle complete
- Staff assignment operational
- Tenant isolation preserved
- API contracts validated
- Integration tests pass
- Documentation complete

---

## 9. Deferred Scope

Items explicitly out of scope for CRM-008:
- Payroll integration
- HR administration
- Accounting integration
- ERP financial posting
- Tax engine
- Recruitment
- Employee contracts
- Attendance hardware integration

---

## 10. References

- CRM-007 Final Closure Certificate
- SANAD Platform Architecture Guide
- CRM Ownership Module (existing implementation)
- Flyway Migration Guide
- OpenAPI Specification
