import { describe, expect, it } from "vitest";
import { deriveWorkflowDiagnostics } from "../components/workflow-diagnostics";
import type {
  WorkflowStepResponse,
  WorkflowTransitionResponse,
  WorkflowStepType,
} from "@/lib/api/workflow-api";

const workflowStep = (
  id: string,
  stepType: WorkflowStepType,
): WorkflowStepResponse => ({
  id,
  workflowDefinitionId: "wf",
  stepKey: id,
  name: id,
  stepType,
  sequenceOrder: 0,
  configuration: "{}",
  slaHours: 0,
  requiredCapability: "",
  requiredRole: "",
  version: 1,
});

const workflowTransition = (
  id: string,
  fromStepId: string,
  toStepId: string,
  transitionKey = id,
): WorkflowTransitionResponse => ({
  id,
  fromStepId,
  toStepId,
  transitionKey,
  outcome: "SUCCESS",
  priority: 0,
});

const start = (id = "start") => workflowStep(id, "START");
const human = (id: string) => workflowStep(id, "HUMAN_TASK");
const end = (id = "end") => workflowStep(id, "END");

describe("workflow deterministic diagnostics", () => {
  it("flags a missing START as a publish blocker", () => {
    const diagnostics = deriveWorkflowDiagnostics(
      [human("a"), end()],
      [workflowTransition("t1", "a", "end")],
    );

    expect(diagnostics).toContainEqual(
      expect.objectContaining({
        code: "MISSING_START",
        severity: "PUBLISH_BLOCKER",
      }),
    );
  });

  it("flags multiple START nodes as a publish blocker", () => {
    const diagnostics = deriveWorkflowDiagnostics(
      [start("s1"), start("s2"), end()],
      [
        workflowTransition("t1", "s1", "end"),
        workflowTransition("t2", "s2", "end"),
      ],
    );

    expect(diagnostics).toContainEqual(
      expect.objectContaining({
        code: "MULTIPLE_START",
        severity: "PUBLISH_BLOCKER",
      }),
    );
  });

  it("flags transitions that reference missing steps", () => {
    const diagnostics = deriveWorkflowDiagnostics(
      [start()],
      [workflowTransition("broken", "start", "missing")],
    );

    expect(diagnostics).toContainEqual(
      expect.objectContaining({
        code: "BROKEN_TRANSITION_REFERENCE",
        severity: "ERROR",
        transitionId: "broken",
      }),
    );
  });

  it("flags duplicate transition keys as warnings", () => {
    const diagnostics = deriveWorkflowDiagnostics(
      [start(), human("a"), end()],
      [
        workflowTransition("t1", "start", "a", "same-key"),
        workflowTransition("t2", "a", "end", "same-key"),
      ],
    );

    expect(diagnostics).toContainEqual(
      expect.objectContaining({
        code: "DUPLICATE_TRANSITION_KEY",
        severity: "WARNING",
      }),
    );
  });

  it("flags unreachable nodes from START as warnings", () => {
    const diagnostics = deriveWorkflowDiagnostics(
      [start(), human("reachable"), human("orphan"), end()],
      [
        workflowTransition("t1", "start", "reachable"),
        workflowTransition("t2", "reachable", "end"),
      ],
    );

    expect(diagnostics).toContainEqual(
      expect.objectContaining({
        code: "UNREACHABLE_STEP",
        severity: "WARNING",
        stepId: "orphan",
      }),
    );
  });

  it("flags reachable nodes that cannot reach an END as warnings", () => {
    const diagnostics = deriveWorkflowDiagnostics(
      [start(), human("dead"), end()],
      [workflowTransition("t1", "start", "dead")],
    );

    expect(diagnostics).toContainEqual(
      expect.objectContaining({
        code: "NO_PATH_TO_END",
        severity: "WARNING",
        stepId: "dead",
      }),
    );
  });

  it("treats a missing END as a warning rather than an invented publish blocker", () => {
    const diagnostics = deriveWorkflowDiagnostics(
      [start(), human("a")],
      [workflowTransition("t1", "start", "a")],
    );

    expect(diagnostics).toContainEqual(
      expect.objectContaining({
        code: "MISSING_END",
        severity: "WARNING",
      }),
    );
    expect(
      diagnostics.some(
        (item) => item.code === "MISSING_END" && item.severity === "PUBLISH_BLOCKER",
      ),
    ).toBe(false);
  });

  it("returns no structural diagnostics for a simple valid path", () => {
    const diagnostics = deriveWorkflowDiagnostics(
      [start(), human("a"), end()],
      [
        workflowTransition("t1", "start", "a"),
        workflowTransition("t2", "a", "end"),
      ],
    );

    expect(diagnostics).toEqual([]);
  });
});
