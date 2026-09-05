# R0C-8 — Trial Expiration Runtime Driver Closure

Date: 2026-09-03
Branch: `scp/r0c-8-trial-expiration-runtime`
Status: PASS (all acceptance gates green; see §23 + final report)

## 1. Predecessor

- Base branch: `scp/r0c-7-lifecycle-single-writer`
- REQUIRED_BASE_HEAD: `8e1bcdffc395ee11ab8b9abe84fa3a24ee3b1b7d`
- Verified twice (fetch+prune rev-parse AND live `git ls-remote`) — exact
  match, R0C7_BASE_VERIFIED = YES.
- Branch created from the verified remote predecessor and pushed BEFORE any
  modification (CHECKPOINT_1: LOCAL = REMOTE = `8e1bcdff`).
- R0C-7's invariant carried forward as the foundation: 
  `SubscriptionCommandService.applyCanonicalTransition` is the ONLY writer of
  `tenant_subscriptions.status` transitions.

## 2. Expiration-path inventory (§5 forensic)

Repository-wide search for `trial_ends_at`, `TRIALING`, `TRIAL`, `EXPIRE`,
`EXPIRED`, `current_period_end`, `@Scheduled`, scheduler, trial expiration/
expiry — every hit classified (production `apps/sanad-platform/src/main`):

| # | Site | Kind | Classification |
|---|------|------|----------------|
| 1 | `SubscriptionLifecycle.EXPIRE` table entry (TRIAL/TRIALING/GRACE_PERIOD → EXPIRED) | transition-table definition | CANONICAL_COMMAND (definition; no runtime caller existed) |
| 2 | `SubscriptionCommandService.publishEvent`: EXPIRE → `SubscriptionCancelledEvent` | event wiring | CANONICAL_COMMAND (public execute path) |
| 3 | `LifecycleController` POST `/subscriptions/{id}/lifecycle/{command}` | API entrypoint | CANONICAL_COMMAND (manual operator path — EXECUTIVE_MANAGE) |
| 4 | `SaasAdministrationService.createSubscription` (status=TRIALING, trial_ends_at=now+days, period_end=trialEndsAt, NO invoice, billing_state defaults CURRENT) | trial birth | INITIALIZATION (allowed INSERT exception) |
| 5 | `SaasAdministrationService.renewSubscription` (RENEW TRIALING→ACTIVE + trial_ends_at=NULL + period rollover + recurring invoice) | conversion | CANONICAL_COMMAND (operator-initiated sanctioned trial→paid conversion) |
| 6 | `ProvisioningJobRunner` VALIDATE step (ACTIVATE from PENDING_*/TRIAL/TRIALING) | activation | CANONICAL_COMMAND (via the primitive; R0C-7) |
| 7 | `BillingStateService` javadoc: "TRIALING → CURRENT (when trial_ends_at elapses; handled elsewhere)" | comment | STALE_COMMENT (no implementation anywhere; "elsewhere" does not exist) |
| 8 | `BillingStateService.evaluateAndTransition` early-returns CANCELLED/TRIALING billing states | guard | explicit non-driver ("no automatic transitions out of these") |
| 9 | `V20260815_20` migration comment "TRIALING is preserved until trial_ends_at elapses" + status→billing_state backfill | historical comment/backfill | STALE_COMMENT/HISTORICAL (no successor state defined) |
| 10 | New-subscription INSERT omits `billing_state` → column default 'CURRENT' (trial rows are born CURRENT; only backfilled rows carry TRIALING) | schema default | INITIALIZATION (billing dimension) |
| 11 | `tenants.status='TRIAL'` + `tenants.trial_ends_at` (AdminPlatformService/ExecutivePlatformService create/update) | tenant-directory dimension | INITIALIZATION/OPERATOR_EDIT (no expiry driver, no contract demanding one — separate domain from `tenant_subscriptions.status`) |
| 12 | `UsageMeteringService` (usage counted for ACTIVE/TRIALING/TRIAL) | reader | READ_ONLY |
| 13 | `TenantDirectoryAdministrationService.limits()` (TRIALING/ACTIVE/PAST_DUE required for directory changes) | reader | READ_ONLY (EXPIRED correctly blocks directory changes) |
| 14 | Grid/detail/executive readers (trial filters, counts, `subscription_commands` timeline) | readers | READ_ONLY (detail already exposes the command ledger → §27 read-model gap = NO) |
| 15 | `issueRecurringInvoice` call sites (create non-trial / resume / renew only) | billing | evidence: NO invoice scheduler exists → no auto-conversion support |
| 16 | `WorkflowSlaScheduler`, `SchedulingConfig`, `BillingStateService.runDunningCycle` | schedulers | RUNTIME_DRIVER (unrelated domains: workflow SLA read-only; dunning billing-only and explicitly excludes TRIALING) |
| 17 | `EXPIRED` in CRM/workflow/commerce/security/ERP domains | unrelated-domain matches | excluded (not subscription lifecycle) |

**RUNTIME_DRIVER for trial expiration: NONE existed** — the gap this task
closes. UNKNOWN_TRIAL_EXPIRY_PATHS = 0 (every hit classified above).

## 3. Semantic conflict (§6-§8)

Two candidate contracts for "trial_ends_at elapses":

- **Canonical**: `SubscriptionLifecycle.EXPIRE: TRIAL/TRIALING → EXPIRED`
  (also GRACE_PERIOD). Introduced with the SCP lifecycle table (commit
  `c5f82d05`, #924); design doc §3 G3 designates the transition table as the
  single source of transition legality and lists `expire` among the domain
  commands; `SubscriptionLifecycleTest` unit-tests the row; the public
  `execute()` already wires ledger + `SUBSCRIPTION_EXPIRE` audit +
  `SubscriptionCancelledEvent`; R0C-7 plan §20.4 classified the gap as "EXPIRE
  has no runtime driver".
- **Stale comment**: `BillingStateService` javadoc claims billing_state
  `TRIALING → CURRENT` "handled elsewhere" — but `evaluateAndTransition`
  early-returns TRIALING, the dunning scan selects only
  CURRENT/PAST_DUE/SUSPENDED, and an exhaustive search finds no "elsewhere".
  Per order §8 a comment is not authority by itself; the claim was proven
  false on PostgreSQL (RED-04).

Additional decisive evidence against auto-conversion (MODEL_B): no invoice
scheduler exists — `issueRecurringInvoice` is called only from
operator-initiated create/resume/renew. Auto-converting TRIALING → ACTIVE at
trial end would create an ACTIVE subscription with zero invoices, violating
the billing contract. The sanctioned trial→paid conversion is the
operator-initiated RENEW (invoice + trial_ends_at=NULL + period rollover).
The legacy-era lifecycle doc (`docs/stage-28/04`) lists "Trial → Activation …
Expiration → Reactivation" as stages without defining any automatic
mechanism — consistent with MODEL_A's terminal expiration plus the manual
RENEW/RESUME paths.

## 4. Authoritative expiration contract (semantic gate)

```
TRIAL_EXPIRATION_CONTRACT = MODEL_A
TRIAL/TRIALING → EXPIRED, applied via the canonical EXPIRE command.
```

Evidence hierarchy (nothing invented):
1. Design doc G3: single `SubscriptionLifecycle` transition table is THE
   authority; `expire` is in the command list; EXPIRED is terminal.
2. Implementation plan SCP-G3: `expire` in SubscriptionCommandService.
3. The table row itself + unit tests.
4. R0C-7's classification of the missing piece as the EXPIRE driver.
5. No repository evidence of any automatic TRIALING→ACTIVE behavior to
   represent (unlike R0C-7's RESUME/PAYMENT_RECEIVED cases, where legacy
   runtime behavior existed and the table was extended to represent it).

GRACE_PERIOD → EXPIRED remains table-legal but is NOT part of the trial
driver's due set (order §11: only TRIAL/TRIALING with a non-null elapsed
`trial_ends_at`).

## 5. Lifecycle vs billing state (two dimensions kept separate)

```
TRIAL_EXPIRY_LIFECYCLE_EFFECT = status TRIAL|TRIALING → EXPIRED
                                 (canonical EXPIRE; cancelled_at NOT set —
                                 EXPIRE ≠ CANCEL; trial_ends_at, periods,
                                 composition, seats all preserved)
TRIAL_EXPIRY_BILLING_EFFECT   = NONE (billing_state never written)
```

`BillingStateService` remains the sole `billing_state` authority; no
authoritative behavior requires a billing transition at trial end; expired
rows are inert to the dunning scan (CURRENT rows with zero overdue invoices
are a no-op; TRIALING rows are early-returned). Both row shapes (backfilled
`billing_state='TRIALING'` and modern default `'CURRENT'`) are preserved —
proven in PG-19. The stale javadoc line was corrected in the GREEN commit
(R0C-8): the claim is withdrawn and the resolved contract documented in place.

## 6. RED evidence (§24)

Battery: `TrialExpirationRedPostgresTest` (5 tests), committed BEFORE any
production change (commit `083b47ef`) and run on the pristine R0C-7 head —
**Tests run: 5, Failures: 4, Errors: 0**:

- RED-01 due trial remains TRIALING indefinitely — no runtime driver exists.
- RED-02 no automatic EXPIRE ledger row / `SUBSCRIPTION_EXPIRE` audit.
- RED-03 no runtime entitlement expiration event.
- RED-04 stale comment captured: dunning never performs the claimed
  billing TRIALING→CURRENT transition ("handled elsewhere" = nowhere).
- GUARD-01 (passes on pristine): canonical manual EXPIRE already works —
  proving the gap is exactly the missing RUNTIME DRIVER, not the command.

Mechanism: the battery locates `TrialExpirationService` **reflectively** so
it compiles on the predecessor; the RED failure IS the gap ("no runtime
trial-expiration driver exists"). Post-fix all 5 pass and remain as permanent
gap-regression guards.

## 7. Runtime driver (§10)

`com.sanad.platform.subscription.lifecycle.TrialExpirationService`:

- **Due scan** (deterministic): `status IN ('TRIAL','TRIALING') AND
  trial_ends_at IS NOT NULL AND trial_ends_at <= <executionTime>`,
  `ORDER BY trial_ends_at ASC, id ASC`, `LIMIT 200` per tick
  (WorkflowSlaScheduler safety-bound convention). ACTIVE/PAST_DUE/SUSPENDED/
  CANCELLED/EXPIRED/TERMINATED are never selected (PG-05..10).
- **Lock + re-check + canonical transition**: each due trial is processed in
  its own `TransactionTemplate` transaction that first re-reads the row with
  `SELECT ... FOR UPDATE` and re-checks status + trial end; only a row that
  is still a due trial invokes `commandService.execute(id, "EXPIRE", reason,
  null, null)` — the canonical PUBLIC path (ledger + audit + entitlement
  event owned by that path, exactly once). The driver never writes
  `tenant_subscriptions` directly (no status SQL, no billing SQL).
- **Reason string**: `"Trial expired at <row's trial_ends_at> (trial-expiry
  runtime driver)"` — the row's own historical fact, never recomputed.
- **System actor**: `actor_tenant_id`/`actor_user_id` NULL (automated; no
  operator impersonation — PG-11).
- **Failure isolation**: per-subscription try/catch, SLF4J error log with
  subscription/tenant/trial-end context, cycle continues; counts
  (`dueSeen/expired/skipped/failed`) returned in `TrialExpiryResult`.
  No printStackTrace anywhere.
- **Result record** for logging + test assertions.

## 8. Timing model (§12)

- One execution timestamp per cycle: `clock.instant()` captured once; used
  for the due scan, the locked re-check AND the reason boundaries — never a
  scattered `Instant.now()` per row (TRIAL_EXPIRY_TIME_DETERMINISTIC = YES).
- Injectable `Clock` (test constructor; production wires
  `Clock.systemUTC()` via the `@Autowired` constructor — no unrelated service
  was refactored to introduce Clock).
- DETERM-01 proves clock authority on PostgreSQL: a trial whose
  `trial_ends_at` is wall-clock-past but clock-future stays untouched, and
  the ledger reason embeds the row's exact trial end.

## 9. Idempotency (§13)

- The due scan cannot re-select an EXPIRED row (status filter) — second
  cycle: dueSeen=0 (PG-14).
- A row transitioned between scan and processing is caught by the locked
  re-check → skipped, no exception, no writes (PG-14/PG-15).
- Exactly one: `subscription_commands` row, `SUBSCRIPTION_EXPIRE` audit
  entry, `SubscriptionCancelledEvent` (PG-11/12/13/14).
  TRIAL_EXPIRY_IDEMPOTENT = YES.

## 10. Concurrency (§14)

Two workers racing the same due trial (CyclicBarrier-started threads,
PG-15): the `SELECT ... FOR UPDATE` re-check serializes them — the second
worker blocks until the first commits, then re-reads EXPIRED and skips.
`sum(expired) == 1`, ledger/audit/event each exactly 1.
DUPLICATE_EXPIRATION_TRANSITIONS = 0. Rows are processed in deterministic
order (oldest trial end first) so concurrent workers cannot deadlock; no
distributed locking was invented (plain row locks, per the existing
architecture).

**Discovered + fixed pre-existing defect (P0-adjacent):** the R0C-7
canonical primitive's read-validate-write window allowed a lost update —
proven by PG-18's activate-vs-expiry race, where an operator ACTIVATE that
had read TRIALING blindly overwrote a concurrently committed EXPIRED back to
ACTIVE (terminal resurrection via lost update). Fix (GREEN commit):
`applyCanonicalTransition`'s UPDATE is now GUARDED
(`WHERE id = ? AND status = <validated fromStatus>`); zero affected rows
re-reads the row and fails closed with `IllegalStateException` (mapped by
legacy callers to 409 CONFLICT, exactly like canonical table rejections).
Single-threaded behavior is byte-identical; single-writer invariant
unchanged; the R0C-7 battery (19/19) and the full suite confirm zero
regression.

## 11. Canonical lifecycle integration (§15)

The driver calls the PUBLIC `execute()` — not the internal primitive —
because for trial expiration there is no other caller owning audit/events
( unlike billing/provisioning wrappers). Therefore:

- transition legality: `SubscriptionLifecycle.transition("EXPIRE", status)`
  — rejects everything outside TRIAL/TRIALING/GRACE_PERIOD before any write;
- status write: the guarded single-writer primitive (§10) — the transition
  SQL still exists exactly once in the codebase;
- ledger: one `subscription_commands` row (command=EXPIRE,
  from_status, to_status, reason, system actor);
- joins the driver's transaction (REQUIRED propagation).

Post-fix scan: `UPDATE tenant_subscriptions ... SET status` exists ONLY in
the primitive's three guarded switch branches; INSERT only in
createSubscription. UNSAFE_DIRECT_STATUS_WRITERS = 0.

## 12. Audit contract (§17)

EXPIRY_AUDIT_COUNT = exactly 1 per expiration — the canonical public
execute path's `auditService.success(null, tenant, "SUBSCRIPTION_EXPIRE",
"subscription", id, reason, fromStatus, toStatus)` (PG-12; RED-02 pre-fix
proves zero). The automated driver does NOT bypass audit and does NOT add a
second audit writer. DUPLICATE_AUDIT_EVENTS = 0 (PG-14/PG-15).

## 13. Entitlement events (§18)

EXPIRE publishes exactly one `SubscriptionCancelledEvent` (the existing
canonical wiring in `publishEvent`) after commit — the listener
recalculates entitlements and `EntitlementResolver` resolves only from
`status='ACTIVE'` subscriptions, so expiration disables entitlements through
the EXISTING path. No second event type was introduced.
DUPLICATE_ENTITLEMENT_EVENTS = 0 (PG-13/14/15).

## 14. Billing effects (§19)

INVOICE_DELTA = 0, CREDIT_DELTA = 0, PRORATION_DELTA = 0 — proven, not
assumed (PG-20): total invoice count unchanged across a cycle; the paid
ACTIVE subscription's `credit_balance_minor`/seat_quantity unchanged; the
expired trial itself never held an invoice and gains none. The driver issues
no invoice, credit, refund or proration; RENEW remains the only sanctioned
conversion (and still issues its recurring invoice exactly as before —
billing suite 10/0/0).

## 15. Race safety (§20-§21)

- CANCELLED_AFTER_RACE = CANCELLED; TERMINATED_AFTER_RACE = TERMINATED
  (PG-16/PG-17 deterministic branch: operator wins first → driver's locked
  re-check sees the terminal state and skips; zero EXPIRE ledger rows).
- NO_TERMINAL_RESURRECTION = YES — in the concurrent races the final state
  is always terminal and exactly ONE command family ledgers:
  - expiry wins → the operator command is rejected by the canonical table
    or by the guarded write (no stale from_status rows, no overwrite);
  - operator wins → the driver skips (no blind expiration of a
    non-TRIAL row, even with elapsed trial_ends_at — PG-18 ACTIVE outcome).
- Manual ACTIVATE race respects canonical legality (PG-18): ACTIVATE from
  TRIALING is legal (operator may activate a trial early); ACTIVATE from
  EXPIRED is illegal — rejected, never a resurrection.

## 16. Tenant isolation (§26)

Scheduler is platform-level, but every mutation is addressed by
subscription id and the ledger row carries the row's own tenant_id. PG-25:
expiring tenant A's trial never mutates tenant B/C subscriptions — zero
cross-tenant ledger rows, zero foreign entitlement events. The batteries run
against the least-privilege `sanad` role (NOSUPERUSER NOCREATEDB NOCREATEROLE
NOBYPASSRLS) on real PostgreSQL 16.15. TENANT_ISOLATION = PASS.

## 17. Reconciliation (§28)

REPORT_ONLY. New classification (defined only after the semantics were
proven, §4):

```
OVERDUE_TRIAL_NOT_TRANSITIONED:
  status IN ('TRIAL','TRIALING')
  AND trial_ends_at IS NOT NULL AND trial_ends_at <= NOW()
```

PG-27: the classification is a pure read — no repair, no ledger, no audit,
no status change. It tells the operator exactly what the (disabled) driver
WILL transition once enabled. No historical row is repaired by R0C-8.

## 18. Writer rescan (§30)

Post-GREEN scans (production code):
- `tenant_subscriptions.status` UPDATE writers: ONLY the 3 guarded branches
  of `applyCanonicalTransition` (all other `SET status` hits are other
  tables: user_role_assignments, billing_invoices, system_services,
  website_pages/publications, provisioning_jobs).
- INSERT writer: `createSubscription` (initialization — allowed exception).
- `trial_ends_at` writers: createSubscription (INSERT), renewSubscription ×2
  (conversion clears it), test batteries; the DRIVER never writes
  trial_ends_at. Tenant-directory `tenants.trial_ends_at` writers are the
  separate operator-edit dimension (§2 #11).
- EXPIRE command callers: `TrialExpirationService` (the driver — canonical
  public path), `LifecycleController` (generic manual passthrough), the
  lifecycle table definition + event wiring. No other caller.

UNKNOWN_STATUS_WRITERS = 0; UNKNOWN_TRIAL_EXPIRY_WRITERS = 0;
UNSAFE_DIRECT_STATUS_WRITERS = 0. Migration diff vs base = 0 lines →
NEW_MIGRATIONS = 0 (STOP condition never triggered).

## 19. PostgreSQL Direct evidence (§24/§25)

Environment: real PostgreSQL 16.15 (non-root local install — no Docker, no
Testcontainers, no H2 anywhere in the acceptance path), least-privilege
`sanad` role, per-test-class isolated schema via
`MigrationTestSchemaSupport` (Flyway clean/migrate/validate per test).

RED battery (pristine R0C-7 head, commit `083b47ef`):
`TrialExpirationRedPostgresTest` — **5 run, 4 failures, 0 errors**
(RED-01..04 fail with the gap; GUARD-01 passes).

GREEN battery (commit `8cf748cf` + `3be4affc`):
`TrialExpirationRuntimePostgresTest` — **28/28 PASS**
(PG-01..26 + DETERM-01 time-authority + PG-27 reconciliation).
Post-fix RED battery: **5/5 PASS** (permanent gap guards).
R0C-7 battery `LifecycleSingleWriterPostgresTest`: **19/19 PASS**.

Scheduler configuration values (order §22):

```
TRIAL_EXPIRY_SCHEDULER_DEFAULT  = disabled (double gate:
  global scheduling.enabled=false AND per-job
  sanad.tenancy.billing.trial-expiry-enabled=false / SANAD_TRIAL_EXPIRY_ENABLED)
TRIAL_EXPIRY_SCHEDULER_INTERVAL = 3600000 ms (1 hour, dunning cadence —
  deliberately NOT sub-hour), overridable via
  sanad.tenancy.billing.trial-expiry-interval-ms
```

Tests never trigger the scheduled method (scheduling disabled in tests;
the testable entry point `runTrialExpiryCycleOnce()` is invoked directly).

## 20. Full Maven evidence (§31)

Serial regression sequence — every step its own foreground Maven run:

| Step | Scope | Result |
|------|-------|--------|
| 1 | SubscriptionLifecycleTest | 43/0/0 |
| 2 | SubscriptionCommandServiceTest (seams updated for the guarded write) | 5/0/0 |
| 3 | TrialExpirationRedPostgresTest + TrialExpirationRuntimePostgresTest | 5/0/0 + 28/0/0 |
| 4 | R0C-7 recert LifecycleSingleWriterPostgresTest | 19/0/0 |
| 5 | R0C-6 recert AnchoredPlanSeatQuantity PG + unit | 26/0/0 + 9/0/0 |
| 6 | R0C-5 recert MultiPlanAnchorAuthorityPostgresTest | 21/0/0 |
| 7 | R0C-4 recert SaasAdministrationLegacyConvergencePostgresTest | 8/0/0 |
| 8 | R0C-3 recert SubscriptionAnchorPostgresTest | 8/0/0 |
| 9 | R0C-2R recert SubscriptionChangeServicePostgresTest | 8/0/0 |
| 10 | subscription suite (`com.sanad.platform.subscription.**`) | 265/0/0 |
| 11 | admin suite (`com.sanad.platform.admin.**`) | 24/0/0 |
| 12 | billing suite (BillingStateServiceIntegrationTest + invoice tests) | 10/0/0 |
| 13 | provisioning suite (ProvisioningJobRunnerTest + ControlPlane integration) | 6/0/0 |
| 14 | executive suite (read models + reporting + routes) | 21/0/0 |
| 15 | FULL Maven `mvn test` (ephemeral CRM key per CI contract) | **2562 tests, Failures 0, Errors 0, Skipped 6, BUILD SUCCESS (12:00 min)** — 2529 (R0C-7) + 33 new |
| 16 | dedicated pg-acceptance on FRESH `pg_acceptance` DB (`SPRING_PROFILES_ACTIVE=pg-acceptance`, PG_ACCEPTANCE_* creds, no SPRING_DATASOURCE_URL) | CommerceOrderPostgresConcurrencyTest 6/6 |

FAILURES = 0, ERRORS = 0. PLAN_COMPOSITION_REGRESSION = 0 (PG-21 + R0C-5
recert), SEAT_MIRROR_REGRESSION = 0 (PG-22 + R0C-6 recert),
BILLING_SEMANTIC_DELTA = 0 (PG-19/PG-24 + billing suite).

## 21. Remaining lifecycle gaps (REPORT_ONLY)

1. **Historical overdue trials** — pre-R0C-8 rows with elapsed
   trial_ends_at remain TRIALING until an operator enables the driver
   (classified `OVERDUE_TRIAL_NOT_TRANSITIONED`, §17). No automatic repair
   (order §29); operator-approved repair is a later task.
2. **Expired trials are a dead end for the tenant** — EXPIRED is terminal
   with no exit command, and `createSubscription` enforces one subscription
   per tenant (`uk_tenant_subscriptions_tenant`), so a tenant whose trial
   expired cannot be re-subscribed without operator intervention (RESUME
   covers only PAUSED/CANCELLED). This is a well-defined product gap, not a
   contradiction; raising it for product decision is recommended (R0C-9
   candidate).
3. **Public canonical CANCEL does not clear `cancel_at_period_end`** and
   legacy resume-on-PAUSED only clears the flag — pre-existing R0C-7
   semantics, unchanged on purpose.
4. **Operator-path stale `from_status` ledger rows under extreme
   read-validate-write interleaving** — closed by the R0C-8 guarded write
   for status-changing commands; the no-op command ledger (RENEW ACTIVE→
   ACTIVE, SCHEDULE_CANCELLATION) still records the read state by design.
5. **Dunning `runDunningCycleOnce` still uses `e.printStackTrace()`**
   (pre-existing style debt in BillingStateService, outside R0C-8's driver;
   listed for a future hygiene task).

## 22. R0C-9 recommendation

1. **Operator-gated repair tooling** for the accumulated REPORT_ONLY
   classifications across R0C-3/5/6/7/8 (historical
   OVERDUE_TRIAL_NOT_TRANSITIONED, anchor/quantity/status divergences):
   read-only report + capability-guarded repair endpoint, extending the
   reconciliation batteries.
2. **Expired-trial re-subscription product decision** (§21 #2): either a
   canonical revival command (e.g. RESUME from EXPIRED, mirroring the
   proven CANCELLED revival) or an operator flow for superseding an expired
   subscription — needs an explicit semantic order; do not invent it here.
3. **Read-model convergence** — surface the canonical command ledger in the
   detail timeline UI alongside legacy events (the API already exposes it;
   this is presentation-layer work).
4. **Scheduler enablement runbook** — production rollout note for
   `scheduling.enabled=true` + `SANAD_TRIAL_EXPIRY_ENABLED=true` (and the
   dunning flag), with the reconciliation report as the pre-flight
   checklist.

## 23. Remote durability

- CHECKPOINT 1 (branch creation): pushed at `8e1bcdff`, LOCAL = REMOTE
  (verified with `git ls-remote`).
- CHECKPOINT 2 (RED/semantic evidence): pushed at `083b47ef`, LOCAL =
  REMOTE.
- CHECKPOINT 3 (GREEN): pushed at `8cf748cf`, LOCAL = REMOTE.
- CHECKPOINT 4 (final tests + docs + reconciliation): see final report —
  LOCAL_FINAL_HEAD = REMOTE_FINAL_HEAD (verified with `git ls-remote` after
  push).
- `origin/main` untouched (`7f30c4ff`); no merge, no deploy, no force push;
  serial Maven runs only; no secret values printed at any point.

