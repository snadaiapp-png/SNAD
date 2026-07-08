# Control Plane Access Governance

## Access Model

The Control Plane is governed by a two-layer access control:

### Layer 1: Tenant Matching (ControlPlaneAccessGuard)

```java
@Value("${sanad.control-plane.tenant-id:}")
```

Only users belonging to the configured Control Plane Tenant (`SANAD_CONTROL_PLANE_TENANT_ID`) can access any Control Plane endpoint. This is enforced by `ControlPlaneAccessGuard.require()` on every request.

This check is INDEPENDENT from normal tenant RBAC. A tenant administrator cannot access the Control Plane unless their tenant matches the configured control plane tenant.

### Layer 2: Capability-Based Authorization (@RequireCapability)

After tenant matching, endpoints require specific capabilities:
- `ROLE.READ`: Required for all GET endpoints (dashboard, tenants, systems, audit)
- `ROLE.WRITE`: Required for all POST/PATCH/PUT/DELETE endpoints (create tenant, change status, etc.)

Capabilities are assigned via role grants. The control plane admin must have a role with `ROLE.READ` and `ROLE.WRITE` capabilities.

## Access Check Endpoint

```http
GET /api/v1/control-plane/access-check
```

Returns:
```json
{
  "authenticated": true,
  "controlPlaneConfigured": true,
  "controlTenantMatched": true,
  "capabilities": ["ROLE.READ", "ROLE.WRITE"],
  "canRead": true,
  "canWrite": true
}
```

This endpoint does NOT require capabilities — it's a diagnostic that reports what the user CAN do.

## Security Rules

1. **No disabling ControlPlaneAccessGuard**: The guard is always active on Control Plane endpoints.
2. **No tenant admin escalation**: A regular tenant admin cannot access the Control Plane.
3. **No creation without tenant**: Organization and membership creation requires an existing tenant.
4. **No creation without subscription**: Organization and membership creation requires an active subscription.
5. **No exceeding limits**: maxUsers and maxOrganizations from the plan are enforced.
6. **No secret printing**: No passwords, tokens, or GitHub secrets are logged.
7. **No direct SQL from frontend**: All database operations go through the backend API.
8. **No real customer data for testing**: Test identities are separate from real customers.

## Diagnostic Endpoint Security

The `access-check` endpoint:
- Does NOT expose the control plane tenant UUID
- Does NOT expose user UUIDs
- Does NOT expose secrets or tokens
- Only reports boolean flags and capability names
- Accessible by any authenticated user (for diagnostics)

## Audit Trail

All Control Plane operations are audited via `PlatformAuditService`:
- `TENANT.PROVISION`: When a tenant is created
- `TENANT.STATUS_CHANGE`: When tenant status changes
- `SUBSCRIPTION.CREATE`: When a subscription is created
- `ORGANIZATION.CREATE`: When an organization is created
- `MEMBERSHIP.CREATE`: When a membership is created
- All entries include: timestamp, actor, tenant, action, result

Audit log is:
- Immutable (append-only)
- Retained for 1 year
- Accessible only by Control Plane users with ROLE.READ
