# HRM-G0 WS4 Security and Integration Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add fine-grained scoped authorization and producer-local audit/outbox/idempotency durability, then connect HR lifecycle events to IAM and the existing Platform Audit sink without cross-module database coupling.

**Architecture:** Keep existing `@RequireCapability`/`CapabilityEvaluationService` as the coarse capability gate. Canonical HRM v2 adds `ScopedAuthorizationService` for resource-level scope evaluation. HR mutations append immutable local audit evidence and a local outbox event in the same transaction. Post-commit workers deliver audit/events at-least-once. HR idempotency is durable and producer-local. IAM changes flow through an explicit port/consumer and affect only employment-managed access.

**Tech Stack:** Java 17, Spring Boot 3.5.6, Spring Security/AOP, JdbcTemplate, PostgreSQL 17 Direct, Flyway, scheduled workers/TransactionTemplate, JUnit 5/AssertJ, ArchUnit.

**Spec:** `docs/superpowers/specs/2026-08-27-hrm-g0-foundation-design.md`

## Global Constraints

- Default deny.
- Capability does not imply scope; HRM v2 requires both.
- Current canonical `HR_MANAGER` is not automatically granted new `HRM.*` capabilities.
- `SELF`, `DIRECT_REPORTS`, and `REPORTING_TREE` are evaluated from current authorization relationships, even when business data is queried `asOf` a historical date.
- Historical management never resurrects access.
- Role scope is the normal maximum. Any direct-user exception that widens scope requires explicit exception metadata, reason, expiry, ADMIN approval, and audit; no implicit widening.
- Sensitive PII, compensation, contracts, lifecycle commands, compliance overrides, and audit viewing remain separate capabilities.
- Local audit ledger rows are append-only. Delivery state is stored separately so workers never mutate the audit fact.
- A critical HR mutation fails/rolls back if its required local audit/outbox append fails.
- Sensitive-read audit fails closed: do not return restricted data if the required read-audit record cannot be written.
- Outbox delivery is at-least-once and consumers are idempotent.
- HRM does not directly update IAM/Workflow/Payroll/Accounting tables.
- Existing CRM outbox/idempotency code is a reference pattern only; HRM must not import CRM implementation classes.
- IAM account status is not Employment status. Termination does not blindly suspend a User who has unrelated/non-HR-managed access.

---

## File Structure

```text
apps/sanad-platform/src/main/resources/db/migration/
  V20260827_8__create_hr_access_scopes.sql
  V20260827_9__create_hr_audit_outbox_idempotency.sql

apps/sanad-platform/src/main/java/com/sanad/platform/security/scope/
  AccessScopeType.java
  AccessScopeGrant.java
  ScopedAuthorizationRequest.java
  ScopedAuthorizationDecision.java
  ScopedAuthorizationService.java
  JdbcAccessScopeRepository.java

apps/sanad-platform/src/main/java/com/sanad/platform/hr/security/
  HrAuthorizationResourceContext.java
  HrResourceContextResolver.java

apps/sanad-platform/src/main/java/com/sanad/platform/hr/audit/
  HrAuditRecord.java
  HrAuditService.java
  SensitiveReadAuditService.java
  JdbcHrAuditRepository.java
  HrAuditDeliveryWorker.java

apps/sanad-platform/src/main/java/com/sanad/platform/hr/integration/
  HrDomainEventPublisher.java
  JdbcHrOutboxRepository.java
  HrOutboxWorker.java
  HrmIamEventConsumer.java
  IamEmploymentAccessPort.java
  UserServiceIamEmploymentAccessAdapter.java
  HrmIamAccessPolicy.java

apps/sanad-platform/src/main/java/com/sanad/platform/hr/idempotency/
  JdbcHrRequestIdempotencyService.java

apps/sanad-platform/src/test/java/com/sanad/platform/hr/security/
  HrScopedAuthorizationIntegrationTest.java
  HrHistoricalAuthorizationIntegrationTest.java

apps/sanad-platform/src/test/java/com/sanad/platform/hr/integration/
  HrAuditOutboxAtomicityIntegrationTest.java
  HrSensitiveReadAuditIntegrationTest.java
  HrOutboxDeliveryIntegrationTest.java
  HrIamPolicyConsumerIntegrationTest.java
  HrIdempotencyIntegrationTest.java
  HrModuleBoundaryArchitectureTest.java
```

### Task 1: Add access-scope persistence and fail-closed schema

**Files:**
- Create: `V20260827_8__create_hr_access_scopes.sql`
- Create: `HrScopedAuthorizationIntegrationTest.java`

**Interfaces:**
- Produces: `access_scope_grants`.
- Scope types: `SELF`, `DIRECT_REPORTS`, `REPORTING_TREE`, `ORG_UNIT`, `ORGANIZATION`, `TENANT`.

- [ ] **Step 1: Write failing scope persistence/evaluation fixtures**

```java
@Test
void capabilityWithoutScopeIsDeniedForCanonicalHrmRequest() {
    fixture.grantCapability(roleId, "HRM.EMPLOYEE.VIEW");
    assertThat(authorize(userId, employeeResource)).isDenied();
}

@Test
void organizationScopeDoesNotCrossOrganizationBoundary() {
    fixture.grantScopedCapability(roleId, "HRM.EMPLOYEE.VIEW", ORGANIZATION, orgA);
    assertThat(authorize(userId, employeeInOrgA)).isAllowed();
    assertThat(authorize(userId, employeeInOrgB)).isDenied();
}
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrScopedAuthorizationIntegrationTest test
```

- [ ] **Step 3: Implement scope table**

```sql
CREATE TABLE access_scope_grants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    role_id UUID REFERENCES roles(id),
    user_id UUID REFERENCES users(id),
    capability_id UUID NOT NULL REFERENCES access_capabilities(id),
    scope_type VARCHAR(30) NOT NULL,
    organization_id UUID REFERENCES organizations(id),
    org_unit_id UUID REFERENCES hr_org_units(id),
    legal_entity_id UUID REFERENCES legal_entities(id),
    is_direct_exception BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(500),
    granted_by UUID,
    effective_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    effective_to TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_access_scope_principal CHECK ((role_id IS NULL) <> (user_id IS NULL)),
    CONSTRAINT ck_access_scope_type CHECK (scope_type IN (
      'SELF','DIRECT_REPORTS','REPORTING_TREE','ORG_UNIT','ORGANIZATION','TENANT')),
    CONSTRAINT ck_access_scope_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_access_scope_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    CONSTRAINT ck_access_scope_direct_exception CHECK (
      is_direct_exception = FALSE OR (user_id IS NOT NULL AND reason IS NOT NULL AND effective_to IS NOT NULL AND granted_by IS NOT NULL)
    )
);
```

Add fail-closed FORCE RLS and indexes on tenant/principal/capability/status.

- [ ] **Step 4: Do not backfill legacy `HR.*` as canonical scope grants**

Legacy v1 continues to use its current capability behavior until WS5 compatibility cutover. New `HRM.*` capabilities/scopes are seeded in WS5 after their API surface exists. This avoids silently broadening `HR_MANAGER`.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrScopedAuthorizationIntegrationTest test
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_8__create_hr_access_scopes.sql \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/security/HrScopedAuthorizationIntegrationTest.java
git commit -m "feat(security): add scoped authorization grants"
```

### Task 2: Implement scoped authorization and current-time reporting scopes

**Files:**
- Create: `com/sanad/platform/security/scope/*` files listed above.
- Create: `com/sanad/platform/hr/security/*` files listed above.
- Create: `HrHistoricalAuthorizationIntegrationTest.java`
- Modify: `HrScopedAuthorizationIntegrationTest.java`

**Interfaces:**

```java
ScopedAuthorizationDecision authorize(ScopedAuthorizationRequest request);
void require(ScopedAuthorizationRequest request);
```

`HrAuthorizationResourceContext` contains tenant, resource type/id, person/employment/assignment IDs, current Organization/Org Unit/Legal Entity context, and data classification; it never contains raw PII.

- [ ] **Step 1: Write scope behavior tests**

Cover:

```text
SELF           → only Person linked to current User
DIRECT_REPORTS → current effective primary assignment reports directly to actor's current assignment
REPORTING_TREE → current effective recursive reporting chain
ORG_UNIT       → target unit and its effective descendants
ORGANIZATION   → exact Organization
TENANT         → any resource in tenant, still subject to data-classification capability
```

Historical test:

```java
@Test
void formerManagerCannotReadHistoricalEmployeeAfterCurrentRelationshipEnds() {
    fixture.historicalManagerRelationship(manager, employee, lastYear);
    fixture.noCurrentReportingRelationship(manager, employee);
    assertThat(authorizeHistoricalRead(manager, employee, lastYear)).isDenied();
}
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrScopedAuthorizationIntegrationTest,HrHistoricalAuthorizationIntegrationTest \
  test
```

- [ ] **Step 3: Implement two-stage authorization**

Algorithm:

```text
1. Validate tenant/user/capability/resource context.
2. Call existing CapabilityEvaluationService for coarse capability authorization.
3. Load effective scope grants for the user's matching role(s) and capability.
4. Apply direct-user exception only when explicit, unexpired, approved metadata exists.
5. Evaluate scope against CURRENT authorization graph.
6. Apply data-classification capability requirements.
7. Default DENY if no grant matches.
```

Do not modify `CapabilityAuthorizationAspect` to infer HR resource scope from URLs. HR v2 application services call `ScopedAuthorizationService.require()` with explicit canonical resource context after the coarse annotation gate.

- [ ] **Step 4: Implement recursive reporting and Org Unit checks with cycle-safe queries**

Use recursive CTEs with visited-path guards and current effective dates. A corrupt/cyclic graph returns DENY and emits a security/audit failure rather than looping.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrScopedAuthorizationIntegrationTest,HrHistoricalAuthorizationIntegrationTest \
  test
git add apps/sanad-platform/src/main/java/com/sanad/platform/security/scope \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr/security \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/security
git commit -m "feat(security): enforce HRM resource scopes"
```

### Task 3: Add immutable local audit, HR outbox and durable idempotency schema

**Files:**
- Create: `V20260827_9__create_hr_audit_outbox_idempotency.sql`
- Create: `HrAuditOutboxAtomicityIntegrationTest.java`
- Create: `HrIdempotencyIntegrationTest.java`

**Interfaces:**
- Produces: `hr_audit_ledger`, `hr_audit_delivery`, `hr_domain_event_outbox`, `hr_idempotency_records`, `hr_iam_access_bindings`.

- [ ] **Step 1: Write failing schema/atomicity tests**

```java
@Test
void auditLedgerRejectsUpdateAndDelete() { /* direct SQL update/delete must fail */ }

@Test
void duplicateIdempotencyKeyWithDifferentFingerprintConflicts() { /* expect conflict */ }
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrAuditOutboxAtomicityIntegrationTest,HrIdempotencyIntegrationTest \
  test
```

- [ ] **Step 3: Implement append-only audit fact and separate delivery state**

```sql
CREATE TABLE hr_audit_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    actor_user_id UUID,
    action VARCHAR(150) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id UUID,
    organization_id UUID,
    legal_entity_id UUID,
    data_classification VARCHAR(40) NOT NULL,
    reason VARCHAR(500),
    before_state JSONB,
    after_state JSONB,
    result VARCHAR(20) NOT NULL,
    correlation_id UUID,
    request_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_hr_audit_result CHECK (result IN ('SUCCESS','FAILURE'))
);

CREATE TABLE hr_audit_delivery (
    audit_id UUID PRIMARY KEY REFERENCES hr_audit_ledger(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMPTZ,
    last_error_code VARCHAR(80)
);
```

Create a PostgreSQL trigger that raises on UPDATE/DELETE of `hr_audit_ledger`. Worker updates only `hr_audit_delivery`.

- [ ] **Step 4: Implement HR outbox table**

Store the shared `DomainEventEnvelope` fields plus JSONB payload, claim token/worker/expiry, attempt/max-attempt, status, availability and error code. No raw PII/secrets in payload.

- [ ] **Step 5: Implement idempotency table**

Unique key:

```sql
UNIQUE (tenant_id, principal_id, operation_code, idempotency_key)
```

Store SHA-256 request fingerprint, operation ID, response status/body, created/expires timestamps. Same key+same fingerprint replays; same key+different fingerprint conflicts.

- [ ] **Step 6: Add HR-managed IAM access binding**

`hr_iam_access_bindings` links tenant/person/user and records whether access lifecycle is explicitly `HR_MANAGED`. This prevents Employment termination from disabling unrelated IAM users.

- [ ] **Step 7: Add fail-closed RLS to all tenant-owned tables and run tests**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrAuditOutboxAtomicityIntegrationTest,HrIdempotencyIntegrationTest \
  test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260827_9__create_hr_audit_outbox_idempotency.sql \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/integration/HrAuditOutboxAtomicityIntegrationTest.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/integration/HrIdempotencyIntegrationTest.java
git commit -m "feat(hrm): add local audit outbox and idempotency stores"
```

### Task 4: Implement transactional audit and domain-event append

**Files:**
- Create: `com/sanad/platform/hr/audit/*` service/repository files except worker.
- Create: `HrDomainEventPublisher.java`, `JdbcHrOutboxRepository.java`.
- Modify: `EmploymentCommandService.java` and `HrAssignmentService.java` to use the new services in the same transaction.
- Create/modify: `HrAuditOutboxAtomicityIntegrationTest.java`

**Interfaces:**

```java
UUID appendMutationAudit(HrAuditRecord record);
void append(DomainEventEnvelope envelope);
```

- [ ] **Step 1: Write rollback tests**

```java
@Test
void mutationRollsBackWhenAuditAppendFails() { /* inject failing audit repository; state unchanged */ }

@Test
void mutationAndOutboxCommitAtomically() { /* success => state + audit + outbox; forced outbox failure => none */ }
```

- [ ] **Step 2: Run and confirm RED**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrAuditOutboxAtomicityIntegrationTest test
```

- [ ] **Step 3: Implement same-transaction append**

Lifecycle transaction sequence:

```text
validate auth/compliance/invariants
→ mutate canonical state/history
→ append hr_audit_ledger + hr_audit_delivery
→ append hr_domain_event_outbox
→ COMMIT
```

Do not use `REQUIRES_NEW` for the critical mutation audit/outbox append.

- [ ] **Step 4: Add redaction guard**

Central redaction rejects or masks known sensitive field names (`nationalId`, `passport`, `identifierCiphertext`, `bankAccount`, `password`, `token`, `secret`, encryption/blind-index keys) before JSON serialization. Tests assert raw fixtures do not appear in audit/outbox rows.

- [ ] **Step 5: Run tests and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrAuditOutboxAtomicityIntegrationTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/audit \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr/integration/HrDomainEventPublisher.java \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr/integration/JdbcHrOutboxRepository.java \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr/employment \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr/assignment \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/integration/HrAuditOutboxAtomicityIntegrationTest.java
git commit -m "feat(hrm): make mutations audit and outbox atomic"
```

### Task 5: Implement sensitive-read audit that fails closed

**Files:**
- Create: `SensitiveReadAuditService.java`
- Create: `HrSensitiveReadAuditIntegrationTest.java`
- Modify later-sensitive Person/Contract/Compensation query services to call it before returning protected data.

**Interfaces:**

```java
void recordOrThrow(HrAuthenticatedContext actor, String action,
                   String resourceType, UUID resourceId, String classification,
                   String reason);
```

- [ ] **Step 1: Write tests**

```java
@Test
void piiReadWritesAuditBeforeReturningData() { /* audit count increments */ }

@Test
void piiReadFailsIfAuditWriteFails() { /* service throws; restricted DTO not returned */ }
```

- [ ] **Step 2: Implement using local audit ledger**

Read audit records action/resource/classification/correlation only; it does not copy the sensitive value into before/after state.

- [ ] **Step 3: Run and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrSensitiveReadAuditIntegrationTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/audit/SensitiveReadAuditService.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/integration/HrSensitiveReadAuditIntegrationTest.java
git commit -m "feat(hrm): audit sensitive reads fail closed"
```

### Task 6: Implement at-least-once audit/outbox delivery workers

**Files:**
- Create: `HrAuditDeliveryWorker.java`
- Create: `HrOutboxWorker.java`
- Create: `HrOutboxDeliveryIntegrationTest.java`

**Interfaces:**
- Audit worker consumes local delivery rows and calls `PlatformAuditSink`.
- Outbox worker claims HR events and dispatches to registered consumers.

- [ ] **Step 1: Write claim/retry/DLQ/idempotency tests**

Test two workers racing for the same row; only one owns a valid claim token. Test retryable failure increments attempt/availability; exhausted attempts become DEAD/DEAD_LETTER. Test completed event is not re-dispatched.

- [ ] **Step 2: Implement short claim transaction → no-DB external dispatch → finalize transaction**

Follow the proven pattern conceptually used by the CRM worker but use HR-owned repositories and generic event contracts. Never hold a DB transaction open during an external HTTP/service call.

- [ ] **Step 3: Run and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrOutboxDeliveryIntegrationTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/audit/HrAuditDeliveryWorker.java \
  apps/sanad-platform/src/main/java/com/sanad/platform/hr/integration/HrOutboxWorker.java \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/integration/HrOutboxDeliveryIntegrationTest.java
git commit -m "feat(hrm): deliver audit and events reliably"
```

### Task 7: Implement employment-derived IAM policy consumer safely

**Files:**
- Create: `IamEmploymentAccessPort.java`
- Create: `UserServiceIamEmploymentAccessAdapter.java`
- Create: `HrmIamAccessPolicy.java`
- Create: `HrmIamEventConsumer.java`
- Create: `HrIamPolicyConsumerIntegrationTest.java`

**Interfaces:**
- Consumes: `HRM.EMPLOYEE.ACTIVATED.v1`, `HRM.EMPLOYEE.SUSPENDED.v1`, `HRM.EMPLOYEE.TERMINATED.v1`, `HRM.EMPLOYEE.USER_LINKED.v1`.
- Calls existing `UserService` only through `IamEmploymentAccessPort`.

- [ ] **Step 1: Write policy tests**

```java
@Test
void terminationDoesNotSuspendUnmanagedUser() { /* HR_MANAGED=false => no UserService status change */ }

@Test
void terminationSuspendsManagedAccessOnlyWhenNoOtherActiveManagedEmploymentNeedsIt() { /* deterministic */ }
```

- [ ] **Step 2: Implement idempotent consumer**

Store consumed event IDs or a consumer idempotency record so duplicate at-least-once delivery has no duplicate side effect.

- [ ] **Step 3: Implement policy boundary**

Only `hr_iam_access_bindings.access_mode='HR_MANAGED'` permits employment lifecycle to change IAM account status. If the linked User has another active HR-managed Employment requiring access, do not suspend/deactivate the User.

- [ ] **Step 4: Run and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrIamPolicyConsumerIntegrationTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/integration \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/integration/HrIamPolicyConsumerIntegrationTest.java
git commit -m "feat(hrm): integrate employment access with IAM policy"
```

### Task 8: Implement durable HR idempotency service

**Files:**
- Create: `JdbcHrRequestIdempotencyService.java`
- Modify: `HrIdempotencyIntegrationTest.java`

**Interfaces:**
- Implements shared `RequestIdempotencyService` for HR command endpoints in WS5.

- [ ] **Step 1: Test replay/conflict/in-flight/expiry behavior**

```text
same key + same fingerprint + completed → replay stored response
same key + different fingerprint         → HRM_IDEMPOTENCY_CONFLICT
same key + in-flight                     → conflict/retry-later
expired completed record                 → new operation allowed
```

- [ ] **Step 2: Implement DB-backed service with SHA-256 request fingerprint supplied by caller**

Use a transaction and unique constraint to make concurrent `begin()` race-safe. Do not catch and ignore a unique violation; re-read the existing record and apply the same replay/conflict rules.

- [ ] **Step 3: Run and commit**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrIdempotencyIntegrationTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/hr/idempotency \
  apps/sanad-platform/src/test/java/com/sanad/platform/hr/integration/HrIdempotencyIntegrationTest.java
git commit -m "feat(hrm): add durable command idempotency"
```

### Task 9: Enforce module boundaries with ArchUnit

**Files:**
- Create: `HrModuleBoundaryArchitectureTest.java`

- [ ] **Step 1: Encode forbidden dependencies**

```java
noClasses().that().resideInAPackage("..hr..")
    .should().dependOnClassesThat().resideInAnyPackage(
        "..crm.idempotency..",
        "..crm.integration..",
        "..accounting.infrastructure..",
        "..erp.infrastructure..")
    .check(importedClasses);
```

Allow explicit Platform contracts/ports only.

- [ ] **Step 2: Run architecture test**

```bash
mvn -f apps/sanad-platform/pom.xml -Dtest=HrModuleBoundaryArchitectureTest test
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add apps/sanad-platform/src/test/java/com/sanad/platform/hr/integration/HrModuleBoundaryArchitectureTest.java
git commit -m "test(hrm): enforce module integration boundaries"
```

### Task 10: WS4 verification gate

**Files:**
- Create: `docs/hrm/g0/evidence/04-security-integration.md`

- [ ] **Step 1: Run complete WS4 suite**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrScopedAuthorizationIntegrationTest,HrHistoricalAuthorizationIntegrationTest,HrAuditOutboxAtomicityIntegrationTest,HrSensitiveReadAuditIntegrationTest,HrOutboxDeliveryIntegrationTest,HrIamPolicyConsumerIntegrationTest,HrIdempotencyIntegrationTest,HrModuleBoundaryArchitectureTest \
  test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Verify exact HR_MANAGER role matrix has not changed**

Run the existing role-template contract/migration verification and assert the canonical HR_MANAGER still has exactly:

```text
HR.EMPLOYEE.READ
HR.EMPLOYEE.WRITE
HR.EMPLOYEE.ARCHIVE
```

No `HRM.*` capability is bound to HR_MANAGER in WS4.

- [ ] **Step 3: Verify audit/outbox contain no raw sensitive fixture values**

Use SQL assertions from the focused tests plus a manual fixture scan. If any raw National ID/passport/token/key appears in JSON payloads, fail the gate.

- [ ] **Step 4: Record evidence and commit**

```bash
git add docs/hrm/g0/evidence/04-security-integration.md
git commit -m "docs(hrm): record security integration evidence"
```

Expected verdict: `WS4_SECURITY_INTEGRATION=PASS` only after scope escalation is denied, sensitive reads audit fail-closed, mutation audit/outbox atomicity passes, workers retry safely, and IAM policy does not disable unmanaged users.
