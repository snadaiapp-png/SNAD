# Stage 04 — Tenant Isolation Trust Boundary

## 1. Trust Model

The tenant isolation trust boundary is established at the HTTP layer and enforced through every subsequent layer down to the PostgreSQL database.

```
Client Request
    ↓ (UNTRUSTED)
HTTP Filter Chain
    ↓
RequestIdFilter (generates/correlates X-Request-Id)
    ↓
JwtAuthenticationFilter (verifies JWT signature, extracts tenant_id claim,
    validates session_version, rejects tenantId param mismatch with 403)
    ↓ (TRUSTED: verified JWT claims)
TenantContextFilter (builds TenantContext from verified claims,
    populates ThreadLocal + MDC, clears in finally)
    ↓ (TRUSTED: TenantContext established)
Controller (@RequireCapability evaluates capability in tenant scope)
    ↓
Service (uses TenantResolver.requireTenantId() — never client tenantId)
    ↓
Repository (all queries scoped by tenant_id predicate)
    ↓
PostgreSQL (RLS policy: tenant_id = current_setting('app.current_tenant_id'))
    ↓ (DATABASE-LEVEL ENFORCEMENT)
Response
```

## 2. Trust Boundary Rules

### 2.1 Client-supplied values (UNTRUSTED)
- `tenantId` query parameter → SELECTOR ONLY; validated against JWT tenant (403 on mismatch)
- `X-Tenant-Id` header → NOT USED (tenant comes from JWT, not headers)
- Request body `tenantId` field → IGNORED by service; service uses TenantContext.tenantId
- URL path `tenantId` → NOT present in any route

### 2.2 Server-established values (TRUSTED)
- JWT `tenant_id` claim → verified by JwtAuthenticationFilter (signature + session_version)
- TenantContext.tenantId → from verified JWT, stored in ThreadLocal
- `app.current_tenant_id` PostgreSQL session variable → from TenantContext, SET LOCAL per transaction

### 2.3 MDC values (LOGGING ONLY — NOT AUTHORITY)
- `requestId`, `tenantId`, `userId` in MDC are for structured logging only
- MDC is NEVER read as a source of authorization decisions
- MDC is cleared in the TenantContextFilter's `finally` block

## 3. Data Classification

| Classification | Count | RLS | Example |
|---------------|-------|-----|---------|
| TENANT_OWNED | 8 | YES | users, organizations, roles |
| GLOBAL_REFERENCE | 1 | NO | access_capabilities (platform catalog) |
| SECURITY_GLOBAL | 1 | NO | tenants (root table) |
| TENANT_SHARED | 0 | N/A | (none currently) |

## 4. Cross-tenant Administrative Access

No cross-tenant admin API exists. If needed in the future, it must:
- Be explicit and documented
- Require a separate `PLATFORM.TENANT_SUPPORT` capability
- Not be accessible via ordinary APIs
- Be logged and tested
- Not rely on a generic `isAdmin` bypass

## 5. Fail-Closed Semantics

- Missing TenantContext → `TenantContextException` (403)
- Missing `app.current_tenant_id` in PostgreSQL → RLS returns 0 rows (fail-closed)
- Client tenantId mismatch → 403 `SANAD-TEN-002`
- Expired/revoked session → 401 (session_version mismatch)
- Archived tenant → login rejected at AuthService level
