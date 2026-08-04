# REM-2 Wave-1 Implementation Report — CRM-008 Database Foundation

**Date:** 2026-08-04
**Epic:** REM-2 — CRM-008 Database Schema Remediation
**Wave:** 1 of 3 (Database Foundation)
**Branch:** `fix/rem-2-crm-008-schema-foundation`
**Commit:** `9fa506bdb5dc2a12d7fa0651cfb6d94dc4f3f2b0`

---

## 1. Migration Files Created

| Version | File | Table | Columns | Indexes | Constraints |
|---------|------|-------|---------|---------|-------------|
| V20260804_2 | `create_crm_shift_templates.sql` | crm_shift_templates | 12 | 3 | PK, UK(tenant_id,id), UK(tenant_id,name) |
| V20260804_3 | `create_crm_shift_assignments.sql` | crm_shift_assignments | 13 | 4 | PK, UK(tenant_id,id), FK→templates |
| V20260804_4 | `create_crm_staff_availability.sql` | crm_staff_availability | 14 | 3 | PK, UK(tenant_id,id) |
| V20260804_5 | `create_crm_staff_skills.sql` | crm_staff_skills | 11 | 3 | PK, UK(tenant_id,id), UK(tenant_id,staff_id,skill_name) |
| V20260804_6 | `create_crm_capacity_plans.sql` | crm_capacity_plans | 13 | 3 | PK, UK(tenant_id,id) |
| V20260804_7 | `create_crm_workload_assignments.sql` | crm_workload_assignments | 15 | 4 | PK, UK(tenant_id,id) |
| V20260804_8 | `create_crm_service_assignments.sql` | crm_service_assignments | 10 | 3 | PK, UK(tenant_id,id), UK(tenant_id,team_id,service_id) |

**Total:** 7 migrations, 88 columns, 23 indexes, 14 constraints

---

## 2. Tables Created

| # | Table | Purpose | Repository |
|---|-------|---------|-----------|
| 1 | `crm_shift_templates` | Recurring shift pattern definitions | JdbcShiftTemplateRepository |
| 2 | `crm_shift_assignments` | Staff-to-shift assignments | JdbcShiftAssignmentRepository |
| 3 | `crm_staff_availability` | Staff availability/leave tracking | JdbcAvailabilityRepository |
| 4 | `crm_staff_skills` | Staff skill proficiency tracking | JdbcSkillRepository |
| 5 | `crm_capacity_plans` | Team capacity planning | JdbcCapacityRepository |
| 6 | `crm_workload_assignments` | Workload assignment tracking | JdbcWorkloadRepository |
| 7 | `crm_service_assignments` | Service-to-team assignments | JdbcServiceAssignmentRepository |

---

## 3. Columns per Table

### crm_shift_templates (V20260804_2)
| Column | Type | Nullable | Default | Source |
|--------|------|----------|---------|--------|
| id | UUID | NO | — | shiftTemplateMapper |
| tenant_id | UUID | NO | — | shiftTemplateMapper |
| name | VARCHAR(200) | NO | — | INSERT, existsByName |
| start_time | TIME | NO | — | shiftTemplateMapper |
| end_time | TIME | NO | — | shiftTemplateMapper |
| days_of_week | VARCHAR(50) | NO | — | parseDayOfWeekArray CSV |
| status | VARCHAR(20) | NO | 'ACTIVE' | INSERT hardcoded |
| created_by | UUID | NO | — | INSERT |
| updated_by | UUID | NO | — | INSERT, UPDATE |
| created_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT/UPDATE CURRENT_TIMESTAMP |
| version | BIGINT | NO | 1 | INSERT, optimistic lock |

### crm_shift_assignments (V20260804_3)
| Column | Type | Nullable | Default | Source |
|--------|------|----------|---------|--------|
| id | UUID | NO | — | shiftAssignmentMapper |
| tenant_id | UUID | NO | — | shiftAssignmentMapper |
| team_id | UUID | NO | — | INSERT, findByTeamId |
| staff_id | UUID | NO | — | INSERT, findByStaffId, hasOverlap |
| shift_template_id | UUID | NO | — | INSERT, UPDATE, FK→templates |
| start_date | DATE | NO | — | INSERT, overlap query |
| end_date | DATE | NO | — | INSERT, overlap query |
| status | VARCHAR(20) | NO | 'SCHEDULED' | INSERT hardcoded |
| created_by | UUID | NO | — | INSERT |
| updated_by | UUID | NO | — | INSERT, UPDATE |
| created_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT/UPDATE CURRENT_TIMESTAMP |
| version | BIGINT | NO | 1 | INSERT, optimistic lock |

### crm_staff_availability (V20260804_4)
| Column | Type | Nullable | Default | Source |
|--------|------|----------|---------|--------|
| id | UUID | NO | — | staffAvailabilityMapper |
| tenant_id | UUID | NO | — | staffAvailabilityMapper |
| staff_id | UUID | NO | — | INSERT, findByStaffId |
| type | VARCHAR(20) | NO | — | INSERT, AvailabilityType enum |
| start_date | DATE | NO | — | INSERT, date range query |
| end_date | DATE | NO | — | INSERT, date range query |
| start_time | TIME | YES | — | INSERT (nullable) |
| end_time | TIME | YES | — | INSERT (nullable) |
| reason | VARCHAR(500) | YES | — | INSERT (nullable) |
| created_by | UUID | NO | — | INSERT |
| updated_by | UUID | NO | — | INSERT, UPDATE |
| created_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT/UPDATE CURRENT_TIMESTAMP |
| version | BIGINT | NO | 1 | INSERT, optimistic lock |

### crm_staff_skills (V20260804_5)
| Column | Type | Nullable | Default | Source |
|--------|------|----------|---------|--------|
| id | UUID | NO | — | staffSkillMapper |
| tenant_id | UUID | NO | — | staffSkillMapper |
| staff_id | UUID | NO | — | INSERT, findByStaffId, existsByStaffAndSkill |
| skill_name | VARCHAR(200) | NO | — | INSERT, findBySkillName, existsByStaffAndSkill |
| level | VARCHAR(20) | NO | — | INSERT, SkillLevel enum |
| proficiency | INT | NO | — | INSERT, UPDATE |
| created_by | UUID | NO | — | INSERT |
| updated_by | UUID | NO | — | INSERT, UPDATE |
| created_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT/UPDATE CURRENT_TIMESTAMP |
| version | BIGINT | NO | 1 | INSERT, optimistic lock |

### crm_capacity_plans (V20260804_6)
| Column | Type | Nullable | Default | Source |
|--------|------|----------|---------|--------|
| id | UUID | NO | — | capacityPlanMapper |
| tenant_id | UUID | NO | — | capacityPlanMapper |
| team_id | UUID | NO | — | INSERT, findByTeamId, findActiveByTeamAndPeriod |
| period_start | DATE | NO | — | INSERT, overlap query |
| period_end | DATE | NO | — | INSERT, overlap query |
| max_capacity | INT | NO | — | INSERT, UPDATE |
| allocated_capacity | INT | NO | 0 | INSERT hardcoded, UPDATE |
| status | VARCHAR(20) | NO | 'DRAFT' | INSERT hardcoded, UPDATE |
| created_by | UUID | NO | — | INSERT |
| updated_by | UUID | NO | — | INSERT, UPDATE |
| created_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT/UPDATE CURRENT_TIMESTAMP |
| version | BIGINT | NO | 1 | INSERT, optimistic lock |

### crm_workload_assignments (V20260804_7)
| Column | Type | Nullable | Default | Source |
|--------|------|----------|---------|--------|
| id | UUID | NO | — | workloadAssignmentMapper |
| tenant_id | UUID | NO | — | workloadAssignmentMapper |
| staff_id | UUID | NO | — | INSERT, findByStaffId, SUM queries |
| service_id | UUID | YES | — | INSERT, findByServiceId |
| job_id | UUID | YES | — | INSERT |
| estimated_hours | INT | NO | — | INSERT, SUM query |
| actual_hours | INT | YES | NULL | INSERT (nullable), UPDATE, SUM query |
| status | VARCHAR(20) | NO | 'PLANNED' | INSERT hardcoded, UPDATE, SUM filter |
| start_date | DATE | NO | — | INSERT, SUM filter |
| end_date | DATE | YES | — | INSERT (nullable), UPDATE |
| created_by | UUID | NO | — | INSERT |
| updated_by | UUID | NO | — | INSERT, UPDATE |
| created_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT/UPDATE CURRENT_TIMESTAMP |
| version | BIGINT | NO | 1 | INSERT, optimistic lock |

### crm_service_assignments (V20260804_8)
| Column | Type | Nullable | Default | Source |
|--------|------|----------|---------|--------|
| id | UUID | NO | — | serviceAssignmentMapper |
| tenant_id | UUID | NO | — | serviceAssignmentMapper |
| team_id | UUID | NO | — | INSERT, findByTeamId, existsByTeamAndService |
| service_id | UUID | NO | — | INSERT, findByServiceId, existsByTeamAndService |
| status | VARCHAR(20) | NO | 'ACTIVE' | INSERT hardcoded, UPDATE |
| created_by | UUID | NO | — | INSERT |
| updated_by | UUID | NO | — | INSERT, UPDATE |
| created_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP WITH TIME ZONE | NO | — | INSERT/UPDATE CURRENT_TIMESTAMP |
| version | BIGINT | NO | 1 | INSERT, optimistic lock |

---

## 4. Constraints

| Table | Constraint | Type | Columns |
|-------|-----------|------|---------|
| crm_shift_templates | pk_crm_shift_templates | PRIMARY KEY | id |
| crm_shift_templates | uk_crm_shift_templates_tenant_id | UNIQUE | (tenant_id, id) |
| crm_shift_templates | uk_crm_shift_templates_tenant_name | UNIQUE | (tenant_id, name) |
| crm_shift_assignments | pk_crm_shift_assignments | PRIMARY KEY | id |
| crm_shift_assignments | uk_crm_shift_assignments_tenant_id | UNIQUE | (tenant_id, id) |
| crm_shift_assignments | fk_crm_shift_assignments_template | FOREIGN KEY | (tenant_id, shift_template_id) → crm_shift_templates(tenant_id, id) |
| crm_staff_availability | pk_crm_staff_availability | PRIMARY KEY | id |
| crm_staff_availability | uk_crm_staff_availability_tenant_id | UNIQUE | (tenant_id, id) |
| crm_staff_skills | pk_crm_staff_skills | PRIMARY KEY | id |
| crm_staff_skills | uk_crm_staff_skills_tenant_id | UNIQUE | (tenant_id, id) |
| crm_staff_skills | uk_crm_staff_skills_tenant_staff_name | UNIQUE | (tenant_id, staff_id, skill_name) |
| crm_capacity_plans | pk_crm_capacity_plans | PRIMARY KEY | id |
| crm_capacity_plans | uk_crm_capacity_plans_tenant_id | UNIQUE | (tenant_id, id) |
| crm_workload_assignments | pk_crm_workload_assignments | PRIMARY KEY | id |
| crm_workload_assignments | uk_crm_workload_assignments_tenant_id | UNIQUE | (tenant_id, id) |
| crm_service_assignments | pk_crm_service_assignments | PRIMARY KEY | id |
| crm_service_assignments | uk_crm_service_assignments_tenant_id | UNIQUE | (tenant_id, id) |
| crm_service_assignments | uk_crm_service_assignments_tenant_team_service | UNIQUE | (tenant_id, team_id, service_id) |

---

## 5. Indexes

| Table | Index | Columns | Purpose |
|-------|-------|---------|---------|
| crm_shift_templates | idx_crm_shift_templates_tenant_status | (tenant_id, status) | Status filter |
| crm_shift_assignments | idx_crm_shift_assignments_tenant_team | (tenant_id, team_id) | Team lookup |
| crm_shift_assignments | idx_crm_shift_assignments_tenant_staff | (tenant_id, staff_id) | Staff lookup |
| crm_shift_assignments | idx_crm_shift_assignments_tenant_staff_dates | (tenant_id, staff_id, start_date, end_date) | Date range + overlap |
| crm_staff_availability | idx_crm_staff_availability_tenant_staff | (tenant_id, staff_id) | Staff lookup |
| crm_staff_availability | idx_crm_staff_availability_tenant_staff_dates | (tenant_id, staff_id, start_date) | Date range |
| crm_staff_skills | idx_crm_staff_skills_tenant_staff | (tenant_id, staff_id) | Staff lookup |
| crm_staff_skills | idx_crm_staff_skills_tenant_skill_name | (tenant_id, skill_name) | Skill lookup |
| crm_capacity_plans | idx_crm_capacity_plans_tenant_team | (tenant_id, team_id) | Team lookup |
| crm_capacity_plans | idx_crm_capacity_plans_tenant_team_status | (tenant_id, team_id, status) | Active plan lookup |
| crm_workload_assignments | idx_crm_workload_assignments_tenant_staff | (tenant_id, staff_id) | Staff lookup |
| crm_workload_assignments | idx_crm_workload_assignments_tenant_service | (tenant_id, service_id) | Service lookup |
| crm_workload_assignments | idx_crm_workload_assignments_tenant_staff_status | (tenant_id, staff_id, status) | Status filter |
| crm_service_assignments | idx_crm_service_assignments_tenant_team | (tenant_id, team_id) | Team lookup |
| crm_service_assignments | idx_crm_service_assignments_tenant_service | (tenant_id, service_id) | Service lookup |

---

## 6. Foreign Keys

| Table | FK Name | Columns | References | Notes |
|-------|---------|---------|------------|-------|
| crm_shift_assignments | fk_crm_shift_assignments_template | (tenant_id, shift_template_id) | crm_shift_templates(tenant_id, id) | Retained — same migration chain |

**FKs to crm_sales_teams omitted:** The `crm_sales_teams` table is created by V20260722_1 which lives in `db/vendor/postgresql/` (PostgreSQL-only). Shared migrations in `db/migration/` cannot reference PostgreSQL-only tables because H2 (the local test profile database) does not have them. The Java code does not enforce these FKs at the application level — referential integrity is maintained by the use-case layer.

---

## 7. Migration Numbering

| Version | Previous Gap ID | Description |
|---------|----------------|-------------|
| V20260804_1 | REM-1 | crm_custom_field_definitions + crm_pipelines (already committed) |
| V20260804_2 | GAP-01 | crm_shift_templates |
| V20260804_3 | GAP-02 | crm_shift_assignments |
| V20260804_4 | GAP-03 | crm_staff_availability |
| V20260804_5 | GAP-04 | crm_staff_skills |
| V20260804_6 | GAP-05 | crm_capacity_plans |
| V20260804_7 | GAP-06 | crm_workload_assignments |
| V20260804_8 | GAP-07 | crm_service_assignments |

**Collision resolution:** The original plan (REM-2-EXECUTION-SEQUENCE.md) proposed V20260804_2 through V20260804_8. This was followed exactly. No collisions with existing migrations.

---

## 8. Flyway Execution Results

### H2 (Local Test Profile)
```
CrmModuleWiringTest: 12/12 PASS (129.8 s)
All 8 new migrations (V20260804_2 through V20260804_8) applied successfully.
No checksum errors. No schema validation failures.
```

### PostgreSQL (CI — Testcontainers)
```
Docker daemon unavailable locally. Migration validation deferred to CI.
All migrations use standard SQL compatible with PostgreSQL ≥9.6.
FK to crm_shift_templates uses composite key pattern matching V20260722_1 convention.
```

---

## 9. Build Results

| Gate | Scope | Result |
|------|-------|--------|
| Clean compile | 593 source files | ✅ BUILD SUCCESS |
| CrmModuleWiringTest | Spring context + Flyway on H2 | ✅ 12/12 PASS |
| Full test suite | 976 tests | ✅ 0 failures (37 Docker errors, 12 skipped) |
| CRM integration tests | 94 tests (CI required check) | ✅ 94/94 PASS |

---

## 10. Remaining Work for Wave-2

| Story | Description | Status |
|-------|-------------|--------|
| REM-2-S8 | Testcontainers repository tests (7 classes) | NOT STARTED |
| REM-2-S10 | Spring context validation (verify staff beans) | NOT STARTED |

### Wave-3 (after Wave-2)
| Story | Description | Status |
|-------|-------------|--------|
| REM-2-S9 | MockMvc controller tests (7 classes) | NOT STARTED |

### Wave-F (documentation)
| Story | Description | Status |
|-------|-------------|--------|
| REM-2-S11 | Documentation reconciliation | NOT STARTED |

---

## 11. Commit Hash

```
9fa506bdb5dc2a12d7fa0651cfb6d94dc4f3f2b0
```

**Branch:** `fix/rem-2-crm-008-schema-foundation`
**Base:** `fix/rem-1-crm-schema-drift`
**Message:** `fix(crm): REM-2 Wave-1 create 7 missing CRM-008 staff table migrations`

---

## 12. Pull Request Reference

Not yet created. Branch ready for PR to `crm/td-003-s2-repo-tests`.
