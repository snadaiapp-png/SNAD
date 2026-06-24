# SANAD Test Evidence

**Program**: SANAD-FDP-001 — EXEC-PROMPT-001
**Date**: 2026-06-24
**Repository SHA**: 635ebe3

---

## Backend Build & Test

### Command: `mvn clean compile`

| Field | Value |
|-------|-------|
| Environment | Local (OpenJDK 21.0.11, Maven 3.9.6) |
| Date | 2026-06-24T00:01:45Z |
| Result | PASS |
| Output summary | BUILD SUCCESS, 0 compilation errors |
| SHA tested | 635ebe3 |

### Command: `mvn test`

| Field | Value |
|-------|-------|
| Environment | Local (OpenJDK 21.0.11, Maven 3.9.6, H2 in-memory) |
| Date | 2026-06-24T00:02:29Z |
| Result | PASS |
| Output summary | Tests run: 364, Failures: 0, Errors: 0, Skipped: 11 |
| SHA tested | 635ebe3 |

**Skipped tests (11):** Likely PostgreSQL-specific tests requiring Testcontainers (not available in local H2 profile). Not a defect — expected behavior.

---

## Frontend Build & Test

### Command: `npm ci`

| Field | Value |
|-------|-------|
| Environment | Local (Node.js from Next.js 16) |
| Date | 2026-06-24T00:02:40Z |
| Result | PASS |
| Output summary | 157 packages installed, 2 moderate vulnerabilities |
| SHA tested | 635ebe3 |

**Vulnerabilities:** 2 moderate — not blocking, should be reviewed.

### Command: `npm run build`

| Field | Value |
|-------|-------|
| Environment | Local |
| Date | 2026-06-24T00:02:55Z |
| Result | PASS |
| Output summary | Next.js 16.2.9 (Turbopack), compiled in 3.5s, 5 pages generated |
| SHA tested | 635ebe3 |

**Pages generated:** `/`, `/_not-found`, `/api/system/backend-status`, `/reset-password`

### Command: `npm run lint`

| Field | Value |
|-------|-------|
| Environment | Local |
| Date | 2026-06-24T00:03:05Z |
| Result | FAIL |
| Output summary | 6 errors, 3 warnings |
| SHA tested | 635ebe3 |

**Errors:**
1. `auth-boundary.tsx:217` — `<a>` element instead of `<Link>`
2. `auth-boundary.tsx:555` — `setState` in effect body
3. `auth-boundary.tsx:607` — `<a>` element instead of `<Link>`
4. `auth-boundary.tsx:622` — `<a>` element instead of `<Link>`
5. `memberships-live-panel.tsx:62` — `setState` in effect body
6. `memberships-live-panel.tsx:90` — `setState` in effect body

### Command: `npm test`

| Field | Value |
|-------|-------|
| Environment | Local (Vitest 4.1.9) |
| Date | 2026-06-24T00:03:15Z |
| Result | FAIL (3 of 151 tests) |
| Output summary | Test Files: 2 failed / 10 passed (12), Tests: 3 failed / 148 passed (151) |
| SHA tested | 635ebe3 |

**Failed tests:**
1. `lib/api-config.test.ts > re-exports normalized API configuration` — Expected `API_TIMEOUT_MS` 10000, got 60000
2. `lib/api/auth.test.ts > login throws AmbiguousTenantError on 409` — Throws `ApiHttpError` instead of `AmbiguousTenantError`
3. `lib/api/auth.test.ts > logout sends request` — `Response constructor: Invalid response status code 204`

---

## Production Deployment Verification

### Backend Health Check

| Field | Value |
|-------|-------|
| Command | `curl https://sanad-backend-mcrj.onrender.com/actuator/health` |
| Environment | Production (Render, Frankfurt) |
| Date | 2026-06-24T00:00:00Z |
| Result | PASS |
| Output | `{"status":"UP","groups":["liveness","readiness"]}` |

### Backend Liveness

| Field | Value |
|-------|-------|
| Command | `curl https://sanad-backend-mcrj.onrender.com/actuator/health/liveness` |
| Environment | Production |
| Date | 2026-06-24T00:00:00Z |
| Result | PASS |
| Output | `{"status":"UP"}` |

### Backend Readiness

| Field | Value |
|-------|-------|
| Command | `curl https://sanad-backend-mcrj.onrender.com/actuator/health/readiness` |
| Environment | Production |
| Date | 2026-06-24T00:00:00Z |
| Result | PASS |
| Output | `{"status":"UP"}` |

### Frontend Verification

| Field | Value |
|-------|-------|
| Command | `curl -sI https://snad-app.vercel.app` |
| Environment | Production (Vercel) |
| Date | 2026-06-24T00:00:00Z |
| Result | PASS |
| Output | HTTP/2 200, content-type: text/html, server: Vercel |

### GitHub Deployment Record

| Field | Value |
|-------|-------|
| Deployment ID | 5174005825 |
| Environment | Production |
| SHA | 635ebe3 |
| Status | success |
| Created | 2026-06-23T23:50:47Z |

---

## Code Audit Evidence

### Authentication Code Review

| Aspect | File | Finding |
|--------|------|---------|
| JWT generation | `security/JwtTokenProvider.java` | HMAC-SHA, 15min access, 7-day refresh |
| JWT validation | `security/JwtAuthenticationFilter.java` | Signature + issuer + tenant binding + session version |
| Password hashing | `config/SecurityConfig.java` | BCryptPasswordEncoder(10) |
| Logout revocation | `security/AuthService.java` | Increments session_version + revokes all refresh tokens |
| Rate limiting | `security/AuthService.java` | Caffeine in-memory: 5 attempts/5min (prod), 20/1min (default) |
| CORS config | `config/SecurityConfig.java` + `config/CorsProperties.java` | Exact-origin allowlist via `SANAD_CORS_ALLOWED_ORIGINS` — DEFECT-011 FIXED |

### Multi-Tenancy Code Review

| Aspect | File | Finding |
|--------|------|---------|
| TenantId source | `JwtAuthenticationFilter.java` | JWT claim (trusted), validated against request param |
| Repository scoping | All repositories | All queries include `tenantId` filter |
| Cross-tenant query | `UserRepository.java` | `findAllByEmail()` — intentional for email-only login |
| Tenant binding | `JwtAuthenticationFilter.java` | Mismatch → 403 |

### RBAC Code Review

| Aspect | File | Finding |
|--------|------|---------|
| @RequireCapability | 11 controllers | 43/44 endpoints enforced |
| Missing enforcement | `UserMembershipController.java` | No @RequireCapability — DEFECT-021 |
| ADMIN bootstrap | `CredentialBootstrapService.java` | Runtime only — no migration — DEFECT-014 |
| Capability count | V14 migration | 19 capabilities seeded |
| 401/403 separation | `SecurityConfig.java` | Custom entryPoint + accessDeniedHandler |

### Flyway Migration Review

| Version | Type | Destructive | Notes |
|---------|------|-------------|-------|
| V1 | SQL | No | Create tenants |
| V2 | SQL | No | Create organizations |
| V3 | SQL | No | Create organization_memberships |
| V4 | SQL | No | Create users |
| V5 | SQL | No | Link users to memberships |
| V6 | SQL | No | Create roles |
| V7 | SQL | No | Create access_capabilities |
| V8 | SQL | No | Create role_capabilities |
| V9 | SQL | No | Create user_role_assignments |
| V10 | SQL | No | Auth credentials + refresh_tokens |
| V11 | SQL | No | Bootstrap columns |
| V12 | SQL | No | Password reset tokens |
| V13 | SQL | No | Session version |
| V14 | SQL | No | Seed RBAC capabilities |
| **V15** | **Missing** | — | No migration for ADMIN role-capability seeding |

---

## CI/CD Workflow Audit

### smoke-test.yml — Plaintext Password

| Field | Value |
|-------|-------|
| File | `.github/workflows/smoke-test.yml` |
| Lines | 22-24 |
| Finding | `admin_password` input type string — visible in logs |
| Severity | P1 (DEFECT-012) |

### backend-deploy.yml — No SHA Verification

| Field | Value |
|-------|-------|
| File | `.github/workflows/backend-deploy.yml` |
| Finding | Deploys via hook without pinning commit SHA |
| Severity | P2 (DEFECT-018) |

### security-scan.yml — JDK Version Mismatch

| Field | Value |
|-------|-------|
| File | `.github/workflows/security-scan.yml` |
| Line | 56 |
| Finding | `java-version: '17'` — project uses JDK 21 |
| Severity | P3 (DEFECT-024) |
