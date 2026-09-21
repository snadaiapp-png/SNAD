# ADR-040 — Control Plane Cross-Tenant Executive Authority

**Status:** Accepted for implementation  
**Date:** 2026-09-21  
**Owner:** SNAD Platform  
**Security boundary:** Multi-tenant authorization / Control Plane

## Context

SNAD uses JWT tenant binding to reject requests where a tenant-scoped query
parameter targets a tenant different from the authenticated JWT tenant. That
rule is correct for normal tenant workloads.

The Executive Control Plane is different: an authenticated platform operator
belongs to the dedicated Control Plane tenant but must administer target tenants.
Several governed Executive APIs therefore accept a target `tenantId`, including
billing and usage operations.

The previous global tenant-binding filter rejected those legitimate requests
before `ControlPlaneAccessGuard` and capability authorization could run. The
production symptom was HTTP 403 for the canonical project owner while requesting
another tenant's Executive billing data.

## Decision

Permit a foreign `tenantId` through the JWT tenant-binding filter only when:

1. the JWT tenant is the configured `SANAD_CONTROL_PLANE_TENANT_ID`; and
2. the request path is an explicit target-aware Executive route that declares a `tenantId` selector.

This is not an authorization grant. It only allows the request to reach the
existing server-side authorization layers. Executive controllers remain
responsible for both:

- `ControlPlaneAccessGuard.require(authentication)`; and
- an explicit `@RequireCapability(...)`.

All non-target-aware APIs retain the original fail-closed foreign-tenant rejection, including Executive diagnostics such as `access-check/v2` that do not consume a target tenant. Regular tenant users retain the rejection on every path.

## Canonical owner invariant

The approved project owner is the deterministic Control Plane identity:

- tenant ID: `00000000-0000-0000-0000-000000000001`
- user ID: `00000000-0000-0000-0000-000000000010`
- email: `snad.ai.app@gmail.com`

A forward-only migration reconciles that owner to ACTIVE + `platform_admin=true`,
an ACTIVE tenant-wide ADMIN role grant, every ACTIVE capability, and TENANT-wide
scope grants used by scoped authorization.

Any foreign user row that reuses the canonical owner email is archived rather
than deleted, its active grants/sessions are revoked, and its row remains
available for audit history.

## Alternatives considered

### Disable tenant binding for platform administrators

Rejected. A global bypass would allow privileged sessions to cross tenant
boundaries on ordinary tenant APIs and would substantially weaken isolation.

### Hard-code the owner email in the JWT filter

Rejected. Authorization must be based on tenant identity plus RBAC/capabilities,
not a mutable email string.

### Remove target tenant selectors from Executive APIs

Rejected for this correction because the Control Plane necessarily needs an
explicit target tenant. A future API-shape cleanup may move selectors from query
parameters to resource paths without changing this authorization decision.

## Consequences

- Canonical Control Plane operators can perform legitimate cross-tenant
  Executive administration.
- Normal tenant isolation remains fail-closed.
- Missing capabilities still return 403 from the authorization layer.
- Tenant-binding conflicts remain distinguishable from capability denials in
  the frontend error mapping.
- Regression tests must cover both the permitted Control Plane case and the
  denied normal-tenant/non-Executive cases.
