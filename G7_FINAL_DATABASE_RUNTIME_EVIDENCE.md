# G7_FINAL_DATABASE_RUNTIME_EVIDENCE

**Date:** 2026-08-12 · **Status: ⛔ DATABASE_RUNTIME = BLOCKED** · No secrets printed.

## Access determination (PHASE 1–4 completed)
- Server: PostgreSQL 17 running on `localhost:5432` (`postgresql-x64-17`, `AUTO_START`, `NetworkService`).
- `pg_hba.conf`: **`scram-sha-256` on every connection method** (no trust/peer/md5).
- Credential search (all authorized sources): shell env, Windows **User** env, Windows **Machine** env, `.env*`, `%APPDATA%\postgresql\pgpass.conf`, `%USERPROFILE%\.pgpass`, project scripts → **none**.
- **Admin elevation = FALSE** → cannot stop the service / acquire the data-dir lock / run single-user maintenance to provision `sanad` (PHASE 2 not executable).
- Docker daemon **stopped**; compose requires 4 absent secrets → PHASE 3 not executable.
- ⇒ `POSTGRES_REACHABLE_WITH_AUTH = FALSE` → PHASE 4 BLOCKED.

## Runtime checks (all NOT EXECUTED)
| Check | Status |
|-------|--------|
| Connect to `sanad` db | BLOCKED |
| Flyway full chain incl. `V20260812_1`, `V20260812_2` | BLOCKED |
| Tables `mobile_device_registry` / `mobile_sync_cursor` / `mobile_sync_log` / `mobile_conflict_log` | BLOCKED |
| FKs / UNIQUE / CHECK (C1–C12) / indexes | BLOCKED |
| RLS ENABLED + policies on `app.tenant_id` | BLOCKED |
| Fail-closed (no tenant context → no rows) | BLOCKED |
| `sync_version` / `last_synced_at` columns | BLOCKED |
| `fn_update_sync_version()` trigger fires | BLOCKED |
| Tenant A ↔ Tenant B isolation | BLOCKED |

Static artifacts exist (migration files, DEF-007 GUC alignment) but **static SQL ≠ runtime PASS**.

## Unblocking (requires human/elevated action)
Provide the PG superuser password **or** run an elevated shell to provision `sanad` role/db **or** start Docker + set the 4 compose secrets. Then `mvn spring-boot:run` → Flyway → the table/RLS/trigger assertions above.
