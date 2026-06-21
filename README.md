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
- Backend hosting: Render
- Pilot backend deployed and verified
- Commercial production not approved
- Free-tier pilot only

### Infrastructure
- Docker (multi-stage build, non-root user)
- GitHub Actions CI/CD
- Vercel frontend
- Render backend
- Supabase PostgreSQL pilot database

## Project Structure

```
apps/
  web/              # Next.js frontend
  sanad-platform/   # Spring Boot backend
docs/
  architecture/adr/ # Architecture Decision Records
  deployment/       # Deployment guides
  development/      # Developer guides
  execution/        # Execution progress reports
  operations/       # Operations and monitoring
.github/workflows/  # CI/CD pipelines
```

## Pilot URLs

| Service | URL | Status |
|---|---|---|
| Frontend | https://snad-app.vercel.app | Live pilot |
| Backend | https://sanad-backend-mcrj.onrender.com | Live pilot |

## Documentation

- [Backend Runtime](docs/deployment/backend-runtime.md)
- [Render Deployment Guide](docs/deployment/render-backend-deployment.md)
- [Monitoring Baseline](docs/operations/backend-monitoring.md)
- [Frontend API Client Guide](docs/development/frontend-api-client.md)
- [Progress Report](docs/execution/progress-report.md)

Status: Stage 5 - Frontend–Backend Integration Foundation (IN PROGRESS)
