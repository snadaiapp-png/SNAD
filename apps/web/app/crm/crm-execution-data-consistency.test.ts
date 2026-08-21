import { describe, expect, it } from "vitest";
import {
  calculateGroupProgress,
  validateCrossLayerConsistency,
  type ExecutionGroup,
} from "../../lib/execution";
import { CRM_GROUP_DATA, CRM_TASKS } from "./crm-execution-data";

function executionGroup(code: string): ExecutionGroup {
  const groupData = CRM_GROUP_DATA.find((group) => group.code === code);
  if (!groupData) throw new Error(`Missing execution group ${code}`);

  return {
    id: `GROUP-${groupData.code}`,
    code: groupData.code,
    titleAr: groupData.titleAr,
    titleEn: groupData.titleEn,
    purposeAr: groupData.purposeAr,
    purposeEn: groupData.purposeEn,
    status: groupData.status,
    dependencies: [...groupData.dependencies],
    canParallelizeWith: [...groupData.canParallelizeWith],
    stageReport: groupData.stageReport,
    milestones: [],
    tasks: CRM_TASKS
      .filter((task) => task.groupCode === groupData.code)
      .map((task) => ({
        ...task,
        dependencies: [...task.dependencies],
        evidence: [],
      })),
  };
}

describe("CRM execution data integrity", () => {
  it("G7 APPROVED closure is represented by registered completed tasks and 100% progress", () => {
    const g7 = executionGroup("G7");
    const progress = calculateGroupProgress(g7);

    expect(g7.status).toBe("APPROVED");
    expect(g7.tasks.length).toBeGreaterThan(0);
    expect(g7.tasks.every((task) => task.status === "DONE" || task.status === "APPROVED")).toBe(true);
    expect(progress.total).toBe(g7.tasks.length);
    expect(progress.done + progress.approved).toBe(progress.total);
    expect(progress.percentage).toBe(100);
  });

  it("rejects APPROVED groups that have no tasks and therefore zero progress", () => {
    const g7 = executionGroup("G7");
    const invalidApprovedGroup: ExecutionGroup = {
      ...g7,
      tasks: [],
      status: "APPROVED",
    };

    const failures = validateCrossLayerConsistency(invalidApprovedGroup).filter(
      (result) => !result.passed,
    );

    expect(failures.some((result) => result.message.includes("0%"))).toBe(true);
  });
});
