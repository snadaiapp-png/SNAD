# SNAD Platform IAM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build production-grade Platform IAM for SNAD Executive/Control Plane users with explicit platform membership, protected platform roles, granular capability authorization, temporary grants, session revocation, audit, and Executive UI surfaces.

**Architecture:** Reuse the existing tenant-scoped `users`, `roles`, `access_capabilities`, `role_capabilities`, `user_role_assignments`, `access_scope_grants`, JWT/session infrastructure, `CapabilityEvaluationService`, `ControlPlaneAccessGuard`, and Executive shell. Add explicit `platform_memberships` and `platform_role_metadata`, then introduce a thin Platform IAM application layer that composes those existing primitives and enforces owner-safety and fail-closed platform membership before capability evaluation.

**Tech Stack:** Java 21, Spring Boot 3, Spring Security, Spring Data/JDBC/JPA as already used in the repository, PostgreSQL, Flyway, JUnit 5, AssertJ, Next.js/React/TypeScript, existing SDS components, existing i18n, existing SCP access provider.

**Spec:** `docs/superpowers/specs/2026-09-25-platform-iam-design.md`

## Global Constraints

- `PLATFORM_USER != TENANT_USER` is a hard security boundary.
- `platform_admin=true` remains bootstrap compatibility only and is never an authorization source by itself.
- Reuse the existing capability engine; do not create duplicate platform role/capability engines.
- Executive Platform IAM APIs never trust a caller-supplied control tenant ID.
- Platform-to-tenant business-data access is out of scope and must remain explicit, scoped, time-bound, reason-bound, and audited.
- No physical deletion of Platform Users in this delivery.
- Protected owner invariant: effective ACTIVE `PLATFORM_OWNER` count must never fall below 1.
- `SUSPENDED`, `LOCKED`, and `DISABLED` membership mutations revoke active refresh tokens and invalidate access sessions.
- Temporary direct grants require `effective_to`, `reason`, and `granted_by`.
- Audit must never persist passwords, password hashes, raw JWTs, refresh tokens, MFA secrets, or recovery secrets.
- PostgreSQL Direct is the certification path; no Docker, Testcontainers, or H2 as acceptance evidence.
- Historical Flyway migrations are immutable; add forward-only migrations.
- Frontend permission gating is UX only; backend authorization remains authoritative.
- Do not fabricate MFA, device, browser, or IP session metadata that the current backend does not provide.

## Review Focus

- Existing control-plane users with `platform_admin=true` but no membership must be denied until bootstrapped into `platform_memberships`; Task 4 adds an explicit negative authorization test.
- The same email may exist in a tenant other than the control tenant; Platform User creation must not promote that row across tenants; Task 6 tests this explicitly.
- Concurrent owner-removal mutations must preserve at least one ACTIVE owner; Task 5 adds a PostgreSQL concurrency acceptance test.
- Temporary grants with an exact expiry boundary must deny at or after `effective_to`; Task 4 pins this boundary condition.
- Session invalidation must cover already-issued access tokens through `session_version`, not only refresh tokens; Task 7 verifies both mechanisms.

---

## File Structure Map

### Backend — new Platform IAM package

- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/domain/PlatformMembershipStatus.java` — lifecycle enum.
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/domain/PlatformMembership.java` — membership aggregate/entity.
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/domain/PlatformRoleMetadata.java` — system/custom/protected/owner role metadata.
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/repository/PlatformMembershipRepository.java` — membership persistence contract.
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/repository/PlatformRoleMetadataRepository.java` — role metadata persistence contract.
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformMembershipService.java` — lifecycle transitions and membership lookup.
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformAuthorizationService.java` — platform authorization orchestration.
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformOwnerSafetyService.java` — protected-owner invariant and locking.
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformUserService.java` — Executive user directory/detail/create/update lifecycle use cases.
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformRoleService.java` — role/capability assignment and protected-role rules.
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/api/PlatformUserController.java` — `/api/v1/executive/users` API.
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/api/PlatformAccessController.java` — `/api/v1/executive/roles`, capabilities, temporary access API.
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/dto/...` — focused request/response records/classes.
- `apps/sanad-platform/src/main/java/com/sanad/platform/security/authorization/PlatformMembershipGuard.java` — hard membership boundary for Executive Platform IAM endpoints.

### Backend — existing files to extend

- `apps/sanad-platform/src/main/java/com/sanad/platform/security/service/AuthService.java` — expose/reuse a session-revocation operation suitable for administrative use without duplicating token logic.
- `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/rbac/ControlPlaneAccessService.java` — include new Platform IAM read capabilities in access-check output as required by navigation/pages.
- `apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/PlatformAuditService.java` — reuse existing API; only extend if a focused helper is required.
- `apps/sanad-platform/src/main/java/com/sanad/platform/config/GlobalDiagnosticExceptionHandler.java` or the existing global exception mapping path — map stable Platform IAM conflicts/denials to 401/403/404/409 consistently.

### Database

- `apps/sanad-platform/src/main/resources/db/migration/V20260926_1__create_platform_iam_memberships.sql`
- `apps/sanad-platform/src/main/resources/db/migration/V20260926_2__create_platform_role_metadata.sql`
- `apps/sanad-platform/src/main/resources/db/migration/V20260926_3__seed_platform_iam_capabilities.sql`
- `apps/sanad-platform/src/main/resources/db/migration/V20260926_4__seed_platform_iam_roles.sql`
- `apps/sanad-platform/src/main/resources/db/migration/V20260926_5__bootstrap_platform_owner_membership.sql`

### Frontend

- `apps/web/lib/api/scp-api.ts` — add Platform IAM API types/methods.
- `apps/web/app/executive/_components/ScpNav.tsx` — add Users and Access links.
- `apps/web/app/executive/users/page.tsx` — platform user directory.
- `apps/web/app/executive/users/[userId]/page.tsx` — user detail.
- `apps/web/app/executive/access/page.tsx` — roles/capabilities/temporary-access surface.
- `apps/web/lib/i18n/locales/ar.ts` — Arabic keys.
- `apps/web/lib/i18n/locales/en.ts` — English keys.

---

### Task 1: Platform IAM schema and migration contracts

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260926_1__create_platform_iam_memberships.sql`
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260926_2__create_platform_role_metadata.sql`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformIamSchemaMigrationContractTest.java`

**Interfaces:**
- Consumes: existing `users(tenant_id,id)`, `roles(tenant_id,id)`, `tenants(id)`.
- Produces: `platform_memberships` and `platform_role_metadata` tables with exact lifecycle/protection constraints required by later tasks.

- [ ] **Step 1: Write the failing migration contract test**

Create tests named:

```java
platformMembershipSchemaMustEnforceControlTenantUserIdentity()
platformMembershipSchemaMustRestrictLifecycleValues()
platformRoleMetadataMustReferenceExistingControlTenantRole()
platformRoleMetadataMustExposeProtectedAndOwnerFlags()
```

Assertions must verify the SQL contains the composite membership FK `(control_tenant_id, user_id) -> users(tenant_id, id)`, unique membership constraint, lifecycle values `INVITED|ACTIVE|SUSPENDED|LOCKED|DISABLED`, and role metadata fields `role_type`, `protected`, `owner_role`.

- [ ] **Step 2: Run the contract test and verify RED**

Run:

```bash
cd apps/sanad-platform && ./mvnw -Dtest=PlatformIamSchemaMigrationContractTest test
```

Expected: FAIL because migration files do not exist.

- [ ] **Step 3: Implement the two Flyway migrations**

`platform_memberships` must include the spec fields, indexes on `(control_tenant_id,status)` and `(control_tenant_id,user_id)`, timestamps, actor columns, and status check constraint. `platform_role_metadata` must uniquely identify `(control_tenant_id,role_id)` and classify `SYSTEM|CUSTOM`.

- [ ] **Step 4: Add PostgreSQL RLS policy where compatible with the established control-tenant context**

Use the repository's existing fail-closed `current_setting('app.tenant_id', true)` pattern for tenant-owned rows. The policy must not allow a caller to select an arbitrary control tenant.

- [ ] **Step 5: Re-run migration contract tests**

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260926_1__create_platform_iam_memberships.sql \
        apps/sanad-platform/src/main/resources/db/migration/V20260926_2__create_platform_role_metadata.sql \
        apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformIamSchemaMigrationContractTest.java
git commit -m "feat(platform-iam): add membership schema"
```

---

### Task 2: Seed Platform IAM capabilities, protected roles, and owner bootstrap

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260926_3__seed_platform_iam_capabilities.sql`
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260926_4__seed_platform_iam_roles.sql`
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260926_5__bootstrap_platform_owner_membership.sql`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformIamSeedMigrationContractTest.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformIamBootstrapPostgresAcceptanceTest.java`

**Interfaces:**
- Consumes: Task 1 schema, existing canonical owner row, roles/capabilities/grants.
- Produces: canonical Platform IAM capability catalog, system roles, metadata, ACTIVE owner membership, `PLATFORM_OWNER` assignment.

- [ ] **Step 1: Write failing capability/role migration contract tests**

Pin all capability codes from the spec and roles:

```text
PLATFORM_OWNER
PLATFORM_ADMIN
SECURITY_ADMIN
BILLING_ADMIN
SUPPORT_OPERATOR
READ_ONLY_AUDITOR
```

- [ ] **Step 2: Write failing PostgreSQL owner-bootstrap acceptance test**

Assert after Flyway:

```text
canonical owner row remains ACTIVE
platform_admin remains true for compatibility
exactly one ACTIVE platform_membership exists for canonical owner
PLATFORM_OWNER role is ACTIVE and protected/owner_role
canonical owner has ACTIVE tenant-wide assignment to PLATFORM_OWNER
PLATFORM_OWNER has all ACTIVE PLATFORM.* capabilities
```

- [ ] **Step 3: Run tests and verify RED**

Run contract test normally and PostgreSQL acceptance under the repository's `pg-acceptance` profile.

- [ ] **Step 4: Implement idempotent seed migrations**

Use deterministic lookups against the configured/canonical control tenant conventions already established by the repository. Do not create a second owner identity and do not mutate credentials.

- [ ] **Step 5: Add fail-closed verification to owner bootstrap migration**

The migration must raise an exception if the canonical owner identity or required control-plane role prerequisites cannot be resolved safely.

- [ ] **Step 6: Re-run tests and verify PASS**

- [ ] **Step 7: Commit**

```bash
git commit -am "feat(platform-iam): seed roles capabilities and owner membership"
```

---

### Task 3: Membership domain model and persistence

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/domain/PlatformMembershipStatus.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/domain/PlatformMembership.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/domain/PlatformRoleMetadata.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/repository/PlatformMembershipRepository.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/repository/PlatformRoleMetadataRepository.java`
- Create: repository implementations following the dominant repository pattern in this package area.
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformMembershipRepositoryPostgresTest.java`

**Interfaces:**
- Produces:
  - `Optional<PlatformMembership> findByControlTenantIdAndUserId(UUID controlTenantId, UUID userId)`
  - `List<PlatformMembership> findByControlTenantId(UUID controlTenantId)`
  - `PlatformMembership save(PlatformMembership membership)`
  - row-locking method for owner-sensitive membership reads, e.g. `List<PlatformMembership> lockActiveMembershipsByRoleCode(UUID controlTenantId, String roleCode)` implemented through the owner-safety service/repository boundary.

- [ ] **Step 1: Write repository tests for create/read/update uniqueness and cross-tenant FK rejection**

- [ ] **Step 2: Run tests and verify RED**

- [ ] **Step 3: Implement domain types and repositories**

`PlatformMembershipStatus` values must exactly match the spec. Keep entities focused; no authorization logic in persistence classes.

- [ ] **Step 4: Re-run tests and verify PASS**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(platform-iam): add membership persistence"
```

---

### Task 4: Platform membership guard and authorization orchestration

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/security/authorization/PlatformMembershipGuard.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformAuthorizationService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformMembershipService.java`
- Extend only if needed: `apps/sanad-platform/src/main/java/com/sanad/platform/security/scope/JdbcAccessScopeRepository.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformAuthorizationServiceTest.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformAuthorizationPostgresAcceptanceTest.java`

**Interfaces:**
- Produces:
  - `void PlatformMembershipGuard.requireActive(Authentication authentication)`
  - `AccessDecisionResponse PlatformAuthorizationService.evaluate(Authentication authentication, String capabilityCode)` or an equivalent existing decision type preserving explainability.
  - membership lifecycle read helpers used by controllers/services.

- [ ] **Step 1: Write unit tests for membership guard fail-closed behavior**

Cover unauthenticated, wrong control tenant, no membership, `INVITED`, `SUSPENDED`, `LOCKED`, `DISABLED`, and `ACTIVE`.

- [ ] **Step 2: Add authorization tests for legacy flag isolation**

Assert `platform_admin=true` with no ACTIVE membership is denied.

- [ ] **Step 3: Add temporary-grant tests**

Assert `effective_from <= now < effective_to` allows only the exact capability, while `now >= effective_to` denies. Revoked grants deny.

- [ ] **Step 4: Run tests and verify RED**

- [ ] **Step 5: Implement `PlatformMembershipGuard`**

Resolve trusted `tenant_id` and `user_id` from authenticated context; never from request parameters.

- [ ] **Step 6: Implement `PlatformAuthorizationService`**

Decision order must match the spec: control-plane boundary -> active membership -> active account -> protected invariant -> direct temporary grant -> existing role/capability engine -> deny.

- [ ] **Step 7: Re-run unit and PostgreSQL acceptance tests**

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git commit -am "feat(platform-iam): enforce platform membership authorization"
```

---

### Task 5: Protected owner safety and concurrency

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformOwnerSafetyService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/exception/LastPlatformOwnerException.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformOwnerSafetyServiceTest.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformOwnerConcurrencyPostgresAcceptanceTest.java`

**Interfaces:**
- Produces:
  - `void assertMayDeactivateMembership(UUID controlTenantId, UUID userId)`
  - `void assertMayRemoveOwnerRole(UUID controlTenantId, UUID userId, UUID roleId)`
  - stable conflict reason `LAST_PLATFORM_OWNER`.

- [ ] **Step 1: Write single-owner rejection tests**

Assert suspend, lock, disable, and owner-role removal all throw `LastPlatformOwnerException` when they would leave zero effective ACTIVE owners.

- [ ] **Step 2: Write two-owner positive tests**

Assert one owner may be deactivated/role-removed while another effective ACTIVE owner remains.

- [ ] **Step 3: Write PostgreSQL concurrency test**

Run two concurrent owner-removal mutations against two owners. Assert at least one mutation is rejected and final effective ACTIVE owner count is `>= 1`.

- [ ] **Step 4: Run tests and verify RED**

- [ ] **Step 5: Implement transaction/locking strategy**

Use database row locking around the effective owner set or equivalent serializable protection; a plain count-before-update without locking is not acceptable.

- [ ] **Step 6: Re-run tests and verify PASS**

- [ ] **Step 7: Commit**

```bash
git commit -am "feat(platform-iam): protect last platform owner"
```

---

### Task 6: Platform User application service and Executive APIs

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformUserService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/api/PlatformUserController.java`
- Create: focused DTOs under `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/dto/`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformUserServiceTest.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformUserControllerAuthorizationTest.java`

**Interfaces:**
- Produces Executive API contract from the spec for list/create/detail/update/activate/suspend/lock/disable/roles/effective permissions.
- `PlatformUserService.createPlatformUser(Authentication actor, CreatePlatformUserRequest request)` resolves the control tenant internally.

- [ ] **Step 1: Write service tests for create/reuse rules**

Cover:

```text
existing identity in control tenant without membership -> reuse row, create membership
same email only in another tenant -> do not promote cross-tenant row; create/reuse only control-tenant identity
existing active platform membership -> 409 duplicate membership
```

- [ ] **Step 2: Write controller authorization tests**

Pin exact `@RequireCapability` codes for each endpoint and verify tenant-only ADMIN access is denied by platform membership boundary.

- [ ] **Step 3: Write lifecycle transition tests**

Reject invalid transitions and require reason for sensitive transitions required by policy.

- [ ] **Step 4: Run tests and verify RED**

- [ ] **Step 5: Implement service and controller**

Do not accept `controlTenantId` in request DTOs. Return sanitized user/membership/role data only.

- [ ] **Step 6: Integrate owner-safety checks into sensitive lifecycle and role mutations**

- [ ] **Step 7: Re-run tests and verify PASS**

- [ ] **Step 8: Commit**

```bash
git commit -am "feat(platform-iam): add executive user management api"
```

---

### Task 7: Administrative session revocation and security-state integration

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/security/service/AuthService.java`
- Modify or reuse repository methods in refresh-token persistence as required.
- Extend: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformUserService.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformSessionRevocationTest.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformSessionRevocationPostgresAcceptanceTest.java`

**Interfaces:**
- Produces a reusable administrative method such as `void revokeAllSessions(UUID tenantId, UUID userId)` that performs refresh-token revocation, `session_version` increment, and `SessionVersionCache` invalidation using existing AuthService mechanisms.

- [ ] **Step 1: Write test proving suspend/lock/disable revoke refresh tokens**

- [ ] **Step 2: Write test proving pre-existing access token/session version is invalidated**

- [ ] **Step 3: Run tests and verify RED**

- [ ] **Step 4: Refactor existing logout revocation logic into a focused reusable administrative operation**

Do not duplicate hashing/token/session cache logic.

- [ ] **Step 5: Wire membership mutations to revoke sessions in the same transaction boundary where repository semantics allow; otherwise guarantee fail-safe ordering and audit consistency**

- [ ] **Step 6: Re-run tests and verify PASS**

- [ ] **Step 7: Commit**

```bash
git commit -am "feat(platform-iam): revoke sessions on security state changes"
```

---

### Task 8: Platform roles, capabilities, temporary grants, and audit

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformRoleService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/api/PlatformAccessController.java`
- Create: role/capability/temporary-grant DTOs under `.../platformiam/dto/`
- Reuse: `apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/PlatformAuditService.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformRoleServiceTest.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/PlatformIamAuditTest.java`

**Interfaces:**
- Produces role/capability API contract from the spec and temporary grant create/revoke operations.
- Protected role mutation checks depend on Task 5.

- [ ] **Step 1: Write tests for system-role protection and custom-role management**

System protected roles cannot be deleted or stripped in a way that violates owner safety. Custom roles may be created/updated according to exact capabilities.

- [ ] **Step 2: Write tests for temporary grants**

Require capability, expiry, reason, actor; deny invalid or indefinite direct exceptions.

- [ ] **Step 3: Write audit tests**

Assert sensitive events emit the required action names and before/after/reason fields while excluding secret/token fields.

- [ ] **Step 4: Run tests and verify RED**

- [ ] **Step 5: Implement role/access controller and service**

Use existing `roles`, `role_capabilities`, `user_role_assignments`, and `access_scope_grants`; do not add parallel tables.

- [ ] **Step 6: Re-run tests and verify PASS**

- [ ] **Step 7: Commit**

```bash
git commit -am "feat(platform-iam): add role permission and temporary access management"
```

---

### Task 9: Extend SCP access-check and frontend API client

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/rbac/ControlPlaneAccessService.java`
- Modify: `apps/web/lib/api/scp-api.ts`
- Test: existing `ControlPlaneAccessServiceExecutiveCapabilityContractTest.java`
- Test: add/extend `apps/web/lib/api/scp-api.test.ts` if present; otherwise create `apps/web/lib/api/scp-platform-iam.test.ts`.

**Interfaces:**
- Produces access-check keys for Platform IAM read/navigation capabilities and typed frontend methods for Platform Users, roles, capabilities, temporary access, and session revocation.

- [ ] **Step 1: Add failing backend contract test for Platform IAM capability exposure**

At minimum pin `PLATFORM.USER.READ`, `PLATFORM.ROLE.READ`, and `PLATFORM.PERMISSION.READ` in the capability map contract.

- [ ] **Step 2: Add failing TypeScript API shape tests**

Pin response fields used by UI: membership status, roles, last login, effective capabilities, temporary grants, security/session summary, and summary metrics.

- [ ] **Step 3: Implement backend access-check extension and frontend client methods**

- [ ] **Step 4: Run backend and frontend tests**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(platform-iam): expose platform iam api to executive web"
```

---

### Task 10: Executive navigation and Platform Users directory

**Files:**
- Modify: `apps/web/app/executive/_components/ScpNav.tsx`
- Modify: `apps/web/app/executive/_components/scp-nav.test.tsx`
- Create: `apps/web/app/executive/users/page.tsx`
- Create: `apps/web/app/executive/users/platform-users.test.tsx`
- Modify: `apps/web/lib/i18n/locales/ar.ts`
- Modify: `apps/web/lib/i18n/locales/en.ts`

**Interfaces:**
- Consumes: Task 9 API client and `useScpAccess()`.
- Produces `/executive/users` directory and Users navigation link.

- [ ] **Step 1: Add failing nav tests**

Assert `/executive/users` appears only when `PLATFORM.USER.READ=true`; fail-closed states hide it after authorization resolves.

- [ ] **Step 2: Add failing directory tests**

Cover loading, error, empty, populated, search, status filter, role filter, pagination, summary metrics, and create button capability gating.

- [ ] **Step 3: Implement i18n keys and navigation link**

Place `المستخدمون` under the operations/governance section; do not duplicate a nonexistent existing route.

- [ ] **Step 4: Implement the directory page using existing `ScpPage`, `ScpSkeleton`, `ScpError`, `ScpEmpty`, `ScpStatusPill`, SDS controls, and `useScpAccess` patterns**

- [ ] **Step 5: Run frontend tests and verify PASS**

- [ ] **Step 6: Commit**

```bash
git commit -am "feat(platform-iam): add executive users directory"
```

---

### Task 11: Platform User detail, roles/effective permissions, and session controls

**Files:**
- Create: `apps/web/app/executive/users/[userId]/page.tsx`
- Create: `apps/web/app/executive/users/[userId]/platform-user-detail.test.tsx`
- Modify: `apps/web/lib/i18n/locales/ar.ts`
- Modify: `apps/web/lib/i18n/locales/en.ts`

**Interfaces:**
- Consumes: Task 9 API client.
- Produces user Overview, Roles & Permissions, Security & Sessions, Activity sections.

- [ ] **Step 1: Write failing detail tests**

Pin display of Assigned Roles, Direct Temporary Grants, Effective Capabilities, membership status, last login, session summary, and audit/activity state.

- [ ] **Step 2: Write failing sensitive-action tests**

Suspend/lock/disable/revoke-session controls require exact capabilities, confirmation, reason where policy requires, and display backend `LAST_PLATFORM_OWNER` conflict without pretending the frontend is the security boundary.

- [ ] **Step 3: Implement the detail page**

Do not display MFA/device/IP/browser fields unless returned canonically by backend.

- [ ] **Step 4: Run tests and verify PASS**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(platform-iam): add platform user detail and session controls"
```

---

### Task 12: Executive Access administration page

**Files:**
- Create: `apps/web/app/executive/access/page.tsx`
- Create: `apps/web/app/executive/access/platform-access.test.tsx`
- Modify: `apps/web/app/executive/_components/ScpNav.tsx`
- Modify: `apps/web/app/executive/_components/scp-nav.test.tsx`
- Modify: `apps/web/lib/i18n/locales/ar.ts`
- Modify: `apps/web/lib/i18n/locales/en.ts`

**Interfaces:**
- Consumes: Task 9 API client.
- Produces Roles, Permissions/Capabilities, Temporary Access surfaces under `/executive/access`.

- [ ] **Step 1: Write failing nav/access tests**

Show `/executive/access` when `PLATFORM.ROLE.READ` or `PLATFORM.PERMISSION.READ` is allowed according to the final chosen navigation rule; deny otherwise.

- [ ] **Step 2: Write failing role table and capability-matrix tests**

Pin system-protected badge, user count, capability count, and capability grouping as presentation only.

- [ ] **Step 3: Write failing temporary-access tests**

Grant form requires user, capability, valid-from, expiry, and reason. Expired/revoked grants render correct state.

- [ ] **Step 4: Implement page and i18n**

- [ ] **Step 5: Run tests and verify PASS**

- [ ] **Step 6: Commit**

```bash
git commit -am "feat(platform-iam): add executive access administration"
```

---

### Task 13: Global regression, PostgreSQL Direct certification, and exact-head evidence

**Files:**
- Modify only if required by evidence: `.github/workflows/ci.yml`
- Add targeted regression tests only where gaps remain.
- Update/add execution evidence document under `docs/superpowers/plans/` or the repository's established closure/evidence location after execution.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: exact-head certification evidence or an explicit blocker report.

- [ ] **Step 1: Run focused backend Platform IAM unit/contract suite**

Expected: zero failures/errors.

- [ ] **Step 2: Run PostgreSQL Direct Platform IAM acceptance suite**

Include schema, owner bootstrap, authorization, owner concurrency, session revocation, and tenant isolation acceptance tests.

- [ ] **Step 3: Run existing authorization/security regression suites**

At minimum cover `CapabilityEvaluationService`, `ControlPlaneAccessGuard`, project-owner permissions contract, auth/refresh/session tests, and scoped/RLS tests relevant to changed primitives.

- [ ] **Step 4: Run frontend Executive tests**

Cover `ScpAccess`, navigation, users directory/detail, access page, RTL/i18n relevant tests.

- [ ] **Step 5: Run full backend and frontend build/test commands used by CI**

Do not substitute targeted tests for the repository's final build gates.

- [ ] **Step 6: Record `FINAL_HEAD_SHA` after the last code/documentation commit**

Any subsequent commit invalidates prior exact-head CI evidence.

- [ ] **Step 7: Push branch and verify CI on exactly `FINAL_HEAD_SHA`**

Required gates from the spec:

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
```

- [ ] **Step 8: Perform authenticated visual acceptance against real APIs**

Verify `/executive/users`, `/executive/users/{id}`, and `/executive/access` with an authorized Platform account and a denied context. No mock screenshots/data.

- [ ] **Step 9: Complete post-merge production verification only after review/merge gates pass**

Validate production route availability, authenticated platform access, denied tenant-only access, and no tenant-isolation regression.

- [ ] **Step 10: Emit final certification status**

Only:

```text
FULLY_CERTIFIED
```

when every mandatory gate has evidence, otherwise:

```text
BLOCKED
BLOCKER: ...
ROOT_CAUSE: ...
EVIDENCE: ...
REQUIRED_ACTION: ...
```

- [ ] **Step 11: Commit final evidence**

```bash
git add <evidence-files>
git commit -m "docs(platform-iam): record certification evidence"
```
