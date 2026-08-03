# G1-G2 SCOPE MATRIX

**Audit Date:** 2026-08-03
**Repository:** snadaiapp-png/SNAD
**HEAD SHA:** `1356b902e11da10384cad00e537369c672ee6752`
**HEAD Commit:** 2026-08-02 19:16:02 +0300

---

## G1: Database and Multi-Tenant Foundation

### Acceptance Criteria

| # | Criterion | Required | Verified |
|---|-----------|----------|----------|
| 1 | 8 extension tables with `tenant_id UUID NOT NULL` | 8 | ✅ 8/8 |
| 2 | 26 explicit performance indexes (all tenant_id as leading column) | 26 | ✅ 26/26 |
| 3 | 8 tenant-root foreign keys referencing `tenants(id)` | 8 | ✅ 8/8 |
| 4 | 2 same-tenant composite foreign keys (`crm_phone_numbers` → `crm_contacts`, `crm_contact_lookup_index` → `crm_contacts`) | 2 | ✅ 2/2 |
| 5 | Testcontainers integration tests with `postgres:16-alpine` | 4 files | ✅ 4/4 |
| 6 | G1 Schema Isolation CI gate passes | required check | ✅ PASS |
| 7 | Cross-tenant isolation test passes | 1 test | ✅ PASS |
| 8 | No critical/high defects in G1 components | 0 | ✅ 0 |

### Required Components

| Component | Type | File/Path |
|-----------|------|-----------|
| `crm_tasks` | Table | `V20260716_1__create_crm_tasks.sql` |
| `crm_notes` | Table | `V20260716_2__create_crm_notes.sql` |
| `crm_assignments` | Table | `V20260717_6__create_crm_g1_extension_tables.sql` |
| `crm_transfers` | Table | `V20260717_6__create_crm_g1_extension_tables.sql` |
| `crm_audit_logs` | Table | `V20260717_6__create_crm_g1_extension_tables.sql` |
| `crm_reports` | Table | `V20260717_6__create_crm_g1_extension_tables.sql` |
| `crm_phone_numbers` | Table | `V20260717_6__create_crm_g1_extension_tables.sql` |
| `crm_contact_lookup_index` | Table | `V20260717_6__create_crm_g1_extension_tables.sql` |
| `Assignment.java` | Domain | `crm/ownership/domain/Assignment.java` |
| `AssignmentRecordType.java` | Domain | `crm/ownership/domain/AssignmentRecordType.java` |
| `AssignmentStatus.java` | Domain | `crm/ownership/domain/AssignmentStatus.java` |
| `OwnerType.java` | Domain | `crm/ownership/domain/OwnerType.java` |
| 8 Ownership Controllers | API | `crm/ownership/web/*.java` |
| G1 Schema Isolation CI | Workflow | `crm-g1-schema-isolation.yml` |
| G1 Deployment Readiness CI | Workflow | `crm-deployment-readiness.yml` |
| G1 Production Closure CI | Workflow | `crm-g1-production-closure.yml` |

### Required Tests

| Test File | Methods | Testcontainers |
|-----------|---------|----------------|
| `CrmG1TenantIsolationPostgresTest.java` | 1 | postgres:16-alpine |
| `Crm008bFoundationAcceptanceTest.java` | 11 | postgres:16-alpine |
| `CrmFlywayHistoryAssertionTest.java` | 5 | postgres:16-alpine |
| `CrmPostgresMigrationTest.java` | 4 | postgres:16-alpine |
| **Total** | **21** | **All** |

---

## G2: i18n, RTL/LTR, and Accessibility Hardening

### Acceptance Criteria

| # | Criterion | Required | Verified |
|---|-----------|----------|----------|
| 1 | `CrmI18nProvider` React context component exists | 1 | ✅ |
| 2 | `useCrmI18n` hook exports `{ lang, dir, toggleLang, setLang, t }` | 1 | ✅ |
| 3 | Arabic/English bilingual dictionary (130+ keys) | 130+ | ✅ 304 keys |
| 4 | RTL/LTR direction switching based on language | 1 | ✅ |
| 5 | Brand tokens (`--snad-brand-primary`, `--snad-brand-gold`) | 2+ | ✅ |
| 6 | `snad-tokens.css` delegates to `theme.css` | 1 | ✅ |
| 7 | Frontend tests cover i18n context | 4 tests | ✅ 4/4 |
| 8 | No critical/high defects in G2 components | 0 | ✅ 0 |

### Required Components

| Component | Type | File/Path |
|-----------|------|-----------|
| `CrmI18nProvider` | React Context | `apps/web/app/crm/crm-i18n.tsx` |
| `useCrmI18n` | Hook | `apps/web/app/crm/crm-i18n.tsx` |
| Arabic/English dictionary | i18n | `apps/web/app/crm/crm-i18n.tsx` |
| RTL/LTR switching | i18n | `apps/web/app/crm/crm-i18n.tsx` |
| `snad-tokens.css` | Brand Tokens | `apps/web/app/snad-tokens.css` |
| `theme.css` | Canonical Tokens | `apps/web/design-system/tokens/theme.css` |
| `crm-execution-data.ts` | Execution Groups | `apps/web/app/crm/crm-execution-data.ts` |
| `crm-interactions.test.tsx` | Tests | `apps/web/app/crm/crm-interactions.test.tsx` |

### Required Tests

| Test File | Methods | Coverage |
|-----------|---------|----------|
| `crm-interactions.test.tsx` | 4 | Pipeline accessibility, RTL, virtualization |
| **Total** | **4** | **i18n-wrapped** |

---

## Cross-Cutting Requirements

| Requirement | G1 | G2 |
|-------------|----|----|
| No hardcoded secrets | ✅ | ✅ |
| JWT auth required | ✅ | ✅ |
| CORS restricted | ✅ | ✅ |
| Security headers present | ✅ | ✅ |
| Production deployment green | ✅ | ✅ |
| CI required checks pass | ✅ | ✅ |
| Branch protection enabled | ✅ | ✅ |

---

## Scope Boundary

**IN SCOPE:**
- 8 G1 extension tables, 26 indexes, 8 tenant FKs, 2 same-tenant FKs
- 4 G1 domain classes, 8 ownership controllers
- 4 G1 Testcontainers test files (21 test methods)
- G2 CrmI18nProvider + useCrmI18n with 304 bilingual keys
- G2 RTL/LTR switching
- G2 brand tokens (snad-tokens.css + theme.css)
- G2 frontend tests (4 methods)
- CI/CD: 7 required status checks, branch protection
- Production: backend health, frontend deployment

**OUT OF SCOPE:**
- G0-G10 components not listed above
- Performance optimization
- New feature implementation
- Documentation generation
