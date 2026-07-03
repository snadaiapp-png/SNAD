# SNAD Business Operating System

Arabic-first, multi-tenant business operating platform combining enterprise administration, workflow automation, secure account management, and the foundations for ERP, CRM, HRM, Accounting, Commerce, POS, AI, and analytics.

## Current governance status

```text
Issue #101: OPEN
Development Gate: NOT APPROVED
OWASP Final: NOT PASSED
Commercial Go-Live: NOT AUTHORIZED
```

Development and pilot-integration work continue under the documented restrictions. A successful build or pilot deployment is not commercial production approval.

## Technology stack

### Frontend

- Next.js 16
- React 19
- TypeScript
- Tailwind CSS 4
- Noto Sans Arabic and Noto Sans through `next/font`
- Vercel pilot deployment

### Backend

- Spring Boot 3.3.5
- Java 17 target / Java 21 CI runtime
- Maven
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL 16
- Render pilot deployment

### Infrastructure and evidence

- Docker multi-stage backend image
- GitHub Actions CI/CD and governance workflows
- Vercel frontend
- Render backend
- Supabase PostgreSQL pilot database
- Resumable NVD bulk-feed mirror and offline security-data pipeline

## Pilot endpoints

| Service | Endpoint | Status |
|---|---|---|
| Frontend | `https://snad-app.vercel.app` | Pilot deployment active |
| Backend | `https://sanad-backend-mcrj.onrender.com` | Pilot backend; verify health before use |

Free-tier pilot constraints apply. These endpoints are not an approved commercial-production environment.

## Implemented foundations

- SNAD visual identity and Arabic/Latin typography.
- Authentication boundary and tenant selection.
- Organization, user, and membership integration interfaces.
- Forgotten-password and reset-password flows.
- Administrator-issued set-password links.
- Password-change security notifications.
- No plaintext password delivery by email.
- Runtime-separated notification-provider integration.
- PostgreSQL migration, backup/restore, and performance validation.
- NVD bulk-feed mirror with checkpointing and integrity verification.

## Repository structure

```text
apps/
  web/                 Next.js frontend
  sanad-platform/      Spring Boot backend

docs/
  README.md            Canonical documentation index
  architecture/        Architecture decisions
  brand/               Identity and typography
  deployment/          Deployment and configuration
  development/         Developer guides
  execution/           Delivery progress
  governance/          Current controlling status
  operations/          Monitoring and operational runbooks
  security/            Security operating models
  system/              System reference
  testing/             Acceptance and evidence matrices

.github/workflows/      CI/CD, security, and governance workflows
scripts/                Validation, operations, and security tooling
```

## Documentation

Start with the [canonical documentation index](docs/README.md).

Primary references:

- [SNAD system reference](docs/system/SNAD-SYSTEM-REFERENCE.md)
- [Current implementation status](docs/governance/CURRENT-IMPLEMENTATION-STATUS.md)
- [Acceptance evidence matrix](docs/testing/ACCEPTANCE-EVIDENCE.md)
- [Runtime configuration matrix](docs/deployment/RUNTIME-CONFIGURATION-MATRIX.md)
- [Account recovery and email runbook](docs/operations/ACCOUNT-RECOVERY-EMAIL-RUNBOOK.md)
- [Visual identity implementation](docs/brand/SNAD-VISUAL-IDENTITY-IMPLEMENTATION.md)
- [Render backend deployment](docs/deployment/render-backend-deployment.md)

## Local verification

### Web

```bash
cd apps/web
npm ci
npm run lint
npm test
npm run build
```

### Backend

```bash
cd apps/sanad-platform
mvn test
```

Do not add real provider, database, JWT, mailbox, or application-password secrets to repository files.

## Account recovery contract

```text
Recovery request
  -> generic response
  -> random single-use value
  -> hash stored with expiry
  -> secure link delivered by configured provider
  -> user selects a new password
  -> old sessions revoked
  -> confirmation notification generated
```

The approved sender is supplied through `SECURITY_NOTIFICATION_FROM`. Real backend delivery remains pending until the deployment-managed notification endpoint and credential are configured and an end-to-end test passes.

## Project stage

Current repository stage: **pilot integration foundation with active security and operational gate work**.
