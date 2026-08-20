# G7 RAW REQUIREMENTS REGISTER

> **Report ID:** G7-RAW-REQ-V1
> **Date:** 2026-08-12
> **Status:** DRAFT — NOT APPROVED
> **Purpose:** Every raw requirement extracted from every source, verbatim, before normalization or deduplication.

---

## 1. RAW REQUIREMENTS FROM SRC-03 (BASELINE)

These are the 39 individually enumerated requirements from the prior reconciliation.

| Raw ID | Category | Raw Text | Priority | Status |
|--------|----------|----------|----------|--------|
| G7-MOB-FR-001 | Functional | Mobile-optimized CRM entity APIs | P0 | MISSING |
| G7-MOB-FR-002 | Functional | Offline sync schema | P0 | MISSING |
| G7-MOB-FR-003 | Functional | Delta pull with cursor | P0 | MISSING |
| G7-MOB-FR-004 | Functional | Batch push with idempotency | P0 | MISSING |
| G7-MOB-FR-005 | Functional | Mobile-specific auth flow | P0 | MISSING |
| G7-MOB-FR-006 | Functional | Offline entity subset definition | P1 | MISSING |
| G7-MOB-FR-007 | Functional | Entity-level offline eligibility rules | P1 | MISSING |
| G7-MOB-FR-008 | Functional | Conflict resolution policy (12 classes) | P0 | MISSING |
| G7-MOB-FR-009 | Functional | Server-authoritative state management | P1 | MISSING |
| G7-MOB-FR-010 | Functional | Client-side offline storage architecture | P0 | MISSING |
| G7-MOB-NFR-001 | Non-Functional | Mobile API response time < 200ms | P1 | MISSING |
| G7-MOB-NFR-002 | Non-Functional | Offline data encrypted at rest | P0 | MISSING |
| G7-MOB-NFR-003 | Non-Functional | Client-side storage quota management | P2 | MISSING |
| G7-MOB-NFR-004 | Non-Functional | Network state detection and adaptive sync | P1 | MISSING |
| G7-MOB-NFR-005 | Non-Functional | Background sync scheduling | P2 | MISSING |
| G7-MOB-SEC-001 | Security | Offline data encryption strategy | P0 | MISSING |
| G7-MOB-SEC-002 | Security | Mobile token caching and refresh | P1 | MISSING |
| G7-MOB-SEC-003 | Security | Device registration and binding | P2 | MISSING |
| G7-MOB-SEC-004 | Security | Offline authorization enforcement | P1 | MISSING |
| G7-MOB-SEC-005 | Security | Sync transport security (HTTPS) | P1 | MISSING |
| G7-MOB-SYNC-001 | Sync | Client-side sync engine with queue | P0 | MISSING |
| G7-MOB-SYNC-002 | Sync | Delta sync with cursor-based pagination | P0 | MISSING |
| G7-MOB-SYNC-003 | Sync | Mutation queue with FIFO ordering | P1 | MISSING |
| G7-MOB-SYNC-004 | Sync | Cursor invalidation and full resync | P1 | MISSING |
| G7-MOB-SYNC-005 | Sync | Conflict detection (version-based) | P1 | MISSING |
| G7-MOB-SYNC-006 | Sync | Conflict resolution (auto-merge + manual) | P1 | MISSING |
| G7-MOB-SYNC-007 | Sync | Retry with exponential backoff | P2 | MISSING |
| G7-MOB-SYNC-008 | Sync | Idempotency for all sync mutations | P1 | MISSING |
| G7-MOB-DATA-001 | Data | Sync metadata tables (4 tables) | P0 | MISSING |
| G7-MOB-DATA-002 | Data | Change tracking columns on CRM tables | P0 | MISSING |
| G7-MOB-DATA-003 | Data | Client-side local storage schema | P1 | MISSING |
| G7-MOB-DATA-004 | Data | Sync audit trail (mobile_sync_log) | P2 | MISSING |
| G7-MOB-DATA-005 | Data | Conflict log (mobile_conflict_log) | P2 | MISSING |
| G7-MOB-TEST-001 | Test | Unit tests for sync engine components | P1 | MISSING |
| G7-MOB-TEST-002 | Test | Integration tests for pull sync | P1 | MISSING |
| G7-MOB-TEST-003 | Test | Integration tests for push sync | P1 | MISSING |
| G7-MOB-TEST-004 | Test | Integration tests for conflict resolution | P2 | MISSING |
| G7-MOB-TEST-005 | Test | End-to-end offline/online transition test | P2 | MISSING |
| G7-MOB-TEST-006 | Test | Performance tests for mobile APIs | P2 | MISSING |

**Subtotal: 39 raw requirements**

---

## 2. RAW REQUIREMENTS FROM SRC-10 (SYNC CONTRACT)

These are detailed behavioral requirements extracted from the sync contract truth document.

| Raw ID | Section | Raw Text |
|--------|---------|----------|
| SYNC-R-001 | 2.1 | Mutation envelope must include: idempotency_key, entity_type, entity_id, operation, base_version, payload, timestamp, device_id |
| SYNC-R-002 | 2.2 | base_version required, 0 for CREATE |
| SYNC-R-003 | 3.1 | Local storage: SQLite (mobile), IndexedDB (web/PWA) |
| SYNC-R-004 | 3.2 | Local schema mirrors server entity models exactly |
| SYNC-R-005 | 3.3 | Queue table: pending_mutations with state machine |
| SYNC-R-006 | 4.1 | Queue ordering: FIFO per entity type |
| SYNC-R-007 | 4.2 | Sequence_number per entity type |
| SYNC-R-008 | 5.1 | State machine: LOCAL_CHANGE → QUEUED → READY → SENT → ACKNOWLEDGED/CONFLICT/RETRYABLE_FAILURE/PERMANENT_FAILURE |
| SYNC-R-009 | 5.2 | State transitions defined with triggers |
| SYNC-R-010 | 6.1 | Pull request: last_sync_cursor, entity_types, tenant_id |
| SYNC-R-011 | 6.2 | Server processing: RLS, query by updated_at, entity_type filter |
| SYNC-R-012 | 6.3 | Pull response: entities, new_cursor, has_more |
| SYNC-R-013 | 6.4 | Client pull processing: upsert, update server_version, store cursor, clear local_modified |
| SYNC-R-014 | 6.5 | Pagination: max 1000 per page, has_more flag |
| SYNC-R-015 | 7.1 | Push request: batch of mutation envelopes |
| SYNC-R-016 | 7.2 | Server processing: idempotency check, version check, apply or conflict |
| SYNC-R-017 | 7.3 | Push response: per-mutation results with status, new_version, conflict details |
| SYNC-R-018 | 7.4 | Batch integrity: independent per mutation, partial failure allowed |
| SYNC-R-019 | 8.1 | Cursor: base64-encoded, last_sync_timestamp + last_sync_version |
| SYNC-R-020 | 8.2 | Cursor scope: one per entity type per device |
| SYNC-R-021 | 8.3 | Cursor invalidation: full resync on schema change, token expiry, explicit request |
| SYNC-R-022 | 9.1 | Acknowledgement flow: remove from queue, update local cache, clear local_modified |
| SYNC-R-023 | 9.2 | Acknowledgement granularity: per-mutation |
| SYNC-R-024 | 10.1 | Retry: 1s initial, 2x multiplier, 16s max, ±20% jitter, max 5 attempts |
| SYNC-R-025 | 10.3 | Retryable: 500, 502, 503, 408, network timeout |
| SYNC-R-026 | 10.4 | Non-retryable: 401, 403, 404, 412 |
| SYNC-R-027 | 11.1 | Ordering: FIFO per entity type, monotonically increasing sequence_number |
| SYNC-R-028 | 11.2 | Sequence gap detection: reject mutation with SEQUENCE_GAP_DETECTED |
| SYNC-R-029 | 12.1 | Idempotency: SHA-256 fingerprint, 24h retention, IdempotencyService |
| SYNC-R-030 | 15.1 | Conflict detection: server.entity.version != client.base_version |
| SYNC-R-031 | 15.2 | Conflict response: status, server_version, client_version, conflict_id, server_payload, client_payload |
| SYNC-R-032 | 16.1 | Conflict isolation: per-mutation, no batch blocking |
| SYNC-R-033 | 16.2 | Conflict logging: mobile_conflict_log |
| SYNC-R-034 | 17.1 | Auto-merge: non-conflicting fields, server wins on conflicts |
| SYNC-R-035 | 17.2 | Manual resolution: user selects keep server/client/merge |
| SYNC-R-036 | 17.3 | Server-authoritative fields: state transitions, financial data, system-generated |
| SYNC-R-037 | 18.1 | Delete conflict matrix: UPDATE vs DELETE → server wins; DELETE vs DELETE → idempotent |
| SYNC-R-038 | 19.1 | Full resync triggers: cursor invalid, token expiry, explicit request, server-detected long offline |
| SYNC-R-039 | 19.2 | Full resync procedure: clear cache, clear cursor, clear queue, pull all, rebuild |
| SYNC-R-040 | 20.1 | App restart recovery: reload queue, resume from QUEUED/READY |
| SYNC-R-041 | 20.2 | Network recovery: flush pending queue, FIFO order |
| SYNC-R-042 | 20.3 | Crash recovery: read queue from persistent storage, reset SENT to READY |
| SYNC-R-043 | 21.1 | Token config: refresh 7 days, access 15 min |
| SYNC-R-044 | 21.2 | Offline token: use cached, queue mutations, re-auth on reconnect |
| SYNC-R-045 | 22.1 | Entity types: CONTACT, ACCOUNT, LEAD, OPPORTUNITY, TASK, ACTIVITY, NOTE |
| SYNC-R-046 | 24.1-24.10 | 10 invariants must hold at all times |

**Subtotal: 46 raw requirements**

---

## 3. RAW REQUIREMENTS FROM SRC-11 (SECURITY GATE)

| Raw ID | Section | Raw Text | Status |
|--------|---------|----------|--------|
| SEC-R-001 | 2.1 | JWT access token: RS256, 15min TTL, memory-only storage | EXISTS |
| SEC-R-002 | 2.2 | Refresh token: opaque, 7-day TTL, rotation on use, revocation | EXISTS |
| SEC-R-003 | 2.3 | Mobile token management: cached tokens, expiry handling | NOT_DEFINED |
| SEC-R-004 | 2.4 | Re-authentication flow: refresh → re-auth → full resync | EXISTS |
| SEC-R-005 | 3.1 | RBAC enforcement on all sync operations | EXISTS |
| SEC-R-006 | 3.2 | Capability matrix: Viewer/User/Manager/Admin/Super Admin | EXISTS |
| SEC-R-007 | 3.3 | Sync-specific authorization: Pull=Viewer+, Push=User+, Conflict=User+ | NOT_DEFINED |
| SEC-R-008 | 4.1 | RLS on all CRM tables via PostgreSQL | EXISTS |
| SEC-R-009 | 4.2 | Tenant context flow: JWT → extract tenant_id → SET app.tenant_id → RLS | EXISTS |
| SEC-R-010 | 5.2 | RLS on new sync tables (4 tables) | NOT_IMPLEMENTED |
| SEC-R-011 | 6.1 | Entity ownership tracking: owner_id field | EXISTS |
| SEC-R-012 | 6.2 | Ownership rules: create/update/delete/transfer | EXISTS |
| SEC-R-013 | 6.4 | Ownership and sync: server-authoritative, non-owner blocked | NOT_DEFINED |
| SEC-R-014 | 7.2 | JWT validation: 6 checks (signature, expiry, issuer, audience, tenant, roles) | EXISTS |
| SEC-R-015 | 8.2 | Refresh token security: rotation, revocation, theft detection | EXISTS |
| SEC-R-016 | 9.1 | Device UUID: v4 on first launch, secure storage | NOT_IMPLEMENTED |
| SEC-R-017 | 9.2 | Device registry table: mobile_device_registry schema | NOT_IMPLEMENTED |
| SEC-R-018 | 9.3 | Device tracking: registration, update, query, revocation | NOT_IMPLEMENTED |
| SEC-R-019 | 10.1 | Idempotency key: UUID v4, SHA-256 fingerprint, 24h retention | EXISTS |
| SEC-R-020 | 11.3 | Idempotency on all push operations (Create/Update/Delete/Conflict) | EXISTS |
| SEC-R-021 | 12.1 | Audit logging: PlatformAuditWriter, before/after JSON | EXISTS |
| SEC-R-022 | 12.3 | Mobile sync audit: mobile_sync_log table | NOT_IMPLEMENTED |
| SEC-R-023 | 13.1 | Client-side encryption: SQLCipher or OS-level, NOT_DEFINED | NOT_DEFINED |
| SEC-R-024 | 13.4 | Encryption key management: 256-bit, device-specific, not backed up | NOT_DEFINED |
| SEC-R-025 | 14.1 | Transport security: HTTPS TLS 1.2+, HSTS, cert pinning recommended | EXISTS |
| SEC-R-026 | 14.2 | Sync endpoint security: JWT + RBAC + ownership on all endpoints | NOT_DEFINED |
| SEC-R-027 | 15.3 | Cross-tenant sync blocked by RLS | EXISTS |
| SEC-R-028 | 16.2 | Offline token handling: cache, reconnect check, re-auth | NOT_DEFINED |
| SEC-R-029 | 17.1 | Duplicate mutation handling: idempotency dedup, 24h window | EXISTS |
| SEC-R-030 | 18.3 | Authorization audit: authorization_logs table | NOT_DEFINED |

**Subtotal: 30 raw requirements**

---

## 4. RAW REQUIREMENTS FROM SRC-06 (GAP REGISTER)

| Raw ID | Gap ID | Raw Text | Severity | Priority |
|--------|--------|----------|----------|----------|
| GAP-R-001 | GAP-001 | Mobile Sync API Layer: 9 mobile APIs implemented | BLOCKER | P0 |
| GAP-R-002 | GAP-002 | Sync Metadata Schema: 4 tables with RLS | BLOCKER | P0 |
| GAP-R-003 | GAP-003 | Change Tracking Columns: all CRM tables have version + updated_at | BLOCKER | P0 |
| GAP-R-004 | GAP-004 | Conflict Resolution Policy: ADR approved and implemented | BLOCKER | P0 |
| GAP-R-005 | GAP-005 | Sync Engine (Client-Side): full sync engine with queue, retry, conflict handling | BLOCKER | P0 |
| GAP-R-006 | GAP-006 | Offline Data Encryption: all offline data encrypted at rest | BLOCKER | P0 |
| GAP-R-007 | GAP-007 | Offline Authorization: token caching with expiry check | HIGH | P1 |
| GAP-R-008 | GAP-008 | Conflict Detection + Resolution: all 12 conflict classes handled | HIGH | P1 |
| GAP-R-009 | GAP-009 | Mobile Entity APIs: optimized mobile payloads, <200ms | HIGH | P1 |
| GAP-R-010 | GAP-010 | Test Suite: 26 tests covering all scenarios | HIGH | P1 |
| GAP-R-011 | GAP-011 | Device Registry: device registration and binding | MEDIUM | P2 |
| GAP-R-012 | GAP-012 | Sync Log: all sync operations logged | MEDIUM | P2 |
| GAP-R-013 | GAP-013 | Offline Entity Subset Definition: complete entity offline requirements | MEDIUM | P1 |
| GAP-R-014 | GAP-014 | Performance Budget: mobile API <200ms | MEDIUM | P1 |

**Subtotal: 14 raw requirements**

---

## 5. RAW REQUIREMENTS FROM SRC-04 (FORENSIC REPORT)

| Raw ID | Raw Text |
|--------|----------|
| FORENSIC-R-001 | G7 must be resolved to a single canonical definition |
| FORENSIC-R-002 | Mobile Offline Foundation is the selected interpretation |
| FORENSIC-R-003 | 12 conflict classes must be implemented |
| FORENSIC-R-004 | Conflict policy must be approved (ADR-G7-001) |
| FORENSIC-R-005 | Server-authoritative fields must be defined per entity |
| FORENSIC-R-006 | Auto-merge must handle non-conflicting field changes |
| FORENSIC-R-007 | Manual resolution must be available for conflicting fields |
| FORENSIC-R-008 | Delete conflicts must favor server |

**Subtotal: 8 raw requirements**

---

## 6. RAW REQUIREMENTS FROM SRC-05 (TRUTH REPORT)

| Raw ID | Raw Text |
|--------|----------|
| TRUTH-R-001 | 9 new mobile APIs must be implemented |
| TRUTH-R-002 | 4 new sync metadata tables must be created |
| TRUTH-R-003 | Change tracking columns on all CRM tables |
| TRUTH-R-004 | Client-side sync engine with queue, retry, conflict handling |
| TRUTH-R-005 | Conflict resolution with 12 conflict classes |
| TRUTH-R-006 | Mobile auth with token caching |
| TRUTH-R-007 | Offline data encryption strategy |
| TRUTH-R-008 | RLS must extend to sync tables |
| TRUTH-R-009 | Sync metrics and observability |
| TRUTH-R-010 | 26 tests must be implemented |
| TRUTH-R-011 | ADR-G7-001 must be approved |
| TRUTH-R-012 | Mobile framework must be selected |

**Subtotal: 12 raw requirements**

---

## 7. RAW REQUIREMENTS FROM SRC-08 (ACCEPTANCE GATES)

| Raw ID | Gate | Raw Text | Status |
|--------|------|----------|--------|
| GATE-R-001 | GATE-01 | G7 identity locked and agreed | PASS |
| GATE-R-002 | GATE-02 | All requirements reconciled and baselined | PASS |
| GATE-R-003 | GATE-03 | Architecture stable and approved (ADR) | CONDITIONAL |
| GATE-R-004 | GATE-04 | Data model defined and approved (4 tables) | PASS |
| GATE-R-005 | GATE-05 | API contracts defined (9 APIs) | PASS |
| GATE-R-006 | GATE-06 | Client storage architecture defined | NOT_STARTED |
| GATE-R-007 | GATE-07 | Mobile auth flow defined | PASS |
| GATE-R-008 | GATE-08 | Delta pull API functional | NOT_STARTED |
| GATE-R-009 | GATE-09 | Mutation queue functional | NOT_STARTED |
| GATE-R-010 | GATE-10 | Idempotency verified for all operations | NOT_STARTED |
| GATE-R-011 | GATE-11 | Batch push API functional | NOT_STARTED |
| GATE-R-012 | GATE-12 | Conflict detection and resolution functional (12 classes) | NOT_STARTED |
| GATE-R-013 | GATE-13 | All security requirements met | NOT_STARTED |
| GATE-R-014 | GATE-14 | RLS enforced on all sync tables | NOT_STARTED |
| GATE-R-015 | GATE-15 | Sync operations observable | NOT_STARTED |
| GATE-R-016 | GATE-16 | All tests pass | NOT_STARTED |
| GATE-R-017 | GATE-17 | Recovery scenarios handled | NOT_STARTED |
| GATE-R-018 | GATE-18 | All gates pass (production readiness) | NOT_STARTED |

**Subtotal: 18 raw requirements**

---

## 8. AGGREGATE SUMMARY

| Source | Raw Count | Category |
|--------|-----------|----------|
| SRC-03 (Baseline) | 39 | Enumerated requirements |
| SRC-10 (Sync Contract) | 46 | Behavioral specifications |
| SRC-11 (Security) | 30 | Security requirements |
| SRC-06 (Gaps) | 14 | Gap requirements |
| SRC-04 (Forensic) | 8 | Analysis requirements |
| SRC-05 (Truth) | 12 | Truth requirements |
| SRC-08 (Gates) | 18 | Acceptance criteria |
| **TOTAL RAW** | **167** | |

**NOTE:** This is the GROSS count before deduplication. Many requirements appear in multiple sources with different wording. The normalization phase will collapse these to the TRUE requirement count.

---

*Generated: 2026-08-12*
*Phase 2 of G7 Requirements Reconciliation*
