# G7 CONFLICT RESOLUTION — TEST SPECIFICATION

> **Report ID:** G7-CONFLICT-TEST-SPEC-V1
> **Date:** 2026-08-11
> **Mode:** TEST DEFINITION / READ-ONLY
> **Status:** DEFINED (not PASS)
> **No code modified. No tests executed.**

---

## Overview

This document defines 12 acceptance tests for the G7 Conflict Resolution Policy. These tests are **DEFINED** but not yet **PASS**. They will be implemented as part of TRACK B (after ADR acceptance).

---

## G7-CONFLICT-001: Fresh Mutation → Success

**Priority:** P0 BLOCKER
**Track:** B (after ADR acceptance)
**Type:** Integration Test

### Scenario
Client pushes a mutation for an entity whose server version matches the client's base version.

### Preconditions
- Entity exists on server with `version = 5`
- Client read entity at `version = 5`
- No other mutations occurred

### Test Steps
1. Client reads Contact (version = 5)
2. Client updates Contact.phone = "555-0100"
3. Client pushes mutation with `base_version = 5`
4. Server processes mutation

### Expected Result
```
Status: APPLIED
New Version: 6
Response: { status: "APPLIED", new_version: 6, entity_id: "..." }
```

### Assertions
- `response.status == "APPLIED"`
- `response.new_version == 6`
- Entity.phone == "555-0100" on server
- Entity.version == 6 on server
- `mobile_sync_log` entry with status = "APPLIED"

---

## G7-CONFLICT-002: Stale Mutation → HTTP 412

**Priority:** P0 BLOCKER
**Track:** B
**Type:** Integration Test

### Scenario
Client pushes a mutation for an entity whose server version does NOT match the client's base version.

### Preconditions
- Entity exists on server with `version = 5`
- Client read entity at `version = 5`
- Another user updated entity to `version = 6`

### Test Steps
1. Client reads Contact (version = 5)
2. Another user updates Contact (version 5 → 6)
3. Client updates Contact.phone = "555-0100"
4. Client pushes mutation with `base_version = 5`
5. Server detects version mismatch

### Expected Result
```
Status: CONFLICT
Server Version: 6
Client Version: 5
Response: { status: "CONFLICT", server_version: 6, client_version: 5, conflict_id: "..." }
```

### Assertions
- `response.status == "CONFLICT"`
- `response.server_version == 6`
- `response.client_version == 5`
- `response.conflict_id` is not null
- Entity.phone unchanged on server (still original value)
- Entity.version still 6 on server
- `mobile_conflict_log` entry created
- `mobile_sync_log` entry with status = "CONFLICT"

---

## G7-CONFLICT-003: Duplicate Mutation → Idempotent Result

**Priority:** P0 BLOCKER
**Track:** B
**Type:** Integration Test

### Scenario
Client pushes the same mutation twice (same idempotency_key).

### Preconditions
- Entity exists on server with `version = 5`
- Client has mutation with idempotency_key = "key-123"

### Test Steps
1. Client pushes mutation with idempotency_key = "key-123"
2. Server processes mutation (APPLIED, version 6)
3. Client pushes SAME mutation again with idempotency_key = "key-123"
4. Server detects duplicate

### Expected Result
```
First push:  Status: APPLIED, New Version: 6
Second push: Status: DUPLICATE, Same result as first
```

### Assertions
- First push returns `APPLIED`
- Second push returns `DUPLICATE` with same result as first
- Entity.version == 6 (not 7 — no double-increment)
- Entity.phone == "555-0100" (applied once, not twice)
- `mobile_sync_log` has 2 entries: one APPLIED, one DUPLICATE

---

## G7-CONFLICT-004: Client-Only Field Change → Auto-Merge

**Priority:** P0 BLOCKER
**Track:** B
**Type:** Integration Test

### Scenario
Client changes a field that the server has NOT changed since the base version.

### Preconditions
- Entity (Account) at base version 5: name="Acme", phone="111", email="old@acme.com"
- Server version 6: name="Acme", phone="111", email="new@acme.com" (server changed email)
- Client mutation: phone="222" (client changed phone)

### Test Steps
1. Client reads Account (version 5): name=A, phone=111, email=old
2. Server receives update changing email to "new@acme.com" (version 5 → 6)
3. Client pushes phone="222" with base_version=5
4. Server performs three-way merge

### Expected Result
```
Status: APPLIED (auto-merged)
Phone: 222 (client's change applied)
Email: new@acme.com (server's change preserved)
Name: Acme (unchanged)
New Version: 7
```

### Assertions
- `response.status == "APPLIED"`
- `response.new_version == 7`
- Entity.phone == "222" (client's change)
- Entity.email == "new@acme.com" (server's change preserved)
- Entity.name == "Acme" (unchanged)
- `mobile_sync_log` entry with status = "APPLIED"

---

## G7-CONFLICT-005: Server-Only Field Change → Preserve Server

**Priority:** P0 BLOCKER
**Track:** B
**Type:** Integration Test

### Scenario
Server changes a field that the client has NOT changed.

### Preconditions
- Entity (Contact) at base version 5: name="John", phone="111", email="john@example.com"
- Server version 6: name="John", phone="222", email="john@example.com" (server changed phone)
- Client mutation: email="new@example.com" (client changed email)

### Test Steps
1. Client reads Contact (version 5): name=John, phone=111, email=john@example.com
2. Server receives update changing phone to "222" (version 5 → 6)
3. Client pushes email="new@example.com" with base_version=5
4. Server performs three-way merge

### Expected Result
```
Status: APPLIED (auto-merged)
Phone: 222 (server's change preserved)
Email: new@example.com (client's change applied)
Name: John (unchanged)
New Version: 7
```

### Assertions
- `response.status == "APPLIED"`
- Entity.phone == "222" (server's change)
- Entity.email == "new@example.com" (client's change)
- Entity.name == "John" (unchanged)

---

## G7-CONFLICT-006: Same-Field Mutation → Conflict

**Priority:** P0 BLOCKER
**Track:** B
**Type:** Integration Test

### Scenario
Both client and server change the SAME field.

### Preconditions
- Entity (Account) at base version 5: name="Acme", phone="111"
- Server version 6: name="Acme", phone="333" (server changed phone)
- Client mutation: phone="222" (client changed phone)

### Test Steps
1. Client reads Account (version 5): phone=111
2. Server receives update changing phone to "333" (version 5 → 6)
3. Client pushes phone="222" with base_version=5
4. Server detects same-field conflict

### Expected Result
```
Status: CONFLICT
Server Version: 6
Client Version: 5
Conflict Type: SAME_FIELD
Conflicting Fields: ["phone"]
Server Value: "333"
Client Value: "222"
```

### Assertions
- `response.status == "CONFLICT"`
- `response.conflict_type == "SAME_FIELD"`
- `response.conflicting_fields` contains "phone"
- `response.server_payload.phone == "333"`
- `response.client_payload.phone == "222"`
- Entity.phone unchanged on server (still "333" or original)
- `mobile_conflict_log` entry with conflict_type = "SAME_FIELD"

---

## G7-CONFLICT-007: Delete-vs-Update → Conflict

**Priority:** P1 CRITICAL
**Track:** B
**Type:** Integration Test

### Scenario
Client updates an entity that the server has archived (soft-deleted).

### Preconditions
- Entity (Contact) at base version 5: lifecycle_status = "ACTIVE"
- Server version 6: lifecycle_status = "ARCHIVED" (someone archived it)
- Client mutation: phone = "555-0100"

### Test Steps
1. Client reads Contact (version 5): status=ACTIVE
2. Server archives Contact (version 5 → 6, status=ARCHIVED)
3. Client pushes phone update with base_version=5
4. Server detects conflict

### Expected Result
```
Status: CONFLICT
Conflict Type: DELETE_VS_UPDATE
Server State: ARCHIVED
Client Action: UPDATE
```

### Assertions
- `response.status == "CONFLICT"`
- `response.conflict_type == "DELETE_VS_UPDATE"`
- Entity still ARCHIVED on server
- `mobile_conflict_log` entry with conflict_type = "DELETE_VS_UPDATE"

---

## G7-CONFLICT-008: State Transition Conflict → Reject/Resolve

**Priority:** P1 CRITICAL
**Track:** B
**Type:** Integration Test

### Scenario
Client and server both change the state of an entity.

### Preconditions
- Lead at base version 5: status = "CONTACTED"
- Server version 6: status = "QUALIFIED" (server qualified the lead)
- Client mutation: status = "DISQUALIFIED" (client wants to disqualify)

### Test Steps
1. Client reads Lead (version 5): status=CONTACTED
2. Server qualifies Lead (version 5 → 6, status=QUALIFIED)
3. Client pushes status=DISQUALIFIED with base_version=5
4. Server detects state conflict

### Expected Result
```
Status: CONFLICT
Conflict Type: STATE_TRANSITION
Server State: QUALIFIED
Client State: DISQUALIFIED
Base State: CONTACTED
```

### Assertions
- `response.status == "CONFLICT"`
- `response.conflict_type == "STATE_TRANSITION"`
- Lead.status remains "QUALIFIED" on server
- `mobile_conflict_log` entry with conflict_type = "STATE_TRANSITION"

---

## G7-CONFLICT-009: Cross-Tenant Mutation → Reject

**Priority:** P0 BLOCKER
**Track:** B
**Type:** Security Test

### Scenario
Client from Tenant A attempts to mutate an entity belonging to Tenant B.

### Preconditions
- Entity belongs to Tenant B
- Client authenticated as user in Tenant A

### Test Steps
1. Client (Tenant A) pushes mutation for entity (Tenant B)
2. Server validates tenant context

### Expected Result
```
Status: UNAUTHORIZED
Error: Cross-tenant mutation rejected
```

### Assertions
- `response.status == "UNAUTHORIZED"`
- Entity unchanged on server
- No `mobile_conflict_log` entry (not a conflict, an authorization failure)
- `mobile_sync_log` entry with status = "UNAUTHORIZED"

---

## G7-CONFLICT-010: Unauthorized Mutation → Reject

**Priority:** P0 BLOCKER
**Track:** B
**Type:** Security Test

### Scenario
Client pushes a mutation but the user no longer has write permission.

### Preconditions
- Entity exists, user had write permission at base_version time
- User's role changed (revoked write permission)

### Test Steps
1. Client reads entity (had permission)
2. User's role is changed (permission revoked)
3. Client pushes mutation
4. Server re-validates authorization

### Expected Result
```
Status: UNAUTHORIZED
Error: User no longer has write permission
```

### Assertions
- `response.status == "UNAUTHORIZED"`
- Entity unchanged on server
- `mobile_sync_log` entry with status = "UNAUTHORIZED"

---

## G7-CONFLICT-011: Retry After Server Commit → No Duplicate

**Priority:** P1 CRITICAL
**Track:** B
**Type:** Integration Test

### Scenario
Client pushes mutation, server commits, client retries due to network timeout.

### Preconditions
- Entity at version 5
- Client pushes mutation (idempotency_key = "retry-test-1")

### Test Steps
1. Client pushes mutation with idempotency_key = "retry-test-1"
2. Server commits (version 5 → 6)
3. Network timeout (client doesn't receive response)
4. Client retries with same idempotency_key = "retry-test-1"
5. Server detects duplicate

### Expected Result
```
First push:  APPLIED (version 6)
Retry:       DUPLICATE (same result as first)
```

### Assertions
- First push returns APPLIED
- Retry returns DUPLICATE with same result
- Entity.version == 6 (not 7)
- No duplicate mutation created

---

## G7-CONFLICT-012: Multi-Device Concurrent Mutation → Correct Resolution

**Priority:** P1 CRITICAL
**Track:** B + C (requires C1 decision)
**Type:** Integration Test

### Scenario
Three devices modify the same entity concurrently.

### Preconditions
- Entity at version 5
- Device A reads entity (base_version = 5)
- Device B reads entity (base_version = 5)
- Device C reads entity (base_version = 5)

### Test Steps
1. Device A pushes phone = "111" (base_version = 5)
2. Device B pushes email = "b@test.com" (base_version = 5)
3. Device C pushes name = "Charlie" (base_version = 5)
4. All three push simultaneously

### Expected Result (depends on C1 decision)
```
Scenario 1 (First-come-first-served):
  Device A: APPLIED (version 6)
  Device B: CONFLICT (base_version 5 != server_version 6)
  Device C: CONFLICT (base_version 5 != server_version 6)

Scenario 2 (All-or-nothing batch):
  All three: CONFLICT (batch contains conflicting mutations)
```

### Assertions
- At least one device gets APPLIED
- Other devices get CONFLICT with correct server_version
- `mobile_conflict_log` entries for conflicting devices
- Entity state is consistent (no partial writes)

---

## Test Summary

| Test ID | Name | Priority | Track | Type | Status |
|---------|------|----------|-------|------|--------|
| G7-CONFLICT-001 | Fresh Mutation → Success | P0 BLOCKER | B | Integration | DEFINED |
| G7-CONFLICT-002 | Stale Mutation → HTTP 412 | P0 BLOCKER | B | Integration | DEFINED |
| G7-CONFLICT-003 | Duplicate Mutation → Idempotent | P0 BLOCKER | B | Integration | DEFINED |
| G7-CONFLICT-004 | Client-Only Field → Auto-Merge | P0 BLOCKER | B | Integration | DEFINED |
| G7-CONFLICT-005 | Server-Only Field → Preserve | P0 BLOCKER | B | Integration | DEFINED |
| G7-CONFLICT-006 | Same-Field → Conflict | P0 BLOCKER | B | Integration | DEFINED |
| G7-CONFLICT-007 | Delete-vs-Update → Conflict | P1 CRITICAL | B | Integration | DEFINED |
| G7-CONFLICT-008 | State Transition → Conflict | P1 CRITICAL | B | Integration | DEFINED |
| G7-CONFLICT-009 | Cross-Tenant → Reject | P0 BLOCKER | B | Security | DEFINED |
| G7-CONFLICT-010 | Unauthorized → Reject | P0 BLOCKER | B | Security | DEFINED |
| G7-CONFLICT-011 | Retry After Commit → No Dup | P1 CRITICAL | B | Integration | DEFINED |
| G7-CONFLICT-012 | Multi-Device → Resolution | P1 CRITICAL | B+C | Integration | DEFINED |

### Coverage by Category

| Category | Tests | P0 | P1 |
|----------|-------|----|----|
| Version Conflict | 001, 002, 003 | 3 | 0 |
| Auto-Merge | 004, 005 | 2 | 0 |
| Same-Field Conflict | 006 | 1 | 0 |
| Delete Conflict | 007 | 0 | 1 |
| State Conflict | 008 | 0 | 1 |
| Security | 009, 010 | 2 | 0 |
| Idempotency | 003, 011 | 1 | 1 |
| Multi-Device | 012 | 0 | 1 |
| **TOTAL** | **12** | **9** | **3** |

---

## Implementation Notes

1. All tests use PostgreSQL Direct (not Docker/Testcontainers)
2. Each test gets a fresh tenant context
3. Tests use existing `CrmConcurrencyContractTest` patterns
4. Security tests validate RLS enforcement
5. Multi-device test (012) depends on TRACK C decision for exact behavior

---

**END OF G7 CONFLICT TEST SPECIFICATION**
