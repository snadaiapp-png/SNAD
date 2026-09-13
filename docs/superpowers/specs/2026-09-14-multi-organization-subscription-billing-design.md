# SNAD Multi-Organization Subscription, Billing & Workspace — Master Design Specification

- **Date:** 2026-09-14
- **Repository:** `snadaiapp-png/SNAD`
- **Design baseline:** `e30d47be1716dcd32a4101f01428f62a66d0ac18` (`main`)
- **Design branch:** `docs/20260914-multi-org-subscription-billing-design`
- **Classification:** Architectural
- **Implementation status:** NOT STARTED
- **Authority:** User-approved design sections 1–6 in the 2026-09-14 subscription/tenant remediation review
- **Database policy:** PostgreSQL Direct only for certification; Docker/Testcontainers execution paths are not accepted for final certification
- **Primary objective:** Support one commercial customer with multiple operational branches, different applications per branch, scope-aware entitlements, branch-aware usage and billing, websites/stores, default/custom domains, and a dynamic workspace while preserving existing subscription, Finance, security and audit authorities.

---

## 0. Governance and non-negotiable decisions

This document is the governing architecture for the multi-organization subscription and tenant workspace program. It is additive to the existing Subscription Control Plane (SCP), not a replacement.

The following decisions are approved and MUST NOT be changed during implementation without a new design decision:

1. **`Organization = Branch / Operating Unit`.** No parallel `branches` aggregate is introduced.
2. **Hierarchical Organizations.** Organizations may be `HEAD_OFFICE`, `REGION`, `BRANCH`, or `OPERATING_UNIT`.
3. **Hybrid / Scope-Aware Licensing.** Commercial availability may be tenant-, organization-, user-, resource-, or usage-scoped.
4. **One Effective Commercial Subscription per Tenant.** Branches do not receive independent subscriptions.
5. **Hybrid Billing.** Consolidated tenant billing is default; optional billing profiles can produce separate invoices for selected organizations/groups.
6. **Configurable Cost Allocation.** Tenant-wide costs may be allocated centrally, equally, by users, by usage, or by explicit percentages without changing the contracted charge.
7. **Single Writer.** Each business fact has one authoritative writer.
8. **Preview → Confirm** for every commercial mutation with price, entitlement, provisioning, or billing impact.
9. **One entitlement authority.** The existing entitlement engine is generalized; no competing entitlement engine is introduced.
10. **Global hostname authority.** A central domain registry prevents cross-resource hostname collisions.
11. **Soft lifecycle changes.** Applications, organizations, domains and commercial items are archived/deactivated/cancelled rather than physically deleted when history exists.
12. **Fail closed.** Missing tenant, organization, entitlement, capability, pricing, routing, or verification evidence denies the operation.
13. **Backward compatibility.** Existing subscriptions behave as tenant-scoped until explicitly configured otherwise; historical invoices and usage are never assigned invented branch detail.
14. **PostgreSQL Direct certification.** Final acceptance uses host-native PostgreSQL with a least-privilege application role.

---

## 1. Verified repository baseline

The following facts were verified against the design baseline and are constraints to preserve.

### 1.1 Organization and access model

- `organizations` already belongs to `tenant_id`, has status `ACTIVE|INACTIVE|ARCHIVED`, and already has a composite unique key `(tenant_id,id)` from the existing access-control migration.
- `user_role_assignments` already carries optional `organization_id` and uses composite tenant-safe foreign keys.
- `CapabilityEvaluationService` currently supports tenant-wide grants and exact organization matching, but not hierarchy-descendant semantics.
- `ControlPlaneAccessGuard` separately pins platform control-plane access to the configured control tenant. Tenant administrators must not become platform operators merely by holding an ADMIN role.

### 1.2 Subscription and pricing model

- One effective commercial subscription per tenant is already the governing model.
- `subscription_items` supports multiple PLAN/ADD_ON/METERED/OTHER items but has no organization/resource scope.
- Pricing already supports `PER_BRANCH` and other quantity/usage models.
- Plan versions already carry organization limits through the existing plan/version model.
- Subscription lifecycle status has a canonical writer in `SubscriptionCommandService`.
- `SubscriptionChangeService` already implements a plan-oriented preview/execute flow.
- The current HTTP generic lifecycle endpoint accepts a command string and therefore requires an explicit operator/system command boundary.

### 1.3 Usage and billing

- `usage_events` and `usage_aggregates` are tenant-scoped and do not currently attribute usage to organization/application/resource.
- `usage_metrics` already includes a `branches` metric.
- `billing_invoices` is the SCP/dunning-compatible billing projection and contains invoice totals but no branch-aware line model.
- R0C13 established Finance as the authoritative accounting invoice/payment domain. This program MUST NOT create a parallel accounting ledger or replace Finance.
- Existing billing/provider integration, reconciliation, outbox and BillingStateService authority are preserved.

### 1.4 Websites, stores and domains

- `websites` and `commerce_stores` are tenant-scoped but are not currently linked to `organization_id`.
- Website and commerce domain tables exist and support default/custom lifecycle concepts.
- `tenant_domains` exists but is unique only within a tenant, while website/store hostname uniqueness is enforced separately. There is no single cross-resource hostname authority.
- Tenant creation does not currently complete a full domain-routing readiness contract.
- No production `DomainRoutingFilter`/equivalent host-to-resource authority was verified in the baseline.

### 1.5 Frontend facts

- Tenant create/edit uses the SDS `Modal`; the modal focus effect depends on an unstable `onClose` callback through `handleKeyDown`. Parent rerenders can re-run focus initialization and move focus from an input back to the panel. This matches the observed “one character then typing stops” UAT defect.
- Tenant forms use free-text country/currency fields and can expose raw backend validation text.
- Executive Applications UI exposes creation but not the full update/archive lifecycle.
- Subscription detail hard-codes lifecycle buttons instead of consuming backend-allowed actions.
- Workspace launchers are not uniformly entitlement- and organization-driven.

### 1.6 Current overlapping pull requests

At design time the following open PRs overlap early remediation work and MUST be reconciled before implementation to prevent duplicate fixes:

- PR #1056 — tenant modal input/account forms.
- PR #1057 — application catalog lifecycle.
- PR #1058 — lifecycle/provisioning invariants.
- Older open SCP repair PRs (#1028–#1033 and #922) may contain superseded or partially duplicated work and require classification against current `main` before reuse.

No implementation work may blindly recreate a change already merged or correctly implemented by one of these PRs.

---

## 2. Target domain hierarchy

The governing hierarchy is:

```text
Tenant / Customer
  └── Organization hierarchy
      ├── HEAD_OFFICE
      ├── REGION
      ├── BRANCH
      └── OPERATING_UNIT
          ├── application assignments
          ├── users / scoped roles
          ├── websites / stores
          ├── usage attribution
          └── billing allocation
```

A Tenant is the contractual and security-isolation customer boundary. An Organization is an operational scope inside that tenant. An Organization is never a tenant and never creates an independent subscription.

### 2.1 Organization hierarchy

Extend `organizations` additively with:

- `organization_type`: `HEAD_OFFICE|REGION|BRANCH|OPERATING_UNIT`.
- `parent_organization_id` nullable.
- `hierarchy_version` for optimistic concurrency.

Existing rows are backfilled as `OPERATING_UNIT` roots; no historical parentage is inferred.

Maintain an `organization_hierarchy` closure table:

- `tenant_id`
- `ancestor_id`
- `descendant_id`
- `depth`
- primary/unique key on `(tenant_id,ancestor_id,descendant_id)`

Every organization has a self-row with `depth=0`.

All parent and closure relationships use composite tenant-safe foreign keys. Self-parent and cycles are rejected. Re-parenting is serialized per tenant and must verify that the proposed parent is not a descendant of the organization being moved.

### 2.2 Dynamic hierarchy semantics

Organization-tree grants and inherited application assignments follow the current hierarchy, not a frozen copy of descendants. Moving a branch can therefore change effective permissions and inherited application availability. The move operation requires an impact preview before confirmation.

---

## 3. Commercial subscription and scope model

### 3.1 Commercial contract

Each tenant has one effective commercial subscription. The subscription contains multiple billable items. An item may be tenant-wide or scoped to one or more organizations/resources.

The canonical relationship is:

```text
Tenant
  -> Effective Subscription
      -> Subscription Items
          -> Commercial Scopes
          -> Application Assignments
          -> Charges
              -> Cost Allocations
              -> Billing Profiles
              -> Invoice Lines
```

### 3.2 Commercial scopes

Do not add a single `organization_id` column to `subscription_items` as the sole scope mechanism because one item may cover multiple organizations.

Introduce `subscription_item_scopes` with:

- `id`
- `tenant_id`
- `subscription_item_id`
- `scope_type = TENANT|ORGANIZATION|USER|WEBSITE|STORE`
- exactly one matching resource identifier where required
- `inherit_to_descendants`
- `status = PENDING|ACTIVE|CANCELLED`
- `effective_from`, `effective_to`
- timestamps

Database CHECK constraints enforce the valid reference shape for each `scope_type`. Cross-tenant resource references are prevented with composite foreign keys.

Every pre-existing active subscription item receives a `TENANT` scope during compatibility backfill. The migration does not infer historical branch ownership.

### 3.3 Application assignment is distinct from commercial item scope

Commercial scope answers “what did the customer contract for?” Operational application assignment answers “where is this application currently enabled?”

Introduce `subscription_application_scopes`:

- `tenant_id`
- `subscription_id`
- `source_subscription_item_id`
- `application_id`
- `scope_type = TENANT|ORGANIZATION`
- optional `organization_id`
- `inherit_to_descendants`
- `status = PENDING|ACTIVE|SUSPENDED|CANCELLED`
- effective timestamps

An application assignment MUST NOT become ACTIVE unless a valid commercial item/scope authorizes it and provisioning verification succeeds.

### 3.4 Catalog scope policy

Introduce catalog scope policy data rather than hard-coding scope assumptions in services or React. A policy associates an Application/Product with allowed scopes, default scope and descendant-inheritance support.

The existing catalog is backfilled with tenant scope allowed/default, preserving legacy behavior until explicitly configured.

`HYBRID` is a pricing/licensing strategy, not a separate resource scope.

---

## 4. Commercial change authority

All mutations that can alter recurring price, entitlement, provisioning, quantity, scope or billing grouping use one commercial change authority.

### 4.1 CommercialChangeService

Responsibilities:

1. Resolve the effective subscription and current version.
2. Validate target organizations/resources and catalog scope policy.
3. Resolve authoritative country/currency and applicable price.
4. Calculate current and target commercial state.
5. Calculate entitlement, provisioning and billing impact.
6. Persist an immutable preview.
7. Confirm using expected versions and preview hash.
8. Apply all operations atomically.
9. Emit durable provisioning/outbox/audit facts.

Direct paid-composition writes from controllers are not allowed after cutover.

### 4.2 Commercial change ledger

Introduce:

#### `commercial_change_requests`

- tenant/subscription identifiers
- `status = PREVIEWED|CONFIRMING|APPLIED|EXPIRED|CANCELLED|FAILED`
- base subscription/configuration version
- pricing timestamp/version references
- preview hash
- current/target/delta amounts and currency
- expiration
- requester/confirmer
- idempotency key
- timestamps

#### `commercial_change_operations`

Typed operations such as:

- `ADD_APPLICATION_TO_ORGANIZATION`
- `REMOVE_APPLICATION_FROM_ORGANIZATION`
- `CHANGE_APPLICATION_SCOPE`
- `SET_ITEM_QUANTITY`
- `ADD_PRODUCT`
- `REMOVE_PRODUCT`
- `ADD_BRANCH_LICENSE`
- `CHANGE_BILLING_PROFILE`
- `CHANGE_COST_ALLOCATION`

Confirm fails with `409 STALE_PREVIEW` if the subscription/configuration, catalog/price basis, organization state or preview hash no longer matches.

The same idempotency key with the same payload returns the same result. Reuse with a different payload returns an idempotency conflict.

---

## 5. Lifecycle and provisioning authority

### 5.1 HTTP lifecycle boundary

`SubscriptionCommandService` remains the canonical internal lifecycle writer.

The generic HTTP route MUST NOT remain a public command bus for system-owned transitions. The compatibility route, while it exists, uses an explicit operator allowlist and rejects system-owned commands.

System-owned commands include at least:

- `ACTIVATE` — Provisioning authority.
- `RENEW` — Renewal/commercial authority.
- `PAYMENT_RECEIVED` — Billing settlement authority.
- `MARK_PAST_DUE` / `ENTER_GRACE` — Billing state authority.
- `EXPIRE` — expiration runtime.
- `REQUEST_ACTIVATION` — provisioning workflow.

Human/operator actions receive explicit routes such as pause/resume/suspend/cancel/terminate subject to the canonical transition table.

The subscription read model returns `availableActions[]` and `blockingReasons[]`; the frontend renders only those actions. Backend legality remains authoritative.

### 5.2 Scope-aware provisioning

Provisioning jobs become organization/application-aware and may carry:

- tenant
- subscription
- organization
- application
- commercial change id

Canonical step order for an application assignment:

```text
VALIDATE_SCOPE
-> ENSURE_DEPENDENCIES
-> ENABLE_APPLICATION
-> MATERIALIZE_ENTITLEMENTS
-> VERIFY
-> ACTIVATE_SCOPE
```

A failed prerequisite prevents later steps from running. Retry resumes at the first incomplete/failed step and does not duplicate succeeded side effects. An application scope is not ACTIVE until VERIFY succeeds.

---

## 6. Entitlement and authorization

### 6.1 One entitlement engine

The existing entitlement resolver remains authoritative and is generalized into a scope-aware resolver. It accepts tenant plus optional organization/user/resource context and returns an explicit allow/deny decision with reason, source item/scope, limit and usage where relevant.

The effective-access equation is:

```text
Authenticated identity
AND tenant boundary
AND effective subscription
AND commercial entitlement
AND organization scope
AND actor capability
AND resource state
```

Any missing factor denies access.

### 6.2 RBAC hierarchy

Reuse `user_role_assignments`. Add a non-null `scope_mode` column with explicit role-grant semantics:

- `TENANT` — requires `organization_id IS NULL`.
- `ORGANIZATION_ONLY` — requires `organization_id IS NOT NULL` and matches only that organization.
- `ORGANIZATION_TREE` — requires `organization_id IS NOT NULL` and dynamically covers that organization plus descendants in `organization_hierarchy`.

A database CHECK constraint enforces the valid `scope_mode` / `organization_id` shape. Backfill is deterministic: existing grants with `organization_id IS NULL` become `TENANT`; existing organization-bound grants become `ORGANIZATION_ONLY`. No existing grant is silently widened to descendant access.

`ORGANIZATION_TREE` dynamically covers descendants according to `organization_hierarchy`. General-purpose negative/deny roles are out of scope; denial derives from absence of grant or suspended/inactive commercial/resource state.

`CapabilityEvaluationService` remains an RBAC evaluator. A higher-level `ScopedAuthorizationService` combines RBAC with entitlement and resource ownership. This keeps authorization concerns testable and prevents commercial logic from being embedded in role evaluation.

### 6.3 Control Plane vs Tenant Workspace

Platform Control Plane routes remain guarded by `ControlPlaneAccessGuard` plus capabilities.

Tenant Workspace routes do not use ControlPlaneAccessGuard. They use authenticated tenant context, organization scope and scoped authorization. A tenant admin must never gain platform-operator authority through tenant-local roles.

The known tenant-filtered subscription HTTP 403 remains a forensic blocker until the exact failing layer is proven. It MUST NOT be “fixed” by broadly granting capabilities or removing the control-plane guard.

---

## 7. Usage attribution

Extend `usage_events` additively with optional attribution identifiers such as organization/application/subscription-item/website/store.

Historical tenant-only events remain tenant-only. No migration invents organization attribution.

Keep the existing tenant rollup for backward-compatible reads. Add a scope-aware aggregate projection (`usage_scope_aggregates`) for organization/application/resource reporting.

The existing tenant+metric+idempotency key remains the ingestion replay boundary unless later evidence proves a change is required.

Billable usage may only be accepted from trusted backend/service identities, not directly from an untrusted browser.

---

## 8. Billing profiles, rating and cost allocation

Billing and entitlement remain distinct concerns.

### 8.1 Billing profiles

Introduce `billing_profiles` and organization bindings.

A tenant always has one default active billing profile. An organization without a special profile uses the default tenant profile. Selected organizations may be assigned to a separate profile.

Bindings use explicit states; only one ACTIVE binding may exist for an organization. A future change is PENDING and is activated atomically at the billing boundary. This avoids overlapping active profiles.

Default behavior is one consolidated invoice. Separate-profile billing is optional and does not change the total contractual value.

### 8.2 Rating before allocation

Introduce an immutable/replay-safe charge projection:

`subscription_billing_charges`

Each charge pins:

- subscription item
- billing period
- pricing model
- quantity
- base/unit amounts
- final charge amount
- currency
- idempotency key
- state `CALCULATED|FINALIZED|INVOICED|VOID`

Pricing occurs once. Cost allocation never re-runs pricing.

### 8.3 Cost allocation

Introduce `cost_allocation_policies` and targets.

Supported methods:

- `CENTRAL`
- `EQUAL`
- `USERS`
- `USAGE`
- `MANUAL_PERCENTAGE`
- internal `DIRECT_SCOPE` for organization-scoped charges

Manual percentages use basis points; active manual policies MUST total 10000 basis points.

Charge allocations preserve the financial invariant:

```text
SUM(allocated_amount_minor) = charge_amount_minor
```

Remainder minor units are distributed deterministically; floating-point arithmetic is prohibited.

### 8.4 Invoice lines

Add branch-aware SCP invoice lines tied back to charge/allocation/item/profile/organization.

The existing `billing_invoices` remains the SCP/dunning projection and continues to integrate with Finance through the existing R0C13 boundary. This program does not create another accounting invoice domain.

Financial invariant:

```text
SUM(invoice line amounts) = billing_invoices.subtotal_minor
```

Historical invoices are not rewritten with invented branch data. If compatibility lines are needed, they are explicitly classified as legacy rollups with no organization attribution.

---

## 9. Websites, stores and monetized resource limits

Add nullable `organization_id` to `websites` and `commerce_stores` with composite tenant-safe foreign keys.

- NULL = corporate/tenant resource.
- organization id = resource belongs to that organization.

Existing resources remain NULL during backfill.

Website/store creation first resolves entitlement capacity. If capacity exists, create the resource. If the limit is reached but an add-on is purchasable, return an upgrade-required response and commercial preview; no hidden purchase occurs.

The same commercial pattern applies to additional websites, stores, POS locations, user seats and branch allowances.

---

## 10. Global domain registry and routing

Introduce `domain_routes` as the **platform-scoped routing index and hostname claim authority**.

Each route contains only the minimum routing identity needed before tenant context exists:

- globally unique normalized hostname
- tenant id
- route type (tenant application / website / store)
- exactly one typed owner reference
- optional organization id
- status

Do not rely on a weak polymorphic foreign key. CHECK constraints require the owner column matching the route type.

`domain_routes` is an explicit exception to ordinary tenant-table RLS because the Host resolver must discover `tenant_id` *from the hostname* before a tenant session context exists. It MUST NOT be exposed through tenant-facing generic CRUD. Database privileges are deny-by-default: only the internal routing component/service identity receives the minimum read/claim operations required by the routing lifecycle. Detailed domain metadata remains in the tenant-scoped domain tables and is still protected by tenant isolation. The routing index must not contain secrets or customer-private configuration beyond identifiers required to route.

Before backfill, scan all existing tenant/website/store domain registries. If the same hostname maps to different owners, the migration/cutover fails closed; no automatic winner is chosen.

### 10.1 Default hostname hierarchy

Generated hostnames follow the configured `SANAD_BASE_DOMAIN`, not a hard-coded project domain:

- tenant: `<tenant>.<base-domain>`
- website: `<website>.<tenant>.<base-domain>`
- store: `<store>.<tenant>.<base-domain>`

### 10.2 Custom domain lifecycle

Normalize hostname case, trailing dot and IDNA/punycode. Reserve protected subdomains. Custom domains use a challenge lifecycle and only become routable after successful verification.

Unknown, inactive or unverified hosts fail closed. There is no “default tenant” fallback.

Releasing a custom domain uses a release-pending state where propagation safety requires it; primary routes cannot be removed without a replacement/fallback.

---

## 11. Tenant bootstrap and readiness

Creating a tenant is a workflow, not a single row insert.

Target bootstrap:

```text
reserve subdomain
-> create tenant
-> create HEAD_OFFICE
-> create/admin identity and initial scoped RBAC
-> create effective commercial subscription
-> create default tenant scope/application assignments
-> create default domain claim
-> create global route
-> enqueue provisioning
-> READY
```

Database-local core work is transactional. External/long-running work uses a durable idempotent provisioning workflow/outbox.

A tenant is not reported READY while required provisioning/routing is incomplete. Supported readiness states include at least PROVISIONING, READY, DEGRADED/SUSPENDED where appropriate, and FAILED/PROVISIONING_FAILED.

---

## 12. Executive and Tenant Workspace UX

### 12.1 Surface separation

- **Executive / Control Plane:** platform operator management of tenants, catalog, plans/prices, subscriptions, billing supervision, provisioning and audit.
- **Tenant Workspace:** customer management of organizations, scoped applications, users/roles, usage, billing, websites/stores, domains and audit.

### 12.2 Dynamic workspace context

Workspace launchers are derived from a backend workspace-context/read model. A launcher is shown only when the selected organization is commercially entitled, the application scope is active and the actor is authorized.

Hard-coded application links are retired after the dynamic catalog path passes compatibility tests.

The Organization Switcher shows only organizations visible to the user. “All company” is available only when the user has tenant-wide authority. Selecting an organization is a UI context, not an authorization grant; every request is revalidated by the backend.

### 12.3 Organization UX

Organization management supports hierarchy view, detail, applications, users, resources, usage, billing and audit.

Creating or moving an organization uses an impact/confirmation flow when the change affects commercial cost, inherited applications or permissions.

### 12.4 Applications UX

Executive Applications manages the SNAD catalog with create/edit/archive/restore. No physical delete is exposed for referenced applications.

Tenant Workspace Applications shows application assignments across organizations and routes paid changes through Commercial Preview → Confirm.

### 12.5 Subscription UX

Subscription detail renders `availableActions` from the backend rather than a hard-coded command list. System-owned lifecycle commands are never exposed as generic buttons.

Plan/scope/quantity changes use a commercial preview surface showing current amount, target amount, delta, proration, affected organizations/apps, provisioning actions, warnings and preview expiry.

### 12.6 Billing UX

Billing shows contract totals, open balance, next invoice, cost by organization/application and billing profiles. Cost allocation is explicitly presented as allocation, not repricing.

Historical invoices without organization detail state that the detail is unavailable rather than showing invented zeroes or allocations.

### 12.7 Domains/resources UX

Domain management unifies default/custom resource domains while showing their owner and verification/routing state.

Website/store creation selects Corporate or an organization and shows entitlement capacity. Limit exhaustion returns a purchasable-upgrade path where applicable.

### 12.8 Form and accessibility recovery

The SDS `Modal` focus lifecycle is fixed centrally: focus initialization occurs only on open transition; changing an unstable parent callback does not refocus the modal panel. ESC uses the latest callback without retriggering focus setup. Focus is restored only on close.

Business forms use visible labels. Country/currency/locale/timezone use structured selectors rather than raw regex-driven text entry where possible. Backend validation remains authoritative, but UI presents localized field errors rather than raw regex/SQL/internal text.

All async screens distinguish loading, empty, permission-denied, entitlement-required, provisioning, retryable failure, blocked failure and stale states.

---

## 13. Error and audit contracts

New API surfaces use stable error codes with localized UX mapping. Required classes include:

- tenant/scope mismatch
- organization missing/inactive
- capability missing
- application not entitled/suspended
- subscription not effective
- usage limit exceeded
- domain unverified/inactive
- stale preview
- idempotency conflict

Cross-tenant identifier probing should return a non-enumerating response (normally 404) on tenant-facing surfaces.

High-impact mutations produce durable audit evidence including actor, target tenant, organization, application/resource, action, before/after, correlation id and commercial change id where applicable.

Critical audit/outbox evidence is atomic with the governed business mutation. Critical security/financial audit must not be swallowed with `catch (Exception ignored)`.

Secrets, authorization headers, payment card data and raw verification secrets are prohibited in audit/outbox payloads.

---

## 14. Database isolation and migration strategy

### 14.1 Composite tenant foreign keys

Every new relationship between tenant-owned rows uses `(tenant_id, resource_id)` where the referenced table supports it. This prevents cross-tenant references even if application code is wrong.

### 14.2 RLS

All new tenant-owned tables enable and FORCE PostgreSQL RLS with fail-closed `app.tenant_id` semantics.

The platform-scoped `domain_routes` routing index is the deliberate exception defined in §10: it is privilege-isolated rather than tenant-GUC isolated because it is queried before tenant identity is known.

Existing tables that already use RLS are verified for fail-closed behavior.

Legacy SCP tables without RLS are not converted casually in the same migration. Any RLS cutover for legacy subscription tables requires caller inventory, transaction-context proof, scheduler/background-job proof and a dedicated compatibility gate.

Organization-level data isolation is enforced primarily by tenant RLS plus explicit organization predicates and ScopedAuthorizationService. General organization-RLS session context is not introduced in this phase.

### 14.3 Forward-only migrations

Migrations are additive and forward-only. No automatic down migration is the rollback strategy.

Logical migration groups are:

- M1 — organization hierarchy foundation
- M2 — catalog scope policy, commercial scopes and commercial change ledger
- M3 — organization/application usage attribution
- M4 — billing profiles, charges, allocations and invoice lines
- M5 — website/store organization binding
- M6 — global domain registry and safe backfill

The previously discussed `V20260914_1...V20260914_6` names are **provisional only**. Actual Flyway versions are allocated immediately before implementation after a fresh collision check against current protected `main`. No implementation may assume those numbers remain free.

### 14.4 Backfill truth rules

- Existing organizations: root `OPERATING_UNIT`; no invented parent.
- Existing subscription items: `TENANT` scope.
- Existing application/product scope policy: tenant allowed/default.
- Historical usage: remains tenant-only.
- Existing websites/stores: corporate/tenant (organization NULL).
- Billing: default tenant billing profile.
- Historical invoice details: legacy rollup only, no invented organization allocation.
- Domains: registry backfill only after global collision scan passes.

---

## 15. Security and fraud controls

- Platform operator and tenant workspace authorities remain separate.
- Tenant-wide entitlement does not imply tenant-wide data access.
- Region-tree RBAC never overrides missing commercial entitlement.
- Price/currency/quantity values used for finalized charges are pinned snapshots.
- Finalized/open invoice monetary data is not silently rewritten; corrections use governed adjustment/credit/replacement semantics.
- Duplicate commercial confirmation, rating, invoice generation, payment settlement and usage ingestion are idempotent.
- Global hostname claim is race-safe; exactly one conflicting claimant succeeds.
- Subscription/configuration confirmations use optimistic version/CAS or appropriate row locking.
- Billable usage sources are trusted service identities.
- Reserved default subdomain labels are configuration-driven.
- High-impact finance/security actions may require separation-of-duties approval through existing workflow/approval infrastructure; this design does not create a parallel approval engine.

---

## 16. Verification architecture

No implementation stage is accepted because it compiles or because a narrow unit suite passes.

Required test layers:

1. unit/state-machine tests
2. architecture/single-writer boundary tests
3. PostgreSQL Direct migration/integration/RLS/concurrency tests
4. real HTTP/JWT/API-contract tests
5. frontend component/accessibility tests
6. Playwright/UAT critical journeys

### 16.1 Mandatory invariants

At minimum prove:

- tenant A cannot read/write tenant B
- organization-only grants do not reach siblings/descendants
- organization-tree grants reach descendants only
- entitlement missing overrides RBAC allow
- RBAC missing overrides entitlement allow
- hierarchy cycles/cross-tenant parents are rejected
- generic HTTP lifecycle boundary rejects system-owned commands
- provisioning does not advance after a prerequisite failure
- retry does not duplicate successful steps or activation
- commercial preview is side-effect free
- stale/expired/replayed commercial confirms fail or replay safely as specified
- scope-aware usage attributes to the correct organization/application without altering tenant totals
- charge allocations conserve the original charge
- invoice lines conserve invoice subtotal
- consolidated/separate billing does not change total governed charges
- historical invoices/usage remain unchanged
- domain hostname is globally unique across tenant/website/store owners
- unknown/unverified hosts fail closed
- duplicate invoice generation is rejected/idempotent
- new tenant does not become READY before required provisioning
- Modal input accepts full multi-character typing without focus loss
- raw regex/backend validation text is not shown as the primary user-facing error
- TERMINATED subscriptions expose no illegal lifecycle actions
- dynamic workspace does not show applications absent from entitlement+RBAC for the selected organization

### 16.2 PostgreSQL Direct certification

Run against host-native PostgreSQL with application role equivalent to:

`NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS`.

Mandatory PostgreSQL acceptance tests may not be counted as PASS when skipped due to missing environment.

Both migration modes are required:

- fresh database V1 → HEAD
- upgrade simulation from the pre-program schema/data shape

### 16.3 Financial reconciliation gate

Before the new rating/billing path becomes authoritative for compatible legacy customers, compare legacy expected recurring totals with the new charge total. Any unexplained divergence blocks billing cutover.

### 16.4 Human UAT regressions

Convert observed defects into named regression cases, including:

- modal typing/focus
- raw validation error
- terminated illegal actions
- billing empty/tenant context behavior
- timestamp localization
- application edit/archive
- tenant-filtered 403 diagnosis

The 403 case is resolved only after identifying the exact failing boundary; no capability broadening by guesswork.

---

## 17. Rollout and rollback

Roll out in independent waves. Migration presence does not imply feature activation.

Conceptual waves:

- W0 schema only, new features off
- W1 modal/forms/error recovery
- W2 catalog and tenant executive recovery
- W3 organization hierarchy
- W4 scoped applications/entitlements
- W5 commercial preview/confirm
- W6 usage attribution
- W7 billing profiles/cost allocation
- W8 website/store organization binding
- W9 domain registry/routing
- W10 dynamic workspace
- W11 production certification

Feature flags/dark launch are required for high-risk authority cutovers.

Software rollback means disabling the new writer/read authority and returning to compatible paths. It MUST NOT silently reverse an already confirmed customer commercial transaction. Business reversal uses an explicit compensating commercial action.

Domain and billing cutovers use shadow/read-only verification before becoming authoritative.

---

## 18. Repository-wide PostgreSQL Direct governance

The primary CI already uses host-native PostgreSQL Direct, but design-time search still found Docker/docker-compose artifacts and workflows containing PostgreSQL service-container execution paths outside the primary CI.

The user's governing requirement is repository-wide final certification with:

```text
Docker execution paths = 0
Testcontainers imports/paths = 0
Docker-dependent CI = 0
```

Therefore a repository-wide execution-path inventory and remediation is a required certification workstream. Historical documentation may describe old Docker usage, but no executable certification/runtime/test path may depend on it at closure.

---

## 19. Implementation decomposition

This master architecture is intentionally broader than one implementation plan. It MUST be delivered as smaller independently reviewable workstreams, each with its own bounded/derived spec (where architectural detail remains), plan, TDD execution and certification.

Recommended dependency order:

### WS0 — Current correctness convergence
Reconcile/land or supersede existing repair PRs, including tenant Modal/forms, Application catalog lifecycle, lifecycle command boundary/provisioning ordering, billing transaction isolation, current-month usage and revenue semantics. Re-audit `main` after merges. No duplicate implementation.

### WS1 — Organization hierarchy and scoped authorization foundation
Organization types/tree/closure, hierarchy moves, RBAC scope modes, ScopedAuthorizationService and organization-context contracts.

### WS2 — Commercial scope and application assignment
Catalog scope policies, item scopes, application scopes, CommercialChange preview/confirm authority, scope-aware entitlement/provisioning.

### WS3 — Usage, rating, billing profiles and cost allocation
Usage attribution, charges, allocation conservation, billing profiles, invoice lines, Finance compatibility/reconciliation.

### WS4 — Websites, stores and global domains
Organization binding, resource entitlement limits, default/custom domain registry, host routing authority and verification.

### WS5 — Tenant/Executive UX convergence
Tenant detail, organization switcher, dynamic application workspace, billing/domain/resource UX, localized validation/error states. Early P0 UX fixes from WS0 remain separate and need not wait for all backend work.

### WS6 — Repository and production certification
Repo-wide PostgreSQL Direct/Docker-zero audit, full regression, security/financial reconciliation, canary, post-merge exact-SHA production certification and UAT.

No workstream may claim completion until its predecessor contracts required by that workstream are merged and revalidated.

---

## 20. Definition of done

The program is complete only when a real tenant can be proven to support:

```text
one customer
-> multiple hierarchical organizations
-> different applications per organization
-> organization-aware RBAC
-> scope-aware entitlements
-> organization/application usage attribution
-> deterministic pricing
-> cost allocation without repricing
-> consolidated and optional separate billing profiles
-> websites/stores linked to organizations
-> unique default/custom domains
-> dynamic workspace visibility
```

while simultaneously proving:

- no regression for existing tenant-wide customers
- no historical invoice/usage falsification
- no cross-tenant or cross-organization authorization leak
- no lifecycle command bypass
- no duplicated charge/invoice/payment/usage side effect
- no hostname takeover/collision
- no mandatory PostgreSQL acceptance skips
- no unresolved P0/P1 defect within the delivered workstream
- no Docker/Testcontainers executable dependency at final repository certification.

---

## 21. Explicit non-goals

This program does not:

- create a second tenant abstraction for branches
- create a subscription per branch
- replace Finance accounting authority
- replace the existing workflow/approval engine
- invent historical branch allocations
- physically delete referenced business history
- create general negative/deny RBAC semantics
- implement a new payment provider or authorize live collection merely because billing allocation is added
- enable a dynamic DNS/CDN/SSL provider by assumption; external routing/certificate automation is a separately proven integration
- remove legacy SCP columns/endpoints in the same cutover unless a dedicated deprecation gate is approved.

---

## 22. Pre-implementation gates

Before any implementation plan for WS1+ is executed:

1. fetch protected `main` and record exact SHA
2. classify PRs #1056/#1057/#1058 and older overlapping SCP PRs as merged/superseded/still-needed
3. re-run a targeted forensic diff for affected subscription, billing, organization, domain and frontend files
4. allocate actual Flyway versions after collision scan
5. confirm PostgreSQL Direct environment and least-privilege role
6. confirm no design decision in this spec has been invalidated by merged production changes
7. if material drift exists, update this spec or the derived workstream spec before code.

