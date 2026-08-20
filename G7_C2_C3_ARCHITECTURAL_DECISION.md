# G7 C2/C3 Architectural Decision

> **Generated:** 2026-08-11
> **Purpose:** Architectural decisions for Offline Duration (C2) and Conflict Lifecycle (C3)
> **Status:** PROPOSED — Awaiting Operator Review
> **Scope:** Analysis only — NO code, NO migrations, NO implementation

---

## 1. Current Evidence

### C2 — Existing Duration Values

| Parameter | Value | Source | Mobile-Specific? |
|-----------|-------|--------|-----------------|
| Access Token TTL | 15 minutes | `SecurityProperties.java:43` | NO — server-wide |
| Refresh Token TTL | 7 days (168h) | `SecurityProperties.java:60` | NO — server-wide |
| Idempotency Retention | 24 hours | `IdempotencyService.java:42` | NO — server-wide |
| Session Version Cache | 5 seconds | `SessionVersionCache.java:37` | NO — server-wide |
| Password Reset TTL | 30 minutes | `AuthService.java:55` | NO — server-wide |
| Mobile offline duration limit | **NOT DEFINED** | — | **TRACK C** |
| Full re-sync trigger | **NOT DEFINED** | — | **TRACK C** |
| Stale client detection | **NOT DEFINED** | — | **TRACK C** |

**Key finding:** The refresh token (7 days) provides a NATURAL HARD MAXIMUM for offline authentication. After 7 days without connectivity, the device cannot obtain a new access token and must re-authenticate.

### C3 — Existing Conflict Infrastructure

| Component | Status | Evidence |
|-----------|--------|----------|
| `mobile_conflict_log` table | **NOT CREATED** | No Flyway migration |
| Conflict resolution timeout | **NOT DEFINED** | No configuration |
| Conflict expiration/purge | **NOT DEFINED** | No configuration |
| Unresolved conflict handling | **NOT DEFINED** | No configuration |
| Conflict retention policy | **PROPOSED** (1 year) | `ADR-G7-001` line 374 |
| Resolution deadline | **NOT DEFINED** | No configuration |
| Cleanup scheduled job | **NOT EXISTS** | `SchedulingConfig.java` |
| Current conflict handling | **IMMEDIATE REJECTION** | HTTP 412 on version mismatch |

**Key finding:** The current system uses IMMEDIATE REJECTION (HTTP 412). The mobile offline foundation introduces a NEW conflict detection layer that must coexist with this server-side behavior.

---

## 2. C2 Options

### Conceptual Framework

The user correctly identified four distinct concepts that must NOT be conflated:

| Concept | Definition | Existing Bound |
|---------|-----------|----------------|
| **OFFLINE_PERMISSION** | Can the user continue working offline? | None — no enforcement |
| **AUTHENTICATION_EXPIRY** | When does the token expire? | 7 days (refresh token) |
| **DATA_STALENESS** | How stale can local data be? | None — no detection |
| **SYNC_ELIGIBILITY** | When is the device eligible to sync? | None — no gating |

### OPTION A: Hard Maximum Offline Duration

**Definition:** Enforce a server-side or client-side maximum offline period (e.g., 24h, 48h, 7d). After expiration, the device is forced into full resync + re-authentication.

| Dimension | Assessment |
|-----------|-----------|
| Security | GOOD — bounded exposure window |
| Data Integrity | GOOD — bounded staleness |
| User Experience | BAD — arbitrary cutoff frustrates users mid-task |
| Conflict Rate | GOOD — bounded window means bounded conflicts |
| Storage | GOOD — bounded local data |
| Sync Complexity | LOW — simple "time exceeded = full resync" |
| Authentication | Tied to refresh token (7d) — redundant if <7d |
| Auditability | GOOD — predictable behavior |
| Multi-device | GOOD — bounded window limits divergence |
| Scalability | GOOD — bounded local data |

**Problems:**
- No basis for any specific number (24h? 48h? 7d?)
- The refresh token already provides a 7-day hard maximum
- A hardcoded maximum would be arbitrary and potentially harmful
- A user working offline on a flight would be forcibly disconnected

**Verdict: REJECT** — No justification for an arbitrary number when the refresh token provides a natural bound.

### OPTION B: No Hard Maximum + Staleness Detection + Re-authentication + Full Resync

**Definition:** Allow unlimited offline operation. Detect staleness by comparing the device's last sync timestamp against entity modification timestamps. Trigger re-authentication when the refresh token expires. Trigger full resync when staleness exceeds configurable thresholds.

| Dimension | Assessment |
|-----------|-----------|
| Security | GOOD — re-authentication when needed |
| Data Integrity | GOOD — staleness detection catches issues |
| User Experience | GOOD — user works freely, warned when stale |
| Conflict Rate | POTENTIALLY HIGHER — no bound on staleness |
| Storage | CONCERN — unbounded local data if user stays offline indefinitely |
| Sync Complexity | MEDIUM — must detect staleness and handle re-sync |
| Authentication | NATURAL BOUND via refresh token (7d) |
| Auditability | GOOD — staleness events are logged |
| Multi-device | GOOD — staleness detection catches divergent edits |
| Scalability | CONCERN — unbounded local data, but bounded by refresh token |

**Key insight:** The refresh token (7 days) IS the natural hard maximum. No additional bound needed.

**Staleness detection mechanism:**
1. Device records `last_sync_timestamp` locally
2. On reconnect, server compares device's `last_sync_timestamp` against each entity's `updated_at`
3. If any entity was modified while the device was offline, the device receives fresh data
4. If the device has pending mutations, conflict detection applies

**Full resync triggers:**
1. Re-authentication required (refresh token expired)
2. Device's sync cursor is invalid (server cannot find the referenced state)
3. Device explicitly requests full resync (user action or automated)
4. Server detects the device has been offline longer than the refresh token TTL

**Verdict: RECOMMEND** — Simple, secure, user-friendly. The refresh token provides the natural bound.

### OPTION C: No Hard Maximum + Entity-Specific Staleness Rules

**Definition:** Allow unlimited offline operation. Each entity type has different staleness rules (e.g., Account data is stale after 24h, Contact data after 7d, Opportunity after 48h).

| Dimension | Assessment |
|-----------|-----------|
| Security | GOOD — per-entity exposure control |
| Data Integrity | GOOD — entity-appropriate staleness |
| User Experience | COMPLEX — different entities behave differently |
| Conflict Rate | GOOD — entity-appropriate bounds |
| Storage | GOOD — entity-appropriate retention |
| Sync Complexity | HIGH — N rules for N entity types |
| Authentication | Same as B |
| Auditability | GOOD — per-entity staleness logged |
| Multi-device | COMPLEX — stale for one entity, fresh for another |
| Scalability | CONCERN — rule management overhead |

**Problems:**
- Adds significant complexity for marginal benefit
- No basis for entity-specific numbers
- Hard to reason about and test
- Users don't think in terms of "Account staleness vs Contact staleness"

**Verdict: REJECT** — Over-engineered for the current requirements.

### OPTION D: Hybrid

**Definition:** Combine elements of B and C. Default staleness rules for most entities, with entity-specific overrides where needed.

| Dimension | Assessment |
|-----------|-----------|
| Security | GOOD |
| Data Integrity | GOOD |
| User Experience | MEDIUM — mostly simple, some entity-specific behavior |
| Conflict Rate | GOOD |
| Storage | GOOD |
| Sync Complexity | MEDIUM-HIGH — default + overrides |
| Authentication | Same as B |
| Auditability | GOOD |
| Multi-device | GOOD |
| Scalability | MEDIUM |

**Problems:**
- Combines complexity of B and C
- No current need for entity-specific overrides
- Can be achieved later by extending Option B if needed

**Verdict: DEFER** — Start with B, extend to D if entity-specific requirements emerge.

---

## 3. C2 Recommendation

### Recommended Policy: OPTION B — Staleness Detection with Natural Authentication Bound

### Four Concepts — Defined

#### 1. OFFLINE_PERMISSION

**Policy:** UNLIMITED offline operation. The device can work offline indefinitely.

**Rationale:** There is no technical or business reason to forcibly disconnect a user who is working offline. The refresh token provides a natural authentication bound. Data staleness is handled by the sync engine, not by arbitrary time limits.

**Implementation:** No enforcement. The device queues mutations locally and syncs when connectivity is available.

#### 2. AUTHENTICATION_EXPIRY

**Policy:** Refresh token expires after 7 days (existing server-wide configuration).

**Rationale:** This is the NATURAL HARD MAXIMUM. After 7 days offline, the device cannot obtain a new access token. The user must re-authenticate. This is already implemented and does not require G7 changes.

**Implementation:** Existing `SecurityProperties.refreshTokenTtl = Duration.ofDays(7)`. No G7 changes needed.

**Edge case:** If the user is offline for >7 days, the refresh token expires. On reconnect:
1. Device attempts to refresh → gets 401
2. Device prompts user to re-authenticate
3. User enters credentials → new access token + refresh token issued
4. Device performs full resync (see SYNC_ELIGIBILITY below)

#### 3. DATA_STALENESS

**Policy:** Staleness is DETECTED, not ENFORCED. The server compares the device's last sync timestamp against entity modification timestamps.

**Rationale:** No arbitrary staleness threshold is needed. The server knows what data was modified while the device was offline. The device receives only the data it needs.

**Implementation:**
1. Device stores `last_sync_timestamp` locally (per-entity or per-entity-type)
2. On reconnect, device sends `If-Modified-Since: <last_sync_timestamp>` header
3. Server responds with only entities modified after that timestamp
4. Server also sends `stale_entities: [...]` list for entities the device has locally but were modified by other users
5. Device merges fresh data, detects conflicts for pending mutations

**Staleness states for individual entities:**

| State | Condition | Device Action |
|-------|-----------|--------------|
| FRESH | `entity.updated_at <= device.last_sync_timestamp` | Use local copy |
| STALE | `entity.updated_at > device.last_sync_timestamp` | Server sends fresh copy |
| CONFLICTED | Entity has pending local mutation AND server version changed | Trigger conflict resolution |

#### 4. SYNC_ELIGIBILITY

**Policy:** Device is eligible to sync when:
1. Device has network connectivity
2. Device has a valid access token (or can refresh it)
3. Device is not in SYNC_BLOCKED state (see states below)

**Rationale:** Sync eligibility is a binary check, not a staleness check. The device syncs whenever it can. Staleness is handled during the sync process.

**Implementation:** Standard network connectivity check + token validity check.

### Device States

| State | Condition | Behavior |
|-------|-----------|----------|
| **ONLINE** | Connected + valid token | Real-time sync, push/pull immediately |
| **OFFLINE** | No connectivity | Queue mutations locally, cache reads |
| **OFFLINE_STALE** | No connectivity + local data older than X | Same as OFFLINE — no forced action |
| **REAUTH_REQUIRED** | Refresh token expired | Must re-authenticate before sync |
| **FULL_RESYNC_REQUIRED** | Sync cursor invalid or device explicitly requests | Download all entities, rebuild local state |
| **SYNC_BLOCKED** | Server rejects sync (e.g., tenant suspended) | Cannot sync until unblocked |

**Note:** `OFFLINE_STALE` is an informational state, not a blocking state. The device continues to work offline. On reconnect, staleness is resolved during sync.

### What Happens When Reconnecting

**Scenario 1: Offline < 7 days, no pending mutations**
1. Device detects connectivity
2. Device sends `If-Modified-Since: <last_sync_timestamp>`
3. Server responds with modified entities
4. Device updates local cache
5. State: ONLINE

**Scenario 2: Offline < 7 days, WITH pending mutations**
1. Device detects connectivity
2. Device sends pending mutations with `If-Match` headers (ETag-based)
3. Server processes each mutation:
   - Version matches → APPLIED (HTTP 200)
   - Version mismatch → REJECTED (HTTP 412) → conflict logged
4. Device receives fresh data for rejected mutations
5. Device presents conflicts to user
6. State: ONLINE (with conflicts pending resolution)

**Scenario 3: Offline > 7 days (refresh token expired)**
1. Device detects connectivity
2. Device attempts to refresh token → gets 401
3. Device prompts user to re-authenticate
4. User enters credentials → new tokens issued
5. Device performs FULL_RESYNC (downloads all entities)
6. Any pending local mutations are discarded (too stale to merge)
7. State: ONLINE

**Scenario 4: Offline indefinitely (user never reconnects)**
1. Device continues to work offline (queued mutations)
2. Local data becomes increasingly stale
3. No enforcement — the user is responsible for reconnecting
4. On eventual reconnect: Scenario 2 or 3 applies

### C2 Final Decision

```
C2_STATUS = DEFINED
C2_POLICY = OPTION B — Staleness Detection with Natural Authentication Bound
C2_OFFLINE_PERMISSION = UNLIMITED
C2_AUTHENTICATION_EXPIRY = 7 days (existing refresh token TTL)
C2_DATA_STALENESS = DETECTED, not ENFORCED (server compares timestamps)
C2_SYNC_ELIGIBILITY = Connectivity + valid token
C2_HARD_MAXIMUM = NONE (refresh token is the natural bound)
C2_FULL_RESYNC_TRIGGER = Re-authentication OR sync cursor invalid OR explicit request
C2_BLOCKER = NO — architecture supports this without additional design
```

---

## 4. C3 Options

### Conceptual Framework

The user correctly identified five distinct concepts that must NOT be conflated:

| Concept | Definition | Current State |
|---------|-----------|---------------|
| **User Resolution SLA** | How quickly must a user resolve a conflict? | None |
| **Conflict Data Retention** | How long are conflict records kept? | None (no table) |
| **Conflict Expiration** | What happens to unresolved conflicts over time? | None |
| **Operational Alerting** | When should ops be notified about conflicts? | None |
| **Cleanup** | How are old conflict records managed? | None |

### OPTION A: No SLA — Conflict Remains OPEN Until Resolved

**Definition:** Conflicts have no expiration. They remain in OPEN status until the user explicitly resolves them. No retention policy. No cleanup.

| Dimension | Assessment |
|-----------|-----------|
| Simplicity | EXCELLENT — no SLA logic needed |
| User Pressure | NONE — user resolves at their own pace |
| Data Integrity | RISK — old conflicts may reference stale entity versions |
| Storage | RISK — conflicts accumulate indefinitely |
| Operational Visibility | GOOD — all conflicts are visible |
| Cleanup | NONE — unbounded growth |

**Problems:**
- Conflicts accumulate indefinitely
- Old conflicts may reference entity versions that no longer exist
- Storage grows without bound
- Operational team has no signal for "this conflict has been open for 6 months"

**Verdict: REJECT** — Unbounded accumulation is not acceptable.

### OPTION B: Product-Defined Resolution SLA

**Definition:** A product-defined deadline for conflict resolution (e.g., "must resolve within 7 days"). After the deadline, the conflict is auto-resolved (server wins) or escalated.

| Dimension | Assessment |
|-----------|-----------|
| Simplicity | MEDIUM — requires SLA configuration |
| User Pressure | HIGH — artificial deadline may frustrate users |
| Data Integrity | GOOD — bounded resolution window |
| Storage | GOOD — bounded conflict lifetime |
| Operational Visibility | GOOD — SLA breach alerts |
| Cleanup | GOOD — auto-resolve after deadline |

**Problems:**
- Requires a product decision (what's the number?)
- Artificial deadlines don't improve data quality
- A user on a 2-week vacation would have all conflicts auto-resolved (server wins)
- May cause data loss if the user's mutation was actually correct

**Verdict: REJECT** — Artificial deadlines cause data loss risk.

### OPTION C: Technical Expiration/Retention Without User-Resolution SLA

**Definition:** No user-resolution SLA. Conflicts remain OPEN indefinitely from the user's perspective. Technical retention policy governs conflict record lifecycle. Auto-resolve after retention period (server wins). Archive old records.

| Dimension | Assessment |
|-----------|-----------|
| Simplicity | GOOD — clean separation of concerns |
| User Pressure | NONE — user resolves at their own pace |
| Data Integrity | GOOD — retention prevents stale references |
| Storage | GOOD — bounded by retention period |
| Operational Visibility | GOOD — retention-based cleanup |
| Cleanup | GOOD — archive after retention |

**Key insight:** The user-resolution SLA is a PRODUCT decision, not an ARCHITECTURAL decision. The architecture should support whatever SLA the product team defines (including no SLA).

**Retention policy:**
- Conflict records are retained for a configurable period (default: 1 year)
- After retention: conflict is auto-resolved (server wins) and record is ARCHIVED
- Archived records are kept for audit but no longer actionable
- No deletion — audit trail preserved

**Verdict: RECOMMEND** — Clean separation of concerns. Architecture supports any product-defined SLA.

### OPTION D: Hybrid

**Definition:** Combine elements of B and C. Technical retention for all conflicts, with optional product-defined SLA for specific entity types.

| Dimension | Assessment |
|-----------|-----------|
| Simplicity | MEDIUM — two mechanisms |
| User Pressure | LOW-MEDIUM — SLA only for some entities |
| Data Integrity | GOOD |
| Storage | GOOD |
| Operational Visibility | GOOD |
| Cleanup | GOOD |

**Problems:**
- Adds complexity for marginal benefit
- No current need for entity-specific SLAs
- Can be achieved later by extending Option C

**Verdict: DEFER** — Start with C, extend to D if entity-specific SLAs are needed.

---

## 5. C3 Recommendation

### Recommended Policy: OPTION C — Technical Retention Without User-Resolution SLA

### Five Concepts — Defined

#### 1. User Resolution SLA

**Policy:** NO ARCHITECTURAL SLA. Conflicts remain OPEN until the user resolves them. The product team may define an SLA later; the architecture supports it.

**Rationale:** Artificial deadlines cause data loss. A user on vacation should not have their mutations auto-discarded. The architecture should support resolution at the user's pace.

**Implementation:** No timeout on OPEN conflicts. No auto-resolve based on time. The conflict remains in the UI until the user takes action.

**Future extensibility:** If the product team defines an SLA, add a `resolution_deadline` column to `mobile_conflict_log` and a scheduled job that auto-resolves expired conflicts. The architecture supports this without changes.

#### 2. Conflict Data Retention

**Policy:** Conflict records are retained for a configurable period (default: 1 year). After retention, the record is ARCHIVED (not deleted).

**Rationale:** Audit trail must be preserved. Retention prevents unbounded storage growth. Archiving preserves the record for compliance without cluttering the active conflict list.

**Implementation:**
- `mobile_conflict_log.retention_expires_at` = `created_at + retention_period`
- Scheduled job runs daily, archives records where `retention_expires_at < NOW()`
- Archived records are moved to `mobile_conflict_log_archive` table (or flagged with `status = 'ARCHIVED'`)

**Retention period:** Configurable via `sanad.conflict.retention-days` (default: 365).

#### 3. Conflict Expiration

**Policy:** When a conflict record reaches its retention expiration, it is AUTO-RESOLVED with `resolution = 'SERVER_WINS'` and `status = 'EXPIRED'`.

**Rationale:** Prevents unbounded accumulation. The server's version is the authoritative state. The user had the entire retention period to resolve the conflict. If they didn't, the server wins.

**Implementation:**
1. Scheduled job finds conflicts where `retention_expires_at < NOW()` and `status = 'OPEN'`
2. For each conflict:
   - Set `resolution = 'SERVER_WINS'`
   - Set `resolved_at = NOW()`
   - Set `status = 'EXPIRED'`
   - Apply the server's version as the canonical state
   - Log the expiration in `platform_audit_logs`
3. The user's pending mutation is discarded (the server's version wins)
4. The device is notified on next sync that the conflict was auto-resolved

**Edge case:** If the user has been offline for the entire retention period and reconnects after expiration:
- The conflict is already EXPIRED
- The device receives the server's version
- The device's pending mutation is discarded
- The user is notified: "Your changes to [entity] were not applied because the conflict expired. The server's version was preserved."

#### 4. Operational Alerting

**Policy:** Monitor for conflicts older than a configurable threshold (default: 30 days). Alert the operations team.

**Rationale:** A conflict open for >30 days may indicate a problem (user doesn't know about it, UI bug, etc.). Operational visibility is important.

**Implementation:**
- Scheduled job runs daily
- Finds conflicts where `status = 'OPEN'` and `created_at < NOW() - alert_threshold`
- Sends alert to operations dashboard / notification system
- Alert includes: conflict_id, entity_type, entity_id, days_open, user_id

**Alert threshold:** Configurable via `sanad.conflict.alert-threshold-days` (default: 30).

#### 5. Cleanup

**Policy:** Archive expired conflicts. Never delete. Retain archived records for the full retention period from archival date.

**Rationale:** Audit trail must be preserved. Deletion destroys evidence. Archiving moves records out of the active set while preserving them for compliance.

**Implementation:**
- Daily scheduled job archives expired conflicts
- Archived records are retained for an additional configurable period (default: 1 year from archival)
- After archival retention: record is moved to cold storage or flagged for deletion (operator decision)

### Conflict Lifecycle States

```
DETECTED → OPEN → RESOLUTION_PENDING → RESOLVED
                ↓
              EXPIRED (after retention period)
                ↓
              ARCHIVED (after additional retention)
```

| State | Definition | Transition |
|-------|-----------|------------|
| **DETECTED** | Conflict identified by sync engine | → OPEN (immediately) |
| **OPEN** | Conflict presented to user, awaiting resolution | → RESOLUTION_PENDING (user chooses) |
| **RESOLUTION_PENDING** | User has chosen a resolution, awaiting server application | → RESOLVED (applied) |
| **RESOLVED** | Conflict resolved (user choice or auto-merge) | Terminal |
| **EXPIRED** | Conflict auto-resolved after retention period (server wins) | → ARCHIVED |
| **ARCHIVED** | Conflict record archived for audit | Terminal |

**Note:** REJECTED is NOT a separate state. A rejected mutation is handled at the HTTP level (412) and logged as a conflict with `status = 'OPEN'`. The conflict is not "rejected" — it is "detected."

### What Happens to Unresolved Conflicts — Critical Question

**Can the device continue syncing OTHER independent mutations while a conflict is pending?**

**Answer: YES.**

The conflict is on a SPECIFIC ENTITY. Other entities can be synced freely. The device should:

1. **Queue the conflicted mutation locally** — Do not discard it
2. **Continue syncing all other mutations** — No blocking
3. **Present the conflict to the user** — Show which entity has a conflict
4. **When resolved, sync the resolved mutation** — Apply user's choice

**This is critical for UX.** A single conflict should NOT block all syncing. The device operates normally for all non-conflicted entities.

**Implementation:**
- Device maintains a local conflict queue: `[{entity_type, entity_id, base_version, pending_mutation, conflict_info}]`
- On sync, the device sends all non-conflicted mutations
- Conflicted mutations are held locally until resolution
- The device receives fresh data for the conflicted entity (server's version)
- The user sees: "Contact 'John Smith' has a conflict. Your changes: [phone]. Server changes: [email]. Choose which to keep."

### Conflict Retention Schema (Proposed)

```sql
CREATE TABLE mobile_conflict_log (
    conflict_id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    device_id UUID NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    base_version BIGINT NOT NULL,
    client_mutation JSONB NOT NULL,
    server_version BIGINT NOT NULL,
    server_state JSONB NOT NULL,
    conflict_type VARCHAR(40) NOT NULL, -- 'VERSION_MISMATCH', 'FIELD_CONFLICT', 'STATE_CONFLICT'
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'RESOLUTION_PENDING', 'RESOLVED', 'EXPIRED', 'ARCHIVED'
    resolution VARCHAR(40), -- NULL until resolved: 'CLIENT_WINS', 'SERVER_WINS', 'MERGED', 'USER_CHOICE'
    resolved_by UUID,
    resolved_at TIMESTAMP WITH TIME ZONE,
    retention_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_mobile_conflict_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_mobile_conflict_log_status CHECK (status IN ('OPEN', 'RESOLUTION_PENDING', 'RESOLVED', 'EXPIRED', 'ARCHIVED')),
    CONSTRAINT ck_mobile_conflict_log_resolution CHECK (resolution IS NULL OR resolution IN ('CLIENT_WINS', 'SERVER_WINS', 'MERGED', 'USER_CHOICE'))
);

CREATE INDEX idx_mobile_conflict_log_entity ON (tenant_id, entity_type, entity_id);
CREATE INDEX idx_mobile_conflict_log_status ON (tenant_id, status, created_at);
CREATE INDEX idx_mobile_conflict_log_retention ON (retention_expires_at) WHERE status = 'OPEN';
```

### C3 Final Decision

```
C3_STATUS = DEFINED
C3_POLICY = OPTION C — Technical Retention Without User-Resolution SLA
C3_USER_RESOLUTION_SLA = NONE (product decision, not architectural)
C3_CONFLICT_RETENTION = 1 year (configurable)
C3_CONFLICT_EXPIRATION = AUTO-RESOLVE (server wins) after retention
C3_OPERATIONAL_ALERTING = Alert after 30 days open (configurable)
C3_CLEANUP = Archive (never delete)
C3_UNRESOLVED_CONFLICT = Device continues syncing other entities; conflicted entity queued locally
C3_LIFECYCLE = DETECTED → OPEN → RESOLUTION_PENDING → RESOLVED / EXPIRED → ARCHIVED
C3_BLOCKER = NO — architecture supports this without additional design
```

---

## 6. Security Implications

### C2 Security

| Aspect | Implication | Mitigation |
|--------|------------|------------|
| Token theft | Device offline >7d → token expired → re-auth required | Existing refresh token TTL |
| Stale data exposure | User sees outdated data while offline | Staleness detection on reconnect |
| Offline mutation queue | Local mutations stored on device | Device encryption (OS-level) |
| Re-authentication | User must enter credentials after 7d offline | Standard auth flow |

**No new security risks introduced by C2 policy.**

### C3 Security

| Aspect | Implication | Mitigation |
|--------|------------|------------|
| Conflict data exposure | Conflict records contain before/after state | Tenant isolation on all queries |
| Auto-resolve (server wins) | User's mutation discarded after retention | Audit trail preserved in conflict log |
| Operational alerts | Conflict data sent to ops dashboard | No PII in alerts (entity_id only) |
| Archived records | Retained for compliance | Access controlled by tenant_id |

**No new security risks introduced by C3 policy.**

---

## 7. Data Integrity Implications

### C2 Data Integrity

| Aspect | Implication | Mitigation |
|--------|------------|------------|
| Offline edits | User may edit stale data | Conflict detection on sync |
| Multi-device divergence | Two devices may edit same entity | Version-based conflict detection (existing) |
| Full resync data loss | Pending mutations discarded on full resync | User warned before full resync; local backup |

**No data integrity risks introduced by C2 policy.** The existing optimistic locking + ETag system handles conflicts.

### C3 Data Integrity

| Aspect | Implication | Mitigation |
|--------|------------|------------|
| Auto-resolve (server wins) | User's mutation discarded | Audit trail; user notified on next sync |
| Retention expiration | Conflict expires while user is offline | User notified on reconnect; server version preserved |
| Concurrent conflict resolution | Two users resolve same conflict | Version-based detection on resolution application |

**No data integrity risks introduced by C3 policy.** The server's version is always authoritative.

---

## 8. Operational Implications

### C2 Operational

| Aspect | Implication | Mitigation |
|--------|------------|------------|
| Storage growth | Local data grows while offline | Bounded by refresh token (7d) |
| Sync bandwidth | Full resync after 7d offline | Incremental sync for <7d offline |
| Monitoring | No visibility into offline duration | Device reports `last_sync_timestamp` |

**No operational risks introduced by C2 policy.**

### C3 Operational

| Aspect | Implication | Mitigation |
|--------|------------|------------|
| Conflict log growth | Records accumulate | Retention + archival |
| Scheduled jobs | Daily cleanup job required | `@Scheduled` + `scheduling.enabled=true` |
| Alert fatigue | Too many alerts | Configurable threshold (30d default) |
| Archived record storage | Long-term storage required | Cold storage or compressed archives |

**Operational requirements:**
1. Enable `scheduling.enabled=true` in production
2. Deploy daily cleanup job for conflict archival
3. Configure alerting threshold
4. Monitor conflict log table size

---

## 9. Deferred Decisions

The following decisions are DEFERRED — they can be made later without blocking G7 implementation:

| Decision | Current Default | When to Decide | Impact of Deferral |
|----------|----------------|----------------|-------------------|
| Conflict retention period | 1 year | Before production launch | None — default is safe |
| Alert threshold | 30 days | Before production launch | None — default is safe |
| User-resolution SLA | None | Product team decision | None — architecture supports any SLA |
| Entity-specific staleness rules | None | If requirements emerge | None — Option B is sufficient |
| Full resync threshold | After re-authentication | Before mobile app launch | None — default is safe |
| Archived record cold storage | None | When storage becomes concern | None — archival is sufficient |

**None of these deferments block G7 implementation.** The architecture supports all of them through configuration changes.

---

## 10. Implementation Impact

### What G7 MUST Implement

| Component | Effort | Priority |
|-----------|--------|----------|
| `mobile_conflict_log` table (schema above) | Low | HIGH |
| Conflict detection in sync push endpoint | Medium | HIGH |
| Conflict resolution UI on mobile | Medium | HIGH |
| Device state management (ONLINE/OFFLINE/REAUTH_REQUIRED) | Medium | HIGH |
| Staleness detection (If-Modified-Since header) | Low | MEDIUM |
| Full resync endpoint | Medium | MEDIUM |
| Conflict archival scheduled job | Low | LOW |

### What G7 Does NOT Need to Implement

| Component | Reason |
|-----------|--------|
| Hard maximum offline duration | Refresh token is the natural bound |
| User-resolution SLA | Product decision, not architectural |
| Entity-specific staleness rules | Option B is sufficient |
| Conflict expiration logic | Retention + archival handles this |
| Operational alerting | Can be added later via configuration |

### Implementation Sequence

1. **Phase 1 (Core):** `mobile_conflict_log` table + conflict detection in sync push
2. **Phase 2 (Device):** Device state management + staleness detection
3. **Phase 3 (Resolution):** Conflict resolution UI on mobile
4. **Phase 4 (Operations):** Archival job + alerting (can be deferred)

---

## Final Status

```
C2_STATUS = DEFINED
C2_POLICY = OPTION B — Staleness Detection with Natural Authentication Bound
C2_BLOCKER = NO

C3_STATUS = DEFINED
C3_POLICY = OPTION C — Technical Retention Without User-Resolution SLA
C3_BLOCKER = NO

TRUE_ARCHITECTURAL_BLOCKERS = 0

ADR_STATUS = REQUIRES_REVISION (update entity policy, add C2/C3 decisions)

G7_ARCHITECTURE_READY = YES
```

---

**END OF G7_C2_C3_ARCHITECTURAL_DECISION**
