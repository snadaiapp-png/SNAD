# ADR-032A: Backend Authentication and Session Boundary

## Status

Accepted — 2026-06-21

## Context

SANAD requires a real authentication contract before frontend EXEC-PROMPT-032 can protect its operational panels. The backend now has user lifecycle, tenant, membership, role, and capability models but previously had no trusted principal or session lifecycle.

## Decision

### Access authentication

- Access tokens are short-lived HS256 JWTs, default lifetime 15 minutes.
- Claims: `sub`, `tenant_id`, `email`, `iat`, `exp`, and `iss`.
- Production startup fails when the signing material is blank or shorter than 32 bytes, including combined production profiles.
- Browser API calls send the access token through `Authorization: Bearer`.

### Refresh-session boundary

The approved topology is:

```text
Browser → same-origin Next.js BFF → Render backend
```

- The browser never receives the opaque refresh token in JSON.
- The Render backend returns and accepts the refresh token through `X-SANAD-Refresh-Token` only for server-to-server BFF calls.
- This header is intentionally absent from CORS allowed and exposed headers.
- EXEC-PROMPT-032 stores the refresh token in a Secure, HttpOnly, SameSite cookie owned by the Vercel same-origin BFF.
- The backend creates no production browser cookie; therefore its API remains stateless and CSRF is enforced at the BFF cookie boundary.
- Local/development compatibility may use a local-only HttpOnly cookie and request-body fallback. Neither path is active in production.

### Refresh rotation

- Refresh values are opaque 256-bit random tokens; only SHA-256 hashes are persisted.
- Each refresh is consumed under a pessimistic database lock.
- The old record becomes `USED`, links to exactly one replacement, and the replacement becomes the only active descendant.
- Reuse of a `USED` value triggers family invalidation.
- Refresh is rejected and active tokens are revoked when the user is not `ACTIVE`.
- `replaced_by_id` has a nullable self-referencing foreign key to preserve lineage integrity.

### Tenant isolation

- JWTs carry the authenticated tenant identifier.
- Requests containing the legacy `tenantId` query parameter are rejected with 403 when it differs from the authenticated tenant.
- Services and repositories remain tenant-scoped.
- Resource-ID-only endpoints must validate ownership in their service/repository lookup; negative integration coverage is required for Organizations, Users, Memberships, Roles, Grants, and Capabilities.

### Credentials and bootstrap

- Credentials use BCrypt and are never returned or logged.
- Existing users may initially have no credential.
- Administrative bootstrap is disabled by default and must target an existing active tenant.
- Bootstrap is one-time: an already enrolled account causes startup failure rather than silent credential rotation.
- The administrator receives a tenant-wide `ADMIN` role grant.
- V11 records credential-enrollment audit fields and a forced-rotation flag.
- Bootstrap environment values must be removed immediately after successful enrollment.

### Endpoint contract

| Method | Path | Authentication | Contract |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Public | JSON access response; refresh value in trusted BFF response header |
| POST | `/api/v1/auth/refresh` | Trusted BFF refresh header | Rotated access response and replacement refresh header |
| POST | `/api/v1/auth/logout` | Access JWT | Revokes all active refresh values for the principal |
| GET | `/api/v1/auth/me` | Access JWT | Current user, memberships, and active role grants |

All authentication responses use `Cache-Control: no-store`.

### Security configuration

- `/api/v1/auth/login`, `/api/v1/auth/refresh`, and health probes are public.
- All other `/api/**` routes require authentication.
- H2 console access and frame-option relaxation exist only in the `local` profile.
- Production Swagger and non-health actuator endpoints remain disabled.
- CORS allows the approved frontend origin and ordinary access-token headers only; credentials are disabled at the Render API boundary.

## Consequences

- Backend APIs become inaccessible to anonymous callers.
- Frontend EXEC-PROMPT-032 must implement the same-origin BFF before operational panels can call protected APIs.
- Access JWT revocation remains bounded by its short lifetime; refresh revocation is immediate.
- In-memory login throttling is pilot-only and must move to shared infrastructure before multi-instance operation.
- Full capability enforcement remains a separate authorization stage; authentication and tenant isolation cannot be deferred.
