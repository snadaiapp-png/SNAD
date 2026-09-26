# Tenant–Subscription–Billing Convergence Design

Date: 2026-09-23
Status: DRAFT FOR OWNER REVIEW
Branch: `fix/tenant-subscription-billing-convergence`
Scope: Executive tenant management, subscription lifecycle, billing projection, Finance linkage, access gating, and production diagnostics

## 1. Objective

Eliminate the current class of state-divergence defects between tenant lifecycle, effective subscription lifecycle, billing state, and Finance-linked billing evidence, while preserving tenant isolation, existing R0C13 accounting boundaries, canonical lifecycle writers, auditability, idempotency, and fail-closed release governance.

The target operational invariant is:

> A tenant's commercial access decision, subscription action, and billing presentation must be derived from one canonical effective-subscription resolution path and must never depend on an arbitrary historical subscription row or UI-only status heuristic.

This design does **not** authorize live payment collection, payment-provider activation, or arbitrary repair of production commercial data.

## 2. Problem Statement

The repository currently has multiple partially overlapping authorities:

1. `ExecutivePlatformService` provisions executive tenants but does not create the commercial subscription.
2. `AdminPlatformService` contains a separate tenant-provisioning flow that attempts automatic subscription creation.
3. Tenant status and subscription status are mutated by different services without a single cross-domain access decision.
4. `TenantDirectoryQueryService` derives displayed subscription state from the latest historical row, not the canonical effective subscription.
5. Tenant login eligibility is partially decided by frontend heuristics and partially by backend tenant status only.
6. The upgrade journey assumes an existing subscription and dead-ends for tenants without one.
7. The subscription grid exposes a monthly-equivalent value under a generic amount label for annual subscriptions.
8. The Executive billing page exposes only the SCP billing projection and omits Finance/reconciliation context needed to distinguish operational billing evidence from accounting truth.

These defects allow observable contradictions such as:

- `tenant.status = ACTIVE` with no effective subscription;
- `tenant.status = ACTIVE` with a terminal latest subscription;
- login-link availability without a valid effective subscription;
- upgrade intent with no executable commercial action;
- billing display that can be interpreted as accounting truth although `billing_invoices` is only the SCP compatibility/dunning projection.

## 3. Design Principles

### 3.1 Canonical authority, not mirrored state

`tenant.status` remains an account/operational state. It will not become a mirror of `tenant_subscriptions.status`.

Commercial access is derived by a dedicated decision service using both tenant state and the canonical effective subscription.

### 3.2 Preserve existing single writers

The existing authorities remain authoritative:

- `SubscriptionCommandService` remains the lifecycle status writer for subscription lifecycle transitions.
- `BillingStateService` remains the `billing_state` writer.
- Finance remains authoritative for accounting invoices/payments/ledger.
- `billing_invoices` remains an SCP compatibility/dunning projection and must not become a second accounting source of truth.

### 3.3 Fail closed

No login, upgrade, or commercial mutation may proceed from incomplete or ambiguous state.

When the system cannot resolve exactly one eligible effective subscription, the user-visible action is blocked and the anomaly is surfaced explicitly.

### 3.4 No silent provisioning degradation

New commercial tenant creation must not return success while subscription provisioning has failed. The current pattern of swallowing subscription-creation failure and leaving a partially provisioned commercial account is not acceptable for the Executive path.

### 3.5 Historical rows are history

Historical terminal subscriptions remain queryable for audit/history but cannot determine current access, current billing, or current commercial actions.

## 4. Canonical Tenant Commercial State

Introduce a backend application service named conceptually `TenantCommercialStateService` (exact package/file naming may follow repository conventions during implementation).

It consumes:

- tenant id;
- tenant operational status;
- `SubscriptionResolutionService` effective-subscription result;
- effective subscription status and billing state;
- optional Finance/reconciliation summary when requested by billing views.

It produces a stable read model:

```text
TenantCommercialState
- tenantId
- tenantStatus
- effectiveSubscriptionId | null
- effectiveSubscriptionStatus | null
- billingState | null
- accessDecision
- commercialAction
- anomalyCode | null
```

### 4.1 Access decisions

Supported decisions:

- `ACCESS_ALLOWED`
- `TENANT_NOT_ACTIVE`
- `NO_EFFECTIVE_SUBSCRIPTION`
- `SUBSCRIPTION_TRIAL`
- `SUBSCRIPTION_PAST_DUE`
- `SUBSCRIPTION_SUSPENDED`
- `SUBSCRIPTION_PAUSED`
- `SUBSCRIPTION_TERMINAL`
- `AMBIGUOUS_EFFECTIVE_SUBSCRIPTION`

The exact mapping must be covered by tests and must use the repository's canonical lifecycle vocabulary. Unknown statuses fail closed.

### 4.2 Commercial actions

Supported actions:

- `UPGRADE`
- `RESUME`
- `CREATE_SUBSCRIPTION`
- `CREATE_SUCCESSOR`
- `NONE`
- `BLOCKED`

The decision service determines which action is legal; the frontend does not infer this from raw status strings.

## 5. Provisioning Convergence

### 5.1 Single Executive provisioning orchestrator

Introduce one application orchestration boundary for the Executive tenant-creation command.

The orchestrator must perform, in one governed transaction where repository boundaries permit:

1. validate tenant creation request;
2. resolve requested plan by `planId` or `planCode`, with a documented default only if the current product contract requires one;
3. validate plan is commercially usable and has an eligible active plan version where required;
4. provision tenant, administrator, default organization, memberships, and role grants through the canonical registration authority;
5. apply tenant profile fields;
6. create the initial subscription using `SaasAdministrationService`/canonical subscription authorities;
7. initialize subscription composition/items and entitlements through existing canonical paths;
8. emit audit/outbox/entitlement events through existing authorities;
9. return the created tenant plus its derived commercial state.

### 5.2 Failure semantics

For the Executive commercial creation path:

- subscription provisioning failure aborts the command;
- plan resolution failure aborts the command;
- no successful API response is returned with a partially provisioned commercial account;
- no catch-and-log continuation is permitted for subscription creation.

If existing non-Executive onboarding flows intentionally support tenant-only provisioning, they remain separate and must not be silently routed through the commercial contract.

### 5.3 Compatibility

Existing DTO fields are preserved where possible:

- `trialDays`
- `planCode`
- `planId`
- `billingCycle`
- `seatQuantity`

Validation becomes authoritative rather than advisory.

## 6. Tenant Directory and Read Models

`TenantDirectoryQueryService` must stop selecting the most recent subscription row as the current state.

The tenant directory must expose:

- historical subscription count;
- effective subscription id/status, if one exists;
- derived access decision;
- derived commercial action;
- optional anomaly code.

The effective row must use the same semantic definition as `SubscriptionResolutionService`.

No duplicate lifecycle-resolution logic should be embedded independently in SQL if it can drift from the canonical resolver. If a dedicated efficient query read model is introduced, its semantics must be locked by parity tests against `SubscriptionResolutionService`.

## 7. Tenant Login Link Security

The backend endpoint for tenant login-link audit/action must enforce the commercial access decision, not only `tenant.status == ACTIVE`.

A login link is allowed only when:

- the tenant operational status permits access;
- exactly one eligible effective subscription exists;
- the effective subscription lifecycle/billing state permits access under the explicit policy;
- the caller remains `EXECUTIVE_MANAGE` authorized.

The frontend must render login controls from the backend-derived decision. It must not use conditions equivalent to `subscriptionStatus != TERMINATED`.

Every denial must return deterministic conflict/forbidden semantics and a stable user-facing reason without leaking cross-tenant data.

## 8. Subscription Continuation and Upgrade Journey

The Executive journey must become action-driven rather than page-driven.

For a selected tenant:

- effective eligible subscription -> `UPGRADE` opens the existing subscription detail/change-plan flow;
- resumable cancelled subscription -> `RESUME` invokes the canonical resume path;
- no subscription history -> `CREATE_SUBSCRIPTION` opens a creation flow using an existing active plan/version;
- eligible terminal-history continuation -> `CREATE_SUCCESSOR` uses the canonical successor creation contract;
- ambiguous/illegal state -> `BLOCKED` with an explicit anomaly message.

No action may create a new plan/version implicitly. Plan selection uses existing active catalog data only.

## 9. Subscription Price Semantics

The subscription grid/read model must distinguish:

- `recurringAmountMinor`: the actual recurring charge for the billing cycle;
- `billingCycle`: `MONTHLY` or `ANNUAL`;
- `monthlyEquivalentMinor`: optional analytical value for annual plans;
- `currencyCode`.

For annual subscriptions, `recurringAmountMinor` must be the annual recurring amount, not `annual / 12`.

If `monthlyEquivalentMinor` is displayed, it must be labeled explicitly as an analytical monthly equivalent and must use an agreed rounding policy. It cannot be shown as the invoice amount.

## 10. Executive Billing Read Model

Create an Executive billing read model that presents operational billing and accounting linkage without collapsing their boundaries.

Each billing row should include, when available:

- SCP billing projection invoice id/number/status;
- subscription id;
- tenant id/name;
- subtotal;
- credit applied;
- tax;
- total;
- amount paid;
- outstanding amount;
- currency;
- period start/end;
- due date;
- paid date;
- payment reference;
- Finance link id / Finance invoice id;
- Finance/accounting invoice state summary;
- settlement/payment state summary;
- reconciliation classification/status;
- source-of-truth indicator.

The API must make it explicit that Finance is accounting truth and SCP invoice fields are the subscription billing projection.

The existing `mark-paid` compatibility path must not be promoted as the primary accounting settlement mechanism if R0C13 settlement/Finance flow supersedes it. Any retained compatibility action must remain capability-gated, audited, and covered by convergence tests.

## 11. Production Consistency Diagnostics

Add a read-only diagnostic service/query set for governed investigation. It must not mutate data.

Required anomaly classes:

1. active tenant without effective subscription;
2. active tenant whose latest historical subscription is terminal and no effective subscription exists;
3. more than one effective subscription for one tenant;
4. tenant operational state inconsistent with the permitted access policy;
5. overdue OPEN billing projection invoices whose effective subscription did not converge to expected dunning state;
6. billing projection / Finance linkage missing where the integration contract requires one;
7. payment/settlement/reconciliation mismatch;
8. currency mismatch between subscription, billing projection, and Finance linkage;
9. orphaned or invalid plan/version references relevant to current effective subscriptions.

Diagnostics return evidence only. Repair is a separate explicit command path and is outside this design unless a later anomaly is proven to require one.

## 12. Dunning

`BillingStateService` remains the sole billing-state authority.

Implementation work must verify:

- scheduler configuration in governed deployment;
- `SANAD_DUNNING_ENABLED` effective behavior;
- overdue invoice scoping by effective subscription id;
- transitions `CURRENT -> PAST_DUE -> SUSPENDED` and recovery through canonical lifecycle commands;
- terminal subscription exclusion;
- idempotency across repeated scheduler runs;
- isolation of per-tenant failures.

Enabling or changing production runtime configuration is a separate release action and must occur only if tests prove the required configuration and the release path authorizes it.

## 13. Security and Tenant Isolation

All new/changed APIs must preserve:

- `EXECUTIVE_VIEW` for read-only Executive surfaces;
- `EXECUTIVE_MANAGE` or narrower existing capabilities for commands;
- `ControlPlaneAccessGuard`;
- canonical project-owner cross-tenant allowlist behavior only where already authorized;
- PostgreSQL RLS/FORCE RLS for tenant-owned new tables, if any;
- no tenant id accepted from untrusted bodies when session/route context should determine it;
- no historical subscription row used to bypass effective-subscription rules;
- no cross-tenant Finance, billing, or reconciliation leakage.

No security guard may be weakened to make tests pass.

## 14. Data Model and Migration Policy

Prefer no schema change when the existing tables and R0C13 structures are sufficient.

A migration is permitted only when required for a stable read model or invariant that cannot be implemented safely otherwise.

Any migration must be:

- forward-only;
- non-destructive;
- PostgreSQL-compatible;
- Flyway-ordered at repository head;
- covered by PostgreSQL Direct migration and rollback-safety evidence;
- free of data guessing/backfill that invents commercial state.

Existing anomalous production data must not be auto-repaired by migration.

## 15. TDD and Verification Strategy

Implementation follows RED -> GREEN -> REFACTOR.

### 15.1 Required RED contracts

Before production code changes, add tests that reproduce at least:

- Executive tenant creation succeeds today without creating an effective subscription;
- login-link endpoint permits an ACTIVE tenant with no effective subscription;
- tenant directory can surface a terminal latest historical row instead of the effective subscription;
- upgrade intent dead-ends when no subscription exists;
- annual subscription amount is exposed as monthly equivalent under generic amount semantics;
- billing read surface lacks Finance/reconciliation distinction.

### 15.2 Backend unit/integration tests

Required coverage:

- provisioning orchestration success;
- provisioning rollback on subscription failure;
- plan resolution validation;
- no implicit catalog/plan creation;
- access-decision state matrix;
- login-link allow/deny matrix;
- effective-subscription parity;
- terminal-history behavior;
- create/resume/successor action selection;
- subscription amount semantics;
- billing/Finance/reconciliation read mapping;
- dunning state transitions;
- idempotency;
- audit/outbox emission;
- authorization and cross-tenant denial.

### 15.3 PostgreSQL Direct

Docker/Testcontainers are not authoritative for governed acceptance.

PostgreSQL Direct must verify:

- real Flyway migrations;
- transaction rollback on provisioning failure;
- tenant isolation/RLS where applicable;
- concurrent subscription creation/successor behavior;
- unique effective-subscription invariant;
- dunning queries and transitions;
- Finance/billing linkage;
- reconciliation classifications;
- no cross-tenant reads or writes.

### 15.4 Web tests

Required checks:

- tenant list renders derived access/commercial state;
- login controls hidden/disabled consistently with backend decision;
- no frontend-only bypass;
- upgrade/create/resume/successor routes are deterministic;
- annual recurring amount labeling is correct;
- billing table exposes accounting/projection distinction;
- loading, empty, error, 403, 409 states;
- Arabic/English i18n parity;
- accessibility and keyboard behavior;
- responsive layout.

### 15.5 Full verification gate

Before merge/closure, all required commands must complete on the same exact head SHA:

- backend Maven full suite: `FAILURES=0`, `ERRORS=0`;
- PostgreSQL Direct governed suites: all pass;
- frontend unit/integration tests: all pass;
- TypeScript: 0 errors;
- ESLint: 0 errors/warnings under repository policy;
- i18n parity: pass;
- design-system compliance: pass;
- performance budget: pass;
- Next.js production build: pass;
- exact-head CI required checks: success;
- no skipped required acceptance test without an explicit governed exception.

A generated report is not evidence unless the underlying tests actually executed.

## 16. Production Verification and Release Governance

Before any production mutation:

1. exact-head CI passes on the implementation SHA;
2. independent approval is obtained where repository governance requires it;
3. protected merge occurs through the approved path;
4. exact-main image is produced;
5. production release is authorized and deployed;
6. post-merge verification passes on the same release baseline;
7. authenticated production smoke tests verify tenant, subscription, billing, and access paths;
8. read-only consistency diagnostics are run against production-accessible data if the governed data connection is available.

No production database repair is included automatically.

## 17. Rollback

Rollback must preserve accounting and subscription history.

Allowed rollback mechanisms:

- deploy previous known-good application image;
- disable newly exposed UI/action paths with existing safe configuration if such a flag already exists;
- leave forward-only schema additions in place if rollback-safe and unused.

Forbidden rollback mechanisms:

- deleting Finance truth;
- deleting subscription history;
- rewriting historical invoice/payment records;
- restoring tenant access by direct status mutation outside canonical lifecycle authorities.

## 18. Non-Goals

Out of scope:

- enabling live payment collection;
- creating or activating a real payment-provider account;
- changing Commerce `PaymentGatewayPort` semantics;
- replacing Finance as accounting source of truth;
- rewriting R0C13 settlement/reconciliation architecture;
- broad unrelated tenant UI redesign;
- automatic mutation of existing anomalous production commercial data;
- creation of new plan catalog entries merely to satisfy tests.

## 19. Acceptance Criteria

This work is complete only when all of the following are true:

1. Executive commercial tenant creation cannot return success without a valid initial subscription when the commercial contract requires one.
2. Tenant login eligibility is enforced by the backend canonical commercial-state decision and mirrored by the UI.
3. Tenant directory current subscription state is derived from the canonical effective subscription, not the newest historical row.
4. Every tenant receives a deterministic commercial action: upgrade, resume, create subscription, create successor, none, or blocked.
5. Annual recurring amount and monthly equivalent are distinct and correctly labeled.
6. Executive billing clearly separates SCP billing projection from Finance accounting truth and surfaces settlement/reconciliation context.
7. Production consistency diagnostics can enumerate known divergence classes without mutation.
8. Dunning behavior is verified end-to-end under governed configuration.
9. RBAC, RLS/tenant isolation, audit, and idempotency regressions pass.
10. Full backend/frontend/PostgreSQL Direct test suites complete with zero failures/errors under the repository's governed acceptance policy.
11. Exact-head CI, protected merge, post-merge verification, and production smoke evidence are bound to the final approved SHA before closure is declared.

## 20. Current Environment Observation

Read-only Render inspection of the approved `Snad` workspace on 2026-09-23 found:

- active service `sanad-backend` (`srv-d8ragqkm0tmc73bviqq0`), image-based deployment, auto-deploy disabled;
- currently configured image observed as `ghcr.io/snadaiapp-png/snad-backend:66893aeb14a4713fe40b5a88983460eddc3dc2b2` at inspection time;
- legacy `sanad-backend-v2` service is suspended;
- no Render-hosted PostgreSQL instance exists in the workspace, so production database diagnostics require the repository's actual external PostgreSQL connection path rather than Render Postgres tooling.

These observations are environment evidence only and do not authorize deployment or configuration mutation.
