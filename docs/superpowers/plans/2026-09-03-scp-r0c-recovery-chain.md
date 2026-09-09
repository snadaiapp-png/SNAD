# SCP Recovery Chain — Final Report

**Task**: R0C-RECOVERY-CHAIN — Re-establish durable Subscription Closure Chain
**Branch**: `scp/r0c-recovery-chain` (single connected chain, pushed to origin)
**Durable base**: `origin/main @ 7f30c4ff1f8c8f856bb17126fb6364c9eae6b291` (verified before any work; unchanged after)
**Date**: 2026-09-03
**Context**: the original R0C-2R / R0C-3 / R0C-4 branches were destroyed by a
sandbox reset (hashes ccb9c76f / 8fae3048 / 2d5a9a72 unreachable locally, on
origin, and via direct SHA fetch; recovery bundles absent). This chain
re-closed all three stages from the last verifiable durable state, with a
remote checkpoint after every stage (LOCAL-ONLY commits are not durable — §3).

## Chain topology

```
7f30c4ff (origin/main, verified base)
   ├─ STAGE-1 (R0C-2R)  commits 7e297ac4 → acb08c66   [pushed, remote-verified]
   ├─ STAGE-2 (R0C-3)   commits → 1bc660fa            [pushed, remote-verified]
   └─ STAGE-3 (R0C-4)   commits → bb4ef275            [pushed, remote-verified]
```

Every stage was RED-proven on pristine stage predecessors (PostgreSQL Direct)
before its fix; every stage's full Maven suite is green; every stage pushed
and verified via `git ls-remote` before the next stage started.

## Stage summaries

### STAGE-1 — R0C-2R (re-certified) — PASS
- P0-A invalid tenant subscription country source — CONFIRMED + CLOSED:
  pricing country is now always the server-side authoritative
  `tenants.country_code` (GLOBAL fallback when absent); the client-supplied
  country parameter is ignored. CLIENT_COUNTRY_AUTHORITY = NONE.
- P0-B multi-column scalar queryForObject — CONFIRMED + CLOSED: replaced by a
  single RowMapper subscription-context query (tenant_subscriptions JOIN
  tenants). 4 RED `IncorrectResultSetColumnCount` errors → 0.
- P0-C subscription_commands.to_status overflow — CONFIRMED + CLOSED without
  a migration: ledger from/to carry real lifecycle statuses (≤ 17 chars);
  TARGET_VERSION detail lives in the VARCHAR(500) reason.
- Pre-existing harness flakiness (order-dependent CRM outbox tests) was
  reproduced on pristine 7f30c4ff first and fixed test-only (§17 protocol).
- Full suite at closure: **2428 / 0 / 0 / 6**.

### STAGE-2 — R0C-3 (re-certified) — PASS
- Anchor divergence re-proven (item moves to plan B, `plan_id`/
  `plan_version_id` anchors stay on plan A; grid/detail read models stale).
- `execute()` now performs CANCEL + INSERT + UPDATE ANCHORS + LEDGER inside
  one transaction; rollback proven at two failure injection points;
  exactly-one ACTIVE PLAN item invariant; grid/detail consistency; tenant
  isolation; reconciliation REPORT_ONLY with zero new-write mismatches.
- Full suite at closure: **2436 / 0 / 0 / 6**.

### STAGE-3 — R0C-4 (re-certified) — PASS
- Legacy divergence re-proven: create NPE (defect A), create without
  version/PLAN item (defect C), changePlan(IMMEDIATE) and renewal
  pending-application never touching items, detail timeline NPE (defect B),
  SCP engine refusing legacy-born subscriptions — 8/8 RED.
- Convergence: `SubscriptionChangeService` is now THE canonical authority
  (`resolveActivePlanVersion` deterministic + fail-closed,
  `insertInitialPlanItem`, `applyCanonicalPlanCompositionChange`);
  `execute()` delegates to it; legacy create / IMMEDIATE change / renewal
  pending-application route through it while legacy billing (proration,
  invoices, credit), audit, entitlement events, and wire shapes are
  unchanged.
- Full suite at closure: **2444 / 0 / 0 / 6**.

## Final writer inventory (§19)

Systematic scans (`UPDATE tenant_subscriptions`, all `subscription_items`
writers, `INSERT INTO subscription_commands`) — every hit classified:

- Canonical: `SubscriptionChangeService` (insertInitialPlanItem /
  applyCanonicalPlanCompositionChange / execute).
- Legacy compatibility (converged or non-composition): `SaasAdministrationService`
  (create via canonical; NEXT_CYCLE pending metadata; no-pending renewal
  self-write; seats/status/cancel non-composition), `BillingStateService`,
  `SubscriptionCommandService`, `ProvisioningJobRunner`.
- **UNKNOWN_PLAN_WRITERS = 0.**
- **UNSAFE_DUPLICATE_PLAN_WRITERS = 0 within the closed stage scope** (create /
  immediate / renewal / SCP). Known remaining, deliberately deferred:
  `SubscriptionItemService` generic item administration can still mutate PLAN
  items — exactly the R0C-5 TRACK-B mandate.

## Final reconciliation (§20) — REPORT_ONLY

Classification queries (MISSING_ACTIVE_PLAN_ITEM, MULTIPLE_ACTIVE_PLAN_ITEMS,
PLAN_ID_MISMATCH, PLAN_VERSION_ID_MISMATCH) run in STAGE-2/STAGE-3 PG tests:
historical divergent rows are classified and NOT repaired; every newly
generated write (create / change / renewal / SCP) contributes
**NEW_WRITE_MISMATCH_COUNT = 0**. No bulk repair, no backfill, no healer.

## R0C-5 readiness gate (§21)

- STAGE1 = PASS, STAGE2 = PASS, STAGE3 = PASS ✅
- REMOTE_STAGE{1,2,3}_VERIFIED = YES (ls-remote equality at every checkpoint) ✅
- LOCAL_FINAL_HEAD = REMOTE_FINAL_HEAD ✅
- RECOVERY_CHAIN_DURABLE = YES ✅

**R0C_5_READY = YES**

Recommended R0C-5 scope (already specified by the R0C-5 order): (A) create
reliability deep-dive (the NPE fix is in; atomicity/billing/trial regression
suites are largely in place from this chain), and (B) PLAN item
administration authority closure in `SubscriptionItemService`/
`SubscriptionItemController` — the one remaining PLAN composition writer
outside the canonical authority, plus the seats-vs-item quantity question.

## Governance

MERGE = NO. DEPLOY = NO. main untouched (still `7f30c4ff`). No new
migrations across all three stages. No force push. PostgreSQL Direct
(real PostgreSQL 16.15 server, CI-identical role/database contract) used for
all acceptance evidence; Docker/Testcontainers/H2 never used.
