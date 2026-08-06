# CRM-019 Architecture Notes — Opportunities Tab

**Date:** 2026-07-29
**Work Item:** EXEC-PROMPT-CRM-019

---

## 1. Design Decisions

### 1.1 Pipeline/Stage Data Fetching

Pipelines and stages are fetched once on mount and cached in component state:

```typescript
const [pipelines, setPipelines] = useState<CrmPipeline[]>([]);
const [stages, setStages] = useState<CrmStage[]>([]);
```

**Rationale:**
- Pipelines and stages change infrequently
- Single fetch avoids repeated API calls
- Enables client-side filtering without additional requests

### 1.2 Dual Filter Strategy

Two independent filters are supported:
1. **Status filter** — chip-based (OPEN, WON, LOST, ABANDONED)
2. **Pipeline filter** — dropdown (all pipelines)

**Rationale:**
- Status is the most common filter (chip-based for quick access)
- Pipeline filter is secondary (dropdown to avoid clutter)
- Both filters are composable (can be used together)

### 1.3 Stage Movement Dialog

The move stage dialog:
- Shows only stages from the opportunity's pipeline
- Pre-selects the current stage
- Includes an optional reason field
- Uses `crmApi.moveOpportunity()` for the actual move

**Rationale:**
- Restricting to same-pipeline stages prevents data integrity issues
- Optional reason enables audit trail
- Pre-selecting current stage provides clear starting point

### 1.4 Create Form Pipeline/Stage联动

When the pipeline changes in the create form, the stage dropdown auto-selects the first stage:

```typescript
const handlePipelineChange = useCallback((newPipelineId: string) => {
  setPipelineId(newPipelineId);
  const firstStage = stages
    .filter((s) => s.pipeline_id === newPipelineId)
    .sort((a, b) => a.sequence - b.sequence)[0];
  setStageId(firstStage?.id ?? "");
}, [stages]);
```

**Rationale:**
- Prevents stale stage selection when pipeline changes
- First stage is the natural starting point
- Sorted by sequence to ensure correct order

---

## 2. API Design

### 2.1 Parallel Fetching

Pipelines and opportunities are fetched in parallel:

```typescript
const [oppData, pipelineData] = await Promise.all([
  crmApi.opportunities(),
  crmApi.pipelines(),
]);
```

**Rationale:**
- Reduces total load time
- These endpoints are independent
- Stages are fetched sequentially (depends on pipeline IDs)

### 2.2 Stage Fetching Strategy

Stages are fetched for all pipelines after pipelines are loaded:

```typescript
const allStages: CrmStage[] = [];
for (const p of pipelineData) {
  const s = await crmApi.stages(p.id);
  allStages.push(...s);
}
```

**Rationale:**
- Stages are pipeline-specific (each pipeline has its own stages)
- Sequential fetching ensures correct association
- All stages are needed for display names and create form

---

## 3. State Management

### 3.1 Local State

| State | Type | Purpose |
|-------|------|---------|
| `opportunities` | `CrmOpportunity[]` | All opportunities |
| `pipelines` | `CrmPipeline[]` | All pipelines |
| `stages` | `CrmStage[]` | All stages |
| `loading` | `boolean` | Fetch in progress |
| `error` | `string \| null` | Error message |
| `statusFilter` | `OpportunityStatus \| ""` | Active status filter |
| `pipelineFilter` | `string` | Active pipeline filter |
| `showCreateForm` | `boolean` | Create form visibility |
| `moveTarget` | `CrmOpportunity \| null` | Opportunity being moved |

### 3.2 No Global State

All state is local to the component. No Redux, Zustand, or Context API used.

---

## 4. Error Handling Strategy

| Layer | Strategy |
|-------|----------|
| API call | Try/catch with error message extraction |
| Loading state | Spinner + loading message |
| Error state | Error banner with dismiss |
| Empty state | Empty message when no results |
| Null fields | Fallback to "—" display |

---

## 5. Future Enhancements

| Enhancement | Priority | Notes |
|-------------|----------|-------|
| Kanban board view | High | CRM-020 scope |
| Inline editing | Medium | Edit opportunity details |
| Drag-and-drop stage movement | High | Part of Kanban |
| Bulk stage movement | Low | Multi-select operations |
| Pipeline management UI | Low | Create/edit pipelines |

---

**Architecture Authority:** CRM-019 Implementation Authority
**Date:** 2026-07-29
