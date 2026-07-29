# CRM-020 — API Mapping

Complete mapping of every backend API endpoint consumed by the pipeline Kanban
board, including request/response shapes, error handling, and the UI surface
that invokes each.

## 1. Endpoint Summary

| # | Method | Endpoint | crmApi Method | Invoked By |
|---|--------|----------|---------------|------------|
| 1 | GET | `/api/v1/crm/pipelines` | `crmApi.pipelines()` | `PipelineTab.fetchAll()` |
| 2 | GET | `/api/v1/crm/pipelines/{pipelineId}/stages` | `crmApi.stages(pipelineId)` | `PipelineTab.fetchAll()` (per pipeline) |
| 3 | GET | `/api/v1/crm/opportunities` | `crmApi.opportunities()` | `PipelineTab.fetchAll()` |
| 4 | GET | `/api/v1/crm/accounts` | `crmApi.accounts()` | `PipelineTab.fetchAll()` |
| 5 | PATCH | `/api/v1/crm/opportunities/{id}/stage` | `crmApi.moveOpportunity(id, stageId, reason?)` | `PipelineTab.handleMove()` |

All requests use `cache: "no-store"` and carry tenant context + auth headers via
the shared `apiClient`.

## 2. DTO Definitions

### CrmPipeline
```ts
interface CrmPipeline {
  id: string;
  name: string;
  currency_code?: string | null;
  active: boolean;
}
```

### CrmStage
```ts
interface CrmStage {
  id: string;
  pipeline_id: string;
  name: string;
  sequence: number;
  probability: number;       // 0.0 – 1.0
  terminal_state?: string | null;
}
```

### CrmOpportunity
```ts
interface CrmOpportunity {
  id: string;
  account_id: string;
  contact_id?: string | null;
  pipeline_id: string;
  stage_id: string;
  name: string;
  amount?: number | null;
  currency_code: string;
  probability: number;       // 0.0 – 1.0
  status: string;            // OPEN | WON | LOST | ABANDONED
  expected_close_date?: string | null;
  updated_at: string;
}
```

### CrmAccount (used for accountNames map)
```ts
interface CrmAccount {
  id: string;
  display_name: string;
  account_type: string;
  lifecycle_status: string;
  primary_currency_code?: string | null;
  owner_user_id?: string | null;
  updated_at: string;
}
```

## 3. Data Transformation

The `PipelineTab` wrapper transforms raw API arrays into the shapes the board
component expects:

```
crmApi.pipelines()          → CrmPipeline[]        → pipelines (passthrough)
crmApi.stages(id) [×N]      → CrmStage[][]         → Record<pipelineId, CrmStage[]>
crmApi.opportunities()      → CrmOpportunity[]     → opportunities (filtered by status)
crmApi.accounts()           → CrmAccount[]         → Map<accountId, display_name>
```

### Board prop contract
```ts
interface PipelineBoardProps {
  pipelines: CrmPipeline[];
  stages: Record<string, CrmStage[]>;        // keyed by pipeline ID
  opportunities: CrmOpportunity[];
  accountNames: Map<string, string>;          // keyed by account ID
  busy: boolean;
  searchQuery?: string;
  onMove: (opportunityId: string, stageId: string) => Promise<void> | void;
}
```

## 4. Move Operation (Drag-and-Drop)

### Request
```
PATCH /api/v1/crm/opportunities/{opportunityId}/stage
Content-Type: application/json

{
  "stageId": "stage-uuid",
  "reason": "optional reason string"
}
```

### Response
```ts
// 200 OK — returns updated opportunity
CrmOpportunity
```

### UI invocation paths
1. **Drag-and-drop** — HTML5 DnD: card `onDragStart` sets
   `application/x-snad-opportunity-id`; column `onDrop` calls `move()`.
2. **Prev/Next buttons** — `moveAdjacent()` computes adjacent stage by sequence
   and calls `move()`.
3. **Keyboard** — `Alt+ArrowLeft` / `Alt+ArrowRight` calls `moveAdjacent()`.

### Optimistic update + rollback
```ts
const handleMove = async (opportunityId, stageId) => {
  const previous = opportunities;                          // snapshot
  setOpportunities(prev => prev.map(o =>                   // optimistic
    o.id === opportunityId ? { ...o, stage_id: stageId } : o
  ));
  try {
    await crmApi.moveOpportunity(opportunityId, stageId);  // confirm
  } catch {
    setOpportunities(previous);                            // rollback
    setError("Failed to move — rolled back");
  }
};
```

## 5. Guard Conditions

The board's `move()` function applies these guards before invoking `onMove`:
- `busy` is false (no concurrent move in flight)
- `opportunity.status === "OPEN"` (terminal opportunities are not draggable)
- `opportunity.stage_id !== stageId` (no-op if same stage)

## 6. Error Handling

| Scenario | Handling |
|----------|----------|
| Initial load failure | Error banner + retry button |
| Move API failure | Optimistic rollback + error banner |
| Empty pipelines | `board.empty.noPipeline` message |
| Empty opportunities | Board renders columns with "drag here" placeholders |
| Network error | Caught by `try/catch`, message surfaced in banner |
