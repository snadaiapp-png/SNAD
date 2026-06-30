# Stage 03A — Pagination Integration Report

**Stage:** 03A
**Branch:** `infra/03a-api-contract-enforcement`

## 1. Objective

Apply the pagination standard to all 8 collection endpoints — not just create the `PageResponse` / `PageRequestParams` types.

## 2. Endpoints Migrated

All 8 collection endpoints now return `ResponseEntity<PageResponse<T>>` and accept `page`, `size`, and `sort` query parameters:

| Endpoint | Resource | Sort Allowlist |
|----------|----------|----------------|
| `GET /api/v1/users` | `UserResponse` | `id, email, displayName, status, createdAt, updatedAt` |
| `GET /api/v1/organizations` | `OrganizationResponse` | `id, name, status, createdAt, updatedAt` |
| `GET /api/v1/organizations/{orgId}/memberships` | `OrganizationMembershipResponse` | `id, organizationId, userId, email, status, createdAt, updatedAt` |
| `GET /api/v1/access/roles` | `RoleResponse` | `id, code, name, status, createdAt, updatedAt` |
| `GET /api/v1/access/capabilities` | `CapabilityResponse` | `id, code, name, status, createdAt, updatedAt` |
| `GET /api/v1/access/roles/{roleId}/access-items` | `RoleAccessResponse` | `id, roleId, capabilityId, createdAt` |
| `GET /api/v1/users/{userId}/memberships` | `OrganizationMembershipResponse` | `id, organizationId, userId, email, status, createdAt, updatedAt` |
| `GET /api/v1/access/users/{userId}/role-links` | `UserAccessResponse` | `id, userId, roleId, organizationId, status, createdAt` |

## 3. Repository Layer

Each collection endpoint uses a paginated repository method with explicit tenant scoping at the JPQL level:

```java
@Query(
    value = "SELECT u FROM User u WHERE u.tenantId = :tenantId",
    countQuery = "SELECT COUNT(u) FROM User u WHERE u.tenantId = :tenantId")
Page<User> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);
```

The `countQuery` is tenant-scoped — `totalElements` and `totalPages` are computed from the tenant-restricted count, NOT from a cross-tenant query.

## 4. Sort Allowlist Enforcement

The `SortAllowlist.toPageable()` helper validates sort parameters against a per-resource allowlist:

- **Rejects**: field not in allowlist, direction other than `asc`/`desc`, nested paths (e.g. `tenant.name`), sensitive fields (`password`, `secret`, `token`), SQL expressions (parens, semicolons, quotes).
- **Returns**: `SANAD-PAG-002` / HTTP 400 on any violation.
- **Multiple sort parameters**: supported via `?sort=name,asc&sort=createdAt,desc`. The parser handles Spring's comma-splitting behavior (each `sort` param value is split into a list entry).

## 5. PageRequestParams Hard Limits

`PageRequestParams` enforces:
- `page >= 0` (else `SANAD-PAG-001`)
- `1 <= size <= 100` (else `SANAD-PAG-001`)
- Sort parameters are validated per-resource via `SortAllowlist`.

The compact constructor throws `InvalidPaginationException` rather than clamping — callers receive a 400 so they know their input was rejected.

## 6. Tenant-Aware Pagination Tests

`TenantAwarePaginationIntegrationTest` (10 tests) verifies:

- Tenant A content contains only A records
- Tenant A `totalElements` excludes B records
- Tenant B `totalElements` excludes A records
- Sorting does not cross tenant boundary
- Second page remains tenant-scoped
- Invalid tenant context rejected before query
- Oversized size rejected (400)
- Invalid sort field rejected with `SANAD-PAG-002` (400)
- Invalid sort direction rejected (400)
- Multiple sort parameters applied in order
- Default page params return first page

## 7. Backwards Compatibility

The unpaginated `listXxx(tenantId)` methods remain on the services for existing callers (e.g. internal services, tests). Only the REST controllers were migrated to the paginated variants. This preserves the public service-layer contract while enforcing pagination at the API boundary.

## 8. Related Debts

- CD-03-P1-003 (collection endpoints pagination) — CLOSED
