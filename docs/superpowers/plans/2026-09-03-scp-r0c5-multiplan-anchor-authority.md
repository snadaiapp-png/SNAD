# SCP R0C-5 — Multi-Plan Compatibility Anchor + PLAN Item Authority

**Status:** PASS
**Branch:** `scp/r0c-5-multiplan-anchor-authority` from `scp/r0c-recovery-chain` @ `1e74887be09a9f120a1d432bb32e93fa70253733`
**Chain of record:** `1e74887b` (base) → `6009c40d` (RED) → `bdf04361` (GREEN) → docs commit (final)

---

## 1. Architectural contract — MULTI_PLAN_MODEL = CONFIRMED

Repository evidence, inspected before any production change (§2):

- `V20260829_3__scp_subscription_items.sql` header: *"A subscription is no longer
  assumed to be a single product/plan: Subscription ── 1..N subscription_items
  (PLAN | ADD_ON | METERED | OTHER)"*; the unique index comment: *"a subscription
  may carry ERP + HRM + CRM plans, but never the same plan twice"*.
- `uk_subscription_items_active_plan (subscription_id, plan_id) WHERE item_type='PLAN'
  AND status='ACTIVE'` rejects ONLY the same-plan duplicate.
- `docs/superpowers/specs/2026-08-29-subscription-control-plane-design.md`:
  *"1..N SubscriptionItems (PLAN | ADD_ON | METERED | OTHER; each may pin a
  PlanVersion)"*; `tenant_subscriptions.plan_id` is retained as the
  dual-compatibility anchor (never dropped in this effort).

Corrected invariant (replaces the R0C-3/4-era phrase "exactly one ACTIVE PLAN"):

```
MULTIPLE_DISTINCT_ACTIVE_PLAN_ITEMS = VALID
DUPLICATE_ACTIVE_SAME_PLAN          = INVALID
LEGACY_ANCHORED_PLAN_ITEM_COUNT     = 1
```

The **legacy-anchored PLAN item** is the ACTIVE `subscription_items` row with
`item_type='PLAN'` AND `plan_id = tenant_subscriptions.plan_id` (pinned to
`tenant_subscriptions.plan_version_id` when present). Secondary distinct PLAN
items are valid billable lines of the multi-product model.

## 2. PLAN quantity semantics — DEFINED = SEATS (§10)

Investigated, not guessed. Evidence:

| Evidence | Location |
|---|---|
| Composition changes carry the subscription's seat count into the item | `SaasAdministrationService.createSubscription` / `changePlan(IMMEDIATE)` / `renewSubscription` → `insertInitialPlanItem(..., seatQuantity)` / `applyCanonicalPlanCompositionChange(..., before.seatQuantity(), ...)` |
| Billing prices seats, not item quantity | `issueRecurringInvoice`: `price(plan, cycle) × subscription.seatQuantity()` |
| Seat limits are plan-contract limits | `validateUsageAgainstPlan(seats, plan.maxUsers)` + occupied-membership floor |
| The sanctioned quantity mutation path is billing-validated | `changeSeats` (proration + invoice + events) |

Therefore PLAN item quantity is the **seat mirror**: generic `SET_QUANTITY` on a
PLAN item is rejected fail-closed (`SubscriptionItemService.updateQuantity`) —
mutating it through the item API would desync the billing mirror. ADD_ON /
METERED quantity mutation remains supported.

## 3. Forensic classification (§4) — UNKNOWN_SINGLE_PLAN_ASSUMPTIONS = 0

| Site | Classification | Disposition |
|---|---|---|
| `SubscriptionItemRepository.findActiveBySubscriptionIdAndType` (singular `queryForObject`) | **SINGLE_PLAN_BUG** | §6: converted to LIST semantics; deterministic `findActiveBySubscriptionIdAndPlanId` introduced |
| `SubscriptionChangeService.preview` — `findFirst()` over ACTIVE PLAN stream | **SINGLE_PLAN_BUG** | §8: anchored selection via `tenant_subscriptions.plan_id` |
| `SubscriptionChangeService.execute` — singular current-item lookup | **SINGLE_PLAN_BUG** | §8: anchored lookup, fail-closed when missing |
| `SubscriptionChangeService.applyCanonicalPlanCompositionChange` — cancel singular | **SINGLE_PLAN_BUG** | §8: cancels ONLY the anchored item; secondary plans preserved; fail-closed target-already-secondary guard |
| `SubscriptionItemService.effectivePlanVersionId` — arbitrary ACTIVE PLAN preference | **SINGLE_PLAN_BUG** | §7: anchor → anchored item version → anchor-column fallback (deterministic) |
| `SubscriptionItemService.addItem` — no duplicate-same-plan guard | §9-B gap | fail-closed IllegalStateException (unique index remains the backstop) |
| `SubscriptionItemService.cancelItem` — cancels the anchored PLAN | §9-D gap | fail-closed; anchored replacement only via canonical authority |
| `SubscriptionItemService.updateQuantity` — PLAN quantity mutation | §10 gap | rejected (seats authority) |
| `SubscriptionGridQueryService` items COUNT / `SubscriptionDetailService` item list | MULTIPLAN_SAFE | unchanged |
| `ProvisioningJobRunner` ACTIVE items COUNT | MULTIPLAN_SAFE | unchanged |
| `UsageMeteringService` item JOIN (list) | MULTIPLAN_SAFE | unchanged |
| `ItemEntitlementRepository` / `ItemAwareEntitlementResolver` (ADD_ON/METERED merge) | MULTIPLAN_SAFE | unchanged |
| Grid/overview/detail/admin reads sourcing `s.plan_id` columns | ANCHOR_SPECIFIC | correct by contract |
| Migration backfill (one PLAN per legacy subscription) | UNRELATED | historical, idempotent |
| Test mocks (`SubscriptionChangeServiceTest`, `SubscriptionItemServiceTest`) | updated to the anchored contract | — |

## 4. RED evidence (§5, commit `6009c40d`)

PostgreSQL Direct (PG 16.15, least-privilege `sanad` role, Flyway-migrated,
real schema — no Docker/Testcontainers/H2). 13-test battery run BEFORE the fix:

```
Tests run: 13, Failures: 4, Errors: 4
```

Defects proven (all fail with `IncorrectResultSizeDataAccessException:
Incorrect result size: expected 1, actual 2` or wrong-plan selection):

- **§5 RED (PASS):** `findActiveBySubscriptionIdAndType` with two ACTIVE PLAN
  rows throws — SINGULAR_PLAN_TYPE_LOOKUP_RED = PASS.
- `effectivePlanVersionId` crashes on 2 ACTIVE PLANs (PG-04).
- `preview` prices the older-created secondary (X = 50000) instead of the
  anchored plan (A = 30000) — arbitrary `findFirst()` selection (PG-05).
- `execute` crashes on 2 ACTIVE PLANs (PG-06).
- Legacy IMMEDIATE change and renewal pending application crash on 2 ACTIVE
  PLANs (PG-17/19) — the canonical authority inherited the singular lookup.
- Anchored PLAN cancel NOT rejected; PLAN quantity mutation NOT rejected;
  duplicate same-plan add surfaces a raw constraint violation (PG-12/13/02B).

Model proofs passed at RED: distinct ACTIVE plans coexist (PG-01); the unique
index rejects only same-plan duplicates (PG-02); secondary add/cancel allowed
(PG-10/11).

## 5. GREEN (§6–§11, commit `bdf04361`)

Production changes (3 files, no migration):

1. **§6 repository contract** — `findActiveBySubscriptionIdAndType` → LIST;
   new deterministic `findActiveBySubscriptionIdAndPlanId` (unique-index
   backed). No method meaning "give me THE active PLAN" remains.
2. **§7 `effectivePlanVersionId`** — anchor plan → matching ACTIVE item's
   version → `tenant_subscriptions.plan_version_id` fallback (dual
   compatibility only).
3. **§8 change/preview engine** — preview selects the anchored plan (A) even
   when an older secondary (X) precedes it; `execute` and
   `applyCanonicalPlanCompositionChange` cancel ONLY the anchored item;
   secondary plans preserved through IMMEDIATE/NEXT_CYCLE/renewal changes;
   anchors move to B/versionB; fail-closed guard when the target plan is
   already ACTIVE as a secondary.
4. **§9 item admin contract** — (A) secondary distinct PLAN add: allowed,
   anchor unchanged; (B) duplicate same-plan add: rejected fail-closed;
   (C) secondary cancel: allowed, anchor unchanged; (D) anchored PLAN cancel
   via generic API: rejected fail-closed (canonical authority only).
5. **§10 PLAN quantity** — generic mutation rejected (seats authority, §2).
6. **§11 reconciliation model** — corrected classifications:
   `MISSING_ANCHORED_PLAN_ITEM`, `DUPLICATE_ACTIVE_SAME_PLAN`,
   `ANCHOR_PLAN_ID_MISMATCH`, `ANCHOR_PLAN_VERSION_MISMATCH`,
   `PLAN_VERSION_PLAN_MISMATCH`, `ORPHAN_PLAN_VERSION`; distinct secondary
   PLAN items are VALID/INFORMATIONAL; historical rows REPORT_ONLY, no repair.

## 6. Test evidence (§12/§15)

- **PG battery** `MultiPlanAnchorAuthorityPostgresTest`: **21/21 PASS**
  (PG-01..PG-19, PG-23; PG-20/21/22 re-certified by the R0C-4/R0C-2R
  regression suites below).
- **Re-certification battery** (R0C-2R `SubscriptionChangeServicePostgresTest`
  8/8, R0C-3 `SubscriptionAnchorPostgresTest` 8/8 with the corrected §11
  reconciliation model, R0C-4 `SaasAdministrationLegacyConvergencePostgresTest`
  8/8, unit `SubscriptionChangeServiceTest` 7/7, unit
  `SubscriptionItemServiceTest` 13/13): **44/44 PASS** — R0C2R/R0C3/R0C4
  regressions = 0.
- **Full Maven suite** (serial, `./mvnw test`, PostgreSQL Direct + ephemeral
  CI-contract CRM encryption key): **2469 tests, Failures: 0, Errors: 0,
  Skipped: 6 — BUILD SUCCESS** (09:06 min).
- NEW_MIGRATIONS = 0 (schema untouched; `git diff base..HEAD` includes no
  `db/migration` file).

## 7. Governance

- MERGE = NO, DEPLOY = NO, no force push; `origin/main` untouched at
  `7f30c4ff`.
- Serial Maven only (single `./mvnw test` process at a time).
- Remote checkpoints: branch creation `1e74887b`, RED `6009c40d`, GREEN
  `bdf04361`, docs (final) — each verified LOCAL_HEAD = REMOTE_HEAD.

## 8. Acceptance checklist (§17)

| Requirement | Value |
|---|---|
| MULTI_PLAN_MODEL | CONFIRMED |
| MULTIPLE_DISTINCT_ACTIVE_PLAN_ITEMS | VALID |
| DUPLICATE_ACTIVE_SAME_PLAN | REJECTED (fail-closed + unique index) |
| LEGACY_ANCHORED_PLAN_ITEM_COUNT | 1 (enforced by anchored selection) |
| SINGULAR_PLAN_TYPE_LOOKUP_BUG | CLOSED |
| EFFECTIVE_PLAN_VERSION_DETERMINISTIC | YES |
| PREVIEW_USES_ANCHORED_PLAN | YES |
| CANONICAL_CHANGE_PRESERVES_SECONDARY_PLANS | YES |
| DIRECT_ANCHORED_PLAN_CANCEL | FAIL_CLOSED |
| SECONDARY_PLAN_ADMIN_CONTRACT | DEFINED (§9 A–D) |
| PLAN_QUANTITY_SEMANTICS | DEFINED (seats) |
| TENANT_ISOLATION | PASS |
| RECONCILIATION_MODEL | MULTIPLAN_SAFE |
| R0C2R / R0C3 / R0C4 regressions | 0 / 0 / 0 |
| POSTGRESQL_DIRECT | PASS |
| FULL_MAVEN_SUITE | PASS (2469 / 0 / 0 / 6) |
| NEW_MIGRATIONS | 0 |
