# G7 Identity Document

## Canonical Name
**G7 — أساس الجوال / Mobile Offline Foundation**

## Source of Truth
`apps/web/app/crm/crm-execution-data.ts` (lines 129–137)

## Scope

| Area | Description |
|------|-------------|
| Mobile-optimized CRM entity APIs | REST endpoints returning reduced-payload subsets of CRM entities optimized for mobile consumption |
| Offline sync schema | Database schema for change tracking, cursors, device registration, and conflict logging |
| Client-side offline storage architecture | IndexedDB/SQLite local storage design for offline entity subsets |
| Sync engine architecture | Delta pull, outbox-based push, conflict detection, resolution, retry, and idempotency |
| Mobile-specific auth flow | Short-lived tokens, refresh flow, device binding, tenant-scoped authorization |
| Offline entity subset | Defined subset of CRM entities available for offline caching |

## Non-Scope

| Area | Reason |
|------|--------|
| Native mobile app UI | Not part of G7; handled by mobile app team |
| Push notifications | Deferred to G8 |
| Caller identification | Deferred to G8 |
| Real-time collaboration | Out of scope for mobile offline foundation |
| Offline-first full database replication | Not required; only entity subset |
| Background sync on iOS/Android | Platform-specific; not part of backend scope |

## Dependencies

| Dependency | Description |
|------------|-------------|
| G1 — Database & Multi-Tenant Foundation | Provides tenant isolation, schema management, database connection pooling |
| G3 — Core CRM Entities | Provides entity definitions, relationships, and business logic that G7 exposes via mobile APIs |

## Status
**NOT_STARTED** — on the Execution Board

## Naming Conflict Resolution

Four conflicting definitions of G7 were discovered during baseline analysis:

| Candidate | Description | Resolution |
|-----------|-------------|------------|
| G7-a | CI/CD Pipeline Hardening | Reassigned — conflicts with G7 Mobile Offline Foundation |
| G7-b | Quality Gates & Automated Testing | Reassigned — conflicts with G7 Mobile Offline Foundation |
| G7-c | Readiness Gate & Deployment Automation | Reassigned — conflicts with G7 Mobile Offline Foundation |
| G7-d | Central Workflow Engine | Reassigned — conflicts with G7 Mobile Offline Foundation |

**Decision:** All four conflicting definitions resolved in favor of **Mobile Offline Foundation**. The displaced features are reassigned to other gap numbers.

## Summary
G7 is the mobile offline foundation — the backend APIs, sync engine, offline storage architecture, and mobile auth flow that enable CRM entities to be used on mobile devices with intermittent or no connectivity.
