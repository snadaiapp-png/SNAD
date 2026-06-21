# ADR-032A: Backend Authentication Architecture

## Status

Accepted

## Date

2026-06-21

## Context

The SANAD backend has zero authentication infrastructure (confirmed by EXEC-PROMPT-032 Contract Gap Report). All API endpoints are publicly accessible with only `tenantId` query parameter scoping. EXEC-PROMPT-032A authorizes building the backend authentication foundation.

The existing codebase has:
- `User` entity with `id, tenantId, email, displayName, status, createdAt, updatedAt` — no password field
- `UserStatus` enum: `ACTIVE, INACTIVE, INVITED, SUSPENDED, ARCHIVED`
- `UserRoleGrant` + `Role` + `AccessCapability` RBAC infrastructure (already functional)
- `OrganizationMembership` linking users to organizations within a tenant
- Tenant scoping via `tenantId` query parameter (not header)
- No Spring Security, no JWT, no password encoder

## Decision

### 1. Session Model: Stateless JWT with Refresh Token Rotation

**Access Token:** Short-lived JWT (15 minutes), passed via `Authorization: Bearer <token>` header. Contains `sub` (userId), `tenantId`, `email`, `iat`, `exp`. Stateless — no server-side session store needed.

**Refresh Token:** Long-lived (7 days), opaque random token (not JWT), stored in `refresh_tokens` table with server-side validation. Rotated on each use (old token revoked, new token issued). Replay protection via rotation + single-use enforcement.

**Why not server-side sessions?**
- The frontend (Vercel) and backend (Render) are on different domains — cookie-based sessions require SameSite/CORS complexity
- The existing API is designed as a stateless REST API (tenantId as query param, no session affinity)
- JWT allows the frontend to include the token in requests without server-side lookup overhead
- Refresh token rotation provides revocation capability without a session store

**Why not JWT-only (no refresh table)?**
- JWT-only with long lifetime is insecure (cannot revoke)
- JWT-only with short lifetime requires frequent re-authentication (bad UX)
- Refresh token table allows revocation on logout, password change, or suspicious activity

### 2. Credential Storage: BCrypt Password Hash

- **Algorithm:** BCrypt (via `BCryptPasswordEncoder`, strength 10)
- **Column:** `password_hash VARCHAR(255)` added to `users` table (nullable — existing users have no password until they set one)
- **Never log or expose** the password hash in API responses, logs, or error messages
- Passwords are normalized (trimmed) before encoding

### 3. Login Tenant Resolution

**Login request includes `tenantId` explicitly.** The `users` table has a unique constraint on `(tenant_id, email)` — emails can repeat across tenants. Requiring `tenantId` in the login request is the cleanest design and avoids ambiguity.

`LoginRequest { tenantId: UUID, email: String, password: String }`

Lookup: `UserRepository.findByTenantIdAndEmail(tenantId, email)` (already exists).

### 4. Identity Relationship

- **User lifecycle:** Login is blocked unless `user.status == ACTIVE`. `SUSPENDED`, `INACTIVE`, `INVITED`, `ARCHIVED` users cannot log in.
- **Tenant scope:** The JWT contains `tenantId` from the authenticated user. All subsequent requests use this tenant identity (but the existing `tenantId` query parameter is still required on tenant-scoped endpoints for backward compatibility — it will be validated to match the JWT's tenantId in a future stage).
- **Organization memberships:** Not loaded into the JWT. The `/api/v1/auth/me` endpoint returns the user's memberships via `OrganizationMembershipRepository.findByTenantIdAndUserId(tenantId, userId)`.
- **RBAC:** User's role grants (`UserRoleGrantRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, ACTIVE)`) are loaded on `/api/v1/auth/me` for the frontend to use. They are NOT embedded in the JWT (roles can change during the token's lifetime).

### 5. Endpoint Contracts

| Method | Path | Auth Required | Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/auth/login` | No | `LoginRequest` | `AuthResponse` (accessToken, refreshToken, expiresIn, user) |
| POST | `/api/v1/auth/refresh` | No (refresh token in body) | `RefreshRequest` | `AuthResponse` (new accessToken, new refreshToken, expiresIn, user) |
| POST | `/api/v1/auth/logout` | Yes (Bearer token) | — | 204 No Content |
| GET | `/api/v1/auth/me` | Yes (Bearer token) | — | `MeResponse` (user + memberships + roleGrants) |

### 6. Token Lifetimes

| Token | Lifetime | Configurable Via |
|---|---|---|
| Access JWT | 15 minutes | `sanad.security.jwt.access-token-ttl` (default: `15m`) |
| Refresh token | 7 days | `sanad.security.jwt.refresh-token-ttl` (default: `168h`) |

### 7. Refresh Token Rotation & Revocation

- Each refresh token is a single-use opaque token (256-bit random, URL-safe base64)
- On `POST /api/v1/auth/refresh`: the old refresh token is marked `USED` (revoked), a new refresh token is issued
- If a `USED` token is presented again → replay attack detected → all refresh tokens for that user are revoked (family invalidation)
- On `POST /api/v1/auth/logout`: the refresh token associated with the access token is revoked
- Refresh tokens are stored in a `refresh_tokens` table with `userId, tokenHash, status (ACTIVE/USED/REVOKED), expiresAt, createdAt, replacedById`

### 8. Brute-Force Protection

- Login rate limiting: max 5 failed attempts per `(tenantId, email)` per 5-minute window
- Tracked in-memory (Caffeine cache) — sufficient for a single-instance pilot
- After 5 failures: returns 429 Too Many Requests with `Retry-After` header
- Successful login resets the counter

### 9. Security Configuration

- `SecurityFilterChain`: 
  - `permitAll()`: `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/actuator/health`, `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/h2-console/**` (local only)
  - `authenticated()`: all other `/api/**` paths
  - `csrf().disable()` (stateless JWT, no cookies)
  - `sessionManagement().sessionCreationPolicy(STATELESS)`
  - `cors()` with existing CORS configuration
  - `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter)`
- `PasswordEncoder`: `BCryptPasswordEncoder` bean
- `JwtAuthenticationFilter`: extracts Bearer token, validates, populates `SecurityContext`

### 10. RBAC Integration

- `@EnableMethodSecurity` enabled
- `JwtAuthenticationFilter` populates `Authentication` with authorities from the JWT's `authorities` claim
- Future stages can use `@PreAuthorize("hasAuthority('...')")` on controller methods
- For this stage, RBAC enforcement is NOT applied to existing endpoints (they remain open to any authenticated user) — only the auth endpoints themselves are secured

## Consequences

- **Breaking change:** All existing API endpoints (except `/actuator/health`) will now require a valid JWT. The frontend (EXEC-PROMPT-032 frontend) must authenticate before calling them. However, EXEC-PROMPT-032A only adds the backend infrastructure — the frontend is NOT modified in this stage.
- **Migration:** `V10__add_user_password_hash.sql` adds `password_hash` column (nullable). Existing users have no password until set via a future password-setting flow.
- **Test impact:** Existing integration tests that call `/api/v1/**` endpoints will get 401 unless they authenticate. A test helper will be added to mint JWTs for tests.
- **Configuration:** `sanad.security.jwt.secret` must be set in production (env var `JWT_SECRET`). Local/dev profiles use a default test key.
