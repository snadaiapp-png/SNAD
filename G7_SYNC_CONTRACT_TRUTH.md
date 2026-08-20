# G7 SYNC CONTRACT TRUTH

**Phase 7 Reconciliation Gate — Complete Sync Contract Definition**
**Status:** DEFINITIVE
**Authority:** This document is the single source of truth for all client-server synchronization behavior.

---

## 1. MUTATION IDENTITY

Every mutation is uniquely identified by the combination of:

| Field | Type | Source | Description |
|-------|------|--------|-------------|
| `entity_type` | enum | Client | One of: CONTACT, ACCOUNT, LEAD, OPPORTUNITY, TASK, ACTIVITY, NOTE |
| `entity_id` | UUID | Client | Unique identifier for the entity instance |
| `operation` | enum | Client | One of: CREATE, UPDATE, DELETE |
| `idempotency_key` | UUID v4 | Client | Generated client-side, globally unique, never reused |

The `idempotency_key` is the sole identity for deduplication. The same mutation submitted multiple times carries the same `idempotency_key`.

---

## 2. MUTATION ENVELOPE

Every mutation transmitted between client and server is wrapped in a standard envelope.

### 2.1 Envelope Schema

```json
{
  "idempotency_key": "uuid",
  "entity_type": "CONTACT | ACCOUNT | LEAD | OPPORTUNITY | TASK | ACTIVITY | NOTE",
  "entity_id": "uuid",
  "operation": "CREATE | UPDATE | DELETE",
  "base_version": 5,
  "payload": {},
  "timestamp": "ISO-8601",
  "device_id": "uuid"
}
```

### 2.2 Field Constraints

| Field | Required | Constraint |
|-------|----------|------------|
| `idempotency_key` | Yes | UUID v4, client-generated |
| `entity_type` | Yes | Valid enum value |
| `entity_id` | Yes | UUID v4 |
| `operation` | Yes | Valid enum value |
| `base_version` | Yes | Client's known version at time of mutation; 0 for CREATE |
| `payload` | Conditional | Required for CREATE and UPDATE; omitted for DELETE |
| `timestamp` | Yes | ISO-8601 with timezone, UTC preferred |
| `device_id` | Yes | UUID identifying the originating device |

---

## 3. LOCAL PERSISTENCE

### 3.1 Storage Engine

| Platform | Engine | Rationale |
|----------|--------|-----------|
| React Native (Mobile) | SQLite | Native performance, transactional guarantees |
| Web / PWA | IndexedDB | Browser-native, large storage capacity |

### 3.2 Schema Requirements

- Local schema mirrors server entity models exactly.
- Each local entity record includes a `local_version` field tracking the last-known server version.
- A dedicated `pending_mutations` queue table stores offline mutations awaiting sync.

### 3.3 Queue Table Schema (Conceptual)

```
pending_mutations:
  id                  INTEGER PRIMARY KEY AUTOINCREMENT
  idempotency_key     TEXT NOT NULL UNIQUE
  entity_type         TEXT NOT NULL
  entity_id           TEXT NOT NULL
  operation           TEXT NOT NULL
  base_version        INTEGER NOT NULL
  payload             TEXT          -- JSON, NULL for DELETE
  timestamp           TEXT NOT NULL
  device_id           TEXT NOT NULL
  status              TEXT NOT NULL  -- QUEUED | READY | SENT | ACKNOWLEDGED | CONFLICT | RETRYABLE_FAILURE | PERMANENT_FAILURE | FAILED
  retry_count         INTEGER DEFAULT 0
  last_error          TEXT
  sequence_number     INTEGER NOT NULL
  created_at          TEXT NOT NULL
  updated_at          TEXT NOT NULL
```

### 3.4 Local Version Tracking

Each entity in the local cache stores:

| Field | Description |
|-------|-------------|
| `server_version` | Last-known server version for this entity |
| `local_modified` | Boolean flag indicating unsynced local changes |
| `last_synced_at` | Timestamp of last successful sync for this entity |

---

## 4. QUEUE

### 4.1 Queue Ordering

- FIFO per entity type.
- Mutations within the same entity type are ordered by `sequence_number`.
- No cross-entity-type ordering guarantee.

### 4.2 Queue Entry

Each entry in the offline queue consists of:

| Field | Description |
|-------|-------------|
| `mutation_envelope` | The complete mutation envelope (Section 2) |
| `status` | Current state in the state machine (Section 5) |
| `retry_count` | Number of retry attempts for this mutation |
| `last_error` | Most recent error message (if any) |
| `sequence_number` | Monotonically increasing integer per entity type |

### 4.3 Queue Persistence

The queue is persisted to local storage (SQLite / IndexedDB). It survives app restarts, device reboots, and network disconnections.

---

## 5. QUEUE STATE MACHINE

```
                        +----------------+
                        |  LOCAL_CHANGE  |
                        +-------+--------+
                                |
                                v
                        +-------+--------+
                   +--->|    QUEUED      |
                   |    +-------+--------+
                   |            |
                   |            v
                   |    +-------+--------+
                   |    |     READY      |
                   |    +-------+--------+
                   |            |
                   |            v
                   |    +-------+--------+
                   |    |      SENT      |
                   |    +-------+--------+
                   |      /    |    \     \
                  v   v       v     v      v
     +--------+  +-------+  +----------+  +------------------+
     |ACKNOWLEDGED|CONFLICT|RETRYABLE_|  |PERMANENT_FAILURE|
     +-----+------+-------+  |FAILURE   |  +--------+---------+
           |            |    +----+-----+           |
           v            |         |                  v
     [REMOVED]          v         v              [FAILED]
     from queue    [LOGGED]    RETRY
                  to conflict    |
                  log            v
                          back to READY
```

### 5.1 State Definitions

| State | Description |
|-------|-------------|
| `LOCAL_CHANGE` | Entity modified locally, not yet enqueued |
| `QUEUED` | Mutation written to local persistence queue |
| `READY` | Eligible for transmission (not blocked, not in backoff) |
| `SENT` | Transmitted to server, awaiting response |
| `ACKNOWLEDGED` | Server confirmed successful application |
| `CONFLICT` | Server detected version mismatch |
| `RETRYABLE_FAILURE` | Transient error (network, timeout, 500) |
| `PERMANENT_FAILURE` | Non-retryable error (401, 403, 404, 412) |
| `FAILED` | Terminal failure, requires manual intervention |
| `STALE` | Cursor invalid, full resync required |
| `FULL_RESYNC_REQUIRED` | Triggers complete cache wipe and rebuild |

### 5.2 Transitions

| From | To | Trigger |
|------|----|---------|
| LOCAL_CHANGE | QUEUED | Mutation written to local queue |
| QUEUED | READY | Queue scheduler selects mutation for sending |
| READY | SENT | Mutation transmitted to server |
| SENT | ACKNOWLEDGED | Server returns success status |
| SENT | CONFLICT | Server returns version mismatch |
| SENT | RETRYABLE_FAILURE | Transient error (network, timeout, 500) |
| SENT | PERMANENT_FAILURE | Non-retryable error (401, 403, 404, 412) |
| RETRYABLE_FAILURE | READY | Backoff timer expires, retry eligible |
| PERMANENT_FAILURE | FAILED | Max retries exceeded or non-retryable error |
| STALE | FULL_RESYNC_REQUIRED | Cursor invalid or token expiry detected |

---

## 6. PULL (Server to Client)

### 6.1 Pull Request

The client initiates a pull by sending:

| Field | Type | Description |
|-------|------|-------------|
| `last_sync_cursor` | string | Opaque cursor from previous pull; null for initial sync |
| `entity_types` | enum[] | Subset of entity types to pull; null for all |
| `tenant_id` | UUID | Tenant identifier (bound from auth context) |

### 6.2 Server Processing

1. Server validates tenant context (RLS enforcement).
2. Server queries: `SELECT * FROM entities WHERE updated_at > last_sync_cursor ORDER BY updated_at ASC`.
3. Server applies entity_type filter if specified.
4. Server packages results into response.

### 6.3 Pull Response

| Field | Type | Description |
|-------|------|-------------|
| `entities` | array | Array of entity objects with current server state |
| `new_cursor` | string | Opaque cursor representing the sync point |
| `has_more` | boolean | True if more entities exist beyond this page |

### 6.4 Client Pull Processing

1. Client receives response.
2. Client upserts each entity into local cache (SQLite/IndexedDB).
3. Client updates local `server_version` for each entity.
4. Client stores `new_cursor` for next pull.
5. Client clears `local_modified` flag for any entity that matches server state.

### 6.5 Pagination

- Server returns a maximum page size (configurable, default 1000 entities per page).
- If `has_more` is true, client continues pulling with `new_cursor` until `has_more` is false.
- Client performs incremental pulls during normal operation; full pulls only on resync.

---

## 7. PUSH (Client to Server)

### 7.1 Push Request

The client sends a batch of mutations:

```json
{
  "operations": [
    { "mutation_envelope_1" },
    { "mutation_envelope_2" },
    ...
  ]
}
```

### 7.2 Server Processing (Per Mutation)

For each mutation envelope in the batch, the server applies the following logic:

```
IF idempotency_key exists in idempotency store:
    RETURN cached_result (Section 12)

IF entity does NOT exist AND operation == CREATE:
    Apply mutation
    Increment version
    RETURN { status: "ACKNOWLEDGED", new_version: 1 }

IF entity DOES exist:
    IF entity.version == client.base_version:
        Apply mutation
        Increment version
        RETURN { status: "ACKNOWLEDGED", new_version: new_version }
    ELSE:
        RETURN { status: "CONFLICT", server_version, client_version, conflict_id }

IF operation == DELETE AND entity does NOT exist:
    RETURN { status: "ACKNOWLEDGED", new_version: null }
```

### 7.3 Push Response

| Field | Type | Description |
|-------|------|-------------|
| `results` | array | Per-mutation result objects |

Each result object:

| Field | Type | Description |
|-------|------|-------------|
| `idempotency_key` | UUID | Matches the input mutation |
| `status` | enum | ACKNOWLEDGED, CONFLICT, RETRYABLE_FAILURE, PERMANENT_FAILURE |
| `new_version` | integer | New server version (on ACKNOWLEDGED) |
| `conflict` | object | Conflict details (on CONFLICT) |

### 7.4 Batch Integrity

- Each mutation in a batch is processed independently.
- A failure or conflict on one mutation does not prevent processing of others.
- The server returns a result for every mutation in the batch, even if some fail.

---

## 8. CURSOR

### 8.1 Cursor Structure

The cursor is an opaque, base64-encoded token containing:

| Field | Description |
|-------|-------------|
| `last_sync_timestamp` | Timestamp of the last synced entity |
| `last_sync_version` | Version number at last sync point |

Encoding: `base64( JSON({ last_sync_timestamp, last_sync_version }) )`

### 8.2 Cursor Scope

- One cursor per entity type per device.
- Stored in local persistence alongside the entity cache.

### 8.3 Cursor Invalidation

The cursor is invalidated (triggering a full resync) when:

| Event | Rationale |
|-------|-----------|
| Full resync requested | Explicit user or system action |
| Schema change | Entity model changed, old cursor incompatible |
| Token expiry | Authentication token expired beyond grace period |
| Server-detected long offline | Server determines client has been disconnected too long |

### 8.4 Cursor Persistence

- Stored in local SQLite/IndexedDB alongside entity cache.
- Updated after each successful pull.
- Cleared on cursor invalidation.

---

## 9. ACKNOWLEDGEMENT

### 9.1 Acknowledgement Flow

1. Server processes mutation and returns per-mutation result.
2. Client receives response.
3. For each result with status `ACKNOWLEDGED`:
   - Client removes the mutation from the pending queue.
   - Client updates the local entity cache with `new_version`.
   - Client clears `local_modified` flag.
4. For each result with status `CONFLICT`:
   - Client marks the mutation as CONFLICT in the queue.
   - Client logs conflict details to `mobile_conflict_log`.
   - Client does NOT remove the mutation from the queue.

### 9.2 Acknowledgement Granularity

- Acknowledgement is per-mutation, not per-batch.
- A batch of 10 mutations may yield 8 ACKNOWLEDGED, 1 CONFLICT, 1 FAILED.

---

## 10. RETRY

### 10.1 Retry Policy

| Parameter | Value |
|-----------|-------|
| Initial backoff | 1 second |
| Multiplier | 2x |
| Maximum backoff | 16 seconds |
| Jitter | plus or minus 20% |
| Maximum attempts | 5 |

### 10.2 Backoff Calculation

```
backoff = min( initial * (2 ^ attempt), max_backoff )
actual_backoff = backoff * random(0.8, 1.2)
```

### 10.3 Retryable Errors

| Error | Retryable | Rationale |
|-------|-----------|-----------|
| Network timeout | Yes | Transient |
| Connection refused | Yes | Transient |
| HTTP 500 | Yes | Server error, may recover |
| HTTP 502 | Yes | Server error, may recover |
| HTTP 503 | Yes | Server error, may recover |
| HTTP 408 | Yes | Request timeout, may recover |

### 10.4 Non-Retryable Errors

| Error | Retryable | Rationale |
|-------|-----------|-----------|
| HTTP 401 | No | Authentication failure, requires re-auth |
| HTTP 403 | No | Authorization failure, requires re-auth or policy change |
| HTTP 404 | No | Entity not found, may require resync |
| HTTP 412 | No | Precondition failed (conflict) |

### 10.5 Retry Scheduling

- Retry timer starts after the failed attempt.
- Timer is based on wall clock, not elapsed time.
- If the app is killed during retry wait, the timer resumes on next launch.
- Retry only occurs when the device has network connectivity.

---

## 11. ORDERING

### 11.1 Ordering Guarantee

- Mutations are ordered FIFO per entity type.
- Each mutation within an entity type carries a monotonically increasing `sequence_number`.
- The server processes mutations in the order received within an entity type.

### 11.2 Sequence Gap Detection

- If the server detects a gap in the sequence numbers for a given entity type, it rejects the mutation.
- Rejection reason: `SEQUENCE_GAP_DETECTED`.
- Client must retry the gap-filling mutation before proceeding.

### 11.3 Cross-Entity-Type Ordering

- No ordering guarantee across different entity types.
- Entity types are processed independently.

---

## 12. IDEMPOTENCY

### 12.1 Server-Side Idempotency

| Aspect | Value |
|--------|-------|
| Key | `idempotency_key` from mutation envelope |
| Fingerprint | SHA-256 hash of the full mutation envelope |
| Retention window | 24 hours |
| Storage | Existing `IdempotencyService` framework |

### 12.2 Duplicate Detection

1. Server receives mutation.
2. Server computes SHA-256 fingerprint of the mutation envelope.
3. Server checks idempotency store for existing fingerprint.
4. If found within retention window: return cached result.
5. If not found: process mutation, store result with fingerprint.

### 12.3 Duplicate Mutation Handling

- Identical `idempotency_key` returns the same result as the original submission.
- Status, `new_version`, and any conflict details are identical to the original response.
- No re-processing occurs on the server.

---

## 13. PARTIAL FAILURE

### 13.1 Batch Processing

- A push batch may contain multiple mutations.
- Each mutation is processed independently.
- The server returns a result for every mutation.

### 13.2 Mixed Results

A single batch push response may contain:

| Combination | Example |
|-------------|---------|
| All ACKNOWLEDGED | 5 of 5 mutations applied |
| Mixed ACKNOWLEDGED + CONFLICT | 3 applied, 2 conflicted |
| Mixed ACKNOWLEDGED + FAILED | 4 applied, 1 failed |
| Mixed all states | Some applied, some conflicted, some failed |

### 13.3 Client Handling

- Client processes each result independently.
- ACKNOWLEDGED mutations are removed from queue.
- CONFLICT mutations are logged and flagged.
- FAILED mutations are marked for manual intervention.

---

## 14. TIMEOUT

### 14.1 Client-Side Timeout

| Parameter | Value |
|-----------|-------|
| Request timeout | 30 seconds |
| Applies to | Both pull and push operations |

### 14.2 Timeout Behavior

- If the server does not respond within 30 seconds, the client considers the request failed.
- Timeout is classified as a retryable failure.
- Client applies exponential backoff and retries.

### 14.3 Server-Side Timeout

- No change to existing server timeout configuration.
- Server timeout is handled independently of client timeout.

---

## 15. CONFLICT

### 15.1 Conflict Detection

A conflict is detected when:

```
server.entity.version != client.mutation.base_version
```

This means another device or user modified the entity after the client read it locally.

### 15.2 Conflict Response

```json
{
  "status": "CONFLICT",
  "server_version": 8,
  "client_version": 5,
  "conflict_id": "uuid",
  "server_payload": { ... },
  "client_payload": { ... }
}
```

| Field | Description |
|-------|-------------|
| `status` | Always "CONFLICT" |
| `server_version` | Current server version of the entity |
| `client_version` | The `base_version` the client submitted |
| `conflict_id` | Unique identifier for this conflict instance |
| `server_payload` | Current server entity state |
| `client_payload` | The mutation payload the client attempted to apply |

### 15.3 Conflict Resolution Policy

The client must:

1. Fetch the server version (`server_payload`).
2. Compare with the client's attempted changes (`client_payload`).
3. Apply resolution per the merge policy (Section 16).

---

## 16. CONFLICT ISOLATION

### 16.1 Per-Mutation Isolation

- Conflicts are detected and reported per-mutation.
- A conflict on one mutation does not block other mutations in the same batch.
- Each mutation in a batch is resolved independently.

### 16.2 Conflict Logging

- Every conflict is logged to the `mobile_conflict_log` table.
- Log entry includes: `conflict_id`, `entity_type`, `entity_id`, `client_version`, `server_version`, `timestamp`, `resolution`.

### 16.3 Conflict State

- Conflicted mutations remain in the pending queue with status `CONFLICT`.
- They are not automatically retried.
- They require explicit resolution (auto-merge or manual).

---

## 17. MERGE

### 17.1 Auto-Merge

- Applicable when client and server modified different fields of the same entity.
- Server applies client changes to non-conflicting fields.
- Conflicting fields use server value.
- No user intervention required.

### 17.2 Manual Resolution

- Required when client and server modified the same field.
- Client presents both versions to the user.
- User selects: keep server, keep client, or merge manually.
- Resolution is applied and pushed as a new mutation.

### 17.3 Server-Authoritative Fields

Some fields are always server-authoritative:

| Field Category | Examples |
|----------------|----------|
| State transitions | Status changes, workflow progression |
| Financial data | Amounts, balances, billing |
| System-generated | Created-at, updated-at, audit fields |

Client attempts to modify server-authoritative fields are rejected with `SERVER_AUTHORITY_CONFLICT`.

### 17.4 Merge Ordering

1. Non-conflicting fields: auto-merged.
2. Conflicting fields: queued for manual resolution.
3. Server-authoritative fields: always use server value.

---

## 18. DELETE CONFLICT

### 18.1 Conflict Matrix

| Client Operation | Server State | Result |
|------------------|--------------|--------|
| UPDATE | Server DELETE | CONFLICT (server wins) |
| DELETE | Server UPDATE | CONFLICT (server wins) |
| DELETE | Server DELETE | APPLIED (idempotent) |

### 18.2 Resolution

- In all UPDATE vs DELETE conflicts, the server wins.
- Client is notified of the conflict and must reconcile.
- If both sides deleted, the operation is idempotent and acknowledged.

---

## 19. FULL RESYNC

### 19.1 Triggers

| Trigger | Source |
|---------|--------|
| Cursor invalid | Client detects corrupt or missing cursor |
| Token expiry | Authentication token expired beyond grace period |
| Explicit request | User initiates manual sync |
| Server-detected long offline | Server determines client has been disconnected beyond threshold |

### 19.2 Resync Procedure

1. Client clears local entity cache.
2. Client clears local cursor.
3. Client clears pending mutation queue (conflicts logged first).
4. Client sends pull request with `last_sync_cursor = null`.
5. Client receives all entities from server.
6. Client rebuilds local state from server data.
7. Client stores new cursor for future incremental pulls.

### 19.3 Resync Guarantees

- After resync, local state is identical to server state.
- No local-only data survives a full resync.
- Conflict log is preserved for audit.

---

## 20. RECOVERY

### 20.1 App Restart

- On app restart, the client reloads the pending mutation queue from local persistence.
- Queue processing resumes from the first QUEUED or READY mutation.
- No mutations are lost.

### 20.2 Network Recovery

- When network connectivity is restored, the client flushes the pending queue.
- Mutations are sent in FIFO order per entity type.
- Retries are handled per the retry policy (Section 10).

### 20.3 Crash Recovery

- The pending mutation queue is persisted in local storage (SQLite/IndexedDB).
- On crash recovery, the client reads the queue from persistent storage.
- In-progress mutations (SENT status) are reset to READY for retransmission.

---

## 21. AUTHENTICATION EXPIRY

### 21.1 Token Configuration

| Token | Duration | Source |
|-------|----------|--------|
| Refresh token | 7 days | Existing |
| Access token | 15 minutes | Existing |

### 21.2 Offline Behavior

- While offline, the client uses the cached access token.
- If the token expires while offline, the client continues queueing mutations.
- On next network contact, if the token is expired, the client prompts re-authentication.

### 21.3 Post-Reauthentication

1. User completes re-authentication.
2. Client receives new access token and refresh token.
3. Client performs a full resync to ensure consistency.
4. Pending mutations are retried with new credentials.

---

## 22. ENTITY TYPE COVERAGE

All sync operations apply to the following entity types:

| Entity Type | CREATE | UPDATE | DELETE | Pull | Push |
|-------------|--------|--------|--------|------|------|
| CONTACT | Yes | Yes | Yes | Yes | Yes |
| ACCOUNT | Yes | Yes | Yes | Yes | Yes |
| LEAD | Yes | Yes | Yes | Yes | Yes |
| OPPORTUNITY | Yes | Yes | Yes | Yes | Yes |
| TASK | Yes | Yes | Yes | Yes | Yes |
| ACTIVITY | Yes | Yes | Yes | Yes | Yes |
| NOTE | Yes | Yes | Yes | Yes | Yes |

---

## 23. SYNC PROTOCOL SUMMARY

### 23.1 Normal Operation

```
Client                          Server
  |                               |
  |--- PULL (cursor) ----------->|
  |<-- entities, new_cursor ------|
  |                               |
  |--- PUSH (mutations) -------->|
  |<-- results per mutation ------|
  |                               |
  | [ACKNOWLEDGED mutations removed from queue]
  | [CONFLICT mutations logged]
```

### 23.2 Offline Operation

```
Client                          Server
  |                               |
  | [Queue mutations locally]     |
  | [No network]                  |
  |                               |
  | [Network restored]            |
  |--- PULL (cursor) ----------->|
  |<-- entities, new_cursor ------|
  |--- PUSH (queued mutations) ->|
  |<-- results per mutation ------|
  |                               |
  | [Process results]             |
```

### 23.3 Conflict Resolution

```
Client                          Server
  |                               |
  |--- PUSH (mutation v5) ------>|
  |<-- CONFLICT (server v8) ------|
  |                               |
  | [Client fetches server v8]    |
  | [Client compares fields]      |
  | [Auto-merge or manual]        |
  |--- PUSH (merged mutation) -->|
  |<-- ACKNOWLEDGED (v9) ---------|
```

---

## 24. INvariants

These invariants must hold at all times:

1. Every mutation has a unique `idempotency_key`.
2. The pending queue is always persisted to local storage.
3. Acknowledged mutations are always removed from the queue.
4. Conflicted mutations are always logged to `mobile_conflict_log`.
5. Retries always respect exponential backoff with jitter.
6. Full resync always clears local cache before pulling.
7. Cursor is always updated after a successful pull.
8. Server version is always authoritative for state transitions and financial data.
9. Delete conflicts always favor the server.
10. Partial failure in a batch never blocks other mutations.

---

*This document is the definitive sync contract. All implementation must conform to this specification.*
