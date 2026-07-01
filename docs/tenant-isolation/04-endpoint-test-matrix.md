# Stage 04 — Endpoint Test Matrix

See `04-endpoint-test-matrix.json` for the machine-readable version.

## Summary

| Classification | Count |
|---------------|-------|
| TESTED | 23 |
| GLOBAL_EXEMPT_WITH_JUSTIFICATION | 4 |
| NOT_APPLICABLE | 0 |
| UNCLASSIFIED | 0 |
| **Total** | **29** |

## Tested Endpoints

Every tenant-owned endpoint has at least one integration test verifying cross-tenant denial:

- **Users** (8 endpoints): `TenantCrudIsolationIntegrationTest`, `TenantBindingSecurityIntegrationTest`
- **Organizations** (6 endpoints): `OrganizationTenantIsolationTest`, `TenantCrudIsolationIntegrationTest`, `TenantAwarePaginationIntegrationTest`
- **Memberships** (3 endpoints): `OrganizationMembershipApiIntegrationTest`, `UserMembershipControllerTest`
- **Access Roles** (3 endpoints): `TenantBindingSecurityIntegrationTest`
- **Access Items / Role Links** (2 endpoints): `TenantBindingSecurityIntegrationTest`
- **Auth** (1 endpoint): `AuthApiIntegrationTest`, `TokenRevocationIntegrationTest`

## Global Exempt Endpoints

- `GET /api/v1/access/capabilities` — GLOBAL_REFERENCE (platform catalog)
- `POST /api/v1/access/capabilities` — GLOBAL_REFERENCE (platform admin only)
- `POST /api/v1/auth/login` — Authentication endpoint (no tenant context yet)
- `POST /api/v1/auth/refresh` — Token refresh (tenant from refresh token)

## Test Cases Covered

For each tested endpoint:
- Cross-tenant access → 403 (tenant mismatch) or 404 (not found)
- Same-tenant access → 200 (accepted)
- Missing tenantId → 400 (missing required param)
- Invalid tenantId format → 400 (type mismatch)
- Cross-tenant ID enumeration → 403 or 404 (no data leakage)
