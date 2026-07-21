# CRM-007 Exact-SHA Production Deployment

This tracked marker ensures the Vercel project rooted at `apps/web` builds the same merge SHA that publishes and deploys the corrected backend image.

Release purpose: nullable PostgreSQL UUID exclusion remediation for Address and Communication mutation lifecycles.

Closure requirements remain unchanged:

- Vercel Production must be `READY` on the exact merge SHA.
- Render must be `live` on `ghcr.io/snadaiapp-png/snad-backend:<exact-merge-sha>`.
- Flyway `20260721.2` must remain `SQL / true`.
- CRM-G1 and CRM-007 must pass on the same SHA.
- HTTP 500 remains a hard failure.
