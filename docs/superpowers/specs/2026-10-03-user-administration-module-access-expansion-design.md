# SANAD User Administration & Module Access Expansion — Design Specification

**Date:** 2026-10-03  
**Status:** APPROVED FOR IMPLEMENTATION  
**Repository:** `snadaiapp-png/SNAD`  
**Working branch:** `feat/user-admin-module-access-expansion-20261003`  
**Implementation baseline:** `7b990cd2edbdb4fdc1ef457b602cd69f2ee5bd5a`  
**Parent Users closure:** `4c1e283112442b6a221a5f6db35ebf32078d6357` — remains closed/canonical.  
**Governance rule:** This expansion is additive work after Users Module closure. It does not reopen or rewrite the prior closure unless a regression is proven against the closed scope.

---

## 1. Product objective

Deliver one tenant-scoped **User Administration Workspace** from which an authorized administrator can:

- create a user;
- view and edit identity/profile data;
- assign a tenant-unique username;
- initialize a first credential safely;
- issue administrator password-reset links;
- activate, suspend, deactivate, and archive a user;
- assign/revoke roles;
- grant module access through canonical roles/capabilities;
- constrain role grants to supported scopes;
- inspect the user's effective access;
- understand why access is allowed or denied without exposing secrets.

The governing model is:

```text
User -> Tenant -> Module -> Role -> Capabilities -> Scope
```

A user is created **once per tenant**. HRM, CRM, Workflow, ERP, Accounting, Ecommerce, POS, and later modules consume the same identity and authorization model. No module-specific duplicate user table or independent IAM system is permitted.

---

## 2. Security invariants

The following are non-negotiable:

1. **Tenant isolation remains authoritative.** User, role, grant, scope, credential, and effective-access mutations are bound to the authenticated tenant.
2. **No physical delete.** Administrative "delete" means archive/deactivate. Referential integrity and audit history remain intact.
3. **Passwords are never readable.** No API, UI, audit event, log, screenshot, or telemetry surface may return a current password or password hash.
4. **Initial credential is write-only.** An administrator may initialize a credential only for an eligible user with no credential. The credential must be rotated by the user.
5. **Existing credentials cannot be overwritten by admin initialization.** Subsequent recovery uses the single-use reset flow.
6. **Session revocation is immediate on credential changes.** Existing session-version semantics remain authoritative.
7. **Last-admin/owner guards remain enforced.** Archive, suspension, deactivation, role removal, and scope changes must not bypass protected administrator invariants.
8. **Role/capability enforcement is backend authoritative.** UI gating is UX only.
9. **PostgreSQL Direct only** for certification. No Docker/Testcontainers/H2 evidence qualifies for final acceptance.
10. **No second authorization model.** Module access is a projection over roles, capabilities, grants, and supported scopes.
11. **Fail closed.** Missing, stale, unknown, or degraded authorization state never produces access.
12. **Audit without secrets.** Actor, target, action, role/capability/scope, reason, and outcome may be audited; raw passwords/tokens may not.

---

## 3. Canonical product routes

Tenant administration remains under:

```text
/management/users
/management/users/[userId]
/management/access
```

The user detail route evolves into the canonical **User Administration Workspace**.

The Executive Platform IAM routes remain separate:

```text
/executive/users
/executive/access
/executive/authorization
```

`PLATFORM_USER != TENANT_USER` remains a hard security boundary.

---

## 4. User profile model

### 4.1 Existing canonical fields

The existing tenant User model remains the identity source for:

- id
- tenantId
- email
- displayName
- mobileNumber
- mobileRegion
- lifecycle status
- credential metadata
- lastLoginAt
- sessionVersion
- timestamps

### 4.2 New username

Add a canonical tenant-scoped `username` with these semantics:

- normalized to lowercase and trimmed;
- case-insensitive from the user's perspective;
- unique **within one tenant**;
- may repeat across different tenants;
- stored independently from email;
- never used as cross-tenant authority;
- initially nullable for backward compatibility with existing users;
- new UI-created users must provide a username after the rollout gate is enabled;
- legacy users without a username remain able to sign in by email.

Database migration requirements:

```text
users.username VARCHAR(100) NULL
UNIQUE tenant-scoped index for non-null normalized usernames
```

A migration must not fabricate usernames for existing accounts. Backfill, if later desired, is a separate governed operation.

### 4.3 Login compatibility

Existing email login remains supported.

The login contract becomes additive:

```text
email + password [+ tenantId]        -> existing path
username + password [+ tenantId]     -> new path
```

Exactly one login identifier is supplied per request.

Because username uniqueness is tenant-scoped, username login without `tenantId` may resolve to more than one tenant. The existing ambiguous-tenant behavior is reused; the backend must not guess a tenant.

Password recovery remains email-based. Username must not become a recovery secret.

---

## 5. Credential administration

The system already exposes safe credential primitives and the UI must compose them rather than inventing a new credential store.

### 5.1 First credential

For an eligible ACTIVE user that has no credential:

```text
POST /api/v1/auth/admin-initialize-credential/{userId}
```

The administrator supplies a temporary credential once. The backend stores only the password hash and marks credential rotation required.

The UI must:

- use a password input;
- never redisplay the submitted value;
- clear the value immediately after a successful request;
- show that rotation is required;
- never place the credential in URLs, logs, analytics, or screenshots.

### 5.2 Subsequent reset

For a user with an existing credential:

```text
POST /api/v1/auth/admin-reset-password/{userId}
```

The administrator issues a single-use set-password link. Direct administrative overwrite of an existing password remains prohibited.

### 5.3 User self-service

Existing authenticated `change-credential`, forgot-password, and reset-password flows remain authoritative and compatible.

---

## 6. Lifecycle and deletion semantics

User lifecycle remains:

```text
INVITED
ACTIVE
INACTIVE
SUSPENDED
ARCHIVED
```

Administrative Delete maps to **Archive**.

No `DELETE FROM users` product action is introduced.

The UI must clearly distinguish:

- Suspend — temporary security/operational hold;
- Deactivate — login/access disabled;
- Archive — administrative deletion state retained for audit/history;
- Activate — restore an eligible account.

Protected last-admin invariants remain enforced by the backend.

---

## 7. Module Access Matrix

### 7.1 Principle

Module access is not persisted as a parallel boolean.

It is derived from canonical authorization state:

```text
assigned role grants
+ role capabilities
+ direct governed overrides where supported
+ scope
= effective module access
```

The matrix is a product projection over this state.

### 7.2 Initial modules

The first matrix groups capabilities for:

- CRM
- HRM
- Workflow
- ERP
- Accounting / Finance
- Ecommerce
- POS
- Executive read access where tenant-appropriate

Additional modules must be data-driven by capability namespace, not hardcoded security rules in the browser.

### 7.3 Canonical role templates

Existing templates remain valid starting points, including:

```text
CRM_SALES
HR_MANAGER
ERP_PURCHASER
ERP_APPROVER
FINANCE_USER
FINANCE_APPROVER
STORE_MANAGER
WORKFLOW_APPROVER
EXECUTIVE_VIEWER
TENANT_ADMIN
```

Examples of current exact template intent:

```text
CRM_SALES
  -> core CRM read/write + lead conversion capabilities

HR_MANAGER
  -> HR.EMPLOYEE.READ
  -> HR.EMPLOYEE.WRITE
  -> HR.EMPLOYEE.ARCHIVE

WORKFLOW_APPROVER
  -> WORKFLOW.VIEW
  -> WORKFLOW.APPROVE
  -> intentionally no WORKFLOW.WRITE
```

Newer HRM capability families are not silently added to `HR_MANAGER`. If a user needs performance, leave, structure, termination, or other HRM authority, use an explicitly governed role/capability assignment.

---

## 8. Role and capability administration

The matrix must reuse existing tenant access APIs:

- list/create/update/lifecycle roles;
- list capabilities;
- list/attach/detach role capabilities;
- list/grant/revoke user-role links.

The user workspace may grant multiple roles to one user.

Effective capability union must be visible to authorized administrators.

Custom roles remain tenant-scoped. Canonical SNAD template roles must not be silently converted into customer-managed roles or widened outside their governed matrices.

---

## 9. Scope model

### 9.1 Core scope

The current user-role grant contract already supports:

```text
TENANT-wide role grant
OR
organizationId-scoped role grant
```

The UI will expose this instead of always sending an unscoped tenant-wide grant.

### 9.2 Department and domain scopes

A generic fake `departmentId` field must not be added to every role grant.

Department/team/territory/project scope must use the canonical module-specific authorization relationship/scope mechanism where one exists.

The matrix therefore presents:

```text
Tenant
Organization
Module-specific governed scope
```

only when the backend can prove and enforce that scope.

---

## 10. Effective access

The administrator must be able to inspect:

- assigned roles;
- organization-scoped grants;
- effective capability codes;
- direct allow/deny overrides where authorized;
- relationship/scope sources where available;
- account lifecycle and credential state.

The UI must distinguish **assigned** access from **effective** access.

No frontend-only calculation is allowed to be treated as security authority.

---

## 11. User Administration Workspace information architecture

`/management/users/[userId]` evolves into these sections:

```text
Overview
Profile
Login & Security
Module Access
Roles & Permissions
Memberships & Scope
Effective Access
Lifecycle
Audit / security actions (when supported)
```

### 11.1 Overview/Profile

- display name
- username
- email
- mobile number
- mobile region
- lifecycle status
- created/updated timestamps
- last login

### 11.2 Login & Security

- username
- email login compatibility
- credential initialized: yes/no
- credential rotation required
- initialize temporary credential when eligible
- issue reset link
- never show current password

### 11.3 Module Access

For each module:

```text
Module
Effective access
Assigned role(s)
Scope
Capability summary
Manage action
```

Advanced view exposes exact capability codes.

---

## 12. API evolution rules

All changes are additive where possible.

Required backend changes include:

- `username` on User entity/DTOs;
- tenant-scoped username repository lookups;
- additive username login request support;
- safe user profile exposure for mobile/credential status metadata;
- no password/hash exposure;
- optional API projection for grouped/effective module access if frontend composition would otherwise create N+1 or inconsistent decisions.

Existing API clients that use email-only login must remain valid.

---

## 13. Audit requirements

At minimum, audit:

- user created;
- username changed;
- profile changed;
- lifecycle transition;
- credential initialized (never credential value);
- admin reset link issued;
- role granted/revoked;
- scope changed;
- role capability attached/detached;
- authorization override created/revoked when used.

Audit records must capture actor, tenant, target, action, result, timestamp, and non-secret reason/context.

---

## 14. Acceptance criteria

The expansion is complete only when all of the following are true:

1. Existing Users closure remains intact.
2. Username migration is forward-only and PostgreSQL-safe.
3. Duplicate username in one tenant is rejected.
4. Same username in different tenants is allowed.
5. Legacy email login still works.
6. Username login works and never guesses among multiple tenants.
7. Passwords/hashes are absent from every response and audit record.
8. First credential initialization is one-time and forces rotation.
9. Existing-credential admin overwrite is rejected.
10. Admin reset uses single-use recovery flow.
11. Create/edit/profile/lifecycle UI is capability-gated and tenant-bound.
12. Archive is the administrative delete operation.
13. Module matrix is backed by canonical roles/capabilities, not browser booleans.
14. Multiple roles per user work.
15. Organization-scoped role grants work through the UI.
16. Effective access is displayed from backend-authoritative state.
17. Cross-tenant user/role/scope mutations are denied.
18. Last-admin protections remain green.
19. Arabic/English, RTL/LTR, responsive, keyboard, and accessibility gates pass.
20. Full PostgreSQL Direct regression passes with `FAILURES=0` and `ERRORS=0`.
21. Authenticated desktop/mobile E2E evidence is captured.
22. Exact-head review, protected merge, post-merge verification, and live production certification pass before final expansion closure.

---

## 15. Non-goals for this expansion

The following remain separate future programs unless explicitly added later:

- SAML federation;
- SCIM synchronization;
- passkey/hardware-key administration;
- external IdP lifecycle automation;
- physical deletion of user history;
- storing per-module duplicate user identities;
- exposing or recovering an existing plaintext password.

---

## Final design decision

```text
USER_ADMINISTRATION_EXPANSION = APPROVED
USERNAME = TENANT_SCOPED / ADDITIVE / BACKWARD_COMPATIBLE
PASSWORD_VISIBILITY = PROHIBITED
ADMIN_PASSWORD_OVERWRITE = PROHIBITED
ADMIN_INITIAL_CREDENTIAL = ALLOWED_ONCE
ADMIN_RESET = SINGLE_USE_LINK
USER_DELETE = ARCHIVE
MODULE_ACCESS = ROLE + CAPABILITY + SCOPE
PARALLEL_MODULE_IAM = PROHIBITED
POSTGRESQL_DIRECT = REQUIRED
```
