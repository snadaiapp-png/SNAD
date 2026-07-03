# Environment Setup Guide

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+ (or use wrapper)
- Node.js 20+ with pnpm
- PostgreSQL 16 (for production-like local testing)
- Docker (for Testcontainers tests)

## Backend Local Development

```bash
cd apps/sanad-platform

# Run with H2 in-memory database (default profile)
mvn spring-boot:run

# Run with local profile (H2 + debug logging)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Run with PostgreSQL (requires local PG instance)
mvn spring-boot:run -Dspring-boot.run.profiles=prod \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/sanad \
  -Dspring.datasource.username=sanad \
  -Dspring.datasource.password=sanad
```

## Frontend Local Development

```bash
cd apps/web
npm install
npm run dev
# Opens at http://localhost:3000
```

## Environment Variables

### Backend (Required for Production)
```
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://<host>:5432/<db>
DATABASE_USERNAME=<username>
DATABASE_PASSWORD=<password>
JWT_SECRET=<32+ byte secret>
BOOTSTRAP_ENABLED=false
SANAD_CONTROL_PLANE_TENANT_ID=<valid tenant UUID>
SANAD_CORS_ALLOWED_ORIGINS=https://<frontend-domain>
```

### Backend (Optional)
```
SECURITY_NOTIFICATION_PROVIDER=smtp
SECURITY_NOTIFICATION_FROM=<email>
SPRING_MAIL_HOST=<smtp-host>
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<email>
SPRING_MAIL_PASSWORD=<app-password>
```

### Frontend
```
NEXT_PUBLIC_API_BASE_URL=<backend-url>
```

## Database Setup

### Fresh Installation
```bash
# H2 (local profile) — automatic
# PostgreSQL — Flyway runs on startup
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### Migration Verification
```bash
# Check migration status
psql -U sanad -d sanad -c "SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;"
```

## Docker Build

```bash
cd apps/sanad-platform
docker build --build-arg RENDER_GIT_COMMIT=$(git rev-parse HEAD) -t sanad-platform .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/sanad \
  -e DATABASE_USERNAME=sanad \
  -e DATABASE_PASSWORD=sanad \
  -e JWT_SECRET=test-secret-32-bytes-minimum-key-1234 \
  -e BOOTSTRAP_ENABLED=false \
  sanad-platform
```

## CI/CD

### Required GitHub Secrets
```
RENDER_API_KEY
RENDER_SERVICE_ID
PRODUCTION_DATABASE_URL
PRODUCTION_BASE_URL
WEB_PRODUCTION_BASE_URL
```

### Required GitHub Variables
```
PRODUCTION_BASE_URL=https://sanad-backend-mcrj.onrender.com
WEB_PRODUCTION_BASE_URL=https://snad-app.vercel.app
```

