# Repository Audit Report

## 1. Repository Structure

```
SNAD-https/
├── apps/
│   ├── sanad-platform/          # Spring Boot backend (Java 21, Maven)
│   │   ├── src/main/java/com/sanad/platform/
│   │   │   ├── access/          # RBAC: capabilities, roles, grants
│   │   │   ├── admin/           # Platform admin services
│   │   │   ├── api/             # Platform API utilities
│   │   │   ├── application/     # Application configuration
│   │   │   ├── config/          # App config, migration, CORS
│   │   │   ├── controlplane/    # Control Plane API controllers
│   │   │   ├── crm/web/         # CRM API: controller, service, models
│   │   │   ├── domain/          # Domain entities
│   │   │   ├── infrastructure/  # Infrastructure services
│   │   │   ├── organization/    # Organization management
│   │   │   ├── security/        # JWT, auth, tenant isolation, RBAC
│   │   │   ├── shared/          # Shared API utilities, error handling
│   │   │   ├── tenant/          # Tenant context, RLS, multi-tenancy
│   │   │   └── user/            # User management
│   │   ├── src/main/resources/
│   │   │   ├── db/migration/    # 21 Flyway SQL migrations + 1 Java V15
│   │   │   └── application*.yml # Spring profiles: local, dev, prod
│   │   ├── Dockerfile           # Multi-stage Docker build
│   │   └── pom.xml              # Maven project (Spring Boot 3.5.6)
│   └── web/                     # Next.js frontend (React, TypeScript)
│       ├── app/                 # Next.js App Router
│       │   ├── api/platform/    # BFF proxy to backend
│       │   ├── control-plane/   # Admin dashboard
│       │   ├── crm/             # CRM workspace
│       │   ├── workspace/       # Tenant workspace
│       │   └── reset-password/  # Password reset flow
│       ├── components/          # Shared React components
│       └── lib/                 # Frontend utilities
├── docs/                        # Project documentation
├── scripts/                     # CI/CD and ops scripts
├── tests/                       # Integration and security tests
├── .github/workflows/           # 36 GitHub Actions workflows
├── render.yaml                  # Render deployment blueprint
└── .gitleaks.toml               # Secret scanning configuration
```

## 2. Backend Component Map

| Component | Location | Purpose | Status | Dependencies |
|-----------|----------|---------|--------|--------------|
| Multi-Tenant Core | `tenant/` | TenantContext, RLS, TenantContextProvider | OPERATIONAL | PostgreSQL RLS |
| Security | `security/` | JWT auth, session validation, RBAC aspect | OPERATIONAL | tenant/, user/ |
| RBAC | `access/` | Capabilities, roles, grants, evaluation | OPERATIONAL | V14 seed |
| User Management | `user/` | Users, memberships, credentials | OPERATIONAL | organization/ |
| Organization | `organization/` | Orgs, memberships | OPERATIONAL | tenant/ |
| CRM | `crm/web/` | Accounts, contacts, leads, pipelines, opportunities, activities | OPERATIONAL | V20260702_1 |
| Admin | `admin/` | Platform admin, SaaS admin, tenant directory | OPERATIONAL | V19 |
| Control Plane | `controlplane/` | Platform operations, SaaS admin APIs | OPERATIONAL | admin/ |
| Flyway V15 | `config/migration/` | Java migration: seed RBAC roles | PRODUCTION-MATCHED | V14 |
| Reconciler | `db/migration/V20260702_2` | ADMIN role + capability reconciliation | OPERATIONAL | V15, V20260702_1 |

## 3. Frontend Component Map

| Component | Location | Purpose | Status |
|-----------|----------|---------|--------|
| Auth Flow | `app/(auth)/` | Login, credential rotation, tenant picker | OPERATIONAL |
| Workspace | `app/workspace/` | Tenant dashboard | OPERATIONAL |
| Control Plane | `app/control-plane/` | Admin dashboard | NEEDS RENDER CONFIG |
| CRM Workspace | `app/crm/` | CRM operational workspace | OPERATIONAL |
| BFF Proxy | `app/api/platform/` | Next.js BFF to backend | OPERATIONAL |
| Password Reset | `app/reset-password/` | Password reset flow | OPERATIONAL |

## 4. Database Schema Summary

### Core Tables (V1-V14)
- tenants, organizations, organization_memberships
- users, roles, access_capabilities, role_capabilities, user_role_assignments
- auth_credentials, password_reset_tokens

### Platform Identity (V16)
- Extended tenants and users with platform identity columns

### SaaS Administration (V17-V19)
- platform_audit_logs, system_services
- saas_plans, saas_plan_entitlements, tenant_subscriptions
- subscription_change_events, billing_invoices

### CRM Core (V20260702_1)
- crm_accounts, crm_contacts, crm_pipelines, crm_pipeline_stages
- crm_leads, crm_opportunities, crm_opportunity_stage_history
- crm_activities, crm_timeline_events, crm_import_jobs
- crm_custom_field_definitions

### RBAC Reconciliation (V20260702_2)
- Forward-only migration: creates ADMIN roles + capability grants

## 5. Flyway Configuration

| Profile | Locations | V15 Type | Status |
|---------|-----------|----------|--------|
| local (H2) | `classpath:db/migration` | JDBC | OPERATIONAL |
| dev (H2) | `classpath:db/migration` | JDBC | OPERATIONAL |
| prod (PostgreSQL) | `classpath:db/migration` | JDBC | PRODUCTION-MATCHED |

## 6. CI/CD Summary

| Workflow | Purpose | Status |
|----------|---------|--------|
| ci.yml | Build + test on PR | ACTIVE |
| production-release.yml | Exact-commit deploy to Render | ACTIVE |
| backend-production-smoke.yml | Production smoke test | ACTIVE |
| postgres-acceptance.yml | PostgreSQL integration tests | ACTIVE |
| security-scan.yml | OWASP + gitleaks | ACTIVE |
| web-ci.yml | Frontend lint + build + test | ACTIVE |

## 7. Known Issues

1. **SANAD_CONTROL_PLANE_TENANT_ID** not set in Render — blocks production release
2. **No db/migration-pg-only on main** — RLS migrations are not in production
3. **fix/flyway-forward-migrations-20260703** branch has forward-only audit/RLS migrations but was reverted to match main
4. **Testcontainers tests** require Docker (not available in all environments)
5. **V15 Java migration** uses `java.sql.Types.OTHER` for PostgreSQL UUID compatibility

