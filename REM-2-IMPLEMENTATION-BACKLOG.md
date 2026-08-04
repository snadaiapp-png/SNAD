# REM-2 Implementation Backlog — CRM-008 Schema Remediation

**Date:** 2026-08-04
**Scope:** Only genuine missing work. Excludes already-implemented code, dead code, duplicate work, obsolete work.

---

## Epic REM-2: CRM-008 Database Schema Remediation

**Goal:** Create the 7 missing Flyway migrations so the existing Java application layer can function against a real database.

---

### Feature REM-2-F1: Staff Table Migrations

**Story REM-2-S1: Create crm_shift_templates migration**

| Field | Value |
|-------|-------|
| Story Points | 3 |
| Priority | P1 |
| Dependencies | None |
| Gap | GAP-01 |

**Tasks:**
1. Derive DDL from `JdbcShiftTemplateRepository.java` SQL + `OwnershipJdbcSupport.shiftTemplateMapper()` column mappings
2. Write `V20260804_2__create_crm_shift_templates.sql` in `db/migration/` (shared H2+PG)
3. Use single-column ALTER pattern (H2 portable) for any ALTER statements
4. Add indexes: tenant, tenant+name (unique), tenant+status
5. Verify H2 compatibility (no PG-specific types beyond what H2 PG-mode supports)

**Acceptance Criteria:**
- Migration applies cleanly on H2 (CrmModuleWiringTest passes)
- Table columns match exactly: id, tenant_id, name, start_time, end_time, days_of_week, status, created_by, updated_by, created_at, updated_at, version
- UNIQUE constraint on (tenant_id, name)
- version defaults to 1

**DoD:**
- Migration file committed
- CrmModuleWiringTest passes (12/12)
- Column names match repository INSERT/SELECT exactly

---

**Story REM-2-S2: Create crm_shift_assignments migration**

| Field | Value |
|-------|-------|
| Story Points | 3 |
| Priority | P1 |
| Dependencies | REM-2-S1 (FK to crm_shift_templates) |
| Gap | GAP-02 |

**Tasks:**
1. Derive DDL from `JdbcShiftAssignmentRepository.java`
2. Write `V20260804_3__create_crm_shift_assignments.sql`
3. Add FK to crm_shift_templates(id) and crm_sales_teams(id)
4. Add indexes: tenant, tenant+team, tenant+staff, tenant+staff+dates

**Acceptance Criteria:**
- All columns match repository SQL
- FK constraints enforce referential integrity
- Overlap detection query works (start_date/end_date range)

---

**Story REM-2-S3: Create crm_staff_availability migration**

| Field | Value |
|-------|-------|
| Story Points | 3 |
| Priority | P1 |
| Dependencies | None |
| Gap | GAP-03 |

**Tasks:**
1. Derive DDL from `JdbcAvailabilityRepository.java`
2. Write `V20260804_4__create_crm_staff_availability.sql`
3. start_time/end_time must be nullable (partial-day availability)
4. reason must be nullable VARCHAR(500)

**Acceptance Criteria:**
- All columns match repository SQL
- Nullable time/reason columns work correctly

---

**Story REM-2-S4: Create crm_staff_skills migration**

| Field | Value |
|-------|-------|
| Story Points | 3 |
| Priority | P1 |
| Dependencies | None |
| Gap | GAP-04 |

**Tasks:**
1. Derive DDL from `JdbcSkillRepository.java`
2. Write `V20260804_5__create_crm_staff_skills.sql`
3. UNIQUE on (tenant_id, staff_id, skill_name)
4. proficiency as INT (1-100)

**Acceptance Criteria:**
- All columns match repository SQL
- UNIQUE constraint prevents duplicate skills per staff member

---

**Story REM-2-S5: Create crm_capacity_plans migration**

| Field | Value |
|-------|-------|
| Story Points | 3 |
| Priority | P1 |
| Dependencies | None |
| Gap | GAP-05 |

**Tasks:**
1. Derive DDL from `JdbcCapacityRepository.java`
2. Write `V20260804_6__create_crm_capacity_plans.sql`
3. FK to crm_sales_teams(id)
4. allocated_capacity defaults to 0

**Acceptance Criteria:**
- All columns match repository SQL
- FK enforces team reference
- Status defaults to 'DRAFT'

---

**Story REM-2-S6: Create crm_workload_assignments migration**

| Field | Value |
|-------|-------|
| Story Points | 3 |
| Priority | P1 |
| Dependencies | None |
| Gap | GAP-06 |

**Tasks:**
1. Derive DDL from `JdbcWorkloadRepository.java`
2. Write `V20260804_7__create_crm_workload_assignments.sql`
3. service_id and job_id nullable (optional associations)
4. actual_hours nullable (set when work completes)

**Acceptance Criteria:**
- All columns match repository SQL
- Nullable service_id, job_id, actual_hours, end_date work correctly

---

**Story REM-2-S7: Create crm_service_assignments migration**

| Field | Value |
|-------|-------|
| Story Points | 3 |
| Priority | P1 |
| Dependencies | None |
| Gap | GAP-07 |

**Tasks:**
1. Derive DDL from `JdbcServiceAssignmentRepository.java`
2. Write `V20260804_8__create_crm_service_assignments.sql`
3. FK to crm_sales_teams(id)
4. UNIQUE on (tenant_id, team_id, service_id)

**Acceptance Criteria:**
- All columns match repository SQL
- UNIQUE constraint prevents duplicate assignments
- FK enforces team reference

---

### Feature REM-2-F2: Test Suite

**Story REM-2-S8: Testcontainers repository tests**

| Field | Value |
|-------|-------|
| Story Points | 7 |
| Priority | P1 |
| Dependencies | REM-2-S1 through REM-2-S7 |
| Gap | GAP-08 |

**Tasks:**
1. Create 7 Testcontainers test classes (one per repository)
2. Extend `CrmRepositoryPostgresTestBase`
3. Test: create round-trip, findById, update with version, stale version → CRM_CONCURRENCY_CONFLICT, findAll/findByStaffId/findByTeamId queries, delete (where applicable)
4. Test unique constraint enforcement
5. Test tenant isolation (cross-tenant queries return empty)

**Acceptance Criteria:**
- All 7 test classes pass on Testcontainers (CI)
- Each repository has ≥5 test methods
- Optimistic locking verified
- Tenant isolation verified

---

**Story REM-2-S9: MockMvc controller tests**

| Field | Value |
|-------|-------|
| Story Points | 7 |
| Priority | P2 |
| Dependencies | REM-2-S8 |
| Gap | GAP-08 |

**Tasks:**
1. Create 7 MockMvc test classes (one per controller domain)
2. Use H2 in PostgreSQL mode (same pattern as AccountV2HttpIntegrationTest)
3. Test: POST create → 201, GET list → 200, GET by id → 200, PATCH update → 200, DELETE → 204
4. Test RBAC: missing capability → 403
5. Test validation: invalid request → 400

**Acceptance Criteria:**
- All controller endpoints exercised
- HTTP status codes correct
- Response bodies match expected shapes

---

**Story REM-2-S10: Spring context validation**

| Field | Value |
|-------|-------|
| Story Points | 1 |
| Priority | P1 |
| Dependencies | REM-2-S1 through REM-2-S7 |
| Gap | GAP-08 |

**Tasks:**
1. Verify CrmModuleWiringTest already covers staff beans (it should — it loads the full context)
2. If not, add explicit bean assertions for the 7 staff use cases

**Acceptance Criteria:**
- CrmModuleWiringTest passes with all staff beans wired

---

### Feature REM-2-F3: Documentation Reconciliation

**Story REM-2-S11: Mark fabricated documents**

| Field | Value |
|-------|-------|
| Story Points | 3 |
| Priority | P2 |
| Dependencies | None |
| Gap | GAP-10 |

**Tasks:**
1. Add DEPRECATED/RECONCILED header to all fabricated closure documents
2. Update CRM-008-ARCH-003-MIGRATIONS.md to reflect actual migration status
3. Update CRM-008-OFFICIAL-CLOSURE-RECORD.md with corrected status

**Acceptance Criteria:**
- All 125+ documents clearly marked as needing reconciliation
- No document claims "PASS" or "100% certified" for non-existent code

---

## Backlog Summary

| Story | Description | SP | Priority | Dependencies |
|-------|-------------|----|----|--------------|
| REM-2-S1 | crm_shift_templates migration | 3 | P1 | None |
| REM-2-S2 | crm_shift_assignments migration | 3 | P1 | S1 |
| REM-2-S3 | crm_staff_availability migration | 3 | P1 | None |
| REM-2-S4 | crm_staff_skills migration | 3 | P1 | None |
| REM-2-S5 | crm_capacity_plans migration | 3 | P1 | None |
| REM-2-S6 | crm_workload_assignments migration | 3 | P1 | None |
| REM-2-S7 | crm_service_assignments migration | 3 | P1 | None |
| REM-2-S8 | Testcontainers repo tests | 7 | P1 | S1-S7 |
| REM-2-S9 | MockMvc controller tests | 7 | P2 | S8 |
| REM-2-S10 | Spring context validation | 1 | P1 | S1-S7 |
| REM-2-S11 | Documentation reconciliation | 3 | P2 | None |
| **Total** | | **40** | | |
