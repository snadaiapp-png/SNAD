# TD-006 Epic Stub — V2 Feature Completion + V1 Controller Removal

**Status:** NOT STARTED
**Estimated SP:** ~25
**Dependencies:** TD-002 Phase 1 (COMPLETE)
**Created:** 2026-08-04

---

## 1. Objective

Complete the V2 CRM API surface so that every V1 endpoint has a functionally equivalent V2 replacement, then remove the V1 controller layer entirely.

This is the Phase 2 follow-on to TD-002 (Phase 1), which added deprecation headers and migrated 30 of 42 frontend API methods to V2.

---

## 2. Background

TD-002 readiness verification (2026-08-04) proved that V2 covers only ~27% of the V1 endpoint surface (30 of 113 endpoints). Full V1 removal was deferred to this Epic because:

- 15 of 16 V1 controllers have **zero** V2 equivalent
- 3 core endpoints (dashboard, pipeline create, sensitive CF read) have **no** V2 equivalent
- 12 frontend `crm.ts` methods cannot be migrated until V2 equivalents exist
- 2 shared services (`CrmService`, `LegacyCrmInfrastructureService`) are consumed by V2 controllers

---

## 3. Proposed Stories

| Story | SP | Scope |
|-------|----|-------|
| TD-006-1 | 1 | V2 Dashboard endpoint (`GET /api/v2/crm/dashboard`) |
| TD-006-2 | 1 | V2 Pipeline Create endpoint (`POST /api/v2/crm/pipelines`) |
| TD-006-3 | 1 | V2 Sensitive Custom-Field Read (`GET /api/v2/crm/custom-fields/values/{type}/{id}/sensitive`) |
| TD-006-4 | 3 | V2 Customer Master Controller (master, identifiers, duplicates, merge) |
| TD-006-5 | 4 | V2 Note/Task/Tag Controllers (3 missing controllers) |
| TD-006-6 | 3 | V2 Search/Reports/Export Controllers |
| TD-006-7 | 5 | V2 Ownership Controllers (8 V1 ownership controllers → V2) |
| TD-006-8 | 3 | Migrate remaining 12 frontend `crm.ts` methods to V2 |
| TD-006-9 | 2 | Migrate `customer-master-panel.tsx` direct V1 calls to V2 |
| TD-006-10 | 2 | Remove V1 controllers + V1-only DTOs + V1-only use cases |
| **Total** | **~25** | |

---

## 4. Prerequisites

- TD-002 Phase 1 merged (deprecation headers active)
- V2 deprecation headers give consumers visibility into the sunset date
- No external API consumers depend on V1-only endpoints

---

## 5. Risks

| Risk | Mitigation |
|------|-----------|
| Removing `CrmService` breaks V2 controllers | Verify V2 controllers no longer reference `CrmService` before removal; refactor V2 to use use-cases directly |
| Removing `LegacyCrmInfrastructureService` breaks V2 | Same — verify V2 no longer references it |
| Flyway migrations cannot be deleted | Leave tables in place as orphan schema OR add forward-only DROP migrations |
| Frontend breaks if V2 response shapes differ | V2 response adapters in `crm.ts` (from TD-002 Phase 1) handle envelope unwrapping |

---

## 6. Not Started

This stub is for planning purposes only. No work has begun. Implementation requires explicit governance approval.
