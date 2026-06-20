# ADR-028: Backend Hosting Provider Selection

## Status

Accepted for initial production deployment readiness. Provider provisioning and runtime validation remain pending.

## Date

2026-06-20

## Context

SANAD requires a production provider for its Spring Boot backend and PostgreSQL database with Docker deployment, health checks, secrets management, TLS, GitHub integration, and low operational overhead.

## Providers Evaluated

### Render

| Criterion | Assessment |
|---|---|
| Docker support | Native Dockerfile builds |
| Managed PostgreSQL | Supported; exact backup and recovery capabilities depend on selected plans |
| Private networking | Supported between provisioned services |
| Health checks | Configurable HTTP health checks |
| Deployment controls | Manual and automated deployment controls are supported |
| Rollback | Provider supports rollback to previous deployments; SANAD rollback has not yet been validated |
| GitHub integration | Supported; SANAD uses a manual first deployment with `autoDeployTrigger: off` |
| Monorepo support | Root directory, Dockerfile path, and Docker context supported |
| Custom domains and TLS | Supported; not yet configured for SANAD backend |
| Secrets management | Supported through provider environment variables and `fromDatabase` references |
| Logs and metrics | Platform capabilities available; actual visibility and retention require provisioning verification |
| Operational complexity | Low relative to hyperscaler alternatives |
| Initial monthly cost | Planning estimate only; final price must be confirmed in Render Dashboard |
| Vendor lock-in | Limited by standard Docker and PostgreSQL usage |
| Saudi/Middle East latency | Frankfurt selected as the initial region; latency from Saudi Arabia remains unmeasured |

### Railway

Supports Docker and managed PostgreSQL with low operational complexity, but pricing predictability and production-operational controls were considered less suitable for the current phase.

### Fly.io

Provides strong container placement and broader region options, but its PostgreSQL operational model would require more platform-management effort.

### AWS App Runner and RDS

Provides mature regional, scaling, security, and database capabilities, including Middle East regions, but introduces materially higher cost and operational complexity for the current delivery phase.

## Decision

**Selected provider: Render**

**Selected initial region: Frankfurt — EU Central**

### Rationale

1. Render offers a low-complexity path for Docker and managed PostgreSQL.
2. The current SANAD Dockerfile and application health endpoints align with the provider model.
3. `render.yaml` allows infrastructure configuration to remain version controlled.
4. Database credentials can be wired through `fromDatabase` without committing secrets.
5. The first release can remain manual while deployment, smoke testing, and rollback are validated.
6. Standard Docker and PostgreSQL reduce migration friction if a future move is required.

### Deployment Policy

- First deployment: manual.
- Current Blueprint setting: `autoDeployTrigger: off`.
- Automatic deployment: disabled until the first deployment and rollback path are verified.
- Official Render Blueprint validation: pending manual provisioning gate.

### Capability Disclaimer

Render supports health checks, deployment controls, rollback, managed database operations, TLS, and observability features. These capabilities have not yet been configured or validated for SANAD and must not be treated as active production controls before provisioning.

### Pricing Estimate

- Estimated web service price: approximately USD 7 per month, pending confirmation.
- Estimated `basic-256mb` PostgreSQL price: pending confirmation in Render Dashboard.
- Estimated total: planning estimate only; final monthly cost must be confirmed before provisioning.

## Region Decision

Frankfurt is selected as the conservative initial region because of geographic proximity to Saudi Arabia relative to North American regions. No measured KSA-to-Render latency evidence is available yet.

A formal latency test from Saudi Arabia must be conducted after deployment. If latency, data residency, compliance, or scale requirements are not met, SANAD should evaluate a Middle East hyperscaler region or another provider.

## Migration Triggers

Re-evaluate the provider when any of the following occurs:

1. Saudi data-residency requirements become mandatory.
2. Measured latency does not meet product requirements.
3. Database scale, traffic, or availability requirements exceed the selected plans.
4. Multi-region active-active deployment becomes necessary.
5. Compliance or operational controls require a hyperscaler-managed environment.
6. Render cost or platform constraints exceed the value of its operational simplicity.
