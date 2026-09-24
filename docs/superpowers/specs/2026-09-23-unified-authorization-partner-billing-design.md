# SANAD Unified Authorization, Partner Control Plane & Hierarchical Billing — Architecture Design

**Date:** 2026-09-23 · **Revision B — 2026-09-24 correction record**  
**Status:** DESIGN APPROVED — REVISED per independent review (`INDEPENDENT_REVIEW_COMPLETE` → `PLAN_CORRECTION_REQUIRED`). Revision B supersedes Revision A wherever they differ.  
**Repository baseline:** `8d0d49c7bdd3ad3a886a23cffc1e735e61998712` (= origin/main; correction branch `docs/unified-control-plane-plan-corrections`)  
**Design branch:** `design/unified-authorization-partner-billing`  
**Revision B changes:** §4.1 runtime decision algorithm (protected safety is NOT a precedence layer), §5 protected role set, §6.1 Capability Contract Table, §9 Direct DENY v1 database invariant, §13 delegation vocabulary, §13.1 deterministic partner membership, §14.3 platform_files repository-grounded model, §17.2 auto-invoicing carrier, §18.3 agreement temporal integrity, §18.5–§18.7 settlement state machine + adjustments + fee display, §21.1 notification scoping/RLS, §22.4–§22.5 dashboard exact schema + reconciliation invariant, §29.1 progressive cutover, §31.6 correction-mandated tests.  

---

## 1. Purpose

This specification defines the target architecture for SANAD's unified authorization, delegated partner administration, commercial identity, tenant/partner billing, notifications, and executive dashboards.

The design must support one platform across:

- SANAD platform owner and internal control-plane administrators;
- agents, resellers, distributors, and sellers operating isolated commercial portfolios;
- tenants/customers owned directly by SANAD or commercially managed by a partner;
- tenant users, employees, managers, teams, departments, branches, business units, projects, legal entities, service accounts, and future AI agents;
- all current and future SANAD applications and modules.

The central rule is that security, commercial ownership, billing identity, and business-data access are separate concerns. Selling or managing a subscription never implicitly grants access to the customer's business data.

---

## 2. Existing foundations to preserve

Implementation must extend existing platform capabilities rather than create competing systems.

Existing foundations include:

- tenant-scoped roles;
- role-to-capability assignments;
- user-to-role grants;
- `CapabilityEvaluationService`;
- `@RequireCapability` enforcement;
- scoped authorization for HR/resource access;
- platform audit infrastructure;
- Subscription Control Plane entitlements;
- subscription billing projection;
- Finance as the authoritative accounting owner for invoices, payments, journals, and ledgers;
- existing billing outbox/integration work;
- partner/reseller ecosystem architecture.

Backward compatibility with current capability annotations and existing APIs is a hard constraint.

---

## 3. Target platform hierarchy

SANAD becomes a hierarchical multi-principal SaaS platform.

```text
SANAD PLATFORM
│
├── PLATFORM OWNER
├── PLATFORM ADMINS
├── DIRECT SANAD TENANTS
│   └── TENANT
│
└── AGENTS / RESELLERS / DISTRIBUTORS / SELLERS
    ├── PARTNER A
    │   ├── PARTNER ADMINS / USERS
    │   ├── BUSINESS IDENTITY
    │   ├── BILLING ACCOUNT
    │   ├── COMMERCIAL AGREEMENT
    │   └── TENANTS A1..An
    │
    └── PARTNER B
        ├── PARTNER ADMINS / USERS
        ├── BUSINESS IDENTITY
        ├── BILLING ACCOUNT
        ├── COMMERCIAL AGREEMENT
        └── TENANTS B1..Bn
```

Security boundaries are hierarchical:

```text
PLATFORM BOUNDARY
  -> PARTNER BOUNDARY
    -> TENANT BOUNDARY
      -> ORGANIZATION / DEPARTMENT / TEAM / BRANCH / PROJECT
        -> USER / EMPLOYEE / RESOURCE
```

`TENANT_ALL` always means all permitted data inside the current tenant. It never means all tenants.

---

## 4. Authorization architecture

The approved authorization model is:

**SANAD Hierarchical Unified Authorization Engine**

```text
Tenant-aware
+ Partner-aware
+ Hybrid RBAC
+ ABAC
+ ReBAC-lite
+ Capability Registry
+ Subscription Entitlement Gate
+ Commercial/Delegation Gate
+ Explicit User ALLOW/DENY
+ Dynamic Data Scopes
+ Protected System Roles (administration/mutation invariants — NOT a runtime allow source)
+ Break-Glass (explicit time-bounded audited grant, evaluated normally — never bypasses DENY)
+ Explainable Decisions
+ Event-driven Effective Permissions
+ Immutable Audit
```

### 4.1 Runtime decision algorithm (Revision B — authoritative)

The runtime evaluation is a five-stage algorithm. There is NO "protected safety" runtime precedence stage and NO allow source that outranks explicit DENY:

```text
A. HARD GUARDS
   - authenticated subject / security context
   - platform boundary
   - partner boundary
   - tenant boundary
   - active/known capability
   - commercial entitlement where applicable
   - tenant subscription entitlement where applicable
   Failure of any hard guard = DENY.

B. EXPLICIT DIRECT DENY
   - an active direct user DENY for the capability = DENY
   - For v1: DIRECT DENY IS CAPABILITY-WIDE.
     Scoped direct DENY is NOT supported in v1.

C. CANDIDATE ALLOW SOURCES
   - direct ALLOW
   - active Role Capability
   - applicable ABAC/ReBAC relationship policy
   - valid delegated-administration grant

D. DATA/RESOURCE SCOPE
   - at least one candidate ALLOW must also match the applicable
     scope/context; multiple valid ALLOW scopes union.

E. RESULT
   - valid candidate + valid scope = ALLOW
   - otherwise = DEFAULT DENY
```

Protected roles and break-glass protections are NOT a runtime allow precedence layer and NOT a bypass around explicit DENY. They are mutation invariants, protected administration constraints, recovery-path guarantees, and high-risk change safeguards (§5.1). Break-glass emergency access, if implemented, must itself create an explicit, time-bounded, audited authorization grant/override that is evaluated normally by this algorithm; the existence of a protected role never bypasses runtime DENY.

No role, override, policy, partner delegation, or scope may expand access beyond tenant or partner isolation.

### 4.2 Fail-closed behavior

The following always deny access:

```text
missing authorization context
unknown capability
inactive capability
partner mismatch
tenant mismatch
invalid scope
expired override
unresolved policy
missing required entitlement
```

No implicit fallback allow is permitted.

---

## 5. Roles and administrative levels

Protected system roles:

```text
PLATFORM_OWNER
PLATFORM_ADMIN
AGENT_SUPER_ADMIN
TENANT_ADMIN
```

`AGENT_CUSTOM_ADMIN` remains an administrative hierarchy level and a customizable partner-admin role. It is NOT a protected system role. It must not appear in the `protected_system_roles` registry, must not receive protected-role immutability treatment, and reclassifying it as protected requires a new explicit design decision approved by the owner.

Custom roles remain tenant/partner scoped.

The authority hierarchy is administrative, not an unrestricted inheritance chain:

```text
PLATFORM_OWNER
  > PLATFORM_ADMIN
    > AGENT_SUPER_ADMIN
      > AGENT_CUSTOM_ADMIN
        > TENANT_ADMIN
          > CUSTOM_ROLE
```

`AGENT_SUPER_ADMIN` means broad administration only inside that partner's authorized commercial/tenant scope. It never becomes `PLATFORM_ADMIN`.

### 5.1 Break-glass rules

The backend must reject changes that would:

- remove/deactivate the last tenant administrator;
- remove/deactivate the last partner administrator;
- remove the final recovery capability for a tenant or partner;
- allow a partner to grant itself platform permissions;
- allow a tenant administrator to manage control-plane roles;
- allow a partner to access another partner's tenants;
- allow a partner to alter its own protected commercial rate/settlement agreement;
- disable all administrative recovery paths.

The backend must simulate the proposed state before committing high-risk authorization changes.

Break-glass emergency access, where implemented, is not a runtime precedence layer. It creates an explicit, time-bounded, audited authorization grant/override (a direct ALLOW override row with a hard expiry and a mandatory reason) that is evaluated normally by the §4.1 algorithm. An active explicit direct DENY for the capability always beats a break-glass grant. Protected-role status never bypasses runtime DENY, never exempts a subject from hard guards, and never exempts a subject from entitlement checks.

---

## 6. Capability Registry

Capabilities are registered centrally and remain application-neutral.

Examples:

```text
CRM.CONTACT.READ
CRM.DEAL.UPDATE
HRM.EMPLOYEE.READ
HRM.LEAVE.APPROVE
HRM.PAYROLL.RUN
ERP.PURCHASE_ORDER.APPROVE
ACCOUNTING.JOURNAL.POST
SUBSCRIPTION.MANAGE
TENANT.ACTIVATE
AUTHORIZATION.RECOVER
```

Capability metadata should support:

```text
application
module
resource
action
risk_level
supports_scope
system_protected
```

Applications should depend on the registry and unified evaluator, not implement separate authorization engines.

### 6.1 Capability Contract Table (canonical vocabulary — one contract)

Every capability consumed by this design is listed once here. Delegated partner flows wrap the SAME canonical codes with `PartnerDelegationGate` + partner scope; semantically duplicate names (e.g. a parallel `PARTNER.BILLING.MANAGE` next to canonical `BILLING.MANAGE`) are forbidden.

| CAPABILITY | PRINCIPAL TYPES | SCOPE | DELEGATABLE | SYSTEM_PROTECTED | USED BY API | SEEDED IN MIGRATION |
|---|---|---|---|---|---|---|
| `BILLING.READ` | TENANT, PARTNER (delegated), PLATFORM | partner/tenant billing read | YES | no | `/api/v1/partner/invoices` (list/read), `/api/v1/partner/settlements` | exists (canonicalized from SCP `billing.read`, `V20260830_2` + `V20260901_1`) |
| `BILLING.MANAGE` | PARTNER (delegated), PLATFORM | partner billing administration | YES | no | partner invoice draft/issue, credit-note administration | `V20260925_5` (W2 — created once; consumed by platform and delegated surfaces) |
| `PARTNER.INVOICE.ISSUE` | PARTNER (delegated) | own bound tenants | YES | no | `/api/v1/partner/invoices` issue | `V20260927_9` (W4) |
| `PARTNER.CREDITNOTE.ISSUE` | PARTNER (delegated) | own bound tenants | YES | no | `/api/v1/partner/credit-notes` | `V20260927_9` (W4) |
| `SETTLEMENT.VIEW` | PARTNER (self read), PLATFORM | own periods/items | no | no | `/api/v1/partner/settlements`, executive settlements read | `V20260927_9` (W4) |
| `SETTLEMENT.FINALIZE` | PLATFORM only | platform | no | YES | `/api/v1/executive/settlements` finalize | `V20260927_9` (W4) |
| `PARTNER.PLATFORM.MANAGE` | PLATFORM only | platform | no | YES | `/api/v1/executive/partners/**` | `V20260925_5` (W2) |
| `TENANT.CREATE` / `TENANT.ACTIVATE` / `TENANT.SUSPEND` | PARTNER (delegated), PLATFORM | delegated tenant targets | YES | no | partner provisioning paths | `V20260925_5` (W2) |
| `TENANT.USER.MANAGE` / `TENANT.AUTHORIZATION.MANAGE` | PARTNER (delegated), PLATFORM | delegated tenant targets | YES | no | partner user/authorization delegation | `V20260925_5` (W2) |
| `SUBSCRIPTION.CREATE` / `SUBSCRIPTION.UPGRADE` / `SUBSCRIPTION.DOWNGRADE` / `SUBSCRIPTION.CANCEL` | PARTNER (delegated), PLATFORM | delegated tenants | YES | no | partner subscription delegation | `V20260925_5` (W2) |
| `COMMERCIAL.PROFILE.READ` / `COMMERCIAL.PROFILE.WRITE` | TENANT | own tenant | no | no | `/api/v1/commercial/**` | `V20260926_5` (W3) |
| `PARTNER.COMMERCIAL.READ` / `PARTNER.COMMERCIAL.WRITE` | PARTNER | own partner | no | no | `/api/v1/partner/commercial/**` | `V20260926_5` (W3) |
| `AUTHORIZATION.OVERRIDE.MANAGE` / `AUTHORIZATION.RELATIONSHIP.MANAGE` / `AUTHORIZATION.RESYNC` / `AUTHORIZATION.BREAK_GLASS` / `AUTHORIZATION.RECOVER` / `AUTHORIZATION.PLATFORM.MANAGE` | PLATFORM, TENANT admins (per code) | tenant/platform | no | YES | `/api/v1/access/**`, `/api/v1/executive/authorization/**` | `V20260924_4` (W1) |

`PARTNER.INVOICE.ISSUE` and `PARTNER.CREDITNOTE.ISSUE` are deliberately distinct from `BILLING.MANAGE`: they authorize creating binding financial documents, so a partner can be granted document issuance without full billing administration. They are used consistently across registry, delegation allowlist, controllers, tests, and UI gates (see §6.1 table).

---

## 7. RBAC + ABAC + ReBAC-lite

RBAC remains the human-administration layer. ABAC and relationship policies prevent role explosion.

Relevant attributes may include:

```text
user.department_id
user.branch_id
user.job_level
employee.manager_id
resource.owner_id
resource.department_id
resource.branch_id
resource.project_id
```

Relevant relationships may include:

```text
USER -> MANAGES -> EMPLOYEE
USER -> MEMBER_OF -> TEAM
EMPLOYEE -> BELONGS_TO -> DEPARTMENT
USER -> ADMIN_OF -> TENANT
PARTNER -> MANAGES -> TENANT
```

Relationships must be explicit and tenant-scoped. Identity or authorization must never be granted by fuzzy name/email matching.

---

## 8. User/employee model

The security principal is a user/subject. An employee is an HR business entity.

```text
USER / SUBJECT
  ├── authentication identity
  ├── tenant membership
  ├── partner membership if applicable
  ├── roles
  ├── direct overrides
  └── explicit employee link
        -> EMPLOYEE
             -> department
             -> manager
             -> team
             -> branch
             -> position
             -> legal entity
             -> business unit
```

A user may exist without an employee record. An employee may exist without a login account. Service accounts and future AI agents are also subjects.

---

## 9. Direct user overrides

Direct user overrides are first-class records.

Logical model:

```text
user_permission_overrides
- tenant_id
- partner_id nullable when direct tenant
- user_id
- capability_id
- effect: ALLOW | DENY
- scope_type nullable
- scope_reference nullable
- reason
- valid_from
- valid_until
- created_by
- created_at
- updated_at
```

Overrides participate in the runtime algorithm of §4.1 (Revision B). There is NO "protected safety" precedence stage:

```text
A. HARD GUARDS (subject/context; platform; partner; tenant; active/known
   capability; commercial entitlement where applicable; tenant subscription
   entitlement where applicable) — any failure = DENY
B. EXPLICIT DIRECT DENY — active direct user DENY for the capability = DENY
   (v1: capability-wide; scoped DENY not supported)
C. CANDIDATE ALLOW SOURCES — direct ALLOW; active Role Capability;
   applicable ABAC/ReBAC relationship policy; valid delegated grant
   (including time-bounded break-glass overrides)
D. DATA/RESOURCE SCOPE — at least one candidate ALLOW must match the
   applicable scope/context; multiple valid ALLOW scopes union
E. RESULT — valid candidate + valid scope = ALLOW; otherwise DEFAULT DENY
```

Database invariant for v1 (`user_permission_overrides`), enforced by a table CHECK constraint:

```text
effect = 'DENY'  =>  scope_type IS NULL AND scope_reference IS NULL
```

A scoped DENY insert is rejected by the database. A capability-wide DENY beats role ALLOWs, direct ALLOWs, relationship-policy ALLOWs, delegated grants, and break-glass overrides, and applies across every data scope. Protected-role status never bypasses this runtime result. `DecisionSource.PROTECTED_SAFETY` (an ALLOW-source/precedence concept) is removed from the engine vocabulary.

---

## 10. Data scopes

The approved dynamic scope family is extensible:

```text
SELF
OWN
DIRECT_REPORTS
REPORTING_TREE
TEAM
DEPARTMENT
ORG_UNIT
ORGANIZATION
BRANCH
BUSINESS_UNIT
LEGAL_ENTITY
PROJECT
TENANT_ALL
```

Each capability declares its allowed scope types. Invalid combinations deny access.

---

## 11. Effective permissions and explainability

Effective permissions are a computed projection/cache, not the source of truth.

The evaluator returns structured decisions, not only booleans.

Example:

```json
{
  "decision": "ALLOW",
  "capability": "HRM.LEAVE.APPROVE",
  "source": "ROLE",
  "role": "HR_MANAGER",
  "scope": "DEPARTMENT",
  "policy": "SAME_DEPARTMENT",
  "reason": "SUBJECT_DEPARTMENT_MATCH",
  "decisionId": "..."
}
```

A denial may report `EXPLICIT_DENY`, `TENANT_MISMATCH`, `PARTNER_MISMATCH`, `SCOPE_DENIED`, `ENTITLEMENT_DISABLED`, or another stable reason code.

### 11.1 Cache model

Logical key:

```text
tenantId:userId:authorizationVersion
```

Authorization changes increment the relevant version and invalidate only affected projections when practical.

Stale cache may never expand access. On uncertain state, the system must re-evaluate from authoritative sources or deny.

---

## 12. Entitlements versus permissions

Subscription entitlements and user authorization remain separate.

```text
Subscription Entitlement
= what products/modules/capabilities the tenant commercially owns

Authorization
= what the user may do inside those entitled products
```

A user role or direct override may not grant a capability outside the tenant's active commercial entitlement.

### 12.1 Recalculation controls

Two controls remain explicitly separate:

```text
Recalculate subscription entitlements
إعادة حساب استحقاقات الاشتراك
```

and:

```text
Resync effective permissions
إعادة مزامنة الصلاحيات الفعلية
```

Authorization updates recalculate/invalidate automatically. Manual permission resync is a recovery function only.

---

## 13. Delegated partner administration

A partner is a separate principal, not a normal tenant.

Partner administration may include explicitly delegated capabilities such as:

```text
TENANT.CREATE
TENANT.ACTIVATE
TENANT.SUSPEND
TENANT.USER.MANAGE
TENANT.AUTHORIZATION.MANAGE
SUBSCRIPTION.CREATE
SUBSCRIPTION.UPGRADE
SUBSCRIPTION.DOWNGRADE
SUBSCRIPTION.CANCEL
BILLING.READ
BILLING.MANAGE
```

Partner A must never access Partner B through caller-supplied IDs. The backend resolves partner scope from authenticated context and validates all targets.

Commercial administration does not imply access to CRM, HRM, payroll, accounting, or other tenant business data. Business-data access requires a separate explicit tenant-scoped delegation/role.

### 13.1 Partner user membership — one deterministic v1 model (Revision B)

A user may hold AT MOST ONE ACTIVE partner membership. This is enforced at the database level with a partial unique index equivalent to `UNIQUE(user_id) WHERE status = 'ACTIVE'` on the partner-membership table; historical SUSPENDED memberships may remain. JWT minting assumes one `partner_id`; this constraint is what makes that derivation deterministic. A future multi-partner-user model must use an explicit selected-partner session context and token exchange — never arbitrary first-ACTIVE-row selection. Two simultaneous ACTIVE membership inserts for the same user must fail at the database (concurrency-tested). Partner suspension or partner-admin removal must invalidate session authorization immediately by incrementing the affected user's `session_version` (existing `V13` mechanism + `SessionVersionCache.invalidate`) — never by waiting for a short-lived JWT membership cache (5 s) to expire.

---

## 14. Business identity and branding

Every commercial principal owns an independent business profile.

Applicable principal types:

```text
PLATFORM
PARTNER / AGENT / RESELLER / DISTRIBUTOR / SELLER
TENANT
```

Required business identity fields include:

```text
legal_name
trade_name
commercial_registration_number
tax_registration_number
country
city
national_address / structured addresses
billing_address
business_email
finance_email
phone_numbers
website
primary_logo
invoice_logo
verification_status
```

Branding and commercial identity are separate from login-user identity and security privileges.

### 14.1 UI requirement

A reusable **Commercial & Tax Information** screen must exist for:

- the SANAD platform owner/root account;
- every partner/agent/reseller/distributor/seller;
- every tenant/customer.

The screen supports structured commercial data, tax data, addresses, contact numbers, finance contacts, and logo uploads.

### 14.2 Future white-label readiness

Branding is modeled separately so future white-label/co-branding can be added without redesigning business identity or authorization.

### 14.3 Logo/asset storage — `platform_files` extension (repository-grounded, Revision B)

The existing `platform_files` table (`V20260911_2`) has exactly: `tenant_id NOT NULL`, `source_module`, `source_entity_type`, `source_entity_id`, `mime_type`, `size_bytes`, `checksum_sha256`, `classification`, `storage_reference`, `uploaded_by`. It has NO `kind` column and NO `partner_id` column. The design therefore:

- does NOT use a fabricated `platform_files.kind = 'BRAND_LOGO'` field;
- reuses the table without creating a duplicate blob/file registry, and does not fake a partner as a normal customer tenant;
- stores brand logos with `source_module='COMMERCIAL'`, `source_entity_type='BRAND_LOGO'`, `source_entity_id=<brand/business principal id>`;
- adds a NULLABLE `partner_id` column for partner-owned assets (additive migration);
- retains existing `tenant_id` compatibility for all current users of the table (workflow attachments and every other existing consumer);
- defines ownership without ambiguity:

```text
platform-owned asset: control-plane tenant context, partner_id NULL
partner-owned asset:  control-plane tenant carrier + partner_id set
tenant-owned asset:   tenant_id = actual tenant, partner_id NULL
```

  (partner assets may use the canonical control-plane tenant carrier only);
- enforces a CHECK preventing ambiguous ownership (a row with `partner_id` set must carry the control-plane carrier tenant, not a customer tenant);
- rewrites `platform_files` RLS as ONE fail-closed principal-aware policy (ENABLE + FORCE + `DROP POLICY IF EXISTS` first) or a proven non-overbroad policy set;
- mandates regression of ALL existing Workflow attachment/file-reference behavior, because `platform_files` is shared infrastructure.

---

## 15. Billing identity snapshots

Issued invoices must not depend on mutable live business-profile records.

At invoice issuance, capture immutable seller/buyer snapshots:

```text
seller legal/trade identity
seller tax identity
seller billing address
seller invoice branding
buyer legal/trade identity
buyer tax identity
buyer billing address
currency
contract/invoice references
```

Changing a company logo, address, or tax profile later must not alter historical invoices.

Post-issuance financial corrections use credit notes/adjustments, not silent mutation of historical financial records.

---

## 16. Commercial models

The platform should support these commercial ownership models:

```text
DIRECT
RESELLER
COMMISSION
HYBRID
```

The commercial model is explicit per partner agreement and, where allowed, per managed tenant/contract.

### 16.1 DIRECT

```text
Seller = SANAD
Buyer = Tenant
```

### 16.2 RESELLER

```text
Seller = Partner
Buyer = Tenant
SANAD settles/bills the Partner separately
```

### 16.3 COMMISSION

```text
Seller = SANAD
Buyer = Tenant
Partner receives commission according to agreement
```

### 16.4 HYBRID

The agreed commercial model is resolved per contract/tenant under platform-owner control.

---

## 17. Tenant billing by partners

Partners may issue invoices to their authorized tenants using their own commercial/tax identity.

Two modes are supported:

```text
MANUAL
AUTOMATIC
```

### 17.1 Manual billing

The system builds an invoice draft from the tenant subscription, billing period, plan/items, commercial identity, and contract terms. The authorized partner user reviews and issues it.

### 17.2 Automatic billing after trial

Trial expiration alone must not create a chargeable invoice.

Required lifecycle:

```text
TRIAL
 -> TRIAL_ENDING
 -> PENDING_CONTINUATION
 -> acceptance/continuation confirmed
 -> ACTIVE_BILLABLE
 -> invoice generated
 -> Finance authoritative invoice
 -> notification/audit
```

Automatic billing is allowed only when the subscription has automatic invoicing enabled and the customer is explicitly accepted/confirmed for continued paid service according to the product workflow. The confirmation source and timestamp must be auditable.

The authoritative carrier for the automatic-invoicing preference is the SUBSCRIPTION — a pre-invoice entity that exists when the decision is made. `tenant_subscriptions.auto_invoicing_enabled boolean NOT NULL DEFAULT false`. `billing_invoices` must NOT carry the controlling flag (an invoice does not exist when the automatic-invoicing decision is made). The scheduler condition is exactly:

```text
subscription ACTIVE_BILLABLE
AND tenant_subscriptions.auto_invoicing_enabled = true
AND auditable continuation confirmation exists
AND partner binding ACTIVE
AND required delegation AND effective commercial agreement valid
```

then create the invoice.

---

## 18. Partner settlement and SANAD-to-partner billing

SANAD may invoice a partner based on the partner's eligible collected tenant revenue.

The selected settlement basis is **B: actual net collected revenue**.

### 18.1 Settlement basis

For a settlement period:

```text
Eligible Net Collected Revenue
= eligible settled cash collections allocated to qualifying tenant invoices
- tax/VAT portions
- refunds allocated to those collections
- credit notes / negative financial adjustments
```

Discounts already reflected in the issued invoice are naturally reflected in the collected net amount and must not be subtracted a second time.

Payment-provider fees, exceptional rebates, or other commercial exclusions are governed by the versioned partner agreement and must never be silently hard-coded.

### 18.2 Platform charge percentage

The partner commercial agreement contains a clearly named versioned percentage, for example:

```text
platform_fee_percent
```

Then:

```text
SANAD partner charge
= Eligible Net Collected Revenue × platform_fee_percent
```

Example:

```text
Eligible Net Collected Revenue = 10,000 SAR
platform_fee_percent           = 20%
SANAD charge to Partner        = 2,000 SAR
```

The percentage must not be ambiguous with a partner commission percentage.

### 18.3 Agreement versioning

Commercial rate history is immutable and effective-dated.

```text
PartnerCommercialAgreement v1: 20%
PartnerCommercialAgreement v2: 15%
```

Each qualifying partner-issued tenant invoice binds to the effective agreement version at invoice issuance. Collections, refunds, and credit notes attributable to that invoice retain that bound agreement version for settlement. Later edits do not retroactively rewrite previous invoices or settlement economics.

Agreement versions must not overlap for the same agreement. The database enforces non-overlapping effective windows with an exclusion constraint — `EXCLUDE USING gist (agreement_id WITH =, tstzrange(effective_from, COALESCE(effective_to, 'infinity'::timestamptz), '[)') WITH &&)` with the `btree_gist` extension — the same mechanism family already used by `hr_org_unit_versions` (`V20260905_3`), so it is compatible with repository extension policy. A same-start concurrent insert race must admit exactly one row (constraint-enforced, not SELECT-before-INSERT).

### 18.4 Settlement lifecycle

```text
tenant invoice
 -> tenant payment collected
 -> Finance payment authoritative
 -> settlement eligibility projection
 -> partner settlement calculation
 -> review/finalization
 -> SANAD invoice to partner
 -> Finance authoritative invoice
 -> notification/audit
```

Settlement calculations must be replay-safe, traceable to tenant invoices/payments, and reconcilable.

### 18.5 Settlement period state machine (authoritative, Revision B)

One settlement period row exists per (partner, period window) — `UNIQUE(partner_id, period_start, period_end)`.

```text
(no row) --calculate--> CALCULATED --submit--> PENDING_APPROVAL --finalize--> FINALIZED --issue invoice--> INVOICED
                             ^    \
                             \____/ recalculate (allowed only before FINALIZED)
```

Before FINALIZED:

- one settlement period row exists;
- `CALCULATED` may be recalculated — recalculation rebuilds/reconciles the FULL period deterministically (full rebuild, never incremental delta);
- the SAME idempotency key is a strict no-op/replay (row-count and totals invariant);
- a NEW idempotency key may create a new run record and updates/replaces the non-finalized calculation transactionally (previous calculation items replaced atomically).

After FINALIZED:

- period economics are immutable;
- late refund/credit/collection adjustments do NOT rewrite finalized history;
- they create explicit ADJUSTMENT ITEMS in the next open settlement period, each referencing the original invoice/payment/period (§18.6).

Concurrent calculate/finalize is serialized on the period row; exactly one finalizer wins. "Incremental delta only" is NOT part of the model.

### 18.6 Late adjustments after finalization

Adjustment items live in a dedicated table (`partner_settlement_adjustment_items`) with FKs to the original invoice/payment and the source settlement period, carrying signed amounts. They participate in the next open period's eligible-net calculation. Finalized periods are never mutated.

### 18.7 Fee percentage authority and display (Revision B)

The settlement authority is the per-item bound `agreement_version_id` and its `platform_fee_percent`. A period-level `platform_fee_percent` is NOT persisted as an authoritative column (no "majority version" fee). Dashboards may display a derived, display-only statistic `weighted_effective_fee_percent = Σ(item.platform_charge_minor) / Σ(item.eligible_net_minor)` computed at read time with exact defined arithmetic; dashboards and accounting must never treat it as settlement authority.

---

## 19. Finance remains authoritative

This design does not create a second accounting ledger.

Finance remains authoritative for:

```text
accounting invoices
payments
journal entries
ledger truth
financial status
```

Subscription/partner billing services coordinate commercial calculations and projections, then integrate into Finance through explicit idempotent ports.

Existing live-payment activation controls remain unchanged. This design does not itself authorize live payment collection or production card charging.

---

## 20. Billing data model — logical additions

Exact physical names are implementation-plan decisions, but the following logical concepts are required:

```text
business_principals
business_profiles
business_addresses
business_contacts
brand_profiles
billing_accounts
partner_commercial_agreements
partner_commercial_agreement_versions
partner_tenant_bindings
partner_delegation_grants
partner_settlement_periods
partner_settlement_items
partner_settlement_invoices
invoice_party_snapshots
authorization_subjects
user_permission_overrides
role_permission_scopes
subject_relationships
authorization_change_events
effective_permission_projection
notification_events
notification_deliveries
partner_dashboard_projection
platform_dashboard_projection
```

All tenant-scoped and partner-scoped tables must carry explicit scope identifiers, RLS where applicable, and database-enforced tenant/partner integrity constraints.

---

## 21. Notifications

Introduce a central notification/activity-event capability.

Events requiring platform-owner visibility include at least:

```text
PARTNER_CREATED_USER
PARTNER_CREATED_TENANT
PARTNER_UPDATED_TENANT
PARTNER_ACTIVATED_TENANT
PARTNER_SUSPENDED_TENANT
PARTNER_CREATED_SUBSCRIPTION
PARTNER_UPDATED_SUBSCRIPTION
PARTNER_UPGRADED_SUBSCRIPTION
PARTNER_DOWNGRADED_SUBSCRIPTION
PARTNER_CANCELLED_SUBSCRIPTION
PARTNER_UPDATED_BUSINESS_PROFILE
PARTNER_CHANGED_ADMIN
TENANT_INVOICE_CREATED
TENANT_INVOICE_ISSUED
TENANT_PAYMENT_RECORDED
PARTNER_SETTLEMENT_CALCULATED
PARTNER_INVOICE_CREATED
PARTNER_INVOICE_ISSUED
```

Each event should capture:

```text
partner
actor
action
target
before/after summary where relevant
timestamp
correlation_id
```

For every partner-created account/tenant and every partner change to a tenant or subscription, an in-product notification to the primary `PLATFORM_OWNER` account is mandatory. Delivery may later expand to email/SMS/push according to notification preferences, but the durable in-product notification and audit event are required.

Notifications and audit are separate. Deleting/reading a notification never deletes audit evidence.

### 21.1 Notification scoping and RLS model (explicit, Revision B)

`notification_events` carries denormalized scope columns `scope CHECK IN ('PLATFORM','PARTNER','TENANT')`, `tenant_id`, `partner_id` with consistency CHECKs (`scope='TENANT'` ⇒ `tenant_id NOT NULL AND partner_id IS NULL`; `scope='PARTNER'` ⇒ `partner_id NOT NULL AND tenant_id IS NULL`; `scope='PLATFORM'` ⇒ both NULL). `notification_deliveries` denormalizes the same scope columns from its event (NOT NULL, consistency CHECKs), so every delivery row is independently RLS-scoped without joins. RLS is ENABLE + FORCE with explicit policies written in the migration (owner rows readable in control-plane context; partner rows only under the matching `app.partner_id` GUC; tenant rows only under the matching `app.tenant_id` GUC). A partner can never read platform-owner deliveries. Acknowledge/read is restricted to the recipient: `WITH CHECK` includes `recipient_user_id` congruence with the session identity, and the service re-validates ownership. Mandatory negative tests: Partner A cannot see Partner B deliveries; Tenant A cannot see Tenant B deliveries; a partner cannot read platform-owner deliveries; a recipient cannot acknowledge another recipient's delivery.

---

## 22. Executive dashboards

Two dashboard scopes are mandatory:

```text
PLATFORM GLOBAL DASHBOARD
PER-PARTNER DASHBOARD
```

The same governed projections should power both to prevent metric drift.

### 22.1 Platform owner global dashboard

The platform owner sees aggregate metrics across all partners/distributors/resellers/sellers and direct tenants.

Required metric families:

| Area | Metrics |
|---|---|
| Partners | total, active, inactive/suspended |
| Tenants | total, active, inactive, suspended |
| Trials | active trials, trial-ending, converted, expired |
| Subscriptions | active, trial, suspended, cancelled, expired |
| Accounts | active, inactive, invited/pending where applicable |
| Billing | invoices issued, collected, outstanding, overdue, draft, void/credited |
| Revenue | gross collected, eligible net collected, partner settlement base |
| Partner settlement | calculated, pending approval, finalized, invoiced, paid/outstanding |

Filters must support at least period, partner, tenant, subscription/plan, and billing/invoice status.

Drill-down:

```text
Platform
 -> Partner
   -> Tenant
     -> Subscription
       -> Invoice
```

### 22.2 Per-partner dashboard for platform owner

The owner can select one partner and see only that partner's governed metrics, including:

```text
tenant count
active/inactive/trial tenants
subscription counts
partner users/admins
customer invoices
collections
outstanding/overdue balances
eligible net collected revenue
current platform_fee_percent/current agreement version
estimated settlement
finalized settlement
SANAD-issued partner invoices
```

### 22.3 Partner/reseller/distributor/seller dashboard

A partner sees only its own scope.

Required billing panels:

```text
Customer Invoices
- draft
- issued/open
- paid/collected
- outstanding
- overdue
- credited/void

Collections
- gross collected
- net collected
- refunds/credits

SANAD Settlement
- current settlement period
- eligible net collected revenue
- applicable platform fee percentage
- estimated platform charge
- finalized settlement
- SANAD invoices to partner
- paid/outstanding status
```

Partner dashboards must not accept arbitrary `partnerId` as authority. Partner scope is derived from authenticated context and verified server-side.

### 22.4 Dashboard projection schema — exact typed design (Revision B)

Projection DDL is written column-exactly in the plans — no schematic placeholders such as `subscriptions_*`. The projection apply-log is typed per scope (one UUID projection_key is never overloaded to mean both a partner UUID and a platform `bucket_month DATE`):

```text
dashboard_projection_events(
  id uuid pk,
  projection_scope text NOT NULL CHECK (projection_scope IN ('PLATFORM','PARTNER')),
  partner_id uuid NULL REFERENCES partners(id),
  bucket_month date NULL,
  source_event_type text NOT NULL,
  source_event_id uuid NOT NULL,
  applied_at timestamptz NOT NULL DEFAULT now(),
  CHECK ( (projection_scope='PARTNER'  AND partner_id IS NOT NULL AND bucket_month IS NULL)
       OR (projection_scope='PLATFORM' AND partner_id IS NULL     AND bucket_month IS NOT NULL) ),
  UNIQUE (projection_scope, partner_id, bucket_month, source_event_type, source_event_id)
)
```

### 22.5 Global dashboard reconciliation invariant (corrected, Revision B)

```text
GLOBAL = DIRECT SANAD TENANTS + SUM(PARTNER DASHBOARDS)
```

under identical period, plan, tenant status, invoice status, and currency/commercial filters. Direct tenants are intentionally supported, so `GLOBAL = SUM(PARTNERS)` alone is WRONG and must never be asserted or tested. Reconciliation fixtures must contain at least 2 partners, multiple tenants per partner, and at least 1 direct SANAD tenant, and must reconcile every governed metric.

---

## 23. Invoice screens

Invoice management is visible from both platform-owner and partner surfaces.

### 23.1 Platform owner

Views:

```text
All Partner Customer Invoices (governed read/oversight)
SANAD -> Partner Invoices
Settlement-linked invoice detail
Tenant/customer invoice trace
Invoice/payment reconciliation
```

The owner can filter/drill down by partner, tenant, date, status, currency, and settlement period.

### 23.2 Partner/reseller/distributor/seller

Views:

```text
Partner -> Tenant Customer Invoices
Draft invoice creation
Automatic invoice results
Payment/collection status
Credit notes/adjustments
SANAD -> Partner invoices
Settlement trace
```

Cross-partner invoice access is always denied.

---

## 24. Executive/partner UI information architecture

### 24.1 Platform owner control plane

```text
Executive
├── Overview
├── Partners / Resellers / Distributors / Sellers
│   ├── Partner profile
│   ├── Commercial & Tax Information
│   ├── Branding
│   ├── Users & Admins
│   ├── Permissions
│   ├── Tenants
│   ├── Subscriptions
│   ├── Commercial Agreement
│   ├── Billing & Invoices
│   ├── Settlement
│   ├── Notifications
│   └── Audit
├── Tenants
├── Subscriptions
├── Billing
├── Global Partner Dashboard
├── Authorization
└── Audit
```

### 24.2 Partner portal

```text
Partner Portal
├── Dashboard
├── Commercial & Tax Information
├── Branding
├── Customers / Tenants
├── Subscriptions
├── Customer Invoices
├── Collections
├── SANAD Settlement / Partner Invoices
├── Users & Permissions
├── Notifications
└── Audit
```

### 24.3 Tenant administration

Each tenant retains an independent Commercial & Tax Information screen, its own branding/profile, subscription/billing view, users/permissions, employees, and business applications.

---

## 25. Authorization administration UI

A dedicated access-management surface is separate from subscription entitlements.

```text
Users & Permissions
├── Users
├── Roles
├── Capabilities
├── Policies & Scopes
└── Access Audit
```

User detail:

```text
Overview
Roles
Direct Permissions
Data Scopes
Effective Permissions
Audit
```

Protected roles are visibly immutable where appropriate, and attempts to violate break-glass rules are rejected server-side even if a client bypasses UI restrictions.

---

## 26. Event-driven invalidation and projections

Relevant changes emit typed domain events:

```text
RoleChanged
RoleCapabilityChanged
UserRoleChanged
UserOverrideChanged
ScopeChanged
EmployeeRelationshipChanged
PartnerDelegationChanged
SubscriptionEntitlementChanged
BusinessProfileChanged
TenantCreated
TenantUpdated
SubscriptionChanged
TenantInvoiceIssued
TenantPaymentCollected
PartnerSettlementCalculated
PartnerInvoiceIssued
```

Events drive:

```text
authorization cache invalidation
billing projections
notification delivery
dashboard projections
audit evidence
```

Source-of-truth mutations and required outbox/audit events must commit atomically where the domain requires it.

---

## 27. Audit requirements

High-value audit records must include as applicable:

```text
decision_id
tenant_id
partner_id
actor_user_id
target_user_id / target_role_id / target_tenant_id / target_invoice_id
operation
capability
effect
scope
before
after
reason
source
correlation_id
timestamp
```

Authorization DENY events, high-risk ALLOW operations, commercial-agreement changes, tenant lifecycle changes, invoice issuance, settlement finalization, and protected-role changes must be auditable.

---

## 28. API categories

Exact route names are implementation-plan details. Required API categories are fixed.

Authorization:

```text
roles
user-role grants
user overrides
effective permissions
capabilities
policies/scopes
evaluate
resync
authorization audit
```

Partner control plane:

```text
partners
partner admins/users
partner delegation
partner tenants
partner commercial agreements
partner business/tax profile
partner branding
```

Tenant commercial identity:

```text
tenant business/tax profile
tenant addresses/contacts
tenant branding
```

Billing/settlement:

```text
customer invoice draft/issue/read
billing profile/snapshot
collections
partner settlement preview/finalize
SANAD partner invoice
reconciliation
```

Dashboards/notifications:

```text
platform global dashboard
per-partner dashboard
partner self-dashboard
notification feed
notification read/ack state
```

---

## 29. Migration strategy

No big-bang replacement.

### Phase 1 — Compatibility foundation

Preserve current roles, capabilities, grants, `@RequireCapability`, entitlements, billing, and Finance ownership.

### Phase 2 — Unified authorization core

Make current capability evaluation a compatibility facade over the new decision engine.

### Phase 3 — Direct overrides and dynamic scopes

Introduce explicit user ALLOW/DENY, generalized scopes, explainable decisions, and break-glass rules.

### Phase 4 — Partner principal/delegation

Introduce partner boundaries, admins, tenant bindings, and partner-safe control-plane operations.

### Phase 5 — Business identity/branding

Add independent platform, partner, and tenant commercial/tax profiles and invoice snapshots.

### Phase 6 — Partner/customer billing

Enable partner-issued tenant invoices using existing authoritative Finance integration.

### Phase 7 — Settlement billing

Add net-collected settlement calculation and SANAD-to-partner invoicing.

### Phase 8 — Notifications and dashboards

Add event-driven partner notifications, global owner dashboard, per-partner owner dashboard, partner self-dashboard, and invoice dashboard panels.

### Phase 9 — Cutover and cleanup

Move remaining application-specific authorization/billing surfaces onto the unified model only after compatibility and security gates pass.

### 29.1 Progressive cutover — no big bang (Revision B)

All feature flags remain default OFF in code/config until their own gate is passed. Cutover is ordered, staged, and independently rollback-capable — flags are never all turned on in one commit or one deployment:

```text
G7-A Unified Authorization shadow/equivalence mode
G7-B Unified Authorization authoritative mode (after compatibility/security gate)
G7-C Partner Principal + Delegated Administration
G7-D Commercial Identity
G7-E Partner Billing / Trial Continuation
G7-F Settlement
G7-G Notifications + Dashboards
```

Each stage defines: exact precondition; same-SHA tests; negative security gates; observability; rollback flag; rollback trigger; post-enable smoke; evidence path.

---

## 30. Security invariants

The following are release-blocking invariants:

```text
Partner A -> Partner B data = DENY
Tenant A -> Tenant B data = DENY
Tenant Admin A -> Tenant B = DENY
AGENT_SUPER_ADMIN A -> Partner B = DENY
User override -> foreign tenant = DENY
TENANT_ALL -> current tenant only
Partner ownership -> no implicit tenant business-data access
Partner cannot edit its own protected settlement percentage
Partner cannot issue invoice for foreign tenant
Tenant cannot alter seller identity snapshot on issued invoice
```

Tenant/partner isolation must be enforced in backend and database controls, never only in the frontend.

---

## 31. Testing strategy

### 31.1 Authorization matrix

Subjects:

```text
Platform Owner
Platform Admin
Agent Super Admin
Agent Custom Admin
Tenant Admin
Custom User
Employee
Manager
Service Account
No-role User
```

Across:

```text
Partner A / Partner B
Tenant A / Tenant B
ALLOW / DENY
active/inactive roles
expired overrides
scope match/mismatch
subscription enabled/disabled
commercial delegation present/absent
```

### 31.2 Break-glass tests

Mandatory rejection tests:

```text
remove last Tenant Admin
remove last Agent Admin
remove final recovery capability
archive protected role
partner self-grants platform role
partner changes own protected commercial rate
partner binds foreign tenant
```

### 31.3 Billing/settlement tests

Mandatory tests include:

```text
manual tenant invoice by authorized partner
automatic invoice only after confirmed continuation and ACTIVE_BILLABLE
partner invoice uses partner seller snapshot
tenant buyer snapshot immutable after issuance
cross-partner invoice denied
settlement uses actual collected payments only
VAT/tax excluded from eligible net collected base
refund reduces eligible base
credit note reduces eligible base
invoice binds the effective agreement version at issuance
rate version does not retroactively change previous settlement economics
replay/idempotency of settlement generation
Finance invoice is authoritative
```

### 31.4 Dashboard tests

Global totals must reconcile with per-partner totals for the same filters and source period.

Partner dashboards must be tenant/partner isolated under direct API attempts, not merely UI navigation.

### 31.5 Environment

PostgreSQL Direct remains the required database test path. Security/RLS/tenant-isolation and partner-isolation tests are release gates.

### 31.6 Correction-mandated negative and concurrency tests (Revision B)

```text
scoped DENY insert rejected (DB CHECK)
capability-wide DENY beats role ALLOW
capability-wide DENY beats direct ALLOW
capability-wide DENY beats relationship-policy ALLOW
capability-wide DENY applies across every data scope
two simultaneous ACTIVE partner memberships: exactly one succeeds
partner suspension/admin removal invalidates sessions immediately (session_version)
two different Partner principals coexist; duplicate Partner principal rejected
two different Tenant principals coexist; duplicate Tenant principal rejected
second PLATFORM principal rejected
agreement v1 [T1,T2) accepted; v2 [T2,T3) accepted
agreement overlap rejected; same-start race admits exactly one row
open-ended active agreement version blocks overlapping later version until properly closed
settlement: concurrent calculate/finalize — exactly one finalizer wins
settlement: same idempotency key = strict no-op replay; new key = full deterministic rebuild
settlement: late refund after FINALIZED creates next-period adjustment item
platform_files: ambiguous-ownership insert rejected; workflow attachment regression suite green
notification: Partner A/B isolation; Tenant A/B isolation; partner cannot read owner deliveries; cross-recipient ack denied
dashboard: GLOBAL = DIRECT + SUM(PARTNERS) with ≥2 partners, multiple tenants per partner, ≥1 direct tenant
```

---

## 32. Release acceptance criteria

The feature set is not complete until all of the following are proven:

```text
Custom Roles
Role Permission Editing
Protected Platform/Tenant/Partner Roles
User-specific ALLOW/DENY
Dynamic Data Scopes
User <-> Employee explicit linkage
ABAC/ReBAC evaluation
Explainable decisions
Break-glass invariants
Automatic authorization invalidation
Manual effective-permission resync
Partner principal isolation
Delegated partner administration
Independent platform owner business/tax profile
Independent partner business/tax profile
Independent tenant business/tax profile
Logo/branding upload surfaces
Immutable invoice party snapshots
Partner -> Tenant manual invoicing
Partner -> Tenant automatic invoicing after confirmed continuation gate
Actual-net-collected settlement basis
Versioned partner percentage
Invoice-bound commercial agreement version
SANAD -> Partner invoice generation
Platform owner invoice dashboard
Per-partner invoice dashboard
Partner/reseller/distributor/seller invoice dashboard
Mandatory in-product owner notifications for partner-created accounts/tenants and tenant/subscription changes
Global partner dashboard
Per-partner owner dashboard
Partner self-dashboard
Immutable audit
Backward-compatible capability enforcement
Finance remains authoritative
PostgreSQL Direct security/isolation tests PASS
Frontend RTL/accessibility/mobile tests PASS
```

---

## 33. Non-goals and legal/compliance boundary

This specification does not claim final legal/tax compliance certification, ZATCA certification, or authorization to collect live card payments.

Tax rules, invoice fiscal requirements, e-invoicing requirements, provider activation, and production payment collection require separate compliance/activation gates where applicable.

The architecture must make these integrations possible without embedding unverified legal rules as hard-coded truth.

---

## 34. Final architecture decision

The approved target is:

**SANAD Hierarchical Multi-Principal SaaS Control Plane**

with:

```text
Unified Authorization Engine
+ Platform Administration
+ Partner / Agent / Reseller / Distributor / Seller Isolation
+ Tenant Isolation
+ Delegated Administration
+ Hybrid RBAC / ABAC / ReBAC-lite
+ Capability Registry
+ Subscription Entitlements
+ Business Identity & Branding
+ Separate Billing Accounts
+ Commercial Agreement Versioning
+ Partner-issued Tenant Invoices
+ Net-collected Partner Settlement
+ SANAD-issued Partner Invoices
+ Event-driven Notifications
+ Global Executive Dashboard
+ Per-Partner Dashboard
+ Invoice Dashboards
+ Protected Roles
+ Break-Glass
+ Explainable Decisions
+ Immutable Audit
```

This design intentionally separates security authority, commercial authority, accounting authority, and operational visibility so SANAD can scale to many applications, partners, tenants, users, and employees without weakening tenant isolation or creating parallel finance/security subsystems.
