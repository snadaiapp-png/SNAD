# G7 Architecture Final Baseline

Architecture baseline for G7.

## CURRENT ARCHITECTURE

- Spring Boot backend (Java)
- PostgreSQL with RLS
- Next.js web app with BFF proxy
- JWT auth + RBAC
- Optimistic locking (version + ETag)
- Idempotency framework
- Audit trail

## TARGET ARCHITECTURE (G7)

- Same backend + new sync API layer
- Same database + 4 new sync tables with RLS
- Mobile client (TBD framework) with:
  - Local storage (SQLite/IndexedDB)
  - Sync engine (pull/push/conflict/retry)
  - Auth manager (token caching)
  - Connectivity detector
- New components:
  - PullSyncService (server)
  - PushSyncService (server)
  - ConflictDetectionService (server)
  - ConflictResolutionService (server)
  - SyncEngine (client)
  - LocalStorage (client)
  - MutationQueue (client)
  - AuthManager (client)

## DESIGN PRINCIPLES

1. Extend, don't replace -- build on existing infrastructure
2. Server-authoritative -- server is source of truth
3. Optimistic concurrency -- version-based conflict detection
4. Idempotent operations -- safe to retry
5. Tenant isolation -- RLS on all new tables
6. Incremental implementation -- can be built and tested incrementally

## MODULE DEPENDENCIES

- backend/sync depends on backend/core (existing CRM)
- backend/conflict depends on backend/sync
- mobile/sync depends on mobile/storage
- mobile/storage depends on mobile/core
