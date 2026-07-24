# SANAD Independent-Assurance Threat Model Baseline

This is the defender's baseline supplied to the independent assessor for challenge. It is not an independent review result.

## Protected assets

- Tenant identities, sessions, memberships, roles and capabilities.
- Customer, employee, financial, accounting, inventory and commerce data.
- Workflow approvals, audit trails and evidence records.
- Production credentials, signing keys, provider tokens and database access.
- Release provenance, migrations, backups and operational configuration.

## Trust boundaries

1. Browser/client to the Vercel-hosted frontend and BFF.
2. BFF to the backend API and identity/session services.
3. Authenticated principal and capability checks to tenant-scoped business objects.
4. Application to PostgreSQL, migrations, backups and analytics projections.
5. GitHub Actions and deployment providers to production configuration and secrets.
6. SANAD to email, payment and other external integrations.

## Priority abuse cases

| ID | Abuse case | Required independent challenge |
|---|---|---|
| TM-01 | Change a tenant/object identifier to read or mutate another tenant's data | Cross-tenant BOLA/IDOR matrix over read, write, import, export and nested resources |
| TM-02 | Use a valid low-privilege session for an administrative or financial action | Horizontal and vertical privilege-escalation tests, including stale/revoked roles |
| TM-03 | Steal, replay, fixate or retain a session across refresh/logout/lockout | Cookie, CSRF, rotation, replay, expiry and concurrent-session testing |
| TM-04 | Bypass workflow approval to alter operational or financial truth | Direct API, state-transition, replay and race-condition testing |
| TM-05 | Inject malicious content through imports, files, queries or integrations | Injection, parser, formula, path, SSRF and stored-content tests where applicable |
| TM-06 | Discover or exfiltrate secrets from code, artifacts, logs, images or CI | Repository-history, artifact, container, workflow and production-config review |
| TM-07 | Introduce or exploit a vulnerable dependency or build component | Lockfile, provenance, action pinning, container and dependency review |
| TM-08 | Suppress or forge audit evidence | Audit authorization, completeness, immutability, correlation and clock tests |
| TM-09 | Expose personal data beyond purpose, tenant or retention limits | Data-flow, minimization, export, deletion, logging and access review |
| TM-10 | Abuse failover, retries or partial failure to duplicate financial effects | Idempotency, concurrency, rollback and reconciliation tests |

## Security invariants

- Server-derived tenant context is authoritative; client-supplied tenant identifiers never grant access.
- Every object read or mutation is authorized for both tenant and capability.
- Financial and approval-sensitive changes are attributable, auditable and idempotent.
- Authentication and dependency failures fail closed without leaking secrets or cross-tenant data.
- Production secrets are absent from repository content, build artifacts, logs and client bundles.
- No critical/high finding may be accepted as residual risk for `REM-P0-006` closure.

The assessor must add missing threats, identify disproved assumptions and reference resulting tests in the assessment manifest.
