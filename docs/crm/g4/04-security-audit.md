# G4 Security Audit

**Module**: Opportunities & Pipeline (G4)
**Generated**: 2026-08-06
**HEAD**: 7bb72ffe

## Security Summary

| Category | Status | Evidence |
|----------|--------|----------|
| Authentication | ✅ ENFORCED | BearerAuth on all endpoints, 401 returned without token |
| Authorization (RBAC) | ✅ ENFORCED | @RequireCapability on all controller methods |
| Idempotency | ✅ ENFORCED | Idempotency-Key header required on POST/PUT/PATCH/DELETE |
| CSRF Protection | ✅ ENFORCED | Origin header validation on state-changing requests |
| CSP Headers | ✅ PRESENT | Content-Security-Policy: base-uri 'self'; frame-ancestors 'none' |
| HSTS | ✅ PRESENT | Strict-Transport-Security: max-age=63072000; includeSubDomains; preload |
| X-Content-Type-Options | ✅ PRESENT | nosniff |
| X-Frame-Options | ✅ PRESENT | DENY |
| Production Security Guard | ✅ ACTIVE | ProductionSecurityGuard blocks startup without security config |
| CORS | ✅ CONFIGURED | Backend allows Vercel origin |
| Input Validation | ✅ PRESENT | Jakarta validation annotations on request records |
| SQL Injection | ✅ MITIGATED | JPA/Hibernate parameterized queries |
| Secrets in Code | ✅ NONE | No hardcoded secrets found |

## Endpoint Security Matrix

| Endpoint | Auth | RBAC | Idempotency | CSRF |
|----------|------|------|-------------|------|
| POST /pipelines | BearerAuth | @RequireCapability | Idempotency-Key | Origin check |
| POST /opportunities | BearerAuth | @RequireCapability | Idempotency-Key | Origin check |
| PATCH /opportunities/{id} | BearerAuth | @RequireCapability | Idempotency-Key | Origin check |
| DELETE /opportunities/{id} | BearerAuth | @RequireCapability | Idempotency-Key | Origin check |
| POST /opportunities/{id}/move | BearerAuth | @RequireCapability | Idempotency-Key | Origin check |
| POST /leads/{id}/convert | BearerAuth | @RequireCapability | Idempotency-Key | Origin check |
| GET /dashboard | BearerAuth | @RequireCapability | N/A (GET) | N/A |
| GET /pipelines | BearerAuth | @RequireCapability | N/A (GET) | N/A |
| GET /opportunities | BearerAuth | @RequireCapability | N/A (GET) | N/A |
| GET /stages | BearerAuth | @RequireCapability | N/A (GET) | N/A |

## BFF Security

| Control | Status |
|---------|--------|
| Backend URL hardcoded for production | ✅ `PRODUCTION_BACKEND_URL` constant |
| Stale dashboard variables cannot re-enable tunnel | ✅ `backendBaseUrl()` validation |
| Request timeout budget | ✅ 25s default, 45s max |
| Retry logic for 502/503/504 | ✅ MAX_IDEMPOTENT_ATTEMPTS=2 |
| Refresh token in httpOnly cookie | ✅ `sanad_refresh` cookie |
| Session hint cookie | ✅ `sanad_session_hint` cookie |
| Origin validation on state-changing requests | ✅ `hasValidOrigin()` |
