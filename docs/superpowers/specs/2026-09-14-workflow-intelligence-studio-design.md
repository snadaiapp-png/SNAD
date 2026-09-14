# Workflow Intelligence Studio Design

**Date:** 2026-09-14
**Status:** Approved design baseline
**Repository:** snadaiapp-png/SNAD
**Baseline:** eaa506f6de70a73cb2b680315b408525319d72ed

## Goal
Modernize the Workflow Y2 definition designer into a professional, RTL-first orchestration workspace while preserving existing Y2 server contracts, immutable publication semantics, fail-closed conflict handling, and side-effect-free validation/simulation.

## Scope
This design covers the Workflow definition workspace: command bar, step library, canvas, contextual inspector, transition editing, deterministic diagnostics, simulation presentation, publication readiness, responsive behavior, accessibility, and stage/progress visualization.

AI is explicitly out of scope. No Copilot, LLM generation, AI suggestions, embeddings, or AI dependencies are introduced. The architecture may retain clean extension boundaries for a later project-wide AI phase, but current behavior must not depend on AI.

## Architecture
Use a Progressive Workspace Refactor rather than a rewrite. Keep existing workflowApi contracts and server truth authoritative. Decompose the current designer responsibilities into focused UI units while retaining the existing StepPalette, StepInspector, ExpressionRuleEditor, AssignmentRuleEditor, and PublishPanel behavior until replaced by tested equivalents.

The workspace consists of:
- Designer Shell and Command Bar.
- Searchable Step Library.
- Canvas Workspace.
- Context Inspector.
- Transition Editor.
- Diagnostics Drawer for Validation, Simulation, and Activity.
- Publish Readiness / Version controls.
- Designer view-state layer isolated from server state.

No backend contract change is permitted merely to support presentation. If implementation discovers a required backend semantic change, stop that slice and treat it as separately authorized scope.

## Server and State Semantics
The server remains authoritative for definitions, steps, transitions, validation, simulation, and publication state.

`DRAFT` is editable. `PUBLISHED` and `RETIRED` are read-only. A published definition is never edited in place; the supported path is Create Next Draft.

Every graph mutation increments the local graph revision and invalidates prior validation and simulation evidence. HTTP 409 is fail-closed: invalidate stale evidence, reload server truth, and communicate the conflict clearly.

Node positions remain presentation state unless a supported server contract exists. Moving or selecting a node must not cause a business mutation.

Undo/redo initially covers local presentation and unsaved UI operations only. It must never imply rollback of a completed server mutation.

## Workspace Interaction Model
The canvas is the primary surface and receives the majority of available space. Desktop layout uses Step Library on the right, Canvas in the center, and Context Inspector on the left for RTL. Smaller screens convert secondary panels into drawers while retaining the canvas as the primary surface.

The Command Bar shows definition identity, Draft/Published/Retired state, version, refresh, validation, simulation, publication, and version/draft actions. Controls are gated by publication state and server evidence.

Canvas capabilities target pan, zoom, fit-to-view, selection, keyboard focus, multi-select where safe, minimap, and deterministic auto-layout. The implementation should prefer native DOM/SVG and existing project patterns unless evidence demonstrates a graph dependency is justified.

The Step Library supports search and category filtering and preserves click-to-add. Drag-and-drop may be added without removing keyboard-accessible addition.

The Context Inspector changes content according to selected entity and step type. Irrelevant controls are not shown. Transition editing treats the transition as a first-class selected entity with source, destination, outcome, condition, and supported priority fields.

## Deterministic Diagnostics
Diagnostics are non-AI and deterministic. Local checks may augment but never replace authoritative server validation.

Where supported by existing contracts/data, diagnostics cover structural issues such as START/END topology, unreachable nodes, paths unable to reach END, missing required configuration, malformed transition references, duplicate keys, incomplete approval/assignment configuration, and invalid condition drafts. A local diagnostic that cannot prove a server invariant is advisory rather than an invented enforcement rule.

Diagnostics are classified as Error, Warning, Info, and Publish Blocker. Each item should navigate to the relevant node or transition when an identifier is available.

## Simulation
Simulation remains explicitly non-production and side-effect-free. It must not create work items, approvals, notifications, or source-module business mutations. The UI visualizes visited steps and available server notes/evidence without claiming execution details the API does not provide.

Any graph mutation invalidates the displayed simulation. Simulation is never presented as current after graph revision changes.

## Publish Readiness
Publication requires the latest valid authoritative server validation and editable state, preserving the current gate. The UI presents readiness rather than weakening the gate. A 409 during publish invalidates validation/simulation, reloads the server definition, and blocks publication until validation is re-established.

After successful publication, the workspace becomes read-only immediately. The primary editing CTA for a published definition is Create Next Draft.

## SANAD Workflow Stage Color System
The workflow uses SANAD brand semantics rather than introducing unrelated primary colors. Exact color values must bind to existing SANAD design tokens where available; hard-coded speculative brand hex values are prohibited.

Semantic stages:
- START: dark royal petrol green.
- UPCOMING: platinum gray.
- COMPLETED: brighter royal petrol/success treatment.
- END: polished Royal Gold and the strongest destination treatment.
- Inactive/off-path: subdued platinum/neutral treatment.

Error/Blocker and Warning colors are operational diagnostics and remain separate from stage colors. Selection/focus is also separate and must not overwrite stage meaning.

Color is never the sole signal: each stage uses color + icon + text/state treatment.

## SANAD Workflow Progress Path
The graph communicates progressive achievement, not merely connected boxes. A canonical linear representation is:

`START -> COMPLETED -> COMPLETED -> CURRENT -> UPCOMING -> END`

CURRENT/ACTIVE is an execution/view overlay, not a fifth structural step type. It uses emphasis such as a focus ring or restrained highlight while retaining the underlying stage identity.

Completed path segments receive completed treatment; future segments receive platinum/upcoming treatment; the terminal END receives polished Royal Gold. For branched workflows the system renders a Graph Progress Journey rather than a misleading linear progress bar. Non-selected/off-path branches are visually subdued when execution evidence exists.

A numerical completion percentage must not be invented for branched workflows. Show a count or percentage only when it can be derived truthfully from the actual execution path/evidence. Designer preview must be labeled as preview and never confused with production execution.

The same semantic language is used in Canvas, structure/table view, simulation, execution/history surfaces when those surfaces have corresponding evidence, and minimap.

## Motion
Motion is restrained and purposeful: short state transitions, completion acknowledgement, and terminal emphasis. No perpetual decorative animation. Respect `prefers-reduced-motion`.

## Accessibility and RTL
RTL is structural, not merely `dir=rtl`. Panel placement, navigation, edge labels, focus order, and keyboard behavior must be coherent in Arabic. Every actionable control has an accessible name. Selected/focused/error states do not depend on color alone. Keyboard operation must remain available for adding/selecting/navigating core designer entities. Target WCAG AA-compatible contrast using the established SANAD tokens.

## Performance
Separate server state from view state. Avoid network mutations for pan, zoom, selection, or node positioning. Avoid whole-graph rerenders for inspector-only state changes where practical. Memoize graph-derived presentation data where measurement demonstrates benefit. Heavy secondary panels may be loaded lazily. Do not add a large graph library without demonstrated need.

## Failure UX
Failures are classified into validation, authorization, conflict, transient/network, server invariant, and publication failures. Recovery guidance is specific: reload server truth, navigate to the affected entity, create a next draft, retry a transient request, or resolve blockers. Generic success claims are prohibited after failed authoritative operations.

## Testing Strategy
Maintain existing Workflow Y2 regression coverage and add focused tests for the new workspace behavior. Required coverage includes publication-state gating, server-validation publication gate, simulation side-effect-free labeling/evidence invalidation, 409 conflict behavior, semantic stage/progress rendering, RTL/accessibility hooks, library search, canvas controls, and responsive panel behavior where testable.

Tests must preserve the existing safe AST constraint: no eval/new Function/raw expression textarea regression.

## Acceptance Criteria
1. Canvas is the dominant workspace surface.
2. Step Library supports fast search/filter and accessible addition.
3. Inspector is contextual by selected node/transition and step type.
4. Pan, zoom, fit, selection, and minimap work without business mutations.
5. Transition labels clearly communicate routing semantics.
6. Diagnostics classify and navigate deterministic issues without pretending to replace server validation.
7. Simulation remains side-effect-free and becomes stale after graph mutation.
8. PUBLISHED and RETIRED remain strictly read-only; PUBLISHED offers Create Next Draft.
9. 409 conflicts fail closed, reload server truth, and invalidate validation/simulation.
10. Publish remains gated on the latest valid server validation.
11. Undo/redo never pretends to roll back completed server mutations.
12. RTL, keyboard navigation, accessible naming, reduced motion, and non-color state cues are preserved.
13. No AI feature or AI dependency is introduced.
14. Existing tests remain green and new behavior receives regression coverage.
15. No backend semantic contract changes without separate evidence and authorization.
16. SANAD stage semantics are START petrol, UPCOMING platinum, COMPLETED petrol-success, END polished Royal Gold.
17. The Progress Path presents progressive achievement and handles branching without a false linear completion claim.
18. END is the highest-value visual destination and receives the polished Royal Gold terminal treatment.
19. Production closure requires CI success and the repository's Workflow Vercel production certification for the merged release commit.

## Definition of Done
`WORKFLOW_DESIGNER_MODERNIZATION = COMPLETE` only after implementation, regression tests, required CI, merge, deployment, and production certification all succeed for the authorized release commit. A local or PR-level visual success is not production closure.
