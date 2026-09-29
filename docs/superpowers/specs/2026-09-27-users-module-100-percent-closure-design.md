# SNAD Users Module — 100% Closure Design

**Date:** 2026-09-27  
**Status:** PENDING USER REVIEW  
**Repository:** `snadaiapp-png/SNAD`  
**Working branch:** `feat/users-module-closure`  
**Baseline:** `1929d5885a3e975d90fda76c75fe27adc6ff0cb3`  
**Depends on:** `docs/superpowers/specs/2026-09-25-platform-iam-design.md`

---

## 1. Purpose

Close the SNAD Users Module as an end-to-end product surface, not merely as backend APIs or isolated IAM primitives.

The closure covers four connected product areas:

1. **Tenant Users UI** — tenant-scoped user directory, create/update/lifecycle, and user detail.
2. **Tenant Memberships / Roles / Permissions UI** — organization memberships, user role links, tenant roles, and role capabilities.
3. **Executive Platform Users / Access UI + missing Platform IAM application APIs** — platform user directory/detail, platform role/capability management, protected-owner safety, temporary access, lifecycle, and session-security actions required by the approved Platform IAM architecture.
4. **Authenticated E2E and visual certification** — real authenticated product routes, desktop/mobile evidence, tenant-isolation proof, exact-head CI, merge, post-merge, and final live verification.

The governing security boundary remains:

```text
PLATFORM_USER != TENANT_USER
```

This design does not merge tenant administration and platform administration into one identity surface.

---

## 2. Definition of “100% closed”

For this delivery, `USERS_MODULE = FULLY_CLOSED` means all of the following are true on one final merged `main` SHA:

```text
Tenant user lifecycle UI                 PASS
Tenant membership UI                     PASS
Tenant role assignment UI                PASS
Tenant role/capability management UI     PASS
Executive platform user lifecycle UI     PASS
Executive platform access UI             PASS
Backend authorization                    PASS
Tenant isolation / RLS                    PASS
Protected owner invariant                PASS
Frontend unit/component tests             PASS
Backend unit/PostgreSQL Direct tests      PASS
Authenticated E2E                        PASS
Desktop visual evidence                   PASS
Mobile visual evidence                    PASS
Exact-head CI                             GREEN
Independent review                        APPROVED
Merge                                     COMPLETE
Post-merge verification                   GREEN
Live authenticated smoke                  PASS
Open blockers                             0
```

“100%” applies to the product scope defined in this document. It does not claim completion of future enterprise identity products such as SAML federation, SCIM directory sync, hardware-key administration, or external IdP lifecycle automation.

---

## 3. Existing foundations that must be reused

### 3.1 Tenant Users backend

The existing `/api/v1/users` contract is authoritative for tenant user lifecycle:

```text
POST   /api/v1/users
GET    /api/v1/users
GET    /api/v1/users/{userId}
PUT    /api/v1/users/{userId}
PATCH  /api/v1/users/{userId}/activate
PATCH  /api/v1/users/{userId}/deactivate
PATCH  /api/v1/users/{userId}/suspend
PATCH  /api/v1/users/{userId}/archive
```

Existing capability controls remain authoritative:

```text
USER.CREATE
USER.READ
USER.WRITE
USER.DELETE
```

The existing `UserService` tenant-scoped repository behavior is preserved. No unscoped repository operations may be introduced.

### 3.2 Tenant memberships

User membership reads already exist at:

```text
GET /api/v1/users/{userId}/memberships
```

This endpoint resolves tenant identity from the authenticated JWT security context. The new frontend must not add a user-editable tenant selector or trust a caller-supplied tenant for this flow.

Organization membership mutation continues to use the existing organization membership APIs. No parallel membership model is introduced.

### 3.3 Tenant roles and grants

Reuse the existing canonical RBAC engine and endpoints:

```text
GET/POST/PUT/PATCH /api/v1/access/roles...
GET/POST/DELETE    /api/v1/access/roles/{roleId}/access-items...
GET                /api/v1/access/capabilities
POST               /api/v1/access/users/{userId}/role-links/{roleId}
GET                /api/v1/access/users/{userId}/role-links
PATCH               /api/v1/access/users/role-links/{grantId}/revoke
```

Canonical capabilities remain the authorization source, including:

```text
ROLE.READ
ROLE.WRITE
CAPABILITY.READ
CAPABILITY.MANAGE
USER.GRANT_ROLE
USER.REVOKE_ROLE
MEMBERSHIP.READ
MEMBERSHIP.WRITE
MEMBERSHIP.DELETE
```

No second role, permission, grant, or capability engine may be created.

### 3.4 Existing frontend API foundation

Reuse `apps/web/lib/api/users.ts` and existing API client/auth infrastructure. Extend the typed API layer for memberships/access only where the current product UI needs existing backend contracts that are not yet represented in TypeScript.

### 3.5 Platform IAM foundations

Preserve the approved Platform IAM design and the currently implemented core:

```text
PlatformAuthorizationService
PlatformMembershipService
PlatformOwnerSafetyService
platform_memberships
platform_role_metadata
existing role/capability engine
existing audit/session infrastructure
```

This closure completes the missing Platform IAM application/API/UI surfaces; it does not redesign Platform IAM.

---

## 4. Global invariants

1. **Tenant scope is authenticated context, not user input.** UI may display the active tenant but must not ask an administrator to type or select an arbitrary tenant UUID.
2. **Platform scope is configured/trusted control-plane context.** Executive Platform IAM APIs never accept a caller-supplied control tenant ID.
3. **Backend authorization is authoritative.** Frontend capability gating is UX only.
4. **Fail closed.** Missing membership, missing capability, stale session, invalid lifecycle state, or tenant mismatch denies the operation.
5. **No physical user deletion.** Tenant user removal remains lifecycle/archive behavior. Platform users follow Platform IAM lifecycle semantics.
6. **Protected owner invariant.** Effective ACTIVE `PLATFORM_OWNER` count must never fall below one, including concurrent mutations.
7. **No cross-tenant promotion.** A user with the same email in another tenant must never be reused as a control-plane platform identity.
8. **No fake product state.** No mocked users, fake role assignments, fake session/device metadata, or fabricated audit events in production surfaces.
9. **PostgreSQL Direct only.** Acceptance evidence must use the repository’s host-native PostgreSQL Direct path; Docker/Testcontainers/H2 are not certification evidence.
10. **Arabic/English and RTL/LTR are first-class.** No hardcoded production UI strings outside the established i18n conventions.
11. **WCAG 2.2 AA interaction target.** Keyboard operation, visible focus, semantic labels, and minimum touch-target conventions must match the existing SDS/ExecutiveShell standards.
12. **No security secrets in visual evidence.** Screenshots/logs must not expose passwords, tokens, raw JWTs, database credentials, or recovery secrets.

---

## 5. Product information architecture

### 5.1 Tenant administration

Canonical tenant IAM routes:

```text
/management/users
/management/users/[userId]
/management/access
```

`/management` remains the tenant-management entry surface. The Users closure adds dedicated product routes instead of restoring the historical root-page `users-live-panel` experiment.

### 5.2 Executive platform administration

Canonical Platform IAM routes:

```text
/executive/users
/executive/users/[userId]
/executive/access
```

These remain isolated from `/management/*` and use the approved Executive/Control Plane authorization chain.

### 5.3 Navigation

- `/workspace` continues to launch `/management` and `/executive` as distinct destinations.
- `/management` navigation exposes Users and Access only when the authenticated identity has the relevant tenant capabilities.
- `/executive` navigation exposes Platform Users and Platform Access only when the authenticated platform identity has the required Platform IAM read capabilities.
- Direct-route access remains protected even when navigation links are hidden.

---

## 6. Tenant Users product surface

### 6.1 `/management/users` — User Directory

Required states:

```text
loading
loaded with users
empty tenant
API error
insufficient permission
session expired
```

Required user-facing functionality:

- list tenant users;
- search/filter the loaded list by name/email/status without introducing a new server search API;
- create/invite a tenant user using the existing `POST /api/v1/users` contract;
- show lifecycle status: `INVITED | ACTIVE | INACTIVE | SUSPENDED | ARCHIVED`;
- open user detail;
- perform lifecycle actions only when the current identity has the corresponding capability;
- refresh after successful mutation;
- expose deterministic success/error feedback without leaking backend internals.

A tenant UUID must never be a form field.

### 6.2 `/management/users/[userId]` — User Detail

Required sections:

1. identity summary — display name, email, lifecycle status;
2. edit identity — email/display name;
3. lifecycle controls — activate/deactivate/suspend/archive as allowed;
4. organization memberships;
5. assigned role links, including organization-scoped grants;
6. effective-access explanation where the current backend can provide it without inventing data;
7. audit/security actions only when an existing backend contract supports them.

Role assignment must use the existing user role-link APIs. Revocation must use the existing grant ID and revoke endpoint. The UI must not mutate role tables directly.

### 6.3 Membership behavior

The detail page lists memberships through `/api/v1/users/{userId}/memberships` and links to the existing organization context where useful.

Membership mutation must reuse existing organization-membership APIs. If the current backend lacks one UI-required mutation contract, the implementation plan may add only the smallest authenticated tenant-scoped endpoint needed to expose existing domain behavior; it must not introduce a second membership service.

---

## 7. Tenant Access product surface

### 7.1 `/management/access`

The page contains two product sections:

```text
Roles
Capabilities
```

Required role functions:

- list roles;
- inspect one role;
- create custom tenant role;
- update role metadata supported by the backend;
- activate/deactivate/archive role;
- list attached capabilities;
- attach capability;
- detach capability.

Capability registry management is shown only to identities holding `CAPABILITY.MANAGE`. Read-only administrators with `CAPABILITY.READ` may inspect capabilities but cannot mutate them.

### 7.2 Safety behavior

- every mutation uses the active session tenant scope;
- backend 403/404/409 responses are presented as stable product errors;
- destructive-looking operations require an explicit confirmation step;
- archived users/roles remain visible when necessary for lifecycle auditability; no physical delete is added for this closure.

---

## 8. Executive Platform Users application layer

The approved Platform IAM plan is completed with a focused application/API layer.

### 8.1 Required backend components

Create the missing Platform IAM application types under the existing package:

```text
PlatformUserService
PlatformUserController
PlatformRoleService
PlatformAccessController
focused request/response DTOs
```

The implementation must compose existing identity, membership, role/capability, session-revocation, owner-safety, and audit services.

### 8.2 Platform User API

Canonical route family:

```text
/api/v1/executive/users
/api/v1/executive/users/{userId}
/api/v1/executive/users/{userId}/roles
/api/v1/executive/users/{userId}/effective-permissions
```

Required use cases:

- list platform users;
- get platform user detail;
- create/invite platform user in the configured control tenant;
- update supported identity attributes;
- activate membership;
- suspend membership;
- lock membership;
- disable membership;
- list role assignments;
- replace or mutate role assignments through the canonical role engine;
- show effective permissions;
- revoke sessions for security-sensitive lifecycle transitions as required by the approved Platform IAM spec.

The controller must derive control-plane scope from trusted configuration/authentication context. No `controlTenantId` request parameter is permitted.

### 8.3 Identity reuse rules

Creation must follow exactly these rules:

```text
existing identity in configured control tenant without membership
  -> reuse that row and create platform membership

same email only in another tenant
  -> do not promote or reuse that cross-tenant row

existing active platform membership
  -> conflict; do not duplicate membership
```

### 8.4 Protected owner behavior

Mutations affecting the owner membership or owner role must route through `PlatformOwnerSafetyService` and the established locking/concurrency path.

The system must reject any operation that would leave zero effective ACTIVE `PLATFORM_OWNER` identities.

---

## 9. Executive Platform Access surface

### 9.1 `/executive/users`

Required functionality:

- platform-user directory;
- search/filter by displayed identity/status;
- membership lifecycle status;
- create/invite platform user;
- quick navigation to detail;
- capability-gated actions;
- no tenant-business user rows from non-control tenants.

### 9.2 `/executive/users/[userId]`

Required sections:

```text
Platform identity
Membership status
Platform roles
Effective permissions
Temporary access
Session/security actions
Audit references where supported
```

The UI must clearly distinguish platform membership state from the underlying account state.

### 9.3 `/executive/access`

Required functionality:

- list protected system platform roles;
- list custom platform roles;
- inspect role capabilities;
- create/update custom platform role where allowed;
- assign/revoke platform roles;
- expose protected-role restrictions visibly;
- inspect platform capabilities;
- create/revoke temporary direct grants using the approved `access_scope_grants` mechanism;
- require expiry and reason for temporary access.

No UI control may allow a protected system role to be deleted or mutated in violation of `platform_role_metadata`.

---

## 10. Frontend architecture

### 10.1 Shared principles

Use the existing:

```text
AuthProvider / authenticated session
ApiClient
SDS components/tokens
ExecutiveShell
I18nProvider
existing error conventions
```

Do not introduce a second auth state manager or a separate design system.

### 10.2 API modules

Tenant UI reuses/extents:

```text
apps/web/lib/api/users.ts
apps/web/lib/api/memberships.ts
```

Add focused access clients if not already present for:

```text
roles
role capabilities
user role links
capabilities
```

Executive Platform IAM methods belong in the existing SCP/Executive API layer rather than the tenant `users.ts` client.

### 10.3 UI composition

Prefer focused route components and small domain components instead of one large Users page.

Recommended component boundaries:

```text
UserDirectory
UserCreateDialog
UserIdentityCard
UserLifecycleActions
UserMembershipsCard
UserRoleLinksCard
TenantRolesPanel
RoleCapabilitiesPanel

PlatformUserDirectory
PlatformUserCreateDialog
PlatformMembershipCard
PlatformRoleAssignmentsCard
EffectivePermissionsCard
TemporaryAccessCard
SessionSecurityCard
PlatformRolesPanel
```

Exact filenames are deferred to the implementation plan, but these responsibilities must remain separated.

---

## 11. Authorization and error semantics

### 11.1 Tenant UI

Frontend actions map to backend capabilities and are hidden/disabled when the session definitively lacks permission. Direct API calls remain protected by `@RequireCapability`.

Expected failure handling:

```text
401 -> session/authentication flow
403 -> insufficient permission; do not retry as another tenant
404 -> resource unavailable in active scope
409 -> business conflict; show stable actionable message
5xx -> stable generic error + retry affordance where safe
```

### 11.2 Platform UI

Authorization chain remains:

```text
JWT Authentication
-> trusted control-plane context
-> ControlPlaneAccessGuard
-> PlatformMembershipGuard / PlatformAuthorizationService
-> required PLATFORM.* capability
-> protected owner/business invariant
-> ALLOW or DENY
```

A legacy `platform_admin=true` flag without ACTIVE platform membership must never grant Platform IAM access.

---

## 12. Testing strategy

Every implementation task follows RED -> GREEN -> REFACTOR.

### 12.1 Backend unit tests

Must cover at minimum:

- tenant-scoped user lifecycle;
- duplicate email scoped to one tenant;
- role grant/revoke authorization;
- Platform User identity reuse rules;
- platform membership lifecycle;
- legacy `platform_admin` denial without membership;
- protected owner invariant;
- temporary grant expiry boundary;
- session invalidation on security-sensitive lifecycle transitions;
- stable 401/403/404/409 controller mappings.

### 12.2 PostgreSQL Direct acceptance

Must cover at minimum:

- Tenant A cannot read/mutate Tenant B users;
- Tenant B cannot read/mutate Tenant A users;
- user role grants cannot cross tenant boundaries;
- membership/user composite relationships reject cross-tenant references;
- platform membership cannot reference a non-control-tenant identity;
- same email in another tenant is not promoted to Platform User;
- concurrent owner-removal operations preserve at least one ACTIVE owner;
- RLS and Flyway apply under the host-native PostgreSQL Direct path.

### 12.3 Frontend tests

Required categories:

- typed API contract tests;
- validation tests;
- route authorization states;
- loading/empty/error/success states;
- capability-gated actions;
- lifecycle confirmation flows;
- Arabic/English key parity;
- RTL/LTR rendering invariants where the test framework supports them;
- keyboard interaction for primary tables/dialogs/actions.

### 12.4 Authenticated E2E

At minimum, automated or reproducible authenticated E2E must demonstrate:

**Tenant B**

```text
login
-> /management/users
-> list real users
-> open user detail
-> inspect memberships
-> inspect/modify an allowed role link
-> verify another tenant is inaccessible
```

**Control Plane operator / authorized Platform IAM identity**

```text
login
-> /executive/users
-> list platform users
-> open platform user detail
-> inspect roles/effective permissions
-> /executive/access
-> inspect protected platform roles
-> verify unauthorized/protected mutation fails closed
```

Tests must not depend on hardcoded production passwords in repository code.

---

## 13. Visual certification

Visual proof is a release gate, not decoration.

### 13.1 Required screenshots

Authenticated desktop evidence:

```text
Tenant B /management/users
Tenant B /management/users/[userId]
Tenant B /management/access
Control Plane /executive/users
Control Plane /executive/users/[userId]
Control Plane /executive/access
```

Authenticated mobile evidence for the same route families must prove responsive layout and reachable primary actions.

### 13.2 Evidence requirements

Each evidence item must identify:

```text
route
environment
main/deployed SHA where observable
authenticated role/context
viewport class (desktop/mobile)
```

Credentials, tokens, raw tenant secrets, and sensitive recovery data must be redacted/not rendered.

A successful build or existence of a route is not visual proof.

---

## 14. Delivery gates

The implementation cannot be declared closed until the sequence below completes in order:

```text
1. Task-level TDD GREEN
2. Full frontend tests GREEN
3. Full backend PostgreSQL Direct tests GREEN
4. Security / RLS / tenant-isolation focused gates GREEN
5. Authenticated E2E GREEN
6. Visual evidence prepared on exact final implementation
7. PR exact-head required CI GREEN
8. Independent review APPROVED on the exact final head
9. exact-head merge guard
10. Merge
11. Post-Merge Main Verification GREEN on the new main SHA
12. Live authenticated smoke on that same deployed main revision
13. Desktop/mobile authenticated visual proof confirmed
14. Blockers = 0
15. USERS_MODULE = FULLY_CLOSED
```

If `main` moves before merge, exact-head evidence is stale and must be regenerated after reconciliation.

---

## 15. Non-goals for this closure

The following are separate identity-platform programs and do not block this Users Module closure unless an existing dependency is found during implementation:

```text
SAML enterprise federation
SCIM 2.0 directory synchronization
external IdP lifecycle administration
new MFA methods / hardware-key management
bulk CSV user import
HR employee provisioning automation
cross-tenant support impersonation
new tenant provisioning architecture
physical deletion / GDPR purge workflow
```

Invite-email delivery and password-reset infrastructure may be linked from the Users UI only when the current backend already exposes a production-safe contract. This closure does not fabricate or duplicate account-recovery infrastructure.

---

## 16. Rollback and failure policy

- Prefer additive routes/components and reuse existing APIs.
- Forward-only Flyway migrations only if a genuinely missing Platform IAM schema addition is discovered; historical migrations remain immutable.
- Never weaken tenant isolation or owner safety to make UI tests pass.
- Never bypass backend capability checks with frontend-only controls.
- Any discovered cross-tenant leakage, owner-invariant break, or authentication bypass is a release blocker and upgrades the task to security remediation before feature continuation.
- A failed visual/E2E gate does not get waived by unit-test success.

---

## 17. Acceptance summary

The intended final product experience is:

```text
Tenant administrator
  -> Workspace
  -> Management
  -> Users
  -> User detail
  -> Memberships / Roles / Lifecycle
  -> Tenant Access / Roles / Capabilities

Platform administrator
  -> Workspace
  -> Executive
  -> Platform Users
  -> Platform User detail
  -> Platform Roles / Effective Permissions / Temporary Access
  -> Platform Access
```

Both journeys must be real, authenticated, tenant-safe, responsive, tested, and visually evidenced.

**No Users Module closure declaration is permitted before every gate in Section 14 is satisfied on the final merged main revision.**
