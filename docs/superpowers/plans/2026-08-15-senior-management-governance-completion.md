# Senior Management Governance Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Senior Management P0 governance by adding executive Finance aggregation and Module Registry governance without touching Render or implementing downstream modules.

**Architecture:** Add two read-only management integration services. Finance reads existing `finance_invoices` and `finance_payments`; Module Governance reads the existing global `modules` registry and capability structures. Controllers expose management-scoped projections protected by `EXECUTIVE_COMMAND_CENTER.VIEW`; no duplicate domain logic or schema is introduced.

**Tech Stack:** Java 17+, Spring Boot, JdbcTemplate, PostgreSQL, JUnit/Spring integration tests, existing `@RequireCapability`, existing management controller/service conventions.

## Global Constraints

- Certified reference remains `v20260815.3` (`708bc0cbabf0e94ad46a0b6a5cc7192e932243ce`).
- Render is deferred and must not be touched.
- Do not implement ERP, HRM, POS, ECOMMERCE_CX, or runtime Analytics in this mission.
- Reuse existing Finance and Module Registry schemas; no migration is expected.
- Finance aggregation must be tenant-scoped and read-only.
- Module Registry is global; do not incorrectly apply tenant filtering to the global module catalog. Tenant entitlement information must only be included through existing entitlement APIs/services if required.
- All new management endpoints require `EXECUTIVE_COMMAND_CENTER.VIEW`.
- Follow existing JdbcTemplate patterns; do not introduce JPA for these integrations.
- TDD: write each focused test first, verify RED, implement minimal production code, verify GREEN, then refactor.
- Certification requires CI with zero failures, zero errors, zero skipped tests and a clean worktree.

---

### Task 1: Finance management integration contract and failing tests

**Files:**
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/management/FinanceManagementIntegrationTest.java`
- Inspect: existing management integration tests and Finance repository/migration

**Interfaces:**
- Produces tests defining the executive Finance overview contract.

- [ ] **Step 1: Write failing integration tests**

Cover exactly these behaviors:
1. Empty tenant returns zero counts and zero monetary totals.
2. Invoice status counts and total invoice value are aggregated correctly.
3. Completed payments are aggregated into collected revenue and outstanding amount is derived as invoice total minus paid/completed amount according to existing Finance semantics.
4. Finance data from another tenant is excluded.
5. Endpoint requires `EXECUTIVE_COMMAND_CENTER.VIEW`.

Use the existing test harness, tenant setup, JDBC fixtures, authentication/capability setup, and assertion conventions already used by `CrmManagementIntegrationTest`.

- [ ] **Step 2: Run the focused test and verify RED**

Run the exact Finance management integration test class with the repository's normal Maven test command. Expected failure: the new service/endpoint is absent or the expected contract is not implemented.

- [ ] **Step 3: Commit only the failing test**

Commit message: `test(management): define finance executive integration contract`

---

### Task 2: Implement Finance executive aggregation

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/management/application/FinanceManagementIntegrationService.java`
- Modify: the existing Management controller that owns `/api/v1/management/*`
- Inspect: `finance_invoices`, `finance_payments` repositories/migration and `CrmManagementIntegrationService`

**Interfaces:**
- Produces `FinanceManagementIntegrationService#getOverview(...)` returning an immutable executive projection containing invoice count, invoice status distribution, invoice value, payment count, completed payment value, and outstanding amount.
- Produces `GET /api/v1/management/finance/overview`.

- [ ] **Step 1: Implement only the minimal service required by the failing tests**

Use JdbcTemplate with tenant predicates. Query `finance_invoices` and `finance_payments` directly or through existing repositories when those repositories expose the exact required read operation. Do not duplicate Finance domain rules.

Use the schema's actual statuses: invoice statuses are `DRAFT`, `ISSUED`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `CANCELLED`; payment statuses are `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED`, `CANCELLED`. The migration defines these statuses and the tables as tenant-scoped with RLS. fileciteturn36file0L2-L2

- [ ] **Step 2: Add the management endpoint**

Expose `GET /api/v1/management/finance/overview` with `@RequireCapability("EXECUTIVE_COMMAND_CENTER.VIEW")`. Keep it read-only.

- [ ] **Step 3: Run the focused Finance test and verify GREEN**

Expected: all Finance management integration tests pass with zero failures/errors/skips.

- [ ] **Step 4: Refactor only after GREEN**

Align naming, projection records, and SQL style with `CrmManagementIntegrationService` without changing behavior.

- [ ] **Step 5: Commit**

Commit message: `feat(management): add finance executive integration`

---

### Task 3: Module governance failing tests

**Files:**
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/management/ModuleGovernanceIntegrationTest.java`
- Inspect: `ModuleEntity`, `ModuleRepository`, `ModuleCapabilityRepository`, existing `ModuleRegistryController`

**Interfaces:**
- Produces tests defining `GET /api/v1/management/modules/status`.

- [ ] **Step 1: Write failing integration tests**

Cover exactly:
1. Returns all registered modules ordered by `display_order, code`.
2. Returns each module's code, name, registry status, enabled flag, display order, and version.
3. Includes registered capability codes for each module.
4. Does not mutate the global registry.
5. Endpoint requires `EXECUTIVE_COMMAND_CENTER.VIEW`.

The existing `ModuleEntity` is explicitly a global, non-tenant-scoped catalog with code, status, display order, version, and enabled fields. fileciteturn34file0L2-L2 The existing `ModuleRepository.findAll()` already returns the registry in `display_order, code` order. fileciteturn35file0L2-L2

- [ ] **Step 2: Run the focused test and verify RED**

Expected failure because the management projection/service/endpoint does not yet exist.

- [ ] **Step 3: Commit only the failing test**

Commit message: `test(management): define module governance contract`

---

### Task 4: Implement Module Registry governance projection

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/management/application/ModuleGovernanceService.java`
- Create if needed: `apps/sanad-platform/src/main/java/com/sanad/platform/management/api/dto/ModuleGovernanceResponse.java`
- Modify: existing Management controller
- Inspect: `ModuleCapabilityRepository` and capability entity/DTO

**Interfaces:**
- Produces `ModuleGovernanceService#getModuleStatuses()` returning an ordered list of executive module projections.
- Produces `GET /api/v1/management/modules/status`.

- [ ] **Step 1: Implement minimal service using existing registry repositories**

Reuse `ModuleRepository.findAll()` and the existing module-capability repository. Do not create a second module registry. The current platform already has a dedicated `/api/v1/executive` Module Registry controller, so the new endpoint is an executive read projection, not a replacement. fileciteturn37file0L2-L2

- [ ] **Step 2: Add endpoint with capability enforcement**

Use `@RequireCapability("EXECUTIVE_COMMAND_CENTER.VIEW")` and the same authentication/authorization conventions used by the management controller.

- [ ] **Step 3: Run the focused Module Governance test and verify GREEN**

Expected: all governance tests pass with zero failures/errors/skips.

- [ ] **Step 4: Refactor after GREEN**

Keep registry semantics separate from tenant entitlement semantics. Do not introduce auto-discovery or runtime mutation.

- [ ] **Step 5: Commit**

Commit message: `feat(management): add module registry governance projection`

---

### Task 5: Integrate both projections into the Executive Command Center

**Files:**
- Modify: existing `ExecutiveCommandCenterService`
- Modify: its existing response DTO only if required
- Modify: `PlatformApiCountTest` expected endpoint count
- Test: existing command-center integration test plus focused regression assertions

**Interfaces:**
- Command Center exposes Finance and Module Governance as composed read projections without duplicating their SQL.

- [ ] **Step 1: Add failing composition assertions**

Assert that the executive dashboard/overview includes the Finance summary and module governance summary at the existing command-center aggregation boundary. Do not create a second dashboard endpoint.

- [ ] **Step 2: Run the focused test and verify RED**

Expected failure because the command-center response does not yet contain the new projections.

- [ ] **Step 3: Implement minimal composition**

Inject the two integration services into `ExecutiveCommandCenterService` and compose their existing projections. Preserve all current dashboard fields and behavior.

- [ ] **Step 4: Update API count regression**

Increase the expected endpoint count only by the exact number of newly added endpoints (Finance overview + Module status = 2), then run the test.

- [ ] **Step 5: Run focused management regression tests and verify GREEN**

Run all management integration tests plus `PlatformApiCountTest`.

- [ ] **Step 6: Commit**

Commit message: `feat(management): compose finance and module governance in command center`

---

### Task 6: Full verification and certification gate

**Files:**
- No production changes unless a verified test failure requires one.

- [ ] **Step 1: Run complete local backend verification**

Run the repository's standard full Maven/management/CRM test commands used by prior certified missions. Require zero failures, zero errors, zero skipped tests.

- [ ] **Step 2: Inspect repository state**

Verify current HEAD, origin/main, clean worktree, and preservation of all certified tags through `v20260815.3`.

- [ ] **Step 3: Push the verified implementation**

Push only after all local verification is green.

- [ ] **Step 4: Wait for CI and inspect the actual CI result**

Do not infer certification from a started run. Require `completed | success` and zero failures/errors/skips in the reported test totals.

- [ ] **Step 5: Create the certification tag only after CI PASS**

Target tag: `v20260815.4-senior-management-governance-complete-certification`.

- [ ] **Step 6: Final forensic verification**

Confirm the new baseline SHA equals `HEAD` and `origin/main`, worktree is clean, prior baselines are intact, Render was untouched, and no downstream module was implemented.

---

## Self-review against approved spec

- Finance integration: covered by Tasks 1-2.
- Module Registry governance: covered by Tasks 3-4.
- Future-module governance contract: represented by stable registry projection without premature auto-discovery; future modules can expose registry/capability metadata through the existing contract.
- Command Center composition: covered by Task 5.
- Security/RLS/capability enforcement: covered by focused endpoint tests and existing RLS authority.
- No migration: explicit throughout.
- Render deferred: explicit global constraint and final verification.
- No ERP/HRM/POS: explicit global constraint.
- CI certification gate: covered by Task 6.
- No placeholders or undefined interfaces: all task outputs and test behaviors are specified.
