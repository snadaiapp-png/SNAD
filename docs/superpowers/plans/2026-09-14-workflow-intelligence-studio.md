# Workflow Intelligence Studio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modernize the Workflow Y2 definition designer into an RTL-first SANAD orchestration workspace with deterministic diagnostics, progressive stage visualization, professional canvas controls, contextual editing, and preserved fail-closed publication semantics.

**Architecture:** Apply a progressive workspace refactor around the existing Workflow Y2 API. Keep server state authoritative, isolate local view state, retain native DOM/SVG graph rendering, and add focused components/utilities instead of replacing backend contracts or introducing a graph/AI dependency.

**Tech Stack:** Next.js 16, React 19, TypeScript 5.9, CSS Modules, native DOM/SVG, Vitest 4, Testing Library, existing `workflowApi` client.

**Spec:** `docs/superpowers/specs/2026-09-14-workflow-intelligence-studio-design.md`

## Global Constraints

- Baseline: `eaa506f6de70a73cb2b680315b408525319d72ed`.
- AI is out of scope: no Copilot, LLM generation, embeddings, AI suggestions, or AI dependencies.
- Preserve current Workflow Y2 server contracts; no backend semantic change without separate authorization.
- `DRAFT` is editable; `PUBLISHED` and `RETIRED` are read-only.
- Any graph mutation invalidates previous validation/simulation evidence.
- HTTP 409 is fail-closed: invalidate stale evidence, reload server truth, block publish until re-validation.
- Simulation remains non-production and side-effect-free.
- Keep native DOM/SVG rendering unless evidence proves an external graph dependency necessary.
- SANAD stage semantics: START = dark royal petrol, UPCOMING = platinum, COMPLETED = petrol-success, END = polished Royal Gold.
- Stage meaning must use color + icon + text; diagnostic colors and selection/focus remain separate.
- Do not invent completion percentages for branched workflows without truthful execution evidence.
- Preserve safe AST constraints: no `eval`, `new Function`, or raw expression textarea.
- Production closure requires merged-release CI plus Workflow Vercel production certification.

---

## File Structure

**Create**
- `apps/web/app/workflow/definitions/[id]/components/workflow-stage.ts` — stage/progress semantic derivation with no React dependency.
- `apps/web/app/workflow/definitions/[id]/components/workflow-diagnostics.ts` — deterministic local graph diagnostics.
- `apps/web/app/workflow/definitions/[id]/components/designer-command-bar.tsx` — definition identity and top-level commands.
- `apps/web/app/workflow/definitions/[id]/components/workflow-canvas.tsx` — native SVG/DOM graph viewport, zoom/pan/fit/minimap/selection.
- `apps/web/app/workflow/definitions/[id]/components/diagnostics-drawer.tsx` — Validation / Simulation / Activity presentation.
- `apps/web/app/workflow/definitions/[id]/components/workflow-designer.module.css` — studio layout and SANAD semantic presentation.
- `apps/web/app/workflow/definitions/[id]/__tests__/workflow-stage.test.ts` — stage/progress semantics.
- `apps/web/app/workflow/definitions/[id]/__tests__/workflow-diagnostics.test.ts` — deterministic diagnostics.

**Modify**
- `apps/web/app/workflow/definitions/[id]/components/workflow-designer.tsx` — orchestration shell and server/view-state ownership.
- `apps/web/app/workflow/definitions/[id]/components/step-palette.tsx` — search/category UX while preserving accessible click-to-add.
- `apps/web/app/workflow/definitions/[id]/components/step-inspector.tsx` — contextual node/transition inspector behavior.
- `apps/web/app/workflow/definitions/[id]/components/publish-panel.tsx` — controlled validation/simulation state and readiness presentation.
- `apps/web/app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx` — regression contract expansion.

---

### Task 1: Lock SANAD Stage and Progress Semantics

**Files:**
- Create: `apps/web/app/workflow/definitions/[id]/components/workflow-stage.ts`
- Create: `apps/web/app/workflow/definitions/[id]/__tests__/workflow-stage.test.ts`

**Interfaces:**
- Consumes: `WorkflowStepResponse`, `WorkflowTransitionResponse` from `@/lib/api/workflow-api`.
- Produces:
  - `type WorkflowStageVisual = "START" | "UPCOMING" | "COMPLETED" | "END" | "INACTIVE"`
  - `interface WorkflowStagePresentation { visual: WorkflowStageVisual; label: string; icon: string; isCurrent: boolean }`
  - `deriveWorkflowStagePresentation(step, context): WorkflowStagePresentation`
  - `deriveTransitionProgressState(transition, context): "COMPLETED" | "UPCOMING" | "INACTIVE"`

- [ ] **Step 1: Write failing unit tests for stage semantics**

```ts
import { describe, expect, it } from "vitest";
import { deriveWorkflowStagePresentation } from "../components/workflow-stage";

const step = (stepType: string, id = stepType) => ({
  id,
  workflowDefinitionId: "wf",
  stepKey: id.toLowerCase(),
  name: id,
  stepType,
  sequenceOrder: 0,
  configuration: "{}",
  slaHours: 0,
  requiredCapability: "",
  requiredRole: "",
  version: 1,
});

describe("SANAD workflow stage semantics", () => {
  it("maps START to petrol start treatment", () => {
    expect(deriveWorkflowStagePresentation(step("START"), { visitedStepIds: [], currentStepId: null })).toMatchObject({
      visual: "START",
      label: "بداية",
      icon: "▶",
    });
  });

  it("maps visited non-terminal nodes to COMPLETED", () => {
    expect(deriveWorkflowStagePresentation(step("HUMAN_TASK", "a"), { visitedStepIds: ["a"], currentStepId: null }).visual).toBe("COMPLETED");
  });

  it("keeps END as Royal Gold terminal even when reached", () => {
    expect(deriveWorkflowStagePresentation(step("END", "end"), { visitedStepIds: ["end"], currentStepId: "end" })).toMatchObject({
      visual: "END",
      label: "نهاية",
      icon: "◆",
      isCurrent: true,
    });
  });
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run from `apps/web`:

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-stage.test.ts
```

Expected: FAIL because `workflow-stage.ts` does not exist.

- [ ] **Step 3: Implement the minimal pure semantic mapper**

```ts
import type { WorkflowStepResponse, WorkflowTransitionResponse } from "@/lib/api/workflow-api";

export type WorkflowStageVisual = "START" | "UPCOMING" | "COMPLETED" | "END" | "INACTIVE";

export interface WorkflowProgressContext {
  visitedStepIds: readonly string[];
  currentStepId: string | null;
  activeTransitionIds?: readonly string[];
}

export interface WorkflowStagePresentation {
  visual: WorkflowStageVisual;
  label: string;
  icon: string;
  isCurrent: boolean;
}

export function deriveWorkflowStagePresentation(
  step: WorkflowStepResponse,
  context: WorkflowProgressContext,
): WorkflowStagePresentation {
  const isCurrent = context.currentStepId === step.id;
  if (step.stepType === "START") return { visual: "START", label: "بداية", icon: "▶", isCurrent };
  if (step.stepType === "END") return { visual: "END", label: "نهاية", icon: "◆", isCurrent };
  if (context.visitedStepIds.includes(step.id)) return { visual: "COMPLETED", label: "منجزة", icon: "✓", isCurrent };
  return { visual: "UPCOMING", label: "قادمة", icon: "○", isCurrent };
}

export function deriveTransitionProgressState(
  transition: WorkflowTransitionResponse,
  context: WorkflowProgressContext,
): "COMPLETED" | "UPCOMING" | "INACTIVE" {
  if (context.activeTransitionIds?.includes(transition.id)) return "COMPLETED";
  if (context.visitedStepIds.includes(transition.fromStepId)) return "UPCOMING";
  return "INACTIVE";
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-stage.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/web/app/workflow/definitions/'[id]'/components/workflow-stage.ts apps/web/app/workflow/definitions/'[id]'/__tests__/workflow-stage.test.ts
git commit -m "feat(workflow): add SANAD stage progress semantics"
```

---

### Task 2: Add Deterministic Local Graph Diagnostics

**Files:**
- Create: `apps/web/app/workflow/definitions/[id]/components/workflow-diagnostics.ts`
- Create: `apps/web/app/workflow/definitions/[id]/__tests__/workflow-diagnostics.test.ts`

**Interfaces:**
- Consumes: `WorkflowStepResponse[]`, `WorkflowTransitionResponse[]`.
- Produces:
  - `type DiagnosticSeverity = "ERROR" | "WARNING" | "INFO" | "PUBLISH_BLOCKER"`
  - `interface WorkflowDiagnostic { code: string; severity: DiagnosticSeverity; message: string; stepId?: string; transitionId?: string }`
  - `deriveWorkflowDiagnostics(steps, transitions): WorkflowDiagnostic[]`

- [ ] **Step 1: Write failing graph-diagnostic tests**

```ts
it("flags missing START and unreachable nodes without inventing backend rules", () => {
  const diagnostics = deriveWorkflowDiagnostics(
    [human("a"), end("end")],
    [{ id: "t1", fromStepId: "a", toStepId: "end", transitionKey: "done", outcome: "SUCCESS", priority: 0 }],
  );
  expect(diagnostics.some((item) => item.code === "MISSING_START" && item.severity === "PUBLISH_BLOCKER")).toBe(true);
});

it("flags transitions that reference missing steps", () => {
  const diagnostics = deriveWorkflowDiagnostics(
    [start("start")],
    [{ id: "broken", fromStepId: "start", toStepId: "missing", transitionKey: "x", outcome: "SUCCESS", priority: 0 }],
  );
  expect(diagnostics).toContainEqual(expect.objectContaining({ code: "BROKEN_TRANSITION_REFERENCE", transitionId: "broken" }));
});
```

- [ ] **Step 2: Run test and verify RED**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-diagnostics.test.ts
```

Expected: FAIL because diagnostic module does not exist.

- [ ] **Step 3: Implement proven structural checks only**

Implement adjacency maps and deterministic checks for:
- zero START => `PUBLISH_BLOCKER`;
- multiple START => `PUBLISH_BLOCKER`;
- broken transition references => `ERROR`;
- duplicate `transitionKey` => `WARNING` unless server validation later escalates it;
- unreachable nodes from START => `WARNING`;
- nodes unable to reach any END => `WARNING`;
- no END => `WARNING` rather than invented hard blocker.

Core traversal helper:

```ts
function reachableFrom(seed: string, adjacency: Map<string, string[]>): Set<string> {
  const visited = new Set<string>();
  const queue = [seed];
  while (queue.length) {
    const id = queue.shift()!;
    if (visited.has(id)) continue;
    visited.add(id);
    for (const next of adjacency.get(id) ?? []) queue.push(next);
  }
  return visited;
}
```

- [ ] **Step 4: Run focused diagnostics tests and verify GREEN**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-diagnostics.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/web/app/workflow/definitions/'[id]'/components/workflow-diagnostics.ts apps/web/app/workflow/definitions/'[id]'/__tests__/workflow-diagnostics.test.ts
git commit -m "feat(workflow): add deterministic graph diagnostics"
```

---

### Task 3: Build the Studio Shell, Command Bar, and SANAD Visual Tokens

**Files:**
- Create: `apps/web/app/workflow/definitions/[id]/components/designer-command-bar.tsx`
- Create: `apps/web/app/workflow/definitions/[id]/components/workflow-designer.module.css`
- Modify: `apps/web/app/workflow/definitions/[id]/components/workflow-designer.tsx`
- Modify: `apps/web/app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx`

**Interfaces:**
- `DesignerCommandBar` consumes definition state and callbacks only; it does not call APIs directly.
- `WorkflowDesigner` remains owner of authoritative load/mutation lifecycle.

- [ ] **Step 1: Extend regression tests for shell and semantic tokens**

Add assertions that the designer imports the CSS module and command bar, and that the stylesheet defines semantic classes/tokens for start/upcoming/completed/end/current without speculative brand hex literals.

```ts
expect(designer).toContain("DesignerCommandBar");
expect(designer).toContain("workflow-designer.module.css");
expect(styles).toContain("stageStart");
expect(styles).toContain("stageUpcoming");
expect(styles).toContain("stageCompleted");
expect(styles).toContain("stageEnd");
expect(styles).toContain("var(--snad-color-brand-primary)");
expect(styles).toContain("var(--snad-color-brand-accent)");
expect(styles).not.toMatch(/#[0-9a-f]{6}/i);
```

- [ ] **Step 2: Run existing designer tests and verify RED for new expectations**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx
```

- [ ] **Step 3: Create `DesignerCommandBar` as a pure controlled component**

Required props:

```ts
interface DesignerCommandBarProps {
  definition: WorkflowDefinitionResponse;
  busy: boolean;
  view: "canvas" | "table";
  onViewChange: (view: "canvas" | "table") => void;
  onRefresh: () => void;
  onCreateNextDraft: () => void;
}
```

Render Arabic accessible labels, publication/version badges, canvas/table toggle, refresh, and Create Next Draft only for published definitions.

- [ ] **Step 4: Move page-level layout to CSS module**

Use logical properties for RTL and existing SANAD tokens. Define desktop grid:

```css
.studioGrid {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr) minmax(240px, 300px);
  gap: var(--snad-space-3);
  align-items: stretch;
}

.stageStart { --workflow-stage-accent: var(--snad-color-brand-primary); }
.stageUpcoming { --workflow-stage-accent: var(--snad-color-border-strong); }
.stageCompleted { --workflow-stage-accent: var(--snad-color-success); }
.stageEnd { --workflow-stage-accent: var(--snad-color-brand-accent); }

@media (max-width: 980px) {
  .studioGrid { grid-template-columns: 1fr; }
}
```

If a referenced token does not exist, bind to the closest existing SANAD token found in repository styles rather than adding a guessed hex value.

- [ ] **Step 5: Refactor `WorkflowDesigner` shell without changing mutation semantics**

Keep `load`, `saveStep`, `createTransition`, `createNextDraft`, `graphRevision`, and 409 handling intact. Replace only the header/layout markup with the new command bar and structured regions.

- [ ] **Step 6: Run focused tests and lint**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx
npm run lint -- app/workflow/definitions/[id]/components
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/web/app/workflow/definitions/'[id]'/components apps/web/app/workflow/definitions/'[id]'/__tests__/workflow-designer.test.tsx
git commit -m "feat(workflow): introduce studio shell and command bar"
```

---

### Task 4: Upgrade Step Library with Search and Deterministic Categories

**Files:**
- Modify: `apps/web/app/workflow/definitions/[id]/components/step-palette.tsx`
- Modify: `apps/web/app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx`

**Interfaces:**
- Preserve `onAdd(type: WorkflowStepType)` and `disabled?: boolean`.
- No server/API calls inside palette.

- [ ] **Step 1: Add failing source-level regression assertions**

```ts
expect(palette).toContain('aria-label="بحث في مكتبة الخطوات"');
expect(palette).toContain("searchQuery");
expect(palette).toContain("CONTROL");
expect(palette).toContain("HUMAN");
expect(palette).toContain("SYSTEM");
```

- [ ] **Step 2: Verify RED**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx
```

- [ ] **Step 3: Extend metadata and filtering**

Use deterministic categories only:

```ts
type StepCategory = "FLOW" | "HUMAN" | "CONTROL" | "SYSTEM";

const Y2_STEP_TYPES = [
  { type: "START", label: "بداية", category: "FLOW" },
  { type: "HUMAN_TASK", label: "مهمة بشرية", category: "HUMAN" },
  { type: "APPROVAL", label: "موافقة", category: "HUMAN" },
  { type: "CONDITION", label: "شرط", category: "CONTROL" },
  { type: "PARALLEL_FORK", label: "تفريع متوازٍ", category: "CONTROL" },
  { type: "PARALLEL_JOIN", label: "دمج متوازٍ", category: "CONTROL" },
  { type: "SYSTEM_ACTION", label: "إجراء نظامي", category: "SYSTEM" },
  { type: "CALL_WORKFLOW", label: "استدعاء سير عمل", category: "SYSTEM" },
  { type: "NOTIFICATION", label: "إشعار", category: "SYSTEM" },
  { type: "END", label: "نهاية", category: "FLOW" },
] satisfies { type: WorkflowStepType; label: string; category: StepCategory }[];
```

Filtering must match Arabic label and technical type, preserve keyboard-accessible buttons, and never require drag-and-drop.

- [ ] **Step 4: Run tests**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/web/app/workflow/definitions/'[id]'/components/step-palette.tsx apps/web/app/workflow/definitions/'[id]'/__tests__/workflow-designer.test.tsx
git commit -m "feat(workflow): add searchable categorized step library"
```

---

### Task 5: Extract Native Workflow Canvas with Zoom, Pan, Fit, Selection, Minimap, and Progress Path

**Files:**
- Create: `apps/web/app/workflow/definitions/[id]/components/workflow-canvas.tsx`
- Modify: `apps/web/app/workflow/definitions/[id]/components/workflow-designer.tsx`
- Modify: `apps/web/app/workflow/definitions/[id]/components/workflow-designer.module.css`
- Modify: `apps/web/app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx`

**Interfaces:**

```ts
interface WorkflowCanvasProps {
  steps: WorkflowStepResponse[];
  transitions: WorkflowTransitionResponse[];
  positions: Record<string, { x: number; y: number }>;
  selectedStepId: string | null;
  selectedTransitionId: string | null;
  editable: boolean;
  progressContext: WorkflowProgressContext;
  onSelectStep: (id: string | null) => void;
  onSelectTransition: (id: string | null) => void;
  onMoveStep: (id: string, position: { x: number; y: number }) => void;
}
```

- [ ] **Step 1: Add failing assertions for native controls**

```ts
expect(canvas).toContain('aria-label="تكبير"');
expect(canvas).toContain('aria-label="تصغير"');
expect(canvas).toContain('aria-label="ملاءمة الرسم"');
expect(canvas).toContain('aria-label="الخريطة المصغرة"');
expect(canvas).toContain("deriveWorkflowStagePresentation");
expect(canvas).toContain("<svg");
expect(canvas).not.toMatch(/reactflow|xyflow|dagre/i);
```

- [ ] **Step 2: Verify RED**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx
```

- [ ] **Step 3: Implement viewport state locally**

```ts
interface ViewportState { scale: number; offsetX: number; offsetY: number }
const [viewport, setViewport] = useState<ViewportState>({ scale: 1, offsetX: 0, offsetY: 0 });

const zoomBy = (delta: number) => setViewport((current) => ({
  ...current,
  scale: Math.min(1.8, Math.max(0.55, current.scale + delta)),
}));
```

Pan/zoom/fit are view-only operations and must never call `workflowApi`.

- [ ] **Step 4: Render stage-aware nodes and progress-aware edges**

Each node renders icon + label + stage text and a current overlay when applicable. END uses the `stageEnd` CSS treatment bound to SANAD Royal Gold token. Edge style derives from `deriveTransitionProgressState` and preserves the existing visible `SOURCE -> OUTCOME/CONDITION -> DESTINATION` label contract.

- [ ] **Step 5: Implement minimap as a derived SVG view**

Minimap must be `aria-label="الخريطة المصغرة"`, presentation-only, and use the same stage semantic classes. It must not create a second graph state.

- [ ] **Step 6: Implement Fit-to-view from current local node bounds**

Use node positions and canvas dimensions only. Clamp scale to the same viewport bounds; no network call.

- [ ] **Step 7: Keep node-position drag local**

`onMoveStep` updates only `positions`; do not increment `graphRevision` and do not invalidate server validation because presentation-only position movement is not a graph mutation.

- [ ] **Step 8: Run tests and lint**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx app/workflow/definitions/[id]/__tests__/workflow-stage.test.ts
npm run lint -- app/workflow/definitions/[id]/components
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add apps/web/app/workflow/definitions/'[id]'/components apps/web/app/workflow/definitions/'[id]'/__tests__
git commit -m "feat(workflow): add native progress-aware canvas controls"
```

---

### Task 6: Make Inspector Contextual and Promote Transition Selection

**Files:**
- Modify: `apps/web/app/workflow/definitions/[id]/components/step-inspector.tsx`
- Modify: `apps/web/app/workflow/definitions/[id]/components/workflow-designer.tsx`
- Modify: `apps/web/app/workflow/definitions/[id]/components/workflow-canvas.tsx`
- Modify: `apps/web/app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx`

**Interfaces:**
- Add `selectedTransition: WorkflowTransitionResponse | null`.
- Existing create-step and create-transition callbacks remain unchanged.
- Saved steps remain read-only because the current server contract exposes create/list but no update/delete API in this scope.

- [ ] **Step 1: Add failing inspector regression expectations**

```ts
expect(inspector).toContain("selectedTransition");
expect(inspector).toContain("تفاصيل الانتقال");
expect(inspector).toContain("سياسة الموافقة");
expect(inspector).toContain("AssignmentRuleEditor");
```

- [ ] **Step 2: Verify RED**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx
```

- [ ] **Step 3: Add transition selection path**

Clicking an edge sets `selectedTransitionId` and clears step selection. Clicking a node does the inverse. `WorkflowDesigner` derives the selected transition from server-loaded transitions.

- [ ] **Step 4: Reorder inspector rendering by context**

Render in this priority:
1. local unsaved draft editor;
2. selected saved transition details;
3. selected saved step details;
4. empty-state guidance;
5. new-transition creator only when editable.

Do not imply an update API for persisted steps/transitions.

- [ ] **Step 5: Preserve step-type-specific controls for drafts**

Approval keeps `ANY_ONE/ALL` and self-approval; Human Task/Approval keep AssignmentRuleEditor; SLA remains explicit; safe AST editor remains the only condition editor.

- [ ] **Step 6: Run tests**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/web/app/workflow/definitions/'[id]'/components apps/web/app/workflow/definitions/'[id]'/__tests__/workflow-designer.test.tsx
git commit -m "feat(workflow): make designer inspector contextual"
```

---

### Task 7: Add Diagnostics Drawer and Controlled Validation/Simulation Evidence

**Files:**
- Create: `apps/web/app/workflow/definitions/[id]/components/diagnostics-drawer.tsx`
- Modify: `apps/web/app/workflow/definitions/[id]/components/publish-panel.tsx`
- Modify: `apps/web/app/workflow/definitions/[id]/components/workflow-designer.tsx`
- Modify: `apps/web/app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx`

**Interfaces:**
- `WorkflowDesigner` owns `latestValidation` and `simulation` so canvas/progress and drawer see the same evidence.
- `PublishPanel` receives controlled evidence/callbacks rather than keeping isolated hidden state.
- `DiagnosticsDrawer` receives local diagnostics plus authoritative validation/simulation evidence.

- [ ] **Step 1: Add failing regression assertions for evidence ownership and drawer**

```ts
expect(designer).toContain("latestValidation");
expect(designer).toContain("simulation");
expect(designer).toContain("DiagnosticsDrawer");
expect(drawer).toContain("Validation");
expect(drawer).toContain("Simulation");
expect(drawer).toContain("Activity");
```

- [ ] **Step 2: Verify RED**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx
```

- [ ] **Step 3: Lift validation/simulation state into `WorkflowDesigner`**

Use exact invalidation helper:

```ts
const invalidateEvidence = () => {
  setLatestValidation(null);
  setSimulation(null);
};
```

Call it only for server graph mutations (`saveStep`, `createTransition`, successful publication state change, 409 reload path), not for pan/zoom/selection/node positioning.

- [ ] **Step 4: Refactor `PublishPanel` into controlled operations**

The authoritative gate remains:

```ts
const canPublish = editable && latestValidation?.valid === true && !busy;
```

Simulation remains disabled unless latest authoritative validation is valid. On publish 409: invalidate evidence, display conflict recovery text, await `onReload()`.

- [ ] **Step 5: Implement diagnostics drawer**

Validation tab merges deterministic local diagnostics with authoritative server errors but labels their source. Local warnings must never override server `valid` status. Simulation tab shows `visitedStepIds.length`, notes, and explicit `محاكاة غير إنتاجية` / `لا تنفذ آثارًا جانبية`. Activity tab initially shows truthful current-session events only (reload, validation, simulation, conflict), not invented backend history.

- [ ] **Step 6: Feed simulation evidence into progress context**

Use `simulation?.visitedStepIds ?? []`. Current step is the last visited step only in a clearly labeled **simulation preview** context; do not present it as production execution.

- [ ] **Step 7: Run tests**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx app/workflow/definitions/[id]/__tests__/workflow-diagnostics.test.ts app/workflow/definitions/[id]/__tests__/workflow-stage.test.ts
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/web/app/workflow/definitions/'[id]'/components apps/web/app/workflow/definitions/'[id]'/__tests__
git commit -m "feat(workflow): add deterministic diagnostics and evidence drawer"
```

---

### Task 8: Add Keyboard, RTL, Reduced-Motion, and Responsive Regression Coverage

**Files:**
- Modify: `apps/web/app/workflow/definitions/[id]/components/workflow-canvas.tsx`
- Modify: `apps/web/app/workflow/definitions/[id]/components/workflow-designer.module.css`
- Modify: `apps/web/app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx`

**Interfaces:**
- Keyboard commands affect local view state only unless activating an already-visible button.

- [ ] **Step 1: Add failing accessibility/source-regression checks**

```ts
expect(canvas).toContain("onKeyDown");
expect(canvas).toContain('aria-label="لوحة تصميم سير العمل"');
expect(styles).toContain("prefers-reduced-motion");
expect(styles).toContain("focus-visible");
expect(styles).toContain("@media (max-width:");
```

- [ ] **Step 2: Verify RED**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx
```

- [ ] **Step 3: Implement local keyboard interactions**

Required keys when canvas has focus:
- `Escape`: clear selection;
- `f` / `F`: fit view;
- `+` / `=`: zoom in;
- `-`: zoom out.

Do not bind Delete to persisted server entities because no delete contract exists in the authorized scope.

- [ ] **Step 4: Add visible focus and reduced-motion rules**

```css
.canvasNode:focus-visible,
.canvasControl:focus-visible {
  outline: 3px solid var(--snad-color-focus-ring);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .canvasNode,
  .progressEdge,
  .drawer { transition: none !important; animation: none !important; }
}
```

- [ ] **Step 5: Ensure logical RTL layout properties**

Use `inset-inline-*`, `margin-inline-*`, `padding-inline-*`, and avoid left/right positioning for semantic layout where possible.

- [ ] **Step 6: Run focused tests and lint**

```bash
npm test -- app/workflow/definitions/[id]/__tests__/workflow-designer.test.tsx
npm run lint -- app/workflow/definitions/[id]/components
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/web/app/workflow/definitions/'[id]'/components apps/web/app/workflow/definitions/'[id]'/__tests__/workflow-designer.test.tsx
git commit -m "feat(workflow): harden studio RTL and accessibility"
```

---

### Task 9: Full Web Regression and Production-Trigger Contract Verification

**Files:**
- Modify only if a failing regression proves a scoped fix is required.

**Interfaces:**
- No new feature interface; this is the release-candidate verification gate.

- [ ] **Step 1: Run all Workflow designer tests**

```bash
cd apps/web
npm test -- app/workflow/definitions/[id]/__tests__
```

Expected: PASS, zero failures.

- [ ] **Step 2: Run complete web test suite**

```bash
npm test
```

Expected: PASS, zero failures.

- [ ] **Step 3: Run lint**

```bash
npm run lint
```

Expected: PASS.

- [ ] **Step 4: Run production build**

```bash
npm run build
```

Expected: PASS.

- [ ] **Step 5: Run brand identity check**

```bash
npm run brand:check
```

Expected: PASS. Any failure caused by speculative colors must be fixed by rebinding to repository SANAD tokens, never by suppressing the check.

- [ ] **Step 6: Verify no AI or graph dependency was introduced**

```bash
git diff eaa506f6de70a73cb2b680315b408525319d72ed...HEAD -- apps/web/package.json apps/web/package-lock.json
```

Expected: no AI dependency and no graph-library dependency additions.

- [ ] **Step 7: Verify Workflow production certification trigger still covers runtime surface**

```bash
python3 scripts/production/tests/test_workflow_y2_vercel_production_certification.py
```

Expected: PASS.

- [ ] **Step 8: Review diff for backend-contract drift**

```bash
git diff --stat eaa506f6de70a73cb2b680315b408525319d72ed...HEAD
git diff eaa506f6de70a73cb2b680315b408525319d72ed...HEAD -- apps/web/lib/api/workflow-api.ts
```

Expected: no backend API semantic change required by this modernization.

- [ ] **Step 9: Commit any verification-only scoped fixes, then stop before merge**

```bash
git status --short
git log --oneline --decorate -12
```

Expected: clean working tree; implementation branch ready for independent review/PR. Do not claim production closure at this point.

---

## Plan Self-Review

- Spec coverage: architecture, server authority, fail-closed 409, validation/simulation invalidation, SANAD stage colors, Progress Path, canvas controls, searchable library, contextual inspector, diagnostics, accessibility/RTL, performance constraints, no AI, and production certification are each mapped to an implementation task.
- Placeholder scan: no TBD/TODO/"implement later" placeholders are used as execution instructions.
- Type consistency: all new shared types are defined in Task 1 or Task 2 before consumption by later tasks; existing API request/response names match `workflow-api.ts`.
- Scope control: no backend mutation/update/delete semantics are invented; persisted saved steps remain read-only under current API capabilities.
- Release truth: Task 9 ends at a verified branch/PR-ready state; merge, Vercel deployment, and production certification remain later release gates and must be evidenced before `WORKFLOW_DESIGNER_MODERNIZATION = COMPLETE`.
