# CRM-007 Exact-SHA Production Deployment

This tracked marker forces the Vercel project rooted at `apps/web` to build the same merge SHA that publishes and deploys the corrected backend image.

Release generation: `crm-current-main-exact-sha-closure-v6`.

Authoritative correction:

- PR #651 is merged.
- PR #657 serialized CRM-007 behind exact-SHA CRM-G1 success.
- PR #659 corrected the duplicated Vercel Root Directory.
- PR #661 removed the duplicate API deployment and now consumes the canonical Vercel Git Integration deployment.
- Corrective PR fixes reconciliation workflow correlation: orchestrator now passes `publish_run_id` and `release_sha` as explicit inputs, replacing ambiguous `event=push` filtering with direct run identity verification.

Exact-SHA closure requirements:

- Vercel Production must be `READY` on the exact merge SHA.
- Render must be `live` on `ghcr.io/snadaiapp-png/snad-backend:<exact-merge-sha>`.
- Flyway `20260721.1` and `20260721.2` must remain `SQL / true` with zero failed migrations.
- CRM-G1 and CRM-007 must pass on the same SHA.
- Contact, Address and Communication lifecycle operations must complete without unexplained 5xx responses.
- Tenant isolation must remain fail-closed.

This marker contains no runtime application logic. Its only purpose is to make an exact current-main web deployment observable when the control-plane correction itself is outside the Vercel Root Directory.
