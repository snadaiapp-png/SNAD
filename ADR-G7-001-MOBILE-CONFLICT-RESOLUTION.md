# ADR-G7-001: Mobile Offline Conflict Resolution Policy

> **Status:** REQUIRES_REVISION
> **Date:** 2026-08-11
> **Decision Makers:** Operator (SNAD)
> **Technical Lead:** TBD
> **Stakeholders:** Mobile team, CRM team, Security team
> **Supersedes:** None
> **Superseded By:** None
> **Related:** G7_MOBILE_FOUNDATION_MASTER_BASELINE.md, G7_CONFLICT_RESOLUTION_DECISION_REPORT.md, G7_C2_C3_ARCHITECTURAL_DECISION.md

---

## Title

Mobile Offline Conflict Resolution Policy for SNAD CRM

---

## Status

**PROPOSED** — Not yet ACCEPTED. Requires operator approval before implementation.

---

## Context

SNAD is building a mobile CRM application (G7: Mobile Offline Foundation) that allows field sales teams to access and modify CRM data (Accounts, Contacts, Leads, Opportunities, Tasks) without continuous network connectivity. When the mobile device reconnects, locally-stored mutations must be synchronized with the server.

The SNAD backend already has a mature, production-grade conflict resolution architecture:

1. **Optimistic Locking:** Every CRM entity has a `version BIGINT` column. All UPDATE statements use `WHERE version = :expectedVersion`. On mismatch, the server returns HTTP 412 `CRM_CONCURRENCY_CONFLICT`.

2. **ETag + If-Match:** All PATCH endpoints require `If-Match` headers with SHA-256-derived ETags. Missing headers yield HTTP 428 `CRM_PRECONDITION_REQUIRED`. Stale ETags yield HTTP 412.

3. **Idempotency:** All POST endpoints require `Idempotency-Key` headers with SHA-256 fingerprint-based deduplication. 24-hour retention window.

4. **Pessimistic Locking:** `SELECT ... FOR UPDATE` used surgically for security tokens, outbox claiming, and ownership mutations.

5. **Audit Trail:** All mutations logged with before/after JSON snapshots to `platform_audit_logs`.

This existing architecture follows a **"Reject stale mutations; client must re-fetch and retry"** pattern. There is NO automatic server-side merge, NO Last-Write-Wins, and NO Client-Wins behavior.

The challenge for G7 is that mobile clients operating offline CANNOT immediately re-fetch and retry. They may have stale data for hours or days. The conflict resolution policy must extend the existing server-side behavior to handle this offline gap.

---

## Problem

When a mobile client pushes a mutation whose base version does not match the server's current version, the system must:

1. Detect the conflict (version mismatch)
2. Classify the conflict type (same field, different fields, delete-vs-update, etc.)
3. Determine resolution strategy (auto-merge, user resolution, server authority)
4. Log the conflict for audit and user notification
5. Return appropriate response to the mobile client
6. Preserve data integrity across multi-tenant, multi-device scenarios

The policy must be consistent with the existing server-side architecture while adding mobile-specific capabilities for offline-to-online synchronization.

---

## Constraints

1. **MUST NOT** break existing `CRM_CONCURRENCY_CONFLICT` (HTTP 412) behavior
2. **MUST NOT** introduce Last-Write-Wins or Client-Wins as default policies
3. **MUST** maintain tenant isolation on all sync operations (RLS enforced)
4. **MUST** maintain audit trail for all conflict events
5. **MUST** be compatible with existing `version` column on all CRM entities
6. **MUST** be compatible with existing `IdempotencyService` framework
7. **MUST** support hybrid strategy (different policies per entity type)
8. **MUST** handle financial/critical data differently (server authority)
9. **MUST** be convertible to acceptance tests and acceptance gates
10. **MUST** be implementable incrementally (not all-or-nothing)

---

## Existing Evidence

| Evidence | Source | Relevance |
|----------|--------|-----------|
| `version BIGINT NOT NULL DEFAULT 0` on all CRM tables | Flyway migrations V20260702_1 through V20260804_* | Version column already exists |
| `WHERE version = :expectedVersion` in all UPDATE statements | All JDBC repositories | Optimistic locking is the norm |
| `CRM_CONCURRENCY_CONFLICT` (HTTP 412) error code | `CrmErrorCode.java` line 63 | Existing conflict response |
| `CRM_PRECONDITION_REQUIRED` (HTTP 428) error code | `CrmErrorCode.java` line 57 | ETag enforcement |
| `ETagService.java` with SHA-256 ETag computation | `crm/concurrency/ETagService.java` | ETag infrastructure exists |
| `CrmOwnershipAtomicIfMatchAspect.java` | `crm/ownership/infrastructure/` | Atomic If-Match for ownership |
| `IdempotencyService.java` with fingerprint dedup | `crm/idempotency/` | Idempotency infrastructure exists |
| `ConcurrentClaimConflictException` mapped to 412 | `CrmOwnershipProblemHandler.java` | Ownership conflict handling |
| `PlatformAuditWriter.java` with before/after JSON | `admin/service/` | Audit infrastructure exists |
| `CrmConcurrencyContractTest.java` | Test suite | Concurrency contract tested |
| `13-TESTING-AUDIT.md` line 290 | Documentation | "Last-write-wins scenario is prevented" |

---

## Options Considered

### Option A: Last Write Wins (LWW)

**Description:** The most recent mutation (by timestamp) overwrites all prior changes.

| Criterion | Rating |
|-----------|--------|
| Data Integrity | LOW |
| User Experience | MEDIUM |
| Offline Capability | HIGH |
| Data Loss Risk | HIGH |
| Implementation Complexity | LOW |
| Auditability | LOW |
| Multi-Tenant Safety | LOW |
| Concurrency Safety | LOW |

**Rejected because:**
- Conflicts with existing `CRM_CONCURRENCY_CONFLICT` architecture
- Explicitly tested against in `13-TESTING-AUDIT.md`
- Silent data loss — no audit trail for overwritten changes
- High risk for multi-device scenarios

### Option B: Server Wins

**Description:** Server version always takes precedence. Client changes are discarded.

| Criterion | Rating |
|-----------|--------|
| Data Integrity | MEDIUM |
| User Experience | LOW |
| Offline Capability | LOW |
| Data Loss Risk | HIGH |
| Implementation Complexity | LOW |
| Auditability | MEDIUM |
| Multi-Tenant Safety | MEDIUM |
| Concurrency Safety | MEDIUM |

**Rejected because:**
- Mobile users lose offline edits silently
- Poor UX for field teams who spend hours offline
- Discards valuable field data

### Option C: Client Wins

**Description:** Client version always takes precedence. Server changes are overwritten.

| Criterion | Rating |
|-----------|--------|
| Data Integrity | LOW |
| User Experience | HIGH |
| Offline Capability | HIGH |
| Data Loss Risk | HIGH |
| Implementation Complexity | LOW |
| Auditability | LOW |
| Multi-Tenant Safety | LOW |
| Concurrency Safety | LOW |

**Rejected because:**
- Another user's concurrent edits are lost
- Violates multi-tenant safety
- No server-side validation of client changes

### Option D: Optimistic Concurrency + Reject

**Description:** Server rejects stale mutations with HTTP 412. Client must re-fetch and retry.

| Criterion | Rating |
|-----------|--------|
| Data Integrity | HIGH |
| User Experience | MEDIUM |
| Offline Capability | MEDIUM |
| Data Loss Risk | LOW |
| Implementation Complexity | MEDIUM |
| Auditability | HIGH |
| Multi-Tenant Safety | HIGH |
| Concurrency Safety | HIGH |

**Partially adopted:** This is the existing server-side policy. Extended for mobile with additional resolution strategies.

### Option E: Field-Level Merge

**Description:** Non-overlapping field changes are automatically merged. Overlapping fields require resolution.

| Criterion | Rating |
|-----------|--------|
| Data Integrity | HIGH |
| User Experience | HIGH |
| Offline Capability | MEDIUM |
| Data Loss Risk | LOW |
| Implementation Complexity | HIGH |
| Auditability | HIGH |
| Multi-Tenant Safety | HIGH |
| Concurrency Safety | HIGH |

**Adopted for:** Master Data entities (Account, Contact) and Task — where field-level merge is safe.

### Option F: Version-Based Merge

**Description:** Uses version metadata to determine merge strategy.

| Criterion | Rating |
|-----------|--------|
| Data Integrity | HIGH |
| User Experience | MEDIUM |
| Offline Capability | MEDIUM |
| Data Loss Risk | LOW |
| Implementation Complexity | HIGH |
| Auditability | HIGH |
| Multi-Tenant Safety | HIGH |
| Concurrency Safety | HIGH |

**Superseded by Option E:** Field-level merge is simpler and sufficient for CRM entities.

### Option G: Manual Resolution

**Description:** All conflicts require user intervention.

| Criterion | Rating |
|-----------|--------|
| Data Integrity | HIGH |
| User Experience | LOW |
| Offline Capability | LOW |
| Data Loss Risk | LOW |
| Implementation Complexity | MEDIUM |
| Auditability | HIGH |
| Multi-Tenant Safety | HIGH |
| Concurrency Safety | HIGH |

**Adopted for:** Lead, Opportunity (state-sensitive), and all cases where auto-merge is unsafe.

### Option H: Domain-Specific Resolution

**Description:** Different conflict strategies per entity type based on data characteristics.

| Criterion | Rating |
|-----------|--------|
| Data Integrity | HIGH |
| User Experience | MEDIUM |
| Offline Capability | MEDIUM |
| Data Loss Risk | LOW |
| Implementation Complexity | HIGH |
| Auditability | HIGH |
| Multi-Tenant Safety | HIGH |
| Concurrency Safety | HIGH |

**Adopted:** The G7 policy assigns different strategies per entity class.

### Option I: Hybrid Policy (RECOMMENDED)

**Description:** Combines multiple strategies:
- Server Authority + Client Notification (default)
- Auto-Merge for non-conflicting fields (Master Data, Task)
- User Resolution for conflicting fields (all entities)
- Server Authority for critical/financial data (future)
- Push-Only for append-only entities (Activity, Note)
- Pull-Only for reference data (Pipeline, Tags, Custom Fields)

| Criterion | Rating |
|-----------|--------|
| Data Integrity | HIGH |
| User Experience | HIGH |
| Offline Capability | HIGH |
| Data Loss Risk | LOW |
| Implementation Complexity | HIGH |
| Auditability | HIGH |
| Multi-Tenant Safety | HIGH |
| Concurrency Safety | HIGH |

**ADOPTED.**

---

## Decision

**Adopt Option I: Hybrid Policy — "Optimistic Concurrency with Progressive Resolution"**

### Default Policy

When a mobile client pushes a mutation whose base version does not match the server's current version:

1. **REJECT** the mutation (HTTP 412 or sync conflict response)
2. **LOG** the conflict in `mobile_conflict_log` with full before/after payloads
3. **NOTIFY** the client with both versions and conflict details
4. **CLASSIFY** the conflict type (same field, different fields, delete-vs-update, etc.)
5. If auto-merge is permitted for the entity type AND fields are non-overlapping:
   - **AUTO-MERGE** non-conflicting fields
   - **FLAG** conflicting fields for user resolution
6. If auto-merge is NOT permitted OR fields overlap:
   - **REQUIRE** user resolution before mutation is applied
7. The client decides: retry with fresh data, merge locally, or escalate to user

### Entity-Specific Policies

| Entity | Strategy | Auto-Merge? | User Resolution? |
|--------|----------|-------------|-----------------|
| Account | Reject + Auto-Merge Non-Conflicting | YES (non-overlapping fields) | Only for overlapping fields |
| Contact | Reject + Auto-Merge Non-Conflicting | YES (non-overlapping fields) | Only for overlapping fields |
| Lead | Reject + User Resolution | NO | YES — always |
| Opportunity | Reject + User Resolution | NO | YES — always |
| Task | Reject + Auto-Merge | YES (non-overlapping fields) | Only for overlapping fields |
| Activity | Reject + Auto-Merge Non-Conflicting | YES (non-overlapping fields) | Only for overlapping fields |
| Note | Push-Only (Archive only) | N/A | NO |
| Pipeline | Reject + User Resolution | NO | YES — always |
| Tags | Reject + User Resolution | NO | YES — always |
| Custom Fields | Reject + User Resolution | NO | YES — always |

### Critical Data Policy (Preemptive)

For any future financial/critical data entering G7 scope:

```
CRITICAL_DATA = Server Authority + Reject + Manual Resolution
```

- NO automatic merge
- NO Last Write Wins
- NO Client Wins
- Conflict logged with full before/after snapshots
- User MUST resolve before mutation is applied
- Audit trail mandatory

### Manual Resolution Triggers

| Trigger | Action |
|---------|--------|
| Same field modified on both sides | User must choose |
| Status/state transition conflict | User must choose valid transition |
| Delete vs Update conflict | User must choose: keep deleted or apply update |
| Ownership/permission conflict | Server authority; reject offline mutation |
| More than 3 conflicts in single sync batch | Pause sync; require user review |
| Conflict on entity with financial impact (future) | User must resolve before sync completes |

---

## Rationale

1. **Preserves existing architecture:** The hybrid policy extends the existing `CRM_CONCURRENCY_CONFLICT` pattern rather than replacing it. All server-side behavior remains unchanged.

2. **Minimizes data loss:** Auto-merge for non-conflicting fields preserves the maximum amount of offline work. User resolution for conflicting fields ensures no silent data loss.

3. **Respects domain differences:** Lead and Opportunity have state machines that make auto-merge unsafe. Account and Contact are simpler data structures where field-level merge is safe.

4. **Supports field teams:** Push-only entities (Activity, Note) allow field teams to log activities without conflict. Pull-only reference data (Pipeline, Tags) ensures consistency.

5. **Future-proof:** The critical data policy preemptively protects financial data when it enters scope.

6. **Testable:** Every decision maps to specific test scenarios (same-field conflict, different-field conflict, delete-vs-update, etc.).

7. **Auditable:** All conflicts are logged with full context for compliance and debugging.

---

## Consequences

### Positive

- Existing server-side behavior unchanged
- Field teams can work offline without data loss
- Non-conflicting edits are automatically merged (minimal friction)
- Conflicting edits require explicit user resolution (data safety)
- Full audit trail for all conflicts
- Compatible with existing `version`, `ETag`, and `Idempotency` infrastructure

### Negative

- Higher implementation complexity than simple LWW or Server Wins
- User resolution adds friction for conflicting edits
- Field-level merge requires entity-specific merge logic
- Conflict logging adds database storage overhead

### Risks

| Risk | Mitigation |
|------|------------|
| Auto-merge logic may have edge cases | Extensive testing with conflict scenarios |
| User may ignore conflict notifications | Conflict queue persists until resolved |
| Performance impact of version checks on sync | Index on `version` column; batch processing |
| Storage growth from conflict logs | 1-year retention policy; archival strategy |

---

## Rejected Alternatives

| Alternative | Reason for Rejection |
|-------------|---------------------|
| Last Write Wins | Silent data loss; contradicts existing architecture |
| Server Wins | Offline data loss; poor UX for field teams |
| Client Wins | Multi-tenant safety violation; server data loss |
| Pure Optimistic Concurrency (Reject only) | Insufficient for mobile offline gap |
| Pure Manual Resolution | Too much friction; poor UX |
| Single policy for all entities | Ignores domain differences (Lead vs Account) |

---

## Security Implications

1. **Tenant Isolation:** All sync operations enforced via RLS. Cross-tenant mutations rejected.
2. **Authorization:** RBAC checked on every sync push. Stale authorization rejected.
3. **Ownership:** Ownership validated post-mutation. Ownership conflicts rejected.
4. **Audit:** All conflicts logged with tenant, user, device, and full payloads.
5. **Idempotency:** Prevents duplicate mutations from retries.

---

## Data Integrity Implications

1. **No silent data loss:** All conflicts are detected and logged
2. **No automatic overwrite:** Server version is never silently replaced
3. **Field-level merge is safe:** Only non-overlapping fields are auto-merged
4. **State machine integrity:** Lead/Opportunity state transitions are user-validated
5. **Audit trail complete:** Before/after snapshots for all conflict events

---

## Migration Implications

1. **New tables required:** `mobile_sync_log`, `mobile_conflict_log`, `mobile_device_registry`, `mobile_sync_cursor`
2. **No changes to existing tables:** `version` columns already exist on all CRM entities
3. **No changes to existing APIs:** Server-side behavior unchanged
4. **New mobile sync APIs required:** `/api/v2/mobile/sync/pull`, `/api/v2/mobile/sync/push`, `/api/v2/mobile/sync/status`

---

## Testing Implications

1. **Unit tests:** Field-level merge logic, conflict classification, version comparison
2. **Integration tests:** Sync push with version match/mismatch, idempotency dedup
3. **Contract tests:** Sync API contracts, conflict response format
4. **Concurrency tests:** Multi-device simultaneous edits, out-of-order mutations
5. **Security tests:** Tenant isolation on sync, authorization validation
6. **E2E tests:** Full offline→online cycle with conflict resolution

---

## Acceptance Criteria

| # | Criterion | Evidence |
|---|-----------|----------|
| 1 | Conflict detected on version mismatch | Integration test: push stale mutation → 412 |
| 2 | Auto-merge works for non-conflicting fields | Integration test: Account phone + email → merged |
| 3 | User resolution required for same-field conflict | Integration test: Account name conflict → CONFLICT |
| 4 | Lead state conflict requires user resolution | Integration test: Lead status conflict → CONFLICT |
| 5 | Activity push-only accepts without conflict | Integration test: Activity creation → APPLIED |
| 6 | Pull-only entities reject offline writes | Integration test: Pipeline update → REJECTED |
| 7 | Conflict logged in mobile_conflict_log | SQL query: conflict record exists |
| 8 | Tenant isolation on sync operations | Security test: cross-tenant sync → REJECTED |
| 9 | Idempotency prevents duplicate mutations | Integration test: same idempotency_key → DUPLICATE |
| 10 | Audit trail complete for all conflicts | Audit query: conflict event in platform_audit_logs |

---

## Decision Makers

| Role | Name | Decision |
|------|------|----------|
| Operator | SNAD | APPROVE / REJECT |
| Technical Lead | TBD | REVIEW |
| Security Lead | TBD | REVIEW |

---

## Next Steps

1. Operator reviews and approves/rejects this ADR
2. If approved: implement conflict resolution in G7 sync push endpoint
3. If rejected: revise policy based on operator feedback
4. Create acceptance tests based on acceptance criteria above
5. Update G7_MOBILE_FOUNDATION_MASTER_BASELINE.md with approved policy

---

## Approval Required From

| Role | Responsibility | Decision |
|------|---------------|----------|
| Architecture Owner | Overall architecture consistency | ACCEPT / REJECT / REQUEST CHANGES |
| Product Owner | Entity policy alignment with product requirements | ACCEPT / REJECT / REQUEST CHANGES |
| Security Owner | Tenant isolation, authorization on sync | ACCEPT / REJECT / REQUEST CHANGES |
| Data/Platform Owner | Schema design, migration strategy | ACCEPT / REJECT / REQUEST CHANGES |

---

## Revision Notes

This ADR was revised after validation against actual source code:

| # | Change | Reason |
|---|--------|--------|
| 1 | Activity: Push-Only → Reject + Auto-Merge Non-Conflicting | Activity is mutable (update/complete with version check) |
| 2 | Pipeline: Pull-Only → Reject + User Resolution | Pipeline is mutable (create/update with version check) |
| 3 | Tags: Pull-Only → Reject + User Resolution | Tags is mutable (full CRUD + hard delete with version check) |
| 4 | Custom Fields: Pull-Only → Reject + User Resolution | Custom Fields is mutable (create/update with version check) |

All infrastructure claims (ETag, idempotency, audit, version checking) were VALIDATED against source code.

---

**END OF ADR-G7-001**
