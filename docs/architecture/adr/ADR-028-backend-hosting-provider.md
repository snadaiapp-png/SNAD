# ADR-028: Backend Hosting Provider Selection

## Status

Accepted with pilot amendment. Provider provisioning and runtime validation remain pending.

## Date

2026-06-20

## Context

SANAD requires a provider for its Spring Boot backend and PostgreSQL database with Docker deployment, health checks, secrets management, TLS, GitHub integration, and low operational overhead.

## Original Decision

Render remains the selected backend hosting provider in Frankfurt with manual first deployment and `autoDeployTrigger: off`.

The original managed PostgreSQL plan on Render is deferred for the pilot because the owner approved a temporary free-tier verification path.

## Pilot Amendment

For pilot and integration verification only:

```text
Vercel → Frontend
Render Free → Spring Boot Backend
Supabase Free → PostgreSQL
```

### Pilot Database Decision

- Database provider: Supabase PostgreSQL
- Region: Central EU (Frankfurt)
- Connection method: Session Pooler
- Port: `5432`
- TLS: required through `sslmode=require`
- Migration management: Flyway V1–V9
- Secrets: Render environment variables with `sync: false`
- Connection pool: maximum `5`, minimum idle `1`

### Required Environment Variables

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
```

No database credential may be committed to GitHub.

## Rationale

1. Render Free provides a no-cost backend pilot path.
2. Supabase Free provides PostgreSQL without the temporary Render database plan selected previously.
3. Both services can be placed in Frankfurt to reduce cross-region latency.
4. SANAD already uses standard PostgreSQL, JDBC, JPA, and Flyway, so no domain logic change is required.
5. Standard PostgreSQL preserves portability for later migration to a paid provider.
6. The pilot keeps deployment manual and limits the connection pool for free-tier capacity.

## Deployment Policy

- First deployment: manual.
- Current Blueprint setting: `autoDeployTrigger: off`.
- Render Blueprint provisions only `sanad-backend`.
- Supabase credentials are entered manually in Render Dashboard.
- Automatic deployment remains disabled until deployment, smoke testing, and rollback are verified.

## Capability Disclaimer

Free plans are not production-grade SANAD infrastructure. They may sleep, throttle, limit storage or connections, and provide reduced recovery guarantees. This amendment authorizes pilot verification only.

## Production Gate

Before commercial production launch, SANAD must:

1. Upgrade the Render backend or move to an approved production compute plan.
2. Upgrade Supabase or move to an approved production PostgreSQL plan.
3. Validate backups, PITR, restore procedures, monitoring, and alerting.
4. Complete production smoke and rollback verification.
5. Measure latency from Saudi Arabia.
6. Review data residency and compliance requirements.

## Region Decision

Frankfurt is selected for both backend and database during the pilot. A formal latency test from Saudi Arabia remains required.

## Migration Triggers

Re-evaluate the pilot architecture when any of the following occurs:

1. Commercial launch approval is requested.
2. Saudi data-residency requirements become mandatory.
3. Measured latency does not meet product requirements.
4. Database scale, traffic, or availability exceeds free-tier limits.
5. Multi-region deployment becomes necessary.
6. Compliance or operational controls require a hyperscaler-managed environment.
