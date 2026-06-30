# SNAD Governance Exception — GOV-EXC-2026-06-30-001

## Exception Details

| Field | Value |
|---|---|
| Exception ID | GOV-EXC-2026-06-30-001 |
| Authorized By | Project Owner |
| Date | 2026-06-30 |
| Decision | Allow infrastructure development and validation stages to continue while provider-side credential rotation remains pending. |

## Scope

This exception allows:
- Development, testing, CI, non-production validation and architecture hardening
- Remote CI validation (GitHub Actions)
- Docker, PostgreSQL, Flyway, Testcontainers validation in CI
- Code quality improvements
- Infrastructure hardening stages (02C, 03, etc.)

## What This Does NOT Close

- CD-00-P0-001: Historical administrator credential exposure — remains BLOCKED_OWNER_ACTION
- CD-00-P0-002: Historical email-proxy fallback verification — remains BLOCKED_OWNER_ACTION

## What This Does NOT Authorize

- Production deployment
- Commercial release
- Credential reuse (any compromised credential must NOT be reused)
- Use of production data
- Use of production API keys in CI
- Connection of CI to production systems using exposed credentials
- Staging with real customer data
- Public launch

## Expiration

This exception expires MANDATORILY before:
- Production Gate
- Any staging environment with real data
- Public launch
- Commercial release

## Debt Status

Both P0 debt items remain BLOCKED_OWNER_ACTION with the following fields:
- `ownerDeferred: true`
- `progressionExceptionId: GOV-EXC-2026-06-30-001`
- `blocksDevelopmentProgression: false`
- `blocksProductionRelease: true`
- `blocksCommercialLaunch: true`
- `requiresOwnerAction: true`

## Important Rules

1. No compromised credential may be reused in any environment
2. CI must use test-only credentials and fixtures
3. No production secrets may be used in CI
4. The debt register must accurately reflect that P0 remains open
5. This exception does NOT close, resolve, or accept any security risk
6. The human owner must still execute credential rotation before production
