# R0C-10 Implementation Plan — Subscription Multiplicity Runtime

Date: 2026-09-06
Branch: `scp/r0c-10-subscription-multiplicity-runtime`
Base: `R0C9_FINAL_HEAD=13c144e2a23bbdb34325b13f664489fb9440bc0c`
Design authority: `docs/superpowers/specs/2026-09-06-scp-r0c-10-subscription-multiplicity-design.md`

---

## 1. Pre-Flight (executed evidence)

- `refs/heads/scp/r0c-9-expired-continuation-contract` = `13c144e2a23bbdb34325b13f664489fb9440bc0c` (remote, noninteractive fetch)
- Ancestry: `37c880de5c0e63724f0fd40dafc47ec7739a8d0f` → `0d3124520252310978f352a90258b25e8aef21ba` → `13c144e2a23bbdb34325b13f664489fb9440bc0c` (merge-base proven)
- `R0C10_BRANCH_DURABILITY=PASS` (LOCAL_HEAD = REMOTE_HEAD = `13c144e2…`)

## 2. Forensic Inventory — exact repository paths

| Ref | Path |
|-----|------|
| A. subscription table | `tenant_subscriptions` (DDL: `apps/sanad-platform/src/main/resources/db/migration/V19__create_saas_administration.sql:38`) |
| B. subscription repository | No JPA entity — raw `JdbcTemplate` SQL in services; item model: `subscription/item/SubscriptionItemRepository.java`; new R0C-10 query component: `subscription/lifecycle/SubscriptionResolutionService.java` |
| C. lifecycle service | `subscription/lifecycle/SubscriptionCommandService.java` (canonical single writer, R0C-7) + `subscription/lifecycle/SubscriptionLifecycle.java` (transition table) |
| D. create guard | `admin/service/SaasAdministrationService.createSubscription` (line ~293: `COUNT(*) … WHERE tenant_id = ?` → CONFLICT "Tenant already has a subscription") |
| E. resume operation | `admin/service/SaasAdministrationService.resumeSubscription` (legacy endpoint) + canonical `RESUME` command |
| F. lifecycle event/change-event writer | `SaasAdministrationService.recordEvent` (`subscription_change_events`) + `SubscriptionCommandService` (`subscription_commands` ledger) |
| G. audit writer | `admin/service/PlatformAuditService.java` / `PlatformAuditWriter.java` |
| H. entitlement recalculation | `module/entitlement/SubscriptionEntitlementListener.java` → `EntitlementResolver.recalculateEntitlements` |
| I. provisioning | `subscription/provisioning/ProvisioningJobRunner.java` + `provisioning_jobs`/`provisioning_job_steps` (V20260830_1); job creation: `subscription/api/LifecycleController.java:140` |
| J. BillingStateService | `admin/service/BillingStateService.java` |
| K. invoice/dunning queries | `SaasAdministrationService.issueInvoice/listInvoices` + `BillingStateService.countOverdueInvoices`; schema: `billing_invoices` (V19, `subscription_id UUID NOT NULL` + FK) |
| L. EntitlementResolver | `module/entitlement/EntitlementResolver.java` |
| M. SubscriptionImpactService | `module/lifecycle/SubscriptionImpactService.java` |
| N. TenantDirectoryAdministrationService | `admin/service/TenantDirectoryAdministrationService.java` |
| O. other tenant→subscription lookups | `subscription/read/*` (Grid/Detail/ExecutiveOverview/TenantDirectoryQuery), `health/service/HealthIntelligenceService.java`, `subscription/usage/UsageMeteringService.java`, `subscription/plan/PlanVersionService.java` (javadoc only), `module/lifecycle/ModuleReset{Service,Registry}.java` (table registries, not lookups) |
| P. migration introducing `uk_tenant_subscriptions_tenant` | `V19__create_saas_administration.sql:57` |
| Q. indexes/constraints on `tenant_subscriptions` | `pk_tenant_subscriptions` (id), `uk_tenant_subscriptions_tenant UNIQUE (tenant_id)`, FKs tenant/plan/pending_plan/plan_version (V20260829_2), `ck_tenant_subscriptions_status` (widened V20260830_1: 13 values), `idx_tenant_subscriptions_status` (status), `idx_tenant_subscriptions_billing_state (tenant_id, billing_state)` (V20260815_20), `idx_tenant_subscriptions_plan_version (plan_version_id)` (V20260829_2) |
| R. feature-flag conventions | `sanad.tenancy.billing.dunning-enabled` / `SANAD_DUNNING_ENABLED` (BillingStateService), `sanad.tenancy.billing.trial-expiry-enabled` / `SANAD_TRIAL_EXPIRY_ENABLED` (TrialExpirationService) — system property with env fallback, default `false`; global `scheduling.enabled` (SchedulingConfig) |

## 3. Consumer Lookup Classification (production code)

Legend: EFFECTIVE_ONLY / ALL_HISTORY / EXACT_SUBSCRIPTION_ID / AMBIGUOUS.

| # | Site (class#method) | Lookup | Classification |
|---|---------------------|--------|----------------|
| 1 | SaasAdministrationService#createSubscription guard | `COUNT(*) WHERE tenant_id` | **AMBIGUOUS** → MODEL_B guard |
| 2 | SaasAdministrationService#listSubscriptions | `WHERE tenant_id ORDER BY created_at DESC` | ALL_HISTORY |
| 3 | SaasAdministrationService#getSubscription | `WHERE s.id` | EXACT_SUBSCRIPTION_ID |
| 4 | SaasAdministrationService#updatePlan usage scans | `WHERE plan_id AND status IN (TRIALING,ACTIVE,PAST_DUE,SUSPENDED)` MAX aggregates | EFFECTIVE_ONLY (deterministic aggregates) |
| 5 | SaasAdministrationService#resumeSubscription / renew / changePlan / changeSeats / cancel | `getSubscription(id)` | EXACT_SUBSCRIPTION_ID |
| 6 | SubscriptionCommandService#readSubscription/applyCanonicalTransition | `WHERE id` / `WHERE id AND status` | EXACT_SUBSCRIPTION_ID |
| 7 | TrialExpirationService#expireIfStillDue recheck | `WHERE id FOR UPDATE` | EXACT_SUBSCRIPTION_ID |
| 8 | TrialExpirationService#findDueTrials | `status IN (TRIAL,TRIALING) AND trial_ends_at <= ? ORDER BY … LIMIT 200` | EFFECTIVE_ONLY (deterministic scan) |
| 9 | LifecycleController (job creation, tenant resolve) | `WHERE id` | EXACT_SUBSCRIPTION_ID |
| 10 | ProvisioningJobRunner (all steps + job tables) | `WHERE id` / `subscription_id` | EXACT_SUBSCRIPTION_ID |
| 11 | ItemAwareEntitlementResolver / SubscriptionItemRepository / SubscriptionItemService | by `subscription_id` | EXACT_SUBSCRIPTION_ID |
| 12 | SubscriptionChangeService (composition/anchors) | by `subscription_id` | EXACT_SUBSCRIPTION_ID |
| 13 | SubscriptionDetailService | `WHERE s.id` | EXACT_SUBSCRIPTION_ID |
| 14 | SubscriptionGridQueryService | filtered listing + COUNT | ALL_HISTORY |
| 15 | ExecutiveOverviewService | COUNT/MRR aggregates by status | ALL_HISTORY (deterministic aggregates) |
| 16 | TenantDirectoryQueryService#subscription_count | `COUNT(*) WHERE s.tenant_id = t.id` | ALL_HISTORY |
| 17 | TenantDirectoryQueryService#subscription_status | `ORDER BY s.created_at DESC LIMIT 1` | ALL_HISTORY (deterministic latest — existing contract) |
| 18 | HealthIntelligenceService#tenantHealth | `LEFT JOIN … MAX(seat_quantity)` | ALL_HISTORY (deterministic aggregate) |
| 19 | TenantDirectoryAdministrationService#limits | `WHERE tenant_id AND status IN (TRIALING,ACTIVE,PAST_DUE)` → get(0) | EFFECTIVE_ONLY (subset of non-terminal; ≤1 by invariant; deterministic ordering added defensively) |
| 20 | UsageMeteringService (plan + item branches) | `WHERE ts.tenant_id AND status IN (ACTIVE,TRIALING,TRIAL)` + deterministic MAX | EFFECTIVE_ONLY (deterministic aggregate) |
| 21 | BillingStateService#findSubscription | `SELECT billing_state WHERE tenant_id` → get(0) | **AMBIGUOUS** → EFFECTIVE |
| 22 | BillingStateService#applyTransition SELECT | `WHERE tenant_id` findFirst | **AMBIGUOUS** → EFFECTIVE |
| 23 | BillingStateService#applyTransition UPDATE | `UPDATE … WHERE tenant_id` | **AMBIGUOUS** → by subscription_id |
| 24 | BillingStateService#countOverdueInvoices | `billing_invoices WHERE tenant_id` | **AMBIGUOUS** → by subscription_id |
| 25 | BillingStateService#runDunningCycleOnce scan | `WHERE billing_state IN (…)` | EFFECTIVE_ONLY + terminal-status exclusion (converged) |
| 26 | EntitlementResolver#findActiveSubscription | `WHERE tenant_id AND status='ACTIVE' LIMIT 1` (arbitrary) | **AMBIGUOUS** → EFFECTIVE + ACTIVE gate |
| 27 | SubscriptionImpactService#getCurrentPlanCode | `WHERE tenant_id AND status='ACTIVE' LIMIT 1` (arbitrary) | **AMBIGUOUS** → EFFECTIVE + ACTIVE gate |

```
R0C10_CONSUMER_INVENTORY=27
R0C10_AMBIGUOUS_LOOKUPS=7   (sites 1, 21, 22, 23, 24, 26, 27 — all converged in Task B)
R0C10_UNKNOWN_LOOKUPS=0
```

## 4. Flyway Inventory (Phase 5)

- Branch max occupied version: `V20260901_1` (115 migrations total)
- `CURRENT_ORIGIN_MAIN_HEAD=539439051c350bdfcdd4ddf9b9594178f0477aa2`
- origin/main max occupied version: `V20260904_1` (main carries 8 extra Workflow-Y2 migrations: V20260902_1..7, V20260904_1; shared set content-identical)
- Duplicate versions across both inventories: `R0C10_FLYWAY_DUPLICATE_VERSION_COUNT=0`
- Selected: `R0C10_SELECTED_MIGRATION_VERSION=V20260906_1` (fresh/unused on both lineages; follows `V<YYYYMMDD>_<seq>` convention; `V20260906_1__scp_subscription_multiplicity_model_b.sql`)

## 5. TDD Task Sequence

| Task | Scope | RED evidence | GREEN evidence |
|------|-------|--------------|----------------|
| A | `SubscriptionResolutionService` effective/history contract | new PG test fails against stub (no effective/history semantics) | focused `mvn -Dtest=SubscriptionResolutionPostgresTest` |
| B | Converge 7 ambiguous lookups (billing ×4, entitlement, impact, creation guard) | new/updated PG tests fail on current arbitrary lookups | focused consumer tests |
| C | Migration V20260906_1 + DB invariant battery | old schema rejects multiplicity (legacy UNIQUE proven RED) | migration tests + inventory assertions |
| D | Gated EXPIRED successor creation | gate ON successor test fails pre-implementation | focused successor tests |
| E | No second automatic trial | successor trial test fails pre-implementation | focused test |
| F | CANCELLED/TERMINATED semantics | collision/deferral tests fail pre-implementation | focused tests |
| G | resume(EXPIRED) fail-closed (P1 fix) | false-success defect test fails (PG-03b supersession) | focused test |
| H | Billing subscription_id isolation | overdue historical invoice dunn test fails pre-fix | focused test |
| I | Entitlement/provisioning convergence | effective-resolution tests fail pre-change | focused tests |
| J | Concurrent successor creation | race test (real threads + real PG) pre-implementation | focused test |
| — | RLS/tenant isolation | cross-tenant negative tests | focused tests |

Focused runs use `mvn test -Dtest=<Class> -B -ntp` per class during development;
the final gate is ONE complete canonical `mvn test` invocation (Phase 23).

## 6. Superseded R0C-9 Forensic Assertions (documented, not hidden)

`ExpiredContinuationDeadEndPostgresTest` (forensic evidence at `13c144e2…`,
preserved in git history):

- **PG-03b** asserted the `resume(EXPIRED)` false-success (silent no-op +
  misleading `SUBSCRIPTION.RESUMED` event). R0C-10 Task G fixes exactly this
  defect → PG-03b is superseded in place by the fail-closed assertion with an
  explanatory note.
- **PG-06** asserted the legacy `UNIQUE (tenant_id)` storage fact. R0C-10
  replaces that constraint with the partial unique index → PG-06 is superseded
  in place by the new-invariant assertion with an explanatory note.
- **PG-07** asserted the unqualified billing tenant lookup + stale
  `billing_state` dunning scan exposure. R0C-10 converges billing to
  effective-only, subscription-scoped resolution → PG-07 is superseded in
  place with an explanatory note.
- PG-01/02/03/04/04b/05/05b/08/09 remain valid under gate-OFF fail-closed
  behavior and stay unmodified (PG-02's wire message is preserved by design
  when the gate is OFF).

## 7. Commit Sequence (forward-only, explicit paths only)

1. `docs(scp): R0C-10 design and implementation plan`
2. `test(scp): effective/history resolution contract (Task A)`
3. `feat(scp): converge ambiguous consumers to effective semantics (Task B)`
4. `test(db): MODEL_B multiplicity migration V20260906_1 (Task C)`
5. `fix(scp): resume(EXPIRED)/resume(TERMINATED) fail closed (Task G)`
6. `feat(scp): gated EXPIRED successor runtime + no-second-trial (Tasks D/E)`
7. `fix(scp): CANCELLED/TERMINATED collision + deferral semantics (Task F)`
8. `fix(scp): billing/entitlement/provisioning subscription_id isolation (Tasks H/I)`
9. `test(scp): concurrency + RLS + R0C-10 acceptance suite (Task J)`
10. `docs(scp): rollout/rollback runbook`
11. `docs(scp): final R0C-10 certification`

Exact count may vary with coherent TDD units; history stays reviewable.

## 8. Environment (unchanged R0C-9 provisioning)

- PostgreSQL 16.2 real server binaries, `127.0.0.1:5433`, bootstrap actor `pgadmin`
- Application role `sanad`: NOSUPERUSER / NOCREATEDB / NOCREATEROLE / NOBYPASSRLS
- DBs: `sanad`, `test_migration`, `pg_acceptance`; isolated per-test schemas via
  `MigrationTestSchemaSupport`; `Crm009TestEnvironment.requirePostgreSqlDirectOrSkip`
- No Docker / Testcontainers / H2 anywhere
