# SANAD Global AI ERP SaaS

Enterprise AI-Powered ERP, CRM and Workflow Platform.

## Stack

### Frontend
- Next.js 16
- React 19
- TypeScript
- Tailwind CSS
- Hosted on Vercel: https://snad-app.vercel.app

### Backend
- Spring Boot 3.3.5
- Java 17 (target) / 21 (runtime)
- Maven
- PostgreSQL 16 (Flyway migrations V1-V9)
- Backend hosting target: Render
- Provisioning status: pending
- Production URL: pending verification

### Infrastructure
- Docker (multi-stage build, non-root user)
- GitHub Actions CI/CD (backend + frontend + Docker + Render Blueprint validation)
- Vercel (frontend)
- Render (backend + managed PostgreSQL) — pending provisioning

## Project Structure

```
apps/
  web/              # Next.js frontend
  sanad-platform/   # Spring Boot backend
docs/
  architecture/adr/ # Architecture Decision Records
  deployment/       # Deployment guides
  execution/        # Execution progress reports
  operations/       # Operations and monitoring
.github/workflows/  # CI/CD pipelines
render.yaml         # Render Blueprint
.env.example        # Environment variable template
```

## Production URLs

| Service | URL | Status |
|---|---|---|
| Frontend | https://snad-app.vercel.app | Live |
| Backend | Pending provisioning | Pending |

## Documentation

- [Backend Runtime](docs/deployment/backend-runtime.md)
- [Render Deployment Guide](docs/deployment/render-backend-deployment.md)
- [Monitoring Baseline](docs/operations/backend-monitoring.md)
- [ADR-028: Hosting Provider Selection](docs/architecture/adr/ADR-028-backend-hosting-provider.md)
- [Progress Report](docs/execution/progress-report.md)

Status: Stage 4 - Backend Production Release (IN PROGRESS)
