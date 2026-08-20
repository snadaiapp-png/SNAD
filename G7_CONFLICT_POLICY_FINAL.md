# G7 - Conflict Resolution Policy Final

**Document Status:** Final  
**Version:** 1.0  
**Date:** 2026-08-11  

---

## 1. Executive Summary

This document defines the complete conflict resolution policy for the CRM mobile sync system. The policy establishes a foundation of optimistic concurrency with server-side rejection, extended with a mobile-specific conflict detection layer to handle the offline-to-online transition gap.

**Core Principle:** The server is the authoritative source of truth. All conflicts are ultimately resolved in favor of data integrity over convenience.

---

## 2. Server-Side Policy (Established Foundation)

### 2.1 Optimistic Concurrency with Rejection

| Aspect | Specification |
|--------|---------------|
| **Mechanism** | Every mutation requires a version identifier (version column or If-Match header) |
| **Stale Detection** | Client version does not match server version |
| **Response** | HTTP 412 CRM_CONCURRENCY_CONFLICT |
| **Client Action** | Re-fetch current state, reconcile changes, retry mutation |
| **Server Merge** | None — no automatic server-side merge |
| **Last-Writer-Wins** | Never used as default |
| **Client-Wins** | Never used as default |

### 2.2 Audit Trail

- All mutations are audit-logged via `PlatformAuditWriter`
- Before/after JSON snapshots captured
- Timestamp, user_id, tenant_id, entity_type, entity_id recorded
- Idempotency prevents duplicate audit entries

### 2.3 Idempotency

- Idempotency key required on all mutations
- SHA-256 fingerprint of mutation parameters
- 24-hour retention window
- Same key returns cached result, no re-execution

---

## 3. Mobile Extension (Proposed)

### 3.1 Design Rationale

The server rejection model assumes immediate re-fetch capability. Mobile clients experience:

- **Offline gaps** — no network connectivity for hours or days
- **Deferred sync** — mutations queued locally, pushed when online
- **Partial connectivity** — intermittent connections, flaky networks
- **Competing clients** — same user on multiple devices

The mobile extension adds a conflict detection layer without altering the server rejection foundation.

### 3.2 Conflict Detection Layer

```
┌─────────────────────────────────────────────────────────┐
│                    MOBILE CLIENT                        │
│                                                         │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────┐ │
│  │  Local DB     │───▶│ Sync Manager │───▶│ Network  │ │
│  │  (SQLCipher)  │    │              │    │ Layer    │ │
│  └──────────────┘    │  - Version   │    └──────────┘ │
│                      │  - Sequence  │                   │
│                      │  - Timestamp │                   │
│                      └──────────────┘                   │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                    SERVER                                │
│                                                         │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────┐ │
│  │  RLS Filter   │───▶│ Concurrency  │───▶│ Conflict │ │
│  │              │    │ Check        │    │ Resolver │ │
│  └──────────────┘    └──────────────┘    └──────────┘ │
└─────────────────────────────────────────────────────────┘
```

### 3.3 Offline-to-Online Transition

| Phase | Action |
|-------|--------|
| **Offline** | Mutations stored locally with version, timestamp, sequence number |
| **Reconnect** | Client initiates sync with current server state |
| **Conflict Detection** | Server compares client version against current server version |
| **Resolution** | Per conflict class rules (see Section 4) |
| **Retry** | If rejection, client re-fetches and retries |

---

## 4. Conflict Classes (12 Identified)

### C1: Same Record / Same Field

| Aspect | Specification |
|--------|---------------|
| **Scenario** | Two clients modify the same field on the same record |
| **Detection** | Version mismatch on same field path |
| **Resolution** | REJECT — server returns 412 with both versions |
| **Client Action** | Present both values to user for manual resolution |
| **UI Pattern** | Side-by-side comparison, user picks winner |

**Example:**
```
Client A: Contact.phone = "555-0100" (version: 1)
Client B: Contact.phone = "555-0200" (version: 1)
Server: Contact.phone = "555-0100" (version: 2, from Client A)
Client B receives: CONFLICT — user must resolve
```

### C2: Same Record / Different Fields

| Aspect | Specification |
|--------|---------------|
| **Scenario** | Two clients modify different fields on the same record |
| **Detection** | Version mismatch but field paths are disjoint |
| **Resolution** | AUTO_MERGE — server merges non-conflicting fields |
| **Client Action** | Accept merged result, no user intervention |
| **Audit** | Merge recorded in audit log with both source versions |

**Example:**
```
Client A: Contact.email = "new@email.com" (version: 1)
Client B: Contact.phone = "555-0200" (version: 1)
Server: Merges both, Contact.version = 3
```

### C3: Delete vs Update

| Aspect | Specification |
|--------|---------------|
| **Scenario** | Client attempts to delete a record that has pending updates |
| **Detection** | Delete mutation arrives, but server version > client's last-known version |
| **Resolution** | REJECT — delete is blocked if update is pending |
| **Client Action** | Re-fetch record, check for updates, re-evaluate delete |
| **User Notification** | "Record was modified since you last viewed it. Delete cancelled." |

### C4: Update vs Delete

| Aspect | Specification |
|--------|---------------|
| **Scenario** | Client attempts to update a record that server has marked for deletion |
| **Detection** | Update mutation arrives, but record is deleted on server |
| **Resolution** | REJECT — same as C3 |
| **Client Action** | Re-fetch, confirm deletion, remove from local DB |

### C5: Create vs Create

| Aspect | Specification |
|--------|---------------|
| **Scenario** | Two clients create the same record (duplicate creation) |
| **Detection** | Idempotency key match |
| **Resolution** | IDEMPOTENCY_DEDUP — second creation returns first result |
| **Client Action** | Accept existing record ID |
| **Server Action** | Return HTTP 200 with existing record, not HTTP 201 |

### C6: Parent vs Child

| Aspect | Specification |
|--------|---------------|
| **Scenario** | Parent record modified while child record is being created/updated |
| **Detection** | Version check on parent entity |
| **Resolution** | REJECT if parent version stale — client must re-fetch parent |
| **Cascade** | Child mutation blocked until parent is current |
| **UI Pattern** | "Parent record has changed. Please refresh before continuing." |

### C7: Reference vs Transactional

| Aspect | Specification |
|--------|---------------|
| **Scenario** | Reference data (picklists, configurations) conflicts with transactional data |
| **Detection** | Reference data always pulled, never pushed by mobile |
| **Resolution** | PULL_ONLY — no conflict possible |
| **Client Action** | Reference data sync is read-only |

**Reference Data Types:**
- Picklist values
- Custom field definitions
- Role definitions
- Territory assignments
- Workflow configurations

### C8: Status Transition

| Aspect | Specification |
|--------|---------------|
| **Scenario** | Entity status changed to invalid transition |
| **Detection** | State machine validation on server |
| **Resolution** | REJECT — invalid state transition |
| **Valid Transitions** | Defined per entity type (see Section 6) |
| **Client Action** | Re-fetch current status, present valid options |

**State Machine Example (Lead):**
```
NEW → CONTACTED → QUALIFIED → CONVERTED
                 ↓
              DISQUALIFIED (from NEW, CONTACTED, or QUALIFIED)
```

### C9: Permission / Ownership

| Aspect | Specification |
|--------|---------------|
| **Scenario** | Client attempts mutation on entity they no longer own/have access to |
| **Detection** | RBAC + ownership check on server |
| **Resolution** | REJECT — authorization failure |
| **Client Action** | Re-fetch entity, verify permissions |
| **Audit** | Authorization failure logged |

### C10: Duplicate Mutation

| Aspect | Specification |
|--------|---------------|
| **Scenario** | Same mutation sent multiple times (retry, network duplicate) |
| **Detection** | Idempotency key match |
| **Resolution** | IDEMPOTENCY_DEDUP — return cached result |
| **Client Action** | Accept cached result |
| **Server Action** | No re-execution, return previous result |

### C11: Reordered Mutations

| Aspect | Specification |
|--------|---------------|
| **Scenario** | Mutations arrive out of order due to network conditions |
| **Detection** | Sequence numbering on client mutations |
| **Resolution** | Server applies in sequence order, rejects out-of-sequence |
| **Client Action** | Re-order locally, retry in correct sequence |
| **Sequence Format** | `{entity_type}:{entity_id}:{sequence_number}` |

### C12: Long-Offline Stale

| Aspect | Specification |
|--------|---------------|
| **Scenario** | Client was offline for extended period (days/weeks) |
| **Detection** | Client's last sync timestamp > server's stale threshold |
| **Resolution** | FULL_RE_SYNC — client discards local state, re-syncs everything |
| **Threshold** | Configurable, default: 7 days |
| **Client Action** | Show progress bar, re-download all data |

---

## 5. Resolution Strategies

### 5.1 Strategy Definitions

| Strategy | Code | Description | When Used |
|----------|------|-------------|-----------|
| **AUTO_MERGE** | 1 | Non-conflicting fields merged automatically | C2 conflicts, field-level divergence |
| **SERVER_WINS** | 2 | Server version overwrites client | Financial data, critical fields |
| **CLIENT_WINS** | 3 | Client version overwrites server | **NEVER as default** — requires explicit opt-in |
| **USER_RESOLUTION** | 4 | User must manually resolve | C1 conflicts, same-field divergence |
| **REJECT** | 5 | Mutation rejected, client re-fetches | C3, C4, C6, C8, C9 conflicts |

### 5.2 Strategy Selection Matrix

```
┌─────────────────────────────────────────────────────────┐
│                  CONFLICT DETECTED                      │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  Same field changed?   │
              └────────────────────────┘
                    │            │
                   YES          NO
                    │            │
                    ▼            ▼
            ┌──────────┐  ┌──────────┐
            │ USER_RES │  │AUTO_MERGE│
            └──────────┘  └──────────┘
                    │
                    ▼
              ┌────────────────────────┐
              │  Is field financial?   │
              └────────────────────────┘
                    │            │
                   YES          NO
                    │            │
                    ▼            ▼
            ┌──────────┐  ┌──────────┐
            │SERVER_WIN│  │USER_RES  │
            └──────────┘  └──────────┘
```

### 5.3 Strategy Application Rules

1. **AUTO_MERGE** is the preferred strategy when fields are disjoint
2. **SERVER_WINS** is mandatory for financial/critical fields (see Section 6)
3. **CLIENT_WINS** requires explicit user confirmation and audit trail
4. **USER_RESOLUTION** presents both versions for manual selection
5. **REJECT** is the fallback when resolution cannot be determined automatically

---

## 6. Per-Entity Policy

### 6.1 Account

| Field Category | Strategy | Rationale |
|----------------|----------|-----------|
| Name | USER_RESOLUTION | Critical identity field |
| Phone | AUTO_MERGE | Non-conflicting contact info |
| Email | AUTO_MERGE | Non-conflicting contact info |
| Address | AUTO_MERGE | Non-conflicting contact info |
| Industry | USER_RESOLUTION | Classification field |
| Revenue | SERVER_WINS | Financial data |
| Employees | AUTO_MERGE | Non-conflicting |

**Resolution Flow:**
- Auto-merge non-conflicting fields (phone, email, address, employees)
- User resolution for conflicting identity fields (name, industry)
- Server wins for financial fields (revenue)

### 6.2 Contact

| Field Category | Strategy | Rationale |
|----------------|----------|-----------|
| First Name | USER_RESOLUTION | Identity field |
| Last Name | USER_RESOLUTION | Identity field |
| Phone | AUTO_MERGE | Non-conflicting contact info |
| Email | AUTO_MERGE | Non-conflicting contact info |
| Title | AUTO_MERGE | Non-conflicting |
| Department | AUTO_MERGE | Non-conflicting |

**Resolution Flow:**
- Auto-merge non-conflicting fields (phone, email, title, department)
- User resolution for name conflicts

### 6.3 Lead

| Field Category | Strategy | Rationale |
|----------------|----------|-----------|
| Name | USER_RESOLUTION | Identity field |
| Company | USER_RESOLUTION | Classification |
| Phone | AUTO_MERGE | Non-conflicting |
| Email | AUTO_MERGE | Non-conflicting |
| Status | STATE_MACHINE | Transition validation |
| Score | SERVER_WINS | Calculated field |

**Resolution Flow:**
- State machine validation for status transitions
- Server wins for calculated score
- User resolution for identity fields
- Auto-merge contact info

### 6.4 Opportunity

| Field Category | Strategy | Rationale |
|----------------|----------|-----------|
| Name | USER_RESOLUTION | Identity field |
| Amount | SERVER_WINS | Financial — critical |
| Close Date | SERVER_WINS | Financial — critical |
| Stage | STATE_MACHINE | Transition validation |
| Probability | SERVER_WINS | Financial — critical |
| Description | AUTO_MERGE | Non-conflicting |

**Resolution Flow:**
- Server wins for ALL financial fields (amount, close date, probability)
- State machine for stage transitions
- User resolution for name
- Auto-merge description

### 6.5 Task

| Field Category | Strategy | Rationale |
|----------------|----------|-----------|
| Subject | AUTO_MERGE | Non-conflicting |
| Due Date | USER_RESOLUTION | Time-sensitive |
| Status | STATE_MACHINE | Transition validation |
| Priority | USER_RESOLUTION | Classification |
| Description | AUTO_MERGE | Non-conflicting |

**Resolution Flow:**
- State machine for status (Not Started → In Progress → Completed)
- User resolution for due date and priority
- Auto-merge subject and description

### 6.6 Activity

| Field Category | Strategy | Rationale |
|----------------|----------|-----------|
| All Fields | SERVER_WINS | Push-only, no conflict possible |

**Resolution Flow:**
- Activities are push-only from mobile
- No conflict possible — server is always authoritative
- Client receives confirmation only

### 6.7 Note

| Field Category | Strategy | Rationale |
|----------------|----------|-----------|
| Content | SERVER_WINS | Push-only |
| Created By | SERVER_WINS | Immutable |
| Created At | SERVER_WINS | Immutable |

**Resolution Flow:**
- Notes are push-only from mobile
- No conflict possible
- Server timestamps and ownership are authoritative

### 6.8 Pipeline

| Field Category | Strategy | Rationale |
|----------------|----------|-----------|
| All Fields | PULL_ONLY | No offline writes, no conflict |

**Resolution Flow:**
- Pipeline is pull-only from server
- Mobile cannot modify pipeline configuration
- No conflict possible

### 6.9 Tags

| Field Category | Strategy | Rationale |
|----------------|----------|-----------|
| Tag Assignment | USER_RESOLUTION | Affects multiple entities |

**Resolution Flow:**
- Tags affect multiple entities simultaneously
- Conflict on tag assignment requires user resolution
- Auto-merge if tags are additive (no conflict)

### 6.10 Custom Fields

| Field Type | Strategy | Rationale |
|------------|----------|-----------|
| Text | AUTO_MERGE | Non-conflicting free text |
| Number | USER_RESOLUTION | Numerical conflict |
| Date | USER_RESOLUTION | Time-sensitive |
| Choice | USER_RESOLUTION | Mutually exclusive selection |
| Multi-Choice | AUTO_MERGE | Additive selections |
| Boolean | USER_RESOLUTION | Binary conflict |

**Resolution Flow:**
- Text fields auto-merge (concatenate or overwrite based on length)
- Choice fields require user resolution
- Multi-choice fields auto-merge (union of selections)

---

## 7. Delete Conflict Resolution

### 7.1 Delete Conflict Matrix

| Client Action | Server Action | Result | Strategy |
|---------------|---------------|--------|----------|
| Update | Delete | CONFLICT | Server wins — delete stands |
| Delete | Update | CONFLICT | Server wins — delete blocked |
| Delete | Delete | APPLIED | Idempotent — no conflict |
| Update | Hard Delete | REJECTED | Record no longer exists |

### 7.2 Delete Conflict Details

**Client Update + Server Delete:**
```
Client: Contact (id: 123, version: 5) → Update phone
Server: Contact (id: 123) → Deleted by another user
Result: CONFLICT — Server wins
Client Action: Remove contact from local DB, notify user
```

**Client Delete + Server Update:**
```
Client: Contact (id: 123, version: 5) → Delete
Server: Contact (id: 123, version: 6) → Updated phone
Result: CONFLICT — Server wins, delete blocked
Client Action: Re-fetch contact, show user "Record was updated"
```

**Client Delete + Server Delete:**
```
Client: Contact (id: 123) → Delete
Server: Contact (id: 123) → Already deleted
Result: APPLIED (idempotent)
Client Action: Remove from local DB (already done)
```

**Client Update + Server Hard Delete:**
```
Client: Contact (id: 123, version: 5) → Update phone
Server: Contact (id: 123) → Hard deleted (purged)
Result: REJECTED — 404 Not Found
Client Action: Remove from local DB, show "Record no longer exists"
```

### 7.3 Soft Delete vs Hard Delete

| Type | Behavior | Recovery |
|------|----------|----------|
| **Soft Delete** | Record marked as deleted, data retained | Can be restored within retention period |
| **Hard Delete** | Record purged from database | Cannot be recovered |

**Mobile Policy:**
- Soft deletes are syncable — conflict detection applies
- Hard deletes are terminal — no conflict possible
- Client must handle 404 gracefully

---

## 8. Conflict Resolution UI Patterns

### 8.1 Same-Field Conflict (C1)

```
┌─────────────────────────────────────────────┐
│         CONFLICT RESOLUTION REQUIRED         │
├─────────────────────────────────────────────┤
│                                             │
│  Contact: John Smith                        │
│  Field: Phone Number                        │
│                                             │
│  ┌─────────────────┐  ┌─────────────────┐  │
│  │   YOUR VERSION  │  │ SERVER VERSION  │  │
│  │                 │  │                 │  │
│  │  555-0100       │  │  555-0200       │  │
│  │                 │  │                 │  │
│  │  Modified:      │  │  Modified:      │  │
│  │  2026-08-10     │  │  2026-08-11     │  │
│  └─────────────────┘  └─────────────────┘  │
│                                             │
│  ┌─────────────────┐  ┌─────────────────┐  │
│  │  KEEP MINE      │  │  KEEP SERVER    │  │
│  └─────────────────┘  └─────────────────┘  │
│                                             │
│  ┌─────────────────────────────────────┐   │
│  │  ENTER NEW VALUE                    │   │
│  └─────────────────────────────────────┘   │
│                                             │
└─────────────────────────────────────────────┘
```

### 8.2 Multi-Field Conflict (C2)

```
┌─────────────────────────────────────────────┐
│         CONFLICT RESOLUTION REQUIRED         │
├─────────────────────────────────────────────┤
│                                             │
│  Contact: John Smith                        │
│                                             │
│  The following fields were modified by      │
│  another user:                              │
│                                             │
│  ☐ Email: john@new.com (server)             │
│    vs john@old.com (yours)                  │
│                                             │
│  ☐ Title: VP Sales (server)                 │
│    vs Sales Manager (yours)                 │
│                                             │
│  [ACCEPT ALL SERVER]  [KEEP MINE]           │
│                                             │
└─────────────────────────────────────────────┘
```

### 8.3 Delete Conflict (C3/C4)

```
┌─────────────────────────────────────────────┐
│         DELETE CONFLICT                      │
├─────────────────────────────────────────────┤
│                                             │
│  Contact: John Smith                        │
│                                             │
│  This record was modified since you         │
│  last viewed it. Your delete operation      │
│  has been blocked.                          │
│                                             │
│  Server changes:                            │
│  - Phone updated to 555-0200                │
│  - Email updated to john@new.com            │
│                                             │
│  [VIEW CHANGES]  [RETRY DELETE]             │
│                                             │
└─────────────────────────────────────────────┘
```

---

## 9. Sync Sequence Protocol

### 9.1 Sequence Numbering

All client mutations are assigned sequence numbers:

```
Format: {entity_type}:{entity_id}:{sequence_number}

Example: contact:abc-123:001
         contact:abc-123:002
         opportunity:def-456:001
```

### 9.2 Sequence Rules

1. Sequence numbers are monotonically increasing per entity
2. Server rejects out-of-sequence mutations (C11)
3. Client must retry in correct order
4. Sequence numbers are stored locally until confirmed by server

### 9.3 Sync Order

```
1. Reference data (pull-only)
2. Creates (new records)
3. Updates (existing records)
4. Deletes (mark for deletion)
5. Status transitions
6. Tag assignments
```

---

## 10. Conflict Logging

### 10.1 Conflict Log Schema

```sql
CREATE TABLE mobile_conflict_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    device_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    conflict_class VARCHAR(10) NOT NULL,  -- C1-C12
    resolution_strategy VARCHAR(20) NOT NULL,
    client_version INTEGER,
    server_version INTEGER,
    client_payload JSONB,
    server_payload JSONB,
    resolved_payload JSONB,
    resolved_by VARCHAR(20),  -- 'auto', 'user', 'server'
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 10.2 Conflict Log Fields

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| tenant_id | UUID | Tenant isolation |
| user_id | UUID | User who triggered conflict |
| device_id | UUID | Device that triggered conflict |
| entity_type | VARCHAR | Entity type (Contact, Lead, etc.) |
| entity_id | UUID | Entity identifier |
| conflict_class | VARCHAR | Conflict class (C1-C12) |
| resolution_strategy | VARCHAR | Strategy used for resolution |
| client_version | INTEGER | Client's version at time of conflict |
| server_version | INTEGER | Server's version at time of conflict |
| client_payload | JSONB | Client's mutation payload |
| server_payload | JSONB | Server's current state |
| resolved_payload | JSONB | Final resolved state |
| resolved_by | VARCHAR | How conflict was resolved |
| resolved_at | TIMESTAMP | When conflict was resolved |
| created_at | TIMESTAMP | When conflict was detected |

### 10.3 Conflict Metrics

Track for monitoring and alerting:

- Total conflicts per hour/day
- Conflicts by class (C1-C12)
- Conflicts by entity type
- Auto-merge success rate
- User resolution average time
- Conflict frequency per user/device

---

## 11. Edge Cases

### 11.1 Concurrent Deletes

**Scenario:** Two clients delete the same record simultaneously.

**Resolution:**
- First delete succeeds (APPLIED)
- Second delete is idempotent (APPLIED)
- No conflict — both clients remove from local DB

### 11.2 Create During Offline

**Scenario:** Client creates record while offline, server record is deleted.

**Resolution:**
- Client push: Create mutation with idempotency key
- Server: Record was deleted, but create is new
- Result: New record created (no conflict)

### 11.3 Rapid Successive Edits

**Scenario:** Client makes 5 edits to same field in rapid succession.

**Resolution:**
- Client batches edits locally
- Only final state is pushed to server
- Sequence number ensures ordering
- Only one conflict possible (final state vs server)

### 11.4 Large Payload Conflict

**Scenario:** Conflict on record with 100+ fields.

**Resolution:**
- Auto-merge for non-conflicting fields (majority)
- User resolution only for conflicting fields (minority)
- UI shows only conflicting fields, not all 100+

### 11.5 Network Timeout During Resolution

**Scenario:** Client submitting user resolution, network drops.

**Resolution:**
- Resolution is stored locally
- Re-sync on reconnect
- If server state changed again, new conflict detected
- User must re-resolve

---

## 12. Configuration

### 12.1 Conflict Policy Configuration

```yaml
conflict_policy:
  # Global settings
  auto_merge_enabled: true
  server_wins_fields:
    - "opportunity.amount"
    - "opportunity.close_date"
    - "opportunity.probability"
    - "lead.score"
    - "activity.*"  # All activity fields
    - "note.*"      # All note fields
  
  # Per-entity overrides
  entities:
    account:
      auto_merge_fields: ["phone", "email", "address", "employees"]
      user_resolution_fields: ["name", "industry"]
      server_wins_fields: ["revenue"]
    
    contact:
      auto_merge_fields: ["phone", "email", "title", "department"]
      user_resolution_fields: ["first_name", "last_name"]
    
    opportunity:
      auto_merge_fields: ["description"]
      user_resolution_fields: ["name"]
      server_wins_fields: ["amount", "close_date", "probability"]
      state_machine_fields: ["stage"]
  
  # Stale threshold for long-offline detection
  stale_threshold_days: 7
  
  # Sequence validation
  sequence_validation_enabled: true
  
  # Conflict log retention
  conflict_log_retention_days: 90
```

### 12.2 Entity State Machines

```yaml
state_machines:
  lead:
    initial: "NEW"
    transitions:
      NEW: ["CONTACTED", "DISQUALIFIED"]
      CONTACTED: ["QUALIFIED", "DISQUALIFIED"]
      QUALIFIED: ["CONVERTED", "DISQUALIFIED"]
      CONVERTED: []
      DISQUALIFIED: []
  
  opportunity:
    initial: "PROSPECTING"
    transitions:
      PROSPECTING: ["QUALIFICATION", "CLOSED_LOST"]
      QUALIFICATION: ["NEEDS_ANALYSIS", "CLOSED_LOST"]
      NEEDS_ANALYSIS: ["PROPOSAL", "CLOSED_LOST"]
      PROPOSAL: ["NEGOTIATION", "CLOSED_LOST"]
      NEGOTIATION: ["CLOSED_WON", "CLOSED_LOST"]
      CLOSED_WON: []
      CLOSED_LOST: []
  
  task:
    initial: "NOT_STARTED"
    transitions:
      NOT_STARTED: ["IN_PROGRESS", "CANCELLED"]
      IN_PROGRESS: ["COMPLETED", "CANCELLED"]
      COMPLETED: []
      CANCELLED: []
```

---

## 13. Testing Scenarios

### 13.1 Unit Test Cases

| Test Case | Conflict Class | Expected Result |
|-----------|----------------|-----------------|
| Same field, different values | C1 | USER_RESOLUTION |
| Different fields, same record | C2 | AUTO_MERGE |
| Delete with pending update | C3 | REJECT |
| Update with pending delete | C4 | REJECT |
| Duplicate create | C5 | IDEMPOTENCY_DEDUP |
| Parent version stale | C6 | REJECT |
| Reference data push attempt | C7 | PULL_ONLY |
| Invalid status transition | C8 | REJECT |
| Permission denied | C9 | REJECT |
| Duplicate mutation | C10 | IDEMPOTENCY_DEDUP |
| Out-of-sequence mutation | C11 | REJECT |
| Long-offline stale | C12 | FULL_RE_SYNC |

### 13.2 Integration Test Cases

1. **Offline → Online with C2 conflict:** Verify auto-merge works
2. **Offline → Online with C1 conflict:** Verify user resolution UI
3. **Multiple devices, same record:** Verify conflict detection
4. **Delete on device A, update on device B:** Verify delete blocks
5. **Long offline (7+ days):** Verify full re-sync triggers

---

## 14. Appendix

### A. Conflict Class Summary

| Class | Name | Strategy | User Action Required |
|-------|------|----------|---------------------|
| C1 | Same Record/Same Field | USER_RESOLUTION | Yes |
| C2 | Same Record/Different Fields | AUTO_MERGE | No |
| C3 | Delete vs Update | REJECT | Yes (re-fetch) |
| C4 | Update vs Delete | REJECT | Yes (re-fetch) |
| C5 | Create vs Create | IDEMPOTENCY_DEDUP | No |
| C6 | Parent vs Child | REJECT | Yes (re-fetch parent) |
| C7 | Reference vs Transactional | PULL_ONLY | No |
| C8 | Status Transition | STATE_MACHINE | Yes (retry) |
| C9 | Permission/Ownership | REJECT | Yes (re-verify) |
| C10 | Duplicate Mutation | IDEMPOTENCY_DEDUP | No |
| C11 | Reordered Mutations | REJECT | Yes (re-order) |
| C12 | Long-Offline Stale | FULL_RE_SYNC | Yes (wait) |

### B. Resolution Strategy Summary

| Strategy | Code | Description | Default Use |
|----------|------|-------------|-------------|
| AUTO_MERGE | 1 | Merge non-conflicting fields | Yes |
| SERVER_WINS | 2 | Server overwrites client | Financial/critical |
| CLIENT_WINS | 3 | Client overwrites server | **Never default** |
| USER_RESOLUTION | 4 | User picks winner | Same-field conflicts |
| REJECT | 5 | Mutation blocked | Ambiguous conflicts |

### C. Delete Conflict Summary

| Client | Server | Result |
|--------|--------|--------|
| Update | Delete | CONFLICT (server wins) |
| Delete | Update | CONFLICT (server wins) |
| Delete | Delete | APPLIED (idempotent) |
| Update | Hard Delete | REJECTED (404) |

---

**Document End**
