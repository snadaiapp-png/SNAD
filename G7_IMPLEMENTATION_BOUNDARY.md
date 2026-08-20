# G7 IMPLEMENTATION BOUNDARY

> **Report ID:** G7-IMPL-BOUNDARY-V1
> **Date:** 2026-08-11
> **Mode:** IMPLEMENTATION PLANNING / READ-ONLY
> **No code modified. No commits made.**

---

## TRACK A — UNBLOCKED NOW

These components have NO dependency on the ADR and can proceed immediately.

| # | Component | Description | Dependencies | Estimated Scope |
|---|-----------|-------------|-------------|-----------------|
| A1 | Connectivity Detection | Monitor network status (online/offline) | None | Small — single service/hook |
| A2 | Local Persistence | SQLite/MMKV/IndexedDB setup | None | Medium — schema design + setup |
| A3 | Local Data Layer | CRUD operations on local storage | A2 | Medium — mirror server entity models |
| A4 | Pull-Only Sync (Full Entities) | Fetch entities from server, store locally | A3 | Large — pagination, delta detection |
| A5 | Push-Only Entities (Note Archive) | POST note archive to server | A3 | Small — single endpoint |
| A6 | Authentication Plumbing | Token storage, refresh flow | None | Medium — secure storage + refresh |
| A7 | Sync Telemetry | Metrics for sync operations | None | Small — counters + latency |
| A8 | Retry Infrastructure | Exponential backoff logic | None | Small — configurable retry policy |
| A9 | Device/Session Foundation | Device ID, session management | A6 | Small — UUID + storage |

### TRACK A Implementation Notes

**A1 — Connectivity Detection:**
- Use `navigator.onLine` (web) or platform-specific APIs (React Native)
- Emit events on status change
- Do NOT rely solely on `navigator.onLine` — it can be inaccurate
- Implement heartbeat/ping to verify actual connectivity

**A2 — Local Persistence:**
- SQLite for React Native (via `react-native-sqlite-storage` or `drizzle`)
- IndexedDB for web/PWA (via `idb` or `Dexie.js`)
- MMKV for key-value storage (tokens, settings)
- Schema must mirror server entity models for offline CRUD

**A3 — Local Data Layer:**
- CRUD interface matching server API shape
- Local entity models with `version`, `updated_at`, `updated_by`
- Offline queue for pending mutations
- Conflict detection metadata (base_version per entity)

**A4 — Pull-Only Sync:**
- Full entity fetch on first sync
- Delta detection using `updated_at` or `version`
- Pagination for large datasets
- Store server version locally for future conflict detection

**A5 — Push-Only Entities:**
- Note archive: `PATCH /api/v1/crm/notes/{id}/archive`
- No conflict possible (server accepts)
- Idempotent (archive is idempotent)

**A6 — Authentication Plumbing:**
- Secure token storage (Keychain/Keystore/EncryptedSharedPreferences)
- Token refresh flow (existing `/api/v1/auth/refresh`)
- Offline token cache with expiry check
- Session persistence across app restarts

**A7 — Sync Telemetry:**
- Sync operation count (pull/push)
- Sync latency (p50, p95, p99)
- Conflict count
- Error count
- Queue depth

**A8 — Retry Infrastructure:**
- Exponential backoff: 1s, 2s, 4s, 8s, 16s (max 5 attempts)
- Configurable per operation type
- Dead-letter queue for permanent failures
- Retry only on transient errors (network, timeout, 500)

**A9 — Device/Session Foundation:**
- Device ID: UUID generated on first launch, stored in secure storage
- Session management: tie device to user session
- Device registration endpoint (future: `/api/v2/mobile/device/register`)

---

## TRACK B — WAITING FOR ADR ACCEPTANCE

These components require the ADR to be ACCEPTED before implementation.

| # | Component | Blocked By | Prerequisites | Estimated Scope |
|---|-----------|-----------|---------------|-----------------|
| B1 | Conflict-Aware Push | ADR ACCEPTED | ADR approval | Large — version check + response handling |
| B2 | Auto-Merge Logic | ADR ACCEPTED + Entity Policy | ADR approval + entity policy finalization | Large — three-way merge algorithm |
| B3 | Conflict Log Table | ADR ACCEPTED | ADR approval | Small — Flyway migration |
| B4 | Conflict Resolution API | ADR ACCEPTED + B3 | ADR approval + schema | Medium — CRUD for conflict resolution |
| B5 | Conflict UI Contract | ADR ACCEPTED + B4 | ADR approval + API | Medium — API contract for conflict UI |
| B6 | Delete Conflict Handling | ADR ACCEPTED + B1 | ADR approval + push | Medium — delete-specific conflict logic |
| B7 | State Transition Conflict | ADR ACCEPTED + B1 | ADR approval + push | Medium — state machine validation |
| B8 | Activity Offline Writes | ADR ACCEPTED | ADR approval (policy corrected) | Medium — Activity offline CRUD |
| B9 | Pipeline/Tags/Custom Fields Offline | ADR ACCEPTED | ADR approval (policy corrected) | Medium — offline CRUD for reference data |

### TRACK B Implementation Notes

**B1 — Conflict-Aware Push:**
- Extend sync push endpoint with version check
- Return conflict details when version mismatch detected
- Log conflicts to `mobile_conflict_log`
- Support batch push with per-mutation results

**B2 — Auto-Merge Logic:**
- Three-way merge using BASE_VERSION as ancestor
- Field-by-field comparison
- Auto-merge non-conflicting fields
- Flag conflicting fields for user resolution
- Apply to: Account, Contact, Task, Activity

**B3 — Conflict Log Table:**
- Flyway migration for `mobile_conflict_log`
- 18 columns as defined in TASK 7
- RLS policies for tenant isolation
- Indexes for query performance

**B4 — Conflict Resolution API:**
- `GET /api/v2/mobile/conflicts` — list unresolved conflicts
- `GET /api/v2/mobile/conflicts/{id}` — get conflict details
- `POST /api/v2/mobile/conflicts/{id}/resolve` — resolve conflict
- `POST /api/v2/mobile/conflicts/{id}/skip` — skip conflict

**B5 — Conflict UI Contract:**
- API contract for conflict resolution UI
- Return local change, server change, timestamp, user, fields
- Return resolution options (keep server, keep local, merge, skip)

**B6 — Delete Conflict Handling:**
- Client Update + Server Delete → CONFLICT
- Client Delete + Server Update → CONFLICT
- Client Delete + Server Delete → APPLIED (idempotent)
- Client Update + Server Hard Delete → REJECTED

**B7 — State Transition Conflict:**
- Server-authoritative state machine validation
- Client state change rejected if server state differs from base
- Terminal states cannot be reversed
- Task has SQL-level state guards

**B8 — Activity Offline Writes:**
- Activity is mutable (update, complete)
- Offline CRUD with version tracking
- Auto-merge for non-conflicting fields
- State transitions require user resolution

**B9 — Pipeline/Tags/Custom Fields Offline:**
- All three are mutable (not read-only as originally claimed)
- Conservative approach: Reject + User Resolution for all
- Pipeline changes affect opportunities
- Tag changes affect tagged entities
- Custom field changes affect data entry

---

## TRACK C — REQUIRES ADDITIONAL ARCHITECTURAL DECISION

| # | Component | Why Additional Decision Needed | Impact |
|---|-----------|-------------------------------|--------|
| C1 | Multi-device conflict detection | How to handle same entity modified on 3+ devices simultaneously | High — affects conflict resolution complexity |
| C2 | Offline duration limits | How long can a device stay offline before forced full re-sync | Medium — affects data freshness and storage |
| C3 | Conflict resolution SLA | How quickly must conflicts be resolved before data is purged | Medium — affects data retention |
| C4 | Cross-entity conflict (parent-child) | Account owner changes while Contact is being added | High — affects relationship integrity |
| C5 | Custom field value conflicts | How to handle custom field value merges (dynamic schema) | Medium — affects custom field offline support |

### TRACK C Decision Requirements

**C1 — Multi-device conflict detection:**
- Device A modifies Contact (v5 → v6)
- Device B modifies same Contact (v5 → v7)
- Device C modifies same Contact (v5 → v8)
- All three push simultaneously
- How to resolve? Last successful push wins? All three conflict?

**C2 — Offline duration limits:**
- Device offline for 1 hour: partial sync sufficient
- Device offline for 24 hours: full re-sync recommended
- Device offline for 7 days: forced full re-sync + re-authentication
- What are the thresholds?

**C3 — Conflict resolution SLA:**
- Unresolved conflicts older than 7 days: auto-resolve with server wins?
- Unresolved conflicts older than 30 days: purge from conflict log?
- What are the retention and resolution deadlines?

**C4 — Cross-entity conflict:**
- Account.owner changes from User A to User B
- Contact.account_id is being set to this Account by User A (offline)
- On sync: Contact's account_id references an Account whose owner changed
- Is this a conflict? How to resolve?

**C5 — Custom field value conflicts:**
- Custom field definitions are dynamic (created at runtime)
- Client has custom field value for entity X
- Server's custom field definition changed (label, type, required)
- How to merge custom field values when the schema itself changed?

---

## SUMMARY

```
TRACK A = READY (9 components, can start immediately)
  A1: Connectivity Detection
  A2: Local Persistence
  A3: Local Data Layer
  A4: Pull-Only Sync
  A5: Push-Only Entities
  A6: Authentication Plumbing
  A7: Sync Telemetry
  A8: Retry Infrastructure
  A9: Device/Session Foundation

TRACK B = READY_AFTER_ADR (9 components, waiting for ADR approval)
  B1: Conflict-Aware Push
  B2: Auto-Merge Logic
  B3: Conflict Log Table
  B4: Conflict Resolution API
  B5: Conflict UI Contract
  B6: Delete Conflict Handling
  B7: State Transition Conflict
  B8: Activity Offline Writes
  B9: Pipeline/Tags/Custom Fields Offline

TRACK C = 5 ITEMS (require additional architectural decisions)
  C1: Multi-device conflict detection
  C2: Offline duration limits
  C3: Conflict resolution SLA
  C4: Cross-entity conflict
  C5: Custom field value conflicts
```

---

**END OF G7 IMPLEMENTATION BOUNDARY**
