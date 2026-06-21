# EXEC-PROMPT-029 — Frontend–Backend Integration Foundation

## Status

APPROVED — READY FOR MERGE

## Objective

Establish the reusable typed frontend API integration boundary required by all subsequent SANAD live-data, authentication, authorization, and tenant-aware UI stages.

## Scope delivered

- Environment-aware API base URL validation
- Safe URL and query-parameter construction
- Generic GET, POST, PUT, PATCH, and DELETE client
- JSON request serialization and response parsing
- HTTP 204 and empty-response handling
- Distinct internal-timeout and external-cancellation errors
- Protected request headers
- Normalized backend HTTP errors and request IDs
- Backend health integration
- Backward-compatible exports for existing frontend files
- Unit and route-contract tests

## Backend contracts verified

- API base path is `/api/v1`
- Tenant identity is currently supplied through the `tenantId` query parameter
- Health endpoint is `/actuator/health`
- Handled errors expose timestamp, status, error, message, and path
- No JWT or automatic authorization injection is introduced by this stage

## Security controls

- No credentials or secrets in source
- Reject non-local cleartext API base URLs
- Reject base URLs with embedded credentials
- Block caller overrides of authorization and protocol-critical headers
- Do not expose internal health errors through the public system-status route
- Keep tenant selection explicit

## Validation evidence

The final reviewed branch passed all required repository gates on 21 June 2026:

- Web CI passed lint, tests, and Next.js build
- Backend CI passed build, tests, packaging, Docker Compose validation, and production health validation
- Security Baseline passed repository secret scanning, frontend production dependency audit, and backend container hardening
- Master Backlog Validation passed
- Service Decomposition Validation passed

Pull Request: `#48`

## Compatibility

The legacy API configuration and integration modules remain as compatibility shims. The public backend-status route keeps only the configured, reachable, and statusCode fields.

## Deferred stages

- EXEC-PROMPT-030 covers organization live integration.
- EXEC-PROMPT-031 covers users and memberships.
- EXEC-PROMPT-032 and EXEC-PROMPT-033 cover authentication foundation and session lifecycle.
- EXEC-PROMPT-034 covers authorization enforcement.
- EXEC-PROMPT-035 covers automatic tenant and organization context.

## Rollback

Revert the merged stage commit. No backend schema or commercial-production deployment migration is included.
