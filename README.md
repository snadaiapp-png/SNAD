# SNAD Business Operating System

<!-- STATUS_AUTHORITY: CURRENT -->

**Status as of:** 2026-07-17, Asia/Riyadh  
**Executive decision:** **CONDITIONAL CONTINUE**  
**Broad commercial go-live:** **NOT APPROVED**

Arabic-first, multi-tenant business operating platform combining enterprise administration, workflow automation, secure account management, ERP, CRM, HRM, Accounting, Commerce, POS, AI and analytics foundations.

## Current authority

The current platform state is controlled by:

1. GitHub **Issue #516** — executive remediation tracker.
2. [`docs/governance/CURRENT-STATUS.json`](docs/governance/CURRENT-STATUS.json) — machine-readable status.
3. [`docs/governance/CURRENT-IMPLEMENTATION-STATUS.md`](docs/governance/CURRENT-IMPLEMENTATION-STATUS.md) — human-readable status.
4. [`docs/governance/EXECUTIVE-REVIEW-REMEDIATION-2026-07-17.md`](docs/governance/EXECUTIVE-REVIEW-REMEDIATION-2026-07-17.md) — evidence and remaining risks.

Historical stage reports, old deployment observations and module closure records do not override these sources.

## Current decision summary

```text
PROJECT: CONDITIONAL CONTINUE
CONTROLLED DEVELOPMENT: ALLOWED
LIMITED PILOT: ALLOWED
BROAD COMMERCIAL GO-LIVE: NOT APPROVED
ISSUE #101: CLOSED / HISTORICAL — NOT THE CURRENT CONTROL GATE
ISSUE #516: AUTHORITATIVE REMEDIATION TRACKER
```

Closed findings include:

- `REM-P0-003` — Executor #23 Master Execution Backlog.
- `REM-P1-008` — SLA/SLO and incident operating model.

Open or deferred findings include backend hosting and authentication reliability, governance sequencing, disaster recovery, independent security assurance, end-to-end business-process proof, repository visibility and final status-document reconciliation.

## Runtime boundary

- Frontend: `https://snad-app.vercel.app`.
- Backend: currently exposed through a temporary development tunnel and **not accepted as final enterprise hosting**.
- Authentication: intermittent reliability remains open.
- A successful build, HTTP `200`, healthy endpoint or stage closure is not commercial production approval.

## Technology

- Frontend: Next.js, React, TypeScript and Tailwind CSS.
- Backend: Spring Boot, Java, Maven, Spring Security, JPA, Flyway and PostgreSQL.
- Delivery and evidence: GitHub Actions, Vercel and repository-governed validation workflows.

## Repository structure

```text
apps/                     application code
docs/                     architecture, governance, operations and evidence
.github/workflows/        CI/CD and governance gates
scripts/                  validation, operations and security tooling
```

## Documentation

Start with the [canonical documentation index](docs/README.md).

Status-document rules are defined in [`docs/governance/STATUS-DOCUMENTATION-POLICY.md`](docs/governance/STATUS-DOCUMENTATION-POLICY.md). Historical records are retained for auditability but must be visibly classified and linked to the current authority.

## Local verification

```bash
cd apps/web
npm ci
npm run lint
npm test
npm run build

cd ../sanad-platform
mvn test
```

Do not commit provider credentials, database passwords, JWT secrets, mailbox credentials or application passwords.

