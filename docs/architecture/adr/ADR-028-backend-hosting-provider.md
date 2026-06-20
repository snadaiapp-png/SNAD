# ADR-028: Backend Hosting Provider Selection

## Status

Accepted

## Date

2026-06-20

## Context

The SANAD platform backend (Spring Boot 3.3.5, Java 17/21, Docker) needs a production hosting provider that supports:

- Docker container deployment
- Managed PostgreSQL
- Health checks and auto-restart
- GitHub integration for CI/CD
- Environment variable / secrets management
- TLS/HTTPS
- Custom domains
- Reasonable latency for Saudi Arabia / Middle East users
- Low operational complexity for a small team
- Affordable initial monthly cost

## Providers Evaluated

### 1. Render

| Criterion | Assessment |
|---|---|
| Docker support | Native Dockerfile build |
| Managed PostgreSQL | Yes, with automated backups and PITR |
| Private networking | Internal connections between services |
| Health checks | Configurable health check path |
| Zero-downtime deploy | Yes (blue-green on paid plans) |
| Rollback | One-click rollback to previous deploy |
| GitHub integration | Auto-deploy from branch |
| Monorepo support | Root directory + Dockerfile path |
| Custom domains + TLS | Automatic TLS, custom domains on paid plan |
| Secrets management | Encrypted environment variables |
| Logs and metrics | Built-in log streaming + basic metrics |
| Operational complexity | Low — managed, minimal DevOps required |
| Initial monthly cost | ~$7 (Starter web) + ~$7 (PostgreSQL) = ~$14 |
| Scaling path | Vertical (instance size) then horizontal (instances) |
| Vendor lock-in | Low — standard Docker, standard PostgreSQL |
| Saudi/ME latency | Frankfurt (EU Central) is closest available; ~120-180ms estimated RTT to KSA |

### 2. Railway

| Criterion | Assessment |
|---|---|
| Docker support | Yes |
| Managed PostgreSQL | Yes |
| Health checks | Limited (TCP only on free tier) |
| Zero-downtime deploy | Yes |
| GitHub integration | Yes |
| Custom domains + TLS | Yes |
| Operational complexity | Low |
| Initial monthly cost | Usage-based, ~$5-15 |
| Saudi/ME latency | Similar to Render (US regions) |
| Vendor lock-in | Low |

**Rejected**: Less mature health check support; usage-based pricing is less predictable.

### 3. Fly.io

| Criterion | Assessment |
|---|---|
| Docker support | Excellent (Firecracker VMs) |
| Managed PostgreSQL | Via Fly Postgres (semi-managed) |
| Health checks | Yes |
| Zero-downtime deploy | Yes |
| GitHub integration | Via GitHub Actions (no native auto-deploy) |
| Custom domains + TLS | Yes |
| Operational complexity | Medium — requires more configuration |
| Initial monthly cost | ~$3-10 |
| Saudi/ME latency | Has Middle East region (Amman, Jordan) |
| Vendor lock-in | Low |

**Rejected**: Semi-managed PostgreSQL requires more operational effort; less turnkey than Render for a small team.

### 4. AWS App Runner + RDS

| Criterion | Assessment |
|---|---|
| Docker support | Yes |
| Managed PostgreSQL | RDS (fully managed, excellent) |
| Health checks | Yes |
| Zero-downtime deploy | Yes (blue-green) |
| GitHub integration | Via CodePipeline or App Runner source |
| Custom domains + TLS | Yes (ACM + Route 53) |
| Operational complexity | High — multiple AWS services to manage |
| Initial monthly cost | ~$25+ (App Runner) + ~$15+ (RDS) = ~$40+ |
| Saudi/ME latency | Bahrain (me-south-1) region available |
| Vendor lock-in | Medium |

**Rejected**: Too complex and expensive for the current delivery phase; appropriate for scale-up later.

## Decision

**Selected Provider: Render**

### Rationale

1. **Lowest operational complexity** — Render provides a turnkey Docker + PostgreSQL experience with minimal configuration. A small team can deploy and maintain the backend without dedicated DevOps.

2. **Predictable pricing** — Fixed monthly plans ($7 Starter web + $7 PostgreSQL) are more predictable than usage-based pricing.

3. **Native Dockerfile support** — The existing Dockerfile works without modification.

4. **Health check path** — Render supports HTTP health checks at `/actuator/health`, which aligns with the existing Spring Boot Actuator configuration.

5. **One-click rollback** — Render's dashboard provides rollback to any previous deployment, satisfying the rollback requirement.

6. **GitHub auto-deploy** — Render can auto-deploy from `main` on push, integrating naturally with the existing CI/CD pipeline.

7. **Infrastructure as Code** — `render.yaml` Blueprint allows version-controlled infrastructure definition.

8. **Managed PostgreSQL with backups** — Render's PostgreSQL includes daily backups; retention depends on Workspace plan (Hobby: 3-day, Pro+: 7-day).

### Region Decision

Render's available regions as of 2026:
- Oregon (US West) — `oregon`
- Ohio (US East) — `ohio`
- Frankfurt (EU Central) — `frankfurt`

**Selected: Frankfurt (EU Central)**

#### Region Comparison

| Region | Approximate RTT to KSA | Rationale |
|---|---|---|
| Frankfurt | ~120-180ms | Shorter geographic distance to KSA; major ME peering hub (DE-CIX) |
| Oregon | ~250-300ms | Trans-Pacific route; significantly longer distance |
| Singapore | ~180-220ms | Via Indian Ocean; longer than Frankfurt |

#### Uncertainty Statement

The above RTT estimates are approximate, based on geographic distance, typical backbone routing, and published cloud latency benchmarks. **Actual latency has not been measured from Saudi Arabia.** Estimates carry ±50ms uncertainty. A formal latency test from a KSA endpoint should be conducted post-deployment.

#### Why Frankfurt Over Oregon

1. Geographic proximity: Frankfurt is ~4,100km from Riyadh vs ~12,000km for Oregon
2. Network routing: European backbone has high-capacity links to MENA cable systems
3. DE-CIX (Frankfurt) is one of the world's largest IXPs with direct MENA peering
4. Conservative default: when measured latency is unavailable, choose geographically closer region

Backend and database must be in the same region (Frankfurt).

## Migration Triggers

The following conditions would trigger evaluation of migration to AWS, Azure, or Kubernetes:

1. **Latency requirement** — If sub-150ms latency to KSA becomes a hard requirement, migrate to AWS (Bahrain) or Azure (UAE).
2. **Scale** — If concurrent users exceed 10,000 or database size exceeds 100GB, migrate to AWS RDS + ECS/EKS.
3. **Compliance** — If Saudi data residency is required, migrate to a provider with KSA region (e.g., Oracle Cloud Jeddah, Google Cloud Dammam).
4. **Multi-region** — If active-active multi-region is needed, migrate to Kubernetes on EKS/GKE.
5. **Cost at scale** — If Render costs exceed $500/month, evaluate self-managed Kubernetes for better cost efficiency.
