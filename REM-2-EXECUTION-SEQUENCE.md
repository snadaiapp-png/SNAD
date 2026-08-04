# REM-2 Execution Sequence — CRM-008 Implementation Order

**Date:** 2026-08-04
**Principle:** Only genuine missing work. Excludes already-implemented code.

---

## Execution Overview

| Wave | Stories | Parallel? | Gate |
|------|---------|-----------|------|
| Wave 1 | S1, S3, S4, S5, S6, S7, S11 | YES (7 parallel) | CrmModuleWiringTest passes |
| Wave 2 | S2, S8, S10 | YES (3 parallel, after Wave 1) | All migrations apply + repo tests pass |
| Wave 3 | S9 | Sequential (after S8) | Controller tests pass |

---

## Wave 1: Independent Migrations + Documentation (Parallel)

### Task 1.1: V20260804_2__create_crm_shift_templates.sql
- **File:** `apps/sanad-platform/src/main/resources/db/migration/V20260804_2__create_crm_shift_templates.sql`
- **Source of truth:** `JdbcShiftTemplateRepository.java` (INSERT columns) + `OwnershipJdbcSupport.shiftTemplateMapper()` (SELECT columns)
- **DDL:** CREATE TABLE with 12 columns, 3 indexes, 1 UNIQUE constraint
- **Verify:** `mvn test -Dtest=CrmModuleWiringTest` passes

### Task 1.2: V20260804_4__create_crm_staff_availability.sql
- **File:** `apps/sanad-platform/src/main/resources/db/migration/V20260804_4__create_crm_staff_availability.sql`
- **Source of truth:** `JdbcAvailabilityRepository.java` + `OwnershipJdbcSupport.staffAvailabilityMapper()`
- **DDL:** CREATE TABLE with 14 columns, 3 indexes
- **Note:** start_time, end_time, reason are nullable

### Task 1.3: V20260804_5__create_crm_staff_skills.sql
- **File:** `apps/sanad-platform/src/main/resources/db/migration/V20260804_5__create_crm_staff_skills.sql`
- **Source of truth:** `JdbcSkillRepository.java` + `OwnershipJdbcSupport.staffSkillMapper()`
- **DDL:** CREATE TABLE with 11 columns, 3 indexes, 1 UNIQUE constraint
- **UNIQUE:** (tenant_id, staff_id, skill_name)

### Task 1.4: V20260804_6__create_crm_capacity_plans.sql
- **File:** `apps/sanad-platform/src/main/resources/db/migration/V20260804_6__create_crm_capacity_plans.sql`
- **Source of truth:** `JdbcCapacityRepository.java` + `OwnershipJdbcSupport.capacityPlanMapper()`
- **DDL:** CREATE TABLE with 13 columns, 3 indexes, 1 FK → crm_sales_teams

### Task 1.5: V20260804_7__create_crm_workload_assignments.sql
- **File:** `apps/sanad-platform/src/main/resources/db/migration/V20260804_7__create_crm_workload_assignments.sql`
- **Source of truth:** `JdbcWorkloadRepository.java` + `OwnershipJdbcSupport.workloadAssignmentMapper()`
- **DDL:** CREATE TABLE with 15 columns, 4 indexes
- **Note:** service_id, job_id, actual_hours, end_date are nullable

### Task 1.6: V20260804_8__create_crm_service_assignments.sql
- **File:** `apps/sanad-platform/src/main/resources/db/migration/V20260804_8__create_crm_service_assignments.sql`
- **Source of truth:** `JdbcServiceAssignmentRepository.java` + `OwnershipJdbcSupport.serviceAssignmentMapper()`
- **DDL:** CREATE TABLE with 10 columns, 4 indexes, 1 FK → crm_sales_teams, 1 UNIQUE constraint

### Task 1.7: Documentation Reconciliation
- Add DEPRECATED header to fabricated closure documents
- Update CRM-008-ARCH-003-MIGRATIONS.md

### Wave 1 Gate
```bash
cd apps/sanad-platform
mvn test -Dtest=CrmModuleWiringTest
# Expected: 12/12 PASS (V20260804_2 through V20260804_8 all apply on H2)
```

---

## Wave 2: Sequential Migration + Repository Tests (After Wave 1)

### Task 2.1: V20260804_3__create_crm_shift_assignments.sql
- **File:** `apps/sanad-platform/src/main/resources/db/migration/V20260804_3__create_crm_shift_assignments.sql`
- **Source of truth:** `JdbcShiftAssignmentRepository.java` + `OwnershipJdbcSupport.shiftAssignmentMapper()`
- **DDL:** CREATE TABLE with 13 columns, 4 indexes, 2 FKs (crm_sales_teams, crm_shift_templates)
- **Depends on:** V20260804_2 (shift_templates must exist first)

### Task 2.2: Testcontainers Repository Tests (7 classes)
- **Files:** `src/test/java/com/sanad/platform/crm/ownership/infrastructure/Jdbc{ShiftTemplate,ShiftAssignment,Availability,Skill,Capacity,Workload,ServiceAssignment}RepositoryPostgresTest.java`
- **Pattern:** Extend `CrmRepositoryPostgresTestBase`
- **Tests per class:** ≥5 (create, findById, update+version, staleVersion→exception, list queries)
- **Depends on:** All 7 migrations (S1-S7)

### Task 2.3: Spring Context Validation
- Verify `CrmModuleWiringTest` covers staff beans (it loads the full context, so it should)
- Add explicit assertions if missing

### Wave 2 Gate
```bash
mvn test -Dtest=CrmModuleWiringTest
# Expected: 12/12 PASS

# Testcontainers tests: CI-only (Docker required)
# JdbcShiftTemplateRepositoryPostgresTest: 5+ tests
# JdbcShiftAssignmentRepositoryPostgresTest: 5+ tests
# JdbcAvailabilityRepositoryPostgresTest: 5+ tests
# JdbcSkillRepositoryPostgresTest: 5+ tests
# JdbcCapacityRepositoryPostgresTest: 5+ tests
# JdbcWorkloadRepositoryPostgresTest: 5+ tests
# JdbcServiceAssignmentRepositoryPostgresTest: 5+ tests
```

---

## Wave 3: Controller Tests (After Wave 2)

### Task 3.1: MockMvc Controller Tests (7 classes)
- **Files:** `src/test/java/com/sanad/platform/crm/ownership/web/{Availability,Capacity,ServiceAssignment,ShiftAssignment,ShiftTemplate,Skill,Workload}ControllerIntegrationTest.java`
- **Pattern:** H2 in PostgreSQL mode, MockMvc, `@SpringBootTest`
- **Tests per class:** ≥5 (POST→201, GET→200, PATCH→200, DELETE→204, validation→400)
- **Depends on:** Repository tests passing (S8)

### Wave 3 Gate
```bash
mvn test -Dtest='com.sanad.platform.crm.ownership.web.*IntegrationTest'
# Expected: 35+ tests, 0 failures
```

---

## Commit Strategy

| Commit | Content | Message |
|--------|---------|---------|
| 1 | V20260804_2 (shift_templates) | `fix(crm): REM-2 create crm_shift_templates migration` |
| 2 | V20260804_4-8 (5 independent tables) | `fix(crm): REM-2 create 5 staff table migrations` |
| 3 | V20260804_3 (shift_assignments) | `fix(crm): REM-2 create crm_shift_assignments migration` |
| 4 | 7 Testcontainers test classes | `test(crm): REM-2 add Testcontainers tests for 7 staff repositories` |
| 5 | 7 MockMvc test classes | `test(crm): REM-2 add MockMvc tests for 7 staff controllers` |
| 6 | Documentation reconciliation | `docs(crm): REM-2 reconcile fabricated CRM-008 closure documents` |

---

## Total Estimated Effort

| Category | SP |
|----------|----|
| 7 Flyway migrations | 21 |
| 7 Testcontainers tests | 7 |
| 7 MockMvc tests | 7 |
| Spring context validation | 1 |
| Documentation reconciliation | 3 |
| **Total** | **39-40** |
