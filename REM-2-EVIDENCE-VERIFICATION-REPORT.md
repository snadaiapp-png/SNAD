# REM-2 Evidence Verification Report — CRM-008 Forensic Analysis

**Date:** 2026-08-04
**Mode:** READ-ONLY forensic analysis
**Repository HEAD:** `9b469441` (fix/rem-1-crm-schema-drift)
**Authoritative Principle:** Repository HEAD is the single source of truth.

---

## Executive Summary

CRM-008 "Team Management" was officially CLOSED on 2026-07-29 with documentation claiming 100% completion, 414 tests passing, 0 defects, and 158 implementation files. This forensic analysis reveals that **the closure was fabricated**: the 7 database tables that the entire feature depends on were never created by any Flyway migration. All 41 API endpoints will throw `BadSqlGrammarException` at runtime. Zero tests exist for any staff functionality. The Java application layer (48 files) is complete but operates against non-existent tables.

---

## Phase 1 — Inventory

### Documents Found

| Category | Count | Notes |
|----------|-------|-------|
| Architecture docs (CRM-008-ARCH-*) | 7 | Design specs, not verified against repo |
| Domain layer docs (CRM-008-DOM-*) | 6 | Interface/record specs |
| Application layer docs (CRM-008-APP-*) | 8 | Use case specs |
| API layer docs (CRM-008-API-*) | 7 | Controller/endpoint specs |
| Integration docs (CRM-008-INT-*) | 7 | Workflow/event specs |
| QA docs (CRM-008-QA-*) | 9 | Test results (fabricated) |
| Production readiness docs (CRM-008-PROD-*) | 9 | Deployment specs |
| Closure docs | 12 | Closure evidence, certificates |
| Foundation docs (CRM-008B-*) | 6 | Implementation backlog/runbook |
| Remediation docs (CRM-008-REM-*) | 3 | Alerting/logging fixes |
| Design spec | 1 | Full DDL for 7 tables |
| Implementation plan | 1 | Step-by-step build instructions |
| Final closure package | 34 | Archive copies of above |
| **Total documents** | **~125** | |

### Source Code Found

| Category | Count | Location |
|----------|-------|----------|
| Domain records | 7 | `crm/ownership/domain/{availability,capacity,scheduling,service,skills,workload}/` |
| Domain enums | 7 | Same packages |
| Domain repository interfaces | 7 | Same packages |
| Application use cases | 7 | `crm/ownership/application/` |
| Infrastructure JDBC repos | 7 | `crm/ownership/infrastructure/` |
| Infrastructure support | 1 | `OwnershipJdbcSupport.java` |
| Web controllers | 8 | `crm/ownership/web/` |
| Web DTOs | 1 | `TeamModels.java` |
| Integration constants | 2 | `TeamManagementEventTypes.java`, `TeamManagementNotificationTypes.java` |
| Spring configuration | 1 | `OwnershipModuleConfiguration.java` |
| **Total Java files** | **48** | |

### Migrations Found

| File | Location | Purpose |
|------|----------|---------|
| `V20260728_1__seed_crm_008_team_management_capabilities.sql` | `db/vendor/postgresql/` | Seeds 13 RBAC capabilities ONLY |

### Tests Found

| File | What it tests | Staff functionality? |
|------|--------------|---------------------|
| `Crm008bFoundationAcceptanceTest.java` | Flyway migration schema invariants | **NO** — tests ownership migrations, not staff |
| `crm-008r-production-closure.spec.ts` | Playwright e2e for CRM-008R | **NO** — tests ownership concurrency/pagination |

---

## Phase 2 — Database Forensics

### Table Existence Matrix

| Table | Created by Migration? | Exists on Disk (DDL)? | Java Code References It? | Documentation Claims? |
|-------|----------------------|----------------------|-------------------------|----------------------|
| `crm_shift_templates` | **NO** | **NO** | YES (JdbcShiftTemplateRepository) | "PASS" in ARCH-003 |
| `crm_shift_assignments` | **NO** | **NO** | YES (JdbcShiftAssignmentRepository) | "PASS" in ARCH-003 |
| `crm_staff_availability` | **NO** | **NO** | YES (JdbcAvailabilityRepository) | "PASS" in ARCH-003 |
| `crm_staff_skills` | **NO** | **NO** | YES (JdbcSkillRepository) | "PASS" in ARCH-003 |
| `crm_capacity_plans` | **NO** | **NO** | YES (JdbcCapacityRepository) | "PASS" in ARCH-003 |
| `crm_workload_assignments` | **NO** | **NO** | YES (JdbcWorkloadRepository) | "PASS" in ARCH-003 |
| `crm_service_assignments` | **NO** | **NO** | YES (JdbcServiceAssignmentRepository) | "PASS" in ARCH-003 |

**Result: 0/7 tables exist. All 7 are missing from the database.**

### Schema Design (from DDL in CRM-008-ARCH-003-MIGRATIONS.md)

The documentation specifies complete DDL for all 7 tables. Key schema attributes per table:

| Table | PK | Tenant | Version | Audit Cols | Soft Delete | Indexes | FKs |
|-------|-----|--------|---------|------------|-------------|---------|-----|
| crm_shift_templates | id UUID | tenant_id UUID | version BIGINT | created_by, updated_by, created_at, updated_at | status enum | 3 | 0 |
| crm_shift_assignments | id UUID | tenant_id UUID | version BIGINT | created_by, updated_by, created_at, updated_at | status enum | 4 | 2 (team, template) |
| crm_staff_availability | id UUID | tenant_id UUID | version BIGINT | created_by, updated_by, created_at, updated_at | status enum | 3 | 0 |
| crm_staff_skills | id UUID | tenant_id UUID | version BIGINT | created_by, updated_by, created_at, updated_at | No (hard delete) | 3 | 0 |
| crm_capacity_plans | id UUID | tenant_id UUID | version BIGINT | created_by, updated_by, created_at, updated_at | status enum | 3 | 1 (team) |
| crm_workload_assignments | id UUID | tenant_id UUID | version BIGINT | created_by, updated_by, created_at, updated_at | status enum | 4 | 2 (staff, service) |
| crm_service_assignments | id UUID | tenant_id UUID | version BIGINT | created_by, updated_by, created_at, updated_at | status enum | 3 | 2 (team, service) |

### Version Number Collision

The planned migration `V20260728_1__create_crm_shift_templates.sql` conflicts with the existing `V20260728_1__seed_crm_008_team_management_capabilities.sql`. Flyway requires unique version numbers. Similarly, `V20260729_1`, `V20260729_2`, and `V20260730_1` are taken by CRM-010 and CRM-018 migrations.

---

## Phase 3 — Repository Verification

### Repository Dependency Graph

```
Controller → UseCase → Repository Interface → JdbcRepository → [MISSING TABLE]
```

| Repository Interface | Jdbc Implementation | Table | SQL Operations | Optimistic Locking | Tenant Filtered |
|---------------------|--------------------|-------|----------------|-------------------|-----------------|
| AvailabilityRepository | JdbcAvailabilityRepository | crm_staff_availability | SELECT, INSERT, UPDATE, DELETE | YES | YES |
| CapacityRepository | JdbcCapacityRepository | crm_capacity_plans | SELECT, INSERT, UPDATE | YES | YES |
| ServiceAssignmentRepository | JdbcServiceAssignmentRepository | crm_service_assignments | SELECT, INSERT, UPDATE, DELETE | YES | YES |
| ShiftAssignmentRepository | JdbcShiftAssignmentRepository | crm_shift_assignments | SELECT, INSERT, UPDATE | YES | YES |
| ShiftTemplateRepository | JdbcShiftTemplateRepository | crm_shift_templates | SELECT, INSERT, UPDATE | YES | YES |
| SkillRepository | JdbcSkillRepository | crm_staff_skills | SELECT, INSERT, UPDATE, DELETE | YES | YES |
| WorkloadRepository | JdbcWorkloadRepository | crm_workload_assignments | SELECT, INSERT, UPDATE, DELETE | YES | YES |

**All 7 repositories are fully implemented** with proper:
- Tenant isolation (every query includes `tenant_id=:tenantId`)
- Optimistic locking (every UPDATE uses `version=version+1` with `AND version=:expectedVersion`)
- CRUD operations
- RowMappers defined in `OwnershipJdbcSupport.java`

**Status: COMPLETE but BROKEN** — every SQL statement references a non-existent table.

---

## Phase 4 — Service Verification

| Use Case | Repository Dependencies | Transactional | Validation | Error Handling | Status |
|----------|------------------------|---------------|------------|----------------|--------|
| AvailabilityManagementUseCases | AvailabilityRepository, AuditPort, TimelineEventPort | YES (@Transactional) | Date range, type validation | CrmContractException | COMPLETE |
| CapacityManagementUseCases | CapacityRepository, SalesTeamRepository, AuditPort, TimelineEventPort | YES | Period overlap, capacity limits | CrmContractException | COMPLETE |
| ServiceAssignmentUseCases | ServiceAssignmentRepository, SalesTeamRepository, AuditPort, TimelineEventPort | YES | Duplicate prevention, state transitions | CrmContractException | COMPLETE |
| ShiftManagementUseCases | ShiftTemplateRepository, ShiftAssignmentRepository, SalesTeamRepository, AuditPort, TimelineEventPort | YES | Overlap detection, name uniqueness | CrmContractException | COMPLETE |
| SkillManagementUseCases | SkillRepository, AuditPort, TimelineEventPort | YES | Staff-skill uniqueness, proficiency range | CrmContractException | COMPLETE |
| WorkloadManagementUseCases | WorkloadRepository, AuditPort, TimelineEventPort | YES | Status transitions, capacity checks | CrmContractException | COMPLETE |
| TeamManagementUseCases | SalesTeamRepository, AuditPort, TimelineEventPort | YES | Status validation | CrmContractException | COMPLETE |

**All 7 use cases are wired** via `OwnershipModuleConfiguration.java` (lines 127-197). Spring component scanning discovers all `@Repository` and `@Configuration` beans. **Status: COMPLETE but will crash at first SQL execution.**

---

## Phase 5 — Controller Verification

| Controller | Base Path | Endpoints | Reachable? | Live Path? | Status |
|-----------|-----------|-----------|------------|------------|--------|
| AvailabilityController | `/api/v1/crm/availability` | 5 | YES (component-scanned) | YES (no feature flag) | DEAD ON ARRIVAL |
| CapacityController | `/api/v1/crm/capacity` | 5 | YES | YES | DEAD ON ARRIVAL |
| ServiceAssignmentController | `/api/v1/crm/service-assignments` | 6 | YES | YES | DEAD ON ARRIVAL |
| ShiftAssignmentController | `/api/v1/crm/shift-assignments` | 4 | YES | YES | DEAD ON ARRIVAL |
| ShiftTemplateController | `/api/v1/crm/shift-templates` | 6 | YES | YES | DEAD ON ARRIVAL |
| SkillController | `/api/v1/crm/skills` | 4 | YES | YES | DEAD ON ARRIVAL |
| WorkloadController | `/api/v1/crm/workload` | 5 | YES | YES | DEAD ON ARRIVAL |
| TeamController | `/api/v1/crm/teams` | 6 | YES | YES | DEAD ON ARRIVAL |
| **Total** | | **41** | | | |

**Authorization:** All endpoints use `@RequireCapability("CRM.*")` AOP annotation. 14 RBAC capabilities seeded by V20260728_1 (PostgreSQL only).

**Every endpoint will throw `BadSqlGrammarException` (HTTP 500)** on first database interaction because the backing tables do not exist.

---

## Phase 6 — Test Coverage

| Layer | Components | Test Count | Coverage |
|-------|-----------|------------|----------|
| Staff Controllers (HTTP) | 8 controllers, 41 endpoints | **0** | 0% |
| Staff Use Cases | 7 service classes | **0** | 0% |
| Staff Jdbc Repositories | 7 repository implementations | **0** | 0% |
| Staff Domain Models | 7+ domain records/enums | **0** | 0% |
| Staff Migration DDL | 7 CREATE TABLE scripts | **0** | 0% |
| **Total** | **~50 components** | **0** | **0%** |

The only CRM-008 test (`Crm008bFoundationAcceptanceTest`) validates Flyway migration schema invariants for the **ownership** module (V20260722_*), not the staff management feature.

**Existing reusable test harnesses:**
- `CrmRepositoryPostgresTestBase` — Testcontainers + Flyway harness (can be extended)
- `AccountV2HttpIntegrationTest` — MockMvc + H2 harness (can be extended)
- `CrmModuleWiringTest` — Spring context validation (already validates all beans)

---

## Phase 7 — Implementation Status

| Component | Status | Evidence |
|-----------|--------|----------|
| Domain records (7) | **COMPLETE** | Java files exist with validation logic |
| Domain enums (7) | **COMPLETE** | Java files exist |
| Repository interfaces (7) | **COMPLETE** | Java files exist with full contract |
| JDBC implementations (7) | **COMPLETE** | Java files exist with full SQL |
| RowMappers (7) | **COMPLETE** | Defined in OwnershipJdbcSupport.java |
| Use case services (7) | **COMPLETE** | Java files exist with business logic |
| Spring configuration | **COMPLETE** | OwnershipModuleConfiguration.java wires all beans |
| Controllers (8) | **COMPLETE** | Java files exist with full endpoint mappings |
| DTOs (13 records) | **COMPLETE** | TeamModels.java with validation |
| RBAC capabilities (13) | **COMPLETE** | V20260728_1 seeds all capabilities |
| **Flyway migrations (7 tables)** | **MISSING** | No CREATE TABLE statements exist on disk |
| **Test suite** | **MISSING** | Zero tests for any staff component |
| **Documentation (125+ docs)** | **FABRICATED** | Claims "PASS" and "100% certified" for non-existent code |

---

## Phase 8 — Gap Analysis

### Critical Gaps

| Gap ID | Description | Impact | Risk |
|--------|-------------|--------|------|
| GAP-01 | 7 Flyway migrations missing (CREATE TABLE for all staff tables) | All 41 endpoints crash at runtime | CRITICAL — HTTP 500 on every request |
| GAP-02 | 0 tests for staff functionality | No regression protection | HIGH — changes can break silently |
| GAP-03 | Version number collision (V20260728_1 taken) | Cannot use planned migration versions | MEDIUM — requires version reassignment |
| GAP-04 | 125+ fabricated closure documents | False confidence in production readiness | HIGH — governance integrity compromised |

### Non-Gaps (Already Complete)

| Component | Files | Notes |
|-----------|-------|-------|
| Domain layer | 21 | Records, enums, interfaces |
| Application layer | 8 | Use cases + configuration |
| Infrastructure layer | 8 | JDBC repos + support |
| Web layer | 9 | Controllers + DTOs |
| Integration layer | 2 | Event/notification constants |
| RBAC seeding | 1 | V20260728_1 (PostgreSQL only) |

---

## Phase 9 — Execution Readiness

### Prerequisites Met

- ✅ All Java application code exists and compiles
- ✅ Spring wiring is complete (OwnershipModuleConfiguration)
- ✅ RBAC capabilities are seeded
- ✅ Existing test harnesses can be extended
- ✅ REM-1 migration pattern established (single-column ALTER, shared db/migration/)

### Prerequisites NOT Met

- ❌ No Flyway migration scripts for table creation
- ❌ No test suite
- ❌ No verified DDL against repository SQL (documentation DDL may not match actual column names)

---

## Phase 10 — Final Decision

### **READY TO IMPLEMENT REM-2**

The Java application layer is complete and well-structured. The ONLY missing piece is the Flyway migration scripts to create the 7 tables, plus a test suite to validate them. This is a bounded, well-defined implementation task with clear repository evidence for every required column.

**Implementation scope:**
- 7 Flyway migration scripts (CREATE TABLE with indexes, constraints, FKs)
- 7 Testcontainers repository tests
- 7 MockMvc controller tests
- Documentation reconciliation

**Estimated effort:** 21-34 story points across 3 epics (migrations, tests, documentation cleanup).
