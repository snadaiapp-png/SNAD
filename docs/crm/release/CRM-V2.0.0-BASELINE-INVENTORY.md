# CRM v2.0.0 — Baseline Inventory

| Field | Value |
|-------|-------|
| Inventory Date | 2026-07-30 |
| Baseline Version | crm-v2.0.0 |
| Baseline SHA | `8c2950bc` |

---

## 1. Frontend Inventory

### 1.1 Pages/Routes

| Route | Component | Status |
|-------|-----------|--------|
| `/crm/command-center` | CRM Command Center shell | ✅ Active |
| `/crm/leads` | Leads tab | ✅ Wired |
| `/crm/accounts` | Customers/Accounts tab | ✅ Wired |
| `/crm/accounts/[accountId]` | Customer 360 view | ✅ Wired |
| `/crm/contacts` | Contacts tab | ✅ Wired |
| `/crm/opportunities` | Opportunities tab | ✅ Wired |
| `/crm/pipelines` | Pipeline Kanban board | ✅ Wired |
| `/crm/overview` | Dashboard overview | ✅ Active |
| `/crm/activities` | Activities tab | ✅ Active |
| `/crm/tasks` | Tasks tab (shell) | ✅ Shell |
| `/crm/notes` | Notes tab | ✅ Active |
| `/crm/tags` | Tags tab | ✅ Active |
| `/crm/reports` | Reports tab (shell) | ✅ Shell |
| `/crm/search` | Global search | ✅ Active |
| `/crm/settings/custom-fields` | Custom fields config | ✅ Active |
| `/crm/imports` | Data import | ✅ Active |
| `/crm/integrations` | External integrations | ✅ Active |

### 1.2 Source Files

| Category | Count |
|----------|-------|
| Frontend source files (tsx/ts) | 80 |
| CRM-specific components | 51 |
| Frontend test files | Playwright + Vitest |

### 1.3 Key Libraries

| Library | Version |
|---------|---------|
| Next.js | 16.2.11 |
| React | 19.2.7 |
| TypeScript | 5.9.3 |
| Tailwind CSS | v4 |

---

## 2. Backend Inventory

### 2.1 Package Structure

| Package | Classes | Responsibility |
|---------|---------|----------------|
| `crm/activity` | — | Activity tracking |
| `crm/concurrency` | — | ETag optimistic locking |
| `crm/configuration` | — | Feature toggles |
| `crm/dto` | — | Data transfer objects |
| `crm/error` | 4 | Exception handlers |
| `crm/export` | 1 | Export controller |
| `crm/idempotency` | — | Idempotency key support |
| `crm/integration` | — | External integrations |
| `crm/intelligence` | — | Customer 360 scoring |
| `crm/lead` | — | Lead management |
| `crm/legacy` | — | Legacy migration |
| `crm/mapper` | — | DTO mapping |
| `crm/note` | 1 | Note controller |
| `crm/opportunity` | — | Opportunity/pipeline domain |
| `crm/ownership` | 10+ | Teams, queues, territories |
| `crm/pagination` | — | Cursor pagination |
| `crm/party` | 5+ | Accounts, contacts, addresses |
| `crm/query` | — | Custom query support |
| `crm/reports` | 1 | Reports controller |
| `crm/search` | 1 | Search controller |
| `crm/tag` | 1 | Tag controller |
| `crm/task` | 1 | Task controller |
| `crm/web` | 1 | Address coms controller |

### 2.2 Source Statistics

| Category | Count |
|----------|-------|
| Total Java source files | 577 |
| API Controllers | ~30 (across all packages) |
| Test source files | 175 |
| Non-Docker tests | 920 (all pass) |

---

## 3. API Inventory

### 3.1 API Groups

| Group | Base Path | Operations |
|-------|-----------|------------|
| Users | `/api/v1/users` | 9 |
| Access | `/api/v1/access` | 20 |
| Control Plane | `/api/v1/control-plane` | 35 |
| CRM v1 | `/api/v1/crm` | 125 |
| CRM v2 | `/api/v2/crm` | 140 |
| Business Process | `/api/v1/business-process-e2e` | 2 |
| **Total** | | **357** |

### 3.2 Key CRM Controllers

| Controller | Path |
|------------|------|
| `CrmAddressCommunicationController` | `/api/v1/crm/*` |
| `CustomerMasterController` | `/api/v1/crm/accounts/*` |
| `NoteController` | `/api/v1/crm/notes/*` |
| `SearchController` | `/api/v1/crm/search` |
| `TagController` | `/api/v1/crm/tags/*` |
| `TaskController` | `/api/v1/crm/tasks/*` |
| `ReportsController` | `/api/v1/crm/reports/*` |

---

## 4. Database Inventory

### 4.1 Schema

| Component | Detail |
|-----------|--------|
| Database | PostgreSQL (Supabase) |
| CRM tables | 62 (with RLS enabled) |
| Schema migrations | 58 SQL migration files |

### 4.2 Flyway Migrations (CRM-018 RLS)

| Version | File | Description |
|---------|------|-------------|
| V20260730_1 | `enable_crm_row_level_security.sql` | Enable RLS on all 62 CRM tables |
| V20260730_2 | `disable_crm_row_level_security.sql` | Disable RLS (rollback) |

---

## 5. Security Inventory

### 5.1 Security Layers

| Layer | Description |
|-------|-------------|
| JWT Authentication | Tenant-id in token claims |
| RBAC Authorization | `@RequireCapability`, deny-by-default |
| Application Filtering | `WHERE tenant_id = :t` in all queries |
| Composite Foreign Keys | Cross-reference prevention |
| PostgreSQL RLS (NEW) | `SET LOCAL app.tenant_id` + row-level policies |

### 5.2 RLS Infrastructure

| File | Purpose |
|------|---------|
| `TenantRlsDataSource.java` | DataSource decorator |
| `TenantRlsConnectionHandler.java` | JDBC proxy for `SET LOCAL` |
| `TenantRlsDataSourcePostProcessor.java` | Auto-wiring BPP |

### 5.3 Security Documentation

| Document | Location |
|----------|----------|
| CRM-018 Security Assessment | `docs/crm/crm-018/` |
| G4 Security Certificate | `docs/crm/stage-reports/CRM-G4-SECURITY-CERTIFICATE.md` |

---

## 6. Infrastructure Inventory

| Component | Provider | Status |
|-----------|----------|--------|
| Frontend hosting | Vercel | ✅ Production |
| Backend hosting | Render / Fly.io | ✅ Active |
| Database | Supabase (PostgreSQL) | ✅ Active |
| CI/CD | GitHub Actions | ✅ 128 workflows |
| Monitoring | Vercel Analytics | ✅ Active |
| Source control | GitHub | ✅ Main branch |

---

## 7. Documentation Inventory

| Category | File Count |
|----------|------------|
| CRM documentation (docs/crm/) | ~460 files |
| CRM-010 documentation | 60+ reports |
| CRM-014–020 implementation reports | 30+ files |
| G2/G3/G4 closure packages | 12 files |
| Release artifacts | 9 files |

---

## 8. CRM Prompt Inventory

| CRM ID | Title | Milestone | Status | Evidence |
|--------|-------|-----------|--------|----------|
| CRM-010 | Customer 360 & Intelligence | G3 | ✅ DONE | `docs/crm/crm-010/` |
| CRM-011 | Document production Flyway operations | G1 | ✅ DONE | `docs/crm/CRM-DEPLOYMENT-READINESS.md` |
| CRM-012 | Author the G1 stage report | G1 | ✅ DONE | `docs/crm/crm-012/` |
| CRM-013 | i18n, RTL/LTR, accessibility | G2 | ✅ DONE | `apps/web/app/crm/crm-i18n.tsx` |
| CRM-014 | Wire leads tab | G3 | ✅ DONE | `apps/web/app/crm/(operational)/leads/` |
| CRM-015 | Wire customers tab | G3 | ✅ DONE | `apps/web/app/crm/(operational)/accounts/` |
| CRM-016 | Wire contacts tab | G3 | ✅ DONE | `apps/web/app/crm/(operational)/contacts/` |
| CRM-017 | Wire customer-360 view | G3 | ✅ DONE | `apps/web/app/crm/accounts/[accountId]/` |
| CRM-018 | Row-level security | G4 | ✅ DONE | `security/rls/` + `V20260730_[12]` |
| CRM-019 | Wire opportunities tab | G4 | ✅ DONE | `apps/web/app/crm/(operational)/opportunities/` |
| CRM-020 | Wire pipeline Kanban board | G4 | ✅ DONE | `apps/web/app/crm/(operational)/pipelines/` |

---

## 9. Completed Work Items Summary

**Total prompts DONE:** 18 / 34 (52.9%)

| Prompt | Title |
|--------|-------|
| CRM-001 | Reconcile baseline against main |
| CRM-003 | Author the G0 stage report |
| CRM-004 | Lock the Command Center route |
| CRM-005 | Lock the Execution Board data registry |
| CRM-006 | Establish governance drift check |
| CRM-007 | Apply unified CRM core migration |
| CRM-009 | Reconcile ADMIN role and capabilities |
| CRM-010 | Complete imports and custom-field persistence |
| CRM-011 | Document production Flyway operations |
| CRM-012 | Author the G1 stage report |
| CRM-013 | Lock i18n provider and brand tokens |
| CRM-014 | Wire leads tab |
| CRM-015 | Wire customers tab |
| CRM-016 | Wire contacts tab |
| CRM-017 | Wire customer-360 view |
| CRM-018 | Add row-level security |
| CRM-019 | Wire opportunities tab |
| CRM-020 | Wire pipeline Kanban board |

---

*Inventory compiled 2026-07-30 by Release Baseline Authority*
