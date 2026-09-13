# R0C13 R13-G07 — RBAC, Operator API & Billing Operations UI Design

- **Date:** 2026-09-13
- **Repository:** `snadaiapp-png/SNAD`
- **Authority:** Issue #1017 + R0C13 architecture/specification + R0C13 implementation plan
- **Re-anchored baseline:** `a7fd5c0428ea6c774ff52e564cd791f9d1faf3a3`
- **Prior certified merge:** PR #1019 / `3507e23cb2e5baeb9e5fa64782bcf03936276a6c`
- **Stage:** R13-G07 design
- **Execution status:** DESIGN ONLY — implementation has not started
- **Database acceptance:** PostgreSQL Direct / host-native only
- **Live payment collection:** FORBIDDEN; separate human activation gate required

## 1. Re-anchor and scope integrity

Protected `main` advanced by one commit after PR #1019 merged:

`3507e23cb2e5baeb9e5fa64782bcf03936276a6c`
→
`a7fd5c0428ea6c774ff52e564cd791f9d1faf3a3`

The drift changes subscription read models, subscription web pages, their tests, `scp-api.ts`, and one Arabic/English i18n key pair. It does not modify the R0C13 Billing/Finance/provider bounded context, provider mode guard, billing migrations, billing acceptance matrix, or the R13-G07 source definition.

Classification:

```text
G07_REANCHOR_BASELINE = a7fd5c0428ea6c774ff52e564cd791f9d1faf3a3
R0C13_SCOPE_CONFLICT = NONE_FOUND
UI_I18N_OVERLAP = YES
REQUIRED_RESPONSE = REGRESSION_TEST_DURING_G07
PRODUCTION_MUTATION = 0
LIVE_PAYMENT_COLLECTION = FORBIDDEN
```

The G07 branch must remain anchored to this exact SHA unless protected `main` moves again, in which case the same fail-closed drift review is repeated before implementation continues.

## 2. Goal

R13-G07 exposes the already-approved R0C13 billing/reconciliation capabilities to authorized control-plane operators without weakening tenant isolation, Finance authority, billing/lifecycle single-writer rules, provider safety, or legacy Executive behavior.

The work is executed as four internal **workstreams**, not new repository gates:

1. RBAC / capabilities
2. Operator API
3. Billing Operations UI
4. Integrated G07 acceptance

These names do not create `G07.1`, `G07.2`, or any other new formal gate.

## 3. Security model

### 3.1 New granular capabilities

Seed exactly these additive ACTIVE capabilities:

- `BILLING.READ`
- `BILLING.MANAGE`
- `BILLING.RECONCILE`
- `BILLING.REFUND`
- `BILLING.PROVIDER_ADMIN`

Canonical backend authority remains `CapabilityEvaluationService` through the existing `@RequireCapability` aspect.

Every new control-plane billing endpoint also calls `ControlPlaneAccessGuard.require(authentication)`.

### 3.2 Legacy compatibility without privilege expansion

Existing endpoints preserve their current authority, including `EXECUTIVE_VIEW`, `EXECUTIVE_MANAGE`, and `EXECUTIVE_BILLING` where already used.

No broad legacy capability implies a new `BILLING.*` capability.

Forbidden:

```text
EXECUTIVE_VIEW    -> BILLING.* implicit grant
EXECUTIVE_MANAGE  -> BILLING.* implicit grant
EXECUTIVE_BILLING -> BILLING.* implicit grant
```

Existing callers therefore remain compatible, while sensitive G07 surfaces require explicit new authority.

### 3.3 Initial grants

The migration grants all five new capabilities only to the active control-plane `ADMIN` role.

No other role receives an automatic grant. In particular, there is zero mirroring from legacy Executive capabilities.

Future non-ADMIN grants must use normal RBAC governance rather than migration-time inference.

### 3.4 AccessCheckV2

Extend the existing `ControlPlaneAccessService.CONTROL_PLANE_CAPABILITIES` / `GET /api/v1/executive/access-check/v2` result with the five `BILLING.*` codes.

Do not create a second authorization/readiness service for the web application.

The UI may use these booleans to hide or disable controls, but UI gating is never an authorization boundary. Backend exact-capability enforcement remains authoritative and must return 403 on missing authority.

## 4. Operator API namespace

All new operator APIs use an explicit tenant path:

```text
/api/v1/executive/tenants/{tenantId}/billing/...
```

This makes tenant selection explicit in routing, audit evidence, and cross-tenant denial tests.

The request-path `tenantId` is never trusted as sufficient authority. The caller must pass the control-plane guard, exact capability check, and the application service must execute tenant-scoped PostgreSQL/RLS access.

The existing signed webhook ingress remains separate:

```text
/api/v1/billing/provider/webhook
```

It remains JWT-independent and continues to authenticate provider events through signature verification plus trusted stored provider binding.

## 5. API surface

### 5.1 Read surfaces — `BILLING.READ`

Approved additive read categories are exposed under the tenant billing namespace:

- billing summary by subscription;
- billing invoice and Finance linkage state;
- provider/payment state;
- reconciliation run list/detail and item detail.

Concrete route shape:

```text
GET /api/v1/executive/tenants/{tenantId}/billing/summary
GET /api/v1/executive/tenants/{tenantId}/billing/invoices
GET /api/v1/executive/tenants/{tenantId}/billing/invoices/{invoiceId}
GET /api/v1/executive/tenants/{tenantId}/billing/payments
GET /api/v1/executive/tenants/{tenantId}/billing/payments/{paymentAttemptId}
GET /api/v1/executive/tenants/{tenantId}/billing/reconciliation-runs
GET /api/v1/executive/tenants/{tenantId}/billing/reconciliation-runs/{runId}
```

Read DTOs expose operational identifiers and sanitized provider state only. They never expose secrets, webhook signatures, raw payloads, PAN, CVC, API keys, or credential metadata.

### 5.2 General billing mutation — `BILLING.MANAGE`

`BILLING.MANAGE` is the exact authority for any new general billing mutation admitted by the already-approved R0C13 scope.

G07 does **not** invent a general mutation merely to consume this capability. Refund, reconciliation/repair, and provider diagnostics use their dedicated capabilities instead. Architecture tests must prevent a future new general billing mutation from appearing without `BILLING.MANAGE`.

Legacy commands remain on their existing authority and are not silently migrated.

## 6. Reconciliation and the only repair allowed in G07

### 6.1 Reconciliation run

```text
POST /api/v1/executive/tenants/{tenantId}/billing/reconciliation-runs
Capability: BILLING.RECONCILE
```

The command delegates to `BillingReconciliationService.reconcileReadOnly(...)`.

Required behavior:

- tenant-scoped;
- idempotent by tenant + idempotency key;
- classification/evidence only;
- no provider mutation;
- no Finance mutation;
- no SCP billing/lifecycle repair;
- auditable.

### 6.2 Repair policy

G07 permits exactly one repair classification:

```text
PROVIDER_PAID_FINANCE_PENDING
```

The repair command is explicit and classification-bound. Recommended route:

```text
POST /api/v1/executive/tenants/{tenantId}/billing/reconciliation-items/{itemId}/converge-provider-paid
Capability: BILLING.RECONCILE
```

Preconditions:

1. reconciliation item belongs to `tenantId`;
2. item classification is exactly `PROVIDER_PAID_FINANCE_PENDING`;
3. referenced payment attempt/invoice/binding belong to the same tenant;
4. provider state is queried from the configured provider;
5. provider truth is still `SUCCEEDED`;
6. amount and ISO currency match stored attempt/invoice truth;
7. command idempotency key is valid and replay-safe.

Only after those checks may the application command reuse the canonical settlement convergence path: Finance authoritative settlement first, billing projection convergence, then `BillingStateService`.

It must not fabricate a webhook and must not update Finance, billing projection, or subscription lifecycle with direct controller SQL.

All other reconciliation discrepancies remain report-only in G07:

- `AMOUNT_MISMATCH`
- `CURRENCY_MISMATCH`
- `MISSING_PROVIDER_REFERENCE`
- `PROVIDER_UNAVAILABLE`
- `FINANCE_PAID_PROVIDER_UNCONFIRMED`
- any unknown/future classification unless separately designed and approved

A rejected repair produces zero financial/domain side effects and records sanitized audit evidence.

## 7. Refund command

### 7.1 Authority

```text
POST /api/v1/executive/tenants/{tenantId}/billing/payments/{paymentAttemptId}/refunds
Capability: BILLING.REFUND
```

Refund authority is separate from `BILLING.MANAGE`.

### 7.2 Scope

G07 supports full refunds only.

Partial refunds remain out of scope because the current Finance/R0C13 accounting model has no approved partial-refund/credit-note semantics.

### 7.3 Provider-confirmed convergence

Refund sequence:

```text
operator command
 -> ControlPlaneAccessGuard
 -> BILLING.REFUND
 -> tenant/RLS ownership checks
 -> load SUCCEEDED payment attempt
 -> full amount/currency verification
 -> provider.requestRefund(deterministic idempotency key)
 -> inspect provider result
```

Rules:

- provider `FAILED` => fail closed; no Finance/local refund mutation;
- provider `ACCEPTED` => request is pending/accepted only; Finance/local state stays unchanged;
- provider `SUCCEEDED` => response alone is not treated as sufficient accounting truth; provider state must independently confirm `REFUNDED`, or convergence waits for a cryptographically verified `payment.refunded` webhook;
- final local convergence uses the existing Finance-owned refund authority and the canonical R0C13 settlement/refund path;
- controller code never writes Finance tables directly;
- replay is idempotent;
- a never-succeeded attempt cannot be refunded.

Invariant:

```text
REFUND_REQUEST_ACCEPTED != FINANCE_REFUNDED
```

## 8. Provider readiness diagnostics

### 8.1 Route and authority

```text
GET /api/v1/executive/tenants/{tenantId}/billing/provider-readiness
Capability: BILLING.PROVIDER_ADMIN
```

### 8.2 Passive-only design

Diagnostics are zero-side-effect.

They must not:

- create a provider customer;
- create a payment intent;
- query an arbitrary external resource merely as a health probe;
- request a refund;
- send a webhook;
- expose any secret/config value.

Permitted sanitized fields include:

```text
mode                    = DISABLED | TEST
providerCode            = sanitized identifier
providerOperationsReady = boolean
webhookConfigPresent    = boolean
customerBindingPresent  = boolean
paymentBindingCount     = number
lastVerifiedEventAt     = timestamp|null
lastReconciliationAt    = timestamp|null
liveModeAvailable       = false
livePaymentAuthorized   = false
```

`DISABLED` is reported as healthy-but-disabled rather than an application error. It is the production-safe engineering state.

`LIVE` is not an available G07 operating mode. `BillingProviderModeGuard` continues to reject LIVE startup until a separate human activation gate exists.

## 9. Billing Operations web workspace

### 9.1 Routing

Use dedicated routed pages rather than returning to the legacy single-page/tab console.

```text
/executive/billing
/executive/tenants/{tenantId}/billing
```

`/executive/billing` is the Billing Operations landing page and tenant selector.

`/executive/tenants/{tenantId}/billing` is the tenant-specific operational workspace.

The existing billing page and legacy invoice route remain backward compatible; G07 must not remove or rename them.

### 9.2 Workspace sections

Tenant billing workspace exposes:

- Summary
- Invoices & Finance Link
- Payments
- Reconciliation
- Provider Readiness

Sensitive controls appear only when the AccessCheckV2 capability is true:

- reconcile / safe convergence: `BILLING.RECONCILE`
- refund: `BILLING.REFUND`
- provider diagnostics/admin: `BILLING.PROVIDER_ADMIN`
- any approved general billing mutation: `BILLING.MANAGE`

Backend authorization remains mandatory even when the control is hidden.

### 9.3 UI governance

All new UI must:

- use existing SDS primitives where applicable;
- use `useI18n().t`;
- add Arabic and English keys in parity;
- preserve RTL/LTR behavior;
- use existing `ExecutiveShell` / SNAD logo governance rather than importing brand assets directly;
- expose no PAN, CVC, payment secret, webhook secret, API key, raw provider payload, or credential value;
- show provider mode explicitly;
- show LIVE as unavailable/not authorized;
- provide deterministic loading, empty, denied, error, and retry states.

The re-anchor commit changed subscription pages and one Arabic/English key pair, so G07 web validation must include regression against those current-main changes.

## 10. Error and HTTP semantics

Minimum fail-closed semantics:

- unauthenticated => existing security-chain response;
- caller outside control-plane boundary => denied;
- missing exact capability => 403;
- path tenant not authorized/resolvable => denied without cross-tenant disclosure;
- malformed identifiers/body => 400-class response;
- resource missing within authorized tenant => 404-class response;
- idempotency conflict/mismatch => 409;
- unsupported repair classification => 409/422 with zero side effects;
- provider DISABLED for an operation that requires provider IO => 503-class operational-unavailable response;
- provider refund ACCEPTED => success response representing pending acceptance, not refunded accounting state;
- provider/Finance mismatch => fail closed;
- audit/outbox failure on required mutation => transaction rollback.

Responses and logs must remain sanitized.

## 11. Audit requirements

Every mutating G07 command records enough immutable audit evidence to answer:

- actor;
- tenant;
- capability exercised;
- action;
- target resource;
- idempotency key/reference when safe;
- result classification;
- timestamp.

Never store raw provider payloads, signatures, credentials, PAN, CVC, or secrets in audit metadata.

Required audited commands:

- reconciliation run request;
- provider-paid convergence repair;
- refund request and final result transition.

Authorization denials continue to use the existing audited capability aspect.

## 12. Database and tenant-isolation rules

G07 may add only forward-only migrations.

The RBAC capability seed migration is additive and idempotent.

Any new tenant-scoped table/column introduced only if necessary for approved command idempotency/audit must follow existing R0C13 rules:

- `tenant_id NOT NULL`;
- ENABLE RLS + FORCE RLS;
- tenant-safe composite relationships where material;
- database-enforced uniqueness/idempotency;
- no Flyway repair;
- no history mutation;
- no out-of-order execution;
- no destructive R0C12/R0C13 schema change.

PostgreSQL Direct / host-native is the authoritative acceptance environment. Docker/Testcontainers are forbidden for governed DB acceptance.

## 13. Verification strategy

Implementation follows TDD and sequential workstreams.

### Workstream A — RBAC

RED first, then GREEN for:

- all five capabilities seeded ACTIVE;
- control-plane ADMIN receives exactly the approved initial grants;
- no automatic legacy mirroring;
- AccessCheckV2 reports all five codes;
- deny-by-default when not granted;
- legacy Executive behavior remains intact.

### Workstream B — API

RED first for:

- exact capability per endpoint;
- ControlPlaneAccessGuard on every new operator endpoint;
- explicit tenant path;
- cross-tenant denial;
- reconciliation read-only semantics;
- single allowed repair classification;
- repair replay/idempotency;
- refund separation;
- `ACCEPTED != REFUNDED`;
- partial refund rejected;
- provider-disabled operation failure;
- passive readiness;
- no secret fields.

PostgreSQL tests use the real host-native migration chain and least-privilege application role.

### Workstream C — Web

Tests prove:

- routed Billing Operations workspace;
- capability-based control rendering;
- backend errors handled safely;
- Arabic/English key parity;
- RTL/LTR behavior;
- SDS/logo governance;
- no payment-secret/card fields;
- LIVE unavailable;
- current-main subscription/i18n regression remains green.

### Workstream D — Integrated acceptance

R13-G07 closes only when the repository-defined predicate is proven on one exact head:

```text
RBAC_DENY_BY_DEFAULT      = PASS
CROSS_TENANT_API          = DENIED
REFUND_SEPARATE_CAPABILITY= PASS
WEBHOOK_NO_JWT_BUT_SIGNED = PASS
UI_CAPABILITY_GATING      = PASS
I18N_PARITY               = PASS
SDS_LOGO_BRAND            = PASS
```

Acceptance-matrix rows attributable to G07 are updated only from direct evidence. Shared G08/G09/G10/G11 predicates are not promoted early.

## 14. Explicit non-goals

G07 does not authorize or implement:

- LIVE payment activation;
- production secret provisioning;
- direct production mutation;
- partial refunds;
- generic reconciliation repair;
- silent repair;
- Finance rewrite or direct SQL from controller/billing package;
- lifecycle direct writes;
- Commerce payment contract replacement;
- legacy Executive endpoint removal/renaming;
- tax/legal/ZATCA certification;
- unrelated subscription/CRM/ERP/HRM/POS refactors;
- a new formal `G07.1`/`G07.2` gate hierarchy.

## 15. Design completion predicate

This G07 design is ready for implementation planning when:

- the re-anchored baseline remains exact or is re-audited if main moves;
- no placeholder/TBD remains;
- every approved RBAC/API/UI/refund/repair/readiness decision is represented;
- no LIVE-payment authority is introduced;
- user reviews and approves this written spec.

Until that approval:

```text
R13-G07_DESIGN = WRITTEN
R13-G07_IMPLEMENTATION_PLAN = NOT_STARTED
R13-G07_CODE_IMPLEMENTATION = NOT_STARTED
PRODUCTION_MUTATION = 0
LIVE_PAYMENT_COLLECTION = FORBIDDEN
```
