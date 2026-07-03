# SNAD System Reference

## 1. Purpose

SNAD is an Arabic-first, multi-tenant Business Operating System for SMEs and growing organizations. The platform combines enterprise administration, workflow automation, AI-assisted operations, and business applications behind a shared SaaS foundation.

This document describes the implementation currently present in the repository. It does not replace the SANAD Master Reference or authorize production use.

## 2. Product identity

- Active product name: **سند / SNAD**.
- Frontend pilot URL: `https://snad-app.vercel.app`.
- Approved visual palette: petroleum primary and royal-gold accent.
- Arabic typography: Noto Sans Arabic.
- Latin and numeric typography: Noto Sans.
- Interface direction: RTL by default, with explicit LTR treatment for email addresses, identifiers, and numeric technical values.

## 3. Runtime architecture

```text
Browser
  -> Vercel Next.js frontend
  -> HTTPS API requests with credentials
  -> Render Spring Boot backend
  -> Supabase PostgreSQL pilot database
```

### Frontend

- Next.js 16.
- React 19.
- TypeScript.
- Tailwind CSS 4.
- Authentication boundary and tenant context.
- Organization, user, membership, and dashboard interfaces.
- Password recovery and reset screens.

### Backend

- Spring Boot 3.3.5.
- Java 17 target with Java 21 CI/runtime verification.
- Maven.
- Spring Security.
- Spring Data JPA.
- Flyway-managed PostgreSQL schema.
- Health and readiness probes.
- Structured account recovery notification abstraction.

### Persistence

- PostgreSQL 16 pilot database.
- Flyway migrations are the schema authority.
- Hibernate is configured to validate rather than create production schema.
- Account recovery values are stored only as hashes and are single-use.

## 4. Authentication and account recovery

### Login

- User signs in with email and password.
- Ambiguous tenant membership requires an explicit tenant selection.
- Access and refresh tokens follow the configured authentication lifecycle.
- Credential rotation can be required before access to the application shell.

### Forgotten password

```text
User submits email
  -> generic response prevents account enumeration
  -> cryptographically random reset value generated
  -> SHA-256 representation stored
  -> expiring single-use link produced
  -> security notification gateway delivers the link
  -> user chooses a new password
  -> previous sessions are revoked
  -> confirmation notification is generated
```

The system never sends a plaintext password by email.

### Administrator-assisted recovery

An authorized administrator can request a set-password link for an active user. The administrator does not choose or receive the user's password. Legacy direct-password request fields are rejected.

## 5. Notification architecture

The backend does not embed a provider credential in source code. `SecurityNotificationGateway` separates application behavior from the delivery provider.

Supported operating modes:

- `local` or test behavior: no external network delivery.
- `disabled`: fail closed outside local/test.
- `http`: send a structured message to a separately managed HTTPS email-delivery endpoint.

The approved operational mailbox is documented in the account recovery runbook. Provider credentials remain deployment-managed secrets.

## 6. Multi-tenancy

- Tenant identity is part of the authenticated context.
- Cross-tenant ambiguity is resolved explicitly.
- Organization, user, and membership operations are tenant-scoped.
- Authorization and data isolation remain mandatory acceptance concerns for every new module.

## 7. Security model

- Security by Design.
- Zero Trust assumptions.
- No plaintext password transmission.
- No provider secrets committed to GitHub.
- HTTPS required for non-local API and notification endpoints.
- Generic recovery responses prevent account discovery.
- Reset values expire and are single-use.
- CI includes compilation, tests, security baseline, PostgreSQL acceptance, performance, backup/restore, and identity governance.
- OWASP final acceptance remains controlled by Issue #101 and the NVD data pipeline.

## 8. Deployment model

### Frontend

- Platform: Vercel.
- Project: `snad-app`.
- Production pilot domain: `snad-app.vercel.app`.
- Git integration publishes reviewed `main` commits.

### Backend

- Platform: Render pilot service.
- Service name: `sanad-backend`.
- Region: Frankfurt.
- Auto-deploy is disabled by design.
- Deployment is performed from an exact reviewed commit.

### Database

- Platform: Supabase PostgreSQL pilot.
- Region: Frankfurt/Central EU.
- Session Pooler is used for the pilot runtime.

## 9. Evidence hierarchy

Use the following order when determining system state:

1. Merged commit on `main`.
2. Pull request evidence and successful required checks.
3. GitHub Actions artifacts and immutable releases.
4. Deployment status and health verification.
5. Current governance issue and explicit owner decision.
6. Documentation.

Documentation must not overrule failed evidence or a closed gate.

## 10. Current limitations

- Pilot infrastructure uses free-tier services.
- Commercial production is not authorized.
- Real outbound recovery email requires a configured delivery provider in the backend environment.
- OWASP final gate remains unresolved until NVD evidence and Issue #101 requirements are completed.
- The implemented application is an integration foundation, not the completed ERP/BOS product scope.

## 11. Change control

Changes to authentication, tenant isolation, recovery tokens, notification delivery, security gates, or production configuration require:

1. A dedicated branch or controlled documentation-only commit.
2. Tests covering the security contract.
3. Security baseline and CI evidence.
4. No committed secret material.
5. Explicit documentation of any unresolved external dependency.
6. Separate approval for production or commercial release.
