# REM-2 Dependency Graph — CRM-008 Implementation Dependencies

**Date:** 2026-08-04

---

## Dependency Matrix

```
                    ┌─────────────────────────────────────────────┐
                    │           REM-2-F1: Migrations              │
                    │                                             │
                    │  S1 (shift_templates)                       │
                    │    │                                        │
                    │    └──→ S2 (shift_assignments) [FK]         │
                    │                                             │
                    │  S3 (staff_availability)  [independent]     │
                    │  S4 (staff_skills)        [independent]     │
                    │  S5 (capacity_plans)      [independent]     │
                    │  S6 (workload_assignments)[independent]     │
                    │  S7 (service_assignments) [independent]     │
                    │                                             │
                    └──────────────────┬──────────────────────────┘
                                       │
                    ┌──────────────────▼──────────────────────────┐
                    │        REM-2-F2: Test Suite                 │
                    │                                             │
                    │  S10 (context validation) [needs S1-S7]     │
                    │  S8  (repo tests)         [needs S1-S7]     │
                    │    │                                        │
                    │    └──→ S9 (controller tests) [needs S8]    │
                    │                                             │
                    └──────────────────┬──────────────────────────┘
                                       │
                    ┌──────────────────▼──────────────────────────┐
                    │     REM-2-F3: Documentation                 │
                    │                                             │
                    │  S11 (doc reconciliation) [independent]     │
                    │                                             │
                    └─────────────────────────────────────────────┘
```

---

## Story Dependencies

| Story | Depends On | Blocked By | Enables |
|-------|-----------|------------|---------|
| S1 (shift_templates) | None | — | S2, S8, S9, S10 |
| S2 (shift_assignments) | S1 (FK) | — | S8, S9, S10 |
| S3 (staff_availability) | None | — | S8, S9, S10 |
| S4 (staff_skills) | None | — | S8, S9, S10 |
| S5 (capacity_plans) | None | — | S8, S9, S10 |
| S6 (workload_assignments) | None | — | S8, S9, S10 |
| S7 (service_assignments) | None | — | S8, S9, S10 |
| S8 (repo tests) | S1-S7 | — | S9 |
| S9 (controller tests) | S8 | — | — |
| S10 (context validation) | S1-S7 | — | — |
| S11 (doc reconciliation) | None | — | — |

---

## Parallelization Opportunities

### Wave 1 (No dependencies — can run in parallel)
- S1: crm_shift_templates
- S3: crm_staff_availability
- S4: crm_staff_skills
- S5: crm_capacity_plans
- S6: crm_workload_assignments
- S7: crm_service_assignments
- S11: Documentation reconciliation

### Wave 2 (Depends on Wave 1)
- S2: crm_shift_assignments (needs S1 for FK)
- S10: Spring context validation (needs S1-S7)
- S8: Testcontainers repo tests (needs S1-S7)

### Wave 3 (Depends on Wave 2)
- S9: MockMvc controller tests (needs S8)

---

## Migration Version Map

| Version | Story | Table | Type |
|---------|-------|-------|------|
| V20260804_1 | REM-1 (already committed) | crm_custom_field_definitions, crm_pipelines | ALTER TABLE |
| V20260804_2 | REM-2-S1 | crm_shift_templates | CREATE TABLE |
| V20260804_3 | REM-2-S2 | crm_shift_assignments | CREATE TABLE |
| V20260804_4 | REM-2-S3 | crm_staff_availability | CREATE TABLE |
| V20260804_5 | REM-2-S4 | crm_staff_skills | CREATE TABLE |
| V20260804_6 | REM-2-S5 | crm_capacity_plans | CREATE TABLE |
| V20260804_7 | REM-2-S6 | crm_workload_assignments | CREATE TABLE |
| V20260804_8 | REM-2-S7 | crm_service_assignments | CREATE TABLE |

All migrations placed in `db/migration/` (shared H2+PG) following REM-1 convention.

---

## Risk: FK Ordering

`crm_shift_assignments` has FK to `crm_shift_templates`. Migration V20260804_3 (shift_assignments) must come AFTER V20260804_2 (shift_templates). The version numbering enforces this ordering.

`crm_service_assignments` has FK to `crm_sales_teams`. The `crm_sales_teams` table was created by V20260722_1 (CRM-008B ownership migrations), which is already applied. No ordering risk.

`crm_capacity_plans` has FK to `crm_sales_teams`. Same as above — no ordering risk.
