# CRM-007 Exact-SHA Production Deployment

This tracked marker forces the Vercel project rooted at `apps/web` to build the same merge SHA that publishes and deploys the corrected backend image.

Release generation: `crm-nullable-exclusion-closure-v4`.

Authoritative correction:

- PR #651 is merged.
- Nullable Address and Communication exclusion parameters are explicitly typed as PostgreSQL UUIDs.
- HTTP 500 is a hard failure and is not an accepted test outcome.

Exact-SHA closure requirements:

- Vercel Production must be `READY` on the exact merge SHA.
- Render must be `live` on `ghcr.io/snadaiapp-png/snad-backend:<exact-merge-sha>`.
- Flyway `20260721.2` must remain `SQL / true`.
- CRM-G1 and CRM-007 must pass on the same SHA.
- Contact, Address and Communication lifecycle operations must complete without unexplained 5xx responses.
- Tenant isolation must remain fail-closed.

This marker contains no runtime application logic.