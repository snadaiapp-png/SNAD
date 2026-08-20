# G7 Implementation Package 01 — Database Schema

> **Status:** COMPLETE
> **Requirements:** DATA-001 (Sync Tables), DATA-002 (Change Tracking)
> **Files Changed:** 2 Flyway migrations
> **Tests:** Migration execution, RLS verification

---

## Files

| File | Purpose |
|------|---------|
| `V20260812_1__create_mobile_sync_tables.sql` | Creates 4 sync metadata tables with RLS |
| `V20260812_2__add_sync_columns_to_crm_entities.sql` | Adds change tracking to 7 entity tables |

## Tables Created

1. `mobile_device_registry` — Device registration per tenant/user
2. `mobile_sync_cursor` — Per-device, per-entity sync cursors
3. `mobile_sync_log` — Sync operation audit trail
4. `mobile_conflict_log` — Conflict records with full payloads

## Columns Added

- `last_synced_at` (TIMESTAMPTZ) on 7 entity tables
- `sync_version` (BIGINT) on 7 entity tables
- Auto-increment trigger on UPDATE

## RLS Policies

- All 4 new tables have tenant isolation via `app.current_tenant_id`

## Verification

- [ ] Flyway migration runs successfully
- [ ] All 4 tables created
- [ ] RLS policies enforce tenant isolation
- [ ] sync_version auto-increments on UPDATE
- [ ] Existing data unaffected
