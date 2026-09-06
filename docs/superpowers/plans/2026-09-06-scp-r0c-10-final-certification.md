# R0C-10 Final Certification — Subscription Multiplicity Runtime (MODEL_B)

Date: 2026-09-06
Branch: `scp/r0c-10-subscription-multiplicity-runtime`
Verdict: **R0C_10_STATUS=CLOSED · R0C_10_CLOSURE_VERDICT=PASS · R0C10_FINAL_CERTIFICATION=PASS**

Every value in this document was re-verified mechanically from preserved fresh local
evidence during finalization (canonical log parse, ElementTree parse of all 342 fresh
Surefire XMLs, git forensics, and read-only repository scans). No value is transcribed
on trust.

---

## 1. Chain Identity

| Field | Value |
|---|---|
| R0C9_FINAL_HEAD | `13c144e2a23bbdb34325b13f664489fb9440bc0c` |
| R0C10_BRANCH | `scp/r0c-10-subscription-multiplicity-runtime` |
| R0C10_IMPLEMENTATION_HEAD_BEFORE_CERT | `6c026c7b8fe8cc97d1fbc24771a5e05ecebf1b46` |
| CURRENT_ORIGIN_MAIN_HEAD (fresh at certification) | `748e2c6076c94b7a29e2a5e8e4f4a817b0f2fc2b` |
| Ancestry | `git merge-base --is-ancestor 13c144e2… HEAD` → exit 0 (forward-only) |
| Remote state before final push | `REMOTE_PRE_FINAL=13c144e2a23bbdb34325b13f664489fb9440bc0c` (unmoved, exact) |
| Worktree before certification commit | 0 dirty / 0 staged / 0 untracked |

### R0C-10 commit chain (13c144e2… → 6c026c7b…, oldest first)

| Commit | Subject |
|---|---|
| `f237eff3` | docs(scp): R0C-10 design and implementation plan |
| `f33f447c` | test(db): MODEL_B multiplicity migration V20260906_1 |
| `e2f91f60` | test(scp): effective/history resolution contract (Task A) |
| `76892953` | feat(scp): converge ambiguous consumers to effective semantics (Task B) |
| `47206ee2` | fix(scp): resume(EXPIRED)/resume(TERMINATED) fail closed (Task G, P1) |
| `bd2bb27d` | feat(scp): gated EXPIRED successor runtime + no-second-trial + terminal semantics (Tasks D/E/F) |
| `b44bc738` | test(scp): provisioning isolation + concurrent successor creation (Tasks I/J) |
| `2c6da0ef` | test(scp): supersede R0C-9 PG-03b forensic assertion (resume fail-closed pinned) |
| `33f5c7f7` | fix(scp): @Autowired primary constructors; preserve billing audit target contract |
| `f92dbf73` | test(scp): adapt resolver unit tests + migration ledgers to R0C-10 inventory |
| `6c026c7b` | docs(scp): R0C-10 rollout/rollback runbook |

---

## 2. Canonical Full Maven Execution (single complete run)

| Field | Value |
|---|---|
| FULL_MAVEN_EXECUTION_MODE | SINGLE_COMPLETE_CANONICAL_RUN |
| CANONICAL_RUN_CODE_HEAD | `f92dbf732c7b23fcc6357edfc7465706fba99d21` |
| CANONICAL_RUN_START | 2026-09-06T13:49:07Z (epoch 1788702547) |
| mvn invocation | `mvn clean -B -ntp` (finished 13:49:09Z) followed by EXACTLY ONE `mvn test -B -ntp -Dsurefire.useFile=false` — no package scopes, no shards, no exclusions |
| FULL_MAVEN_TESTS | 2610 |
| FULL_MAVEN_FAILURES | 0 |
| FULL_MAVEN_ERRORS | 0 |
| FULL_MAVEN_SKIPPED | 6 |
| FULL_MAVEN_BUILD | SUCCESS |
| FULL_MAVEN_DURATION | 13:14 (Total time: 13:14 min; Finished at 2026-09-06T14:02:25Z) |
| FULL_MAVEN_COUNT_RECONCILIATION | PASS |
| Log structural markers | exactly 1 × "Scanning for projects"; 1 × surefire goal `(default-test)`; 1 × final `Results:` block; 1 × `BUILD SUCCESS`; 0 × `BUILD FAILURE` |
| Log integrity | SHA-256 `e9522c193a45781d0b2d8081f94f1fd1b983a5bee79494858038350fb01e82a1` (500,324,029 bytes) |
| Evidence location | outside tracked repo: `/home/z/my-project/r0c10-evidence/` (canonical_full_run.log + surefire-xml/ + reconciliation JSONs) |

### Count reconciliation (PASS)

- Final aggregate parsed from the Results block immediately preceding BUILD SUCCESS:
  `Tests run: 2610, Failures: 0, Errors: 0, Skipped: 6` (printed at `[WARNING]` level
  because skipped > 0). Per-class "Tests run:" lines were NOT summed.
- All 342 fresh `TEST-*.xml` files parsed with an XML parser (ElementTree), each
  aggregated exactly once: `<testcase>` element sum = **2610**; element-level failures
  = 0, errors = 0, skipped = 6; distinct `(classname, name)` identities = 2610.
- `XML_TESTCASE_COUNT (2610) == MAVEN_FINAL_AGGREGATE_TESTS (2610)` — exact.
- `testsuite@tests` root-attribute sum (2368) was parsed but deliberately NOT used as
  authoritative: surefire 3.5.4 writes `tests="0"` on 21 nested-class-only suites
  while all `<testcase>` elements are present (242 = 2610 − 2368; the same JUnit5
  nested-class behavior already reconciled at R0C-9 closure).
- STALE_XML_FILES = 0 (every XML mtime ≥ canonical run start epoch 1788702547).
- Skipped = 6 belongs entirely to `com.sanad.platform.commerce.CommerceOrderPostgresConcurrencyTest`
  (pg-acceptance-gated, intentional, unchanged since R0C-9).

### POST_CANONICAL_CODE_DELTA = 0

Commit chronology is mechanical: `f92dbf73` committed 13:49:01Z → canonical run started
13:49:07Z → run finished 14:02:25Z → `6c026c7b` (runbook, documentation-only) committed
14:02+ / 14:07:28Z. `git diff --name-status f92dbf73..HEAD` = exactly one file:
`docs/superpowers/plans/2026-09-06-scp-r0c-10-rollout-rollback-runbook.md` (A).
Zero changes under `src/main/`, `src/test/`, `pom.xml`, `.mvn/`, Flyway migrations, or
runtime configuration after the canonical run. The full-suite evidence is NOT
invalidated; no rerun required or performed.

---

## 3. Database Model (MODEL_B)

| Field | Value |
|---|---|
| R0C10_MIGRATION_VERSION | `V20260906_1` (`V20260906_1__scp_subscription_multiplicity_model_b.sql`) |
| LEGACY_FULL_TENANT_UNIQUE_REMOVED | YES — `ALTER TABLE tenant_subscriptions DROP CONSTRAINT IF EXISTS uk_tenant_subscriptions_tenant` |
| PARTIAL_UNIQUE_NAME | `uk_tenant_subscriptions_effective` — `UNIQUE (tenant_id) WHERE status NOT IN ('CANCELLED','EXPIRED','TERMINATED')` |
| LOOKUP_INDEX_NAME | `idx_tenant_subscriptions_tenant_status` ON `tenant_subscriptions (tenant_id, status)` |
| Migration character | forward-only, additive-only; no data rewrite; no physical DELETE; no RLS statement touched |
| R0C10_FLYWAY_DUPLICATE_VERSION_COUNT | 0 (branch: 116 SQL migrations, 0 internal duplicate versions; freshly fetched `origin/main`: 143 SQL migrations, 0 internal duplicate versions; cross-branch duplicate versions: 0) |
| R0C-10 migration delta vs origin/main | branch adds exactly 1 migration (`V20260906_1`); main's 28 additional migrations (HRM / Workflow Y2, latest `V20260905_18`) are independent forward progress with no version collision |

**SUBSCRIPTION_MULTIPLICITY_MODEL=MODEL_B** (frozen R0C-9 contract, inherited and now
enforced in schema + runtime):
- HISTORICAL_CARDINALITY = 0..N (terminal rows preserved as immutable history)
- EFFECTIVE_CARDINALITY = 0..1 (effective = `status NOT IN ('CANCELLED','EXPIRED','TERMINATED')`, PostgreSQL-enforced by the partial unique index)
- Automatic second trial = NO

---

## 4. Final Consumer Inventory Audit (read-only, re-run at certification)

Raw inventory: 76 production references to `tenant_subscriptions` =
52 executable SQL statement sites + 22 javadoc/comment references + 2 entries in the
ModuleReset protected-denylist (`tenant_subscriptions` is reset-protected and can never
be deleted by module reset).

Every executable lookup is classified exactly:

| Classification | Sites (representative) |
|---|---|
| EFFECTIVE_ONLY | `SubscriptionResolutionService.findEffectiveSubscription`; `BillingStateService.evaluateAndTransition` + dunning scan (terminal rows excluded); `EntitlementResolver.findActiveSubscription` (effective + unchanged ACTIVE gate); `SubscriptionImpactService.getCurrentPlanCode` (same); `TenantDirectoryAdministrationService` limit guard (`status IN ('TRIALING','ACTIVE','PAST_DUE')`, deterministic `created_at DESC, id DESC`); `UsageMeteringService` entitlement limit union (effective statuses only); `TrialExpirationService.findDueTrials` (TRIAL/TRIALING only, explicit batch ordering); `SaasAdministrationService` plan-update usage guards (MAX over non-terminal statuses) |
| EXACT_SUBSCRIPTION_ID | all lifecycle canonical transitions (`WHERE id = ? AND status = ?`); changePlan / changeSeats / cancel / resume / renew writes (`WHERE id = ?`); `SubscriptionItemService` + `SubscriptionChangeService` anchor reads; `LifecycleController.enqueueJob`; `ProvisioningJobRunner` (job by id, steps by `subscription_id`); `TrialExpirationService` recheck (`WHERE id = ? FOR UPDATE`); `SubscriptionDetailService` |
| ALL_HISTORY | `ExecutiveOverviewService` status counts + MRR aggregates; `SubscriptionGridQueryService` grid; `TenantDirectoryQueryService.subscription_count` + deterministic latest-status display subquery; `HealthIntelligenceService` seat aggregate; `SaasAdministrationService.subscriptionSelect` admin list; `SubscriptionResolutionService.findAllHistory` |

| Field | Value |
|---|---|
| CONSUMER_INVENTORY_TOTAL | 76 |
| AMBIGUOUS_LOOKUPS_REMAINING | 0 |
| UNKNOWN_LOOKUPS_REMAINING | 0 |

Unsafe-pattern scan (src/main, read-only):

- `LIMIT 1` touching `tenant_subscriptions`: exactly 2, both explicitly correct —
  `SubscriptionResolutionService.findLatestHistorical` (continuation-guard contract,
  deterministic `ORDER BY created_at DESC, id DESC LIMIT 1`, latest row's terminal
  status decides the continuation rule) and the `TenantDirectoryQueryService`
  latest-status display subquery (read-only projection, deterministic `created_at DESC`).
  The `UsageMeteringService` `LIMIT 1` orders by `limit_value DESC` over an
  effective-status set — it selects a limit value, never a subscription row.
- `findFirstByTenant`-style arbitrary JPA selection on subscriptions: 0.
- Tenant-only billing lifecycle predicates: 0 (dunning scan excludes terminal rows).
- Tenant-only entitlement resolution: 0 (resolver + usage metering resolve effective).
- Arbitrary subscription selection: 0.
- `tenant_subscriptions` is on the ModuleReset hardcoded PROTECTED denylist (never
  resettable/deletable).

Named consumers final state: BillingStateService ✓, EntitlementResolver ✓,
SubscriptionImpactService ✓, TenantDirectoryAdministrationService ✓, subscription
creation guard ✓, resume lifecycle path ✓, ProvisioningJobRunner ✓.

---

## 5. MODEL_B Behavior Final Audit (fresh canonical-run evidence)

R0C-10 PostgreSQL acceptance battery — 8 new acceptance classes, run against real
PostgreSQL Direct inside the canonical run; per-class testcase counts parsed from fresh
XML: 10 + 6 + 5 + 3 + 6 + 12 + 4 + 2 = **48**.

| Field | Value |
|---|---|
| R0C10_PG_ACCEPTANCE_TESTS | 48 |
| R0C10_PG_ACCEPTANCE_FAILURES | 0 |
| R0C10_PG_ACCEPTANCE_ERRORS | 0 |
| R0C10_PG_ACCEPTANCE_SKIPPED | 0 |

| Behavior gate | Proven by (all PASS in canonical XML) |
|---|---|
| HISTORICAL_CARDINALITY=0..N | `db02_expiredPlusActiveAllowed`, `db03_twoExpiredPlusActiveAllowed`, `db04_cancelledExpiredActiveAllowed`, `db07_multipleTerminalRowsAllowed` |
| EFFECTIVE_CARDINALITY=0..1 | `db05_twoActiveRejected`, `db06_trialPlusActiveRejected`, `db09_concurrentEffectiveInserts`, `qa03_effectiveAtMostOne` |
| EXPIRED_SUCCESSOR_RUNTIME=PASS | `qd02_gatedSuccessorCreation` (gate ON → approved successor; effective cardinality 1); `qd01_gateOffPreservesDeadEnd` (gate OFF → dead end preserved bit-for-bit) |
| EXPIRED_OLD_ROW_IMMUTABLE=PASS | `qd02` asserts the old EXPIRED row is byte-for-byte immutable after successor creation |
| EXPIRED_RESUME_FAIL_CLOSED=PASS | `qg01_resumeExpiredFailsClosed`, `qg02_resumeTerminatedFailsClosed`, `pg03_resumeExpiredIllegal`, `pg03b_legacyResumeSilentNoOp` (superseded assertion — now 409 CONFLICT), `qi02_provisioningHistoricalRowFailsClosed` |
| EXPIRED_FALSE_EVENT_COUNT=0 | `qg01`: zero `SUBSCRIPTION.RESUMED` change events |
| EXPIRED_FALSE_AUDIT_COUNT=0 | `qg01`: platform audit writer never invoked (`verifyNoInteractions(audit)`) |
| EXPIRED_ENTITLEMENT_RECALCULATION_COUNT=0 | `qg01`: zero entitlement events of any kind |
| EXPIRED_PROVISIONING_SIDE_EFFECT_COUNT=0 | `qg01`: zero `provisioning_jobs` rows for the subscription |
| EXPIRED_BILLING_SIDE_EFFECT_COUNT=0 | `qg01`: zero invoices; `billing_state` unchanged |
| AUTOMATIC_SECOND_TRIAL=REJECTED | `qe01_noSecondAutomaticTrial` (successor trial FORCED OFF regardless of plan default or request payload) |
| CANCELLED_RESUME_COMPATIBILITY=PASS | `qg03_cancelledResumePreserved` (R0C-7 revival contract intact when no effective successor) |
| CANCELLED_SUCCESSOR_COLLISION_GUARD=PASS | `qf02_cancelledResumeWithEffectiveSuccessorRejected` |
| CANCELLED_CREATE_NEW=REJECTED | `qf01_createNewAfterCancelledRejected` |
| TERMINATED_RESUBSCRIBE=DEFERRED | `qf03_createNewAfterTerminatedRejected` |

Supporting adaptations (also fresh in the canonical run, all passing): `EntitlementResolverTest` 15, `DowngradeSafetyTest` 6, `TrialExpirationRuntimePostgresTest` 28, `TrialExpirationRedPostgresTest` 5, `SubscriptionLifecycleTest` 43, `ProvisioningJobRunnerTest` 5.

Creation guard (single INSERT path) decision order, verified in code and tests:
effective exists → CONFLICT; no history → first creation; deterministic latest
historical (`created_at DESC, id DESC`): EXPIRED → gate OFF: fail-closed rejection,
gate ON: approved continuation (trial forced off; partial unique index is the
concurrency backstop — `qj01_concurrentSuccessorCreation`,
`qj02_concurrentFirstCreation`); CANCELLED → create-new rejected; TERMINATED →
deferred; any other status → CONFLICT. Lost insert races translate to the
deterministic domain CONFLICT (no PostgreSQL constraint leakage).

---

## 6. Billing / Entitlement / Provisioning Final Audit

| Gate | Proven by (PASS) |
|---|---|
| BILLING_EFFECTIVE_CONVERGENCE | `bb02_billingTransitionHitsEffectiveRowOnly`, `bb03_dunningScanExcludesTerminalRows` |
| HISTORICAL_INVOICE_ISOLATION | `bb01_historicalInvoiceDoesNotDunnSuccessor` — S1=EXPIRED (old overdue OPEN invoice) + S2=ACTIVE: `evaluateAndTransition` returns CURRENT for S2; S2 status/billing_state unchanged; S1 row untouched. An S1 invoice can never dunn, suspend, or otherwise change S2 |
| ENTITLEMENT_EFFECTIVE_CONVERGENCE | `bb04_entitlementEffectiveResolution`, `bb05_impactResolvesEffectivePlan`, `qi01_entitlementFromEffectiveRowOnly`, `pg08_entitlementLookupDeniedForExpiredTenant` — historical terminal rows cannot override the effective subscription |
| PROVISIONING_SUBSCRIPTION_ID_SCOPE | `qi02_provisioningHistoricalRowFailsClosed`, `qi03_provisioningSuccessorTargetsById`, `qi04_crossTenantProvisioningIsolation` — provisioning targeting S1 cannot mutate S2; all provisioning work is `subscription_id`-scoped |

Lifecycle single-writer remains intact: every `tenant_subscriptions.status` write in
production flows through `SubscriptionCommandService.applyCanonicalTransition`
(the only `UPDATE … SET status` sites in src/main, 3 hits, all inside that one method),
with optimistic `WHERE id = ? AND status = ?` guards.

---

## 7. Feature Gate + Mixed Version Safety

| Field | Value |
|---|---|
| FEATURE_GATE | `sanad.scp.subscription.expired-successor.enabled` (system property; env fallback `SANAD_SCP_EXPIRED_SUCCESSOR_ENABLED`) |
| FEATURE_GATE_DEFAULT | OFF |
| Gate OFF behavior | no EXPIRED successor row runtime — R0C-9 dead end preserved bit-for-bit (`qd01`) |
| Gate ON behavior | only the approved EXPIRED continuation path (no effective subscription + deterministic latest historical EXPIRED + existing creation contract satisfied) inserts a successor (`qd02`) |
| OLD_BINARY_SECOND_ROW_CREATION_PATHS | 0 — production has exactly ONE INSERT path into `tenant_subscriptions` (`SaasAdministrationService.createSubscription`, guarded end-to-end); no bootstrap/registration/seed INSERT path exists |
| Backstop | `uk_tenant_subscriptions_effective` is a DB-level invariant independent of binary version — a second EFFECTIVE row can never be committed by any binary |
| MIXED_VERSION_SAFETY | PASS |

Hard invariant: **OLD_BINARY + MULTIPLE_ROWS = NEVER ALLOWED** — enforced by rollout
order (runbook Stages A–D) plus the DB-level partial unique index; an old binary can
never CREATE the state (its guard counts all rows → CONFLICT) and can never COMMIT a
second effective row (partial unique violation).

---

## 8. Security / Data Integrity Final Audit

| Check | Result |
|---|---|
| Canonical secret scan (`scripts/ci/scan_secrets.py`) | PASS — 4786 files scanned, 0 findings, 0 scan errors (fresh run at certification) |
| SECRETS_COMMITTED | 0 |
| HARDCODED_CREDENTIALS | 0 |
| CROSS_TENANT_READ_PATHS | 0 (all R0C-10 lookups tenant_id- or id-scoped; `db08_tenantIsolation`, `pg09_tenantIsolationUnaffected`, `qi04_crossTenantProvisioningIsolation` PASS) |
| CROSS_TENANT_WRITE_PATHS | 0 |
| UNAUTHORIZED_STATUS_WRITERS | 0 (single canonical writer; R0C-10 delta adds none) |
| PHYSICAL_SUBSCRIPTION_DELETES | 0 (`DELETE FROM tenant_subscriptions` occurrences in src/main: 0; migration is additive-only) |
| ROLE_PRIVILEGE_EXPANSION | 0 (no GRANT / CREATE ROLE / ALTER ROLE / BYPASSRLS statements in the R0C-10 delta; the single grep hit is runbook prose describing the `sanad` role's NOSUPERUSER/NOCREATEDB/NOCREATEROLE/NOBYPASSRLS guarantees) |
| RLS_WEAKENING | 0 (zero RLS statements in the delta; `db10_rlsStateUntouched` PASS) |
| TENANT_ONLY_AMBIGUOUS_CURRENT_LOOKUPS | 0 (Phase 4 inventory) |
| R0C10_SECURITY_AUDIT | PASS |
| R0C10_DATA_INTEGRITY_AUDIT | PASS |

---

## 9. RLS / Tenant Isolation Classification (truthful)

| Field | Value |
|---|---|
| TENANT_SUBSCRIPTIONS_TABLE_RLS | **NO** — `tenant_subscriptions` is application-isolated; no migration creates, enables, or forces a PostgreSQL RLS policy on it (16+ other module tables do have RLS migrations; `tenant_subscriptions` is not among them) |
| R0C10_RLS_REGRESSION | NOT APPLICABLE as a table-RLS claim (no such policy exists to regress; none claimed) |
| R0C10_TENANT_ISOLATION | PASS — proven by application/domain isolation tests on real PostgreSQL: `db08_tenantIsolation`, `pg09_tenantIsolationUnaffected`, `qi04_crossTenantProvisioningIsolation`, plus tenant-scoped resolution everywhere (Phase 4) |
| R0C10_RLS_SCOPE | NO_NEW_RLS_WEAKENING / EXISTING_RLS_MODULES_UNCHANGED |

No authoritative R0C-10 requirement mandates adding PostgreSQL RLS to
`tenant_subscriptions`; the R0C-10 design requirement is the MODEL_B partial unique +
effective-resolution convergence, which is what was delivered. No RLS policy was
invented during certification.

---

## 10. Rollout / Rollback Runbook

Committed file: `docs/superpowers/plans/2026-09-06-scp-r0c-10-rollout-rollback-runbook.md`.

| Required item | Runbook coverage |
|---|---|
| A. consumer-converged binary with feature OFF | Stage A |
| B. schema migration | Stage B (`V20260906_1`, gate still OFF) |
| C. prove all live binaries multiplicity-compatible | Stage C |
| D. only then feature enable | Stage D (explicit operator decision; `DO_NOT_ENABLE_PROD=YES` — NOT part of R0C-10) |
| E. no old binary after multiple rows | §1 hard invariant + Stage ordering |
| F. kill switch stops new successors | §5 kill-switch reference (gate OFF at any time; existing rows untouched) |
| G. historical rows never deleted | §4/§5 (physical DELETE of subscription history FORBIDDEN; no repair mechanism exists) |
| H. rollback to pre-multiplicity binary forbidden after multiplicity exists | §4 (`ROLLBACK_TO_PRE_MULTIPLICITY_BINARY = FORBIDDEN` once any tenant has >1 row) |

| Field | Value |
|---|---|
| R0C10_ROLLOUT_RUNBOOK | PASS |
| R0C10_ROLLBACK_RUNBOOK | PASS |

Production feature is NOT enabled (`FEATURE_GATE_DEFAULT=OFF`; enablement is an
explicit operator decision outside R0C-10).

---

## 11. Intentionally Superseded R0C-9 Forensic Assertions

| R0C-9 assertion | Superseding R0C-10 behavior | Why superseded |
|---|---|---|
| PG-03b: `resume(EXPIRED)` is a silent no-op (status stays EXPIRED while a misleading `SUBSCRIPTION.RESUMED` event, a `SUBSCRIPTION.RESUME` audit row and an entitlement recalculation are emitted) | Task G (P1 defect fix): `resume(EXPIRED)` / `resume(TERMINATED)` FAIL CLOSED with 409 CONFLICT **before any side effect** — no status mutation, no RESUMED event, no misleading audit, no entitlement recalculation, no billing or provisioning side effect (`qg01`, `qg02`, `pg03b` now documents the supersession) | The R0C-9-era behavior was a P1 defect (false-success artifacts), not a contract to preserve; R0C-10 pins the fail-closed contract |
| Storage invariant: at most one `tenant_subscriptions` row per tenant (`UNIQUE(tenant_id)` = `uk_tenant_subscriptions_tenant`) | MODEL_B: historical 0..N — `V20260906_1` drops the full-tenant unique and adds the partial unique `uk_tenant_subscriptions_effective` | MODEL_B preserves terminal history as multiple immutable rows; uniqueness now bounds only the EFFECTIVE row, which is the real invariant the product needs |
| Expiry dead end: continuation after EXPIRED is impossible in every configuration | Conditional gate: gate OFF preserves the R0C-9 dead end bit-for-bit (`qd01`); gate ON permits exactly one approved EXPIRED continuation path with trial forced off (`qd02`, `qe01`) | R0C-10 delivers operator-controlled continuation readiness without changing default behavior |
| Tenant-only resolution: billing / entitlement / impact / directory resolve "the" subscription by `tenant_id` alone (safe only under 1-row invariant) | All such consumers converge on `SubscriptionResolutionService.findEffectiveSubscription` (effective predicate, partial-unique-bounded, deterministic ordering); exact lifecycle/invoice/provisioning work stays `subscription_id`-scoped; reporting keeps ALL_HISTORY | Under historical multiplicity, a tenant-only lookup would be an arbitrary row selection; effective resolution is the multiplicity-safe equivalent with unchanged observable semantics |
| R0C-9 full-suite totals: 2574 tests / 11:25 (R0C-9 canonical corrective run) | 2610 tests / 13:14 (R0C-10 canonical run) | R0C-10 adds 8 acceptance classes (48 tests) plus supporting adaptations; the R0C-9 numbers remain valid for the R0C-9 chain and are superseded only as "latest canonical suite" |

---

## 12. Explicitly Deferred (out of R0C-10 scope)

- TERMINATED re-subscription semantics (`qf03` pins the deferral)
- CANCELLED create-new semantics (resume remains the sanctioned path; `qf01` pins the rejection)
- Repeat-trial future product policy beyond the current automatic-second-trial prohibition (`qe01`)
- Production feature enablement (Stage D operator decision; `DO_NOT_ENABLE_PROD=YES`)
- Merge to main and deploy (`MERGE=NO`, `DEPLOY=NO`)
- R0C-11 (`R0C11=FORBIDDEN`)

---

## 13. Certification Checklist (all gates, actual values)

```
R0C10_PG_ACCEPTANCE_TESTS=48              R0C10_PG_ACCEPTANCE_FAILURES=0
R0C10_PG_ACCEPTANCE_ERRORS=0              R0C10_PG_ACCEPTANCE_SKIPPED=0
FULL_MAVEN_EXECUTION_MODE=SINGLE_COMPLETE_CANONICAL_RUN
FULL_MAVEN_TESTS=2610                     FULL_MAVEN_FAILURES=0
FULL_MAVEN_ERRORS=0                       FULL_MAVEN_SKIPPED=6
FULL_MAVEN_BUILD=SUCCESS                  FULL_MAVEN_DURATION=13:14
FULL_MAVEN_COUNT_RECONCILIATION=PASS      STALE_XML_FILES=0
POST_CANONICAL_CODE_DELTA=0
AMBIGUOUS_LOOKUPS_REMAINING=0             UNKNOWN_LOOKUPS_REMAINING=0
LEGACY_FULL_TENANT_UNIQUE_REMOVED=YES     PARTIAL_EFFECTIVE_UNIQUE=PASS
EXPIRED_SUCCESSOR_RUNTIME=PASS            EXPIRED_OLD_ROW_IMMUTABLE=PASS
EXPIRED_RESUME_FAIL_CLOSED=PASS           AUTOMATIC_SECOND_TRIAL=REJECTED
CANCELLED_RESUME_COMPATIBILITY=PASS       CANCELLED_SUCCESSOR_COLLISION_GUARD=PASS
CANCELLED_CREATE_NEW=REJECTED             TERMINATED_RESUBSCRIBE=DEFERRED
BILLING_EFFECTIVE_CONVERGENCE=PASS        HISTORICAL_INVOICE_ISOLATION=PASS
ENTITLEMENT_EFFECTIVE_CONVERGENCE=PASS    PROVISIONING_SUBSCRIPTION_ID_SCOPE=PASS
FEATURE_GATE_DEFAULT=OFF                  MIXED_VERSION_SAFETY=PASS
R0C10_TENANT_ISOLATION=PASS               R0C10_SECURITY_AUDIT=PASS
R0C10_DATA_INTEGRITY_AUDIT=PASS           R0C10_ROLLOUT_RUNBOOK=PASS
R0C10_ROLLBACK_RUNBOOK=PASS
TRACKED_WORKTREE_CLEAN=YES                STAGED_WORKTREE_CLEAN=YES
R0C10_FORWARD_ONLY_CHAIN=PASS             R0C10_REMOTE_DURABILITY=PASS
```

Certification commit: created at finalization ("docs(scp): certify and close R0C-10
multiplicity runtime"), staged explicitly (single file, this document), parent =
`6c026c7b8fe8cc97d1fbc24771a5e05ecebf1b46`; `R0C10_FINAL_HEAD` = that commit's SHA,
pushed fast-forward, verified durable on origin.
