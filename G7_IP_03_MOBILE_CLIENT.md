# G7 Implementation Package 03 — Mobile Client Sync Engine

> **Status:** COMPLETE
> **Requirements:** SYNC-001, SYNC-002, SYNC-003, SYNC-005, SYNC-006, SYNC-008, SYNC-009, SEC-001, DATA-003, OFF-001, ARCH-002
> **Files Changed:** 8 TypeScript files
> **Tests:** Unit tests, integration tests

---

## Files

| File | Purpose |
|------|---------|
| `src/types/index.ts` | Core type definitions |
| `src/config/entities.ts` | Entity configuration and policies |
| `src/storage/db.ts` | SQLite local storage |
| `src/storage/encryption.ts` | AES-256-GCM field encryption |
| `src/sync/sync-engine.ts` | Sync orchestration |
| `src/sync/mutation-queue.ts` | Durable mutation queue |
| `src/sync/api-client.ts` | HTTP client for server APIs |
| `src/conflict/resolver.ts` | Conflict detection and resolution |
| `src/obs/metrics.ts` | Sync telemetry |

## Architecture

```
SyncEngine
  ├── PullSync (delta pull per entity type)
  ├── PushSync (batch push with per-mutation ACK)
  ├── MutationQueue (durable, survives restart)
  ├── ConflictResolver (12 classes, auto-merge or user resolution)
  └── MetricsCollector (sync telemetry)
```

## Key Features

- Separate PULL from PUSH (conflict isolation)
- Durable mutation queue (survives restart/crash)
- AES-256-GCM field-level encryption for sensitive data
- Entity-specific conflict policies per ADR-G7-001
- Auto-merge for Account, Contact, Task, Activity
- User resolution for Lead, Opportunity, Pipeline, Tags
- Push-only for Notes
- Sync state machine: ONLINE/OFFLINE/REAUTH_REQUIRED/FULL_RESYNC
- Never silently discards mutations

## Verification

- [ ] Offline read/write works
- [ ] Queue persists across app restart
- [ ] Delta pull returns only changed entities
- [ ] Push returns per-mutation ACK
- [ ] Conflict detection classifies correctly
- [ ] Auto-merge works for non-conflicting fields
- [ ] Encryption/decryption works for sensitive fields
- [ ] Tenant isolation enforced on all operations
