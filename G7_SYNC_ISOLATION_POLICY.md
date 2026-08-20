# G7 SYNC ISOLATION POLICY

**Phase 7 Reconciliation — Sync Isolation Policy**
**Status:** DEFINITIVE
**Authority:** This document defines all isolation boundaries enforced on sync operations.

---

## 1. ISOLATION MODEL OVERVIEW

Sync isolation ensures that no mutation, conflict, or failure in one isolation boundary can affect data or operations in another. Five isolation dimensions are enforced:

| Dimension | Scope | Enforcement |
|-----------|-------|-------------|
| Tenant Isolation | Data separation between tenants | RLS (Row-Level Security) |
| Device Isolation | Mutation provenance tracking | device_id required on all mutations |
| User Isolation | User-to-device binding | user_id bound to device context |
| Conflict Isolation | Per-mutation conflict handling | Conflicts are per-mutation, not per-batch |
| Failure Isolation | Independent mutation failure handling | One failure does not affect others |
| Network Isolation | Offline queue independence | Queue persists independent of network state |

---

## 2. TENANT ISOLATION

### 2.1 Principle

Every sync operation is scoped to a single tenant. No sync operation can read, write, modify, or delete data belonging to another tenant.

### 2.2 Enforcement Mechanism

- **Row-Level Security (RLS)** is enforced at the database layer for all sync queries.
- Every sync request includes the `tenant_id` bound from the authenticated session.
- Server-side queries include: `WHERE tenant_id = :tenant_id` (enforced by RLS policy, not application code).
- RLS policies are defined at the database level and cannot be bypassed by application logic.

### 2.3 Tenant Binding

| Field | Source | Description |
|-------|--------|-------------|
| `tenant_id` | Auth session | Extracted from JWT or session token |
| Applied to | All entity queries | Every pull and push operation |

### 2.4 Tenant Isolation Guarantees

1. A pull request for tenant A never returns entities belonging to tenant B.
2. A push mutation for tenant A never writes to tenant B's data.
3. RLS prevents cross-tenant access even if application code has bugs.
4. No mutation envelope can specify a different `tenant_id` than the authenticated session.
5. Cursor tokens are scoped to tenant — a cursor from tenant A cannot be used for tenant B.

### 2.5 Tenant Isolation Violations

If a sync operation attempts to access data outside the authenticated tenant's scope:

- The database rejects the query via RLS.
- The server returns HTTP 403 Forbidden.
- The mutation is marked PERMANENT_FAILURE in the client queue.
- An audit log entry is created.

---

## 3. DEVICE ISOLATION

### 3.1 Principle

Every mutation is bound to the device that created it. The device identity is immutable per mutation and cannot be forged or overridden by the client after submission.

### 3.2 Device Identity

| Field | Type | Description |
|-------|------|-------------|
| `device_id` | UUID | Unique identifier for the physical or virtual device |
| Generated | Client-side | On first app launch, persisted to device storage |
| Persistence | Local storage | Survives app restarts, not survives app uninstall |

### 3.3 Device Binding

- Every mutation envelope requires a `device_id` field.
- The server validates that the `device_id` is registered and associated with the authenticated user.
- Unregistered devices are rejected with HTTP 403.

### 3.4 Device Isolation Guarantees

1. Mutations from device A cannot be attributed to device B.
2. Each device maintains its own cursor and sync state.
3. A full resync on device A does not affect device B's cursor or queue.
4. Conflict logs record the originating `device_id` for every conflict.

### 3.5 Device Registration

| Step | Action |
|------|--------|
| 1 | Client generates `device_id` (UUID v4) on first launch |
| 2 | Client stores `device_id` in secure local storage |
| 3 | Client sends device registration to server on first sync |
| 4 | Server associates `device_id` with authenticated `user_id` |
| 5 | Server rejects sync from unregistered devices |

---

## 4. USER ISOLATION

### 4.1 Principle

Each device is bound to exactly one authenticated user. A user may own multiple devices, but each device operates under a single user identity at any given time.

### 4.2 User-Device Binding

| Relationship | Cardinality | Description |
|--------------|-------------|-------------|
| User to Device | 1:N | One user can own multiple devices |
| Device to User | 1:1 | Each device is bound to one user at a time |

### 4.3 User Binding Enforcement

- The `user_id` is extracted from the authentication token (JWT).
- The server validates that the `device_id` in the mutation envelope is registered to the authenticated `user_id`.
- Mismatches are rejected with HTTP 403.

### 4.4 User Switching

When a user switches accounts on a device:

1. The device logs out the current user.
2. The pending mutation queue is flushed or discarded (per policy).
3. The device re-registers with the new user.
4. A full resync is performed for the new user's data.
5. The old user's data is cleared from local cache.

### 4.5 User Isolation Guarantees

1. Device A, logged in as User X, cannot access User Y's data.
2. Mutations from Device A are always attributed to User X.
3. A user cannot see mutations from another user's devices in their conflict log.
4. User-scoped cursors are invalidated on user switch.

---

## 5. CONFLICT ISOLATION

### 5.1 Principle

Conflicts are per-mutation, not per-batch. A conflict on one mutation does not block, delay, or affect other mutations in the same batch or queue.

### 5.2 Conflict Scope

| Scope | Behavior |
|-------|----------|
| Per-mutation | Each mutation is independently conflict-checked |
| Per-batch | Batch processing continues despite individual conflicts |
| Per-entity-type | Conflicts in entity type A do not affect entity type B |

### 5.3 Conflict Isolation Guarantees

1. A CONFLICT on mutation M1 does not prevent mutation M2 from being processed.
2. A CONFLICT on entity type CONTACT does not affect entity type ACCOUNT.
3. A CONFLICT on device A does not affect device B.
4. Conflicts are logged independently in `mobile_conflict_log`.
5. Each conflict has a unique `conflict_id` for tracking and resolution.

### 5.4 Conflict Logging Isolation

The `mobile_conflict_log` table records each conflict with:

| Field | Description |
|-------|-------------|
| `conflict_id` | Unique identifier for this conflict |
| `tenant_id` | Tenant scope |
| `user_id` | User who owns the mutation |
| `device_id` | Device that originated the mutation |
| `entity_type` | Type of entity in conflict |
| `entity_id` | ID of the entity in conflict |
| `client_version` | Version the client submitted |
| `server_version` | Version the server held |
| `client_payload` | What the client attempted to write |
| `server_payload` | What the server currently holds |
| `timestamp` | When the conflict was detected |
| `resolution` | Auto-merge, manual, or server-wins |

### 5.5 Conflict Resolution Isolation

- Resolution of conflict C1 does not affect conflict C2.
- Each conflict is resolved independently.
- Auto-merged conflicts do not require manual intervention.
- Manual conflicts are presented to the user one at a time.

---

## 6. FAILURE ISOLATION

### 6.1 Principle

One mutation failure does not affect the processing, retry, or status of any other mutation. Each mutation operates in its own failure domain.

### 6.2 Failure Scope

| Scope | Behavior |
|-------|----------|
| Per-mutation | Each mutation has independent retry state |
| Per-batch | Batch continues processing despite individual failures |
| Per-entity-type | Failures in entity type A do not affect entity type B |

### 6.3 Failure Isolation Guarantees

1. A RETRYABLE_FAILURE on mutation M1 does not delay mutation M2.
2. A PERMANENT_FAILURE on mutation M1 does not block mutation M2.
3. Max retry count is tracked per-mutation, not per-batch or per-entity-type.
4. Backoff timers are per-mutation, not global.
5. A network failure during push does not affect the pull cursor.

### 6.4 Failure State Isolation

Each mutation's failure state is independent:

| Mutation | Status | Retry Count | Effect on Others |
|----------|--------|-------------|------------------|
| M1 | RETRYABLE_FAILURE | 3 | None |
| M2 | ACKNOWLEDGED | 0 | None |
| M3 | PERMANENT_FAILURE | 1 | None |
| M4 | CONFLICT | 0 | None |
| M5 | QUEUED | 0 | None |

### 6.5 Failure Recovery Isolation

- Retrying M1 does not affect M2, M3, M4, or M5.
- Resolving M4's conflict does not affect M1, M2, M3, or M5.
- Manual intervention on M3 does not affect M1, M2, M4, or M5.

---

## 7. NETWORK ISOLATION

### 7.1 Principle

The offline mutation queue operates independently of network state. Mutations are queued regardless of connectivity, and network recovery flushes the queue without affecting other operations.

### 7.2 Network State Independence

| Network State | Queue Behavior | Pull Behavior |
|---------------|----------------|---------------|
| Online | Queue flushes normally | Pulls proceed normally |
| Offline | Mutations queue locally | Pulls deferred until online |
| Intermittent | Queue flushes when connected | Pulls resume when connected |
| Slow | Queue flushes with timeout handling | Pulls use pagination |

### 7.3 Network Isolation Guarantees

1. Going offline does not corrupt or lose queued mutations.
2. Going online does not force immediate queue flush (respects backoff).
3. Network failures on push do not affect pull cursor state.
4. Network failures on pull do not affect push queue state.
5. Timeout on one request does not cancel other in-flight requests.

### 7.4 Offline Queue Persistence

- The queue is persisted in SQLite (mobile) or IndexedDB (web).
- Queue state survives: app kill, device reboot, network loss, battery drain.
- Queue state does NOT survive: app uninstall, explicit user clear, schema migration requiring cache wipe.

### 7.5 Network Recovery Procedure

1. Network connectivity is detected.
2. Client verifies authentication token validity.
3. If token is valid: flush pending queue (FIFO per entity type).
4. If token is expired: prompt re-authentication, then full resync.
5. Queue flush proceeds with retry and backoff per mutation.

---

## 8. ISOLATION BOUNDARY DIAGRAM

```
+------------------------------------------------------------------+
|                        TENANT BOUNDARY                            |
|  +--------------------------------------------------------------+|
|  |                    USER BOUNDARY                             ||
|  |  +----------------------------------------------------------+||
|  |  |                  DEVICE BOUNDARY                         |||
|  |  |  +----------------------------------------------------+ |||
|  |  |  |              MUTATION BOUNDARY                     ||||
|  |  |  |                                                    ||||
|  |  |  |  M1 [QUEUED] -> [SENT] -> [ACKNOWLEDGED]          ||||
|  |  |  |  M2 [QUEUED] -> [SENT] -> [CONFLICT]              ||||
|  |  |  |  M3 [QUEUED] -> [SENT] -> [RETRYABLE_FAILURE]     ||||
|  |  |  |  M4 [QUEUED] -> [SENT] -> [PERMANENT_FAILURE]     ||||
|  |  |  |  M5 [QUEUED]                                      ||||
|  |  |  |                                                    ||||
|  |  |  |  Each mutation is independent.                     ||||
|  |  |  |  Failure of M2 does not affect M1, M3, M4, M5.    ||||
|  |  |  +----------------------------------------------------+ |||
|  |  |                                                          |||
|  |  |  CONFLICT ISOLATION:                                     |||
|  |  |  M2's CONFLICT does not block M1, M3, M4, M5.          |||
|  |  |                                                          |||
|  |  |  NETWORK ISOLATION:                                      |||
|  |  |  Queue persists regardless of network state.             |||
|  |  |  Offline queue is independent of online operations.      |||
|  |  +----------------------------------------------------------+||
|  |                                                                ||
|  |  DEVICE ISOLATION:                                             ||
|  |  This device's queue does not affect other devices.           ||
|  |  This device's cursor does not affect other devices.          ||
|  +----------------------------------------------------------------+|
|                                                                      |
|  TENANT ISOLATION:                                                   |
|  RLS ensures no cross-tenant data access.                           |
|  All queries scoped to authenticated tenant.                        |
+----------------------------------------------------------------------+
```

---

## 9. ISOLATION MATRIX

| Isolation Dimension | Affects Pull | Affects Push | Affects Queue | Affects Conflicts | Affects Cursor |
|---------------------|-------------|-------------|---------------|-------------------|----------------|
| Tenant | Yes | Yes | No | Yes | Yes |
| Device | Yes | Yes | Yes | Yes | Yes |
| User | Yes | Yes | Yes | Yes | Yes |
| Conflict | No | Yes | Yes | Yes | No |
| Failure | No | Yes | Yes | No | No |
| Network | Yes | Yes | Yes | No | Yes |

---

## 10. ISOLATION VIOLATION RESPONSES

| Violation Type | Response | Client Action |
|----------------|----------|---------------|
| Cross-tenant access | HTTP 403 | Mark mutation PERMANENT_FAILURE |
| Unregistered device | HTTP 403 | Prompt device registration |
| User-device mismatch | HTTP 403 | Prompt re-authentication |
| Cross-mutation conflict bleed | N/A (should not occur) | Log error, report bug |
| Cross-device queue bleed | N/A (should not occur) | Log error, report bug |
| Network state corruption | Queue integrity check on startup | Full resync if corruption detected |

---

## 11. AUDIT AND COMPLIANCE

### 11.1 Audit Logging

Every isolation-relevant event is logged:

| Event | Log Entry |
|-------|-----------|
| Cross-tenant access attempt | Tenant ID, user ID, timestamp, rejection reason |
| Unregistered device attempt | Device ID, user ID, timestamp |
| User-device mismatch | Device ID, user ID, timestamp |
| Conflict detected | Conflict ID, entity type, entity ID, versions |
| Failure recorded | Mutation ID, error type, retry count |
| Full resync triggered | Device ID, user ID, trigger reason, timestamp |

### 11.2 Compliance Requirements

- RLS policies must be verified quarterly.
- Device registration must be audited monthly.
- Conflict logs must be retained for 12 months.
- Isolation violations must be reported within 24 hours.

---

## 12. TESTING ISOLATION

### 12.1 Isolation Test Cases

| Test | Isolation Dimension | Expected Result |
|------|---------------------|-----------------|
| Tenant A pull does not return Tenant B data | Tenant | Pass (RLS enforced) |
| Tenant A push does not write Tenant B data | Tenant | Pass (RLS enforced) |
| Device A mutations not attributed to Device B | Device | Pass (device_id validation) |
| User A device cannot access User B data | User | Pass (auth binding) |
| Conflict on M1 does not block M2 processing | Conflict | Pass (independent processing) |
| Failure on M1 does not affect M2 retry | Failure | Pass (independent retry state) |
| Offline queue survives network loss | Network | Pass (local persistence) |
| Queue flush does not affect pull cursor | Network | Pass (independent state) |
| Full resync on Device A does not affect Device B | Device | Pass (independent cursors) |
| User switch clears device cache | User | Pass (cache isolation) |

### 12.2 Isolation Test Execution

- Isolation tests run as part of the sync integration test suite.
- Tests are executed on every deployment.
- Isolation regressions are treated as P0 defects.

---

## 13. ISOLATION CONFIGURATION

### 13.1 RLS Configuration

```sql
-- Example RLS policy for contacts table
CREATE POLICY tenant_isolation ON contacts
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

-- Example RLS policy for mutations table
CREATE POLICY tenant_isolation ON mutations
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

### 13.2 Device Registration Configuration

| Setting | Value |
|---------|-------|
| Max devices per user | 5 |
| Device ID format | UUID v4 |
| Device registration required | Yes |
| Unregistered device action | Reject with HTTP 403 |

### 13.3 Queue Configuration

| Setting | Value |
|---------|-------|
| Queue persistence | SQLite (mobile), IndexedDB (web) |
| Queue ordering | FIFO per entity type |
| Max queue size | No hard limit (storage permitting) |
| Queue integrity check | On app startup |

---

*This document defines the complete isolation policy for all sync operations. All implementation must conform to these isolation boundaries.*
