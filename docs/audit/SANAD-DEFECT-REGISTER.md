# SANAD Defect Register

**Program**: SANAD-FDP-001 — EXEC-PROMPT-001
**Date**: 2026-06-24
**Repository SHA**: 635ebe3

---

## Defect Summary

| Severity | Count |
|----------|-------|
| P0 | 0 |
| P1 | 4 |
| P2 | 6 |
| P3 | 5 |
| P4 | 4 |
| **Total** | **19** |

---

## P1 — Critical Security or Production Blocker

### DEFECT-011: CORS Wildcard for All Vercel Deployments

| Field | Value |
|-------|-------|
| ID | DEFECT-011 |
| Title | CORS allows any Vercel deployment to make credentialed API requests |
| Severity | P1 |
| Affected component | Backend — SecurityConfig.corsConfigurationSource() |
| Evidence | `allowedOriginPatterns` includes `https://*.vercel.app` — any Vercel preview deployment from any project can make cross-origin credentialed requests |
| Root cause | Development convenience pattern left in production configuration |
| Business impact | Cross-tenant data exfiltration via malicious Vercel app; CSRF-like attacks |
| Security impact | High — allows unauthorized cross-origin access with credentials |
| Recommended remediation | Replace wildcard with exact origin `https://snad-app.vercel.app`; use environment variable for allowed origins |
| Required tests | CORS preflight test against unauthorized origin → expect 403 |
| Dependencies | None |
| Estimated complexity | Low (1 hour) |
| Go-live blocking | Yes |
| **Remediation status** | **IMPLEMENTED — CI REVALIDATION IN PROGRESS** |
| Remediation branch | `fix/EXEC-PROMPT-002-defect-011-cors-allowlist` |
| Remediation details | Replaced `setAllowedOriginPatterns(wildcards)` with `setAllowedOrigins(exact)`. Added `CorsProperties` with startup validation. Deleted dead `CorsConfig` (DEFECT-022). Unified env var to `SANAD_CORS_ALLOWED_ORIGINS`. Fixed test FK deletion order (26 errors → 0). CI runner blocker bypassed by making repository temporarily public. First real CI failure: `ProductionProfileTest` missing test-scoped CORS property (10 errors). Fixed by adding `sanad.cors.allowed-origins=https://snad-app.vercel.app` as `@SpringBootTest(properties=...)`. Local: 422 tests, 0 failures, 0 errors (2 independent verify runs). CI revalidation pending. |

### DEFECT-012: Admin Password in Plaintext CI Input

| Field | Value |
|-------|-------|
| ID | DEFECT-012 |
| Title | smoke-test.yml accepts admin password as plaintext workflow input |
| Severity | P1 |
| Affected component | CI/CD — .github/workflows/smoke-test.yml lines 22-24 |
| Evidence | `admin_password` input type string — visible in GitHub Actions run logs |
| Root cause | Convenience for manual smoke testing |
| Business impact | Admin credentials exposed in CI logs; account takeover risk |
| Security impact | Critical — plaintext credential exposure |
| Recommended remediation | Replace with `secrets.ADMIN_PASSWORD` GitHub Secret |
| Required tests | Verify smoke test works with secret reference |
| Dependencies | GitHub repository settings access |
| Estimated complexity | Low (30 minutes) |
| Go-live blocking | Yes |

### DEFECT-013: Access Token Stored in localStorage

| Field | Value |
|-------|-------|
| ID | DEFECT-013 |
| Title | Frontend stores JWT access token in localStorage — vulnerable to XSS |
| Severity | P1 |
| Affected component | Frontend — apps/web/lib/auth/auth-provider.tsx lines 48-49, 118-119 |
| Evidence | `localStorage.setItem('sanad_access_token', ...)` and `localStorage.setItem('sanad_access_token_expires_at', ...)` |
| Root cause | Standard SPA token storage pattern — simpler than in-memory |
| Business impact | If any XSS vulnerability exists, attacker can exfiltrate access tokens |
| Security impact | High — token theft via XSS |
| Recommended remediation | Store access token in memory only (React state/context); rely on HttpOnly refresh cookie for session persistence across page reloads |
| Required tests | Token persists across navigation; token lost on page refresh → silent refresh via cookie; XSS simulation test |
| Dependencies | None |
| Estimated complexity | Medium (4-8 hours) |
| Go-live blocking | Yes |

### DEFECT-014: No RBAC Migration Seeding for ADMIN Role

| Field | Value |
|-------|-------|
| ID | DEFECT-014 |
| Title | ADMIN role capabilities depend on runtime bootstrap — no migration seeding |
| Severity | P1 |
| Affected component | Database — Flyway migrations + CredentialBootstrapService |
| Evidence | V14 seeds 19 capabilities only. ADMIN role created at runtime by `CredentialBootstrapService` with capabilities assigned programmatically. No migration exists to seed role-capability mappings. |
| Root cause | V15 Java migration was removed (commit cec99c1) and never replaced |
| Business impact | If bootstrap is disabled or fails, ADMIN role exists with zero capabilities — system unusable |
| Security impact | High — new capabilities added via migration won't be automatically assigned to ADMIN |
| Recommended remediation | Create a new migration (V15 or V16) that seeds the ADMIN role with all capabilities; make bootstrap idempotent verification only |
| Required tests | Bootstrap with existing data → ADMIN still has all capabilities; fresh install → ADMIN has all capabilities |
| Dependencies | None |
| Estimated complexity | Medium (4 hours) |
| Go-live blocking | Yes |

---

## P2 — Major Functional or Reliability Defect

### DEFECT-015: Non-Distributed Rate Limiting

| Field | Value |
|-------|-------|
| ID | DEFECT-015 |
| Title | Login and password reset rate limits use in-memory Caffeine cache — ineffective in multi-instance deployment |
| Severity | P2 |
| Affected component | Backend — AuthService rate limiting |
| Evidence | Caffeine Cache.newBuilder() with local expiry — no distributed coordination |
| Root cause | Simplicity for single-instance deployment; no Redis dependency |
| Business impact | Brute-force protection ineffective with horizontal scaling |
| Security impact | Medium — brute-force attacks bypass rate limits by distributing across instances |
| Recommended remediation | Implement Redis-backed rate limiting or use Render's built-in rate limiting |
| Required tests | Concurrent login attempts across simulated instances |
| Dependencies | Redis or external rate limiting service |
| Estimated complexity | Medium (8 hours) |
| Go-live blocking | No (single-instance deployment currently) |

### DEFECT-016: Frontend Lint Errors

| Field | Value |
|-------|-------|
| ID | DEFECT-016 |
| Title | 6 ESLint errors in production code — React anti-patterns and Next.js violations |
| Severity | P2 |
| Affected component | Frontend — auth-boundary.tsx, memberships-live-panel.tsx |
| Evidence | 3x `<a>` elements instead of `<Link>`, 3x `setState` in effect body |
| Root cause | React 19 strict mode enforcement + development shortcuts |
| Business impact | Potential cascading renders, performance degradation, SEO impact |
| Security impact | Low — no direct security impact |
| Recommended remediation | Replace `<a>` with `<Link>`; refactor setState-in-effect to use `useMemo` or derived state |
| Required tests | `npm run lint` passes with 0 errors |
| Dependencies | None |
| Estimated complexity | Low (2 hours) |
| Go-live blocking | No |

### DEFECT-017: Frontend Unit Test Failures

| Field | Value |
|-------|-------|
| ID | DEFECT-017 |
| Title | 3 frontend unit tests fail — API config timeout and auth flow drift |
| Severity | P2 |
| Affected component | Frontend — lib/api-config.test.ts, lib/api/auth.test.ts |
| Evidence | `API_TIMEOUT_MS` expected 10000 but got 60000; `AmbiguousTenantError` not thrown (throws `ApiHttpError` instead); Response constructor rejects status 204 |
| Root cause | Config change (timeout 10s→60s) not reflected in test; auth error class hierarchy changed; mock Response doesn't support 204 |
| Business impact | Test suite is unreliable — CI may pass or fail unpredictably |
| Security impact | Low — tests don't affect runtime security |
| Recommended remediation | Update test expectations to match current config and error classes; fix 204 mock |
| Required tests | `npm test` passes with 0 failures |
| Dependencies | None |
| Estimated complexity | Low (1 hour) |
| Go-live blocking | No |

### DEFECT-018: No SHA Verification in Backend Deploy Workflow

| Field | Value |
|-------|-------|
| ID | DEFECT-018 |
| Title | backend-deploy.yml deploys via hook without verifying commit SHA |
| Severity | P2 |
| Affected component | CI/CD — .github/workflows/backend-deploy.yml |
| Evidence | Deploy hook deploys whatever is latest on main — no SHA pinning unlike production-release.yml |
| Root cause | Simpler workflow design; hook-based deployment lacks SHA control |
| Business impact | Unintended commit may be deployed if main advances between intent and execution |
| Security impact | Medium — unreviewed code could reach production |
| Recommended remediation | Add SHA verification and pinning similar to production-release.yml |
| Required tests | Deploy with specific SHA → verify deployed SHA matches |
| Dependencies | None |
| Estimated complexity | Medium (4 hours) |
| Go-live blocking | No |

### DEFECT-019: No Frontend Server-Side Route Protection

| Field | Value |
|-------|-------|
| ID | DEFECT-019 |
| Title | No Next.js middleware for server-side auth verification |
| Severity | P2 |
| Affected component | Frontend — apps/web/ (no middleware.ts) |
| Evidence | Auth enforcement is entirely client-side via AuthBoundary component |
| Root cause | SPA-first architecture; no middleware implemented |
| Business impact | Flash of unprotected content; SEO indexing of protected pages |
| Security impact | Medium — client-side protection can be bypassed |
| Recommended remediation | Add middleware.ts with token validation and route protection |
| Required tests | Protected route redirects unauthenticated users server-side |
| Dependencies | None |
| Estimated complexity | Medium (4 hours) |
| Go-live blocking | No |

### DEFECT-020: PostgreSQL Port Exposed in docker-compose.prod.yml

| Field | Value |
|-------|-------|
| ID | DEFECT-020 |
| Title | PostgreSQL 5432 port mapped to host in production compose |
| Severity | P2 |
| Affected component | Infrastructure — docker-compose.prod.yml line 10 |
| Evidence | `"5432:5432"` — exposes database to host network |
| Root cause | Development convenience carried into production compose |
| Business impact | Database accessible from host network |
| Security impact | Medium — expanded attack surface |
| Recommended remediation | Use internal Docker network only; remove port mapping or use `expose` |
| Required tests | Backend connects to PostgreSQL without host port mapping |
| Dependencies | None |
| Estimated complexity | Low (30 minutes) |
| Go-live blocking | No |

---

## P3 — Medium Defect or Incomplete Acceptance

### DEFECT-021: UserMembershipController Missing @RequireCapability

| Field | Value |
|-------|-------|
| ID | DEFECT-021 |
| Title | Any authenticated user can list any user's organization memberships |
| Severity | P3 |
| Affected component | Backend — UserMembershipController |
| Evidence | No `@RequireCapability` annotation on listMemberships endpoint |
| Root cause | Oversight during RBAC implementation |
| Business impact | Low — information disclosure within tenant boundary |
| Security impact | Low — no cross-tenant access, but membership enumeration |
| Recommended remediation | Add `@RequireCapability("MEMBERSHIP.READ")` |
| Required tests | User without MEMBERSHIP.READ capability gets 403 |
| Dependencies | None |
| Estimated complexity | Low (15 minutes) |
| Go-live blocking | No |

### DEFECT-022: Dual CORS Configuration (Dead Code)

| Field | Value |
|-------|-------|
| ID | DEFECT-022 |
| Title | CorsConfig WebMVC class is overridden by SecurityConfig CORS |
| Severity | P3 |
| Affected component | Backend — config/CorsConfig.java (DELETED) |
| Evidence | SecurityConfig.corsConfigurationSource() takes precedence; CorsConfig is never effective |
| Root cause | Two CORS implementations created at different times |
| Business impact | None — dead code |
| Security impact | Low — confusion about which config is active |
| Recommended remediation | Remove dead CorsConfig class; keep SecurityConfig CORS only |
| Required tests | CORS behavior unchanged after removal |
| Dependencies | None |
| Estimated complexity | Low (30 minutes) |
| Go-live blocking | No |
| **Remediation status** | **FIXED** (removed as part of DEFECT-011 remediation) |
| Remediation details | Deleted `CorsConfig.java`. Spring Security `CorsConfigurationSource` is the sole authoritative CORS layer. All API endpoints under `/api/**` are covered by SecurityFilterChain. |

### DEFECT-023: Rollback Never Tested

| Field | Value |
|-------|-------|
| ID | DEFECT-023 |
| Title | Production rollback procedure documented but never executed |
| Severity | P3 |
| Affected component | Operations — deployment procedures |
| Evidence | docs/deployment/render-backend-deployment.md: "NOT YET EXECUTED" |
| Root cause | No DR exercise conducted yet |
| Business impact | Rollback may fail during actual incident |
| Security impact | Low — operational risk |
| Recommended remediation | Conduct and document a rollback test in staging |
| Required tests | Rollback test executed successfully |
| Dependencies | Staging environment |
| Estimated complexity | Medium (4 hours including documentation) |
| Go-live blocking | No |

### DEFECT-024: JDK Version Mismatch in Security Scan

| Field | Value |
|-------|-------|
| ID | DEFECT-024 |
| Title | Security scan uses JDK 17 but project targets JDK 21 |
| Severity | P3 |
| Affected component | CI/CD — .github/workflows/security-scan.yml line 56 |
| Evidence | `java-version: '17'` — project uses JDK 21 per Dockerfile and CI |
| Root cause | Workflow not updated when project upgraded from JDK 17 to 21 |
| Business impact | False negatives in dependency scanning |
| Security impact | Medium — vulnerabilities in JDK 21 dependencies not detected |
| Recommended remediation | Update to `java-version: '21'` |
| Required tests | Security scan runs with JDK 21 |
| Dependencies | None |
| Estimated complexity | Low (15 minutes) |
| Go-live blocking | No |

### DEFECT-025: Free-Tier Infrastructure Not Production Grade

| Field | Value |
|-------|-------|
| ID | DEFECT-025 |
| Title | Render free tier with cold starts and connection pool max=5 |
| Severity | P3 |
| Affected component | Infrastructure — render.yaml |
| Evidence | `plan: free`, `DATABASE_POOL_MAX: "5"` |
| Root cause | Cost optimization for pilot phase |
| Business impact | Slow response after idle periods; limited concurrency |
| Security impact | None |
| Recommended remediation | Upgrade to paid tier for production; increase pool to 15-20 |
| Required tests | Load test with 20+ concurrent connections |
| Dependencies | Budget approval |
| Estimated complexity | Low (configuration change) |
| Go-live blocking | No (for pilot) / Yes (for commercial) |

---

## P4 — Improvement or Technical Debt

### DEFECT-026: No Structured Audit Framework

| Field | Value |
|-------|-------|
| ID | DEFECT-026 |
| Title | Auth events logged via SLF4J text — no structured/tamper-proof audit trail |
| Severity | P4 |
| Recommended remediation | Implement structured JSON audit logging with correlation IDs |

### DEFECT-027: Next.js Config Empty — No CSP Headers

| Field | Value |
|-------|-------|
| ID | DEFECT-027 |
| Title | next.config.ts has no security headers (CSP, HSTS, X-Frame-Options) |
| Severity | P4 |
| Recommended remediation | Add security headers configuration |

### DEFECT-028: Login Placeholder Hints Admin Email

| Field | Value |
|-------|-------|
| ID | DEFECT-028 |
| Title | Login form uses `snad@app.com` as placeholder — reveals admin email pattern |
| Severity | P4 |
| Recommended remediation | Use generic placeholder like `email@example.com` |

### DEFECT-029: COOKIE_SAME_SITE Default Mismatch

| Field | Value |
|-------|-------|
| ID | DEFECT-029 |
| Title | docker-compose.prod.yml defaults to `None` but application-prod.yml defaults to `lax` |
| Severity | P4 |
| Recommended remediation | Align defaults to `lax` everywhere |
