# SNAD Platform Identity, Workforce & Access Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn SNAD's existing users, organization memberships, RBAC, workforce records, module entitlements, and audit infrastructure into one tenant-safe platform foundation with a unified `/admin` operational center consumed by CRM, ERP, Finance, HRM, POS, Workflow, Analytics, and future modules.

**Architecture:** Preserve the existing authoritative tables and services instead of creating parallel identity models. Harden tenant authority and PostgreSQL RLS first, then add missing identity/workforce invariants, expose effective-access and workforce APIs, add auditable lifecycle/offboarding orchestration, and finally build the tenant administration UI. CRM/ERP/Finance remain consumers and never become identity/workforce sources of truth.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring Security/JWT, Spring Data JPA/JDBC, PostgreSQL Direct + Flyway, existing `EntitlementResolver`, existing platform audit services, Next.js 16 / React 19 / TypeScript, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-08-27-platform-identity-workforce-foundation-design.md` at approved design commit `a84b8845c1977b9d989b0b929e3b3b762abf9ecb`.

---

## Execution invariants

These rules apply to every task below.

- Work from a fresh implementation branch created from the latest protected `main`, not from the design branch.
- Suggested branch: `feat/platform-identity-workforce-foundation`.
- At implementation start, fetch `main` and re-check the migration directory. The plan currently reserves `V20260827_1` through `V20260827_3`; if another merged track has already consumed any of those exact versions, renumber these three migrations forward as one contiguous set before writing them. Never edit or reuse an already-applied Flyway version.
- PostgreSQL Direct is authoritative. Do not introduce Docker/Testcontainers into this track.
- Do not modify historical migrations (`V1` through current production baseline) to achieve the new state. All schema changes are forward-only.
- Migration preconditions must fail with a descriptive exception on incompatible existing data; never silently delete, merge, or re-parent records to make a constraint pass.
- Runtime tenant scope comes from authenticated server-side security context. A legacy request `tenantId` may be accepted temporarily only when absent or exactly equal to the authenticated tenant; it must never choose the tenant.
- Preserve the Control Plane as a separate security boundary. `/admin` in this plan is tenant administration, not cross-tenant platform administration.
- Do not add `crm_users`, `erp_users`, `finance_users`, `pos_users`, duplicate employee tables, or an authoritative `user_effective_permissions` table.
- Do not hard-delete operational identities. Historical `created_by`, `approved_by`, `owner_user_id`, and audit references remain valid after suspension/archive/termination.
- Backend authorization is authoritative. UI hiding/disabling is never an access-control substitute.
- Every behavior change follows TDD: write a failing test, run it and observe the expected failure, implement the minimum change, rerun to green, then run the surrounding regression suite.
- Before any claim of completion, use `superpowers:verification-before-completion` and capture fresh CI/runtime evidence.

## Baseline repository map used by this plan

Existing authoritative assets that MUST be reused:

- User domain/API:
  - `apps/sanad-platform/src/main/java/com/sanad/platform/user/domain/User.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/user/service/UserService.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/user/api/UserController.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/user/repository/UserRepository.java`
- Organization membership:
  - `apps/sanad-platform/src/main/java/com/sanad/platform/organization/membership/api/OrganizationMembershipController.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/user/api/UserMembershipController.java`
- RBAC:
  - `apps/sanad-platform/src/main/java/com/sanad/platform/access/role/Role.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/access/grant/UserRoleGrant.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/access/grant/UserRoleGrantService.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/access/evaluation/CapabilityEvaluationService.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/security/authorization/CapabilityAuthorizationAspect.java`
- Entitlements:
  - `apps/sanad-platform/src/main/java/com/sanad/platform/module/entitlement/EntitlementResolver.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/module/entitlement/ModuleCapabilityContext.java`
- Workforce:
  - `apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/HrController.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/hr/domain/HrEmployee.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/hr/domain/HrEmployeeRepository.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/hr/infrastructure/JdbcHrEmployeeRepository.java`
- Audit:
  - `apps/sanad-platform/src/main/java/com/sanad/platform/audit/AuditService.java`
  - `apps/sanad-platform/src/main/java/com/sanad/platform/audit/PlatformAuditLog.java`
- Frontend:
  - `apps/web/lib/api/users.ts`
  - `apps/web/lib/api/memberships.ts`
  - `apps/web/lib/api/organizations.ts`
  - `apps/web/lib/api/hr-api.ts`
  - `apps/web/app/api/platform/[...path]/route.ts`
- Current execution-only pages under `apps/web/app/identity` and `apps/web/app/hr` are not the new tenant administration UI and must not be treated as its source of truth.

Current schema facts to preserve rather than duplicate:

- `users` is tenant-scoped and already has `(tenant_id, id)` uniqueness from `V5`.
- `organization_memberships.user_id` is nullable for invitation-first membership and already uses a tenant-safe composite FK to users.
- `roles` is tenant-scoped; later migrations already added `is_system_managed`, `role_origin`, `template_key`, and `template_version`. These fields are the approved SYSTEM/CUSTOM equivalent. Do **not** add a redundant `role_type` column unless PF-01 proves those fields are absent in the runtime schema.
- `access_capabilities` is a global catalog.
- `role_capabilities` maps tenant roles to global capabilities.
- `user_role_assignments` already models tenant-wide (`organization_id IS NULL`) and organization-scoped grants, but needs null-scope uniqueness and temporal lifecycle metadata.
- `hr_departments`, `hr_positions`, and `hr_employees` already exist; their current single-column workforce FKs and HR RLS need hardening.

---

## Task 1 — PF-01: Establish the executable baseline and reconciliation evidence

**Purpose:** Prove the exact repository/database contract before changing it, and block implementation if another track moved the foundation underneath this plan.

**Files:**
- Create: `docs/execution/platform-foundation/PF-01-RECONCILIATION.md`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/foundation/PlatformFoundationBaselineContractTest.java`
- Reference only: migrations `V3`, `V4`, `V5`, `V6`, `V7`, `V8`, `V9`, `V20260819_1`, `V20260820_3`, `V20260820_5`.

- [ ] **Step 1: Create the implementation branch from fresh `main`.**

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feat/platform-identity-workforce-foundation
```

Expected: branch starts from the latest protected `main`. Record the exact SHA in `PF-01-RECONCILIATION.md`.

- [ ] **Step 2: Verify Flyway namespace before reserving versions.**

```bash
find apps/sanad-platform/src/main/resources/db/migration -maxdepth 1 -type f -printf '%f\n' | sort -V | tail -30
```

Expected on the design baseline: latest dated family is `V20260823_*`; no `V20260827_1`, `_2`, or `_3`. If any planned version already exists after synchronization, renumber all planned foundation migrations forward before proceeding and update this plan file in the implementation branch in the same commit that records the new versions.

- [ ] **Step 3: Add a baseline contract test that pins the existing authoritative building blocks.**

Create `PlatformFoundationBaselineContractTest.java` to assert, by reflection/source-level contract where practical, that the application still contains:

```java
assertThat(User.class).isNotNull();
assertThat(Role.class).isNotNull();
assertThat(UserRoleGrant.class).isNotNull();
assertThat(CapabilityEvaluationService.class).isNotNull();
assertThat(EntitlementResolver.class).isNotNull();
assertThat(HrEmployee.class).isNotNull();
```

Also pin that the existing controller roots remain `/api/v1/users`, `/api/v1/access/roles`, `/api/v1/access/users`, `/api/v1/organizations/{organizationId}/memberships`, and `/api/v1/hr`. Do not assert implementation details that this plan intentionally changes.

- [ ] **Step 4: Run the baseline contract test.**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=PlatformFoundationBaselineContractTest test
```

Expected: PASS before production code changes. A failure means the fresh `main` differs materially from the approved design; stop and reconcile the plan rather than coding against stale assumptions.

- [ ] **Step 5: Run focused existing regression tests.**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=UserControllerTest,UserMembershipControllerTest,OrganizationMembershipControllerTest,AccessCatalogControllerTest,AccessLinkControllerTest,CapabilityEvaluationServiceTest,UserRoleGrantServiceTest,HrTenantContextRegressionTest test
```

Expected: PASS. Record counts/results in `PF-01-RECONCILIATION.md`.

- [ ] **Step 6: Record the schema/security observations, not assumptions.**

The reconciliation document must explicitly record:

```text
USERS_TENANT_COMPOSITE_KEY       = PRESENT/ABSENT
MEMBERSHIP_TENANT_SAFE_USER_FK   = PRESENT/ABSENT
ROLE_PROVENANCE_COLUMNS          = PRESENT/ABSENT
TENANT_WIDE_GRANT_PARTIAL_UNIQUE = PRESENT/ABSENT
GRANT_TEMPORAL_COLUMNS           = PRESENT/ABSENT
EMPLOYEE_USER_COMPOSITE_FK       = PRESENT/ABSENT
EMPLOYEE_USER_PARTIAL_UNIQUE     = PRESENT/ABSENT
WORKFORCE_COMPOSITE_FKS          = PRESENT/ABSENT
HR_FORCE_RLS                     = PRESENT/ABSENT
HR_FAIL_CLOSED_POLICY            = PRESENT/ABSENT
```

Use `information_schema`, `pg_constraint`, `pg_indexes`, `pg_class`, and `pg_policies` against the PostgreSQL Direct acceptance database. Never infer production state solely from migration text.

- [ ] **Step 7: Commit PF-01 evidence.**

```bash
git add docs/execution/platform-foundation/PF-01-RECONCILIATION.md \
  apps/sanad-platform/src/test/java/com/sanad/platform/foundation/PlatformFoundationBaselineContractTest.java
git commit -m "test(platform): pin identity workforce foundation baseline"
```

---

## Task 2 — PF-02: Make authenticated tenant context authoritative across platform administration APIs

**Purpose:** Remove request-controlled tenant authority without an abrupt client break.

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/security/TenantScopeResolver.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/security/TenantScopeResolverTest.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/user/api/UserController.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/organization/membership/api/OrganizationMembershipController.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/access/api/RoleController.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/access/api/UserAccessController.java`
- Modify tests: `UserControllerTest.java`, `OrganizationMembershipControllerTest.java`, `AccessCatalogControllerTest.java`, `AccessLinkControllerTest.java`.

- [ ] **Step 1: Write the RED unit test for tenant resolution.**

Test these three cases:

```java
resolve(authFor(TENANT_A), null)      -> TENANT_A
resolve(authFor(TENANT_A), TENANT_A)  -> TENANT_A
resolve(authFor(TENANT_A), TENANT_B)  -> AccessDeniedException
```

Run:

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=TenantScopeResolverTest test
```

Expected: FAIL because `TenantScopeResolver` does not exist.

- [ ] **Step 2: Implement the minimum resolver.**

Core contract:

```java
@Component
public final class TenantScopeResolver {
    public UUID resolve(Authentication authentication, UUID legacyTenantId) {
        UUID authenticatedTenantId = SecurityContextUtils.tenantId(authentication);
        if (legacyTenantId != null && !authenticatedTenantId.equals(legacyTenantId)) {
            throw new AccessDeniedException("tenantId does not match authenticated tenant");
        }
        return authenticatedTenantId;
    }
}
```

This component is a transition adapter only. The security context is always authoritative.

- [ ] **Step 3: Convert controller tenant resolution one controller at a time.**

For each tenant-plane controller, change the request-supplied parameter to optional/deprecated and derive the effective tenant with `TenantScopeResolver`:

```java
@GetMapping
public ResponseEntity<List<UserResponse>> listUsers(
        Authentication authentication,
        @RequestParam(required = false) UUID tenantId) {
    UUID effectiveTenantId = tenantScopeResolver.resolve(authentication, tenantId);
    return ResponseEntity.ok(userService.listUsers(effectiveTenantId));
}
```

Apply the same pattern to create/get/update/state transitions, role CRUD, role grants, and organization membership operations.

For `InviteOrganizationMemberRequest`, do not trust body `tenantId`/`organizationId`. During compatibility transition, overwrite the DTO's values inside the controller with the authenticated tenant and path organization before calling the service. A mismatching legacy query/body value must not select another tenant.

- [ ] **Step 4: Add controller RED tests for mismatched legacy tenant.**

Each changed controller must prove:

```text
JWT tenant A + no legacy tenant       -> normal behavior
JWT tenant A + legacy tenant A        -> normal behavior
JWT tenant A + legacy tenant B        -> 403
```

Run focused tests after each controller edit.

- [ ] **Step 5: Run the tenant authority regression suite.**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=TenantScopeResolverTest,UserControllerTest,OrganizationMembershipControllerTest,AccessCatalogControllerTest,AccessLinkControllerTest,UserMembershipControllerTest,HrTenantContextRegressionTest test
```

Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/security/TenantScopeResolver.java \
  apps/sanad-platform/src/main/java/com/sanad/platform/user/api/UserController.java \
  apps/sanad-platform/src/main/java/com/sanad/platform/organization/membership/api/OrganizationMembershipController.java \
  apps/sanad-platform/src/main/java/com/sanad/platform/access/api/RoleController.java \
  apps/sanad-platform/src/main/java/com/sanad/platform/access/api/UserAccessController.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/security/TenantScopeResolverTest.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/user/api/UserControllerTest.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/organization/membership/api/OrganizationMembershipControllerTest.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/access/AccessCatalogControllerTest.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/access/AccessLinkControllerTest.java
git commit -m "fix(security): make authenticated tenant authoritative"
```

---

## Task 3 — PF-03: Harden PostgreSQL RLS for tenant-owned platform foundation tables

**Purpose:** Missing tenant context must fail closed at the database layer, including HR tables whose current migration policy allows rows when `app.tenant_id` is absent.

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260827_1__platform_foundation_rls_hardening.sql`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/foundation/PlatformFoundationRlsPostgresTest.java`

**Tenant-owned RLS set for this task:**

```text
organizations
organization_memberships
users
roles
role_capabilities
user_role_assignments
hr_departments
hr_positions
hr_employees
```

`tenants` and global `access_capabilities` are intentionally excluded from this tenant-plane RLS migration because they have different Control Plane/catalog semantics.

- [ ] **Step 1: Write the RED PostgreSQL Direct acceptance test.**

The test must use the same non-superuser application DB role semantics as runtime and prove for every table above:

1. tenant A sees/inserts tenant A rows;
2. tenant A cannot see/update tenant B rows;
3. missing `app.tenant_id` yields zero tenant rows and cannot insert tenant-owned rows;
4. RLS is enabled and forced in `pg_class`;
5. an applicable policy has both `USING` and `WITH CHECK` fail-closed semantics.

Run:

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=PlatformFoundationRlsPostgresTest test
```

Expected: FAIL on the current baseline, especially HR missing-context behavior and/or tables without FORCE RLS.

- [ ] **Step 2: Implement the forward-only RLS migration.**

For each target table, the final predicate must be equivalent to:

```sql
tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
```

No `... IS NULL OR ...` escape is allowed.

For HR tables, replace the old `tenant_isolation` policy instead of layering an OR-permissive policy beside a restrictive-looking one:

```sql
DROP POLICY IF EXISTS tenant_isolation ON hr_employees;
ALTER TABLE hr_employees ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_employees FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_employees
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

Apply the same fail-closed form to the target set, using table-specific policy names only where an existing policy name would collide.

- [ ] **Step 3: Prove the migration does not rely on owner/superuser bypass.**

The test setup must connect through the runtime/acceptance application role for row assertions. A migration-owner-only test is insufficient.

- [ ] **Step 4: Run RED → GREEN and related security tests.**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=PlatformFoundationRlsPostgresTest,HrTenantContextRegressionTest test
```

Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_1__platform_foundation_rls_hardening.sql \
  apps/sanad-platform/src/test/java/com/sanad/platform/foundation/PlatformFoundationRlsPostgresTest.java
git commit -m "fix(security): make platform foundation RLS fail closed"
```

---

## Task 4 — PF-04: Reconcile SYSTEM/CUSTOM role semantics without creating a second role-type source

**Purpose:** Expose and enforce the role provenance already present in the database (`is_system_managed`, `role_origin`, `template_key`, `template_version`) rather than adding redundant schema.

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/access/role/Role.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/access/role/RoleResponse.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/access/role/RoleService.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/access/RoleCatalogServiceTest.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/access/AccessCatalogControllerTest.java`

- [ ] **Step 1: Add RED tests for system-managed role protection.**

Required cases:

```text
customer/custom role -> rename/update/archive allowed with ROLE.WRITE
SNAD_TEMPLATE/system-managed role -> descriptive/name update policy only as approved
system-managed role code/provenance mutation -> denied
system-managed role archive/delete semantics -> denied
RoleResponse exposes systemManaged + origin/template metadata
```

Run:

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=RoleCatalogServiceTest,AccessCatalogControllerTest test
```

Expected: FAIL because `Role` currently does not map the provenance columns.

- [ ] **Step 2: Map existing provenance columns in `Role`.**

Add read-only/controlled fields, not a new `role_type` database column:

```java
@Column(name = "is_system_managed", nullable = false)
private boolean systemManaged;

@Column(name = "role_origin")
private String roleOrigin;

@Column(name = "template_key")
private String templateKey;

@Column(name = "template_version")
private String templateVersion;

public boolean isSystemManaged() { return systemManaged; }
```

Treat `systemManaged=true` / `role_origin='SNAD_TEMPLATE'` as SYSTEM. Everything else is tenant CUSTOM.

- [ ] **Step 3: Protect system role semantics inside `RoleService`, not only UI.**

Before code/status changes that would invalidate a canonical role:

```java
if (role.isSystemManaged()) {
    throw new IllegalStateException("System-managed role semantics cannot be changed by tenant administration");
}
```

Allow reading system roles and assigning them; protect their canonical code/provenance/matrix.

- [ ] **Step 4: Extend `RoleResponse` with provenance needed by `/admin/roles`.**

Response must include at least:

```text
systemManaged
roleOrigin
templateKey
templateVersion
```

- [ ] **Step 5: Run tests and commit.**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=RoleCatalogServiceTest,AccessCatalogControllerTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/access/role \
  apps/sanad-platform/src/test/java/com/sanad/platform/access/RoleCatalogServiceTest.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/access/AccessCatalogControllerTest.java
git commit -m "feat(access): expose and protect role provenance"
```

---

## Task 5 — PF-05: Add temporal role-grant lifecycle and close tenant-wide duplicate races

**Purpose:** Make temporary access first-class and enforce one active logical tenant-wide grant at the DB level.

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260827_2__user_role_assignment_lifecycle_and_scope_integrity.sql`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/access/grant/UserRoleGrant.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/access/grant/UserRoleGrantRepository.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/access/grant/UserRoleGrantService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/access/UserAccessResponse.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/access/UserRoleGrantServiceTest.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/access/AccessPersistenceIntegrationTest.java`

- [ ] **Step 1: Write RED tests for time-effective grants.**

Cases:

```text
effective_from <= now and no effective_until -> effective
effective_from in future                    -> not effective
effective_until in past                     -> derived EXPIRED
persisted REVOKED                            -> not effective
organization scoped grant                    -> only matching org
tenant-wide duplicate under concurrent insert -> DB unique violation
```

- [ ] **Step 2: Add migration preconditions before any unique index.**

The migration must abort if historical duplicates already exist:

```sql
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM user_role_assignments
        WHERE organization_id IS NULL
        GROUP BY tenant_id, user_id, role_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'PF-05 reconciliation required: duplicate tenant-wide role grants exist';
    END IF;
END $$;
```

No automatic deletion/deduplication is permitted.

- [ ] **Step 3: Add lifecycle columns and partial uniqueness.**

```sql
ALTER TABLE user_role_assignments
    ADD COLUMN effective_from TIMESTAMPTZ,
    ADD COLUMN effective_until TIMESTAMPTZ,
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN revoked_by UUID;

CREATE UNIQUE INDEX uq_user_role_tenant_wide_scope
    ON user_role_assignments (tenant_id, user_id, role_id)
    WHERE organization_id IS NULL;
```

Add a check that `effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from`.

- [ ] **Step 4: Make expiration derived, not cron-mutated.**

`UserRoleGrant` should expose:

```java
public boolean isEffectiveAt(Instant now) {
    return status == UserGrantStatus.ACTIVE
        && (effectiveFrom == null || !effectiveFrom.isAfter(now))
        && (effectiveUntil == null || effectiveUntil.isAfter(now));
}
```

Persisted states remain backward-compatible (`ACTIVE`, `REVOKED`); API response may expose a derived effective state `EXPIRED` when the time window ended.

- [ ] **Step 5: Revoke with audit fields.**

`UserRoleGrantService.revoke(...)` must set `status=REVOKED`, `revokedAt=now`, and authenticated actor id when available through the application service boundary.

- [ ] **Step 6: Run tests.**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=UserRoleGrantServiceTest,AccessPersistenceIntegrationTest,CapabilityEvaluationServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_2__user_role_assignment_lifecycle_and_scope_integrity.sql \
  apps/sanad-platform/src/main/java/com/sanad/platform/access \
  apps/sanad-platform/src/test/java/com/sanad/platform/access
git commit -m "feat(access): add temporal role grant lifecycle"
```

---

## Task 6 — PF-06: Introduce explicit user identity type and preserve HUMAN/SERVICE/INTEGRATION boundaries

**Purpose:** Distinguish workforce-linked humans from service/integration identities without splitting the `users` source of truth.

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/user/domain/UserType.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/user/domain/User.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/user/dto/CreateUserRequest.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/user/dto/UserResponse.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/user/service/UserService.java`
- Add the `users.user_type` change to migration `V20260827_3__workforce_identity_tenant_integrity.sql` created in Task 7 (do not create a fourth migration only for one column).
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/user/api/UserControllerTest.java`
- Modify/create user service tests as matching existing repository pattern.

- [ ] **Step 1: Write RED API/domain tests.**

Required behavior:

```text
create user without userType -> HUMAN
create HUMAN                 -> accepted
create SERVICE               -> accepted by authorized admin path
create INTEGRATION           -> accepted by authorized admin path
unknown type                 -> 400
UserResponse                 -> includes userType
```

- [ ] **Step 2: Add enum and domain field.**

```java
public enum UserType {
    HUMAN,
    SERVICE,
    INTEGRATION
}
```

Map `user_type` with `@Enumerated(EnumType.STRING)` and default new users to `HUMAN` when request omits it.

- [ ] **Step 3: Keep security lifecycle separate from type.**

Do not derive `status`, roles, employee linkage, or module entitlements from user type. Type only classifies identity and enables policies such as “non-human users cannot link to employees.”

- [ ] **Step 4: Run focused tests.**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=UserControllerTest test
```

Expected: PASS after implementation.

- [ ] **Step 5: Commit Java/API changes only after Task 7 migration is on the same branch and tests can start against the migrated schema.**

The Task 6 and Task 7 schema/domain commits may be combined if necessary to avoid a transient JPA schema mismatch, but keep their test groups logically distinct.

---

## Task 7 — PF-07: Enforce tenant-safe User ↔ Employee 1:1 and workforce foreign keys

**Purpose:** Make cross-tenant workforce links and duplicate employee/user links impossible in PostgreSQL.

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260827_3__workforce_identity_tenant_integrity.sql`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/HrWorkforceIntegrityPostgresTest.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/domain/HrEmployee.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/domain/HrEmployeeRepository.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/infrastructure/JdbcHrEmployeeRepository.java`

- [ ] **Step 1: Write RED database tests.**

Prove current schema permits at least one target violation or lacks the target constraints, then assert desired behavior:

```text
same user linked to two employees in same tenant -> rejected
employee tenant A -> user tenant B              -> rejected
employee tenant A -> department tenant B        -> rejected
employee tenant A -> position tenant B          -> rejected
employee tenant A -> manager tenant B           -> rejected
position tenant A -> department tenant B        -> rejected
department tenant A -> parent tenant B           -> rejected
```

Run:

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrWorkforceIntegrityPostgresTest test
```

Expected: FAIL before migration.

- [ ] **Step 2: Add migration preconditions for existing data drift.**

Abort on:

- duplicate non-null `(tenant_id, user_id)` in `hr_employees`;
- employee/user tenant mismatch;
- department/position/manager tenant mismatch;
- position/department tenant mismatch;
- child/parent department tenant mismatch.

Each failure message must name the violated relation and instruct operators to reconcile data before retrying Flyway. Do not auto-fix records.

- [ ] **Step 3: Add `users.user_type` and supporting check.**

```sql
ALTER TABLE users ADD COLUMN user_type VARCHAR(20) NOT NULL DEFAULT 'HUMAN';
ALTER TABLE users ADD CONSTRAINT ck_users_user_type
    CHECK (user_type IN ('HUMAN','SERVICE','INTEGRATION'));
```

- [ ] **Step 4: Add composite keys needed by tenant-safe FKs.**

Ensure `(tenant_id,id)` unique constraints exist on `hr_departments`, `hr_positions`, and `hr_employees`.

- [ ] **Step 5: Replace/supplement workforce FKs with composite tenant-safe FKs.**

Target relationships:

```text
(hr_employees.tenant_id, user_id)       -> users(tenant_id, id)
(hr_employees.tenant_id, department_id) -> hr_departments(tenant_id, id)
(hr_employees.tenant_id, position_id)   -> hr_positions(tenant_id, id)
(hr_employees.tenant_id, manager_id)    -> hr_employees(tenant_id, id)
(hr_positions.tenant_id, department_id) -> hr_departments(tenant_id, id)
(hr_departments.tenant_id, parent_department_id) -> hr_departments(tenant_id, id)
```

Then add:

```sql
CREATE UNIQUE INDEX uq_hr_employee_tenant_user
    ON hr_employees (tenant_id, user_id)
    WHERE user_id IS NOT NULL;
```

- [ ] **Step 6: Run integrity + user tests.**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrWorkforceIntegrityPostgresTest,UserControllerTest,HrTenantContextRegressionTest test
```

Expected: PASS.

- [ ] **Step 7: Commit Task 6 + Task 7 coherent schema/domain state.**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_3__workforce_identity_tenant_integrity.sql \
  apps/sanad-platform/src/main/java/com/sanad/platform/user \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr \
  apps/sanad-platform/src/test/java/com/sanad/platform/user \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr
git commit -m "feat(platform): harden workforce identity relationships"
```

---

## Task 8 — PF-08: Complete Workforce Directory APIs for employees, departments, and positions

**Purpose:** Provide operational APIs behind `/admin/employees`, `/admin/departments`, and `/admin/positions` without making the UI query tables directly.

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/domain/HrDepartment.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/domain/HrPosition.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/domain/HrDepartmentRepository.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/domain/HrPositionRepository.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/infrastructure/JdbcHrDepartmentRepository.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/infrastructure/JdbcHrPositionRepository.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/application/HrWorkforceService.java`
- Create typed request/response records under `apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/dto/`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/HrController.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/HrWorkforceControllerTest.java`

- [ ] **Step 1: Write RED controller tests for the full read/write surface.**

Required routes:

```text
GET    /api/v1/hr/employees
GET    /api/v1/hr/employees/{id}
POST   /api/v1/hr/employees
PATCH  /api/v1/hr/employees/{id}
PATCH  /api/v1/hr/employees/{id}/terminate

GET    /api/v1/hr/departments
GET    /api/v1/hr/departments/{id}
POST   /api/v1/hr/departments
PATCH  /api/v1/hr/departments/{id}
PATCH  /api/v1/hr/departments/{id}/archive

GET    /api/v1/hr/positions
GET    /api/v1/hr/positions/{id}
POST   /api/v1/hr/positions
PATCH  /api/v1/hr/positions/{id}
PATCH  /api/v1/hr/positions/{id}/archive
```

Capabilities:

```text
HR.EMPLOYEE.READ / WRITE / ARCHIVE
HR.DEPARTMENT.READ / WRITE
HR.POSITION.READ / WRITE
```

- [ ] **Step 2: Replace raw `Map<String,Object>` employee mutation at the controller boundary with typed DTOs.**

Do not change the database source of truth. Move validation/orchestration to `HrWorkforceService` and keep repository classes persistence-focused.

- [ ] **Step 3: Implement tenant-scoped department and position repositories.**

Every method signature must carry authoritative `tenantId`, for example:

```java
List<HrDepartment> findAll(UUID tenantId, int limit, String search);
Optional<HrDepartment> findById(UUID tenantId, UUID id);
HrDepartment save(HrDepartment department);
```

No unscoped `findById(UUID id)` method is permitted on tenant-owned workforce repositories.

- [ ] **Step 4: Enforce archive instead of destructive delete for in-use departments/positions.**

Attempting to archive a department/position referenced by active employees must return a domain conflict unless those employees are reassigned first. Do not cascade-delete workforce records.

- [ ] **Step 5: Run tests.**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrWorkforceControllerTest,HrTenantContextRegressionTest,HrWorkforceIntegrityPostgresTest test
```

Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr
git commit -m "feat(hr): complete tenant workforce directory APIs"
```

---

## Task 9 — PF-09: Add User ↔ Employee linking and manager hierarchy validation

**Purpose:** Operationalize the approved optional 1:1 relationship and prevent workforce hierarchy cycles.

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/application/HrWorkforceService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/HrController.java`
- Modify: employee DTOs under `hr/api/dto/`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/HrEmployeeUserLinkTest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/HrManagerHierarchyTest.java`

- [ ] **Step 1: Write RED employee-user link tests.**

Routes:

```text
PUT    /api/v1/hr/employees/{employeeId}/user-link
DELETE /api/v1/hr/employees/{employeeId}/user-link
```

Request:

```json
{"userId":"<uuid>"}
```

Required behavior:

```text
HUMAN user same tenant, not linked elsewhere -> link succeeds
SERVICE user                             -> 409/422 deny
INTEGRATION user                         -> 409/422 deny
user in another tenant                   -> 404/deny without information leak
user already linked to another employee  -> 409
unlink                                   -> employee remains; user remains
```

- [ ] **Step 2: Implement link/unlink in the application service.**

Validate user through `UserRepository.findByTenantIdAndId`, require `UserType.HUMAN`, then persist the employee link. Treat the database unique/FK constraints as the final race-safe guard; translate constraint violation to a domain conflict rather than returning 500.

- [ ] **Step 3: Write RED manager hierarchy tests.**

Reject:

```text
employee manages self
A -> B and B -> A
A -> B -> C and C -> A
manager from another tenant
```

- [ ] **Step 4: Implement cycle detection before save.**

Traverse existing manager links within the same tenant until null; maintain a visited set. If the candidate employee id is encountered, reject the mutation. Database tenant-safe FK remains the lower-layer guard.

- [ ] **Step 5: Include `userId` and `managerId` in typed employee responses and in `apps/web/lib/api/hr-api.ts` later.**

- [ ] **Step 6: Run tests and commit.**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrEmployeeUserLinkTest,HrManagerHierarchyTest,HrWorkforceControllerTest,HrWorkforceIntegrityPostgresTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr
git commit -m "feat(hr): link employees to users safely"
```

---

## Task 10 — PF-10: Build the authoritative Effective Access service

**Purpose:** Explain and enforce access through user status, memberships, time-effective grants, role/capability status, module entitlement, organization scope, and runtime policy.

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/access/evaluation/EffectiveAccessService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/access/evaluation/EffectiveAccessResponse.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/access/evaluation/EffectiveCapabilityDecision.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/access/evaluation/CapabilityScopeClassifier.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/access/api/EffectiveAccessController.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/access/evaluation/CapabilityEvaluationService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/security/authorization/CapabilityAuthorizationAspect.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/access/EffectiveAccessServiceTest.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/access/CapabilityEvaluationServiceTest.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/access/CapabilityEvaluationControllerTest.java`

- [ ] **Step 1: Write RED decision-matrix tests.**

At minimum:

```text
unauthenticated                                  -> DENY
user SUSPENDED/ARCHIVED                          -> DENY
required membership inactive                     -> DENY
future/expired/revoked grant                      -> DENY
role inactive                                     -> DENY
capability inactive                               -> DENY
org-scoped grant + wrong organization             -> DENY
module capability + module disabled               -> DENY
module capability + module enabled + RBAC allow   -> ALLOW
platform-core USER/ROLE/MEMBERSHIP/CAPABILITY cap -> does NOT require commercial module entitlement
```

- [ ] **Step 2: Implement explicit capability scope classification.**

Start with a small deterministic classifier, not a duplicated entitlement table:

```java
PLATFORM_CORE prefixes = USER, ROLE, MEMBERSHIP, CAPABILITY, ORGANIZATION
MODULE prefixes        = CRM, ERP, FINANCE, HR, ECOMMERCE, POS, WORKFLOW, ANALYTICS, AI, ...
```

For module-scoped capabilities, resolve module entitlement through the existing `EntitlementResolver`. Do not query subscription tables directly.

- [ ] **Step 3: Preserve `CapabilityEvaluationService` as the low-level RBAC primitive but route authorization through the effective policy.**

Refactor so the aspect's final answer no longer means “RBAC capability exists only.” It must call the effective decision service for module-scoped business operations while preserving explicit platform-admin/control-plane bypass semantics already governed elsewhere.

- [ ] **Step 4: Add authenticated self-observability endpoint.**

```text
GET /api/v1/access/me/effective-permissions
```

Response must include:

```text
userId
tenantId
organizations[]
roles[] with scope/effective window
effectiveCapabilities[]
moduleDecisions{}
```

Do not expose secrets, raw JWTs, or credentials.

- [ ] **Step 5: Add tenant-admin explanation endpoint for another user.**

```text
GET /api/v1/access/users/{userId}/effective-permissions
```

Require `USER.READ` + `ROLE.READ` semantics and tenant scoping. Include a decision trace/first failing gate for each requested capability without leaking another tenant's identity.

- [ ] **Step 6: Run the access suite.**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=EffectiveAccessServiceTest,CapabilityEvaluationServiceTest,CapabilityEvaluationControllerTest,AccessLinkControllerTest,UserRoleGrantServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/access \
  apps/sanad-platform/src/main/java/com/sanad/platform/security/authorization/CapabilityAuthorizationAspect.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/access
git commit -m "feat(access): compute effective platform permissions"
```

---

## Task 11 — PF-11: Add audited access governance and offboarding orchestration

**Purpose:** Treat employee termination/security suspension as an auditable workflow without deleting historical identity or silently leaving privileges active.

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/governance/offboarding/OffboardingService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/governance/offboarding/OffboardingRequest.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/governance/offboarding/OffboardingResult.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/governance/offboarding/OffboardingResponsibilityPort.java`
- Create adapters only for repository-supported responsibility queries that exist on the implementation baseline; do not invent duplicate ownership ledgers.
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/application/HrWorkforceService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/user/service/UserService.java` only as needed for suspension/session-version invalidation.
- Use existing `AuditService` rather than a new audit database.
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/governance/offboarding/OffboardingServiceTest.java`

- [ ] **Step 1: Write RED security-offboarding tests.**

For a linked HUMAN user:

```text
terminate employee
-> employee becomes TERMINATED
-> linked user becomes SUSPENDED before business handover completion
-> sessionVersion/authoritative session revocation mechanism invalidates old sessions
-> active privileged role grants are revoked or flagged according to explicit service policy
-> historical user/employee rows remain
-> audit events are written for each security mutation
```

For an employee with no user link, termination must still succeed and audit HR termination without attempting IAM changes.

- [ ] **Step 2: Separate PREPARE from COMPLETE.**

Implement two concepts:

```text
prepareOffboarding(employeeId)
  -> security freeze + responsibility inventory

completeOffboarding(employeeId, reassignmentTarget)
  -> only allowed when required business responsibilities are transferred/cleared
  -> archive user only after security/business closure policy allows it
```

Do not automatically reassign CRM/ERP/Finance records by guessing a successor.

- [ ] **Step 3: Define the responsibility port as a consumer boundary.**

```java
public interface OffboardingResponsibilityPort {
    List<Responsibility> findOpenResponsibilities(UUID tenantId, UUID userId);
    void reassign(UUID tenantId, UUID fromUserId, UUID toUserId, String correlationId);
}
```

Module adapters must call existing CRM/ERP/Workflow ownership/application services or repositories. They must not create a parallel platform ownership table.

- [ ] **Step 4: Audit with correlation.**

All mutations in one offboarding flow must share a correlation id and capture actor, target, before/after state, result, tenant, and organization where available.

- [ ] **Step 5: Write RED access-change audit tests for role grant/revoke and employee-user link/unlink.**

Wire existing `AuditService` at application service boundaries. Do not rely on controller-only logging because non-HTTP callers must be audited too.

- [ ] **Step 6: Run tests.**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=OffboardingServiceTest,UserRoleGrantServiceTest,HrEmployeeUserLinkTest test
```

Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/governance \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr \
  apps/sanad-platform/src/main/java/com/sanad/platform/user \
  apps/sanad-platform/src/main/java/com/sanad/platform/access \
  apps/sanad-platform/src/test/java/com/sanad/platform/governance \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr \
  apps/sanad-platform/src/test/java/com/sanad/platform/access
git commit -m "feat(governance): add audited workforce offboarding"
```

---

## Task 12 — PF-12: Build the unified tenant `/admin` operational center

**Purpose:** Give tenant administrators one place to operate users, employees, organizations, memberships, departments, positions, roles, capabilities, access, and audit without duplicating module identities.

**Files:**
- Create: `apps/web/lib/api/access-api.ts`
- Modify: `apps/web/lib/api/users.ts`
- Modify: `apps/web/lib/api/memberships.ts`
- Modify: `apps/web/lib/api/organizations.ts`
- Modify: `apps/web/lib/api/hr-api.ts`
- Create: `apps/web/app/admin/admin.module.css`
- Create: `apps/web/app/admin/components/admin-shell.tsx`
- Create: `apps/web/app/admin/components/admin-feedback.tsx`
- Create pages:
  - `apps/web/app/admin/page.tsx`
  - `apps/web/app/admin/users/page.tsx`
  - `apps/web/app/admin/users/[userId]/page.tsx`
  - `apps/web/app/admin/employees/page.tsx`
  - `apps/web/app/admin/employees/[employeeId]/page.tsx`
  - `apps/web/app/admin/organizations/page.tsx`
  - `apps/web/app/admin/memberships/page.tsx`
  - `apps/web/app/admin/departments/page.tsx`
  - `apps/web/app/admin/positions/page.tsx`
  - `apps/web/app/admin/roles/page.tsx`
  - `apps/web/app/admin/capabilities/page.tsx`
  - `apps/web/app/admin/access/page.tsx`
  - `apps/web/app/admin/audit/page.tsx`
- Create tests:
  - `apps/web/app/admin/admin-routes.test.ts`
  - `apps/web/lib/api/access-api.test.ts`
  - extend/add tests for users/hr/memberships clients.

- [ ] **Step 1: Write RED route-surface test.**

Pin every route above and assert no `/crm/users`, `/erp/users`, or equivalent module-specific identity route is introduced by this work.

Run:

```bash
cd apps/web
npm test -- app/admin/admin-routes.test.ts
```

Expected: FAIL because `/admin` does not exist.

- [ ] **Step 2: Write RED typed API tests.**

`users.ts` and membership/role grant clients must stop requiring browser-selected tenant ids for normal tenant-plane calls. During backend compatibility period, omit `tenantId` from new frontend requests entirely.

`hr-api.ts` must include at least:

```ts
userId: string | null;
managerId: string | null;
```

and typed department/position/link/offboarding methods.

`access-api.ts` must wrap:

```text
roles CRUD/status
capabilities list
role-capability mapping
user role grants/revokes
effective permissions self/user
```

Use the existing `/api/platform` BFF path and `credentials: "include"`; do not call the backend origin directly from the browser.

- [ ] **Step 3: Build a shared authenticated RTL admin shell.**

Navigation labels:

```text
نظرة عامة
المستخدمون
الموظفون
المنظمات
العضويات
الأقسام
المسميات الوظيفية
الأدوار
الصلاحيات
الوصول
سجل التدقيق
```

Follow existing SNAD brand/SDS constraints. Do not hardcode alternate brand names.

- [ ] **Step 4: Implement `/admin` overview as exception-oriented operations, not decoration.**

Cards/queues must surface at least:

```text
active/invited/suspended users
active employees / employees without accounts
terminated employees with active linked accounts
expiring role grants
unlinked HUMAN users
failed/incomplete offboarding items when backend exposes them
```

- [ ] **Step 5: Implement users and employee detail journeys.**

`/admin/users/[userId]` tabs/sections:

```text
Overview
Memberships
Employee Link
Roles
Effective Access
Sessions/Status actions available from existing IAM surface
Audit
```

`/admin/employees/[employeeId]`:

```text
workforce details
department/position/manager
linked user
link/unlink/create access setup actions
terminate/offboarding action
```

Forms must preserve entered data on server failure and reset only after confirmed success, following the hardened ERP form pattern.

- [ ] **Step 6: Implement roles/capabilities/access explorer.**

Roles UI groups capabilities by prefix/module. System-managed roles are visibly protected and mutation controls disabled, but backend remains the authority.

Access Explorer supports:

```text
What can this user do?
Why does this user have capability X?
Who has capability X in this organization?
```

The first two must use Effective Access APIs. The “who” query must use a backend tenant-scoped endpoint if one is added; do not download every user and reconstruct security decisions entirely in the browser.

- [ ] **Step 7: Implement tenant-scoped organization/membership pages.**

`/admin/organizations` is tenant administration only. Cross-tenant tenant selection remains under the existing Control Plane, never this route.

- [ ] **Step 8: Run web unit/lint/build.**

```bash
cd apps/web
npm test
npm run lint
npm run build
npm run brand:check
```

Expected: all PASS.

- [ ] **Step 9: Commit.**

```bash
git add apps/web/lib/api apps/web/app/admin
git commit -m "feat(admin): add unified tenant administration center"
```

---

## Task 13 — PF-13: Cross-module authorization, PostgreSQL acceptance, and end-to-end regression

**Purpose:** Prove the shared foundation is consumed safely across platform modules and does not weaken existing CRM/ERP/Finance authorization.

**Files:**
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/foundation/PlatformFoundationCrossModuleAuthorizationTest.java`
- Extend: `apps/sanad-platform/src/test/java/com/sanad/platform/foundation/PlatformFoundationRlsPostgresTest.java`
- Extend: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/HrWorkforceIntegrityPostgresTest.java`
- Create: `apps/web/e2e/platform-admin-acceptance.spec.ts`
- Modify CI only if the existing required suites do not automatically discover these tests; prefer discovery over adding another workflow.

- [ ] **Step 1: Add cross-module authorization tests.**

Use representative existing protected endpoints from CRM, ERP, Finance, HR, and Workflow. For each module prove:

```text
RBAC missing                         -> DENY
RBAC present + module entitlement off -> DENY for module-scoped capabilities
RBAC present + entitlement on         -> proceed to resource policy
wrong tenant                          -> DENY
platform-core administration cap      -> not blocked by unrelated commercial module entitlement
```

Do not alter business-module ownership rules to make these tests pass; fix only the platform authorization composition if needed.

- [ ] **Step 2: Run the PostgreSQL Direct acceptance set.**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=PlatformFoundationRlsPostgresTest,HrWorkforceIntegrityPostgresTest,PlatformFoundationCrossModuleAuthorizationTest test
```

Expected: PASS with the direct PostgreSQL acceptance datasource/application role.

- [ ] **Step 3: Run the full backend Maven suite used by branch protection.**

```bash
mvn -f apps/sanad-platform/pom.xml test
```

Expected: `FAILURES=0`, `ERRORS=0`. Skips must be reconciled; no DB-security/foundation acceptance test may be skipped because PostgreSQL is unavailable in CI.

- [ ] **Step 4: Run the full web quality gates.**

```bash
cd apps/web
npm test
npm run lint
npm run build
npm run brand:check
```

Expected: PASS.

- [ ] **Step 5: Add authenticated Playwright coverage.**

`platform-admin-acceptance.spec.ts` must exercise, against a disposable tenant fixture:

```text
login
/admin loads
create employee without user
create/link HUMAN user
assign membership
assign custom role
observe effective permission
change department/position/manager
revoke role
terminate employee / verify security freeze
confirm module pages still authorize through same user identity
```

Do not run destructive steps against production data.

- [ ] **Step 6: Run Playwright using the repository's existing authenticated test harness/configuration.**

Expected: PASS; no new hardcoded credentials committed to the repository.

- [ ] **Step 7: Commit acceptance tests.**

```bash
git add apps/sanad-platform/src/test/java/com/sanad/platform/foundation \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr \
  apps/web/e2e/platform-admin-acceptance.spec.ts
git commit -m "test(platform): certify shared identity and workforce foundation"
```

---

## Task 14 — PF-14: Pull request, human acceptance, merge gate, and production certification

**Purpose:** Close the architectural change only after CI and human operation prove the tenant administration center and shared security model work as designed.

**Files:**
- Create/update evidence: `docs/execution/platform-foundation/PF-14-ACCEPTANCE.md`
- No feature-code change is allowed during acceptance without returning to the relevant TDD task and rerunning its gates.

- [ ] **Step 1: Rebase/merge latest protected `main` into the feature branch before final PR verification.**

If ERP or another approved track changed shared files or migrations, resolve conflicts by preserving both approved semantics; do not overwrite newer work with the design baseline.

- [ ] **Step 2: Open a draft PR with scope and non-goals explicitly stated.**

PR body must include:

```text
Platform User = security identity
Employee = workforce record
optional tenant-safe 1:1 link
no module-specific user stores
no direct tenant user-capability grants
forward-only migrations
PostgreSQL Direct
/admin = tenant plane, not Control Plane
```

- [ ] **Step 3: Wait for all protected required checks and relevant diagnostic workflows.**

At minimum, do not merge while any of the branch-protection checks are expected/failing. Capture exact run ids/SHA for:

```text
Maven Test Suite
CRM Integration Tests
Build Next.js Web
provenance
CRM Deployment Readiness
schema/isolation required check
Playwright / visual regression where applicable
security baseline where applicable
```

- [ ] **Step 4: Create a full-stack disposable human preview.**

Reuse an approved repository preview mechanism. It must use the exact PR head SHA and must verify browser-equivalent authenticated routing. Do not use production data. The preview handoff must not expose secrets in public logs/artifacts.

- [ ] **Step 5: Human acceptance checklist.**

The user validates:

```text
/admin navigation and Arabic RTL
users lifecycle actions
employee create/edit
employee <-> user link/unlink
organizations and memberships
roles and capability grouping
system-role protection
custom role creation
role grant/revoke and organization scope
effective access explanation
department/position/manager hierarchy
offboarding journey
CRM/ERP identity selectors continue using platform users
no duplicate module user-management UI introduced
```

Record `PASS/FAIL` per item in `PF-14-ACCEPTANCE.md`.

- [ ] **Step 6: Use `superpowers:verification-before-completion`.**

Freshly rerun or fetch the exact latest-head evidence. Do not rely on earlier commits' green checks after the branch moves.

- [ ] **Step 7: Mark PR ready and merge only after explicit human acceptance and all required checks pass.**

Use expected-head protection when merging. No force/bypass.

- [ ] **Step 8: Verify production deployment and runtime only after merge.**

Confirm the deployed frontend SHA matches the merged `main` SHA and backend deployment/migrations are healthy. Smoke-test tenant-safe routes without mutating sensitive production data:

```text
/admin
/admin/users
/admin/employees
/admin/roles
/admin/access
/api/v1/access/me/effective-permissions
```

Check runtime errors/logs for new 4xx/5xx clusters caused by the foundation rollout.

- [ ] **Step 9: Final certification format.**

`PF-14-ACCEPTANCE.md` ends with evidence-backed values:

```text
PLATFORM_IDENTITY_FOUNDATION      = PASS/FAIL
TENANT_AUTHORITY                  = PASS/FAIL
FOUNDATION_RLS                    = PASS/FAIL
USER_EMPLOYEE_1_TO_1              = PASS/FAIL
WORKFORCE_TENANT_FKS              = PASS/FAIL
ROLE_PROVENANCE_PROTECTION        = PASS/FAIL
TEMPORAL_ROLE_GRANTS              = PASS/FAIL
EFFECTIVE_ACCESS                  = PASS/FAIL
MODULE_ENTITLEMENT_INTERSECTION   = PASS/FAIL
OFFBOARDING                       = PASS/FAIL
ADMIN_UI                          = PASS/FAIL
POSTGRESQL_DIRECT_ACCEPTANCE      = PASS/FAIL
FULL_BACKEND_SUITE                = PASS/FAIL
WEB_CI                            = PASS/FAIL
HUMAN_ACCEPTANCE                  = PASS/FAIL
MERGED_MAIN_SHA                   = <actual SHA at execution>
PRODUCTION_DEPLOYED_SHA           = <actual SHA at execution>
PLATFORM_FOUNDATION_CERTIFIED     = YES/NO
```

The SHA fields are evidence slots populated at execution time, not design placeholders; certification MUST remain `NO` until both are concrete and verified.

---

## Acceptance gates by phase

### Gate A — Foundation schema/security

Must pass before UI work is considered trustworthy:

```text
PF-01 reconciliation complete
PF-02 authenticated tenant authority green
PF-03 fail-closed FORCE RLS green through application DB role
PF-05 grant uniqueness/lifecycle green
PF-07 workforce composite FK + employee/user 1:1 green
```

### Gate B — Platform behavior

Must pass before `/admin` human preview:

```text
roles provenance protected
workforce APIs complete
authorized employee-user linking
manager cycles rejected
effective-access decision matrix green
offboarding security freeze/audit green
```

### Gate C — Human/release

Must pass before merge/certification:

```text
full Maven suite green
PostgreSQL Direct foundation tests green
web test/lint/build/brand green
Playwright admin acceptance green
all protected checks green
human visual/operational acceptance PASS
production SHA verified after merge
```

---

## Expected final architecture after execution

```text
                         SNAD TENANT
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
       IAM              ORGANIZATION          WORKFORCE
      users          memberships/orgs      employees/depts/positions
        │                    │                    │
        └──────────────┬─────┘                    │
                       ▼                          │
                 ACCESS CONTROL                  │
          roles → capabilities → grants          │
                       │                          │
                       └────────────┬─────────────┘
                                    ▼
                           EFFECTIVE ACCESS
                                    │
               ┌────────────────────┼────────────────────┐
               ▼                    ▼                    ▼
              CRM                  ERP                Finance/HR/POS/...

Tenant Admin UI: /admin/*
Cross-Tenant Control Plane: remains separate
```

The execution is complete only when this architecture is demonstrated by fresh repository, PostgreSQL, CI, human-preview, merge, and production evidence—not merely because the code was written.
