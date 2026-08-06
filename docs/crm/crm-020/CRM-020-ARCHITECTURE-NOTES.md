# CRM-020 — Architecture Notes

## 1. Design Decision: Wrap Existing Board vs. Rebuild

**Decision:** Wrap the existing `CrmPipelineBoard` component rather than rebuild.

**Rationale:**
- A fully-functional drag-and-drop Kanban board already existed at
  `crm-pipeline-board.tsx` with:
  - HTML5 drag-and-drop
  - Keyboard navigation (Alt+Arrow)
  - Prev/Next buttons
  - ARIA live announcements
  - Adjacent-stage calculation helper
- It was orphaned (not wired into the command center) but its internal logic
  was sound and well-structured.
- Rebuilding would have discarded working, reviewed code and introduced
  regression risk.
- The roadmap acceptance criteria explicitly state: *"renders the existing
  `CrmPipelineBoard` component."*

**Trade-off:** The board had hardcoded Arabic strings. This was resolved by
adding full i18n support (28 keys) without altering the component's contract.

## 2. Component Architecture

```
crm-command-center.tsx
  └─ renderContent()
       └─ case "pipeline": <PipelineTab />

PipelineTab (data + state container)
  ├─ State: pipelines, stagesByPipeline, opportunities, accounts
  ├─ State: loading, error, search, statusFilter, busy
  ├─ fetchAll() — parallel API calls
  ├─ handleMove() — optimistic update + rollback
  └─ <CrmPipelineBoard
        pipelines={pipelines}
        stages={stagesByPipeline}      ← Record<pipelineId, CrmStage[]>
        opportunities={filtered}        ← CrmOpportunity[]
        accountNames={accountNames}     ← Map<accountId, display_name>
        busy={busy}
        searchQuery={search}
        onMove={handleMove}
     />
```

**Separation of concerns:**
- `PipelineTab` owns data fetching, state management, filtering, and error
  handling — the "smart" container.
- `CrmPipelineBoard` owns presentation, interaction (DnD, keyboard), and
  derived display values (totals, weighted) — the "dumb" presentational
  component.

## 3. Data Shape Transformation

The board requires `stages` as `Record<pipelineId, CrmStage[]>` and
`accountNames` as `Map<accountId, string>`. These are non-trivial
transformations performed by the wrapper:

```ts
// stages: CrmStage[][] → Record<pipelineId, CrmStage[]>
const stageEntries = await Promise.all(
  pipelineData.map(async (p) => [p.id, await crmApi.stages(p.id)] as const),
);
const stagesMap: Record<string, CrmStage[]> = {};
for (const [id, stageList] of stageEntries) stagesMap[id] = stageList;

// accounts: CrmAccount[] → Map<accountId, display_name>
const accountNames = new Map(accounts.map(a => [a.id, a.display_name]));
```

This keeps the board component pure (no API knowledge) while the wrapper
absorbs the impedance mismatch between API arrays and board prop shapes.

## 4. Optimistic Update Strategy

The move operation uses a **snapshot-and-rollback** pattern:

```ts
const handleMove = async (opportunityId, stageId) => {
  const previous = opportunities;            // 1. Snapshot current state
  setOpportunities(prev => prev.map(...));   // 2. Apply optimistic update
  setBusy(true);
  try {
    await crmApi.moveOpportunity(...);        // 3. Confirm with API
  } catch (err) {
    setOpportunities(previous);              // 4. Roll back on failure
    setError(...);
  } finally {
    setBusy(false);
  }
};
```

**Why not refetch after success?**
- The PATCH response returns the updated `CrmOpportunity`, but the optimistic
  state already reflects the correct `stage_id`.
- Refetching would cause a visible flicker (cards jumping back and forth).
- The `busy` flag prevents concurrent moves, avoiding race conditions.
- On error, the rollback restores the exact pre-move state — no data loss.

**Trade-off:** If the server modifies additional fields during the move (e.g.,
`probability`, `updated_at`), those changes won't be reflected until a manual
refresh. This is acceptable because:
1. Stage `probability` is derived from the stage, not the opportunity.
2. `updated_at` is display-only and non-critical.
3. The refresh button provides a full resync when needed.

## 5. Caching Strategy

**No client-side caching.** All API calls use `cache: "no-store"`:
- Pipeline data changes frequently (opportunities move between stages).
- Stale cache would show incorrect board positions.
- The board is the primary operational view — freshness > performance.

The `fetchAll` function is memoized via `useCallback` with `[]` deps, so it
has a stable identity for the `useEffect` dependency array. Manual refresh
is available via the header button.

## 6. i18n Placeholder Pattern

ARIA labels use a simple string-replacement pattern rather than a full ICU
formatter:

```ts
t("board.aria.moved")
  .replace("{name}", opportunity.name)
  .replace("{stage}", stage?.name ?? t("board.newStage"))
```

This matches the existing codebase convention (no ICU library is used in the
CRM module). Placeholders are documented in the translation values.

## 7. CSS Strategy

The board uses `crm.module.css` (not `crm-command-center.module.css`).
This is intentional — the board was originally part of a different module
and its styles are self-contained. The wrapper tab uses
`crm-command-center.module.css` for the shared tab chrome (header, filter bar,
loading/error states), consistent with all other tabs.

New classes added to `crm.module.css`:
- `.pipelineSummary` — flex row of board-level totals
- `.stageProbability` — small probability text under stage name
- `.columnTotalRow` — per-column total/weighted footer
- `.columnWeightedLabel` — muted weighted label
- `.cardProbability` — blue probability indicator on cards
- `.cardWeighted` — small weighted value text on cards

All use existing CSS custom properties (`--snad-color-*`) for theme
consistency.

## 8. Accessibility Architecture

| Layer | Technique |
|-------|-----------|
| Board structure | `role="list"` / `role="listitem"` semantic grouping |
| Column labels | `aria-label` with stage name + card count |
| Card labels | `aria-label` with name + account + stage |
| Focus management | `tabIndex={0}` on cards |
| Keyboard move | `Alt+ArrowLeft/Right` → `moveAdjacent()` |
| Move feedback | `aria-live="polite"` announcement region |
| Button labels | `aria-label` with opportunity name for screen readers |

Drag-and-drop is an enhancement — all functionality is also available via
Prev/Next buttons and keyboard, ensuring the board is fully usable without
a mouse.

## 9. Dependency Inversion Compliance

The implementation preserves the existing dependency inversion:
- `PipelineTab` depends on the `crmApi` abstraction (API client interface),
  not on concrete fetch implementations.
- `CrmPipelineBoard` depends only on its props interface, not on any API
  client or state management — it can be tested or reused in isolation.
- No new coupling to backend internals, repositories, or domain services.

## 10. Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Stage fetch N+1 (one request per pipeline) | Parallel `Promise.all`; acceptable for typical pipeline counts (<10) |
| Optimistic update mask server-side field changes | Refresh button available; stage probability is derived from stage |
| Large opportunity lists slow board rendering | `limit: 200` on API; `useMemo` for filtered lists |
| Concurrent moves race | `busy` flag disables all interactions during a move |
