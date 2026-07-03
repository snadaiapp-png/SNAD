# SANAD Remediation Plan

**Program**: SANAD-FDP-001 — EXEC-PROMPT-001
**Date**: 2026-06-24
**Repository SHA**: 635ebe3

---

## Remediation Priority Order

Remediations are ordered by severity and dependency chain. Each work package is designed to be independently executable and verifiable.

---

## Work Package 1: P0/P1 Blockers

### WP1.1: Fix CORS Wildcard (DEFECT-011)

| Attribute | Value |
|-----------|-------|
| Priority | P1 |
| Estimated effort | 1 hour |
| Target branch | `fix/EXEC-PROMPT-002-defect-011-cors-allowlist` |
| **Status** | **IMPLEMENTED — PENDING DEPLOYMENT VERIFICATION** |

**Steps completed:**
1. Created `CorsProperties` with `@ConfigurationProperties(prefix = "sanad.cors")` and startup validation
2. Added `@ConfigurationPropertiesScan` to main application class
3. Replaced `setAllowedOriginPatterns(wildcards)` with `setAllowedOrigins(exact)` in SecurityConfig
4. Added `@PostConstruct initializeCorsOrigins()` to validate CORS origins at startup
5. Deleted dead `CorsConfig.java` (DEFECT-022)
6. Unified environment variable to `SANAD_CORS_ALLOWED_ORIGINS` (removed legacy `CORS_ALLOWED_ORIGINS`)
7. Updated render.yaml, docker-compose.prod.yml, .env.example
8. Created `CorsOriginValidatorTest` — 25 standalone unit tests for validation logic
9. Created `CorsStartupValidationTest` — 11 ApplicationContextRunner tests for startup scenarios
10. Created `CorsSecurityTest` — 13 Spring Security CORS integration tests via MockMvc
11. Backend regression: 422 tests, 0 failures, 0 errors, 11 skipped

**Verification:**
- `curl -H "Origin: https://evil.vercel.app" -H "Access-Control-Request-Method: POST" -X OPTIONS https://sanad-backend-mcrj.onrender.com/api/v1/auth/login` returns no `Access-Control-Allow-Origin` header

### WP1.2: Move Admin Password to GitHub Secret (DEFECT-012)

| Attribute | Value |
|-----------|-------|
| Priority | P1 |
| Estimated effort | 30 minutes |
| Target branch | fix/defect-012-ci-secret |

**Steps:**
1. Add `ADMIN_PASSWORD` as a GitHub repository secret
2. Replace `inputs.admin_password` with `secrets.ADMIN_PASSWORD` in `smoke-test.yml`
3. Remove the `admin_password` input parameter from the workflow
4. Test by triggering the workflow manually

**Verification:**
- Workflow runs successfully with secret reference
- Admin password does not appear in any workflow log

### WP1.3: Migrate Access Token from localStorage to Memory (DEFECT-013)

| Attribute | Value |
|-----------|-------|
| Priority | P1 |
| Estimated effort | 4-8 hours |
| Target branch | fix/defect-013-token-storage |

**Steps:**
1. Move access token from `localStorage` to React context/state (in-memory only)
2. On page load, attempt silent refresh via HttpOnly cookie to restore session
3. If refresh fails, redirect to login
4. Keep token expiry in memory for proactive refresh
5. Update `auth-provider.tsx` — remove all `localStorage.setItem/getItem` for tokens
6. Keep `localStorage` for non-sensitive preferences only (e.g., last selected tenantId)
7. Update all consuming components

**Verification:**
- Access token never appears in localStorage
- Page refresh restores session via silent refresh
- XSS test: `localStorage.getItem('sanad_access_token')` returns null

### WP1.4: Create V15 Migration for RBAC Seeding (DEFECT-014)

| Attribute | Value |
|-----------|-------|
| Priority | P1 |
| Estimated effort | 4 hours |
| Target branch | fix/defect-014-rbac-migration |

**Steps:**
1. Create `V15__seed_admin_role_and_capabilities.sql`
2. Insert ADMIN role for existing tenants (idempotent `WHERE NOT EXISTS`)
3. Insert all 19 role-capability mappings for ADMIN role (idempotent)
4. Modify `CredentialBootstrapService` to verify (not create) ADMIN role and capabilities
5. Test: fresh install → ADMIN has all capabilities; existing DB → migration adds missing mappings
6. Verify `flyway_schema_history` alignment after migration

**Verification:**
- `SELECT COUNT(*) FROM role_capabilities WHERE role_id = (SELECT id FROM roles WHERE code = 'ADMIN')` returns 19
- Bootstrap logs show "ADMIN role verified" (not "created")

---

## Work Package 2: Security and Tenant Isolation

### WP2.1: Add @RequireCapability to UserMembershipController (DEFECT-021)

| Attribute | Value |
|-----------|-------|
| Priority | P3 |
| Estimated effort | 15 minutes |
| Target branch | fix/defect-021-membership-rbac |

**Steps:**
1. Add `@RequireCapability("MEMBERSHIP.READ")` to `UserMembershipController.listMemberships()`
2. Add test: user without MEMBERSHIP.READ gets 403

### WP2.2: Remove Dead CorsConfig (DEFECT-022)

| Attribute | Value |
|-----------|-------|
| Priority | P4 |
| Estimated effort | 30 minutes |
| Target branch | fix/defect-022-dead-cors |

**Steps:**
1. Delete `CorsConfig.java`
2. Verify CORS behavior unchanged (SecurityConfig handles it)

---

## Work Package 3: Backend Stability

### WP3.1: Implement Distributed Rate Limiting (DEFECT-015)

| Attribute | Value |
|-----------|-------|
| Priority | P2 |
| Estimated effort | 8 hours |
| Target branch | fix/defect-015-distributed-rate-limit |

**Steps:**
1. Add Redis dependency to `pom.xml`
2. Implement `RedisRateLimitService` replacing Caffeine-based rate limiting
3. Configure Redis connection in application-prod.yml
4. Add Redis service to docker-compose.prod.yml
5. Fallback to in-memory for local/dev profiles
6. Test concurrent rate limiting across simulated instances

---

## Work Package 4: Frontend Quality

### WP4.1: Fix Lint Errors (DEFECT-016)

| Attribute | Value |
|-----------|-------|
| Priority | P2 |
| Estimated effort | 2 hours |
| Target branch | fix/defect-016-frontend-lint |

**Steps:**
1. Replace `<a>` elements with `<Link>` from next/link (3 occurrences)
2. Refactor `setState` in effects to use derived state or `useMemo`
3. Run `npm run lint` — expect 0 errors

### WP4.2: Fix Failing Tests (DEFECT-017)

| Attribute | Value |
|-----------|-------|
| Priority | P2 |
| Estimated effort | 1 hour |
| Target branch | fix/defect-017-frontend-tests |

**Steps:**
1. Update `API_TIMEOUT_MS` expectation from 10000 to 60000
2. Fix `AmbiguousTenantError` test — update to expect `ApiHttpError` with `tenantIds` in details
3. Fix 204 Response mock — use `new Response(null, { status: 204 })` instead of JSON body
4. Run `npm test` — expect 0 failures

### WP4.3: Add Next.js Middleware (DEFECT-019)

| Attribute | Value |
|-----------|-------|
| Priority | P2 |
| Estimated effort | 4 hours |
| Target branch | fix/defect-019-middleware |

**Steps:**
1. Create `apps/web/middleware.ts`
2. Check for access token in request cookies or authorization header
3. Redirect unauthenticated users to login for protected routes
4. Allow public routes (login, reset-password) without auth
5. Test: accessing protected route without token redirects to login

---

## Work Package 5: CI/CD Reliability

### WP5.1: Add SHA Verification to Backend Deploy (DEFECT-018)

| Attribute | Value |
|-----------|-------|
| Priority | P2 |
| Estimated effort | 4 hours |
| Target branch | fix/defect-018-deploy-sha |

**Steps:**
1. Add `commit_sha` input to `backend-deploy.yml`
2. Verify SHA matches main branch head before triggering deploy hook
3. Add rollback step on deployment failure
4. Test with valid and invalid SHAs

### WP5.2: Fix JDK Version in Security Scan (DEFECT-024)

| Attribute | Value |
|-----------|-------|
| Priority | P3 |
| Estimated effort | 15 minutes |
| Target branch | fix/defect-024-jdk-version |

**Steps:**
1. Change `java-version: '17'` to `java-version: '21'` in security-scan.yml

---

## Work Package 6: Production Evidence

### WP6.1: Conduct and Document Rollback Test (DEFECT-023)

| Attribute | Value |
|-----------|-------|
| Priority | P3 |
| Estimated effort | 4 hours |
| Target branch | N/A (operational) |

**Steps:**
1. Deploy a known-good version to staging
2. Deploy a breaking version
3. Execute rollback procedure
4. Verify health after rollback
5. Document results in deployment guide

### WP6.2: Infrastructure Upgrade Assessment (DEFECT-025)

| Attribute | Value |
|-----------|-------|
| Priority | P3 (pilot) / P1 (commercial) |
| Estimated effort | 2 hours (assessment) |
| Target branch | N/A (configuration) |

**Steps:**
1. Document Render paid tier options and costs
2. Recommend connection pool size (15-20) for paid tier
3. Estimate concurrent user capacity at each tier
4. Present cost-benefit analysis to Governance Board

---

## Work Package 7: P2/P3 Defects

Already covered in WP2-WP6 above.

---

## Work Package 8: Product Enhancements

Not in scope for EXEC-PROMPT-001. Deferred to subsequent execution prompts.
