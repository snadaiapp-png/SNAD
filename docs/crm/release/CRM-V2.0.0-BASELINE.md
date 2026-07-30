# CRM v2.0.0 Production Baseline

| Field | Value |
|-------|-------|
| Baseline Date | 2026-07-30 |
| Release Version | **crm-v2.0.0** |
| Release SHA | `8c2950bcbca922c8fa34b314696234fdf7bf79cb` |
| Release Tag | `crm-v2.0.0` |
| Production URL | https://snad-app.vercel.app |
| Repository | snadaiapp-png/SNAD |
| Baseline Authority | Release Baseline Authority |

---

## 1. Release Identity

| Property | Value |
|----------|-------|
| Semantic Version | 2.0.0 |
| Tag | `crm-v2.0.0` |
| HEAD commit | `8c2950bc` |
| Previous baseline | `crm-v1.0.0` (2026-07-28) |
| GitHub Release | https://github.com/snadaiapp-png/SNAD/releases/tag/crm-v2.0.0 |
| Production URL | https://snad-app.vercel.app |
| Vercel Project | snad-team/snad-app |

---

## 2. Build Versions

| Component | Version | Notes |
|-----------|---------|-------|
| Frontend (Next.js) | 16.2.11 | Build: SUCCESS, 0 TS errors |
| Backend (Spring Boot) | 3.5.6 | Maven: 920/920 non-Docker tests pass |
| Java | 17 | Target compatibility |
| Node.js | 24.x | Vercel runtime |
| PostgreSQL | Supabase managed | Production database |

---

## 3. Database Migrations

| Migration | Description | Vendor | Reversible |
|-----------|-------------|--------|------------|
| `V20260730_1` | Enable RLS on all 62 CRM tables | PostgreSQL | ✅ `V20260730_2` |
| `V20260730_2` | Disable RLS on all CRM tables | PostgreSQL | ✅ Self |
| `V20260729_1` | Create CRM customer intelligence | PostgreSQL | ✅ |
| `V20260729_2` | Seed default scoring models | PostgreSQL | ✅ |
| `V20260728_1` | Seed CRM-008B team management caps | PostgreSQL | ✅ |

---

## 4. CRM Modules Included

### 4.1 Frontend Tabs (Command Center)

| Module | Route | Description | CRM ID |
|--------|-------|-------------|--------|
| Command Center | `/crm/command-center` | 16-tab CRM shell | CRM-005 |
| Leads | `/crm/leads` | Lead management, search, filter, create, convert | CRM-014 |
| Customers (Accounts) | `/crm/accounts` | Account list, lifecycle filter, create, archive/restore | CRM-015 |
| Contacts | `/crm/contacts` | Contact list, consent tracking, create, archive/restore | CRM-016 |
| Customer 360 | `/crm/accounts/[accountId]` | Unified customer intelligence view | CRM-017 |
| Opportunities | `/crm/opportunities` | Opportunity list, stage transitions, win/loss capture | CRM-019 |
| Pipeline Kanban | `/crm/pipelines` | Drag-and-drop Kanban, value totals, win probability | CRM-020 |
| Overview | `/crm/overview` | Dashboard and summary | CRM-003 |
| Activities | `/crm/activities` | Activity timeline | — |
| Tasks | `/crm/tasks` | Task list (shell) | CRM-021 (pending) |
| Notes | `/crm/notes` | Note management | — |
| Tags | `/crm/tags` | Tag management | — |
| Reports | `/crm/reports` | Reports (shell) | CRM-025 (pending) |
| Search | `/crm/search` | Global search | — |
| Settings | `/crm/settings/custom-fields` | Custom field configuration | — |
| Imports | `/crm/imports` | CSV/XLSX import | — |
| Integrations | `/crm/integrations` | External integrations | — |

### 4.2 Backend Packages

| Package | Responsibility |
|---------|----------------|
| `crm/activity` | Activity tracking and timeline |
| `crm/concurrency` | ETag-based optimistic concurrency |
| `crm/configuration` | CRM configuration and feature toggles |
| `crm/dto` | Data transfer objects |
| `crm/error` | CRM-specific error handling |
| `crm/export` | Data export functionality |
| `crm/idempotency` | Idempotency key support |
| `crm/integration` | External system integration |
| `crm/intelligence` | Customer 360 scoring and intelligence |
| `crm/lead` | Lead management domain |
| `crm/legacy` | Legacy data migration support |
| `crm/mapper` | Entity-DTO mapping |
| `crm/note` | Note management domain |
| `crm/opportunity` | Opportunity and pipeline domain |
| `crm/ownership` | Ownership, teams, queues, territories |
| `crm/pagination` | Cursor and keyset pagination |
| `crm/party` | Accounts, contacts, addresses, communication |
| `crm/query` | Custom query support |
| `crm/reports` | Report generation |
| `crm/search` | Global search |
| `crm/tag` | Tag management |
| `crm/task` | Task management domain |
| `crm/web` | Web controllers and API endpoints |

### 4.3 Security Infrastructure

| Component | Description |
|-----------|-------------|
| `security/rls/TenantRlsDataSource` | DataSource decorator wrapping connections |
| `security/rls/TenantRlsConnectionHandler` | JDBC proxy applying `SET LOCAL app.tenant_id` |
| `security/rls/TenantRlsDataSourcePostProcessor` | Auto-wiring BeanPostProcessor |

---

## 5. Closed Milestones

| Milestone | Title | Prompts | Status |
|-----------|-------|---------|--------|
| G0 | Execution control and shell | 001–006 | DONE |
| G2 | i18n, RTL/LTR, accessibility | 013 | ✅ CLOSED |
| G3 | Core CRM entities end-to-end | 014–017 | ✅ CLOSED |
| G4 | Opportunities, pipeline, Kanban | 018–020 | ✅ CLOSED |

---

## 6. Portfolio Status

| Metric | Value |
|--------|-------|
| Total prompts | 34 |
| DONE | 18 (52.9%) |
| IN_PROGRESS | 1 (2.9%) |
| NOT_STARTED | 15 (44.1%) |
| Closed milestones | 4 / 9 |

---

## 7. Test Summary

| Suite | Run | Pass | Fail | Error | Skip | Result |
|-------|-----|------|------|-------|------|--------|
| Backend (non-Docker) | 920 | 920 | 0 | 0 | 12 | ✅ |
| Backend (Docker/Testcontainers) | 25 | 0 | 0 | 25 | 0 | ⚠️ Requires Docker |
| Frontend TypeScript | — | 0 errors | — | — | — | ✅ |
| Frontend Lint | — | 6 known | — | — | — | ⚠️ Pre-existing |
| Frontend Build | — | SUCCESS | — | — | — | ✅ |

---

## 8. CI/CD Summary

| Pipeline | Status |
|----------|--------|
| ci.yml | ✅ Configured |
| web-ci.yml | ✅ Configured |
| playwright-ci.yml | ✅ Configured |
| Production Release | ✅ `crm-v2.0.0` deployed |
| Vercel Production | ✅ https://snad-app.vercel.app |

---

## 9. Release Artifacts

| Artifact | Location |
|----------|----------|
| Release Audit | `docs/crm/release/CRM-RELEASE-AUDIT.md` |
| Release Notes | `docs/crm/release/CRM-RELEASE-NOTES.md` |
| Changelog | `docs/crm/release/CRM-CHANGELOG.md` |
| Build Report | `docs/crm/release/CRM-BUILD-REPORT.md` |
| Release Certificate | `docs/crm/release/CRM-RELEASE-CERTIFICATE.md` |
| Deployment Report | `docs/crm/release/CRM-DEPLOYMENT-REPORT.md` |
| Production Verification | `docs/crm/release/CRM-PRODUCTION-VERIFICATION.md` |
| Rollback Status | `docs/crm/release/CRM-ROLLBACK-STATUS.md` |
| Baseline | `docs/crm/release/CRM-V2.0.0-BASELINE.md` |

---

*Baseline established 2026-07-30 by Release Baseline Authority*
