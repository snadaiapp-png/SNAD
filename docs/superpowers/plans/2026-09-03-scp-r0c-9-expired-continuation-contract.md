# R0C-9 — Expired Subscription Continuation + Subscription Multiplicity Contract Gate

- **Task ID:** R0C-9
- **Task Name:** Expired Subscription Continuation + Subscription Multiplicity Contract Gate
- **Branch:** `scp/r0c-9-expired-continuation-contract`
- **Base:** `scp/r0c-8-trial-expiration-runtime` @ `de32ef7ab304b28386e199deb289b192f82ddfeb` (verified against `origin` via `git ls-remote` before any work; §1)
- **Date:** 2026-09-03
- **Outcome:** **BLOCKED_MIGRATION_REQUIRED** — MODEL_B is the repository-declared target
  direction; safe implementation requires a dedicated schema + consumer-convergence
  task (§12, §16). No implementation, no migration, no invented semantics in R0C-9.

---

## 1. Predecessor durability verification

```
git ls-remote origin refs/heads/scp/r0c-8-trial-expiration-runtime
→ de32ef7ab304b28386e199deb289b192f82ddfeb      (matches REQUIRED_BASE_HEAD)
```

Branch `scp/r0c-9-expired-continuation-contract` was created from that exact
commit (`git checkout -b … de32ef7a`). R0C_8_BASE_VERIFIED = YES.

**Remote checkpoint caveat (credential blocker):** this sandbox has no GitHub
credentials (no `GITHUB_PAT`, no `gh` CLI, no SSH key — the same reset documented
in the R0C-RECOVERY-CHAIN report). All `git push origin` attempts fail with
`fatal: could not read Username for 'https://github.com'`. All commits in this
task are therefore **LOCAL-DURABLE ONLY** and must be pushed once credentials
are available (§17 lists the exact checkpoint SHAs). Read access to origin
(clone/fetch/ls-remote) is unaffected and was used for verification.

---

## 2. RED dead-end evidence (PostgreSQL Direct, real production paths)

Battery: `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/lifecycle/ExpiredContinuationDeadEndPostgresTest.java`
(12 tests, all PASS on PostgreSQL 17.11, Flyway 143 migrations, real
`SaasAdministrationService.createSubscription` + real R0C-8
`TrialExpirationService` runtime driver). Commit `9d1c25de`.

The dead-end scenario, end to end: a tenant creates a trial, the trial's
`trial_ends_at` elapses, R0C-8's runtime driver executes the canonical `EXPIRE`
command → the subscription becomes `EXPIRED`. From that moment:

| PG | Proof | Result |
|---|---|---|
| PG-01 | real create → real driver | trial → `EXPIRED`, exactly one row for the tenant, one `EXPIRE` ledger row |
| PG-02 | `createSubscription` again | **409 CONFLICT "Tenant already has a subscription"** (count guard: `COUNT(*) WHERE tenant_id > 0`) — nothing written |
| PG-03 | canonical `RESUME` from EXPIRED | **IllegalStateException** ("Illegal subscription transition: RESUME from EXPIRED") — no ledger row |
| PG-03b | legacy `resumeSubscription` (PATCH …/resume) | **silent no-op**: returns 200, status stays `EXPIRED`, but a misleading `SUBSCRIPTION.RESUMED` change event + `SUBSCRIPTION.RESUME` audit + entitlement recalc event are recorded (the guard only special-cases `CANCELLED`; the non-CANCELLED branch just clears `cancel_at_period_end`) |
| PG-04 | canonical `RENEW` from EXPIRED | **IllegalStateException** — no ledger row |
| PG-04b | legacy `renewSubscription` (POST …/renew) | **409 CONFLICT** (canonical RENEW rejection surfaced by `canonicalTransition`); no invoice issued |
| PG-05 | canonical `ACTIVATE` from EXPIRED | **IllegalStateException** — no ledger row |
| PG-05b | every command in `SubscriptionLifecycle.COMMANDS` | **all 13 commands illegal from EXPIRED** (terminal, zero exits; no invented REACTIVATE/RESUBSCRIBE/REOPEN/RESTART_TRIAL exists) — row never mutated, sweep leaves exactly the original EXPIRE ledger row |
| PG-06 | direct `INSERT` of a second row | constraint is exactly `UNIQUE (tenant_id)`; **`uk_tenant_subscriptions_tenant` violation** regardless of the new row's status (even `EXPIRED`) |
| PG-07 | billing tenant lookup | `SELECT billing_state … WHERE tenant_id = ?` **finds the EXPIRED row** (`billing_state='CURRENT'`); `evaluateAndTransition` no-ops on it; the dunning scan (`billing_state IN (CURRENT,PAST_DUE,SUSPENDED)`) **includes the terminal-status tenant by billing_state value, not by status** — the lookup relies on one-row uniqueness |
| PG-08 | entitlement tenant lookup | `SELECT id, plan_id … WHERE tenant_id = ? AND status='ACTIVE' LIMIT 1` → **empty**; real `EntitlementResolver` → **all modules denied**, `ctx.subscriptionId() == null` |
| PG-09 | tenant isolation | tenant A's dead end (expired + rejected revival attempts) never touches tenant B's rows/ledger/events/invoices/entitlement lookups; zero cross-tenant ledger rows |

**EXPIRED_TENANT_DEAD_END_RED = YES.**

Additional dead-end surface (source-verified, same guard):
`cancelSubscription`, `changePlan`, `changeSeats` call `ensureMutableSubscription`
(legacy five-status set `TRIALING/ACTIVE/PAST_DUE/SUSPENDED/CANCELLED`) →
**409 "Subscription is not mutable in its current state"** for EXPIRED.

---

## 3. Legacy one-subscription evidence

1. `V19__create_saas_administration.sql:57` —
   `CONSTRAINT uk_tenant_subscriptions_tenant UNIQUE (tenant_id)` (PG-06 proves
   the definition verbatim: `UNIQUE (tenant_id)`).
2. `SaasAdministrationService.createSubscription` (line 293) —
   `SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ?` > 0 →
   409 "Tenant already has a subscription" (PG-02). The guard blocks creation
   for a tenant with **any** row — including terminal EXPIRED/CANCELLED ones.
3. Design spec §1 (forensic facts table): "One subscription per tenant, one
   plan per subscription — `V19` `tenant_subscriptions` has `UNIQUE(tenant_id)`".
4. The status CHECK widened additively (V20260830_1) but no exit from EXPIRED
   was ever added: `SubscriptionLifecycle.COMMANDS` has no entry with EXPIRED
   as a fromStatus; `TERMINAL_STATUSES = {CANCELLED, EXPIRED, TERMINATED}`.

**LEGACY_SUBSCRIPTION_MULTIPLICITY = ONE_SUBSCRIPTION_PER_TENANT** (schema +
writer + read paths, all confirm).

---

## 4. Target 0..N evidence

1. Design spec §2 (Target Domain Model): `Tenant └── 0..N Subscriptions (state
   machine; 0..N items)` — explicit, authoritative.
2. Design spec §3: lifecycle commands `activate, startTrial, pause, resume,
   scheduleCancellation, cancel, renew, suspend, expire, terminate` — no
   revival/resubscribe command exists or is implied for EXPIRED.
3. `V20260829_3__scp_subscription_items.sql` — subscription_items with
   `UNIQUE (subscription_id, plan_id) WHERE item_type='PLAN' AND status='ACTIVE'`
   and the comment: "a subscription may carry ERP + HRM + CRM plans, but never
   the same plan twice" — **multi-PLAN inside one subscription** is the target.
4. SCP v2 read models are already multiplicity-tolerant:
   `TenantDirectoryQueryService` (count + LATEST by `created_at DESC`),
   `SubscriptionGridQueryService` (one row per subscription),
   `ExecutiveOverviewService` (per-subscription aggregates),
   `HealthIntelligenceService` (COUNT DISTINCT + MAX aggregates),
   `UsageMeteringService` limit resolution (max-merge across non-terminal rows).
5. `billing_invoices` already carries BOTH `tenant_id` AND `subscription_id`
   (FK) — invoice linkage is subscription-ready.
6. `provisioning_jobs`, `subscription_commands`, `subscription_change_events`,
   `subscription_items` are all subscription-id-keyed.

**TARGET_SUBSCRIPTION_MULTIPLICITY = 0_N_SUBSCRIPTIONS_PER_TENANT.**

The explicit conflict: the target shape (0..N) is **incompatible** with the
legacy constraint (UNIQUE tenant_id) and the legacy writer guard (COUNT > 0).

---

## 5. SUBSCRIPTION_MULTIPLICITY_ASSUMPTION_MATRIX

Every file in `apps/sanad-platform/src/main/java` that references
`tenant_subscriptions` (23 files) is classified below; plus the schema and the
two inheritance-based consumers. "WOULD_BREAK" = behavior change if a tenant
could have 0..N subscription rows.

| ID | FILE | METHOD / SITE | DOMAIN | QUERY / BEHAVIOR | ASSUMES_ONE? | WOULD_BREAK WITH 0..N? | R/W | SEC | BIL | ENT | RISK |
|---|---|---|---|---|---|---|---|---|---|---|---|
| M-01 | V19 migration | `uk_tenant_subscriptions_tenant` | storage | `UNIQUE (tenant_id)` | YES | HARD BLOCK — second row impossible (by design today; must change for 0..N) | W (constraint) | — | foundation | foundation | **CRITICAL** |
| M-02 | SaasAdministrationService | `createSubscription` guard | create | `COUNT(*) WHERE tenant_id > 0` → 409 | YES | THE DEAD END — tenant with only terminal history can never re-subscribe | W | — | — | — | **CRITICAL** |
| M-03 | BillingStateService | `findSubscription` | billing | `SELECT billing_state … WHERE tenant_id` → `rows.get(0)` | YES | arbitrary row among many; wrong billing_state read | R | — | **YES** | — | HIGH |
| M-04 | BillingStateService | `applyTransition` selection | billing | `SELECT id, status … WHERE tenant_id` → `findFirst` | YES | arbitrary subscription selected for lifecycle command | R | — | **YES** | — | HIGH |
| M-05 | BillingStateService | `applyTransition` writes | billing | `UPDATE … SET billing_state WHERE tenant_id` (×2 sites) | YES | billing_state write **fans out to ALL tenant rows incl. terminal history** | W | — | **YES** | — | HIGH |
| M-06 | BillingStateService | `countOverdueInvoices` | billing | overdue invoices counted per TENANT (not subscription) | YES (tenant≡sub) | historical subscription's overdue invoices drag the new one into dunning | R | — | **YES** | — | HIGH |
| M-07 | BillingStateService | `runDunningCycleOnce` scan | billing | `SELECT tenant_id … WHERE billing_state IN (…)` (no dedupe) | YES | duplicate tenant evaluations (idempotent, but per-tenant semantics) | R→W | — | YES | — | MED |
| M-08 | EntitlementResolver | `findActiveSubscription` | entitlement | `… WHERE tenant_id AND status='ACTIVE' LIMIT 1` (findFirst) | YES (≤1 ACTIVE) | arbitrary ACTIVE wins; entitlement authority undefined with ≥2 ACTIVE | R | — | — | **YES** | HIGH |
| M-09 | ItemAwareEntitlementResolver | `getEffectiveEntitlements` | entitlement | inherits `base.subscriptionId()` (M-08's pick) then item-joins by it | YES (via M-08) | items of the other ACTIVE subscriptions ignored | R | — | — | **YES** | MED |
| M-10 | SubscriptionImpactService | `getCurrentPlanCode` | lifecycle preview | `… WHERE tenant_id AND status='ACTIVE' LIMIT 1` | YES (≤1 ACTIVE) | arbitrary plan for upgrade/downgrade ranking | R | — | — | partial | MED |
| M-11 | TenantDirectoryAdministrationService | `limits(tenantId)` | directory limits | `… WHERE tenant_id AND status IN (TRIALING,ACTIVE,PAST_DUE)` → `rows.get(0)` | YES | arbitrary plan limits for user/org creation caps | R | — | — | partial | MED |
| M-12 | SaasAdministrationService | `validateUsageAgainstPlan` | create/change | plan max-users vs seat quantity (per selected plan) | NO | unchanged (plan-scoped) | R | — | — | — | LOW |
| M-13 | TenantDirectoryQueryService | tenants v2 read | directory read | `COUNT(*)` per tenant + LATEST (`ORDER BY created_at DESC LIMIT 1`) status | **NO** | no break (explicit count + explicit LATEST) | R | — | — | — | LOW |
| M-14 | SubscriptionGridQueryService | subscriptions v2 grid | read model | one row per subscription; optional tenant filter | **NO** | no break | R | — | — | — | LOW |
| M-15 | ExecutiveOverviewService | overview metrics | read model | COUNT/SUM grouped by status per subscription row | **NO** | no break (per-subscription semantics correct for MRR; "trials" counts rows not tenants — display nuance) | R | — | display | — | LOW |
| M-16 | HealthIntelligenceService | `tenantHealth` | health read | `LEFT JOIN tenant_subscriptions` + `MAX(seat_quantity)` | NO (aggregate) | seat capacity = MAX across ALL rows **including EXPIRED history** (latent inaccuracy only) | R | — | — | — | LOW |
| M-17 | UsageMeteringService | `usageSnapshot` limit query | usage | UNION plan-derived + item-derived across all non-terminal subs; `ORDER BY limit_value DESC LIMIT 1` (max-merge) | **NO** | no break (aggregate MAX; policy = highest limit wins, implicit) | R | — | — | partial | LOW |
| M-18 | UsageMeteringService | ingest / aggregates | usage | tenant-scoped by design (`UNIQUE(tenant_id, metric_code, idempotency_key)`; FORCE-RLS via `TenantRlsTransactionContext`) | **NO** | no break (usage is per-tenant per design spec §5) | W | RLS-scoped | — | — | NONE |
| M-19 | ProvisioningJobRunner | all steps | provisioning | every query `WHERE id = ?` (subscription-scoped); terminal fail-closed | **NO** | no break | R+W | — | — | — | NONE |
| M-20 | SubscriptionCommandService | `applyCanonicalTransition` / `execute` | lifecycle | `WHERE id = ?` guarded updates; ledger subscription-scoped | **NO** | no break | R+W | — | — | — | NONE |
| M-21 | TrialExpirationService | scan + re-check | expiry driver | scan by status+trial_ends_at; re-check `WHERE id = ? FOR UPDATE` | **NO** | no break | R+W | — | — | — | NONE |
| M-22 | SubscriptionChangeService | anchor reads / composition | change | `WHERE id = ?` (subscription-scoped) | **NO** | no break | R+W | — | — | — | NONE |
| M-23 | SubscriptionItemService / SubscriptionItemRepository | items | items | `WHERE subscription_id = ?` / anchor by id | **NO** | no break | R+W | — | — | — | NONE |
| M-24 | SaasAdministrationService (renew/resume/cancel/changePlan/seats) | legacy lifecycle | lifecycle | id-keyed + `ensureMutableSubscription` (legacy 5-status set) | **NO** | no break (but EXPIRED not in legacy mutable set → 409; resume has the PG-03b no-op artifact) | R+W | — | — | — | LOW |
| M-25 | SubscriptionDetailService | detail read model | read | `WHERE s.id = ?` | **NO** | no break | R | — | — | — | NONE |
| M-26 | LifecycleController / SaasAdministrationCommandController / PlanVersionController | API layer | API | id-keyed endpoints; `PlanVersionService` never touches tenant_subscriptions rows (doc contract) | **NO** | no break | R+W | RBAC-guarded | — | — | NONE |
| M-27 | ModuleResetService / ModuleResetRegistry | module reset scope | reset | table-name lists only (tenant_subscriptions is PROTECTED from module reset) | **NO** | no break | — | — | — | — | NONE |
| M-28 | ModuleCapabilityContext / SubscriptionItemEntity | contracts | contracts | doc-level "the active subscription" (singular) wording | YES (doc) | doc wording only | R | — | — | — | NONE |

**MULTIPLICITY_ASSUMPTIONS_DISCOVERED = 28. UNKNOWN_MULTIPLICITY_ASSUMPTIONS = 0.**
Every `tenant_subscriptions` reference in the main tree is classified; the
ambiguous migration-risk entries are exactly the seven below.

### Ambiguous tenant-subscription reads (must be 0 before MODEL_B implementation)

`AMBIGUOUS_TENANT_SUBSCRIPTION_READS = 7` — unqualified/arbitrary tenant-keyed
access that relies on today's uniqueness:

1. M-03 `BillingStateService.findSubscription` (billing_state read)
2. M-04 `BillingStateService.applyTransition` (subscription selection)
3. M-05 `BillingStateService.applyTransition` UPDATE (billing_state fan-out write)
4. M-08 `EntitlementResolver.findActiveSubscription` (entitlement authority pick)
5. M-10 `SubscriptionImpactService.getCurrentPlanCode` (plan pick)
6. M-11 `TenantDirectoryAdministrationService.limits` (plan limits pick)
7. M-02 `createSubscription` COUNT guard (the creation gate itself)

Classifications these sites should carry after convergence (§13 semantics):
M-02 → NON_TERMINAL (creation blocked while a non-terminal subscription
exists — pending the multiple-active decision); M-03/M-04 →
BILLING_EFFECTIVE; M-05 → subscription-id write; M-08 → ENTITLEMENT_EFFECTIVE;
M-10/M-11 → ENTITLEMENT_EFFECTIVE (or NON_TERMINAL + explicit order).
M-13's LATEST and M-17's max-merge are already qualified/explicit.

---

## 6. Candidate models and gates

### MODEL_A — reuse the same subscription row (EXPIRED re-subscribed in place)

**MODEL_A_INVALID = YES.** No authoritative source defines any exit from
EXPIRED:

- allowed command: **UNDEFINED** — `SubscriptionLifecycle.COMMANDS` contains
  zero transitions with EXPIRED as fromStatus (PG-05b proves all 13 commands
  illegal). REACTIVATE/RESUBSCRIBE/REOPEN/RESTART_TRIAL do not exist anywhere
  in the repository, and inventing them is forbidden (order §1/§9).
- EXPIRED → target status: UNDEFINED.
- plan/version behavior: UNDEFINED (change-plan is blocked for EXPIRED by
  `ensureMutableSubscription` — 409).
- seat behavior: UNDEFINED (seats likewise blocked).
- trial behavior: UNDEFINED (trial_ends_at is a preserved historical fact per
  R0C-8; whether a re-trial may reset it is undefined).
- billing period behavior: UNDEFINED.
- invoice behavior: UNDEFINED.
- entitlement behavior: only the denial behavior is defined (PG-08).
- audit/event behavior: only the existing EXPIRE-side artifacts exist.

The nearest analogues are NOT authority: RESUME from CANCELLED is the proven
legacy revival (R0C-7) but CANCELLED ≠ EXPIRED; RENEW is the sanctioned
trial→paid conversion (R0C-8) but only from TRIAL/TRIALING — never EXPIRED.
Extending either to EXPIRED would be an invented semantic.

### MODEL_B — create a new subscription row (EXPIRED stays terminal)

Repository support: the design spec §2 target explicitly declares 0..N
subscriptions per tenant; the v2 read models already tolerate/aggregate
multiplicity; invoices and provisioning/ledger/items are subscription-keyed.
The EXPIRED row is never mutated (immutable terminal history) and the new row
is born through the existing, proven creation path.

Open (undefined) MODEL_B semantics — the reason this is a gate, not an
implementation task: see §10–§11 (selection rules, active multiplicity) and
§12 (migration). The R0C-8 final report §22 explicitly anticipated this:
"either a canonical revival command … or an operator flow for superseding an
expired subscription — **needs an explicit semantic order; do not invent it
here**."

### MODEL_C — expired tenant may never subscribe again

Rejected: no product/business evidence declares the dead end intentional.
R0C-8 §21 #2 classifies it as "a well-defined **product gap**, not a
contradiction" and recommends R0C-9 for the decision. MODEL_C would also
contradict the 0..N target (a tenant with one EXPIRED row may hold 0 "current"
subscriptions but must be able to hold 1 again).

### Selection

**SUBSCRIPTION_MULTIPLICITY_MODEL = MODEL_B** — as the repository-declared
target direction (A is invalid per its gate; C is rejected by evidence; B is
the only model consistent with the authoritative target). Implementation is
**not authorized** in R0C-9: the continuation contract still contains UNKNOWNs
(§14) and the schema/consumer convergence requires a dedicated task (§12).
**R0C_9_STATUS = BLOCKED_MIGRATION_REQUIRED** per order §21.

MODEL_EVIDENCE = design spec §2 (`Tenant └── 0..N Subscriptions`) + §3 (command
set has no EXPIRED exit) + V19:57 (`UNIQUE (tenant_id)`) + SaasAdministrationService:293
(count guard) + R0C-8 plan §21 #2/§22 ("product gap … needs an explicit
semantic order") + PG-02/PG-05b/PG-06 (PostgreSQL proofs).

---

## 7. Historical vs simultaneous multiplicity (do not conflate)

- **HISTORICAL_MULTIPLE_SUBSCRIPTIONS_ALLOWED** (current) = **NO** — impossible
  at the storage layer (`UNIQUE (tenant_id)` rejects ANY second row, including
  terminal ones — PG-06). Target (MODEL_B) = YES: many historical rows, each
  terminal row immutable.
- **MULTIPLE_SIMULTANEOUS_ACTIVE_SUBSCRIPTIONS_ALLOWED** = **UNDEFINED** —
  current schema makes it impossible; the target design does not define it
  either: `EntitlementResolver` assumes ≤1 ACTIVE (`LIMIT 1`, M-08), the item
  model already supports multi-PLAN inside ONE subscription
  (V20260829_3: "ERP + HRM + CRM plans … never the same plan twice"), which
  reduces the need for simultaneous ACTIVE subscriptions, but no authoritative
  source forbids or allows two ACTIVE rows. This is a required R0C-10 input
  (§16).

**CURRENT_SUBSCRIPTION_SELECTION_RULE = UNDEFINED.** The repository carries
three divergent de facto rules: entitlement/impact = ACTIVE + arbitrary-first
(M-08/M-10); tenant directory display = LATEST by created_at (M-13); billing =
unqualified first row + tenant-scoped writes (M-03/04/05); usage limits =
max-merge (M-17). No rule is authoritative; none defines BILLING_EFFECTIVE or
ENTITLEMENT_EFFECTIVE semantics. AMBIGUOUS reads must reach 0 (§5) before
MODEL_B can be implemented.

---

## 8. Billing impact (no changes made in R0C-9)

**BILLING_MULTIPLICITY_IMPACT = MATERIAL.** `BillingStateService` is
tenant-keyed four ways (M-03–M-06): subscription selection by
`findFirst`, billing_state writes fan out by `tenant_id` (would mark ALL the
tenant's historical rows), overdue invoices counted per tenant (an expired
row's straggler OPEN invoice would drag the tenant's NEW subscription into
dunning), and the dunning scan returns duplicate tenant ids. `billing_state`
itself lives on the subscription row (per-row, already 0..N-shaped) and
`billing_invoices.subscription_id` exists (FK) — so subscription-scoped
convergence is mechanical, but the BILLING_EFFECTIVE selection rule must be
defined first. Not BLOCKING (no divergence possible while UNIQUE(tenant_id)
holds; the impact is the required convergence scope, not a live defect).

R0C-8 already resolved the trial-end billing behavior: no billing_state write
at EXPIRE, dunning leaves EXPIRED rows inert.

---

## 9. Entitlement impact (no changes made in R0C-9)

**ENTITLEMENT_MULTIPLICITY_IMPACT = MATERIAL.** The Source-of-Truth chain is
`tenant → (one) ACTIVE subscription → plan → plan_module_entitlements`
(M-08 `LIMIT 1`); `ItemAwareEntitlementResolver` extends the SAME pick with
item-derived merges (M-09). With 0..N and ≥2 non-terminal subscriptions the
ENTITLEMENT_EFFECTIVE authority is undefined (arbitrary ACTIVE row wins).
Design spec §4 defines the item-merge generalization but not the
multi-subscription selection. PG-08 proves the current EXPIRED behavior:
denied everywhere (this is the product pain of the dead end). Not BLOCKING
for the same reason as billing: unreachable while the uniqueness constraint
holds.

---

## 10. Provisioning / usage impact (no changes made in R0C-9)

- **PROVISIONING_MULTIPLICITY_IMPACT = LOW.** `provisioning_jobs` and every
  `ProvisioningJobRunner` step are subscription-id-keyed (M-19); jobs are
  enqueued per subscription id (`LifecycleController.enqueueJob`); the runner
  fails closed on terminal statuses (an EXPIRED subscription refuses
  activation — "Subscription is terminal (EXPIRED); refusing to activate").
  A second (new) subscription simply gets its own job. No tenant-keyed path.
- **USAGE_MULTIPLICITY_IMPACT = LOW.** Usage events/aggregates are
  tenant-scoped BY DESIGN (design spec §5; FORCE-RLS tables, M-18) — usage
  metering belongs to the tenant, not the subscription. Limit resolution
  max-merges plan-derived + item-derived limits across all non-terminal
  subscriptions (M-17) — already multiplicity-tolerant; the "highest limit
  wins" policy should be confirmed as intended when the effective-subscription
  rules are defined. Plan items / prices / read models: M-12/M-14/M-15 —
  no break.

---

## 11. Security / tenant isolation

**TENANT_ISOLATION_IMPACT = DEFINED — no weakening.** Evidence:

- RLS inventory (verified against the migrated schema): `usage_events` /
  `usage_aggregates` are ENABLE+FORCE RLS (V20260830_2, fail-closed, scoped by
  `TenantRlsTransactionContext`); CRM/mobile/commerce/websites/ERP/governance/
  finance/workflow/AI/analytics tables are RLS-enabled. The control-plane
  subscription tables (`tenant_subscriptions`, `subscription_items`,
  `subscription_commands`, `subscription_change_events`, `billing_invoices`,
  `provisioning_jobs`) are deliberately platform-scoped — exactly the design
  spec §11 rule ("RLS where the table pattern requires it; catalog tables are
  platform-scoped") — guarded by `@RequireCapability` +
  `ControlPlaneAccessGuard` (executive RBAC), not tenant RLS.
- Multiple historical rows for ONE tenant add rows INSIDE that tenant's
  boundary: every subscription-scoped query keys on `id`; every tenant-keyed
  query filters `tenant_id = ?` (M-02..M-11). No consumer reads "any tenant's"
  subscription unscoped.
- PG-09: tenant A's dead-end state never reaches tenant B's rows, ledger,
  events, invoices or entitlement resolution; zero cross-tenant ledger rows.
- A future partial-unique migration (§12) does not touch RLS policies; the
  control-plane guard set is unchanged.

---

## 12. Migration impact gate (design only — migration NOT created)

**MIGRATION_REQUIRED = YES** (MODEL_B cannot store multiple rows per tenant
while `uk_tenant_subscriptions_tenant` exists).

**MIGRATION_SCOPE (design for R0C-10):**

1. **Old constraint:** `uk_tenant_subscriptions_tenant UNIQUE (tenant_id)`
   (V19:57).
2. **New constraint/index — depends on the multiple-active decision (§7):**
   - Option IN-1 (at most one non-terminal per tenant):
     `CREATE UNIQUE INDEX uk_tenant_subscriptions_tenant_live ON tenant_subscriptions (tenant_id) WHERE status IN ('DRAFT','PENDING_ACTIVATION','PENDING_PAYMENT','TRIAL','TRIALING','ACTIVE','PAST_DUE','GRACE_PERIOD','PAUSED','SUSPENDED')`
     — matches the likely product model (many history rows + at most one
     effective) and keeps every existing single-row tenant valid.
   - Option IN-2 (at most one ACTIVE only): partial index filtered to
     `status='ACTIVE'` — permits e.g. ACTIVE + PAUSED simultaneously.
   - Option IN-3 (no uniqueness): multiplicity enforced only in the writer —
     highest risk (no storage backstop), not recommended.
   - A non-unique read-support index on `(tenant_id, status)`/`(tenant_id,
     created_at DESC)` is needed in every option.
   - **The choice requires an explicit semantic order (same class of decision
     as the R0C-8 §22 recommendation) — R0C-9 does not decide it.**
3. **Data preconditions:** `SELECT tenant_id, COUNT(*) FROM tenant_subscriptions
   GROUP BY tenant_id HAVING COUNT(*) > 1` must be empty (trivially true today
   — PG-06); for IN-1, no tenant may have >1 non-terminal row; verified as a
   pre-flight reconciliation check, REPORT_ONLY.
4. **Rollout:** forward-only, additive-first (new index created before the old
   constraint drops), single migration, Flyway versioned, idempotent guards
   (`IF EXISTS`). **Rollback/recovery:** if no tenant has >1 row, restoring
   `UNIQUE (tenant_id)` is exact; otherwise terminal-history rows must be
   excluded (archival/supersede policy) — rollback design must be part of the
   R0C-10 order, not improvised.
5. **Read-path changes (the 7 ambiguous sites, §5):** M-02 (creation gate →
   non-terminal scoped), M-03/M-04 (BILLING_EFFECTIVE selection), M-05
   (billing_state write by subscription id), M-08 (ENTITLEMENT_EFFECTIVE
   selection), M-10/M-11 (effective plan pick). M-13 LATEST and M-17 max-merge
   already qualify.
6. **Writer changes:** `createSubscription` guard (M-02) becomes
   non-terminal-scoped (or removed per IN-3); `BillingStateService` writes by
   subscription id; the legacy `resumeSubscription` PG-03b silent no-op must be
   made explicit (reject EXPIRED, or route through the decided model).
7. **RLS implications:** none (§11 — control-plane tables stay
   platform-scoped; usage tables stay tenant-RLS).
8. **Billing implications:** subscription-scoped overdue counting (M-06);
   dunning scan by subscription; BILLING_EFFECTIVE rule defined.
9. **Entitlement implications:** ENTITLEMENT_EFFECTIVE rule defined (M-08/M-09);
   item-merge stays (design spec §4).

**STOP after this design — a dedicated implementation task follows (§16).**

---

## 13. Current-subscription selection audit

See §5 matrix + §7. Every tenant-keyed query classified: LATEST (M-13),
ACTIVE-arbitrary (M-08/M-10), NON_TERMINAL-arbitrary (M-11), unqualified
(M-03/M-04/M-05, M-02), aggregate-max (M-17, M-16), per-row/aggregates
(M-14/M-15), id-keyed (all others). BILLING_EFFECTIVE and
ENTITLEMENT_EFFECTIVE do not exist as defined semantics anywhere —
**CURRENT_SUBSCRIPTION_SELECTION_RULE = UNDEFINED**;
AMBIGUOUS_TENANT_SUBSCRIPTION_READS = 7 → must be 0 before MODEL_B
implementation (order §13).

---

## 14. EXPIRED continuation product contract (explicit table)

Model-B semantics as the target direction; every field that is UNKNOWN marks
implementation as NOT AUTHORIZED.

| Field | Contract |
|---|---|
| ACTION | CREATE_NEW_SUBSCRIPTION (supersede the EXPIRED row; direction per MODEL_B) |
| ALLOWED? | **NOT AUTHORIZED YET** — blocked on migration (§12) + selection rules (§7) + the UNKNOWNs below |
| NEW_ROW? | YES (MODEL_B) |
| OLD_ROW_MUTATED? | **NO** — EXPIRED stays immutable terminal history (R0C-8 contract; no resurrection — R0C-8 race-proof writer guard) |
| TARGET_STATUS | new row born `ACTIVE` (non-trial create) or `TRIALING` (trial create) via the existing create path |
| PLAN_SELECTION | operator-selected ACTIVE plan (existing `activePlan` validation) |
| PLAN_VERSION_SELECTION | `resolveActivePlanVersion` fail-closed (R0C-4 canonical, exists) |
| SEATS | operator input, validated against plan `max_users` (exists) |
| BILLING_CYCLE | MONTHLY/ANNUAL, normalized (exists) |
| TRIAL_ALLOWED_AGAIN? | **UNKNOWN** — no authoritative re-trial policy (plan `trial_days` exists, but nothing defines whether a tenant with EXPIRED trial history may receive a second trial; abuse-prevention policy undefined) |
| INVOICE | initial recurring invoice on non-trial create (exists); **UNKNOWN:** whether/how a historical EXPIRED row's OPEN straggler invoices are scoped (M-06 convergence) |
| ENTITLEMENTS | **UNKNOWN** — ENTITLEMENT_EFFECTIVE rule undefined (M-08/M-09 convergence) |
| PROVISIONING | per-subscription PROVISION_SUBSCRIPTION job → ACTIVATE only after success (exists, subscription-scoped, terminal-fail-closed) |
| AUDIT | `SUBSCRIPTION.CREATE` platform audit per creation (exists, subscription-scoped) |
| EVENTS | `subscription_commands` ledger + `SUBSCRIPTION.CREATED` change event + entitlement recalc after commit (exists, subscription-scoped) |
| OLD-ROW SIDE-EFFECTS | **UNKNOWN→must-fix:** legacy `resumeSubscription` on EXPIRED records a false `SUBSCRIPTION.RESUMED` event/audit (PG-03b) — the converged writer must make EXPIRED an explicit rejection (or a real, decided revival) |

Any UNKNOWN ⇒ implementation not authorized (order §17). This table is the
input contract for the R0C-10 order, which must resolve each UNKNOWN from
product authority before code.

---

## 15. PostgreSQL Direct evidence summary

Environment: user-space PostgreSQL 17.11 (Debian trixie packages extracted to
`~/tools/pgroot`, `initdb` cluster on `:5432`), provisioned exactly per the CI
contract (bootstrap superuser `postgres`; least-privilege application role
`sanad` NOSUPERUSER/NOBYPASSRLS; `sanad` + `test_migration` databases owned by
`sanad`; `crm_contact_rls_test_user`), Flyway 143 migrations validated per
test class. The RED battery (12 tests) and the full pre-existing R0C-8 suite
(28 tests) both ran green against this instance (§16 for the full battery).

| Test | Result |
|---|---|
| ExpiredContinuationDeadEndPostgresTest (PG-01..PG-09, 12 tests) | PASS 12/12 |
| TrialExpirationRuntimePostgresTest (predecessor regression, 28 tests) | PASS 28/28 |
| Regression scope 1 — subscription.* / admin.* / module.entitlement.* / health.* | PASS 335/0/0/0 |
| Regression scope 2 — executive.* / module.* / security.* | PASS 183/0/0/0 |
| Regression scope 3 — crm.* / commerce.* / e2e.* | 1186/0/2E/6S; both error classes re-run green 25/0/0/0 once the harness env var `CRM_CUSTOM_FIELD_ENCRYPTION_KEY` was supplied — root cause environment-only (context-load tests), not code (R0C-9 changed zero production code) |
| Documented intentional skips | 6 (CI concurrency job is the authoritative path — same classification as the R0C-8 closure report 2428/0/0/6) |

POSTGRESQL_DIRECT = AVAILABLE; FAILURES = 0; ERRORS = 0 (all classes green
after harness env correction; no code-attributable failure).

---

## 16. Next implementation task (R0C-10 proposal)

**R0C-10 — Subscription Multiplicity Schema + Consumer Convergence.**
Prerequisites/inputs it must resolve (in order):

1. **Domain decision order** (product authority, one document): confirm
   MODEL_B for EXPIRED continuation; define the active-multiplicity invariant
   (IN-1/IN-2/IN-3, §12.2); define TRIAL_ALLOWED_AGAIN policy (§14); define
   BILLING_EFFECTIVE + ENTITLEMENT_EFFECTIVE selection rules (§7/§13).
2. **Schema migration** per §12 (new partial-unique/index → drop
   `uk_tenant_subscriptions_tenant`; pre-flight REPORT_ONLY reconciliation;
   forward-only; rollback plan).
3. **Consumer convergence** of the 7 ambiguous sites (§5) to the defined
   semantics; billing_state writes by subscription id; subscription-scoped
   overdue counting; dunning scan by subscription.
4. **createSubscription gate** → non-terminal-scoped (the dead-end fix), with
   the PG-03b legacy-resume no-op made an explicit rejection.
5. Full RED→GREEN protocol on PostgreSQL Direct (dead-end battery flips GREEN
   on the continuation path), reconciliation recert, regression suite.

R0C_10_READY = **NO** — blocked on (1) the explicit domain decision order
(the repository deliberately defers it: R0C-8 §22 "needs an explicit semantic
order; do not invent it here") and on a credentials-enabled session for remote
checkpoints. The forensic/contract work R0C-10 needs is complete in this
document.

---

## 17. Remote durability

- CHECKPOINT 1 (branch creation): local `de32ef7a` (== verified remote base
  head). Push attempt failed — no credentials (§1 caveat).
- CHECKPOINT 2 (RED/forensic evidence): local `9d1c25de` —
  `ExpiredContinuationDeadEndPostgresTest` (12/12 PG proofs). Push attempt
  failed — no credentials.
- CHECKPOINT 3 (final contract document + regression evidence): final commit
  on `scp/r0c-9-expired-continuation-contract` (see FINAL_HEAD in the final
  response). Push pending credentials.
- LOCAL_HEAD = FINAL_HEAD (single branch, linear: de32ef7a → 9d1c25de → final);
  LOCAL ≠ REMOTE until a credentialed push happens (`git push origin
  scp/r0c-9-expired-continuation-contract`). Base head on origin was verified
  read-only and is untouched; no merge, no deploy, no force push; no secret
  values printed at any point.

**REMOTE_CHECKPOINTS_VERIFIED = NO (credential blocker — pushes blocked;
remote base verified read-only).** Per order §24 this is a documented blocker,
not a fabricated pass: the task ends BLOCKED_MIGRATION_REQUIRED with the
push blocker surfaced for the operator, exactly like the PMV-942 precedent in
the shared worklog.
