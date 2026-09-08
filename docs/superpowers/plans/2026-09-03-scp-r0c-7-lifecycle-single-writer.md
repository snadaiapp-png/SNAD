# SCP R0C-7 — Lifecycle Status Single-Writer Convergence + Semantic Contract Reconciliation

**Date:** 2026-09-03
**Branch:** `scp/r0c-7-lifecycle-single-writer`
**Base:** `scp/r0c-6-anchored-plan-seat-quantity @ 7205c7c155f3aa79eec97deda3bd2336ed2c3786`
**Status:** PASS
**Governance:** RED-first TDD, PostgreSQL Direct (no Docker/Testcontainers/H2), serial Maven, MERGE = NO, DEPLOY = NO, NEW_MIGRATIONS = 0.

---

## 1. Durable predecessor

`origin/scp/r0c-6-anchored-plan-seat-quantity` verified via `git fetch origin --prune` +
`git rev-parse` + `git ls-remote`, both returning `7205c7c155f3aa79eec97deda3bd2336ed2c3786`
(exact match with the order's REQUIRED_BASE_HEAD). The R0C-7 branch was created from that
remote HEAD literally and pushed before any production modification.
R0C6_BASE_VERIFIED = YES.

## 2. Full status-writer matrix (LIFECYCLE_STATUS_WRITER_MATRIX)

Repository-wide scan for every production mutation of `tenant_subscriptions.status`
(UPDATE/INSERT, direct SQL, JdbcTemplate, services, jobs, listeners, billing,
provisioning, legacy admin):

| ID | File | Method | Entrypoint | From → To | Direct SQL? | Ledger? | Audit? | Event? | TX? | Classification |
|----|------|--------|-----------|-----------|-------------|---------|--------|--------|-----|-----------------|
| W1 | SaasAdministrationService | createSubscription (INSERT) | admin command API | birth → ACTIVE/TRIALING | YES (INSERT) | NO | YES | YES | YES | INITIALIZATION |
| W2 | SaasAdministrationService | cancelSubscription (immediate) | admin command API | mutable → CANCELLED | YES | NO | YES | YES | YES | LEGACY_DIRECT_TRANSITION |
| W3 | SaasAdministrationService | cancelSubscription (scheduled) | admin command API | none (flag) | NO status write | NO | YES | YES | YES | NOT_A_TRANSITION |
| W4 | SaasAdministrationService | resumeSubscription (non-CANCELLED) | admin command API | none (flag) | NO status write | NO | YES | YES | YES | NOT_A_TRANSITION |
| W5 | SaasAdministrationService | resumeSubscription (CANCELLED) | admin command API | CANCELLED → ACTIVE | YES | NO | YES | YES | YES | LEGACY_DIRECT_TRANSITION (Conflict A) |
| W6 | SaasAdministrationService | renewSubscription (cancelAtPeriodEnd) | admin command API | any → CANCELLED | YES | NO | YES | YES | YES | LEGACY_DIRECT_TRANSITION |
| W7 | SaasAdministrationService | renewSubscription (pending plan) | admin command API | any → ACTIVE | YES | NO | YES | YES | YES | LEGACY_DIRECT_TRANSITION (Conflict C) |
| W8 | SaasAdministrationService | renewSubscription (plain) | admin command API | any → ACTIVE | YES | NO | YES | YES | YES | LEGACY_DIRECT_TRANSITION (Conflict C) |
| W9 | BillingStateService | applyTransition (status mirror) | markInvoicePaid + dunning scheduler | per billing map (incl. SUSPENDED → ACTIVE and CANCELLED → ACTIVE resurrection) | YES | NO | billing audit only | NO | YES + swallowed failures | BILLING_DERIVED_TRANSITION (Conflict B, partial state) |
| W10 | ProvisioningJobRunner | executeStep VALIDATE | /executive provision + jobs/{id}/retry | non-terminal → ACTIVE | YES | NO | NO | NO | YES | PROVISIONING_DIRECT_TRANSITION |
| W11 | SubscriptionCommandService | execute | /executive subscriptions/{id}/lifecycle/{command} | SubscriptionLifecycle table | YES | YES | YES | YES | YES | CANONICAL_LIFECYCLE_TRANSITION |

FALSE_POSITIVE (not live writers): V20260815_20 / V20260829_2 migrations (historical
one-time backfills), SubscriptionChangeService:314 (writes plan columns only),
SubscriptionItemRepository (subscription_items.status — a different table's status),
CRM/ERP/commerce status writers (different tables).

**UNKNOWN_STATUS_WRITERS = 0.**

## 3. Status vs billing_state authority

Two distinct state dimensions, never conflated:

- `tenant_subscriptions.status` = canonical subscription lifecycle state.
  **Authority: SubscriptionCommandService** (via the internal canonical
  transition primitive `applyCanonicalTransition` — the ONLY writer of status
  transitions after R0C-7).
- `tenant_subscriptions.billing_state` = billing/dunning-specific state
  (CURRENT/PAST_DUE/SUSPENDED). **Authority: BillingStateService** (unchanged).

BillingStateService remains the owner of billing_state, but every billing
transition that must be reflected in `status` now routes through the canonical
lifecycle authority inside the SAME transaction. The two columns can never
diverge (see §16).

STATUS_VS_BILLING_STATE_AUTHORITY = DEFINED.

## 4. Legacy vs canonical semantics (LEGACY_VS_CANONICAL_LIFECYCLE_MATRIX)

| Operation | Entrypoint | Current (pre-R0C-7) behavior | Canonical command | FROM | TO | Metadata side effects | Audit | Event | Ledger | Compatible? |
|-----------|-----------|------------------------------|-------------------|------|-----|-----------------------|-------|-------|--------|-------------|
| CREATE | createSubscription | INSERT status=ACTIVE/TRIALING + composition birth + invoice + event + audit | (none — initialization) | — | ACTIVE/TRIALING | trial_ends_at, periods, seats, item | YES | YES | NO | YES (allowed exception) |
| ACTIVATE | /lifecycle/ACTIVATE; provisioning VALIDATE | command: table-driven; provisioning: direct write | ACTIVATE | DRAFT/PENDING_*/TRIAL/TRIALING | ACTIVE | cancelled_at untouched | cmd: YES; prov: NO | cmd: YES; prov: NO | cmd: YES; prov: NO | converged |
| CANCEL_IMMEDIATE | cancelSubscription(true) | direct status write + cancelled_at + flag | CANCEL | mutable set | CANCELLED | cancelled_at, cancel_at_period_end=FALSE | YES | YES | NO → YES | converged |
| SCHEDULE_CANCEL | cancelSubscription(false) | flag=TRUE only, no ledger | SCHEDULE_CANCELLATION (no-op, ledgered) | status preserved | status preserved | cancel_at_period_end=TRUE | YES | YES | NO → YES | converged |
| RESUME | resumeSubscription | non-CANCELLED: flag clear only; CANCELLED: direct ACTIVE + period reset + invoice | RESUME | PAUSED→ACTIVE (canonical); CANCELLED→ACTIVE (R0C-7 reconciliation) | ACTIVE | cancelled_at=NULL, periods, invoice | YES | YES | NO → YES | converged (Conflict A resolved) |
| RENEW | renewSubscription | direct ACTIVE write + period rollover + invoice + pending plan application | RENEW | ACTIVE/TRIAL(ING)/PAST_DUE/GRACE_PERIOD | ACTIVE | periods, trial_ends_at=NULL, billing_cycle, pending_* | YES | YES | NO → YES | converged (Conflict C resolved: SUSPENDED rejected) |
| PAST_DUE | billing dunning | billing_state + force status mirror (swallowed) | MARK_PAST_DUE | ACTIVE/TRIAL(ING) | PAST_DUE | none | billing audit | NO | NO → YES | converged |
| SUSPEND | billing dunning; /lifecycle/SUSPEND | billing_state + force status mirror | SUSPEND | ACTIVE/PAST_DUE/GRACE/TRIAL(ING) | SUSPENDED | none | billing audit / cmd audit | billing: NO | NO → YES | converged |
| PAYMENT_RECOVERY | billing (invoices paid) | billing_state → CURRENT + force status ACTIVE (incl. resurrecting CANCELLED) | PAYMENT_RECEIVED | PAST_DUE/GRACE_PERIOD/SUSPENDED (R0C-7) | ACTIVE | none | billing audit | NO | NO → YES | converged (Conflict B resolved: CANCELLED skipped) |
| PROVISION_ACTIVATION | provisioning VALIDATE | direct ACTIVE write | ACTIVATE | PENDING_ACTIVATION etc. | ACTIVE | none | none | none | NO → YES | converged; already-ACTIVE = idempotent no-op |
| EXPIRE | (no runtime entrypoint) | table only | EXPIRE | TRIAL(ING)/GRACE_PERIOD | EXPIRED | — | — | — | — | gap (see §20) |
| TERMINATE | /lifecycle/TERMINATE | table-driven | TERMINATE | non-terminal + CANCELLED | TERMINATED | cancelled_at | YES | YES | YES | unchanged |

## 5. CANCELLED resume conflict (Conflict A)

**Legacy behavior re-proven:** `resumeSubscription()` on a CANCELLED subscription
sets `status='ACTIVE'`, clears `cancelled_at`, resets the period and issues the
resumption invoice. `renewSubscription()` refuses CANCELLED with 409 "Cancelled
subscription must be resumed first". Both were born together in the original SaaS
administration control plane commit (#201) and are unchanged since.

**Canonical side:** RESUME only allowed PAUSED → ACTIVE; CANCELLED ∈
TERMINAL_STATUSES; the illegal-transitions CSV rejects `RENEW, CANCELLED` but
makes NO assertion about `RESUME, CANCELLED` (absent from both legal and illegal
CSVs).

**Resolution (from authoritative tests/docs, nothing invented):**
CANCELLED_RESUME_CONTRACT = RESOLVED_BY_EXISTING_TESTS/DOCS →
**LEGACY_REVIVAL_IS_AUTHORITATIVE**. Evidence: (a) the paired original semantics
(renew's guard literally directs operators to resume); (b) design doc §3 —
"Existing commands (change-plan, seats, cancel, resume, renew) are preserved as
aliases over the new state machine (backward compatible)"; (c) implementation
plan — "Legacy service methods delegate (no behavior change for old callers)";
(d) the canonical table simply lacked the entry (the illegal CSV omits it).
The canonical table now carries `RESUME: CANCELLED → ACTIVE`, and the converged
legacy resume routes the revival through the canonical primitive (which also
clears `cancelled_at`). The `terminalStatuses` unit-test exception set is
explicit: TERMINATE from CANCELLED (mission rule) and RESUME from CANCELLED
(revival contract).

## 6. Suspended payment recovery conflict (Conflict B)

**Legacy behavior re-proven:** `BillingStateService` recovers SUSPENDED →
CURRENT when all overdue invoices are paid (integration-test-proven:
`evaluateAndTransition_recoversToCurrentWhenAllInvoicesPaid`) and mirrors
`status='ACTIVE'` (best-effort, failures swallowed). The javadoc documents the
recovery contract.

**Canonical side:** PAYMENT_RECEIVED allowed only PENDING_PAYMENT→PENDING_ACTIVATION,
PAST_DUE→ACTIVE, GRACE_PERIOD→ACTIVE; RESUME only PAUSED→ACTIVE. No command
represented SUSPENDED → ACTIVE.

**Resolution:** SUSPENDED_PAYMENT_RECOVERY_CONTRACT = RESOLVED_BY_EXISTING_TESTS/DOCS
→ the tested billing recovery is authoritative; canonically represented as
`PAYMENT_RECEIVED: SUSPENDED → ACTIVE` (the same payment-recovery command family
as PAST_DUE→ACTIVE and GRACE_PERIOD→ACTIVE). The converged billing machine routes
this through the canonical primitive. No silent lifecycle transition was added —
the entry is unit-tested in SubscriptionLifecycleTest with the evidence trail
recorded here and in the code comments.

## 7. Renew semantics (Conflict C)

Legacy renew accepts every status except CANCELLED (its only guard) and
force-writes ACTIVE. The legacy-reachable status domain (original CHECK
constraint) is {TRIALING, ACTIVE, PAST_DUE, SUSPENDED, CANCELLED}:

- TRIALING → ACTIVE: canonical RENEW ✓ (TRIALING entry)
- TRIAL → ACTIVE: canonical RENEW ✓
- ACTIVE → ACTIVE: canonical RENEW ✓ (no-op status, ledgered)
- PAST_DUE → ACTIVE: canonical RENEW ✓
- CANCELLED: refused by BOTH (legacy 409 guard; canonical illegal CSV entry
  `RENEW, CANCELLED`) ✓
- SUSPENDED → ACTIVE: canonical **explicitly unit-tested illegal**
  (`RENEW, SUSPENDED` in the illegal CSV). No test proves legacy renew-from-
  SUSPENDED as a contract. → **canonical authority governs: converged renew
  fails closed with 409 CONFLICT.**
- Post-widening statuses (PAUSED, GRACE_PERIOD, DRAFT, PENDING_*, EXPIRED,
  TERMINATED): never reachable by the legacy engine (they postdate it);
  canonical table governs (RENEW accepts GRACE_PERIOD; the rest fail closed).

RENEW_CONTRACT = RESOLVED. The scheduled-cancel application branch inside
renew (cancelAtPeriodEnd) now uses canonical CANCEL, whose table was extended
with `SUSPENDED → CANCELLED` and whose no-op scheduling command with
`SCHEDULE_CANCELLATION: SUSPENDED → SUSPENDED` so the full legacy mutable
domain {TRIALING, ACTIVE, PAST_DUE, SUSPENDED} stays operable (a suspended
subscription remains cancellable and can schedule its cancellation; a
scheduled cancel on a suspended subscription is applied after payment
recovery re-activates it, or the renew attempt fails closed).

## 8. Canonical transition primitive

`SubscriptionCommandService.applyCanonicalTransition(subscriptionId, command,
reason, actorTenantId, actorUserId)` — the single writer of
`tenant_subscriptions.status` transitions:

1. reads the current row (fail-closed on unknown subscription);
2. validates via `SubscriptionLifecycle.transition` (IllegalStateException on
   illegal transitions — BEFORE any write);
3. writes the new status plus domain-owned transition metadata only:
   `cancelled_at = NOW()` on CANCEL/TERMINATE, `cancelled_at = NULL` on RESUME;
4. writes exactly one `subscription_commands` ledger row;
5. joins the caller's transaction (REQUIRED propagation).

The primitive performs NO platform audit, NO entitlement event, NO
caller-specific metadata. The public `execute()` is literally
primitive + lifecycle platform audit + entitlement event (transition SQL
exists exactly once in the codebase — post-GREEN rescan proves it).

## 9. Legacy cancel convergence

Immediate cancel: canonical `CANCEL` primitive (status + cancelled_at + ledger)
+ legacy-owned `cancel_at_period_end = FALSE` flag write, legacy
`subscription_change_events` row, platform audit, entitlement event and wire
response — all preserved byte-for-byte. Scheduled cancel: canonical no-op
`SCHEDULE_CANCELLATION` command (ledgered; the design doc's G3 command list
includes scheduleCancellation — the no-op command is meant to ledger the
operation) + legacy flag write + legacy event surface.
LEGACY_CANCEL_DIRECT_STATUS_WRITE = 0.

## 10. Resume convergence

Non-CANCELLED branch: unchanged (clears `cancel_at_period_end` only — not a
status transition; no ledger, exactly as before). CANCELLED branch: canonical
`RESUME` (status ACTIVE + `cancelled_at = NULL` + ledger) + legacy-owned period
reset, resumption invoice, change event, audit, entitlement event. Period
initialization, invoice behavior, `cancel_at_period_end` and wire response
preserved (PG-06/PG-19/PG-20/PG-21 prove it). No billing-semantic delta.

## 11. Renew convergence

Both renewal branches write only their own metadata
(billing_cycle/pending_*/trial_ends_at/periods/plan_id-same-value) and route
the status change through canonical `RENEW`. The pending-plan application
still composes through the R0C-4/5 canonical plan authority; seat mirroring
from R0C-6 is untouched (PG-22/PG-23 recert batteries green). Renew from
SUSPENDED fails closed 409 (§7). Scheduled-cancel application at renewal uses
canonical `CANCEL` (§4).

## 12. Billing convergence

`applyTransition` maps the proven billing targets to canonical commands:
CURRENT→PAST_DUE = MARK_PAST_DUE; *→SUSPENDED = SUSPEND; PAST_DUE/SUSPENDED→CURRENT
= PAYMENT_RECEIVED. Three cases:

1. **status already equals the target** (e.g. billing PAST_DUE while status
   already PAST_DUE): billing_state update alone — the pair stays consistent;
2. **canonical transition legal** for the current status: billing_state
   UPDATE + canonical primitive in the SAME transaction — both commit or both
   roll back (trigger-injection test proves it);
3. **canonical transition illegal** (e.g. status CANCELLED — the resurrection
   bug; status PAUSED — force-dunning): the whole transition is skipped
   atomically (no writes at all; idempotent like the from==to early return).

The legacy swallowed best-effort mirror is removed. BILLING_DIRECT_STATUS_WRITES = 0.
Billing semantics (invoice calculation, due dates, grace timing, dunning
selection, payment amounts, seat pricing, credit behavior) are untouched —
BILLING_SEMANTIC_DELTA = 0.

## 13. Provisioning activation convergence

VALIDATE: terminal check (unchanged, fail-closed) → already-ACTIVE = idempotent
no-op ("subscription already ACTIVE", job SUCCEEDs, no transition, no ledger) →
otherwise canonical `ACTIVATE` primitive (ledger + validation). ACTIVATE accepts
DRAFT/PENDING_ACTIVATION/PENDING_PAYMENT/TRIAL/TRIALING; PAUSED/SUSPENDED/etc.
fail the step (job RETRYING/FAILED) — fail-closed per canonical authority. Job
status, step status, retry behavior and idempotency behavior preserved
(keyed steps skip on retry; PG-14 proves no duplicate transition on retry or
re-provision). PROVISIONING_DIRECT_STATUS_WRITES = 0.

**Discovered and fixed pre-existing defect:** `recordStep` omitted the NOT NULL
`created_at` column (no default in the migration), so every real-PostgreSQL
provisioning run failed at the first step record — masked forever by
mocked-JdbcTemplate unit tests. RED-08/RED-12 surfaced it on the pristine
predecessor (DataIntegrityViolation). Fixed by including `created_at NOW()` in
the INSERT; the migration is untouched (the column already existed — the write
was wrong, not the schema).

## 14. Status metadata ownership (STATUS_METADATA_MATRIX)

| Metadata | Owner | Rules |
|----------|-------|-------|
| `status` | canonical primitive | table-validated transitions only |
| `cancelled_at` | canonical primitive | NOW() on CANCEL/TERMINATE; NULL on RESUME |
| `cancel_at_period_end` | legacy wrappers (cancel/resume/renew) | scheduling flag semantics — not a status transition |
| `trial_ends_at` | legacy create/renew wrappers | trial metadata (billing/period domain) |
| `current_period_start/end` | legacy resume/renew wrappers | period rollover (billing domain) |
| `updated_at` | every writer | coarse bookkeeping |

STATUS_METADATA_MATRIX_COMPLETE = YES.

## 15. Audit / event behavior

- Legacy cancel/resume/renew: 1 platform audit + 1 entitlement event + 1 legacy
  change event each — unchanged (the primitive adds none).
- Billing transitions: 1 billing audit (SUBSCRIPTION.BILLING_STATE.CHANGED) +
  1 ledger row; zero entitlement events (exactly the historical surface —
  the primitive fires none). Billing audit failure swallow preserved
  (audit semantics measured separately, unchanged).
- Provisioning: job/step rows only (historical surface) + 1 ledger row.
- Public canonical execute(): lifecycle audit + entitlement event + ledger —
  unchanged.

DUPLICATE_AUDIT_EVENTS = 0 and DUPLICATE_ENTITLEMENT_EVENTS = 0, proven by
exactly-once Mockito verification (`verifyNoMoreInteractions`) in PG-15..18.

## 16. Transaction atomicity

All converged writers run inside their existing `@Transactional` boundaries;
the primitive joins (REQUIRED). Billing: billing_state UPDATE and the
canonical status transition commit or roll back as ONE unit — the trigger
injection test (BEFORE UPDATE OF status raises) proves the exception now
propagates (no swallow) and both columns stay unchanged.
BILLING_LIFECYCLE_PARTIAL_STATE = IMPOSSIBLE. `markInvoicePaid`'s outer
best-effort catch is retained: it guards only infra errors (lifecycle
rejections no longer throw — they pre-check legality and skip atomically),
preserving the payment-recording primary contract.

## 17. Writer rescan (post-GREEN)

Repository-wide scan for `UPDATE tenant_subscriptions SET status`:

- **A. Initial INSERT status during create:** exactly 1 —
  `SaasAdministrationService.createSubscription` (allowed initialization).
- **B. ONE canonical lifecycle transition primitive:** exactly 1 —
  `SubscriptionCommandService.applyCanonicalTransition` (three switch
  branches of the single method; `execute()` delegates to it).
- Everything else: 0.

INITIAL_STATUS_WRITERS = 1; CANONICAL_TRANSITION_WRITERS = 1;
UNSAFE_DIRECT_STATUS_WRITERS = 0; UNKNOWN_STATUS_WRITERS = 0.

## 18. PostgreSQL Direct evidence

- **RED (on pristine 7205c7c1):** 58 tests → 11 failures + 6 errors.
  RED-01..04b legacy cancel/schedule/resume/renew unledgered; RED-05..07
  billing mirror unledgered; RED-08/12 provisioning unledgered + recordStep
  created_at defect; RED-09 billing resurrection of CANCELLED; RED-10 partial
  state (swallowed mirror); RED-11 renew-from-SUSPENDED bypass; +4 unit CSV
  contract rows failing on the pristine table. Log: `scripts/r0c7_red.log`.
- **GREEN:** LifecycleSingleWriterPostgresTest 19/19 (RED-01..12 converted +
  GUARD-01/02 + PG-14 retry/idempotency + PG-15..18 exactly-once side effects +
  PG-20 periods + PG-24 tenant isolation). Affected-suite battery: 167/167
  (incl. BillingStateServiceIntegrationTest 8/8, R0C-4 8/8, R0C-5 21/21,
  R0C-6 26/26, R0C-3 8/8, command/lifecycle/provisioning units).
- **Full suite:** 2529 tests, Failures 0, Errors 0, Skipped 6, BUILD SUCCESS.
  Log: `scripts/r0c7_full_suite.log`.
- **Dedicated pg-acceptance** (fresh `pg_acceptance` DB, least-privilege
  `sanad` role, per CI contract): CommerceOrderPostgresConcurrencyTest 6/6.

## 19. Full Maven evidence

Serial foreground run: `Tests run: 2529, Failures: 0, Errors: 0, Skipped: 6,
BUILD SUCCESS` (R0C-6: 2504 → +19 battery, +4 lifecycle CSV rows, +2
provisioning unit tests). No Docker, no Testcontainers, no H2 anywhere.

## 20. Remaining lifecycle gaps (REPORT_ONLY)

1. **Historical divergent rows** — status/billing_state inconsistencies from
   the swallowed-mirror era remain unrepaired (historical repair is NOT
   R0C-7; live writers are now converged so new divergence is impossible).
   A cancelled subscription whose invoices were later paid keeps
   billing_state PAST_DUE (cosmetic — dunning re-evaluates it as a no-op).
2. **Legacy resume on a non-CANCELLED, PAUSED subscription** still only
   clears the cancel flag (pre-existing legacy semantics preserved; the
   canonical RESUME command handles PAUSED → ACTIVE via the executive
   endpoint).
3. **Public canonical CANCEL** does not clear `cancel_at_period_end`
   (pre-existing canonical command behavior — unchanged on purpose).
4. **EXPIRE has no runtime driver** (no scheduler applies trial expiry) —
   the canonical table defines it but nothing invokes it.
5. **markInvoicePaid's outer catch** remains for infra errors only (payment
   recording is the primary contract; lifecycle rejections skip atomically
   without throwing).

## 21. R0C-8 recommendation

1. **Operator-approved repair tooling** for the REPORT_ONLY classifications
   across R0C-3/5/6/7 (historical anchor/quantity/status divergences) —
   read-only report + operator-gated repair, extending the reconciliation
   battery.
2. **Trial-expiry scheduler wiring** — the design doc's trial conversion
   (TRIALING → ACTIVE at trial end, or EXPIRE) has no runtime driver; wiring
   it through the canonical authority closes the last undriven lifecycle
   command family.
3. **Read-model convergence** — surface the canonical command ledger in the
   subscription detail timeline alongside legacy events for a single
   operator-visible lifecycle history.

## 22. Remote durability

- CHECKPOINT 1 (branch creation): pushed at `7205c7c1`, LOCAL = REMOTE.
- CHECKPOINT 2 (RED evidence): pushed at `0b6927b3`, LOCAL = REMOTE.
- CHECKPOINT 3 (production GREEN): pushed at `84eb9c58`, LOCAL = REMOTE
  (+ `0735c105` primitive-delegation refactor, tests green).
- CHECKPOINT 4 (final tests + docs): see final report — LOCAL_FINAL_HEAD =
  REMOTE_FINAL_HEAD (verified with `git ls-remote` after push).
- `origin/main` untouched (7f30c4ff); no merge, no deploy, no force push.
