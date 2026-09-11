# R0C13 — Revenue/Billing Integration Closure — Implementation Plan

- **Date:** 2026-09-11
- **Design:** `docs/superpowers/specs/2026-09-11-r0c13-revenue-billing-integration-closure-design.md`
- **Starting production baseline:** `16509abed344ce5d6635eb3660512e3e9011584b`
- **Implementation repository baseline (R13-S0 re-anchor):** `15d50fa03b748a9190b9ee7380d746f91d55b34e`
- **Pre-R0C13 repository Flyway head:** `20260910.1`
- **Authority:** Issue #1017
- **Execution rule:** sequential fail-closed workstreams; no later workstream starts while the current mandatory gate is RED
- **Database acceptance:** PostgreSQL Direct / host-native only; Docker/Testcontainers are forbidden for governed database acceptance
- **Live payment collection:** FORBIDDEN until a separate explicit activation gate

## 0. Global execution rules

1. R13-S0 re-anchored implementation to protected `main=15d50fa03b748a9190b9ee7380d746f91d55b34e`. Re-lock current `main` before creating the implementation branch; any later drift requires another explicit re-anchor before work.
2. TDD for every behavioral increment: RED -> GREEN -> regression -> self-review.
3. No migration is edited after it has been merged/applied; remediation is forward-only.
4. No `flyway repair`, manual `flyway_schema_history` changes, `outOfOrder=true`, or direct production DDL.
5. No test can use H2/Testcontainers as proof for PostgreSQL-specific RLS/concurrency/idempotency.
6. No secret/cardholder data in source, fixtures, logs, artifacts, issues or PRs.
7. Finance remains the accounting source of truth.
8. `BillingStateService` and `SubscriptionCommandService` remain the existing single-writer authorities.
9. Commerce payment contracts remain unchanged.
10. R0C13 production deployment, when eventually authorized, defaults to provider mode `DISABLED`. Live mode is a different authority gate.

---

## S1 — Exact-baseline forensic lock & RED contracts

### Goal
Freeze the implementation starting point (initial R13-S0 re-anchor: `15d50fa03b748a9190b9ee7380d746f91d55b34e`) and prove the integration gaps before implementation.

### Required work
- capture exact main SHA/tree;
- enumerate current writers/readers for:
  - `billing_invoices`;
  - `finance_invoices`;
  - `finance_payments`;
  - `tenant_subscriptions.status`;
  - `tenant_subscriptions.billing_state`;
  - Commerce `PaymentGatewayPort`;
- add architecture/contract tests that fail when:
  - subscription billing writes Finance tables directly instead of through the integration port;
  - payment/provider code writes subscription lifecycle columns directly;
  - a simulated provider can activate by default;
  - provider amounts use floating point at the R0C13 domain boundary.

### Gate R13-G01
```text
BASELINE_LOCK = PASS
CURRENT_WRITER_MAP = COMPLETE
RED_CONTRACTS = REPRODUCED
APPLICATION_BEHAVIOR_CHANGE = 0
```

---

## S2 — Additive schema foundation

### Goal
Create the minimum durable persistence for provider binding, event idempotency, Finance linkage, outbox and reconciliation.

### Expected logical tables
Names may be normalized during implementation, but roles are fixed:

1. `subscription_billing_provider_customers`
2. `subscription_billing_payment_attempts`
3. `subscription_billing_provider_events`
4. `subscription_billing_finance_links`
5. `subscription_billing_outbox`
6. `subscription_billing_reconciliation_runs`
7. `subscription_billing_reconciliation_items`

### Mandatory constraints
- `tenant_id NOT NULL` on every tenant-scoped row;
- ENABLE + FORCE RLS on all new tenant-scoped tables;
- unique provider/customer and provider/event identifiers;
- stable idempotency key uniqueness;
- same-tenant composite FK where applicable;
- amount fields as BIGINT minor units;
- ISO currency length/validation;
- no cardholder-data fields;
- provider payload persistence limited to sanitized metadata/hash/reference;
- append-only audit/event evidence where applicable.

### Tests
- fresh Flyway chain applies on PostgreSQL Direct;
- repeated migration/application bootstrap does not duplicate seeds;
- own-tenant CRUD allowed only through correct context;
- cross-tenant read/write denied;
- no-tenant-context fails closed;
- duplicate provider event/idempotency key rejected deterministically.

### Gate R13-G02
```text
FLYWAY_FRESH_CHAIN = PASS
RLS_FORCE = PASS
TENANT_ISOLATION = PASS
IDEMPOTENCY_CONSTRAINTS = PASS
DESTRUCTIVE_DDL = 0
```

---

## S3 — Subscription -> Finance integration boundary

### Goal
Create a domain port from R0C13 billing orchestration to Finance without duplicating Finance logic.

### Required implementation
- define `SubscriptionFinancePort` (name may vary);
- implement it in/adjacent to Finance using existing Finance repositories/services;
- create/resolve one Finance invoice for each subscription billing invoice;
- stable external reference: `SCP_INVOICE:<billingInvoiceId>` or equivalent;
- create invoice lines from governed subscription billing line items;
- record settlement into `finance_payments` idempotently;
- reject mismatched amount/currency/reference;
- expose link identifiers for reconciliation;
- no direct journal SQL from the subscription package.

### Compatibility
`billing_invoices` remains the SCP/dunning compatibility invoice projection. The Finance invoice is the accounting invoice.

### Tests
- replay creates no duplicate Finance invoice;
- replayed settlement creates no duplicate payment;
- same reference + different amount fails closed;
- cross-tenant Finance linkage fails;
- Finance failure rolls back required billing transaction;
- existing Finance module suite remains green.

### Gate R13-G03
```text
FINANCE_SOURCE_OF_TRUTH = PRESERVED
INVOICE_LINK_IDEMPOTENCY = PASS
PAYMENT_LINK_IDEMPOTENCY = PASS
NO_PARALLEL_LEDGER = PASS
FINANCE_REGRESSION = PASS
```

---

## S4 — Provider-neutral billing payment contract

### Goal
Introduce SaaS-subscription payment integration without coupling the domain to Commerce or one PSP.

### Required implementation
Provider-neutral port with integer minor-unit money:
- ensure provider customer;
- create intent/session for an invoice;
- query payment state;
- request refund;
- verify/parse provider event envelope.

### Provider modes
- `DISABLED`: production-safe default; no external provider calls;
- `TEST`: provider sandbox/test mode only;
- `LIVE`: unavailable unless separate live-payment authority is present.

### First adapter
Stripe test-mode adapter is allowed as the first concrete implementation, but the domain API must remain provider-neutral.

### Safety
- no `matchIfMissing=true` for TEST/LIVE provider;
- DISABLED adapter refuses create/verify/refund;
- live credentials never appear in repository/tests;
- startup/config guard rejects LIVE without explicit activation evidence/configuration.

### Gate R13-G04
```text
PROVIDER_DOMAIN_NEUTRAL = PASS
MINOR_UNIT_MONEY = PASS
DISABLED_DEFAULT = PASS
TEST_MODE_EXPLICIT_ONLY = PASS
LIVE_MODE_FAIL_CLOSED = PASS
COMMERCE_PORT_UNCHANGED = PASS
```

---

## S5 — Signed webhook inbox + transactional outbox

### Goal
Make provider callbacks secure, durable, replay-safe and auditable.

### Webhook pipeline
```text
HTTP request
 -> signature validation
 -> provider event id extraction
 -> durable inbox insert
 -> trusted provider-reference resolution
 -> transactional domain processing
 -> Finance/SCP convergence
 -> audit + billing outbox
 -> processed marker
```

### Rules
- invalid signature: HTTP rejection + zero domain side effects;
- duplicate provider event id: no duplicate side effects;
- tenant is resolved from trusted stored binding, never trusted from request body;
- processing failure leaves event retryable;
- audit/outbox failure causes rollback where the mutation requires evidence;
- raw secret/card data is never stored.

### Gate R13-G05
```text
WEBHOOK_SIGNATURE = PASS
WEBHOOK_IDEMPOTENCY = PASS
UNTRUSTED_TENANT_INPUT = REJECTED
ATOMIC_AUDIT_OUTBOX = PASS
RETRYABLE_FAILURE = PASS
SECRET_CHD_LEAKAGE = 0
```

---

## S6 — Settlement, reconciliation, dunning & lifecycle convergence

### Goal
Converge provider state, Finance truth and SCP billing state without introducing a second lifecycle writer.

### Success path
- provider success -> Finance payment completed -> billing projection paid -> `BillingStateService.evaluateAndTransition` -> canonical `PAYMENT_RECEIVED` when valid.

### Failure path
- provider failure records payment attempt/event evidence;
- invoice remains unpaid/open as appropriate;
- existing dunning owns PAST_DUE/SUSPENDED timing;
- no direct subscription state mutation by webhook/provider code.

### Reconciliation
Read-only-by-default classification across provider/Finance/SCP. Explicit repair commands are capability-gated, idempotent and audited.

### Required race/replay tests
- duplicate webhook concurrently;
- webhook + reconciliation race;
- payment success after subscription cancellation;
- payment success for historical subscription cannot resurrect successor/terminal row;
- amount mismatch;
- currency mismatch;
- Finance write failure after provider success;
- outbox/audit failure;
- reconciliation replay.

### Gate R13-G06
```text
BILLING_STATE_SINGLE_WRITER = PASS
LIFECYCLE_SINGLE_WRITER = PASS
DUNNING_REGRESSION = PASS
TERMINAL_RESURRECTION = 0
RECONCILIATION_CLASSIFICATION = PASS
CONCURRENCY_REPLAY_SAFETY = PASS
```

---

## S7 — RBAC, API and operator UI

### Goal
Provide controlled operational access without exposing payment secrets/data.

### Capabilities
Additively seed:
- `BILLING.READ`
- `BILLING.MANAGE`
- `BILLING.RECONCILE`
- `BILLING.REFUND`
- `BILLING.PROVIDER_ADMIN`

Legacy `EXECUTIVE_BILLING` remains compatible.

### API surfaces
Additive categories:
- billing summary;
- invoice/Finance linkage;
- provider/payment state;
- reconciliation list/detail;
- explicit repair/reconcile command;
- refund command;
- provider readiness/diagnostic;
- signed webhook ingress.

### Web UI
- billing/reconciliation operational page(s);
- no PAN/CVC/payment-secret rendering;
- per-capability action gating;
- bilingual keys (Arabic/English parity);
- RTL/LTR and SDS compliance;
- explicit DISABLED/TEST/LIVE indicator with LIVE unavailable without authority.

### Gate R13-G07
```text
RBAC_DENY_BY_DEFAULT = PASS
CROSS_TENANT_API = DENIED
REFUND_SEPARATE_CAPABILITY = PASS
WEBHOOK_NO_JWT_BUT_SIGNED = PASS
UI_CAPABILITY_GATING = PASS
I18N_PARITY = PASS
SDS_LOGO_BRAND = PASS
```

---

## S8 — Compatibility and full regression

### Required suites
- R0C12 SCP acceptance/regression;
- Finance module integration;
- Commerce payment production-safety regression;
- BillingStateService/dunning;
- Subscription lifecycle convergence;
- new R0C13 PostgreSQL Direct acceptance;
- web tests/build/security/governance.

### Mandatory invariants
- R0C12 production semantics unchanged with R0C13 mode DISABLED;
- Commerce simulated provider still cannot auto-activate in production;
- existing manual invoice paid flow remains compatible or has an explicitly tested adapter path;
- no legacy endpoint removed/renamed;
- no unexplained skips.

### Gate R13-G08
```text
FULL_MAVEN = PASS
POSTGRESQL_DIRECT = PASS
R0C12_REGRESSION = PASS
FINANCE_REGRESSION = PASS
COMMERCE_REGRESSION = PASS
WEB_GATE = PASS
UNEXPLAINED_SKIPS = 0
```

---

## S9 — Security, failure injection and evidence certification

### Failure-injection matrix
At minimum:
- invalid webhook signature;
- duplicate event;
- wrong tenant mapping;
- Finance unavailable/write fails;
- provider timeout;
- provider success + local transaction failure;
- outbox write failure;
- audit failure;
- reconciliation amount/currency mismatch;
- refund unauthorized;
- LIVE mode without authority;
- secret-like values injected into logs/evidence test path.

### Evidence
Generate immutable certification artifacts containing:
- exact SHA;
- tool/runtime versions;
- PostgreSQL Direct proof;
- migration head;
- test reconciliation;
- RLS matrix;
- failure-injection results;
- provider mode proof;
- zero-secret scan;
- compatibility matrix.

### Gate R13-G09
```text
FAIL_CLOSED_MATRIX = PASS
SECURITY_SCAN = PASS
SECRET_SCAN = PASS
EVIDENCE_MANIFEST = PASS
CLAIM_STRENGTH_LE_EVIDENCE_STRENGTH = PASS
```

---

## S10 — Protected release candidate

### Requirements
- protected PR;
- independent human approval;
- all required exact-head checks green;
- PMV exact-head success;
- immutable artifact/image provenance;
- no scope drift from the approved R0C13 spec.

### Gate R13-G10
```text
PROTECTED_REVIEW = APPROVED
EXACT_HEAD_CHECKS = PASS
PMV = PASS
PROVENANCE = PASS
SCOPE_DRIFT = NONE
```

---

## S11 — Production-safe deployment with payments disabled

R0C13 engineering may be deployed only in a non-collecting state:

```text
R0C13_PROVIDER_MODE = DISABLED
LIVE_PAYMENT_COLLECTION = OFF
```

Production verification must prove:
- exact image/SHA;
- health/readiness;
- Flyway compatibility;
- new tables/RLS present;
- provider mode is DISABLED;
- no simulated/test provider active;
- webhook endpoint rejects unsigned requests;
- billing/reconciliation read surfaces healthy;
- existing subscription/Finance/Commerce smoke green;
- no live charge can be created.

### Gate R13-G11
```text
PRODUCTION_EXACT_SHA = PASS
PROVIDER_MODE = DISABLED
LIVE_CHARGE_PATH = INOPERABLE
HEALTH_READINESS = PASS
FLYWAY = PASS
SECURITY_SMOKE = PASS
R0C12_PRODUCTION_REGRESSION = PASS
```

---

## S12 — R0C13 engineering closure

### Final closure predicate

```text
R13-G01..R13-G11 = PASS
R0C13_ENGINEERING = CLOSED_PRODUCTION_VERIFIED
LIVE_PAYMENT_COLLECTION = NOT_ACTIVATED
LIVE_PAYMENT_AUTHORITY = NOT_GRANTED_BY_R0C13_CLOSURE
```

The separate live-payment activation gate is explicitly outside automatic R0C13 engineering closure.

It requires independent business/security/legal/tax/owner approval and a dedicated controlled activation/rollback execution.

## Commit / PR discipline

Use small logical commits by workstream. No production authorization marker belongs in implementation commits. Production release authority must be a separate protected governance decision after certification.

No merge is allowed merely because implementation tests are green; exact-head required checks and independent human approval remain mandatory.
