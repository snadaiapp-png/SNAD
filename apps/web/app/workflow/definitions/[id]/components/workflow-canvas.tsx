"use client";

import {
  useMemo,
  useRef,
  useState,
  type DragEvent,
  type KeyboardEvent,
  type PointerEvent,
} from "react";
import type {
  WorkflowStepResponse,
  WorkflowTransitionResponse,
} from "@/lib/api/workflow-api";
import {
  deriveTransitionProgressState,
  deriveWorkflowStagePresentation,
  type WorkflowProgressContext,
  type WorkflowStageVisual,
} from "./workflow-stage";
import styles from "./workflow-designer.module.css";

export interface WorkflowCanvasProps {
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

interface ViewportState {
  scale: number;
  offsetX: number;
  offsetY: number;
}

interface PanState {
  pointerId: number;
  startX: number;
  startY: number;
  initialOffsetX: number;
  initialOffsetY: number;
}

const NODE_WIDTH = 164;
const NODE_HEIGHT = 72;
const MIN_SCALE = 0.55;
const MAX_SCALE = 1.8;

export function WorkflowCanvas({
  steps,
  transitions,
  positions,
  selectedStepId,
  selectedTransitionId,
  editable,
  progressContext,
  onSelectStep,
  onSelectTransition,
  onMoveStep,
}: WorkflowCanvasProps) {
  const viewportRef = useRef<HTMLDivElement | null>(null);
  const [viewport, setViewport] = useState<ViewportState>({ scale: 1, offsetX: 0, offsetY: 0 });
  const [draggedStepId, setDraggedStepId] = useState<string | null>(null);
  const [panState, setPanState] = useState<PanState | null>(null);

  const bounds = useMemo(() => graphBounds(steps, positions), [positions, steps]);
  const worldWidth = Math.max(920, bounds.maxX + NODE_WIDTH + 80);
  const worldHeight = Math.max(520, bounds.maxY + NODE_HEIGHT + 80);

  const zoomBy = (delta: number) => {
    setViewport((current) => ({
      ...current,
      scale: clamp(current.scale + delta, MIN_SCALE, MAX_SCALE),
    }));
  };

  const fitToView = () => {
    const host = viewportRef.current;
    if (!host || steps.length === 0) {
      setViewport({ scale: 1, offsetX: 0, offsetY: 0 });
      return;
    }

    const padding = 48;
    const contentWidth = Math.max(NODE_WIDTH, bounds.maxX - bounds.minX + NODE_WIDTH);
    const contentHeight = Math.max(NODE_HEIGHT, bounds.maxY - bounds.minY + NODE_HEIGHT);
    const nextScale = clamp(
      Math.min(
        (host.clientWidth - padding * 2) / contentWidth,
        (host.clientHeight - padding * 2) / contentHeight,
      ),
      MIN_SCALE,
      MAX_SCALE,
    );

    setViewport({
      scale: nextScale,
      offsetX: padding - bounds.minX * nextScale,
      offsetY: padding - bounds.minY * nextScale,
    });
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    if (!editable || !draggedStepId) return;
    event.preventDefault();
    const rect = event.currentTarget.getBoundingClientRect();
    const logicalX = (event.clientX - rect.left - viewport.offsetX) / viewport.scale - NODE_WIDTH / 2;
    const logicalY = (event.clientY - rect.top - viewport.offsetY) / viewport.scale - NODE_HEIGHT / 2;
    onMoveStep(draggedStepId, {
      x: Math.max(8, logicalX),
      y: Math.max(8, logicalY),
    });
    setDraggedStepId(null);
  };

  const startPan = (event: PointerEvent<HTMLDivElement>) => {
    if (event.target !== event.currentTarget) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    setPanState({
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      initialOffsetX: viewport.offsetX,
      initialOffsetY: viewport.offsetY,
    });
  };

  const continuePan = (event: PointerEvent<HTMLDivElement>) => {
    if (!panState || panState.pointerId !== event.pointerId) return;
    setViewport((current) => ({
      ...current,
      offsetX: panState.initialOffsetX + event.clientX - panState.startX,
      offsetY: panState.initialOffsetY + event.clientY - panState.startY,
    }));
  };

  const endPan = (event: PointerEvent<HTMLDivElement>) => {
    if (!panState || panState.pointerId !== event.pointerId) return;
    setPanState(null);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  const clearSelection = () => {
    onSelectStep(null);
    onSelectTransition(null);
  };

  return (
    <div className={styles.canvasShell}>
      <div className={styles.canvasToolbar} aria-label="أدوات لوحة الرسم">
        <button type="button" aria-label="تكبير" onClick={() => zoomBy(0.12)}>+</button>
        <button type="button" aria-label="تصغير" onClick={() => zoomBy(-0.12)}>−</button>
        <button type="button" aria-label="ملاءمة الرسم" onClick={fitToView}>ملاءمة</button>
        <span className={styles.zoomReadout} aria-live="polite">{Math.round(viewport.scale * 100)}%</span>
        <span className={styles.previewBadge}>معاينة بنيوية · ليست حالة تنفيذ إنتاجية</span>
      </div>

      <div
        ref={viewportRef}
        className={styles.canvasViewport}
        onDragOver={(event) => { if (editable) event.preventDefault(); }}
        onDrop={handleDrop}
        onPointerDown={startPan}
        onPointerMove={continuePan}
        onPointerUp={endPan}
        onPointerCancel={endPan}
        onDoubleClick={clearSelection}
      >
        <div
          className={styles.canvasSurface}
          style={{
            width: worldWidth,
            height: worldHeight,
            transform: `translate(${viewport.offsetX}px, ${viewport.offsetY}px) scale(${viewport.scale})`,
          }}
        >
          <svg
            className={styles.edgeLayer}
            width={worldWidth}
            height={worldHeight}
            aria-label="انتقالات سير العمل"
          >
            <defs>
              <marker id="workflow-arrow" markerWidth="8" markerHeight="8" refX="8" refY="4" orient="auto">
                <path d="M0,0 L8,4 L0,8 z" fill="currentColor" />
              </marker>
            </defs>
            {transitions.map((transition) => {
              const from = positions[transition.fromStepId];
              const to = positions[transition.toStepId];
              if (!from || !to) return null;
              const progressState = deriveTransitionProgressState(transition, progressContext);
              const labelX = (from.x + NODE_WIDTH + to.x) / 2;
              const labelY = (from.y + NODE_HEIGHT / 2 + to.y + NODE_HEIGHT / 2) / 2 - 10;
              const selected = selectedTransitionId === transition.id;
              return (
                <g
                  key={transition.id}
                  role="button"
                  tabIndex={0}
                  aria-label={`انتقال ${transition.transitionKey}: ${transition.outcome}`}
                  data-progress-state={progressState}
                  className={`${styles.edgeGroup} ${styles[`edge${progressState}`]} ${selected ? styles.edgeSelected : ""}`.trim()}
                  onClick={(event) => {
                    event.stopPropagation();
                    onSelectStep(null);
                    onSelectTransition(transition.id);
                  }}
                  onKeyDown={(event: KeyboardEvent<SVGGElement>) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      onSelectStep(null);
                      onSelectTransition(transition.id);
                    }
                  }}
                >
                  <line
                    x1={from.x + NODE_WIDTH}
                    y1={from.y + NODE_HEIGHT / 2}
                    x2={to.x}
                    y2={to.y + NODE_HEIGHT / 2}
                    markerEnd="url(#workflow-arrow)"
                  />
                  <text
                    data-testid={`edge-label-${transition.transitionKey}`}
                    x={labelX}
                    y={labelY}
                    textAnchor="middle"
                  >
                    {transition.outcome || transition.transitionKey}
                  </text>
                </g>
              );
            })}
          </svg>

          {steps.map((step) => {
            const position = positions[step.id] ?? { x: 12, y: 12 };
            const presentation = deriveWorkflowStagePresentation(step, progressContext);
            const selected = selectedStepId === step.id;
            return (
              <button
                key={step.id}
                type="button"
                draggable={editable}
                onDragStart={() => setDraggedStepId(step.id)}
                onDragEnd={() => setDraggedStepId(null)}
                onClick={(event) => {
                  event.stopPropagation();
                  onSelectTransition(null);
                  onSelectStep(step.id);
                }}
                className={`${styles.canvasNode} ${stageClass(presentation.visual)} ${presentation.isCurrent ? styles.stageCurrent : ""} ${selected ? styles.nodeSelected : ""}`.trim()}
                style={{ left: position.x, top: position.y }}
                data-stage={presentation.visual}
                aria-pressed={selected}
              >
                <span className={styles.stageIcon} aria-hidden>{presentation.icon}</span>
                <span className={styles.nodeText}>
                  <strong>{step.name}</strong>
                  <span>{step.stepType}{isParallel(step) ? " ∥" : ""}</span>
                  <small>{presentation.label}</small>
                </span>
              </button>
            );
          })}

          {steps.length === 0 && (
            <p className={styles.canvasEmpty}>لا توجد خطوات بعد. أضف أول عقدة من المكتبة.</p>
          )}
        </div>
      </div>

      <svg
        className={styles.minimap}
        aria-label="الخريطة المصغرة"
        viewBox={`0 0 ${worldWidth} ${worldHeight}`}
        role="img"
      >
        {transitions.map((transition) => {
          const from = positions[transition.fromStepId];
          const to = positions[transition.toStepId];
          if (!from || !to) return null;
          return (
            <line
              key={transition.id}
              x1={from.x + NODE_WIDTH / 2}
              y1={from.y + NODE_HEIGHT / 2}
              x2={to.x + NODE_WIDTH / 2}
              y2={to.y + NODE_HEIGHT / 2}
              className={styles.minimapEdge}
            />
          );
        })}
        {steps.map((step) => {
          const position = positions[step.id] ?? { x: 12, y: 12 };
          const presentation = deriveWorkflowStagePresentation(step, progressContext);
          return (
            <rect
              key={step.id}
              x={position.x}
              y={position.y}
              width={NODE_WIDTH}
              height={NODE_HEIGHT}
              rx={10}
              className={`${styles.minimapNode} ${stageClass(presentation.visual)}`}
              data-stage={presentation.visual}
            />
          );
        })}
      </svg>
    </div>
  );
}

function graphBounds(
  steps: WorkflowStepResponse[],
  positions: Record<string, { x: number; y: number }>,
) {
  if (steps.length === 0) return { minX: 0, minY: 0, maxX: NODE_WIDTH, maxY: NODE_HEIGHT };
  const xs = steps.map((step) => positions[step.id]?.x ?? 12);
  const ys = steps.map((step) => positions[step.id]?.y ?? 12);
  return {
    minX: Math.min(...xs),
    minY: Math.min(...ys),
    maxX: Math.max(...xs),
    maxY: Math.max(...ys),
  };
}

function stageClass(visual: WorkflowStageVisual) {
  switch (visual) {
    case "START": return styles.stageStart;
    case "COMPLETED": return styles.stageCompleted;
    case "END": return styles.stageEnd;
    case "INACTIVE": return styles.stageInactive;
    default: return styles.stageUpcoming;
  }
}

function isParallel(step: WorkflowStepResponse) {
  return step.stepType === "PARALLEL_FORK" || step.stepType === "PARALLEL_JOIN";
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}
