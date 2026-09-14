import type {
  WorkflowStepResponse,
  WorkflowTransitionResponse,
} from "@/lib/api/workflow-api";

export type WorkflowStageVisual =
  | "START"
  | "UPCOMING"
  | "COMPLETED"
  | "END"
  | "INACTIVE";

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

  if (step.stepType === "START") {
    return { visual: "START", label: "بداية", icon: "▶", isCurrent };
  }

  if (step.stepType === "END") {
    return { visual: "END", label: "نهاية", icon: "◆", isCurrent };
  }

  if (context.visitedStepIds.includes(step.id)) {
    return { visual: "COMPLETED", label: "منجزة", icon: "✓", isCurrent };
  }

  return { visual: "UPCOMING", label: "قادمة", icon: "○", isCurrent };
}

export function deriveTransitionProgressState(
  transition: WorkflowTransitionResponse,
  context: WorkflowProgressContext,
): "COMPLETED" | "UPCOMING" | "INACTIVE" {
  if (context.activeTransitionIds?.includes(transition.id)) {
    return "COMPLETED";
  }

  if (context.visitedStepIds.includes(transition.fromStepId)) {
    return "UPCOMING";
  }

  return "INACTIVE";
}
