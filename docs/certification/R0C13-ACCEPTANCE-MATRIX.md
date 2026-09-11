# R0C13 — Revenue/Billing Integration Closure — Acceptance Matrix

- **Date:** 2026-09-11
- **Starting production baseline:** `16509abed344ce5d6635eb3660512e3e9011584b`
- **Issue:** #1017
- **Design:** `docs/superpowers/specs/2026-09-11-r0c13-revenue-billing-integration-closure-design.md`
- **Plan:** `docs/superpowers/plans/2026-09-11-r0c13-revenue-billing-integration-closure-implementation.md`
- **Current stage:** R13-S0.2
- **Implementation:** NOT STARTED

## Acceptance vocabulary

- **PASS** — directly proven by authoritative evidence.
- **FAIL** — acceptance predicate disproven.
- **BLOCKED** — required evidence cannot currently be obtained.
- **NOT_STARTED** — implementation/evidence not yet attempted.
- **N/A_WITH_PROOF** — explicitly non-applicable with recorded evidence.

No inferred PASS is permitted.

## A. Scope and architecture acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-AC-001 | Exact starting baseline is `16509abed...` and no implementation precedes scope approval | branch/commit evidence | S0/G01 | PASS |
| R13-AC-002 | Finance remains source of truth for accounting invoices/payments/ledger | architecture tests + code review | G03 | NOT_STARTED |
| R13-AC-003 | No parallel GL/accounting model introduced | schema/code scan | G03 | NOT_STARTED |
| R13-AC-004 | `billing_invoices` remains compatible with existing dunning | regression tests | G03/G06 | NOT_STARTED |
| R13-AC-005 | `BillingStateService` remains sole billing-state authority | writer scan + tests | G06 | NOT_STARTED |
| R13-AC-006 | `SubscriptionCommandService` remains lifecycle single writer | writer scan + tests | G06 | NOT_STARTED |
| R13-AC-007 | Commerce `PaymentGatewayPort` contract remains unchanged | API diff + commerce tests | G04/G08 | NOT_STARTED |
| R13-AC-008 | SaaS billing provider contract uses integer minor units | domain tests/static scan | G04 | NOT_STARTED |
| R13-AC-009 | R0C13 implementation is additive/backward-compatible | API/schema diff | G08 | NOT_STARTED |
| R13-AC-010 | LIVE payment collection is outside automatic engineering closure | config/governance evidence | G04/G11 | NOT_STARTED |

## B. Database / tenancy / migration acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-DB-001 | All new tenant-scoped tables have `tenant_id NOT NULL` | migration inspection + PG catalog query | G02 | NOT_STARTED |
| R13-DB-002 | New tenant tables ENABLE and FORCE RLS | PG catalog query | G02 | NOT_STARTED |
| R13-DB-003 | own-tenant access succeeds | PostgreSQL Direct test | G02 | NOT_STARTED |
| R13-DB-004 | cross-tenant read denied | PostgreSQL Direct test | G02 | NOT_STARTED |
| R13-DB-005 | cross-tenant write denied | PostgreSQL Direct test | G02 | NOT_STARTED |
| R13-DB-006 | no-tenant context fails closed | PostgreSQL Direct test | G02 | NOT_STARTED |
| R13-DB-007 | provider event id uniqueness is DB-enforced | concurrent/replay PG test | G02/G05 | NOT_STARTED |
| R13-DB-008 | idempotency key uniqueness is DB-enforced | concurrent/replay PG test | G02/G05 | NOT_STARTED |
| R13-DB-009 | same-tenant FK prevents mismatched linkage | PostgreSQL Direct test | G02/G03 | NOT_STARTED |
| R13-DB-010 | Flyway fresh chain succeeds without repair/out-of-order | canonical PG Direct evidence | G02/G08 | NOT_STARTED |
| R13-DB-011 | destructive DDL against R0C12 billing/finance tables = 0 | migration diff scan | G02 | NOT_STARTED |
| R13-DB-012 | cardholder/secret columns = 0 | schema scan | G02/G09 | NOT_STARTED |

## C. Finance integration acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-FIN-001 | one billing invoice maps to at most one Finance invoice | DB unique constraint + PG test | G03 | NOT_STARTED |
| R13-FIN-002 | integration replay creates no duplicate Finance invoice | unit + PG test | G03 | NOT_STARTED |
| R13-FIN-003 | settlement replay creates no duplicate Finance payment | unit + PG test | G03 | NOT_STARTED |
| R13-FIN-004 | amount mismatch fails closed | test | G03/G06 | NOT_STARTED |
| R13-FIN-005 | currency mismatch fails closed | test | G03/G06 | NOT_STARTED |
| R13-FIN-006 | cross-tenant invoice/payment link fails | PG test | G03 | NOT_STARTED |
| R13-FIN-007 | subscription package does not write Finance journal tables directly | ArchUnit/static scan | G03 | NOT_STARTED |
| R13-FIN-008 | Finance regression suite remains green | FinanceModuleIntegrationTest + full suite | G03/G08 | NOT_STARTED |
| R13-FIN-009 | provider settlement is reflected in authoritative Finance payment before lifecycle recovery | integration test | G06 | NOT_STARTED |

## D. Provider integration acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-PAY-001 | provider contract is domain-neutral | interface/code review | G04 | NOT_STARTED |
| R13-PAY-002 | DISABLED is production-safe default | config tests | G04 | NOT_STARTED |
| R13-PAY-003 | DISABLED refuses create/verify/refund | tests | G04 | NOT_STARTED |
| R13-PAY-004 | TEST requires explicit configuration | context/config test | G04 | NOT_STARTED |
| R13-PAY-005 | LIVE cannot start without separate authority/config gate | startup/fail-closed test | G04/G11 | NOT_STARTED |
| R13-PAY-006 | no simulated provider activates by default | Spring context + production config scan | G04/G08 | NOT_STARTED |
| R13-PAY-007 | provider secret is never logged or returned | log/API security tests | G09 | NOT_STARTED |
| R13-PAY-008 | raw PAN/CVC/card data storage = 0 | schema/source scan | G09 | NOT_STARTED |

## E. Webhook / event acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-WH-001 | invalid signature causes zero domain mutation | integration test | G05 | NOT_STARTED |
| R13-WH-002 | valid signed event is accepted exactly once | integration test | G05 | NOT_STARTED |
| R13-WH-003 | duplicate event is side-effect-free | concurrent PG test | G05 | NOT_STARTED |
| R13-WH-004 | tenant identity resolves from trusted binding, not body input | negative test | G05 | NOT_STARTED |
| R13-WH-005 | unknown provider customer/reference is rejected | negative test | G05 | NOT_STARTED |
| R13-WH-006 | processing failure remains retryable | failure-injection test | G05 | NOT_STARTED |
| R13-WH-007 | required mutation + audit + outbox is atomic | PG transaction test | G05 | NOT_STARTED |
| R13-WH-008 | billing outbox has deterministic typed/versioned events | contract tests | G05 | NOT_STARTED |
| R13-WH-009 | no secret/raw sensitive provider payload in evidence | artifact scan | G05/G09 | NOT_STARTED |

## F. Lifecycle / dunning / reconciliation acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-LC-001 | provider code never writes subscription status directly | writer scan + ArchUnit | G06 | NOT_STARTED |
| R13-LC-002 | provider code never writes billing_state directly | writer scan + ArchUnit | G06 | NOT_STARTED |
| R13-LC-003 | successful settlement can recover valid PAST_DUE/SUSPENDED through canonical path | PG integration test | G06 | NOT_STARTED |
| R13-LC-004 | terminal subscription cannot be resurrected by late payment | PG negative test | G06 | NOT_STARTED |
| R13-LC-005 | historical subscription invoice cannot dunn/recover successor | R0C10 regression | G06/G08 | NOT_STARTED |
| R13-LC-006 | existing dunning cadence/grace semantics unchanged | regression tests | G06 | NOT_STARTED |
| R13-LC-007 | reconciliation detects amount mismatch | reconciliation test | G06 | NOT_STARTED |
| R13-LC-008 | reconciliation detects currency mismatch | reconciliation test | G06 | NOT_STARTED |
| R13-LC-009 | reconciliation detects missing Finance/provider linkage | tests | G06 | NOT_STARTED |
| R13-LC-010 | reconciliation is read-only by default | transaction/write-count proof | G06 | NOT_STARTED |
| R13-LC-011 | repair action requires explicit capability + audit | API/security test | G06/G07 | NOT_STARTED |

## G. RBAC / API / UI acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-SEC-001 | BILLING.READ is deny-by-default | auth tests | G07 | NOT_STARTED |
| R13-SEC-002 | BILLING.MANAGE is required for billing mutations | auth tests | G07 | NOT_STARTED |
| R13-SEC-003 | BILLING.RECONCILE protects repair commands | auth tests | G07 | NOT_STARTED |
| R13-SEC-004 | BILLING.REFUND is separate from generic manage | auth tests | G07 | NOT_STARTED |
| R13-SEC-005 | BILLING.PROVIDER_ADMIN protects provider diagnostics/admin | auth tests | G07 | NOT_STARTED |
| R13-SEC-006 | existing EXECUTIVE_BILLING callers remain compatible | regression tests | G07/G08 | NOT_STARTED |
| R13-SEC-007 | cross-tenant API access denied | API + PG tests | G07 | NOT_STARTED |
| R13-SEC-008 | webhook path has signature auth, not tenant JWT dependency | API tests | G05/G07 | NOT_STARTED |
| R13-WEB-001 | all new strings have Arabic/English parity | i18n gate | G07 | NOT_STARTED |
| R13-WEB-002 | SDS/logo/brand governance passes | web CI | G07/G08 | NOT_STARTED |
| R13-WEB-003 | no card/payment-secret UI fields exist | source/UI tests | G07/G09 | NOT_STARTED |
| R13-WEB-004 | mutating UI controls follow exact backend capability model | component/API tests | G07 | NOT_STARTED |

## H. Concurrency / failure-injection acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-FI-001 | concurrent duplicate webhooks yield one settlement | PG concurrency test | G06/G09 | NOT_STARTED |
| R13-FI-002 | webhook/reconciliation race converges deterministically | PG concurrency test | G06/G09 | NOT_STARTED |
| R13-FI-003 | Finance failure after provider success does not falsely activate subscription | failure injection | G09 | NOT_STARTED |
| R13-FI-004 | outbox failure rolls back required local mutation | failure injection | G09 | NOT_STARTED |
| R13-FI-005 | audit failure rolls back required privileged mutation | failure injection | G09 | NOT_STARTED |
| R13-FI-006 | provider timeout produces retryable state, not paid state | failure injection | G09 | NOT_STARTED |
| R13-FI-007 | unauthorized refund has zero provider/domain side effect | security test | G07/G09 | NOT_STARTED |
| R13-FI-008 | LIVE mode without authority fails closed | startup/config failure test | G04/G09 | NOT_STARTED |

## I. Regression / CI acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-CI-001 | full Maven suite passes on exact candidate SHA | canonical CI | G08 | NOT_STARTED |
| R13-CI-002 | PostgreSQL Direct acceptance passes host-native | CI logs/evidence | G08 | NOT_STARTED |
| R13-CI-003 | R0C12 regression passes | dedicated matrix | G08 | NOT_STARTED |
| R13-CI-004 | Finance regression passes | test evidence | G08 | NOT_STARTED |
| R13-CI-005 | Commerce production-safe payment regression passes | test evidence | G08 | NOT_STARTED |
| R13-CI-006 | Web lint/typecheck/tests/build pass | Web CI | G08 | NOT_STARTED |
| R13-CI-007 | security/workflow/secret scans pass | CI | G09 | NOT_STARTED |
| R13-CI-008 | unexplained skips = 0 | test reconciliation | G08 | NOT_STARTED |
| R13-CI-009 | immutable evidence manifest binds exact SHA | artifact | G09/G10 | NOT_STARTED |

## J. Protected release / production-safe closure acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-REL-001 | implementation PR exact head independently APPROVED | GitHub review | G10 | NOT_STARTED |
| R13-REL-002 | required exact-head checks all green | GitHub checks | G10 | NOT_STARTED |
| R13-REL-003 | PMV success on merged exact main | PMV manifest | G10/G11 | NOT_STARTED |
| R13-REL-004 | immutable exact-SHA image/provenance exists | artifact/OCI evidence | G10/G11 | NOT_STARTED |
| R13-REL-005 | production deployment exact SHA/image binding passes | release evidence | G11 | NOT_STARTED |
| R13-REL-006 | production provider mode = DISABLED | sanitized runtime evidence | G11 | NOT_STARTED |
| R13-REL-007 | no TEST/simulated provider active in production | runtime evidence | G11 | NOT_STARTED |
| R13-REL-008 | unsigned webhook rejected in production | safe smoke | G11 | NOT_STARTED |
| R13-REL-009 | health/readiness/Flyway/security/R0C12 smoke pass | release evidence | G11 | NOT_STARTED |
| R13-REL-010 | a live charge cannot be created in R0C13 engineering closure | controlled negative smoke | G11 | NOT_STARTED |
| R13-REL-011 | rollback/deactivation path is defined and executable | runbook/workflow evidence | G11 | NOT_STARTED |
| R13-REL-012 | no baseline drift at closure | main/Render recheck | G11/S12 | NOT_STARTED |

## K. Final closure

R0C13 engineering closure requires every mandatory item above to be PASS or N/A_WITH_PROOF and:

```text
R13-G01 = PASS
R13-G02 = PASS
R13-G03 = PASS
R13-G04 = PASS
R13-G05 = PASS
R13-G06 = PASS
R13-G07 = PASS
R13-G08 = PASS
R13-G09 = PASS
R13-G10 = PASS
R13-G11 = PASS

R0C13_ENGINEERING = CLOSED_PRODUCTION_VERIFIED
LIVE_PAYMENT_COLLECTION = NOT_ACTIVATED
LIVE_PAYMENT_AUTHORITY = SEPARATE_GATE
```

Any live-payment activation requires a new explicit human decision and cannot be inferred from R0C13 engineering closure.
