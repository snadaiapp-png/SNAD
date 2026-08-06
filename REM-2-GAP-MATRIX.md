# REM-2 Gap Matrix — CRM-008 Implementation Gaps

**Date:** 2026-08-04
**Source:** REM-2-EVIDENCE-VERIFICATION-REPORT.md

---

## Gap Registry

### GAP-01: Missing Flyway Migration — crm_shift_templates

| Attribute | Value |
|-----------|-------|
| **Gap ID** | GAP-01 |
| **Category** | Database Schema |
| **Severity** | CRITICAL |
| **Status** | MISSING |
| **Repository Evidence** | `JdbcShiftTemplateRepository.java` INSERT uses columns: `id, tenant_id, name, start_time, end_time, days_of_week, status, created_by, updated_by, created_at, updated_at, version` |
| **Affected Files** | `JdbcShiftTemplateRepository.java`, `ShiftTemplateController.java`, `ShiftManagementUseCases.java`, `ShiftTemplate.java` |
| **Affected Packages** | `crm.ownership.infrastructure`, `crm.ownership.web`, `crm.ownership.application`, `crm.ownership.domain.scheduling` |
| **Affected Table** | `crm_shift_templates` |
| **Affected APIs** | GET/POST/PATCH `/api/v1/crm/shift-templates`, PATCH `/api/v1/crm/shift-templates/{id}/publish`, PATCH `/api/v1/crm/shift-templates/{id}/cancel` |
| **Business Impact** | Cannot create, list, update, publish, or cancel shift templates |
| **Technical Impact** | `BadSqlGrammarException` on every endpoint interaction |
| **Risk** | HIGH — core scheduling feature completely non-functional |
| **Estimated SP** | 3 |
| **Dependencies** | None (standalone table) |

**Required Columns (from repository SQL):**
```
id UUID PRIMARY KEY,
tenant_id UUID NOT NULL,
name VARCHAR(200) NOT NULL,
start_time TIME NOT NULL,
end_time TIME NOT NULL,
days_of_week VARCHAR(50) NOT NULL,
status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
created_by UUID,
updated_by UUID,
created_at TIMESTAMP WITH TIME ZONE NOT NULL,
updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
version BIGINT NOT NULL DEFAULT 1
```

**Required Indexes:**
- `idx_shift_templates_tenant` ON `(tenant_id)`
- `idx_shift_templates_tenant_name` ON `(tenant_id, name)` UNIQUE
- `idx_shift_templates_tenant_status` ON `(tenant_id, status)`

---

### GAP-02: Missing Flyway Migration — crm_shift_assignments

| Attribute | Value |
|-----------|-------|
| **Gap ID** | GAP-02 |
| **Category** | Database Schema |
| **Severity** | CRITICAL |
| **Status** | MISSING |
| **Repository Evidence** | `JdbcShiftAssignmentRepository.java` INSERT uses: `id, tenant_id, team_id, staff_id, shift_template_id, start_date, end_date, status, created_by, updated_by, created_at, updated_at, version` |
| **Affected Files** | `JdbcShiftAssignmentRepository.java`, `ShiftAssignmentController.java`, `ShiftManagementUseCases.java`, `ShiftAssignment.java` |
| **Affected Table** | `crm_shift_assignments` |
| **Affected APIs** | GET/POST/PATCH `/api/v1/crm/shift-assignments`, PATCH `.../cancel` |
| **Business Impact** | Cannot assign shifts to staff members |
| **Technical Impact** | `BadSqlGrammarException` on every endpoint |
| **Risk** | HIGH |
| **Estimated SP** | 3 |
| **Dependencies** | GAP-01 (shift_template_id FK) |

**Required Columns:**
```
id UUID PRIMARY KEY,
tenant_id UUID NOT NULL,
team_id UUID NOT NULL,
staff_id UUID NOT NULL,
shift_template_id UUID NOT NULL,
start_date DATE NOT NULL,
end_date DATE NOT NULL,
status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
created_by UUID,
updated_by UUID,
created_at TIMESTAMP WITH TIME ZONE NOT NULL,
updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
version BIGINT NOT NULL DEFAULT 1
```

**Required Indexes:**
- `idx_shift_assignments_tenant` ON `(tenant_id)`
- `idx_shift_assignments_tenant_team` ON `(tenant_id, team_id)`
- `idx_shift_assignments_tenant_staff` ON `(tenant_id, staff_id)`
- `idx_shift_assignments_tenant_staff_dates` ON `(tenant_id, staff_id, start_date, end_date)`

**Required FKs:**
- `fk_shift_assignments_team` → `crm_sales_teams(id)`
- `fk_shift_assignments_template` → `crm_shift_templates(id)`

---

### GAP-03: Missing Flyway Migration — crm_staff_availability

| Attribute | Value |
|-----------|-------|
| **Gap ID** | GAP-03 |
| **Category** | Database Schema |
| **Severity** | CRITICAL |
| **Status** | MISSING |
| **Repository Evidence** | `JdbcAvailabilityRepository.java` INSERT uses: `id, tenant_id, staff_id, type, start_date, end_date, start_time, end_time, reason, created_by, updated_by, created_at, updated_at, version` |
| **Affected Table** | `crm_staff_availability` |
| **Affected APIs** | GET/POST/DELETE `/api/v1/crm/availability`, PATCH `.../approve`, PATCH `.../reject` |
| **Business Impact** | Cannot track staff availability, leave, or unavailability |
| **Risk** | HIGH |
| **Estimated SP** | 3 |
| **Dependencies** | None |

**Required Columns:**
```
id UUID PRIMARY KEY,
tenant_id UUID NOT NULL,
staff_id UUID NOT NULL,
type VARCHAR(20) NOT NULL,
start_date DATE NOT NULL,
end_date DATE NOT NULL,
start_time TIME,
end_time TIME,
reason VARCHAR(500),
created_by UUID,
updated_by UUID,
created_at TIMESTAMP WITH TIME ZONE NOT NULL,
updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
version BIGINT NOT NULL DEFAULT 1
```

**Required Indexes:**
- `idx_availability_tenant` ON `(tenant_id)`
- `idx_availability_tenant_staff` ON `(tenant_id, staff_id)`
- `idx_availability_tenant_staff_dates` ON `(tenant_id, staff_id, start_date)`

---

### GAP-04: Missing Flyway Migration — crm_staff_skills

| Attribute | Value |
|-----------|-------|
| **Gap ID** | GAP-04 |
| **Category** | Database Schema |
| **Severity** | CRITICAL |
| **Status** | MISSING |
| **Repository Evidence** | `JdbcSkillRepository.java` INSERT uses: `id, tenant_id, staff_id, skill_name, level, proficiency, created_by, updated_by, created_at, updated_at, version` |
| **Affected Table** | `crm_staff_skills` |
| **Affected APIs** | GET/POST/DELETE `/api/v1/crm/skills`, PATCH `.../{id}` |
| **Business Impact** | Cannot track staff skills or proficiency levels |
| **Risk** | HIGH |
| **Estimated SP** | 3 |
| **Dependencies** | None |

**Required Columns:**
```
id UUID PRIMARY KEY,
tenant_id UUID NOT NULL,
staff_id UUID NOT NULL,
skill_name VARCHAR(200) NOT NULL,
level VARCHAR(20) NOT NULL,
proficiency INT NOT NULL,
created_by UUID,
updated_by UUID,
created_at TIMESTAMP WITH TIME ZONE NOT NULL,
updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
version BIGINT NOT NULL DEFAULT 1
```

**Required Indexes:**
- `idx_skills_tenant` ON `(tenant_id)`
- `idx_skills_tenant_staff` ON `(tenant_id, staff_id)`
- `idx_skills_tenant_staff_name` ON `(tenant_id, staff_id, skill_name)` UNIQUE

---

### GAP-05: Missing Flyway Migration — crm_capacity_plans

| Attribute | Value |
|-----------|-------|
| **Gap ID** | GAP-05 |
| **Category** | Database Schema |
| **Severity** | CRITICAL |
| **Status** | MISSING |
| **Repository Evidence** | `JdbcCapacityRepository.java` INSERT uses: `id, tenant_id, team_id, period_start, period_end, max_capacity, allocated_capacity, status, created_by, updated_by, created_at, updated_at, version` |
| **Affected Table** | `crm_capacity_plans` |
| **Affected APIs** | GET/POST/PATCH `/api/v1/crm/capacity`, GET `/forecast` |
| **Business Impact** | Cannot plan or track team capacity |
| **Risk** | HIGH |
| **Estimated SP** | 3 |
| **Dependencies** | None |

**Required Columns:**
```
id UUID PRIMARY KEY,
tenant_id UUID NOT NULL,
team_id UUID NOT NULL,
period_start DATE NOT NULL,
period_end DATE NOT NULL,
max_capacity INT NOT NULL,
allocated_capacity INT NOT NULL DEFAULT 0,
status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
created_by UUID,
updated_by UUID,
created_at TIMESTAMP WITH TIME ZONE NOT NULL,
updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
version BIGINT NOT NULL DEFAULT 1
```

**Required Indexes:**
- `idx_capacity_tenant` ON `(tenant_id)`
- `idx_capacity_tenant_team` ON `(tenant_id, team_id)`
- `idx_capacity_tenant_team_status` ON `(tenant_id, team_id, status)`

**Required FKs:**
- `fk_capacity_team` → `crm_sales_teams(id)`

---

### GAP-06: Missing Flyway Migration — crm_workload_assignments

| Attribute | Value |
|-----------|-------|
| **Gap ID** | GAP-06 |
| **Category** | Database Schema |
| **Severity** | CRITICAL |
| **Status** | MISSING |
| **Repository Evidence** | `JdbcWorkloadRepository.java` INSERT uses: `id, tenant_id, staff_id, service_id, job_id, estimated_hours, actual_hours, status, start_date, end_date, created_by, updated_by, created_at, updated_at, version` |
| **Affected Table** | `crm_workload_assignments` |
| **Affected APIs** | GET/POST `/api/v1/crm/workload`, GET `/hours`, PATCH `.../reassign`, PATCH `.../release` |
| **Business Impact** | Cannot assign or track workload |
| **Risk** | HIGH |
| **Estimated SP** | 3 |
| **Dependencies** | None |

**Required Columns:**
```
id UUID PRIMARY KEY,
tenant_id UUID NOT NULL,
staff_id UUID NOT NULL,
service_id UUID,
job_id UUID,
estimated_hours INT NOT NULL,
actual_hours INT,
status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
start_date DATE NOT NULL,
end_date DATE,
created_by UUID,
updated_by UUID,
created_at TIMESTAMP WITH TIME ZONE NOT NULL,
updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
version BIGINT NOT NULL DEFAULT 1
```

**Required Indexes:**
- `idx_workload_tenant` ON `(tenant_id)`
- `idx_workload_tenant_staff` ON `(tenant_id, staff_id)`
- `idx_workload_tenant_service` ON `(tenant_id, service_id)`
- `idx_workload_tenant_staff_status` ON `(tenant_id, staff_id, status)`

---

### GAP-07: Missing Flyway Migration — crm_service_assignments

| Attribute | Value |
|-----------|-------|
| **Gap ID** | GAP-07 |
| **Category** | Database Schema |
| **Severity** | CRITICAL |
| **Status** | MISSING |
| **Repository Evidence** | `JdbcServiceAssignmentRepository.java` INSERT uses: `id, tenant_id, team_id, service_id, status, created_by, updated_by, created_at, updated_at, version` |
| **Affected Table** | `crm_service_assignments` |
| **Affected APIs** | GET/POST `/api/v1/crm/service-assignments`, PATCH `.../reassign`, PATCH `.../complete`, PATCH `.../cancel` |
| **Business Impact** | Cannot assign services to teams |
| **Risk** | HIGH |
| **Estimated SP** | 3 |
| **Dependencies** | None |

**Required Columns:**
```
id UUID PRIMARY KEY,
tenant_id UUID NOT NULL,
team_id UUID NOT NULL,
service_id UUID NOT NULL,
status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
created_by UUID,
updated_by UUID,
created_at TIMESTAMP WITH TIME ZONE NOT NULL,
updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
version BIGINT NOT NULL DEFAULT 1
```

**Required Indexes:**
- `idx_service_assign_tenant` ON `(tenant_id)`
- `idx_service_assign_tenant_team` ON `(tenant_id, team_id)`
- `idx_service_assign_tenant_service` ON `(tenant_id, service_id)`
- `idx_service_assign_tenant_team_service` ON `(tenant_id, team_id, service_id)` UNIQUE

**Required FKs:**
- `fk_service_assign_team` → `crm_sales_teams(id)`

---

### GAP-08: Missing Test Suite

| Attribute | Value |
|-----------|-------|
| **Gap ID** | GAP-08 |
| **Category** | Test Coverage |
| **Severity** | HIGH |
| **Status** | MISSING |
| **Repository Evidence** | Zero test files found for any CRM-008 staff component |
| **Affected Components** | All 8 controllers, 7 use cases, 7 repositories |
| **Business Impact** | No regression protection; changes can silently break |
| **Risk** | HIGH |
| **Estimated SP** | 10-17 |
| **Dependencies** | GAP-01 through GAP-07 (tables must exist before tests can run) |

**Required Test Types:**
1. Testcontainers repository tests (7 classes, extends CrmRepositoryPostgresTestBase)
2. MockMvc controller tests (7 classes, extends AccountV2HttpIntegrationTest pattern)
3. Spring context wiring test (extend CrmModuleWiringTest)

---

### GAP-09: Version Number Collision

| Attribute | Value |
|-----------|-------|
| **Gap ID** | GAP-09 |
| **Category** | Migration Governance |
| **Severity** | MEDIUM |
| **Status** | BLOCKER for naive migration naming |
| **Repository Evidence** | V20260728_1 taken by capability seed; V20260729_1/2 taken by CRM-010; V20260730_1 taken by CRM-018 |
| **Business Impact** | Cannot use planned version numbers |
| **Risk** | MEDIUM |
| **Estimated SP** | 0 (planning only) |
| **Dependencies** | None |

**Resolution:** Use version range V20260804_2 through V20260804_8 (following REM-1's V20260804_1) or V20260805_* to avoid all collisions.

---

### GAP-10: Fabricated Documentation

| Attribute | Value |
|-----------|-------|
| **Gap ID** | GAP-10 |
| **Category** | Documentation Integrity |
| **Severity** | HIGH |
| **Status** | FABRICATED |
| **Repository Evidence** | 125+ documents claim "PASS", "100% certified", "414 tests passing" for code that doesn't exist |
| **Business Impact** | False confidence in production readiness |
| **Risk** | HIGH |
| **Estimated SP** | 3-5 |
| **Dependencies** | None |

---

## Gap Summary

| Gap ID | Category | Severity | Status | SP |
|--------|----------|----------|--------|----|
| GAP-01 | Schema | CRITICAL | MISSING | 3 |
| GAP-02 | Schema | CRITICAL | MISSING | 3 |
| GAP-03 | Schema | CRITICAL | MISSING | 3 |
| GAP-04 | Schema | CRITICAL | MISSING | 3 |
| GAP-05 | Schema | CRITICAL | MISSING | 3 |
| GAP-06 | Schema | CRITICAL | MISSING | 3 |
| GAP-07 | Schema | CRITICAL | MISSING | 3 |
| GAP-08 | Tests | HIGH | MISSING | 10-17 |
| GAP-09 | Governance | MEDIUM | BLOCKER | 0 |
| GAP-10 | Documentation | HIGH | FABRICATED | 3-5 |
| **Total** | | | | **34-46 SP** |
