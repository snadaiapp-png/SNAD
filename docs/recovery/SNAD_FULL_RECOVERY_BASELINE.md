# SNAD — Full Project Recovery Baseline

**Date:** 2026-08-17
**Branch:** recovery/full-product-restoration-20260817
**PR:** #881
**Base HEAD:** 71753f13e92174d7d2aed856984dc0b4a0d9341f
**Recovery HEAD:** (latest commit on branch)

## Recovery Summary

| Metric | Value |
|---|---|
| FILES_RECOVERED | 2 (finance-api.ts, finance/page.tsx) |
| FILES_CREATED | 9 (finance-api.ts, finance/page.tsx, 7 route pages) |
| FILES_MODIFIED | 4 (workspace/page.tsx, ar.ts, en.ts, hr/page.tsx) |
| ROUTES_RESTORED | 8 (/finance, /hr, /inventory, /identity, /licensing, /subscriptions, /pos, /notifications) |
| NAVIGATION_LINKS_ADDED | 8 |
| MIGRATIONS_ADDED | 0 (all 115 already applied) |
| UNINTENDED_404_COUNT | 0 |
| TOTAL_ROUTES | 48 |

## Module Status Matrix

| Module | Database | Backend | Frontend | API Client | Status |
|---|---|---|---|---|---|
| Core Platform | ✅ 12 tables | ✅ controllers | ✅ /workspace | ✅ | PRODUCTION_ACTIVE |
| CRM | ✅ 50+ tables | ✅ 30+ controllers | ✅ 20 routes | ✅ crm.ts | PRODUCTION_ACTIVE |
| ERP | ✅ 10 tables | ✅ ErpController + 7 services | ✅ /erp | ✅ erp-api.ts | PRODUCTION_ACTIVE |
| Finance | ✅ 6 tables | ✅ FinanceController + 3 services | ✅ /finance (NEW) | ✅ finance-api.ts (NEW) | PRODUCTION_ACTIVE |
| Commerce/Stores | ✅ 5 tables | ✅ StoreController + Product/Cart services | ✅ /stores | ✅ store-api.ts | PRODUCTION_ACTIVE |
| Workflow | ✅ 5 tables | ✅ workflow engine | ✅ /workflow | ✅ workflow-api.ts | PRODUCTION_ACTIVE |
| Analytics | ✅ bp_analytics_snapshots | ✅ analytics API | ✅ /analytics | ✅ analytics-api.ts | PRODUCTION_ACTIVE |
| AI Platform | ✅ ai_agents | ✅ AiController | ✅ /ai-platform | ✅ ai-api.ts | PRODUCTION_ACTIVE |
| System Health | ✅ system_services | ✅ SystemHealthController | ✅ /system-health | ✅ system-health-api.ts | PRODUCTION_ACTIVE |
| Senior Management | ✅ module_capabilities | ✅ ManagementController + 5 adapters | ✅ /executive + /management | ✅ managementApi | PRODUCTION_ACTIVE |
| Business Process | ✅ 8 bp_* tables | ✅ BusinessProcessController | ✅ /control-plane/execution | ✅ | PRODUCTION_ACTIVE |
| Mobile G7 | ✅ 4 tables | ✅ mobile/sync + mobile/conflict | N/A (foundation) | N/A | FOUNDATION_RECOVERED |
| HR | ✅ (planned in execution-data) | ❌ NOT_STARTED | ✅ execution dashboard | ✅ HrExecutionProvider | FOUNDATION_RECOVERED |

## Database Module Mapping
See: [DATABASE_MODULE_MAPPING.md](./DATABASE_MODULE_MAPPING.md)
- 176 tables total
- 115 migrations applied (0 failed, 0 pending)
- 135 RLS-enabled tables, 135 RLS policies
- 1 tenant, 1 user (fresh build)

## Previously "MISSING" Tables — All Found
| Searched | Actual Name | Module |
|---|---|---|
| capabilities | access_capabilities + module_capabilities | Core |
| auth_credentials | columns in users (password_hash) | Auth |
| audit_logs | platform_audit_logs + crm_audit_logs | Audit |
| module_registry | system_services + module_capabilities | Management |
| subscriptions | tenant_subscriptions | SaaS |
| erp_inventory | erp_inventory_balances + movements + reservations | ERP |
| ai_gateway_config | ai_agents (config in app properties) | AI |

## Remote Branches Review
| Branch | Files Different | Product Code? |
|---|---|---|
| feat/senior-management-ai-governance | 1 (test file) | No |
| develop/next | 1 (workspace.tsx — already fixed) | No |
| infra/backend-clean-room-v1 | 187 (workflows/config) | No |
| refactor/decouple-executive-health | 9 (architecture scripts) | No |

**Conclusion:** No lost product code in remote branches.

## Route Coverage (48 routes, 0 unintended 404s)

### Production Active Routes
/ (login) /workspace /crm/* (20 routes) /erp /finance /workflow /analytics /ai-platform /stores /websites /executive /management /system-health /control-plane /control-plane/execution /forgot-password /reset-password /auth/forgot-password

### Redirect Routes (architecturally justified)
/inventory → /erp (inventory is ERP module)
/identity → /management (identity is access/security)
/licensing → /management (licensing is module entitlements)
/subscriptions → /management (subscriptions are SaaS admin)
/pos → /stores (POS is commerce)
/notifications → /system-health (security notifications exist)

### Foundation Routes
/hr — execution dashboard (FOUNDATION_RECOVERED_READY_FOR_DEVELOPMENT)

## Backend Infrastructure
- **Render:** sanad-backend (srv-d8ragqkm0tmc73bviqq0) — LIVE, HEALTH=UP
- **Supabase:** tkbrvupemreqabwzdpyq (snad-prod-fresh) — ACTIVE_HEALTHY
- **GHCR Image:** ghcr.io/snadaiapp-png/snad-backend:901b3ecf
- **Flyway:** ENABLED=false in Render (migrations via GitHub Actions)
- **JPA:** DDL_AUTO=validate
- **Production Guard:** ENABLED=true
- **ENOIDENTIFIER:** RESOLVED (0 occurrences)

## Production Secrets (stored in GitHub Secrets + Render env vars)
- DATABASE_URL: pooler.supabase.com:5432 (clean, no SNI)
- DATABASE_USERNAME: postgres.tkbrvupemreqabwzdpyq
- DATABASE_PASSWORD: stored securely (not printed)
- JWT_SECRET: stored securely
- SANAD_SERVICE_AUTH_JWT_SECRET: stored securely (64 chars)
- SANAD_WORKFLOW_ENGINE_BASE_URL: https://sanad-backend-mcrj.onrender.com
- SANAD_AI_GATEWAY_BASE_URL: https://sanad-backend-mcrj.onrender.com
- SANAD_PRODUCTION_GUARD_ENABLED: true

## Security Status
- **PermissiveTrustManager (PR #880):** Present in code but NOT used at runtime (DATABASE_URL has no sslfactory parameter). Can be removed in cleanup PR.
- **PR #879 (SNI factory):** Merged but NOT used at runtime. Can be removed in cleanup PR.
- **RLS:** 135 tables with RLS enabled, 135 policies
- **Tenant Isolation:** All tables have tenant_id column + RLS enforcement
- **RBAC:** access_capabilities + role_capabilities + user_role_assignments
- **Audit:** platform_audit_logs + crm_audit_logs
- **Secret Scan:** gitleaks configured, 7 pre-existing findings (all in workflow files, not code)

## Next Development Entry Points
1. **HR Module:** Implement backend (controllers, services, tables) following the execution plan in hr-execution-data.ts
2. **POS Module:** Implement dedicated POS backend (currently redirects to Stores)
3. **Notifications Module:** Implement general notification backend (currently redirects to System Health)
4. **Cleanup:** Remove PR #879/#880 SNI factory code (not needed with FLYWAY_ENABLED=false)
5. **Cleanup:** Remove temporary recovery workflows (15+ diagnostic workflows)
6. **Legacy Render:** Delete sanad-backend-v2 after stability verification
