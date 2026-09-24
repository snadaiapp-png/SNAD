# G7_FINAL_DATABASE_VERIFICATION

**Date:** 2026-08-12
**Status: ⛔ DATABASE_RUNTIME = BLOCKED**

---

## 1. Runtime execution

| Step | Status | Evidence |
|------|--------|----------|
| Connect to PostgreSQL | **FAILED (no credential)** | `pg_isready` accepts; auth is SCRAM/password; no credential in env/`.pgpass`/repo |
| Apply G7 Flyway migrations | **NOT EXECUTED** | Requires DB connection |
| Verify `mobile_device_registry` / `mobile_sync_cursor` / `mobile_sync_log` / `mobile_conflict_log` | **NOT EXECUTED** | Requires DB |
| Verify `sync_version` / `last_synced_at` columns | **NOT EXECUTED** | Requires DB |
| Verify `fn_update_sync_version()` trigger fires | **NOT EXECUTED** | Requires DB |
| Verify FKs / CHECK constraints / indexes | **NOT EXECUTED** | Requires DB |
| Verify RLS enabled + policies + tenant isolation + fail-closed | **NOT EXECUTED** | Requires DB |

## 2. Static artifacts present (NOT runtime evidence)

- `V20260812_1__create_mobile_sync_tables.sql` — 4 tables, FKs→`tenants`/`users`, CHECK (conflict_class C1–C12), indexes, RLS enabled, policies on `app.tenant_id` (fail-closed; DEF-007).
- `V20260812_2__add_sync_columns_to_crm_entities.sql` — `sync_version`/`last_synced_at` on 6 CRM tables, indexes, `fn_update_sync_version()` PL/pgSQL trigger on 6 tables.

Per governance, **static SQL ≠ runtime PASS**. These remain BLOCKED until executed on PostgreSQL.

## 3. What PASS would require (unblock steps)

1. DB connection (see `G7_FINAL_RUNTIME_VERIFICATION.md §5`).
2. `mvn spring-boot:run` → Flyway applies the full chain.
3. `psql` assertions: tables exist; `SET app.tenant_id='T1'; SELECT … FROM mobile_sync_log;` → cross-tenant invisible (fail-closed); `UPDATE crm_accounts SET name='x'; SELECT sync_version;` → incremented; constraint violations on bad `conflict_class`.

## 4. Verdict

**DATABASE_RUNTIME = BLOCKED** — infrastructure access, not a code defect.

---

## Appendix — PostgreSQL unblock discovery (2026-08-12, completed)

Authorized discovery confirms the blocker is **credential/auth, not code**:
- Server: **PostgreSQL 17** running on `:5432` (`postgresql-x64-17`; PG18 service stopped).
- `pg_hba.conf`: **`scram-sha-256` on every connection method** (local + 127.0.0.1/32 + ::1/128). No trust/peer/md5 path.
- No credential in: shell env, Windows **User** env, Windows **Machine** env, `.env*` files, `pgpass`/`pgpass.conf`.
- No admin access → cannot provision `sanad` role/db (PHASE 4 not executable).

Guardrails honored: `pg_hba.conf` not modified, SCRAM not disabled, no password guessing/reset, no H2, no RLS disable. Full detail: `G7_POSTGRES_RUNTIME_UNBLOCK_REPORT.md`.
