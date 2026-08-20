# G7 Implementation Package 02 — Server Sync Controllers

> **Status:** COMPLETE
> **Requirements:** API-003, API-004, API-005, API-007, API-008, API-009, SYNC-017
> **Files Changed:** 10 Java files
> **Tests:** API contract tests, integration tests

---

## Files

| File | Purpose |
|------|---------|
| `PullSyncController.java` | GET /api/v2/mobile/sync/pull |
| `PushSyncController.java` | POST /api/v2/mobile/sync/push |
| `SyncStatusController.java` | GET /api/v2/mobile/sync/status |
| `ConflictController.java` | GET/POST /api/v2/mobile/conflicts |
| `PullSyncService.java` | Delta pull logic with cursor management |
| `PushSyncService.java` | Batch push with per-mutation ACK |
| `ConflictService.java` | Conflict detection and classification |
| `DeltaSyncRequest.java` | Pull request model |
| `DeltaSyncResponse.java` | Pull response model |
| `PushSyncRequest.java` | Push request model |
| `PushSyncResponse.java` | Push response model |
| `SyncStatusResponse.java` | Status response model |
| `ConflictResponse.java` | Conflict response model |

## API Endpoints

| Method | Path | Requirement |
|--------|------|-------------|
| GET | /api/v2/mobile/sync/pull | API-003 |
| POST | /api/v2/mobile/sync/push | API-004 |
| GET | /api/v2/mobile/sync/status | API-005 |
| GET | /api/v2/mobile/conflicts | API-007 |
| POST | /api/v2/mobile/conflicts/:id/resolve | API-008 |
| POST | /api/v2/mobile/conflicts/:id/skip | API-009 |

## Key Features

- ETag-based version validation (HTTP 412 on mismatch)
- Per-mutation ACK (APPLIED/REJECTED/CONFLICT/DUPLICATE)
- Idempotency via SHA-256 fingerprint (24h retention)
- 12 conflict classes (C1-C12) per ADR-G7-001
- Conflict isolation (one conflict doesn't block others)
- Tenant isolation via RLS context

## Verification

- [ ] All endpoints respond correctly
- [ ] ETag validation works (HTTP 412 on version mismatch)
- [ ] Per-mutation ACK returns correct status
- [ ] Idempotency prevents duplicate mutations
- [ ] Conflict detection classifies correctly
- [ ] Tenant isolation enforced
