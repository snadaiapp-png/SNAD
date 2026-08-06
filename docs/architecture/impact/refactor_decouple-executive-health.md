# Architecture Impact Analysis — Executive Management + System Health Decoupling

> **Branch:** `refactor/decouple-executive-health`
> **Date:** 2026-08-07
> **Author:** SANAD Architecture Team
> **Reference:** `docs/governance/ARCHITECTURE-PROTECTION-POLICY.md`

This document satisfies the Architecture Protection Policy §14 (Code Review Policy)
and §13 (Protected Directories) requirements for changes to protected directories
in the `refactor/decouple-executive-health` branch.

---

## 1. Architecture impact analysis

### 1.1 Summary of changes

This PR completes the **decoupling** of Executive Management and System Health
from the legacy combined "Control Plane" module. After this change, both
Executive Management and System Health are independent bounded contexts with
zero cross-imports, each owning their own UI, routes, navigation, API, controllers,
services, DTOs, permissions, feature flags, and tests.

### 1.2 Bounded contexts affected

| Bounded Context | Status Before | Status After |
|---|---|---|
| Executive Management | Combined with System Health under `/control-plane` | Independent module at `/executive` |
| System Health | Combined with Executive Management under `/control-plane` | Independent module at `/system-health` |
| Core Platform | Held shared control-plane logic | Pure — no business logic |
| Identity | Unchanged | Unchanged |

### 1.3 Architectural invariants preserved

- Multi-Tenant SaaS — every request carries a tenant scope (no hardcoded `tenantId="default"`)
- Modular Architecture — bounded contexts, no cross-domain DB sharing
- Domain-Driven Design — pure domain, ports & adapters
- API-First — every endpoint under `/api/v1/executive/**` and `/api/v1/system-health/**`
- Security by Design — every endpoint declares `@RequireCapability`
- Tenant Data Isolation — every query is tenant-scoped

---

## 2. Dependency analysis

### 2.1 Backend — `com.sanad.platform.executive.*`

- **Imports:** only `com.sanad.platform.shared`, `com.sanad.platform.config`,
  `com.sanad.platform.security`, `com.sanad.platform.tenant`, `java.*`, `jakarta.*`,
  Spring Framework, Jackson, SLF4J
- **Does NOT import:** `com.sanad.platform.health`, `com.sanad.platform.crm`, any other bounded context
- **Layer direction:** `api → service → domain/infrastructure` (correct)

### 2.2 Backend — `com.sanad.platform.health.*`

- **Imports:** only `com.sanad.platform.shared`, `com.sanad.platform.config`,
  `com.sanad.platform.security`, `com.sanad.platform.tenant`, `java.*`, `jakarta.*`,
  Spring Framework, Jackson, SLF4J
- **Does NOT import:** `com.sanad.platform.executive`, `com.sanad.platform.crm`, any other bounded context
- **Layer direction:** `api → service → domain/infrastructure` (correct)

### 2.3 Frontend — `apps/web/app/executive/`

- **Imports:** only `lib/api/executive-api.ts`, `lib/navigation/executive-navigation.ts`,
  `lib/routes/executive-routes.ts`, `lib/modules/executive-module.ts`,
  `lib/feature-flags/feature-flags.ts`, `lib/i18n/*`, `lib/design-system/*`
- **Does NOT import:** `app/system-health/*`, `lib/api/system-health-*`, any other business module

### 2.4 Frontend — `apps/web/app/system-health/`

- **Imports:** only `lib/api/system-health-api.ts`, `lib/navigation/system-health-navigation.ts`,
  `lib/routes/system-health-routes.ts`, `lib/modules/system-health-module.ts`,
  `lib/feature-flags/feature-flags.ts`, `lib/i18n/*`, `lib/design-system/*`
- **Does NOT import:** `app/executive/*`, `lib/api/executive-*`, any other business module

### 2.5 Validation

- dependency-cruiser config: `apps/web/.dependency-cruiser.cjs` (10 rules, all PASS)
- Frontend Python validator: `scripts/architecture/check_frontend_boundaries.py` (PASS)
- Backend Python validator: `scripts/architecture/check_backend_boundaries.py` (PASS)
- Multi-tenant validator: `scripts/architecture/check_tenant_hardcoding.py` (PASS)
- All checks pass with zero violations.

### 2.6 Circular dependencies

None. Validated by dependency-cruiser's `no-circular` rule.

---

## 3. Risk analysis

### 3.1 Low-risk changes

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Production users hitting old `/control-plane` URL | Low | Low | `/control-plane` is preserved as a redirect to `/executive` (the natural successor) |
| Stale bookmarks to `/control-plane/health` tab | Low | Low | Redirect handles this gracefully |
| Feature flag default state hides new modules | Low | Medium | Both `EXECUTIVE_MODULE` and `SYSTEM_HEALTH_MODULE` default to `true` |

### 3.2 Medium-risk changes

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| RBAC capabilities not yet assigned to roles | Medium | Medium | Flyway migration `V20260806_1__seed_executive_health_capabilities.sql` seeds the 6 capabilities; role assignment is a follow-up ops task |
| Backend API path change `/api/v1/control-plane/health` → `/api/v1/system-health` | Medium | Low | Old path returns 410 Gone with a `Location` header pointing to the new path |

### 3.3 High-risk changes

None.

---

## 4. Migration strategy

### 4.1 Backend migration

1. New controllers registered at `/api/v1/executive/**` and `/api/v1/system-health/**`
2. Old `controlplane` package deleted (controllers, services, DTOs all moved)
3. Flyway migration `V20260806_1__seed_executive_health_capabilities.sql` seeds 6 capabilities:
   - `EXECUTIVE_VIEW`, `EXECUTIVE_MANAGE`, `EXECUTIVE_BILLING`
   - `SYSTEM_HEALTH_VIEW`, `SYSTEM_HEALTH_MONITOR`, `SYSTEM_HEALTH_ALERTS`
4. Spring Boot will auto-wire the new controllers; no manual registration needed

### 4.2 Frontend migration

1. New routes `/executive` and `/system-health` added to App Router
2. New module entries in `lib/modules/executive-module.ts` and `lib/modules/system-health-module.ts`
3. New feature flags `EXECUTIVE_MODULE` and `SYSTEM_HEALTH_MODULE` in `lib/feature-flags/feature-flags.ts`
4. New navigation entries in `lib/navigation/`
5. New route registries in `lib/routes/`
6. `/control-plane` page replaced with a redirect to `/executive`
7. Workspace cards added for both `/executive` and `/system-health`

### 4.3 Rollout sequence

1. Merge PR to `main`
2. Vercel auto-deploys frontend from `main`
3. Render auto-deploys backend from `main`
4. Smoke tests verify `/executive` and `/system-health` return HTTP 200 on production
5. Old `/control-plane` URL redirects to `/executive` (handled at the page level)

---

## 5. Rollback strategy

### 5.1 Frontend rollback

If `/executive` or `/system-health` is broken in production:

1. Use Vercel's instant rollback to the previous deployment
2. Frontend reverts to the previous build (still has working `/control-plane`)
3. Backend is unaffected — both old and new API paths remain available

### 5.2 Backend rollback

If backend `/api/v1/executive/**` or `/api/v1/system-health/**` is broken:

1. Use Render's manual deploy to roll back to the previous commit
2. The Flyway migration is forward-only — capabilities are harmless if rolled back (they simply won't be checked)
3. Frontend `/control-plane` page is preserved as a redirect; if the rollback removes the redirect page, the old route will 404 — handle by also deploying a one-line `redirect /control-plane → /workspace` fallback

### 5.3 Database rollback

The `V20260806_1` migration only INSERTs capability rows. Rollback (if ever needed)
is a simple `DELETE FROM capabilities WHERE name IN ('EXECUTIVE_VIEW', 'EXECUTIVE_MANAGE', 'EXECUTIVE_BILLING', 'SYSTEM_HEALTH_VIEW', 'SYSTEM_HEALTH_MONITOR', 'SYSTEM_HEALTH_ALERTS')`.

---

## 6. Updated documentation

The following documentation is updated by this PR:

- `docs/governance/ARCHITECTURE-PROTECTION-POLICY.md` — new permanent governance policy
- `docs/operations/EXECUTIVE-HEALTH-DEPLOYMENT.md` — updated to reflect decoupled deployment
- `CONSTITUTION.md` — §2 (Architectural Principles) updated to reference the new policy
- This document — `docs/architecture/impact/refactor_decouple-executive-health.md`

---

## 7. Acceptance

This PR satisfies all requirements of the SANAD Architecture Protection Policy:

- Architecture impact analysis (§14)
- Dependency analysis (§14)
- Risk analysis (§14)
- Migration strategy (§14)
- Rollback strategy (§14)
- Updated documentation (§14)
- All CI gates pass (§10, §15)
- No cross-module imports (§4)
- No hardcoded tenant IDs (§8)
- Each module owns its own RBAC, feature flags, routes, navigation, module registry (§7, §9)
