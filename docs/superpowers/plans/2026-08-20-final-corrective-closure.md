# SNAD Final Corrective Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining Commerce/Finance/ERP/idempotency/RBAC P0 invariants, re-certify G7 against current code and PostgreSQL, and carry the exact merged SHA through CI, Render, production business smoke, and governance reconciliation.

**Architecture:** Preserve the existing modular-monolith boundaries. Commerce calls Finance and ERP through existing ports/adapters inside Spring transactions; checkout idempotency is enforced by PostgreSQL unique invariants plus a durable SHA-256 request fingerprint already provisioned by V20260820_6; tenant role templates are provisioned at runtime using the exact matrix validated by V20260820_7. G7 is not redesigned unless evidence proves a defect.

**Tech Stack:** Java/Spring Boot, JdbcTemplate/JPA, PostgreSQL/Flyway, JUnit 5/Mockito, Next.js, mobile TypeScript.

**Spec:** Current project handoff and repository migrations V20260820_5 through V20260820_10; G7 governance artifacts on `main`.

## Global Constraints

- Base every change on current `origin/main`; never overwrite newer main commits.
- Applied Flyway migrations are immutable; no `flyway repair` as a release workaround.
- PostgreSQL is the governing DB/test authority; H2 is not production authority.
- Preserve tenant isolation, RLS, JWT-only auth, and branch protection.
- TDD: add/modify a failing test before each production behavior change.
- No direct push to protected `main`.

---

### Task 1: Finance settlement invariant

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/commerce/domain/CommerceFinancePort.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/commerce/application/CommerceFinanceAdapter.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/commerce/application/OrderSettlementService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/commerce/application/CheckoutService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/finance/domain/FinanceInvoice.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/finance/application/FinanceInvoiceService.java`
- Test: finance domain/service and commerce settlement tests.

**Interfaces:**
- Produces: `CommerceFinancePort.markOrderSettled(UUID, UUID, BigDecimal)`, amount-aware invoice settlement.

- [ ] Write failing tests for paid amount persistence, finance settlement invocation, replay idempotency, and rollback behavior.
- [ ] Run PR CI and record the expected RED evidence.
- [ ] Implement minimal amount-aware finance settlement and wire both manual and verified-PSP flows.
- [ ] Re-run focused + governing tests.

### Task 2: ERP physical inventory fail-closed

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/erp/application/ErpInventoryAvailabilityAdapter.java`
- Test: ERP adapter unit tests.

**Interfaces:**
- Consumes: canonical `commerce_products.product_type`.
- Produces: physical products fail closed; DIGITAL/SERVICE bypass stock; BUNDLE remains stock-controlled unless repository evidence proves otherwise.

- [ ] Write failing tests for missing mapping, missing warehouse, direct fulfillment failure, and digital/service bypass.
- [ ] Run RED.
- [ ] Implement minimal fail-closed behavior.
- [ ] Re-run tests.

### Task 3: Checkout fingerprint and cart race

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/commerce/application/CheckoutService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/commerce/application/OrderService.java`
- Test: checkout idempotency + PostgreSQL concurrency tests.

**Interfaces:**
- Produces: deterministic SHA-256 fingerprint stored in `commerce_orders.idempotency_fingerprint`; bare `ON CONFLICT DO NOTHING` claim covers both key and cart unique invariants.

- [ ] Write failing tests for same key/different payload and same cart/different keys.
- [ ] Run RED.
- [ ] Implement canonical fingerprint + durable comparison and atomic conflict handling.
- [ ] Re-run unit and PostgreSQL concurrency suites.

### Task 4: Runtime canonical role provisioning

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/security/service/RoleTemplateProvisioner.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/AdminPlatformService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/executive/service/ExecutivePlatformService.java`
- Test: role provisioner/onboarding tests.

**Interfaces:**
- Produces: exactly 9 authoritative SNAD template bindings using the capability matrix validated by V20260820_7; idempotent and fail-closed on customer-role collision.

- [ ] Write failing onboarding/provisioning tests.
- [ ] Run RED.
- [ ] Implement provisioner and invoke it for both tenant creation paths.
- [ ] Re-run tests.

### Task 5: G7 forensic certification

**Files:**
- Modify only if evidence proves a defect; otherwise add/adjust tests/evidence/governance only.

- [ ] Reconcile G7 database/API/services/mobile tests against current code.
- [ ] Run PostgreSQL G7 integration tests including RLS, sync pull/push, cursor, ETag/412, idempotency, conflict, multi-device, delete-vs-update, and full resync.
- [ ] Fix only proven defects using TDD.
- [ ] Record zero-open-P0 evidence.

### Task 6: Release and production certification

- [ ] Frontend typecheck/lint/tests/build.
- [ ] Mobile typecheck/lint/all G7 tests.
- [ ] Secret/security/tenant-isolation gates.
- [ ] All required GitHub checks green.
- [ ] Merge PR and verify post-merge exact SHA.
- [ ] Verify GHCR/Render deploy and Flyway validate/migrate.
- [ ] Run production Commerce→Inventory→Finance, replay/idempotency, role provisioning, and G7 sync/replay/conflict/isolation probes.
- [ ] Reconcile worklog/master/G7 governance only after runtime evidence.
- [ ] Final verdict: PASS only with zero P0 and all governing runtime gates green.
