# Stage 04 — Repository Enforcement

## 1. Rule

Every repository method on a TENANT_OWNED entity MUST include a `tenantId` predicate in its query. Unscoped `findById`, `findAll`, `existsById`, `deleteById` (inherited from JpaRepository) are FORBIDDEN on tenant-owned repos.

## 2. Enforcement

### Application layer
- All repository methods take `tenantId` as a parameter
- Services obtain `tenantId` from `TenantResolver.requireTenantId()` (never from client)
- Controllers validate client-supplied `tenantId` against `TenantContext` (§9 transitional)

### Database layer
- PostgreSQL RLS policies (V17) enforce `tenant_id = current_setting('app.current_tenant_id')` on all 8 tenant-owned tables
- Even if the application layer fails to scope a query, RLS returns 0 rows from other tenants
- Missing `app.current_tenant_id` setting → RLS fails closed (0 rows)

### Static gate
- `scripts/ci/check-tenant-isolation.sh` scans for unscoped repository methods
- CI `tenant-isolation` job runs the gate on every push

## 3. Repository Method Inventory

### UserRepository
- `findByTenantId(tenantId)` ✓ scoped
- `findByTenantId(tenantId, pageable)` ✓ scoped
- `findByTenantIdAndId(tenantId, id)` ✓ scoped
- `findByTenantIdAndEmail(tenantId, email)` ✓ scoped
- `existsByTenantIdAndEmail(tenantId, email)` ✓ scoped
- `findSessionVersionByTenantIdAndId(tenantId, id)` ✓ scoped
- `findAllByEmail(email)` — CROSS-TENANT (login only; membership-validated by AuthService)

### OrganizationRepository
- `findByTenantId(tenantId)` ✓ scoped
- `findByTenantId(tenantId, pageable)` ✓ scoped
- `findByTenantIdAndId(tenantId, id)` ✓ scoped
- `existsByTenantIdAndName(tenantId, name)` ✓ scoped

### OrganizationMembershipRepository
- All 8 methods scoped by `tenantId` ✓

### RoleRepository
- All methods scoped by `tenantId` ✓

### RoleCapabilityRepository
- All methods scoped by `tenantId` ✓

### UserRoleGrantRepository
- All methods scoped by `tenantId` ✓

### RefreshTokenRepository
- `findByTokenHash(hash)` — cross-tenant lookup (token-hash only; validated by tenant_id in query result)
- All other methods scoped by `tenantId` ✓

### PasswordResetTokenRepository
- `findByTokenHash(hash)` — cross-tenant lookup (token-hash only; validated by tenant_id in query result)
- All other methods scoped by `tenantId` ✓

## 4. Exceptions

Two cross-tenant lookups exist by design:
1. `UserRepository.findAllByEmail(email)` — email-only login; AuthService validates the resulting tenant set
2. `RefreshTokenRepository.findByTokenHash(hash)` / `PasswordResetTokenRepository.findByTokenHash(hash)` — token-based lookups; the token hash is a cryptographic secret, and the query result is validated against the tenant context

These are documented in `04-tenant-domain-inventory.json` with `exceptionJustification`.

## 5. In-memory Tenant Filtering (§12)

FORBIDDEN: `repository.findAll()` → filter in Java → subList

All pagination, count, sort, and aggregation MUST occur at the database level with a tenant-scoped query. The Stage 03A pagination implementation uses `Page<T>` with tenant-scoped JPQL queries and `countQuery` — no in-memory filtering.
