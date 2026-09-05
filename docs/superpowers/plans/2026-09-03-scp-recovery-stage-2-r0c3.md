# SCP Recovery Chain — STAGE-2 (R0C-3) Re-Certification

**Task**: R0C-RECOVERY-CHAIN
**Stage**: STAGE-2 — R0C-3 (canonical plan anchor convergence)
**Branch**: `scp/r0c-recovery-chain`
**Stage-1 base**: `acb08c66` (remote-verified STAGE-1 head)
**Date**: 2026-09-03

The original R0C-3 branch was lost (sandbox reset); its claims are hypothesis
only. Everything below was re-proven on the recovery branch.

---

## 1. Re-discovered defect (current repository)

`SubscriptionChangeService.execute()` (as left by STAGE-1) swapped the PLAN
item — cancel old, insert new, write ledger — but never touched
`tenant_subscriptions.plan_id` / `plan_version_id`. Since both read models
(`SubscriptionGridQueryService`, `SubscriptionDetailService`) source plan data
from those anchor columns, every plan change left:

- effective ACTIVE PLAN item → plan B / version B
- compatibility anchors → still plan A / version A (or NULL)
- grid + detail UI → the OLD plan

That is the R0C-3 anchor divergence, re-established on the current tree.

## 2. RED evidence (PostgreSQL Direct, STAGE-1 head acb08c66 + new test only)

New test class:
`src/test/java/com/sanad/platform/subscription/change/SubscriptionAnchorPostgresTest.java`

RED run: `Tests run: 8, Failures: 4, Errors: 0`

| Test | RED evidence |
|---|---|
| `planChangeUpdatesAnchorsAndItemTogether` | anchor `plan_id` stays A while item is on B (`expected: <planB> but was: <planA>`) |
| `gridReadModelReflectsEffectivePlanAfterChange` | grid still shows plan A |
| `detailReadModelReflectsEffectivePlanAfterChange` | detail still shows plan A |
| `reconciliationIsReportOnlyAndNewWritesAreClean` | new-write mismatch count ≠ 0 |

The four passing tests at RED time (ledger rollback, plan-version-lookup
rollback, `@Transactional` annotation contract, tenant isolation) locked the
atomicity/isolation contracts that the fix must preserve.

## 3. Fix (minimal)

`SubscriptionChangeService.execute()` now performs, inside the existing
`@Transactional` method:

```
CANCEL old PLAN item
+ INSERT new PLAN item (pinned to target version)
+ UPDATE tenant_subscriptions SET plan_id, plan_version_id   ← added
+ WRITE subscription_commands ledger
= ATOMIC
```

No schema change, no read-model change, no migration (`NEW_MIGRATIONS = 0`).

## 4. GREEN evidence (PostgreSQL Direct)

```
SubscriptionAnchorPostgresTest           Tests run: 8,  Failures: 0, Errors: 0
SubscriptionChangeServicePostgresTest    Tests run: 8,  Failures: 0, Errors: 0
SubscriptionChangeServiceTest            Tests run: 7,  Failures: 0, Errors: 0
```

Key contracts proven on real PostgreSQL 16:

- **Exactly-one invariant**: `ACTIVE_PLAN_ITEM_COUNT = 1` after every change
  and after every failed (rolled-back) change.
- **Anchor convergence**: `plan_id`/`plan_version_id` match the ACTIVE PLAN
  item after change; `PLAN_ID_MISMATCH = 0`, `PLAN_VERSION_ID_MISMATCH = 0`
  for new writes.
- **Atomicity A** (ledger failure via table rename): old item restored to
  ACTIVE on version A, anchors unchanged, zero ledger rows, zero cancelled
  items.
- **Atomicity B** (plan-version lookup failure): same full rollback.
- **`execute()` is `@Transactional`** (annotation contract checked).
- **Grid consistency**: anchor-sourced grid row shows plan B / `v2` after the
  change.
- **Detail consistency**: overview shows planId B, planCode `R0C3-B`,
  planVersion 2.
- **Tenant isolation**: a change on tenant A's subscription leaves tenant B's
  subscription, items, and ledger completely untouched.
- **Reconciliation REPORT_ONLY**: the four classification queries
  (MISSING_ACTIVE_PLAN_ITEM, MULTIPLE_ACTIVE_PLAN_ITEMS, PLAN_ID_MISMATCH,
  PLAN_VERSION_ID_MISMATCH) report deliberately-seeded historical divergence
  without repairing it (the divergent row is still divergent after the run);
  newly-written canonical changes contribute zero mismatches
  (`NEW_WRITE_MISMATCH_COUNT = 0`).

## 5. Full Maven suite

`mvn test -B -ntp` (CI env contract, serial): **BUILD SUCCESS —
Tests run: 2436, Failures: 0, Errors: 0, Skipped: 6** (2428 + 8 new
STAGE-2 tests; the 6 skips are the pre-existing intentional
pg-acceptance-profile skips).

## 6. Stage-2 acceptance checklist

| Requirement | Result |
|---|---|
| ACTIVE PLAN item changes to B while anchors follow (RED first) | ✅ RED 4 failures → GREEN |
| CANCEL + INSERT + UPDATE ANCHOR + LEDGER atomic | ✅ two failure-injection rollbacks + `@Transactional` |
| exactly one ACTIVE PLAN item | ✅ after success and after rollback |
| plan_id mismatch = 0 (new writes) | ✅ |
| plan_version mismatch = 0 (new writes) | ✅ |
| grid consistency | ✅ |
| detail consistency | ✅ |
| rollback on failures | ✅ |
| tenant isolation | ✅ |
| reconciliation REPORT_ONLY | ✅ historical row classified, not repaired |
| NEW_MIGRATIONS = 0 | ✅ |
| FULL_MAVEN_SUITE = PASS | ✅ 2436/0/0/6 |

## 7. Files changed (STAGE-2)

- `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/change/SubscriptionChangeService.java` (anchor update in execute())
- `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/change/SubscriptionAnchorPostgresTest.java` (new — RED/GREEN evidence)
- `docs/superpowers/plans/2026-09-03-scp-recovery-stage-2-r0c3.md` (this document)
