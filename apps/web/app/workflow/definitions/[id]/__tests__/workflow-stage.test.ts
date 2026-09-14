import { describe, expect, it } from "vitest";
import {
  deriveTransitionProgressState,
  deriveWorkflowStagePresentation,
} from "../components/workflow-stage";
import type {
  WorkflowStepResponse,
  WorkflowTransitionResponse,
} from "@/lib/api/workflow-api";

const step = (stepType: string, id = stepType): WorkflowStepResponse => ({
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

const transition = (
  id: string,
  fromStepId: string,
  toStepId: string,
): WorkflowTransitionResponse => ({
  id,
  fromStepId,
  toStepId,
  transitionKey: id,
  outcome: "SUCCESS",
  priority: 0,
});

describe("SANAD workflow stage semantics", () => {
  it("maps START to the start presentation", () => {
    expect(
      deriveWorkflowStagePresentation(step("START"), {
        visitedStepIds: [],
        currentStepId: null,
      }),
    ).toMatchObject({
      visual: "START",
      label: "بداية",
      icon: "▶",
      isCurrent: false,
    });
  });

  it("maps visited non-terminal nodes to COMPLETED", () => {
    expect(
      deriveWorkflowStagePresentation(step("HUMAN_TASK", "a"), {
        visitedStepIds: ["a"],
        currentStepId: null,
      }),
    ).toMatchObject({
      visual: "COMPLETED",
      label: "منجزة",
      icon: "✓",
      isCurrent: false,
    });
  });

  it("keeps END as the terminal presentation even when reached", () => {
    expect(
      deriveWorkflowStagePresentation(step("END", "end"), {
        visitedStepIds: ["end"],
        currentStepId: "end",
      }),
    ).toMatchObject({
      visual: "END",
      label: "نهاية",
      icon: "◆",
      isCurrent: true,
    });
  });

  it("marks unvisited non-terminal nodes as UPCOMING", () => {
    expect(
      deriveWorkflowStagePresentation(step("APPROVAL", "approval"), {
        visitedStepIds: [],
        currentStepId: null,
      }).visual,
    ).toBe("UPCOMING");
  });
});

describe("SANAD workflow transition progress semantics", () => {
  it("marks explicitly active transitions as COMPLETED", () => {
    expect(
      deriveTransitionProgressState(transition("t1", "start", "a"), {
        visitedStepIds: ["start", "a"],
        currentStepId: "a",
        activeTransitionIds: ["t1"],
      }),
    ).toBe("COMPLETED");
  });

  it("marks outgoing transitions from visited nodes as UPCOMING when not active", () => {
    expect(
      deriveTransitionProgressState(transition("t2", "a", "b"), {
        visitedStepIds: ["a"],
        currentStepId: "a",
        activeTransitionIds: [],
      }),
    ).toBe("UPCOMING");
  });

  it("marks unrelated transitions as INACTIVE", () => {
    expect(
      deriveTransitionProgressState(transition("t3", "x", "y"), {
        visitedStepIds: ["a"],
        currentStepId: "a",
        activeTransitionIds: [],
      }),
    ).toBe("INACTIVE");
  });
});
