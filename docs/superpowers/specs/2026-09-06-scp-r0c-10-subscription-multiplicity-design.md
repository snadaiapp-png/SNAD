# R0C-10 Design — Subscription Multiplicity + Consumer Convergence + EXPIRED Runtime + Mixed-Version Safe Enablement

Status: APPROVED DESIGN AUTHORITY (frozen R0C-9 contract inherited verbatim)
Date: 2026-09-06
Branch: `scp/r0c-10-subscription-multiplicity-runtime` (from R0C9_FINAL_HEAD `13c144e2a23bbdb34325b13f664489fb9440bc0c`)
Model: `SUBSCRIPTION_MULTIPLICITY_MODEL=MODEL_B`

---

## 0. Inherited Frozen Contract (R0C-9, not re-opened)

- Historical subscriptions: `tenant -> 0..N`
- Effective/current subscriptions: `tenant -> 0..1`
- Effective definition: `status NOT IN ('CANCELLED', 'EXPIRED', 'TERMINATED')`
- Terminal statuses: `CANCELLED`, `EXPIRED`, `TERMINATED`
- `EXPIRED` — historical row immutable; continuation creates a NEW subscription row; never mutated back.
- `CANCELLED` — R0C-7 RESUME semantics retained; create-new successor after CANCELLED NOT implemented (fail closed); CANCELLED resume rejected when an effective successor exists.
- `TERMINATED` — re-subscribe deferred; fail closed; no successor behavior implemented.
- Repeat trial — automatic second trial FORBIDDEN; an EXPIRED successor never silently receives another trial.
- Billing — effective subscription is the unique non-terminal row; invoice/dunning authority is `subscription_id`-scoped; historical invoices may never dunn/suspend/downgrade a successor.
- Entitlement — resolve the unique effective/non-terminal subscription first, then apply lifecycle/status gating.
- Provisioning — `subscription_id`-scoped; never tenant-only ambiguous binding.
- `resume(EXPIRED)` — FAIL_CLOSED: no status mutation, no RESUMED change event, no misleading audit, no entitlement recalculation, no provisioning side effect, no billing side effect.
- Mixed-version invariant — `OLD_BINARY + MULTIPLE_SUBSCRIPTION_ROWS = NEVER ALLOWED`.
- No physical DELETE of subscription history.

Nothing in this design invents product behavior beyond the frozen contract.

## 1. Effective vs History Semantics

One storage table (`tenant_subscriptions`) holds both history and the current row.
The distinction is a query contract, not a column:

- **EFFECTIVE** — rows with `status NOT IN ('CANCELLED','EXPIRED','TERMINATED')`.
  The partial unique index guarantees at most one such row per tenant.
- **ALL_HISTORY** — every row of the tenant, ordered deterministically by
  `(created_at DESC, id DESC)` (the same chronology contract the existing
  read services already use, e.g. `TenantDirectoryQueryService.subscription_status`).
- **EXACT_SUBSCRIPTION_ID** — lifecycle, invoice, provisioning, item and
  event work keyed by `subscription_id` (unchanged; already the repository norm).

Canonical chronology authority: `(created_at DESC, id DESC)` on
`tenant_subscriptions`. There is no sequence column; `created_at` is
`TIMESTAMP WITH TIME ZONE NOT NULL` and `id` is the PK — the composite is the
existing repository convention for "latest subscription" and is reused verbatim.

## 2. Repository / Query Contract (new component)

New `com.sanad.platform.subscription.lifecycle.SubscriptionResolutionService`
(single small read component; no unnecessary abstraction):

- `Optional<EffectiveSubscription> findEffectiveSubscription(UUID tenantId)`
  → `WHERE tenant_id = ? AND status NOT IN ('CANCELLED','EXPIRED','TERMINATED')
  ORDER BY created_at DESC, id DESC` (no arbitrary `LIMIT 1`; the partial unique
  index bounds cardinality at 1 — the deterministic ordering is defensive and
  keeps the query well-defined even pre-migration).
- `List<HistoricalSubscription> findAllHistory(UUID tenantId)`
  → `WHERE tenant_id = ? ORDER BY created_at DESC, id DESC` (ALL_HISTORY).
- `Optional<HistoricalSubscription> findLatestHistorical(UUID tenantId)`
  → same ordering, `LIMIT 1` — an EXPLICIT deterministic ordering with a
  documented contract (latest row decides the continuation rule). This is the
  only `LIMIT 1` introduced, and it is not arbitrary.
- `boolean hasEffectiveSubscription(UUID tenantId)`.

Consumers converge per the classification inventory in the implementation
plan (§4 of the plan doc):

- Current-state consumers (billing, entitlement, impact, directory limits,
  creation guard) use **EFFECTIVE**.
- History/reporting consumers (admin grids, executive overview, directory
  counts, health aggregates) keep **ALL_HISTORY** (they already aggregate
  deterministically; listing history is their product contract).
- Exact lifecycle/invoice/provisioning work stays **EXACT_SUBSCRIPTION_ID**.

## 3. Storage Invariant (one fresh Flyway migration)

Migration `V20260906_1__scp_subscription_multiplicity_model_b.sql`:

1. `ALTER TABLE tenant_subscriptions DROP CONSTRAINT uk_tenant_subscriptions_tenant;`
   (the legacy full-tenant UNIQUE that blocks MODEL_B history).
2. `CREATE UNIQUE INDEX uk_tenant_subscriptions_effective
   ON tenant_subscriptions (tenant_id)
   WHERE status NOT IN ('CANCELLED','EXPIRED','TERMINATED');`
   — the MODEL_B effective-uniqueness backstop (final concurrency authority).
3. `CREATE INDEX IF NOT EXISTS idx_tenant_subscriptions_tenant_status
   ON tenant_subscriptions (tenant_id, status);` — efficient effective/history
   lookup index (naming follows the existing `idx_tenant_subscriptions_*` convention).

- No data is rewritten; no row is deleted or modified (no physical DELETE).
- No RLS statement is touched: `tenant_subscriptions` is not an RLS-enforced
  table in this repository (verified across all 115 migrations — SCP core
  isolates at the application layer; module tables keep their RLS untouched).
- `ck_tenant_subscriptions_status` already permits terminal statuses
  (V20260830_1 widened it additively) — no CHECK change needed.

Database tests must prove (PostgreSQL Direct):
EXPIRED+ACTIVE allowed; EXPIRED+EXPIRED+ACTIVE allowed; CANCELLED+EXPIRED+ACTIVE
allowed; ACTIVE+ACTIVE rejected; TRIAL+ACTIVE rejected; multiple terminal rows
allowed; per-tenant isolation of the index; concurrency safety of the partial
unique index (two concurrent inserts — exactly one wins).

## 4. EXPIRED Successor Runtime (gated)

- `resume(EXPIRED)` is NOT changed into successor creation and stays FAIL_CLOSED
  (see §8).
- The approved continuation path is the existing public creation contract
  (`SaasAdministrationService.createSubscription`), extended with the MODEL_B
  guard:
  1. effective subscription exists → `409 CONFLICT` ("Tenant already has a
     subscription" — unchanged legacy wire contract);
  2. no effective subscription:
     - no history at all → plain first creation (unchanged);
     - latest historical row (deterministic chronology) is `EXPIRED`:
       - feature gate OFF → `409 CONFLICT` with the legacy message — the R0C-9
         dead-end behavior is preserved bit-for-bit while the gate is OFF;
       - feature gate ON → a NEW subscription row is inserted through the
         existing creation contract (same plan/version/usage validation, same
         invoice, event, audit side effects). The old EXPIRED row remains
         byte-for-byte immutable; the successor gets a fresh `subscription_id`;
         the tenant's `tenant_id` is unchanged; effective cardinality becomes
         exactly 1.
     - latest historical row is `CANCELLED` → `409 CONFLICT`
       ("create-new after CANCELLED is not supported; resume the subscription
       instead") — no CANCELLED successor semantics are implemented;
     - latest historical row is `TERMINATED` → `409 CONFLICT` ("re-subscription
       after TERMINATED is deferred") — deferred, fail closed.
- The partial unique index is the final database backstop for any race.

## 5. Repeat-Trial Prevention

In the gated successor path the trial decision is forced to "no trial":
`trial_days` is clamped to 0 regardless of plan default or request payload —
`status = 'ACTIVE'`, `trial_ends_at = NULL`. An EXPIRED successor can never
silently receive another trial (`AUTOMATIC_SECOND_TRIAL=REJECTED`).
Trial grants for successors as a manual/product policy are explicitly deferred
(out of R0C-10 scope; no override mechanism is invented).

## 6. CANCELLED / TERMINATED Semantics

- `resume(CANCELLED)` with NO effective successor → legacy R0C-7 revival
  (unchanged: period reset, recurring invoice, RESUMED event/audit/recalc).
- `resume(CANCELLED)` with an effective successor → `409 CONFLICT`
  (a cancelled row may not resurrect alongside an effective row).
- `create-new` after CANCELLED → `409 CONFLICT` (see §4).
- `resume(TERMINATED)` → `409 CONFLICT` (fail closed; terminal contract).
- `create-new / re-subscribe` after TERMINATED → `409 CONFLICT` (deferred).
- No TERMINATED successor behavior is implemented.

## 7. Billing Isolation (convergence)

`BillingStateService` converges:

- `findSubscription(tenantId)` → effective-only resolution via
  `SubscriptionResolutionService` (was: unqualified `WHERE tenant_id` picking
  an arbitrary row).
- `countOverdueInvoices` → **`subscription_id`-scoped**:
  `SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ?
  AND status = 'OPEN' AND due_at < ?` (was tenant-only predicate — the defect
  that let historical invoices dunn a successor).
- `applyTransition` → resolves and updates the EFFECTIVE row by
  `subscription_id` (`UPDATE ... WHERE id = ?`; was `WHERE tenant_id`).
- The dunning scan gains `AND status NOT IN ('CANCELLED','EXPIRED','TERMINATED')`
  so stale `billing_state` values on terminal rows can never re-enter the
  cycle; the scan remains the hint, `evaluateAndTransition(tenant)` re-resolves
  the effective row.

`billing_invoices.subscription_id` already exists (`UUID NOT NULL`, FK, V19) —
no billing schema extension is required (`R0C10_BILLING_SCHEMA_EXTENSION_REQUIRED`
does not apply).

Test must prove: with history `S1=EXPIRED` + `S2=ACTIVE` and an overdue
`I1(subscription_id=S1)`: no dunning state change on S2, no lifecycle command
on S2, no invoice/status/entitlement effect on S2 — only invoices of S2 may
affect S2.

## 8. `resume(EXPIRED)` False-Success Fix (P1)

`SaasAdministrationService.resumeSubscription`:

- `EXPIRED` / `TERMINATED` → `409 CONFLICT` BEFORE any side effect:
  `STATUS_MUTATION=0`, `RESUMED_CHANGE_EVENT=0`, `MISLEADING_AUDIT=0`,
  `ENTITLEMENT_RECALCULATION=0`, `PROVISIONING_SIDE_EFFECT=0`,
  `BILLING_SIDE_EFFECT=0`. The failure is surfaced, never swallowed.
- No successor is created through `resume()`.
- `CANCELLED` keeps the legacy path (plus the §6 successor-collision guard).

## 9. Entitlement + Provisioning Convergence

- `EntitlementResolver.findActiveSubscription` → resolve the unique EFFECTIVE
  subscription, then apply the existing lifecycle gate (`status = 'ACTIVE'`
  module-enable rule is preserved verbatim — no behavior invention: a TRIAL
  or EXPIRED-only tenant resolves exactly as before, but the resolution is now
  multiplicity-safe and free of an arbitrary `LIMIT 1`).
- `SubscriptionImpactService.getCurrentPlanCode` → same convergence.
- Provisioning is already `subscription_id`-scoped end-to-end
  (`provisioning_jobs`, `provisioning_job_steps`, step reads by id; terminal
  refusal built into VALIDATE). R0C-10 adds proof tests: a provisioning event
  for S1 must not change S2; cross-tenant ids must fail isolation.

## 10. Feature Gate (default OFF)

Repository convention (dunning/trial-expiry schedulers): system property with
environment fallback, default `false`:

- property: `sanad.scp.subscription.expired-successor.enabled`
- env var: `SANAD_SCP_EXPIRED_SUCCESSOR_ENABLED`
- default: **OFF** (`FEATURE_GATE_DEFAULT=OFF`)

Gate OFF: no EXPIRED successor creation, no second-row runtime activation —
the guard rejects exactly as the pre-R0C-10 binary did.
Gate ON: only the approved EXPIRED continuation path (§4) may create a successor.
The gate does NOT enable any production environment in this task.

## 11. Transaction / Concurrency Behavior

- Creation remains one transaction (`@Transactional` create contract): guard,
  INSERT, item anchor, invoice, event, audit commit or roll back atomically.
- Two concurrent successor creations (gate ON, expired-only tenant) are
  serialized by `uk_tenant_subscriptions_effective`: exactly one INSERT wins;
  the loser receives a unique-violation that is translated — deterministically,
  at the service boundary — into the repository-standard `409 CONFLICT`
  ("Tenant already has a subscription"). No raw PostgreSQL constraint details
  leak to API consumers; no idempotency semantics are invented.
- Historical rows are never locked for mutation by the successor path.

## 12. Rolling Deployment / Mixed-Version Safety

- The single INSERT path into `tenant_subscriptions` in production code is the
  guarded `createSubscription` (verified by inventory). Its guard counts ALL
  rows of the tenant, so an OLD (pre-R0C-10) binary refuses creation for any
  tenant with existing history — including terminal history. Therefore an old
  binary can never create a second historical row through any normal runtime
  path: `OLD_BINARY_SECOND_ROW_CREATION_PATHS=0`.
- Rollout order (see runbook): converged binary with gate OFF → migration →
  prove fleet compatibility → enable gate. `OLD_BINARY + MULTIPLE_ROWS` is
  structurally impossible in that order.

## 13. Rollback Restrictions

- Before any tenant has >1 historical row: rollback to a pre-multiplicity
  binary/schema is allowed only with proven schema/code compatibility.
- After ANY tenant has multiple rows: `ROLLBACK_TO_PRE_MULTIPLICITY_BINARY=FORBIDDEN`
  (restoring `UNIQUE(tenant_id)` would require deleting history — never done).
- Allowed rollback direction afterwards: only to a multiplicity-compatible
  binary. The kill-switch (gate) disables NEW successor creation only; existing
  historical rows remain.

## 14. PostgreSQL Tests / RLS / Tenant Isolation / Release Gate

- Dedicated R0C-10 PostgreSQL Direct acceptance suite (20 scenarios, §21 of
  the run directive): migration invariant, terminal history multiplicity,
  effective uniqueness, gated successor, EXPIRED immutability, resume
  fail-closed, no-second-trial, CANCELLED/TERMINATED semantics, billing
  isolation, entitlement/provisioning convergence, gate OFF/ON, concurrency,
  cross-tenant isolation, migration/index inventory.
- Cross-tenant isolation: tenant A cannot resolve B's effective subscription,
  read B's history, resume/create B's subscriptions, or affect B's
  billing/entitlement/provisioning.
- RLS regression: R0C-10 introduces no RLS statement, no privilege change, and
  no weakening of any RLS-enforced table; predecessor suites re-run serially.
- Release gate: all closure-gate fields of the run directive must be proven
  before `R0C_10_STATUS=CLOSED`; production feature enablement and R0C-11 are
  out of scope.
