# CRM Release Notes — v2.0.0

**Release Date:** 2026-07-30
**Repository:** snadaiapp-png/SNAD
**Tag:** `crm-v2.0.0`

---

## 🎯 Release Highlights

This release delivers the **complete CRM Command Center** with six fully-wired
tabs, customer-360 intelligence, a drag-and-drop Kanban pipeline board, and
**PostgreSQL Row-Level Security** as a defense-in-depth tenant isolation layer.

Three milestones are closed: **G2** (i18n), **G3** (core CRM entities), and
**G4** (opportunities, pipeline, Kanban).

---

## ✨ New Features

### Frontend — CRM Command Center

| Tab | Feature | CRM ID |
|-----|---------|--------|
| **Leads** | Lead management with search, status filter, create, convert-to-opportunity | CRM-014 |
| **Customers** | Account list with lifecycle filter, create, archive/restore, 360 navigation | CRM-015 |
| **Contacts** | Contact list with consent tracking, create, archive/restore | CRM-016 |
| **Customer 360** | Unified view: account, contacts, opportunities, activities, timeline | CRM-017 |
| **Opportunities** | List with create, status/pipeline filter, stage transitions | CRM-019 |
| **Pipeline** | Kanban board with drag-and-drop, value totals, win probability | CRM-020 |

### Backend — Security

- **Row-Level Security (CRM-018):** PostgreSQL native RLS on all 62 CRM tables
  providing database-level enforcement of multi-tenant data isolation. This is
  a defense-in-depth layer on top of the existing application-level filtering.

### Backend — Intelligence

- **Customer 360 (CRM-010):** Unified customer intelligence with scoring models,
  customer scores, segments, and next-best-actions.

---

## 🔒 Security Improvements

- **5-layer defense-in-depth** tenant isolation:
  1. JWT authentication (tenant_id claim)
  2. RBAC authorization (@RequireCapability, deny-by-default)
  3. Application filtering (WHERE tenant_id = :t in 351+ queries)
  4. Composite foreign keys (cross-reference prevention)
  5. **PostgreSQL RLS** (NEW — database-enforced row filtering)
- Cross-tenant SELECT, INSERT, UPDATE, and DELETE are now denied at the
  database level when tenant context is active
- No breaking changes — RLS uses a permissive-when-unset policy

---

## 🌐 Internationalization

- Full bilingual support (Arabic + English) across all new tabs
- 170+ new translation keys
- RTL/LTR aware layout
- Localized dates, currencies, and status labels

---

## 🗄️ Database Migrations

| Version | Description | Reversible |
|---------|-------------|------------|
| `V20260730_1` | Enable RLS on all CRM tables | ✅ |
| `V20260730_2` | Disable RLS (rollback) | ✅ |

Migrations are PostgreSQL-specific (in `db/vendor/postgresql/`). H2 test
environments use no-op mirror migrations for version parity.

---

## 📊 Portfolio Progress

| Metric | Value |
|--------|-------|
| Total prompts | 34 |
| Completed | 18 (52.9%) |
| Closed milestones | 4 (G0*, G2, G3, G4) |
| Active milestones | 2 (G0, G1) |

---

## 📦 Upgrade Instructions

### Backend

1. Deploy the latest `main` branch
2. Flyway will automatically apply `V20260730_1` (RLS enable) on startup
3. No configuration changes required — RLS is enabled by default

### Frontend

1. Deploy via Vercel (production deployment from `main`)
2. No environment variable changes required

### Rollback (if needed)

- **Soft rollback:** Set `SNAD_RLS_ENABLED=false` and restart
- **Full rollback:** Apply `V20260730_2` migration

See `docs/crm/crm-018/CRM-018-ROLLBACK-GUIDE.md` for details.

---

## 🧪 Testing

| Suite | Result |
|-------|--------|
| Backend compilation | ✅ 0 errors |
| Frontend TypeScript | ✅ 0 errors |
| RLS unit tests | ✅ 6/6 pass |
| CRM tenant isolation contract | ✅ 5/5 pass |
| RLS integration tests | 9 scenarios (CI/Docker) |

---

## 📄 Documentation

Complete documentation available in:
- `docs/crm/release/` — Release audit, notes, build report
- `docs/crm/crm-018/` — Security assessment, RLS design, 5 reports
- `docs/crm/crm-019/` — Opportunities implementation
- `docs/crm/crm-020/` — Pipeline Kanban implementation
- `docs/crm/stage-reports/` — G3 and G4 closure packages

---

## ⚠️ Known Issues

1. **Pre-existing Flyway version collision** (`V20260722.1` exists in both
   `db/migration/` and `db/vendor/h2/`). This affects `@SpringBootTest`
   full-context tests locally but does not affect production deployments.
   Predates this release.

2. **RLS integration tests require Docker.** The Testcontainers-based tests
   (`CrmRlsTenantIsolationPostgresTest`) are designed for CI environments
   with Docker available.

---

## 🚀 What's Next

With G4 closed, the next milestone is **G5** (Tasks, transfers, employees,
and assignments). The first work item is **CRM-021** (Wire tasks tab),
which depends on CRM-008 (code already on main).
