# HRM G3 — Performance Reviews & Goals Design

**Status:** DESIGN / PRE-IMPLEMENTATION GATE  
**Date:** 2026-09-30  
**Repository:** `snadaiapp-png/SNAD`  
**Branch:** `feat/hrm-g3-performance-reviews-goals`  
**G2 official closure baseline:** `d2b93450b6774f14814a825e83b7e2dc76a6cc81`  
**Database execution model:** PostgreSQL Direct only  

## 1. Purpose

G3 implements the HR performance domain that follows the closed G1/G2 HR foundations. The repository roadmap defines G3 as **Performance: Reviews & Goals**: employee performance goals, periodic performance reviews, performance evaluation UI, and goal tracking UI.

G3 must begin from the closed G2 baseline above and must not reopen G2. A regression discovered in G2 behavior may be fixed only when reproduced and proven to be caused by G3 work.

## 2. Authoritative existing roadmap scope

The current `apps/web/app/hr/hr-execution-data.ts` defines these G3 roadmap items:

1. `G3-T01` — performance goals persistence with metric, target, and progress tracking.
2. `G3-T02` — performance reviews persistence, including self/manager/peer evaluation support.
3. `G3-T03` — performance evaluation UI with ratings, comments, and result presentation.
4. `G3-T04` — employee goal tracking UI with progress indicators and update history.

G3 depends on G1. G4 depends on G2 + G3, so G3 closure is a prerequisite for later payroll integration work.

## 3. Selected delivery approach

### Rejected approach A — UI-first roadmap literalism

Implement only the four visible roadmap rows as independent database/UI changes. This is rejected because UI without a governed service/API contract would bypass the architecture pattern already established in HRM and would make authorization, tenancy, auditing, and testing implicit.

### Selected approach B — governed vertical slice

Implement G3 as one canonical vertical slice:

`schema -> tenant/RLS/security contract -> domain/service -> API -> web UI -> authenticated acceptance -> post-merge verification`

The four roadmap rows remain the business scope, while backend/API/security/testing work is treated as required enabling work rather than scope expansion.

### Deferred approach C — broad talent-management suite

Do not expand G3 into compensation reviews, succession planning, calibration committees, competency libraries, learning plans, promotion workflows, or AI scoring. Those require separate authorization and design.

## 4. Canonical domain boundaries

G3 owns only performance goals and performance reviews.

### 4.1 Performance goal

A goal belongs to one tenant and one canonical HR employment/person context. At minimum the domain must represent:

- goal identity;
- tenant identity;
- employee/employment identity through the canonical HR identity graph;
- title/description;
- measurable metric;
- target value or target descriptor;
- current progress;
- lifecycle/status;
- goal-cycle or effective period;
- created/updated audit metadata.

The design must preserve canonical `User -> Person -> Employment -> PRIMARY Assignment` identity resolution established before G3. G3 must not restore legacy direct coupling through deprecated employee user/manager fields.

### 4.2 Performance review

A review belongs to one tenant and one review subject. It must support the roadmap requirement for self, manager, and peer review sources without inventing automatic 360-review orchestration beyond the required data model.

At minimum the domain must represent:

- review identity;
- tenant identity;
- subject employment/person identity;
- reviewer identity and review source/type;
- review period/cycle;
- rating/assessment payload;
- comments;
- lifecycle/status;
- created/updated audit metadata.

## 5. Identity, tenancy, and authorization invariants

These are hard gates, not optional implementation details:

1. Every G3 persistent row is tenant-bound.
2. Cross-tenant reads and writes fail closed.
3. Employee SELF access resolves through the canonical Person/Employment graph.
4. Manager/TEAM access, where required, resolves through effective-dated PRIMARY assignment reporting relationships.
5. HR/admin access must use explicit existing capability/RBAC mechanisms; no broad role shortcut may be introduced.
6. G3 must not change global RBAC, RLS, Flyway governance, or PostgreSQL configuration unless a failing RED contract proves the change belongs in that layer.
7. No production data mutation is part of G3 implementation or certification.

## 6. Database and migration policy

- PostgreSQL Direct only.
- Docker and Testcontainers are prohibited.
- Flyway remains the schema migration mechanism already used by the repository.
- New migrations must be additive and narrowly scoped to G3.
- Existing RLS policies must not be weakened.
- If new G3 tables require RLS policies, those policies must be covered by explicit tenant-isolation tests before implementation is considered complete.
- No migration may alter G2 data contracts merely to simplify G3.

## 7. API and service contract

G3 must expose governed application/service operations sufficient for the four roadmap tasks. The exact route names are implementation-plan decisions, but the contract must include:

- create/read/update/list goals;
- update goal progress with auditable state change;
- create/read/update/list performance reviews;
- retrieve the authenticated employee's own goals/reviews;
- retrieve manager/team views only when canonical assignment scope authorizes them;
- validate tenant, subject, reviewer, effective period, and status transitions;
- return explicit fail-closed authorization errors instead of silently widening scope.

No UI component may query PostgreSQL directly or bypass the application service/API layer.

## 8. Web experience scope

G3 web work is limited to the two roadmap surfaces:

### Performance evaluation

- render review context and period;
- capture supported ratings/comments;
- show existing review results according to authorization;
- support Arabic/English behavior consistent with the current HRM shell;
- provide deterministic loading, empty, success, validation, and forbidden states.

### Goal tracking

- list authorized employee goals;
- display metric/target/current progress/status;
- allow authorized progress updates;
- show goal update history when supplied by the service contract;
- provide deterministic loading, empty, success, validation, and forbidden states.

No redesign of unrelated HR navigation or G0/G1/G2 screens is authorized.

## 9. RED-first implementation contract

No product implementation begins until a regression/contract test is added and observed failing on the G3 branch baseline.

The first RED slice must prove the first canonical invariant being implemented. The preferred first slice is:

`tenant + canonical employment -> create/read performance goal -> foreign tenant denied`

The test must run against host-native PostgreSQL Direct. Passing a mock-only test is not sufficient evidence for the database/security contract.

Subsequent slices must remain RED -> minimal implementation -> GREEN.

## 10. Verification matrix

### Focused backend

- goal domain/service tests;
- review domain/service tests;
- canonical employment scope tests;
- RBAC/capability tests for SELF/TEAM/HR paths actually exposed;
- PostgreSQL tenant-isolation/RLS tests;
- Flyway migration validation.

### Focused frontend

- goal tracking component/page tests;
- performance evaluation component/page tests;
- Arabic/English rendering;
- desktop/mobile behavior for G3 screens;
- forbidden and empty-state behavior.

### Integrated acceptance

Authenticated acceptance must cover at least employee and manager paths needed by the delivered scope and verify real backend HTTP behavior against PostgreSQL Direct.

## 11. G3 acceptance gates

G3 cannot be declared closed until all of the following are bound to one exact head SHA:

1. **Baseline gate** — branch ancestry proves it started from `d2b93450b6774f14814a825e83b7e2dc76a6cc81` or a later explicitly authorized `main` head that includes it.
2. **RED evidence gate** — first contract failure captured before implementation.
3. **Schema gate** — G3 migrations apply cleanly on PostgreSQL Direct; Flyway history is valid.
4. **Tenant isolation gate** — cross-tenant G3 access is denied by tests exercising the real database path.
5. **Identity scope gate** — SELF/TEAM scope uses canonical Person/Employment/Assignment resolution where applicable.
6. **Backend gate** — focused G3 backend tests are green.
7. **Frontend gate** — G3 web tests are green in Arabic/English and desktop/mobile for implemented surfaces.
8. **Authenticated runtime gate** — real authenticated G3 acceptance passes without hidden 403/500 failures.
9. **Full CI gate** — Maven/CI and required repository checks are terminal success on exact head.
10. **Human/runtime evidence gate** — G3 human-preview/runtime evidence is reviewed when a preview workflow exists for the delivered screens.
11. **Merge governance gate** — protected PR approval/rules satisfied; no bypass.
12. **Post-merge gate** — `main` post-merge verification is terminal success on the merge SHA before `G3 CLOSED — FINAL` is declared.

## 12. Explicit non-goals

G3 does **not** authorize:

- Docker or Testcontainers;
- broad RBAC rewrites;
- weakening RLS;
- PostgreSQL server/configuration changes;
- unrelated Flyway repair;
- production database mutation;
- reopening closed G2 implementation;
- payroll implementation (G4);
- compensation, succession, learning, calibration, promotion, or AI performance scoring;
- declaring legal/compliance/production certification solely from engineering test success.

## 13. Execution sequence

After this design is approved, implementation planning must follow this exact order:

`G3 Baseline -> RED contract -> minimal implementation -> focused verification -> full CI -> authenticated/human runtime acceptance -> protected merge -> post-merge main verification -> G3 FINAL gate`

No later step may be used to excuse a failed earlier gate.

## 14. Design completion condition

This document closes only the **G3 design/kickoff gate**. It does not claim G3 implementation has started or completed. Product code changes require an approved implementation plan derived from this design.