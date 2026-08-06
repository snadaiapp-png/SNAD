# CRM-007 PROD-003: Environment Certification

> **Agent:** Agent 7 — Production Readiness Auditor
> **Command:** CRM-007-CLOSURE-007
> **Task:** 3 — Environment Certification
> **Date:** 2026-07-28
> **Status:** PASS

---

## 1. Executive Summary

Production environment configuration is fully documented with secure defaults, environment separation, and comprehensive variable matrix. All required variables are documented and validated.

---

## 2. Environment Variables Matrix

### 2.1 Database Configuration

| Variable | Source | Validation | Status |
|---|---|---|---|
| DATABASE_URL | Render Secret | JDBC format, SSL required | PASS |
| DATABASE_USERNAME | Render Secret | Non-empty | PASS |
| DATABASE_PASSWORD | Render Secret | Non-empty | PASS |
| DATABASE_HOST | Derived from URL | Valid hostname | PASS |
| DATABASE_PORT | Derived from URL | 5432 | PASS |
| DATABASE_NAME | Derived from URL | Valid database name | PASS |

### 2.2 Security Configuration

| Variable | Source | Validation | Status |
|---|---|---|---|
| JWT_SECRET | Render auto-generated | 48+ bytes | PASS |
| SECURITY_NOTIFICATION_RESEND_API_KEY | Render Secret | Non-empty | PASS |
| SECURITY_NOTIFICATION_FROM | Render Secret | Valid email | PASS |

### 2.3 Application Configuration

| Variable | Source | Value | Status |
|---|---|---|---|
| SPRING_PROFILES_ACTIVE | Render env | prod | PASS |
| JAVA_OPTS | Render env | Memory tuning flags | PASS |
| FLYWAY_ENABLED | Render env | true | PASS |
| JPA_DDL_AUTO | Render env | validate | PASS |
| LAZY_INIT | Render env | true | PASS |
| BOOTSTRAP_ENABLED | Render env | false | PASS |
| MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE | Render env | health | PASS |

### 2.4 CORS Configuration

| Variable | Source | Value | Status |
|---|---|---|---|
| CORS_ALLOWED_ORIGINS | Render env | https://snad-app.vercel.app | PASS |

### 2.5 Control Plane Configuration

| Variable | Source | Validation | Status |
|---|---|---|---|
| SANAD_CONTROL_PLANE_TENANT_ID | Render Secret | Valid UUID | PASS |

### 2.6 Connection Pool Configuration

| Variable | Source | Value | Status |
|---|---|---|---|
| HIKARI_MAXIMUM_POOL_SIZE | Render env | 3 | PASS |
| HIKARI_MINIMUM_IDLE | Render env | 1 | PASS |
| HIKARI_CONNECTION_TIMEOUT | Render env | 30000 (30s) | PASS |

### 2.7 Logging Configuration

| Variable | Source | Value | Status |
|---|---|---|---|
| LOGGING_LEVEL_ROOT | Render env | WARN | PASS |
| LOGGING_LEVEL_SANAD | Render env | INFO | PASS |

---

## 3. Environment Separation

### 3.1 Environment Matrix

| Environment | Frontend | Backend | Database | Status |
|---|---|---|---|---|
| Development | localhost:3000 | localhost:8080 | H2/Local PG | PASS |
| CI | Vercel preview | Local/Testcontainers | H2/Testcontainers | PASS |
| Staging | Not provisioned | Not provisioned | Not provisioned | DEFERRED |
| Production | snad-app.vercel.app | sanad-backend-mcrj.onrender.com | Supabase PostgreSQL | PASS |

### 3.2 Configuration Separation

| Aspect | Development | Production | Status |
|---|---|---|---|
| Spring Profile | default | prod | PASS |
| DDL Auto | create/update | validate | PASS |
| Bootstrap | enabled | disabled | PASS |
| CORS | localhost | Vercel origin only | PASS |
| Logging | DEBUG | WARN/INFO | PASS |
| Connection Pool | Default | Tuned for free-tier | PASS |

---

## 4. Secure Defaults

### 4.1 Security Controls

| Control | Implementation | Status |
|---|---|---|
| No secrets in Git | sync: false for sensitive vars | PASS |
| JWT secret auto-generated | Render generateValue: true | PASS |
| CORS locked down | Single origin | PASS |
| Bootstrap disabled | No auto-schema changes | PASS |
| Swagger disabled | Returns 404 | PASS |
| Actuator/env disabled | Returns 404 | PASS |
| Management endpoints | health only | PASS |

### 4.2 Secret Management

| Secret | Storage | Access | Status |
|---|---|---|---|
| DATABASE_URL | Render Secret | Service only | PASS |
| DATABASE_USERNAME | Render Secret | Service only | PASS |
| DATABASE_PASSWORD | Render Secret | Service only | PASS |
| JWT_SECRET | Render auto-generated | Service only | PASS |
| RESEND_API_KEY | Render Secret | Service only | PASS |
| CONTROL_PLANE_TENANT_ID | Render Secret | Service only | PASS |

---

## 5. Environment Validation

### 5.1 Production Release Validation

The production-release workflow validates:

| Check | Method | Status |
|---|---|---|
| All secrets present | API validation | PASS |
| HTTPS URLs | URL scheme validation | PASS |
| Bootstrap disabled | Explicit check | PASS |
| Control Plane tenant | Database query | PASS |
| Environment variables | Non-empty validation | PASS |

### 5.2 Runtime Validation

| Check | Method | Status |
|---|---|---|
| Health endpoint | GET /actuator/health | PASS |
| Readiness probe | GET /actuator/health/readiness | PASS |
| Flyway migrations | SQL verification | PASS |
| Auth contract | HTTP assertions | PASS |
| Security boundary | Endpoint verification | PASS |

---

## 6. Environment Documentation

### 6.1 Configuration Files

| File | Purpose | Status |
|---|---|---|
| render.yaml | Render deployment blueprint | PASS |
| fly.toml | Fly.io deployment config | PASS |
| railway.json | Railway deployment config | PASS |
| scripts/.env.example | Environment template | PASS |
| RUNTIME-CONFIGURATION-MATRIX.md | Production topology docs | PASS |

### 6.2 Deployment Documentation

| Document | Purpose | Status |
|---|---|---|
| Self-Hosted Production Runbook | Self-hosted deployment | PASS |
| Operational Readiness Runbook | Operational procedures | PASS |
| Backup/Restore Runbook | Recovery procedures | PASS |
| Auth Rollback Runbook | Authentication recovery | PASS |

---

## 7. Environment Risks

| Risk | Severity | Mitigation | Status |
|---|---|---|---|
| Staging not provisioned | MEDIUM | Production-only pilot | ACCEPTED |
| Free-tier limitations | LOW | Acceptable for pilot | ACCEPTED |
| Single-region deployment | LOW | Pilot scope | ACCEPTED |

---

## 8. Conclusion

### Decision: **PASS**

Production environment is fully documented with secure defaults, environment separation, and comprehensive variable matrix. All required variables are documented and validated through automated checks.

---

**Certification Date:** 2026-07-28
**Agent 7 Task 3 Status:** PASS
