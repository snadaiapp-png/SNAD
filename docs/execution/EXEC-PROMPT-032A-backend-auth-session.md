# EXEC-PROMPT-032A — Backend Authentication & Session Foundation

## Status

```
IMPLEMENTED — PENDING CI AND PM REVIEW
```

## Objective

Build the backend authentication infrastructure (Spring Security, JWT, password storage, refresh token rotation, login/logout/refresh/me endpoints) that the frontend will integrate with in EXEC-PROMPT-032 (frontend auth).

## Base

- **Base SHA:** `81585a9e09223b7cf1066fb40e960b6ef249ccd7` (EXEC-FIX-031A merge via PR #51)
- **Branch:** `feat/EXEC-PROMPT-032A-backend-auth-session`
- **Fresh clone:** Yes — created from `origin/main` at `81585a9`

## Architecture Decision

See [ADR-032A](../../architecture/adr/ADR-032A-backend-authentication.md) for the full architecture decision record.

### Summary

- **Session model:** Stateless JWT access tokens (15 min) + opaque refresh tokens (7 days) with rotation and replay protection
- **Credential storage:** BCrypt (strength 10) password hashes in `password_hash` column on `users` table
- **Login tenant resolution:** Explicit `tenantId` in login request (emails are unique per tenant, not globally)
- **Refresh rotation:** Single-use opaque tokens; replay detection revokes all tokens for the user
- **Brute-force protection:** In-memory rate limiting (Caffeine cache) — max 5 failed attempts per 5-minute window
- **RBAC integration:** `/api/v1/auth/me` returns user's role grants and memberships; `@EnableMethodSecurity` enabled for future `@PreAuthorize` usage

## Endpoint Contracts

| Method | Path | Auth | Request Body | Response | Status |
|---|---|---|---|---|---|
| POST | `/api/v1/auth/login` | No | `LoginRequest {tenantId, email, password}` | `AuthResponse {accessToken, refreshToken, expiresAt, user}` | 200 |
| POST | `/api/v1/auth/refresh` | No | `RefreshRequest {refreshToken}` | `AuthResponse` (new token pair) | 200 |
| POST | `/api/v1/auth/logout` | Yes (Bearer) | — | — | 204 |
| GET | `/api/v1/auth/me` | Yes (Bearer) | — | `MeResponse {id, tenantId, email, displayName, status, lastLoginAt, memberships, roleGrants}` | 200 |

### Error Responses (401/403/429)

All auth errors use the existing `ApiErrorResponse` shape:
```json
{
  "timestamp": "2026-06-21T12:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "بيانات الدخول غير صحيحة",
  "path": "/api/v1/auth/login"
}
```

- **401:** Invalid credentials, suspended account, expired/invalid token, replay detected
- **403:** Access denied (authenticated but not authorized)
- **429:** Too many login attempts (with `Retry-After: 300` header)
- **400:** Malformed request, validation errors

## Credential Storage Model

- **Algorithm:** BCrypt (strength 10, ~100ms per hash)
- **Column:** `password_hash VARCHAR(255)` on `users` table (nullable — existing users have no password)
- **Never exposed** in API responses, logs, or `toString()`
- Passwords are trimmed before encoding

## Token/Session Lifetimes

| Token | Lifetime | Configurable Via |
|---|---|---|
| Access JWT | 15 minutes | `sanad.security.jwt.access-token-ttl` |
| Refresh token | 7 days | `sanad.security.refresh.refresh-token-ttl` |

## Refresh Rotation and Revocation Behavior

1. Login issues both access JWT and refresh token
2. Refresh token is opaque (256-bit random, SHA-256 hashed in DB)
3. On `POST /api/v1/auth/refresh`: old token marked `USED`, new token issued
4. If a `USED` token is presented again → **replay attack** → all tokens for that user are revoked
5. On `POST /api/v1/auth/logout`: all active refresh tokens for the user are revoked
6. Access JWT is stateless — cannot be revoked, expires naturally (15 min)

## Tenant Identity Rules

- Login requires explicit `tenantId` in the request body
- JWT contains `tenant_id` claim from the authenticated user
- All existing `/api/v1/**` endpoints still require `tenantId` as a query parameter (backward compatibility)
- Future stage (EXEC-PROMPT-035) will implement automatic tenant resolution

## RBAC Integration

- `@EnableMethodSecurity` enabled — future stages can use `@PreAuthorize`
- `GET /api/v1/auth/me` returns the user's active role grants (with role codes) and organization memberships
- Role grants are loaded via `UserRoleGrantRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, ACTIVE)`
- Roles are NOT embedded in the JWT (they can change during the token's lifetime)
- For this stage, all authenticated users get `ROLE_USER` authority — RBAC enforcement is deferred

## Migration Details

### V10__add_auth_credentials.sql

- Adds `password_hash VARCHAR(255)` column to `users` table (nullable)
- Adds `last_login_at TIMESTAMP WITH TIME ZONE` column to `users` table
- Creates `refresh_tokens` table with columns: `id, tenant_id, user_id, token_hash, status, expires_at, created_at, used_at, replaced_by_id`
- Foreign keys: `refresh_tokens(tenant_id, user_id)` → `users(tenant_id, id)`
- Unique constraint on `token_hash`
- Check constraint: `status IN ('ACTIVE', 'USED', 'REVOKED')`
- Indexes on `(tenant_id, user_id)` and `status`

## Test Counts and Results

```
mvn test → BUILD SUCCESS
Tests run: 323, Failures: 0, Errors: 0, Skipped: 10
```

### New tests (20):
- `AuthApiIntegrationTest`: 20 tests covering:
  - Login: valid credentials, wrong password, non-existent user, wrong tenant, suspended user, user without password, malformed request, no password in response
  - Refresh: valid token, replay protection, invalid token
  - /me: without token (401), with valid token, with invalid token
  - Logout: with valid token (204), without token (401), revokes refresh token
  - Protected endpoints: /api/v1/users without token (401), with valid token (200)
  - Actuator health remains public (200 without token)

### Existing tests (303):
All existing tests pass with security bypass configuration:
- `@SpringBootTest` tests use `@Import(SecurityPermitAllTestConfig.class)`
- `@WebMvcTest` tests use `@AutoConfigureMockMvc(addFilters = false)`
- `PlatformApiCountTest` updated: expected API count 44 → 48 (4 new auth endpoints)

## Security Findings

1. **No passwords in logs:** `User.toString()` excludes `passwordHash`; `AuthService` logs only userId/tenantId, never passwords
2. **No tokens in logs:** `RefreshToken.toString()` excludes `tokenHash`; JWT secret never logged
3. **No credentials in responses:** `AuthResponse` contains only accessToken/refreshToken/user (no password hash)
4. **Brute-force protection:** 5 failed attempts per 5-minute window per (tenantId, email)
5. **Replay protection:** USED refresh token presentation revokes all tokens
6. **Production JWT secret:** No default in `application-prod.yml` — must be set via `JWT_SECRET` env var
7. **CSRF disabled:** Stateless JWT, no cookies → CSRF not applicable
8. **CORS:** Integrated with Spring Security via `CorsConfigurationSource` bean
9. **Actuator health:** Remains public (no auth required)
10. **All `/api/**` endpoints:** Now require authentication (except `/api/v1/auth/login` and `/api/v1/auth/refresh`)

## Rollback Plan

1. Revert the merge commit on `main` (if already merged), OR
2. Close the PR without merging

The Flyway migration `V10` is additive (adds columns + new table) — no data loss on rollback. The `password_hash` column is nullable, so existing users are unaffected. To fully remove auth infrastructure, revert the commit and run a new migration to drop the `refresh_tokens` table and the `password_hash` / `last_login_at` columns.

---

Executed For: Abdulrahman Sinan, SANAD Business Operating System
Date: 21 June 2026
