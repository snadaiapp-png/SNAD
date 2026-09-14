import type {
  WorkflowStepResponse,
  WorkflowTransitionResponse,
} from "@/lib/api/workflow-api";

export type DiagnosticSeverity =
  | "ERROR"
  | "WARNING"
  | "INFO"
  | "PUBLISH_BLOCKER";

export interface WorkflowDiagnostic {
  code: string;
  severity: DiagnosticSeverity;
  message: string;
  stepId?: string;
  transitionId?: string;
}

function reachableFrom(
  seed: string,
  adjacency: Map<string, string[]>,
): Set<string> {
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

export function deriveWorkflowDiagnostics(
  steps: readonly WorkflowStepResponse[],
  transitions: readonly WorkflowTransitionResponse[],
): WorkflowDiagnostic[] {
  const diagnostics: WorkflowDiagnostic[] = [];
  const stepIds = new Set(steps.map((step) => step.id));
  const startSteps = steps.filter((step) => step.stepType === "START");
  const endSteps = steps.filter((step) => step.stepType === "END");

  if (startSteps.length === 0) {
    diagnostics.push({
      code: "MISSING_START",
      severity: "PUBLISH_BLOCKER",
      message: "يجب أن يحتوي سير العمل على خطوة بداية واحدة.",
    });
  } else if (startSteps.length > 1) {
    diagnostics.push({
      code: "MULTIPLE_START",
      severity: "PUBLISH_BLOCKER",
      message: "يجب أن يحتوي سير العمل على خطوة بداية واحدة فقط.",
    });
  }

  if (endSteps.length === 0) {
    diagnostics.push({
      code: "MISSING_END",
      severity: "WARNING",
      message: "لا توجد خطوة نهاية في الرسم الحالي.",
    });
  }

  const validTransitions: WorkflowTransitionResponse[] = [];
  for (const transition of transitions) {
    if (!stepIds.has(transition.fromStepId) || !stepIds.has(transition.toStepId)) {
      diagnostics.push({
        code: "BROKEN_TRANSITION_REFERENCE",
        severity: "ERROR",
        message: "يشير الانتقال إلى خطوة غير موجودة.",
        transitionId: transition.id,
      });
      continue;
    }
    validTransitions.push(transition);
  }

  const transitionsByKey = new Map<string, WorkflowTransitionResponse[]>();
  for (const transition of transitions) {
    const group = transitionsByKey.get(transition.transitionKey) ?? [];
    group.push(transition);
    transitionsByKey.set(transition.transitionKey, group);
  }
  for (const [key, group] of transitionsByKey) {
    if (group.length > 1) {
      diagnostics.push({
        code: "DUPLICATE_TRANSITION_KEY",
        severity: "WARNING",
        message: `مفتاح الانتقال مكرر: ${key}`,
        transitionId: group[0].id,
      });
    }
  }

  const forward = new Map<string, string[]>();
  const reverse = new Map<string, string[]>();
  for (const transition of validTransitions) {
    forward.set(transition.fromStepId, [
      ...(forward.get(transition.fromStepId) ?? []),
      transition.toStepId,
    ]);
    reverse.set(transition.toStepId, [
      ...(reverse.get(transition.toStepId) ?? []),
      transition.fromStepId,
    ]);
  }

  if (startSteps.length > 0) {
    const reachable = reachableFrom(startSteps[0].id, forward);
    for (const step of steps) {
      if (!reachable.has(step.id)) {
        diagnostics.push({
          code: "UNREACHABLE_STEP",
          severity: "WARNING",
          message: "لا يمكن الوصول إلى هذه الخطوة من البداية.",
          stepId: step.id,
        });
      }
    }
  }

  if (endSteps.length > 0) {
    const canReachEnd = new Set<string>();
    for (const terminal of endSteps) {
      for (const id of reachableFrom(terminal.id, reverse)) canReachEnd.add(id);
    }
    for (const step of steps) {
      if (step.stepType !== "END" && !canReachEnd.has(step.id)) {
        diagnostics.push({
          code: "NO_PATH_TO_END",
          severity: "WARNING",
          message: "لا يوجد مسار من هذه الخطوة إلى نهاية سير العمل.",
          stepId: step.id,
        });
      }
    }
  }

  return diagnostics;
}
