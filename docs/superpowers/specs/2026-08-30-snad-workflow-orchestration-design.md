# SNAD Workflow Orchestration Platform — Y2 Design Spec

> **Date:** 2026-08-30
> **Status:** DESIGN APPROVED — WRITTEN SPEC REVIEW PENDING
> **Baseline SHA:** `a8a7ce4da18f7f1b03e6a54933ff886a3f6484e5`
> **Repository:** `snadaiapp-png/SNAD`
> **Design branch:** `design/workflow-orchestration-spec`
> **Scope:** Workflow definitions and immutable versions, visual designer, runtime orchestration, employee assignment, human tasks, approvals, SLA/business calendars, delegation, escalation, incidents, compensation, notifications, reliable events, migration/cutover, security, observability, and operational UI.

---

## 1. Purpose

This specification defines the approved evolution of SNAD's existing Workflow Engine into a reusable **Workflow Orchestration Platform (Y2)** for HR, CRM, ERP, Accounting, Management, Commerce, and future modules.

The target is not a replacement BPMN product and not a second source of truth for domain data. Workflow owns process definitions, execution state, assignment state, approval state, work items, process context, incidents, timers, orchestration audit, and integration delivery state. Source modules continue to own their business entities and business invariants.

The implementation must be a controlled evolution of the current production code and schema. Existing identifiers, historical audit, running instances, API compatibility, tenant isolation, and rollback safety take precedence over architectural neatness.

### 1.1 Approved architecture

The approved architecture is **Y2 — Workflow Orchestration Platform**:

```text
HR / CRM / ERP / Accounting / Commerce / Management / ...
                         |
       Manual / Domain Event / API / Schedule
                         v
+-------------------------------------------------------+
|              Workflow Orchestration Platform          |
|-------------------------------------------------------|
| Definition + Immutable Versioning                     |
| Publish Validator + Simulation                        |
| Runtime / Graph Execution                             |
| Assignment Resolver + Eligibility Snapshot            |
| WorkItem Service                                      |
| Approval Policy Engine                                |
| Safe Expression Engine                                |
| SLA + Business Calendar Engine                        |
| Delegation + Escalation                               |
| System Action Execution + Retry                       |
| Compensation + Incident Management                    |
| Sub-Workflow Orchestration                            |
| Notification Orchestration Adapter                    |
| Transactional Outbox + Idempotent Inbox               |
| Audit + Metrics + Traces + Operational Read Models    |
+-------------------------+-----------------------------+
                          |
                 HR / Identity / RBAC
```

### 1.2 Architectural boundaries

Workflow **MUST NOT**:

- become the owner of HR, CRM, ERP, Accounting, Commerce, or other source-module business records;
- grant authorization merely because a task is visible or assigned;
- execute arbitrary user-authored JavaScript, SQL, shell, network calls, or `eval` expressions;
- claim exactly-once distributed delivery;
- mutate a published workflow version in place;
- migrate an already-running legacy instance to Y2 unless an explicit, verified migration path proves semantic equivalence;
- create a second independent authentication, RBAC, notification-provider, or employee directory stack when a reusable platform facility exists;
- use name/email fuzzy matching as an identity relation.

---

## 2. Repository Forensics and Existing Baseline

The implementation plan MUST start from the following observed repository facts.

### 2.1 Current backend structure

The existing backend is under:

`apps/sanad-platform/src/main/java/com/sanad/platform`

Workflow already follows a layered package structure:

```text
com.sanad.platform.workflow
  api/
  application/
  domain/
  infrastructure/
```

The current domain contains `WorkflowDefinition`, `WorkflowStep`, `WorkflowInstance`, `WorkflowStepInstance`, `WorkflowApprovalRequest`, and `WorkflowTransitionAudit`. The current application layer contains `WorkflowDefinitionService`, `WorkflowExecutionService`, `WorkflowApprovalService`, `WorkflowMonitoringService`, and `WorkflowSlaScheduler`. Persistence uses JDBC repository adapters such as `JdbcWorkflowDefinitionRepository` and `JdbcWorkflowInstanceRepository`.

**Design consequence:** Y2 extends the current domain-port/JDBC-adapter pattern. It does not switch Workflow to a different persistence paradigm merely because Spring Data JPA is available elsewhere in the backend.

### 2.2 Current runtime is primarily linear

`WorkflowExecutionService` currently advances a single `currentStepKey`, completes the prior `WorkflowStepInstance`, creates the next `PENDING` step instance, and records `WorkflowTransitionAudit`. SLA due time is currently computed as wall-clock `Instant.now() + slaHours`.

**Design consequence:** graph transitions, parallel branches, business-time SLA, durable worker execution, work pools, and explicit outcomes are additive runtime capabilities; they are not assumed to exist today.

### 2.3 Current definitions already carry versions

`workflow_definitions` already has `(tenant_id, code, version)` uniqueness. `workflow_steps` reference a concrete definition row, and `workflow_instances` already record both the definition ID and workflow version.

**Approved migration model:** preserve each existing `workflow_definitions.id` as a concrete version identity. Add a stable logical-family identifier and Y2 publication metadata rather than destructively replacing the table. Existing definition IDs therefore remain valid and existing instances remain pinned.

### 2.4 Existing employee-to-user link already exists

The HR backend already has nullable `hr_employees.user_id`, and `HrEmployee` already exposes `UUID userId`. The web HR API type currently does not expose the same link.

**Design consequence:** A1 does **not** introduce `Employee.userId` from scratch. Y2 formalizes, validates, indexes, and exposes the existing optional relationship where authorized. A non-null link must be tenant-safe and one-to-one within a tenant. No email/name identity inference is permitted.

### 2.5 Existing Workflow capabilities are coarse

The existing seed migration defines:

- `WORKFLOW.VIEW`
- `WORKFLOW.WRITE`
- `WORKFLOW.ADMIN`
- `WORKFLOW.APPROVE`

Y2 therefore expands capability granularity additively and maintains compatibility for existing roles during migration.

### 2.6 Existing test investment must be preserved

The repository already contains Workflow API-contract, architecture, database-forensics, integration, idempotency, E2E, security-negative, approval-reference-integrity, and SLA tests.

**Design consequence:** implementation extends these test families and keeps them green through Z3/AA3 cutover. It does not discard the current test suite in favor of a new isolated test harness.

### 2.7 Existing event/outbox patterns exist in CRM

CRM has a durable event-outbox contract and additive migration pattern. Y2 must first evaluate whether that infrastructure can be generalized safely. If it cannot, Workflow may own a compatible outbox/inbox adapter, but the event envelope and operational semantics should align with platform conventions rather than inventing an incompatible delivery model.

---

## 3. Normative Design Invariants

These rules are approved and MUST NOT be weakened during implementation:

1. Concrete human assignee identity is `Employee.id`.
2. Authentication identity is `User.id`.
3. Authorization comes from current server-side roles/capabilities, never assignment alone.
4. An employee may exist without a login user.
5. A human workflow action requires the employee to be actionable through an ACTIVE linked user unless the action is a non-interactive administrative system operation.
6. A hard-disabled user makes existing assigned work unavailable; it does not auto-transfer ownership.
7. Published versions are immutable and running instances are version-pinned.
8. Only `HUMAN_TASK` and `APPROVAL` produce central actionable WorkItems.
9. Eligibility is snapshotted at step activation but authorization is revalidated at action time.
10. `ANY_ONE` and `ALL` are the V1 approval aggregation policies; quorum is deferred.
11. Self-approval is denied by default.
12. Reject transitions are explicit graph edges and rejection reason is mandatory.
13. Human task pools use atomic claim semantics.
14. Automated actions are idempotent, time-bounded, retry-classified, and audited.
15. Business cancellation uses explicit compensation, not blind technical rollback.
16. Conditions use a safe declarative AST, not arbitrary executable code.
17. Business-time SLA uses tenant calendars and timezone-aware calculations.
18. Sub-workflows are version-aware, cycle-checked, depth-limited, and independently auditable.
19. Distributed event delivery is at-least-once plus idempotency, not exactly-once.
20. Notification-provider failure never rolls back a committed workflow business transition.
21. Tenant identity is derived from trusted security context, not request payload.
22. Audit is append-only business evidence; technical logs/traces are separate.
23. Historical workflow evidence is not hard-deleted during normal operations.
24. All Y2 database changes are forward-only and additive-first until migration gates close.
25. No instance may execute on both LEGACY and Y2 engines.
26. PostgreSQL remains the authoritative persisted state for workflow execution.

---

## 4. Identity, Employee Linking, and Actionability — A1 / B1

### 4.1 Canonical relation

```text
Employee.id -------------------- concrete business assignee
    |
    +-- userId? ---------------- optional identity link
                                      |
                                      v
                                  User.id
                                      |
                              Roles / Capabilities
```

Policy:

- Employee may be created without User.
- Employee without User remains valid for HR operations.
- Interactive Workflow `HUMAN_TASK`, `APPROVAL`, and user-executed actions require `employee.userId != null` and current linked user status ACTIVE.
- Employee and User must belong to the same tenant.
- One User may link to at most one Employee in the same tenant.
- Disabling a User does not mutate the Employee HR status.
- Terminating/archiving an Employee does not delete its User.

The HR migration must add an additive partial uniqueness constraint/index equivalent to:

```text
UNIQUE (tenant_id, user_id) WHERE user_id IS NOT NULL
```

and application validation must verify same-tenant linkage. The web HR contract may expose `userId` or a restricted `identityLink` view to authorized HR/workflow administrators.

### 4.2 Hard user deactivation — B1

When a linked User becomes non-actionable:

- no new workflow action may be executed through that identity;
- existing assigned WorkItems remain persisted;
- assignment availability becomes `ASSIGNEE_UNAVAILABLE`;
- the system does not automatically assign the work to the manager merely because the user was disabled;
- an authorized manager/administrator must explicitly reassign it;
- old employee, new employee, actor, reason, timestamp, and source policy are audited.

G3 delegation/fallback does not override B1 for a hard-disabled user. Planned absence delegation and SLA fallback remain separate policies.

---

## 5. Definition Family, Immutable Versions, and Publishing — I3

### 5.1 Persistence evolution

The safest additive design is to preserve the current `workflow_definitions` row as the concrete **version row** and add a stable logical-family identity, rather than deleting or renaming existing IDs.

Recommended additive fields include:

```text
workflow_definitions
  definition_family_id UUID
  engine_generation    LEGACY | Y2
  publication_state    DRAFT | PUBLISHED | RETIRED
  published_by         UUID?
  published_at         timestamptz?
  validated_at         timestamptz?
  definition_checksum  varchar?
  schema_version       int
```

Existing rows are backfilled safely. Existing `id` values remain valid version IDs. New versions share `definition_family_id`, `tenant_id`, and `code`, and increment `version`.

### 5.2 Lifecycle

```text
DRAFT -> VALIDATE -> PUBLISHED (immutable)
                     |
                     +-> create next DRAFT version

PUBLISHED old version may become RETIRED for future starts,
but historical/running instances remain pinned to it.
```

“Rollback” means selecting a previously published version for **new starts**. It never rewrites the definition of a running instance.

### 5.3 Publish gate

Publishing requires the validation/simulation checks in Section 18 and the `WORKFLOW.PUBLISH` capability. The published checksum provides a tamper-detection reference for audit and runtime loading.

---

## 6. Graph Model and Step Types — H3 / R3 / W3

Y2 replaces sequence-order-as-semantics with an explicit graph. `sequence_order` may remain as presentation/backward-compatibility metadata, but runtime routing uses transition rows.

New step types:

```text
START
HUMAN_TASK
APPROVAL
CONDITION
SYSTEM_ACTION
NOTIFICATION
PARALLEL_FORK
PARALLEL_JOIN
CALL_WORKFLOW
END
```

Legacy `ACTION` maps through compatibility logic to the appropriate Y2 behavior only where migration semantics are known; it is not globally guessed.

Introduce an additive transition table, for example `workflow_step_transitions`, version-scoped through the concrete definition ID:

```text
id
tenant_id
workflow_definition_id
from_step_id
to_step_id
transition_key
outcome               -- SUCCESS / APPROVE / REJECT / TRUE / FALSE / ...
condition_ast JSONB?
priority
metadata JSONB
```

### 6.1 Controlled parallelism — R3

V1 supports explicit `PARALLEL_FORK` and `PARALLEL_JOIN` only, not full BPMN gateway semantics.

`PARALLEL_JOIN` policies:

- `ALL_BRANCHES`: continue after every required branch terminalizes successfully.
- `ANY_BRANCH`: continue after one qualifying branch succeeds; remaining branch disposition must be explicitly defined (cancel, allow-to-finish, compensate, or ignore result). The runtime may not silently abandon durable side effects.

Each branch has a durable token/branch identity. Join completion uses compare-and-set/locking so a race cannot advance the graph twice.

### 6.2 Sub-workflow — W3

`CALL_WORKFLOW` supports:

- pinned version or current published version resolution;
- explicit input/output mapping;
- `WAIT_FOR_COMPLETION` or `FIRE_AND_CONTINUE`;
- timeout/failure transition;
- `parent_instance_id`/child references;
- cycle detection at publish time;
- bounded call depth.

Running parent/child instances keep their resolved version IDs even if a newer child version is later published.

---

## 7. Runtime State and Typed Workflow Context — S3

### 7.1 Instance additions

Recommended additive fields to `workflow_instances`:

```text
definition_family_id
engine_generation      LEGACY | Y2
definition_version_id  -- points to concrete workflow_definitions.id
parent_instance_id
trigger_type
trigger_id / event_id
idempotency_key
causation_id
context_json JSONB
context_schema_version
migration_status
```

The existing `workflow_definition_id`, `workflow_version`, business-entity reference, correlation ID, and version are preserved during compatibility.

### 7.2 Typed context

Workflow context is not a free shared scratchpad. It has typed namespaces:

```text
context
  source
  requester
  variables
  stepOutputs.<stepKey>
  system
```

Each step declares an input schema and output schema. Input/output mappings are validated at publish and runtime boundaries. A step cannot overwrite another step's output namespace arbitrarily.

Schema evolution uses an explicit schema version. Historical instance payloads remain interpretable by their pinned version metadata.

---

## 8. Assignment Resolver, Snapshot, and Work Pools — D3 / L3 / N3

### 8.1 Assignment is not authorization

Step assignment rules may target:

- `EMPLOYEE`
- `MANAGER`
- `POSITION`
- `DEPARTMENT`
- `ROLE`
- `PERMISSION` / capability

At activation the resolver uses HR and RBAC to produce concrete Employee candidates. It never persists “a role” as if that role itself were a human assignee.

### 8.2 Eligibility snapshot — N3

At step activation the system persists the effective candidate set and resolution evidence, including employee IDs, resolver rule/version, resolved timestamp, and relevant organizational references. This is historical evidence, not a permanent authorization grant.

At `CLAIM`, `APPROVE`, `REJECT`, `COMPLETE`, `RELEASE`, or delegated execution, the backend revalidates:

- Employee still actionable for the operation;
- linked User is still ACTIVE;
- current required capabilities remain granted;
- current Workflow policy is satisfied;
- delegation, if used, remains valid.

### 8.3 Human-task assignment — L3

`HUMAN_TASK` supports:

- `DIRECT`: one resolved Employee assignee;
- `WORK_POOL`: several candidate Employees, one atomic claimant.

Claim/release/reassign commands use optimistic concurrency. Two employees cannot successfully claim the same WorkItem version.

---

## 9. Central WorkItem Model — C3 / T3

Only human-action steps create central WorkItems.

Recommended table/model:

```text
workflow_work_items
  id
  tenant_id
  workflow_instance_id
  workflow_step_instance_id
  type                  HUMAN_TASK | APPROVAL
  status                AVAILABLE | CLAIMED | IN_PROGRESS |
                        ASSIGNEE_UNAVAILABLE | COMPLETED |
                        CANCELLED | EXPIRED
  assignee_employee_id?
  claimed_by_employee_id?
  assignment_mode       DIRECT | WORK_POOL
  source_module
  source_entity_type
  source_entity_id
  title
  description
  priority
  due_at
  sla_due_at
  claimed_at
  completed_at
  version
  created_at
  updated_at
```

Candidate rows should be relational for pool querying and My Tasks performance, while a frozen resolver snapshot may additionally be stored as JSON metadata.

### 9.1 Hybrid task UI — T3

A `HUMAN_TASK` declares one of:

- `WORKFLOW_FORM`: schema-driven form inside My Tasks;
- `MODULE_ACTION`: link/embed/command handoff to a source-module action that remains owned by that module.

Workflow-form fields support typed validation, conditional visibility, attachments, comments, and `READ_ONLY | EDITABLE | HIDDEN` field-level policy. The server validates the form schema and values; frontend behavior is not an authority boundary.

Attachments reference the platform file/object-storage abstraction rather than placing binary content in workflow context JSON.

---

## 10. Approval Engine — E3 / F3 / M3 / Q2

Each approval step persists a policy snapshot and one effective approval request per candidate as appropriate.

### 10.1 V1 aggregation

`ANY_ONE`:

- first valid `APPROVE` completes the step through `onApprove`;
- outstanding parallel approval requests close/cancel as no longer required;
- one `REJECT` closes only that actor's request;
- `onReject` fires only when no remaining effective candidate can approve.

`ALL`:

- all required candidates must approve;
- the first valid rejection makes unanimous approval impossible and routes through `onReject` unless a future explicit collection mode is introduced.

`QUORUM/N_OF_M` is deferred.

### 10.2 Explicit reject transitions — F3

Every approval step declares `onApprove` and `onReject` graph transitions. Rejection is not synonymous with workflow termination. Reject may route to correction, previous review, exception handling, or an end-rejected node.

Reject reason is mandatory and audited.

### 10.3 Self-approval — M3

Default = `DENY`.

An explicit `ALLOW` override requires:

- an explicit published step policy;
- a dedicated capability for the actor/role allowed to configure/use such a policy;
- audit evidence that self-approval was permitted by that exact version.

Existing `requested_by_user_id` is retained for compatibility and SoD evidence. Y2 additionally records requester Employee identity when available. Current contradictory legacy comments around self-approval semantics must be corrected as part of the refactor; executable policy and tests are authoritative.

---

## 11. Delegation, Fallback, Reassignment, and Escalation — G3

### 11.1 Delegation

Delegation is planned, time-bounded, tenant-safe, and optionally scoped by workflow family, module, task category, or capability. A delegate does not inherit all permissions from the delegator. The delegate must independently satisfy current action authorization.

### 11.2 Fallback

A workflow may declare explicit fallback resolution for planned absence or unresolved assignment, such as manager, backup Employee, or another candidate pool. Fallback still resolves to Employee IDs and still passes authorization.

### 11.3 B1 dominance

Hard User deactivation does not trigger automatic fallback/reassignment of existing assigned work. It produces `ASSIGNEE_UNAVAILABLE`, alerting an authorized supervisor to perform an explicit reassignment.

### 11.4 Escalation

SLA escalation may:

- notify assignee/manager;
- increase priority;
- add an authorized supervisor candidate;
- reassign only where an explicit published policy permits it;
- create an Incident.

Every delegation, release, claim, reassignment, fallback, and escalation action is audited.

---

## 12. Safe Conditions and Data Mapping — U3

Conditions are represented as a declarative AST/structured rule model, not code strings executed with `eval`.

Allowed expressions are bounded combinations of:

- typed context fields and step outputs;
- equality and numeric/date/string comparisons;
- AND / OR / NOT;
- IN / NOT_IN;
- EMPTY / EXISTS;
- approved pure functions such as contains or date comparisons.

The engine enforces maximum expression depth/complexity and runtime duration. Expressions have no direct DB, network, filesystem, secret, reflection, shell, or arbitrary class access.

The visual editor renders the AST as business-readable rule rows and stores the normalized AST as versioned definition data.

---

## 13. System Actions, Resilience, Idempotency, and Incidents — O3 / AF3

`SYSTEM_ACTION` uses durable worker execution, not long-running controller transactions.

Each action definition declares:

- action/adapter type;
- input/output schema;
- timeout;
- idempotency strategy/key derivation;
- retryable failure classes;
- max attempts;
- exponential/backoff policy;
- failure transition or incident policy;
- compensation metadata where applicable.

Business validation failures are non-retryable unless explicitly classified otherwise. Transient infrastructure failures may retry. Every attempt is durably recorded.

Recommended `workflow_execution_attempts` fields include instance, step instance, attempt number, idempotency key, started/finished timestamps, outcome, normalized failure category, external reference, and sanitized diagnostic metadata.

### 13.1 Incident model — AF3

A first-class `WorkflowIncident` is created when manual operational intervention is required.

Suggested lifecycle:

```text
OPEN -> ACKNOWLEDGED -> RESOLVED -> CLOSED(optional)
```

It records severity, source, instance/step/action, failure category, owner, attempt history, timestamps, resolution, and retry/resume linkage. The platform never silently converts an exhausted failure to success.

---

## 14. Cancellation and Compensation — P3

Cancellation is an explicit business command with authorization and reason.

```text
ACTIVE -> CANCELLING -> CANCELLED
                     -> INCIDENT (if required compensation fails)
```

A committed business side effect is not rolled back by pretending the distributed operation shares a database transaction. A `SYSTEM_ACTION` may declare:

- `compensatable=true|false`;
- compensation adapter/action;
- compensation timeout;
- compensation retry policy;
- compensation failure routing.

Examples include VOID/CANCEL domain operations, not direct deletion of financial history. Compensation is itself idempotent and audited.

---

## 15. SLA and Tenant Business Calendar — V3

Y2 introduces a tenant-scoped Business Calendar model containing:

- timezone;
- working weekdays;
- one or more working-hour windows;
- weekends;
- official/custom holidays;
- custom closures;
- effective/version dates.

Step SLA supports:

- `CALENDAR_TIME`;
- `BUSINESS_TIME`;
- due-in duration;
- reminder-before thresholds;
- escalation-after thresholds;
- maximum escalations;
- breach action.

All persisted timestamps are UTC instants. Due-date calculation uses the pinned calendar/policy reference appropriate to the step version so later calendar changes do not make historical evidence ambiguous.

Existing wall-clock `slaHours` remains supported for legacy instances; Y2 resolves it through explicit SLA policy semantics.

---

## 16. Triggers, Reliable Events, Inbox/Outbox — J3 / X3

Supported trigger classes:

- `MANUAL`
- `DOMAIN_EVENT`
- `API`
- `SCHEDULE`

Every start passes version resolution, authorization where relevant, schema validation, and idempotency before instance creation.

### 16.1 Event envelope

```text
eventId
 eventType
 tenantId
 aggregateType
 aggregateId
 occurredAt
 correlationId
 causationId
 schemaVersion
 payload
```

### 16.2 Delivery semantics

The approved model is **transactional outbox + idempotent inbox + at-least-once delivery**.

For a source-module transaction, business state and the outgoing event intent commit atomically. Workflow consumers deduplicate by durable event identity and trigger/version key. A duplicate delivery therefore cannot create a duplicate instance.

Failed dispatch/consumption uses retry plus dead-letter/Incident handling. “Exactly once” must not appear as a system guarantee.

Where the CRM outbox implementation can be safely generalized, reuse/extract platform primitives. Otherwise implement a compatible Workflow adapter without coupling the Workflow domain directly to CRM tables.

### 16.3 API and webhooks

Inbound integration requests use authentication/signature, nonce/timestamp anti-replay where applicable, rate limits, schema validation, and idempotency keys. Outbound webhooks are signed, retried, deduplicated, and delivery-audited.

Secrets are stored in the platform secret/configuration mechanism and are never embedded in workflow definition JSON.

---

## 17. Notification Orchestration — K3

Workflow emits notification intents such as:

- `TASK_ASSIGNED`
- `APPROVAL_REQUESTED`
- `DUE_SOON`
- `SLA_BREACHED`
- `REASSIGNED`
- `APPROVED`
- `REJECTED`
- `INCIDENT_OPENED`

Channel resolution combines tenant policy, user preferences, severity, and event type.

V1 channel policy:

- IN_APP: mandatory primary channel for qualifying workflow events;
- EMAIL: optional/configurable;
- WEBHOOK: integration use;
- SMS/WhatsApp: deferred extension.

Workflow does not place provider-specific send logic inside its domain. It calls the reusable notification orchestration/delivery boundary. If the existing notification implementation is CRM-scoped, implementation should extract/reuse the durable platform primitive rather than create another unrelated provider stack.

Delivery status (`PENDING/SENT/DELIVERED/FAILED` where applicable), deduplication key, attempts, and failure diagnostics are operationally visible. Delivery failure never reverses a workflow transition.

---

## 18. Publish Validator and Side-Effect-Free Simulation — AN3

No Y2 version may publish without validation.

The validator checks at minimum:

- exactly one valid start semantics;
- valid end/reachability and no unintended orphan nodes;
- every transition references nodes in the same tenant/version;
- branch/fork/join structural correctness;
- all approval outcomes resolve to valid transitions;
- mappings match input/output schemas;
- expression AST types and complexity;
- assignment resolver configuration;
- no unresolvable required assignee rule detected from static configuration;
- sub-workflow version references, call-depth limit, and cycle absence;
- trigger schema/configuration;
- business calendar/SLA references;
- self-approval and SoD policy;
- required idempotency for side-effecting actions;
- compensation configuration where a policy requires compensation;
- required notification/integration configuration references;
- forbidden secrets/executable code are absent.

Simulation executes the graph with test context and adapter stubs/sandbox behavior. It **must not** create real invoices, orders, users, messages, outbound webhooks, payments, or other production side effects.

---

## 19. Fine-Grained Authorization and Governance — AB3 / AH3 / AJ3

### 19.1 Capability expansion

Keep `WORKFLOW.VIEW` and `WORKFLOW.APPROVE`, and add fine-grained capabilities using the existing code convention. Proposed codes:

```text
WORKFLOW.DESIGN
WORKFLOW.VALIDATE
WORKFLOW.PUBLISH
WORKFLOW.START
WORKFLOW.TASK_EXECUTE
WORKFLOW.REASSIGN
WORKFLOW.DELEGATE
WORKFLOW.CANCEL
WORKFLOW.INCIDENT_MANAGE
WORKFLOW.MONITOR
WORKFLOW.AUDIT_VIEW
WORKFLOW.BREAK_GLASS
WORKFLOW.SELF_APPROVAL_OVERRIDE
```

Final seed migration must reconcile these with the platform RBAC catalog and role templates. Existing `WORKFLOW.WRITE` and `WORKFLOW.ADMIN` are not deleted during initial migration. Existing roles receive an explicit compatibility mapping so current users do not lose legitimate access unexpectedly.

### 19.2 Separation of duties

By default:

```text
DESIGN != PUBLISH
REASSIGN != APPROVE
AUDIT_VIEW is read-only
INCIDENT_MANAGE is independent
SELF_APPROVAL_OVERRIDE is exceptional
```

Small tenants may intentionally grant multiple capabilities to one role. Enterprise tenants can keep them separated.

### 19.3 Break-glass — AH3

Break-glass is an explicit privileged command, never hidden bypass behavior. It requires dedicated capability, reason, actor identity, timestamp, target, and audit. It may allow controlled retry, resume, unblock, cancel, or reassignment.

It must never forge another employee's approval, impersonate an approver, erase rejection evidence, or mutate a published version. An override is recorded as an override event.

### 19.4 Server authority — AJ3

Every action is authorized server-side. UI controls may hide unavailable actions for usability but never replace backend checks. Tenant comes from security context. Input schemas, expressions, mappings, URLs, webhook payloads, and form definitions are validated/sanitized.

---

## 20. Audit, Observability, and Retention — AC3 / AE3 / AG3

### 20.1 Business audit — AC3

Workflow business audit is append-only and records, where applicable:

- tenant;
- workflow family/version;
- instance and step instance;
- WorkItem/approval/incident;
- actor User ID and actor Employee ID when resolvable;
- action;
- before/after state;
- reason/comment;
- assignment source and old/new Employee;
- timestamp;
- correlation/causation IDs;
- policy/version metadata.

Business audit is separate from user-facing timeline and separate from technical execution logs.

### 20.2 Technical observability — AG3

Structured logs, metrics, and traces carry safe correlation fields such as tenant ID, correlation ID, instance ID, step-instance ID, WorkItem ID, and attempt ID. Sensitive context values and secrets are redacted.

Operational SLIs include:

- instance start/step execution latency;
- work queue depth;
- task/approval aging;
- system-action failure and retry rates;
- SLA breach rate;
- outbox/inbox lag;
- timer/scheduler lag;
- open incident count/age;
- stuck parallel joins;
- notification delivery failures.

### 20.3 Retention — AE3

No normal hard-delete for published versions, historical/running instances, approval decisions, or business audit. Definitions use draft deletion only where safe and archive/retire lifecycle otherwise.

Technical logs, bulky payloads, attachments, and delivery records may use configurable retention/minimization policies. Compliance/legal retention may override cleanup. PII copied into context must be minimized and redacted where the business flow does not require it.

---

## 21. Tenant Isolation and Data Integrity — AD3

Every new tenant-owned table carries `tenant_id` unless there is a proven immutable parent-only scoping mechanism and the repository convention explicitly permits omission. Cross-table references must prevent same-database cross-tenant injection.

Rules:

- tenant identity is derived from `SecurityContextUtils`/trusted execution context;
- external/client payload cannot choose a different tenant;
- RLS is applied consistently with platform conventions;
- composite tenant+foreign-key constraints are preferred where the current schema supports them;
- assignment candidates, delegations, calendars, incidents, versions, WorkItems, approvals, and event records are tenant-scoped;
- cross-tenant employee/user, role, position, department, definition, step, sub-workflow, or source-record references fail closed.

Migration and security-negative tests must explicitly attempt cross-tenant reference injection.

---

## 22. API Contracts and Concurrency — AI3

Keep `/api/v1/workflows` backward-compatible during Z3. Existing Map-based responses may be internally replaced by typed records while preserving their JSON contract. Additive fields/endpoints are preferred. A `/v2` API is introduced only if a breaking semantic contract cannot be represented compatibly.

New commands use typed request/response DTOs and a standard error taxonomy, for example:

- validation/configuration error -> 400/422 as established by platform conventions;
- authentication/authorization -> 401/403;
- missing tenant-scoped resource -> 404 or controlled non-enumerating error;
- state/version conflict -> 409;
- idempotent replay -> prior result or explicit replay contract;
- transient system-action failures are runtime incidents, not raw controller 500s.

Mutating commands use optimistic concurrency (`expectedVersion`, resource version, and/or an HTTP conditional model compatible with current clients). Claim, approve, reject, reassign, publish, and incident-resolution races must be deterministic.

Idempotency keys are required for externally retryable mutation boundaries such as API starts and side-effecting integration actions.

---

## 23. Operational Read Model — AL3

The normalized transactional tables remain the source of truth. User-facing high-volume screens may use an optimized read model/index maintained from committed state for:

- My Tasks;
- My Approvals;
- Work Pool;
- workflow definitions/version summaries;
- instance search;
- incidents;
- SLA/monitoring dashboards.

Read-model lag must be measurable. A stale projection must never authorize an action; command handling re-loads authoritative state and revalidates authorization.

---

## 24. UI Information Architecture and Visual Designer — H3 / AP3 / AM3

The `/workflow` product evolves from the current four-tab page into:

```text
Overview
Definitions
My Tasks
Approvals
Instances
Incidents
Monitoring
Settings
```

### 24.1 Definitions and designer

Definition list includes search, module, status, trigger, version, last publication, and health indicators.

Designer uses a visual canvas plus structured inspector:

```text
Canvas / Node Palette / Connections / Mini-map / Zoom
                          |
                          v
Properties
  Details
  Assignment
  Conditions
  Inputs & Outputs
  Form / Module Action
  Approval Policy
  SLA & Escalation
  Notifications
  Retry & Compensation
```

A structured table view is always available for large workflows and auditability. The UI also exposes validation errors, simulation, version history/diff, and publish controls based on capabilities/SoD.

### 24.2 My Tasks

Shows direct and pool work separately, with source entity context, assignee/pool, priority, due/SLA, status, claim/release/reassign actions, form/module-action entry, comments, attachments, and audit/timeline where authorized.

### 24.3 Approvals

Shows requester/source context, policy (`ANY_ONE`/`ALL`), due/SLA, decision history, required reason/comment, and safe approve/reject commands. Seeing an approval does not by itself prove action authorization.

### 24.4 Instances and monitoring

Instance detail is a graph/timeline view of branch tokens, human actions, automated attempts, incidents, sub-workflows, SLA, compensation, and correlation IDs.

Monitoring provides SLA breaches, stuck work, retry/incident queues, outbox lag, worker health, and operational metrics according to permissions.

### 24.5 Localization — AM3

Codes/status identifiers are language-neutral. Labels support Arabic and English. RTL is first-class. Persist UTC timestamps; render in tenant/user timezone while business-time calculation follows the pinned tenant calendar.

---

## 25. Worker and Scalability Model — AQ3

HTTP requests perform short authoritative commands and commit durable intent/state. Timers, schedules, system actions, retries, SLA checks, outbox dispatch, inbox consumption, notification delivery, and compensation execute through durable workers/queues or the platform's equivalent scheduled-worker primitives.

Rules:

- no long external call inside a database transaction;
- claim worker jobs atomically;
- retries are durable, bounded, and observable;
- pagination is required for list/scan APIs;
- scheduler scans use due indexes and bounded batches;
- parallel-join and task-claim updates use compare-and-set/optimistic locking;
- workers are restart-safe and idempotent.

Initial deployment may remain inside the existing Spring Boot service process where operationally appropriate, but domain/port boundaries must allow workers to be split later without redesigning workflow semantics.

---

## 26. Migration and Cutover — Z3 / AA3

### 26.1 Z3: additive controlled evolution

No destructive migration occurs until all migration gates pass.

Sequence:

1. Forensic snapshot of definitions, steps, instances, approvals, audit, capabilities, HR links, and API contracts.
2. Add Y2 columns/tables/constraints without deleting legacy columns.
3. Backfill logical definition family IDs and engine generation safely.
4. Add the Employee/User uniqueness guard and application validation.
5. Add compatibility repositories/adapters and typed DTOs.
6. Build Y2 runtime behind a feature/cutover control.
7. Validate data parity and existing API behavior.
8. Publish Y2 definitions through the new path.
9. Establish cutover timestamp/generation marker.
10. Prevent new LEGACY instance starts after cutover.
11. Let existing legacy instances complete on LEGACY runtime.
12. Retire legacy runtime only after closure gates.

### 26.2 AA3: Strangler cutover

```text
Before cutover instance -> engine_generation=LEGACY -> LEGACY runtime until terminal
After cutover start      -> engine_generation=Y2     -> Y2 runtime only
```

No dual execution for the same instance. No opportunistic live conversion merely because a Y2 version exists.

Legacy retirement requires:

- active legacy instances = 0;
- unresolved legacy incidents = 0;
- audit verification PASS;
- data integrity PASS;
- API/read parity requirements closed;
- rollback requirement closed;
- production monitoring stable for the agreed observation window.

### 26.3 Compatibility capability mapping

Existing `WORKFLOW.WRITE`/`WORKFLOW.ADMIN` remain during migration. Additive migrations map existing trusted roles to the new granular capabilities according to least privilege and existing behavior. Endpoint guards migrate incrementally. Removal/deprecation of coarse capabilities is a later explicit cleanup, not part of the first Y2 cutover.

---

## 27. Test and Release Strategy — AO3

Y2 is accepted only with tests across domain, persistence, API, security, concurrency, migration, and end-to-end behavior.

Required coverage includes:

- employee/user link tenant safety and one-to-one invariant;
- employee without user remains valid HR record but is not actionable;
- hard-disabled user -> `ASSIGNEE_UNAVAILABLE` and no automatic transfer;
- dynamic assignment resolution and frozen candidate snapshot;
- current capability revocation prevents stale-candidate execution;
- direct task and work-pool claim/release/reassign;
- concurrent double-claim and expectedVersion conflict;
- `ANY_ONE`, `ALL`, rejection aggregation, and stale/double approval races;
- default self-approval deny and explicit override path;
- explicit approve/reject graph transitions;
- graph validation, unreachable nodes, invalid fork/join, and cycles;
- condition AST safety/type limits;
- typed input/output mapping;
- business-time SLA across working hours, weekends, holidays, DST/timezone boundaries where relevant;
- planned delegation vs B1 hard-deactivation behavior;
- system-action retry classification, timeout, idempotency, exhausted Incident;
- compensation success/failure and idempotent replay;
- parallel fork/join races;
- sub-workflow version pinning and cycle/depth checks;
- domain-event duplicate delivery -> one instance;
- outbox/inbox retry and dead-letter/Incident behavior;
- notification failure does not roll back workflow state;
- tenant isolation/RLS and cross-tenant reference attacks;
- capability matrix and separation-of-duties negatives;
- published version immutability;
- running instance version pinning;
- legacy API contract compatibility;
- LEGACY/Y2 strangler routing and no dual execution;
- rollback/readiness gates.

Existing tests under `apps/sanad-platform/src/test/java/com/sanad/platform/workflow` are extended rather than replaced. Web tests cover designer validation surfaces, My Tasks, approvals, incidents, permission-sensitive actions, RTL, and compatibility navigation.

Release uses feature flags/cutover controls, staged rollout, production telemetry, and a rollback path that can stop **new Y2 starts** without corrupting already-started Y2 instances.

---

## 28. V1 Scope and Explicit Deferrals — AR3

### 28.1 V1 includes

- immutable workflow versions and publish validation;
- visual/structured designer;
- manual/event/API/schedule triggers;
- graph execution;
- HUMAN_TASK and central WorkItems;
- direct and work-pool assignment;
- Employee/HR/RBAC resolver;
- `ANY_ONE` and `ALL` approvals;
- default self-approval deny;
- safe expressions;
- typed context and mappings;
- business calendars and SLA/escalation;
- planned delegation and explicit reassignment;
- system-action resilience/idempotency;
- incidents and compensation;
- controlled parallel fork/join;
- CALL_WORKFLOW;
- in-app notification + optional email/webhook adapters;
- outbox/inbox reliable eventing;
- audit, metrics, traces, and monitoring;
- backward-compatible migration and strangler cutover.

### 28.2 Deferred

- `QUORUM/N_OF_M` approvals;
- full BPMN semantics and advanced inclusive/event gateways;
- arbitrary user scripting;
- dynamic graph mutation during an instance;
- native SMS/WhatsApp delivery in V1;
- advanced AI-driven autonomous workflow rewriting/optimization;
- cross-tenant workflow execution;
- exactly-once distributed guarantees.

A deferred feature may only enter V1 through explicit scope change and corresponding design/test updates.

---

## 29. Decision Ledger

All architectural choices below are approved. Choices after AB3 were approved by delegated “use the recommended option” authority.

| ID | Approved decision |
|---|---|
| A1 | Optional Employee↔User link; Employee is business assignee identity |
| B1 | Disabled User -> fail closed, `ASSIGNEE_UNAVAILABLE`, manual reassignment |
| C3 | Central WorkItems only for HUMAN_TASK and APPROVAL |
| D3 | Separate assignment resolution from authorization |
| E3 | Approval policies ANY_ONE + ALL; quorum deferred |
| F3 | Explicit approve/reject transitions; rejection reason mandatory |
| G3 | Delegation + explicit fallback + escalation, subject to B1 |
| H3 | Visual designer + structured property editor/table view |
| I3 | Draft -> Validate -> Publish -> Immutable version |
| J3 | Manual + Domain Event + API + Schedule triggers |
| K3 | Notification Orchestrator abstraction; in-app primary |
| L3 | Direct + Work Pool human task assignment |
| M3 | Self-approval denied by default; exceptional explicit override |
| N3 | Eligibility snapshot at step activation + live authorization recheck |
| O3 | Resilient system action execution with retry/idempotency/Incident |
| P3 | Cancellation + explicit business compensation |
| Q2 | ANY_ONE: first approval wins; individual rejection does not fail step |
| R3 | Controlled PARALLEL_FORK / PARALLEL_JOIN |
| S3 | Typed context + explicit input/output mapping |
| T3 | Hybrid task UI: workflow form or source-module action |
| U3 | Safe declarative expression AST; no arbitrary eval |
| V3 | Tenant business calendar + SLA policy engine |
| W3 | Reusable version-aware CALL_WORKFLOW |
| X3 | Transactional outbox + idempotent inbox; at-least-once |
| Y2 | Independent Workflow Orchestration Platform architecture |
| Z3 | Backward-compatible additive evolution and controlled migration |
| AA3 | Strangler cutover; legacy-running/new-Y2 separation |
| AB3 | Fine-grained capabilities + separation of duties |
| AC3 | Append-only business audit separated from technical telemetry |
| AD3 | Strict tenant/org isolation and server-derived tenant context |
| AE3 | Archive/retention policy; no normal hard-delete of historical evidence |
| AF3 | First-class Workflow Incident model |
| AG3 | Operational metrics/logs/traces and workflow SLIs |
| AH3 | Audited break-glass commands without approval impersonation |
| AI3 | Typed/versioned APIs, concurrency, idempotency, compatibility |
| AJ3 | Server-side security, safe schemas, signed integrations, anti-replay |
| AK3 | Durable signed webhook/integration delivery and retry logging |
| AL3 | Optimized read model for task/approval/instance/monitoring queries |
| AM3 | UTC persistence + timezone/business-calendar rendering; Arabic/English RTL |
| AN3 | Mandatory publish validator + zero-side-effect simulation |
| AO3 | Full domain/integration/security/concurrency/migration/E2E release gates |
| AP3 | Workflow IA: Overview, Definitions, My Tasks, Approvals, Instances, Incidents, Monitoring, Settings |
| AQ3 | Durable workers/queues, short transactions, locking, pagination |
| AR3 | V1 core fixed; quorum/full BPMN/arbitrary scripting/etc. deferred |

---

## 30. Implementation Ordering Constraints

This document is a design specification, not the implementation plan. The subsequent writing-plans phase must decompose implementation into small verified waves. The following dependency order is normative for planning:

1. Baseline/forensics and migration safety tests.
2. Identity-link invariant and granular RBAC foundation.
3. Immutable version/family model and graph/publish validator.
4. Y2 instance/step runtime primitives and explicit transitions.
5. Assignment resolver, snapshots, WorkItems, My Tasks.
6. Approval aggregation and self-approval policy.
7. Typed context/forms/expressions.
8. Business calendar/SLA/delegation/escalation.
9. Durable system actions, attempts, incidents, compensation.
10. Parallelism and sub-workflows.
11. Outbox/inbox triggers and notification orchestration.
12. Observability/read models/monitoring.
13. Visual designer and operational UI completion.
14. Migration rehearsal, strangler cutover, production acceptance.

No wave may bypass tenant-isolation, RBAC, idempotency, or audit tests merely because the corresponding UI is not yet implemented.

---

## 31. Acceptance Definition

The Y2 Workflow module is considered implemented only when all of the following are true:

- published definitions are immutable and new drafts create new versions;
- running instances remain pinned to their version;
- employee tasks and approvals resolve to real Employees and execute only through currently authorized active linked Users;
- My Tasks and Approvals are operational, not merely display lists;
- assignment, claim, release, reassign, delegation, approval, rejection, escalation, incident, retry, cancellation, and compensation are auditable;
- graph conditions, parallel joins, and sub-workflows are deterministic under concurrency;
- system actions are durable and idempotent;
- business-time SLA is correct for tenant calendars/timezones;
- duplicate events/API retries cannot duplicate instances or side effects;
- notification delivery failures do not corrupt workflow state;
- no cross-tenant read/write/reference path is demonstrated in security testing;
- existing legacy API behavior remains compatible until explicitly retired;
- pre-cutover legacy instances complete only on LEGACY runtime and post-cutover starts use Y2 only;
- production monitoring can identify stuck work, incidents, SLA breaches, retries, event lag, and worker degradation;
- all required backend and web tests pass under the repository's governed PostgreSQL/direct CI path;
- rollback/stop-new-start controls are proven before production cutover.

---

## 32. Final Architectural Position

SNAD Workflow Y2 is a **modular orchestration core**, not a general-purpose BPMN clone and not embedded business logic inside every module. It coordinates domain-owned actions through typed contracts, resolves human work through HR identity, authorizes through existing RBAC, persists durable process state in PostgreSQL, and makes every consequential transition observable and auditable.

The existing Workflow engine is retained as the migration foundation. Y2 is introduced additively, then becomes the only engine for new starts after controlled cutover. Legacy execution is retired only when its remaining instances and incidents reach verified closure.

**Design phase result:** approved architecture and policies are complete. Implementation planning begins only after review/approval of this written specification.
