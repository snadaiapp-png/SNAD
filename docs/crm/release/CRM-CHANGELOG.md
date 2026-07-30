# CRM Changelog — Release v2.0.0

**Release Date:** 2026-07-30
**Previous Release:** v1.0.0 (2026-07-28)

---

## [2.0.0] - 2026-07-30

### Overview

CRM Release 2.0.0 delivers the complete CRM Command Center with 6 wired tabs,
customer-360 intelligence, a Kanban pipeline board, and database-level
row-level security. This release closes milestones G2 (i18n), G3 (core CRM
entities), and G4 (opportunities, pipeline, Kanban).

### Added — Frontend (CRM Command Center)

| Feature | CRM ID | Description |
|---------|--------|-------------|
| **Leads tab** | CRM-014 | Full leads management with list, search, status filter, create form, and lead-to-opportunity conversion dialog |
| **Customers tab** | CRM-015 | Customer accounts list with search, lifecycle status filter, create, archive/restore, and customer-360 navigation |
| **Contacts tab** | CRM-016 | Contacts list with search, lifecycle filter, create form with consent summary, and archive/restore |
| **Customer-360 view** | CRM-017 | Unified customer detail with account summary, contacts, opportunities, activities, and reverse-chronological timeline |
| **Opportunities tab** | CRM-019 | Opportunities list with create, filter by status/pipeline, and stage transition dialog with win/loss reason capture |
| **Pipeline Kanban board** | CRM-020 | Drag-and-drop Kanban with stage columns, opportunity cards, optimistic updates with rollback, value totals, win probability display, search, status filter, and keyboard navigation |

### Added — Backend (Security)

- **PostgreSQL Row-Level Security** (CRM-018): Defense-in-depth tenant isolation
  via native RLS policies on all 62 CRM tables
  - Permissive-when-unset policy (zero breakage, backward compatible)
  - `SET LOCAL app.tenant_id` context propagation via connection proxy
  - `TenantRlsDataSource` transparently wraps HikariCP connection pool
  - `@ConditionalOnProperty` feature toggle (`snad.rls.enabled`)
- **2 Flyway migrations**: RLS enable (`V20260730_1`) + rollback (`V20260730_2`)
- **6 unit tests** for connection handler logic
- **9 integration test scenarios** for RLS tenant isolation (Testcontainers)

### Added — Backend (Intelligence)

- **Customer 360 & Unified Customer Intelligence** (CRM-010): Scoring models,
  customer scores, segments, segment memberships, next-best-actions

### Added — Internationalization (CRM-013)

- 170+ new i18n keys across leads, customers, contacts, customer-360,
  opportunities, and pipeline modules (Arabic + English)
- Full RTL/LTR support

### Added — Documentation

- 30+ implementation/API-mapping/test/architecture reports across CRM-014–020
- G3 closure package (closure report, certificate, audit summary, lessons learned)
- G4 closure package (closure report, certificate, audit summary, security certificate)
- CRM-018 security assessment and RLS design documents
- Migration guide and rollback guide for RLS

### Closed Milestones

| Milestone | Title | Status |
|-----------|-------|--------|
| **CRM-G2** | i18n, RTL/LTR, accessibility | ✅ DONE |
| **CRM-G3** | Core CRM entities end-to-end | ✅ CLOSED |
| **CRM-G4** | Opportunities, pipeline, Kanban | ✅ CLOSED |

### Statistics

| Metric | Value |
|--------|-------|
| CRM work items released | 11 (CRM-010 through CRM-020) |
| Milestones closed | 3 (G2, G3, G4) |
| New frontend components | 6 tab components + customer-360 view |
| New backend classes | 3 (RLS infrastructure) |
| New SQL migrations | 2 (RLS enable/disable) + 2 H2 mirrors |
| New i18n keys | 170+ |
| New documentation files | 40+ |
| Portfolio completion | 52.9% (18/34 prompts) |

---

*This changelog is part of the CRM v2.0.0 release package.*
