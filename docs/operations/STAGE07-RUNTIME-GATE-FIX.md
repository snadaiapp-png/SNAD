# Stage 07 Runtime Gate Correction

## Problem

The production release workflow attempted to resolve and query the production PostgreSQL host from a GitHub-hosted runner. The database hostname is private to the provider network, so the release gate failed before deployment even though the live application could reach the database.

## Correction

- Added `ControlPlaneHealthIndicator` inside the backend runtime.
- The indicator requires the configured Control Plane tenant to be `ACTIVE`.
- The indicator requires an `ACTIVE` `ADMIN` role for the same tenant.
- A failed query, missing tenant, or missing role returns `DOWN`.
- Render's `/actuator/health` deployment gate includes registered health indicators and therefore fails closed inside the provider network.
- The external verification script still executes direct SQL when the database is publicly resolvable; when DNS is private, it records a controlled deferral to the in-runtime gate rather than requiring public database exposure.

## Security boundary

This correction does not expose the production database, publish connection details, weaken authentication, enable bootstrap, or create a bypass. It moves verification to the network location where the database is intentionally reachable.
