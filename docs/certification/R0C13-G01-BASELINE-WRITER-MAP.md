# R0C13 G01 — Exact Baseline Lock, Writer Map, and RED Contract Evidence

- **Gate:** R13-G01
- **Repository:** snadaiapp-png/SNAD
- **Protected main SHA:** `43eeffcbd668fb27c48b561284c0e6d91d273a3f`
- **R13-S0 scope PR:** #1018 — merged
- **R13-S0 merge SHA:** `43eeffcbd668fb27c48b561284c0e6d91d273a3f`
- **Production baseline inherited from R0C12:** `16509abed344ce5d6635eb3660512e3e9011584b`
- **Pre-R0C13 repository Flyway head:** `20260910.1`
- **Application behavior changes in this G01 branch:** NONE
- **Production mutation:** NONE
- **Live payment collection:** NOT AUTHORIZED

## 1. Baseline lock

R13-G01 starts from the exact protected-main tree produced by the approved R13-S0 scope merge:

```text
R13_G01_BASELINE_SHA=43eeffcbd668fb27c48b561284c0e6d91d273a3f
R13_S0_MERGED=YES
BASELINE_LOCK=PASS
```

Any protected-main drift before G02 implementation requires explicit re-anchor.

## 2. Current production writer map

The inventory below is source-locked to `src/main/java` on the G01 baseline.

### 2.1 `billing_invoices`

**Runtime writers**
- `com.sanad.platform.admin.service.SaasAdministrationService`
  - INSERTs billing invoices.
  - UPDATEs invoice settlement to `PAID`, `amount_paid_minor=total_minor`, `paid_at`, and `payment_reference`.

**Runtime readers**
- `BillingStateService` — counts overdue OPEN invoices by **subscription_id** for dunning/billing-state convergence.
- `SubscriptionDetailService` — subscription invoice read model.
- `SaasAdministrationService` — control-plane invoice reads.
- `HealthIntelligenceService` — aggregate health metrics only.

**Classification**
```text
billing_invoices = SCP/DUNNING COMPATIBILITY PROJECTION
ACCOUNTING_SOURCE_OF_TRUTH = NO
```

R0C13 must preserve these identifiers/semantics while linking each governed billing invoice idempotently to Finance.

### 2.2 `tenant_subscriptions.status`

**Initial row writer**
- `SaasAdministrationService` INSERTs a new subscription row with its initial lifecycle status.

**Canonical transition writer**
- `SubscriptionCommandService` owns runtime lifecycle status transitions via `UPDATE tenant_subscriptions SET status ... WHERE id=? AND status=?`.

**Classification**
```text
INITIALIZATION = SaasAdministrationService
RUNTIME_LIFECYCLE_TRANSITIONS = SubscriptionCommandService ONLY
```

R0C13 provider/webhook/reconciliation code must never write lifecycle status directly.

### 2.3 `tenant_subscriptions.billing_state`

**Runtime writer**
- `BillingStateService` is the verified production writer of direct `UPDATE tenant_subscriptions SET billing_state...`.

Historical Flyway migrations contain backfill SQL but are not runtime writers.

**Classification**
```text
BILLING_STATE_RUNTIME_WRITER = BillingStateService ONLY
```

R0C13 payment/provider code must converge through `BillingStateService.evaluateAndTransition(...)`.

### 2.4 `finance_invoices`

**Finance authoritative repository writer**
- `JdbcFinanceInvoiceRepository.save(...)` persists/upserts Finance invoice state.

**Existing cross-module writer**
- `CommerceFinanceAdapter` directly INSERTs a Finance invoice for Commerce using a stable external reference.

**Classification**
```text
FINANCE_INVOICE_ACCOUNTING_AUTHORITY = Finance module
COMMERCE_DIRECT_ADAPTER = EXISTING BOUNDED-CONTEXT PRECEDENT
R0C13_DIRECT_SQL_TO_FINANCE = FORBIDDEN
```

R0C13 must introduce a subscription-specific Finance integration port implemented on the Finance side rather than copy the Commerce SQL pattern into subscription billing.

### 2.5 `finance_payments`

**Authoritative writer**
- `JdbcFinancePaymentRepository.save(...)` INSERTs/upserts Finance payment records.

No R0C13 code exists yet.

**Classification**
```text
FINANCE_PAYMENT_AUTHORITY = Finance module
R0C13_PAYMENT_SETTLEMENT = MUST USE FINANCE INTEGRATION PORT
```

### 2.6 Commerce payment boundary

Current Commerce payment contract:
- `PaymentGatewayPort` is order-specific and uses `BigDecimal` major-unit amounts.
- `DefaultNoOpPaymentAdapter` is the production-safe default and never auto-verifies.
- `SimulatedPaymentAdapter` is gated with:
  `havingValue="simulated", matchIfMissing=false`.
- `application-prod.yml` leaves `SANAD_COMMERCE_PAYMENT_PROVIDER` unset by default.

**Classification**
```text
COMMERCE_PAYMENT_PORT = OUTSIDE R0C13 CONTRACT
SIMULATED_ACTIVE_BY_DEFAULT = NO
R0C13_PROVIDER_PORT = DISTINCT CONTRACT REQUIRED
R0C13_MONEY = INTEGER MINOR UNITS REQUIRED
```

## 3. G01 architecture guard contracts

G01 adds test-only guardrails that must remain green throughout R0C13 implementation:

1. Production source under the future `subscription/billing` bounded context must not contain direct SQL writes to:
   - `finance_invoices`
   - `finance_payments`
   - `finance_journal_entries`
   - `finance_journal_lines`
2. The R0C13 billing package must not contain direct SQL lifecycle writes:
   - `UPDATE tenant_subscriptions SET status`
   - `UPDATE tenant_subscriptions SET billing_state`
3. The R0C13 billing package must not import/reuse Commerce `PaymentGatewayPort`.
4. Provider-domain source must not use `BigDecimal` for provider charge/refund amounts.
5. Existing Commerce simulated adapter must remain `matchIfMissing=false`.
6. Production Commerce provider must not default to `simulated`.

These are invariants, not implementation of the feature.

## 4. RED gap contract

A separate G01 RED test asserts that the approved R0C13 production bounded context is present.

At the exact baseline it is intentionally absent:

```text
src/main/java/com/sanad/platform/subscription/billing = ABSENT
```

Therefore the RED test must fail before implementation.

This proves the implementation gap without modifying application behavior.

Expected RED:
```text
R0C13_BILLING_BOUNDED_CONTEXT_PRESENT = FAIL
reason = approved R0C13 production package does not exist yet
```

G02/G03 implementation will turn this contract GREEN by introducing the approved package and interfaces; architecture guards must stay green.

## 5. G01 gate predicate

```text
BASELINE_LOCK = PASS
CURRENT_WRITER_MAP = COMPLETE_FOR_R0C13_TARGETED_AUTHORITIES
ARCHITECTURE_GUARDS = ADDED
RED_CONTRACTS = PENDING_EXECUTION
APPLICATION_BEHAVIOR_CHANGE = 0
PRODUCTION_MUTATION = 0
LIVE_PAYMENT_COLLECTION = NOT_AUTHORIZED
```

R13-G01 becomes PASS only after the RED test is executed on this exact branch/head and the expected failure is captured as evidence.
