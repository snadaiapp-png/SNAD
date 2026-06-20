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
- Hosted on Render (production)

### Infrastructure
- Docker (multi-stage build, non-root user)
- GitHub Actions CI/CD (3 backend jobs + frontend CI + production smoke)
- Vercel (frontend)
- Render (backend + managed PostgreSQL)

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

| Service | URL |
|---|---|
| Frontend | https://snad-app.vercel.app |
| Backend | https://sanad-backend.onrender.com |
| Backend Health | https://sanad-backend.onrender.com/actuator/health |

## Documentation

- [Backend Runtime](docs/deployment/backend-runtime.md)
- [Render Deployment Guide](docs/deployment/render-backend-deployment.md)
- [Monitoring Baseline](docs/operations/backend-monitoring.md)
- [ADR-028: Hosting Provider Selection](docs/architecture/adr/ADR-028-backend-hosting-provider.md)
- [Progress Report](docs/execution/progress-report.md)

Status: Stage 4 - Backend Production Release
