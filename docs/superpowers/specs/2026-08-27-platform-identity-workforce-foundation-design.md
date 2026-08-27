# SNAD Platform Identity, Workforce & Access Foundation — Design Specification

**Date:** 2026-08-27  
**Status:** ARCHITECTURE APPROVED / WRITTEN SPEC AWAITING USER REVIEW / IMPLEMENTATION NOT STARTED  
**Repository:** `snadaiapp-png/SNAD`  
**Design branch:** `design/platform-identity-workforce-foundation`  
**Baseline `main` SHA at design creation:** `2dd8d1151ec0b231a51c13ee20722da6598e89e3`  
**Scope:** Platform-wide foundation consumed by CRM, ERP, Finance, HRM, POS, Analytics, Workflow, and future modules.

---

## 1. Executive Decision

SNAD SHALL use one shared platform foundation for identity, workforce, organization membership, and authorization.

The approved model is **Option B**:

- `User` is the platform security identity used for authentication, sessions, memberships, roles, and capabilities.
- `Employee` is the workforce/HR record.
- `User` and `Employee` have independent lifecycles.
- A human employee MAY be linked to one human user within the same tenant.
- A user MAY exist without an employee record.
- An employee MAY exist without a user account.
- Non-human identities (`SERVICE`, `INTEGRATION`) MUST NOT be linked to employees.
- CRM, ERP, Finance, POS, and other business modules MUST NOT create module-specific identity or workforce sources of truth.

Target relationship:

```text
Employee 0..1 ───── 0..1 User
```

The physical link remains `hr_employees.user_id`, hardened by tenant-safe referential integrity and uniqueness.

---

## 2. Goals

This foundation SHALL:

1. provide one source of truth for platform security identities;
2. provide one source of truth for employees, departments, positions, and manager hierarchy;
3. provide one tenant-scoped RBAC system consumed by all modules;
4. separate HR lifecycle from security lifecycle;
5. support tenant-wide and organization-scoped role grants;
6. compute effective permissions from authoritative sources instead of duplicating permission state;
7. provide a unified `/admin` operational center;
8. enforce tenant isolation at both application and PostgreSQL layers;
9. preserve historical user references after suspension, termination, or archival;
10. provide auditable onboarding, access change, and offboarding flows;
11. reuse existing tables/services wherever authoritative;
12. use forward-only migrations that fail on incompatible production data instead of silently rewriting it.

---

## 3. Non-Goals

This program SHALL NOT:

- create `crm_users`, `erp_users`, `finance_users`, `pos_users`, or equivalent module-local identity tables;
- merge `users` and `hr_employees`;
- introduce a generic `Person`/MDM master entity;
- equate job positions with security roles;
- introduce direct tenant-plane `User → Capability` grants;
- persist `user_effective_permissions` as an editable source of truth;
- hard-delete identities with operational history;
- redesign CRM ownership, ERP workflows, or Finance approval semantics beyond integrating them with the shared foundation;
- rely on frontend visibility as an authorization boundary;
- bypass branch protection, RLS, entitlement, or backend capability enforcement.

---

## 4. Architectural Boundaries and Sources of Truth

| Concern | Source of truth | Rule |
|---|---|---|
| Authentication identity | IAM / `users` | Platform-wide security principal |
| User account status | IAM / `users` | Independent from employment status |
| Credentials / sessions / MFA | IAM security subsystem | Reconcile in PF-01; never duplicate into HR |
| Tenant | SaaS Core | Authoritative tenant boundary |
| Organization | Organization domain | Sub-scope inside tenant |
| Membership | `organization_memberships` | Invitation-first linkage to organization/user |
| Roles | Access Control / `roles` | Tenant-scoped |
| Capability catalog | `access_capabilities` | Platform catalog |
| Role capabilities | `role_capabilities` | Tenant role → platform capability |
| User role grants | `user_role_assignments` | Tenant-wide or organization-scoped |
| Employee | `hr_employees` | Workforce source of truth |
| Department | `hr_departments` | Workforce hierarchy |
| Position | `hr_positions` | Job position, not security role |
| Manager hierarchy | `hr_employees.manager_id` | Workforce hierarchy |
| CRM record ownership | CRM | References platform user; CRM owns ownership semantics only |
| ERP ownership/approval | ERP / Workflow | References platform identities |
| Finance approval/history | Finance / Workflow | References platform identities |
| Module entitlement | SaaS module/subscription lifecycle | Can only reduce module access |
| Audit | Platform + domain audit | Correlated, immutable where required |

A business module MAY reference `User.id` or `Employee.id`, but MUST NOT create a duplicate source of truth for identity, role, department, position, manager, or employment status.

---

## 5. Existing Repository Assets to Preserve

The program is consolidation/hardening, not a rebuild.

### Tables to keep

- `users`
- `organization_memberships`
- `roles`
- `access_capabilities`
- `role_capabilities`
- `user_role_assignments`
- `hr_employees`
- `hr_departments`
- `hr_positions`

### Existing API/domain surfaces to preserve and harden

- `/api/v1/users`
- `/api/v1/users/{userId}/memberships`
- `/api/v1/organizations/{organizationId}/memberships`
- `/api/v1/access/roles`
- `/api/v1/access/capabilities`
- `/api/v1/access/users/...`
- `/api/v1/hr/employees`

Exact routes may be extended, but existing contracts SHALL not be duplicated gratuitously.

---

## 6. Core Domain Model

```text
Tenant
│
├── Users
│    └── User Role Assignments
│            └── Roles
│                  └── Role Capabilities
│                          └── Access Capability Catalog
│
├── Organizations
│    └── Organization Memberships ─────► User (nullable while invited)
│
└── Workforce
     ├── Departments
     ├── Positions
     └── Employees
          ├── user_id ────────────────► User (optional 1:1)
          ├── department_id ──────────► Department
          ├── position_id ────────────► Position
          └── manager_id ─────────────► Employee
```

### 6.1 User

`User` represents security identity, not employment.

Target lifecycle:

```text
INVITED → ACTIVE → SUSPENDED → ARCHIVED
```

Existing `INACTIVE` remains a compatibility concern to reconcile in PF-01 before any state migration.

The platform SHALL have an explicit identity classification, unless PF-01 proves an equivalent authoritative field already exists:

```text
HUMAN
SERVICE
INTEGRATION
```

Only `HUMAN` users may link to employees.

### 6.2 Employee

Employee lifecycle:

```text
ACTIVE
ON_LEAVE
SUSPENDED
TERMINATED
```

Employee owns workforce attributes including employee number, department, position, manager, employment type, hire date, termination date, and HR metadata.

### 6.3 Position is not Role

Mandatory invariant:

```text
Position != Role
```

A workforce change MAY trigger an access review or recommendation; it MUST NOT silently grant/revoke a role unless an explicitly approved tenant policy/workflow performs that action.

---

## 7. User ↔ Employee Link

The approved physical link remains `hr_employees.user_id`.

### Required same-tenant optional 1:1 invariant

```sql
CREATE UNIQUE INDEX ...
ON hr_employees (tenant_id, user_id)
WHERE user_id IS NOT NULL;
```

and a tenant-safe relationship equivalent to:

```text
(hr_employees.tenant_id, hr_employees.user_id)
    → users(tenant_id, id)
```

Before adding constraints, a forward-only reconciliation migration MUST detect violations and fail without mutating ambiguous data.

Rules:

- both records must belong to the same tenant;
- `SERVICE`/`INTEGRATION` users cannot link to employees;
- unlinking deletes neither record;
- linkage may remain for suspended/archived users for history;
- link/unlink operations are audited.

---

## 8. Workforce Referential Integrity

Workforce references SHALL become tenant-safe at PostgreSQL level:

```text
(hr_employees.tenant_id, department_id)
    → hr_departments(tenant_id, id)

(hr_employees.tenant_id, position_id)
    → hr_positions(tenant_id, id)

(hr_employees.tenant_id, manager_id)
    → hr_employees(tenant_id, id)

(hr_positions.tenant_id, department_id)
    → hr_departments(tenant_id, id)

(hr_departments.tenant_id, parent_department_id)
    → hr_departments(tenant_id, id)
```

Supporting unique keys on `(tenant_id, id)` SHALL be added where absent.

Manager and department hierarchies MUST reject cycles at the application layer. Database-level cycle protection may be added later only as an additive, reviewed invariant.

---

## 9. Tenant Authority

### 9.1 Tenant plane

Authenticated tenant-plane requests SHALL derive `tenantId` from validated security context/JWT:

```text
Authenticated request
      ↓
SecurityContext / tenant_id claim
      ↓
Backend tenant scope
```

The browser SHALL NOT choose authority using `?tenantId=` or request-body tenant fields.

### 9.2 Compatibility migration for legacy query parameters

Existing APIs that still accept `tenantId` MUST be migrated without an uncontrolled breaking change:

1. backend authority switches to `SecurityContextUtils.tenantId(auth)`;
2. during a compatibility window, a supplied legacy `tenantId` may be accepted only as a redundant assertion and MUST equal the authenticated tenant, otherwise `403`;
3. web/API clients are migrated to stop sending it;
4. the legacy parameter is then removed/deprecated according to the repository's API compatibility policy.

At no stage may client-supplied tenant data override authenticated tenant authority.

### 9.3 Control plane

Cross-tenant operations are allowed only through explicit Control Plane APIs with dedicated platform capabilities, audit, and policy checks. Tenant Plane and Control Plane authority remain distinct.

---

## 10. PostgreSQL RLS Model

Required posture:

```text
Missing tenant context → DENY / zero tenant rows
Tenant mismatch        → DENY
Tenant match           → ALLOW subject to policy
```

Policies that allow access when `current_setting('app.tenant_id', true)` is null are incompatible with this target and SHALL be reconciled.

For tenant-owned foundation tables PF-02 SHALL evaluate/apply, based on actual runtime schema:

- `ENABLE ROW LEVEL SECURITY`;
- `FORCE ROW LEVEL SECURITY` where appropriate for the runtime application role;
- fail-closed `USING`;
- fail-closed `WITH CHECK`;
- least-privilege application grants;
- PostgreSQL Direct cross-tenant tests.

RLS closure is invalid unless verified through the runtime-equivalent least-privilege application role.

---

## 11. Authorization Model

Effective permission is an intersection of authoritative gates:

```text
Authenticated User
∩ User Status
∩ Required Membership Status
∩ Active User Role Grant
∩ Active Role
∩ Active Capability
∩ Applicable Module Entitlement
∩ Organization Scope
∩ Runtime / Resource Policy
```

Default decision is **DENY / fail closed**.

Decision flow:

```text
Authenticated?
  ├─ no → DENY
  ▼
User ACTIVE?
  ├─ no → DENY
  ▼
Required membership valid?
  ├─ no → DENY
  ▼
Applicable module/core entitlement valid?
  ├─ no → DENY
  ▼
Required capability present through active role grant?
  ├─ no → DENY
  ▼
Organization scope valid?
  ├─ no → DENY
  ▼
Runtime/resource policy satisfied?
  ├─ no → DENY
  ▼
ALLOW
```

Frontend visibility is UX only. Backend authorization remains authoritative.

---

## 12. Roles, Capabilities, and Grants

### 12.1 Roles

Roles are tenant-scoped capability bundles.

The platform SHALL distinguish:

```text
SYSTEM
CUSTOM
```

unless PF-01 discovers an equivalent existing classification. Existing roles must be classified deterministically before a new constraint is enforced.

- system-role semantics are platform governed;
- authorized Tenant Admins can create/modify custom roles;
- business authorization SHOULD check capabilities rather than hard-coded role names.

### 12.2 Capability catalog

Capabilities are platform catalog entries such as:

```text
USER.*
MEMBERSHIP.*
ROLE.*
CAPABILITY.*
HR.*
CRM.*
ERP.*
FINANCE.*
POS.*
WORKFLOW.*
ANALYTICS.*
```

Modules register their capability contract; Access Control owns role composition and role grants.

### 12.3 No direct tenant-plane capability grants

Tenant-plane authorization remains:

```text
User → Role → Capability
```

A one-user exception is modeled as a custom role rather than a direct user-capability grant.

### 12.4 User role grants

`user_role_assignments` remains authoritative.

A grant is:

- tenant-wide when `organization_id IS NULL`; or
- organization-scoped when `organization_id` is populated.

The null-scope uniqueness race SHALL be closed with a database invariant equivalent to:

```sql
UNIQUE (tenant_id, user_id, role_id)
WHERE organization_id IS NULL;
```

Organization-scoped uniqueness remains enforced.

Temporary/auditable access requires lifecycle metadata. The implementation SHALL introduce or reuse authoritative equivalents for:

- `effective_from`;
- `effective_until`;
- `revoked_at`;
- `revoked_by`.

If an existing authoritative lifecycle mechanism is discovered in PF-01, it SHALL be reused instead of adding duplicate columns.

Expired/revoked grants never contribute to effective access.

---

## 13. Module and Core Entitlements

Entitlement is a higher-order gate for capabilities whose owning feature/module is entitlement-controlled.

Example:

```text
RBAC: HR.EMPLOYEE.READ present
Tenant HRM entitlement disabled
Final = DENY
```

Core platform capabilities such as IAM/organization/access administration MUST NOT be accidentally denied merely because they do not map to an optional paid module. PF-01/PF-04 SHALL classify capability ownership as either:

- core platform capability; or
- entitlement-controlled module capability.

For entitlement-controlled modules, RBAC can never reactivate a disabled module. The effective-access service reads the existing authoritative subscription/module lifecycle and does not duplicate entitlement state.

---

## 14. Effective Access Service

A central read model SHALL expose authenticated effective access, for example:

```text
GET /api/v1/access/me/effective-permissions
```

Response SHOULD include:

- user id;
- tenant id;
- active organizations/scopes;
- roles/grant scopes;
- effective capabilities;
- entitlement results where applicable;
- machine-readable access explanation metadata.

This endpoint supports UI/support observability only; business APIs independently enforce authorization.

The administration experience SHALL also support explanation such as:

```text
Why does this user have CRM.OPPORTUNITY.APPROVE?
```

with a trace of membership, grant, role, capability, entitlement, organization scope, and runtime policy. A deny trace SHOULD report the authoritative failed gate without exposing secrets.

No editable `user_effective_permissions` source-of-truth table is introduced. Cache/projections are allowed only as disposable derived state with invalidation/consistency rules defined in the implementation plan.

---

## 15. Lifecycle and Access Governance

### 15.1 Employee creation and access provisioning

Creating Employee does not automatically create User.

When access is requested:

```text
Employee created
→ Create or link User
→ Activate required membership
→ Grant roles
→ Verify effective access
```

This is application orchestration/Saga semantics, not one destructive cross-domain transaction. The central Workflow Engine may later orchestrate it, but the first implementation MUST NOT depend on an unavailable workflow capability. If access provisioning fails, the valid employee remains and the UI reports incomplete setup with retry/remediation.

### 15.2 Department/position change

A workforce move triggers access review rather than silent privilege accumulation. Proposed grants/revocations require explicit approved policy or administrator action.

### 15.3 Suspension

`User.status = SUSPENDED` is independent of employee status and denies operational access/revokes sessions according to IAM policy.

`Employee.status = SUSPENDED` may trigger an access policy/workflow; it does not silently mutate IAM through a database trigger.

### 15.4 Termination / Offboarding

Employee termination SHALL start auditable offboarding before final user archival:

```text
Employee TERMINATED
→ freeze security exposure
→ revoke sessions
→ revoke privileged/temporary access
→ transfer business work
→ close memberships according to policy
→ audit closure
→ archive user when required gates are complete
```

Business handover may include CRM ownership, queues/tasks, ERP approvals, Finance approvals, and Workflow assignments/escalations.

Historical identities are preserved; no hard delete is used for identities referenced by operational records.

### 15.5 Rehire

Rehire SHOULD reuse the existing employee/user identity after review when legally/operationally appropriate, but old roles MUST NOT be restored automatically.

---

## 16. Unified `/admin` Information Architecture

Approved tenant administration surface:

```text
/admin
├── overview
├── users
├── employees
├── organizations
├── memberships
├── departments
├── positions
├── roles
├── capabilities
├── access
└── audit
```

Business modules MUST NOT duplicate this as `/crm/users`, `/erp/users`, etc.

### `/admin/overview`

Operational exception dashboard: active/invited/suspended users, employees without accounts, expiring grants, terminated employees with active accounts, privileged accounts requiring review, failed offboarding, and unlinked human identities.

### `/admin/users`

Invite/create, activate/suspend/archive, resend invitation, revoke sessions, link employee, manage memberships/roles, inspect effective permissions and audit.

### `/admin/users/{id}`

Tabs: Overview, Memberships, Employee Link, Roles, Effective Access, Sessions, Audit. Workforce fields may be displayed but edited through the employee record.

### `/admin/employees`

Create/edit, department/position/manager change, suspend/terminate, create/link system account, start offboarding.

### `/admin/organizations`

Tenant-scoped organization directory/administration according to existing Organization domain authority. This page MUST NOT become a Control Plane tenant switcher. Exact create/update/archive actions are reconciled against existing organization APIs in PF-03.

### `/admin/departments`

Hierarchical management with archive semantics. Referenced departments are not hard-deleted.

### `/admin/positions`

Position management with department, grade, status, description. No hard Position→Role coupling.

### `/admin/memberships`

Invite/activate/deactivate/remove organization memberships and inspect scoped role grants.

### `/admin/roles`

Create/clone/edit custom roles, manage capability composition, lifecycle, users/scopes; protect system-role semantics.

### `/admin/capabilities`

Tenant Admin: read/search/filter allowed catalog. Platform Admin: platform-governed lifecycle/metadata operations.

### `/admin/access`

Effective Access Explorer answers both:

- What can this user do?
- Who can perform this capability in this organization?

### `/admin/audit`

Filter by actor, target user, employee, action, module, organization, time, correlation id, result. Sensitive mutations capture before/after where safe and compliant.

---

## 17. Platform Admin vs Tenant Admin

### Platform / Control Plane Admin

Explicitly authorized to manage platform-level concerns such as tenants, global capability catalog, protected system roles/policies, cross-tenant operations, and platform audit.

### Tenant Admin

May manage only authenticated tenant scope: users, employees, organizations within tenant authority, memberships, departments, positions, custom roles, grants, and tenant audit.

Tenant Admin SHALL NOT:

- access another tenant;
- redefine platform capability codes;
- alter protected system-role semantics;
- bypass module entitlement;
- use `/admin/organizations` as a cross-tenant control plane.

---

## 18. Audit Model

Sensitive identity/access/workforce changes SHALL record at minimum:

```text
WHO
WHAT
TARGET
BEFORE
AFTER
WHEN
TENANT
ORGANIZATION (when applicable)
CORRELATION_ID
RESULT
```

Mandatory auditable events include user lifecycle, session revocation, employee lifecycle, employee-user linking, membership lifecycle, role/capability composition changes, grants/revocations/expiry, offboarding state changes, and access-review decisions.

Existing platform/domain audit infrastructure SHOULD be reused; this program does not require one giant replacement audit table.

---

## 19. Data Migration and Reconciliation Strategy

All database changes are **forward-only Flyway migrations**. Previously applied migrations MUST NOT be rewritten.

Before adding constraints, deterministic preconditions SHALL detect:

- one user linked to multiple employees;
- cross-tenant employee-user links;
- cross-tenant department/position/manager references;
- duplicate tenant-wide role grants;
- invalid organization scope;
- data incompatible with new user/role classifications.

If incompatible data exists, migration SHALL fail and produce actionable reconciliation evidence. It SHALL NOT delete, relink, or select a winner automatically.

New hardening migrations SHOULD use explicit preconditions/postconditions rather than broad `IF NOT EXISTS` patterns that can mask partial schema drift.

No migration may introduce cascading deletion that erases identities referenced by CRM, ERP, Finance, Workflow, or audit history.

---

## 20. Mandatory Database Hardening

### H1 — Employee/User tenant-safe optional 1:1

- composite same-tenant FK;
- partial unique `(tenant_id, user_id)` where non-null;
- application validation for tenant and user type.

### H2 — Workforce composite tenant FKs

- employee → department;
- employee → position;
- employee → manager;
- position → department;
- department → parent department.

### H3 — HR RLS fail-closed

- inspect actual runtime schema/policies first;
- eliminate permissive null-tenant behavior where present;
- add fail-closed `USING`/`WITH CHECK`;
- evaluate/apply `FORCE RLS` with runtime role;
- prove isolation with PostgreSQL Direct.

### H4 — Tenant-wide role-grant uniqueness

- detect duplicate null-organization grants;
- add partial uniqueness for tenant-wide grants;
- preserve org-scoped uniqueness;
- prove concurrent grant behavior.

---

## 21. API Reconciliation Targets

No gratuitous API version churn is required. Existing endpoints are retained where practical and hardened.

Required logical capabilities:

### Users

List/get/create/invite/update, activate/suspend/archive, session revocation, employee-link inspection/action.

### Memberships

List/invite/activate/deactivate/remove; tenant authority derives from authenticated context in Tenant Plane.

### Roles/grants

List/get/create/update roles according to authority, manage role capabilities, grant/revoke scoped and temporary roles, list user grants.

### Effective access

Authenticated effective permissions plus authorized admin explanation/explorer queries.

### Workforce

Employees, departments, positions, manager hierarchy, employee-user link/unlink, termination/offboarding initiation.

Exact paths/DTOs are finalized in the implementation plan after endpoint inventory so existing contracts are not duplicated.

---

## 22. Security Requirements

1. Default authorization is DENY.
2. Tenant Plane derives tenant from authenticated security context.
3. Client-supplied tenant can never override authenticated tenant.
4. Cross-tenant operations require explicit Control Plane authority.
5. RLS is fail-closed defense in depth.
6. Backend always revalidates authorization.
7. Optional-module entitlement can deny module access even if RBAC grants a capability.
8. Core platform capability classification prevents accidental dependency on optional-module entitlement.
9. Inactive roles/capabilities/grants confer no access.
10. Revoked/expired grants confer no access.
11. Suspended/archived users confer no operational access.
12. Direct tenant-plane user capability grants are prohibited.
13. Service/integration users cannot link to employees.
14. Sensitive access mutations are audited.
15. Access-explanation APIs expose no credentials/secrets.
16. Historical identities remain for traceability.

---

## 23. Failure Semantics

Foundation operations MUST be failure-safe:

- employee creation may succeed while access setup fails; report incomplete provisioning and retry path;
- role-grant failure does not roll back a valid employee;
- offboarding failure leaves the identity in the safest achieved state and creates an actionable exception;
- migration reconciliation failure leaves data/schema unchanged;
- missing tenant context fails closed;
- missing entitlement/capability/policy decision fails closed;
- UI never shows a successful mutation before authoritative backend success.

---

## 24. Implementation Sequence

```text
PF-01  Platform Identity & Schema Reconciliation
PF-02  Tenant Authority + RLS Hardening
PF-03  Organization Membership Consolidation
PF-04  Role + Capability Catalog Hardening
PF-05  User Role Grant Hardening
PF-06  Workforce Directory Completion
PF-07  User ↔ Employee Linking
PF-08  Departments + Positions APIs/Integrity
PF-09  Manager Hierarchy
PF-10  Effective Access Service
PF-11  Offboarding + Audit Governance
PF-12  Unified /admin Operational UI
PF-13  Cross-Module Authorization + PostgreSQL Acceptance
PF-14  Human Visual/Operational Acceptance
```

Each stage begins by reconciling actual repository/runtime state before mutation. Unrelated CRM/ERP/Finance feature work is not part of this program.

---

## 25. Acceptance Criteria

### Identity / workforce

- AC-01: Human User can exist without Employee.
- AC-02: Employee can exist without User.
- AC-03: Human User links to at most one Employee per tenant.
- AC-04: Service/Integration User cannot link to Employee.
- AC-05: Cross-tenant Employee↔User link is rejected by app and DB.
- AC-06: Cross-tenant department/position/manager references are rejected by DB.

### Membership / roles

- AC-07: Invitation-first organization membership remains supported.
- AC-08: Tenant-wide role grant cannot duplicate under concurrency.
- AC-09: Organization-scoped grant remains unique per user/role/org.
- AC-10: Revoked/expired grant contributes no effective permission.
- AC-11: Custom roles compose allowed capabilities without direct user-capability grants.

### Authorization

- AC-12: Missing authentication → DENY.
- AC-13: Suspended/archived user → DENY.
- AC-14: Required invalid/inactive membership → DENY.
- AC-15: Disabled applicable module entitlement overrides module RBAC → DENY.
- AC-16: Missing capability → DENY.
- AC-17: Invalid organization scope → DENY.
- AC-18: Runtime/resource-policy failure → DENY.
- AC-19: Access explanation identifies authoritative grant path or deny gate.
- AC-20: Core platform administration capability is not accidentally dependent on an unrelated optional module entitlement.

### Tenant / PostgreSQL security

- AC-21: Tenant-plane API cannot switch tenant via query/body input.
- AC-22: During compatibility window, mismatched legacy `tenantId` assertion returns `403`.
- AC-23: Missing `app.tenant_id` under runtime role exposes no tenant rows.
- AC-24: Tenant A cannot read/write Tenant B workforce records.
- AC-25: `WITH CHECK` prevents cross-tenant insert/update.
- AC-26: RLS is verified using PostgreSQL Direct and runtime-equivalent least-privilege role.

### Lifecycle

- AC-27: Employee creation does not implicitly create User unless explicit access setup is requested.
- AC-28: Position/department change does not silently grant Role.
- AC-29: User suspension blocks operational access without changing Employee employment state.
- AC-30: Employee termination starts offboarding and preserves historical identity.
- AC-31: Rehire does not silently restore old roles.

### Admin UI

- AC-32: `/admin/users` is operational for identity/access lifecycle.
- AC-33: `/admin/employees` is operational for workforce and user linking.
- AC-34: `/admin/roles` supports custom role composition and protects system roles.
- AC-35: `/admin/access` answers user→capability and capability→users using effective access.
- AC-36: `/admin/audit` exposes authorized identity/access/workforce mutations.
- AC-37: Tenant Admin cannot access Control Plane operations.
- AC-38: `/admin/organizations` remains tenant-scoped and cannot switch tenant authority.
- AC-39: Business modules need no module-specific platform-user management page.

### Regression / integration

- AC-40: Existing CRM user references remain valid.
- AC-41: Existing ERP user/audit references remain valid.
- AC-42: Existing authentication/session behavior remains compatible or has an explicit migration path.
- AC-43: Required repository CI, PostgreSQL acceptance, security checks, and relevant frontend E2E are green on final merge candidate.
- AC-44: Human operational acceptance is completed on isolated preview before production merge when required by release governance.

---

## 26. Risks and Mitigations

**Production schema drift:** PF-01 forensic inventory; never assume migration source equals runtime schema.  
**Authorization regression:** endpoint-by-endpoint reconciliation, preserve capability codes, effective-access tests.  
**Privilege accumulation:** Position != Role; access-review workflow and explicit revoke/grant.  
**Fail-open HR RLS:** PF-02 runtime-role PostgreSQL tests before/after hardening.  
**Concurrent duplicate grants:** database partial unique invariant plus transactional handling.  
**Offboarding orphan work:** auditable handover orchestration and actionable failed state.  
**Breaking legacy tenantId clients:** staged compatibility assertion, client migration, then deprecation/removal.  
**Over-broad entitlement gating:** classify capabilities into core vs entitlement-controlled ownership.  
**`/admin` becoming a monolith:** shared shell but bounded feature routes/clients; backend domains retain ownership.

---

## 27. Architectural Invariants

The following MUST NOT change during implementation without explicit design approval:

1. `Employee != User`.
2. `Position != Role`.
3. Business modules do not own platform users or employees.
4. User↔Employee link is optional 1:1 and tenant-safe.
5. Tenant-plane tenant authority comes from authenticated security context.
6. Effective access is derived, not independently editable source-of-truth state.
7. Tenant-plane capabilities are granted through Roles, not directly to Users.
8. Applicable module entitlement can only reduce access, never be bypassed by RBAC.
9. Core platform capabilities are explicitly classified so optional module entitlement does not disable the platform foundation.
10. Authorization is default-deny/fail-closed.
11. RLS fails closed for missing/mismatched tenant context.
12. Historical identities are preserved for audit/business traceability.
13. Existing authoritative tables are consolidated/hardened, not duplicated.
14. Database migrations are forward-only and stop on unresolved incompatible data.
15. `/admin` is the unified tenant administration surface for identity/workforce/access.
16. CRM, ERP, Finance, HRM, POS, and future modules consume this foundation through stable platform contracts.
17. Legacy client-supplied tenant parameters are migrated compatibly but never remain authoritative.
18. Temporary access has explicit authoritative validity/revocation metadata.

---

## 28. Self-Review Record

The written specification was self-reviewed for placeholders, contradictions, ambiguity, and scope. The review closed these issues before user review:

1. document status now distinguishes approved conversational architecture from pending written-spec approval;
2. entitlement gating now distinguishes core platform capabilities from entitlement-controlled module capabilities;
3. tenant-authority migration now defines a compatibility sequence for legacy `tenantId` clients while preserving fail-closed authority;
4. temporary role-grant lifecycle metadata is mandatory unless an equivalent authoritative mechanism already exists;
5. `/admin/organizations` is explicitly tenant-scoped and cannot become a Control Plane tenant switcher;
6. onboarding orchestration does not require an unavailable central Workflow Engine for the first implementation.

No `TBD`, `TODO`, or unresolved design choice is intentionally left in this document. PF-01 contains forensic reconciliation decisions that depend on discovering actual existing schema/contracts; those are implementation discovery gates, not unresolved product architecture.

---

## 29. Design Closure

This document captures the user-approved architecture for platform-wide identity, workforce, organization membership, RBAC, effective permissions, lifecycle governance, offboarding, tenant isolation, database hardening, and the unified `/admin` center.

**Implementation remains blocked until the user reviews and approves this written specification.** After written-spec approval, the required next step is Superpowers `writing-plans`; no implementation code or migration SHALL be created before that approval gate.
