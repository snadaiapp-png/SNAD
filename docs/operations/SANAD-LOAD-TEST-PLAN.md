# SANAD Load Test Plan
## EXEC-PROMPT-010R2 Section 10

---

## 1. Objective

Validate that the SANAD staging environment can sustain expected pilot-load
traffic without errors, latency regressions, or resource exhaustion.

## 2. Scope

**In scope:**
- Health endpoint
- Login with controlled test identity
- /me (authenticated tenant binding)
- Read-only tenant-scoped endpoint (organization list)
- Token refresh
- Logout

**Out of scope:**
- Financial write operations
- Destructive operations
- Production environment (staging only)

## 3. Environment

| Parameter | Value |
|-----------|-------|
| Target | Staging environment (NOT production) |
| Backend | staging URL (to be provided by owner) |
| Frontend | staging URL (to be provided by owner) |
| Database | Staging PostgreSQL (separate from production) |
| Test identities | 2 controlled test identities in separate tenants |
| Tool | k6 (https://k6.io) |
| Test runner | Local or CI runner with k6 installed |

**BLOCKER:** Staging environment is NOT currently provisioned. Render
free-tier hosts only the production pilot. This load test plan is
documented but cannot be executed until staging is provisioned.

**Status: BLOCKED BY PROVIDER ACCESS** — staging environment required.

## 4. Test Scenarios

### 4.1 Health Endpoint
```
GET /actuator/health
Expected: 200, {"status":"UP"}
```

### 4.2 Login
```
POST /api/v1/auth/login
Body: {"email":"test-tenant-a@example.com","password":"<controlled>"}
Expected: 200, access token + refresh cookie
```

### 4.3 /me (Tenant Binding)
```
GET /api/v1/auth/me
Authorization: Bearer <access-token>
Expected: 200, user info with tenant_id
```

### 4.4 Read-Only Tenant-Scoped Endpoint
```
GET /api/v1/organizations?tenantId=<jwt-tenant-id>
Authorization: Bearer <access-token>
Expected: 200, list of organizations in tenant
```

### 4.5 Token Refresh
```
POST /api/v1/auth/refresh
Cookie: sanad_refresh=<refresh-token>
Expected: 200, new access token + rotated refresh cookie
```

### 4.6 Logout
```
POST /api/v1/auth/logout
Authorization: Bearer <access-token>
Expected: 204, refresh cookie cleared
```

## 5. Thresholds

| Metric | Threshold |
|--------|-----------|
| HTTP error rate | < 1% |
| p95 read latency | < 500 ms |
| p99 read latency | < 1000 ms |
| Authentication failure rate (valid requests) | = 0% |
| Cross-tenant rejection rate | = 100% |
| 5xx burst | None sustained |
| Database connection-pool exhaustion | None |

## 6. Execution Phases

| Phase | Duration | Virtual Users | Description |
|-------|----------|---------------|-------------|
| Warm-up | 2 min | 5 | Establish baseline, warm up JVM |
| Baseline | 5 min | 10 | Sustained low load |
| Ramp-up | 5 min | 10 → 50 | Gradual increase |
| Sustained load | 10 min | 50 | Sustained pilot load |
| Controlled spike | 2 min | 100 | Burst traffic |
| Recovery | 3 min | 10 | Verify recovery after spike |

## 7. Metrics to Record

- Virtual users
- Requests per second
- Duration
- p50, p90, p95, p99 latency
- Error rate
- CPU usage (if available)
- Memory usage (if available)
- Database connections (if available)
- Backend restarts
- Rate-limit behavior

## 8. Pass/Fail Criteria

**PASS:** All thresholds met, no 5xx bursts, no connection-pool exhaustion.
**FAIL:** Any threshold exceeded, any 5xx burst, or any resource exhaustion.

## 9. Execution Blocker

**Status: BLOCKED BY PROVIDER ACCESS**

The load test cannot be executed because:
1. No staging environment is provisioned (Render free-tier hosts production only)
2. No controlled test identities exist in staging
3. No monitoring access to capture CPU/memory/database metrics

**Required owner actions before execution:**
1. Provision staging environment (Render or alternative)
2. Create 2 controlled test identities in separate tenants
3. Configure monitoring access (or accept limited metrics)
4. Approve load test execution against staging

## 10. Report Template

See `SANAD-LOAD-TEST-REPORT.md` for the report template. The report
will be populated after the load test is executed.
