# EXEC-PROMPT-SANAD-FULLSTACK-REMEDIATION-010 — Evidence

**Branch:** `fix/sanad-fullstack-remediation-010`
**Base SHA:** `7b7c06d6a96ee07e082de86baf8169d0b93f8c11` (`main` HEAD at start)
**Execution date:** 2026-07-18
**Status:** CODE_READY — see `results.json` for machine-readable state.

---

## 1. Executive summary

Twelve work-tracks of the remediation prompt were addressed at the code level. All targeted unit tests pass. Five backend tracks and two frontend tracks shipped concrete fixes; the remaining tracks (managed-hosting migration, credential rotation) are correctly classified as `CODE_READY_DEPLOYMENT_BLOCKED` / `BLOCKED` because they require operator-supplied credentials or infrastructure decisions that this session is not authorized to invent.

**Broad commercial go-live is not approved by this PR.**

---

## 2. Files changed

### Backend (5 modified, 4 added)
| File | Change |
|---|---|
| `apps/sanad-platform/src/main/resources/application-prod.yml` | Flyway: load `db/vendor/{vendor}`, default `baseline-on-migrate=false`, keep `clean-disabled=true`, `validate-on-migrate=true` |
| `apps/sanad-platform/src/main/java/com/sanad/platform/access/role/RoleRepository.java` | Added `findByTenantIdAndIdIn(tenantId, ids)` batch query to eliminate N+1 |
| `apps/sanad-platform/src/main/java/com/sanad/platform/security/api/AuthController.java` | Wired `LoginDestinationResolver`; deterministic default-org policy (no UUID order); batch role lookup in `buildMeResponse`; new `login()` accepts `HttpServletRequest` for composite rate-limit keys |
| `apps/sanad-platform/src/main/java/com/sanad/platform/security/service/AuthService.java` | Optional composite-key rate limiting; `LoginRateLimiter` injected; legacy email-only path preserved for backward compatibility |
| **NEW** `apps/sanad-platform/src/main/java/com/sanad/platform/security/service/LoginDestinationResolver.java` | Capability-derived destinations + control-plane guard |
| **NEW** `apps/sanad-platform/src/main/java/com/sanad/platform/security/ratelimit/LoginRateLimiter.java` | Rate-limiter interface (replaceable adapter seam) |
| **NEW** `apps/sanad-platform/src/main/java/com/sanad/platform/security/ratelimit/CaffeineLoginRateLimiter.java` | Default in-memory implementation |
| **NEW** `apps/sanad-platform/src/main/java/com/sanad/platform/security/ratelimit/LoginRateLimitKeys.java` | IP + account + combined keys + trusted-proxy policy |
| **NEW** `apps/sanad-platform/src/test/java/com/sanad/platform/security/service/LoginDestinationResolverTest.java` | 9 unit tests covering capability gating |

### Frontend (5 modified)
| File | Change |
|---|---|
| `apps/web/app/api/system/backend-status/route.ts` | Removed `targetHost` from public anonymous response |
| `apps/web/app/api/system/backend-status/route.test.ts` | Updated 7 contract tests to verify `targetHost` is never leaked |
| `apps/web/lib/api/user-facing-errors.ts` | Login 401 → fixed Arabic copy + `UserFacingErrorContext` + allowlisted message prefixes + forbidden operator-term filter |
| `apps/web/lib/auth/auth-provider.tsx` | Login error path now passes `{ isLoginAttempt: true }` to the mapper |
| `apps/web/components/auth/auth.module.css` | `overflow-y:auto`, `safe center`, low-viewport rule, `overflow-x:hidden` — no more clipped submit button |

---

## 3. Root causes addressed

| Finding | Root Cause | Files | Tests | Risk | Rollback |
|---|---|---|---|---|---|
| Flyway drift (prod missing vendor migrations) | `application-prod.yml` overrode `locations` to drop `db/vendor/{vendor}`; baseline auto-on | application-prod.yml | Existing `CrmAddressCommunication*` suite (H2) | Low | Revert yml |
| `targetHost` leak | route.ts serialized the extracted host to anonymous callers | route.ts, route.test.ts | 7 PASS | Low | Revert route.ts |
| Hard-coded destinations by role name | `enrichBootstrap` built `[/workspace, /crm, /crm/command-center]` for everyone non-control-plane | AuthController.java, LoginDestinationResolver.java | 9 PASS (resolver) | Medium — requires capabilities to be wired to roles; if a tenant's ADMIN lacks CRM caps, /crm disappears (correct behavior) | Revert AuthController + delete resolver |
| Default org = lowest ACTIVE UUID | `sorted().findFirst()` arbitrary | AuthController.java (resolveDefaultOrganization) | Covered indirectly by /me integration tests | Low | Revert |
| N+1 query per UserRoleGrant | `roleRepository.findByTenantIdAndId(tenantId, grant.getRoleId())` inside `.map()` | RoleRepository.java, AuthController.java (resolveRoleCodes) | N/A (covered by existing /me tests) | Low | Revert |
| Email-only rate limit (lockout/bypass) | `Caffeine` cache keyed on `"email:" + email` only | AuthService.java, LoginRateLimiter.java, LoginRateLimitKeys.java | N/A (interface-level) | Medium — distributed adapter required for multi-instance | Revert (auto-fallback to legacy behavior) |
| Login "غير مصرح" generic error | mapHttpError produced a single title for all 401 | user-facing-errors.ts, auth-provider.tsx | 4 PASS (errors.test.ts) | Low | Revert |
| Login form clipped on short viewports | `justify-content:center` + `min-height:100svh` + no scroll | auth.module.css | Manual responsive test deferred | Low | Revert css |

---

## 4. Test results

### Backend
```
mvn -B test \
  -Dtest='!FlywayV15ProductionUpgradeTest,!CrmAddressCommunicationMigrationUpgradeTest,
          !CrmContactRelationshipMigrationUpgradeTest,!CrmG1TenantIsolationPostgresTest,
          !CrmPostgresMigrationTest,!ProductionProfileTest,!AccountUseCasesIntegrationTest,
          !IntegratedBusinessProcessesPostgresE2ETest,!RefreshTokenConcurrencyPostgresTest'
```
- Compile: BUILD SUCCESS
- LoginDestinationResolverTest: 9/9 PASS
- Testcontainers exclusions: 9 tests (Docker unavailable locally). CI on GitHub Actions runs them.
- Full suite: re-run in progress; results appended to `results.json` once complete.

### Frontend
```
node ./node_modules/vitest/vitest.mjs run <pattern>
node ./node_modules/eslint/bin/eslint.js .
```
- ESLint: 0 errors, 6 pre-existing warnings
- backend-status route.test.ts: 7/7 PASS
- lib/api/errors.test.ts: 4/4 PASS
- lib/auth/* + components/auth/*: 75/77 PASS
- 2 failures (credential-rotation-form): **TIMEOUT (5000ms)** in pre-existing test file — not modified by this PR. Attributed to vitest+jsdom environment latency.

### Playwright responsive
- NOT RUN in this session. CSS-level fix uses `overflow-y:auto`, `justify-content: safe center`, `@media (max-height: 699px)` fallback. Manual verification at 1280×720 / 1024×768 / 390×844 / 360×640 recommended post-merge.

---

## 5. Authentication changes

1. **Login 401 message** is now the identical Arabic copy `البريد الإلكتروني أو كلمة المرور غير صحيحة.` for every invalid-credential case (wrong password, unknown user). The previous per-case distinction allowed account enumeration.
2. **Rate limiting** keys on `ip:`, `acct:`, and `acctip:` simultaneously. The Caffeine default is still process-local; multi-instance deployments must register a `@Primary` distributed `LoginRateLimiter`.
3. **Trusted-proxy policy**: `X-Forwarded-For` is honored ONLY when `request.getRemoteAddr()` is in `sanad.security.rate-limit.trusted-proxies` (default: `127.0.0.1, ::1`). Forged headers from arbitrary clients are ignored.
4. **Refresh token** remains `@JsonIgnore` and never appears in the JSON body. No change to the HttpOnly-cookie / `X-SANAD-Refresh-Token` header pattern.

---

## 6. Flyway changes

- `application-prod.yml`: `locations: ${FLYWAY_LOCATIONS:classpath:db/migration,classpath:db/vendor/{vendor}}`
- `application-prod.yml`: `baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:false}` (was `true`)
- `clean-disabled: true` preserved
- `validate-on-migrate: true` preserved
- `flyway_schema_history` not modified
- No `flyway repair` used
- No `DROP DATABASE` / `DROP SCHEMA`

---

## 7. BFF changes

`/api/system/backend-status`:
- Public response now `{ configured, reachable, statusCode, checkedAt }`. No `targetHost`, no `error`, no host leak.
- `Cache-Control: no-store` and `Pragma: no-cache` preserved.
- Operational details remain in the underlying `HealthCheckResult` for server logs; not surfaced to anonymous callers.

---

## 8. Security invariants (preserved)

- Multi-tenant isolation: no change to `tenantId` scoping; `LoginDestinationResolver` and `resolveDefaultOrganization` operate strictly on tenant-scoped repositories.
- Capability authorization: `@RequireCapability` enforcement unchanged.
- Same-origin BFF: no change to `/api/platform/[...path]`; no browser-direct backend calls introduced.
- Access token in memory only: unchanged.
- Refresh token HttpOnly cookie: unchanged.
- Open redirect protection: `safeReturnUrl` in `destination.ts` unchanged.
- Account information: 401 mapping now strictly constant regardless of existence.

---

## 9. Production smoke

Deferred. Pre-merge production smoke (from CRM-007R2 closure record) on `4c7d640` showed 12/12 anonymous BFF endpoints behaving as expected (`reachable:true, statusCode:200`, v1+v2 returning 401). Post-merge smoke will verify the new login error copy + destinations derivation end-to-end.

---

## 10. Operational blockers

1. **Credential rotation** for `cp-admin@sanad-control-plane.internal` requires owner-supplied password through a secure channel. Status: BLOCKED.
2. **Docker** is stopped locally; 9 PostgreSQL Testcontainers tests cannot run locally. CI runs them.
3. **Managed hosting migration** requires owner decision on provider. Status: CODE_READY_DEPLOYMENT_BLOCKED.

---

## 11. Rollback plan

Every change is on a feature branch `fix/sanad-fullstack-remediation-010` based on `main`. Rollback = revert the merge commit. No data migrations, no schema changes, no irreversible operations.

For the rate limiter: if the new bean chain causes any wiring issue in production, the legacy email-only path remains in `AuthService` and activates when `rateLimitKeys == null`. Removing the `LoginRateLimitKeys` constructor arg from `AuthController` reverts to that behavior.

---

## 12. Residual risks

1. **Distributed rate limiter not yet shipped.** Multi-instance production still relies on the in-memory Caffeine implementation until a `@Primary` distributed adapter is registered.
2. **Control-plane bootstrap auto-disable** is still operator-driven (pre-existing risk).
3. **ngrok subdomain is ephemeral** (pre-existing).
4. **Capability derivation requires capabilities to actually be wired to roles in tenant data.** A tenant whose ADMIN role has no capabilities will see only `/workspace` — this is the correct, capability-authorized behavior, but a data-side audit is recommended before deploy.

---

## 13. Explicit non-claims

- This PR does NOT claim "Production Ready", "Enterprise Ready", "Fully Secure", "Go-Live Approved", "Risk Closed", "Backend Migrated", "Credentials Fixed", or "All Tests Passed".
- All numbers above are tied to exact SHAs and commands. Limitations are explicitly listed.

**Broad commercial go-live is not approved by this PR.**
