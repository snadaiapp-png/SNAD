# HRM-G0 WS5 API, UI and Verified Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the canonical HRM-G0 model through a typed `/api/v2/hr` contract, convert `/api/v1/hr` into a safe compatibility adapter, deliver an Arabic/RTL operational HR workspace, and execute a no-guessing per-tenant cutover with production certification evidence.

**Architecture:** Canonical v2 controllers are thin typed adapters over the application services built in WS2–WS4/WS6. Every protected operation has coarse capability + scoped authorization; critical commands add idempotency and optimistic concurrency. Existing v1 remains available only as a compatibility projection and may write only when context is authoritative. Per-tenant cutover uses a migration-state gate and a short write-freeze while backfill/reconciliation runs; there is no permanent dual-write.

**Tech Stack:** Java 17, Spring Boot 3.5.6, Jakarta Validation, springdoc/OpenAPI, MockMvc, PostgreSQL 17 Direct, Next.js 16.2, React 19.2, TypeScript 5.9, existing `apiClient`, existing `/me` capability utilities, Vitest 4.1, Playwright 1.61.

**Spec:** `docs/superpowers/specs/2026-08-27-hrm-g0-foundation-design.md`

## Global Constraints

- `/api/v2/hr` is canonical; no new feature is added to `/api/v1/hr`.
- Typed DTOs only. Do not use `Map<String,Object>` for v2 request bodies.
- Generic PATCH cannot change Employment lifecycle status.
- Critical POST commands require `Idempotency-Key`; stale aggregate version returns HTTP 409 `HRM_CONCURRENCY_CONFLICT`.
- Every resource uses current capability/scope authorization; UI hiding is convenience only, backend is authoritative.
- PII/Compensation/Contract restricted data is never returned by directory/list DTOs.
- v1 `DELETE /employees/{id}` never performs physical SQL delete; retire/block it before production certification.
- v1 create/update must not guess Legal Entity, Organization, jurisdiction, or assignment. Ambiguity returns HTTP 409 `HRM_MIGRATION_REQUIRED`.
- Tenant cutover phases prevent concurrent legacy writes during backfill. No permanent dual-write.
- Country Pack/Compliance status is visible in UI, including `GLOBAL_MODE`/`LOCAL_COMPLIANCE_UNVERIFIED`.
- Arabic/RTL is first-class; English labels may coexist but Arabic usability is the acceptance path.
- Do not mark `apps/web/app/hr/hr-execution-data.ts` G0 complete until final production evidence passes.
- API-count baseline at the approved SHA is 717 operations. The canonical v2 surface in this plan is exactly 58 operations; at that baseline the total becomes 775. If `main` advances before implementation, recalculate only the pre-HRM baseline and preserve `EXPECTED_HRM_V2_OPS=58` unless the approved contract itself changes.

---

## Canonical v2 Surface — 58 Operations

```text
People (9)
GET    /api/v2/hr/people
POST   /api/v2/hr/people
GET    /api/v2/hr/people/{personId}
PATCH  /api/v2/hr/people/{personId}
GET    /api/v2/hr/people/{personId}/private
PATCH  /api/v2/hr/people/{personId}/private
POST   /api/v2/hr/people/{personId}/identifiers
POST   /api/v2/hr/people/{personId}/user-link
DELETE /api/v2/hr/people/{personId}/user-link

Employments (11)
GET  /api/v2/hr/employments
POST /api/v2/hr/employments
GET  /api/v2/hr/employments/{employmentId}
POST /api/v2/hr/employments/{employmentId}/submit-onboarding
POST /api/v2/hr/employments/{employmentId}/activate
POST /api/v2/hr/employments/{employmentId}/start-leave
POST /api/v2/hr/employments/{employmentId}/return-from-leave
POST /api/v2/hr/employments/{employmentId}/suspend
POST /api/v2/hr/employments/{employmentId}/reinstate
POST /api/v2/hr/employments/{employmentId}/terminate
POST /api/v2/hr/employments/{employmentId}/void

Assignments (6)
GET  /api/v2/hr/assignments
POST /api/v2/hr/assignments
GET  /api/v2/hr/assignments/{assignmentId}
POST /api/v2/hr/assignments/{assignmentId}/end
POST /api/v2/hr/assignments/{assignmentId}/change-manager
POST /api/v2/hr/assignments/{assignmentId}/transfer

Org Units (4)
GET  /api/v2/hr/org-units
POST /api/v2/hr/org-units
GET  /api/v2/hr/org-units/{orgUnitId}
POST /api/v2/hr/org-units/{orgUnitId}/revise

Jobs (4)
GET  /api/v2/hr/jobs
POST /api/v2/hr/jobs
GET  /api/v2/hr/jobs/{jobId}
POST /api/v2/hr/jobs/{jobId}/revise

Positions (6)
GET  /api/v2/hr/positions
POST /api/v2/hr/positions
GET  /api/v2/hr/positions/{positionId}
POST /api/v2/hr/positions/{positionId}/revise
POST /api/v2/hr/positions/{positionId}/freeze
POST /api/v2/hr/positions/{positionId}/close

Contracts (6)
GET  /api/v2/hr/contracts
POST /api/v2/hr/contracts
GET  /api/v2/hr/contracts/{contractId}
POST /api/v2/hr/contracts/{contractId}/amend
POST /api/v2/hr/contracts/{contractId}/activate
POST /api/v2/hr/contracts/{contractId}/terminate

Compensation (5)
GET  /api/v2/hr/compensation-packages
POST /api/v2/hr/compensation-packages
GET  /api/v2/hr/compensation-packages/{packageId}
POST /api/v2/hr/compensation-packages/{packageId}/revise
POST /api/v2/hr/compensation-packages/{packageId}/end

Compliance/Audit (7)
GET  /api/v2/hr/compliance/context
GET  /api/v2/hr/compliance/overrides
POST /api/v2/hr/compliance/overrides
POST /api/v2/hr/compliance/overrides/{overrideId}/approve
POST /api/v2/hr/compliance/overrides/{overrideId}/reject
POST /api/v2/hr/compliance/overrides/{overrideId}/revoke
GET  /api/v2/hr/audit
```

## File Structure

```text
apps/sanad-platform/src/main/resources/db/migration/
  V20260827_11__seed_hrm_v2_capabilities_and_admin_scopes.sql

apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/v2/
  HrPeopleController.java
  HrEmploymentsController.java
  HrAssignmentsController.java
  HrStructureController.java
  HrContractsController.java
  HrCompensationController.java
  HrComplianceController.java
  HrAuditController.java
  dto/*Request.java
  dto/*Response.java
  HrApiErrorResponse.java
  HrApiExceptionHandler.java
  HrmIdempotentCommandExecutor.java

apps/sanad-platform/src/main/java/com/sanad/platform/hr/compatibility/
  LegacyHrCompatibilityService.java
  LegacyHrProjectionMapper.java
  HrMigrationStateService.java

apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/v2/
  HrApiV2ContractTest.java
  HrApiV2AuthorizationTest.java
  HrApiV2IdempotencyConcurrencyTest.java

apps/sanad-platform/src/test/java/com/sanad/platform/hr/compatibility/
  HrV1CompatibilityIntegrationTest.java
  HrCutoverStateIntegrationTest.java

apps/web/lib/api/
  hr-v2-api.ts
  hr-v2-api.test.ts

apps/web/lib/auth/capabilities.ts

apps/web/app/hr/
  page.tsx
  execution/page.tsx
  components/hr-workspace.tsx
  components/hr-feedback.tsx
  components/hr-compliance-badge.tsx
  hr.module.css
  employees/page.tsx
  employees/[employmentId]/page.tsx
  org-structure/page.tsx
  jobs/page.tsx
  positions/page.tsx
  assignments/page.tsx
  compliance/page.tsx

apps/web/app/hr/**/*.test.tsx

scripts/hrm/
  g0-cutover-tenant.sql
  g0-rollback-tenant.sql

docs/hrm/g0/evidence/
  05-api-ui-cutover.md
  production-cutover-*.md
```

### Task 1: Seed canonical HRM capabilities without broadening HR_MANAGER

**Files:**
- Create: `V20260827_11__seed_hrm_v2_capabilities_and_admin_scopes.sql`
- Create: `HrApiV2AuthorizationTest.java`
- Modify: `apps/web/lib/auth/capabilities.ts`

**Interfaces:**
- Produces exactly 19 canonical `HRM.*` capabilities.
- Grants them to tenant `ADMIN` roles and creates TENANT scope grants for those ADMIN grants.
- Does not add any of them to `HR_MANAGER`.

Canonical capabilities:

```text
HRM.EMPLOYEE.VIEW
HRM.EMPLOYEE.CREATE
HRM.EMPLOYEE.UPDATE
HRM.EMPLOYEE.TERMINATE
HRM.ORG_STRUCTURE.VIEW
HRM.ORG_STRUCTURE.MANAGE
HRM.ASSIGNMENT.VIEW
HRM.ASSIGNMENT.MANAGE
HRM.CONTRACT.VIEW
HRM.CONTRACT.MANAGE
HRM.COMPENSATION.VIEW
HRM.COMPENSATION.MANAGE
HRM.PII.VIEW
HRM.PII.MANAGE
HRM.USER_LINK.MANAGE
HRM.AUDIT.VIEW
HRM.COMPLIANCE_OVERRIDE.REQUEST
HRM.COMPLIANCE_OVERRIDE.APPROVE
HRM.ADMIN
```

- [ ] **Step 1: Write failing capability matrix assertions**

```java
assertThat(capabilitiesForRole(tenantId, "HR_MANAGER"))
    .containsExactlyInAnyOrder("HR.EMPLOYEE.READ", "HR.EMPLOYEE.WRITE", "HR.EMPLOYEE.ARCHIVE");
assertThat(capabilitiesForRole(tenantId, "ADMIN"))
    .contains("HRM.EMPLOYEE.VIEW", "HRM.PII.VIEW", "HRM.COMPLIANCE_OVERRIDE.APPROVE");
```

- [ ] **Step 2: Run and confirm RED for new capabilities**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrApiV2AuthorizationTest,RoleTemplateProvisionerContractTest test
```

- [ ] **Step 3: Seed active capabilities and ADMIN grants idempotently**

Use the existing `access_capabilities`/`role_capabilities` model. For every new capability, grant only to roles with code `ADMIN`; do not infer HR_MANAGER permissions.

For each ADMIN capability grant create an effective `TENANT` row in `access_scope_grants`. Do not create direct-user exceptions.

- [ ] **Step 4: Add frontend constants**

Extend `apps/web/lib/auth/capabilities.ts`:

```ts
export const HRM_CAPABILITIES = {
  EMPLOYEE_VIEW: "HRM.EMPLOYEE.VIEW",
  EMPLOYEE_CREATE: "HRM.EMPLOYEE.CREATE",
  EMPLOYEE_UPDATE: "HRM.EMPLOYEE.UPDATE",
  EMPLOYEE_TERMINATE: "HRM.EMPLOYEE.TERMINATE",
  ORG_STRUCTURE_VIEW: "HRM.ORG_STRUCTURE.VIEW",
  ORG_STRUCTURE_MANAGE: "HRM.ORG_STRUCTURE.MANAGE",
  ASSIGNMENT_VIEW: "HRM.ASSIGNMENT.VIEW",
  ASSIGNMENT_MANAGE: "HRM.ASSIGNMENT.MANAGE",
  CONTRACT_VIEW: "HRM.CONTRACT.VIEW",
  CONTRACT_MANAGE: "HRM.CONTRACT.MANAGE",
  COMPENSATION_VIEW: "HRM.COMPENSATION.VIEW",
  COMPENSATION_MANAGE: "HRM.COMPENSATION.MANAGE",
  PII_VIEW: "HRM.PII.VIEW",
  PII_MANAGE: "HRM.PII.MANAGE",
  USER_LINK_MANAGE: "HRM.USER_LINK.MANAGE",
  AUDIT_VIEW: "HRM.AUDIT.VIEW",
  OVERRIDE_REQUEST: "HRM.COMPLIANCE_OVERRIDE.REQUEST",
  OVERRIDE_APPROVE: "HRM.COMPLIANCE_OVERRIDE.APPROVE",
  ADMIN: "HRM.ADMIN",
} as const;
```

Frontend checks are UX-only; backend remains authoritative.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrApiV2AuthorizationTest,RoleTemplateProvisionerContractTest test
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_11__seed_hrm_v2_capabilities_and_admin_scopes.sql \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/v2/HrApiV2AuthorizationTest.java \
  apps/web/lib/auth/capabilities.ts
git commit -m "feat(hrm): seed v2 capabilities without role expansion"
```

### Task 2: Add structured HRM v2 error model and typed DTOs

**Files:**
- Create: `HrApiErrorResponse.java`
- Create: `HrApiExceptionHandler.java`
- Create: v2 DTO files under `api/v2/dto`.
- Create/modify: `HrApiV2ContractTest.java`

**Interfaces:**
- Produces structured errors including:

```text
HRM_EMPLOYMENT_NOT_FOUND
HRM_INVALID_STATE_TRANSITION
HRM_ACTIVATION_BLOCKED
HRM_POSITION_OCCUPIED
HRM_ASSIGNMENT_OVERLAP
HRM_EMPLOYMENT_CONFLICT
HRM_SCOPE_DENIED
HRM_COUNTRY_PACK_NOT_CERTIFIED
HRM_COMPLIANCE_BLOCKED
HRM_OVERRIDE_APPROVAL_REQUIRED
HRM_LEGAL_REVIEW_REQUIRED
HRM_IDEMPOTENCY_CONFLICT
HRM_CONCURRENCY_CONFLICT
HRM_MIGRATION_REQUIRED
```

- [ ] **Step 1: Write failing MockMvc contract tests**

```java
mockMvc.perform(post("/api/v2/hr/employments/{id}/activate", employmentId)
        .header("Idempotency-Key", "activate-1")
        .contentType(APPLICATION_JSON)
        .content("{\"effectiveDate\":\"2026-08-27\",\"expectedVersion\":0}"))
    .andExpect(status().isConflict())
    .andExpect(jsonPath("$.code").value("HRM_ACTIVATION_BLOCKED"))
    .andExpect(jsonPath("$.violations").isArray());
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrApiV2ContractTest test
```

- [ ] **Step 3: Implement typed request/response records/classes with Jakarta Validation**

Example:

```java
public record CreateEmploymentRequest(
    @NotNull UUID personId,
    @NotNull UUID legalEntityId,
    @NotBlank @Size(max = 80) String employeeNumber,
    @NotNull LocalDate employmentStartDate,
    @NotBlank @Pattern(regexp = "[A-Z]{2}") String laborJurisdictionCode
) {}
```

Do not expose encrypted identifier values, blind indexes, internal audit rows, or unrestricted compensation fields through generic Employee responses.

- [ ] **Step 4: Implement exception handler status mapping**

Use 400 for validation, 401 unauthenticated, 403 scope/capability denial, 404 missing canonical resource, 409 state/occupancy/idempotency/concurrency/migration conflicts, and 422 for structurally valid requests blocked by compliance/business validation where appropriate. Keep codes stable independently of localized message text.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrApiV2ContractTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/v2 \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/v2/HrApiV2ContractTest.java
git commit -m "feat(hrm): define typed v2 API contract"
```

### Task 3: Implement People and Employment v2 endpoints with lifecycle commands

**Files:**
- Create: `HrPeopleController.java`
- Create: `HrEmploymentsController.java`
- Create: `HrmIdempotentCommandExecutor.java`
- Modify: `HrApiV2ContractTest.java`, `HrApiV2AuthorizationTest.java`
- Create: `HrApiV2IdempotencyConcurrencyTest.java`

**Interfaces:**
- Implements the 9 People and 11 Employment operations listed above.

- [ ] **Step 1: Add failing route/capability tests for all 20 operations**

Require independent capabilities per operation; PII endpoints require `HRM.PII.VIEW/MANAGE`, user link requires `HRM.USER_LINK.MANAGE`, terminate requires `HRM.EMPLOYEE.TERMINATE`.

- [ ] **Step 2: Add idempotency and concurrency tests**

```java
@Test
void duplicateActivateRequestReplaysSameResponse() { /* same key/fingerprint */ }

@Test
void staleExpectedVersionReturns409() { /* expectedVersion 4 vs current 5 */ }
```

- [ ] **Step 3: Implement controllers as thin adapters**

Controller flow for lifecycle POST:

```text
SecurityContext → tenant/user
@RequireCapability coarse gate
→ resolve canonical resource context
→ ScopedAuthorizationService.require
→ HrmIdempotentCommandExecutor with Idempotency-Key/fingerprint
→ CountryPolicyResolver/ComplianceEngine inside command service
→ EmploymentCommandService transition
→ typed response
```

`HrmIdempotentCommandExecutor` owns replay/conflict plumbing; business services do not parse HTTP headers.

- [ ] **Step 4: Require explicit lifecycle effective date and expected version**

No controller defaults lifecycle effective date to `LocalDate.now()` when the request omits it. Missing required legal/business dates are validation errors.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrApiV2ContractTest,HrApiV2AuthorizationTest,HrApiV2IdempotencyConcurrencyTest \
  test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/v2 \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/v2
git commit -m "feat(hrm): expose people employment and lifecycle v2 APIs"
```

### Task 4: Implement Assignment and Structure v2 endpoints

**Files:**
- Create: `HrAssignmentsController.java`
- Create: `HrStructureController.java`
- Modify: `HrApiV2ContractTest.java`, `HrApiV2AuthorizationTest.java`

**Interfaces:**
- Implements 6 Assignment + 4 Org Unit + 4 Job + 6 Position operations = 20 operations.

- [ ] **Step 1: Write failing route tests for all 20 operations**

Reads require `HRM.ASSIGNMENT.VIEW` or `HRM.ORG_STRUCTURE.VIEW`; mutations require corresponding `MANAGE` capability plus scope.

- [ ] **Step 2: Implement Assignment commands**

Create, end, change-manager and transfer requests require effective dates and expected versions. Transfer closes/supersedes the current effective assignment period and creates the new period atomically; it does not overwrite historical placement.

- [ ] **Step 3: Implement effective-dated structure commands**

`revise` creates new version rows; `freeze`/`close` act on Position staffability, not occupancy. Org Unit revise runs period-aware cycle validation.

- [ ] **Step 4: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrApiV2ContractTest,HrApiV2AuthorizationTest,HrAssignmentTemporalConstraintTest,HrStructureVersioningIntegrationTest \
  test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/v2 \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/v2
git commit -m "feat(hrm): expose assignment and structure v2 APIs"
```

### Task 5: Implement Contract, Compensation, Compliance and Audit v2 endpoints

**Files:**
- Create: `HrContractsController.java`
- Create: `HrCompensationController.java`
- Create: `HrComplianceController.java`
- Create: `HrAuditController.java`
- Modify: `HrApiV2ContractTest.java`, `HrApiV2AuthorizationTest.java`

**Interfaces:**
- Implements 6 Contract + 5 Compensation + 7 Compliance/Audit operations = 18 operations.

- [ ] **Step 1: Write failing capability/PII leakage tests**

Employee-view capability alone must receive 403 for compensation/PII/audit endpoints. Contract/compensation list responses are available only through their dedicated capabilities.

- [ ] **Step 2: Implement controllers with sensitive-read audit**

Before returning compensation or restricted contract/audit data, application service authorization and `SensitiveReadAuditService.recordOrThrow()` must succeed.

- [ ] **Step 3: Implement override workflow HTTP semantics**

`POST /compliance/overrides` returns 202/201 for a valid controlled-exception request. Hard-rule override request returns 409/422 `HRM_COMPLIANCE_BLOCKED`. Approval requires `HRM.COMPLIANCE_OVERRIDE.APPROVE`, different approver, justification/comment, and current rule revalidation.

- [ ] **Step 4: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrApiV2ContractTest,HrApiV2AuthorizationTest,HrComplianceOverrideIntegrationTest,HrCompensationAuthorizationAuditIntegrationTest \
  test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/v2 \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/v2
git commit -m "feat(hrm): expose sensitive and compliance v2 APIs"
```

### Task 6: Pin the OpenAPI/API-count contract to exactly 58 HRM v2 operations

**Files:**
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/api/PlatformApiCountTest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/v2/HrOpenApiContractTest.java`
- Create: `docs/hrm/contracts/openapi/hrm-openapi.json` generated from the reviewed runtime contract.

**Interfaces:**
- Produces: stable HRM v2 public surface and committed OpenAPI artifact.

- [ ] **Step 1: Add failing count assertion before updating total baseline**

```java
private static final long EXPECTED_HRM_V2_OPS = 58;
assertThat(count(paths, "/api/v2/hr")).isEqualTo(EXPECTED_HRM_V2_OPS);
```

At the approved 717 baseline, retain the old total first so the test proves RED when 58 operations are introduced.

- [ ] **Step 2: Run and confirm the exact delta**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=PlatformApiCountTest,HrOpenApiContractTest test
```

Expected initial failure: platform total is old baseline + 58; HRM v2 count itself equals 58.

- [ ] **Step 3: Update documented total only after exact count is proven**

At baseline 717:

```java
private static final long EXPECTED_HRM_V2_OPS = 58;
private static final long EXPECTED_TOTAL_OPS = 775;
```

If newer `main` changed the pre-HRM total, set `EXPECTED_TOTAL_OPS = verifiedPreHrmTotal + 58` and document the source SHA/delta in the comment. Never change the number merely to make a failing test green without accounting for every operation.

- [ ] **Step 4: Generate and commit OpenAPI contract**

Use `/v3/api-docs`, extract `/api/v2/hr` paths into `docs/hrm/contracts/openapi/hrm-openapi.json`, and have `HrOpenApiContractTest` compare runtime paths/methods and required schemas to the committed contract.

- [ ] **Step 5: Run and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=PlatformApiCountTest,HrOpenApiContractTest test
git add apps/sanad-platform/src/test/java/com/sanad/platform/api/PlatformApiCountTest.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/v2/HrOpenApiContractTest.java \
  docs/hrm/contracts/openapi/hrm-openapi.json
git commit -m "test(hrm): pin canonical v2 API contract"
```

### Task 7: Replace v1 implementation with safe compatibility semantics

**Files:**
- Create: `LegacyHrCompatibilityService.java`
- Create: `LegacyHrProjectionMapper.java`
- Create: `HrMigrationStateService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/HrController.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/domain/HrEmployeeRepository.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/infrastructure/JdbcHrEmployeeRepository.java`
- Create: `HrV1CompatibilityIntegrationTest.java`
- Create: `HrCutoverStateIntegrationTest.java`

**Interfaces:**
- v1 reads remain compatible.
- v1 unsafe delete is retired.
- Canonical tenants project Person + Employment + effective PRIMARY Assignment into legacy response shape.

- [ ] **Step 1: Write compatibility tests before changing controller**

```java
@Test
void canonicalTenantV1ReadProjectsLegacyShape() { /* names, employeeNumber, department/position projection */ }

@Test
void v1DeleteNeverPhysicallyDeletesEmployment() {
    mockMvc.perform(delete("/api/v1/hr/employees/{id}", employmentId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("HRM_MIGRATION_REQUIRED"));
    assertThat(employmentStillExists(employmentId)).isTrue();
}

@Test
void ambiguousV1CreateReturnsMigrationRequiredInsteadOfGuessing() { /* two eligible orgs */ }
```

- [ ] **Step 2: Run and prove RED against legacy behavior**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrV1CompatibilityIntegrationTest,HrCutoverStateIntegrationTest test
```

- [ ] **Step 3: Implement per-tenant migration phases**

`hr_migration_tenant_state` (created in WS2's employment expansion) uses:

```text
LEGACY
MIGRATING
CANONICAL
BLOCKED
```

`MIGRATING` allows reads but blocks v1 writes with 409. `CANONICAL` routes v1 reads through canonical projection. No tenant moves to CANONICAL until reconciliation is PASS.

- [ ] **Step 4: Refactor `HrController` to delegate**

Remove `Map<String,Object>` business logic from the controller. Preserve v1 response contract but delegate to `LegacyHrCompatibilityService`.

For v1 create in LEGACY/CANONICAL compatibility mode, the service may proceed only when exactly one active Legal Entity and one effective eligible Organization are authoritative and jurisdiction default is valid; otherwise return `HRM_MIGRATION_REQUIRED`.

v1 PATCH rejects lifecycle status, manager, department, position, or employment-type changes that cannot be translated unambiguously; profile-only compatible edits may delegate to canonical Person update after cutover.

- [ ] **Step 5: Remove physical delete capability from repository**

Delete `HrEmployeeRepository.delete(...)` and the `DELETE FROM hr_employees` implementation from `JdbcHrEmployeeRepository`. No operational code path retains it.

- [ ] **Step 6: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrV1CompatibilityIntegrationTest,HrCutoverStateIntegrationTest,HrTenantContextRegressionTest \
  test
rg -n "DELETE FROM hr_employees" apps/sanad-platform/src/main/java
```

Expected: tests PASS; `rg` returns no production Java match.

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/compatibility
git commit -m "refactor(hrm): make v1 a safe compatibility adapter"
```

### Task 8: Build typed HRM v2 web client and shared workspace

**Files:**
- Create: `apps/web/lib/api/hr-v2-api.ts`
- Create: `apps/web/lib/api/hr-v2-api.test.ts`
- Create: `apps/web/app/hr/components/hr-workspace.tsx`
- Create: `apps/web/app/hr/components/hr-feedback.tsx`
- Create: `apps/web/app/hr/components/hr-compliance-badge.tsx`
- Create: `apps/web/app/hr/hr.module.css`
- Create: `apps/web/app/hr/components/hr-workspace.test.tsx`
- Create: `apps/web/app/hr/execution/page.tsx`
- Modify: current `apps/web/app/hr/page.tsx` after preserving its execution-dashboard behavior at `/hr/execution`.

**Interfaces:**
- Client base: `/api/platform/api/v2/hr`.
- Uses existing `apiClient` and existing `hasCapability` helpers.

- [ ] **Step 1: Move the current execution dashboard without losing it**

Copy/refactor current `/hr` execution dashboard to `/hr/execution`; keep `hr-execution-data.ts` and provider behavior unchanged except imports required by route move.

- [ ] **Step 2: Write failing API client route/payload tests**

Cover representative operations from every resource and assert `Idempotency-Key`/expected-version headers or fields are sent for critical commands.

- [ ] **Step 3: Implement typed client**

Do not use `Partial<HrEmployeeResponse>` as the canonical request type. Define separate request/response types mirroring OpenAPI v2.

- [ ] **Step 4: Write failing workspace navigation test**

Required links:

```text
/hr
/hr/employees
/hr/org-structure
/hr/jobs
/hr/positions
/hr/assignments
/hr/compliance
/hr/execution
```

- [ ] **Step 5: Implement Arabic-first authenticated workspace**

Use existing application shell/auth patterns. Provide loading/error/empty/forbidden states and responsive RTL layout. Do not invent client-side authorization as a substitute for server checks.

- [ ] **Step 6: Run and commit**

```bash
cd apps/web
npm test -- lib/api/hr-v2-api.test.ts app/hr/components/hr-workspace.test.tsx
cd ../..
git add apps/web/lib/api/hr-v2-api.ts apps/web/lib/api/hr-v2-api.test.ts \
  apps/web/app/hr apps/web/lib/auth/capabilities.ts
git commit -m "feat(hrm): add typed web client and workspace"
```

### Task 9: Build Employee Directory and Employee 360

**Files:**
- Create: `apps/web/app/hr/employees/page.tsx`
- Create: `apps/web/app/hr/employees/page.test.tsx`
- Create: `apps/web/app/hr/employees/[employmentId]/page.tsx`
- Create: `apps/web/app/hr/employees/[employmentId]/page.test.tsx`

**Interfaces:**
- Directory consumes safe Employment/Person summary only.
- Employee 360 tabs: Overview, Employment, Assignments, Organization, Contract, Compensation, Private Information, Timeline, Compliance, Audit.

- [ ] **Step 1: Write failing directory tests**

Assert search/filter/list behavior and that fixture fields such as National ID/compensation amount do not render in directory rows.

- [ ] **Step 2: Implement directory**

Filters: status, Organization, Org Unit, Legal Entity, worker classification, country mode. Pagination uses server/query contract rather than loading all employees when pagination is available.

- [ ] **Step 3: Write failing Employee 360 permission tests**

Use `/me` capability fixtures:

```text
EMPLOYEE_VIEW only           → overview/employment visible; PII/comp hidden
PII_VIEW                     → private tab available
COMPENSATION_VIEW            → compensation tab available
AUDIT_VIEW                   → audit tab available
```

Backend 403 remains authoritative if scope denies a resource despite frontend capability.

- [ ] **Step 4: Implement lifecycle actions with explicit confirmations**

Activate/suspend/reinstate/terminate/void actions send effective date, expected version and generated idempotency key. Termination is never labeled delete.

- [ ] **Step 5: Implement compliance badge/status**

Display localized mode and safe warnings:

```text
LOCALIZED / pack version
GLOBAL_MODE — local statutory compliance not certified
CONTROLLED_EXCEPTION_REQUIRED
COMPLIANCE_BLOCKED
```

Do not show a green “compliant” badge for Global Mode.

- [ ] **Step 6: Run and commit**

```bash
cd apps/web
npm test -- app/hr/employees/page.test.tsx app/hr/employees/[employmentId]/page.test.tsx
cd ../..
git add apps/web/app/hr/employees
git commit -m "feat(hrm): add employee directory and 360 profile"
```

### Task 10: Build Org Structure, Jobs, Positions, Assignments and Compliance workspaces

**Files:**
- Create pages/tests for `org-structure`, `jobs`, `positions`, `assignments`, `compliance`.
- Modify: `apps/web/app/hr/page.tsx` to become operational HR dashboard.

- [ ] **Step 1: Write failing route/workflow tests**

Cover Org Unit revision, Position freeze/close, Assignment transfer/change-manager, and controlled compliance override request/approval.

- [ ] **Step 2: Implement effective-dated Org Chart**

Provide `asOf` date input and render hierarchical Org Units from the canonical read model. Keep it accessible HTML/React structure; no additional chart dependency is required unless the existing frontend already has an approved reusable tree component.

- [ ] **Step 3: Implement Jobs/Positions/Assignments pages**

Make VACANT/OCCUPIED display derived from effective occupying assignments. Do not add a manual occupancy toggle.

- [ ] **Step 4: Implement Compliance page**

Display Country Pack/mode/rule decision metadata and override requests according to capabilities. Hard statutory blocks show no “override” action. Controlled exceptions show request flow; approval UI is only visible with `HRM.COMPLIANCE_OVERRIDE.APPROVE` and still relies on backend four-eyes enforcement.

- [ ] **Step 5: Implement dashboard**

Use authoritative server summaries: active Employments, onboarding, suspended/on-leave, vacancies, unresolved migration/compliance warnings. Do not use mock production data.

- [ ] **Step 6: Run frontend verification and commit**

```bash
cd apps/web
npm test -- app/hr
npm run lint
npm run build
cd ../..
git add apps/web/app/hr
git commit -m "feat(hrm): add operational structure and compliance workspace"
```

### Task 11: Execute deterministic tenant cutover with write freeze and rollback point

**Files:**
- Create: `scripts/hrm/g0-cutover-tenant.sql`
- Create: `scripts/hrm/g0-rollback-tenant.sql`
- Modify: `HrCutoverStateIntegrationTest.java`
- Create: `docs/hrm/g0/evidence/05-api-ui-cutover.md`

**Interfaces:**
- Transitions one tenant: `LEGACY → MIGRATING → CANONICAL`, or `MIGRATING → BLOCKED/LEGACY` before canonical writes begin.

- [ ] **Step 1: Write cutover-state tests**

```java
@Test
void migratingTenantCanReadV1ButCannotWriteV1() { /* GET 200, POST/PATCH 409 */ }

@Test
void canonicalTransitionRequiresZeroUnresolvedRows() { /* nonzero => blocked */ }
```

- [ ] **Step 2: Implement cutover SQL guard**

`g0-cutover-tenant.sql` accepts a psql variable `tenant_id` and performs:

```text
1. lock/update tenant migration state → MIGRATING
2. refuse if already CANONICAL or unresolved precheck state exists
3. commit write freeze state
4. operator runs g0-backfill-precheck.sql + g0-backfill.sql scoped to tenant
5. operator runs g0-reconcile.sql scoped to tenant
6. only if every reconciliation gate passes, set state → CANONICAL
```

The scripts must not store credentials; use normal environment/psql connection configuration.

- [ ] **Step 3: Implement rollback semantics**

Before CANONICAL writes occur, rollback may return MIGRATING → LEGACY after removing only incomplete canonical rows created by the failed attempt using migration mapping IDs. After canonical writes begin, do not destructively roll back data; use application rollback/forward-fix while canonical data remains authoritative.

- [ ] **Step 4: Rehearse against disposable PostgreSQL Direct fixture**

Run a tenant with unambiguous data and confirm complete cutover. Run ambiguous fixture and prove transition never reaches CANONICAL.

- [ ] **Step 5: Run backend/API and frontend gate**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrApiV2ContractTest,HrApiV2AuthorizationTest,HrApiV2IdempotencyConcurrencyTest,HrOpenApiContractTest,HrV1CompatibilityIntegrationTest,HrCutoverStateIntegrationTest,PlatformApiCountTest \
  test

cd apps/web
npm test -- app/hr lib/api/hr-v2-api.test.ts
npm run lint
npm run build
```

Expected: all PASS.

- [ ] **Step 6: Record evidence and commit**

```bash
git add scripts/hrm/g0-cutover-tenant.sql scripts/hrm/g0-rollback-tenant.sql \
  docs/hrm/g0/evidence/05-api-ui-cutover.md \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/compatibility/HrCutoverStateIntegrationTest.java
git commit -m "feat(hrm): add verified tenant cutover procedure"
```

### Task 12: Preview, production rehearsal and final WS5 gate

**Files:**
- Create/modify a path-filtered HRM preview workflow only if the existing preview harness cannot run this branch safely.
- Evidence: `docs/hrm/g0/evidence/05-api-ui-cutover.md` plus production-cutover evidence.

- [ ] **Step 1: Run full backend suite**

```bash
mvn -f apps/sanad-platform/pom.xml test
```

Expected: BUILD SUCCESS, failures=0, errors=0.

- [ ] **Step 2: Run full web suite/build**

```bash
cd apps/web
npm test
npm run lint
npm run build
```

Expected: all PASS.

- [ ] **Step 3: Deploy preview with a disposable/backfilled HR tenant**

Verify browser flows under the same reverse-proxy/BFF security model used by production. Test same-origin mutations, CSRF/origin protections, auth/session behavior, and cross-site denial.

- [ ] **Step 4: Human acceptance checklist**

Human must verify:

```text
/hr dashboard
/hr/employees directory
Employee 360
Arabic/RTL
Org chart as-of date
Jobs/Positions vacancy derivation
Assignment transfer/change-manager
PII hidden without PII capability
Compensation hidden without compensation capability
Global Mode warning
Hard compliance block has no override path
Controlled override requires request + separate approver
v1 compatibility read
v1 DELETE blocked
```

- [ ] **Step 5: Production migration rehearsal before merge**

On production-shaped PostgreSQL backup/clone, run migration chain, precheck, tenant backfill, reconciliation, cutover and smoke. Record row counts and timings. Do not use production secrets in artifacts.

- [ ] **Step 6: Record WS5 verdict**

`WS5_API_UI_CUTOVER=PASS` only after backend/web tests, preview, human acceptance, and migration rehearsal pass. Final HRM-G0 production certification remains governed by the Master Plan Task 7/8 after all workstreams are integrated.
