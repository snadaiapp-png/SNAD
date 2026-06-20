# Security Hardening Controls

## Gate
Issue #34 — Security Hardening

## Mandatory controls

- Central inventory of credentials, tokens, certificates, and administrative accounts.
- No plaintext credential in source control, tickets, screenshots, or documentation.
- Immediate rotation after suspected exposure.
- Least-privilege access for GitHub, Vercel, Render, Supabase, cloud, and database administration.
- MFA for all administrative accounts.
- Protected `main` branch with required CI checks.
- Restricted CI permissions.
- Approved CORS origins only.
- TLS for all external and database connections.
- Dependency and container-image vulnerability scanning.
- Tenant-isolation and authorization testing.
- Security-event logging and retained audit evidence.

## Automated baseline

The repository security workflow scans the source tree and backend container image for unresolved critical and high-severity findings. A finding at those severities fails the pull request check.

## Manual evidence still required for commercial production

- Administrative access review
- MFA evidence
- Provider permission exports
- Secrets inventory and rotation record
- Penetration and tenant-isolation test evidence
- Incident-response contact and escalation path
- Security sign-off confirming no unresolved critical or high blocker

## Exit rule

Issue #34 closes only after the automated checks pass and the manual access, secrets, isolation, and provider-control evidence is reviewed.