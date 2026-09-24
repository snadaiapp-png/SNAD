# G7_FINAL_API_RUNTIME_VERIFICATION

**Date:** 2026-08-12
**Status: ⛔ API_RUNTIME = BLOCKED**

---

## 1. Runtime execution

No G7 endpoint was exercised over HTTP: Spring Boot cannot start because the Spring `ApplicationContext` requires a PostgreSQL datasource, which is inaccessible (see `G7_FINAL_RUNTIME_VERIFICATION.md`). The Phase-4 test matrix (unauthenticated / authenticated / wrong-tenant / invalid-token / malformed-payload / 412 / duplicate idempotency / invalid cursor / DB side-effect) was therefore **not executed**.

## 2. Static readiness (NOT runtime evidence)

- `mvnw compile` → EXIT 0.
- Controllers map: `GET /api/v2/mobile/sync/pull`, `POST /api/v2/mobile/sync/push`, `GET /api/v2/mobile/sync/status`, `GET /api/v2/mobile/conflicts`, `POST .../{id}/resolve`, `POST .../{id}/skip`.
- DEF-005: identity resolved via `TenantContextPort` (`SecurityContextHolder`, populated by existing `JwtAuthenticationFilter`) — so authenticated/wrong-tenant/invalid-token behavior is delegated to the platform security stack (`SecurityConfig` returns 401/403 before controllers run).
- DEF-004: `PushSyncService` column allowlist — `G7DefectFixesTest` proves injection blocked (2/2 PASS).
- Version/412 path, idempotency (SHA-256), cursor (Base64-URL) logic present in source.

## 3. Expected runtime contract (to validate once backend runs)

| Endpoint | Auth fail | Success | Version mismatch | Duplicate idem. |
|----------|-----------|---------|------------------|-----------------|
| `POST /sync/push` | 401/403 | 200 (per-mutation ACK) | 412 / 207 | `DUPLICATE` result |
| `GET /sync/pull` | 401/403 | 200 + ETag + cursor | — | — |
| `GET /sync/status` | 401/403 | 200 | — | — |
| `GET /conflicts` | 401/403 | 200 | — | — |
| `POST /conflicts/{id}/resolve|skip` | 401/403 | 200 | — | — |

## 4. Verdict

**API_RUNTIME = BLOCKED** — needs running backend + PostgreSQL; not a code defect.

*PostgreSQL unblock discovery (2026-08-12): completed — PG17 up on :5432 with `scram-sha-256` on all `pg_hba.conf` entries; no credential in shell/Windows-user/Windows-machine env or pgpass; Docker stopped; compose secrets absent. Access NOT established. Status unchanged: BLOCKED. See `G7_POSTGRES_RUNTIME_UNBLOCK_REPORT.md`.*
