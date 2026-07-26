# REM-P0-006 Threat-Model Baseline

This baseline is an internal challenge document, not an independent assessment result. The assessor must identify omissions and may expand the scope.

## Protected assets

- Tenant-isolated business and personal data.
- Authentication sessions, identities, capabilities and privileged administration.
- Financial, workflow, approval and audit integrity.
- Secrets, signing material, deployment credentials and recovery controls.
- Source, build, package, container and deployment provenance.

## Trust boundaries

- Browser/client to BFF and public edge.
- BFF to backend APIs.
- Backend to database, object storage and third-party services.
- Tenant context propagation through persistence, cache, queues, exports and logs.
- Developer/CI identities to artifact registries and production deployment.
- Administrative and break-glass paths.

## Mandatory abuse cases

- Cross-tenant direct and nested object access.
- Horizontal or vertical privilege escalation and stale authorization.
- Session fixation, replay, CSRF and token leakage.
- Injection, SSRF, unsafe parsing, import/export and stored-content attacks.
- Workflow state or approval bypass through direct APIs.
- Idempotency, replay and concurrency failures affecting financial integrity.
- Audit omission, tampering or unauthorized access.
- Secret exposure through history, artifacts, logs, images or client bundles.
- Dependency, action, package, container and deployment-chain compromise.
- Excessive collection, retention, disclosure or deletion failure for personal data.

The final report must map each tested control and finding to the coverage matrix and exact assessed release.
