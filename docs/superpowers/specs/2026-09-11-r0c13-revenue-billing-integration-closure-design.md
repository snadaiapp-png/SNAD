# R0C13 — Revenue/Billing Integration Closure — Architecture & Scope Specification

- **Date:** 2026-09-11
- **Starting production baseline:** `16509abed344ce5d6635eb3660512e3e9011584b`
- **Authority:** Issue #1017 — owner authorization granted for R0C13 only
- **Stage:** R13-S0.2 — scope/architecture definition
- **Implementation status:** NOT STARTED
- **Live payment collection:** NOT AUTHORIZED; separate human activation gate required

## 1. Objective

R0C13 closes the deferred integration boundary between the production-verified Subscription Control Plane and the revenue/finance execution path.

The target deterministic lifecycle is:

```text
subscription
  -> billable state
  -> subscription billing invoice/projection
  -> authoritative Finance invoice
  -> provider payment intent/state
  -> signed provider event
  -> authoritative Finance payment
  -> reconciliation
  -> BillingStateService evaluation
  -> canonical subscription lifecycle transition
  -> audit/outbox evidence
```

R0C13 is an integration closure, not a Finance rewrite and not a general payment-platform rewrite.

## 2. Forensic baseline — preserve, do not replace

The current repository already contains authoritative components that R0C13 must reuse:

1. **Subscription lifecycle authority**
   - `SubscriptionCommandService.applyCanonicalTransition(...)` owns lifecycle status transitions.
   - `BillingStateService` is the sole authority for `tenant_subscriptions.billing_state`.
   - R0C7/R0C10 convergence already prevents billing/lifecycle divergence and scopes overdue invoices by `subscription_id`.

2. **Legacy subscription billing projection**
   - `billing_invoices` exists since V19.
   - It is tenant/subscription scoped and is currently consumed by dunning.
   - Existing invoice state: `DRAFT|OPEN|PAID|VOID`.
   - Existing monetary convention: integer minor units.

3. **Finance module is authoritative for accounting**
   - Existing Finance schema: `finance_accounts`, `finance_journal_entries`, `finance_journal_lines`, `finance_invoices`, `finance_invoice_lines`, `finance_payments`.
   - Finance is already the repository-designated source of truth for invoices/payments/ledgers.
   - R0C13 MUST NOT create a parallel accounting ledger or a second authoritative invoice/payment domain.

4. **Commerce payment integration is a separate bounded context**
   - `PaymentGatewayPort` exists for commerce orders and uses an `orderId` contract.
   - `DefaultNoOpPaymentAdapter` is production-safe and never auto-pays.
   - `SimulatedPaymentAdapter` is test-only and explicitly property-gated.
   - R0C13 MUST NOT reuse the commerce order port as the SaaS subscription billing contract.

5. **Existing Finance integration precedent**
   - `CommerceFinanceAdapter` demonstrates idempotent linkage into `finance_invoices`.
   - R0C13 should follow the same direction: domain-specific integration port -> Finance implementation; no duplicated finance model.

6. **Deferred items that motivate R0C13**
   - SCP explicitly deferred full billing integration, payment gateway changes, and cross-module outbox/broker integration.
   - Revenue/billing governance documents define provider test mode and explicitly keep live payment collection behind a separate owner decision.

## 3. Architectural ownership matrix

| Concern | Authoritative owner in R0C13 | Rule |
|---|---|---|
| Subscription eligibility, plan/version/items | Subscription Control Plane | Existing contracts preserved |
| Subscription lifecycle status | `SubscriptionCommandService` | No direct status writes from payment/webhook code |
| Derived billing state / dunning | `BillingStateService` | Remains sole writer of `billing_state` |
| Subscription billing projection | `billing_invoices` compatibility surface | Used for SCP/dunning compatibility; not accounting source of truth |
| Accounting invoice | Finance `finance_invoices` | Authoritative accounting invoice |
| Payment record | Finance `finance_payments` | Authoritative payment record |
| Journal / GL | Finance | R0C13 never writes journal tables directly |
| PSP/customer/payment-intent state | R0C13 Billing Provider Integration | Provider-neutral contract; provider adapter implementation |
| Webhook/event ingestion | R0C13 Billing Provider Integration | Signature verified, idempotent, auditable |
| Billing/Finance linkage | R0C13 integration link | One deterministic idempotent mapping |
| Audit | Existing platform audit + immutable billing evidence | No fail-open mutation |
| Cross-module delivery | R0C13 transactional outbox | Same transaction as canonical billing mutation |
| Commerce checkout payment | Commerce | Unchanged; no contract replacement |

## 4. In-scope work

### 4.1 Billing orchestration bounded context

Introduce a dedicated SaaS billing integration package under the Subscription/Platform boundary, conceptually:

```text
subscription/billing/
  application/
  domain/
  infrastructure/
  api/
```

It coordinates existing SCP, Finance and provider contracts. It does not own Finance bookkeeping.

### 4.2 Finance integration port

Define a subscription-specific integration port implemented on the Finance side.

Required semantics:
- idempotently create/resolve the Finance invoice for a subscription billing invoice;
- idempotently record provider settlement into Finance payment records;
- reject currency/amount/reference mismatch;
- expose reconciliation-safe identifiers;
- never post journal/GL rows by raw SQL from the subscription package;
- preserve Finance's existing state machines and tenant scoping.

A stable external reference format is required, e.g. `SCP_INVOICE:<billingInvoiceId>`, with a uniqueness invariant.

### 4.3 Provider-neutral payment port

Define a SaaS billing payment provider contract distinct from Commerce `PaymentGatewayPort`.

Money must use integer **minor units** plus ISO-4217 currency at the R0C13 domain boundary. No floating-point amounts.

Minimum provider operations:
- ensure/create provider customer reference;
- create payment intent/session for a specific subscription invoice;
- query provider payment state for reconciliation;
- request refund only through separately authorized capability;
- verify provider webhook signature/event envelope.

The first implementation target may be Stripe **test mode** because the repository already documents that path. Provider-neutral domain interfaces remain mandatory.

### 4.4 Provider event inbox

Add a durable provider event inbox with:
- provider;
- provider event id (unique per provider);
- event type;
- payload hash / sanitized reference;
- signature verification result/timestamp;
- received/processed timestamps;
- processing state;
- tenant/subscription/invoice resolution only after trusted mapping;
- error classification and retry metadata.

Raw cardholder data, API keys and webhook secrets are prohibited.

Duplicate provider events MUST be side-effect-free.

### 4.5 Transactional billing outbox

Add a producer-local transactional outbox for typed billing integration facts. Minimum event family:

```text
BILLING.INVOICE_ISSUED.v1
BILLING.PAYMENT_PENDING.v1
BILLING.PAYMENT_SUCCEEDED.v1
BILLING.PAYMENT_FAILED.v1
BILLING.REFUND_RECORDED.v1
BILLING.RECONCILIATION_EXCEPTION.v1
```

Outbox append and the canonical state mutation must commit atomically.

No fire-and-forget direct cross-module publication is an accepted source of truth.

### 4.6 Billing/Finance linkage

R0C13 may add a tenant-scoped linkage table between:
- `billing_invoices.id`
- `finance_invoices.id`
- subscription id
- stable external reference
- timestamps/version

The link must be unique and replay-safe. R0C13 must not duplicate invoice line/accounting truth into a third invoice table.

### 4.7 Reconciliation

Implement deterministic reconciliation across:
- subscription billing projection;
- Finance invoice;
- Finance payment;
- provider intent/event state.

Classifications at minimum:
- MATCHED
- MISSING_FINANCE_INVOICE
- MISSING_PROVIDER_REFERENCE
- AMOUNT_MISMATCH
- CURRENCY_MISMATCH
- PROVIDER_PAID_FINANCE_PENDING
- FINANCE_PAID_PROVIDER_UNCONFIRMED
- DUPLICATE_PROVIDER_EVENT
- TENANT_BINDING_MISMATCH
- SIGNATURE_REJECTED

Reconciliation must be read-only by default. Repair actions require an explicit command/capability and audit record; no silent repair.

### 4.8 Billing state convergence

Provider success/failure must never write subscription lifecycle columns directly.

Required path:

```text
provider event
 -> Finance payment/invoice convergence
 -> billing_invoices compatibility convergence
 -> BillingStateService.evaluateAndTransition(tenant)
 -> SubscriptionCommandService canonical lifecycle transition
```

Existing dunning timing and semantics remain unchanged unless a separately proven defect requires a scoped correction.

### 4.9 API and operator surfaces

Additive APIs only. Exact routes may be finalized during implementation, but the contract categories are fixed:

- billing summary by subscription;
- billing invoice/Finance linkage status;
- payment/provider state;
- reconciliation list/detail;
- explicit reconciliation/repair command;
- provider webhook endpoint;
- refund command (capability-gated, disabled unless provider mode permits);
- activation/readiness diagnostics that never expose secrets.

Executive UI may expose read/reconciliation surfaces. It must not collect or render raw card details.

### 4.10 Configuration and deployment safety

Required configuration states:

```text
DISABLED   = no provider calls; production-safe default
TEST       = provider sandbox/test-mode only
LIVE       = real payment collection; forbidden without separate human activation
```

Production deployment of R0C13 engineering is allowed only with mode `DISABLED` unless a later explicit live-payment authorization exists.

A test/simulated adapter must never become active through `matchIfMissing=true`.

## 5. Data model boundaries

R0C13 may add only additive, forward-only migrations.

Expected logical additions (names may be normalized during implementation):
- provider customer binding;
- provider payment attempt/intent state;
- provider event inbox;
- billing-to-Finance invoice link;
- billing transactional outbox;
- reconciliation run/item evidence.

Hard rules:
- every tenant-scoped new table: `tenant_id NOT NULL`;
- ENABLE RLS + FORCE RLS for new R0C13 tenant-scoped tables;
- same-tenant composite FK where cross-table tenant identity is material;
- idempotency/unique constraints are database-enforced;
- no destructive drop/rename of R0C12 production columns;
- no Flyway repair/history mutation/out-of-order bypass;
- no cardholder data columns;
- no plaintext secret columns.

## 6. Security / authorization invariants

1. Existing `EXECUTIVE_BILLING` compatibility remains intact.
2. R0C13 introduces granular capabilities only additively, at minimum:
   - `BILLING.READ`
   - `BILLING.MANAGE`
   - `BILLING.RECONCILE`
   - `BILLING.REFUND`
   - `BILLING.PROVIDER_ADMIN`
3. Mutating operator endpoints require both control-plane boundary and explicit capability.
4. Webhook endpoint is not authenticated by tenant JWT; instead it MUST:
   - validate provider signature first;
   - resolve tenant through trusted stored provider binding;
   - reject unknown/unbound references;
   - ignore any untrusted caller-supplied tenant mapping.
5. Invalid signature => zero domain side effects.
6. Cross-tenant read/write attempts => denied under PostgreSQL Direct tests.
7. No secret value may be written to repository, issue, PR, logs, evidence artifacts or API responses.
8. No card number/CVC/full payment-method data stored by SNAD.
9. Refunds require a dedicated capability and audit evidence; no automatic refund on arbitrary failure.

## 7. Backward compatibility

R0C13 must preserve:
- current SCP routes and DTOs;
- `billing_invoices` identifiers used by dunning;
- `BillingStateService` authority and subscription-scoped overdue logic;
- `SubscriptionCommandService` single-writer lifecycle model;
- Commerce `PaymentGatewayPort`, `DefaultNoOpPaymentAdapter`, `SimulatedPaymentAdapter`;
- Finance public/domain contracts unless changes are additive;
- existing production behavior when R0C13 provider mode is DISABLED.

Compatibility rule:

```text
R0C13_MODE=DISABLED
=> pre-R0C13 externally observable subscription behavior remains unchanged
   except additive read/evidence surfaces.
```

## 8. Explicit non-goals

R0C13 does NOT include:
- live charging of real customers without a separate activation approval;
- storing cardholder data;
- replacing Finance or creating a second GL;
- replacing Commerce payment integration;
- deleting legacy `billing_invoices`;
- destructive migration of existing subscription/finance rows;
- a generic enterprise event broker;
- tax/legal certification or final ZATCA compliance claims;
- arbitrary tax-rate hardcoding as legal truth;
- customer portal redesign unrelated to billing integration;
- changes to unrelated CRM/ERP/HRM/POS domains;
- direct manual production SQL or Flyway history mutation.

## 9. Failure semantics

All financially material operations are fail-closed.

Examples:
- Finance invoice linkage fails => provider intent MUST NOT be treated as billable success.
- Provider signature invalid => event rejected, no invoice/payment/status mutation.
- Finance settlement write fails => provider event remains retryable; subscription not activated from that event.
- Amount/currency mismatch => reconciliation exception; no auto-convergence.
- Audit/outbox atomic append fails on a required mutation => transaction rolls back.
- Cross-tenant mapping mismatch => reject and record security evidence.
- LIVE mode without activation authority => application startup or provider bean activation must fail closed.

## 10. Live-payment activation boundary

R0C13 engineering closure does not equal live-payment activation.

A future activation gate must independently prove:
- commercial approval;
- legal/tax review or explicit risk acceptance;
- provider account ownership;
- production secret storage;
- exact live product/price mapping;
- signature validation;
- refund/cancellation policy;
- rollback/deactivation runbook;
- first-customer invoice/tax review;
- owner explicit live-payment approval.

Until then:

```text
LIVE_PAYMENT_COLLECTION = FORBIDDEN
R0C13_PRODUCTION_MODE = DISABLED
```

## 11. R13-S0.2 completion predicate

R13-S0.2 is complete when:
- this architecture/scope specification is protected-reviewed;
- the implementation plan exists;
- the acceptance matrix exists;
- all three are bound to starting baseline `16509abed344ce5d6635eb3660512e3e9011584b`;
- no implementation code/migration/runtime configuration has been changed by the scope-definition PR.

Only after R13-S0 closes may R0C13 implementation start.
