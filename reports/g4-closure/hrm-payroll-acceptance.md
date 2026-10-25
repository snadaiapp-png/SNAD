# G4 Closure — HRM/Payroll Acceptance (lifecycle DRAFT → CALCULATED → REVIEWED → APPROVED)

**Candidate SHA:** `b1e22e20de17cadb17a875d8888a856980ff9419` · PR-head tested SHA: `952723e53bd8d3b434300bb7536f54b8bffffe20`

---

## 1. Lifecycle state machine (implemented: `PayrollLifecycle`)

Governed forward transitions (asserted green by `PayrollLifecycleTest` 4/4, and PostgreSQL Direct 13/13):

```
DRAFT → CALCULATED → REVIEWED → APPROVED → EXPORTED (terminal)
       (recalculate allowed only in CALCULATED; CANCELLED terminal)
```

Illegal transitions are rejected (e.g., DRAFT→APPROVED, CALCULATED→EXPORTED, APPROVED→CALCULATED, EXPORTED→REVIEWED; recalculation after REVIEW fails closed and rolls back idempotency evidence — `recalculationFailsClosedAfterReviewAndRollsBackIdempotency`).

## 2. Mandate §5 coverage map → actual green tests (all counts element-based from raw reports / job logs)

| Requirement | Evidence (test → run → result) |
|---|---|
| Valid and invalid state transitions | `PayrollLifecycleTest` (4) + `invalidTransitionRollsBackEverySideEffect`, `governedLifecyclePersistsReviewerApproverAndExportEvidence`, `cancellationPersistsAtomicallyAndIsTerminal`, `recalculationIsAllowedOnlyWhileCalculated` (PayrollLifecyclePostgresTest 13/13 — run `40149956367` 25/25 SUCCESS; PMV JOB D) |
| Idempotency and replay | `transitionCommitsMutationAuditOutboxAndIdempotencyAtomicallyAndReplays` (replay returns the original result); `sameIdempotencyKeyWithDifferentFingerprintFailsClosed` (409 `HRM_IDEMPOTENCY_CONFLICT`); `sameIdempotencyKeyAcrossDifferentActorsDoesNotCollideInOutbox`; `HrIdempotencyIntegrationTest` 12/12; `HrmIdempotentCommandExecutorReplayProjectionTest` 1/1 |
| Concurrent requests, version conflicts, stale updates | `concurrentSameVersionTransitionsProduceExactlyOneWinner` (two concurrent transitions on the same version → exactly one winner); `staleVersionFailsClosedAndLeavesNoPartialIdempotencyEvidence`; `recalculationIsAtomicVersionedIdempotentAndAmountMinimized` |
| Correct `403` and `409` per contract | **403**: `@RequireCapability` aspect + `PayrollAuthorizationGuard` → `AccessDeniedException` → canonical `HRM_SCOPE_DENIED` 403 envelope (`HrApiExceptionHandler`); asserted fail-closed by `PayrollAuthorizationContractTest` 5/5 (review/approve/export without the exact tenant-scoped capability are denied; `HrApiV2AuthorizationTest` 6/6) and in the T10 real-browser flow. **409**: fixed enum mapping `HrApiErrorCode` — `HRM_PAYROLL_VERSION_CONFLICT(409)`, `HRM_IDEMPOTENCY_CONFLICT(409)`, `HRM_CONCURRENCY_CONFLICT(409)`; asserted by the stale-version/idempotency-fingerprint tests above |
| Cross-tenant read/write rejection | `foreignTenantCannotObserveOrMutatePayrollRun` + dedicated isolation workflow (2/2) + T10 foreign-tenant-denied browser flow + `HrRlsFailClosedIntegrationTest` 259/259 |
| Audit records, correlation IDs, traceability | `auditFailureRollsBackMutationAndIdempotency`, `outboxDatabaseFailureAfterAuditRollsBackEverything` (audit+outbox+mutation commit atomically — an audit failure rolls back the mutation), `HrAuditOutboxAtomicityIntegrationTest` 18/18, `HrSensitiveReadAuditIntegrationTest` 8/8; reviewer/approver/export evidence persisted by the lifecycle (`governedLifecyclePersistsReviewerApproverAndExportEvidence`) |
| API, persistence, UI consistency | API contract: `PayrollApiContractTest` 4/4 (canonical v2 base path, explicit capabilities per operation, Idempotency-Key required on mutations, **no statutory bank/payment endpoint exposed**); persistence: PostgreSQL Direct suites; UI: T10 Playwright authenticated acceptance (real browser, full stack) **1 passed** — create→calculate→review→approve→export boundary with foreign-tenant denial (run `40149959660`) |
| Permission enforcement / unauthorized access | T6 run `40149958050`: 13/13 (capability fail-closed contracts + `HrModuleBoundaryArchitectureTest` 8/8) |

## 3. Accounting boundary (no payments exposure)

- `PayrollAccountingExportContractTest` 5/5 — accounting export boundary.
- `noStatutoryBankOrPaymentEndpointIsExposed` (PayrollApiContractTest) — no bank/payment endpoints in the payroll API surface.
- `PAYMENTS = OFF` remains in force; nothing in the payroll surface enables payments.

## 4. Required-test execution record (Issue #1439 item 2)

| Required suite | Run | SHA | Result |
|---|---|---|---|
| G4 payroll PostgreSQL Direct (isolation) | `40149954567` | `b1e22e20` | SUCCESS 2/2 |
| G4-T5 payroll lifecycle (unit + PostgreSQL Direct) | `40149956367` | `b1e22e20` | SUCCESS 25/25 |
| G4-T6 payroll authorization | `40149958050` | `b1e22e20` | SUCCESS 13/13 |
| G4-T10 authenticated acceptance (Spring Boot + Next.js + Playwright) | `40149959660` | `b1e22e20` | SUCCESS (1 passed e2e + 2/2 contract) |
| Full Maven suite | Run #40141730303 (PR head) | `952723e5` | 4722/0F/0E/37S (all skips explained + covered) — reconciliation PASS |
| Full Maven suite on main head | `40149030737` (Gate G dispatch) | `b1e22e20` | in progress → recorded in evidence-manifest.json |
| HRM-focused suite (JOB D) | `40147222757` | `b1e22e20` | 1042/0F/0E/0S |
| PostgreSQL Acceptance (37 gated cases) | #40141730303 | `952723e5` | 37/37 (same run) |
| CRM regression | #40141730303 | `952723e5` | 85/85 |

**Maven/Surefire reconciliation:** element-based XML enumeration (637 files) == console aggregate (4722/0F/0E/37S); 0 parse errors; 0 duplicate reports; every skip classified and covered by the dedicated acceptance job. Failures/errors/skips are fully explained above; there are no critical test exclusions beyond the designed profile gates.

## 5. G4-T12 PMV JOB A–F and final manifest

PMV run `40147222757` on `b1e22e20` — JOB A (frontend verification), B (backend compile), C (PostgreSQL Direct integration, host-native), D (HRM-focused security/RLS), E (security & governance scans: secret scan 6259 files/0 findings, workflow security, SDS compliance, i18n, performance budget), F (final evidence aggregation — the only closure authority): **all SUCCESS**. JOB F manifest: `result=PASS`, 23/23 checks, `failedChecks=[]`, `skippedChecks=[]`, `missingChecks=[]`.

## 6. Production UI acceptance — intentionally unexecuted

Per Issue #1439 item 3 and the mandate: **not dispatched**. Preconditions not yet verified/authorized: protected Production environment credentials/reviewers confirmation and explicit owner authorization for the read-only production test. The workflow (`hrm-g4-production-ui-acceptance.yml`) requires `workflow_dispatch` on `main`, production test credentials, and release-identity alignment; any deployed-web/main mismatch must be recorded as a release/provenance blocker — **no deployment may be performed to make the test green** (none was).

## 7. Sanitized evidence statement

All evidence in this package comes from ephemeral CI PostgreSQL instances and CI logs/artifacts. No production data, no real personal data, no credentials, and no token values are included. Test users/tenants are synthetic (`tenant A/B` seeded fixtures).
