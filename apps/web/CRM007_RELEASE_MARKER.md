# CRM-007 Exact-SHA Production Deployment

This tracked marker forces the Vercel project rooted at `apps/web` to build the same commit SHA used by the protected Production closure chain.

Release generation: `crm-fast-forward-exact-sha-closure-v6`.

Authoritative correction:

- PR #651 is merged.
- PR #657 serialized CRM-007 behind exact-SHA CRM-G1 success.
- PR #659 corrected the duplicated Vercel Root Directory.
- PR #661 removed the duplicate API deployment and now consumes the canonical Vercel deployment.
- PR #663 connected this marker to the current-main closure trigger.
- Nullable Address and Communication exclusion parameters are explicitly typed as PostgreSQL UUIDs.
- HTTP 500 is a hard failure and is not an accepted test outcome.

Exact-SHA closure requirements:

- The Vercel deployment metadata and current `main` must contain the same commit SHA.
- Vercel Production must be `READY` on that exact SHA.
- Render must be `live` on `ghcr.io/snadaiapp-png/snad-backend:<exact-sha>`.
- Flyway `20260721.1` and `20260721.2` must remain `SQL / true` with zero failed migrations.
- CRM-G1 and CRM-007 must pass on the same SHA.
- Contact, Address and Communication lifecycle operations must complete without unexplained 5xx responses.
- Tenant isolation must remain fail-closed.

This marker contains no runtime application logic. It is intentionally the only source-tree change in this release candidate so the exact checked commit can be built as Preview, fast-forwarded to `main` without rewriting its SHA, and promoted without rebuilding.
