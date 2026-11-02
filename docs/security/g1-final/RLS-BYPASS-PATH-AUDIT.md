# RLS-BYPASS-PATH-AUDIT.md

**Question:** does any application-reachable connection path reach PostgreSQL with a role that can bypass RLS (superuser or BYPASSRLS)? Role possession of BYPASSRLS is not, by itself, a vulnerability — reachability from app paths is the criterion.

## 1. Connection paths inventoried

| Path | Role used | Bypass? | Verdict |
|---|---|---|---|
| Managed deployments (`render.yaml` → Supabase Postgres, prod project `snad-prod-fresh`) | `DATABASE_USERNAME` supplied by operator; live read-only inspection (2026-11-01, recorded on Issue #1462) verified runtime `sanad`: `rolsuper=false`, `rolbypassrls=false`; ledger at `20261021.1` | No | `CONFIRMED_SAFE_WITH_EVIDENCE` (historical inspection; re-verification at certification time required) |
| Self-hosted `docker-compose.prod.yml` | `POSTGRES_USER: ${DATABASE_USERNAME:?}` — the initdb superuser **is** whatever `DATABASE_USERNAME` is set to, and the backend connects with the same variable. Therefore the app pool always connects as a **superuser** (any name), including the name `sanad` (the docker image creates `POSTGRES_USER` as superuser — the chain's "restricted sanad" precondition would then fail migrations, i.e. the composition cannot even reach a healthy state without an out-of-band restricted role, and if the operator instead keeps `postgres`, the app runs as superuser) | **Yes — by construction** | `CONFIRMED_VULNERABILITY (deployment path)` — app-plane RLS is advisory in every self-hosted compose deployment; classification **P1** (requires choosing this deployment path; the app still scopes queries by tenant in SQL and sets the GUC, so RLS is a missing second layer, not the only one) |
| `docker-compose.self-hosted.yml` | same pattern (`POSTGRES_USER: ${DATABASE_USERNAME…}`) | Same | folded into the P1 above |
| Non-prod `docker-compose.yml` | dev convenience; port exposed by intent | n/a | out of production scope |
| Supabase Data API roles (`anon`/`authenticated`/`service_role`) | Not configured anywhere in backend config/code (only an SNI socket-factory reference to Supabase); token-table and index ACLs REVOKE ALL from `PUBLIC` and from each API role where it exists (`V20261020_1/_2`, `V20261101_1`) | No reachable path | `CONFIRMED_SAFE_WITH_EVIDENCE` in the rehearsal catalog (roles absent) + prod comments (denied); **re-verify on live prod before certification** |
| SECURITY DEFINER functions | 12 functions, all with pinned `search_path`; the two token/login index syncs are `pg_catalog`-first; direct invocation of trigger functions as `sanad` → 42501 | No | `CONFIRMED_SAFE_WITH_EVIDENCE` |
| Test roles vs runtime roles | CI provisions the restricted `sanad` plus a `NOSUPERUSER/NOBYPASSRLS` probe role — matches the runtime model; no test-only superuser path into prod artifacts was found | No | `VERIFIED` for the reviewed workflows |

## 2. Reachability argument for the P1

Exploitation does not require a remote attacker: any single app-plane SQL flaw (injection, missing tenant predicate, mis-scoped query) becomes cross-tenant when the connection is a superuser, because RLS — the enforced second layer for 150+ tables — is advisory to superusers. The same flaw under the restricted role is contained by FORCE RLS policies (proven 57/57 by the six-table matrix).

## 3. Minimal remediation (implemented in branch `fix/selfhosted-separate-db-identities-20261102`)

1. `docker-compose.prod.yml` / `docker-compose.self-hosted.yml`: separate bootstrap identity (`POSTGRES_USER`, default `postgres`, its own password) from the runtime identity (`DATABASE_USERNAME`, default `sanad`); mount `docker/postgres-init/01-create-restricted-runtime-role.sh` which provisions `sanad LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB` with `DATABASE_PASSWORD` on first init.
2. Regression evidence produced locally (same binary versions): init script SQL executed on a fresh PostgreSQL 17.6 instance → role attributes verified; full chain applies as migrator `postgres`; six-table matrix re-run **as `sanad`** against that topology passes — proving the new composition shape yields exactly the restricted runtime model the migrations demand.
3. Operators of already-initialized volumes must run the documented one-time role-creation step (compose init scripts only run on empty data directories) — called out in the PR description and runbook comment.
