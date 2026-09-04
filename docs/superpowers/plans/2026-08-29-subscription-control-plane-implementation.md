# SNAD Subscription Control Plane — Implementation Plan

- **Date:** 2026-08-29
- **Spec:** `docs/superpowers/specs/2026-08-29-subscription-control-plane-design.md`
- **Base SHA:** `a8a7ce4da18f7f1b03e6a54933ff886a3f6484e5`
- **Branch:** `scp/subscription-control-plane` (PR → main)
- **Execution:** sequential workstreams SCP-G1 → SCP-G7; each ends with its mandatory
  gate (build + tests locally where runnable, CI authoritative for PostgreSQL Direct
  suite). No G(n+1) while G(n) has a failing mandatory gate.

## Environment Contract

- Backend: `cd apps/sanad-platform && mvn -q -DskipTests compile` for fast loop;
  `mvn test` for the suite. PostgreSQL Direct suite runs in CI (local Postgres on this
  machine is not provisioned for the repo's test roles; Docker is not running — do not
  add Testcontainers per governance).
- Frontend: `cd apps/web && npm run lint && npm test && npm run build` + the three
  python governance scripts under `scripts/ci/` (SDS compliance, logo, brand) and
  `scripts/ci/check-i18n-keys.py`.
- Migration numbering: date-stamped `V20260829_N__scp_*.sql` (next free sequence;
  highest existing is `V20260828_1`). Forward-only, append-only.

---

## SCP-G1 — Catalog, Plan Versioning, Subscription Items (foundation)

1. **Migrations (3):**
   - `V20260829_1__scp_applications_catalog.sql` — `applications` (UUID PK, code
     UNIQUE, name, localizedName, description, category, status, version, displayOrder,
     iconKey, provisioningMode, supportedCountries JSONB, dependencies JSONB,
     timestamps) + seed from existing `modules` registry (code/name/description/order)
     so the catalog is data-backed from day one.
   - `V20260829_2__scp_products_and_plan_versions.sql` — `products` (application_id
     nullable FK, code, name, productType CHECK(APPLICATION|ADD_ON|METERED|OTHER),
     status, timestamps, UNIQUE(code)); `plan_versions` (plan_id FK→saas_plans,
     versionNumber, status CHECK(DRAFT|ACTIVE|RETIRED), effectiveFrom/To, currencyCode,
     monthlyPriceMinor, annualPriceMinor, trialDays, maxUsers, maxOrganizations,
     storageMb, timestamps, UNIQUE(plan_id, versionNumber),
     partial-unique one ACTIVE version per plan) + backfill: one ACTIVE `v1` per
     existing plan cloned from `saas_plans` columns; `ALTER TABLE tenant_subscriptions
     ADD COLUMN plan_version_id UUID NULL REFERENCES plan_versions` + backfill to the
     plan's active version.
   - `V20260829_3__scp_subscription_items.sql` — `subscription_items` (tenant_id NOT
     NULL + index, subscription_id FK, itemType CHECK(PLAN|ADD_ON|METERED|OTHER),
     application_id/product_id/plan_id/plan_version_id nullable FKs, name snapshot,
     quantity INT ≥1, unitAmountMinor BIGINT NULL, currencyCode, status
     CHECK(ACTIVE|PENDING|CANCELLED), periodStart/periodEnd, timestamps) + backfill:
     one ACTIVE PLAN item per existing subscription (from its plan + version).
2. **Domain/services (new package `com.sanad.platform.subscription`):**
   - `catalog/` — `ApplicationCatalogService` (CRUD + list), JdbcTemplate repositories
     following `module/registry` style.
   - `plan/` — `PlanVersionService` (create draft → activate → retire; resolve version
     for date; subscriber pinning semantics).
   - `item/` — `SubscriptionItemService` (add/update/cancel items; list by
     subscription; dual-compatible read helper `effectivePlanVersion(subscriptionId)`).
3. **API (additive):** `/api/v1/executive/applications` GET/POST/PUT,
   `/api/v1/executive/products` GET, `/api/v1/executive/plans/{id}/versions`
   GET/POST, `/api/v1/executive/subscriptions/{id}/items` GET/POST/PATCH — guarded by
   `ControlPlaneAccessGuard` + `@RequireCapability` (existing `EXECUTIVE_VIEW` /
   `EXECUTIVE_MANAGE`; granular codes arrive in G4 without changing signatures).
4. **Tests (write first, prove fail → green):**
   - `PlanVersionServiceTest` — pinning (new version does not mutate subscribers),
     single-ACTIVE invariant, date resolution.
   - `SubscriptionItemServiceTest` — multi-item subscription, backfill read
     compatibility, cancel semantics.
   - `ApplicationCatalogServiceTest` — catalog CRUD + code uniqueness.
   - DB integration: migration chain applies (CI `test` job proves the full Flyway
     chain incl. new files on PostgreSQL 16).
5. **Gate G1:** backend compiles; new tests green; `mvn -q -DskipTests compile` clean;
   commit(s) `feat(subscription): ...`.

## SCP-G2 — Pricing, Country/Currency, Entitlement Integration

1. Migration `V20260829_4__scp_prices_and_country_currencies.sql` — `prices` (owner
   planVersionId or productId, model CHECK(12 models), countryCode (2-letter or
   GLOBAL), currencyCode, billingInterval CHECK(MONTHLY|ANNUAL), baseAmountMinor,
   unitAmountMinor, tiers JSONB, minAmountMinor/maxAmountMinor NULL, effectiveFrom/To,
   timestamps) + `country_currencies` (countryCode PK, currencyCode, isDefault) +
   seed SA/AE/KW/GLOBAL; index (owner, countryCode, billingInterval, effectiveFrom).
2. `pricing/` — `PriceResolver` (pick effective price for country/currency/interval;
   fallback chain country → GLOBAL), `PriceCalculator` (FLAT, PER_USER (quantity×unit),
   TIERED marginal, VOLUME cumulative, USAGE_BASED; pure functions, `long` math),
   `Money` guard tests (no float).
3. Entitlement integration (extend, don't replace): `EntitlementResolver` gains an
   item-aware merge — after plan-derived context, merge ADD_ON/METERED item
   entitlements (from `plan_module_entitlements`-style rows attached to products via
   `product_entitlements` table in same migration) with additive limits (max of limits,
   OR of booleans), same `ModuleCapabilityContext` output. Existing plan-only path must
   behave byte-identically when no items exist (regression tests prove it).
4. Tests: `PriceCalculatorTest` (tier boundaries, volume, overage-ready),
   `PriceResolverTest` (country fallback, effective dates),
   `EntitlementResolverItemsTest` (merge semantics + no-item regression).
5. **Gate G2:** tests green, compile clean, commit `feat(pricing): ...`,
   `feat(entitlements): ...`.

## SCP-G3 — Lifecycle, Change Engine, Provisioning

1. Migration `V20260830_1__scp_lifecycle_and_provisioning.sql` — widen
   `tenant_subscriptions.status` CHECK additively (new states added; old preserved) +
   `subscription_commands` audit-friendly change ledger (id, subscriptionId, command,
   fromStatus, toStatus, effectiveDate, reason, actorUserId, correlationId, createdAt)
   + `provisioning_jobs` (+ `provisioning_job_steps` with UNIQUE(job_id, step_key)).
2. `lifecycle/` — `SubscriptionLifecycle` (transition table + `assertTransition`),
   `SubscriptionCommandService` (activate, startTrial, pause, resume,
   scheduleCancellation, cancel, renew, suspend, expire, terminate) — each validates,
   persists, emits existing entitlement events, writes `PlatformAuditWriter`.
   Legacy service methods delegate (no behavior change for old callers).
3. `change/` — extend `SubscriptionImpactService` into item-aware
   `SubscriptionChangeService`: preview (current items vs target, price delta via G2
   calculator, entitlement additions/losses, proration, warnings, destructive effects)
   → confirm executes within one transaction + audit.
4. `provisioning/` — `ProvisioningJobRunner` (PENDING→RUNNING→SUCCEEDED/FAILED/
   RETRYING; idempotent steps: enable applications from ACTIVE items → resolve
   entitlements → validate; keyed progress; retry endpoint). Activation commands
   enqueue jobs; subscription flips to ACTIVE only on job success.
5. API: `/subscriptions/{id}/lifecycle/{command}`, `/subscriptions/{id}/changes`
   (preview+confirm), `/provisioning/jobs` GET + `/retry` POST.
6. Tests: `SubscriptionLifecycleTest` (every legal/illegal transition),
   `SubscriptionCommandServiceTest`, `SubscriptionChangeServiceTest` (preview math,
   proration), `ProvisioningJobRunnerTest` (failure → RETRYING → success idempotent).
7. **Gate G3** + commits `feat(subscription): enforce lifecycle transitions`,
   `feat(provisioning): ...`.

## SCP-G4 — Usage Metering, Audit, RBAC Hardening

1. Migration `V20260830_2__scp_usage_and_rbac.sql` — `usage_metrics` (code PK-ish,
   unit, aggregation, description), `usage_events` (tenant_id + index, metric_code,
   quantity NUMERIC(20,6)→stored as BIGINT scaled? NO — keep BIGINT minor-style integer
   quantity + metric unit; idempotency_key, source, occurred_at, UNIQUE(tenant_id,
   metric_code, idempotency_key)), `usage_aggregates` (tenant_id, metric_code, period
   type, period_start, sum, UNIQUE(tenant, metric, period)); capability seeds (all
   granular codes from spec §7) + grant to control-plane ADMIN role in same migration;
   RLS enable on usage tables (tenant-scoped).
2. `usage/` — `UsageEventService.ingest` (idempotent, tenant-scoped, audited),
   `UsageAggregateService` (period rollup), `UsageReadService` (current vs effective
   limit from G2 entitlements; limitKind UNLIMITED/SOFT/HARD/OVERAGE/PAYG; warning
   thresholds 0.75/0.9).
3. RBAC: switch new endpoints to granular codes (legacy `EXECUTIVE_*` unchanged
   elsewhere); extend `/executive/access-check` payload additively with granular
   flags for UI nav.
4. Audit: verify every G3 command + G2 override paths write `platform_audit_logs`
   (tests assert writer invoked); expose `/executive/audit` paginated read (G5 route,
   service here).
5. Tests: idempotent ingestion (duplicate key → no-op), aggregate math, limit kinds,
   capability annotations on every new endpoint (controller tests),
   unauthorized/cross-tenant 403 paths.
6. **Gate G4** + commits `feat(usage): ...`, `feat(rbac): ...`.

## SCP-G5 — Executive Read Models, Pagination, Filters, Search

1. `executive/read/` — `PageResponse<T>` record + query builders (parameterized,
   whitelist-validated sort columns — no SQL injection).
2. Read models + services: `ExecutiveOverviewService` (tenants, active subs, trials,
   MRR/ARR from ACTIVE items via G2 prices — N/A when price data absent),
   `TenantDirectoryQueryService` (search/status/country/sort/page — fixes the
   load-everything pattern), `SubscriptionGridQueryService` (grid columns incl.
   items/plan names/currency/amount/trial/next-billing), `SubscriptionDetailService`
   (one aggregate read: overview, items, entitlements, usage, invoices, changes,
   provisioning, audit references), `ProvisioningJobQueryService`, `AuditQueryService`.
3. Endpoints per spec §8 (`/overview`, `/tenants/v2`, `/subscriptions/v2`,
   `/subscriptions/{id}`, `/usage`, `/provisioning/jobs`, `/audit`).
4. Tests: pagination boundaries, filter combinations, sort whitelist, detail assembly,
   tenant isolation (tenant-scoped queries only via control-plane guard).
5. **Gate G5** + commit `feat(executive): expose subscription read models`.

## SCP-G6 — Executive UI Rebuild

1. Read Next 16 local docs (`node_modules/next/dist/docs`) for layouts/routes/server
   components before coding (per apps/web/AGENTS.md).
2. Shared layout: `app/executive/(scp)/layout.tsx` — client auth gate (existing
   in-component guard pattern; add `/executive` to PROTECTED_ROOTS if safe),
   sidebar nav (Overview, Applications, Tenants, Subscriptions, Plans & Pricing,
   Entitlements, Usage, Billing, Provisioning, Audit) — visibility from access-check;
   desktop sidebar / tablet collapsible / mobile drawer; SDS primitives + tokens.
3. Pages (client pages fetching only their own scope via new `lib/api/scp-api.ts`):
   overview (read-model cards, N/A discipline), applications (catalog cards),
   tenants (fixed code column, server-side search/filter/sort/pagination),
   subscriptions grid + `/subscriptions/[id]` detail (tabs per spec),
   plans (Plan/Version/Price/Entitlement separated), entitlements (tenant searchable
   selector — kills UUID typing), usage gauges, billing (filters over existing
   invoices API), provisioning (jobs + retry), audit (timeline).
4. Every string through `t()`; keys added to `locales/ar.ts` + `en.ts` (parity script
   gate). RTL/LTR via logical properties only. States: skeleton/empty/error/success.
   Tables → card lists on mobile. ARIA/keyboard per design-system docs.
5. Tests: vitest for scp-api, nav component, tenant selector, filters, states;
   i18n parity; lint; SDS/logo/brand scripts; build + performance budget.
6. **Gate G6** + commits `feat(web): add executive application catalog`, `feat(web):
   rebuild subscription management workspace`, etc. Legacy console file removed in the
   final cutover commit once new pages green.

## SCP-G7 — Hardening, Compatibility, Performance, Security, CI Closure

1. Full local web gate + backend compile; push branch; watch `ci.yml` + `web-ci.yml`
   to green (iterate on failures; any pre-existing failure must be proven identical
   on BASE_SHA run).
2. Backward-compat sweep: diff of existing endpoint DTOs (additive only);
   `git grep` for hardcoded plan codes / application lists in new code.
3. Security sweep: every new endpoint has capability + control-plane guard; tenant
   params validated; usage tables RLS verified by test; no secrets.
4. Performance: no N+1 in new read services (batched queries), pagination everywhere,
   web bundle budget script passes.
5. **Gate G7** → final report (mission §40) with SHAs, gate matrix, file/test evidence.

## Commit Plan

Small logical commits per workstream, e.g.:
`feat(subscription): add product catalog foundation`,
`feat(subscription): support subscription items`,
`feat(subscription): introduce plan versions`,
`feat(pricing): introduce versioned price model`,
`feat(entitlements): resolve item-derived capabilities`,
`feat(subscription): enforce lifecycle transitions`,
`feat(provisioning): add subscription provisioning jobs`,
`feat(executive): expose subscription read models`,
`feat(web): add executive application catalog`,
`feat(web): rebuild subscription management workspace`,
`test(...)`: per-suite additions.

## Risk Register

| Risk | Mitigation |
|---|---|
| CHECK-constraint widening on `tenant_subscriptions.status` | additive widen (old values legal); validated by migration in CI chain |
| Backfill size on subscriptions/items | set-based INSERT…SELECT; no per-row service calls |
| Entitlement regression | byte-identical plan-only path proven by dedicated regression tests |
| Web CI i18n parity | every key added to both locales in same commit |
| SDS compliance gate | use primitives/tokens only; run the three python gates locally before push |
| Local Postgres unavailable | PostgreSQL Direct suite validated in CI; local loop = compile + pure unit tests |
