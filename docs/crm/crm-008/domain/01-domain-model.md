# CRM-008 — Domain Model

> 10 aggregates, designed hexagonally. Pure-domain contracts only — no infrastructure, no controllers.

Each aggregate follows the existing SANAD CRM pattern (see `crm/note/domain/NoteRepository.java`, `crm/task/domain/TaskRepository.java`): a domain port interface + a value object/aggregate record/class. No JDBC, no Spring annotations in the `domain` package.

---

## Aggregate 1: `SalesTeam`

Represents a sales team within a tenant.

### Identity
- `id: UUID` (assigned by repository, never reused)
- `tenantId: UUID` (immutable from creation)
- `code: String` (tenant-unique, e.g. `KSA-CENTRAL-ENT`)

### Attributes
- `displayName: String` (max 200)
- `description: String?` (max 1000)
- `status: TeamStatus` ∈ {`ACTIVE`, `SUSPENDED`, `ARCHIVED`}
- `managerUserId: UUID?` (must be ACTIVE user in same tenant when set)
- `defaultQueueId: UUID?` (FK to `Queue`, optional)
- `defaultTerritoryId: UUID?` (FK to `Territory`, optional)
- `createdAt, updatedAt: Instant`

### Invariants
- One ACTIVE manager per team (DB partial unique index on `(tenant_id, id) WHERE manager_user_id IS NOT NULL AND status='ACTIVE'`)
- Manager must be an ACTIVE user in the same tenant (enforced by `OwnerValidationPort` at write time)
- A user can be the manager of at most one ACTIVE team per tenant (partial unique index)
- SUSPENDED teams cannot receive new assignments (assignment engine checks team status)
- ARCHIVED teams must have all memberships ended first (FK with `ON DELETE RESTRICT` semantic)

### Events
- `SalesTeamCreated(tenantId, teamId, code, displayName, managerUserId)`
- `SalesTeamManagerChanged(tenantId, teamId, prevManagerUserId, newManagerUserId, actor, reason)`
- `SalesTeamStatusChanged(tenantId, teamId, prevStatus, newStatus, actor, reason)`

---

## Aggregate 2: `TeamMembership`

Links a user to a team with a role and time-bounded membership.

### Identity
- `id: UUID`
- `tenantId: UUID`
- `teamId: UUID` (FK to SalesTeam, composite `(tenant_id, team_id)`)
- `userId: UUID` (FK to users, composite `(tenant_id, user_id)`)

### Attributes
- `role: TeamRole` ∈ {`SALES_MANAGER`, `ACCOUNT_MANAGER`, `SALES_REPRESENTATIVE`, `LEAD_QUALIFIER`, `OPPORTUNITY_SPECIALIST`, `READONLY_CONTRIBUTOR`}
- `isPrimary: boolean` (a user has at most one PRIMARY membership per tenant)
- `status: MembershipStatus` ∈ {`ACTIVE`, `ENDED`}
- `joinedAt: Instant`
- `leftAt: Instant?` (null while ACTIVE)
- `leftReason: String?` (e.g. `MANUAL_REMOVAL`, `TEAM_ARCHIVED`, `USER_DEACTIVATED`)
- `capacityMax: int` (max concurrent queue items this member can hold; default 50)
- `metadata: Map<String, String>` (e.g. `language=ar`, `skills=enterprise,saas`)

### Invariants
- A user has at most one PRIMARY membership per tenant (partial unique index `WHERE is_primary=true AND status='ACTIVE'`)
- A user can have multiple non-primary ACTIVE memberships (across different teams)
- `joinedAt < leftAt` when `leftAt` is not null
- `capacityMax >= 0` and `<= 1000` (sanity bound)
- When `TeamMembership.status = ENDED`, the membership no longer counts toward workload

### Events
- `TeamMembershipStarted(tenantId, membershipId, teamId, userId, role, isPrimary)`
- `TeamMembershipRoleChanged(...)`
- `TeamMembershipEnded(tenantId, membershipId, teamId, userId, leftAt, leftReason)`

---

## Aggregate 3: `Queue`

A container for unassigned or shared CRM records.

### Identity
- `id: UUID`
- `tenantId: UUID`
- `code: String` (tenant-unique)

### Attributes
- `displayName: String`
- `recordType: QueueRecordType` ∈ {`LEAD`, `OPPORTUNITY`, `TASK`, `ACTIVITY`, `ACCOUNT`} (one queue per record type — `Queue` is type-scoped)
- `description: String?`
- `status: QueueStatus` ∈ {`ACTIVE`, `DRAINING`, `ARCHIVED`}
- `maxItemsPerUser: int` (claim capacity per user; default 10)
- `slaMinutes: int?` (target aging; null = no SLA)
- `escalationTargetQueueId: UUID?` (FK to another Queue, for SLA breach)
- `defaultOwnerId: UUID?` (optional: fallback if no rule matches)

### Invariants
- A queue has exactly one `recordType` (immutable from creation)
- A queue in `DRAINING` status accepts no new items but allows existing items to be claimed
- A queue in `ARCHIVED` status accepts no claims and no new items
- `maxItemsPerUser` cannot exceed the queue members' individual `capacityMax`
- Self-reference in `escalationTargetQueueId` is forbidden (DB CHECK)

### Events
- `QueueCreated(...)`, `QueueStatusChanged(...)`, `QueueItemAdded(...)`, `QueueItemClaimed(...)`, `QueueItemReleased(...)`

---

## Aggregate 4: `QueueMembership`

### Identity
- `id: UUID`
- `tenantId: UUID`
- `queueId: UUID` (FK composite `(tenant_id, queue_id)`)
- `userId: UUID` (FK composite `(tenant_id, user_id)`)

### Attributes
- `status: QueueMembershipStatus` ∈ {`ACTIVE`, `REMOVED`}
- `addedAt: Instant`
- `removedAt: Instant?`
- `removedReason: String?`

### Invariants
- A user can be a member of multiple queues
- Only ACTIVE members can claim items
- Removing a member with active claimed items is blocked — items must be released first (or the system force-releases them with audit)

---

## Aggregate 5: `Territory`

A hierarchical geographic or segment-based scope.

### Identity
- `id: UUID`
- `tenantId: UUID`
- `code: String` (tenant-unique)

### Attributes
- `displayName: String`
- `parentId: UUID?` (self-reference; null = root)
- `description: String?`
- `status: TerritoryStatus` ∈ {`ACTIVE`, `ARCHIVED`}
- `ruleType: TerritoryRuleType` ∈ {`GEOGRAPHIC`, `SEGMENT`, `CHANNEL`, `ACCOUNT_LIST`}
- `ruleDefinition: JSONB` (structured rule: e.g. `{"country":"SA","region":"Riyadh"}`)
- `priority: int` (higher wins on overlap)

### Invariants
- Hierarchy is acyclic — enforced via closure table (`crm_territory_closure`) with cycle detection at insert/update
- A territory cannot be its own parent (CHECK)
- `parentId` must be in same tenant and ACTIVE
- ARCHIVED territories cannot have new assignments
- Children of an ARCHIVED territory must also be ARCHIVED (recursive check)

### Closure Table: `crm_territory_closure`
- `tenant_id`, `ancestor_id`, `descendant_id`, `depth`
- Maintained by trigger or application-layer transactional update on insert/update of `parentId`
- Cycle detection: before commit, verify `descendant_id` is not in the ancestor chain

---

## Aggregate 6: `TerritoryAssignment`

Links a team or user to a territory.

### Identity
- `id: UUID`
- `tenantId: UUID`
- `territoryId: UUID` (FK composite)
- `assigneeType: AssigneeType` ∈ {`USER`, `TEAM`}
- `assigneeId: UUID` (FK to users OR sales_teams, polymorphic — no DB FK, validated in app)

### Attributes
- `role: TerritoryRole` ∈ {`PRIMARY`, `BACKUP`, `OBSERVER`}
- `priority: int` (for overlap resolution)
- `status: TerritoryAssignmentStatus` ∈ {`ACTIVE`, `INACTIVE`}
- `effectiveFrom: Instant`
- `effectiveTo: Instant?`

### Invariants
- A territory can have multiple ACTIVE assignments (overlap allowed, resolved by priority at assignment time)
- A (territory, assignee, role) tuple is unique among ACTIVE assignments
- `effectiveFrom <= effectiveTo` when `effectiveTo` is not null
- Polymorphic `assigneeId` is validated by app: if `assigneeType=USER`, check `users` table; if `TEAM`, check `sales_teams` table

---

## Aggregate 7: `AssignmentRule`

A versioned distribution rule.

### Identity
- `id: UUID`
- `tenantId: UUID`
- `code: String` (tenant-unique across all versions of the same rule)

### Attributes (current version)
- `version: int` (monotonically increasing per rule code)
- `displayName: String`
- `description: String?`
- `recordType: RuleRecordType` ∈ {`LEAD`, `OPPORTUNITY`, `TASK`, `ACTIVITY`, `ACCOUNT`}
- `priority: int` (lower = evaluated first)
- `matchConditions: JSONB` (e.g. `{"source":"web_form","country":"SA"}`)
- `distributionMethod: DistributionMethod` ∈ {`DIRECT_OWNER`, `TEAM_ASSIGNMENT`, `QUEUE_ASSIGNMENT`, `ROUND_ROBIN`, `LEAST_LOADED`, `WEIGHTED`, `TERRITORY_BASED`, `SKILL_BASED`, `RULE_CHAIN`}
- `targetOwnerId: UUID?` (for DIRECT_OWNER)
- `targetTeamId: UUID?` (for TEAM_ASSIGNMENT, ROUND_ROBIN within team)
- `targetQueueId: UUID?` (for QUEUE_ASSIGNMENT)
- `fallbackOwnerId: UUID?` (when no match)
- `status: RuleStatus` ∈ {`ACTIVE`, `INACTIVE`, `DEPRECATED`}
- `effectiveFrom: Instant`
- `effectiveTo: Instant?`
- `createdBy: UUID`
- `createdAt: Instant`

### Rule Versions Table: `crm_assignment_rule_versions`
- Stores full history of each rule version (snapshots)
- Only one row per `(tenant_id, code)` may have `status=ACTIVE` at a time (partial unique index)

### Invariants
- A rule code is immutable across versions
- Exactly one ACTIVE version per `(tenant_id, code)` (partial unique index `WHERE status='ACTIVE'`)
- Rule evaluation order: `(tenant_id, priority ASC, created_at ASC)` — deterministic
- Simulating a rule does not mutate state — `simulate` endpoint runs the rule against a sample input and returns the would-be owner without persisting

### Events
- `AssignmentRuleCreated`, `AssignmentRuleVersionIncremented`, `AssignmentRuleActivated`, `AssignmentRuleDeprecated`

---

## Aggregate 8: `Assignment`

The effective or historical assignment of a CRM record.

### Identity
- `id: UUID`
- `tenantId: UUID`
- `recordType: AssignmentRecordType` ∈ {`ACCOUNT`, `CONTACT`, `LEAD`, `OPPORTUNITY`, `ACTIVITY`, `TASK`}
- `recordId: UUID` (FK polymorphic — no DB FK, validated by app against the appropriate CRM table)

### Attributes
- `ownerType: OwnerType` ∈ {`USER`, `TEAM`, `QUEUE`}
- `ownerUserId: UUID?` (set when `ownerType=USER`)
- `ownerTeamId: UUID?` (set when `ownerType=TEAM`)
- `ownerQueueId: UUID?` (set when `ownerType=QUEUE`)
- `assignedByRuleId: UUID?` (set when assignment was automatic)
- `assignedByUserId: UUID` (the actor who triggered the assignment, even if rule-driven)
- `reason: String` (e.g. `MANUAL`, `RULE_MATCH`, `ROUND_ROBIN`, `QUEUE_CLAIM`, `TRANSFER`)
- `correlationId: UUID` (links to ownership_history + audit_events)
- `workflowResult: JSONB?` (e.g. `{"workflow_run_id":"...","status":"approved"}`)
- `status: AssignmentStatus` ∈ {`ACTIVE`, `SUPERSEDED`, `ENDED`}
- `effectiveFrom: Instant`
- `effectiveTo: Instant?` (null = open-ended; non-null = temporary)
- `createdAt: Instant`

### Invariants
- Exactly one ACTIVE assignment per `(tenant_id, record_type, record_id)` at any instant — enforced at **both** database layer (PostgreSQL partial unique index `WHERE status='ACTIVE'`) AND application layer (transactional SUPERSEDE-then-INSERT). See STAGE-REPORT-CRM-008A.md §4.7 for the full three-layer enforcement model (DATABASE_ENFORCED + APPLICATION_ENFORCED + CONCURRENCY_TESTED).
- When `ownerType=USER`, `ownerUserId` must be an ACTIVE user in the tenant
- When `ownerType=TEAM`, `ownerTeamId` must be an ACTIVE team in the tenant
- When `ownerType=QUEUE`, `ownerQueueId` must be an ACTIVE queue in the tenant
- `effectiveFrom <= effectiveTo` when `effectiveTo` is not null
- A new assignment automatically SUPERSEDES the previous one (handled transactionally)

### Side-effect on CRM tables
- When an Assignment becomes ACTIVE with `ownerType=USER`, the corresponding `crm_<table>.owner_user_id` column is updated to the new `ownerUserId` (for read-path fast lookup and backward compatibility with existing queries)
- When `ownerType=TEAM` or `QUEUE`, `owner_user_id` is set to NULL and a separate `crm_<table>.owner_team_id` or `owner_queue_id` column is updated (these columns are added in V20260720_5)

---

## Aggregate 9: `TransferRequest`

A formal request to transfer ownership, with approval workflow.

### Identity
- `id: UUID`
- `tenantId: UUID`
- `recordType: TransferRecordType` ∈ {`ACCOUNT`, `LEAD`, `OPPORTUNITY`} (tasks and activities follow simpler reassign flow, not full transfer)
- `recordIds: UUID[]` (array — bulk transfers supported)

### Attributes
- `requesterUserId: UUID` (who initiated)
- `currentOwnerUserId: UUID?` (denormalized at request time for audit)
- `proposedOwnerUserId: UUID` (the new owner)
- `proposedOwnerTeamId: UUID?` (alternative: target team)
- `transferType: TransferType` ∈ {`PERMANENT`, `TEMPORARY`}
- `temporaryEndDate: Instant?` (set when `transferType=TEMPORARY`)
- `reason: String` (e.g. `TERRITORY_CHANGE`, `ABSENCE`, `WORKLOAD_BALANCE`, `PROMOTION`)
- `policy: TransferPolicy` ∈ {`SINGLE_APPROVER`, `MULTI_APPROVER`, `NO_APPROVAL_REQUIRED`}
- `state: TransferState` ∈ {`DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED`, `FAILED`}
- `currentApprovalStep: int?` (0-indexed, used for multi-approver)
- `executedAt: Instant?`
- `executedByUserId: UUID?`
- `failureReason: String?`

### State Machine
```
DRAFT ──submit──> SUBMITTED ──review──> UNDER_REVIEW
                                              │
                      ┌───────────────────────┼──────────────────┐
                      ▼                       ▼                  ▼
                  APPROVED                REJECTED            CANCELLED
                      │                       │
                      ▼                       └─> terminal
                  COMPLETED ─────────────────┘
                      │
                      ▼
                  (terminal)
                  (or FAILED if execution fails)
```

### Transfer Steps Table: `crm_transfer_steps`
- One row per approval step (for multi-approver)
- `transfer_request_id, step_number, approver_user_id, decision, decided_at, comment`

### Invariants
- Separation of Duties (AC-06): if `policy=SINGLE_APPROVER` or `MULTI_APPROVER`, the requester cannot be the approver
- Atomic execution (AC-05): all `recordIds` succeed or all fail — DB transaction
- On `COMPLETED`, a new `Assignment` row is created and the old one is SUPERSEDED
- On `COMPLETED`, an `OwnershipHistory` row is appended (immutable)
- `FAILED` state allows retry — the request can be re-submitted to `DRAFT` for correction

---

## Aggregate 10: `OwnershipHistory`

The append-only immutable ledger of all ownership changes.

### Identity
- `id: UUID` (surrogate)
- `tenantId: UUID`
- `recordType: OwnershipRecordType`
- `recordId: UUID`

### Attributes
- `fromOwnerType: OwnerType?` (null for initial assignment)
- `fromOwnerUserId: UUID?`
- `fromOwnerTeamId: UUID?`
- `fromOwnerQueueId: UUID?`
- `toOwnerType: OwnerType`
- `toOwnerUserId: UUID?`
- `toOwnerTeamId: UUID?`
- `toOwnerQueueId: UUID?`
- `changeType: OwnershipChangeType` ∈ {`INITIAL`, `REASSIGN`, `TRANSFER`, `QUEUE_CLAIM`, `QUEUE_RELEASE`, `TEMPORARY`, `RESTORE`, `BULK`}
- `triggerSource: TriggerSource` ∈ {`MANUAL`, `RULE`, `TRANSFER_REQUEST`, `WORKFLOW`, `ABSENCE_POLICY`}
- `triggerReferenceId: UUID?` (e.g. the TransferRequest ID, or AssignmentRule ID)
- `actorUserId: UUID`
- `reason: String`
- `correlationId: UUID` (links to `platform_audit_logs`)
- `effectiveAt: Instant`
- `recordedAt: Instant`

### Invariants
- **Append-only**: no UPDATE or DELETE ever (enforced by DB role revocation of UPDATE/DELETE on this table)
- Insert is allowed only inside the same transaction as the `Assignment` change it documents
- `correlationId` is mandatory and must match the assignment's `correlationId`
- A query for ownership history returns rows ordered by `effectiveAt ASC` — earliest first

### API
- Read-only via `GET /api/v2/crm/ownership-history?recordType=&recordId=`
- Pagination via cursor (tenant-leading, `tenant_id, record_type, record_id, effective_at DESC`)
- Tenant isolation: WHERE clause always includes `tenant_id = :authenticatedTenantId`

---

## Cross-cutting invariants (all 10 aggregates)

1. Every table has `tenant_id UUID NOT NULL` as the first column.
2. Every FK is composite `(tenant_id, parent_id)` — prevents cross-tenant references at the DB level.
3. Every index leads with `tenant_id` — tenant-leading indexes for fast tenant-scoped queries.
4. `id` is always `UUID` (CONSTITUTION §3.2 — no BIGSERIAL).
5. All FK constraints are named `fk_<child>_<parent>`.
6. Audit columns (`created_at`, `updated_at`, `created_by`, `updated_by`) on all mutable tables.
7. `OwnershipHistory` is exempt from `updated_at` (immutable — no UPDATE).
8. No `tenant_id` from request body (CONSTITUTION §3.4) — read from `SecurityContext`.
