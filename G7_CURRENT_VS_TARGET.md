# G7 Current vs Target State Comparison

Current vs Target state comparison for all G7 components.

| Component | Current State | Target State | Gap | Priority |
|-----------|--------------|-------------|-----|----------|
| Mobile APIs | 10 existing (v1/v2, full payload) | 9 new mobile APIs (v2, optimized) | 9 APIs missing | P0 |
| Sync Metadata | No sync tables | 4 tables (device_registry, sync_cursor, sync_log, conflict_log) | 4 tables missing | P0 |
| Change Tracking | version BIGINT exists, updated_at partial | version + updated_at on all CRM tables | updated_at may be missing | P0 |
| Sync Engine | No sync engine | Client-side sync engine with queue, retry, conflict | Entire engine missing | P0 |
| Conflict Resolution | Server-side rejection (HTTP 412) | Extended for mobile: 12 conflict classes, resolution strategies | Mobile extension missing | P0 |
| Offline Storage | No offline storage | SQLite/IndexedDB with encryption | Entire storage missing | P0 |
| Mobile Auth | Standard JWT (web) | Mobile JWT with caching, refresh, offline support | Mobile extension missing | P1 |
| Device Identity | No device tracking | Device registry with registration, binding | Entire system missing | P2 |
| Observability | Basic audit trail | Sync-specific metrics, alerts, dashboards | Sync observability missing | P2 |
| Testing | 208 tests (no G7-specific) | 26 G7-specific tests | All G7 tests missing | P1 |
| Documentation | Baseline docs exist | Complete API docs, runbook, architecture docs | Partial documentation | P2 |
