# SNAD Users Module — 100% Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the SNAD Users Module as a real authenticated product across Tenant IAM and Executive Platform IAM, with PostgreSQL Direct isolation evidence, authenticated E2E, desktop/mobile visual proof, exact-head CI, merge, and post-merge certification.

**Architecture:** Keep Tenant IAM and Platform IAM as separate product surfaces over the existing canonical identity/RBAC stack. Tenant administration lives under `/management/*` and uses the authenticated tenant context; Platform administration lives under `/executive/*` and resolves the configured control-plane context internally. Reuse existing users, memberships, roles, capabilities, grants, audit, session, SDS, AuthProvider, ApiClient, ExecutiveShell, and Playwright infrastructure; add only the missing application/API/UI adapters required by the approved design.

**Tech Stack:** Java 21, Spring Boot 3, Spring Security, Spring Data/JPA/JDBC, PostgreSQL/Flyway, JUnit 5/AssertJ, Next.js/React/TypeScript, Vitest/Testing Library, Playwright, existing SDS/i18n/auth/API infrastructure, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-27-users-module-100-percent-closure-design.md`

## Global Constraints

- `PLATFORM_USER != TENANT_USER` is a hard security boundary.
- Tenant scope comes from the authenticated session/JWT context; no tenant UUID input field or arbitrary tenant selector is permitted.
- Executive Platform IAM resolves the control tenant from trusted configuration/authentication; no caller-supplied `controlTenantId` parameter is permitted.
- Backend authorization remains authoritative; frontend capability gating is UX only.
- Reuse existing `users`, memberships, roles, `access_capabilities`, role mappings, user role grants, audit, and session infrastructure; do not create duplicate IAM engines.
- No physical user deletion in this delivery.
- Protected-owner invariant: effective ACTIVE `PLATFORM_OWNER` count must never fall below 1, including concurrent mutations.
- Platform security-sensitive lifecycle transitions must invalidate refresh/access sessions using the established mechanisms.
- Temporary platform access requires expiry, reason, actor, and the canonical `access_scope_grants` mechanism.
- PostgreSQL Direct host-native execution is the certification path. Docker, Testcontainers, and H2 are not acceptance evidence.
- Arabic/English and RTL/LTR must use the existing i18n conventions; production UI copy is not hardcoded in route components.
- UI uses existing SDS tokens/components and `ExecutiveShell`; do not introduce a second design system or auth store.
- Visual evidence must never expose passwords, tokens, raw JWTs, database credentials, or recovery secrets.
- Every production change follows RED -> GREEN -> REFACTOR and receives a focused commit.
- No closure claim until every gate in Task 10 is satisfied on the final merged `main` SHA.

## Review Focus

1. **Tenant context tampering:** a caller/UI state must never make Tenant A Users/Access operate on Tenant B. Pin this in Tasks 1, 3, and 8.
2. **Cross-tenant Platform User promotion:** an email existing only outside the configured control tenant must never be promoted/reused as a Platform User. Pin this in Tasks 5 and 6.
3. **Last owner removal under concurrency:** simultaneous owner-affecting mutations must preserve at least one ACTIVE `PLATFORM_OWNER`. Pin this in Task 6.
4. **Stale access after suspension/lock/disable:** already-issued sessions must stop authorizing Platform IAM after security-sensitive lifecycle mutation. Pin this in Tasks 5 and 6.
5. **UI hidden-action bypass:** hiding a button must not be considered authorization; direct-route/direct-API attempts without capability must fail closed. Pin this in Tasks 2, 4, 7, and 8.

---

## File Structure Map

### Tenant frontend

- `apps/web/lib/api/users.ts` — existing tenant Users client; reuse.
- `apps/web/lib/api/memberships.ts` — existing organization Memberships client; reuse.
- `apps/web/lib/api/tenant-access.ts` — focused typed client for user memberships, roles, capabilities, role-capability links, and user-role links.
- `apps/web/lib/api/tenant-access.test.ts` — contract/validation tests.
- `apps/web/app/management/users/page.tsx` — tenant user directory.
- `apps/web/app/management/users/[userId]/page.tsx` — tenant user detail.
- `apps/web/app/management/users/_components/*` — focused tenant user product components.
- `apps/web/app/management/access/page.tsx` — tenant role/capability administration.
- `apps/web/app/management/access/_components/*` — focused role/capability components.
- `apps/web/app/management/users/*.test.tsx`, `apps/web/app/management/access/*.test.tsx` — route/component tests.

### Platform IAM backend

- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformUserService.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformRoleService.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/api/PlatformUserController.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/api/PlatformAccessController.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/dto/*` — focused request/response DTOs.
- Existing `PlatformAuthorizationService`, `PlatformMembershipService`, `PlatformOwnerSafetyService` — extend only through their established interfaces where needed.
- Focused unit/controller/PostgreSQL Direct tests under `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/`.

### Executive frontend

- `apps/web/lib/api/scp-api.ts` — add Platform IAM types/methods under the existing Executive API surface.
- `apps/web/app/executive/_components/ScpNav.tsx` — capability-aware Users/Access navigation.
- `apps/web/app/executive/users/page.tsx`
- `apps/web/app/executive/users/[userId]/page.tsx`
- `apps/web/app/executive/users/_components/*`
- `apps/web/app/executive/access/page.tsx`
- `apps/web/app/executive/access/_components/*`
- `apps/web/lib/i18n/locales/ar.ts`, `apps/web/lib/i18n/locales/en.ts`
- Focused route/API tests next to the affected files.

### E2E / evidence / closure

- `apps/web/e2e/users-module-authenticated.spec.ts`
- `apps/web/e2e/users-module-visual.spec.ts`
- `apps/web/e2e/users-module-visual-helpers.ts`
- `apps/web/playwright.users-module.config.ts`
- `.github/workflows/users-module-closure.yml`
- `tests/ci/test_users_module_closure_workflow.py`
- Evidence artifacts under `test-results/users-module-visual-evidence/` at runtime only; do not commit secrets or generated screenshots.

---

### Task 1: Tenant IAM typed access contracts

**Files:**
- Create: `apps/web/lib/api/tenant-access.ts`
- Create: `apps/web/lib/api/tenant-access.test.ts`
- Modify: `apps/web/lib/api/index.ts`
- Reuse unchanged unless a test proves a contract gap: `apps/web/lib/api/users.ts`, `apps/web/lib/api/memberships.ts`

**Interfaces:**
- Consumes existing backend contracts:
  - `GET /api/v1/users/{userId}/memberships`
  - `/api/v1/access/roles`
  - `/api/v1/access/roles/{roleId}/access-items`
  - `/api/v1/access/capabilities`
  - `/api/v1/access/users/{userId}/role-links`
- Produces `tenantAccessApi` methods used by Tasks 3–4:
  - `listUserMemberships(userId: string)`
  - `listRoles(tenantId: string)` / `getRole(tenantId, roleId)` / role lifecycle mutations
  - `listCapabilities()`
  - `listRoleCapabilities(tenantId, roleId)` / `attachRoleCapability(...)` / `detachRoleCapability(...)`
  - `listUserRoleLinks(tenantId, userId)` / `grantUserRole(...)` / `revokeUserRole(...)`

- [ ] **Step 1: Write failing API contract tests**

Add tests that assert exact paths/methods, UUID validation, no user-editable tenant source, and that `listUserMemberships(userId)` does not append a tenant query parameter because the backend derives it from JWT context.

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
cd apps/web && npm test -- --run lib/api/tenant-access.test.ts
```

Expected: FAIL because `tenant-access.ts` does not exist.

- [ ] **Step 3: Implement the minimal typed client**

Follow the existing `createUsersApi`/singleton pattern and existing `ApiClient`; do not add fetch wrappers or auth logic.

- [ ] **Step 4: Run focused API tests and existing users/memberships tests**

```bash
cd apps/web && npm test -- --run lib/api/tenant-access.test.ts lib/api/users.test.ts lib/api/memberships.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/api/tenant-access.ts apps/web/lib/api/tenant-access.test.ts apps/web/lib/api/index.ts
git commit -m "feat(users): add tenant IAM access client"
```

---

### Task 2: Tenant User Directory product surface

**Files:**
- Create: `apps/web/app/management/users/page.tsx`
- Create: `apps/web/app/management/users/users.module.css`
- Create: `apps/web/app/management/users/page.test.tsx`
- Create focused components under `apps/web/app/management/users/_components/`:
  - `UserDirectory.tsx`
  - `UserCreateDialog.tsx`
  - `UserLifecycleActions.tsx`
- Modify: `apps/web/app/management/page.tsx` only to add a discoverable Users route entry without restructuring the existing management page.
- Modify: `apps/web/lib/i18n/locales/ar.ts`
- Modify: `apps/web/lib/i18n/locales/en.ts`

**Interfaces:**
- Consumes `useAuth()` for authenticated user/tenant/capabilities and `usersApi` from the existing client.
- Produces `/management/users` with list/search/create/lifecycle behavior and stable ready selectors for E2E.

- [ ] **Step 1: Write failing route/component tests**

Cover authenticated loading, empty, loaded, API error, 403-style state, search by email/name/status, create action, lifecycle action visibility, and the absence of any tenant UUID field.

- [ ] **Step 2: Run and verify RED**

```bash
cd apps/web && npm test -- --run app/management/users/page.test.tsx
```

Expected: FAIL because the route/components do not exist.

- [ ] **Step 3: Implement the minimum Product UI**

Use `user.tenantId` from `useAuth()` internally when calling the existing users client. Gate controls from authenticated capabilities, but keep backend authorization authoritative.

- [ ] **Step 4: Verify GREEN + accessibility basics**

Run the focused test, lint, and TypeScript/build path relevant to the route.

```bash
cd apps/web && npm test -- --run app/management/users/page.test.tsx && npm run lint && npm run build
```

Expected: PASS / exit 0.

- [ ] **Step 5: Commit**

```bash
git add apps/web/app/management/users apps/web/app/management/page.tsx apps/web/lib/i18n/locales/ar.ts apps/web/lib/i18n/locales/en.ts
git commit -m "feat(users): add tenant user directory"
```

---

### Task 3: Tenant User Detail, memberships, and role links

**Files:**
- Create: `apps/web/app/management/users/[userId]/page.tsx`
- Create: `apps/web/app/management/users/[userId]/page.test.tsx`
- Create focused components under `apps/web/app/management/users/_components/`:
  - `UserIdentityCard.tsx`
  - `UserMembershipsCard.tsx`
  - `UserRoleLinksCard.tsx`
- Modify: `apps/web/lib/api/tenant-access.ts` only if the Task 1 contract tests intentionally left a detail method out.
- Modify i18n locale files for detail copy.

**Interfaces:**
- Consumes `usersApi.get/update/transition`, `tenantAccessApi.listUserMemberships`, `listUserRoleLinks`, `grantUserRole`, `revokeUserRole`, and `listRoles`.
- Produces `/management/users/[userId]` with identity editing, memberships, role links, lifecycle controls, and organization-scoped role grant display.

- [ ] **Step 1: Write failing tests**

Assert:
- user detail loads only for the authenticated tenant;
- memberships call uses only `userId` and trusted auth context;
- role assignment uses canonical role-link API;
- revoke uses `grantId`;
- missing capabilities hide/disable mutations but direct API behavior is not mocked as authorization;
- cross-tenant-looking route/user payloads never change the tenant used by the client.

- [ ] **Step 2: Run and verify RED**

```bash
cd apps/web && npm test -- --run app/management/users/\[userId\]/page.test.tsx
```

- [ ] **Step 3: Implement the focused detail components**

Do not expose unsupported audit/session/device data. Show only backend-provided fields.

- [ ] **Step 4: Verify GREEN**

Run focused detail tests plus Task 1 API tests and `npm run build`.

- [ ] **Step 5: Commit**

```bash
git add apps/web/app/management/users apps/web/lib/api/tenant-access.ts apps/web/lib/i18n/locales/ar.ts apps/web/lib/i18n/locales/en.ts
git commit -m "feat(users): add tenant user detail and access"
```

---

### Task 4: Tenant Roles and Capabilities product surface

**Files:**
- Create: `apps/web/app/management/access/page.tsx`
- Create: `apps/web/app/management/access/access.module.css`
- Create: `apps/web/app/management/access/page.test.tsx`
- Create focused components under `apps/web/app/management/access/_components/`:
  - `TenantRolesPanel.tsx`
  - `RoleEditor.tsx`
  - `RoleCapabilitiesPanel.tsx`
  - `CapabilityRegistryPanel.tsx`
- Modify: `apps/web/app/management/page.tsx` to add a capability-aware Access route entry.
- Modify i18n locale files.

**Interfaces:**
- Consumes Task 1 `tenantAccessApi` role/capability methods.
- Produces `/management/access` for role CRUD/lifecycle, capability attachment/detachment, and capability registry read/manage according to permissions.

- [ ] **Step 1: Write failing access-page tests**

Pin read-only vs manage states, lifecycle controls, attach/detach behavior, explicit confirmation for destructive-looking actions, stable 403/404/409 messages, and no tenant selector.

- [ ] **Step 2: Run and verify RED**

```bash
cd apps/web && npm test -- --run app/management/access/page.test.tsx
```

- [ ] **Step 3: Implement the Product UI using existing SDS conventions**

Do not create role/capability persistence in the frontend. Mutations go only through canonical APIs.

- [ ] **Step 4: Verify GREEN**

Run focused tests, lint, and build.

- [ ] **Step 5: Commit**

```bash
git add apps/web/app/management/access apps/web/app/management/page.tsx apps/web/lib/i18n/locales/ar.ts apps/web/lib/i18n/locales/en.ts
git commit -m "feat(users): add tenant access management"
```

---

### Task 5: Complete Platform IAM application/API layer

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformUserService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/service/PlatformRoleService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/api/PlatformUserController.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/api/PlatformAccessController.java`
- Create focused DTOs under `apps/sanad-platform/src/main/java/com/sanad/platform/platformiam/dto/`
- Modify existing Platform IAM/session/audit classes only when a failing test demonstrates a missing reusable operation.
- Create/extend tests:
  - `PlatformUserServiceTest.java`
  - `PlatformUserControllerAuthorizationTest.java`
  - `PlatformRoleServiceTest.java`
  - `PlatformAccessControllerAuthorizationTest.java`

**Interfaces:**
- `PlatformUserService.createPlatformUser(Authentication actor, CreatePlatformUserRequest request)` resolves the control tenant internally.
- Executive user API supports list/detail/create/update/activate/suspend/lock/disable, roles, effective permissions, and session-security actions required by the approved Platform IAM design.
- `PlatformRoleService` composes canonical roles/capabilities/grants plus `platform_role_metadata`; it does not create a second RBAC engine.
- Controllers never accept a `controlTenantId` request parameter.

- [ ] **Step 1: Write service RED tests for identity reuse and lifecycle**

Pin:
- existing control-tenant identity without membership -> reuse row + create membership;
- same email only in another tenant -> never reuse/promote that row;
- existing active membership -> 409-style domain conflict;
- suspend/lock/disable invoke established session invalidation;
- owner-affecting mutation calls `PlatformOwnerSafetyService`.

- [ ] **Step 2: Write controller RED tests**

Pin authenticated Platform membership/capability requirements, absence of control-tenant request parameters, and stable 401/403/404/409 mappings.

- [ ] **Step 3: Run focused tests and verify RED**

```bash
cd apps/sanad-platform && ./mvnw -Dtest=PlatformUserServiceTest,PlatformUserControllerAuthorizationTest,PlatformRoleServiceTest,PlatformAccessControllerAuthorizationTest test
```

- [ ] **Step 4: Implement the minimal services/controllers/DTOs**

Reuse `PlatformMembershipService`, `PlatformAuthorizationService`, `PlatformOwnerSafetyService`, canonical role/capability services, session revocation, and `PlatformAuditService`.

- [ ] **Step 5: Re-run focused tests and verify GREEN**

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/platformiam apps/sanad-platform/src/test/java/com/sanad/platform/platformiam
git commit -m "feat(platform-iam): complete user and access APIs"
```

---

### Task 6: Platform IAM PostgreSQL Direct security acceptance

**Files:**
- Create or extend focused tests under `apps/sanad-platform/src/test/java/com/sanad/platform/platformiam/`:
  - `PlatformUserPostgresAcceptanceTest.java`
  - `PlatformOwnerSafetyConcurrencyPostgresTest.java`
  - `PlatformSessionInvalidationPostgresAcceptanceTest.java`
  - extend existing temporary-grant/authorization PostgreSQL acceptance tests only where needed.

**Interfaces:**
- Consumes Task 5 APIs/services and existing migrations/RLS.
- Produces PostgreSQL Direct evidence for control-tenant identity, owner safety, grant expiry, session invalidation, and cross-tenant rejection.

- [ ] **Step 1: Add RED PostgreSQL acceptance cases**

Cases must include:
- same email in Tenant B cannot be promoted into Control Plane identity;
- platform membership cannot reference a non-control-tenant user;
- `now >= effective_to` temporary grant denies;
- suspend/lock/disable invalidates both refresh path and already-issued access session through the repository’s established session-version mechanism;
- two concurrent last-owner-removal attempts cannot leave zero ACTIVE owners.

- [ ] **Step 2: Run with the repository PostgreSQL Direct acceptance profile and verify RED only for genuinely missing Task 5 behavior**

Use the repository’s existing PostgreSQL Direct profile/credentials convention; do not introduce Docker/Testcontainers services.

- [ ] **Step 3: Apply the smallest production fix required by each failing acceptance case**

Do not change immutable historical Flyway migrations; use forward-only migrations only if a database contract is genuinely missing.

- [ ] **Step 4: Re-run until GREEN**

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main apps/sanad-platform/src/test
git commit -m "test(platform-iam): certify users security on postgres"
```

---

### Task 7: Executive Platform Users and Access Product UI

**Files:**
- Modify: `apps/web/lib/api/scp-api.ts`
- Modify: `apps/web/app/executive/_components/ScpNav.tsx`
- Create: `apps/web/app/executive/users/page.tsx`
- Create: `apps/web/app/executive/users/[userId]/page.tsx`
- Create focused components under `apps/web/app/executive/users/_components/`:
  - `PlatformUserDirectory.tsx`
  - `PlatformUserCreateDialog.tsx`
  - `PlatformMembershipCard.tsx`
  - `PlatformRoleAssignmentsCard.tsx`
  - `EffectivePermissionsCard.tsx`
  - `TemporaryAccessCard.tsx`
  - `SessionSecurityCard.tsx`
- Create: `apps/web/app/executive/access/page.tsx`
- Create components under `apps/web/app/executive/access/_components/` including `PlatformRolesPanel.tsx`.
- Modify: `apps/web/lib/i18n/locales/ar.ts`, `apps/web/lib/i18n/locales/en.ts`
- Add focused API/route tests next to modified files.

**Interfaces:**
- Consumes Task 5 Executive `/api/v1/executive/users` and access contracts through `scp-api.ts`.
- Produces `/executive/users`, `/executive/users/[userId]`, and `/executive/access` with capability-aware navigation and mutation controls.

- [ ] **Step 1: Write RED API client tests**

Pin exact Executive paths, request shapes, no control tenant parameter, temporary-grant expiry/reason, and session-security command paths.

- [ ] **Step 2: Write RED route/component tests**

Cover loading/empty/error/permission states, platform membership vs account state distinction, protected-role restrictions, owner-safety conflict display, and hidden-action direct-route protection behavior.

- [ ] **Step 3: Run and verify RED**

Run the focused Vitest files only.

- [ ] **Step 4: Implement typed client + Product UI**

Use the existing Executive/SCP shell and design language. Do not expose tenant-business rows or fabricate session metadata.

- [ ] **Step 5: Verify GREEN + full frontend quality gate**

```bash
cd apps/web && npm test -- --run && npm run lint && npm run build
```

Expected: all exit 0.

- [ ] **Step 6: Commit**

```bash
git add apps/web/lib/api/scp-api.ts apps/web/app/executive apps/web/lib/i18n/locales/ar.ts apps/web/lib/i18n/locales/en.ts
git commit -m "feat(platform-iam): add executive users and access UI"
```

---

### Task 8: Tenant/Platform authenticated E2E behavior

**Files:**
- Create: `apps/web/e2e/users-module-authenticated.spec.ts`
- Create: `apps/web/playwright.users-module.config.ts`
- Reuse existing authenticated login helpers/fixtures where available; extend them rather than copying credential logic.

**Interfaces:**
- Consumes real authenticated Tenant B and Control Plane identities supplied by the dedicated workflow environment.
- Produces behavioral E2E evidence for tenant Users/Access and Executive Platform IAM.

- [ ] **Step 1: Write failing authenticated E2E scenarios**

Tenant B journey:
- authenticate;
- open `/management/users`;
- prove real user rows render;
- open a user detail;
- prove memberships/role links render;
- open `/management/access`;
- prove roles/capabilities render;
- verify a forbidden action remains forbidden when invoked directly without capability.

Platform journey:
- authenticate as authorized Control Plane operator/owner;
- open `/executive/users` and one detail route;
- open `/executive/access`;
- prove Platform IAM data renders;
- prove non-control-tenant identity cannot appear as a Platform User.

Tenant-isolation negative path:
- attempt cross-tenant user/resource access and assert 403/404 fail-closed behavior without changing session tenant.

- [ ] **Step 2: Run against an environment with valid authenticated fixtures and verify RED for missing route behavior only**

- [ ] **Step 3: Fix only product defects exposed by E2E**

Use systematic debugging for any unexpected failure; do not weaken assertions or inject fake data.

- [ ] **Step 4: Re-run authenticated E2E until GREEN**

- [ ] **Step 5: Commit**

```bash
git add apps/web/e2e/users-module-authenticated.spec.ts apps/web/playwright.users-module.config.ts
git commit -m "test(users): add authenticated module e2e"
```

---

### Task 9: Desktop/mobile visual evidence workflow

**Files:**
- Create: `apps/web/e2e/users-module-visual.spec.ts`
- Create: `apps/web/e2e/users-module-visual-helpers.ts`
- Create: `.github/workflows/users-module-closure.yml`
- Create: `tests/ci/test_users_module_closure_workflow.py`

**Interfaces:**
- Consumes Task 8 authenticated helpers and ready selectors.
- Produces exact-SHA screenshots + `manifest.ndjson` under `test-results/users-module-visual-evidence/` as a GitHub Actions artifact.

- [ ] **Step 1: Write RED workflow contract test**

Assert the workflow:
- has no Docker/Testcontainers/Postgres service container;
- checks out the exact workflow SHA;
- runs the dedicated users-module Playwright config;
- uses environment secrets only, never literals;
- uploads evidence with `actions/upload-artifact@v4`;
- fails if required desktop/mobile evidence is missing.

- [ ] **Step 2: Write RED visual spec**

Capture authenticated desktop and mobile screenshots for at least:
- `/management/users`;
- one `/management/users/[userId]` detail;
- `/management/access`;
- `/executive/users`;
- one `/executive/users/[userId]` detail;
- `/executive/access`.

Each capture must assert the route’s ready selector and authenticated identity context before screenshotting.

- [ ] **Step 3: Run workflow contract test locally and verify RED**

```bash
python tests/ci/test_users_module_closure_workflow.py
```

- [ ] **Step 4: Implement workflow/evidence helpers and verify GREEN**

- [ ] **Step 5: Commit**

```bash
git add apps/web/e2e/users-module-visual.spec.ts apps/web/e2e/users-module-visual-helpers.ts .github/workflows/users-module-closure.yml tests/ci/test_users_module_closure_workflow.py
git commit -m "test(users): add authenticated visual closure gate"
```

---

### Task 10: Full verification, PR, exact-head closure, and post-merge proof

**Files:**
- Modify documentation only if needed to record final evidence IDs/URLs after the actual runs.
- No production-code changes are allowed in this task unless a failing gate sends execution back to the owning task.

**Interfaces:**
- Consumes all Tasks 1–9.
- Produces the only allowed final state: `USERS_MODULE = FULLY_CLOSED` on one final merged `main` SHA.

- [ ] **Step 1: Run full local/repository verification before PR**

Frontend:

```bash
cd apps/web
npm test -- --run
npm run lint
npm run build
```

Backend:

```bash
cd apps/sanad-platform
./mvnw test
```

PostgreSQL Direct acceptance: run the repository host-native acceptance suite including Task 6 tests. Expected: zero failures/errors in the governing suite.

- [ ] **Step 2: Open PR from `feat/users-module-closure` to current `main`**

Record exact PR head SHA and current base SHA. If `main` drifts later, rebase/update and invalidate old CI evidence.

- [ ] **Step 3: Require exact-head CI GREEN**

Required branch-protection contexts plus the dedicated Users Module closure workflow must be GREEN on the same final PR head. Do not treat a previous SHA as evidence.

- [ ] **Step 4: Obtain independent approval on the same final head**

Resolve all review threads. Any code change after approval invalidates the approval/CI gate until re-run/re-review as required by repository governance.

- [ ] **Step 5: Perform exact-head guarded merge**

Use the repository-supported merge method and expected-head guard. After merge, fetch the actual new `main` SHA; do not assume rebase merge preserves the PR head SHA.

- [ ] **Step 6: Verify Post-Merge on the actual new `main` SHA**

All governing Post-Merge jobs must complete SUCCESS before calling Post-Merge GREEN.

- [ ] **Step 7: Run live authenticated Users Module smoke on the same merged revision**

Required chain:

```text
Tenant B authentication
-> /management/users renders real data
-> user detail renders memberships/role links
-> /management/access renders roles/capabilities
-> tenant-isolation negative check PASS
-> Control Plane authentication
-> /executive/users renders platform users only
-> platform user detail renders membership/roles/effective permissions
-> /executive/access renders protected/custom access state
-> owner-safety/security mutation negative checks PASS
```

Do not mutate production data merely to create screenshots; use safe/read-only or explicitly reversible test fixtures already approved for smoke use.

- [ ] **Step 8: Inspect visual artifact on the final SHA**

Verify desktop/mobile screenshots and manifest entries are present for all six canonical routes and contain no secrets.

- [ ] **Step 9: Re-fetch `main` and verify no drift between final evidence and closure declaration**

If `main` moved, run the required post-merge/evidence gates on the new governing revision before closure.

- [ ] **Step 10: Declare closure only when all conditions are true**

Final declaration format:

```text
USERS_MODULE                    = FULLY_CLOSED
TENANT_USERS_UI                 = PASS
TENANT_MEMBERSHIPS_ROLES        = PASS
TENANT_ACCESS_UI                = PASS
PLATFORM_USERS_API_UI           = PASS
PLATFORM_ACCESS_API_UI          = PASS
POSTGRESQL_DIRECT_ISOLATION     = PASS
PROTECTED_OWNER_INVARIANT       = PASS
AUTHENTICATED_E2E               = PASS
DESKTOP_VISUAL_EVIDENCE         = PASS
MOBILE_VISUAL_EVIDENCE          = PASS
EXACT_HEAD_CI                   = GREEN
INDEPENDENT_APPROVAL            = APPROVED
MERGE                           = COMPLETE
POST_MERGE                      = GREEN
LIVE_AUTHENTICATED_SMOKE        = PASS
OPEN_BLOCKERS                   = 0
```

No partial-green state, queued job, stale SHA, missing screenshot, unresolved review, or unverified production route permits this declaration.
