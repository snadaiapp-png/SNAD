# SCP Recovery Chain — STAGE-3 (R0C-4) Re-Certification

**Task**: R0C-RECOVERY-CHAIN
**Stage**: STAGE-3 — R0C-4 (legacy plan-write convergence)
**Branch**: `scp/r0c-recovery-chain`
**Stage-2 base**: `1bc660fa` (remote-verified STAGE-2 head)
**Date**: 2026-09-03

The original R0C-4 branch was lost (sandbox reset); its findings are hypothesis
only. Everything below was re-discovered and re-proven on the recovery branch.

---

## 1. Re-discovered legacy divergence (current repository)

| Writer | Divergence (pre-fix, code-verified) |
|---|---|
| `SaasAdministrationService.createSubscription` | **Defect A**: non-trial creation (`trialDays = 0`) computes `trialEndsAt = null` and calls `Timestamp.from(null)` → NPE; the create path had never worked for non-trial. **Defect C**: no `plan_version_id` and no initial ACTIVE PLAN item — subscriptions were born outside the canonical composition model (the SCP change engine then refuses them: "no ACTIVE PLAN item to change"). |
| `SaasAdministrationService.changePlan(IMMEDIATE)` | Wrote `plan_id` directly on `tenant_subscriptions`, never touched `subscription_items` (old PLAN item stayed on the old plan) and never set `plan_version_id` — reverse divergence (anchor moved, effective composition did not). |
| `SaasAdministrationService.renewSubscription` pending-apply | Same direct `plan_id` write; scheduled changes never reached the items. |
| `SubscriptionDetailService` timeline | **Defect B**: `Map.of` with `rs.getObject("from_status")` — the UNION emits literal NULLs for EVENT rows → NPE for any subscription with legacy change events. |

## 2. RED evidence (PostgreSQL Direct, STAGE-2 head 1bc660fa + new test only)

New test class:
`src/test/java/com/sanad/platform/subscription/change/SaasAdministrationLegacyConvergencePostgresTest.java`

RED run: `Tests run: 8, Failures: 5, Errors: 3` — **all 8 tests failed with the
exact defect signatures**:

- `nonTrialCreateSucceedsWithCanonicalComposition` → `NullPointerException:
  Cannot invoke "java.time.Instant.getEpochSecond()" because "instant" is null`
  (defect A, real PostgreSQL)
- `trialCreateBirthsCanonicalComposition` → ACTIVE PLAN item count 0 (defect C)
- `createFailsClosedWithoutActivePlanVersion` → no exception; create succeeded
  (missing fail-closed version resolution)
- `legacyImmediatePlanChangeConvergesComposition` → item stayed on plan A, no
  version anchor (IMMEDIATE divergence)
- `legacyRenewalAppliesPendingPlanConvergence` → same for renewal (renewal
  divergence)
- `legacyChangePlanIsTenantScoped` → control tenant had no PLAN item at all
  (defect C again)
- `detailTimelineSurvivesLegacyEvents` → NPE (defect B)
- `scpCanonicalPathUnchanged` → "Subscription has no ACTIVE PLAN item to
  change" — the SCP engine could not operate on legacy-created subscriptions

## 3. Fixes (minimal, no migration, no schema change)

1. **Canonical authority extension** (`SubscriptionChangeService`):
   - `resolveActivePlanVersion(planId)` — deterministic latest ACTIVE
     `plan_version` (ordered by version_number); fails closed with CONFLICT
     when the plan has no ACTIVE version. `TARGET_VERSION_RESOLUTION =
     DETERMINISTIC`.
   - `insertInitialPlanItem(...)` — canonical birth of the initial ACTIVE
     PLAN item (composition-only contract).
   - `applyCanonicalPlanCompositionChange(...)` — THE single canonical
     authority for effective PLAN composition mutation: cancel old ACTIVE
     PLAN item + insert new (pinned to the target version) + converge BOTH
     anchors + write the command ledger. REQUIRED propagation joins the
     caller's transaction. Composition-only: no pricing, no proration, no
     invoices, no entitlement events.
   - `execute()` (SCP path) refactored to delegate to
     `applyCanonicalPlanCompositionChange` — the SCP and legacy paths now
     share literally one composition writer.
2. **Legacy convergence** (`SaasAdministrationService`):
   - `createSubscription`: version resolved fail-closed BEFORE any row is
     written; `plan_version_id` persisted; initial PLAN item birthed via the
     canonical authority; **defect A fixed** with a null-safe timestamp bind
     (`trialEndsAt == null ? null : Timestamp.from(...)` — no fake dates, no
     sentinels); invoice/audit/event/entitlement behavior untouched.
   - `changePlan(IMMEDIATE)`: legacy UPDATE now covers only billing_cycle /
     credit_balance / pending-clears; plan composition (items + anchors +
     ledger) goes through the canonical authority. Proration, upgrade
     invoice, `PLAN.CHANGE.APPLIED` event, audit, and the single
     `SUBSCRIPTION_PLAN_CHANGED` entitlement event are unchanged.
   - `renewSubscription`: the pending-plan application routes through the
     canonical authority (legacy keeps billing_cycle, status, periods,
     renewal invoice, events, audit). The no-pending branch is unchanged
     (its `plan_id` write is a self-write that alters no composition).
   - Constructor gains the canonical dependency (5-arg `@Autowired`); a
     4-arg self-wiring constructor remains for direct instantiation.
3. **Defect B** (`SubscriptionDetailService`): timeline rows now built with a
   null-tolerant `LinkedHashMap` instead of `Map.of` — legacy EVENT rows with
   NULL from/to statuses (and NULL reasons) no longer NPE the whole detail
   read; wire shape (keys) unchanged.

## 4. GREEN evidence (PostgreSQL Direct)

```
SaasAdministrationLegacyConvergencePostgresTest   Tests run: 8,  F/E: 0/0
SubscriptionAnchorPostgresTest                   Tests run: 8,  F/E: 0/0  (STAGE-2 regression)
SubscriptionChangeServicePostgresTest            Tests run: 8,  F/E: 0/0  (STAGE-1 regression)
SubscriptionChangeServiceTest                    Tests run: 7,  F/E: 0/0  (unit regression)
```

Key contracts proven on real PostgreSQL 16:

- Non-trial create: status ACTIVE, `trial_ends_at` NULL (legitimate NULL
  binding), version anchor + exactly-one ACTIVE PLAN item at the legacy price,
  initial invoice (30000 × 2 seats), 1 audit, 1 entitlement event.
- Trial create: TRIALING + non-null trial end + composition birthed, no
  initial invoice (unchanged), 1 audit, 1 entitlement event.
- Create fails closed on a plan with no ACTIVE version: CONFLICT + zero
  partial rows (`PARTIAL_CHANGE_STATE = 0`).
- Legacy IMMEDIATE change: old item CANCELLED, exactly-one ACTIVE PLAN item on
  plan B/version B at the legacy price, anchors converged, pending cleared,
  command ledger row, prorated upgrade invoice, `PLAN.CHANGE.APPLIED` event,
  1 audit, 1 entitlement event → `LEGACY_IMMEDIATE_DIVERGENCE = 0`,
  `BILLING_SEMANTIC_DELTA = 0`, `DUPLICATE_ENTITLEMENT_EVENTS = 0`.
- NEXT_CYCLE: scheduling moves no composition (item still A, pending set);
  renewal applies the pending plan through the canonical authority; renewal
  invoice + events + entitlement counts unchanged →
  `LEGACY_RENEWAL_DIVERGENCE = 0`.
- Tenant isolation: tenant B's subscription, items, ledger and invoices
  untouched by tenant A's change.
- Detail timeline: EVENT rows (NULL statuses) and COMMAND rows (real
  statuses) both returned.
- SCP canonical change path works on a legacy-created subscription (the two
  authorities now interoperate on the same composition model).

## 5. Full Maven suite

`mvn test -B -ntp` (CI env contract, serial): **BUILD SUCCESS —
Tests run: 2444, Failures: 0, Errors: 0, Skipped: 6** (2436 + 8 new STAGE-3
tests; the 6 skips are the pre-existing intentional pg-acceptance skips).

## 6. Post-fix plan-writer inventory (systematic scan)

Sources: `rg "UPDATE tenant_subscriptions"`, `rg "INSERT INTO subscription_items|UPDATE subscription_items|DELETE FROM subscription_items"`,
`rg "INSERT INTO subscription_commands"` over `src/main/java`.

| ID | Component | Writes | Classification |
|---|---|---|---|
| W1 | `SubscriptionChangeService.insertInitialPlanItem` | `subscription_items` (birth) | **CANONICAL_AUTHORITY** |
| W2 | `SubscriptionChangeService.applyCanonicalPlanCompositionChange` | items + `plan_id`/`plan_version_id` anchors + command ledger | **CANONICAL_AUTHORITY** |
| W3 | `SubscriptionChangeService.execute` (SCP) | delegates to W2 | CANONICAL_SCP |
| W4 | `SaasAdministrationService.createSubscription` | anchors in INSERT + W1 | LEGACY_COMPATIBILITY (converged) |
| W5 | `SaasAdministrationService.changePlan(NEXT_CYCLE)` | `pending_plan_id`/`pending_billing_cycle` (schedule metadata only; applied via W2 at renewal) | LEGACY_COMPATIBILITY |
| W6 | `SaasAdministrationService.renewSubscription` (no pending) | `SET plan_id = <current>` self-write — no composition change | LEGACY_COMPATIBILITY (documented no-op) |
| W7 | `SaasAdministrationService` changeSeats/cancel/resume | seats/status/cancel flags — not plan composition | NON_COMPOSITION |
| W8 | `SubscriptionItemService` add/cancel/setQuantity | `subscription_items` for any type incl. PLAN (via repository) | ITEM_ADMIN — **known, deliberately NOT closed here; R0C-5 TRACK-B scope** |
| W9 | `BillingStateService` | `billing_state` + status | NON_COMPOSITION |
| W10 | `SubscriptionCommandService` | status + commands ledger | NON_COMPOSITION (lifecycle) |
| W11 | `ProvisioningJobRunner` | status | NON_COMPOSITION |

`UNKNOWN_PLAN_WRITERS = 0` — every writer found by the systematic scan is
classified above. Within the closed scope of STAGE-3 (create / immediate
change / renewal pending application / SCP path),
`UNSAFE_DUPLICATE_PLAN_WRITERS = 0`: the only writer of effective plan
composition is `SubscriptionChangeService`.

**Remaining finding for R0C-5**: W8 (`SubscriptionItemService`) can still
add/cancel/quantity-mutate PLAN items through generic item administration —
that is the R0C-5 TRACK-B mandate (PLAN item administration authority
closure) and is intentionally out of scope here, as is the seats-vs-item
quantity mirror question (W7) and item status on lifecycle cancellation.

## 7. Stage-3 acceptance checklist

| Requirement | Result |
|---|---|
| UNKNOWN_PLAN_WRITERS = 0 | ✅ §6 systematic scan |
| LEGACY_IMMEDIATE_DIVERGENCE = 0 | ✅ items+anchors converged via canonical authority |
| LEGACY_RENEWAL_DIVERGENCE = 0 | ✅ pending applied via canonical authority (semantics proven: pending columns + renewal apply + events) |
| ACTIVE_PLAN_ITEM_COUNT = 1 | ✅ after create, change, renewal, and every rollback |
| PLAN_ID_MISMATCH / PLAN_VERSION_ID_MISMATCH = 0 (new writes) | ✅ |
| TARGET_VERSION_RESOLUTION = DETERMINISTIC | ✅ latest ACTIVE version, fail closed |
| PARTIAL_CHANGE_STATE = 0 | ✅ fail-closed create leaves zero rows; rollbacks proven in STAGE-2 |
| BILLING_SEMANTIC_DELTA = 0 | ✅ legacy pricing/proration/invoice code untouched; counts+amounts asserted |
| DUPLICATE_ENTITLEMENT_EVENTS = 0 | ✅ exactly one entitlement event per operation |
| TENANT_ISOLATION = PASS | ✅ |
| WIRE_COMPATIBILITY_BREAKS = 0 | ✅ routes, DTOs, response shapes unchanged (one crash fixed: detail timeline NPE) |
| POSTGRESQL_DIRECT = PASS | ✅ 8/8 |
| FULL_MAVEN_SUITE = PASS | ✅ 2444/0/0/6 |
| NEW_MIGRATIONS = 0 | ✅ no migration files touched |

## 8. Files changed (STAGE-3)

- `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/change/SubscriptionChangeService.java` (canonical authority: resolve/birth/apply; execute delegates)
- `apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/SaasAdministrationService.java` (converged create/changePlan/renewal; defect A fix)
- `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/SubscriptionDetailService.java` (defect B null-safety)
- `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/change/SaasAdministrationLegacyConvergencePostgresTest.java` (new — RED/GREEN evidence)
- `docs/superpowers/plans/2026-09-03-scp-recovery-stage-3-r0c4.md` (this document)
