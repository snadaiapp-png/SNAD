# Stage 04A.1 — Authentication Under RLS

## 1. Problem

The previous authentication flow queried the RLS-protected `users` table BEFORE establishing TenantContext:

```
JWT verification → UserRepository session-version query → Authentication created → TenantContextFilter
```

Under RLS with `FORCE ROW LEVEL SECURITY`, the `users` table returns 0 rows when `app.current_tenant_id` is not set. This means authentication would fail for every request.

## 2. Solution

Stage 04A.1 §4 introduces `JwtSessionValidationService`:

```
Cryptographically validate JWT
→ Extract signed tenantId, userId, jti and sessionVersion
→ Establish provisional TenantContext (PROVISIONAL_TOKEN_VALIDATION source)
→ Execute session validation inside @Transactional(readOnly = true)
  → TenantAwareJpaTransactionManager sets app.current_tenant_id on the connection
  → Query users table (RLS now allows the row)
  → Verify session version, user status, tenant status
→ Create Authentication
→ TenantContextFilter establishes final TenantContext (JWT_CLAIM source)
→ Continue request
→ Clear context in finally
```

## 3. Implementation

### JwtSessionValidationService (interface)
- `ValidatedSession validate(VerifiedJwtClaims claims)`
- `VerifiedJwtClaims` record: tenantId, userId, tokenId (jti), email, sessionVersion, rotationRequired
- `ValidatedSession` record: tenantId, userId, tokenId, email, currentSessionVersion, rotationRequired, userActive, tenantActive

### JwtSessionValidationServiceImpl
- `@Transactional(readOnly = true, propagation = REQUIRED)`
- Establishes provisional TenantContext from verified JWT claims
- Calls `TenantRlsBinder.bindTenantToCurrentTransaction()` to set RLS context
- Queries `UserRepository.findSessionVersionByTenantIdAndId()` (now RLS-safe)
- Loads User entity to check status
- Loads Tenant entity to check status
- Clears provisional context in `finally`

### JwtAuthenticationFilter (updated)
- No longer calls `UserRepository` directly
- Calls `sessionValidationService.validate(verifiedClaims)`
- If session is null → 401 (without disclosing which check failed)
- If session is valid → creates Authentication with details (tenant_id, user_id, jti, session_version)

## 4. Fail-Closed Behavior

- Missing user → 401 (no disclosure of tenant membership)
- Session version mismatch → 401
- Suspended user → 401
- Archived tenant → 401
- Any DB error → 401 (provisional context cleared in finally)

## 5. Test Coverage

- `TenantCrudIsolationIntegrationTest` — verifies cross-tenant authentication is rejected
- `TenantBindingSecurityIntegrationTest` — verifies tenantId mismatch is rejected with 403
- CI `tenant-isolation` job runs against PostgreSQL 16 with runtime role
