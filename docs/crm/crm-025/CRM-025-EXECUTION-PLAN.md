# CRM-025 EXECUTION PLAN

> **Document type:** Authorization gate and implementation plan
> **Created:** 2026-07-31
> **Status:** AUTHORIZED TO START

---

## 1. Executive Summary

CRM-025 (Wire Reports Tab) has been verified against all governance
prerequisites. All dependencies are satisfied, backend APIs exist, and the
frontend architecture is ready. Authorization is granted.

---

## 2. Acceptance Criteria

| # | Criterion | Current Status |
|---|-----------|----------------|
| 1 | Reports tab renders at least three reports: pipeline velocity, lead conversion rate, and activity throughput | ❌ NOT IMPLEMENTED |
| 2 | Reports are backed by aggregation queries on existing CRM tables | ✅ Backend ready |
| 3 | Date-range filter is wired | ❌ NOT IMPLEMENTED |

---

## 3. Prerequisites Verification

### 3.1 Dependency Graph

| Dependency | Status | Evidence |
|------------|--------|----------|
| EXEC-PROMPT-CRM-019 (Wire opportunities) | ✅ DONE | Roadmap line 376: "Status: DONE" |
| EXEC-PROMPT-CRM-021 (Wire tasks tab) | ✅ DONE | Verified in CRM-021 certification |
| CRM-G3 (Core entities) | ✅ DONE | Roadmap: "Status: DONE" |
| CRM-G4 (Opportunities, pipeline) | ✅ DONE | Roadmap: "Status: DONE" |

### 3.2 Backend Readiness

| Component | Status | Evidence |
|-----------|--------|----------|
| ReportsController | ✅ EXISTS | `/api/v1/crm/reports` with 5 endpoints |
| ReportsUseCases | ✅ EXISTS | `apps/sanad-platform/.../reports/application/ReportsUseCases.java` |
| ReportsRepository | ✅ EXISTS | Domain port with 4 report types |
| SalesPipelineReport | ✅ EXISTS | Stages, total value, weighted value |
| LeadConversionReport | ✅ EXISTS | Total, converted, qualified, disqualified |
| ActivitySummaryReport | ✅ EXISTS | By type breakdown with counts |
| AccountGrowthReport | ✅ EXISTS | New, restored, archived counts |

### 3.3 Backend API Endpoints

| Endpoint | Method | Report Type | Status |
|----------|--------|-------------|--------|
| `/api/v1/crm/reports/sales-pipeline` | GET | Pipeline velocity | ✅ EXISTS |
| `/api/v1/crm/reports/lead-conversion` | GET | Lead conversion rate | ✅ EXISTS |
| `/api/v1/crm/reports/activity-summary` | GET | Activity throughput | ✅ EXISTS |
| `/api/v1/crm/reports/account-growth` | GET | Account growth | ✅ EXISTS |
| `/api/v1/crm/reports/dashboard` | GET | Combined dashboard | ✅ EXISTS |

### 3.4 Frontend Readiness

| Component | Status | Evidence |
|-----------|--------|----------|
| crmApi.reports() | ✅ EXISTS | `apps/web/lib/api/crm.ts` line 380 |
| ReportsIcon | ✅ EXISTS | `apps/web/app/crm/crm-command-center.tsx` line 154 |
| Tab definition | ✅ EXISTS | `{ id: "reports", labelKey: "tab.reports", Icon: ReportsIcon }` |
| ReportsTab component | ❌ MISSING | No file found in `apps/web/app/crm/components/` |
| Case "reports" in switch | ❌ MISSING | Not in command center switch statement |
| i18n keys | ❌ MISSING | No reports-specific keys |

### 3.5 CI Baseline

| Check | Status | Evidence |
|-------|--------|----------|
| TypeScript | ✅ PASS | 0 errors on main |
| ESLint | ✅ PASS | 0 errors on main |
| Tests | ✅ PASS | 434 tests passing |
| Production | ✅ READY | `https://sanad-platform-kappa.vercel.app` |

### 3.6 Production Baseline

| Field | Value |
|-------|-------|
| SHA | `5eafed80` |
| URL | `https://sanad-platform-kappa.vercel.app` |
| Status | Ready |

---

## 4. Gap Analysis

### 4.1 Implementation Gaps

| Gap | Severity | Classification |
|-----|----------|----------------|
| ReportsTab component not created | Critical | Missing implementation |
| Case "reports" not wired in Command Center | Critical | Missing integration |
| Date-range filter not implemented | Major | Missing feature |
| i18n keys for reports not added | Minor | Missing localization |
| crmApi type for reports response not defined | Minor | Missing TypeScript model |

### 4.2 No Blocking Issues

| Category | Status |
|----------|--------|
| Open blocking issues | None |
| Governance blockers | None |
| CI failures | None |
| Production incidents | None |

---

## 5. Architecture Review

### 5.1 Implementation Strategy

1. **TypeScript Models:** Add `CrmSalesPipelineReport`, `CrmLeadConversionReport`, `CrmActivitySummaryReport` interfaces to `crm.ts`
2. **API Methods:** Add `crmApi.salesPipeline()`, `crmApi.leadConversion()`, `crmApi.activitySummary()` methods
3. **ReportsTab Component:** Create `apps/web/app/crm/components/reports-tab.tsx` with:
   - Pipeline Velocity report (table with stages, counts, amounts)
   - Lead Conversion Rate report (funnel visualization)
   - Activity Throughput report (breakdown by type)
   - Date-range filter (start/end date inputs)
4. **Command Center:** Add `import { ReportsTab }` and `case "reports": return <ReportsTab />`
5. **i18n:** Add keys for reports tab title, report names, filter labels, column headers

### 5.2 Architecture Compliance

| Constraint | Status |
|------------|--------|
| Existing architecture preserved | ✅ |
| API contracts preserved | ✅ |
| Multi-tenancy preserved | ✅ (backend handles tenant scoping) |
| Security model preserved | ✅ (CRM.ACCOUNT.READ capability) |
| CRM-021/022/023 behavior preserved | ✅ |

### 5.3 Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Backend aggregation queries slow | Low | Medium | Use existing indexed queries |
| Date-range filter complexity | Low | Low | Simple ISO date inputs |
| i18n completeness | Low | Low | Follow existing patterns |
| CSS class availability | Low | Low | Use same patterns as tasks-tab |

---

## 6. Validation Plan

| Check | Tool | Expected |
|-------|------|----------|
| TypeScript | `tsc --noEmit` | 0 errors |
| ESLint | `eslint` | 0 errors |
| Tests | `vitest run` | 434+ tests pass |
| Build | `next build` | Success (network issues excepted) |

---

## 7. Acceptance Criteria Mapping

| Criterion | Implementation | Validation |
|-----------|----------------|------------|
| Pipeline velocity report | `crmApi.salesPipeline()` → ReportsTab | Visual inspection |
| Lead conversion rate report | `crmApi.leadConversion()` → ReportsTab | Visual inspection |
| Activity throughput report | `crmApi.activitySummary()` → ReportsTab | Visual inspection |
| Aggregation queries on existing tables | Backend ReportsRepository | Existing tests pass |
| Date-range filter | ReportsTab date inputs → API query params | Visual inspection |

---

## 8. Implementation Estimate

| Task | Effort |
|------|--------|
| TypeScript models | Small |
| API methods | Small |
| ReportsTab component | Medium |
| Command Center wiring | Small |
| i18n keys | Small |
| **Total** | **Medium** |

---

## 9. Authorization

```
✅ CRM-025 AUTHORIZED TO START
```

**All prerequisites verified. No blockers identified.**

| Prerequisite | Status |
|--------------|--------|
| CRM-019 (opportunities) | ✅ DONE |
| CRM-021 (tasks) | ✅ DONE |
| Backend APIs | ✅ READY |
| Frontend architecture | ✅ READY |
| CI baseline | ✅ PASSING |
| Production baseline | ✅ READY |
| Open blockers | ✅ NONE |

**Authorization granted:** 2026-07-31
**Next step:** Create `feature/crm-025-reports-tab` branch and implement
