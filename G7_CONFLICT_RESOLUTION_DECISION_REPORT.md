# G7 CONFLICT RESOLUTION — ARCHITECTURAL DECISION REPORT

> **Report ID:** G7-CONFLICT-DECISION-V1
> **Date:** 2026-08-11
> **Repository:** https://github.com/snadaiapp-png/SNAD.git
> **Branch:** main
> **Mode:** ARCHITECTURAL DECISION ANALYSIS / READ-ONLY
> **No code modified. No commits made. No migrations executed.**

---

## PHASE 1 — EXISTING POLICY SEARCH

### 1.1 Executive Finding

**YES — SNAD already has a comprehensive, production-grade conflict resolution architecture.** It is NOT a single policy but a multi-layered system built across the entire CRM domain. The system's canonical conflict behavior is: **"Reject stale mutations; client must re-fetch and retry."**

### 1.2 Existing Conflict Resolution Components

| # | Component | Mechanism | HTTP Code | Evidence File | Status |
|---|-----------|-----------|-----------|---------------|--------|
| 1 | Optimistic Locking | `version BIGINT` + `WHERE version = :expectedVersion` | 412 | All JDBC repositories | **IMPLEMENTED** |
| 2 | ETag + If-Match | SHA-256 of `entityType:id:version` + RFC 7232 | 412/428 | `ETagService.java` | **IMPLEMENTED** |
| 3 | Atomic If-Match | `SELECT ... FOR UPDATE` + ETag in transaction | 412 | `CrmOwnershipAtomicIfMatchAspect.java` | **IMPLEMENTED** |
| 4 | Idempotency | `Idempotency-Key` + SHA-256 fingerprint + 24h retention | 409/400 | `IdempotencyService.java` | **IMPLEMENTED** |
| 5 | Concurrent Claim | Unique constraint + `FOR UPDATE` + expected-state | 412 | `JdbcAssignmentRepository.java` | **IMPLEMENTED** |
| 6 | Stale Recommendation | Entity version comparison | 409 | `CrmIntegrationUseCases.java` | **IMPLEMENTED** |
| 7 | Pessimistic Locking | `SELECT ... FOR UPDATE` / `FOR UPDATE SKIP LOCKED` | N/A | Security tokens, outbox, ownership | **IMPLEMENTED** |
| 8 | OptimisticLockFailure Catch | Spring exception → 412 | 412 | `CrmExceptionHandler.java` | **IMPLEMENTED** |
| 9 | Audit Trail | Append-only `platform_audit_logs` with before/after JSON | N/A | `PlatformAuditWriter.java` | **IMPLEMENTED** |
| 10 | Timeline Events | Append-only `crm_timeline_events` | N/A | `JdbcTimelineEventAdapter.java` | **IMPLEMENTED** |
| 11 | Retry Classification | Retryable vs non-retryable at error code level | 412/409 | `CrmErrorCode.java`, `IntegrationErrorCode.java` | **IMPLEMENTED** |
| 12 | Exponential Backoff | Outbox worker with `POWER(2, attempt)` seconds | N/A | `CrmIntegrationStore.java` | **IMPLEMENTED** |
| 13 | Dead Letter Queue | Non-retryable failures → `DEAD_LETTER` status | N/A | `CrmIntegrationStore.java` | **IMPLEMENTED** |

### 1.3 Existing Policy Statement (Extracted)

The codebase's implicit conflict resolution policy is:

> **"Optimistic Concurrency with Rejection"**
>
> 1. Every mutation requires the client to present the current version (via `version` column or `If-Match` header)
> 2. If the version is stale (another mutation occurred), the server rejects with HTTP 412 `CRM_CONCURRENCY_CONFLICT`
> 3. The client must re-fetch the current state, reconcile locally, and retry
> 4. There is NO automatic server-side merge, NO Last-Write-Wins, NO Client-Wins
> 5. Idempotency prevents duplicate mutations from retries
> 6. All mutations are audit-logged with before/after snapshots

**Source:** `CrmErrorCode.java` line 63: `CRM_CONCURRENCY_CONFLICT(412, "The resource was modified by another operation. Please refresh and retry.", true)`

### 1.4 What Does NOT Exist (Mobile Gap)

| Missing Component | Why It Matters for G7 |
|-------------------|----------------------|
| Mobile sync conflict resolution | Server rejects stale pushes, but mobile client cannot always re-fetch immediately |
| Conflict logging for sync | No `mobile_conflict_log` table exists |
| Field-level merge | Server rejects entire mutation, no partial merge |
| Offline-aware conflict handling | Current policy assumes real-time client-server interaction |
| Multi-device conflict detection | No device identity in current version system |
| Deferred resolution queue | No mechanism for conflicts requiring user intervention |

### 1.5 Conclusion

**The server-side conflict resolution policy is ESTABLISHED and PRODUCTIVE.** The G7 mobile sync layer must EXTEND this policy to handle the offline gap — where the client cannot immediately re-fetch and retry. The server's "reject stale" behavior remains the foundation; the mobile layer adds detection, logging, and resolution strategies for the offline-to-online transition.

---

## PHASE 2 — DATA CONFLICT CLASSES

### 2.1 G7-CONFLICT-CLASS-MATRIX

| # | Conflict Type | Example | Business Impact | Data Loss Risk | Security Risk | Recommended Handling | Evidence |
|---|---------------|---------|-----------------|----------------|---------------|---------------------|----------|
| C1 | Same record / same field | Two devices edit Contact.name | HIGH — one edit lost | MEDIUM — field overwrite | LOW | Reject + user resolution | Existing version check catches this |
| C2 | Same record / different fields | Device A edits Contact.phone, Device B edits Contact.email | LOW — non-conflicting | LOW — mergeable | LOW | Auto-merge (field-level) | Server would accept both if sequential |
| C3 | Delete vs Update | Device A deletes Lead, Device B updates Lead | HIGH — update applied to deleted record | MEDIUM — orphaned write | LOW | Reject delete if update pending; server authority on delete | Existing `CRM_CONCURRENCY_CONFLICT` catches |
| C4 | Update vs Delete | Device A updates Opportunity, Device B deletes it | HIGH — same as C3 | MEDIUM | LOW | Same as C3 | Existing version check |
| C5 | Create vs Create | Two devices create Contact with same email | MEDIUM — duplicate records | LOW — duplicates | LOW | Idempotency key + dedup | Existing idempotency framework |
| C6 | Parent vs Child | Device A changes Account.owner, Device B adds Contact to that Account | MEDIUM — orphaned relationship | LOW | LOW | Version check on parent; child creation independent | Existing version check on Account |
| C7 | Reference data vs transactional data | Device A changes Pipeline stages, Device B moves Opportunity through old stages | HIGH — invalid stage reference | MEDIUM | LOW | Reference data is pull-only; no offline writes allowed | G7 design decision |
| C8 | Status transition conflict | Device A marks Lead as CONVERTED, Device B marks same Lead as DISQUALIFIED | HIGH — invalid state | MEDIUM | LOW | State machine validation; reject invalid transitions | Existing state machine in repositories |
| C9 | Permission/ownership conflict | Device A (User X) edits Contact, ownership transferred to User Y while offline | HIGH — unauthorized edit | LOW — authorization | HIGH — stale auth | Re-validate permissions on sync; reject if unauthorized | Existing RBAC + ownership checks |
| C10 | Duplicate mutation | Same edit pushed twice due to network retry | LOW — idempotent | LOW | LOW | Idempotency key dedup | Existing `IdempotencyService` |
| C11 | Reordered offline mutations | Device creates Contact, then updates it; updates arrive before creates | MEDIUM — update applied to non-existent record | LOW | LOW | Sequence numbering; order enforcement | New: requires mutation ordering |
| C12 | Long-offline stale client | Device offline for 7 days; data significantly changed server-side | HIGH — many conflicts | HIGH — bulk rejection | MEDIUM — stale permissions | Full re-sync on reconnect; partial sync for long offline | New: requires re-sync strategy |

### 2.2 Conflict Class Summary

| Severity | Count | Classes |
|----------|-------|---------|
| HIGH | 5 | C1, C3, C4, C8, C9, C12 |
| MEDIUM | 4 | C2, C5, C6, C11 |
| LOW | 3 | C7 (reference data), C10 (idempotency) |

---

## PHASE 3 — POLICY COMPARISON

### 3.1 Evaluation Matrix

| Criteria | A. Last Write Wins | B. Server Wins | C. Client Wins | D. Optimistic Concurrency + Reject | E. Field-Level Merge | F. Version-Based Merge | G. Manual Resolution | H. Domain-Specific | I. Hybrid Policy |
|----------|-------------------|----------------|----------------|-------------------------------------|---------------------|----------------------|---------------------|-------------------|-----------------|
| Data Integrity | LOW | MEDIUM | LOW | **HIGH** | **HIGH** | **HIGH** | **HIGH** | **HIGH** | **HIGH** |
| User Experience | MEDIUM | LOW | HIGH | MEDIUM | **HIGH** | MEDIUM | LOW | MEDIUM | **HIGH** |
| Offline Capability | **HIGH** | LOW | **HIGH** | MEDIUM | MEDIUM | MEDIUM | LOW | MEDIUM | **HIGH** |
| Data Loss Risk | **HIGH** | **HIGH** | **HIGH** | **LOW** | **LOW** | **LOW** | **LOW** | **LOW** | **LOW** |
| Implementation Complexity | LOW | LOW | LOW | MEDIUM | HIGH | HIGH | MEDIUM | HIGH | HIGH |
| Auditability | LOW | MEDIUM | LOW | **HIGH** | **HIGH** | **HIGH** | **HIGH** | **HIGH** | **HIGH** |
| Multi-Tenant Safety | LOW | MEDIUM | LOW | **HIGH** | **HIGH** | **HIGH** | **HIGH** | **HIGH** | **HIGH** |
| CRM Compatibility | MEDIUM | MEDIUM | MEDIUM | **HIGH** | **HIGH** | **HIGH** | **HIGH** | **HIGH** | **HIGH** |
| ERP Compatibility | LOW | MEDIUM | LOW | **HIGH** | **HIGH** | **HIGH** | **HIGH** | **HIGH** | **HIGH** |
| Accounting Compatibility | LOW | LOW | LOW | **HIGH** | MEDIUM | MEDIUM | **HIGH** | **HIGH** | **HIGH** |
| Concurrency Safety | LOW | MEDIUM | LOW | **HIGH** | **HIGH** | **HIGH** | **HIGH** | **HIGH** | **HIGH** |
| Debuggability | LOW | LOW | LOW | **HIGH** | MEDIUM | MEDIUM | **HIGH** | MEDIUM | **HIGH** |
| Scalability | **HIGH** | **HIGH** | **HIGH** | MEDIUM | LOW | LOW | LOW | MEDIUM | MEDIUM |

### 3.2 Policy Analysis

**A. Last Write Wins (LWW)**
- Data Integrity: LOW — silently overwrites without detection
- Data Loss Risk: HIGH — last writer's changes overwrite all prior changes
- Rejected because: Conflicts with existing `CRM_CONCURRENCY_CONFLICT` policy; no audit trail; explicit testing that LWW is prevented (`docs/crm/audit/13-TESTING-AUDIT.md` line 290)

**B. Server Wins**
- Data Integrity: MEDIUM — server state preserved, client changes lost
- Data Loss Risk: HIGH — offline client changes silently discarded
- Rejected because: Mobile users lose data when offline edits are overwritten; poor UX for field teams

**C. Client Wins**
- Data Integrity: LOW — client overwrites server without awareness of server changes
- Data Loss Risk: HIGH — server changes lost
- Rejected because: Another user's concurrent edits are lost; violates multi-tenant safety

**D. Optimistic Concurrency + Reject**
- Data Integrity: HIGH — no silent data loss
- Concurrency Safety: HIGH — version-based detection
- Partially matches existing policy but insufficient alone for mobile (client cannot always re-fetch)

**E. Field-Level Merge**
- Data Integrity: HIGH — non-conflicting fields preserved
- User Experience: HIGH — minimal data loss
- Implementation Complexity: HIGH — requires field-level diffing
- Good for: Same-record/different-field conflicts (C2)

**F. Version-Based Merge**
- Similar to E but uses version metadata for merge decisions
- Good for: Complex entities with many fields

**G. Manual Resolution**
- Data Integrity: HIGH — human decides
- User Experience: LOW — requires user intervention
- Good for: Critical/financial data, ambiguous conflicts

**H. Domain-Specific**
- Different rules per entity type
- Good for: CRM where Account conflicts differ from Task conflicts

**I. Hybrid Policy**
- Combines multiple strategies based on conflict type and entity domain
- Best fit for SNAD: existing server policy + mobile extension

### 3.3 Conclusion

**Option I (Hybrid Policy)** is the recommended approach because:
1. It preserves the existing server-side "reject stale" behavior (Option D)
2. Adds field-level merge for non-conflicting changes (Option E)
3. Adds manual resolution for critical conflicts (Option G)
4. Adds domain-specific rules per entity type (Option H)
5. Extends the existing `CRM_CONCURRENCY_CONFLICT` architecture rather than replacing it

---

## PHASE 4 — DOMAIN CLASSIFICATION

### 4.1 Entity-Level Conflict Strategy

| Entity | Data Class | Conflict Strategy | Reason | Evidence | Risk |
|--------|------------|-------------------|--------|----------|------|
| Account | Master Data | Reject + Auto-Merge (non-conflicting fields) | Account is high-value; field-level merge preserves non-overlapping edits | `crm_accounts` has version column | MEDIUM — name/owner conflicts need resolution |
| Contact | Master Data | Reject + Auto-Merge (non-conflicting fields) | Contacts are frequently edited by field teams | `crm_contacts` has version column | MEDIUM — field conflicts common |
| Lead | Transactional | Reject + User Resolution | Lead lifecycle is state-sensitive; auto-merge could corrupt state machine | `crm_leads` has version column | HIGH — state transitions are critical |
| Opportunity | Transactional | Reject + User Resolution | Opportunity has stage progression; conflicts could corrupt pipeline | `crm_opportunities` has version column | HIGH — financial impact |
| Task | Transactional | Auto-Merge (non-conflicting) + Reject (conflicting) | Tasks are low-risk, frequently edited | `crm_tasks` has version column | LOW |
| Activity | Transactional | Push-Only (no server conflict) | Activities are append-only from mobile; server never conflicts | `crm_activities` has version column | LOW |
| Note | Transactional | Push-Only (no server conflict) | Notes are append-only | `crm_notes` has version column | LOW |
| Pipeline | Reference Data | Pull-Only (no offline writes) | Pipeline structure is server-authoritative | `crm_pipelines` has version column | LOW — read-only on mobile |
| Custom Fields | Reference Data | Pull-Only (no offline writes) | Field definitions are server-authoritative | `crm_custom_field_definitions` has version column | LOW — read-only on mobile |
| Tags | Reference Data | Pull-Only (no offline writes) | Tag definitions are server-authoritative | `crm_tags` has version column | LOW — read-only on mobile |

### 4.2 Classification Summary

| Strategy | Entities | Count |
|----------|----------|-------|
| Reject + Auto-Merge (non-conflicting fields) | Account, Contact | 2 |
| Reject + User Resolution | Lead, Opportunity | 2 |
| Auto-Merge + Reject (conflicting) | Task | 1 |
| Push-Only (no server conflict) | Activity, Note | 2 |
| Pull-Only (no offline writes) | Pipeline, Custom Fields, Tags | 3 |

---

## PHASE 5 — FINANCIAL / CRITICAL DATA

### 5.1 Financial Data Analysis

| Data Type | Present in SNAD? | Offline Write Allowed? | Conflict Policy | Reason |
|-----------|------------------|----------------------|-----------------|--------|
| Invoices | NOT YET (planned future) | NO | N/A — not in G7 scope | Invoice generation must be server-authoritative |
| Payments | NOT YET (planned future) | NO | N/A — not in G7 scope | Payment processing requires real-time validation |
| Orders | NOT YET (planned future) | NO | N/A — not in G7 scope | Order state machine requires server control |
| Inventory | NOT YET (planned future) | NO | N/A — not in G7 scope | Stock levels require real-time accuracy |
| Approvals | YES (TransferUseCases) | NO (server-only) | Reject + Server Authority | Transfer approvals use `WorkflowPort` with version checks |
| Workflow State | YES (CrmWorkflowUseCases) | NO (server-only) | Reject + State Machine | State transitions validated server-side with version checks |
| Audit Records | YES (PlatformAuditWriter) | NO (append-only, server) | N/A — append-only | Audit logs are never modified, only appended |

### 5.2 Critical Data Rules

1. **NO financial data exists in G7 scope** — Invoices, payments, orders, inventory are future features
2. **Approvals are server-authoritative** — Transfer approvals cannot be created/modified offline
3. **Workflow state is server-authoritative** — Workflow dispatch/callbacks happen server-side
4. **Audit records are immutable** — Append-only, no conflict possible
5. **The ONLY financial-adjacent data in G7 scope is Opportunity** — which has revenue/cost fields but no actual financial transactions

### 5.3 Decision

**For G7 Mobile Offline Foundation:**
- No financial data requires special conflict handling (none exists in scope)
- Opportunity (closest to financial) uses "Reject + User Resolution" strategy
- All approval/workflow operations are excluded from offline writes
- The `CRM_CONCURRENCY_CONFLICT` (412) pattern is sufficient for financial data when it eventually enters scope

---

## PHASE 6 — PROPOSED G7 POLICY

### 6.1 G7 DEFAULT CONFLICT POLICY

**"Optimistic Concurrency with Progressive Resolution"**

```
DEFAULT = Server Authority + Client Notification
```

**Rule:** When a mobile client pushes a mutation whose base version does not match the server's current version:
1. The server REJECTS the mutation (HTTP 412 or sync conflict response)
2. The conflict is LOGGED in `mobile_conflict_log`
3. The client is NOTIFIED of the conflict with both versions
4. The client decides: retry with fresh data, merge locally, or escalate to user

**This extends the existing `CRM_CONCURRENCY_CONFLICT` pattern to the mobile sync layer.**

### 6.2 G7 ENTITY-CLASS POLICIES

| Entity Class | Policy | Auto-Merge Allowed? | User Resolution Required? |
|-------------|--------|---------------------|--------------------------|
| Master Data (Account, Contact) | Reject + Auto-Merge Non-Conflicting | YES (non-overlapping fields) | Only for overlapping field conflicts |
| Transactional (Lead, Opportunity) | Reject + User Resolution | NO | YES — always |
| Task | Reject + Auto-Merge | YES (non-overlapping fields) | Only for overlapping field conflicts |
| Activity, Note | Push-Only (Server Accepts) | N/A — no server conflict | NO |
| Reference (Pipeline, Tags, Custom Fields) | Pull-Only (No Offline Write) | N/A | N/A |

### 6.3 G7 CRITICAL DATA POLICY

**For any future financial/critical data entering G7 scope:**

```
CRITICAL_DATA = Server Authority + Reject + Manual Resolution
```

- NO automatic merge
- NO Last Write Wins
- NO Client Wins
- Conflict logged with full before/after snapshots
- User MUST resolve before mutation is applied
- Audit trail mandatory

**Current G7 scope has NO critical financial data** — this policy is preemptive for future entities.

### 6.4 G7 MANUAL RESOLUTION POLICY

**When is manual resolution REQUIRED?**

| Condition | Resolution |
|-----------|------------|
| Same field modified on both sides | User must choose |
| Status/state transition conflict | User must choose valid transition |
| Delete vs Update conflict | User must choose: keep deleted or apply update |
| Ownership/permission conflict | Server authority; reject offline mutation |
| More than 3 conflicts in single sync batch | Pause sync; require user review |
| Conflict on entity with financial impact | User must resolve before sync completes |

**When is manual resolution NOT required?**

| Condition | Resolution |
|-----------|------------|
| Different fields modified | Auto-merge |
| Activity/Note creation (push-only) | Server accepts |
| Reference data read (pull-only) | No conflict possible |
| Idempotent retry of same mutation | Dedup, no conflict |

---

## PHASE 7 — REQUIRED DATA MODEL

### 7.1 Fields Required for Conflict Resolution

| # | Field | Purpose | Owner | Table | Index | Constraint | Retention | Required? |
|---|-------|---------|-------|-------|-------|------------|-----------|-----------|
| 1 | `version` | Optimistic lock version | Server | All CRM entities | Already exists | `NOT NULL DEFAULT 0` | Permanent | **EXISTS** |
| 2 | `updated_at` | Timestamp of last modification | Server | All CRM entities | Already exists | `NOT NULL` | Permanent | **EXISTS** |
| 3 | `updated_by` | User who last modified | Server | All CRM entities | Already exists | `NOT NULL` | Permanent | **EXISTS** |
| 4 | `created_at` | Creation timestamp | Server | All CRM entities | Already exists | `NOT NULL` | Permanent | **EXISTS** |
| 5 | `created_by` | User who created | Server | All CRM entities | Already exists | `NOT NULL` | Permanent | **EXISTS** |
| 6 | `device_id` | Which device performed mutation | Client | `mobile_sync_log` | YES | FK to device registry | 90 days | **NEW** |
| 7 | `mutation_id` | Unique ID for each offline mutation | Client | `mobile_sync_log` | YES (UNIQUE) | `NOT NULL` | 90 days | **NEW** |
| 8 | `idempotency_key` | Prevent duplicate mutations | Client | `mobile_sync_log` | YES | `NOT NULL` | 90 days | **NEW** |
| 9 | `base_version` | Server version when client read entity | Client | `mobile_sync_log` | YES | `NOT NULL` | 90 days | **NEW** |
| 10 | `server_version` | Server version at time of push attempt | Server | `mobile_sync_log` | YES | `NOT NULL` | 90 days | **NEW** |
| 11 | `conflict_id` | Unique identifier for conflict event | Server | `mobile_conflict_log` | YES (PK) | `NOT NULL` | 1 year | **NEW** |
| 12 | `conflict_status` | Status of conflict resolution | Server | `mobile_conflict_log` | YES | `NOT NULL DEFAULT 'PENDING'` | 1 year | **NEW** |
| 13 | `resolution` | How conflict was resolved | Server | `mobile_conflict_log` | YES | Nullable (NULL = unresolved) | 1 year | **NEW** |
| 14 | `resolved_by` | Who resolved the conflict | Server | `mobile_conflict_log` | YES | Nullable (NULL = unresolved) | 1 year | **NEW** |
| 15 | `resolved_at` | When conflict was resolved | Server | `mobile_conflict_log` | YES | Nullable (NULL = unresolved) | 1 year | **NEW** |

### 7.2 Fields NOT Required

| Field | Reason Not Required |
|-------|-------------------|
| `revision` | Not needed — `version` column already serves this purpose |
| `sync_cursor` | Not a per-entity field — belongs in `mobile_sync_cursor` table |
| `server_version` per entity | Not needed — entity `version` column is the server version |

### 7.3 Schema Impact

**Existing tables need NO changes** — `version`, `updated_at`, `updated_by`, `created_at`, `created_by` already exist on all CRM entities.

**New tables required:**
- `mobile_sync_log` (from G7 master baseline)
- `mobile_conflict_log` (from G7 master baseline)
- `mobile_device_registry` (from G7 master baseline)
- `mobile_sync_cursor` (from G7 master baseline)

---

## PHASE 8 — SYNC PROTOCOL

### 8.1 Sync Flow with Conflict Handling

```
CLIENT (Mobile Device)
  │
  ├─ Local Mutation
  │    ├─ Assign mutation_id (UUID)
  │    ├─ Record base_version (version at time of read)
  │    ├─ Generate idempotency_key
  │    └─ Add to offline queue
  │
  ├─ Sync Request (when online)
  │    ├─ Collect pending mutations from queue
  │    ├─ Package: [{entity_type, entity_id, mutation_id, operation,
  │    │            payload, base_version, idempotency_key, device_id}]
  │    └─ POST /api/v2/mobile/sync/push
  │
  └─ SERVER PROCESSING
       │
       ├─ For each mutation:
       │    ├─ Validate tenant context (RLS)
       │    ├─ Validate user authorization (RBAC)
       │    ├─ Check idempotency (dedup)
       │    │    ├─ DUPLICATE → return cached result (APPLIED)
       │    │    └─ NEW → continue
       │    ├─ Load current entity version
       │    │    ├─ NOT_FOUND → if CREATE, proceed; if UPDATE/DELETE, return REJECTED
       │    │    └─ FOUND → continue
       │    ├─ Compare versions
       │    │    ├─ base_version == server.version → APPLY
       │    │    │    ├─ Execute mutation
       │    │    │    ├─ Increment version
       │    │    │    ├─ Log to mobile_sync_log (status=APPLIED)
       │    │    │    └─ Return {status: APPLIED, new_version}
       │    │    │
       │    │    └─ base_version != server.version → CONFLICT
       │    │         ├─ Log to mobile_conflict_log
       │    │         ├─ Classify conflict type
       │    │         │    ├─ Different fields? → AUTO_MERGE (if policy allows)
       │    │         │    ├─ Same field? → USER_RESOLUTION
       │    │         │    └─ Delete conflict? → SERVER_AUTHORITY
       │    │         ├─ Apply resolution (if auto-merge)
       │    │         └─ Return {status: CONFLICT, server_version,
       │    │                   conflict_id, resolution_required}
       │    │
       │    ├─ Authorization check (post-mutation)
       │    │    ├─ UNAUTHORIZED → reject, log to mobile_sync_log (status=UNAUTHORIZED)
       │    │    └─ AUTHORIZED → continue
       │    │
       │    └─ Return per-mutation result
       │
       └─ Return batch result: {results: [...], has_more, new_cursor}
```

### 8.2 Sync Result Codes

| Code | Meaning | Client Action |
|------|---------|---------------|
| `APPLIED` | Mutation successfully applied | Mark mutation as synced |
| `DUPLICATE` | Idempotent replay of same mutation | Mark mutation as synced |
| `CONFLICT` | Version mismatch detected | Review conflict, retry or resolve |
| `REJECTED` | Entity not found or invalid state | Discard mutation, re-sync |
| `UNAUTHORIZED` | User no longer authorized | Re-authenticate, re-sync |
| `INVALID` | Malformed mutation payload | Fix payload, retry |
| `RETRYABLE` | Transient server error | Retry with backoff |
| `PERMANENT_FAILURE` | Non-retryable error | Log, notify user |

### 8.3 Auto-Merge Rules (Field-Level)

For entities where auto-merge is permitted (Account, Contact, Task):

```
IF conflict_type == SAME_RECORD_DIFFERENT_FIELDS:
    FOR each field in client_mutation:
        IF field NOT IN server_mutation_since_base:
            APPLY client field value
        ELSE:
            FLAG for user resolution
    IF all fields auto-merged:
        RETURN APPLIED (merged)
    ELSE:
        RETURN CONFLICT (partial merge + user resolution needed)
```

---

## PHASE 9 — IDEMPOTENCY

### 9.1 Relationship Between Conflict Resolution + Idempotency + Ordering + Retry

```
┌─────────────────────────────────────────────────────────┐
│                    MUTATION LIFECYCLE                     │
│                                                          │
│  1. CREATE MUTATION (offline)                            │
│     ├─ mutation_id = UUID (unique per mutation)          │
│     ├─ idempotency_key = SHA-256(mutation_id + payload) │
│     └─ sequence_number = monotonic per device            │
│                                                          │
│  2. QUEUE MUTATION (offline queue)                       │
│     ├─ Ordered by sequence_number (FIFO per entity)      │
│     └─ Retained until confirmed APPLIED                  │
│                                                          │
│  3. PUSH MUTATION (sync)                                 │
│     ├─ Idempotency check (server-side)                   │
│     │    ├─ Same key + same payload → DUPLICATE (safe)   │
│     │    ├─ Same key + different payload → REJECT (409)  │
│     │    └─ New key → proceed                            │
│     ├─ Version check                                     │
│     │    ├─ Match → APPLIED                              │
│     │    └─ Mismatch → CONFLICT                          │
│     └─ Authorization check                               │
│                                                          │
│  4. RETRY (network failure)                              │
│     ├─ Same idempotency_key → safe to retry              │
│     ├─ Server deduplicates → returns cached result       │
│     └─ No duplicate mutation created                     │
│                                                          │
│  5. CONFLICT RESOLUTION                                  │
│     ├─ User resolves conflict                            │
│     ├─ NEW mutation_id + NEW idempotency_key             │
│     └─ New mutation enters queue as fresh operation      │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 9.2 Specific Scenarios

| Scenario | Handling | Evidence |
|----------|----------|----------|
| Duplicate mutation (same payload, same key) | Server returns cached APPLIED result | `IdempotencyService` fingerprint matching |
| Retry after timeout | Same idempotency_key → dedup | `crm_idempotency_records` table |
| Client retry after server commit | Same idempotency_key → returns cached result | `IdempotencyService.begin()` returns `ReplayHit` |
| Out-of-order mutations | Sequence number check; reject out-of-order | NEW: requires sequence enforcement |
| Same mutation from multiple devices | Different idempotency_keys → both processed; version check catches conflict | Device-specific keys |

### 9.3 Ordering Enforcement

**Required for G7:** Mutations on the same entity must be applied in order.

```
IF mutation.base_version < previous_mutation.server_version:
    REJECT with "out of order" error
    CLIENT must re-sync entity and re-queue mutations
```

This prevents:
- Update arriving before Create
- Delete arriving before Update
- Cascading version mismatches

---

## PHASE 10 — SECURITY

### 10.1 Security Verification for Conflict Resolution

| Threat | Risk | Mitigation | Evidence |
|--------|------|------------|----------|
| Cross-Tenant Mutation | HIGH | RLS policies enforced on all sync operations; `tenant_id` validated at API boundary | `TenantRlsDataSource` enforces tenant isolation |
| Privilege Escalation | HIGH | RBAC check on every mutation; role validated at sync push endpoint | Existing `JwtAuthenticationFilter` + RBAC |
| Unauthorized Update | HIGH | Ownership validation; `updated_by` must match authenticated user | `CrmOwnershipAtomicIfMatchAspect` validates ownership |
| Stale Authorization | MEDIUM | Re-validate permissions on sync push; reject if user no longer authorized | NEW: sync endpoint must re-check RBAC |
| Replay Attack | MEDIUM | Idempotency key + fingerprint; same payload = safe replay, different payload = reject | `IdempotencyService` with SHA-256 fingerprint |
| Data Leakage | MEDIUM | Conflict log contains entity data; must be tenant-isolated and access-controlled | NEW: `mobile_conflict_log` must have RLS |

### 10.2 Pre-Mutation Authorization Check (Sync Push)

```
SERVER receives sync push mutation:
  1. Extract JWT from request
  2. Validate JWT signature + expiry
  3. Extract tenant_id from JWT
  4. Validate tenant_id matches mutation.tenant_id
  5. Extract user_id from JWT
  6. Validate user has WRITE permission on entity_type
  7. Validate user has ownership or team membership on specific entity
  8. IF any check fails → REJECT with UNAUTHORIZED
  9. PROCEED with version check + mutation
```

### 10.3 Post-Mutation Authorization Check

```
AFTER applying mutation:
  1. Re-validate that the user still has authorization
  2. IF ownership changed during mutation → CONFLICT (ownership conflict)
  3. IF user was deactivated → REJECT (stale authorization)
  4. IF tenant was deactivated → REJECT
```

---

## PHASE 11 — AUDIT

### 11.1 Conflict Audit Requirements

| Field | Mandatory? | Purpose |
|-------|-----------|---------|
| `conflict_id` | YES (auto-generated UUID) | Unique identifier for conflict event |
| `tenant_id` | YES | Multi-tenant isolation |
| `entity_type` | YES | Which entity type conflicted |
| `entity_id` | YES | Which specific record conflicted |
| `device_id` | YES | Which device produced the conflicting mutation |
| `user_id` | YES | Which user was operating the device |
| `client_version` | YES | Version the client thought was current |
| `server_version` | YES | Version the server actually had |
| `mutation_id` | YES | The mutation that caused the conflict |
| `client_payload` | YES | What the client tried to write |
| `server_payload` | YES | What the server had at conflict time |
| `detected_at` | YES | Timestamp of conflict detection |
| `resolution_strategy` | CONDITIONAL | How conflict was resolved (NULL if unresolved) |
| `resolution_result` | CONDITIONAL | Outcome of resolution (NULL if unresolved) |
| `resolved_by` | CONDITIONAL | Who resolved (NULL if auto-resolved or unresolved) |
| `resolved_at` | CONDITIONAL | When resolved (NULL if unresolved) |
| `correlation_id` | OPTIONAL | For distributed tracing |

### 11.2 Sync Operation Audit

Every sync operation (PULL and PUSH) must be logged to `mobile_sync_log`:

| Field | Mandatory? | Purpose |
|-------|-----------|---------|
| `id` | YES (auto-generated UUID) | Unique log entry |
| `tenant_id` | YES | Multi-tenant isolation |
| `device_id` | YES | Device identity |
| `user_id` | YES | User identity |
| `operation` | YES | PULL or PUSH |
| `entity_type` | YES | Entity type involved |
| `entity_id` | CONDITIONAL | Specific record (NULL for batch) |
| `mutation_id` | CONDITIONAL | Client mutation ID (NULL for PULL) |
| `idempotency_key` | CONDITIONAL | For dedup tracking |
| `base_version` | CONDITIONAL | Client's expected version |
| `server_version` | CONDITIONAL | Server's actual version |
| `status` | YES | APPLIED/DUPLICATE/CONFLICT/REJECTED/UNAUTHORIZED |
| `synced_at` | YES | Timestamp |

---

## PHASE 12 — UX REQUIREMENTS

### 12.1 Auto-Resolution Scenarios (No User Action Required)

| Scenario | Resolution | User Sees |
|----------|-----------|-----------|
| Different fields modified | Auto-merge | Sync indicator shows "merged" |
| Idempotent retry | Dedup | No notification |
| Pull-only entity updated server-side | Fresh data on next pull | Data refreshes silently |
| Push-only entity (Activity, Note) created | Server accepts | Confirmation |
| Network retry of same mutation | Idempotent dedup | No notification |

### 12.2 User Resolution Required Scenarios

| Scenario | User Sees | Required Information |
|----------|----------|---------------------|
| Same field modified on both sides | "Conflict detected" dialog | Local change, Server change, Timestamp, User who made each change, Fields in conflict, Resolution options |
| Status/state transition conflict | "Conflict detected" dialog | Current status, Attempted transition, Server's transition, Valid transitions |
| Delete vs Update conflict | "Record was deleted by another user" | Who deleted, When, What the local change was |
| Ownership conflict | "Record ownership changed" | Previous owner, New owner, Your change |
| More than 3 conflicts in batch | "Sync paused — review required" | List of conflicting entities, Option to resolve one-by-one or skip all |

### 12.3 Resolution Options Presented to User

| Option | Behavior |
|--------|----------|
| Keep Server Version | Discard local change, use server data |
| Keep My Version | Override server with local data (triggers new version) |
| Merge Manually | Show field-by-field comparison for manual selection |
| Skip This Conflict | Leave unresolved, retry later |
| Skip All Conflicts | Apply all non-conflicting changes, queue all conflicts for later |

### 12.4 Conflict Notification Priority

| Priority | Trigger | Notification |
|----------|---------|-------------|
| LOW | Auto-merged, no data loss | Silent (sync status indicator) |
| MEDIUM | Single field conflict, user resolution available | Badge notification |
| HIGH | Multiple conflicts, batch paused | Active notification + badge |
| CRITICAL | Financial/critical data conflict (future) | Blocking notification, must resolve before continuing |

---

## PHASE 13 — ADR

**See separate file:** `ADR-G7-001-MOBILE-CONFLICT-RESOLUTION.md`

Status: **PROPOSED**

---

## PHASE 14 — BLOCKER RECLASSIFICATION

### 14.1 Analysis

G7-MOB-001 (Conflict Resolution Policy) was classified as **P0 BLOCKER** in the G7 master baseline.

After this analysis, the reclassification is:

**G7-MOB-001 = PARTIALLY_BLOCKING**

### 14.2 Reasoning

The conflict resolution policy is required ONLY for:
- Sync push endpoint (conflict detection + resolution)
- Conflict logging (mobile_conflict_log table)
- Client-side conflict UI

The conflict resolution policy is NOT required for:
- Local storage setup (independent)
- Sync transport layer (independent)
- Connectivity detection (independent)
- Authentication plumbing (independent)
- Observability/metrics (independent)
- Pull-only sync (no conflict possible)
- Push-only entities (Activity, Note — no server conflict)
- Reference data pull (Pipeline, Tags — no conflict possible)

### 14.3 Reclassification

| Item | Previous | New | Reason |
|------|----------|-----|--------|
| G7-MOB-001 | P0 BLOCKER | **PARTIALLY_BLOCKING** | Policy needed for push conflict handling; not needed for independent components |
| Local Storage | BLOCKED | **UNBLOCKED** | Can proceed without conflict policy |
| Sync Transport | BLOCKED | **UNBLOCKED** | Can proceed without conflict policy |
| Connectivity Detection | BLOCKED | **UNBLOCKED** | Can proceed without conflict policy |
| Auth Plumbing | BLOCKED | **UNBLOCKED** | Can proceed without conflict policy |
| Sync Push Endpoint | BLOCKED | **BLOCKED** | Requires conflict policy for version check + resolution |
| Conflict Logging | BLOCKED | **BLOCKED** | Requires conflict policy for log schema |
| Client Conflict UI | BLOCKED | **BLOCKED** | Requires conflict policy for resolution options |

---

## FINAL OUTPUT

```
G7 CONFLICT DECISION

EXISTING_POLICY = Optimistic Concurrency with Rejection (HTTP 412 CRM_CONCURRENCY_CONFLICT)
EVIDENCE = ETagService.java, CrmErrorCode.java, CrmOwnershipAtomicIfMatchAspect.java,
           all JDBC repositories (WHERE version = :expectedVersion),
           CrmConcurrencyContractTest.java, TransferUseCases.java

PROPOSED_DEFAULT_POLICY = Optimistic Concurrency with Progressive Resolution
  (Server rejects stale mutations + Mobile layer adds detection, logging, auto-merge for non-conflicts,
   user resolution for conflicts)

ENTITY_SPECIFIC_POLICIES =
  Account:   Reject + Auto-Merge (non-conflicting fields)
  Contact:   Reject + Auto-Merge (non-conflicting fields)
  Lead:      Reject + User Resolution
  Opportunity: Reject + User Resolution
  Task:      Reject + Auto-Merge (non-conflicting fields)
  Activity:  Push-Only (server accepts)
  Note:      Push-Only (server accepts)
  Pipeline:  Pull-Only (no offline writes)
  Tags:      Pull-Only (no offline writes)
  Custom Fields: Pull-Only (no offline writes)

CRITICAL_DATA_POLICY = Server Authority + Reject + Manual Resolution
  (No financial data in G7 scope; policy preemptive for future entities)

MANUAL_RESOLUTION_REQUIRED = YES
  (For same-field conflicts, state transition conflicts, delete-vs-update, ownership conflicts,
   and batches with >3 conflicts)

REQUIRED_VERSIONING = YES (version column — ALREADY EXISTS on all CRM tables)
REQUIRED_IDEMPOTENCY = YES (idempotency_key per mutation — ALREADY EXISTS for server APIs;
                            NEW: extend to mobile sync mutations)
REQUIRED_CONFLICT_TABLE = YES (mobile_conflict_log — NEW table)

G7-MOB-001 = PARTIALLY_BLOCKING
  (Blocking for: sync push, conflict logging, client UI
   NOT blocking for: local storage, sync transport, connectivity, auth, observability)

DECISION_STATUS = PROPOSED
```

---

## FINAL RULES COMPLIANCE

| Rule | Compliance |
|------|------------|
| No code modified | ✅ |
| No commits | ✅ |
| No migrations executed | ✅ |
| No APIs created | ✅ |
| No policy chosen without evidence | ✅ All policies evaluated with evidence |
| No Last Write Wins as default | ✅ REJECTED — conflicts with existing architecture |
| No Client Wins as default | ✅ REJECTED — data loss risk |
| No Server Wins without analysis | ✅ ANALYZED — insufficient for mobile offline |
| Critical/financial data separated | ✅ Phase 5 confirms no financial data in G7 scope |
| Official policy stated if none exists | ✅ Existing policy extracted from codebase |
| Decision convertible to tests + gates | ✅ All decisions map to test scenarios in Phase 12 |

---

**END OF G7 CONFLICT RESOLUTION DECISION REPORT**
