# SANAD Execution Baseline

**Program**: SANAD-FDP-001 — EXEC-PROMPT-001
**Date**: 2026-06-24
**Repository SHA**: 635ebe3
**Auditor**: Principal Software Architect

---

## Executive Summary

The SANAD Business Operating System repository has been audited at commit `635ebe3`. The backend compiles and passes all 364 tests (11 skipped). The frontend builds successfully but has 3 failing unit tests and 6 lint errors. Authentication, multi-tenancy, and RBAC are implemented with significant depth, but several security and production-readiness gaps were identified. The system is **conditionally ready** for continued development but has blocking issues for commercial go-live.

---

## Application Status

### Backend (apps/sanad-platform)

| Aspect | Status | Evidence |
|--------|--------|----------|
| Framework | Spring Boot 3.3.5 / Java 17 target | pom.xml |
| Runtime Java | OpenJDK 21.0.11 | `java -version` |
| Build (compile) | PASS | `mvn clean compile` — 0 errors |
| Build (test) | PASS | `mvn test` — 364 run, 0 failures, 11 skipped |
| Build (package) | PASS | `mvn package` produces executable JAR |
| Application startup | PASS (local) | H2 profile starts successfully |
| Health endpoint | PASS (production) | `/actuator/health` returns `{"status":"UP"}` |
| Liveness endpoint | PASS (production) | `/actuator/health/liveness` returns UP |
| Readiness endpoint | PASS (production) | `/actuator/health/readiness` returns UP |
| Swagger exposure | PASS (production) | Returns 404 — correctly disabled |
| Env endpoint | PASS (production) | Returns 404 — correctly disabled |

### Frontend (apps/web)

| Aspect | Status | Evidence |
|--------|--------|----------|
| Framework | Next.js 16.2.9 / React 19.2.4 / TypeScript 5.9 | package.json |
| Build | PASS | `npm run build` — compiled in 3.5s, all pages generated |
| Type checking | PASS | Built into `next build` — no type errors |
| Lint | FAIL | 6 errors, 3 warnings — `<a>` elements, setState in effects |
| Unit tests | PARTIAL | 151 tests: 148 pass, 3 fail |
| RTL/Arabic | PASS | `<html lang="ar" dir="rtl">` |
| Production deployment | PASS | Vercel serves 200 at snad-app.vercel.app |

---

## Database Status

| Aspect | Status | Evidence |
|--------|--------|----------|
| Engine | PostgreSQL (Supabase, Frankfurt) | application-prod.yml |
| Migration framework | Flyway | V1–V14 SQL migrations |
| Migration count | 14 | V1 through V14, all SQL |
| Duplicate versions | None | All version numbers unique |
| Destructive operations | None | No DROP, TRUNCATE, or unchecked DELETE |
| UUID consistency | PASS | All UUID columns use native `uuid` type |
| Foreign keys | PASS | Named constraints, composite FKs for tenant-scoped refs |
| Indexes | PASS | Appropriate indexes for query patterns |
| Schema validation | PASS | `jpa.ddl-auto=validate` in production |
| **Gap: V15** | PARTIAL | V14 seeds capabilities only. ADMIN role created at runtime by `CredentialBootstrapService`. No migration seeds role-capability assignments. If bootstrap is skipped or new capabilities are added, ADMIN role may have zero capabilities. |

---

## Authentication Status

| Feature | Status | Evidence |
|---------|--------|----------|
| Login (email) | PASS | `AuthService.login()` with BCrypt(10) |
| Login (email+tenantId) | PASS | 409 on ambiguous email, tenant picker |
| Logout | PASS | Revokes all refresh tokens + increments session_version |
| Token refresh | PASS | Rotated refresh tokens via HttpOnly cookie |
| Password hashing | PASS | BCrypt strength 10 |
| Password reset | PASS | One-time SHA-256 hashed tokens |
| Forced password change | PASS | `credential_rotation_required` flag enforcement |
| Session revocation | PASS | `session_version` mechanism invalidates all JWTs |
| Refresh rotation | PASS | Replay detection revokes entire token family |
| Rate limiting | PARTIAL | Caffeine in-memory cache — non-distributed |
| Audit logging | PARTIAL | SLF4J text logs — no structured audit framework |
| 401/403 separation | PASS | Custom entryPoint (401) and accessDeniedHandler (403) |
| Disabled users blocked | PASS | `AccountInactiveException` at login and refresh |
| No secrets in logs | PASS | No tokens/passwords in log statements |
| **Gap: CORS wildcard** | FAIL | `https://*.vercel.app` allows any Vercel deployment |

---

## Multi-Tenancy Status

| Aspect | Status | Evidence |
|--------|--------|----------|
| TenantId in all queries | PASS | All repositories filter by tenantId |
| TenantId source | PASS | Derived from JWT claim (trusted) |
| Request tenantId validation | PASS | JwtAuthenticationFilter compares against JWT |
| Cross-tenant data access | PASS | Blocked by filter + repository scoping |
| IDOR protection | PASS | TenantId mismatch → 403 |
| `/me` returns correct tenant | PASS | Returns membership and tenant from JWT |
| **Gap: UserMembershipController** | PARTIAL | No `@RequireCapability` — any authenticated user can list memberships |
| **Gap: findAllByEmail** | PARTIAL | Cross-tenant query for email-only login (by design) |
| **Gap: Test config bypass** | PARTIAL | `SecurityPermitAllTestConfig` bypasses JWT filter — not used in production |

---

## RBAC Status

| Aspect | Status | Evidence |
|--------|--------|----------|
| Role definitions | PASS | Tenant-scoped roles with unique (tenant_id, code) constraint |
| Capability definitions | PASS | 19 platform-level capabilities seeded in V14 |
| Role-capability mapping | PASS | `RoleCapability` entity with triple unique constraint |
| User-role grants | PASS | `UserRoleGrant` with org-scoped and tenant-wide grants |
| Endpoint enforcement | PARTIAL | `@RequireCapability` on 43/44 non-auth endpoints |
| Missing enforcement | FAIL | `UserMembershipController` has no `@RequireCapability` |
| 401/403 separation | PASS | Proper separation via custom handlers |
| Positive tests | PASS | Capability evaluation tests exist |
| Negative tests | PASS | Missing capability → 403 tests exist |
| ADMIN bootstrap | PARTIAL | Runtime only via `CredentialBootstrapService` — no migration |

---

## CI/CD Status

| Aspect | Status | Evidence |
|--------|--------|----------|
| CI on push | PASS | `ci.yml` triggers on push/PR to main |
| Backend tests in CI | PASS | `mvn test` in ci.yml |
| Frontend tests in CI | PASS | `npm test` in web-ci.yml |
| Full build in CI | PASS | Both Maven and Next.js builds run |
| Pipeline fails on test failure | PASS | No `continue-on-error` on test steps |
| False-success patterns | PASS | All `|| true` usages are curl status extraction |
| Test reports | PARTIAL | Surefire reports generated but not uploaded as artifacts |
| Production smoke test | PASS | Multiple smoke workflows exist |
| Secrets in files | FAIL | `smoke-test.yml` uses plaintext `admin_password` input |
| SHA verification | PARTIAL | Only `production-release.yml` verifies SHA; `backend-deploy.yml` does not |
| Rollback procedure | PARTIAL | Exists in `production-release.yml`; not in `backend-deploy.yml`; never tested |

---

## Deployment Status

| Aspect | Status | Evidence |
|--------|--------|----------|
| Backend (Render) | PASS | Health UP, liveness UP, readiness UP |
| Frontend (Vercel) | PASS | HTTP 200, content served |
| Render plan | NOT PRODUCTION | Free tier — cold starts, sleep on inactivity |
| Connection pool | NOT PRODUCTION | max=5 — adequate for pilot, not production scale |
| Docker security | PASS | Non-root user, minimal JRE, health check |
| CORS (production) | PARTIAL | Locked to `snad-app.vercel.app` in render.yaml, but wildcard in SecurityConfig |
| Auto-deploy | PASS | Disabled — manual deployment only |

---

## Key Risks

1. **CORS wildcard** — Any Vercel preview deployment can make credentialed requests to the API
2. **Non-distributed rate limiting** — In-memory Caffeine cache resets per instance in horizontal scaling
3. **Access token in localStorage** — Vulnerable to XSS exfiltration
4. **No V15 migration for RBAC seeding** — ADMIN role capabilities depend on runtime bootstrap
5. **Plaintext admin password in CI** — Visible in GitHub Actions logs
6. **Free-tier infrastructure** — Not production-grade for commercial operations
7. **Frontend lint failures** — 6 errors including React anti-patterns
8. **3 failing frontend tests** — API config and auth flow test drift
9. **No frontend middleware** — All route protection is client-side only
10. **Rollback never tested** — Documented but never executed

---

## Proposed Decision

**CONDITIONALLY READY** — The platform is functionally complete and secure for pilot/development use, but has blocking issues for commercial go-live. The authentication and RBAC foundations are solid; the gaps are in production hardening, CI/CD security, and frontend code quality.
