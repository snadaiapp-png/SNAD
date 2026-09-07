# SNAD Workflow Orchestration Platform Y2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evolve the existing SNAD Workflow Engine into the approved Y2 Workflow Orchestration Platform without breaking existing `/api/v1/workflows` clients, historical audit, running legacy instances, tenant isolation, or PostgreSQL Direct release gates.

**Architecture:** Extend the existing `com.sanad.platform.workflow` domain/application/infrastructure pattern and JDBC repositories. Introduce additive Y2 tables and columns, route every instance to exactly one engine generation (`LEGACY` or `Y2`), use `Employee.id` as the concrete human assignee while authenticating/authorizing through the linked active `User`, and keep source-module business records outside Workflow ownership. Published workflow versions are immutable, Y2 execution is graph-driven, only `HUMAN_TASK` and `APPROVAL` create central WorkItems, and distributed delivery uses transactional outbox + idempotent inbox semantics.

**Tech Stack:** Java 17, Spring Boot 3.5.6, Spring Security/AOP `@RequireCapability`, JDBC repositories, PostgreSQL + Flyway, PostgreSQL Direct test path, Next.js 16, React 19, TypeScript 5.9, Vitest 4, Playwright 1.61.

**Spec:** `docs/superpowers/specs/2026-08-30-snad-workflow-orchestration-design.md`

**Execution Branch:** `design/workflow-orchestration-spec`

**Starting Commit:** `7aa2c201e1e7971e83e433a6f189a3874de5a52b`

## Global Constraints

- PostgreSQL remains the authoritative persisted workflow state.
- Database migrations are forward-only and additive-first; never rewrite historical Flyway migrations.
- PostgreSQL Direct remains the governing database test path; do not add Docker/Testcontainers requirements.
- Every tenant-owned Y2 row is tenant-scoped and cross-tenant references fail closed.
- `Employee.id` is the concrete human assignee; `User.id` is the authentication identity.
- Assignment never grants authorization; every human command revalidates current server-side capability and actionability.
- A hard-disabled linked User makes existing work `ASSIGNEE_UNAVAILABLE`; it does not auto-reassign to a manager.
- Only `HUMAN_TASK` and `APPROVAL` create central WorkItems.
- Approval V1 supports `ANY_ONE` and `ALL`; QUORUM/N_OF_M is not implemented in this plan.
- Self-approval is denied by default; an override requires `WORKFLOW.SELF_APPROVAL_OVERRIDE` and an explicit published step policy.
- Published workflow versions are immutable and running instances remain pinned to the concrete version they started with.
- Conditions use a bounded declarative AST; do not execute arbitrary JavaScript, SQL, shell, reflection, or `eval`.
- Distributed delivery is at-least-once + idempotency; never claim exactly-once delivery.
- Notification-provider failure never rolls back a committed workflow transition.
- Existing `/api/v1/workflows` JSON fields remain backward-compatible during cutover.
- No instance may execute on both LEGACY and Y2 engines.
- Existing Workflow test families remain green and are extended rather than replaced.

---

## File Structure and Ownership Map

### Database migrations

- `apps/sanad-platform/src/main/resources/db/migration/V20260830_1__workflow_y2_identity_and_capabilities.sql` — employee/user uniqueness + fine-grained Workflow capabilities and compatibility grants.
- `apps/sanad-platform/src/main/resources/db/migration/V20260830_2__workflow_y2_definition_graph.sql` — definition family/publication metadata, transitions, Y2 step types, checksums.
- `apps/sanad-platform/src/main/resources/db/migration/V20260830_3__workflow_y2_work_items_approvals.sql` — WorkItems, candidates, approval-policy state, reassignment/delegation audit fields.
- `apps/sanad-platform/src/main/resources/db/migration/V20260830_4__workflow_y2_runtime_context.sql` — Y2 instance/runtime metadata, typed context, branch tokens, sub-workflow links.
- `apps/sanad-platform/src/main/resources/db/migration/V20260830_5__workflow_y2_sla_incidents_execution.sql` — business calendars, execution attempts, incidents, compensation metadata.
- `apps/sanad-platform/src/main/resources/db/migration/V20260830_6__workflow_y2_events_notifications.sql` — Workflow inbox/outbox and notification intent/delivery records where platform-shared extraction is not available.

### Backend domain and application

- Existing domain types remain in `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/`.
- New focused domain types: `WorkflowTransition`, `WorkflowWorkItem`, `WorkflowWorkItemCandidate`, `WorkflowApprovalPolicy`, `WorkflowDefinitionValidation`, `WorkflowExpression`, `WorkflowIncident`, `WorkflowExecutionAttempt`, `WorkflowDelegation`, `WorkflowBusinessCalendar`, `WorkflowEventEnvelope`.
- New application services: `WorkflowDefinitionValidator`, `WorkflowAssignmentResolver`, `WorkflowActionabilityService`, `WorkflowWorkItemService`, `WorkflowApprovalPolicyEngine`, `WorkflowGraphExecutionService`, `WorkflowExpressionEvaluator`, `WorkflowBusinessTimeService`, `WorkflowDelegationService`, `WorkflowSystemActionService`, `WorkflowIncidentService`, `WorkflowTriggerService`, `WorkflowEventDeliveryService`.
- `WorkflowExecutionService` becomes the compatibility facade/router; legacy linear behavior remains isolated and Y2 graph execution is delegated to `WorkflowGraphExecutionService`.
- `WorkflowController` keeps backward-compatible v1 routes and delegates new Y2 commands to focused services.

### Backend infrastructure

- Extend `JdbcWorkflowDefinitionRepository` for definition family/publication and transitions.
- Extend `JdbcWorkflowInstanceRepository` and `JdbcWorkflowStepInstanceRepository` for Y2 runtime state.
- Add JDBC adapters for WorkItems, delegations, calendars, execution attempts, incidents, inbox/outbox, and operational query/read models.
- Extend `HrEmployeeRepository` / `JdbcHrEmployeeRepository` with user-link lookup needed by the actionability boundary.

### Web

- Keep `apps/web/lib/api/workflow-api.ts` as the typed Workflow API boundary; add Y2 DTOs and methods without duplicating backend rules.
- Split `apps/web/app/workflow/page.tsx` into an operational shell and focused feature components under `apps/web/app/workflow/components/`.
- Add `apps/web/app/workflow/definitions/[id]/page.tsx` for the definition/version designer.
- Add focused views for Overview, My Tasks, Approvals, Instances, Incidents, Monitoring, and Settings.

---

# Wave 0 — Safety Baseline, Identity, and Authorization

### Task 1: Pin the implementation baseline and regression gate

**Files:**
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowY2BaselineContractTest.java`
- Create: `apps/web/app/workflow/__tests__/workflow-y2-baseline.test.tsx`

**Interfaces:**
- Consumes: current `/api/v1/workflows` contract and the existing four-tab web surface.
- Produces: executable assertions that protect current API fields and the legacy route while Y2 is built additively.

- [ ] **Step 1: Write the backend failing baseline test**

```java
package com.sanad.platform.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import com.sanad.platform.workflow.api.WorkflowController;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowY2BaselineContractTest {
    @Test
    void workflowControllerKeepsV1BasePathDuringY2Cutover() {
        var mapping = WorkflowController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/api/v1/workflows");
    }
}
```

- [ ] **Step 2: Run backend baseline test**

Run:

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowY2BaselineContractTest test
```

Expected: PASS. This is a baseline pin, not a red test.

- [ ] **Step 3: Write the web baseline contract test**

```tsx
import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../page.tsx", import.meta.url), "utf8");

describe("workflow Y2 migration baseline", () => {
  it("keeps the canonical workflow page while the new IA is introduced additively", () => {
    expect(source).toContain('type Tab = "definitions" | "instances" | "approvals" | "monitoring"');
  });
});
```

- [ ] **Step 4: Run the web baseline test**

Run:

```bash
cd apps/web
npm test -- app/workflow/__tests__/workflow-y2-baseline.test.tsx
```

Expected: PASS.

- [ ] **Step 5: Commit the safety baseline**

```bash
git add apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowY2BaselineContractTest.java apps/web/app/workflow/__tests__/workflow-y2-baseline.test.tsx
git commit -m "test(workflow): pin Y2 compatibility baseline"
```

### Task 2: Enforce the existing Employee↔User link as the canonical workflow identity bridge

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260830_1__workflow_y2_identity_and_capabilities.sql`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/domain/HrEmployeeRepository.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/infrastructure/JdbcHrEmployeeRepository.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowActionabilityService.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowEmployeeIdentityIntegrationTest.java`
- Modify: `apps/web/lib/api/hr-api.ts`

**Interfaces:**
- Produces: `Optional<HrEmployee> findByUserId(UUID tenantId, UUID userId)` and `WorkflowActionabilityService.requireActionableEmployee(UUID tenantId, UUID userId)`.
- Later tasks use the returned `HrEmployee.id()` as the concrete assignee/actor employee identity.

- [ ] **Step 1: Write the PostgreSQL Direct identity test**

```java
@Test
void oneUserCannotLinkToTwoEmployeesInSameTenant() {
    jdbc.update("insert into hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name, display_name, employment_type, status) values (?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID(), TENANT, USER, "E-100", "A", "One", "A One", "FULL_TIME", "ACTIVE");
    assertThatThrownBy(() -> jdbc.update("insert into hr_employees (id, tenant_id, user_id, employee_number, first_name, last_name, display_name, employment_type, status) values (?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID(), TENANT, USER, "E-101", "B", "Two", "B Two", "FULL_TIME", "ACTIVE"))
            .isInstanceOf(DataIntegrityViolationException.class);
}
```

- [ ] **Step 2: Run the test and verify RED**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowEmployeeIdentityIntegrationTest test
```

Expected: FAIL because no `(tenant_id,user_id)` partial uniqueness exists.

- [ ] **Step 3: Add the additive identity migration section**

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_employees_tenant_user
    ON hr_employees (tenant_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_hr_employees_tenant_user
    ON hr_employees (tenant_id, user_id)
    WHERE user_id IS NOT NULL;
```

The same migration file will receive capability inserts in Task 3; do not create a second `_1` migration.

- [ ] **Step 4: Add repository lookup**

```java
Optional<HrEmployee> findByUserId(UUID tenantId, UUID userId);
```

Implement in `JdbcHrEmployeeRepository` with a tenant-scoped query:

```java
return jdbc.query("""
    SELECT * FROM hr_employees
    WHERE tenant_id = ? AND user_id = ?
    """, rowMapper, tenantId, userId).stream().findFirst();
```

- [ ] **Step 5: Add actionability service**

```java
@Service
public final class WorkflowActionabilityService {
    private final HrEmployeeRepository employees;
    private final UserRepository users;

    public HrEmployee requireActionableEmployee(UUID tenantId, UUID userId) {
        var employee = employees.findByUserId(tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Authenticated user is not linked to an employee"));
        var user = users.findById(tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "User is not actionable"));
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not actionable");
        }
        return employee;
    }
}
```

When implementing, use the exact existing `UserRepository` method/status accessor found in `com.sanad.platform.user`; do not invent a second user repository or status model.

- [ ] **Step 6: Expose the existing user link in the authorized HR web DTO**

Add to `HrEmployeeResponse` in `apps/web/lib/api/hr-api.ts`:

```ts
userId: string | null;
```

No fuzzy email/name linking code is permitted.

- [ ] **Step 7: Run identity tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowEmployeeIdentityIntegrationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260830_1__workflow_y2_identity_and_capabilities.sql apps/sanad-platform/src/main/java/com/sanad/platform/hr apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowActionabilityService.java apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowEmployeeIdentityIntegrationTest.java apps/web/lib/api/hr-api.ts
git commit -m "feat(workflow): enforce employee identity bridge"
```

### Task 3: Expand Workflow capabilities with compatibility mapping

**Files:**
- Modify: `apps/sanad-platform/src/main/resources/db/migration/V20260830_1__workflow_y2_identity_and_capabilities.sql`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowY2CapabilityMigrationTest.java`

**Interfaces:**
- Produces capability codes consumed by controller/service authorization in all later tasks.

- [ ] **Step 1: Write the migration test**

```java
@Test
void y2CapabilitiesExistAfterFlywayMigration() {
    var codes = jdbc.queryForList("select code from access_capabilities where code like 'WORKFLOW.%'", String.class);
    assertThat(codes).contains(
            "WORKFLOW.DESIGN", "WORKFLOW.VALIDATE", "WORKFLOW.PUBLISH",
            "WORKFLOW.START", "WORKFLOW.TASK_EXECUTE", "WORKFLOW.REASSIGN",
            "WORKFLOW.DELEGATE", "WORKFLOW.CANCEL", "WORKFLOW.INCIDENT_MANAGE",
            "WORKFLOW.MONITOR", "WORKFLOW.AUDIT_VIEW", "WORKFLOW.BREAK_GLASS",
            "WORKFLOW.SELF_APPROVAL_OVERRIDE");
}
```

- [ ] **Step 2: Run RED**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowY2CapabilityMigrationTest test
```

Expected: FAIL because these codes do not exist.

- [ ] **Step 3: Append capability seed SQL to the existing `_1` migration**

```sql
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, description, 'ACTIVE', NOW(), NOW()
FROM (VALUES
 ('WORKFLOW.DESIGN','Workflow Design','Create and edit draft workflow definitions'),
 ('WORKFLOW.VALIDATE','Workflow Validate','Validate and simulate workflow drafts'),
 ('WORKFLOW.PUBLISH','Workflow Publish','Publish immutable workflow versions'),
 ('WORKFLOW.START','Workflow Start','Start workflow instances'),
 ('WORKFLOW.TASK_EXECUTE','Workflow Task Execute','Claim and complete workflow tasks'),
 ('WORKFLOW.REASSIGN','Workflow Reassign','Reassign workflow work items'),
 ('WORKFLOW.DELEGATE','Workflow Delegate','Manage workflow delegation'),
 ('WORKFLOW.CANCEL','Workflow Cancel','Cancel workflow instances'),
 ('WORKFLOW.INCIDENT_MANAGE','Workflow Incident Manage','Acknowledge and resolve workflow incidents'),
 ('WORKFLOW.MONITOR','Workflow Monitor','View operational workflow monitoring'),
 ('WORKFLOW.AUDIT_VIEW','Workflow Audit View','Read workflow business audit'),
 ('WORKFLOW.BREAK_GLASS','Workflow Break Glass','Execute audited emergency workflow overrides'),
 ('WORKFLOW.SELF_APPROVAL_OVERRIDE','Workflow Self Approval Override','Permit explicitly configured exceptional self approval')
) AS c(code,name,description)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities a WHERE a.code = c.code);
```

Add explicit ADMIN compatibility grants using the same tenant/role join pattern as `V20260815_11__add_workflow_capabilities.sql`. Do not delete `WORKFLOW.WRITE` or `WORKFLOW.ADMIN`.

- [ ] **Step 4: Run PASS**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowY2CapabilityMigrationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260830_1__workflow_y2_identity_and_capabilities.sql apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowY2CapabilityMigrationTest.java
git commit -m "feat(workflow): add Y2 capabilities"
```

---

# Wave 1 — Immutable Definitions, Graph, WorkItems, and Approvals

### Task 4: Add immutable definition-family and publication metadata

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260830_2__workflow_y2_definition_graph.sql`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowDefinition.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowDefinitionRepository.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/infrastructure/JdbcWorkflowDefinitionRepository.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowDefinitionService.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowDefinitionVersioningTest.java`

**Interfaces:**
- Produces: `definitionFamilyId`, `engineGeneration`, `publicationState`, `definitionChecksum`, `publishedAt`, `publishedBy`, `schemaVersion`.
- Produces service operations `createNextDraft(...)` and `publish(...)` used by the designer/API.

- [ ] **Step 1: Write RED versioning tests**

```java
@Test
void publishedDefinitionCannotBeMutatedInPlace() {
    var published = fixturePublishedDefinition();
    assertThatThrownBy(() -> published.rename("mutated"))
            .isInstanceOf(IllegalStateException.class);
}

@Test
void nextDraftKeepsFamilyAndIncrementsVersion() {
    var published = fixturePublishedDefinition();
    var draft = published.nextDraft(UUID.randomUUID());
    assertThat(draft.definitionFamilyId()).isEqualTo(published.definitionFamilyId());
    assertThat(draft.version()).isEqualTo(published.version() + 1);
    assertThat(draft.publicationState()).isEqualTo(WorkflowDefinition.PublicationState.DRAFT);
}
```

- [ ] **Step 2: Run RED**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowDefinitionVersioningTest test
```

- [ ] **Step 3: Add additive definition metadata**

```sql
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS definition_family_id UUID;
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS engine_generation VARCHAR(10) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS publication_state VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS published_by UUID;
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS validated_at TIMESTAMPTZ;
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS definition_checksum VARCHAR(128);
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS schema_version INTEGER NOT NULL DEFAULT 1;

UPDATE workflow_definitions SET definition_family_id = id WHERE definition_family_id IS NULL;
ALTER TABLE workflow_definitions ALTER COLUMN definition_family_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_wf_def_family_version ON workflow_definitions(tenant_id, definition_family_id, version DESC);
```

Add CHECK constraints for `engine_generation IN ('LEGACY','Y2')` and `publication_state IN ('DRAFT','PUBLISHED','RETIRED')` using the repository's existing safe migration convention.

- [ ] **Step 4: Extend the domain record and repository**

Add exact enums:

```java
public enum EngineGeneration { LEGACY, Y2 }
public enum PublicationState { DRAFT, PUBLISHED, RETIRED }
```

Add repository methods:

```java
List<WorkflowDefinition> findVersions(UUID tenantId, UUID definitionFamilyId);
Optional<WorkflowDefinition> findPublishedByFamily(UUID tenantId, UUID definitionFamilyId);
```

- [ ] **Step 5: Implement immutable publication methods**

```java
public WorkflowDefinition publish(UUID actorUserId, String checksum) {
    if (publicationState != PublicationState.DRAFT) {
        throw new IllegalStateException("Only DRAFT definitions can be published");
    }
    var now = Instant.now();
    return new WorkflowDefinition(id, tenantId, definitionFamilyId, code, name, description,
            module, version, status, triggerType, createdBy, versionLock + 1,
            EngineGeneration.Y2, PublicationState.PUBLISHED, actorUserId, now,
            now, checksum, schemaVersion, createdAt, now);
}
```

- [ ] **Step 6: Run tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowDefinitionVersioningTest,WorkflowApiContractTest test
```

Expected: PASS and legacy API contract remains green.

- [ ] **Step 7: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260830_2__workflow_y2_definition_graph.sql apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowDefinitionVersioningTest.java
git commit -m "feat(workflow): add immutable definition versions"
```

### Task 5: Add explicit graph transitions and Y2 step types

**Files:**
- Modify: `apps/sanad-platform/src/main/resources/db/migration/V20260830_2__workflow_y2_definition_graph.sql`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowStep.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowTransition.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowDefinitionRepository.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/infrastructure/JdbcWorkflowDefinitionRepository.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowGraphPersistenceTest.java`

**Interfaces:**
- Produces `WorkflowTransition` and repository methods `findTransitions(definitionId)` and `saveTransition(...)`.

- [ ] **Step 1: Write RED persistence test**

```java
@Test
void transitionBelongsToOneConcreteDefinitionVersion() {
    var transition = WorkflowTransition.create(TENANT, DEF, FROM_STEP, TO_STEP,
            "approve", "APPROVE", null, 10, "{}");
    repository.saveTransition(transition);
    assertThat(repository.findTransitions(DEF)).extracting(WorkflowTransition::id)
            .contains(transition.id());
}
```

- [ ] **Step 2: Run RED**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowGraphPersistenceTest test
```

- [ ] **Step 3: Add transition schema**

```sql
CREATE TABLE IF NOT EXISTS workflow_step_transitions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    workflow_definition_id UUID NOT NULL REFERENCES workflow_definitions(id) ON DELETE CASCADE,
    from_step_id UUID NOT NULL REFERENCES workflow_steps(id) ON DELETE CASCADE,
    to_step_id UUID NOT NULL REFERENCES workflow_steps(id),
    transition_key VARCHAR(100) NOT NULL,
    outcome VARCHAR(50),
    condition_ast JSONB,
    priority INTEGER NOT NULL DEFAULT 0,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (workflow_definition_id, transition_key)
);
```

Enable RLS with the same strict tenant policy used by existing Workflow tables.

- [ ] **Step 4: Extend step types**

```java
public enum StepType {
    ACTION,
    APPROVAL,
    CONDITION,
    NOTIFICATION,
    END,
    START,
    HUMAN_TASK,
    SYSTEM_ACTION,
    PARALLEL_FORK,
    PARALLEL_JOIN,
    CALL_WORKFLOW
}
```

Keep `ACTION` for legacy compatibility.

- [ ] **Step 5: Implement transition record**

```java
public record WorkflowTransition(
        UUID id, UUID tenantId, UUID workflowDefinitionId,
        UUID fromStepId, UUID toStepId, String transitionKey,
        String outcome, String conditionAst, int priority, String metadata,
        Instant createdAt, Instant updatedAt) {
    public static WorkflowTransition create(UUID tenantId, UUID definitionId,
            UUID fromStepId, UUID toStepId, String key, String outcome,
            String conditionAst, int priority, String metadata) {
        var now = Instant.now();
        return new WorkflowTransition(UUID.randomUUID(), tenantId, definitionId,
                fromStepId, toStepId, key, outcome, conditionAst, priority,
                metadata == null ? "{}" : metadata, now, now);
    }
}
```

- [ ] **Step 6: Run tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowGraphPersistenceTest,WorkflowArchitectureTest test
```

- [ ] **Step 7: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260830_2__workflow_y2_definition_graph.sql apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain apps/sanad-platform/src/main/java/com/sanad/platform/workflow/infrastructure/JdbcWorkflowDefinitionRepository.java apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowGraphPersistenceTest.java
git commit -m "feat(workflow): persist Y2 graph transitions"
```

### Task 6: Build the publish validator and side-effect-free simulation boundary

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowDefinitionValidation.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowDefinitionValidator.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowSimulationService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowDefinitionService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/api/WorkflowController.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowDefinitionValidatorTest.java`

**Interfaces:**
- Produces `WorkflowDefinitionValidation validate(UUID tenantId, UUID definitionId)`.
- Produces `SimulationResult simulate(UUID tenantId, UUID definitionId, Map<String,Object> context)` with stubbed external effects.

- [ ] **Step 1: Write RED validator tests**

```java
@Test
void publishRejectsGraphWithoutExactlyOneStart() {
    var result = validator.validate(TENANT, definitionWithoutStart());
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
            .contains("START_COUNT_INVALID");
}

@Test
void publishRejectsApprovalWithoutApproveAndRejectTransitions() {
    var result = validator.validate(TENANT, approvalMissingRejectEdge());
    assertThat(result.errors()).extracting(WorkflowDefinitionValidation.Error::code)
            .contains("APPROVAL_OUTCOME_MISSING");
}
```

- [ ] **Step 2: Run RED**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowDefinitionValidatorTest test
```

- [ ] **Step 3: Implement deterministic validation result**

```java
public record WorkflowDefinitionValidation(boolean valid, List<Error> errors) {
    public record Error(String code, String message, UUID stepId) {}
    public static WorkflowDefinitionValidation of(List<Error> errors) {
        return new WorkflowDefinitionValidation(errors.isEmpty(), List.copyOf(errors));
    }
}
```

Validator must implement the publish checks from spec section 18: start/end/reachability, transition ownership, approval outcomes, fork/join structure, mapping/expression validation hooks, assignment config, sub-workflow references/cycles/depth, trigger config, SLA/calendar references, SoD policy, idempotency for side-effecting actions, compensation requirements, and forbidden executable/secrets fields.

- [ ] **Step 4: Add typed validate/simulate endpoints**

```java
@PostMapping("/definitions/{id}/validate")
@RequireCapability("WORKFLOW.VALIDATE")
public WorkflowDefinitionValidationResponse validate(Authentication auth, @PathVariable UUID id) {
    return WorkflowDefinitionValidationResponse.from(
            validator.validate(tenantId(auth), id));
}
```

Simulation adapters must return stub results and must not call real module mutation ports, SMTP, webhook delivery, payment, order, invoice, or user-management commands.

- [ ] **Step 5: Make publish call validator before state transition**

```java
var validation = validator.validate(tenantId, id);
if (!validation.valid()) {
    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "Workflow definition validation failed");
}
return defRepo.save(def.publish(actorUserId, checksumService.checksum(id)));
```

- [ ] **Step 6: Run tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowDefinitionValidatorTest,WorkflowApiContractTest test
```

- [ ] **Step 7: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowDefinitionValidatorTest.java
git commit -m "feat(workflow): validate and simulate definitions"
```

### Task 7: Introduce central WorkItems and atomic work pools

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260830_3__workflow_y2_work_items_approvals.sql`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowWorkItem.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowWorkItemCandidate.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowWorkItemRepository.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/infrastructure/JdbcWorkflowWorkItemRepository.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowWorkItemService.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowWorkItemConcurrencyTest.java`

**Interfaces:**
- Produces `claim(tenantId, workItemId, employeeId, expectedVersion)`, `release(...)`, `complete(...)`, `reassign(...)`, `findMyWork(...)`.
- Later approval task reuses the same WorkItem parent for approval UX.

- [ ] **Step 1: Write RED atomic-claim test**

```java
@Test
void onlyOneCandidateCanClaimTheSameVersion() {
    var item = fixturePoolItem();
    var first = service.claim(TENANT, item.id(), EMPLOYEE_A, item.version());
    assertThat(first.claimedByEmployeeId()).isEqualTo(EMPLOYEE_A);
    assertThatThrownBy(() -> service.claim(TENANT, item.id(), EMPLOYEE_B, item.version()))
            .isInstanceOf(WorkflowVersionConflictException.class);
}
```

- [ ] **Step 2: Run RED**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowWorkItemConcurrencyTest test
```

- [ ] **Step 3: Add WorkItem tables**

```sql
CREATE TABLE workflow_work_items (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 workflow_instance_id UUID NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
 workflow_step_instance_id UUID NOT NULL REFERENCES workflow_step_instances(id) ON DELETE CASCADE,
 type VARCHAR(20) NOT NULL,
 status VARCHAR(30) NOT NULL,
 assignee_employee_id UUID,
 claimed_by_employee_id UUID,
 assignment_mode VARCHAR(20) NOT NULL,
 source_module VARCHAR(50) NOT NULL,
 source_entity_type VARCHAR(100) NOT NULL,
 source_entity_id UUID NOT NULL,
 title VARCHAR(300) NOT NULL,
 description TEXT,
 priority INTEGER NOT NULL DEFAULT 0,
 due_at TIMESTAMPTZ,
 sla_due_at TIMESTAMPTZ,
 claimed_at TIMESTAMPTZ,
 completed_at TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE workflow_work_item_candidates (
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 work_item_id UUID NOT NULL REFERENCES workflow_work_items(id) ON DELETE CASCADE,
 employee_id UUID NOT NULL,
 resolution_source VARCHAR(50) NOT NULL,
 resolved_at TIMESTAMPTZ NOT NULL,
 snapshot_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 PRIMARY KEY (work_item_id, employee_id)
);
```

Add tenant-safe employee FKs according to current HR composite-key support; where the existing HR table lacks a composite unique key, add the necessary additive unique index first rather than relying on application-only tenant checks.

- [ ] **Step 4: Implement optimistic claim SQL**

```sql
UPDATE workflow_work_items
SET status='CLAIMED', claimed_by_employee_id=?, claimed_at=NOW(),
    version=version+1, updated_at=NOW()
WHERE tenant_id=? AND id=? AND version=? AND status='AVAILABLE'
  AND EXISTS (
      SELECT 1 FROM workflow_work_item_candidates c
      WHERE c.work_item_id=workflow_work_items.id AND c.employee_id=?
  );
```

Require exactly one updated row; zero rows becomes 409 version/state conflict.

- [ ] **Step 5: Run concurrency tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowWorkItemConcurrencyTest test
```

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260830_3__workflow_y2_work_items_approvals.sql apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowWorkItemConcurrencyTest.java
git commit -m "feat(workflow): add central work items"
```

### Task 8: Implement assignment resolution and eligibility snapshots

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowAssignmentResolver.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowAssignmentRule.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/domain/HrEmployeeRepository.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/infrastructure/JdbcHrEmployeeRepository.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowAssignmentResolverTest.java`

**Interfaces:**
- Produces `ResolvedAssignment resolve(tenantId, rule, WorkflowAssignmentContext)` returning concrete Employee IDs + immutable resolution evidence.

- [ ] **Step 1: Write RED resolver tests**

```java
@Test
void managerRuleResolvesRequesterManagerAsEmployee() {
    var resolved = resolver.resolve(TENANT,
            WorkflowAssignmentRule.managerOfRequester(), contextWithRequester(REQUESTER));
    assertThat(resolved.employeeIds()).containsExactly(MANAGER_EMPLOYEE);
}

@Test
void permissionRuleNeverReturnsEmployeeWithoutActiveLinkedUser() {
    var resolved = resolver.resolve(TENANT,
            WorkflowAssignmentRule.permission("PURCHASE.APPROVE"), context());
    assertThat(resolved.employeeIds()).doesNotContain(EMPLOYEE_WITHOUT_USER);
}
```

- [ ] **Step 2: Run RED**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowAssignmentResolverTest test
```

- [ ] **Step 3: Define assignment target types**

```java
public sealed interface WorkflowAssignmentRule {
    record Employee(UUID employeeId) implements WorkflowAssignmentRule {}
    record Manager(UUID subjectEmployeeId) implements WorkflowAssignmentRule {}
    record Position(UUID positionId) implements WorkflowAssignmentRule {}
    record Department(UUID departmentId) implements WorkflowAssignmentRule {}
    record Role(String roleCode) implements WorkflowAssignmentRule {}
    record Permission(String capabilityCode) implements WorkflowAssignmentRule {}
}
```

Use a parser/DTO boundary for persisted JSON configuration; do not deserialize untrusted polymorphic Java classes directly.

- [ ] **Step 4: Add focused HR repository queries**

Examples:

```java
List<HrEmployee> findActiveByDepartment(UUID tenantId, UUID departmentId);
List<HrEmployee> findActiveByPosition(UUID tenantId, UUID positionId);
```

Role/capability resolution must query the canonical RBAC tables through a focused authorization read port; it must not infer roles from HR position names.

- [ ] **Step 5: Persist candidates when WorkItem is activated**

```java
var resolved = assignmentResolver.resolve(tenantId, rule, context);
if (resolved.employeeIds().isEmpty()) {
    throw incidentFactory.unresolvableAssignee(...);
}
workItemRepository.insertCandidates(workItem.id(), resolved.candidates());
```

- [ ] **Step 6: Run tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowAssignmentResolverTest,WorkflowSecurityNegativeTest test
```

- [ ] **Step 7: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/main/java/com/sanad/platform/hr apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowAssignmentResolverTest.java
git commit -m "feat(workflow): resolve employee assignments"
```

### Task 9: Replace ad-hoc approvals with the Y2 approval policy engine

**Files:**
- Modify: `apps/sanad-platform/src/main/resources/db/migration/V20260830_3__workflow_y2_work_items_approvals.sql`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowApprovalPolicy.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowApprovalPolicyEngine.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowApprovalService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowApprovalRequest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowApprovalPolicyEngineTest.java`

**Interfaces:**
- Produces deterministic `ApprovalResolution` for `ANY_ONE` and `ALL`.
- `WorkflowApprovalService` remains the API-compatible facade but delegates policy completion to the engine.

- [ ] **Step 1: Write RED ANY_ONE/ALL tests**

```java
@Test
void anyOneRejectKeepsStepOpenWhileAnotherCandidateCanApprove() {
    var resolution = engine.resolveAnyOne(List.of(REJECTED_A, PENDING_B));
    assertThat(resolution.stepComplete()).isFalse();
}

@Test
void anyOneFirstApprovalCompletesAndCancelsRemainingRequests() {
    var resolution = engine.resolveAnyOne(List.of(APPROVED_A, PENDING_B));
    assertThat(resolution.outcome()).isEqualTo("APPROVE");
    assertThat(resolution.requestsToCancel()).contains(PENDING_B.id());
}

@Test
void allFirstRejectionRoutesReject() {
    var resolution = engine.resolveAll(List.of(APPROVED_A, REJECTED_B, PENDING_C));
    assertThat(resolution.outcome()).isEqualTo("REJECT");
}
```

- [ ] **Step 2: Run RED**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowApprovalPolicyEngineTest test
```

- [ ] **Step 3: Add persisted policy snapshot fields**

Add to approval/work item schema:

```sql
ALTER TABLE workflow_approval_requests ADD COLUMN IF NOT EXISTS requested_from_employee_id UUID;
ALTER TABLE workflow_approval_requests ADD COLUMN IF NOT EXISTS approval_policy VARCHAR(20) NOT NULL DEFAULT 'ANY_ONE';
ALTER TABLE workflow_approval_requests ADD COLUMN IF NOT EXISTS self_approval_policy VARCHAR(20) NOT NULL DEFAULT 'DENY';
ALTER TABLE workflow_approval_requests ADD COLUMN IF NOT EXISTS policy_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb;
```

Add CHECK constraints for `ANY_ONE|ALL` and `DENY|ALLOW`.

- [ ] **Step 4: Make rejection reason mandatory**

```java
public WorkflowApprovalRequest reject(UUID rejecterId, String comments) {
    if (comments == null || comments.isBlank()) {
        throw new IllegalArgumentException("Rejection reason is required");
    }
    return resolve(rejecterId, "REJECTED", Status.REJECTED, comments);
}
```

- [ ] **Step 5: Enforce self-approval policy by requester identity, not assignee identity**

```java
if (requestedByUserId != null && actorId.equals(requestedByUserId)
        && policy.selfApproval() == SelfApproval.DENY) {
    throw new IllegalStateException("Self approval is not allowed by this workflow version");
}
```

Explicit `ALLOW` must additionally verify `WORKFLOW.SELF_APPROVAL_OVERRIDE` server-side.

- [ ] **Step 6: Run approval suites**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowApprovalPolicyEngineTest,WorkflowApprovalReferenceIntegrityTest,WorkflowEngineIntegrationTest test
```

- [ ] **Step 7: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260830_3__workflow_y2_work_items_approvals.sql apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowApprovalPolicyEngineTest.java
git commit -m "feat(workflow): add approval policy engine"
```

---

# Wave 2 — Y2 Graph Runtime, Context, SLA, Automation, and Reliability

### Task 10: Add engine-generation routing and graph execution

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260830_4__workflow_y2_runtime_context.sql`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowGraphExecutionService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowExecutionService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowInstance.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowStepInstance.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/infrastructure/JdbcWorkflowInstanceRepository.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/infrastructure/JdbcWorkflowStepInstanceRepository.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowY2GraphExecutionTest.java`

**Interfaces:**
- `WorkflowExecutionService` routes by the persisted instance generation.
- `WorkflowGraphExecutionService.advance(tenantId, instanceId, outcome, actor)` is the authoritative Y2 graph transition command.

- [ ] **Step 1: Write RED no-dual-engine test**

```java
@Test
void y2InstanceNeverUsesLegacyNextStepCommand() {
    var instance = fixtureY2Instance();
    assertThatThrownBy(() -> legacyFacade.advanceToNextStep(TENANT, instance.id(), "manual-key", USER))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Y2 graph");
}
```

- [ ] **Step 2: Add runtime metadata migration**

```sql
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS engine_generation VARCHAR(10) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS definition_family_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS definition_version_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS parent_instance_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(30);
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS trigger_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(200);
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS causation_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS context_json JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS context_schema_version INTEGER NOT NULL DEFAULT 1;
```

Add a uniqueness rule for externally retryable starts, scoped by tenant/trigger/definition as defined by the trigger service in Task 15.

- [ ] **Step 3: Implement routing guard**

```java
if (i.engineGeneration() == WorkflowDefinition.EngineGeneration.Y2) {
    throw new IllegalStateException("Y2 graph instances must advance through WorkflowGraphExecutionService");
}
```

- [ ] **Step 4: Implement graph outcome selection**

```java
var transitions = definitionRepo.findTransitions(instance.definitionVersionId()).stream()
        .filter(t -> t.fromStepId().equals(current.workflowStepId()))
        .filter(t -> outcome.equals(t.outcome()))
        .sorted(Comparator.comparingInt(WorkflowTransition::priority).reversed())
        .toList();
if (transitions.size() != 1) {
    throw incidentService.graphResolutionIncident(instance, current, outcome, transitions.size());
}
```

- [ ] **Step 5: Run tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowY2GraphExecutionTest,WorkflowEngineIntegrationTest,WorkflowIdempotencyTest test
```

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260830_4__workflow_y2_runtime_context.sql apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowY2GraphExecutionTest.java
git commit -m "feat(workflow): add Y2 graph runtime"
```

### Task 11: Implement typed workflow context and safe expression AST

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowExpression.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowExpressionEvaluator.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowContextService.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowExpressionEvaluatorTest.java`

**Interfaces:**
- Produces `boolean evaluate(WorkflowExpression expression, WorkflowContext context)`.
- Produces namespace-safe `writeStepOutput(stepKey, output)`; arbitrary cross-step overwrite is rejected.

- [ ] **Step 1: Write RED expression tests**

```java
@Test
void expressionCanCompareTypedContextWithoutExecutingCode() {
    var expr = new WorkflowExpression.And(List.of(
            new WorkflowExpression.Equals("source.amount", new DecimalValue("100.00")),
            new WorkflowExpression.In("source.currency", List.of("SAR", "USD"))));
    assertThat(evaluator.evaluate(expr, context())).isTrue();
}

@Test
void expressionDepthIsBounded() {
    assertThatThrownBy(() -> evaluator.evaluate(expressionDeeperThan(32), context()))
            .isInstanceOf(WorkflowExpressionLimitException.class);
}
```

- [ ] **Step 2: Define sealed AST**

```java
public sealed interface WorkflowExpression {
    record And(List<WorkflowExpression> items) implements WorkflowExpression {}
    record Or(List<WorkflowExpression> items) implements WorkflowExpression {}
    record Not(WorkflowExpression item) implements WorkflowExpression {}
    record Equals(String path, WorkflowValue value) implements WorkflowExpression {}
    record Compare(String path, Operator operator, WorkflowValue value) implements WorkflowExpression {}
    record In(String path, List<WorkflowValue> values) implements WorkflowExpression {}
    record Exists(String path) implements WorkflowExpression {}
}
```

The parser accepts only this normalized structure. Do not compile strings or resolve arbitrary classes/functions.

- [ ] **Step 3: Implement namespace-safe context writes**

```java
public JsonNode writeStepOutput(ObjectNode context, String stepKey, JsonNode output) {
    var stepOutputs = context.withObject("stepOutputs");
    if (stepOutputs.has(stepKey)) {
        throw new IllegalStateException("Step output namespace is immutable once committed");
    }
    stepOutputs.set(stepKey, output.deepCopy());
    return context;
}
```

- [ ] **Step 4: Run tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowExpressionEvaluatorTest,WorkflowDefinitionValidatorTest test
```

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowExpressionEvaluatorTest.java
git commit -m "feat(workflow): add typed context expressions"
```

### Task 12: Add business calendars, delegation, fallback, and escalation

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260830_5__workflow_y2_sla_incidents_execution.sql`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowBusinessCalendar.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowDelegation.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowBusinessTimeService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowDelegationService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowSlaScheduler.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowBusinessTimeTest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowDelegationPolicyTest.java`

**Interfaces:**
- Produces `Instant addBusinessDuration(calendarVersionId, Instant start, Duration duration)`.
- Produces delegation lookup that never bypasses actionability/RBAC and never auto-reassigns hard-disabled B1 work.

- [ ] **Step 1: Write RED business-time and B1 tests**

```java
@Test
void businessHoursSkipWeekendAndHoliday() {
    var due = service.addBusinessDuration(CALENDAR_VERSION,
            Instant.parse("2026-08-27T13:00:00Z"), Duration.ofHours(8));
    assertThat(due).isEqualTo(expectedAfterWeekendAndHoliday());
}

@Test
void disabledUserDoesNotTriggerManagerAutoReassignment() {
    var result = delegationService.resolveExistingUnavailableAssignment(WORK_ITEM_WITH_DISABLED_USER);
    assertThat(result.status()).isEqualTo("ASSIGNEE_UNAVAILABLE");
    assertThat(result.reassignedEmployeeId()).isNull();
}
```

- [ ] **Step 2: Add calendar/delegation schema**

Create tenant-scoped versioned calendar, working windows/holidays, and delegation tables. Persist delegation `valid_from`, `valid_until`, optional workflow family/module/category scope, delegator employee, delegate employee, and audit actor.

- [ ] **Step 3: Implement business-time traversal**

The algorithm must normalize to the calendar timezone, advance only through configured working windows, skip holidays/closures, then persist the final due time as UTC `Instant`.

- [ ] **Step 4: Update SLA scheduler**

Legacy instances keep current wall-clock `slaHours`. Y2 steps call `WorkflowBusinessTimeService` using the pinned calendar version and SLA policy snapshot.

- [ ] **Step 5: Run tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowBusinessTimeTest,WorkflowDelegationPolicyTest,WorkflowSlaSchedulerTest test
```

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260830_5__workflow_y2_sla_incidents_execution.sql apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowBusinessTimeTest.java apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowDelegationPolicyTest.java
git commit -m "feat(workflow): add business SLA and delegation"
```

### Task 13: Add durable system-action attempts, incidents, retries, and compensation

**Files:**
- Modify: `apps/sanad-platform/src/main/resources/db/migration/V20260830_5__workflow_y2_sla_incidents_execution.sql`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowExecutionAttempt.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowIncident.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowSystemActionService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowIncidentService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowCompensationService.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowSystemActionResilienceTest.java`

**Interfaces:**
- Produces durable attempt state and Incident lifecycle `OPEN -> ACKNOWLEDGED -> RESOLVED`.
- Produces idempotent adapter execution contract used by module adapters.

- [ ] **Step 1: Write RED retry classification test**

```java
@Test
void transientFailureRetriesButBusinessValidationOpensNoRetryLoop() {
    var transientResult = service.execute(stepWithRetries(), transientFailingAdapter());
    assertThat(transientResult.attemptCount()).isGreaterThan(1);

    var businessResult = service.execute(stepWithRetries(), businessValidationFailingAdapter());
    assertThat(businessResult.attemptCount()).isEqualTo(1);
    assertThat(businessResult.incident().failureCategory()).isEqualTo("BUSINESS_VALIDATION");
}
```

- [ ] **Step 2: Add execution-attempt and incident tables**

Persist attempt number, idempotency key, outcome, failure category, external reference, sanitized diagnostics, timestamps, plus incident source/severity/owner/resolution/retry linkage.

- [ ] **Step 3: Define adapter contract**

```java
public interface WorkflowSystemActionAdapter {
    String type();
    ActionResult execute(ActionRequest request);
    default Optional<ActionResult> compensate(CompensationRequest request) { return Optional.empty(); }
}
```

A request carries tenant, instance, step, idempotency key, typed input, correlation and causation IDs. It never exposes unrestricted DB/network primitives to a workflow definition.

- [ ] **Step 4: Implement cancellation state**

Y2 cancellation uses `CANCELLING` before `CANCELLED`; compensation failures create Incidents. Legacy cancellation state remains backward-compatible.

- [ ] **Step 5: Run tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowSystemActionResilienceTest,WorkflowIdempotencyTest,WorkflowSecurityNegativeTest test
```

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260830_5__workflow_y2_sla_incidents_execution.sql apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowSystemActionResilienceTest.java
git commit -m "feat(workflow): add resilient action execution"
```

### Task 14: Implement parallel forks/joins and sub-workflows

**Files:**
- Modify: `apps/sanad-platform/src/main/resources/db/migration/V20260830_4__workflow_y2_runtime_context.sql`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowGraphExecutionService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowBranchToken.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowParallelExecutionTest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowSubWorkflowTest.java`

**Interfaces:**
- Produces durable branch tokens and version-pinned child-instance references.

- [ ] **Step 1: Write RED join-race test**

```java
@Test
void allBranchesJoinAdvancesExactlyOnce() throws Exception {
    var join = fixtureJoinWithTwoBranches();
    runConcurrently(() -> completeBranch(join.branchA()), () -> completeBranch(join.branchB()));
    assertThat(audit.countTransitions(join.instanceId(), "JOIN_COMPLETE")).isEqualTo(1);
}
```

- [ ] **Step 2: Add branch token schema**

Persist `instance_id`, `fork_step_instance_id`, `branch_key`, `status`, `join_step_id`, `version`. Use conditional update/locking at join completion.

- [ ] **Step 3: Add sub-workflow parent/child resolution**

```java
var childDefinition = request.versionMode() == PINNED
        ? definitions.findById(tenantId, request.definitionVersionId()).orElseThrow()
        : definitions.findPublishedByFamily(tenantId, request.definitionFamilyId()).orElseThrow();
cycleGuard.assertAllowed(parentInstance, childDefinition);
```

Persist resolved concrete child definition ID before starting the child.

- [ ] **Step 4: Run tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowParallelExecutionTest,WorkflowSubWorkflowTest,WorkflowDefinitionValidatorTest test
```

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260830_4__workflow_y2_runtime_context.sql apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowParallelExecutionTest.java apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowSubWorkflowTest.java
git commit -m "feat(workflow): add parallel and child workflows"
```

### Task 15: Add reliable triggers, inbox/outbox, and notification intents

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260830_6__workflow_y2_events_notifications.sql`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/domain/WorkflowEventEnvelope.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowTriggerService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowEventDeliveryService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowNotificationService.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowTriggerIdempotencyTest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowNotificationFailureTest.java`

**Interfaces:**
- Event envelope fields: `eventId`, `eventType`, `tenantId`, `aggregateType`, `aggregateId`, `occurredAt`, `correlationId`, `causationId`, `schemaVersion`, `payload`.
- Duplicate delivery must return/reuse the prior start result and never create a duplicate instance.

- [ ] **Step 1: Write RED duplicate-delivery test**

```java
@Test
void duplicateDomainEventStartsOnlyOneInstance() {
    var event = fixtureEvent(EVENT_ID);
    var first = triggerService.consume(event);
    var second = triggerService.consume(event);
    assertThat(second.instanceId()).isEqualTo(first.instanceId());
    assertThat(instanceRepo.countByTrigger(TENANT, EVENT_ID)).isEqualTo(1);
}
```

- [ ] **Step 2: Inspect and reuse/generalize CRM outbox primitives before creating Workflow-owned adapters**

The implementation must first read the existing CRM collaboration event-outbox port/adapter and extract a platform-neutral envelope/claim/retry primitive if dependency boundaries remain clean. If extraction would create CRM→Workflow or Workflow→CRM domain coupling, keep a Workflow-owned adapter but use the same statuses, deterministic ordering, retry semantics, and aggregate envelope conventions.

- [ ] **Step 3: Add inbox uniqueness**

```sql
CREATE TABLE workflow_event_inbox (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenants(id),
 event_id UUID NOT NULL,
 trigger_key VARCHAR(200) NOT NULL,
 workflow_definition_id UUID NOT NULL REFERENCES workflow_definitions(id),
 workflow_instance_id UUID,
 received_at TIMESTAMPTZ NOT NULL,
 processed_at TIMESTAMPTZ,
 status VARCHAR(20) NOT NULL,
 error_code VARCHAR(100),
 UNIQUE (tenant_id, event_id, trigger_key, workflow_definition_id)
);
```

- [ ] **Step 4: Emit notification intents after committed workflow transitions**

```java
notificationPort.enqueue(new WorkflowNotificationIntent(
        tenantId, "TASK_ASSIGNED", workItem.id(), recipientUserId,
        correlationId, deduplicationKey));
```

IN_APP is the primary qualifying channel; EMAIL is optional/configurable; WEBHOOK is integration use. Delivery errors update delivery state and may open an Incident, but never roll back the workflow transaction.

- [ ] **Step 5: Run reliability tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowTriggerIdempotencyTest,WorkflowNotificationFailureTest,WorkflowIdempotencyTest test
```

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260830_6__workflow_y2_events_notifications.sql apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowTriggerIdempotencyTest.java apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowNotificationFailureTest.java
git commit -m "feat(workflow): add reliable triggers and notifications"
```

---

# Wave 3 — Typed API, Operational UI, Designer, and Read Models

### Task 16: Add typed Y2 API DTOs while preserving v1 JSON compatibility

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/api/WorkflowController.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/api/WorkflowDtos.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowApiContractTest.java`
- Modify: `apps/web/lib/api/workflow-api.ts`

**Interfaces:**
- Existing response JSON fields remain unchanged.
- New commands include `expectedVersion` for claim, complete, approve, reject, reassign, publish, and incident resolution.

- [ ] **Step 1: Extend API contract tests before controller refactor**

```java
@Test
void approvalResponseKeepsLegacyFieldsAndAddsEmployeeIdentity() throws Exception {
    mockMvc.perform(get("/api/v1/workflows/approvals").with(auth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].requestedFromUserId").exists())
            .andExpect(jsonPath("$[0].requestedFromEmployeeId").exists());
}
```

- [ ] **Step 2: Introduce typed records**

```java
public record WorkItemCommandRequest(long expectedVersion, String reason) {}
public record ApprovalDecisionRequest(long expectedVersion, String comments) {}
public record PublishDefinitionRequest(long expectedVersion) {}
```

Use typed response records with Jackson field names matching current v1 response keys exactly.

- [ ] **Step 3: Add endpoints**

Required additive routes under `/api/v1/workflows`:

```text
GET  /work-items/mine
GET  /work-items/pool
POST /work-items/{id}/claim
POST /work-items/{id}/release
POST /work-items/{id}/complete
POST /work-items/{id}/reassign
POST /definitions/{id}/validate
POST /definitions/{id}/simulate
POST /definitions/{id}/publish
POST /definitions/{id}/next-draft
GET  /definitions/{id}/steps
GET  /definitions/{id}/transitions
POST /definitions/{id}/transitions
GET  /incidents
POST /incidents/{id}/acknowledge
POST /incidents/{id}/resolve
```

- [ ] **Step 4: Extend TypeScript client**

```ts
export interface WorkflowWorkItemResponse {
  id: string;
  type: "HUMAN_TASK" | "APPROVAL";
  status: string;
  assigneeEmployeeId: string | null;
  claimedByEmployeeId: string | null;
  title: string;
  dueAt: string | null;
  version: number;
}

claimWorkItem: (id: string, expectedVersion: number) =>
  apiClient.post<WorkflowWorkItemResponse>(`${BASE}/work-items/${id}/claim`, { expectedVersion }),
```

- [ ] **Step 5: Run backend and web contract tests**

```bash
cd apps/sanad-platform && mvn -Dtest=WorkflowApiContractTest test
cd ../web && npm test -- workflow
```

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/workflow/api apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowApiContractTest.java apps/web/lib/api/workflow-api.ts
git commit -m "feat(workflow): expose typed Y2 API"
```

### Task 17: Replace the monolithic Workflow page with the approved operational IA

**Files:**
- Modify: `apps/web/app/workflow/page.tsx`
- Create: `apps/web/app/workflow/components/workflow-nav.tsx`
- Create: `apps/web/app/workflow/components/workflow-overview.tsx`
- Create: `apps/web/app/workflow/components/workflow-definitions.tsx`
- Create: `apps/web/app/workflow/components/workflow-my-tasks.tsx`
- Create: `apps/web/app/workflow/components/workflow-approvals.tsx`
- Create: `apps/web/app/workflow/components/workflow-instances.tsx`
- Create: `apps/web/app/workflow/components/workflow-incidents.tsx`
- Create: `apps/web/app/workflow/components/workflow-monitoring.tsx`
- Create: `apps/web/app/workflow/components/workflow-settings.tsx`
- Create: `apps/web/app/workflow/__tests__/workflow-navigation.test.tsx`

**Interfaces:**
- IA: Overview, Definitions, My Tasks, Approvals, Instances, Incidents, Monitoring, Settings.
- UI never authorizes; every mutation handles 403 and 409 explicitly.

- [ ] **Step 1: Rewrite the baseline web test to RED against the new IA**

```tsx
it("renders all Y2 workflow destinations", async () => {
  render(<WorkflowPage />);
  for (const label of ["نظرة عامة", "التعريفات", "مهامي", "الموافقات", "المثيلات", "الحوادث", "المراقبة", "الإعدادات"]) {
    expect(await screen.findByText(label)).toBeInTheDocument();
  }
});
```

- [ ] **Step 2: Run RED**

```bash
cd apps/web
npm test -- app/workflow/__tests__/workflow-navigation.test.tsx
```

- [ ] **Step 3: Split the current page**

`page.tsx` owns authentication, shell, and active section only:

```tsx
export default function WorkflowPage() {
  const { user, loading } = useAuth();
  const [section, setSection] = useState<WorkflowSection>("overview");
  if (loading) return <AuthLoadingState />;
  if (!user) return null;
  return (
    <ExecutiveShell>
      <WorkflowNav value={section} onChange={setSection} />
      <WorkflowSectionRouter section={section} />
    </ExecutiveShell>
  );
}
```

Keep RTL, existing SNAD design tokens, responsive layouts, keyboard focus, and semantic controls.

- [ ] **Step 4: Build My Tasks and Approvals against real APIs**

No static sample data. Rejection opens a reason field/dialog and blocks submission when blank. Claim/complete/approve/reject send the current `version` as `expectedVersion`; a 409 reloads the item and shows a conflict message.

- [ ] **Step 5: Run web tests and build**

```bash
cd apps/web
npm test -- app/workflow
npm run lint
npm run build
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add apps/web/app/workflow apps/web/lib/api/workflow-api.ts
git commit -m "feat(workflow): add operational workflow workspace"
```

### Task 18: Build the definition/version visual designer

**Files:**
- Create: `apps/web/app/workflow/definitions/[id]/page.tsx`
- Create: `apps/web/app/workflow/definitions/[id]/components/workflow-designer.tsx`
- Create: `apps/web/app/workflow/definitions/[id]/components/step-palette.tsx`
- Create: `apps/web/app/workflow/definitions/[id]/components/step-inspector.tsx`
- Create: `apps/web/app/workflow/definitions/[id]/components/assignment-rule-editor.tsx`
- Create: `apps/web/app/workflow/definitions/[id]/components/expression-rule-editor.tsx`
- Create: `apps/web/app/workflow/definitions/[id]/components/publish-panel.tsx`
- Create: `apps/web/app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx`

**Interfaces:**
- Designer edits DRAFT only.
- Published versions render read-only; “Edit” creates a next DRAFT version.
- Conditions edit normalized AST, not arbitrary code.

- [ ] **Step 1: Write RED published-read-only test**

```tsx
it("does not expose graph mutation controls for published version", async () => {
  mockDefinition({ publicationState: "PUBLISHED" });
  render(<WorkflowDesignerPage />);
  expect(await screen.findByText("منشور")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "حذف خطوة" })).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "إنشاء مسودة جديدة" })).toBeInTheDocument();
});
```

- [ ] **Step 2: Implement accessible node/edge editor without adding a new graph dependency initially**

Start with DOM/CSS positioned nodes and SVG edges using existing React. Do not add a graph library until the interaction tests prove the native implementation is insufficient; dependency addition requires explicit review because the current web app has a deliberately small dependency surface.

- [ ] **Step 3: Implement inspector forms**

The inspector must support Y2 step types, assignment rules, approval policy (`ANY_ONE|ALL`), self approval (`DENY|ALLOW`), SLA mode, transition outcomes, and safe condition rows.

- [ ] **Step 4: Wire Validate → Simulate → Publish**

Publish button stays disabled until the latest server validation is valid. Simulation result is clearly marked non-production and never claims that source-module side effects occurred.

- [ ] **Step 5: Run tests/build**

```bash
cd apps/web
npm test -- app/workflow/definitions
npm run lint
npm run build
```

- [ ] **Step 6: Commit**

```bash
git add apps/web/app/workflow/definitions apps/web/lib/api/workflow-api.ts
git commit -m "feat(workflow): add versioned workflow designer"
```

### Task 19: Add operational read models, incidents, and observability metrics

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowOperationalQueryService.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/infrastructure/JdbcWorkflowOperationalQueryRepository.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowMonitoringService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowSlaScheduler.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowOperationalReadModelTest.java`

**Interfaces:**
- Read models power My Tasks, My Approvals, pools, definition summaries, instance search, incidents, and monitoring.
- Commands still reload authoritative state; read-model data is never used as authorization evidence.

- [ ] **Step 1: Write RED query test**

```java
@Test
void myTasksQueryReturnsOnlyCurrentEmployeesTenantWork() {
    var result = queries.findMyTasks(TENANT_A, EMPLOYEE_A, 50);
    assertThat(result).allMatch(row -> row.tenantId().equals(TENANT_A));
    assertThat(result).noneMatch(row -> row.assigneeEmployeeId().equals(EMPLOYEE_B));
}
```

- [ ] **Step 2: Implement indexed SQL queries**

Query normalized source tables directly first. Only introduce a denormalized projection table if `EXPLAIN ANALYZE` against realistic tenant volume fails the agreed latency gate; do not create a projection by default.

- [ ] **Step 3: Add Micrometer metrics**

Register measurements for queue depth, task/approval aging, action retry/failure rate, SLA breach count, inbox/outbox lag, scheduler lag, open incident age/count, stuck joins, and notification failures. Labels must not include unbounded user-entered values.

- [ ] **Step 4: Run tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowOperationalReadModelTest,WorkflowSlaSchedulerTest test
```

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowOperationalReadModelTest.java
git commit -m "feat(workflow): add operational queries and metrics"
```

---

# Wave 4 — Security, Cutover, Release, and Rollback Proof

### Task 20: Close tenant isolation, authorization, break-glass, and race-condition gates

**Files:**
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowSecurityNegativeTest.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowIdempotencyTest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowY2TenantIsolationTest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowBreakGlassTest.java`

**Interfaces:**
- Produces release-gate proof that cross-tenant injection, stale versions, unauthorized assignment actions, and break-glass impersonation fail closed.

- [ ] **Step 1: Add cross-tenant matrix tests**

Test cross-tenant references for Employee/User, role/capability, department, position, definition, transition, WorkItem, candidate, delegation, calendar, incident, child workflow, and source entity reference.

Representative assertion:

```java
assertThatThrownBy(() -> workItemService.reassign(TENANT_A, ITEM_A, EMPLOYEE_B_TENANT_B, ADMIN_A, 0, "test"))
        .isInstanceOf(WorkflowReferenceIntegrityException.class);
```

- [ ] **Step 2: Add stale-version race tests**

Run concurrent claim, approve, reject, publish, reassign, incident resolve, and join completion operations; assert exactly one winning state transition and 409-equivalent conflict for losers.

- [ ] **Step 3: Add break-glass tests**

Verify break-glass can perform only the defined emergency commands with `WORKFLOW.BREAK_GLASS`, requires non-blank reason, records an override audit event, and cannot forge an approver decision or mutate a published version.

- [ ] **Step 4: Run security suites**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowSecurityNegativeTest,WorkflowIdempotencyTest,WorkflowY2TenantIsolationTest,WorkflowBreakGlassTest test
```

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/test/java/com/sanad/platform/workflow
git commit -m "test(workflow): close Y2 security gates"
```

### Task 21: Implement the strangler cutover and legacy compatibility rules

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/application/WorkflowExecutionService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/workflow/api/WorkflowController.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowY2CutoverTest.java`
- Create: `docs/runbooks/workflow-y2-cutover.md`

**Interfaces:**
- Legacy running instances continue on legacy runtime.
- New starts for a Y2-published version always use Y2 runtime.
- No automatic in-flight migration.

- [ ] **Step 1: Write RED cutover tests**

```java
@Test
void legacyRunningInstanceRemainsLegacyAfterY2VersionIsPublished() {
    var legacy = fixtureLegacyRunningInstance();
    publishY2NextVersion(legacy.definitionFamilyId());
    assertThat(executionRouter.engineFor(legacy.id())).isEqualTo(LEGACY);
}

@Test
void newStartOnY2PublishedVersionUsesY2Only() {
    var started = startPublishedY2Version();
    assertThat(started.engineGeneration()).isEqualTo(Y2);
}
```

- [ ] **Step 2: Implement explicit engine selection at start**

Resolve the concrete definition version first, then persist its engine generation on the instance. Subsequent commands route from the persisted instance value, never from “current latest definition”.

- [ ] **Step 3: Write cutover runbook**

The runbook must contain exact preflight SQL counts, deployment order, legacy/Y2 instance counts, rollback trigger conditions, and rollback behavior. Rollback may stop new Y2 starts and repoint future starts to a prior published version; it must not rewrite already-running Y2 instances.

- [ ] **Step 4: Run cutover tests**

```bash
cd apps/sanad-platform
mvn -Dtest=WorkflowY2CutoverTest,WorkflowEngineIntegrationTest,WorkflowManagementE2ETest test
```

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/workflow apps/sanad-platform/src/test/java/com/sanad/platform/workflow/WorkflowY2CutoverTest.java docs/runbooks/workflow-y2-cutover.md
git commit -m "feat(workflow): add Y2 strangler cutover"
```

### Task 22: Full release verification and final execution evidence

**Files:**
- Create: `docs/reports/workflow-y2-release-evidence.md`
- Modify only if a verified defect is found: implementation/test files from Tasks 1–21.

**Interfaces:**
- Produces the release evidence required before opening/merging the implementation PR.

- [ ] **Step 1: Verify migration history and PostgreSQL Direct schema**

Run Flyway through the repository's standard application/test bootstrap against PostgreSQL Direct. Verify all six `V20260830_*` migrations appear exactly once and all new Workflow tables have tenant/RLS coverage.

- [ ] **Step 2: Run the full Workflow backend suite**

```bash
cd apps/sanad-platform
mvn -Dtest='com.sanad.platform.workflow.*' test
```

Expected: 0 failures, 0 errors.

- [ ] **Step 3: Run the full backend suite required by main protection**

```bash
cd apps/sanad-platform
mvn test
```

Expected: 0 failures, 0 errors. Do not waive unrelated failures; classify and fix or stop release.

- [ ] **Step 4: Run web verification**

```bash
cd apps/web
npm test
npm run lint
npm run build
```

Expected: all pass.

- [ ] **Step 5: Run browser E2E for the Workflow workspace**

Add/execute Playwright coverage for:

```text
Definition draft -> validate -> simulate -> publish
Start Y2 instance
Direct HUMAN_TASK -> My Tasks -> complete
Pool HUMAN_TASK -> claim conflict
ANY_ONE approval -> reject one -> approve another
ALL approval -> reject -> onReject transition
Disabled user -> ASSIGNEE_UNAVAILABLE -> manual reassignment
SLA breach -> escalation/incident
Incident acknowledge/resolve
Legacy running instance remains legacy
```

Use the repository's authenticated E2E harness and PostgreSQL Direct environment; no mock backend for this release gate.

- [ ] **Step 6: Produce release evidence**

`docs/reports/workflow-y2-release-evidence.md` must record:

```text
Branch
HEAD SHA
Base SHA
Migration list
Backend Workflow suite result
Full Maven suite result
Web Vitest result
Lint result
Next.js build result
Playwright result
Cross-tenant negative result
Idempotency/concurrency result
Legacy/Y2 cutover result
Known deferred scope: QUORUM, full BPMN gateway set, arbitrary scripting, SMS/WhatsApp provider implementation
Release verdict: PASS or BLOCKED
```

No PASS may be written without command output from this execution.

- [ ] **Step 7: Final commit**

```bash
git add docs/reports/workflow-y2-release-evidence.md
git commit -m "docs(workflow): record Y2 release evidence"
```

---

# Dependency Order and Reviewer Gates

Execute tasks strictly in this dependency order:

```text
1 -> 2 -> 3
       |
       v
4 -> 5 -> 6
       |
       v
7 -> 8 -> 9
       |
       v
10 -> 11 -> 12 -> 13 -> 14 -> 15
                         |
                         v
16 -> 17 -> 18 -> 19
                         |
                         v
20 -> 21 -> 22
```

Reviewer gates:

- **Gate G0 after Task 3:** identity and capability migrations are safe, tenant-aware, and legacy-compatible.
- **Gate G1 after Task 9:** immutable definitions, graph persistence, WorkItems, assignment, and approval semantics are correct before runtime cutover code begins.
- **Gate G2 after Task 15:** Y2 runtime, reliability, automation, SLA, incident, and delivery semantics pass integration/idempotency tests.
- **Gate G3 after Task 19:** typed API and UI operate exclusively through server-authoritative commands and real backend data.
- **Gate G4 after Task 22:** full PostgreSQL Direct, Maven, web, build, E2E, security, and cutover evidence passes.

# Spec Coverage Self-Review

This plan maps the approved design to executable tasks as follows:

- A1/B1 Employee↔User and disabled-user handling: Tasks 2, 8, 12, 20.
- C3 WorkItems for human steps only: Task 7 and runtime activation in Task 10.
- D3 assignment vs authorization: Tasks 2, 8, 20.
- E3/F3 approval ANY_ONE/ALL and explicit reject: Task 9, graph routing Task 10.
- G3 delegation/fallback/escalation with B1 dominance: Task 12.
- H3 graph/designer: Tasks 5, 10, 18.
- I3 immutable versions/publish: Tasks 4, 6, 21.
- J3/X3 triggers/inbox/outbox/idempotency: Task 15.
- K3 notifications: Task 15.
- L3/N3 pools and eligibility snapshot: Tasks 7–8.
- M3 self approval deny by default: Task 9.
- O3/AF3 durable action execution/incidents: Task 13.
- P3 compensation: Task 13.
- R3 parallel forks/joins: Task 14.
- S3 typed context: Task 11.
- T3 hybrid task UI: Tasks 7, 17–18; MODULE_ACTION remains a source-module handoff, not workflow-owned business logic.
- U3 safe expressions: Task 11.
- V3 business calendar: Task 12.
- W3 sub-workflows: Task 14.
- AB3/AH3/AJ3 fine-grained capabilities, break-glass, server authority: Tasks 3, 16, 20.
- AC3/AE3/AG3 audit/retention/observability: Tasks 13, 19, 20, 22.
- AD3 tenant isolation: Tasks 2–5, 7, 12–15, 20.
- AI3 typed API/concurrency/idempotency: Tasks 7, 9, 15–16, 20.
- AL3 operational read model: Task 19.
- AN3 validator/simulation: Task 6.
- AP3 operational IA: Tasks 17–18.
- Z3/AA3 strangler cutover/release gates: Tasks 21–22.

# Explicit Non-Goals for This Implementation Wave

The following are intentionally excluded because the approved Y2 V1 scope defers them:

- QUORUM / N_OF_M approval policy.
- Full BPMN gateway/event semantics beyond controlled fork/join and call-workflow.
- Arbitrary user-authored scripts or executable expressions.
- Workflow-owned SMS/WhatsApp provider implementation.
- Automatic migration of already-running LEGACY instances into Y2.
- Replacing source-module domain models with Workflow-owned records.
- Replacing PostgreSQL with event sourcing.
