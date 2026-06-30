{
  "version": "1.0",
  "generatedAt": "2026-07-01T00:00:00Z",
  "totalEndpoints": 50,
  "controllers": [
    {"name": "AuthController", "module": "security", "endpoints": [
      {"method": "POST", "path": "/api/v1/auth/login", "auth": false, "capability": null, "tenant": false, "pagination": false},
      {"method": "POST", "path": "/api/v1/auth/refresh", "auth": false, "capability": null, "tenant": false, "pagination": false},
      {"method": "POST", "path": "/api/v1/auth/logout", "auth": true, "capability": null, "tenant": true, "pagination": false},
      {"method": "POST", "path": "/api/v1/auth/change-credential", "auth": true, "capability": null, "tenant": true, "pagination": false},
      {"method": "GET", "path": "/api/v1/auth/me", "auth": true, "capability": null, "tenant": true, "pagination": false},
      {"method": "POST", "path": "/api/v1/auth/forgot-password", "auth": false, "capability": null, "tenant": false, "pagination": false},
      {"method": "POST", "path": "/api/v1/auth/reset-password", "auth": false, "capability": null, "tenant": false, "pagination": false},
      {"method": "POST", "path": "/api/v1/auth/admin-reset-password/{userId}", "auth": true, "capability": "USER.WRITE", "tenant": true, "pagination": false}
    ]},
    {"name": "SelfRegistrationController", "module": "security", "endpoints": [
      {"method": "POST", "path": "/api/v1/auth/register", "auth": false, "capability": null, "tenant": false, "pagination": false}
    ]},
    {"name": "OrganizationController", "module": "organization", "endpoints": [
      {"method": "POST", "path": "/api/v1/organizations", "auth": true, "capability": "ORGANIZATION.CREATE", "tenant": true, "pagination": false},
      {"method": "GET", "path": "/api/v1/organizations", "auth": true, "capability": "ORGANIZATION.READ", "tenant": true, "pagination": false},
      {"method": "GET", "path": "/api/v1/organizations/{id}", "auth": true, "capability": "ORGANIZATION.READ", "tenant": true, "pagination": false},
      {"method": "PUT", "path": "/api/v1/organizations/{id}", "auth": true, "capability": "ORGANIZATION.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/organizations/{id}/activate", "auth": true, "capability": "ORGANIZATION.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/organizations/{id}/deactivate", "auth": true, "capability": "ORGANIZATION.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/organizations/{id}/archive", "auth": true, "capability": "ORGANIZATION.DELETE", "tenant": true, "pagination": false}
    ]},
    {"name": "OrganizationMembershipController", "module": "organization.membership", "endpoints": [
      {"method": "POST", "path": "/api/v1/organizations/{orgId}/memberships", "auth": true, "capability": "MEMBERSHIP.CREATE", "tenant": true, "pagination": false},
      {"method": "GET", "path": "/api/v1/organizations/{orgId}/memberships", "auth": true, "capability": "MEMBERSHIP.READ", "tenant": true, "pagination": false},
      {"method": "GET", "path": "/api/v1/organizations/{orgId}/memberships/{id}", "auth": true, "capability": "MEMBERSHIP.READ", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/organizations/{orgId}/memberships/{id}/activate", "auth": true, "capability": "MEMBERSHIP.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/organizations/{orgId}/memberships/{id}/deactivate", "auth": true, "capability": "MEMBERSHIP.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/organizations/{orgId}/memberships/{id}/remove", "auth": true, "capability": "MEMBERSHIP.DELETE", "tenant": true, "pagination": false}
    ]},
    {"name": "OrganizationMembershipAssignmentController", "module": "organization.membership", "endpoints": [
      {"method": "PATCH", "path": "/api/v1/organizations/{orgId}/memberships/{id}/assign/{userId}", "auth": true, "capability": "MEMBERSHIP.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/organizations/{orgId}/memberships/{id}/unassign", "auth": true, "capability": "MEMBERSHIP.WRITE", "tenant": true, "pagination": false}
    ]},
    {"name": "UserController", "module": "user", "endpoints": [
      {"method": "POST", "path": "/api/v1/users", "auth": true, "capability": "USER.CREATE", "tenant": true, "pagination": false},
      {"method": "GET", "path": "/api/v1/users", "auth": true, "capability": "USER.READ", "tenant": true, "pagination": false},
      {"method": "GET", "path": "/api/v1/users/{userId}", "auth": true, "capability": "USER.READ", "tenant": true, "pagination": false},
      {"method": "PUT", "path": "/api/v1/users/{userId}", "auth": true, "capability": "USER.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/users/{userId}/activate", "auth": true, "capability": "USER.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/users/{userId}/deactivate", "auth": true, "capability": "USER.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/users/{userId}/suspend", "auth": true, "capability": "USER.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/users/{userId}/archive", "auth": true, "capability": "USER.DELETE", "tenant": true, "pagination": false}
    ]},
    {"name": "UserMembershipController", "module": "user", "endpoints": [
      {"method": "GET", "path": "/api/v1/users/{userId}/memberships", "auth": true, "capability": "MEMBERSHIP.READ", "tenant": true, "pagination": false}
    ]},
    {"name": "RoleController", "module": "access", "endpoints": [
      {"method": "POST", "path": "/api/v1/access/roles", "auth": true, "capability": "ROLE.WRITE", "tenant": true, "pagination": false},
      {"method": "GET", "path": "/api/v1/access/roles", "auth": true, "capability": "ROLE.READ", "tenant": true, "pagination": false},
      {"method": "GET", "path": "/api/v1/access/roles/{roleId}", "auth": true, "capability": "ROLE.READ", "tenant": true, "pagination": false},
      {"method": "PUT", "path": "/api/v1/access/roles/{roleId}", "auth": true, "capability": "ROLE.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/access/roles/{roleId}/activate", "auth": true, "capability": "ROLE.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/access/roles/{roleId}/deactivate", "auth": true, "capability": "ROLE.WRITE", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/access/roles/{roleId}/archive", "auth": true, "capability": "ROLE.WRITE", "tenant": true, "pagination": false}
    ]},
    {"name": "CapabilityController", "module": "access", "endpoints": [
      {"method": "POST", "path": "/api/v1/access/capabilities", "auth": true, "capability": "CAPABILITY.MANAGE", "tenant": false, "pagination": false},
      {"method": "GET", "path": "/api/v1/access/capabilities", "auth": true, "capability": "CAPABILITY.READ", "tenant": false, "pagination": false},
      {"method": "GET", "path": "/api/v1/access/capabilities/{id}", "auth": true, "capability": "CAPABILITY.READ", "tenant": false, "pagination": false},
      {"method": "PUT", "path": "/api/v1/access/capabilities/{id}", "auth": true, "capability": "CAPABILITY.MANAGE", "tenant": false, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/access/capabilities/{id}/activate", "auth": true, "capability": "CAPABILITY.MANAGE", "tenant": false, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/access/capabilities/{id}/deactivate", "auth": true, "capability": "CAPABILITY.MANAGE", "tenant": false, "pagination": false}
    ]},
    {"name": "RoleAccessController", "module": "access", "endpoints": [
      {"method": "POST", "path": "/api/v1/access/roles/{roleId}/access-items/{capabilityId}", "auth": true, "capability": "ROLE.WRITE", "tenant": true, "pagination": false},
      {"method": "DELETE", "path": "/api/v1/access/roles/{roleId}/access-items/{capabilityId}", "auth": true, "capability": "ROLE.WRITE", "tenant": true, "pagination": false},
      {"method": "GET", "path": "/api/v1/access/roles/{roleId}/access-items", "auth": true, "capability": "ROLE.READ", "tenant": true, "pagination": false}
    ]},
    {"name": "UserAccessController", "module": "access", "endpoints": [
      {"method": "POST", "path": "/api/v1/access/users/{userId}/role-links/{roleId}", "auth": true, "capability": "USER.GRANT_ROLE", "tenant": true, "pagination": false},
      {"method": "GET", "path": "/api/v1/access/users/{userId}/role-links", "auth": true, "capability": "ROLE.READ", "tenant": true, "pagination": false},
      {"method": "PATCH", "path": "/api/v1/access/users/role-links/{grantId}/revoke", "auth": true, "capability": "USER.REVOKE_ROLE", "tenant": true, "pagination": false}
    ]},
    {"name": "CapabilityEvaluationController", "module": "access", "endpoints": [
      {"method": "GET", "path": "/api/v1/access/evaluation", "auth": true, "capability": "ACCESS.EVALUATE", "tenant": true, "pagination": false}
    ]}
  ]
}
