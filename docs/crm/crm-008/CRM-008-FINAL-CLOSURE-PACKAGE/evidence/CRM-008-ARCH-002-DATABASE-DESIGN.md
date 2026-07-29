# CRM-008 Database Design — Agent 1

> **Agent:** Agent 1 — Architecture & Database Foundation
> **Command:** CRM-008-EXECUTION-001
> **Task:** 2 — Database Design
> **Date:** 2026-07-28
> **Status:** PASS

---

## 1. Design Scope

Design the complete database schema for CRM-008 Team Management.

---

## 2. Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           CRM-008 Team Management ERD                           │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐ │
│  │  crm_sales_teams │         │  crm_team_       │         │  crm_shift_      │ │
│  │  (existing)      │◄────────│  memberships     │         │  templates       │ │
│  │                  │    1:N  │  (existing)      │         │  (new)           │ │
│  └────────┬─────────┘         └────────┬─────────┘         └────────┬─────────┘ │
│           │                            │                            │           │
│           │                            │                            │           │
│           │ 1:N                        │ 1:N                        │ 1:N       │
│           ▼                            ▼                            ▼           │
│  ┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐ │
│  │  crm_shift_      │         │  crm_staff_      │         │  crm_shift_      │ │
│  │  templates       │────────▶│  availability    │◀────────│  assignments     │ │
│  │  (new)           │         │  (new)           │         │  (new)           │ │
│  └──────────────────┘         └──────────────────┘         └──────────────────┘ │
│           │                                                       │           │
│           │                                                       │           │
│           │ 1:N                                                   │ N:1       │
│           ▼                                                       ▼           │
│  ┌──────────────────┐                                       ┌──────────────────┐ │
│  │  crm_staff_      │                                       │  crm_capacity_   │ │
│  │  skills          │                                       │  plans           │ │
│  │  (new)           │                                       │  (new)           │ │
│  └──────────────────┘                                       └──────────────────┘ │
│                                                                                  │
│  ┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐ │
│  │  crm_workload_   │         │  crm_service_    │         │  crm_teams       │ │
│  │  assignments     │         │  assignments     │         │  (view)          │ │
│  │  (new)           │         │  (new)           │         │                  │ │
│  └──────────────────┘         └──────────────────┘         └──────────────────┘ │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. New Tables

### 3.1 crm_shift_templates

Stores shift template definitions (e.g., Morning Shift, Night Shift).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK, NOT NULL | Unique identifier |
| `tenant_id` | UUID | FK → tenants(id), NOT NULL | Tenant isolation |
| `name` | VARCHAR(100) | NOT NULL | Template name |
| `start_time` | TIME | NOT NULL | Shift start time |
| `end_time` | TIME | NOT NULL | Shift end time |
| `days_of_week` | VARCHAR(50) | NOT NULL | Comma-separated day numbers (1=Monday, 7=Sunday) |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE, INACTIVE |
| `created_by` | UUID | NOT NULL | Audit: creator |
| `updated_by` | UUID | NOT NULL | Audit: last updater |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: creation time |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: last update time |
| `version` | BIGINT | NOT NULL, DEFAULT 0 | Optimistic locking |

**Constraints:**
- `pk_crm_shift_templates` — PRIMARY KEY (id)
- `uk_crm_shift_templates_tenant` — UNIQUE (tenant_id, id)
- `fk_crm_shift_templates_tenant` — FOREIGN KEY (tenant_id) REFERENCES tenants(id)
- `ck_crm_shift_templates_status` — CHECK (status IN ('ACTIVE', 'INACTIVE'))

**Indexes:**
- `idx_crm_shift_templates_tenant_status` — (tenant_id, status)

### 3.2 crm_shift_assignments

Stores shift assignments for team members.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK, NOT NULL | Unique identifier |
| `tenant_id` | UUID | FK → tenants(id), NOT NULL | Tenant isolation |
| `team_id` | UUID | FK → crm_sales_teams(id), NOT NULL | Team reference |
| `staff_id` | UUID | NOT NULL | Staff member (user_id) |
| `shift_template_id` | UUID | FK → crm_shift_templates(id), NOT NULL | Shift template |
| `start_date` | DATE | NOT NULL | Assignment start date |
| `end_date` | DATE | NOT NULL | Assignment end date |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'SCHEDULED' | SCHEDULED, ACTIVE, COMPLETED, CANCELLED |
| `created_by` | UUID | NOT NULL | Audit: creator |
| `updated_by` | UUID | NOT NULL | Audit: last updater |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: creation time |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: last update time |
| `version` | BIGINT | NOT NULL, DEFAULT 0 | Optimistic locking |

**Constraints:**
- `pk_crm_shift_assignments` — PRIMARY KEY (id)
- `uk_crm_shift_assignments_tenant` — UNIQUE (tenant_id, id)
- `fk_crm_shift_assignments_tenant` — FOREIGN KEY (tenant_id) REFERENCES tenants(id)
- `fk_crm_shift_assignments_team` — FOREIGN KEY (team_id) REFERENCES crm_sales_teams(id)
- `fk_crm_shift_assignments_template` — FOREIGN KEY (shift_template_id) REFERENCES crm_shift_templates(id)
- `ck_crm_shift_assignments_status` — CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED', 'CANCELLED'))

**Indexes:**
- `idx_crm_shift_assignments_team` — (tenant_id, team_id, status)
- `idx_crm_shift_assignments_staff` — (tenant_id, staff_id, start_date)

### 3.3 crm_staff_availability

Stores staff availability records.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK, NOT NULL | Unique identifier |
| `tenant_id` | UUID | FK → tenants(id), NOT NULL | Tenant isolation |
| `staff_id` | UUID | NOT NULL | Staff member (user_id) |
| `type` | VARCHAR(20) | NOT NULL | AVAILABLE, UNAVAILABLE, ON_LEAVE |
| `start_date` | DATE | NOT NULL | Availability start date |
| `end_date` | DATE | NOT NULL | Availability end date |
| `start_time` | TIME | NULLABLE | Optional: daily start time |
| `end_time` | TIME | NULLABLE | Optional: daily end time |
| `reason` | VARCHAR(500) | NULLABLE | Reason for unavailability |
| `created_by` | UUID | NOT NULL | Audit: creator |
| `updated_by` | UUID | NOT NULL | Audit: last updater |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: creation time |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: last update time |
| `version` | BIGINT | NOT NULL, DEFAULT 0 | Optimistic locking |

**Constraints:**
- `pk_crm_staff_availability` — PRIMARY KEY (id)
- `uk_crm_staff_availability_tenant` — UNIQUE (tenant_id, id)
- `fk_crm_staff_availability_tenant` — FOREIGN KEY (tenant_id) REFERENCES tenants(id)
- `ck_crm_staff_availability_type` — CHECK (type IN ('AVAILABLE', 'UNAVAILABLE', 'ON_LEAVE'))

**Indexes:**
- `idx_crm_staff_availability_staff` — (tenant_id, staff_id, start_date)

### 3.4 crm_staff_skills

Stores staff skills and proficiency levels.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK, NOT NULL | Unique identifier |
| `tenant_id` | UUID | FK → tenants(id), NOT NULL | Tenant isolation |
| `staff_id` | UUID | NOT NULL | Staff member (user_id) |
| `skill_name` | VARCHAR(100) | NOT NULL | Skill name |
| `level` | VARCHAR(20) | NOT NULL | BEGINNER, INTERMEDIATE, ADVANCED, EXPERT |
| `proficiency` | INTEGER | NOT NULL | 1-100 proficiency score |
| `created_by` | UUID | NOT NULL | Audit: creator |
| `updated_by` | UUID | NOT NULL | Audit: last updater |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: creation time |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: last update time |
| `version` | BIGINT | NOT NULL, DEFAULT 0 | Optimistic locking |

**Constraints:**
- `pk_crm_staff_skills` — PRIMARY KEY (id)
- `uk_crm_staff_skills_tenant` — UNIQUE (tenant_id, id)
- `fk_crm_staff_skills_tenant` — FOREIGN KEY (tenant_id) REFERENCES tenants(id)
- `ck_crm_staff_skills_level` — CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'))
- `ck_crm_staff_skills_proficiency` — CHECK (proficiency BETWEEN 1 AND 100)

**Indexes:**
- `idx_crm_staff_skills_staff` — (tenant_id, staff_id)
- `idx_crm_staff_skills_name` — (tenant_id, skill_name)

### 3.5 crm_capacity_plans

Stores team capacity plans.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK, NOT NULL | Unique identifier |
| `tenant_id` | UUID | FK → tenants(id), NOT NULL | Tenant isolation |
| `team_id` | UUID | FK → crm_sales_teams(id), NOT NULL | Team reference |
| `period_start` | DATE | NOT NULL | Plan period start |
| `period_end` | DATE | NOT NULL | Plan period end |
| `max_capacity` | INTEGER | NOT NULL | Maximum capacity (hours) |
| `allocated_capacity` | INTEGER | NOT NULL, DEFAULT 0 | Allocated capacity (hours) |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'DRAFT' | DRAFT, ACTIVE, COMPLETED |
| `created_by` | UUID | NOT NULL | Audit: creator |
| `updated_by` | UUID | NOT NULL | Audit: last updater |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: creation time |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: last update time |
| `version` | BIGINT | NOT NULL, DEFAULT 0 | Optimistic locking |

**Constraints:**
- `pk_crm_capacity_plans` — PRIMARY KEY (id)
- `uk_crm_capacity_plans_tenant` — UNIQUE (tenant_id, id)
- `fk_crm_capacity_plans_tenant` — FOREIGN KEY (tenant_id) REFERENCES tenants(id)
- `fk_crm_capacity_plans_team` — FOREIGN KEY (team_id) REFERENCES crm_sales_teams(id)
- `ck_crm_capacity_plans_status` — CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED'))

**Indexes:**
- `idx_crm_capacity_plans_team` — (tenant_id, team_id, status)

### 3.6 crm_workload_assignments

Stores workload assignments for staff members.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK, NOT NULL | Unique identifier |
| `tenant_id` | UUID | FK → tenants(id), NOT NULL | Tenant isolation |
| `staff_id` | UUID | NOT NULL | Staff member (user_id) |
| `service_id` | UUID | NULLABLE | Service reference (optional) |
| `job_id` | UUID | NULLABLE | Job reference (optional) |
| `estimated_hours` | INTEGER | NOT NULL | Estimated hours |
| `actual_hours` | INTEGER | NULLABLE | Actual hours (filled on completion) |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'PLANNED' | PLANNED, IN_PROGRESS, COMPLETED, CANCELLED |
| `start_date` | DATE | NOT NULL | Assignment start date |
| `end_date` | DATE | NULLABLE | Assignment end date |
| `created_by` | UUID | NOT NULL | Audit: creator |
| `updated_by` | UUID | NOT NULL | Audit: last updater |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: creation time |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: last update time |
| `version` | BIGINT | NOT NULL, DEFAULT 0 | Optimistic locking |

**Constraints:**
- `pk_crm_workload_assignments` — PRIMARY KEY (id)
- `uk_crm_workload_assignments_tenant` — UNIQUE (tenant_id, id)
- `fk_crm_workload_assignments_tenant` — FOREIGN KEY (tenant_id) REFERENCES tenants(id)
- `ck_crm_workload_assignments_status` — CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))

**Indexes:**
- `idx_crm_workload_assignments_staff` — (tenant_id, staff_id, status)
- `idx_crm_workload_assignments_service` — (tenant_id, service_id)

### 3.7 crm_service_assignments

Stores team-service associations.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK, NOT NULL | Unique identifier |
| `tenant_id` | UUID | FK → tenants(id), NOT NULL | Tenant isolation |
| `team_id` | UUID | FK → crm_sales_teams(id), NOT NULL | Team reference |
| `service_id` | UUID | NOT NULL | Service reference |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE, INACTIVE |
| `created_by` | UUID | NOT NULL | Audit: creator |
| `updated_by` | UUID | NOT NULL | Audit: last updater |
| `created_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: creation time |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NOT NULL | Audit: last update time |
| `version` | BIGINT | NOT NULL, DEFAULT 0 | Optimistic locking |

**Constraints:**
- `pk_crm_service_assignments` — PRIMARY KEY (id)
- `uk_crm_service_assignments_tenant` — UNIQUE (tenant_id, id)
- `uk_crm_service_assignments_team_service` — UNIQUE (tenant_id, team_id, service_id)
- `fk_crm_service_assignments_tenant` — FOREIGN KEY (tenant_id) REFERENCES tenants(id)
- `fk_crm_service_assignments_team` — FOREIGN KEY (team_id) REFERENCES crm_sales_teams(id)
- `ck_crm_service_assignments_status` — CHECK (status IN ('ACTIVE', 'INACTIVE'))

**Indexes:**
- `idx_crm_service_assignments_team` — (tenant_id, team_id, status)
- `idx_crm_service_assignments_service` — (tenant_id, service_id)

---

## 4. Existing Tables (Reused)

### 4.1 crm_sales_teams (Existing)

| Column | Type | Description |
|---|---|---|
| `id` | UUID | Primary key |
| `tenant_id` | UUID | Tenant isolation |
| `code` | VARCHAR(50) | Team code |
| `display_name` | VARCHAR(200) | Team display name |
| `description` | TEXT | Team description |
| `status` | VARCHAR(20) | ACTIVE, SUSPENDED, ARCHIVED |
| `manager_user_id` | UUID | Team manager |
| `default_queue_id` | UUID | Default queue |
| `default_territory_id` | UUID | Default territory |
| `version` | BIGINT | Optimistic locking |

### 4.2 crm_team_memberships (Existing)

| Column | Type | Description |
|---|---|---|
| `id` | UUID | Primary key |
| `tenant_id` | UUID | Tenant isolation |
| `team_id` | UUID | Team reference |
| `user_id` | UUID | User reference |
| `role` | VARCHAR(30) | Membership role |
| `is_primary` | BOOLEAN | Primary team flag |
| `status` | VARCHAR(20) | ACTIVE, ENDED, REMOVED |
| `joined_at` | TIMESTAMP | Join date |
| `left_at` | TIMESTAMP | Leave date |
| `capacity_max` | INTEGER | Maximum capacity |
| `version` | BIGINT | Optimistic locking |

---

## 5. Relationship Summary

| Parent Table | Child Table | Relationship | FK Column |
|---|---|---|---|
| `tenants` | All CRM tables | 1:N | `tenant_id` |
| `crm_sales_teams` | `crm_shift_assignments` | 1:N | `team_id` |
| `crm_sales_teams` | `crm_capacity_plans` | 1:N | `team_id` |
| `crm_sales_teams` | `crm_service_assignments` | 1:N | `team_id` |
| `crm_shift_templates` | `crm_shift_assignments` | 1:N | `shift_template_id` |

---

## 6. Design Decision

### Decision: **PASS**

Database design is normalized, tenant-aware, and compatible with CRM-007.

| Criterion | Status |
|---|---|
| Normalization | ✅ 3NF |
| Tenant Isolation | ✅ tenant_id on all tables |
| Audit Fields | ✅ created_by, updated_by, created_at, updated_at |
| Optimistic Locking | ✅ version column on all tables |
| Constraints | ✅ PK, FK, UNIQUE, CHECK |
| Indexes | ✅ Performance indexes defined |
| Foreign Keys | ✅ Proper referential integrity |

---

**Design Date:** 2026-07-28
**Designer:** Agent 1 — Architecture & Database Foundation
**Status:** PASS
