# Security and Isolation Review

## 1. Authentication
- **JWT-based**: Access tokens (15min TTL) + refresh tokens (168h TTL)
- **Session versioning**: `session_version` claim prevents revoked sessions
- **Password hashing**: BCrypt (strength 10)
- **Rate limiting**: Login rate limit (5 attempts per 5min window)
- **Status**: OPERATIONAL

## 2. Authorization (RBAC)
- **Capability-based**: Fine-grained capabilities (USER.READ, ORGANIZATION.CREATE, etc.)
- **Role assignments**: Users assigned roles; roles have capabilities
- **@RequireCapability aspect**: Method-level enforcement
- **Status**: OPERATIONAL

## 3. Multi-Tenant Isolation
- **TenantContext**: ThreadLocal context established after JWT validation
- **TenantContextFilter**: Runs after JwtAuthenticationFilter
- **Application-level RLS**: All queries filtered by tenant_id
- **Database-level RLS**: NOT ON MAIN — pg-only migrations not merged
- **Status**: APPLICATION-LEVEL OPERATIONAL, DATABASE-LEVEL MISSING

## 4. Tenant Selector Validation
- `tenantId` request parameter validated against JWT tenant_id claim
- Mismatch → 403 Forbidden
- **Status**: OPERATIONAL

## 5. CORS Configuration
- Exact-origin allowlist (no wildcards)
- Production enforces HTTPS-only
- Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
- **Status**: OPERATIONAL

## 6. Secret Management
- All secrets via environment variables (`${VAR}` in YAML)
- No hardcoded secrets in source code
- Gitleaks scanning in CI
- JWT secret: min 32 bytes, auto-generated in non-prod
- **Status**: OPERATIONAL

## 7. Input Validation
- Jakarta Bean Validation (`@Valid`, `@NotNull`, etc.)
- GlobalExceptionHandler catches validation errors
- SQL injection: PreparedStatement everywhere (no string concatenation)
- **Status**: OPERATIONAL

## 8. Error Handling
- Centralized GlobalExceptionHandler
- Never exposes internal stack traces to client
- Safe generic error messages for unexpected exceptions
- Request ID correlation via MDC
- **Status**: OPERATIONAL

## 9. Audit Logging
- Platform audit logs (V17)
- Audit events with tenant, actor, action, outcome
- NOT YET connected to CRM mutations
- **Status**: PARTIAL — needs CRM audit integration

## 10. Known Security Gaps

| Gap | Risk | Resolution |
|-----|------|------------|
| No database-level RLS on main | Medium | Add pg-only migrations |
| CRM tables without RLS policies | Medium | Add RLS in forward migrations |
| No audit logging for CRM CRUD | Low | Add audit calls in CrmService |
| No rate limiting on CRM APIs | Low | Add rate limiting filter |
| Mass assignment on CRM endpoints | Low | Verify DTOs use allowlist |

