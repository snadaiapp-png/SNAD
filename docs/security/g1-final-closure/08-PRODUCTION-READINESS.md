# 08 — PROTECTED PRODUCTION READINESS — NON-DESTRUCTIVE VERIFICATION (Phase 6.1)

**PRODUCTION_VERIFICATION = BLOCKED** (no authorized production read channel available to the executor).

## What was prepared (query set frozen and ready to execute under authorization)

The following **read-only** verification set was written for immediate execution the moment a scoped production read credential is provided (all queries execute inside `BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE READ ONLY; … ROLLBACK;` where transactional, and never expose customer rows — counts, versions, flags only):

| Probe | Query (read-only) | Proves |
|---|---|---|
| DB identity | `SELECT current_database(), current_user, version();` | target database identity (stopping condition #1: must prove target identity before anything else) |
| PostgreSQL version | `SHOW server_version;` | major-version match with rehearsal (17.x) |
| Flyway ledger | `SELECT installed_rank, version, description, checksum, success, installed_by FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;` | current migration version + success states + who installed |
| Current version | `SELECT max(installed_rank) … WHERE success` + Flyway `info` (read-only mode) | pending-migration state |
| Owners | `SELECT c.relname, pg_get_userbyid(c.relowner) FROM pg_class c WHERE c.relname IN (<six tables>);` | ownership model |
| Roles | `SELECT rolname, rolsuper, rolbypassrls, rolcanlogin FROM pg_roles WHERE rolname IN ('postgres','sanad', …);` | runtime identity restriction |
| RLS config | `SELECT relname, relrowsecurity, relforcerowsecurity FROM pg_class …` + `SELECT … FROM pg_policies …` | ENABLE/FORCE + policies live |
| ACL | `information_schema.role_table_grants` for the runtime role | chain-grant parity with rehearsal |
| SECURITY DEFINER | `SELECT proname, prosecdef, proconfig FROM pg_proc … WHERE prosecdef` | definer surface + pinned search_path |
| Runtime identity (app side) | deployment config review (below) | which identity connects |
| Backup availability | provider-side evidence (owner-supplied) | restore readiness |
| Rollback readiness | owner runbook reference | recovery plan exists |

## Code-level runtime-identity evidence (verified at f95a30b6, no production access needed)

1. `apps/sanad-platform/docker-compose.prod.yml`: `POSTGRES_USER` is documented as **bootstrap superuser for first-time init/maintenance only**; `DATABASE_USERNAME` is a **required** variable ("restricted runtime role, e.g. sanad — NOT the bootstrap superuser") consumed by the application (`DATABASE_URL` + `DATABASE_USERNAME`); failing to set it aborts the compose file (`${DATABASE_USERNAME:?…}`).
2. `apps/sanad-platform/docker-compose.self-hosted.yml`: same required-variable pattern (PR #1466).
3. `apps/sanad-platform/docker/postgres-init/01-create-restricted-runtime-role.sh` (PR #1466): at first init, creates/normalizes the runtime role as `LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION` — **the self-hosted RLS bypass path (P1) is closed in code**; the historical finding (app connecting as the bootstrap superuser) is remediated at the composition level.
4. `render.yaml`: `DATABASE_USERNAME` injected from the dashboard; `FLYWAY_ENABLED=true` — on Render the app runs Flyway **as** the runtime identity (owner topology; ACL gaps invisible there, FORCE RLS still binding — see 03 §2.1, 04 F-01 topology note).
5. CI identity contract: `sanad` is asserted `NOSUPERUSER/NOCREATEDB/NOCREATEROLE/NOBYPASSRLS` on every run (`Assert least-privilege role contract`, post-merge workflow) — matching the rehearsal identity used in 03/04.

## Why execution is blocked (and must not be worked around)

- The only production credentials ever surfaced in this engagement were **plaintext secrets pasted in chat**; under the standing security policy those are treated as compromised (rotation required) and are **refused for use** by this executor.
- No scoped, non-compromised production read credential or owner-issued authorization arrived during the window.
- Per the command: stop, record, and continue only non-blocked independent work — done (03/04/05 are complete).

**Unblocking:** owner issues a scoped read-only credential (or executes the prepared probe set on the executor's behalf and returns the raw outputs). Then 08 converts to VERIFIED_PASS/FAIL with real values, and 09's change request can be drafted with a truthful `CURRENT_FLYWAY_VERSION`.
