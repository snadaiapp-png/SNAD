# Stage 13 — Tenant Provisioning Readiness

**Date**: 2026-07-08

---

## Tenant Model

```
Tenant = Organization
Each tenant has:
  - Unique tenant ID (UUID)
  - Display name
  - Memberships (user-tenant relationships)
  - Role grants (RBAC)
  - Isolated data scope
```

## Current Tenant Infrastructure

### Backend (Spring Boot)

```
Tenant entity: com.sanad.platform.organization.Organization
Tenant ID: UUID, stored in all tenant-scoped entities
Tenant isolation: Enforced via tenant_id column in all tables
RBAC: Role grants per tenant (ADMIN, MANAGER, USER, VIEWER)
```

### Frontend (Next.js)

```
Tenant context: TenantContextProvider (React Context)
Tenant selection: TenantPicker component (for multi-tenant users)
Tenant display: ExecutiveShell shows organization name
Tenant scoping: All API calls include tenant context
```

## Tenant Provisioning Flow

```
1. Admin creates organization (POST /api/organizations)
2. Admin becomes organization owner (ADMIN role)
3. Admin invites users (POST /api/users/invite)
4. Users accept invitation → membership created
5. Admin assigns roles (PUT /api/memberships/{id}/role)
6. Users access tenant-scoped workspace
```

## Tenant Isolation Verification

```
Data isolation:
  - All entities have tenant_id column
  - All queries filter by tenant_id
  - Cross-tenant reads: BLOCKED by repository layer
  - Cross-tenant writes: BLOCKED by service layer

Session isolation:
  - Access token includes tenant context
  - Session cannot switch tenants without re-authentication
  - Tenant switching requires TenantPicker (explicit user action)

Cache isolation:
  - Cache keys include tenant_id prefix
  - No cross-tenant cache leakage

RBAC enforcement:
  - All API endpoints check role grants
  - ADMIN: full tenant management
  - MANAGER: user management (no role changes)
  - USER: standard access
  - VIEWER: read-only access
```

## Tenant Provisioning Readiness

```
Tenant creation: READY ✅
User invitation: READY ✅
Role assignment: READY ✅
Tenant isolation: VERIFIED ✅
RBAC enforcement: VERIFIED ✅
Multi-tenant selection: READY ✅ (TenantPicker)

Provisioning Status: READY
```

## Limitations

```
1. Self-service tenant signup: NOT YET AVAILABLE
   - Currently only admins can create organizations
   - Self-service signup requires public registration flow (Stage 14+)

2. Tenant billing: NOT YET CONFIGURED
   - No subscription/billing integration
   - All tenants are currently free (no payment processing)

3. Tenant data export: NOT YET AVAILABLE
   - No self-service data export for tenants
   - Requires admin assistance (Stage 14+)
```
