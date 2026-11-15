# Requirements Traceability — Subscription Platform Closure (Issue #1055)

- **Baseline / execution HEAD:** `4c9d7e68ca1636135549077650a896aaf4fe236d` (branch `main`, clean tree)
- **Verification date:** 2026-11-15 (UTC)
- **Scope:** the eight workstreams of Issue #1055 "Subscription Platform Closure — UAT, Domains, Branch Billing"
- **Status vocabulary:** VERIFIED / PARTIAL / MISSING / BLOCKED / UNVERIFIED
- **Governing holds (unchanged):** `G1=HOLD`, `P0_SECURITY_HOLD=ACTIVE`, `PRODUCTION_RELEASE=NO-GO`, `PAYMENTS=OFF`
- **Execution model honored:** phased PRs only; no direct main writes. This document is delivered by a docs-only draft PR; nothing here merges code or changes gates.

## 1. Verification environment (re-established after sandbox recycle)

The previous session's local artifacts (including its Surefire XML evidence and batch definitions) were lost with the recycled execution sandbox. All numbers below were therefore re-proven by execution at the exact HEAD, with a **real host-native PostgreSQL 17.11** (no Docker, no Testcontainers, no H2, no mocks), provisioned strictly per the repository's own role contract (`apps/sanad-platform/docker/postgres-init/*` + CI provisioning):

- Bootstrap superuser `postgres`; restricted runtime role `sanad` (LOGIN, NOSUPERUSER/NOCREATEDB/NOCREATEROLE/NOBYPASSRLS/NOREPLICATION)
- `sanad_migrator` + `sanad_callback_replay_cleanup` NOLOGIN roles, non-inheriting membership `GRANT sanad_callback_replay_cleanup TO sanad WITH INHERIT FALSE`
- Databases `sanad` and `test_migration` owned by `sanad`; isolated `pg_acceptance` rebuilt per CI acceptance contract
- `CI=true` so PostgreSQL unavailability FAILS tests instead of silently skipping (fail-closed); result: **Skipped = 0 on every executed batch**
- Batch runner: wipe `target/surefire-reports` → run → archive XML + console log + manifest into an independent evidence directory per run (per-run separation; Surefire 3.5.4 does not honor `-Dsurefire.reportsDirectory`)

### Batch definitions (re-established from code-level analysis of all 78 subscription test classes)

| Batch | Content | Classes |
| --- | --- | --- |
| batch1-unit | Subscription unit tests (no PG references) | 28 |
| batch2a1-pg-lifecycle | Lifecycle controller + change/anchor semantics (PG Direct) | 10 |
| batch2a2-pg-lifecycle | Lifecycle storage/resolution/expiry/trial (PG Direct) | 10 |
| batch2b1-pg-catalog | Catalog runtime, items, plans, provisioning isolation, operating units (PG Direct) | 8 |
| batch2b2-pg-read | Read models, billing queries, usage metering, JWT details, final module acceptance (PG Direct) | 12 |
| batch3-pg-security | Billing R0C13 finance/webhook/settlement/corrective + RBAC forensic 403 (PG Direct) | 9 |
| batch3b-pg-acceptance-rbac | `RbacAccessCheckPostgresAcceptanceTest` with CI acceptance contract (`SPRING_PROFILES_ACTIVE=pg-acceptance`, isolated `pg_acceptance` DB, fresh `CRM_CUSTOM_FIELD_ENCRYPTION_KEY`) | 1 |
| batch4-ws6-ws7-domains | Tenant domain service + website module + store module integration (PG Direct) | 3 |

### Results (Surefire XML, archived per batch)

| Batch | Tests | Failures | Errors | Skipped | Duration |
| --- | --- | --- | --- | --- | --- |
| batch1-unit | 191 | 0 | 0 | 0 | 4.76 s |
| batch2a1-pg-lifecycle | 108 | 0 | 0 | 0 | 348.79 s |
| batch2a2-pg-lifecycle | 100 | 0 | 0 | 0 | 446.17 s |
| batch2b1-pg-catalog | 89 | 0 | 0 | 0 | 191.13 s |
| batch2b2-pg-read | 65 | 0 | 0 | 0 | 87.57 s |
| batch3-pg-security | 72 | 0 | 0 | 15* | 88.84 s |
| batch3b-pg-acceptance-rbac | 15 | 0 | 0 | 0 | 32.49 s |
| batch4-ws6-ws7-domains | 54 | 0 | 0 | 0 | 35.51 s |
| **Backend total (executed)** | **679 executed / 694 scheduled** | **0** | **0** | **0 effective** | **~20.6 min** |

\* The 15 skips in batch3 were `RbacAccessCheckPostgresAcceptanceTest` gating on `@EnabledIfEnvironmentVariable(SPRING_PROFILES_ACTIVE=pg-acceptance)`. They were **not counted as success**: the class was re-run under the exact CI contract (batch3b) and passed 15/15 with zero skips.

Frontend gates: TypeScript `tsc --noEmit` PASS; Vitest **171 files / 1426 tests / 0 failed / 0 skipped**; `next build` PASS (prebuild `validate:integrity` PASS); gitleaks current-tree secret scan (CI parity `--no-git`) **0 leaks**; Python closure contract `tests/ci/test_subscription_1055_comprehensive_closure.py` **12/12 OK**.

## 2. Workstream traceability

### WS1 — Tenant UI recovery — **VERIFIED** (via absorbing PRs)

- **Requirement:** fix modal focus regression (one-character typing), visible labels + governed client-side validation, country/timezone/currency selectors, create/edit tenant regression coverage.
- **Original draft PR:** #1056 (open, draft, not merged — by design).
- **Absorption evidence:** `apps/web/components/sds/Modal.tsx` 100% of added lines present in main; `Modal.test.tsx` 100%; `tenant-management.test.tsx` 75%; `executive/tenants/page.tsx` 42% literal (page was later restructured by merged PR #1156 and wave-1 perf split `fc382d3a` — dialog chunks dynamically imported; semantic behavior preserved, see contract test below).
- **Absorbing PRs (merged into main):** #1138 (2026-09-23), #1156 (2026-09-26, "restore final subscription convergence and #1055 closure").
- **Tests (green):** Vitest suite incl. `tenant-management.test.tsx`, `components/sds/__tests__/Modal.test.tsx` (1426/1426); Python contract `test_ws1_tenant_login_is_audited_and_not_left_blank`, `test_ws1_pending_tenant_can_be_governedly_activated` (12/12 OK).
- **Gap:** none blocking; E2E-level browser proof blocked by environment (see §3).

### WS2 — Application catalog lifecycle — **VERIFIED**

- **Requirement:** create/update UI, no physical delete (governed deactivate/archive), capability-gated mutations.
- **Original draft PR:** #1057 (open, draft). **Absorbed by merged PR #1062** (2026-09-16) — `ApplicationCatalogService.java` 92%, `CatalogController.java` 100%, tests 100%, `applications/page.tsx` 78% (later refinements), i18n 100%; `ScpDtos.java` line absorbed by #1062 commit `8d58f86f`.
- **Tests (green):** `ApplicationCatalogLifecyclePostgresTest` (6), `ApplicationCatalogServiceTest` (13), `ApplicationRequestContractTest` (2), `ProductCatalogRuntimePostgresTest` (29), `ProductCatalogServiceTest` (22); contract `test_ws2_application_catalog_lifecycle_is_governed_and_non_destructive`; migration `V20260914_1__scp_application_catalog_lifecycle.sql` present at HEAD.

### WS3 — Subscription lifecycle integrity — **VERIFIED** (backend + unit + contract)

- **Requirement:** remove generic ACTIVATE/RENEW bypasses; state-aware UI commands; provisioning/renewal authoritative paths only.
- **Original draft PR:** #1058 (open, draft). **Absorbed by merged PR #1156**: `LifecycleControllerCommandBoundaryTest.java` 94% of added lines in main; `ProvisioningJobRunnerTest.java` 100%; `subscription-detail-gating.test.tsx` 46% literal (superseded by #1156 rework + `subscription-lifecycle-actions.test.tsx` in main).
- **Residue note:** #1058's `scp-api.test.ts` "governed renewal / cancelled-resume" literal test is NOT in main (9% match). Functionally superseded: main renews via `executive-api.ts` `renewSubscription → POST /subscriptions/{id}/renew` with UI coverage in `subscription-lifecycle-actions.test.tsx`; `scp-api.ts` no longer carries renew/resume. No functional gap identified; disposition recorded in CLOSURE-REPORT.
- **Tests (green):** `LifecycleControllerCommandBoundaryTest` (13 — generic-route fail-closed 409), `SubscriptionLifecycleTest` (43 transition legality), lifecycle PG batch (2a1: 108, 2a2: 100), contract tests `test_ws3_*` (12/12).

### WS4 — Subscription navigation / RBAC — **VERIFIED**

- **Requirement:** reproduce and fix HTTP 403 on `subscriptions/v2?tenantId=...`; prove EXECUTIVE_VIEW / tenant-control boundary.
- **Tests (green):** `RbacAccessCheckPostgresAcceptanceTest` **15/15 after CI contract re-run** (RB-01…RB-15: real JWT → filter → governance controller → capability aspect → control-plane guard → tenant isolation incl. cross-tenant denial); `G1GDynamic403ForensicPostgresTest` (3); `ExecutiveTenantsV2RoutePrecedenceTest` (4); `ControlPlaneAccessService*` contract tests (3+11).

### WS5 — Billing / read-model correctness — **VERIFIED**

- **Requirement:** billing tenant scoping; seat-quantity pricing; pinned currency; current UTC usage period; localized timestamps.
- **Tests (green):** billing R0C13 suite in batch3 (`G02Schema` 4, `G03FinanceIntegration` 6, `G05Webhook` 9, `G06SettlementReconciliation` 10, `G07Corrective` 12, `G07DisabledReconciliation` 3, `G09FailureInjection` 5); `ExecutiveBillingQueryServicePostgresTest` + service tests; `PinnedPlanVersionCurrencyPostgresTest`; `AnchoredPlanSeatQuantityPostgresTest` (26 — seat-quantity convergence); `UsageMeteringServiceTest` (16 — UTC usage period); read-model batch (2b2: 65).

### WS6 — Tenant / domain provisioning — **VERIFIED**

- **Requirement:** auto-create tenant default subdomain from `SANAD_BASE_DOMAIN`; central host routing authority; custom-domain verify/activate/primary lifecycle; global hostname uniqueness.
- **Tests (green):** subscription provisioning `EntitlementProvisioningIsolationPostgresTest` (8), `ProvisioningJobRunnerTest` (10); **`TenantDomainServiceIntegrationTest` (18) in supplemental batch4** — domain verify/activate/primary lifecycle and uniqueness semantics on the real migration-seeded schema; central routing authority exercised by route-ownership tests (`ExecutiveSubscriptionDetailRouteTest`, `PlanVersionRouteOwnershipTest`, `PriceRouteOwnershipTest`, `ExecutiveTenantsV2RoutePrecedenceTest`).

### WS7 — Website / store monetization — **VERIFIED**

- **Requirement:** auto-generated default domains; multiple websites/stores per tenant; subscription add-ons + entitlement limits.
- **Tests (green):** `WebsiteModuleIntegrationTest` (23), `StoreModuleIntegrationTest` (13) — supplemental batch4 on real PG; entitlement limits `ItemAwareEntitlementResolverTest` (5) + `SubscriptionItemServiceTest` (13) + `SubscriptionItemProductIntegrityTest` (10) in batch2b1.

### WS8 — Branch / operating-unit subscriptions — **VERIFIED**

- **Requirement:** branch model under tenant; branch-scoped applications and entitlements; branch-aware RBAC and usage attribution; PER_BRANCH pricing; consolidated or per-branch billing profiles; sites/stores/POS linked to branch.
- **Tests (green):** `SubscriptionOperatingUnitPostgresTest` (4 — PER_BRANCH pricing semantics, operating-unit model); branch-aware usage attribution `UsageMeteringServiceTest` (16); branch-scoped entitlements `EntitlementProvisioningIsolationPostgresTest` (8); billing profiles exercised in ExecutiveBillingQueryService PG tests.

## 3. Gate-level items not yet provable in this environment

| Item | Status | Precise cause | Resumption |
| --- | --- | --- | --- |
| Playwright standard suite (36 browser cases) | **BLOCKED** | Repo configs pin `channel: "chrome"` (branded Google Chrome at `/opt/google/chrome/chrome`, CI runners have it). Sandbox has no root and read-only `/opt`; PATH shims and cached Chromium do not satisfy Playwright's fixed-path resolution. | Run on a runner with `google-chrome-stable`, or a temporary config override by the controller (deviation from CI parity — controller decision). Harness itself proven: Next production server boots and serves HTTP 200; 6 channel-independent cases passed. |
| `subscription-executive-acceptance.spec.ts` E2E | **BLOCKED** (same root cause) | Same `channel: "chrome"` pinning in `playwright.subscription-acceptance.config.ts`. Backend half of its contract is already proven by batch2/3/3b (lifecycle 409 guard, detail API, RBAC). | Same runner requirement; then follow `.github/workflows/playwright-ci.yml` (backend on 8080 `crm-acceptance` profile, `sanad_test` DB, acceptance seeds, Next on 3001). |
| Human UAT re-run | **UNVERIFIED** | Requires a human operator per Issue #1055 acceptance clause. | Controller schedules with the reviewer account; final closure stays gated. |

## 4. Old draft PRs disposition (checked, not merged)

| PR | State | Finding | Disposition |
| --- | --- | --- | --- |
| #1056 (WS1 tenant modal) | open/draft | 2/4 files 100% absorbed, 2/4 partial via merged #1138/#1156 and `fc382d3a` refactor | **Close as superseded** — do not merge; residual UI polish, if any, rides a new phase PR |
| #1057 (WS2 catalog) | open/draft | Absorbed by merged #1062 (2026-09-16) | **Close as superseded** |
| #1058 (WS3 lifecycle) | open/draft | Test files absorbed by merged #1156; `scp-api.test.ts` renew/resume literal test not carried over; functionally superseded by `executive-api` renew path + `subscription-lifecycle-actions.test.tsx` | **Close as superseded** with the residue note recorded above |

## 5. Known open RED gates (documented, by design, not in CI)

`tests/security/test_users_rls_auth_red_contract.py` (PR #1383) intentionally fails 2 cases documenting pending G1 work (missing-tenant pass-through inside an active transaction; identity-resolution contract naming). CI does not execute this file; recorded here for traceability — **not** a regression introduced by this closure.
