# SANAD Backend — Render crash-loop authentication incident (2026-11-10)

**Status:** ROOT EXCEPTION VERIFIED; credential repair and production restart **NOT EXECUTED**. **No Docker**.
**Affected service:** `sanad-backend` / `srv-d8ragqkm0tmc73bviqq0`, running image `ghcr.io/snadaiapp-png/snad-backend:48322d0bae97082d69ee143b7af26962d6e14ef8`.
**Evidence location:** Render workspace `Snad`, application logs (private) and `server_failed/nonZeroExit=1` events, on 2026-11-10.
**Owner issue:** [#1496](https://github.com/snadaiapp-png/SNAD/issues/1496). G1 amended certification remains `NOT_ACTIVE`.

## Confirmed immediate failure

Seven application-error events between **08:48:20Z–09:00:30Z**, and seven between **00:10:34Z–00:22:25Z**, have the same root:
```
org.postgresql.util.PSQLException: FATAL: password authentication failed for user "postgres"
```
`FlywayAutoConfiguration` requests JDBC metadata during Spring context creation; authentication is rejected and Spring aborts startup. Repeated Render `server_failed` events report `nonZeroExit=1`. These findings establish **authentication rejection**, not a successful Flyway migration, database schema failure, OOM, or cold start.

**Unresolved:** Which exact environment variable supplied the effective `postgres` credential, whether password rotation invalidated the old value, and whether Flyway's separate datasource or the runtime datasource was responsible. The logs do not establish those facts. No credential values or connection strings were read or stored.

## Code-level remediation (proposed; does not by itself recover service)

1. `RenderDatabaseUrlConverter.java`: when `DATABASE_URL` or `RENDER_DATABASE_URL` is raw `postgresql://...`, prefer explicitly provided credentials in this order: `SPRING_DATASOURCE_{USERNAME,PASSWORD}`, then `DATABASE_{USERNAME,PASSWORD}`, then URL-embedded credentials. Previously the raw `DATABASE_URL` path always selected the embedded credentials and overwrote separately rotated values in the highest-priority Spring property source.
2. `application-prod.yml`: `FLYWAY_ENABLED` defaults to `false` for normal production runtime; only the protected, independent migration operator may enable it under a **separate change**. The currently-running **old image** still defaults to true; this source change is not active until an approved deployment.
3. Regression tests use **synthetic credentials only**, and require a real Maven test execution to be treated as PASS. No Docker or production DB in tests.

**Important:** The credential-precedence defect is proven in source code but is **not proven as the underlying source of the currently rejected `postgres` password**. Proper production credential synchronization is still mandatory.

## Minimal supervised recovery runbook (future authorization required)

- **Preflight: (READ-ONLY operator inspection, never publish secrets)** identify configured input *names* in Render: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `RENDER_DATABASE_URL`, `FLYWAY_ENABLED`, `FLYWAY_USER`, `FLYWAY_PASSWORD`, `FLYWAY_URL`. Determine *effective precedence* from source/config and verify whether the explicitly configured Flyway user is `postgres`; verify host, SSL and database name via protected management tooling, not public logs. Be aware JDBC URL may itself contain credentials. Existing Render secrets must be inspected only by authorized operators in their vault; avoid copying them into tickets.
- **Authorization:** a production data/credential owner approves rotation or correction of runtime and/or Flyway credentials and an exact maintenance window. Preserve prechange config metadata and rollback **without storing old secret values in plaintext**. Do not use GitHub Actions to rotate production credentials.
- **Role contract:** production **runtime** must authenticate as least-privilege `sanad` and be separated from `sanad_migrator`. Runtime should not use privileged `postgres`. Recheck account existence/privileges in the target DB with read-only admin metadata. Do not create roles, grant DDL or silently enable Flyway merely to resolve this incident.
- **Credential synchronization:** owner securely changes/revokes stale credentials at the database provider, then updates all applicable Render connection settings consistently (including raw URLs with embedded userinfo, environment precedence and any separate Flyway identity). Verify the new credential through an authorized isolated read-only `SELECT 1` under the exact runtime identity. Do not send secret material to ChatGPT, GitHub comments or CI logs. Set `FLYWAY_ENABLED=false` explicitly on the current image to prevent application-initiated schema changes when authorized to update its configuration. Note that this does **not** fix a bad runtime password on its own.
- **Approved release (separate):** only after a staging rehearsal on the actual Flyway baseline and a Docker-free source/artifact release authorization; avoid the existing Docker-based image publishing workflow. Do not deploy a newer Backend to production merely because the old login fails.
- **Live verification:** verify `/actuator/health` responds HTTP 200, sustained process uptime without `server_failed`, runtime account and Flyway disabled, smoke on login/refresh/logout and critical tenant workflows, and no unauthorized schema changes. Record exact release SHA/digest, logs (redacted), test cases and timestamp. Fail closed if any item fails.
- **Certificate:** original G1 certificate remains historical proof, and proposed amendment A01 stays `NOT_ACTIVE` until all G1 R/D/B/S gates close. Payments remain `OFF`.

## Acceptance ledger

| Criterion | Current outcome |
|---|---|
| Repeated Render `PSQLException: password authentication failed for user "postgres"` | **VERIFIED**, from private Render logs |
| Underlying effective environment variable/secret identified | **UNVERIFIED** |
| Code fix proposed on isolated GitHub branch | **IMPLEMENTED ON BRANCH**, not deployed |
| Focused Java/Maven regression test | **PENDING actual terminal result** |
| Production credential corrected | **NOT EXECUTED** |
| Production app healthy on approved image | **NOT VERIFIED** |
| Production Flyway/RLS correction or security amendment activation | **NOT AUTHORIZED / NOT ACTIVE** |
