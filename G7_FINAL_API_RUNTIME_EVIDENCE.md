# G7_FINAL_API_RUNTIME_EVIDENCE

**Date:** 2026-08-12 · **Status: ⛔ API_RUNTIME = BLOCKED** · No secrets printed.

## Runtime access
Spring Boot cannot start: the `ApplicationContext` requires the PostgreSQL datasource, which is not accessible (see `G7_FINAL_DATABASE_RUNTIME_EVIDENCE.md`). No HTTP request was executed against any G7 endpoint.

## Endpoint runtime matrix (NOT EXECUTED — all BLOCKED)
| Endpoint | auth fail | valid | wrong tenant | bad payload | dup idem. | 412 conflict | DB effect |
|----------|-----------|-------|--------------|-------------|-----------|--------------|-----------|
| `GET /api/v2/mobile/sync/pull` | BLOCKED | BLOCKED | BLOCKED | BLOCKED | — | — | BLOCKED |
| `POST /api/v2/mobile/sync/push` | BLOCKED | BLOCKED | BLOCKED | BLOCKED | BLOCKED | BLOCKED | BLOCKED |
| `GET /api/v2/mobile/sync/status` | BLOCKED | BLOCKED | BLOCKED | — | — | — | BLOCKED |
| `GET /api/v2/mobile/conflicts` | BLOCKED | BLOCKED | BLOCKED | — | — | — | BLOCKED |
| `POST .../conflicts/{id}/resolve` | BLOCKED | BLOCKED | BLOCKED | BLOCKED | — | — | BLOCKED |
| `POST .../conflicts/{id}/skip` | BLOCKED | BLOCKED | BLOCKED | — | — | — | BLOCKED |

## Source readiness (NOT runtime evidence)
`mvnw compile` EXIT 0 · controllers mapped · DEF-005 wired to `TenantContextPort` (so 401/403 come from `SecurityConfig`/`JwtAuthenticationFilter`) · DEF-004 column allowlist proven by `G7DefectFixesTest` (2/2). Compilation/Controller-existence is explicitly NOT accepted as runtime PASS.

## Unblocking
Same as database evidence (DB + backend start), then curl each endpoint asserting status semantics + DB side-effects.
