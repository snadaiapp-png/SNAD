# FLYWAY-HISTORICAL-UPGRADE-EVIDENCE.md

**Environment:** isolated local PostgreSQL **17.6** (same major as production Supabase `snad-prod-fresh`), Flyway CLI **11.7.2** (project BOM version). Locations: `classpath:db/migration` + `db/vendor/postgresql` equivalents (filesystem), mirroring `application-prod.yml`. Production settings mirrored: `baseline-on-migrate=false`, `validate-on-migrate=true`, `out-of-order=false` unless explicitly stated per scenario. **No production contact; no `repair`/`baseline`/`clean` on any shared object.**

## Scenario A — Fresh installation (PASS)

| Item | Result |
|---|---|
| Database | fresh `postgres` rehearsal cluster |
| Chain | PR #1465 head `8e93beaec…` tree (migration files identical to `d0694a00f…`) — **250 migrations** (222 chain + 28 vendor) |
| `flyway migrate` | `Successfully applied 250 migrations … now at version v20261102.2` (exit 0) |
| `flyway validate` | `Successfully validated 250 migrations` (exit 0) |
| Checksums | `20261028.2=304457615`, `20261101.1=−144985054`, `20261102.1=858293278`, `20261102.2=237166129` |

## Scenario B — Historical upgrade from the production ledger state (PASS)

The authorized historical reference is the **live production ledger inspected read-only on 2026-11-01** (Issue #1462 comment): 242 rows, head `20261021.1`.

| Step | Result |
|---|---|
| Build state at `20261021.1` | `flyway -target=20261021.1 migrate` → 240 migrations applied, exit 0 |
| Populate | 1 tenant + 1 user + 1 `refresh_tokens` row (production-shaped data) |
| Upgrade | `flyway migrate` → **10 migrations applied** (20261021.2 → 20261102.2), exit 0, no `outOfOrder` needed (all versions > `20261021.1`) |
| `flyway validate` | exit 0 |
| Data preservation | pre-token row = 1 → post = **1**; tenant preserved; `auth_token_tenant_index` backfill mirrored the pre-existing token row (**1**) |
| Checksum equality | identical to Scenario A for all four late migrations |

## Scenario C — Out-of-order compatibility: DB at `20261031.1` WITHOUT `20261028.2` (PASS, with restrictions)

Built from the pre-#1458 chain (`2e88419cb…`, head `20261031.1`, no `20261028.2`/`20261101.1`), populated with a tenant+user+token row.

| Step | Result |
|---|---|
| Default config `migrate` | **exit 1** — `Detected resolved migration not applied to database: 20261028.2` (fail-closed; application startup against such DBs is blocked until remediation) |
| `outOfOrder=true` catch-up | exit 0 — applied 4 migrations (`20261028.2`, `20261101.1`, `20261102.1`, `20261102.2`) |
| `flyway validate` | exit 0 |
| Data preservation | token row preserved (**1**); index backfill (**1**) |
| Checksum equality | identical to A/B |

**Out-of-order verdict (per mandate):** `outOfOrder=true` is **NOT adopted globally**. It is evidenced **only** for the bounded population of databases that reached `20261031.1` without `20261028.2` (pre-#1458 fleet). Target databases = those with a `flyway_schema_history` head ≥ `20261022` and < `20261101` lacking row `20261028.2`; mechanism = one documented, per-database invocation with `FLYWAY_OUT_OF_ORDER=true` during an authorized maintenance window, immediately reverted (the setting must NOT persist in `application-prod.yml`); effect on ordering = two rows installed out of version order in `flyway_schema_history` (installed_rank ≠ version order) — checksums and future upgrade order are unaffected (proven by Scenario C validate + checksum equality). Production execution remains explicitly out of scope for this command.

## Scenario D — Backup restore (BLOCKED)

`HISTORICAL_BACKUP_REHEARSAL=BLOCKED` — no authorized production backup was made available to the isolated environment. The two Supabase access tokens pasted in chat were **refused** under the P0 security hold (and must be rotated). No synthetic database was substituted or labelled as a production restore.

## Legacy repeatable-migration note

`finalize hr backfill closure` (repeatable) re-applies after the tail migrations; present in every scenario and validates clean.
