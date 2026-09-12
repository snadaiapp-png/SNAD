# R0C13 R13-G06 — Settlement, Reconciliation, Dunning & Lifecycle Convergence Design

- **Date:** 2026-09-12
- **Repository:** `snadaiapp-png/SNAD`
- **Authority:** Issue #1017 and the R0C13 implementation plan
- **Entry gate:** `R13-G05 = FINAL_CLOSED`
- **Scope decision:** backend/domain only
- **Deferred:** API, operator UI, RBAC/capability surfaces -> R13-G07
- **Database acceptance:** PostgreSQL Direct / host-native only
- **Live payment collection:** FORBIDDEN

## 1. Goal

Converge cryptographically verified provider payment state, Finance accounting truth, the SCP billing invoice projection, and the canonical subscription lifecycle without introducing a second writer for either `tenant_subscriptions.status` or `tenant_subscriptions.billing_state`.

## 2. Authoritative boundaries

- Finance remains the accounting source of truth for invoices and payments.
- `SubscriptionFinancePort.recordSettlement(...)` is the only R0C13 settlement path into Finance.
- `BillingStateService` remains the sole billing-state authority.
- `SubscriptionCommandService` remains the canonical lifecycle-status writer.
- Existing dunning logic retains ownership of PAST_DUE/SUSPENDED timing.
- Commerce `PaymentGatewayPort` remains unchanged and is not reused.
- G06 introduces no public API, UI, capability, refund surface, provider-admin surface, or LIVE provider authority.

## 3. Settlement flow

For a verified provider success:

```text
verified provider event
 -> trusted stored provider-payment binding
 -> validate tenant / subscription / billing invoice
 -> validate amount and ISO currency
 -> SubscriptionFinancePort.recordSettlement(...)
 -> authoritative Finance payment COMPLETED
 -> mark SCP billing invoice projection PAID
 -> BillingStateService.evaluateAndTransition(tenant)
 -> canonical PAYMENT_RECEIVED only when legal
 -> audit/outbox evidence within the required transaction boundary
```

The settlement operation is idempotent. Replays and concurrent duplicate success events must create at most one Finance payment and must never duplicate lifecycle effects.

## 4. Failure flow

Provider failure records provider/payment-attempt evidence only. It does not mark the invoice paid and does not write subscription lifecycle state.

If provider success is verified but Finance or required local evidence fails, the mutation rolls back so the event remains safely retryable.

Amount, currency, reference, tenant, or invoice mismatches fail closed before Finance/lifecycle mutation.

## 5. Terminal-state safety

A late provider success must never resurrect `CANCELLED`, `EXPIRED`, or `TERMINATED` history. Historical-subscription invoices cannot dunn or reactivate a successor subscription. Lifecycle legality remains enforced by the existing canonical command authority.

## 6. Reconciliation

`BillingReconciliationService` is read-only with respect to provider state, Finance truth, SCP billing projection, and subscription lifecycle. It may write only tenant-scoped reconciliation run/item evidence.

Required classifications:

- `MATCHED`
- `MISSING_FINANCE_INVOICE`
- `MISSING_PROVIDER_REFERENCE`
- `AMOUNT_MISMATCH`
- `CURRENCY_MISMATCH`
- `PROVIDER_PAID_FINANCE_PENDING`
- `FINANCE_PAID_PROVIDER_UNCONFIRMED`

Reconciliation replay is idempotent by tenant + idempotency key. G06 does not implement repair commands; capability-gated repair belongs to G07.

## 7. Concurrency and transaction rules

Mandatory evidence covers:

- concurrent duplicate success webhook;
- webhook + reconciliation race;
- Finance failure after provider success;
- outbox/audit failure;
- reconciliation replay;
- payment success after cancellation;
- historical/terminal resurrection attempts;
- amount mismatch;
- currency mismatch.

No exception swallowing is permitted across the settlement convergence boundary. No G06 code may directly update `tenant_subscriptions.status` or `tenant_subscriptions.billing_state`.

## 8. Migration/re-anchor rule

The protected main moved during G06 and introduced Flyway `20260911.1..3`. R0C13 migrations were not merged to protected main, so their repository identities are re-anchored without changing migration semantics to:

- `V20260912_1__r0c13_subscription_billing_foundation.sql`
- `V20260912_2__r0c13_verified_webhook_resolution.sql`

All Flyway sentinels must assert the exact combined sequence. No `flyway repair`, out-of-order execution, history mutation, or production DDL is authorized.

## 9. Acceptance

```text
BILLING_STATE_SINGLE_WRITER   = PASS
LIFECYCLE_SINGLE_WRITER       = PASS
DUNNING_REGRESSION            = PASS
TERMINAL_RESURRECTION         = 0
RECONCILIATION_CLASSIFICATION = PASS
CONCURRENCY_REPLAY_SAFETY     = PASS
```

G07 remains blocked until every G06 predicate is proven on one exact re-anchored head with zero unexpected failures/errors and acceptance-matrix evidence is updated row-by-row.
