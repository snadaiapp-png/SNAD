# SCP R0C-6 — Anchored PLAN Seat Quantity Convergence

**Status:** PASS
**Branch:** `scp/r0c-6-anchored-plan-seat-quantity` from `scp/r0c-5-multiplan-anchor-authority` @ `69b87c6fe0b8efea890f74253e07bc9a0eb0c26a`
**Chain of record:** `69b87c6f` (base) → `36450473` (RED) → `32463cc3` (GREEN) → docs commit (final)

---

## 1. Durable predecessor

`git fetch origin --prune`; `git rev-parse origin/scp/r0c-5-multiplan-anchor-authority` =
`69b87c6fe0b8efea890f74253e07bc9a0eb0c26a`; live `git ls-remote` returned the identical
SHA (checked twice: task start and final gate). Branch created literally from the remote
predecessor, pushed before any modification, `LOCAL_HEAD = REMOTE_HEAD` verified at every
checkpoint. `origin/main` never moved (`7f30c4ff1f8c8f856bb17126fb6364c9eae6b291`).
R0C5_BASE_VERIFIED = YES.

## 2. Seat writer / reader + item-quantity writer / reader matrices (§5)

Built BEFORE any mutation (prove-first). Whole-tree scans over `src/main`:
`SET seat_quantity`, `INSERT INTO tenant_subscriptions`, `SET quantity`,
`updateQuantityAndAmount`, `setQuantity(`, `seat_quantity` reads, `item.quantity` reads.

### SEAT_QUANTITY_WRITE_MATRIX (`tenant_subscriptions.seat_quantity`)

| # | Writer | Class | Mirror? |
|---|---|---|---|
| W1 | `SaasAdministrationService.create()` — INSERT with `request.seatQuantity()` | CANONICAL_SEAT_WRITER | yes — `insertInitialPlanItem(..., seatQuantity)` (R0C-4) |
| W2 | `SaasAdministrationService.changeSeats()` — UPDATE (credit/decrease + plain variants) | CANONICAL_SEAT_WRITER | **no — the R0C-6 defect; now mirrored via the canonical sync** |

Callers: `AdminPlatformService.provisionTenant` → `createSubscription` (delegation, not
an independent writer). `UNKNOWN_SEAT_WRITERS = 0`.

### Seat readers

| Reader | Use |
|---|---|
| `issueRecurringInvoice` | `price(plan, cycle) × subscription.seatQuantity()` — **billing authority** |
| `proratedAdjustment` (plan change) | old/new amounts `price × seatQuantity` |
| `changeSeats` proration | `(newSeats − oldSeats) × unitPrice` prorated |
| `validateUsageAgainstPlan` | seats vs `plan.maxUsers` (+ occupied-organization check) |
| `MAX(seat_quantity)` (admin platform aggregate), `HealthIntelligenceService.seat_capacity` | analytics |
| `SubscriptionGridQueryService` / `SubscriptionDetailService` / `SubscriptionResponse` RowMapper | read models / wire |

### PLAN_ITEM_QUANTITY_WRITE_MATRIX (`subscription_items.quantity`)

| # | Writer | Class |
|---|---|---|
| W1 | `SubscriptionChangeService.insertInitialPlanItem` → `repository.insert` | CANONICAL_ANCHORED_PLAN_QUANTITY_WRITER (seat copy at birth / composition change) |
| W2 | `SubscriptionChangeService.syncAnchoredPlanSeatQuantity` → `updateQuantityAndAmount` (NEW, R0C-6) | CANONICAL_ANCHORED_PLAN_QUANTITY_WRITER (sanctioned seat mirror, quantity-only) |
| W3 | `SubscriptionItemService.addItem` → `repository.insert` | SECONDARY_PLAN_INITIAL_QUANTITY / GENERIC_NON_PLAN_QUANTITY_WRITER (ADD_ON/METERED; duplicate-same-plan guarded fail-closed) |
| W4 | `SubscriptionItemService.updateQuantity` → `updateQuantityAndAmount` | GENERIC_NON_PLAN_QUANTITY_WRITER (PLAN rejected fail-closed, R0C-5 §10) |

`UNKNOWN_PLAN_QUANTITY_WRITERS = 0`; `UNSAFE_ANCHORED_PLAN_QUANTITY_WRITERS = 0`
(the only writers of the anchored item's quantity are the canonical pair W1/W2, both
seat-anchored). `CartService` writes `commerce_cart_items.quantity` — a different table,
out of scope.

### Item-quantity readers

| Reader | Use |
|---|---|
| `SubscriptionItemRepository` RowMapper | all item reads |
| `SubscriptionChangeService.preview` | `currentMonthly = nvl(item.unitAmountMinor)`; `targetMonthly = compute(price, anchoredItem.quantity)` — the item mirror feeds preview pricing |
| `SubscriptionChangeService.execute` | carries `currentPlanItem.getQuantity()` into the new item |
| `ScpDtos.SubscriptionItemResponse`, `SubscriptionItemController` SET_QUANTITY response, detail/grid item lists | wire / read models |
| `ItemEntitlementRepository` | ADD_ON/METERED entitlement rows only — quantity-agnostic |

**E — copy sites of seatQuantity into the anchored item:** `create` (→
`insertInitialPlanItem`), legacy `changePlan(IMMEDIATE)` and renewal pending application
(→ `applyCanonicalPlanCompositionChange(..., before.seatQuantity(), ...)`). `changeSeats`
was the only seat writer with **no** copy — closed in R0C-6.

**F — legacy recurring invoice:** `Math.multiplyExact(price(plan, billingCycle),
subscription.seatQuantity())` (line 699 of the predecessor) — never `item.quantity`.

## 3. Quantity authority decision (§6, §7, §15)

- `tenant_subscriptions.seat_quantity` = **legacy commercial seat-count authority**
  (billing, proration, validation read it — proven in §2).
- The compatibility-anchored ACTIVE PLAN item `quantity` = **item-model mirror** of the
  same seat count. New invariant: `ANCHORED_PLAN_ITEM.quantity = seat_quantity`.
- Multi-plan rule: the mirror applies to the ANCHORED item only — secondary PLAN
  quantities are independent billable lines (§7: A=10, X=3, Y=1 → changeSeats 10→15 ⇒
  A=15, X=3, Y=1; ONLY_ANCHORED_PLAN_QUANTITY_CHANGES = YES).
- **ANCHORED_UNIT_AMOUNT_SEMANTICS = UNIT_PRICE_PER_SEAT_SNAPSHOT**: seeded as
  `price(plan, billingCycle)` at item birth; billing computes `price × seats`
  independently of `item.unit_amount_minor × item.quantity`; SCP preview exposes it as
  the current monthly amount. Therefore a seat change modifies **quantity only** and
  the unit amount stays unchanged — defined, not invented.

## 4. RED evidence (§8, commit `36450473`)

PostgreSQL Direct (PG 16.15, least-privilege `sanad` role, Flyway-migrated isolated
schema — no Docker/Testcontainers/H2). 26-test battery written BEFORE the fix,
run against pristine predecessor `69b87c6f`:

```
Tests run: 26, Failures: 11, Errors: 0
```

- **PG-01 RED_ANCHOR_QUANTITY_DIVERGENCE = PASS**: seeded seats=5 / anchored A qty=5 /
  secondary X qty=2 → real `SaasAdministrationService.changeSeats(sub, 8)` →
  `tenant_subscriptions.seat_quantity = 8`, anchored A `.quantity = 5` (stale),
  X = 2 (preserved). The defect was reproducible — fix proceeded.
- PG-02/03/05/07/20/21: convergence/invariant assertions failed (mirror stale, both
  directions — decrease left the item at 8 while seats went to 5).
- PG-09/10/11: the fail-closed guards did not exist (no exception; missing-anchor even
  stranded the seat write outside a Spring TX).
- PG-23: reconciliation counted 2 ANCHOR_QUANTITY_MISMATCH rows (a healthy sub became
  divergent through the sanctioned path).
- PG-12/13/13b PASSED on the predecessor (invoice/audit/seat failures already rolled
  back the transaction — historical semantics documented, §7 of this doc).
- PG-04/06/08/14..19/22/24/25 PASSED (secondary preservation, billing math, no-op,
  isolation, item-admin guards, create mirror, country authority — regression baselines).

RED commit pushed; checkpoint verified (`36450473`).

## 5. Multi-plan quantity semantics (§7)

Exactly as ordered: the seat quantity is applied ONLY to the anchored PLAN item.
PG-05 (§14): A=5, X=2, Y=7 → changeSeats 5→9 ⇒ A=9, X=2, Y=7 — no status changes, no
version changes, no unit-amount changes on any item; PG-04 proves the secondary row is
byte-identical (including `updated_at`). SECONDARY_PLAN_QUANTITY_DELTA = 0.

## 6. Canonical anchored quantity sync (§10)

`SubscriptionChangeService.syncAnchoredPlanSeatQuantity(subscriptionId, expectedPlanId,
newSeatQuantity)` — the shared internal operation in the canonical subscription domain
(no duplicated ad-hoc lookup inside `changeSeats`):

1. Resolves the compatibility anchor row (`tenant_id, plan_id, plan_version_id`) via a
   RowMapper over the PK-addressed row — fail-closed on unknown subscription
   (P0-B discipline: no multi-column scalar queryForObject).
2. Validates the caller's expected plan against the stored anchor —
   `ANCHOR_PLAN_ID_MISMATCH` (stale read / concurrent plan change).
3. Finds the exact ACTIVE anchored PLAN item by `subscriptionId + planId`
   (unique-index-backed deterministic lookup) — missing ⇒ `MISSING_ANCHORED_PLAN_ITEM`.
4. Version consistency: anchor `plan_version_id` present and different from the item's
   pin ⇒ `ANCHOR_PLAN_VERSION_MISMATCH` (both-null / one-null pass — dual-compat).
5. Updates the anchored item's quantity through
   `updateQuantityAndAmount(id, newSeatQuantity, item.getUnitAmountMinor())` —
   **quantity only**, unit price preserved.

Contract: REQUIRED propagation (joins the caller transaction); NO invoice, NO
proration, NO billing state, NO secondary PLAN changes, NO entitlement events, NO unit
price changes. Duplicate active same-plan remains structurally impossible
(`uk_subscription_items_active_plan`). Cross-tenant context is rejected upstream
(executive capability path; tenant-scoped item API carries `expectedTenantId`).

## 7. Atomic transaction contract + failure rollback evidence (§11–§13)

`changeSeats` (single `@Transactional` boundary): seat/credit UPDATE → canonical sync →
prorated invoice (increase) → `SEATS.CHANGED` event → `SUBSCRIPTION.SEATS.CHANGE` audit
→ response. **SEAT_COUNT_PARTIAL_STATE = IMPOSSIBLE.**

| Case | Injection | Result (PG-Direct, TransactionTemplate reproducing the production TX boundary) |
|---|---|---|
| A (PG-09) | anchored item missing | rejected before billing side effects; seats=5, X=2, no invoice, no credit, no event |
| B (PG-10) | anchor version ≠ item pin | entire operation rejected; state untouched |
| C (PG-11) | trigger raising on `subscription_items.quantity` UPDATE | seat update rolls back (seats=5, item=5, X=2, no invoice/event) |
| D (PG-12) | trigger raising on `tenant_subscriptions.seat_quantity` UPDATE | item quantity unchanged; nothing committed |
| E (PG-13) | `billing_invoices` renamed (insert fails on positive adjustment) | seat + item + credit + event all roll back — the prorated invoice historically shares the same transaction |
| F (PG-13b) | audit mock throws | whole operation rolls back (documented actual semantics — audit is inside the TX contract) |

## 8. Secondary PLAN preservation (§14)

PG-04/PG-05/PG-16/PG-17/PG-19/PG-20/PG-21: secondary items keep quantity, status,
version, unit amount and `updated_at` through every seat change, plan change, and
renewal. `ONLY_ANCHORED_PLAN_QUANTITY_CHANGES = YES`.

## 9. Billing / proration comparison (§9, §21)

- `LEGACY_BILLING_SEAT_SOURCE = tenant_subscriptions.seat_quantity` — proven from the
  production path (`issueRecurringInvoice`, `proratedAdjustment`, seat proration).
- `BILLING_ENGINE_CHANGED = NO` — zero lines touched in the billing math.
- PG-06 increase: exactly one invoice, subtotal = `(new − old) × price ×
  remaining-fraction` (HALF_UP, ±2 instant-drift tolerance in the harness only);
  description "Prorated seat increase"; credit applied 0. PRORATION_AMOUNT_DELTA = 0.
- PG-07 decrease: credit += prorated amount, no invoice. CREDIT_AMOUNT_DELTA = 0.
- PG-08 no-op: no update, no invoice, no event, no credit — nothing at all.
- PG-22 create mirror: initial invoice = `6 × price` — seat-based, unchanged.

## 10. Item-admin regressions (§16, §17)

PG-15: `SubscriptionItemController → SET_QUANTITY → PLAN` remains fail-closed
(`IllegalStateException` pointing at the seat-change path) — not reopened.
PG-16 secondary ADD valid; PG-17 secondary CANCEL valid; PG-18 anchored generic CANCEL
rejected. R0C-5 contracts byte-identical.

## 11. Tenant isolation (§20)

PG-14: tenant-B subscription rows byte-identical through any tenant-A seat change;
cross-tenant item-quantity mutation via the tenant-scoped item API DENIED
(`expectedTenantId` → "different tenant"). CROSS_TENANT_SEAT_MUTATION = DENIED: there is
no tenant-scoped seat-mutation surface — seats change only through the executive
operator capability path (`PATCH /subscriptions/{id}/seats`, `EXECUTIVE_MANAGE` +
`ControlPlaneAccessGuard`); the CI least-privilege contract (`sanad` =
NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS) is re-proven by every PG-Direct run
in this report.

## 12. Reconciliation extension (§18) + historical mismatch count (§19)

New REPORT_ONLY classification, anchored-restricted exactly like the R0C-5 taxonomy:

```sql
-- ANCHOR_QUANTITY_MISMATCH
SELECT ... FROM tenant_subscriptions s
JOIN subscription_items i ON i.subscription_id = s.id
    AND i.item_type = 'PLAN' AND i.status = 'ACTIVE' AND i.plan_id = s.plan_id
WHERE i.quantity <> s.seat_quantity
```

PG-23: a historical drift row is classified (count=1) and NOT repaired (REPORT_ONLY);
a healthy sub converged through the sanctioned path stays converged (new-write
mismatch = 0). PG-24: secondary quantities (2, 99 ≠ 5) never count. Existing
classifications (MISSING_ANCHORED_PLAN_ITEM, DUPLICATE_ACTIVE_SAME_PLAN,
ANCHOR_PLAN_ID_MISMATCH, ANCHOR_PLAN_VERSION_MISMATCH, PLAN_VERSION_PLAN_MISMATCH,
ORPHAN_PLAN_VERSION) unchanged. Production DB is not reachable from this environment:
production `ANCHOR_QUANTITY_MISMATCH_COUNT` is not queryable here; any subscription
whose seats changed via `changeSeats` between R0C-4 and R0C-6 carries drift, therefore
`HISTORICAL_QUANTITY_REPAIR_REQUIRED = YES` (repair deliberately NOT executed — out of
scope; recommended as an explicit follow-up task with a dedicated authorization).

## 13. Full PostgreSQL Direct evidence (§24)

`AnchoredPlanSeatQuantityPostgresTest` — 26/26 PASS after GREEN (70.17 s):

PG-01 RED divergence (now converges) · PG-02 5→8 convergence · PG-03 invariant both
directions · PG-04 secondary X preserved · PG-05 X+Y preserved · PG-06 increase
proration unchanged · PG-07 decrease credit unchanged · PG-08 no-op · PG-09 missing
anchor rollback · PG-10 version-mismatch rollback · PG-11 item-update failure rollback ·
PG-12 seat-update failure rollback · PG-13 invoice failure atomicity · PG-13b audit
failure atomicity · PG-14 tenant isolation · PG-15 PLAN SET_QUANTITY rejected ·
PG-16 secondary add · PG-17 secondary cancel · PG-18 anchored cancel rejected ·
PG-19 multi-plan change · PG-20 legacy IMMEDIATE · PG-21 renewal pending · PG-22 create
mirror · PG-23 reconciliation detects mismatch · PG-24 reconciliation ignores
secondary · PG-25 R0C-2R country authority.

Re-certifications on the R0C-6 head (serial): `MultiPlanAnchorAuthorityPostgresTest`
21/21 (R0C-5), `SaasAdministrationLegacyConvergencePostgresTest` 8/8 (R0C-4),
`SubscriptionAnchorPostgresTest` 8/8 (R0C-3), `SubscriptionChangeServicePostgresTest`
8/8 (R0C-2R PG), `SubscriptionChangeServiceTest` 7/7 (R0C-2R units) — 45/7, zero
regressions. Dedicated pg-acceptance job (fresh `pg_acceptance` DB, least-privilege
role, `SPRING_PROFILES_ACTIVE=pg-acceptance`): `CommerceOrderPostgresConcurrencyTest`
6/6.

## 14. Full Maven evidence (§27)

Serial foreground run (offline, `-B -ntp -Dsurefire.useFile=false`, ephemeral
`CRM_CUSTOM_FIELD_ENCRYPTION_KEY` per CI contract, PG-Direct datasource):

```
Tests run: 2504, Failures: 0, Errors: 0, Skipped: 6
BUILD SUCCESS — Total time: 09:53 min
```

(2469 at R0C-5 + 26 PG + 9 unit = 2504; the 6 skips are the intentional
pg-acceptance profile guards, executed authoritatively in the dedicated job above.)

## 15. Remaining writers (§26 rescan)

Post-GREEN scan of the whole `src/main` tree:
seat_quantity writers = 2 (both canonical, both mirrored); subscription_items.quantity
writers = 4 (canonical pair + guarded generic/secondary paths). Detailed matrices in
§2. `UNKNOWN_SEAT_WRITERS = 0`, `UNKNOWN_PLAN_QUANTITY_WRITERS = 0`,
`UNSAFE_ANCHORED_PLAN_QUANTITY_WRITERS = 0`. No new migrations
(`git diff 69b87c6f..HEAD -- src/main/resources/db` = empty; NEW_MIGRATIONS = 0).

## 16. R0C-7 recommendation

1. **Anchored-plan repair tooling** (explicitly authorized, audit-logged): a REPORT_ONLY
   → operator-approved remediation flow for the existing classifications
   (MISSING_ANCHORED_PLAN_ITEM / DUPLICATE_ACTIVE_SAME_PLAN / ANCHOR_PLAN_ID_MISMATCH /
   ANCHOR_PLAN_VERSION_MISMATCH / ANCHOR_QUANTITY_MISMATCH), including the historical
   seat-mirror drift created between R0C-4 and R0C-6. No silent background repair.
2. **Read-model convergence**: `SubscriptionDetailService` / grid item lines could
   surface the seat mirror explicitly (today they already reflect it post-R0C-6).
3. **Legacy anchor deprecation roadmap** (per the SCP design doc): move PLAN_QUANTITY
   reads (preview pricing) from the mirrored item quantity toward the billing-authority
   seat count, then retire `tenant_subscriptions.plan_id` columns in a staged effort.

R0C_7_READY = YES (the multi-plan + anchored-mirror model is now internally consistent
and fully covered; no STOP conditions are pending).

## 17. Remote durability evidence (§30)

| Checkpoint | Commit | LOCAL = REMOTE |
|---|---|---|
| 1 — branch creation (empty) | `69b87c6f` | verified (ls-remote) |
| 2 — RED battery | `36450473` | verified (push + ls-remote) |
| 3 — GREEN production fix | `32463cc3` | verified (push + ls-remote) |
| 4 — tests/docs final | (docs commit) | verified (final gate below) |

Governance: MERGE = NO, DEPLOY = NO, no force push, no main modifications, serial Maven
only, no secret values printed at any point (credentials live outside the repo at
`/home/z/my-project/scripts/.secrets.env`, referenced by a temporary askpass helper).

## 18. Final gate values

```
R0C5_BASE_VERIFIED            = YES
RED_ANCHORED_QUANTITY_DIVERGENCE = YES
SEAT_QUANTITY_AUTHORITY       = DEFINED (tenant_subscriptions.seat_quantity)
ANCHORED_PLAN_QUANTITY_MIRROR = CONVERGED
SECONDARY_PLAN_QUANTITY_DELTA = 0
GENERIC_PLAN_QUANTITY_MUTATION = FAIL_CLOSED
SEAT_COUNT_PARTIAL_STATE      = 0 (single TX, cases A–F proven)
PRORATION_AMOUNT_DELTA = INVOICE_AMOUNT_DELTA = CREDIT_AMOUNT_DELTA = 0
EVENT_DELTA = AUDIT_DELTA = 0  (one SEATS.CHANGED, one SUBSCRIPTION.SEATS.CHANGE)
TENANT_ISOLATION               = PASS
RECONCILIATION_MODEL           = MULTIPLAN_SAFE_PLUS_ANCHOR_QUANTITY
UNKNOWN_SEAT_WRITERS = UNKNOWN_PLAN_QUANTITY_WRITERS = 0
UNSAFE_ANCHORED_PLAN_QUANTITY_WRITERS = 0
POSTGRESQL_DIRECT = FULL_MAVEN_SUITE = PASS
FAILURES = 0, ERRORS = 0, SKIPPED = 6
NEW_MIGRATIONS = 0
MERGE = NO, DEPLOY = NO
```
