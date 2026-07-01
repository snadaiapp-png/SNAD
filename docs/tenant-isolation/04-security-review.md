# Stage 04 — Security Review

## 1. Scope

This review covers the tenant isolation hardening implemented in Stage 04, evaluated against the requirements in the Stage 04 prompt (sections 1-45).

## 2. Findings

### 2.1 Tenant Context Infrastructure (§7-8) — PASS
- Central `TenantContext` record established
- `TenantContextProvider` interface + `ThreadLocalTenantContextProvider` implementation
- `TenantContextFilter` registered in Spring Security chain after `JwtAuthenticationFilter`
- `TenantResolver` helper for services
- `TenantRlsBinder` for PostgreSQL `SET LOCAL`
- Lifecycle tests (10 tests) verify: no context sharing, no child-thread propagation, clear-on-failure, immutability

### 2.2 Client tenantId Trust Removal (§9) — PASS (transitional)
- `JwtAuthenticationFilter` validates client-supplied `tenantId` query param against JWT tenant (403 on mismatch)
- `TenantResolver.validateClientSelector()` available for transitional validation
- Existing controllers retain `@RequestParam UUID tenantId` for backwards API compatibility
- The service layer always receives the verified tenantId (filter rejects mismatches before controller)
- No `X-Tenant-Id` header trust

### 2.3 Repository Enforcement (§11-12) — PASS
- All tenant-owned repository methods include `tenantId` predicate
- No unscoped `findAll()` / `findById()` / `deleteById()` on tenant-owned repos
- Static gate (`check-tenant-isolation.sh`) verifies this on every CI run
- Two documented cross-tenant exceptions: `findAllByEmail` (login) and `findByTokenHash` (token rotation) — both validated by service-layer membership/tenant checks

### 2.4 Nested Resource Isolation (§13) — PASS
- `OrganizationMembershipController` validates `organizationId` belongs to tenant before accessing memberships
- `RoleAccessController` validates `roleId` belongs to tenant before accessing access-items
- `UserMembershipController` extracts tenantId from JWT (not query param)
- `UserAccessController` validates `userId` belongs to tenant before accessing role-links

### 2.5 PostgreSQL RLS (§15-19) — PASS
- V17 migration enables RLS on all 8 tenant-owned tables
- `FORCE ROW LEVEL SECURITY` ensures even table owner is subject to policies
- `USING` and `WITH CHECK` clauses enforce both read and write isolation
- `SET LOCAL app.current_tenant_id` scoped to transaction
- Missing context → fail-closed (0 rows)
- Migration is in `db/migration-pg-only/` (H2 local profile skips it)
- CI `tenant-isolation` job runs against PostgreSQL 16

### 2.6 Global Tables (§20) — PASS
- `tenants` table: SECURITY_GLOBAL (root table, no tenant_id column)
- `access_capabilities` table: GLOBAL_REFERENCE (platform-wide catalog)
- Both documented in `04-global-table-exceptions.json` with justification

### 2.7 Authentication & Session Binding (§22) — PASS
- JWT `tenant_id` claim verified by signature
- `session_version` check prevents stale token use after logout/password-change
- Refresh token rotation validates tenant
- Tenant selector validated against JWT tenant (403 on mismatch)

### 2.8 Capability Evaluation (§23) — PASS
- `@RequireCapability` aspect evaluates capabilities per-request
- Capabilities are tenant-scoped (a user's role in Tenant A does not grant access in Tenant B)
- `TenantBindingSecurityIntegrationTest` verifies cross-tenant capability denial

### 2.9 Caches (§24) — PASS (no caches)
- No application-level caches exist
- Policy documented for future cache implementation

### 2.10 Background Jobs & Async (§25) — PASS (no jobs)
- No `@Async`, `@Scheduled`, or background workers exist
- Policy documented for Stage 12

### 2.11 Events (§26) — PASS (no events)
- No event broker in the stack
- Policy documented for future stages

### 2.12 Logs (§27) — PASS
- MDC populated with `requestId`, `tenantId`, `userId` by `TenantContextFilter`
- MDC cleared in `finally` to prevent leakage
- No secrets, tokens, passwords, or full request bodies logged
- Container smoke test verifies no env var values in logs (Stage 03A)

### 2.13 Frontend Tenant Selection (§28) — PASS
- Frontend uses `SecurityPermitAllTestConfig` in tests; production uses JWT-based auth
- No `X-Tenant-Id` header; tenant comes from JWT
- `tenantId` query param treated as selector, validated by backend

### 2.14 Static Gate (§32) — PASS
- `scripts/ci/check-tenant-isolation.sh` scans for:
  - Unscoped repository methods on tenant-owned repos
  - tenantId in Request DTOs (mass assignment risk)
  - Tenant-owned tables without classification
  - Native queries without tenant predicate
  - Client tenantId validation in JwtAuthenticationFilter
  - TenantContext infrastructure files
  - RLS migration existence

### 2.15 CI Job (§33) — PASS
- `tenant-isolation` job added to `quality-gate.yml`
- Runs against PostgreSQL 16
- Executes tenant isolation test suite + static gate
- `quality-gate` aggregation depends on it
- Total jobs: 14 (13 mandatory + 1 aggregation)

## 3. Residual Risk

### 3.1 Deferred Security Debt (§0)
- CD-00-P0-001: Historical admin credential exposure — BLOCKED_OWNER_ACTION
- CD-00-P0-002: Historical email-proxy fallback — BLOCKED_OWNER_ACTION
- These do NOT block development progression but DO block production release

### 3.2 H2 Local Profile
- RLS is NOT enforced in the local profile (H2 doesn't support it)
- Developers must run against PostgreSQL (via CI or Docker) to verify RLS
- Application-layer scoping remains the source of truth in local profile

### 3.3 Transitional tenantId Query Param
- Controllers still accept `@RequestParam UUID tenantId` for backwards API compatibility
- The filter rejects mismatches (403), but the ideal end-state is to remove the parameter entirely
- Tracked as potential future debt (not blocking)

## 4. Conclusion

Stage 04 tenant isolation hardening is COMPLETE. All acceptance criteria from §44 are met:

- Tenant inventory: COMPLETE (10 entities classified)
- Tenant trust boundary: DOCUMENTED
- Server-established tenant context: PASS
- Client tenant injection protection: PASS
- Tenant context cleanup: PASS
- Tenant-scoped CRUD: PASS
- Tenant-scoped pagination: PASS
- Nested resource isolation: PASS
- Cross-tenant reads: DENIED (403 + RLS)
- Cross-tenant writes: DENIED (403 + RLS)
- RLS: PASS (8 tables, PostgreSQL 16)
- Missing DB tenant context: FAIL-CLOSED
- Static tenant isolation gate: PASS
- tenant-isolation CI job: PASS
- Stage-04 open P0: 0
- Stage-04 open blocking P1: 0
