# SANAD User Administration & Module Access Expansion — Implementation Plan

**Date:** 2026-10-03  
**Status:** APPROVED / EXECUTION STARTED  
**Working branch:** `feat/user-admin-module-access-expansion-20261003`  
**Baseline:** `7b990cd2edbdb4fdc1ef457b602cd69f2ee5bd5a`  
**Design:** `docs/superpowers/specs/2026-10-03-user-administration-module-access-expansion-design.md`

---

## Execution order

The implementation order is intentionally strict:

```text
0. Baseline + safety
1. Username/Auth RED contracts
2. Username persistence + auth implementation
3. User profile/API expansion
4. Credential administration UI
5. Module Access Matrix read model
6. Role/scope mutation UX
7. Effective access + audit
8. E2E / PostgreSQL Direct / protected merge / production certification
```

No later phase may be used to excuse a failed earlier security contract.

---

## Phase 0 — Baseline and safety

### Goals

- bind work to the current protected `main` baseline;
- prove the prior Users closure is an ancestor;
- keep all work on an isolated feature branch;
- preserve existing email login, tenant isolation, RBAC, last-admin, and credential-rotation behavior.

### Required evidence

- baseline SHA recorded in design/plan;
- no direct writes to `main`;
- no Docker/Testcontainers path introduced;
- current Users closure documentation remains untouched.

---

## Phase 1 — Username/Auth contract (RED first)

### Purpose

Define the additive contract before implementation.

### RED tests must require

1. User domain exposes a nullable `username`.
2. Create/Update/UserResponse contracts expose username without exposing password state secrets.
3. UserRepository supports:
   - `findByTenantIdAndUsername`
   - `existsByTenantIdAndUsername`
   - `findAllByUsername`
4. LoginRequest supports additive username login while preserving email login.
5. Login validation requires exactly one identifier: email or username.
6. Username lookup with explicit tenant is tenant-scoped.
7. Username lookup without tenant:
   - zero matches -> generic invalid credentials;
   - one match -> proceed;
   - multiple tenant matches -> existing ambiguous-tenant flow.
8. Same username may exist in different tenants.
9. Duplicate username in the same tenant is rejected.
10. Password recovery remains email-based.
11. Username is normalized before persistence/lookup.
12. Existing email login tests remain green after implementation.

### RED commit discipline

The first implementation commit may intentionally contain failing contract tests. It must not claim readiness.

---

## Phase 2 — Username persistence and authentication

### Database

Create one forward-only migration that:

- adds `users.username VARCHAR(100)`;
- creates a tenant-scoped uniqueness constraint/index for non-null usernames;
- does not invent usernames for existing users;
- preserves current email uniqueness semantics.

Recommended PostgreSQL shape:

```sql
ALTER TABLE users ADD COLUMN username VARCHAR(100);

CREATE UNIQUE INDEX ... ON users (tenant_id, lower(username))
WHERE username IS NOT NULL;
```

The actual migration must follow repository naming/version order at implementation time.

### Domain

Update `User`:

- field/getter/setter;
- normalization;
- safe `toString` treatment;
- equality semantics remain based on stable identity/current existing model, not username.

### DTO/API

Update:

- `CreateUserRequest`
- `UpdateUserRequest`
- `UserResponse`
- mapper/service validation
- typed web `usersApi`

Compatibility rule:

- backend API accepts null username for legacy callers during rollout;
- management UI requires username for newly created users after the UI lands.

### Repository

Add tenant-scoped and cross-tenant login lookups.

### Auth

Update LoginRequest additively.

Recommended request compatibility:

```json
{ "email": "user@example.com", "password": "...", "tenantId": "..." }
```

or:

```json
{ "username": "mohammad.ahmed", "password": "...", "tenantId": "..." }
```

Never accept both identifiers simultaneously.

### Security tests

Require:

- cross-tenant username collision is allowed;
- tenant-local username collision is rejected;
- ambiguous username never auto-selects a tenant;
- generic invalid-credential behavior remains enumeration-safe;
- rate-limit keys normalize the chosen identifier without leaking secrets.

---

## Phase 3 — User profile/API expansion

### Existing data to surface safely

The domain already carries fields not currently exposed in UserResponse:

- mobileNumber
- mobileRegion
- lastLoginAt
- mustChangePassword / credential-rotation state

Expose only non-secret metadata needed by the management UI.

### Do not expose

- passwordHash;
- reset token/hash;
- refresh tokens;
- raw JWT;
- credential value;
- sensitive recovery internals.

### UI

Update:

- `/management/users` create dialog;
- `/management/users/[userId]` profile editor.

Fields:

- display name
- username
- email
- mobile
- region
- lifecycle status
- last login
- credential-state indicators

---

## Phase 4 — Credential administration UI

### Existing backend contracts to reuse

```text
POST /api/v1/auth/admin-initialize-credential/{userId}
POST /api/v1/auth/admin-reset-password/{userId}
```

### UI behavior

#### Initialize credential

Visible only when:

- actor has `USER.WRITE`;
- target is eligible;
- no credential is initialized.

Requirements:

- temporary password input;
- confirmation input;
- value never persisted client-side after success;
- no reveal-after-submit behavior;
- success message tells admin the user must rotate the credential.

#### Reset password

Visible for existing-credential users.

Action:

- issue single-use set-password link;
- never ask admin to choose the replacement password;
- show delivery/result status only.

### Security tests

- direct admin overwrite remains rejected;
- initialize-once semantics enforced;
- reset invalidates/revokes sessions according to existing auth service rules;
- responses contain no password/hash/token in production mode.

---

## Phase 5 — Module Access Matrix read model

### Goal

Add one product view to the User Administration Workspace.

### First rendering

Group current effective access into:

```text
CRM
HRM
Workflow
ERP
Finance/Accounting
Ecommerce
POS
Executive
Other
```

Grouping is based on capability namespace and governed role metadata.

### Data sources

Reuse:

- user role links;
- roles;
- role capabilities;
- effective permissions where available;
- authorization overrides/relationships where applicable.

Avoid N+1 API fan-out. If composition becomes excessive, introduce one backend projection endpoint rather than duplicating authorization rules in Next.js.

### Display

For each module:

- effective access status;
- assigned roles;
- scope;
- summarized capabilities;
- advanced capability list.

No module `enabled` boolean becomes a security source of truth.

---

## Phase 6 — Role and scope mutation UX

### Role assignment

Use existing:

```text
POST /api/v1/access/users/{userId}/role-links/{roleId}
PATCH /api/v1/access/users/role-links/{grantId}/revoke
```

### Scope

Expose:

- tenant-wide grant;
- organization-scoped grant via existing `organizationId`.

Do not invent universal department scope.

Where HRM/CRM/Workflow have canonical relationship/scope services, integrate them through explicit module adapters and show the source in effective access.

### UX

Administrator can:

- add role;
- select supported scope;
- revoke role;
- see inherited capability effects before/after mutation;
- receive stable 403/404/409 errors.

---

## Phase 7 — Effective access and audit

### Effective access

Show backend-authoritative result, including where available:

- role-derived allow;
- override allow/deny;
- relationship/scope;
- effective capability;
- source/reason.

### Audit

Record all user administration mutations without secrets.

Minimum events:

- USER_CREATED
- USER_PROFILE_UPDATED
- USER_USERNAME_CHANGED
- USER_ACTIVATED
- USER_SUSPENDED
- USER_DEACTIVATED
- USER_ARCHIVED
- USER_CREDENTIAL_INITIALIZED
- USER_RESET_LINK_ISSUED
- USER_ROLE_GRANTED
- USER_ROLE_REVOKED
- USER_SCOPE_CHANGED
- ROLE_CAPABILITY_ATTACHED
- ROLE_CAPABILITY_DETACHED

---

## Phase 8 — Verification and release

### Focused verification

Require focused tests for:

- username migration/repository;
- user service/controller;
- auth username/email compatibility;
- ambiguous tenant behavior;
- credential initialization/reset;
- role grants/scopes;
- effective access grouping;
- last-admin guard;
- cross-tenant denial.

### Full backend

Run the complete PostgreSQL Direct suite.

Hard gate:

```text
FAILURES = 0
ERRORS = 0
```

### Frontend

Require:

- lint;
- typecheck;
- unit tests;
- production build;
- accessibility;
- RTL/LTR;
- responsive desktop/mobile;
- no secret leakage in snapshots/artifacts.

### Authenticated E2E

Must cover at least:

1. admin login;
2. create user with username;
3. initialize temporary credential;
4. user first login by username;
5. forced credential rotation;
6. admin assigns CRM role;
7. admin assigns Workflow role with supported scope;
8. effective module access reflects both;
9. revoke one role and verify access removal;
10. suspend account and prove login denial;
11. reactivate;
12. archive and prove login denial;
13. cross-tenant mutation/read denial.

### Merge governance

Before merge:

- exact-head CI green;
- independent review approved on exact head;
- no stale approval;
- protected merge only.

After merge:

- Post-Merge Main Verification green on new main SHA;
- live production deployment lineage verified;
- authenticated production smoke;
- production blocker count = 0.

Only then may a new expansion closure document be created.

---

## Initial file map

Expected files include, subject to repository reality at implementation time:

### Backend

```text
apps/sanad-platform/src/main/java/com/sanad/platform/user/domain/User.java
apps/sanad-platform/src/main/java/com/sanad/platform/user/dto/CreateUserRequest.java
apps/sanad-platform/src/main/java/com/sanad/platform/user/dto/UpdateUserRequest.java
apps/sanad-platform/src/main/java/com/sanad/platform/user/dto/UserResponse.java
apps/sanad-platform/src/main/java/com/sanad/platform/user/repository/UserRepository.java
apps/sanad-platform/src/main/java/com/sanad/platform/user/service/UserService.java
apps/sanad-platform/src/main/java/com/sanad/platform/security/dto/LoginRequest.java
apps/sanad-platform/src/main/java/com/sanad/platform/security/service/AuthService.java
apps/sanad-platform/src/main/resources/db/migration/<next>__add_tenant_username.sql
```

### Frontend

```text
apps/web/lib/api/users.ts
apps/web/lib/api/tenant-access.ts
apps/web/app/management/users/page.tsx
apps/web/app/management/users/[userId]/page.tsx
apps/web/app/management/users/_components/*
apps/web/app/management/access/*
```

### Tests

Focused RED/GREEN tests under:

```text
apps/sanad-platform/src/test/java/com/sanad/platform/user/**
apps/sanad-platform/src/test/java/com/sanad/platform/security/**
apps/web/lib/api/**
apps/web/app/management/users/**
apps/web/e2e/**
```

---

## Execution state

```text
DESIGN_SPEC = CREATED
IMPLEMENTATION_PLAN = CREATED
PHASE_0_BASELINE = PASS
PHASE_1_USERNAME_AUTH_CONTRACT = STARTING
PHASE_2_USERNAME_IMPLEMENTATION = NOT_STARTED
PHASE_3_PROFILE_API = NOT_STARTED
PHASE_4_CREDENTIAL_UI = NOT_STARTED
PHASE_5_MODULE_ACCESS_MATRIX = NOT_STARTED
PHASE_6_SCOPE_MUTATIONS = NOT_STARTED
PHASE_7_EFFECTIVE_ACCESS_AUDIT = NOT_STARTED
PHASE_8_RELEASE_CERTIFICATION = NOT_STARTED
```
