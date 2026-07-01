# Stage 04 — Tenant Domain Inventory

See `04-tenant-domain-inventory.json` for the machine-readable version.

## Summary

| Classification | Count | RLS | Tables |
|---------------|-------|-----|--------|
| TENANT_OWNED | 8 | YES | organizations, organization_memberships, users, roles, role_capabilities, user_role_assignments, refresh_tokens, password_reset_tokens |
| GLOBAL_REFERENCE | 1 | NO | access_capabilities |
| SECURITY_GLOBAL | 1 | NO | tenants |
| TENANT_SHARED | 0 | N/A | (none) |
| **Total** | **10** | | |

## Classification Definitions

- **TENANT_OWNED** — Data owned by exactly one tenant; must have immutable `tenant_id`.
- **TENANT_SHARED** — Shared relation with explicit access rules; not automatically global.
- **GLOBAL_REFERENCE** — Reference data not specific to a single tenant (e.g. capability catalog).
- **SECURITY_GLOBAL** — Centralized security data not treated as tenant-owned.

## Entity Details

### TENANT_OWNED (8 entities)

1. **Organization** (`organizations`) — tenant_id NOT NULL, FK to tenants; RLS enabled
2. **OrganizationMembership** (`organization_memberships`) — tenant_id NOT NULL, FK to tenants; RLS enabled
3. **User** (`users`) — tenant_id NOT NULL, FK to tenants; RLS enabled
4. **Role** (`roles`) — tenant_id NOT NULL, FK to tenants; RLS enabled
5. **RoleCapability** (`role_capabilities`) — tenant_id NOT NULL, composite FK to roles; RLS enabled
6. **UserRoleAssignment** (`user_role_assignments`) — tenant_id NOT NULL, composite FK to users/roles/orgs; RLS enabled
7. **RefreshToken** (`refresh_tokens`) — tenant_id NOT NULL, composite FK to users; RLS enabled
8. **PasswordResetToken** (`password_reset_tokens`) — tenant_id NOT NULL, composite FK to users; RLS enabled

### GLOBAL_REFERENCE (1 entity)

- **AccessCapability** (`access_capabilities`) — Platform-wide RBAC capability catalog. No tenant_id column. Seeded by V14/V15 migrations. Read access: any authenticated user. Write access: CAPABILITY.MANAGE.

### SECURITY_GLOBAL (1 entity)

- **Tenant** (`tenants`) — Root table of the multi-tenant model. Cannot have tenant_id column (it IS the tenant). Access controlled by membership validation in AuthService.

## Cross-tenant Exceptions

Two repository methods are intentionally cross-tenant:

1. `UserRepository.findAllByEmail(email)` — Email-only login. AuthService validates the resulting tenant set against the user's authenticated tenant.
2. `RefreshTokenRepository.findByTokenHash(hash)` / `PasswordResetTokenRepository.findByTokenHash(hash)` — Token-based lookups. The token hash is a cryptographic secret; the query result is validated against the tenant context.

Both are documented in the inventory JSON with `exceptionJustification`.
