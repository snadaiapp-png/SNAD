# SNAD Subscription Control Plane — Design Specification

- **Date:** 2026-08-29
- **Base SHA:** `a8a7ce4da18f7f1b03e6a54933ff886a3f6484e5` (origin/main)
- **Branch:** `scp/subscription-control-plane`
- **Mission:** Transform the Executive SaaS management surface into a full Subscription
  Control Plane (catalog → plans → versions → prices → subscriptions → items →
  entitlements → usage → provisioning → billing → audit) while preserving every
  existing contract.

---

## 1. Current System (Forensic Facts — Preserve, Do Not Rewrite)

Verified against origin/main `a8a7ce4`:

### Backend (`apps/sanad-platform`, Java 17 / Spring Boot, PostgreSQL Direct)

| Fact | Evidence |
|---|---|
| No JPA entity for `Subscription`/`Plan`; the SaaS admin engine is JdbcTemplate in one service | `admin/service/SaasAdministrationService.java` |
| One subscription per tenant, one plan per subscription | `V19__create_saas_administration.sql` — `tenant_subscriptions` has `UNIQUE(tenant_id)`, `plan_id UUID NOT NULL` |
| Subscription status CHECK: `TRIALING, ACTIVE, PAST_DUE, SUSPENDED, CANCELLED` | same table CHECK constraint |
| Plan catalog `saas_plans` has prices inline (`monthly_price_minor`, `annual_price_minor`), no versions | `V19` |
| Legacy per-plan feature flags `saas_plan_entitlements` (feature_code + enabled + limit_value) | `V19` |
| Module control-plane embryo: `modules`, `module_capabilities`, `plan_module_entitlements` (capability types: `MODULE_ENABLED, FEATURE_ENABLED, NUMERIC_LIMIT, QUOTA, BOOLEAN_CAPABILITY`) | `V20260814_1`, `V20260814_2` |
| Entitlement resolution chain: Tenant → active subscription → plan → `plan_module_entitlements` (fallback `module_capabilities.default_value`) → `ModuleCapabilityContext` | `module/entitlement/EntitlementResolver.java` — this is the Source of Truth; `tenant_entitlement_cache` is declared dead |
| Subscription events fire entitlement recalculation after commit | `SubscriptionEntitlementListener.java` |
| Money = `BIGINT` minor units + `VARCHAR(3) currency_code`; `long` arithmetic | `saas_plans`, `billing_invoices`, `SaasAdministrationService.prorate(...)` |
| Executive API base `/api/v1/executive`, capabilities `EXECUTIVE_VIEW` / `EXECUTIVE_MANAGE` / `EXECUTIVE_BILLING`, guarded additionally by `ControlPlaneAccessGuard` | `executive/api/*Controller.java` |
| RBAC: `AccessCapability`, `Role`, `RoleCapability`, `UserRoleGrant`, `CapabilityEvaluationService`, `@RequireCapability` aspect (deny-by-default, audited) | `access/**`, `security/authorization/**` |
| Audit: `platform_audit_logs` (V17) + `PlatformAuditWriter` / `PlatformAuditService` | `admin/service/*` |
| RLS: `app.tenant_id` GUC (transaction-local), fail-closed policies, FORCE RLS; Java-side `TenantRlsTransactionContext` | `security/rls/**`, CRM migrations |
| Billing state machine: `BillingStateService.evaluateAndTransition` (PAST_DUE→CURRENT etc.) | `admin/service/BillingStateService.java` |
| Subscription impact preview already exists (upgrade/downgrade) | `module/lifecycle/SubscriptionImpactService.java` |
| No usage metering subsystem (only `TenantQuota` counters) | `scale/domain/TenantQuota.java` |
| Migrations: 108 files in `db/migration` + 27 PostgreSQL-only in `db/vendor/postgresql`; highest version `V20260828_1` | `src/main/resources/db/` |
| No global response envelope; no standard pagination (query controllers return `List<>`); record DTOs; Jakarta validation; manual mapping | `executive/api/**` |
| Tests: JUnit 5 + AssertJ + Mockito; PostgreSQL Direct integration tests against the CI-provided service (Docker/Testcontainers removed in CI v20260816.10); pg-acceptance tests env-guarded | `.github/workflows/ci.yml`, `src/test/java/**` |

### Frontend (`apps/web`, Next.js 16.2.11 / React 19 / Tailwind 4 / App Router)

| Fact | Evidence |
|---|---|
| `/executive` is a single client page; navigation is `useState` tab bar, not routes | `app/executive/executive-console.tsx` |
| **Bug:** tenant code column renders `t.name` (duplicated) | `executive-console.tsx` line ~136 |
| **Bug:** `DirectoryTab` is hardwired to `snapshot.tenants[0]` | `executive-console.tsx` lines 145–167 |
| `executive.module.css` defines classes the console never uses (stale stylesheet, console effectively unstyled) | `app/executive/executive.module.css` vs component class references |
| Console bypasses SDS primitives (`components/sds`) and i18n (`useI18n().t`) — hardcoded Arabic | same file |
| Entitlements UI exists and is reusable | `app/executive/modules-entitlements-panel.tsx` |
| `previewSubscriptionImpact` / `previewModuleReset` exist as API contracts with no UI consumer | `lib/api/executive-api.ts` |
| API access via typed `ApiClient` (`lib/api/client.ts`), same-origin BFF `/api/platform`, in-memory bearer + auto-refresh | `lib/api/config.ts`, `lib/auth/auth-provider.tsx` |
| i18n: custom `I18nProvider`, `t(key)`, `locales/ar.ts` + `locales/en.ts`, CI key-parity script | `lib/i18n/**`, `scripts/ci/check-i18n-keys.py` |
| Dark theme: `data-theme` + `design-system/tokens/theme.css` tokens; RTL via `<html dir>` + CSS logical properties | `app/layout.tsx`, design-system docs |
| No UI/icon/chart libraries — in-house SDS + Tailwind 4 | `apps/web/package.json` |
| Web CI gates: SDS compliance, logo governance, brand governance, lint, vitest, build, performance budget | `.github/workflows/web-ci.yml` |

---

## 2. Target Domain Model

```
Application (catalog)
  └── 0..N Products (APPLICATION | ADD_ON | METERED | OTHER)
Plan (product-agnostic sellable package; legacy saas_plans preserved)
  └── 1..N PlanVersions (effectiveFrom/To, status DRAFT|ACTIVE|RETIRED, pricing, entitlements)
        └── 1..N Prices (country, currency, interval, model, tiers, min/max, effective dates)
Tenant
  └── 0..N Subscriptions (state machine; 0..N items)
        └── 1..N SubscriptionItems (PLAN | ADD_ON | METERED | OTHER; each may pin a PlanVersion)
              └── → Entitlement Resolution (existing engine, generalized)
UsageMetrics / UsageEvents / UsageAggregates  (metering foundation)
ProvisioningJobs (idempotent, retry-safe)
platform_audit_logs (existing — reused)
```

Key decisions:

1. **Additive, not destructive.** `saas_plans` and `tenant_subscriptions` keep their
   columns (`plan_id` stays). New capability is layered through new tables and nullable
   columns; reads migrate to the new model behind services; deprecation is a separate
   later effort.
2. **Plan versioning.** A plan edit creates/edits a *draft version*; subscribers stay on
   the version they contracted. `tenant_subscriptions.plan_version_id` (nullable,
   backfilled to the plan's current ACTIVE version) becomes the authoritative pricing
   + entitlement anchor once populated.
3. **Subscription items.** `subscription_items` carries the 0..N billable lines
   (application plans, add-ons, metered products). Legacy single-plan subscriptions are
   backfilled as exactly one `PLAN` item. `plan_id` on the subscription remains until
   the new model is authoritative (dual-compatible read).
4. **Entitlement engine — one engine.** The existing `EntitlementResolver` remains the
   Source of Truth. It is *generalized*: item-derived entitlements (add-ons, metered
   products) are merged into the effective context during resolution. No competing
   engine is introduced. Entitlement keys stay namespaced capability codes
   (`hr.payroll.enabled`, `ai.monthly_tokens`, …) — never plan-code comparisons.
5. **Money.** `BIGINT` minor units + ISO `currency_code` everywhere (project standard).
   No floating point.
6. **Catalog-driven UI.** Applications/plans/products come from the catalog API. No
   hardcoded ERP/CRM/HRM/POS/AI lists in React. New applications require zero
   navigation-code changes.

## 3. Subscription Lifecycle (G3)

States: `DRAFT, PENDING_ACTIVATION, PENDING_PAYMENT, TRIAL, ACTIVE, PAST_DUE,
GRACE_PERIOD, PAUSED, SUSPENDED, CANCELLED, EXPIRED, TERMINATED`
(legacy values `TRIALING` maps to `TRIAL`; the existing CHECK constraint is widened
additively; old values remain valid).

Transitions are enforced **only** through backend domain commands:
`activate, startTrial, pause, resume, scheduleCancellation, cancel, renew, suspend,
expire, terminate` validated by a single `SubscriptionLifecycle` transition table
(unit-tested). The frontend never writes `status` directly — it invokes commands.
Existing commands (`change-plan`, `seats`, `cancel`, `resume`, `renew`) are preserved
as aliases over the new state machine (backward compatible).

## 4. Pricing Engine (G2)

`prices` table supports models: `FLAT, PER_USER, PER_EMPLOYEE, PER_BRANCH,
PER_TRANSACTION, PER_API_REQUEST, PER_AI_TOKEN, TIERED, VOLUME, USAGE_BASED,
HYBRID, CUSTOM_CONTRACT` (CHECK constraint; extensible via new migration only).
Price dimensions: country (`ISO 3166-1 alpha-2` + `GLOBAL`), currency, billing
interval, base/unit amount, tier JSONB (`{up_to, unit_amount_minor}` rows), min/max,
effective window. Country→currency default mapping table `country_currencies`
(seed: SA→SAR, AE→AED, KW→KWD, GLOBAL→USD — seed data is catalog data, not code).
Tier evaluation is pure-function and unit-tested (TIERED marginal vs VOLUME cumulative).

## 5. Usage Metering (G4)

`usage_metrics` (code, unit, aggregation), `usage_events` (tenant, metric, quantity,
source, idempotency_key UNIQUE, occurred_at; tenant-scoped + auditable),
`usage_aggregates` (tenant, metric, period, sum). Limit semantics per entitlement:
`UNLIMITED, SOFT_LIMIT, HARD_LIMIT, OVERAGE, PAY_AS_YOU_GO` + warning thresholds.
Read path joins aggregates with effective entitlement limits → usage read model.
Event ingestion is idempotent on `(tenant_id, metric_code, idempotency_key)`.

## 6. Provisioning (G3)

`provisioning_jobs` (`id, tenant_id, subscription_id, action, status
PENDING|RUNNING|SUCCEEDED|FAILED|RETRYING, attempts, started_at, completed_at,
error_code, error_message, correlation_id`). A subscription becomes ACTIVE only after
its provisioning job SUCCEEDS (activation command enqueues; job runner enables
applications from items → recalculates entitlements → validates). Steps are keyed for
retry safety (`UNIQUE(job, step)` progress ledger). Retry endpoint is capability-guarded
and audited.

## 7. RBAC (G4)

New granular capability codes seeded additively (existing `EXECUTIVE_*` codes stay
valid and are still granted to current roles — zero regression):
`subscription.read|create|change_plan|cancel|suspend`, `catalog.read|manage`,
`application.read|manage`, `plan.read|manage`, `pricing.read|manage`,
`entitlement.read|manage|override`, `usage.read`, `billing.read|adjust`,
`provisioning.read|retry`, `audit.read`.
New endpoints declare the granular capability via `@RequireCapability`; the
control-plane tenant guard stays. UI nav visibility follows the access-check payload.

## 8. Executive API (G5) — Read Models & Scale

New endpoints (additive; existing endpoints untouched):

```
GET /api/v1/executive/overview                     — dashboard read model (MRR/ARR/trials/past-due/renewals when supported)
GET /api/v1/executive/applications                 — catalog list (+active subscription counts where available)
POST/PUT /api/v1/executive/applications[/{id}]     — manage
GET /api/v1/executive/plans/{id}/versions          — plan versions
POST /api/v1/executive/plans/{id}/versions         — create version
GET /api/v1/executive/subscriptions/v2             — paginated grid (search, filters: status/country/plan/application/trial, sort, page)
GET /api/v1/executive/subscriptions/{id}           — detail read model (items, entitlements, usage, invoices, changes, provisioning, audit)
POST /api/v1/executive/subscriptions/{id}/items    — add item / change via preview+confirm
GET  /api/v1/executive/subscriptions/{id}/change-preview?target=... — extended impact preview
POST /api/v1/executive/subscriptions/{id}/changes  — confirm+execute a previewed change
POST /api/v1/executive/subscriptions/{id}/lifecycle/{command} — domain commands
GET /api/v1/executive/tenants/v2                   — paginated tenants (search/status/country/sort)
GET /api/v1/executive/usage?tenantId=…             — usage read model
GET /api/v1/executive/provisioning/jobs            — job list (filter by status/tenant)
POST /api/v1/executive/provisioning/jobs/{id}/retry
GET /api/v1/executive/audit?…                      — paginated audit query
```

Pagination contract (new, additive): `?page=0&size=20&sort=field,dir` →
`{ content: [...], page, size, totalElements, totalPages }` (record `PageResponse<T>`).

## 9. Executive UI (G6)

Route-based IA (App Router), shared `/executive` layout with sidebar + header:

```
/executive                 Overview
/executive/applications    Catalog cards
/executive/tenants         Tenant grid (fixed code column, server-side pagination)
/executive/subscriptions   Data grid + saved-view-ready filters
/executive/subscriptions/[id]  Detail (items, entitlements, usage, invoices, changes, provisioning, audit)
/executive/plans           Plans & Pricing (Plan ≠ Version ≠ Price ≠ Entitlement, explicit)
/executive/entitlements    Upgraded ModulesEntitlementsPanel concepts + tenant searchable selector
/executive/usage           Metric gauges (current/limit/%/period/reset/warning)
/executive/billing         Invoices (existing semantics, add filters)
/executive/provisioning    Jobs + retry
/executive/audit           Audit timeline
```

- Sidebar on desktop, collapsible on tablet, drawer on mobile; tables become card
  lists on small screens (not just `overflow-x: auto`).
- SDS primitives (`components/sds`) + tokens only; all strings via `t()` added to
  **both** `locales/ar.ts` and `locales/en.ts`; skeleton/empty/error/success states
  everywhere; keyboard + ARIA per design-system docs; route-level data fetching
  (each page fetches only its own scope; no `Promise.all` mega-load).
- Legacy `executive-console.tsx` is retired only after the new pages pass their gates.

## 10. Backward Compatibility Strategy

- Existing endpoints `/api/v1/executive/**` (plans, subscriptions, invoices, tenants,
  organizations, memberships, modules, entitlements, previews) keep their contracts.
- New read paths are new routes (`/v2`, `/overview`, `/applications`, …) or additive
  DTO fields — no removed/renamed fields.
- `tenant_subscriptions.plan_id` is never dropped in this effort.
- The legacy console keeps working until G6 cutover; i18n key parity script guards
  both locales.

## 11. Test Strategy (TDD per workstream)

- **Domain:** lifecycle transition legality; multi-item subscriptions; plan-version
  pinning; tiered/volume price evaluation; entitlement merge (items → effective);
  usage aggregate + limit evaluation; idempotent usage ingestion; provisioning retry.
- **Database:** migrations apply cleanly on a fresh PostgreSQL 16 (CI `test` job
  runs Flyway against the real service); constraints/indexes verified by
  integration tests following the CRM Postgres-test pattern.
- **API:** pagination/filtering; detail read model; lifecycle commands; authorization
  (granular capabilities; control-plane guard).
- **Security:** unauthorized access → 403; cross-tenant isolation on new tenant-scoped
  tables (RLS where the table pattern requires it; catalog tables are platform-scoped
  like `saas_plans`).
- **Web:** vitest for components/hooks (navigation, filters, tenant selector, states,
  RTL via logical-property markup), colocated per repo convention.
- CI gates: `ci.yml` (Maven Test Suite + CRM required job) and `web-ci.yml` must be
  green on the PR; no skipped/quarantined tests; failures proven against BASE_SHA
  before being attributed as pre-existing.

## 12. Non-Goals (Deferred)

- Full billing engine rewrite (accounting stays in finance module; this effort defines
  the integration contract only).
- Payment gateway integration changes.
- Event broker / outbox for cross-module integration (Constitution marks event-driven
  as PLANNED).
- Deleting legacy `plan_id` / `saas_plan_entitlements` (deprecation later, with its own ADR).
