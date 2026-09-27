import { describe, expect, it } from "vitest";
import { HR_GROUP_DATA, HR_TASKS } from "./hr-execution-data";

describe("HR-G2 execution dashboard closure", () => {
  it("marks G2 DONE only after governed exact-main closure", () => {
    const g2 = HR_GROUP_DATA.find((group) => group.code === "G2");
    expect(g2).toBeDefined();
    expect(g2?.status).toBe("DONE");
  });

  it("marks every execution-dashboard G2 task DONE", () => {
    const tasks = HR_TASKS.filter((task) => task.groupCode === "G2");
    expect(tasks).toHaveLength(5);
    for (const task of tasks) {
      expect(task.status, task.id).toBe("DONE");
    }
  });
});
