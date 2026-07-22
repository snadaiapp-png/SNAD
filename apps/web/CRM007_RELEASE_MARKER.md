# CRM-007 Exact-SHA Production Deployment

This tracked marker forces the Vercel project rooted at `apps/web` to build the same merge SHA that publishes and deploys the corrected backend image.

Release generation: `crm-current-main-exact-sha-closure-v9`.

Authoritative correction:

- PR #651 is merged.
- PR #657 serialized CRM-007 behind exact-SHA CRM-G1 success.
- PR #659 corrected the duplicated Vercel Root Directory.
- PR #661 removed the duplicate API deployment and now consumes the canonical Vercel Git Integration deployment.
- PR #667 fixed reconciliation workflow correlation by passing `publish_run_id` and `release_sha` explicitly.
- PR #670 fixed `resolve_tenant` PostgreSQL interpolation with stdin-driven `psql -f -`.
- PR #675 introduced the Vercel-safe `X-SNAD-If-Match` transport while preserving backend `If-Match` semantics.
- PR #679 fixed PostgreSQL archive status writes by explicitly casting the `archived_at` CASE parameter.
- PR #680 and PR #681 removed tunnel routing and made Render the immutable Vercel Production upstream.
- The BFF now carries the backend domain validator in `X-SNAD-Entity-Tag`, paired with `X-SNAD-If-Match`, so CDN compression cannot weaken optimistic-concurrency identity.
- Current-main closure must execute only after Vercel Production reports the exact merge SHA as `READY`.

Exact-SHA closure requirements:

- Vercel Production must be `READY` on the exact merge SHA.
- Render must be `live` on `ghcr.io/snadaiapp-png/snad-backend:<exact-merge-sha>`.
- Flyway `20260721.1` and `20260721.2` must remain `SQL / true` with zero failed migrations.
- CRM-G1 and CRM-007 must pass on the same SHA.
- Contact, Address and Communication lifecycle operations must complete without unexplained 5xx responses.
- Tenant isolation must remain fail-closed.
- Immutable CRM-G1 and CRM-007 evidence must be generated, reviewed and merged before final closure is asserted.

This marker contains no runtime application logic. Its only purpose is to make an exact current-main web deployment observable when the control-plane correction itself is outside the Vercel Root Directory.
