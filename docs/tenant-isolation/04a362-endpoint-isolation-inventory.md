# Stage 04A.3.6.2 — Endpoint Isolation Inventory

## Purpose

This document inventories the `OrganizationController` endpoints and
records which ones are covered by **IMPLEMENTED CRUD ENDPOINT ISOLATION**
tests under the real JWT authentication chain with RLS enforcement.

The term `IMPLEMENTED CRUD ENDPOINT ISOLATION` refers specifically to
endpoints that have a corresponding tenant-isolation integration test
exercising the full filter chain (`JwtAuthenticationFilter` →
`JwtSessionValidationService` → `TenantContextFilter` →
`RequireCapability` → controller → service → RLS-protected repository).

It does NOT use the term `FULL CRUD ISOLATION`, because UPDATE, DELETE,
and ARCHIVE endpoints are not yet covered by isolation tests in this
stage.

## Scope

Controller: `com.sanad.platform.organization.api.OrganizationController`

Base path: `/api/v1/organizations`

## Endpoint Inventory

| Operation | HTTP Method | Path                              | Isolation Status          | Test Class                                                           | Test Method                              |
|-----------|-------------|-----------------------------------|---------------------------|----------------------------------------------------------------------|------------------------------------------|
| LIST      | GET         | `/api/v1/organizations`           | IMPLEMENTED_AND_TESTED    | `TenantCrudIsolationIntegrationTest`                                 | `tokenA_listOrgsA_200`                   |
| GET       | GET         | `/api/v1/organizations/{id}`      | IMPLEMENTED_AND_TESTED    | `TenantCrudIsolationIntegrationTest`                                 | `organizationSameTenantGet_200`          |
|           |             |                                   |                           | `TenantCrudIsolationIntegrationTest`                                 | `organizationCrossTenantGet_404`         |
| CREATE    | POST        | `/api/v1/organizations`           | IMPLEMENTED_AND_TESTED    | `TenantCrudIsolationIntegrationTest`                                 | `tokenA_createOrgA_201`                  |
|           |             |                                   |                           | `TenantCrudIsolationIntegrationTest`                                 | `organizationSameTenantCreate_201`       |
|           |             |                                   |                           | `TenantCrudIsolationIntegrationTest`                                 | `massAssignment_databaseTenantForcedToA` |
| UPDATE    | PUT         | `/api/v1/organizations/{id}`      | NOT_IMPLEMENTED_ENDPOINT  | —                                                                                                               |
| DELETE    | —           | —                                 | NOT_IMPLEMENTED_ENDPOINT  | —                                                                                                               |
| ARCHIVE   | PATCH       | `/api/v1/organizations/{id}/archive` | NOT_IMPLEMENTED_ENDPOINT | —                                                                                                               |

The `UPDATE`, `DELETE`, and `ARCHIVE` operations exist as controller
methods (PUT, soft-delete via PATCH `/archive`), but they are NOT
covered by tenant-isolation integration tests in this stage. They are
therefore recorded as `NOT_IMPLEMENTED_ENDPOINT` in the **endpoint
isolation** scope. A future stage will add isolation tests for these
operations and update this inventory accordingly.

The `DELETE` operation is intentionally not exposed as a hard-delete
endpoint. The SANAD platform policy is soft-delete via `ARCHIVE`. There
is no `DELETE /api/v1/organizations/{id}` route.

## Test Coverage Matrix

### Same-tenant authorized

| Test                                          | Token | Selector | Resource           | Expected | Status  |
|-----------------------------------------------|-------|----------|--------------------|----------|---------|
| `tokenA_listOrgsA_200`                        | A     | A        | LIST               | 200      | PASS    |
| `organizationSameTenantGet_200`               | A     | A        | Org A (by id)      | 200      | PASS    |
| `tokenA_createOrgA_201`                       | A     | A        | CREATE             | 201      | PASS    |
| `organizationSameTenantCreate_201`            | A     | A        | CREATE             | 201      | PASS    |
| `tokenA_getUserA_200`                         | A     | A        | User A (by id)     | 200      | PASS    |

### Cross-tenant denied

| Test                                          | Token | Selector | Resource           | Expected | Status  |
|-----------------------------------------------|-------|----------|--------------------|----------|---------|
| `tokenA_selectorB_403`                        | A     | B        | LIST               | 403      | PASS    |
| `tokenA_getUserB_404`                         | A     | A        | User B (by id)     | 404      | PASS    |
| `tokenB_selectorA_403`                        | B     | A        | LIST               | 403      | PASS    |
| `organizationCrossTenantGet_404`              | A     | A        | Org B (by id)      | 404      | PASS    |

### Mass-assignment protection

| Test                                          | Token | Selector | Body tenantId      | Expected DB tenant_id | Status  |
|-----------------------------------------------|-------|----------|--------------------|-----------------------|---------|
| `massAssignment_tenantIdIgnored`              | A     | A        | B                  | A (response-only)     | PASS    |
| `massAssignment_databaseTenantForcedToA`      | A     | A        | B                  | A (DB-verified)       | PASS    |

The `massAssignment_databaseTenantForcedToA` test reads the created
organization row directly via the Fixture DataSource (which bypasses
RLS) and asserts that the stored `tenant_id` column equals the JWT's
tenant (Tenant A), NOT the body-supplied `tenantId` (Tenant B). This is
database-level verification, not just response-level.

### Authentication failures

| Test                                          | Token        | Expected | Status  |
|-----------------------------------------------|--------------|----------|---------|
| `noToken_401`                                 | (none)       | 401      | PASS    |
| `malformedToken_401`                          | `not-a-jwt`  | 401      | PASS    |

### Capability enforcement

| Test                                          | Token   | Capability       | Expected | Status  |
|-----------------------------------------------|---------|------------------|----------|---------|
| `tokenNoCap_403`                              | NoCap   | (none)           | 403      | PASS    |

## Out-of-Scope Operations

The following `OrganizationController` operations exist in the source
code but are NOT covered by isolation tests in this stage. They will be
covered in a future stage.

| Operation | Method | Path                                   | Reason                                                    |
|-----------|--------|----------------------------------------|-----------------------------------------------------------|
| UPDATE    | PUT    | `/api/v1/organizations/{id}`           | No tenant-isolation integration test exercises this route |
| ACTIVATE  | PATCH  | `/api/v1/organizations/{id}/activate`  | No tenant-isolation integration test exercises this route |
| DEACTIVATE| PATCH  | `/api/v1/organizations/{id}/deactivate`| No tenant-isolation integration test exercises this route |
| ARCHIVE   | PATCH  | `/api/v1/organizations/{id}/archive`   | No tenant-isolation integration test exercises this route |

A unit-level slice test (`OrganizationControllerTest`) covers these
routes with a mocked service layer, but that test does NOT exercise the
real JWT filter chain, RLS, or capability enforcement. It is therefore
out of scope for `IMPLEMENTED CRUD ENDPOINT ISOLATION`.

## Closure-Debt Cross-Reference

| Debt ID         | Title                                                                              | Closure Evidence                                                                                 |
|-----------------|------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| CD-04-P1-005    | Controllers continue to pass client-supplied tenantId directly to repositories     | `massAssignment_databaseTenantForcedToA` verifies DB row tenant_id = JWT tenant, not body value. |
| CD-04-P1-019    | Application CRUD tenant-isolation certification does not cover Organization resource | `organizationSameTenantGet_200`, `organizationCrossTenantGet_404`, `organizationSameTenantCreate_201`. |

## Final Status

```text
IMPLEMENTED CRUD ENDPOINT ISOLATION: PASS
ORGANIZATION CROSS-TENANT GET: DENIED
MASS ASSIGNMENT DATABASE VERIFICATION: PASS
```

Future stages will expand the inventory to cover UPDATE, ACTIVATE,
DEACTIVATE, and ARCHIVE operations.
