# G7 ADR VALIDATION REPORT

> **Report ID:** G7-ADR-VALIDATION-V1
> **Date:** 2026-08-11
> **ADR Under Review:** ADR-G7-001-MOBILE-CONFLICT-RESOLUTION.md
> **Status:** REQUIRES_REVISION
> **Mode:** READ-ONLY VALIDATION

---

## TASK 1 — VALIDATE ADR AGAINST ACTUAL CODE

### 1.1 ETagService.java

| ADR Claim | Code Evidence | Verdict |
|-----------|---------------|---------|
| SHA-256 ETag computation | `MessageDigest.getInstance("SHA-256")` over `entityType:id:version` | ✅ VALIDATED |
| Missing If-Match → HTTP 428 | `throw new CrmContractException(CRM_PRECONDITION_REQUIRED)` when header blank | ✅ VALIDATED |
| Stale ETag → HTTP 412 | `throw new CrmContractException(CRM_CONCURRENCY_CONFLICT)` when no match | ✅ VALIDATED |
| Wildcard `*` support | `if ("*".equals(trimmed) \|\| current.equals(trimmed))` | ✅ VALIDATED |
| Comma-separated ETag list | Loop over comma-separated values, any match passes | ✅ VALIDATED |

**Additional detail not in ADR:** ETag format is `"<entityType>-<id>-v<version>-<16-char-hex>"`. Only first 8 bytes (16 hex chars) of SHA-256 are used.

### 1.2 CrmErrorCode.java

| ADR Claim | Code Evidence | Verdict |
|-----------|---------------|---------|
| CRM_CONCURRENCY_CONFLICT = HTTP 412, retryable=true | Line 63: `CRM_CONCURRENCY_CONFLICT(412, "...", true)` | ✅ VALIDATED |
| CRM_PRECONDITION_REQUIRED = HTTP 428, retryable=false | Line 57: `CRM_PRECONDITION_REQUIRED(428, "...", false)` | ✅ VALIDATED |
| CRM_IDEMPOTENCY_CONFLICT = HTTP 409, retryable=false | Line 45: `CRM_IDEMPOTENCY_CONFLICT(409, "...", false)` | ✅ VALIDATED |
| CRM_IDEMPOTENCY_KEY_REQUIRED = HTTP 400, retryable=false | Line 60: `CRM_IDEMPOTENCY_KEY_REQUIRED(400, "...", false)` | ✅ VALIDATED |

**Complete retryable classification:**
- 412 (CONCURRENCY_CONFLICT): retryable = **true**
- 429 (RATE_LIMITED): retryable = **true**
- 500 (INTERNAL_ERROR, SCORE_CALC, EMAIL_SEND, REPORT_GEN): retryable = **true**
- All others: retryable = **false**

### 1.3 CrmOwnershipAtomicIfMatchAspect.java

| ADR Claim | Code Evidence | Verdict |
|-----------|---------------|---------|
| SELECT ... FOR UPDATE for ownership | `SELECT updated_at FROM ... WHERE ... FOR UPDATE` | ✅ VALIDATED |
| Version derived from updated_at | `timestampVersion()`: `epochSeconds * 1_000_000 + microseconds` | ✅ VALIDATED |
| If-Match validated in transaction | `transactions.execute(status -> { ... })` wraps controller + ETag validation | ✅ VALIDATED |
| Applies to ownership entities | Intercepts 3 controllers, covers 7 entity types | ✅ VALIDATED |

**Entity coverage:**
| Entity | Table | HTTP Methods |
|--------|-------|-------------|
| team-membership | crm_team_memberships | PATCH, DELETE |
| sales-team | crm_sales_teams | PATCH |
| queue | crm_queues | PATCH |
| territory-assignment | crm_territory_assignments | DELETE |
| territory | crm_territories | PATCH |
| assignment-rule | crm_assignment_rules | PATCH |
| transfer-request | crm_transfer_requests | POST |

### 1.4 CrmConcurrencyContractTest.java

| ADR Claim | Test Evidence | Verdict |
|-----------|---------------|---------|
| ETag determinism | `etagIsDeterministicForSameVersion` | ✅ VALIDATED |
| Version change detection | `etagChangesWhenVersionChanges` | ✅ VALIDATED |
| Entity-type isolation | `etagIncludesEntityTypePrefix` | ✅ VALIDATED |
| Missing If-Match → 428 | `missingIfMatchHeaderIsRejected`, `blankIfMatchHeaderIsRejected` | ✅ VALIDATED |
| Stale If-Match → 412 | `staleIfMatchYieldsConcurrencyConflict` | ✅ VALIDATED |
| Current If-Match accepted | `currentIfMatchIsAccepted` | ✅ VALIDATED |
| Wildcard accepted | `wildcardIfMatchIsAccepted` | ✅ VALIDATED |
| Comma-separated list | `ifMatchListWithAtLeastOneMatchIsAccepted` | ✅ VALIDATED |
| Cross-entity-type rejected | `ifMatchForDifferentEntityTypeIsRejected` | ✅ VALIDATED |
| Quoted ETag format | `etagIsQuotedPerHttpSpec` | ✅ VALIDATED |

### 1.5 IdempotencyService.java

| ADR Claim | Code Evidence | Verdict |
|-----------|---------------|---------|
| Begin/Complete/Fail lifecycle | Interface lines 59-63; InMemory implementation | ✅ VALIDATED |
| Composite key (tenant, principal, endpoint, key) | `compositeKey()` at line 148 | ✅ VALIDATED |
| Fingerprint SHA-256 matching | `fingerprint()` at lines 78-87; comparison at line 105 | ✅ VALIDATED |
| ReplayHit returns cached record | Line 114 | ✅ VALIDATED |
| ReplayMiss returns fresh operationId | Line 124 | ✅ VALIDATED |
| CRM_IDEMPOTENCY_CONFLICT on fingerprint mismatch | Line 106, HTTP 409 | ✅ VALIDATED |
| CRM_IDEMPOTENCY_CONFLICT on in-flight duplicate | Lines 110-112, HTTP 409 (same code, custom message) | ✅ VALIDATED |
| 24-hour retention | Line 42: `Duration.ofHours(24)` | ✅ VALIDATED |

**Note:** In-flight conflict uses same error code (CRM_IDEMPOTENCY_CONFLICT) as fingerprint mismatch, with custom message. ADR does not distinguish these — acceptable.

### 1.6 PlatformAuditWriter.java

| ADR Claim | Code Evidence | Verdict |
|-----------|---------------|---------|
| Fields logged (actor, target, action, states) | INSERT columns at lines 83-85 | ✅ VALIDATED |
| Before/after state serialization | `json()` method at lines 103-116 | ✅ VALIDATED |
| Append-only | Only INSERT at line 81; no UPDATE/DELETE | ✅ VALIDATED |
| Transaction boundaries | No `@Transactional`; single-statement auto-commit | ⚠️ MINOR GAP |

**Gap:** `PlatformAuditWriter` has no explicit `@Transactional`. Each write is a single INSERT relying on auto-commit. The ADR's claim that audit writes happen "within the same transaction as the mutation" is correct at the CALLER level (the caller wraps the mutation + audit in a transaction), but `PlatformAuditWriter` itself does not enforce this. This is not a contradiction but a precision gap.

### 1.7 JDBC Repositories (version check pattern)

| Entity | Table | `WHERE version = :expectedVersion` | Error | Verdict |
|--------|-------|-----------------------------------|-------|---------|
| Account | crm_accounts | ✅ | CRM_CONCURRENCY_CONFLICT | ✅ VALIDATED |
| Contact | crm_contacts | ✅ | CRM_CONCURRENCY_CONFLICT | ✅ VALIDATED |
| Lead | crm_leads | ✅ | CRM_CONCURRENCY_CONFLICT | ✅ VALIDATED |
| Opportunity | crm_opportunities | ✅ | CRM_CONCURRENCY_CONFLICT | ✅ VALIDATED |
| Task | crm_tasks | ✅ | CRM_CONCURRENCY_CONFLICT or CRM_INVALID_TASK_TRANSITION | ✅ VALIDATED |
| Activity | crm_activities | ✅ | CRM_CONCURRENCY_CONFLICT | ✅ VALIDATED |
| Note | crm_notes | ✅ | CRM_CONCURRENCY_CONFLICT or CRM_NOTE_ALREADY_ARCHIVED | ✅ VALIDATED |
| Pipeline | crm_pipelines | ✅ | CRM_CONCURRENCY_CONFLICT | ✅ VALIDATED |
| Tag | crm_tags | ✅ | CRM_CONCURRENCY_CONFLICT | ✅ VALIDATED |
| Custom Field | crm_custom_field_definitions | ✅ | CRM_CONCURRENCY_CONFLICT | ✅ VALIDATED |

**All 10 entities use the same optimistic locking pattern.** No contradictions found.

### 1.8 TASK 1 VERDICT

**ADR STATUS = REQUIRES_REVISION**

Reason: The ADR's entity policy classification has factual errors (see TASK 2). The ADR infrastructure claims are ALL VALIDATED. No contradictions found in the conflict resolution mechanism itself.

---

## TASK 2 — VALIDATE ENTITY POLICY

### 2.1 Entity Policy Corrections

The ADR's entity policy table contains **4 factual errors**:

| Entity | ADR Claim | Actual State | ADR Correct? | Correction Needed |
|--------|-----------|-------------|-------------|-------------------|
| Account | Reject + Auto-Merge | Has version check, mutable fields, state transitions (ACTIVE/INACTIVE/ARCHIVED) | ✅ CORRECT | None |
| Contact | Reject + Auto-Merge | Has version check, mutable fields, state transitions (ACTIVE/INACTIVE/ARCHIVED) | ✅ CORRECT | None |
| Lead | Reject + User Resolution | Has version check, 7-state machine (NEW→ASSIGNED→CONTACTED→QUALIFIED→DISQUALIFIED/CONVERTED/ARCHIVED) | ✅ CORRECT | None |
| Opportunity | Reject + User Resolution | Has version check, 5 states (OPEN/WON/LOST/CANCELLED/ARCHIVED), pipeline/stage | ✅ CORRECT | None |
| Task | Reject + Auto-Merge | Has version check, 4 states with SQL guards, mutable fields | ✅ CORRECT | None |
| Activity | **Push-Only** | **MUTABLE** — has update/complete with version check | ❌ INCORRECT | Must be Reject + Auto-Merge or Reject + User Resolution |
| Note | Push-Only | Append-only (only archive mutation) | ✅ CORRECT | None |
| Pipeline | **Pull-Only** | **MUTABLE** — has create/update with version check | ❌ INCORRECT | Must have conflict strategy |
| Tags | **Pull-Only** | **MUTABLE** — full CRUD + hard delete with version check | ❌ INCORRECT | Must have conflict strategy |
| Custom Fields | **Pull-Only** | **MUTABLE** — create/update with version check | ❌ INCORRECT | Must have conflict strategy |

### 2.2 Corrected Entity Policy

| Entity | Data Class | Conflict Strategy | Reason | Evidence |
|--------|------------|-------------------|--------|----------|
| Account | Master Data | Reject + Auto-Merge Non-Conflicting | High-value; field-level merge safe | `crm_accounts` version check, mutable fields |
| Contact | Master Data | Reject + Auto-Merge Non-Conflicting | Frequently edited by field teams | `crm_contacts` version check, mutable fields |
| Lead | Transactional | Reject + User Resolution | 7-state machine; auto-merge could corrupt states | `crm_leads` version check, state guards |
| Opportunity | Transactional | Reject + User Resolution | 5-state + pipeline/stage; financial impact | `crm_opportunities` version check, stage history |
| Task | Transactional | Reject + Auto-Merge Non-Conflicting | 4-state but simpler; frequently edited | `crm_tasks` version check, SQL state guards |
| Activity | Transactional | Reject + Auto-Merge Non-Conflicting | Mutable but lower risk than Lead/Opportunity | `crm_activities` version check, update/complete |
| Note | Transactional | Push-Only (Archive only) | Append-only body; only archive mutation | `crm_notes` archive-only, no update method |
| Pipeline | Reference Data | Reject + User Resolution | Mutates opportunity structure; stage changes cascade | `crm_pipelines` version check, stage updates |
| Tags | Reference Data | Reject + User Resolution | Mutates tag definitions; hard delete possible | `crm_tags` version check, hard delete |
| Custom Fields | Reference Data | Reject + User Resolution | Mutates field definitions; affects data entry | `crm_custom_field_definitions` version check |

### 2.3 Key Corrections

1. **Activity is NOT push-only** — It has `update()` and `complete()` methods with version checks. Mobile users should be able to edit activities offline. Policy: Reject + Auto-Merge Non-Conflicting.

2. **Pipeline is NOT pull-only** — It has create/update operations. However, pipeline changes affect all opportunities in that pipeline. Policy: Reject + User Resolution (conservative).

3. **Tags is NOT pull-only** — Full CRUD including hard delete. Tag changes affect all entities using that tag. Policy: Reject + User Resolution.

4. **Custom Fields is NOT pull-only** — Create/update operations exist. Custom field definition changes affect data entry forms. Policy: Reject + User Resolution.

### 2.4 TASK 2 VERDICT

**Entity policy requires REVISION.** 4 out of 10 entities have incorrect classifications.

---

## TASK 3 — AUTO-MERGE SAFETY

### 3.1 Algorithm Definition

**Three-way merge algorithm using BASE VERSION as the merge ancestor:**

```
INPUT:
  BASE_VERSION  = Entity state when client last read it (server version at read time)
  SERVER_VERSION = Current entity state on server
  CLIENT_MUTATION = What the client wants to write

FOR each field in CLIENT_MUTATION:
  base_value = BASE_VERSION[field]
  server_value = SERVER_VERSION[field]
  client_value = CLIENT_MUTATION[field]

  IF server_value == base_value:
    // Server has NOT changed this field since client read it
    // Client's change is safe to apply
    APPLY client_value

  ELSE IF client_value == base_value:
    // Client has NOT changed this field (relative to what they read)
    // Server's change should be preserved
    PRESERVE server_value

  ELSE:
    // BOTH server and client changed this field since base version
    // TRUE CONFLICT — cannot auto-merge
    FLAG for user resolution
```

### 3.2 Example Walkthrough

**Scenario: Non-conflicting (auto-merge safe)**
```
BASE (v5):  name=A, phone=111, email=old@example.com
SERVER (v6): name=A, phone=111, email=new@example.com  (server changed email)
CLIENT:      phone=222                                   (client changed phone)

Check phone: server_value(111) == base_value(111) → APPLY client phone=222
Check email: client_value(old) == base_value(old) → PRESERVE server email=new@example.com

RESULT: phone=222, email=new@example.com ✅ AUTO-MERGED
```

**Scenario: True conflict (cannot auto-merge)**
```
BASE (v5):  phone=111
SERVER (v6): phone=333  (server changed phone)
CLIENT:      phone=222  (client changed phone)

Check phone: server_value(333) != base_value(111) AND client_value(222) != base_value(111)
→ TRUE CONFLICT — both changed the same field

RESULT: CONFLICT — user must choose between 333 and 222
```

### 3.3 Domain Compatibility

| Entity | Auto-Merge Safe? | Reason |
|--------|-----------------|--------|
| Account | YES | Simple data fields; no cascading effects from field-level merge |
| Contact | YES | Simple data fields; no cascading effects |
| Task | YES | Simple data fields; state transitions are separate operations |
| Activity | YES | Simple data fields; state transitions are separate operations |
| Lead | NO | State machine; merging non-state fields while state differs could be confusing |
| Opportunity | NO | Pipeline/stage changes have financial implications; conservative approach needed |
| Pipeline | NO | Structure changes affect all opportunities; conservative approach needed |
| Tags | NO | Definition changes affect all tagged entities; conservative approach needed |
| Custom Fields | NO | Definition changes affect data entry; conservative approach needed |

### 3.4 TASK 3 VERDICT

Auto-merge algorithm is **DEFINED and DOMAIN-COMPATIBLE** for Account, Contact, Task, Activity. User resolution required for all other entities.

---

## TASK 4 — STATE TRANSITION CONFLICTS

### 4.1 State Machines Found

| Entity | States | Transitions | SQL Guards |
|--------|--------|-------------|------------|
| Account | ACTIVE, INACTIVE, ARCHIVED | ACTIVE↔INACTIVE, ACTIVE→ARCHIVED, ARCHIVED→ACTIVE | No SQL guards (version check only) |
| Contact | ACTIVE, INACTIVE, ARCHIVED | ACTIVE↔INACTIVE, ACTIVE→ARCHIVED, ARCHIVED→ACTIVE | No SQL guards (version check only) |
| Lead | NEW, ASSIGNED, CONTACTED, QUALIFIED, DISQUALIFIED, CONVERTED, ARCHIVED | Forward: NEW→ASSIGNED→CONTACTED→QUALIFIED; Terminal: DISQUALIFIED, CONVERTED, ARCHIVED | No SQL guards (version check only) |
| Opportunity | OPEN, WON, LOST, CANCELLED, ARCHIVED | OPEN→WON/LOST/CANCELLED/ARCHIVED | No SQL guards (version check only) |
| Task | OPEN, IN_PROGRESS, COMPLETED, CANCELLED | OPEN→IN_PROGRESS→COMPLETED; OPEN/IN_PROGRESS→CANCELLED | **YES**: `AND status = 'OPEN'` for start; `AND status IN ('OPEN','IN_PROGRESS')` for complete/cancel |
| Activity | OPEN, IN_PROGRESS, COMPLETED, CANCELLED, ARCHIVED | Similar to Task | No SQL guards (version check only) |

### 4.2 STATE_CONFLICT_POLICY

```
RULE 1: State transitions are SERVER-AUTHORITATIVE
  - The server validates state transitions against the state machine
  - Client cannot force an invalid transition

RULE 2: If client and server both changed state:
  - Compare BASE state (what client read) with SERVER state (current)
  - If server state == base state: client's state change is valid (apply)
  - If server state != base state: CONFLICT (server state wins; user must re-evaluate)

RULE 3: Non-state fields can still auto-merge even if state differs
  - Example: Client changes Contact.phone AND Lead.status simultaneously
  - Phone can auto-merge (if non-conflicting with server)
  - Status requires user resolution (if server also changed status)

RULE 4: Terminal states cannot be reversed
  - CONVERTED, DISQUALIFIED, WON, LOST, CANCELLED, ARCHIVED are terminal
  - Once terminal, no further state changes allowed
  - Client mutation to reverse terminal state → REJECTED

RULE 5: Task has SQL-level state guards
  - Task.start() requires `status = 'OPEN'`
  - Task.complete() requires `status IN ('OPEN', 'IN_PROGRESS')`
  - Task.cancel() requires `status IN ('OPEN', 'IN_PROGRESS')`
  - If state guard fails: throw CRM_INVALID_TASK_TRANSITION (not CRM_CONCURRENCY_CONFLICT)
```

### 4.3 Specific Conflict Scenarios

| Scenario | Client State | Server State | Base State | Resolution |
|----------|-------------|-------------|------------|------------|
| Both change to same state | IN_PROGRESS | IN_PROGRESS | OPEN | APPLIED (idempotent) |
| Client advances, server same | COMPLETED | OPEN | OPEN | APPLIED (server unchanged) |
| Server advances, client same | OPEN | IN_PROGRESS | OPEN | APPLIED (server change preserved) |
| Both advance differently | COMPLETED | IN_PROGRESS | OPEN | CONFLICT (user resolves) |
| Client reverses terminal | OPEN | CONVERTED | CONVERTED | REJECTED (terminal) |
| Server converts, client edits | QUALIFIED | CONVERTED | QUALIFIED | CONFLICT (state changed on server) |

### 4.4 TASK 4 VERDICT

**STATE_CONFLICT_POLICY is DEFINED.** State transitions are server-authoritative with explicit SQL guards for Task.

---

## TASK 5 — DELETE CONFLICTS

### 5.1 Delete Behavior in SNAD

| Entity | Delete Method | Actual Behavior |
|--------|--------------|-----------------|
| Account | `PATCH /archive` | Soft-delete: `lifecycle_status = 'ARCHIVED'` |
| Contact | `PATCH /archive` | Soft-delete: `lifecycle_status = 'ARCHIVED'` |
| Lead | Status change to ARCHIVED | Soft-delete: `status = 'ARCHIVED'` |
| Opportunity | Status change to ARCHIVED | Soft-delete: `status = 'ARCHIVED'` |
| Task | `PATCH /cancel` | Soft-delete: `status = 'CANCELLED'` |
| Activity | Status change to ARCHIVED | Soft-delete: `status = 'ARCHIVED'` |
| Note | `PATCH /archive` | Soft-delete: `archived = TRUE` |
| Pipeline | `active = FALSE` | Soft-delete |
| Tags | `DELETE /tags/{id}` | **HARD DELETE** |
| Custom Fields | No delete method | Cannot delete |

### 5.2 DELETE_CONFLICT_POLICY

```
SCENARIO 1: Client Update + Server Delete (Archive)
  BASE: Entity is ACTIVE (v5)
  SERVER: Entity is ARCHIVED (v6) — someone archived it
  CLIENT: Update phone (base_version=5)

  RESULT: CONFLICT
  - Server delete (archive) is more recent
  - Client update is stale
  - User must choose: restore and apply update, or keep archived

SCENARIO 2: Client Delete (Archive) + Server Update
  BASE: Entity is ACTIVE (v5)
  SERVER: Entity updated (v6) — someone changed the name
  CLIENT: Archive (base_version=5)

  RESULT: CONFLICT
  - Server update is more recent
  - Client archive is stale
  - User must choose: archive anyway (discarding server update), or cancel archive

SCENARIO 3: Client Delete + Server Delete (Idempotent)
  BASE: Entity is ACTIVE (v5)
  SERVER: Entity ARCHIVED (v6)
  CLIENT: Archive (base_version=5)

  RESULT: APPLIED (idempotent)
  - Both sides want the same outcome
  - Server archive is the authoritative action
  - Client's archive is redundant but harmless

SCENARIO 4: Client Update + Server Hard Delete (Tags only)
  BASE: Tag exists (v5)
  SERVER: Tag deleted (hard delete)
  CLIENT: Update tag name (base_version=5)

  RESULT: REJECTED
  - Entity no longer exists on server
  - Client mutation is for a non-existent entity
  - Return ENTITY_NOT_FOUND

SCENARIO 5: Client Delete + Server Hard Delete (Tags only)
  BASE: Tag exists (v5)
  SERVER: Tag deleted (hard delete)
  CLIENT: Delete tag (base_version=5)

  RESULT: APPLIED (idempotent)
  - Both sides want deletion
  - Server hard delete is authoritative
  - Client delete is redundant
```

### 5.3 TASK 5 VERDICT

**DELETE_CONFLICT_POLICY is DEFINED.** All 5 scenarios have explicit resolution rules.

---

## TASK 6 — IDEMPOTENCY EXTENSION

### 6.1 mutation_id vs idempotency_key

| Identifier | Purpose | Owner | Scope |
|------------|---------|-------|-------|
| `mutation_id` | Unique identifier for each offline mutation operation | Client-generated UUID | Per-mutation; never reused |
| `idempotency_key` | Deduplication key for server-side processing | Client-generated UUID | Per-mutation; ensures exactly-once semantics |

**Both are required.** They serve different purposes:
- `mutation_id`: Client-side tracking, queue management, conflict reference
- `idempotency_key`: Server-side dedup, replay prevention

**Ownership:** Both are CLIENT-GENERATED. The server does not generate these identifiers.

### 6.2 Mutation Lifecycle

```
CREATED
  │ mutation_id assigned (client UUID)
  │ idempotency_key assigned (client UUID)
  │ base_version captured (server version at read time)
  │ payload serialized
  │
  ▼
QUEUED
  │ Added to offline queue
  │ Ordered by sequence_number (FIFO per entity)
  │ Pending network availability
  │
  ▼
SENT
  │ HTTP POST /api/v2/mobile/sync/push
  │ idempotency_key in header
  │ mutation payload in body
  │
  ▼
ACCEPTED
  │ Server received request
  │ Idempotency check passed (new key)
  │ Version check initiated
  │
  ├─► APPLIED
  │     Server version matched
  │     Mutation executed
  │     Version incremented
  │     Result cached for idempotency replay
  │
  ├─► DUPLICATE
  │     Idempotency key already processed
  │     Cached result returned
  │     No duplicate mutation created
  │
  ├─► CONFLICT
  │     Server version != base_version
  │     Conflict logged to mobile_conflict_log
  │     User resolution required
  │
  ├─► REJECTED
  │     Entity not found
  │     Invalid state transition
  │     Authorization failed
  │
  ├─► UNAUTHORIZED
  │     User no longer has write permission
  │     Token expired or revoked
  │
  ├─► INVALID
  │     Malformed payload
  │     Missing required fields
  │     Validation failed
  │
  ├─► RETRYABLE
  │     Transient server error (500, timeout)
  │     Client should retry with backoff
  │
  └─► PERMANENT_FAILURE
        Non-retryable error
        Dead-letter for manual review
```

### 6.3 Retry Behavior

| Scenario | Idempotency Key | Result |
|----------|----------------|--------|
| First attempt | New key | APPLIED/CONFLICT/REJECTED |
| Retry after network timeout | Same key | DUPLICATE (cached result) |
| Retry after server commit | Same key | DUPLICATE (cached result) |
| Retry with different payload | Same key | CRM_IDEMPOTENCY_CONFLICT (409) |
| Retry after fail() | Same key | NEW (fail removes record) |

### 6.4 TASK 6 VERDICT

**Idempotency extension is DEFINED.** Both mutation_id and idempotency_key are required with client-side ownership.

---

## TASK 7 — mobile_conflict_log SCHEMA

### 7.1 Proposed Schema

| # | Column | Type | Required | Index | PII | Retention | Encryption | RLS | Derivable? |
|---|--------|------|----------|-------|-----|-----------|------------|-----|------------|
| 1 | `id` | UUID | YES | PK | NO | Permanent | NO | YES | NO (auto-generated) |
| 2 | `tenant_id` | UUID | YES | YES (FK) | NO | Permanent | NO | YES (RLS) | NO |
| 3 | `entity_type` | VARCHAR(40) | YES | YES | NO | Permanent | NO | YES | NO |
| 4 | `entity_id` | UUID | YES | YES | NO | Permanent | NO | YES | NO |
| 5 | `mutation_id` | UUID | YES | YES | NO | 90 days | NO | YES | NO (client-generated) |
| 6 | `client_version` | BIGINT | YES | NO | NO | 90 days | NO | YES | NO (from client payload) |
| 7 | `server_version` | BIGINT | YES | NO | NO | 90 days | NO | YES | NO (from server state) |
| 8 | `base_version` | BIGINT | YES | NO | NO | 90 days | NO | YES | NO (from client payload) |
| 9 | `conflict_type` | VARCHAR(32) | YES | YES | NO | 90 days | NO | YES | YES (derivable from client/server payloads) |
| 10 | `client_payload` | JSONB | YES | NO | POSSIBLY | 90 days | CONDITIONAL | YES | NO (what client tried to write) |
| 11 | `server_payload` | JSONB | YES | NO | POSSIBLY | 90 days | CONDITIONAL | YES | NO (what server had) |
| 12 | `resolution_strategy` | VARCHAR(32) | CONDITIONAL | YES | NO | 1 year | NO | YES | NO (NULL if unresolved) |
| 13 | `resolution_status` | VARCHAR(24) | YES | YES | NO | 1 year | NO | YES | NO (PENDING/RESOLVED/SKIPPED) |
| 14 | `resolved_by` | UUID | CONDITIONAL | NO | NO | 1 year | NO | YES | NO (NULL if unresolved) |
| 15 | `resolved_at` | TIMESTAMPTZ | CONDITIONAL | NO | NO | 1 year | NO | YES | NO (NULL if unresolved) |
| 16 | `device_id` | TEXT | YES | YES | NO | 90 days | NO | YES | NO (from request header) |
| 17 | `user_id` | UUID | YES | YES | NO | 90 days | NO | YES | NO (from JWT) |
| 18 | `created_at` | TIMESTAMPTZ | YES | YES | NO | 1 year | NO | YES | NO (auto-generated) |

### 7.2 Derivable Fields Analysis

| Field | Can Be Derived? | From What? | Recommendation |
|-------|----------------|------------|----------------|
| `conflict_type` | YES | Compare client_payload and server_payload field-by-field | **KEEP** — pre-computed for query performance; avoids JSON comparison at query time |
| `client_payload` | NO | Client mutation data | REQUIRED |
| `server_payload` | NO | Server entity state at conflict time | REQUIRED |
| `resolution_strategy` | NO | How conflict was resolved | REQUIRED (NULL if unresolved) |

### 7.3 PII Considerations

| Field | PII Risk | Mitigation |
|-------|----------|------------|
| `client_payload` | May contain names, emails, phones | Encrypt at rest if entity contains PII; apply RLS |
| `server_payload` | May contain names, emails, phones | Encrypt at rest if entity contains PII; apply RLS |
| `entity_id` | Indirect PII (identifies a person record) | RLS enforced; access-controlled |

### 7.4 TASK 7 VERDICT

**Schema is DEFINED.** 18 columns, all with specified types, indexes, and retention. `conflict_type` is pre-computed despite being derivable (for performance).

---

## TASK 8 — ACCEPTANCE TESTS

**See separate file:** `G7_CONFLICT_TEST_SPEC.md`

---

## TASK 9 — TRUE IMPLEMENTATION BLOCKERS

### 9.1 Classification

| Component | Blocked By | Status | Can Proceed? |
|-----------|-----------|--------|-------------|
| Local Storage | NONE | UNBLOCKED | ✅ YES |
| Connectivity Detection | NONE | UNBLOCKED | ✅ YES |
| Authentication Plumbing | NONE | UNBLOCKED | ✅ YES |
| Pull-Only Sync (full entities) | NONE | UNBLOCKED | ✅ YES |
| Push-Only Entities (Note archive) | NONE | UNBLOCKED | ✅ YES |
| Observability/Metrics | NONE | UNBLOCKED | ✅ YES |
| Retry Infrastructure | NONE | UNBLOCKED | ✅ YES |
| Device/Session Foundation | NONE | UNBLOCKED | ✅ YES |
| Sync Telemetry | NONE | UNBLOCKED | ✅ YES |
| **Conflict-Aware Push** | ADR ACCEPTED + Sync Contract Defined | BLOCKED | ❌ NO |
| **Auto-Merge Logic** | ADR ACCEPTED + Entity Policy Finalized | BLOCKED | ❌ NO |
| **Conflict Log Table** | ADR ACCEPTED + Schema Finalized | BLOCKED | ❌ NO |
| **Conflict Resolution API** | ADR ACCEPTED + Conflict Schema Created | BLOCKED | ❌ NO |
| **Conflict UI Contract** | ADR ACCEPTED + Resolution API Defined | BLOCKED | ❌ NO |
| **Delete Conflict Handling** | ADR ACCEPTED + Sync Push Implemented | BLOCKED | ❌ NO |
| **State Transition Conflict** | ADR ACCEPTED + State Machine Defined | BLOCKED | ❌ NO |
| **Activity Offline Writes** | ADR ACCEPTED + Entity Policy Corrected | BLOCKED | ❌ NO |
| **Pipeline/Tags/Custom Fields Offline** | ADR ACCEPTED + Entity Policy Corrected | BLOCKED | ❌ NO |

### 9.2 TRACK A — UNBLOCKED NOW

| # | Component | Dependencies | Evidence |
|---|-----------|-------------|----------|
| A1 | Connectivity Detection | None | Network status monitoring |
| A2 | Local Persistence | None | SQLite/MMKV/IndexedDB setup |
| A3 | Local Data Layer | A2 | CRUD operations on local storage |
| A4 | Pull-Only Sync (full entities) | A3 | GET entities from server, store locally |
| A5 | Push-Only Entities (Note archive) | A3 | POST note archive to server |
| A6 | Authentication Plumbing | None | Token storage, refresh flow |
| A7 | Sync Telemetry | None | Metrics for sync operations |
| A8 | Retry Infrastructure | None | Exponential backoff logic |
| A9 | Device/Session Foundation | A6 | Device ID, session management |

### 9.3 TRACK B — WAITING FOR ADR ACCEPTANCE

| # | Component | Blocked By | Can Start After |
|---|-----------|-----------|----------------|
| B1 | Conflict-Aware Push | ADR ACCEPTED | ADR approval |
| B2 | Auto-Merge Logic | ADR ACCEPTED + Entity Policy | ADR approval + policy finalization |
| B3 | Conflict Log Table | ADR ACCEPTED | ADR approval |
| B4 | Conflict Resolution API | ADR ACCEPTED + B3 | ADR approval + schema |
| B5 | Conflict UI Contract | ADR ACCEPTED + B4 | ADR approval + API |
| B6 | Delete Conflict Handling | ADR ACCEPTED + B1 | ADR approval + push |
| B7 | State Transition Conflict | ADR ACCEPTED + B1 | ADR approval + push |
| B8 | Activity Offline Writes | ADR ACCEPTED | ADR approval (policy corrected) |
| B9 | Pipeline/Tags/Custom Fields Offline | ADR ACCEPTED | ADR approval (policy corrected) |

### 9.4 TRACK C — REQUIRES ADDITIONAL ARCHITECTURAL DECISION

| # | Component | Why Additional Decision Needed |
|---|-----------|-------------------------------|
| C1 | Multi-device conflict detection | How to handle same entity modified on 3+ devices simultaneously |
| C2 | Offline duration limits | How long can a device stay offline before forced full re-sync |
| C3 | Conflict resolution SLA | How quickly must conflicts be resolved before data is purged |
| C4 | Cross-entity conflict (parent-child) | Account owner changes while Contact is being added |
| C5 | Custom field value conflicts | How to handle custom field value merges (dynamic schema) |

### 9.5 TASK 9 VERDICT

**TRACK A = READY** (9 components unblocked)
**TRACK B = READY_AFTER_ADR** (9 components waiting for ADR approval)
**TRACK_C = 5 ITEMS** (require additional architectural decisions)

---

## TASK 10 — PREPARE ADR FOR HUMAN APPROVAL

### 10.1 Required Revisions

| # | Section | Issue | Severity | Fix |
|---|---------|-------|----------|-----|
| 1 | Entity Policy Table | Activity listed as "Push-Only" — actually mutable | HIGH | Change to "Reject + Auto-Merge Non-Conflicting" |
| 2 | Entity Policy Table | Pipeline listed as "Pull-Only" — actually mutable | HIGH | Change to "Reject + User Resolution" |
| 3 | Entity Policy Table | Tags listed as "Pull-Only" — actually mutable | HIGH | Change to "Reject + User Resolution" |
| 4 | Entity Policy Table | Custom Fields listed as "Pull-Only" — actually mutable | HIGH | Change to "Reject + User Resolution" |
| 5 | PlatformAuditWriter | No @Transactional annotation noted | LOW | Add note about caller-level transaction management |
| 6 | Conflict Report Section 4.1 | Entity classification table has same errors | HIGH | Update to match corrected entity policy |

### 10.2 ADR Status

```
ADR-G7-001 STATUS = REQUIRES_REVISION

REASON: 4 entity policy classifications are factually incorrect
  - Activity is mutable, not push-only
  - Pipeline is mutable, not pull-only
  - Tags is mutable, not pull-only
  - Custom Fields is mutable, not pull-only

INFRASTRUCTURE CLAIMS: ALL VALIDATED
CONFLICT MECHANISM: ALL VALIDATED
AUTO-MERGE ALGORITHM: DEFINED
STATE CONFLICT POLICY: DEFINED
DELETE CONFLICT POLICY: DEFINED
IDEMPOTENCY EXTENSION: DEFINED
CONFLICT LOG SCHEMA: DEFINED
```

### 10.3 Approval Required From

| Role | Responsibility | Decision |
|------|---------------|----------|
| Architecture Owner | Overall architecture consistency | ACCEPT / REJECT / REQUEST CHANGES |
| Product Owner | Entity policy alignment with product requirements | ACCEPT / REJECT / REQUEST CHANGES |
| Security Owner | Tenant isolation, authorization on sync | ACCEPT / REJECT / REQUEST CHANGES |
| Data/Platform Owner | Schema design, migration strategy | ACCEPT / REJECT / REQUEST CHANGES |

### 10.4 TASK 10 VERDICT

**ADR requires REVISION before human approval.** 4 entity policy errors must be corrected.

---

## TASK 11 — IMPLEMENTATION BOUNDARY

**See separate file:** `G7_IMPLEMENTATION_BOUNDARY.md`

---

## FINAL SUMMARY

```
ADR_STATUS = REQUIRES_REVISION
ADR_VALIDATION = PASS (infrastructure) / FAIL (entity policy)
TRACK_A = READY (9 components)
TRACK_B = READY_AFTER_ADR (9 components)
TRACK_C = 5 ITEMS (additional decisions needed)
G7-MOB-001 = PARTIALLY_BLOCKING
```

---

**END OF G7 ADR VALIDATION REPORT**
