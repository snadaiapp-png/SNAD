# CRM Technical Debt Register

| Field | Value |
|-------|-------|
| Created | 2026-07-30 |
| Last Updated | 2026-08-04 |
| Maintainer | CRM Engineering Team |

---

## Summary

| Category | Count | High | Medium | Low |
|----------|-------|------|--------|-----|
| Governance Debt | 3 | 1 | 2 | 0 |
| CI Debt | 2 | 1 | 1 | 0 |
| Documentation Debt | 4 | 0 | 3 | 1 |
| Workflow Debt | 1 | 0 | 1 | 0 |
| Legacy Suppressions | 1 | 0 | 1 | 0 |
| Build Debt | 1 | 0 | 1 | 0 |
| API Deprecation Debt | 2 | 1 | 1 | 0 |
| **Total** | **14** | **3** | **10** | **1** |

---

## Governance Debt

| ID | Description | Severity | Owner | Proposed Resolution | Target Milestone | Status |
|----|-------------|----------|-------|---------------------|------------------|--------|
| GD-001 | `crm` job not registered as required status check before CRM-022 merge | High | Repo Admin | ✅ Fixed in WS1 (PR #825) | CRM-022 | RESOLVED |
| GD-002 | Governance drift violations in `CRM-G4-CLOSURE-REPORT.md` | Medium | CRM Team | ✅ Fixed in WS3 (PR #827) | CRM-022 | RESOLVED |
| GD-003 | Governance drift violations in `crm-014/IMPLEMENTATION-PLAN.md` | Medium | CRM Team | ✅ Fixed in WS3 (PR #827) | CRM-022 | RESOLVED |

---

## CI Debt

| ID | Description | Severity | Owner | Proposed Resolution | Target Milestone | Status |
|----|-------------|----------|-------|---------------------|------------------|--------|
| CI-001 | Maven Test Suite fails due to hardcoded migration version strings | High | CRM Team | ✅ Fixed in WS2 (PR #826) | CRM-022 | RESOLVED |
| CI-002 | `CrmRlsTenantIsolationPostgresTest` missing vendor migration path | Medium | CRM Team | ✅ Fixed in WS2 (PR #826) | CRM-022 | RESOLVED |

---

## Documentation Debt

| ID | Description | Severity | Owner | Proposed Resolution | Target Milestone | Status |
|----|-------------|----------|-------|---------------------|------------------|--------|
| DOC-001 | CRM-008R status inconsistency in baseline (Section 1 vs Section 5) | Medium | CRM Team | ✅ Fixed in WS4 (PR #828) | CRM-022 | RESOLVED |
| DOC-002 | Status summary counts wrong in roadmap (10 DONE -> 18 DONE) | Medium | CRM Team | ✅ Fixed in WS4 (PR #828) | CRM-022 | RESOLVED |
| DOC-003 | README stale claims (14 tabs, 8 tests, 4 classes) | Medium | CRM Team | ✅ Fixed in WS4 (PR #828) | CRM-022 | RESOLVED |
| DOC-004 | Incomplete migration inventory in baseline (9 files missing) | Low | CRM Team | Add missing 9 migration files to baseline inventory | CRM-023 | OPEN |

---

## Workflow Debt

| ID | Description | Severity | Owner | Proposed Resolution | Target Milestone | Status |
|----|-------------|----------|-------|---------------------|------------------|--------|
| WF-001 | 33 CRM workflow files exist but only 4 are active/needed | Medium | DevOps | Audit and archive unused workflows | CRM-023 | OPEN |

---

## Legacy Suppressions

| ID | Description | Severity | Owner | Proposed Resolution | Target Milestone | Status |
|----|-------------|----------|-------|---------------------|------------------|--------|
| LS-001 | `react-hooks/set-state-in-effect` ESLint override for CRM files | Medium | CRM Team | Refactor CRM components to use proper React patterns | CRM-023 | OPEN |

---

## Build Debt

| ID | Description | Severity | Owner | Proposed Resolution | Target Milestone | Status |
|----|-------------|----------|-------|---------------------|------------------|--------|
| BD-001 | 6 test files use `classpath:db/migration` without vendor path | Low | CRM Team | Add `classpath:db/vendor/postgresql` to all test Flyway configs | CRM-023 | OPEN |

---

## API Deprecation Debt (TD-002 / TD-006)

| ID | Description | Severity | Owner | Proposed Resolution | Target Milestone | Status |
|----|-------------|----------|-------|---------------------|------------------|--------|
| TD-002 | V1 CRM API deprecation — Phase 1: Deprecation/Sunset headers added; 30/42 frontend `crm.ts` methods migrated to V2; 12 methods remain on V1 (dashboard, createPipeline, tags, notes, tasks, reports, search, export, custom-field sensitive read, customer-master-panel) | Medium | CRM Team | ✅ Phase 1 complete (2026-08-04). 12 methods retained on V1 until TD-006 builds V2 equivalents | Sprint 38 | PARTIALLY RESOLVED |
| TD-006 | V1 CRM API removal — Phase 2: Build V2 equivalents for 15 V1-only controllers (Export, Note, Task, Tag, Search, Reports, 8 ownership, CustomerMaster) + 3 core endpoints (dashboard, pipeline create, sensitive CF read); then remove V1 controllers, V1-only DTOs/use cases, shared CrmService + LegacyCrmInfrastructureService | High | CRM Team | ~25 SP. Build V2 surface, migrate remaining 12 frontend methods, remove V1. See `TD-006-EPIC-STUB.md` | Sprint 40+ | NOT STARTED |

---

## Resolved Items (This Remediation Cycle)

| ID | Workstream | PR | Resolved Date |
|----|------------|-----|---------------|
| GD-001 | WS1 | #825 | 2026-07-30 |
| GD-002 | WS3 | #827 | 2026-07-30 |
| GD-003 | WS3 | #827 | 2026-07-30 |
| CI-001 | WS2 | #826 | 2026-07-30 |
| CI-002 | WS2 | #826 | 2026-07-30 |
| DOC-001 | WS4 | #828 | 2026-07-30 |
| DOC-002 | WS4 | #828 | 2026-07-30 |
| DOC-003 | WS4 | #828 | 2026-07-30 |

---

## Remaining Open Items

| ID | Description | Severity | Target |
|----|-------------|----------|--------|
| DOC-004 | Add 9 missing migrations to baseline inventory | Low | CRM-023 |
| WF-001 | Audit and archive 29 unused CRM workflow files | Medium | CRM-023 |
| LS-001 | Refactor CRM components to remove ESLint suppression | Medium | CRM-023 |
| BD-001 | Add vendor migration path to 6 test files | Low | CRM-023 |
| TD-002 | V1 CRM API deprecation Phase 1 complete; 12 methods remain on V1 | Medium | TD-006 |
| TD-006 | V2 feature completion + V1 controller removal (~25 SP) | High | Sprint 40+ |

---

## Notes

- All items resolved in this cycle (WS1-WS4) are marked RESOLVED
- Remaining items are tracked for CRM-023 milestone
- This register should be updated as new debt is identified or existing items are resolved
- Severity follows: High = blocks CI/merge, Medium = affects quality, Low = minor improvement
