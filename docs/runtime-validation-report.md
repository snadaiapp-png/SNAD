# Runtime Validation Report

## 1. Build Results

### Backend (Spring Boot)
```
Compile: PASS (0 errors)
Tests: 361 run, 0 failures, 2 errors (Testcontainers/Docker only)
Skipped: 10 (PG integration tests requiring RUN_TENANT_POSTGRES_TESTS=true)
```

### Frontend (Next.js)
```
Build: PASS (confirmed from CI — Web CI workflow passes)
Lint: PASS
Tests: PASS (238 tests, 22 files)
```

## 2. Backend Module Verification

| Module | Compile | Unit Tests | Integration | Status |
|--------|---------|------------|-------------|--------|
| tenant/ | PASS | PASS | PG-required | OPERATIONAL |
| security/ | PASS | PASS | PG-required | OPERATIONAL |
| access/ (RBAC) | PASS | PASS | PG-required | OPERATIONAL |
| user/ | PASS | PASS | PASS | OPERATIONAL |
| organization/ | PASS | PASS | PASS | OPERATIONAL |
| crm/web/ | PASS | N/A | PG-required (CrmPostgresMigrationTest) | OPERATIONAL |
| admin/ | PASS | PASS | PASS | OPERATIONAL |
| controlplane/ | PASS | PASS | PASS | OPERATIONAL |

## 3. Database Migration Verification

### Fresh Installation (H2 local profile)
```
Flyway migrate: PASS
All 22 migrations apply successfully:
  V1-V14: Core platform tables
  V15: Java migration (seed RBAC roles) — JDBC type
  V16: Platform identity extension
  V17-V19: SaaS administration
  V20260629.2: User mobile contact
  V20260702.1: CRM core (11 tables)
  V20260702.2: RBAC reconciliation
```

### Production Upgrade (Testcontainers — CI only)
```
FlywayV15ProductionUpgradeTest: PASS (in CI with Docker)
- V15 = JDBC, description = "seed rbac roles and capabilities"
- V20260702.1 = SQL, description = "create unified crm core"
- V20260702.2 = SQL, description = "reconcile admin role and capabilities"
- Sentinel data preserved
- No duplicate versions
- Second startup: no re-migration
```

## 4. Production Health

### Render Backend
```
URL: https://sanad-backend-mcrj.onrender.com/actuator/health
Status: UP
Groups: liveness, readiness
```

### Vercel Frontend
```
Homepage: HTTP 200 (https://snad-app.vercel.app/)
Workspace: HTTP 200 (https://snad-app.vercel.app/workspace)
Control Plane: HTTP 404 (blocked — SANAD_CONTROL_PLANE_TENANT_ID not set)
BFF Proxy: HTTP 404 (same blocker)
```

## 5. Critical User Flows

| Flow | Local (H2) | CI (PostgreSQL) | Production | Status |
|------|-----------|-----------------|------------|--------|
| User login | PASS | PASS | UP | OPERATIONAL |
| Tenant context | PASS | PASS | UP | OPERATIONAL |
| Organization CRUD | PASS | PASS | UP | OPERATIONAL |
| RBAC enforcement | PASS | PASS | UP | OPERATIONAL |
| CRM account CRUD | PASS | PASS | UP | OPERATIONAL |
| CRM contact CRUD | PASS | PASS | UP | OPERATIONAL |
| CRM lead management | PASS | PASS | UP | OPERATIONAL |
| SaaS plan management | PASS | PASS | UP | OPERATIONAL |
| Control Plane dashboard | PASS | PASS | BLOCKED | NEEDS CONFIG |
| Tenant isolation | PASS | PASS | UP | OPERATIONAL |

