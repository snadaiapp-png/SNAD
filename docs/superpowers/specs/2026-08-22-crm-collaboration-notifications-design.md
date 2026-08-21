# SNAD CRM Collaboration, Notifications, SLA, and Unified Activity Design Spec

> **Date:** 2026-08-22
> **Status:** DESIGN APPROVED — WRITTEN SPEC REVIEW PENDING
> **Baseline SHA:** `ffb856fa9b7ffb2a7294d8a5094937150f74841b`
> **Repository:** `snadaiapp-png/SNAD`
> **Scope:** CRM Contacts, Tasks, Cases, Notes, Collaboration, Notifications, SLA, Unified UX, Security, Migration, and Production Acceptance

---

## 1. Purpose

This specification consolidates the ten architecture sections approved in the design discussion into one implementation baseline. It extends the existing SNAD CRM without replacing the current domain structure and without introducing a parallel notification or workflow stack.

The target result is a tenant-safe collaboration model in which CRM records have clear ownership, controlled collaboration, immutable operational history, durable in-app notifications, optional email/WhatsApp delivery, support-case SLA management, and a consistent web/mobile user experience.

### 1.1 Architectural choice

The approved architecture is:

**Unified Collaboration Core + Event-Driven Notifications.**

Shared collaboration primitives are reused by Contacts, Tasks, and Cases. Domain-specific lifecycle rules remain inside each domain. PostgreSQL remains the source of truth; the system does not become event sourced.

### 1.2 Non-goals

This scope does not introduce:

- a general-purpose workflow engine;
- a generic entity service that owns all CRM behavior;
- a Slack-like chat subsystem;
- a full privacy-management/DSAR platform;
- payroll, accounting, ERP financial posting, or HR administration;
- Docker/Testcontainers as a new required test path;
- provider-specific WhatsApp logic inside CRM domain code.

---

## 2. Design invariants

The following rules are normative and must not be weakened during implementation:

1. A CRM entity has one primary owner/assignee; collaboration never creates a second owner.
2. `Owner`, `Collaborator`, `Watcher`, and `Reviewer` are distinct concepts.
3. Participation never grants permissions that RBAC does not already allow.
4. Transfer changes ownership; Share adds collaborators; Follow adds watchers.
5. The previous owner becomes a watcher by default after transfer, unless explicitly disabled for that operation.
6. In-app SNAD notification is mandatory for qualifying notification events.
7. Email and WhatsApp are optional delivery channels and may be individually selected or combined.
8. External delivery failure never rolls back a committed CRM business operation.
9. System timeline events are immutable; user-authored notes/comments are versioned and soft-deletable.
10. Audit is separate from the user-facing timeline.
11. Sensitive commands are explicit business commands, not generic field patches.
12. Mutating commands use optimistic concurrency via `expectedVersion` where the entity supports versioning.
13. Tenant identity is derived from trusted security context, never trusted from request payload.
14. PostgreSQL state is the system of record; events support integration, notification, timeline, and audit correlation.
15. Migrations are forward-only, additive-first, tenant-aware, and data-safe.
16. PostgreSQL Direct remains the governing database test path.

---

## 3. Collaboration Core

### 3.1 Primary ownership

Primary ownership remains on the domain entity, for example:

- Task: `assignee_user_id` / current domain owner field.
- Case: `assignee_user_id` / current domain owner field.
- Contact: the domain owner field introduced or reused by the Contact model.

The collaboration table must not duplicate `OWNER` as an active participant role.

### 3.2 Secondary participation

A shared participant relation stores active collaborators and watchers:

```text
crm_entity_participants
-----------------------
id
tenant_id
entity_type
entity_id
user_id
role                    -- COLLABORATOR | WATCHER
added_by_user_id
added_at
removed_by_user_id
removed_at
version
```

The implementation must enforce tenant scoping and prevent duplicate active participation for the same logical relation.

### 3.3 Eligibility

Transfer, share, watcher, reviewer, note-recipient, and mention pickers only return users who satisfy the applicable rule set, including:

- same tenant;
- active employee/user;
- not deleted or disabled;
- required CRM/entity access;
- action-specific eligibility.

Frontend filtering improves UX; backend validation remains authoritative.

### 3.4 Transfer semantics

A transfer is an explicit command that:

1. authenticates and authorizes the actor;
2. validates tenant and recipient eligibility;
3. validates entity state and expected version;
4. changes the primary owner;
5. optionally adds the previous owner as a watcher, default `true`;
6. records timeline and audit facts;
7. creates a notification intent/outbox event;
8. increments the entity version;
9. commits atomically.

### 3.5 Share and watch semantics

Share preserves the owner and adds one or more collaborators. Watch preserves owner and collaborators and adds a watcher. Collaborators cannot automatically transfer ownership, hard-delete records, manage other collaborators, or perform other sensitive operations unless independent RBAC capabilities grant those actions.

---

## 4. Task lifecycle, review, and activity

### 4.1 Lifecycle

The approved task state model is:

```text
OPEN
  -> IN_PROGRESS
      -> WAITING_REVIEW -> COMPLETED
      -> COMPLETED        (owner path when no reviewer is configured)
      -> BLOCKED -> IN_PROGRESS
      -> CANCELLED
```

`OVERDUE` is calculated from `due_at` and terminal state; it is not stored as a task status.

### 4.2 Roles

Each Task supports:

- exactly one owner/assignee;
- zero or more collaborators;
- zero or more watchers;
- zero or one reviewer.

### 4.3 Review rules

If no reviewer is configured, the owner may complete the task directly if RBAC permits. A collaborator does not close another user's task solely because they are a collaborator.

If a reviewer is configured, completion requires `WAITING_REVIEW`. The reviewer may approve to `COMPLETED` or reject to `IN_PROGRESS`. Rejection requires a non-empty review reason/comment.

### 4.4 Block and cancel

`BLOCKED` requires a reason and records blocker/timestamp. Returning from `BLOCKED` goes to `IN_PROGRESS`. Being blocked does not automatically stop due-date aging.

`CANCELLED` requires a cancellation reason and records actor/timestamp.

### 4.5 Timeline

Task activity is presented as one ordered timeline containing structured system events and human comments. Representative system events include:

- `TASK_CREATED`
- `TASK_STARTED`
- `TASK_OWNER_TRANSFERRED`
- `TASK_COLLABORATOR_ADDED`
- `TASK_WATCHER_ADDED`
- `TASK_BLOCKED`
- `TASK_UNBLOCKED`
- `TASK_REVIEW_REQUESTED`
- `TASK_REVIEW_APPROVED`
- `TASK_REVIEW_REJECTED`
- `TASK_DUE_DATE_CHANGED`
- `TASK_PRIORITY_CHANGED`
- `TASK_CANCELLED`
- `TASK_COMPLETED`

User comments are versioned; edits preserve prior versions and display an edited indicator. Deletion is soft deletion and does not erase the audit event.

---

## 5. Contact / Customer lifecycle

### 5.1 Contact to Lead

`Contact -> Lead` is non-destructive. The source Contact remains active unless separately archived. A created Lead keeps an explicit relation back to the source Contact, such as `source_contact_id` or an equivalent domain relation.

The conversion preserves historical context and records linked timeline events on both Contact and Lead.

### 5.2 Duplicate conversion policy

The first implementation must not silently create a second active Lead from the same Contact. If an active linked Lead already exists, the conversion command returns the existing Lead reference and a conflict/business warning suitable for the UI. Multi-lead creation from one Contact is outside the first implementation scope and requires a later explicit policy.

### 5.3 Contact collaboration

Contacts participate in the same owner/collaborator/watcher model as Tasks and Cases.

### 5.4 Archive, restore, and permanent delete

These are three separate operations:

- **Archive:** normal operational removal from active views while preserving history and relations.
- **Restore:** returns an archived Contact to active use and records the action.
- **Hard Delete:** exceptional, highly privileged, impact-analyzed operation.

Hard delete must run a deletion-impact analysis against linked CRM, financial, audit, compliance, and other protected references. Protected dependencies block permanent deletion. Admin privilege does not override mandatory retention or referential-integrity rules.

Hard delete must not erase the fact that deletion occurred. Audit retains the operational fact while avoiding unnecessary retained PII.

Privacy erasure/anonymization remains a separate future capability and must not be conflated with business-entity deletion.

---

## 6. Enterprise Notification Platform

### 6.1 Notification model

A Notification is the durable SNAD record a user is expected to see. Delivery is a per-channel attempt to carry that notification externally.

For every qualifying event:

```text
In-App   = mandatory
Email    = optional
WhatsApp = optional
```

The in-app Notification record is the durable source of truth. Email and WhatsApp are asynchronous deliveries.

### 6.2 Core records

The platform uses concepts equivalent to:

```text
notifications
notification_delivery
notification_preferences
notification_outbox
```

Representative notification fields include tenant, recipient, event type, priority, entity reference, actor, localization/template keys, safe payload, timestamps, and read state.

Delivery records include channel, provider, attempt count, status, provider message ID, timestamps, and failure classification.

### 6.3 Delivery states

External channels support states equivalent to:

- `PENDING`
- `SENDING`
- `DELIVERED`
- `RETRYABLE_FAILURE`
- `PERMANENT_FAILURE`
- `SKIPPED`

Retries use bounded exponential backoff. Permanent failures are not retried indefinitely. Duplicate outbox processing must not produce duplicate successful channel deliveries.

### 6.4 Preferences and operation overrides

In-app cannot be disabled. Employee preferences supply defaults for Email and WhatsApp. Transfer/share/note operations may override those external defaults for that operation only, provided the channel is available and the recipient is eligible.

### 6.5 WhatsApp

WhatsApp is implemented behind a provider adapter and is not coupled to CRM domain code. A provider such as Meta WhatsApp Cloud API may be the first adapter, but the business contract remains provider-neutral.

Employee WhatsApp identity supports:

- `NOT_CONFIGURED`
- `PENDING_VERIFICATION`
- `VERIFIED`
- `DISABLED`

Only `VERIFIED` numbers are eligible for delivery. Changing the number invalidates prior verification.

WhatsApp messages are low-context alerts with a secure/deep link into SNAD; sensitive CRM detail and attachments are not sent by default.

### 6.6 Email

Email uses managed templates and safe field classification. It may contain more context than WhatsApp but must not include fields classified as never-external. Provider failures remain isolated from the business transaction.

### 6.7 Notification Center

The SNAD shell exposes a central bell/unread count and a full notification center supporting read/unread state, filtering, priority, actor, entity, timestamp, and deep links. Deep links never bypass authorization.

---

## 7. Customer Support Cases, SLA, and escalation

### 7.1 Case lifecycle

The approved Case lifecycle is:

```text
OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED
```

Reopen is allowed from `RESOLVED` or `CLOSED` back to `IN_PROGRESS` and requires a reopen reason.

`RESOLVED` requires `resolution_summary`, `resolved_by`, and `resolved_at`. `CLOSED` is a distinct post-resolution closure step.

### 7.2 First Response SLA

First-response time is recorded by an explicit qualifying support-response action/event. Assignment, transfer, or status changes do not count as first response. The initial first-response timestamp for an SLA cycle is not rewritten by later note edits.

### 7.3 Resolution SLA

Resolution SLA measures from the policy-defined cycle start to `RESOLVED`, not `CLOSED`.

### 7.4 Priority and policy

Cases support `LOW`, `MEDIUM`, `HIGH`, and `URGENT`. First-response and resolution targets are tenant-configurable and versioned. Changing priority recalculates the target from the original SLA cycle start using the new policy; it does not reset elapsed time.

### 7.5 Business calendar

Each tenant has one or more policy-referenced business calendars with:

- timezone;
- working days;
- one or more working windows per day;
- weekends;
- holidays;
- exceptional closures;
- support for 24x7 policy mode where configured.

SLA cycles reference policy/calendar versions so historical reporting remains explainable after configuration changes. Calendar logic must support windows that cross midnight and timezones with DST even if the default tenant timezone does not currently observe DST.

### 7.6 SLA states

SLA state is calculated independently from Case status:

- `ON_TRACK`
- `AT_RISK`
- `BREACHED`

The threshold for `AT_RISK` is policy-driven. Breach and escalation events are idempotent.

### 7.7 Pause model

SLA pause is independent of Case status. Pause reasons are controlled policy codes such as:

- `WAITING_FOR_CUSTOMER`
- `WAITING_FOR_THIRD_PARTY`
- `INTERNAL_BLOCKED`

Each reason separately declares whether it pauses First Response SLA and/or Resolution SLA. `INTERNAL_BLOCKED` does not pause SLA by default. Each pause/resume interval records actor, reason, comment, and timestamps.

### 7.8 Escalation

Escalation supports ordered steps and dynamic targets such as case owner, owner manager, support supervisor, role, or specific user. `AT_RISK`, `BREACHED`, and subsequent escalation steps generate timeline/audit/notification events. All affected recipients receive in-app notifications; Email/WhatsApp follow recipient/channel policy.

### 7.9 Reopen cycles

Reopen never erases prior SLA facts. It creates a subsequent resolution cycle and preserves earlier first-response/resolution timings and breach history. `reopen_count` is measurable.

---

## 8. Notes, mentions, and collaborative activity

### 8.1 Note semantics

A Note is not a Task and does not have a separate owner or workflow. Notes may attach to supported CRM subjects including Contact, Lead, Account, Opportunity, Task, Case, and other domain subjects as the existing model allows.

### 8.2 Visibility

Initial visibility levels are:

- `TEAM`: visible to users who already have authorized access to the parent entity.
- `PRIVATE`: visible to the author, explicit recipients, and explicitly privileged users according to RBAC.

Being a collaborator or watcher alone does not grant access to a private note.

### 8.3 Sharing and mentions

A Note may be explicitly shared with one or more employees. `NOTE_SHARED` and `USER_MENTIONED` are different event types. Mentions are stored as user references, not just parsed display text.

If the same command shares a Note with a user and mentions that same user, the notification layer deduplicates the resulting user-facing alert for that operation.

### 8.4 Versioning and replies

User-authored notes retain version history. Soft deletion preserves audit facts. Simple threaded replies are supported through an explicit parent relation; this feature does not become general chat.

### 8.5 Search and leakage prevention

Search, snippets, counts, and autocomplete must enforce tenant, parent access, note visibility, and soft-delete state. Unauthorized private-note metadata must not leak through search results.

---

## 9. Timeline and audit

### 9.1 Timeline

A shared timeline model stores structured operational events:

```text
crm_timeline_events
-------------------
id
tenant_id
entity_type
entity_id
event_type
actor_user_id
occurred_at
summary_key
metadata_json
correlation_id
causation_id
schema_version
```

Localization is driven by stable event/summary keys; the database is not limited to storing finalized Arabic prose.

`metadata_json` is for event-specific facts and must not become an unstructured replacement for queryable schema.

### 9.2 Audit separation

Security/administrative audit is distinct from the timeline. Audit records actor, tenant, command, request/correlation IDs, timestamp, and relevant before/after references or facts. Application APIs do not expose update/delete operations for immutable audit/system events.

### 9.3 Correlation

A business command, timeline event, audit event/reference, notification, and external deliveries share a correlation identifier so an operation can be traced end to end.

---

## 10. Command and API contracts

### 10.1 Explicit commands

Sensitive behavior uses action endpoints/commands rather than generic PATCH operations. Representative commands include:

- `TransferTask`
- `ShareTask`
- `AddTaskWatcher`
- `RequestTaskReview`
- `ApproveTaskReview`
- `RejectTaskReview`
- `TransferContact`
- `ConvertContactToLead`
- `ArchiveContact`
- `RestoreContact`
- `AnalyzeContactDeletion`
- `PermanentlyDeleteContact`
- `ResolveCase`
- `CloseCase`
- `ReopenCase`
- `PauseCaseSla`
- `ResumeCaseSla`
- `ShareNote`

### 10.2 API direction

New capabilities are added primarily through the CRM V2 contract. Representative routes are action-oriented, for example:

```text
POST /api/v2/crm/tasks/{id}/transfer
POST /api/v2/crm/tasks/{id}/share
POST /api/v2/crm/tasks/{id}/request-review
POST /api/v2/crm/cases/{id}/resolve
POST /api/v2/crm/cases/{id}/reopen
POST /api/v2/crm/contacts/{id}/convert-to-lead
POST /api/v2/crm/contacts/{id}/archive
POST /api/v2/crm/contacts/{id}/restore
```

The exact paths must follow existing repository conventions during planning, but the business-command boundary is mandatory.

### 10.3 Concurrency

Mutating commands carry `expectedVersion` where applicable. A stale command returns `409 Conflict` with a stable application error code and enough safe metadata for the client to refresh and retry consciously.

### 10.4 Idempotency

Commands susceptible to retries, especially mobile/offline actions and notification-generating actions, use an idempotency key or equivalent repository-standard mechanism scoped at least by tenant, actor, command, and key.

### 10.5 Error contract

Stable codes include concepts such as:

- `CRM_RECIPIENT_NOT_ELIGIBLE`
- `CRM_CROSS_TENANT_ASSIGNMENT`
- `CRM_ENTITY_VERSION_CONFLICT`
- `CRM_REVIEW_COMMENT_REQUIRED`
- `CRM_CASE_RESOLUTION_REQUIRED`
- `CRM_CASE_REOPEN_REASON_REQUIRED`
- `CRM_CONTACT_DELETE_BLOCKED`
- `NOTIFICATION_WHATSAPP_NOT_VERIFIED`

External-channel warnings do not convert a successful business command into a failed CRM operation.

---

## 11. Transaction and event flow

For a command such as Task Transfer, the transaction boundary is:

```text
BEGIN
  authenticate / derive tenant
  authorize actor
  validate entity and version
  validate recipient eligibility
  update owner
  update participant relation if needed
  insert timeline event
  append audit fact/reference
  create notification intent/outbox record
COMMIT
```

After commit:

```text
Outbox processor
  -> durable in-app Notification
  -> Email delivery if selected/allowed
  -> WhatsApp delivery if selected/allowed/verified
```

An external provider outage must not block CRM commands. Outbox processing must be crash-safe and duplicate-safe.

---

## 12. Unified CRM UX

### 12.1 Detail pages

Contacts, Tasks, and Cases receive operational detail pages with consistent sections for owner, collaborators, watchers, domain-specific roles, action bar, details, timeline, notes, and related entities.

Shared components include concepts equivalent to:

- `EntityParticipants`
- `EmployeePicker`
- `TransferDialog`
- `ShareDialog`
- `WatcherManager`
- `NotificationChannelOptions`
- `ActivityTimeline`
- `ActivityComposer`
- `StatusBadge`
- `SlaIndicator`
- `DangerActionDialog`

Domain pages remain separate; there is no `GenericCrmEntityEverything` component.

### 12.2 Task UX

The Task detail page exposes owner, collaborators, watchers, reviewer, due/overdue state, status actions, and timeline. `WAITING_REVIEW`, `BLOCKED`, cancellation, approval, and rejection use purpose-specific actions/dialogs rather than a generic status dropdown.

### 12.3 Case UX

The Case detail page displays Case status and SLA state separately, including first-response result, resolution progress/remaining time, pause state, and escalation information. Pause, resolve, close, and reopen use dedicated forms with required reasons/summaries.

### 12.4 Contact UX

Contact actions include convert to Lead, transfer, share, follow, archive, and protected administrative actions. If a linked active Lead exists, the UI directs the user to it rather than silently duplicating it.

### 12.5 Notification UX

The main shell shows an unread badge and quick panel. The full notification center supports deep links, filtering, and read state. External channel statuses may be shown when useful without exposing provider secrets/IDs to ordinary users.

### 12.6 Accessibility and mobile

The UI supports RTL/LTR, keyboard navigation, focus management, semantic labels, non-color-only status communication, responsive layouts, and mobile actions that do not depend on hover. Deep links on Android preserve the intended destination through login when the session has expired.

---

## 13. Security, privacy, and resilience

### 13.1 Authorization chain

Every sensitive operation enforces:

```text
Authentication
-> trusted tenant boundary
-> actor RBAC/domain authorization
-> recipient/entity eligibility
-> entity state/version validation
-> execute
```

Client-supplied IDs never prove access.

### 13.2 Private-content protection

Private notes, search results, notification bodies, deep links, and activity endpoints must prevent both content and metadata leakage. Deep links are navigation hints, not authorization tokens.

### 13.3 External-channel privacy

WhatsApp and Email use field-classification/template policy. Full note bodies, sensitive customer data, financial data, attachments, credentials, and secrets are not sent externally by default.

### 13.4 Secrets and provider callbacks

Provider credentials are stored through platform-approved encrypted/secret mechanisms, never plain text configuration tables or logs. Provider callbacks/webhooks must verify signature/authenticity, bind to the correct provider/tenant, resist replay, and enforce valid delivery-state transitions.

### 13.5 Operational isolation

Email and WhatsApp processing are isolated so one blocked provider does not create head-of-line blocking for other channels. Retry/backoff, circuit breaking, permanent-failure handling, queue health, and kill switches are required operational capabilities.

### 13.6 Employee deactivation

Deactivating an employee performs an ownership-impact check for open Tasks, Cases, Contacts, and other owned records. Historical actor references are preserved; employees with audit history are not casually hard-deleted.

---

## 14. Data model direction

The implementation plan may adjust exact names to existing repository conventions, but the following separations are required:

- domain owner field on Contact/Task/Case;
- `crm_entity_participants` for collaborator/watcher membership;
- `crm_timeline_events` for operational timeline;
- note records, note versions, note recipients, and note mentions as separate concerns;
- notifications, delivery records, preferences, and outbox as separate concerns;
- Case SLA cycle records, pause intervals, policy versions, calendar versions, and escalation policy as separate concerns.

Reopen creates a new Case resolution/SLA cycle instead of overwriting the prior cycle.

---

## 15. Migration strategy

### 15.1 Rules

Migrations are:

- forward-only;
- additive-first;
- tenant-aware;
- non-destructive to current data;
- explicit about backfill behavior;
- protected by constraints and indexes after safe backfill;
- compatible with staggered backend/web/Android deployment windows.

No fake ownership defaults such as assigning every unresolved record to the first admin are permitted.

### 15.2 Rollout waves

The approved execution decomposition is:

1. Shared data/event primitives.
2. Collaboration Core.
3. In-app Notification + Outbox.
4. Contacts.
5. Tasks.
6. Notes.
7. Cases basic collaboration/lifecycle hardening.
8. SLA + Business Calendar + Escalation.
9. Email channel.
10. WhatsApp channel.
11. Unified Web UX hardening.
12. Physical Android acceptance.
13. Full regression.
14. Production pilot.
15. General rollout.

The detailed implementation plan will split these into bounded implementation specs/tasks while preserving this master design.

### 15.3 Feature rollout

Feature flags may gate rollout, for example collaboration, task review, case SLA, note sharing, or WhatsApp, but flags must never bypass authorization or schema invariants.

For SLA, a shadow/read-verification phase is recommended before external escalation is enabled for production tenants.

---

## 16. Testing and acceptance

### 16.1 Test layers

Required coverage includes:

- domain unit tests;
- PostgreSQL Direct repository/integration tests;
- API contract tests;
- tenant-isolation and RBAC matrix tests;
- transaction/failpoint tests;
- web UI acceptance;
- Android physical-device acceptance;
- provider adapter and notification delivery tests;
- performance/query-plan checks for critical read paths;
- observability/alert readiness.

### 16.2 Critical collaboration cases

Tests must prove transfer ownership semantics, optional previous-owner watcher behavior, share without ownership change, duplicate-participant prevention, inactive/cross-tenant recipient denial, and RBAC enforcement.

### 16.3 Critical task cases

Tests must cover the full lifecycle, reviewer-required paths, rejection reason, blocked/unblocked behavior, cancellation reason, calculated overdue state, collaborator limitations, and stale-version conflicts.

### 16.4 Critical contact cases

Tests must prove non-destructive Contact-to-Lead conversion, linked source relation, no silent duplicate active Lead, archive/restore history preservation, deletion-impact blocking, and permanent-delete audit preservation.

### 16.5 Critical note cases

Tests must cover TEAM/PRIVATE visibility, explicit sharing, mentions, share+mention deduplication, version history, soft deletion, search leakage prevention, and offline/idempotent retry.

### 16.6 SLA golden tests

A deterministic time abstraction is required. Golden cases include business hours, weekends, holidays, overnight windows, 24x7 mode, pause across non-working time, priority changes, calendar-policy version changes, reopen cycle creation, timezones with DST, and first-response recording exactly once.

### 16.7 Notification tests

Tests must prove notification creation exactly once, preference behavior, verified/unverified WhatsApp behavior, provider timeout classification, permanent invalid-recipient failure, outbox replay safety, channel isolation, and cross-tenant notification isolation.

### 16.8 Transaction failure tests

If timeline/audit/outbox intent cannot be written inside the business transaction, the business state change must roll back. If an external channel fails after commit, the business state remains committed and the external delivery moves to retry/failure state.

### 16.9 Existing baseline protection

The change must preserve existing G7/G8 flows and current CRM CRUD behavior unless explicitly migrated. It must not introduce destructive schema changes or reintroduce the deprecated Docker/Testcontainers path.

---

## 17. Observability and operations

At minimum, operations must expose metrics/logs for:

- collaboration command failures;
- authorization/tenant-denial anomalies;
- outbox pending count and oldest age;
- notification creation failures;
- Email delivery/retry/failure;
- WhatsApp delivery/retry/failure;
- SLA evaluator health, lag, at-risk counts, breach counts, and escalation failures;
- `409 Conflict` rates;
- oldest pending external delivery.

Structured logs use request/correlation IDs, tenant/entity identifiers, command, and result while redacting PII, secrets, tokens, credentials, authorization headers, and full sensitive note content.

---

## 18. Production acceptance gate

The overall package is not `DONE` until all applicable gates are satisfied:

```text
FEATURES_IMPLEMENTED = YES
MIGRATIONS_SAFE = YES
BACKEND_TESTS = PASS
POSTGRESQL_DIRECT = PASS
TENANT_ISOLATION = PASS
RBAC = PASS
WEB_ACCEPTANCE = PASS
PHYSICAL_ANDROID = PASS
NOTIFICATION_CORE = PASS
EMAIL = PASS
WHATSAPP = PASS
SLA_CORRECTNESS = PASS
OBSERVABILITY = READY
CRITICAL_DEFECTS = 0
```

P0 security/data-corruption/tenant-leak defects and P1 core-workflow defects block production acceptance.

Rollback should normally disable the affected feature and redeploy a previous compatible application version while retaining additive schema. Technical rollback must not reverse legitimate business actions that occurred while the newer version was active.

---

## 19. Implementation-plan decomposition

After written-spec review, the next artifact must be a detailed implementation plan. The plan should decompose the master architecture into at least these implementation workstreams:

- Collaboration & Event Foundation
- Contacts
- Tasks
- Cases & SLA
- Notes
- Notification Platform + Email + WhatsApp
- Unified Web UX
- Android Physical Acceptance

Each workstream must name concrete repository files, migrations, tests, dependencies, acceptance evidence, and execution order. No implementation begins from this master spec alone before the implementation plan is reviewed according to the project workflow.

---

## 20. Final architecture summary

```text
                         SNAD CRM
                            |
             Domain Commands / Read Models
                            |
          +-----------------+-----------------+
          |                 |                 |
       Contact            Task              Case
          |                 |                 |
          +-----------------+-----------------+
                            |
                  Collaboration Core
                            |
          +-----------------+-----------------+
          |                 |                 |
     Participants        Timeline           Notes
                            |
                      Domain Events
                            |
                  Transactional Outbox
                            |
               Notification Platform
                  /          |          \
              In-App       Email      WhatsApp
             mandatory    optional     optional
```

The design deliberately centralizes reusable collaboration and notification infrastructure while keeping Contact, Task, Case, Note, and SLA business rules in their respective domains. This is the controlling architecture for the subsequent implementation plan.
