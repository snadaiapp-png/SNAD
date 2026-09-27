# SNAD Platform IAM — Architecture Design

**Date:** 2026-09-25  
**Status:** APPROVED DESIGN — pending implementation plan  
**Repository:** `snadaiapp-png/SNAD`  
**Working branch:** `feature/platform-iam`  
**Baseline:** `da251542c2de9cafb7e2f3c167b6a560969fbfbc`

---

## 1. Purpose

Introduce a production-grade **Platform Identity & Access Management (Platform IAM)** capability for users who directly administer the SNAD platform, Control Plane, and Executive Console.

The design must support:

- direct Platform Users;
- platform roles and capabilities;
- lifecycle states and administrative suspension/locking/disablement;
- temporary access grants;
- session revocation;
- append-only audit;
- protected owner safety;
- strict separation from Tenant IAM;
- Executive UI surfaces under `/executive`.

The governing boundary is:

```text
PLATFORM_USER != TENANT_USER
```

A Platform User never receives tenant business-data access implicitly. Any future platform-to-tenant support access must be **explicit, scoped, time-bound, reason-bound, and audited**.

---

## 2. Existing foundations to preserve

Platform IAM extends the existing security model rather than creating a parallel identity stack.

Existing foundations to preserve:

- tenant-scoped `users`;
- tenant-scoped `roles`;
- global `access_capabilities` registry;
- `role_capabilities`;
- `user_role_assignments`;
- `access_scope_grants`;
- `CapabilityEvaluationService`;
- `@RequireCapability` / `CapabilityAuthorizationAspect`;
- `ControlPlaneAccessGuard`;
- JWT authentication;
- refresh-token revocation;
- `session_version` invalidation;
- `PlatformAuditService` / platform audit infrastructure;
- Subscription Control Plane Executive shell and `ScpAccessProvider`;
- PostgreSQL Direct acceptance testing.

The existing `platform_admin` boolean remains only as a **legacy/bootstrap compatibility signal** during migration. It is not the authoritative Platform IAM authorization source.

---

## 3. Selected architecture

### 3.1 Formal Platform Membership over Control Plane identity

The approved architecture is:

```text
Canonical tenant-scoped User identity
        |
        +-- Tenant context
        |     +-- Tenant roles/capabilities
        |
        +-- Control Plane context
              +-- Platform Membership
                    +-- lifecycle state
                    +-- platform roles
                    +-- effective capabilities
                    +-- temporary grants
                    +-- session/security state
                    +-- audit trail
```

A user becomes a Platform User only when all required platform conditions are true:

```text
user exists
AND user.tenant_id = configured CONTROL_PLANE_TENANT
AND ACTIVE platform_membership exists
AND user.status = ACTIVE
AND required capability evaluates ALLOW
```

Authentication identity and authorization membership are separate concerns.

### 3.2 Request authorization chain

```text
JWT Authentication
  -> trusted tenant context
  -> ControlPlaneAccessGuard
  -> PlatformMembershipGuard
  -> @RequireCapability(...)
  -> CapabilityEvaluationService / direct platform grant evaluation
  -> protected business invariant
  -> ALLOW or DENY
```

All unresolved or invalid states fail closed.

---

## 4. Data model

### 4.1 `platform_memberships`

Add one explicit platform-membership table.

Logical schema:

```text
platform_memberships
- id UUID PK
- control_tenant_id UUID NOT NULL
- user_id UUID NOT NULL
- status VARCHAR NOT NULL
- invited_at TIMESTAMPTZ NULL
- activated_at TIMESTAMPTZ NULL
- suspended_at TIMESTAMPTZ NULL
- locked_at TIMESTAMPTZ NULL
- disabled_at TIMESTAMPTZ NULL
- created_by UUID NULL
- updated_by UUID NULL
- status_reason VARCHAR/TEXT NULL
- created_at TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL
```

Constraints:

```text
UNIQUE(control_tenant_id, user_id)
FOREIGN KEY (control_tenant_id, user_id)
  -> users(tenant_id, id)
```

Supported lifecycle values:

```text
INVITED
ACTIVE
SUSPENDED
LOCKED
DISABLED
```

The database relationship must prevent a membership from referencing a user outside the control-plane tenant.

### 4.2 Platform roles reuse canonical `roles`

Do not create a second RBAC engine or duplicate role tables.

Reuse:

```text
roles
role_capabilities
user_role_assignments
access_capabilities
```

Platform roles exist only inside the configured control-plane tenant.

Initial protected system roles:

```text
PLATFORM_OWNER
PLATFORM_ADMIN
SECURITY_ADMIN
BILLING_ADMIN
SUPPORT_OPERATOR
READ_ONLY_AUDITOR
```

### 4.3 `platform_role_metadata`

Add minimal metadata to distinguish protected platform roles without changing tenant role semantics globally.

Logical schema:

```text
platform_role_metadata
- control_tenant_id UUID NOT NULL
- role_id UUID NOT NULL
- role_type SYSTEM | CUSTOM
- protected BOOLEAN NOT NULL
- owner_role BOOLEAN NOT NULL
- created_at TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL
```

The metadata table does not become an authorization engine. It only classifies roles and protects system invariants.

### 4.4 Capability registry

Reuse `access_capabilities` as the single canonical registry.

Seed the following Platform IAM capabilities:

```text
PLATFORM.USER.READ
PLATFORM.USER.CREATE
PLATFORM.USER.UPDATE
PLATFORM.USER.SUSPEND
PLATFORM.USER.DISABLE

PLATFORM.ROLE.READ
PLATFORM.ROLE.CREATE
PLATFORM.ROLE.UPDATE
PLATFORM.ROLE.ASSIGN
PLATFORM.ROLE.DELETE

PLATFORM.PERMISSION.READ
PLATFORM.PERMISSION.MANAGE

PLATFORM.SESSION.READ
PLATFORM.SESSION.REVOKE

PLATFORM.AUDIT.READ

PLATFORM.SECURITY.READ
PLATFORM.SECURITY.MANAGE
```

Existing Control Plane capabilities such as subscription, billing, catalog, entitlement, provisioning, and audit capabilities remain valid and may be assigned to Platform roles from the same registry.

### 4.5 Temporary access

Reuse `access_scope_grants` rather than create another direct-grant table.

For Platform IAM temporary access:

```text
tenant_id = CONTROL_PLANE_TENANT
user_id = target platform user
capability_id = requested capability
scope_type = TENANT
is_direct_exception = TRUE
effective_from = start time
effective_to = required expiry
reason = required
granted_by = actor
status = ACTIVE
```

Temporary grants require an expiry and reason. Expired or revoked grants never authorize access.

---

## 5. Authorization model

### 5.1 Platform authorization orchestration

Add `PlatformAuthorizationService` as an orchestration layer, not a second permission engine.

Dependencies:

```text
PlatformAuthorizationService
  -> ControlPlaneAccessGuard / configured control tenant
  -> PlatformMembershipService
  -> CapabilityEvaluationService
  -> direct temporary grant lookup
```

Decision order:

```text
1. Valid authentication?
2. Authenticated tenant is the configured Control Plane tenant?
3. Platform membership exists?
4. Platform membership is ACTIVE?
5. User account is ACTIVE?
6. Protected safety invariant permits the requested action?
7. Active direct temporary grant allows the capability?
8. Active role grants the capability?
9. Otherwise DENY
```

No role name itself is sufficient to authorize an endpoint.

### 5.2 Legacy `platform_admin`

`platform_admin=true` alone must not authorize Platform IAM.

Explicit invariant:

```text
platform_admin = true
AND no ACTIVE platform_membership
=> DENY
```

The legacy flag is retained for bootstrap compatibility and can be deprecated in a separate future migration after Platform IAM is stable.

---

## 6. Platform Owner safety

The canonical project owner remains unchanged and must be bootstrapped into an ACTIVE Platform Membership with `PLATFORM_OWNER`.

Protected invariant:

```text
active PLATFORM_OWNER count >= 1
```

The backend must reject any mutation that would leave zero effective active owners, including:

- suspending the last owner;
- locking the last owner;
- disabling the last owner;
- removing the final `PLATFORM_OWNER` assignment;
- disabling or deleting the owner role itself;
- otherwise removing the final administrative recovery path.

Return a conflict result with stable reason code:

```text
LAST_PLATFORM_OWNER
```

Owner mutations must use transaction-level locking or an equivalent concurrency-safe strategy. Two concurrent owner-removal operations must not be able to pass independent pre-checks and commit a zero-owner state.

---

## 7. User lifecycle

Platform Membership state and account authentication state are distinct.

```text
users.status
= whether the login identity is usable

platform_memberships.status
= whether that identity may access the Control Plane
```

Allowed Platform Membership transitions:

```text
INVITED -> ACTIVE
ACTIVE -> SUSPENDED
SUSPENDED -> ACTIVE
ACTIVE -> LOCKED
LOCKED -> ACTIVE
ACTIVE | SUSPENDED | LOCKED -> DISABLED
```

`DISABLED` is administratively terminal for normal UI activation. Recovery requires an explicit recovery workflow rather than a normal activate action.

No physical deletion of Platform Users is part of this design.

---

## 8. Session security

Reuse the current refresh-token and `session_version` infrastructure.

The following membership state changes require immediate session invalidation:

```text
SUSPENDED
LOCKED
DISABLED
```

The mutation must coordinate:

```text
membership state change
+ revoke active refresh tokens
+ increment user.session_version
+ invalidate SessionVersionCache
+ append audit event
```

Ordinary profile edits such as display-name changes do not revoke sessions.

The first Platform IAM UI version must not invent device/IP/browser metadata that is not present in the canonical session model.

---

## 9. Backend API surface

Create a dedicated Executive API. Do not reuse tenant user APIs as the public Platform IAM surface.

### 9.1 Platform Users

```text
GET    /api/v1/executive/users
POST   /api/v1/executive/users
GET    /api/v1/executive/users/{userId}
PATCH  /api/v1/executive/users/{userId}

POST   /api/v1/executive/users/{userId}/activate
POST   /api/v1/executive/users/{userId}/suspend
POST   /api/v1/executive/users/{userId}/lock
POST   /api/v1/executive/users/{userId}/disable

GET    /api/v1/executive/users/{userId}/roles
PUT    /api/v1/executive/users/{userId}/roles

GET    /api/v1/executive/users/{userId}/permissions

GET    /api/v1/executive/users/{userId}/sessions
POST   /api/v1/executive/users/{userId}/sessions/revoke
```

Executive Platform IAM endpoints must not accept a client-provided control tenant as an authority source. The backend resolves the configured control-plane tenant internally.

### 9.2 Roles and capabilities

```text
GET    /api/v1/executive/roles
GET    /api/v1/executive/roles/{roleId}
POST   /api/v1/executive/roles
PATCH  /api/v1/executive/roles/{roleId}

GET    /api/v1/executive/capabilities
GET    /api/v1/executive/roles/{roleId}/capabilities
PUT    /api/v1/executive/roles/{roleId}/capabilities
```

The backend term remains **Capability**. The UI may present capabilities as "Permissions / الصلاحيات".

### 9.3 Capability enforcement examples

```text
GET users               -> PLATFORM.USER.READ
POST users              -> PLATFORM.USER.CREATE
PATCH user              -> PLATFORM.USER.UPDATE
suspend user            -> PLATFORM.USER.SUSPEND
disable user            -> PLATFORM.USER.DISABLE
assign roles            -> PLATFORM.ROLE.ASSIGN
edit role capabilities  -> PLATFORM.PERMISSION.MANAGE
revoke sessions         -> PLATFORM.SESSION.REVOKE
```

---

## 10. Platform user creation rules

`POST /api/v1/executive/users` must execute transactionally:

```text
1. Normalize email.
2. Resolve configured Control Plane tenant internally.
3. Resolve an existing identity inside that tenant.
4. Reuse the existing Control Plane identity when valid; do not create duplicates.
5. If no Control Plane identity exists, create one using the existing canonical user model.
6. Create an INVITED Platform Membership.
7. Assign only permitted initial platform role(s).
8. Append audit evidence.
9. Return a sanitized PlatformUser response.
```

An identity that exists only in another tenant is not promoted into the Control Plane. Under the current tenant-scoped identity model, Platform IAM uses a Control Plane identity row.

---

## 11. Audit

Reuse `PlatformAuditService` and the existing append-only platform audit infrastructure.

Required event families include:

```text
PLATFORM_USER_CREATED
PLATFORM_USER_ACTIVATED
PLATFORM_USER_SUSPENDED
PLATFORM_USER_LOCKED
PLATFORM_USER_DISABLED
PLATFORM_ROLE_ASSIGNED
PLATFORM_ROLE_REMOVED
PLATFORM_ROLE_CREATED
PLATFORM_ROLE_UPDATED
PLATFORM_PERMISSION_CHANGED
PLATFORM_SESSION_REVOKED
PLATFORM_TEMPORARY_ACCESS_GRANTED
PLATFORM_TEMPORARY_ACCESS_REVOKED
```

Audit should contain actor, action, resource/target, reason when required, before/after summaries, timestamp, and correlation information where supported.

Never record passwords, password hashes, raw JWTs, refresh tokens, MFA secrets, or recovery secrets.

---

## 12. Executive UI

Platform IAM is integrated into the existing `/executive` shell.

### 12.1 Navigation

Add under the operations/governance area:

```text
المستخدمون  -> /executive/users
الصلاحيات   -> /executive/access
```

Navigation remains capability-driven through the existing `ScpAccessProvider` fail-closed model.

Suggested visibility capabilities:

```text
/executive/users  -> PLATFORM.USER.READ
/executive/access -> PLATFORM.ROLE.READ or PLATFORM.PERMISSION.READ
```

### 12.2 `/executive/users`

Summary metrics:

```text
Total platform users
Active
Suspended
Pending invitations
Sensitive-access accounts
```

Metrics are returned by the backend and are not derived only from the currently loaded page.

Directory capabilities:

```text
search
status filter
role filter
pagination
loading state
empty state
error state
```

Table fields:

```text
user
email
membership status
roles
last login
security state
joined at
actions
```

Do not show an MFA state unless a canonical MFA backend exists and supplies real data.

### 12.3 `/executive/users/{userId}`

Sections:

```text
Overview
Roles & Permissions
Security & Sessions
Activity
```

The authorization view must distinguish:

```text
Assigned Roles
Direct Temporary Grants
Effective Capabilities
```

Effective permission rows should explain the decision source, for example:

```text
ALLOW via ROLE SECURITY_ADMIN
ALLOW via TEMPORARY_GRANT expires_at=...
DENY reason=NO_ACTIVE_GRANT
```

### 12.4 `/executive/access`

Initial tabs/surfaces:

```text
Roles
Permissions / Capabilities
Temporary Access
```

Role details include metadata, protected/system state, assigned capabilities, assigned users, and audit history.

Protected roles such as `PLATFORM_OWNER` and `PLATFORM_ADMIN` must be visibly marked as system protected.

### 12.5 Sensitive actions

The following actions require explicit confirmation UI and backend authorization:

```text
Suspend
Lock
Disable
Revoke sessions
Remove role
Change protected role
Grant temporary access
```

Sensitive mutations require a reason when specified by backend policy. The UI displays backend conflict errors such as `LAST_PLATFORM_OWNER`; it does not implement a substitute security rule locally.

### 12.6 Frontend security rule

`useScpAccess().has(...)` controls UX visibility and enablement only.

The security boundary remains server-side:

```text
ControlPlaneAccessGuard
+ PlatformMembershipGuard
+ @RequireCapability
+ protected business invariants
```

---

## 13. Internationalization and accessibility

Reuse the existing i18n framework and add `scp.users.*` and `scp.access.*` keys.

Required behavior:

```text
Arabic RTL
English LTR
semantic table headers
keyboard-accessible controls
accessible confirmation dialogs
ARIA labels for controls and filters
```

Avoid hardcoded UI translations when an i18n key is appropriate.

---

## 14. Database migration strategy

All migrations are new, forward-only Flyway migrations. Historical migrations are not edited.

Implementation order:

```text
Migration A: create platform_memberships + constraints/indexes/RLS
Migration B: create platform_role_metadata
Migration C: seed Platform IAM capabilities
Migration D: seed protected Platform roles + role metadata
Migration E: bootstrap canonical owner membership + PLATFORM_OWNER assignment + required grants + fail-closed verification
```

Do not remove in this delivery:

```text
platform_admin
legacy ADMIN role
existing capability grants
```

If production rollback is necessary, prefer application rollback plus a forward corrective migration. Do not rely on destructive schema rollback after Platform IAM data exists.

---

## 15. Tenant isolation and privilege-escalation requirements

Explicitly prove the following:

```text
Tenant ADMIN -> Platform IAM API = DENY
Tenant User -> /executive/users = DENY
platform_admin=true without ACTIVE membership = DENY
Platform Member without required capability = DENY
Platform Member with exact capability = ALLOW
Client-supplied tenant IDs cannot expand Executive scope
SUPPORT_OPERATOR cannot self-promote to PLATFORM_OWNER
Expired temporary grant = DENY
Revoked temporary grant = DENY
Protected role mutation requires both capability and protected-role policy
Platform role assignment never grants tenant business-data access implicitly
```

No Platform capability implicitly grants CRM, HRM, ERP, Accounting, payroll, or other tenant business-data access.

---

## 16. Testing strategy

### 16.1 Backend tests

Required coverage includes:

```text
01 canonical owner bootstrap
02 membership creation
03 INVITED -> ACTIVE
04 ACTIVE -> SUSPENDED
05 SUSPENDED -> ACTIVE
06 ACTIVE -> LOCKED
07 LOCKED -> ACTIVE
08 disable
09 invalid lifecycle transition
10 missing membership -> 403
11 suspended membership -> 403
12 tenant user -> Executive Platform IAM -> 403
13 active membership + missing capability -> 403
14 exact capability -> ALLOW
15 role capability aggregation
16 multiple roles
17 temporary grant valid -> ALLOW
18 temporary grant expired -> DENY
19 temporary grant revoked -> DENY
20 session revoke
21 suspension invalidates sessions
22 disable invalidates sessions
23 last owner suspend -> 409
24 last owner disable -> 409
25 last owner role removal -> 409
26 concurrent owner mutations preserve >= 1 active owner
27 protected-role escalation denied
28 audit event generated
29 audit contains no secret material
30 tenant RBAC regression protection
31 cross-tenant isolation regression protection
```

### 16.2 Frontend tests

Required coverage includes:

```text
/users loading/error/empty/populated
search/status/role filters
pagination
create-user dialog and validation
capability-gated mutation controls
user detail sections
roles/effective permissions/temporary grants
session revocation UI
suspend/lock/disable confirmations
LAST_PLATFORM_OWNER error rendering
/access roles list
protected-role marker
capability matrix
permission-denied state
RTL
English
keyboard-accessible dialogs
```

Hidden controls are never accepted as security evidence.

---

## 17. PostgreSQL Direct requirement

Platform IAM acceptance tests must use **PostgreSQL Direct**.

Forbidden as certification paths:

```text
Docker
Testcontainers
H2 as certification database
```

The implementation must extend the repository's current PostgreSQL acceptance profile and CI conventions.

---

## 18. Regression boundaries

Platform IAM must not regress:

```text
Authentication
JWT issuance/validation
Refresh-token rotation and revocation
session_version invalidation
Tenant users
Tenant roles
CapabilityEvaluationService
ControlPlaneAccessGuard
Subscription Control Plane
Executive tenants
Billing
Audit
HRM scoped authorization
RLS
```

Avoid unrelated refactoring.

---

## 19. Acceptance gates

The implementation is certified only after these gates pass:

```text
G1  REPOSITORY_INTEGRITY
G2  ARCHITECTURE_CONFORMANCE
G3  PLATFORM_IAM_DATABASE
G4  PLATFORM_MEMBERSHIP_BOUNDARY
G5  PLATFORM_CAPABILITY_ENFORCEMENT
G6  TENANT_ISOLATION
G7  PLATFORM_OWNER_SAFETY
G8  SESSION_REVOCATION
G9  AUDIT_INTEGRITY
G10 BACKEND_TESTS
G11 FRONTEND_TESTS
G12 POSTGRESQL_DIRECT_ACCEPTANCE
G13 EXACT_HEAD_CI
G14 AUTHENTICATED_VISUAL_ACCEPTANCE
G15 POST_MERGE_PRODUCTION_VERIFICATION
```

A documentation claim does not satisfy a runtime or security gate.

---

## 20. Authenticated visual acceptance

Authenticated acceptance must prove real runtime behavior for:

```text
/executive/users
/executive/users/{id}
/executive/access
```

Evidence must use real API data and demonstrate real roles, effective capabilities, and at least one authorized lifecycle action.

A separate unauthorized account/context must prove deny behavior.

Mock screenshots, hardcoded users, fake session data, and frontend-only permission logic are not acceptable evidence.

---

## 21. CI and Git governance

After the final implementation change, record `FINAL_HEAD_SHA`.

All required CI/security/acceptance evidence must correspond to that exact SHA.

If another commit is added after successful CI, the previous CI evidence is stale and the exact-head checks must run again.

No merge may occur while a critical gate is unproven.

---

## 22. Final closure contract

Final implementation reporting must use one of two verdicts only:

```text
FULLY_CERTIFIED
BLOCKED
```

If blocked, report:

```text
BLOCKER
ROOT_CAUSE
EVIDENCE
REQUIRED_ACTION
```

Do not claim completion when a critical gate is missing.

---

## 23. Explicit non-goals for this delivery

This Platform IAM delivery does **not** include:

- replacing the existing JWT/authentication stack;
- a second global user table;
- a second RBAC/capability engine;
- physical deletion of Platform Users;
- automatic access to tenant business data;
- full device/browser session intelligence unless already represented by canonical data;
- a new MFA backend;
- removal of `platform_admin` legacy compatibility;
- unrelated tenant IAM redesign;
- destructive rewriting of historical Flyway migrations.

These exclusions keep the migration incremental and preserve compatibility with the current platform security model.
