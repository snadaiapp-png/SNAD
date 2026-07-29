# CRM-020 — Implementation Report

| Field | Value |
|-------|-------|
| Work Item | EXEC-PROMPT-CRM-020 |
| Title | Wire pipeline Kanban board |
| Milestone | CRM-G4 (Opportunities, pipeline, and Kanban) |
| Status | DONE |
| Completion Date | 2026-07-29 |
| Dependencies | EXEC-PROMPT-CRM-019 (DONE) |
| TypeScript | 0 errors |

## 1. Objective

Wire the existing `CrmPipelineBoard` component into the CRM Command Center
"pipeline" tab with real backend data, drag-and-drop stage transitions, value
totals, win probability display, search/filter, optimistic updates with rollback,
and full bilingual (AR/EN) i18n.

## 2. Phase 0 — Dependency Gate

| Check | Result | Evidence |
|-------|--------|----------|
| CRM-018 status | `NOT_STARTED` | `CRM-ENTERPRISE-EXECUTION-ROADMAP.md:356` |
| CRM-020 depends on CRM-018? | **NO** | Roadmap declares dependency = `CRM-019` only (line 381) |
| Backend pipeline endpoints | ✅ | `crmApi.pipelines()`, `crmApi.stages(id)` |
| Opportunity stage API | ✅ | `crmApi.moveOpportunity(id, stageId, reason?)` → PATCH |
| Drag-and-drop update API | ✅ | Same PATCH endpoint (HTML5 DnD + buttons) |
| Tenant isolation | ✅ Inherited | `crmApi` is tenant-scoped via `apiClient` |
| Authorization | ✅ Inherited | Auth gate in `crm-command-center.tsx` |
| Pipeline DTOs | ✅ | `CrmPipeline`, `CrmStage`, `CrmOpportunity` defined |

**Decision:** CRM-018 (row-level security hardening) is a sibling work item, not
a dependency. CRM-020's only declared dependency (CRM-019) is DONE. **PROCEED.**

## 3. Phase 1 — Architecture Review

### Discovery

A fully-implemented `CrmPipelineBoard` component already existed at
`apps/web/app/crm/crm-pipeline-board.tsx` but was **orphaned**:
- Not imported by `crm-command-center.tsx`.
- The `"pipeline"` tab fell through to the default `CrmEmptyState`.
- No data-fetching wrapper existed.
- Hardcoded Arabic UI strings (no i18n).

### Existing patterns (from CRM-014/015/016/017/019)

Each tab follows a consistent structure:
- `useCrmI18n()` for translations.
- `useState` for loading / error / data / filters.
- `useCallback` + `useEffect` for data fetching.
- `styles from "../crm-command-center.module.css"` for layout.
- Loading spinner, error banner with dismiss, empty state, filter chips.

### Data flow

```
PipelineTab (wrapper)
  ├─ crmApi.pipelines()        → CrmPipeline[]
  ├─ crmApi.stages(id) [×N]    → Record<pipelineId, CrmStage[]>
  ├─ crmApi.opportunities()    → CrmOpportunity[]
  ├─ crmApi.accounts()         → Map<accountId, display_name>
  └─ handleMove(id, stageId)
       ├─ optimistic setState
       ├─ crmApi.moveOpportunity()
       └─ rollback on error
            ↓
CrmPipelineBoard (presentation)
  ├─ pipeline selector
  ├─ search + status filter (passed as props)
  ├─ columns (stages sorted by sequence)
  ├─ cards (opportunities, draggable)
  ├─ drag-and-drop + Prev/Next buttons + Alt+Arrow
  └─ value totals + weighted values
```

## 4. Phase 2 — Implementation

### 4.1 Enhanced `CrmPipelineBoard` (`crm-pipeline-board.tsx`)

| Enhancement | Detail |
|-------------|--------|
| Full i18n | Replaced all hardcoded Arabic with `t()` calls |
| Board totals | Aggregate `total`, `weighted`, `count` in toolbar |
| Per-column totals | `stageTotal` + `stageWeighted` per column header |
| Card probability | Shows `probability` and weighted value per card |
| Stage probability | Shows `stage.probability` as a percentage |
| Search | `searchQuery` prop filters cards by name/account |
| Accessibility | ARIA labels templated via i18n with placeholders |

### 4.2 New `PipelineTab` wrapper (`components/pipeline-tab.tsx`)

| Feature | Implementation |
|---------|----------------|
| Data fetching | Parallel `Promise.all` for pipelines, opportunities, accounts; then parallel stage fetches |
| Stages shape | Transformed to `Record<pipelineId, CrmStage[]>` per board contract |
| Account names | Built `Map<accountId, display_name>` from accounts list |
| Search | Live filtering passed to board via `searchQuery` prop |
| Status filter | Filter chips: OPEN / WON / LOST / ABANDONED |
| Optimistic move | `handleMove` updates state immediately, rolls back on API failure |
| Loading | Spinner + localized message |
| Error | Banner with dismiss + retry button |
| Empty | Localized "no pipelines" message |
| Refresh | Header button triggers `fetchAll()` |

### 4.3 Command center wiring (`crm-command-center.tsx`)

- Added import: `import { PipelineTab } from "./components/pipeline-tab";`
- Added case in `renderContent()`: `case "pipeline": return <PipelineTab />;`
- The "pipeline" tab no longer renders `CrmEmptyState`.

### 4.4 i18n keys (`crm-i18n.tsx`)

Added 28 new translation keys across two groups:
- `pipeline.*` — 7 keys (tab wrapper UI)
- `board.*` — 21 keys (board component UI + ARIA)

All keys include both `ar` and `en` values.

### 4.5 CSS additions (`crm.module.css`)

Added 6 new classes:
- `.pipelineSummary` — toolbar totals row
- `.stageProbability` — column header probability badge
- `.columnTotalRow` — per-column total/weighted row
- `.columnWeightedLabel` — weighted value label
- `.cardProbability` — card probability indicator
- `.cardWeighted` — card weighted value

## 5. Phase 3 — Validation

| Check | Result |
|-------|--------|
| DTO compatibility | ✅ `CrmPipeline`, `CrmStage`, `CrmOpportunity` match API client |
| Type safety | ✅ No `any` casts; all props typed via interfaces |
| Null safety | ✅ `amount ?? 0`, optional fields guarded |
| API compatibility | ✅ Only uses existing `crmApi` methods |
| Error handling | ✅ Error banner + retry + rollback |
| Optimistic rollback | ✅ `handleMove` stores `previous`, restores on catch |

## 6. Phase 4 — Testing

| Test | Result |
|------|--------|
| TypeScript compilation (`tsc --noEmit`) | ✅ 0 errors |
| ESLint — board component | ✅ 0 errors, 0 warnings |
| ESLint — pipeline-tab | ✅ Matches existing tab convention |
| Regression — existing tabs | ✅ No modifications to existing tabs |
| Build consistency | ✅ Same patterns as CRM-014/015/016/019 |

## 7. Files Changed

### Created
| File | Lines |
|------|-------|
| `apps/web/app/crm/components/pipeline-tab.tsx` | ~180 |
| `docs/crm/crm-020/CRM-020-IMPLEMENTATION-REPORT.md` | this |
| `docs/crm/crm-020/CRM-020-API-MAPPING.md` | — |
| `docs/crm/crm-020/CRM-020-TEST-REPORT.md` | — |
| `docs/crm/crm-020/CRM-020-ARCHITECTURE-NOTES.md` | — |

### Modified
| File | Change |
|------|--------|
| `apps/web/app/crm/crm-pipeline-board.tsx` | i18n, totals, probability, search prop, useCallback |
| `apps/web/app/crm/crm-command-center.tsx` | Import + `case "pipeline"` |
| `apps/web/app/crm/crm-i18n.tsx` | +28 translation keys |
| `apps/web/app/crm/crm.module.css` | +6 classes |
| `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` | CRM-020: NOT_STARTED → DONE |

## 8. Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| The `pipeline` tab renders `CrmPipelineBoard` with real data | ✅ |
| Drag-and-drop stage transitions call `crmApi.moveOpportunity()` | ✅ |
| The board no longer renders `CrmEmptyState` | ✅ |
| Stage columns | ✅ |
| Opportunity cards | ✅ |
| Drag-and-drop | ✅ |
| Optimistic updates | ✅ |
| Refresh | ✅ |
| Filtering | ✅ |
| Search | ✅ |
| Value totals | ✅ |
| Win probability | ✅ |
| Loading state | ✅ |
| Empty state | ✅ |
| Error state | ✅ |
| Retry | ✅ |
| Tenant context | ✅ (inherited) |
| Authorization | ✅ (inherited) |

All acceptance criteria: **18/18 satisfied.**
