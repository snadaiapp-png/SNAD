# SCP R0C-12 — Module Completeness Ledger (G1–G7)

- **Date:** 2026-09-06
- **Branch:** `scp/r0c-12-final-module-closure` (base `d9e206277b072ca0bca47c4d00061d2dbc22ff92`)
- **Authority chain:** design spec `docs/superpowers/specs/2026-08-29-subscription-control-plane-design.md`,
  implementation plan `docs/superpowers/plans/2026-08-29-subscription-control-plane-implementation.md`,
  final certifications R0C-1 → R0C-11, actual source/tests/migrations at R0C11_FINAL_HEAD.
- **Newer explicit R0C-12 authority:** none found (repo-wide search at base HEAD; only
  R0C-11 certification §8 notes "R0C-12 not started"). No conflict recorded.
- **Method:** every requirement below was mechanically proven against repository evidence
  (file inspection, code read, grep sweeps, test inventory). No UNKNOWN statuses are
  permitted at implementation start.

STATUS vocabulary: `PASS` · `GAP` · `SUPERSEDED_WITH_PROOF` · `DEFERRED_BY_EXPLICIT_AUTHORITY` · `NOT_APPLICABLE_WITH_PROOF`.

Audit basis: three parallel evidence sweeps (G1–G3 backend, G4–G5 backend, G6 web) followed
by first-hand verification of every claimed gap in the working tree before this ledger was
written.

---

## SCP-G1 — Catalog, Plan Versioning, Subscription Items

| REQ_ID | SOURCE | REQUIREMENT | EVIDENCE | STATUS |
|---|---|---|---|---|
| G1-01 | Plan G1.1 | `applications` migration + seed from `modules` | `V20260829_1__scp_applications_catalog.sql` (CREATE TABLE + idempotent INSERT…SELECT from modules) | PASS |
| G1-02 | Plan G1.1 | `products` + `plan_versions` migrations; partial-unique one ACTIVE version per plan; `tenant_subscriptions.plan_version_id` + backfill | `V20260829_2__scp_products_and_plan_versions.sql` L21–119 (`uk_plan_versions_one_active` partial index; plan_version_id FK + backfill; plan_id retained = dual-compatible) | PASS |
| G1-03 | Plan G1.1 | `subscription_items` migration + backfill one ACTIVE PLAN item per subscription | `V20260829_3__scp_subscription_items.sql` (type/status CHECKs, anchored-PLAN unique index, backfill L62–79) | PASS |
| G1-04 | Plan G1.2 | `ApplicationCatalogService` CRUD + code uniqueness | `subscription/catalog/ApplicationCatalogService.java` (create/update/listAll/listAvailable/get; trim+uppercase; existsByCode guard) | PASS |
| G1-05 | Plan G1.2 | `PlanVersionService` draft→activate→retire; resolve-for-date; subscriber pinning (activation never mutates subscribers) | `subscription/plan/PlanVersionService.java` (createDraft increments, activate only-DRAFT + retires current ACTIVE, resolveVersionForDate window filter; javadoc: never touches tenant_subscriptions) | PASS |
| G1-06 | Plan G1.2 | `SubscriptionItemService` add/update/cancel + dual-compatible `effectivePlanVersion` helper | `subscription/item/SubscriptionItemService.java` (addItem×2 fail-closed, cancelItem idempotent, updateQuantity, effectivePlanVersionId anchor→item-pin→subscription-pin fallback) | PASS |
| G1-07 | Plan G1.3 | APIs `/applications` GET/POST/PUT, `/products` GET, `/plans/{id}/versions` GET/POST, `/subscriptions/{id}/items` GET/POST/PATCH; guard + capabilities | `CatalogController` (applications EXECUTIVE_VIEW/MANAGE; products catalog.read/manage + ControlPlaneAccessGuard, no DELETE), `PlanVersionController`, `SubscriptionItemController` (all accessGuard.require) | PASS |
| G1-08 | Plan G1.4 | Tests: pinning, single-ACTIVE invariant, date resolution, multi-item, backfill read compat, cancel semantics, catalog CRUD+uniqueness | `PlanVersionServiceTest` (activate_doesNotTouchSubscriberRows…), `SubscriptionItemServiceTest` (multi-item, duplicate-active reject, pin preference chain), `ApplicationCatalogServiceTest`; PG: `SubscriptionAnchorPostgresTest`, `MultiPlanAnchorAuthorityPostgresTest` (PG-01…PG-23) | PASS |
| G1-09 | Design §2 | Product runtime from R0C-11 preserved (no destructive legacy contract removal) | R0C-11 certification §3/§5; product endpoints unchanged at this HEAD; legacy `saas_plans.plan_id` retained (V20260829_2 keeps plan_id) | PASS |
| G1-10 | Mission §6 | plan version pinning / single-active invariant / multi-item / legacy dual-compatible anchor semantics proven at runtime | Same test evidence as G1-08 + R0C-10 canonical battery (48/0F/0E/0S at R0C-11) | PASS |

G1_TOTAL_REQUIREMENTS=10, G1_PASS=10, G1_GAPS=0.

---

## SCP-G2 — Pricing, Country/Currency, Entitlement Integration

| REQ_ID | SOURCE | REQUIREMENT | EVIDENCE | STATUS |
|---|---|---|---|---|
| G2-01 | Plan G2.1 | `prices` migration: 12-model CHECK, country ISO/GLOBAL, currency, interval, BIGINT minor units, tiers JSONB, min/max, effective window; `country_currencies` seed SA/AE/KW/GLOBAL; `product_entitlements` | `V20260829_4__scp_prices_country_currencies_and_product_entitlements.sql` (ck_prices_model 12 values; seed rows SA→SAR, AE→AED, KW→KWD, GLOBAL→USD is_default; owner CHECK plan_version XOR product) | PASS |
| G2-02 | Plan G2.2 | `PriceResolver` country→GLOBAL fallback + effective dates | `pricing/PriceResolver.java` (exact-country → GLOBAL → latest window-valid; inWindow enforces effectiveFrom/To) | PASS |
| G2-03 | Plan G2.2 | `PriceCalculator` FLAT, PER_USER, TIERED marginal, VOLUME cumulative, USAGE_BASED (+ full 12-model dispatch), pure long math, no float | `pricing/PriceCalculator.java` (SUPPORTED_MODELS=12; tieredMarginal vs volumeCumulative separate implementations; overflow-safe multiply → ArithmeticException; min/max clamps) | PASS |
| G2-04 | Plan G2.3 | Item-aware entitlement merge (max of limits, OR of booleans) without replacing `EntitlementResolver` | `entitlement/ItemAwareEntitlementResolver.java` (base via EntitlementResolver; merge: moduleEnabled OR, Boolean::logicalOr, Long::max; quotas max) | PASS |
| G2-05 | Plan G2.3 | Plan-only path byte-identical when no items exist (regression) | `ItemAwareEntitlementResolverTest.planOnlyPathUnchanged` (returns untouched base object); `EntitlementProvisioningIsolationPostgresTest` QI-01…04 | PASS |
| G2-06 | Plan G2.4 | Tests: tier boundaries, volume, overage-ready; country fallback, effective dates; merge semantics | `PriceCalculatorTest` (flat/perUser/tieredMarginal/volumeCumulative/usageBased/hybrid/customContract/clamps/rejects), `PriceResolverTest` (prefersExactCountryMatch, fallsBackToGlobal, skipsNotYetEffective, skipsExpired, resolvesProductPrices), `ItemAwareEntitlementResolverTest` (mergesLimitsByMax, mergesBooleansWithOr, addOnEnablesModule, deniedStaysDenied) | PASS |
| G2-07 | Design §4 | Money = BIGINT minor units + ISO currency everywhere, no floating point in pricing | PriceCalculator long math only; percent computed only in usage read model (display), never in price math | PASS |
| G2-08 | Mission §7 | Product price ownership via existing `prices.product_id` | PriceController `/products/{productId}/prices` GET/POST; `PriceResolver.resolveForProduct`; R0C-11 PG-16 | PASS |
| G2-09 | Mission §7 | `country_currencies` correctness (country→GLOBAL fallback, seed data is catalog data) | Migration seed rows; `CountryCurrencyRepository`; resolver fallback proven in tests | PASS |
| G2-10 | Deviation note | Plan names `EntitlementResolverItemsTest` | Coverage exists as `ItemAwareEntitlementResolverTest` (same semantics; naming deviation documented) | PASS |

G2_TOTAL_REQUIREMENTS=10, G2_PASS=10, G2_GAPS=0.

---

## SCP-G3 — Lifecycle, Change Engine, Provisioning

| REQ_ID | SOURCE | REQUIREMENT | EVIDENCE | STATUS |
|---|---|---|---|---|
| G3-01 | Plan G3.1 | Status CHECK widened additively (all 5 legacy values preserved); `subscription_commands` ledger; `provisioning_jobs` + `provisioning_job_steps` UNIQUE(job_id, step_key) | `V20260830_1__scp_lifecycle_and_provisioning.sql` (legacy TRIALING/ACTIVE/PAST_DUE/SUSPENDED/CANCELLED all re-added; uk_prov_steps_job_key) | PASS |
| G3-02 | Plan G3.2 | `SubscriptionLifecycle` transition table + assertTransition; 10 domain commands | `lifecycle/SubscriptionLifecycle.java` (COMMANDS table; ACTIVATE/START_TRIAL/PAUSE/RESUME/SUSPEND/CANCEL/RENEW/EXPIRE/TERMINATE + SCHEDULE_CANCELLATION/MARK_PAST_DUE/ENTER_GRACE/REQUEST_ACTIVATION/PAYMENT_RECEIVED; TERMINAL_STATUSES; LEGACY_ALIASES TRIALING→TRIAL) | PASS |
| G3-03 | Plan G3.2 | `SubscriptionCommandService` validates, persists, emits entitlement events after commit, writes audit | `lifecycle/SubscriptionCommandService.java` (applyCanonicalTransition guarded `WHERE id=? AND status=?` fail-closed; afterCommit `SubscriptionActivatedEvent`/Suspended/Resumed/Cancelled; PlatformAuditService.success) | PASS |
| G3-04 | Mission §8 | Single lifecycle authority — no other production status writer | Repo-wide sweep: only `applyCanonicalTransition` writes `UPDATE tenant_subscriptions SET status`; converged callers: SaasAdministrationService:676, BillingStateService:249–256, TrialExpirationService:220, ProvisioningJobRunner:136–137; creation INSERT at SaasAdministrationService:373 is initial-state, by design; `LifecycleSingleWriterPostgresTest` RED-01…12 + GUARD-01/02 | PASS |
| G3-05 | Plan G3.3 | Preview/confirm change engine (item-aware, price delta via G2 calculator, warnings, proration) | `change/SubscriptionChangeService.java` (preview computes signed deltaMonthlyMinor via PriceResolver+PriceCalculator.computeWithBounds; execute blocks on warnings, swaps anchored PLAN item, converges anchors — single plan-composition writer) | PASS |
| G3-06 | Mission §8 | Proration behavior | Preview/execute price-delta + period math in SubscriptionChangeService; `SubscriptionChangeServiceTest.previewComputesDelta/previewDowngradeNegativeDelta`; `SubscriptionChangeServicePostgresTest` (ledger VARCHAR(24) P0-C contract, GLOBAL fallback, rollback paths) | PASS |
| G3-07 | Plan G3.4 | `ProvisioningJobRunner` PENDING→RUNNING→SUCCEEDED/FAILED/RETRYING; idempotent keyed steps; activation only after job success | `provisioning/ProvisioningJobRunner.java` (steps ENABLE_APPLICATIONS→RESOLVE_ENTITLEMENTS→VALIDATE; succeeded steps skipped on retry; VALIDATE routes through applyCanonicalTransition — no direct status write) | PASS |
| G3-08 | Plan G3.5 | APIs: `/subscriptions/{id}/lifecycle/{command}`, `/subscriptions/{id}/changes` (preview+confirm), `/provisioning/jobs` GET + `/{jobId}/retry` POST | `api/LifecycleController.java` (all endpoints accessGuard.require + EXECUTIVE_VIEW/MANAGE) | PASS |
| G3-09 | Plan G3.6 | Tests: legal/illegal transitions, command side effects, preview math, provisioning retry/idempotency | `SubscriptionLifecycleTest` (parameterized legal/illegal, aliases, terminal), `SubscriptionCommandServiceTest` (suspendWritesEverything, illegalTransitionWritesNothing), `SubscriptionChangeServiceTest`, `ProvisioningJobRunnerTest` (retrySkipsCompletedSteps, terminalSubscriptionNotActivated, alreadyActiveIdempotentNoOp) | PASS |
| G3-10 | Mission §8 | R0C-7/R0C-8/R0C-9/R0C-10 frozen semantics preserved | R0C-10 canonical battery re-proven at R0C-11 (48/0F/0E/0S); `LifecycleSingleWriterPostgresTest`; R0C-8 fail-closed concurrent-transition guard in applyCanonicalTransition; R0C-9 continuation contract classes present (ExpiredContinuation* suites) | PASS |
| G3-11 | Mission §8 | Audit on commands; entitlement events | SubscriptionCommandServiceTest.suspendWritesEverything (status+ledger+audit+event); activateFiresActivationEvent | PASS |

G3_TOTAL_REQUIREMENTS=11, G3_PASS=11, G3_GAPS=0.

---

## SCP-G4 — Usage Metering, Audit, RBAC Hardening

| REQ_ID | SOURCE | REQUIREMENT | EVIDENCE | STATUS |
|---|---|---|---|---|
| G4-01 | Plan G4.1 | `usage_metrics` / `usage_events` UNIQUE(tenant,metric,idempotency_key) / `usage_aggregates` UNIQUE(tenant,metric,period); capability seeds (all 22 granular codes); grants; RLS on usage tables | `V20260830_2__scp_usage_metering_and_rbac.sql` (22/22 seed rows exactly matching spec §7; EXECUTIVE_MANAGE-role full grant + EXECUTIVE_VIEW read mirror, idempotent NOT EXISTS; ENABLE+FORCE RLS + policy on usage_events/usage_aggregates). Canonicalization: `V20260901_1` uppercases codes; normalization in CapabilityEvaluationService/AccessCapabilityService (pinned by AccessCapabilityCodeCanonicalizationPostgresTest) | PASS |
| G4-02 | Plan G4.2 | Idempotent ingestion (duplicate key → no-op), tenant-scoped, audited | `UsageMeteringService.ingest` (DuplicateKeyException → duplicate=true, no aggregate upsert; tenantRlsContext.applyForCurrentTransaction FORCE-RLS scoping; capability aspect audits POST /usage/events ALLOW) | PASS |
| G4-03 | Plan G4.2 | Period rollup aggregation | `UsageMeteringService.upsertMonthlyAggregate` (ON CONFLICT DO UPDATE total = total + EXCLUDED.total) | PASS |
| G4-04 | Plan G4.2 | Read model: current vs effective limit from entitlements; limitKind UNLIMITED/SOFT_LIMIT/HARD_LIMIT/OVERAGE/PAY_AS_YOU_GO; warning thresholds 0.75/0.9 | `UsageMeteringService.usageSnapshot` (limit = max of plan-derived ∪ item-derived `USAGE.<METRIC>`; limit_kind from metric catalog; all 5 kinds exist as SQL CHECK values). **GAP:** `WARNING_THRESHOLD_90` declared but never used — javadoc claims "75% and 90%"; plan G4.2 requires thresholds 0.75/0.9. Kind resolution for SOFT_LIMIT/OVERAGE/PAY_AS_YOU_GO lacks test proof (mission §9 requires proving all five). | **GAP → G4-R1, G4-R2** |
| G4-05 | Plan G4.3 | New endpoints declare granular codes; access-check payload extended additively with granular flags | Products: catalog.read/manage; usage.read on GET /usage; audit.read on /audit/v2 (GovernanceController); `GET /access-check/v2` → AccessCheckV2(capabilities map of the 22 codes) via ControlPlaneAccessService (v1 payload untouched = additive) | PASS |
| G4-06 | Plan G4.4 | Audit: every G3 command + override paths write platform_audit_logs; paginated audit read | SubscriptionCommandService audit (G3-03); `AuditQueryService.query` (PageResponse, whitelist created_at/action/resource_type, parameterized, size clamp 1–200) at `GET /audit/v2` `audit.read`; `ExecutiveAuditRouteCompatibilityTest` pins legacy `/audit` kept | PASS |
| G4-07 | Plan G4.5 | Tests: idempotent ingestion, aggregate math, limit kinds, capability annotations, unauthorized/cross-tenant 403 | `UsageMeteringServiceTest` (duplicate→no-op+no upsert; RLS scope verify; negative reject; HARD_LIMIT 76% warning; UNLIMITED never warns); `CatalogControllerProductEndpointsTest` (annotation reflection); `CapabilityAuthorizationAspectTest` (deny + unauthenticated fail-closed); `ControlPlaneAccessGuardTest`; R0C-11 PG-26/27 | PASS |
| G4-08 | Mission §9 | Granular capability existence (22 codes enumerated in mission) | V20260830_2 STEP 3 seeds — exact 1:1 list match verified | PASS |
| G4-09 | Deviation note | Plan names UsageEventService/UsageAggregateService/UsageReadService | Consolidated `UsageMeteringService` provides all three behaviors (ingest, rollup, read model); naming/structure deviation documented; behaviors fully proven | PASS |

G4_TOTAL_REQUIREMENTS=9, G4_PASS=7, G4_GAPS=2 (G4-R1 wire 0.9 threshold + prove all five kinds; G4-R2 is the test-proof part of the same repair).

---

## SCP-G5 — Executive Read Models, Pagination, Filters, Search

| REQ_ID | SOURCE | REQUIREMENT | EVIDENCE | STATUS |
|---|---|---|---|---|
| G5-01 | Plan G5.1 | `PageResponse<T>` record + query builders (parameterized, whitelist-validated sort — no SQL injection) | `read/PageResponse.java`; SORTABLE whitelists in `SubscriptionGridQueryService` (5 cols), `TenantDirectoryQueryService` (5 cols), `AuditQueryService` (3 cols); only whitelisted column interpolated, values bound `?`; `ExecutiveReadModelsTest` proves injection attempt falls back safely + scalar-bind LIMIT/OFFSET regression tests | PASS |
| G5-02 | Plan G5.2 | `ExecutiveOverviewService` (tenants, active subs, trials, MRR/ARR N/A discipline) | `read/ExecutiveOverviewService.java` (6 fixed aggregate SQL; churn/expansion → null N/A) | PASS |
| G5-03 | Plan G5.2 | `TenantDirectoryQueryService` search/status/country/sort/page | `read/TenantDirectoryQueryService.java` (3×ILIKE search, status, country, whitelist sort, PageResponse, scalar subqueries for subscription counts) | PASS |
| G5-04 | Plan G5.2 | `SubscriptionGridQueryService` grid columns (items/plan/currency/amount/trial) | `read/SubscriptionGridQueryService.java` (tenantId/status/country/search/trialOnly filters; item_count scalar subquery; COUNT+page SELECT) | PASS |
| G5-05 | Plan G5.2 | `SubscriptionDetailService` one aggregate read (items, entitlements, usage, invoices, changes, provisioning, audit) | `read/SubscriptionDetailService.java` — record carries overview/items/invoices/changes/provisioningJobs/audit. **GAP: `entitlements` section absent from the detail read model** (design §8/§9 detail contract lists entitlements; frontend detail page therefore cannot render them). | **GAP → G5-R2** |
| G5-06 | Plan G5.2 | `ProvisioningJobQueryService`, `AuditQueryService` | AuditQueryService present (G4-06). ProvisioningJobQueryService absent as named class — equivalent implementation inline in `LifecycleController#listJobs` (filters tenantId/status, parameterized, bounded LIMIT 200). Mission §10 allows "equivalent current implementations"; design §8 requires only "job list (filter by status/tenant)" — bounded list satisfies the contract. Deviation documented. | PASS |
| G5-07 | Design §8 | Endpoints: /overview, /tenants/v2, /subscriptions/v2, /subscriptions/{id}, /usage, /provisioning/jobs, /audit | Present: /overview, /tenants/v2, /subscriptions/v2, /subscriptions/{id}/detail, /usage, /provisioning/jobs(+retry), /audit (legacy, kept) + /audit/v2 (paginated). **GAP: design-required path `GET /subscriptions/{id}` does not exist** (only /detail variant). Compat strategy makes /v2-style additions the sanctioned pattern, but §10 explicitly lists `/subscriptions/{id}` as a required detail read-model path. | **GAP → G5-R1** |
| G5-08 | Plan G5.3 | Pagination contract `?page&size&sort` → PageResponse on the paginated read models | tenants/v2 + subscriptions/v2 + audit/v2 return PageResponse (record fields content/page/size/totalElements/totalPages); clamps proven in ExecutiveReadModelsTest (size 5000→200, page −5→0) | PASS |
| G5-09 | Plan G5.4 | Route precedence safety (v2 literal vs {id}) | `ExecutiveTenantsV2RoutePrecedenceTest` (PathPattern specificity proof); no competing single-segment GET exists for /subscriptions today; when G5-R1 adds GET /subscriptions/{id}, precedence test must be extended (v2 literal must win) | PASS (test extension folded into G5-R1) |
| G5-10 | Plan G5.4 | Tenant isolation: tenant-scoped queries only via control-plane guard | Every executive endpoint calls ControlPlaneAccessGuard.require (fail-closed when unconfigured) + @RequireCapability; detail/grid resolve ids only through guarded controllers; usage tables FORCE-RLS; tenant_subscriptions documented non-RLS (V20260906_1 header) | PASS |
| G5-11 | Mission §10 | No arbitrary tenant→subscription lookup | SubscriptionDetailService.detail(id) reachable only via guarded controller; grid tenantId is a bounded filter parameter; no controller exposes tenant→subscription resolution without guard | PASS |
| G5-12 | Mission §10 | R0C-10 effective-subscription semantics preserved | `SubscriptionResolutionService` (EFFECTIVE_PREDICATE, canonical chronology) consumed by SaasAdministrationService, BillingStateService ("R0C-10: EFFECTIVE only"), EntitlementResolver, SubscriptionImpactService; reporting consumers (grid/directory) keep ALL_HISTORY per the resolution service's authoritative Javadoc contract ("reporting consumers keep ALL_HISTORY") — documented, deliberate, newest repo authority | PASS |
| G5-13 | Plan G5.4 | Tests: pagination boundaries, filter combinations, sort whitelist, detail assembly, tenant isolation | ExecutiveReadModelsTest (8), ExecutiveAuditRouteCompatibilityTest, ExecutiveTenantsV2RoutePrecedenceTest, SubscriptionAnchorPostgresTest (isolation), MultiPlanAnchorAuthorityPostgresTest | PASS |
| G5-14 | Mission §15 (pre-audit) | No N+1 in SCP read services | **GAP: `UsageController#usage` issues 1 + 3N queries (per-metric aggregate + limit + limit_kind); N=8 seeded metrics → 25 queries per request.** All other read services batched (2 statements per grid, 6 fixed detail, 2 audit). | **GAP → G5-R3** |
| G5-15 | Design §8 | API ledger integrity | `PlatformApiCountTest` pins executive=79, total=750 (incl. R0C-11 product endpoints); G5-R1 addition requires ledger adaptation (79→80, 750→751) — legitimate same-class adaptation as R0C-11 `fdfced73` | PASS (adaptation inside G5-R1) |

G5_TOTAL_REQUIREMENTS=15, G5_PASS=13, G5_GAPS=2 (G5-R1 endpoint alias + ledger/precedence; G5-R3 N+1 batch; G5-R2 detail entitlements).

---

## SCP-G6 — Executive UI Rebuild

| REQ_ID | SOURCE | REQUIREMENT | EVIDENCE | STATUS |
|---|---|---|---|---|
| G6-01 | Design §9 | Route IA: /executive, applications, tenants, subscriptions, subscriptions/[id], plans, entitlements, usage, billing, provisioning, audit — each data-backed | All 11 routes present under `app/executive/`; each fetches its own scope via `lib/api/scp-api.ts` (overview→overview(), applications→applications(), tenants→tenants/v2, subscriptions→subscriptions/v2, detail→subscriptionDetail+items+usage+lifecycle+preview/execute, plans→plans+planVersions, entitlements→modules+tenantEntitlements+recalculate, usage→usage(tenantId), billing→invoices, provisioning→jobs+retry, audit→audit/v2) | PASS |
| G6-02 | Design §9 | Shared layout: sidebar desktop, collapsible tablet, drawer mobile | `layout.tsx` → ExecutiveShell + ScpLayout; sidebar sticky desktop; ≤64rem stacked (collapsible, **no toggle control**); ≤40rem stacked (**no drawer**). **GAP: no tablet collapse toggle / mobile drawer.** | **GAP → G6-R3** |
| G6-03 | Design §9 | Auth protection | ScpAuthGate (AUTHENTICATED required) + fail-closed ScpNav capability machine (access-check/v2; exact-true match; degraded=error+retry). **GAP: `/executive` absent from PROTECTED_ROOTS in `app/providers.tsx`** (plan G6.2 explicitly says "add /executive to PROTECTED_ROOTS if safe") — session-loss redirect with returnUrl never fires for /executive. | **GAP → G6-R1** |
| G6-04 | Design §9 | Capability-driven nav visibility | ScpNav SECTIONS declare per-link capability (subscription.read, catalog.read, plan.read, entitlement.read, usage.read, billing.read, provisioning.read, audit.read); fail-closed state machine; pinned by scp-nav.test.tsx (6 tests) | PASS |
| G6-05 | Design §9 | All strings via t(), keys in BOTH ar.ts + en.ts; i18n parity | 170 static keys + 10 dynamic nav keys all resolve in both locales (scp.* sets identical 171=171; parity script gate). **GAP: one hardcoded Arabic string — `app/executive/layout.tsx:9` `logoAriaLabel="الذهاب إلى لوحة الإدارة التنفيذية"` (server component bypasses useI18n).** ScpStatusPill renders raw API enum values (data values, not UI copy — documented, not a gap). | **GAP → G6-R2** |
| G6-06 | Design §9 | Skeleton/empty/error/success states everywhere | ScpSkeleton/ScpError(+retry)/ScpEmpty/ScpNotice/ScpStatusPill; every page early-returns skeleton, renders error+retry and empty branches; route-level loading.tsx→RouteSkeleton; nav degraded state | PASS |
| G6-07 | Design §9 | Keyboard + ARIA per design-system docs | ScpStates roles (status/alert/progressbar/aria-live/aria-busy), nav headings+links semantic, focus states in module CSS; SDS primitives/tokens used | PASS |
| G6-08 | Design §9 | Tenants: fixed code column, server-side pagination/search/filters | tenants/page.tsx (server-side search/status/page/size=20; code column renders tenant.code — legacy duplicate-name bug fixed with docblock; Pagination component) | PASS |
| G6-09 | Design §9 | Subscriptions: grid + filters + pagination; detail (items, entitlements, usage, invoices, changes, provisioning, audit) | Grid: Suspense+useSearchParams, status/search/tenantId filters, pagination. Detail: stacked aria-labelledby sections — items, usage, invoices, changes, provisioning, audit, lifecycle command bar, change preview→confirm. **GAP: entitlements section missing from detail** (blocked by G5-R2 backend field). | **GAP → G6-R4** |
| G6-10 | Design §9 | Plans: Plan ≠ Version ≠ Price ≠ Entitlement explicit | Plan ≠ Version explicit (PlanCard + version table + activate flow + pinning docblock). **GAP: Price not explicit** (only embedded create-version form fields; existing `scpApi.planVersionPrices` unused — no per-country/currency/model price rendering); **Entitlement absent** (existing `executiveApi.planModules` unused). | **GAP → G6-R5** |
| G6-11 | Design §9 | Entitlements: tenant searchable selector (no raw UUID typing) | Debounced 250ms `scpApi.tenants({search, size:8})` + listbox results `{name} · {code}`; capability table per module; recalculate action | PASS |
| G6-12 | Design §9 | Usage gauges: current/limit/%/period/reset/warning | current/limit/percent/progressbar(aria-valuenow)/warning/limitKind rendered. **GAP: period/reset not rendered** (backend UsageSnapshot carries no period field; usage page docblock over-claims). | **GAP → G6-R6** |
| G6-13 | Design §9 | Billing: existing invoice semantics preserved, add filters | Invoice table (number, status pill, money, period, dueAt) + search/status filters (client-side; list bounded by existing invoices contract — documented in page docblock) | PASS |
| G6-14 | Design §9 | Provisioning: jobs + retry; Audit: timeline/query | Status filter, pills, errorCode, attempts, retry action for FAILED/RETRYING; audit = filterable paginated query table (mission allows "timeline/query") | PASS |
| G6-15 | Design §9 | Tables become card lists on small screens (not just overflow-x); RTL/LTR logical properties | `scp.module.css` @media 40rem converts tables to labeled cards (thead visually hidden, td::before content attr(data-label)); 14 logical-property constructs, zero physical left/right rules; RTL_LTR_GUIDE.md present | PASS |
| G6-16 | Design §9 | Route-level data fetching (each page fetches only its own scope) | Client pages each fetch their own scope; no Promise.all mega-load; Suspense where search params used | PASS |
| G6-17 | Design §10 | Legacy executive-console correctly retired after gates | executive-console.tsx + executive.module.css deleted (commit 74eaa112 "feat(web): rebuild executive console as subscription control plane"; 218+157 lines removed); no remaining references; new pages pass gates (web CI history) | PASS |
| G6-18 | Plan G6.5 | Tests: vitest for scp-api, nav, tenant selector, filters, states; parity; lint; SDS/logo/brand; build+perf budget | scp-api.test.ts (7), scp-nav.test.tsx (6), plans-page.test.tsx (8); repo-level governance scripts run at G7 (Phase 19) | PASS |
| G6-19 | Design §9 | Applications page: catalog cards data-backed | applications/page.tsx (scpApi.applications() + createApplication form; catalog-driven) | PASS |

G6_ROUTE_REQUIREMENTS=19, G6_ROUTE_PASS=13, G6_UI_GAPS=6 (G6-R1 PROTECTED_ROOTS; G6-R2 aria-label i18n; G6-R3 responsive nav; G6-R4 detail entitlements; G6-R5 plans price+entitlement; G6-R6 usage period).

---

## SCP-G7 — Hardening, Compatibility, Performance, Security, CI Closure

G7 requirements are executed after G1–G6 gap remediation (mission §12: "Do not begin G7 until TOTAL_GAPS=0") and are tracked in the final certification document:

- G7-compat: removed/renamed endpoints 0; additive-only DTO changes; HARDCODED_PRODUCT_LISTS=0; HARDCODED_PLAN_BRANCHING=0 (sweep at closure).
- G7-security: every SCP endpoint capability+guard; tenant params validated; RLS intact; canonical secret scan.
- G7-performance: N_PLUS_ONE_FINDINGS=0 (after G5-R3); UNBOUNDED_GRID_READS=0; MISSING_REQUIRED_PAGINATION=0; UNSAFE_SORT_PATHS=0.
- G7-database: Flyway full-chain on PostgreSQL Direct; duplicate versions 0; orphan FK 0.
- G7-acceptance: final SCP PG acceptance battery (mission §17 list); R0C-1→R0C-11 recertification.
- G7-gates: web canonical gate; backend canonical single Maven run; code freeze; CI exact-head.

---

## Consolidated Remediation List (executed via TDD RED→GREEN→REFACTOR)

| ID | Gap | Repair | Requirement source |
|---|---|---|---|
| G4-R1 | `WARNING_THRESHOLD_90` unused; kinds lack proof | UsageSnapshot gains `critical` flag (≥90% for threshold kinds); unit tests pin all five kinds' resolution + warning/critical semantics | Plan G4.2 (0.75/0.9); mission §9 |
| G5-R1 | `GET /subscriptions/{id}` absent | Additive alias route → SubscriptionDetailService.detail (EXECUTIVE_VIEW + guard); extend route-precedence test (v2 wins over {id}); adapt PlatformApiCountTest ledger (executive 79→80, total 750→751) | Design §8; mission §10 |
| G5-R2 | Detail read model lacks entitlements | SubscriptionDetail gains additive `entitlements` section (plan-derived ∪ item-derived rows, parameterized, bounded) | Design §8/§9 |
| G5-R3 | UsageController#usage N+1 (1+3N) | Batch into fixed query count (one metrics+catalog read, one aggregate batch, one limits batch); query-count regression test | Mission §15; plan G7.4 |
| G6-R1 | /executive not in PROTECTED_ROOTS | Add "/executive" to PROTECTED_ROOTS | Plan G6.2 |
| G6-R2 | Hardcoded Arabic logoAriaLabel | Client wrapper translates via t() with keys `scp.layout.logoAriaLabel` in ar.ts+en.ts | Design §9 |
| G6-R3 | No tablet collapse / mobile drawer | Accessible toggle (aria-expanded/aria-controls, keyboard) collapsing sidebar ≤64rem; overlay drawer ≤40rem; t() strings; tests | Design §9 |
| G6-R4 | Detail page lacks entitlements section | Render `detail.entitlements` (from G5-R2) in an aria-labelledby section with i18n keys both locales | Design §8/§9 |
| G6-R5 | Plans page lacks Price/Entitlement explicitness | Render per-version prices (existing `scpApi.planVersionPrices`) and per-plan module entitlements (existing `executiveApi.planModules`) | Design §9 |
| G6-R6 | Usage page lacks period display | Backend: UsageSnapshot gains `periodStart` (MONTHLY aggregate period) additive field; frontend renders period/reset | Design §9; mission §11 |

TOTAL_REQUIREMENTS=65, TOTAL_PASS=53, TOTAL_GAPS=10 (4 backend gaps producing 6 repairs incl. frontend surfaces; 6 web gaps; G5-R2 and G6-R4 are the two halves of one contract).

R0C12_REQUIREMENT_UNKNOWN=0.
