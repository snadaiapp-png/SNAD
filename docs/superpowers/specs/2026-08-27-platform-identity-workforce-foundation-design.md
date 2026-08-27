# SNAD Platform Identity, Workforce & Access Foundation — Design Specification

**Date:** 2026-08-27  
**Status:** APPROVED DESIGN / IMPLEMENTATION NOT STARTED  
**Repository:** `snadaiapp-png/SNAD`  
**Design branch:** `design/platform-identity-workforce-foundation`  
**Baseline `main` SHA at design creation:** `2dd8d1151ec0b231a51c13ee20722da6598e89e3`  
**Scope:** Platform-wide foundation consumed by CRM, ERP, Finance, HRM, POS, Analytics, Workflow, and future modules.

---

## 1. Executive Decision

SNAD SHALL use a shared platform foundation for identity, workforce, organization membership, and authorization.

The approved core model is **Option B**:

- `User` is the platform security identity used for authentication, sessions, memberships, roles, and capabilities.
- `Employee` is the workforce/HR record.
- `Employee` and `User` are separate lifecycles.
- An employee MAY be linked to one user within the same tenant.
- A user MAY exist without an employee record.
- An employee MAY exist without a user account.
- Non-human identities (`SERVICE`, `INTEGRATION`) MUST NOT be linked to employees.
- CRM, ERP, Finance, POS, and other business modules MUST NOT create module-specific user or employee sources of truth.

The intended relationship is:

```text
Employee 0..1 ───── 0..1 User
```

with the physical link retained on `hr_employees.user_id` and hardened by tenant-safe referential integrity and uniqueness.

---

## 2. Goals

This foundation SHALL:

1. provide one platform source of truth for security identities;
2. provide one workforce source of truth for employees, departments, positions, and manager hierarchy;
3. provide one tenant-scoped RBAC system for all modules;
4. separate HR lifecycle from security lifecycle;
5. support tenant-wide and organization-scoped role grants;
6. compute effective permissions from authoritative sources instead of persisting duplicated permission state;
7. provide a unified `/admin` operational center;
8. enforce tenant isolation at both application and PostgreSQL layers;
9. preserve historical user references after suspension, termination, or archival;
10. provide auditable onboarding, access change, and offboarding flows;
11. reuse existing tables and services wherever they are already authoritative;
12. use forward-only migrations and stop on incompatible production data instead of silently rewriting it.

---

## 3. Non-Goals

This phase SHALL NOT:

- create `crm_users`, `erp_users`, `finance_users`, `pos_users`, or equivalent module-specific identity tables;
- merge `users` and `hr_employees` into one table;
- create a generic `Person`/MDM master entity;
- make job positions equivalent to security roles;
- grant capabilities directly to users in the tenant plane;
- store `user_effective_permissions` as an authoritative table;
- hard-delete users or employees that have operational history;
- redesign CRM ownership, ERP business workflows, or Finance approval semantics beyond consuming this platform foundation;
- let frontend route visibility substitute for backend authorization;
- bypass branch protection, PostgreSQL RLS, or module entitlement checks.

---

## 4. Architectural Boundaries and Sources of Truth

| Concern | Authoritative owner | Notes |
|---|---|---|
| Authentication identity | IAM / `users` | Platform-wide security principal |
| User account status | IAM / `users` | Independent from employment status |
| Credentials / sessions / MFA | IAM security subsystem | Reconciled in PF-01; not duplicated into HR |
| Tenant | SaaS Core | Authoritative tenant boundary |
| Organization | Organization domain | Sub-scope inside a tenant |
| Organization membership | `organization_memberships` | Links users to organizations; invitation-first is supported |
| Roles | Access Control / `roles` | Tenant-scoped |
| Capability catalog | Access Control / `access_capabilities` | Platform-wide catalog |
| Role-to-capability mapping | `role_capabilities` | Tenant role to global capability |
| User-to-role grant | `user_role_assignments` | Tenant-wide or organization-scoped |
| Employee record | Workforce / `hr_employees` | HR/workforce source of truth |
| Department | Workforce / `hr_departments` | Hierarchical workforce structure |
| Position | Workforce / `hr_positions` | Job position; never equivalent to security role |
| Manager hierarchy | Workforce / `hr_employees.manager_id` | Employee hierarchy |
| CRM record ownership | CRM | References platform users; CRM owns ownership semantics only |
| ERP record ownership/approval | ERP / Workflow | References platform identities |
| Finance approval/history | Finance / Workflow | References platform identities |
| Module entitlement | SaaS module/subscription layer | Higher-order authorization gate |
| Audit | Platform audit + domain-specific audit | Correlated, immutable where required |

### Hard ownership rule

A business module MAY reference `User.id` or `Employee.id`, but MUST NOT copy identity, role, employee department, position, manager, or employment status into a module-local source of truth merely for authorization.

---

## 5. Existing Repository Assets to Preserve

The design intentionally consolidates existing implementation rather than replacing it.

### Existing tables to KEEP

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

Existing business modules SHALL consume these platform capabilities instead of replacing them.

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

`User` SHALL represent security identity, not employment.

Required lifecycle:

```text
INVITED → ACTIVE → SUSPENDED → ARCHIVED
```

Existing `INACTIVE` MAY remain supported for backward compatibility; PF-01 SHALL reconcile the exact state model before changing persisted semantics.

The platform SHALL introduce an explicit identity type, unless an equivalent authoritative field already exists after reconciliation:

```text
HUMAN
SERVICE
INTEGRATION
```

Rules:

- only `HUMAN` users MAY link to an employee;
- `SERVICE` and `INTEGRATION` identities MUST use least-privilege roles;
- user archival MUST preserve historical references;
- user suspension MUST invalidate/revoke active sessions according to IAM policy.

### 6.2 Employee

Employee lifecycle:

```text
ACTIVE
ON_LEAVE
SUSPENDED
TERMINATED
```

Employee SHALL own workforce attributes such as:

- employee number;
- names/display name;
- department;
- position;
- manager;
- employment type;
- hire date;
- termination date;
- HR metadata.

### 6.3 Position is not Role

The following invariant is mandatory:

```text
Position != Role
```

A position change MAY generate an access-review recommendation, but MUST NOT silently grant or revoke a security role unless an explicit tenant policy/workflow authorizes that behavior.

---

## 7. User ↔ Employee Link

The approved link remains `hr_employees.user_id`, but SHALL be hardened.

### Required invariant

Within one tenant, a non-null user may be linked to at most one employee.

Target database invariant:

```sql
CREATE UNIQUE INDEX ...
ON hr_employees (tenant_id, user_id)
WHERE user_id IS NOT NULL;
```

### Tenant-safe FK

The current single-column FK SHALL be replaced or supplemented by a tenant-safe composite relationship:

```text
(hr_employees.tenant_id, hr_employees.user_id)
    → users(tenant_id, id)
```

Before adding this constraint, a forward-only reconciliation migration MUST verify that no existing row violates same-tenant linkage.

### Link rules

- linking requires both records to belong to the same tenant;
- linking a `SERVICE` or `INTEGRATION` user is denied;
- unlinking SHALL NOT delete either record;
- archived/suspended user linkage may remain for audit/history;
- linking/unlinking SHALL emit an audit event.

---

## 8. Workforce Referential Integrity

Workforce relationships SHALL become tenant-safe at the database layer.

Required target relationships:

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

Supporting composite unique keys on `(tenant_id, id)` SHALL be introduced where absent.

Manager and department hierarchies MUST reject cycles at the application layer; if a reliable database-level cycle strategy is introduced later, it SHALL be additive rather than replacing application validation without review.

---

## 9. Tenant Authority

### Tenant-plane rule

For authenticated tenant-plane requests, `tenantId` SHALL come from the validated security context/JWT:

```text
Authenticated request
      ↓
SecurityContext / tenant_id claim
      ↓
Backend tenant scope
```

The browser SHALL NOT be trusted to choose its tenant using `?tenantId=`.

Existing tenant-plane controllers that still accept request-supplied `tenantId` SHALL be reconciled toward the canonical `SecurityContextUtils.tenantId(auth)` pattern.

### Control-plane exception

Cross-tenant operations are permitted only through explicit Control Plane APIs with dedicated platform capabilities, audit, and policy checks. Tenant-plane and Control-plane routes SHALL remain logically distinct.

---

## 10. PostgreSQL RLS Model

The required security posture is:

```text
Missing tenant context → DENY / zero visible tenant rows
Tenant mismatch        → DENY
Tenant match           → ALLOW subject to policy
```

Policies that intentionally allow access when `current_setting('app.tenant_id', true)` is null are incompatible with this target and SHALL be reconciled.

For tenant-owned foundation tables, PF-02 SHALL evaluate and apply as appropriate:

- `ENABLE ROW LEVEL SECURITY`;
- `FORCE ROW LEVEL SECURITY`;
- fail-closed `USING` expressions;
- fail-closed `WITH CHECK` expressions;
- least-privilege application role grants;
- cross-tenant integration tests using PostgreSQL Direct.

No claim of RLS closure is valid without testing through the same application database role used in runtime.

---

## 11. Authorization Model

Effective permission SHALL be computed as an intersection of independent gates:

```text
Effective Permission
=
Authenticated User
∩ User Status
∩ Membership Status
∩ Active User Role Grant
∩ Active Role
∩ Active Capability
∩ Module Entitlement
∩ Organization Scope
∩ Runtime / Resource Policy
```

Default decision is **DENY / fail closed**.

### Decision order

```text
Authenticated?
  ├─ no → DENY
  ▼
User ACTIVE?
  ├─ no → DENY
  ▼
Required tenant/org membership valid?
  ├─ no → DENY
  ▼
Module enabled for tenant?
  ├─ no → DENY
  ▼
Required capability present through an active role grant?
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

Backend authorization is authoritative. Frontend hiding/disabling is UX only.

---

## 12. Roles, Capabilities, and Grants

### 12.1 Roles

Roles are tenant-scoped bundles of capabilities.

The platform SHALL distinguish role type:

```text
SYSTEM
CUSTOM
```

- `SYSTEM` role semantics are platform-governed and protected from tenant mutation where required.
- `CUSTOM` roles can be created and modified by authorized tenant administrators.

Code SHOULD authorize capabilities rather than named business roles. New hard-coded checks such as `if role == SALES_MANAGER` are prohibited except explicit Control Plane policies approved by architecture/security.

### 12.2 Capability Catalog

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

Modules register their capabilities, while Access Control owns role composition and grants.

Tenant Admin MAY view/use allowed capabilities but SHALL NOT redefine platform capability codes.

### 12.3 No direct tenant-plane user capability grants

The tenant-plane model SHALL remain:

```text
User → Role → Capability
```

Direct `User → Capability` grants are out of scope. A one-user exception SHALL be represented as a custom role so the reason for access remains auditable.

### 12.4 User role grants

Existing `user_role_assignments` SHALL remain authoritative.

A grant is either:

- tenant-wide when `organization_id IS NULL`; or
- organization-scoped when `organization_id` is populated.

The existing organization-scoped uniqueness SHALL be retained and the null-scope race SHALL be closed with a partial uniqueness invariant equivalent to:

```sql
UNIQUE (tenant_id, user_id, role_id)
WHERE organization_id IS NULL;
```

The grant model SHOULD be extended with lifecycle metadata needed for temporary and auditable access:

- `effective_from`;
- `effective_until`;
- `revoked_at`;
- `revoked_by`.

Exact column introduction SHALL follow PF-01 schema reconciliation and backward-compatibility review.

---

## 13. Module Entitlements

Module entitlement is a higher-order gate than RBAC.

Example:

```text
RBAC: HR.EMPLOYEE.READ = present
Tenant HRM entitlement = disabled
Final authorization = DENY
```

No role grant may reactivate a module the tenant is not entitled to use.

The effective-access service SHALL read the existing authoritative module/subscription lifecycle rather than duplicating entitlement state in Access Control.

---

## 14. Effective Access Service

A central read model SHALL expose the authenticated user's effective access, for example:

```text
GET /api/v1/access/me/effective-permissions
```

The response SHOULD contain:

- user id;
- tenant id;
- active organization memberships/scopes;
- roles and grant scopes;
- effective capabilities;
- module entitlement results;
- optionally machine-readable deny/explanation metadata.

This endpoint is for UI/support observability. Business APIs MUST independently enforce authorization.

### Permission explanation

The administration experience SHALL support a diagnostic path equivalent to:

```text
Why does this user have CRM.OPPORTUNITY.APPROVE?
```

with a trace such as:

```text
User
→ active membership
→ SALES_MANAGER grant
→ CRM.OPPORTUNITY.APPROVE
→ CRM enabled
→ organization scope matched
→ runtime policy passed
→ ALLOW
```

A denied decision SHOULD explain the first authoritative gate that failed without exposing secrets.

---

## 15. Lifecycle and Access Governance

### 15.1 Employee creation

Creating an employee does not automatically create a user.

If system access is required, access provisioning is an explicit workflow:

```text
Employee created
→ Create or link User
→ Activate membership
→ Grant roles
→ Verify effective access
```

This SHALL be implemented as orchestration/Saga semantics rather than one destructive database transaction across domains. If later provisioning fails, the employee remains valid and the UI reports incomplete access setup with a retry path.

### 15.2 Department/position change

A workforce move SHALL trigger access review, not implicit privilege accumulation.

Example:

```text
Sales → Finance
```

may produce recommendations to revoke obsolete CRM roles and grant Finance roles, but the security change requires explicit policy/approval.

### 15.3 Suspension

`User.status = SUSPENDED` is independent of employee status and SHALL deny new authenticated access and revoke/disable sessions per IAM policy.

`Employee.status = SUSPENDED` MAY generate an access review or security action through policy/workflow; it is not a database trigger that silently mutates IAM.

### 15.4 Termination / Offboarding

Employee termination SHALL start an auditable offboarding workflow before final user archival.

Required phases:

```text
Employee TERMINATED
→ freeze security exposure
→ revoke active sessions
→ revoke privileged/temporary access
→ transfer outstanding business work
→ close memberships according to tenant policy
→ audit completion
→ archive user when safe
```

Business handover MAY include:

- CRM ownership;
- queues and tasks;
- pending ERP approvals;
- Finance approvals;
- Workflow assignments/escalations.

No user with historical operational references is hard-deleted.

### 15.5 Rehire

Rehire SHOULD reuse the existing employee/user identity after review where legally and operationally appropriate, but previous roles MUST NOT be automatically restored. New access is provisioned from the current role/position need.

---

## 16. Unified `/admin` Information Architecture

The approved tenant administration center is:

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

### 16.1 `/admin/overview`

Operational exception dashboard, not decorative KPIs. Examples:

- active/invited/suspended users;
- employees with/without accounts;
- expiring grants;
- terminated employees with active users;
- privileged accounts requiring review;
- failed offboarding workflows;
- unlinked human users/employees.

### 16.2 `/admin/users`

User security identity management:

- invite/create;
- activate/suspend/archive;
- resend invitation;
- revoke sessions;
- link employee;
- manage memberships;
- manage roles;
- inspect effective permissions;
- inspect audit history.

### 16.3 `/admin/users/{id}`

Tabs:

- Overview;
- Memberships;
- Employee Link;
- Roles;
- Effective Access;
- Sessions;
- Audit.

Workforce fields may be displayed but are edited through the employee record.

### 16.4 `/admin/employees`

Workforce operations:

- create/edit employee;
- change department/position/manager;
- suspend/terminate;
- link/create system account;
- start offboarding.

### 16.5 `/admin/departments`

Hierarchical department management with archive semantics. A referenced department is not hard-deleted.

### 16.6 `/admin/positions`

Position management with department, grade, status, and description. Position-to-role hard coupling is prohibited.

### 16.7 `/admin/memberships`

Organization membership lifecycle:

- invite;
- activate;
- deactivate;
- remove;
- inspect organization-scoped role grants.

### 16.8 `/admin/roles`

- create custom role;
- clone;
- edit capabilities;
- activate/deactivate/archive where semantics permit;
- inspect users and scopes;
- protect system-role semantics.

Capability selection SHALL be grouped by module/resource/action with human-readable labels and technical capability code visible as supporting metadata.

### 16.9 `/admin/capabilities`

Tenant Admin: read/search/filter.  
Platform Admin: platform-governed create/activate/deactivate/metadata operations.

### 16.10 `/admin/access`

Access Explorer SHALL answer both directions:

- "What can this user do?"
- "Who can perform this capability in this organization?"

Results must be based on effective access, not a raw role lookup.

### 16.11 `/admin/audit`

Filter dimensions SHOULD include:

- actor;
- target user;
- employee;
- action;
- module;
- organization;
- date/time;
- correlation id;
- result.

Sensitive mutations SHOULD capture before/after state where safe and compliant.

---

## 17. Platform Admin vs Tenant Admin

The same product may use `/admin`, but authority is separated at the backend.

### Platform / Control Plane Admin

May manage, with explicit capabilities and audit:

- tenants;
- global capability catalog;
- system roles/policies;
- cross-tenant platform operations;
- platform audit.

### Tenant Admin

May manage only within authenticated tenant scope:

- users;
- employees;
- departments;
- positions;
- organization memberships;
- custom roles;
- role grants;
- tenant audit.

Tenant Admin SHALL NOT:

- operate on another tenant;
- redefine global capability codes;
- modify protected system-role semantics;
- bypass module entitlement.

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

Mandatory auditable events include:

- user created/invited/activated/suspended/archived;
- sessions revoked;
- employee created/updated/suspended/terminated;
- employee linked/unlinked to user;
- membership invited/activated/deactivated/removed;
- role created/changed/activated/deactivated/archived;
- capability added/removed from role;
- role granted/revoked/expired;
- offboarding started/completed/failed;
- access-review decision.

Existing platform/domain audit infrastructure SHOULD be reused; this design does not require one giant replacement audit table.

---

## 19. Data Migration and Reconciliation Strategy

All database changes are **forward-only** Flyway migrations. Previously applied migrations MUST NOT be rewritten.

### 19.1 Reconciliation before constraints

Before a constraint is added, migration preconditions SHALL detect incompatible data such as:

- one user linked to multiple employees;
- cross-tenant employee-user link;
- cross-tenant department/position/manager reference;
- duplicate tenant-wide role grants;
- invalid organization scope;
- malformed user types or role types after classification is introduced.

If incompatible data exists, the migration SHALL fail with a deterministic reconciliation report. It SHALL NOT arbitrarily delete, relink, or choose a winner.

### 19.2 No silent partial-state masking

New foundation hardening migrations SHOULD use explicit preconditions/postconditions rather than broad `IF NOT EXISTS` patterns that can conceal schema drift. Where idempotent constructs are necessary for operational recovery, their use must be justified and accompanied by state verification.

### 19.3 Historical references

No migration may add cascading deletes that erase user/employee identities referenced by CRM, ERP, Finance, Workflow, or audit history.

---

## 20. Required Database Hardening

The implementation plan SHALL include at least these four mandatory fixes after forensic reconciliation:

### H1 — Employee/User tenant-safe optional 1:1

- composite same-tenant FK;
- partial unique `(tenant_id, user_id)` where non-null;
- application validation for user type and tenant.

### H2 — Workforce composite tenant FKs

- employee → department;
- employee → position;
- employee → manager;
- position → department;
- department → parent department.

### H3 — HR RLS fail-closed

- investigate actual production schema/policies first;
- remove permissive null-tenant behavior where present;
- apply `USING` and `WITH CHECK` fail-closed semantics;
- evaluate/enable `FORCE RLS` under the runtime app role;
- prove cross-tenant isolation using PostgreSQL Direct.

### H4 — Tenant-wide role-grant uniqueness

- detect duplicate null-organization grants;
- add partial unique index for `organization_id IS NULL`;
- keep organization-scoped uniqueness;
- verify concurrent grant behavior.

---

## 21. API Reconciliation Targets

This design does not prescribe gratuitous API version churn. Existing endpoints are retained where possible and hardened.

Required logical capabilities include:

### Users

- list/get/create/invite/update;
- activate/suspend/archive;
- session revocation;
- employee-link inspection/action.

### Memberships

- list/invite/activate/deactivate/remove;
- tenant derived from security context for tenant-plane requests.

### Roles and grants

- list/get/create/update system/custom role according to authority;
- manage role capabilities;
- grant/revoke scoped role;
- support temporary grant metadata;
- list user grants.

### Effective access

- authenticated user's effective permissions;
- authorized admin explanation/explorer queries.

### Workforce

- employees;
- departments;
- positions;
- manager hierarchy;
- employee-user link/unlink;
- termination/offboarding start.

Exact paths/DTOs SHALL be finalized in the implementation plan after endpoint inventory so existing contracts are not duplicated.

---

## 22. Security Requirements

1. Default authorization decision is DENY.
2. Tenant plane derives tenant from authenticated security context.
3. Cross-tenant operations require explicit Control Plane authority.
4. RLS is defense-in-depth and must fail closed.
5. Backend always enforces authorization even if UI hides an action.
6. Module entitlement can deny access even when RBAC grants a capability.
7. Inactive role/capability/grant does not confer access.
8. Revoked/expired grants do not confer access.
9. Suspended/archived user does not receive operational access.
10. Direct tenant-plane capability grants to users are prohibited.
11. Service/integration identities cannot link to employees.
12. Sensitive access mutations are audited.
13. No secrets or credential material are returned by access-explanation APIs.
14. Historical identities are retained for traceability.

---

## 23. Failure Semantics

Foundation operations MUST be failure-safe.

Examples:

- employee creation may succeed while access provisioning fails; UI reports incomplete provisioning and offers retry;
- role grant failure does not roll back a valid employee record;
- offboarding failure leaves the user in the safest achieved state (for example suspended) and raises an actionable governance exception;
- migration reconciliation failure leaves schema/data unchanged;
- missing tenant context fails closed;
- missing entitlement/capability/policy decision fails closed.

No UI SHALL show a successful state transition until the authoritative backend confirms it.

---

## 24. Implementation Sequence

Implementation SHALL proceed in this order, with each stage reconciled against actual repository/runtime state before mutation:

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

CRM/ERP/Finance/POS integration changes that only consume the foundation may be added in the appropriate PF stages, but unrelated module feature development is not part of this program.

---

## 25. Acceptance Criteria

The foundation is not complete until all applicable criteria are evidenced.

### Identity / workforce

- AC-01: A human user can exist without an employee.
- AC-02: An employee can exist without a user.
- AC-03: A human user can link to exactly one employee per tenant.
- AC-04: A service/integration user cannot link to an employee.
- AC-05: Cross-tenant employee-user link is rejected by application and database.
- AC-06: Cross-tenant department/position/manager references are rejected by database constraints.

### Membership / roles

- AC-07: Invitation-first organization membership remains supported.
- AC-08: Tenant-wide role grant cannot be duplicated under concurrency.
- AC-09: Organization-scoped grants remain unique per user/role/org.
- AC-10: Revoked or expired grants do not contribute to effective permissions.
- AC-11: Custom roles can group allowed capabilities without direct user-capability grants.

### Authorization

- AC-12: Missing authentication → DENY.
- AC-13: Suspended/archived user → DENY.
- AC-14: Missing/inactive membership where required → DENY.
- AC-15: Disabled module entitlement overrides an RBAC grant → DENY.
- AC-16: Missing capability → DENY.
- AC-17: Invalid organization scope → DENY.
- AC-18: Runtime/resource-policy failure → DENY.
- AC-19: Effective-access explanation identifies the authoritative grant path or deny gate.

### Tenant security / PostgreSQL

- AC-20: Tenant-plane APIs cannot switch tenant by query/body input.
- AC-21: Missing `app.tenant_id` under runtime app role cannot expose tenant rows.
- AC-22: Tenant A cannot read/write Tenant B workforce records.
- AC-23: `WITH CHECK` prevents cross-tenant insert/update.
- AC-24: RLS behavior is verified with PostgreSQL Direct and runtime-equivalent least-privilege role.

### Lifecycle

- AC-25: Employee creation does not implicitly create a user unless explicit access provisioning is requested.
- AC-26: Position/department change does not silently grant a role.
- AC-27: User suspension revokes/blocks active access without changing employee employment state.
- AC-28: Employee termination starts offboarding and preserves historical identity.
- AC-29: Rehire does not silently restore old roles.

### Admin UI

- AC-30: `/admin/users` is operational for lifecycle and access administration.
- AC-31: `/admin/employees` is operational for workforce management and user linking.
- AC-32: `/admin/roles` supports custom role composition.
- AC-33: `/admin/access` can answer effective access in both user→capability and capability→users directions.
- AC-34: `/admin/audit` exposes auditable identity/access/workforce mutations according to caller authority.
- AC-35: Tenant Admin cannot access Control Plane operations.
- AC-36: Business modules do not require module-specific user-management pages to manage platform identities.

### Regression / integration

- AC-37: Existing CRM user references remain valid.
- AC-38: Existing ERP user/audit references remain valid.
- AC-39: Existing authentication/session behavior remains compatible or has an explicit migration path.
- AC-40: Required repository CI, PostgreSQL acceptance, security checks, and relevant frontend E2E are green on the final merge candidate.
- AC-41: Human operational acceptance is completed on an isolated preview before production merge where the release process requires it.

---

## 26. Risks and Mitigations

### Risk: schema drift in production

**Mitigation:** PF-01 forensic inventory and fail-fast reconciliation migrations; never infer that migration source equals runtime schema.

### Risk: broad authorization regression

**Mitigation:** reconcile endpoint-by-endpoint, preserve capability codes, add effective-access regression tests, and stage tenant-authority changes.

### Risk: privilege accumulation during workforce moves

**Mitigation:** access-review workflow; Position is not Role; explicit revoke/grant decisions.

### Risk: RLS policy currently fail-open under missing tenant context

**Mitigation:** PF-02 mandatory runtime-role PostgreSQL tests before/after policy hardening.

### Risk: duplicate grants under concurrency

**Mitigation:** database partial unique constraint plus transactional application handling.

### Risk: offboarding leaves orphan work

**Mitigation:** auditable orchestration with handover steps and actionable failed-state exception; archive only after required security/business gates.

### Risk: `/admin` becomes a monolith

**Mitigation:** shared shell with bounded feature routes/API clients; each domain keeps its own backend service boundary and source of truth.

---

## 27. Design Invariants

The following statements are architectural invariants and MUST NOT be changed during implementation without explicit design approval:

1. `Employee != User`.
2. `Position != Role`.
3. Business modules do not own platform users or employees.
4. User-to-employee link is optional 1:1 and tenant-safe.
5. Tenant-plane tenant authority comes from authenticated security context.
6. Effective access is computed from authoritative gates; it is not an independently editable source of truth.
7. Role is the tenant-plane mechanism for capability grants; direct user-capability grants are not introduced.
8. Module entitlement can only reduce access, never be bypassed by RBAC.
9. Authorization is default-deny/fail-closed.
10. RLS must fail closed for missing/mismatched tenant context.
11. Historical security identities are preserved for audit and business traceability.
12. Existing authoritative tables are consolidated and hardened, not duplicated.
13. Database migrations are forward-only and stop on unresolved incompatible data.
14. `/admin` is the unified tenant administration surface for this foundation.
15. CRM, ERP, Finance, HRM, POS, and future modules consume this foundation through stable platform contracts.

---

## 28. Design Closure

This document captures the user-approved architecture for platform-wide identity, workforce, organization membership, RBAC, effective permissions, lifecycle governance, offboarding, tenant isolation, database hardening, and the unified `/admin` center.

**Implementation is intentionally blocked until this written specification is reviewed and approved.** After written-spec approval, the next process step is to create the implementation plan via the Superpowers `writing-plans` workflow. No implementation code or migration should be created before that approval gate.
