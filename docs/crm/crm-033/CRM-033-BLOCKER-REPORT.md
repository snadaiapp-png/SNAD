# CRM-033 BLOCKER REPORT

| Field | Value |
|-------|-------|
| Ticket | CRM-033 — Performance baseline for CRM |
| Status | **⛔ BLOCKED** (historical record — **SUPERSEDED** 2026-08-01) |
| Date | 2026-08-01 |
| Reporter | ZCode Agent |
| Blocker Type | Infrastructure / Authentication |

> **RESOLUTION (2026-08-01):** This blocker was removed by the permanent,
> production-safe authentication strategy described in
> `CRM-033-FINAL-CERTIFICATION.md` and `CRM-033-PERFORMANCE-REPORT.md`
> (`perf-test` profile + deterministic seed + automatic k6 login). The
> resolution used **none** of the options in §5: no manual H2 console, no
> manual SQL, no security bypass, no migration-based test data, no production
> test credentials. The recommendations in §6 and next steps in §9 are
> historical and superseded; the automated benchmark now runs end-to-end.

---

## 1. Blocker Summary

CRM-033 execution is **BLOCKED** due to inability to generate valid JWT tokens
for CRM API endpoint authentication. All CRM endpoints require JWT
authentication, but no test users exist in the in-memory H2 database and
no authentication bypass is available for performance testing.

---

## 2. Blocker Details

### 2.1 Authentication Requirement

All CRM endpoints require JWT authentication:
```java
// SecurityConfig.java
.requestMatchers("/api/**").authenticated()
```

The following endpoints return 401 Unauthorized without a valid JWT token:
- `GET /api/v1/crm/dashboard`
- `GET /api/v1/crm/accounts`
- `GET /api/v1/crm/accounts/{id}/customer-360`
- `POST /api/v1/crm/leads/{id}/convert`

### 2.2 JWT Token Generation Blocked

The application uses an ephemeral JWT secret when `JWT_SECRET` is not set:
```java
// JwtTokenProvider.java
if (secret == null || secret.isBlank()) {
    byte[] generated = new byte[MIN_SECRET_BYTES];
    new SecureRandom().nextBytes(generated);
    signingKey = Keys.hmacShaKeyFor(generated);
    log.warn("JWT secret is empty; generated an ephemeral non-production key.");
    return;
}
```

Even when `JWT_SECRET` is set (e.g., `ci-only-non-production-validation-key-1234567890`),
token generation requires a valid user ID, tenant ID, and email from the
database. No test users exist in the in-memory H2 database.

### 2.3 User Creation Blocked

User creation endpoints require authentication:
- `POST /api/v1/users` — requires `@RequireCapability` (authentication required)
- `POST /api/v1/auth/self-register` — returns 401 Unauthorized
- `POST /api/v1/internal/control-plane/bootstrap-admin` — bootstrap mode disabled

### 2.4 H2 Console Access Limited

The H2 console is available at `http://localhost:8080/h2-console` but:
- Requires session management
- Cannot be easily automated via curl
- No programmatic way to execute SQL statements

---

## 3. Prerequisites Verification

| # | Prerequisite | Status | Evidence |
|---|--------------|--------|----------|
| 1 | CRM-022 GOVERNANCE COMPLETE | ✅ PASS | `CRM-022-GOVERNANCE-CLOSURE.md` exists, baseline section 13 confirms |
| 2 | CRM-032 GOVERNANCE COMPLETE | ✅ PASS | 8 governance docs exist, baseline section 12 confirms |
| 3 | GOVERNANCE DRIFT CHECK | ✅ PASS | Quick check validates 7 core rules, all PASS |
| 4 | Working tree clean | ✅ PASS | `git status --porcelain` returns empty |
| 5 | Local main == origin/main | ✅ PASS | Both at `f0019f72bf1051a96d5eafbb7d92f98b50014dd5` |
| 6 | No unresolved governance blockers | ✅ PASS | grep found no open BLOCKED references |
| 7 | CRM-033 dependencies satisfied | ✅ PASS | `EXEC-PROMPT-CRM-027` is DONE |

**All 7 prerequisites PASS.** The blocker is in execution infrastructure, not governance.

---

## 4. Attempted Solutions

| # | Attempt | Result |
|---|---------|--------|
| 1 | Docker k6 via `grafana/k6:latest` | ❌ Docker daemon not running |
| 2 | Direct k6 download (v0.50.0) | ✅ Downloaded successfully |
| 3 | k6 load test execution | ❌ 401 Unauthorized (no JWT token) |
| 4 | JWT token with CI secret | ❌ No users in database |
| 5 | Self-registration endpoint | ❌ Returns 401 Unauthorized |
| 6 | Control plane bootstrap | ❌ Bootstrap mode disabled |
| 7 | H2 console SQL execution | ❌ Session management blocked automation |

---

## 5. Resolution Options

### Option A: Create Test User via H2 Console (Manual)

1. Open `http://localhost:8080/h2-console` in browser
2. Login with JDBC URL: `jdbc:h2:mem:sanad`
3. Execute:
```sql
INSERT INTO tenants (id, name, status) VALUES ('00000000-0000-0000-0000-000000000001', 'Test Tenant', 'ACTIVE');
INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, platform_admin, session_version, must_change_password) 
VALUES ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'test@sanad.ai', 'Test User', 'ACTIVE', '$2a$10$dummy', true, 0, false);
```
4. Restart application with `JWT_SECRET=ci-only-non-production-validation-key-1234567890`
5. Generate JWT token using the secret
6. Run k6 load test with valid token

### Option B: Add Test Data via Migration

Add a test user in a new Flyway migration file for local/test profiles.

### Option C: Disable Authentication for Local Testing

Add a test-only security configuration that permits all requests without authentication.

### Option D: Create Performance Test Endpoint

Add a dedicated performance test endpoint that bypasses authentication
(e.g., `/api/v1/internal/perf-test/*`).

---

## 6. Recommendation

**Recommended:** Option A (manual H2 console) for immediate unblocking,
followed by Option B (test data migration) for sustainable testing.

---

## 7. Governance Decision

**⛔ CRM-033 BLOCKED**

- All governance prerequisites: ✅ PASS
- Execution infrastructure: ❌ BLOCKED (authentication)
- Next action: Resolve authentication blocker, then re-run load test

---

## 8. Files Modified

| File | Action | Purpose |
|------|--------|---------|
| `performance/k6/crm-performance-baseline.js` | Created | k6 load test script for CRM endpoints |
| `docs/crm/crm-033/CRM-033-BLOCKER-REPORT.md` | Created | This blocker report |
| `scripts/crm/governance-drift-check.sh` | Modified | Fixed Git Bash compatibility (FIND variable) |
| `scripts/crm/governance-drift-quick-check.sh` | Created | Fast governance validation |

---

## 9. Next Steps

1. **Immediate:** Manually create test user via H2 console (Option A)
2. **Short-term:** Add test data migration (Option B)
3. **Re-run:** Execute CRM-033 load test after authentication is resolved
4. **Verify:** Confirm p95 < 500ms for all CRM endpoints
5. **Document:** Update governance artifacts with performance baseline results

---

## 10. Contact

For questions about this blocker report, contact the Platform squad or
Security squad for authentication configuration assistance.
