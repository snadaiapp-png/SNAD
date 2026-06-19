# EXEC-PROMPT-019 — User–Organization Membership Link Foundation

Status: IMPLEMENTED / PENDING CI

Scope:
- Flyway V5 adds nullable `user_id` to `organization_memberships`.
- Composite foreign key `(tenant_id, user_id)` references Users in the same Tenant.
- Unique `(tenant_id, organization_id, user_id)` prevents duplicate user membership rows per organization.
- OrganizationMembership entity, response DTO, mapper, and repository queries expose the optional user link.
- Invitation-only memberships remain valid while `user_id` is null.

Out of scope:
- Authentication, login, passwords, JWT, roles, permissions, and RBAC.
